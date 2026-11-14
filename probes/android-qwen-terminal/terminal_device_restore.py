'''
Device ownership recovery for the W3 qwen terminal acceptance run.

Bounded scope, no generic framework / CLI / whole driver:
- Only restore(root, ctx, *, serial) and private helpers are owned here.
- Every device operation goes through the restricted ctx bindings, which run
  the frozen hook before every subprocess; no direct subprocess / Popen.
- Never touches ordinary packages, never rm -rf, never model/native/mic.

The contract only undoes what accepted prepare() may have left behind:
two owned aliases (TEST then APP) and one flat owned stage directory.
Durable local claims (read before any effect) gate every destructive action;
no effect is taken without a matching durable proof of prior ownership.
'''

import json
import re
import shlex
from pathlib import Path

import terminal_file_io as tf
from adb_terminal_observation import _preflight_outputs

APP = 'io.ondeviceai.acceptance.qwenterminal'
TEST = APP + '.test'
RUN = 'w3-qwen-terminal-20261114-01'
STAGE = '/data/local/tmp/' + RUN
FIXED_FP = 'ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5'
NONCE_RE = re.compile(r'^[0-9a-f]{32}$')
HEX64_RE = re.compile(r'^[0-9a-f]{64}$')
MAX_LOCAL = 1048576


def _new_receipt(serial):
    return {
        'schemaVersion': 1,
        'runId': RUN,
        'sourceFingerprint': FIXED_FP,
        'serial': serial,
        'allRestored': False,
        'ordinaryUnchanged': False,
        'returnedToDemo': False,
        'errors': [],
        'operations': [],
    }


def _validate_bindings(ctx, serial):
    required = ('call', 'text', 'identity', 'gate', 'ordinary',
                'ordinary_models', 'installed_sha', 'verify_installed')
    for key in required:
        if key not in ctx:
            raise ValueError('ctx missing binding: ' + key)
        if not callable(ctx[key]):
            raise TypeError('ctx binding not callable: ' + key)
    if type(serial) is not str or not serial:
        raise ValueError('serial must be a nonblank string')
    if serial != serial.strip() or any(c.isspace() or ord(c) < 0x20 or ord(c) == 0x7f for c in serial):
        raise ValueError('serial must not contain whitespace/control chars')
    if ctx.get('serial') != serial:
        raise ValueError('serial binding mismatch')
    if ctx.get('plan', {}).get('sourceFingerprint') != FIXED_FP:
        raise ValueError('plan sourceFingerprint binding mismatch')
    if ctx.get('package', {}).get('sourceFingerprint') != FIXED_FP:
        raise ValueError('package sourceFingerprint binding mismatch')


def _reject_duplicate_keys(pairs):
    seen = {}
    for key, value in pairs:
        if key in seen:
            raise ValueError('duplicate JSON object key: ' + repr(key))
        seen[key] = value
    return dict(pairs)


def _raise_float(token):
    raise ValueError('float literal not allowed in receipt: ' + repr(token))


def _raise_constant(token):
    raise ValueError('non-finite constant not allowed in receipt: ' + repr(token))


def _read_json(root, relative):
    data = tf.read_stable(root, relative, MAX_LOCAL)
    if not isinstance(data, (bytes, bytearray)):
        raise ValueError('read_stable returned non-bytes payload for ' + relative)
    try:
        text = data.decode('utf-8')
    except UnicodeDecodeError as exc:
        raise ValueError('receipt is not valid UTF-8: ' + relative + ': ' + str(exc)) from exc
    try:
        parsed = json.loads(
            text,
            parse_float=_raise_float,
            parse_constant=_raise_constant,
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (ValueError, RecursionError) as exc:
        raise ValueError('receipt is not valid/stable JSON: ' + relative + ': ' + str(exc)) from exc
    if type(parsed) is not dict:
        raise ValueError('receipt is not a JSON object: ' + relative)
    return parsed


def _require_keys(obj, keys, label):
    if type(obj) is not dict:
        raise ValueError(label + ' must be an object')
    for key in keys:
        if key not in obj:
            raise ValueError(label + ' missing field: ' + key)


def _expected_stage_children(ctx):
    chunk = 8 * 1024 * 1024
    model_keys = ('conv_frontend.onnx', 'decoder.int8.onnx', 'encoder.int8.onnx',
                  'tokenizer/merges.txt', 'tokenizer/tokenizer_config.json', 'tokenizer/vocab.json')
    children = ['.odai-owner', 'main.apk', 'test.apk']
    files = ctx['package']['files']
    for apk in ('main.apk', 'test.apk'):
        row = files[apk]
        if type(row) is not dict:
            raise ValueError('package files ' + apk + ' must be an object')
        b = row.get('bytes')
        if type(b) is not int or b <= 0:
            raise ValueError('package files ' + apk + ' bytes must be a positive int')
        s = row.get('sha256')
        if type(s) is not str or not HEX64_RE.fullmatch(s):
            raise ValueError('package files ' + apk + ' sha256 must be 64 lowercase hex')
        count = (b + chunk - 1) // chunk if b > 0 else 0
        if count > 4096:
            raise ValueError('too many chunk parts for ' + apk)
        for i in range(count):
            children.append(apk + '.part-' + str(i).zfill(3))
    models = ctx.get('models')
    if type(models) is not dict or set(models.keys()) != set(model_keys):
        raise ValueError('ctx models must expose the exact six model keys')
    for name in model_keys:
        row = models[name]
        if type(row) is not dict:
            raise ValueError('model ' + name + ' must be an object')
        b = row.get('bytes')
        if type(b) is not int or b <= 0:
            raise ValueError('model ' + name + ' bytes must be a positive int')
        s = row.get('sha256')
        if type(s) is not str or not HEX64_RE.fullmatch(s):
            raise ValueError('model ' + name + ' sha256 must be 64 lowercase hex')
        flat = name.replace('/', '-')
        children.append(flat)
        count = (b + chunk - 1) // chunk if b > 0 else 0
        if count > 4096:
            raise ValueError('too many chunk parts for ' + name)
        for i in range(count):
            children.append(flat + '.part-' + str(i).zfill(3))
    if len(children) > 4096 or len(set(children)) != len(children):
        raise ValueError('staged children set invalid')
    return children


def _read_locals(root, serial, ctx):
    before = _read_json(root, 'device-before.json')
    _require_keys(before, ('schemaVersion', 'runId', 'sourceFingerprint', 'serial',
                           'currentUser', 'identity', 'gate', 'ordinary',
                           'ordinaryModels', 'ordinaryAPKHashes', 'stage'),
                  'device-before.json')
    if type(before['schemaVersion']) is not int or before['schemaVersion'] != 1:
        raise ValueError('device-before.json schemaVersion must be int 1')
    if before['runId'] != RUN:
        raise ValueError('device-before.json runId mismatch')
    if before['sourceFingerprint'] != FIXED_FP:
        raise ValueError('device-before.json sourceFingerprint mismatch')
    if type(before['serial']) is not str or before['serial'] != serial:
        raise ValueError('device-before.json serial mismatch')
    if type(before['currentUser']) is not str or before['currentUser'] != '0':
        raise ValueError('device-before.json currentUser must be exactly "0"')
    expected_identity = {'ro.product.model': 'TECNO KM9',
                         'ro.build.version.sdk': '35',
                         'ro.product.cpu.abi': 'arm64-v8a'}
    if before['identity'] != expected_identity:
        raise ValueError('device-before.json identity mismatch')
    ctx_current = ctx.get('current') if isinstance(ctx, dict) else None
    if isinstance(ctx_current, dict) and 'identity' in ctx_current and ctx_current['identity'] != before['identity']:
        raise ValueError('device-before.json identity does not match ctx current')
    gate = before['gate']
    if type(gate) is not dict or gate.get('awake') is not True or gate.get('unlocked') is not True:
        raise ValueError('device-before.json gate must be {awake: True, unlocked: True}')
    ordinary = before['ordinary']
    if type(ordinary) is not dict or len(ordinary) == 0:
        raise ValueError('device-before.json ordinary must be a non-empty object')
    if 'io.ondeviceai.demo' not in ordinary:
        raise ValueError('device-before.json ordinary must contain io.ondeviceai.demo')
    if APP in ordinary or TEST in ordinary:
        raise ValueError('device-before.json ordinary must exclude owned aliases')
    alias_re = re.compile(r'^io\.ondeviceai\.[A-Za-z0-9][A-Za-z0-9.]*$')
    for alias, path in ordinary.items():
        if type(alias) is not str or not alias_re.fullmatch(alias):
            raise ValueError('device-before.json ordinary alias invalid: ' + repr(alias))
        if type(path) is not str or path == '':
            raise ValueError('device-before.json ordinary path must be a non-empty string: ' + repr(alias))
    if type(before['ordinaryModels']) is not dict:
        raise ValueError('device-before.json ordinaryModels must be an object')
    for mname, mval in before['ordinaryModels'].items():
        if type(mname) is not str:
            raise ValueError('device-before.json ordinaryModels key must be a string')
        if mval is not None and (type(mval) is not str or not HEX64_RE.fullmatch(mval)):
            raise ValueError('device-before.json ordinaryModels value invalid: ' + repr(mname))
    if type(before['ordinaryAPKHashes']) is not dict:
        raise ValueError('device-before.json ordinaryAPKHashes must be an object')
    if set(before['ordinaryAPKHashes'].keys()) != set(ordinary.keys()):
        raise ValueError('device-before.json ordinaryAPKHashes keyset must equal ordinary keyset')
    for alias, sha in before['ordinaryAPKHashes'].items():
        if type(alias) is not str or not alias_re.fullmatch(alias):
            raise ValueError('device-before.json ordinaryAPKHashes key invalid: ' + repr(alias))
        if type(sha) is not str or not HEX64_RE.fullmatch(sha):
            raise ValueError('device-before.json ordinaryAPKHashes value invalid: ' + repr(alias))
    if before['stage'] != STAGE:
        raise ValueError('device-before.json stage mismatch')

    result = {
        'ordinary': before['ordinary'],
        'ordinary_models': before['ordinaryModels'],
        'ordinary_apk_hashes': before['ordinaryAPKHashes'],
        'stage_claim': None,
        'stage_created': None,
        'install_claims': {},
    }

    try:
        claim = _read_json(root, 'ownership-stage-claimed.json')
    except FileNotFoundError:
        claim = None
    if claim is not None:
        _require_keys(claim, ('schemaVersion', 'runId', 'sourceFingerprint', 'serial', 'stage', 'nonce', 'children'),
                      'ownership-stage-claimed.json')
        if type(claim['schemaVersion']) is not int or claim['schemaVersion'] != 1:
            raise ValueError('ownership-stage-claimed.json schemaVersion must be int 1')
        if claim['runId'] != RUN:
            raise ValueError('ownership-stage-claimed.json runId mismatch')
        if claim['sourceFingerprint'] != FIXED_FP:
            raise ValueError('ownership-stage-claimed.json sourceFingerprint mismatch')
        if type(claim['serial']) is not str or claim['serial'] != serial:
            raise ValueError('ownership-stage-claimed.json serial mismatch')
        if claim['stage'] != STAGE:
            raise ValueError('ownership-stage-claimed.json stage mismatch')
        if type(claim['nonce']) is not str or not NONCE_RE.fullmatch(claim['nonce']):
            raise ValueError('ownership-stage-claimed.json nonce must be 32 lowercase hex')
        children = claim['children']
        if (not isinstance(children, list) or len(children) == 0 or
                not all(isinstance(c, str) for c in children) or
                len(children) > 4096 or len(set(children)) != len(children)):
            raise ValueError('ownership-stage-claimed.json children invalid')
        expected = _expected_stage_children(ctx)
        if set(children) != set(expected):
            raise ValueError('ownership-stage-claimed.json children do not match expected frozen set')
        result['stage_claim'] = claim

    try:
        created = _read_json(root, 'ownership-stage-created.json')
    except FileNotFoundError:
        created = None
    if created is not None:
        if result['stage_claim'] is None:
            raise ValueError('ownership-stage-created.json present without a stage claim')
        _require_keys(created, ('schemaVersion', 'runId', 'sourceFingerprint', 'serial', 'stage', 'nonce', 'stageIdentity'),
                      'ownership-stage-created.json')
        if type(created['schemaVersion']) is not int or created['schemaVersion'] != 1:
            raise ValueError('ownership-stage-created.json schemaVersion must be int 1')
        if created['runId'] != RUN or created['sourceFingerprint'] != FIXED_FP or created['serial'] != serial:
            raise ValueError('ownership-stage-created.json identity mismatch')
        if created['stage'] != STAGE:
            raise ValueError('ownership-stage-created.json stage mismatch')
        if type(created['nonce']) is not str or created['nonce'] != claim['nonce']:
            raise ValueError('ownership-stage-created.json nonce must equal the stage claim nonce')
        si = created['stageIdentity']
        if type(si) is not dict or set(si) != {'uid', 'dev', 'ino'}:
            raise ValueError('ownership-stage-created.json stageIdentity invalid')
        for k in ('uid', 'dev', 'ino'):
            if type(si[k]) is not str:
                raise ValueError('ownership-stage-created.json stageIdentity.' + k + ' must be a string')
        if not re.fullmatch(r'0|[1-9][0-9]*', si['uid']):
            raise ValueError('ownership-stage-created.json stageIdentity.uid must be canonical decimal')
        if not re.fullmatch(r'0|[1-9][0-9]*', si['dev']):
            raise ValueError('ownership-stage-created.json stageIdentity.dev must be canonical decimal')
        if not re.fullmatch(r'[1-9][0-9]*', si['ino']):
            raise ValueError('ownership-stage-created.json stageIdentity.ino must be a positive canonical decimal')
        result['stage_created'] = created

    for alias, name in ((APP, 'main'), (TEST, 'test')):
        try:
            ic = _read_json(root, 'ownership-install-' + name + '-claimed.json')
        except FileNotFoundError:
            ic = None
        if ic is not None:
            apk_key = name + '.apk'
            _require_keys(ic, ('schemaVersion', 'runId', 'sourceFingerprint', 'serial', 'alias', 'sha256'),
                          'ownership-install-' + name + '-claimed.json')
            if type(ic['schemaVersion']) is not int or ic['schemaVersion'] != 1:
                raise ValueError('ownership-install-' + name + '-claimed.json schemaVersion must be int 1')
            if ic['runId'] != RUN or ic['sourceFingerprint'] != FIXED_FP or ic['serial'] != serial:
                raise ValueError('ownership-install-' + name + '-claimed.json identity mismatch')
            if ic['alias'] != alias:
                raise ValueError('ownership-install-' + name + '-claimed.json alias mismatch')
            expected_sha = ctx['package']['files'][apk_key]['sha256']
            if type(ic['sha256']) is not str or not HEX64_RE.fullmatch(ic['sha256']):
                raise ValueError('ownership-install-' + name + '-claimed.json sha256 invalid')
            if ic['sha256'] != expected_sha:
                raise ValueError('ownership-install-' + name + '-claimed.json sha256 does not match frozen package files')
            result['install_claims'][alias] = ic

    return result

def _preflight(root):
    _preflight_outputs(root, {
        'restoration': 'restoration.json',
        'stage_recovered': 'ownership-stage-recovered.json',
    })


def _verify_pre(ctx, receipt, before):
    try:
        if ctx['text']('shell', 'am', 'get-current-user') != '0':
            raise AssertionError('current user is not 0')
        ctx['identity']()
        ctx['gate']()
        live_ordinary = ctx['ordinary']()
        live_models = ctx['ordinary_models']()
        if live_ordinary != before['ordinary']:
            raise AssertionError('ordinary packages changed')
        if live_models != before['ordinary_models']:
            raise AssertionError('ordinary models changed')
        for alias, initial_sha in before['ordinary_apk_hashes'].items():
            if ctx['installed_sha'](alias) != initial_sha:
                raise AssertionError('ordinary APK hash drift: ' + alias)
    except Exception as exc:
        receipt['errors'].append('pre-mutation verification failed: ' + type(exc).__name__ + ': ' + str(exc))
        receipt['ordinaryUnchanged'] = False
        return False
    return True


def _alias_absent(ctx, alias):
    proc = ctx['call']('shell', 'pm', 'path', alias, accepted=(0, 1))
    if type(proc.returncode) is not int or proc.returncode not in (0, 1):
        raise RuntimeError('pm path unexpected returncode: ' + alias)
    out = proc.stdout
    err = proc.stderr
    if type(out) is not bytes or type(err) is not bytes:
        raise RuntimeError('pm path non-bytes output: ' + alias)
    if out == b'' and err == b'':
        return True
    if err != b'':
        raise RuntimeError('pm path produced stderr: ' + alias)
    if proc.returncode != 0:
        raise RuntimeError('pm path nonzero with output: ' + alias)
    try:
        text = out.decode('utf-8')
    except UnicodeDecodeError as exc:
        raise RuntimeError('pm path non-UTF-8 output: ' + alias) from exc
    lines = text.split('\n')
    if lines and lines[-1] == '':
        lines.pop()
    if not lines:
        raise RuntimeError('pm path empty package list: ' + alias)
    for line in lines:
        for ch in line:
            o = ord(ch)
            if o < 0x20 or o == 0x7f or ch.isspace():
                raise RuntimeError('pm path control byte in output: ' + alias)
        if line == '':
            raise RuntimeError('pm path blank line: ' + alias)
        if not line.startswith('package:/') or not line.endswith('.apk'):
            raise RuntimeError('pm path malformed line: ' + alias)
        if line[len('package:/'): -len('.apk')] == '':
            raise RuntimeError('pm path empty path: ' + alias)
    return False


def _recover_alias(ctx, receipt, install_claims, alias):
    if _alias_absent(ctx, alias):
        receipt['operations'].append({'op': 'alias-absent', 'alias': alias})
        return True
    claim = install_claims.get(alias)
    if claim is None:
        receipt['errors'].append('alias present without matching install claim, retaining: ' + alias)
        receipt['operations'].append({'op': 'alias-uncertain-retain', 'alias': alias})
        return False
    try:
        live_sha = ctx['installed_sha'](alias)
    except Exception as exc:
        receipt['errors'].append('alias installed_sha probe failed, retaining: ' + alias + ': ' + str(exc))
        receipt['operations'].append({'op': 'alias-uncertain-retain', 'alias': alias})
        return False
    if live_sha != claim['sha256']:
        receipt['errors'].append('alias APK hash mismatch, not adopting: ' + alias)
        receipt['operations'].append({'op': 'alias-mismatch-retain', 'alias': alias})
        return False
    if alias == APP:
        ctx['call']('shell', 'am', 'force-stop', APP)
        receipt['operations'].append({'op': 'force-stop', 'alias': alias})
    result = ctx['text']('shell', 'pm', 'uninstall', alias, timeout=60)
    receipt['operations'].append({'op': 'uninstall', 'alias': alias, 'result': result})
    if result != 'Success' or not _alias_absent(ctx, alias):
        raise RuntimeError('uninstall not verified for ' + alias + ': result=' + str(result))
    receipt['operations'].append({'op': 'alias-removed', 'alias': alias})
    return True


def _recover_stage(ctx, root, receipt, before):
    stage_claim = before['stage_claim']
    stage_created = before['stage_created']
    islink = ctx['call']('shell', 'test', '-L', STAGE, accepted=(0, 1)).returncode == 0
    exists = ctx['call']('shell', 'test', '-e', STAGE, accepted=(0, 1)).returncode == 0
    if not exists and not islink:
        receipt['operations'].append({'op': 'stage-absent'})
        return True
    if islink:
        receipt['errors'].append('stage is a dangling symlink, retaining whole stage')
        receipt['operations'].append({'op': 'stage-link-retain'})
        return False
    if stage_claim is None:
        receipt['errors'].append('stage present without durable claim, retaining whole stage')
        receipt['operations'].append({'op': 'stage-no-claim-retain'})
        return False
    if ctx['call']('shell', 'test', '-L', STAGE, accepted=(0, 1)).returncode == 0:
        receipt['errors'].append('stage is a symlink, retaining')
        return False
    if ctx['call']('shell', 'test', '-L', STAGE + '/.odai-owner', accepted=(0, 1)).returncode == 0:
        receipt['errors'].append('marker is a symlink, retaining whole stage')
        return False
    if ctx['call']('shell', 'test', '-d', STAGE, accepted=(0, 1)).returncode != 0:
        receipt['errors'].append('stage not a directory, retaining')
        return False
    if ctx['call']('shell', 'test', '-f', STAGE + '/.odai-owner', accepted=(0, 1)).returncode != 0:
        receipt['errors'].append('marker missing or not regular, retaining whole stage')
        return False
    marker = ctx['call']('exec-out', 'head', '-c', '34', STAGE + '/.odai-owner').stdout
    if type(marker) is not bytes or len(marker) != 33 or marker != (stage_claim['nonce'] + '\n').encode('ascii'):
        receipt['errors'].append('marker token mismatch, retaining whole stage')
        return False
    stat = ctx['text']('shell', shlex.join(['stat', '-c', '%u %d %i', STAGE])).split()
    if len(stat) != 3 or not all(re.fullmatch(r'(?:0|[1-9][0-9]*)', s) for s in stat):
        receipt['errors'].append('stage stat field invalid, retaining whole stage')
        return False
    uid, dev, ino = int(stat[0]), int(stat[1]), int(stat[2])
    if uid < 0 or dev < 0 or ino <= 0:
        receipt['errors'].append('stage stat field range invalid, retaining whole stage')
        return False
    if stage_created is not None:
        si = stage_created['stageIdentity']
        if str(uid) != si['uid'] or str(dev) != si['dev'] or str(ino) != si['ino']:
            receipt['errors'].append('stage identity mismatch vs created record, retaining whole stage')
            return False
    else:
        recovered = {
            'schemaVersion': 1, 'runId': RUN, 'sourceFingerprint': FIXED_FP,
            'serial': receipt['serial'], 'stage': STAGE, 'nonce': stage_claim['nonce'],
            'stageIdentity': {'uid': str(uid), 'dev': str(dev), 'ino': str(ino)}}
        try:
            tf.write_new_durable(root, 'ownership-stage-recovered.json',
                                 json.dumps(recovered, sort_keys=True, allow_nan=False).encode())
        except Exception as exc:
            receipt['errors'].append('ownership-stage-recovered journal write failed, retaining stage: ' + str(exc))
            return False
        receipt['operations'].append({'op': 'stage-recovered-journal'})
    children = [c for c in ctx['text']('shell', 'ls', '-A', STAGE).splitlines() if c]
    claimed = set(stage_claim['children'])
    unknown = [c for c in children if c not in claimed]
    if not children:
        receipt['errors'].append('stage enumeration empty, retaining whole stage')
        return False
    if len(children) != len(set(children)):
        receipt['errors'].append('stage enumeration not unique, retaining whole stage')
        return False
    if '.odai-owner' not in children:
        receipt['errors'].append('stage enumeration missing .odai-owner, retaining whole stage')
        return False
    if unknown:
        receipt['errors'].append('unknown stage child(ren) present, retaining whole stage: ' + repr(unknown))
        return False
    for child in children:
        full = STAGE + '/' + child
        if ctx['call']('shell', 'test', '-L', full, accepted=(0, 1)).returncode == 0:
            receipt['errors'].append('stage child is a symlink, retaining whole stage: ' + child)
            return False
        if ctx['call']('shell', 'test', '-d', full, accepted=(0, 1)).returncode == 0:
            receipt['errors'].append('stage child is a directory, retaining whole stage: ' + child)
            return False
        if ctx['call']('shell', 'test', '-f', full, accepted=(0, 1)).returncode != 0:
            receipt['errors'].append('stage child not a regular file, retaining whole stage: ' + child)
            return False

    # Last ownership check: re-prove stage identity immediately before deletion.
    if ctx['call']('shell', 'test', '-L', STAGE, accepted=(0, 1)).returncode == 0:
        receipt['errors'].append('stage became a symlink before delete, retaining whole stage')
        return False
    if ctx['call']('shell', 'test', '-d', STAGE, accepted=(0, 1)).returncode != 0:
        receipt['errors'].append('stage no longer a directory before delete, retaining whole stage')
        return False
    if ctx['call']('shell', 'test', '-L', STAGE + '/.odai-owner', accepted=(0, 1)).returncode == 0:
        receipt['errors'].append('marker became a symlink before delete, retaining whole stage')
        return False
    if ctx['call']('shell', 'test', '-f', STAGE + '/.odai-owner', accepted=(0, 1)).returncode != 0:
        receipt['errors'].append('marker missing before delete, retaining whole stage')
        return False
    remarker = ctx['call']('exec-out', 'head', '-c', '34', STAGE + '/.odai-owner').stdout
    if type(remarker) is not bytes or len(remarker) != 33 or remarker != (stage_claim['nonce'] + '\n').encode('ascii'):
        receipt['errors'].append('marker token mismatch before delete, retaining whole stage')
        return False
    restat = ctx['text']('shell', shlex.join(['stat', '-c', '%u %d %i', STAGE])).split()
    if len(restat) != 3 or not all(re.fullmatch(r'(?:0|[1-9][0-9]*)', s) for s in restat):
        receipt['errors'].append('stage stat field invalid before delete, retaining whole stage')
        return False
    ruid, rdev, rino = int(restat[0]), int(restat[1]), int(restat[2])
    if str(ruid) != str(uid) or str(rdev) != str(dev) or str(rino) != str(ino):
        receipt['errors'].append('stage identity changed before delete, retaining whole stage')
        return False

    for child in [c for c in children if c != '.odai-owner']:
        full = STAGE + '/' + child
        ctx['call']('shell', 'rm', '-f', full)
        receipt['operations'].append({'op': 'remove-file', 'name': child})
    ctx['call']('shell', 'rm', '-f', STAGE + '/.odai-owner')
    receipt['operations'].append({'op': 'remove-marker'})
    ctx['call']('shell', 'rmdir', STAGE)
    receipt['operations'].append({'op': 'remove-stage'})
    if ctx['call']('shell', 'test', '-e', STAGE, accepted=(0, 1)).returncode == 0 or \
            ctx['call']('shell', 'test', '-L', STAGE, accepted=(0, 1)).returncode == 0:
        receipt['errors'].append('stage still present after rmdir')
        return False
    return True


def _verify_post(ctx, receipt, before):
    try:
        if ctx['ordinary']() != before['ordinary']:
            raise AssertionError('ordinary packages changed after recovery')
        if ctx['ordinary_models']() != before['ordinary_models']:
            raise AssertionError('ordinary models changed after recovery')
        for alias, initial_sha in before['ordinary_apk_hashes'].items():
            if ctx['installed_sha'](alias) != initial_sha:
                raise AssertionError('ordinary APK hash drift after recovery: ' + alias)
    except Exception as exc:
        receipt['errors'].append('post-mutation ordinary recheck failed: ' + type(exc).__name__ + ': ' + str(exc))
        receipt['ordinaryUnchanged'] = False
        return False
    receipt['ordinaryUnchanged'] = True
    return True


def _return_to_demo(ctx, receipt):
    try:
        ctx['text']('shell', 'am', 'start', '-n', 'io.ondeviceai.demo/io.ondeviceai.demo.MainActivity')
    except Exception as exc:
        receipt['errors'].append('return-to-demo am start failed: ' + type(exc).__name__ + ': ' + str(exc))
        receipt['returnedToDemo'] = False
        receipt['operations'].append({'op': 'return-demo-failed'})
        return
    receipt['operations'].append({'op': 'return-demo'})
    try:
        activities = ctx['text']('shell', 'dumpsys', 'activity', 'activities')
    except Exception as exc:
        receipt['errors'].append('return-to-demo readback failed: ' + type(exc).__name__ + ': ' + str(exc))
        receipt['returnedToDemo'] = False
        return
    ALLOWED = ('io.ondeviceai.demo/.MainActivity', 'io.ondeviceai.demo/io.ondeviceai.demo.MainActivity')
    resumed_lines = []
    for raw in activities.splitlines():
        line = raw.strip()
        if line.startswith('mResumedActivity:') or line.startswith('topResumedActivity='):
            resumed_lines.append(line)
    if not resumed_lines:
        receipt['errors'].append('no resumed activity record, foreground is not io.ondeviceai.demo/.MainActivity')
        receipt['returnedToDemo'] = False
        return
    parsed = []
    for line in resumed_lines:
        start = line.find('ActivityRecord{')
        if start == -1:
            parsed.append((False, 'malformed resumed activity record: ' + line))
            continue
        end = line.find('}', start)
        if end == -1:
            parsed.append((False, 'malformed resumed activity record: ' + line))
            continue
        body = line[start + len('ActivityRecord{'):end]
        user = None
        comp = None
        for tok in body.split():
            if tok.startswith('u') and tok[1:].isdigit():
                user = tok
            if tok in ALLOWED:
                comp = tok
        if user == 'u0' and comp is not None:
            parsed.append((True, None))
        else:
            parsed.append((False, 'foreground activity is not io.ondeviceai.demo/.MainActivity'))
    if any(not ok for ok, _ in parsed):
        for ok, msg in parsed:
            if not ok:
                receipt['errors'].append(msg)
                break
        receipt['returnedToDemo'] = False
        return
    receipt['returnedToDemo'] = True


def _write_receipt(root, receipt):
    tf.write_new_durable(root, 'restoration.json',
                         json.dumps(receipt, sort_keys=True, allow_nan=False).encode())


def restore(root, ctx, *, serial):
    root = Path(root)
    _validate_bindings(ctx, serial)
    before = _read_locals(root, serial, ctx)
    _preflight(root)

    receipt = _new_receipt(serial)
    alias_ok = True
    stage_ok = True
    try:
        if not _verify_pre(ctx, receipt, before):
            _write_receipt(root, receipt)
            return receipt
        receipt['ordinaryUnchanged'] = True

        for alias in (TEST, APP):
            try:
                if not _recover_alias(ctx, receipt, before['install_claims'], alias):
                    alias_ok = False
            except Exception as exc:
                alias_ok = False
                receipt['errors'].append('alias recovery error (' + alias + '): ' +
                                         type(exc).__name__ + ': ' + str(exc))

        try:
            if not _recover_stage(ctx, root, receipt, before):
                stage_ok = False
        except Exception as exc:
            stage_ok = False
            receipt['errors'].append('stage recovery error: ' + type(exc).__name__ + ': ' + str(exc))

        _verify_post(ctx, receipt, before)

        if receipt['ordinaryUnchanged']:
            _return_to_demo(ctx, receipt)
    except BaseException as exc:
        receipt['errors'].append(type(exc).__name__ + ': ' + str(exc))
        receipt['allRestored'] = False
        try:
            _write_receipt(root, receipt)
        except BaseException as secondary:
            try:
                exc.add_note('secondary receipt write failure: ' +
                             type(secondary).__name__ + ': ' + str(secondary))
            except Exception:
                pass
        raise

    receipt['allRestored'] = bool(
        alias_ok and stage_ok and receipt['ordinaryUnchanged']
        and receipt['returnedToDemo'] and not receipt['errors'])
    _write_receipt(root, receipt)
    return receipt
