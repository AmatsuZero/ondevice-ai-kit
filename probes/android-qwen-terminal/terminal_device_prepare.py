import re
import shlex
import json
from pathlib import Path
from uuid import uuid4

import terminal_file_io as tf
from adb_terminal_observation import _preflight_outputs

APP = 'io.ondeviceai.acceptance.qwenterminal'
TEST = APP + '.test'
RUN = 'w3-qwen-terminal-20261112-01'
STAGE = '/data/local/tmp/' + RUN
MODEL = 'no_backup/qwen3-asr-0.6b-int8-393f8a14'
FIXED_FP = 'ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5'
MODEL_KEYS = ('conv_frontend.onnx', 'decoder.int8.onnx', 'encoder.int8.onnx',
              'tokenizer/merges.txt', 'tokenizer/tokenizer_config.json', 'tokenizer/vocab.json')
CHUNK = 8 * 1024 * 1024
HEX64 = re.compile(r'^[0-9a-f]{64}$')
NONCE_RE = re.compile(r'^[0-9a-f]{32}$')


def default_uuid4_hex():
    return uuid4().hex


def _check_row(row):
    b = row.get('bytes')
    if type(b) is not int or b <= 0:
        raise ValueError('row bytes must be positive int')
    s = row.get('sha256')
    if type(s) is not str or not HEX64.fullmatch(s):
        raise ValueError('row sha256 must be 64 lowercase hex')


def _check_local(local, row, digest, base):
    local = Path(local)
    base = Path(base)
    current = local
    while True:
        if current.is_symlink():
            raise ValueError('symlink in input path: ' + str(current))
        if current.parent == current:
            break
        current = current.parent
    if not local.is_file():
        raise ValueError('local not a regular file: ' + str(local))
    if local.stat().st_size != row['bytes']:
        raise ValueError('local size mismatch: ' + str(local))
    if digest(local) != row['sha256']:
        raise ValueError('local hash mismatch: ' + str(local))


def _chunk_parts(label, size):
    count = (size + CHUNK - 1) // CHUNK if size > 0 else 0
    return [label + '.part-' + str(i).zfill(3) for i in range(count)]


def _safe_child(name):
    if not isinstance(name, str) or name in ('', '.', '..') or '/' in name:
        raise ValueError('unsafe child label: ' + repr(name))
    return name


def _write(root, relative, payload):
    tf.write_new_durable(root, relative,
                         json.dumps(payload, sort_keys=True, allow_nan=False).encode())


def _validate_inputs(ctx, root):
    if set(ctx['models'].keys()) != set(MODEL_KEYS):
        raise ValueError('model key set mismatch')
    for name in ('main.apk', 'test.apk'):
        row = ctx['package']['files'][name]
        _check_row(row)
        _check_local(root / name, row, ctx['digest'], root)
    for name, row in ctx['models'].items():
        _check_row(row)
        mdir = Path(ctx['manifest']['directory'])
        _check_local(mdir / name, row, ctx['digest'], mdir)


def _preflight(root):
    targets = {
        'transfer_main_apk': 'transfer-main.apk.json',
        'transfer_test_apk': 'transfer-test.apk.json',
        'device_before': 'device-before.json',
        'stage_claimed': 'ownership-stage-claimed.json',
        'stage_created': 'ownership-stage-created.json',
        'install_main_claimed': 'ownership-install-main-claimed.json',
        'install_test_claimed': 'ownership-install-test-claimed.json',
        'prepared': 'prepared.json',
        'run_started': 'run-started.json',
        'transfer_part': 'transfer-part.tmp',
        'install_main': 'install-main.apk.json',
        'install_test': 'install-test.apk.json',
    }
    for name in MODEL_KEYS:
        targets['transfer_' + name.replace('/', '-')] = 'transfer-' + name.replace('/', '-') + '.json'
    try:
        _preflight_outputs(root, targets)
    except Exception as exc:
        raise ValueError('preflight rejected existing output: ' + str(exc)) from exc


def _build_children(ctx, root):
    children = [_safe_child('.odai-owner'), _safe_child('main.apk'), _safe_child('test.apk')]
    sizes = {}
    for name in ('main.apk', 'test.apk'):
        sizes[name] = (root / name).stat().st_size
    for name in MODEL_KEYS:
        flat = name.replace('/', '-')
        children.append(_safe_child(flat))
        sizes[flat] = (Path(ctx['manifest']['directory']) / name).stat().st_size
    for label, size in sizes.items():
        for part in _chunk_parts(label, size):
            children.append(_safe_child(part))
    if len(children) > 4096:
        raise ValueError('too many staged children')
    if len(set(children)) != len(children):
        raise ValueError('duplicate staged children')
    return children


def prepare(root, ctx, *, serial, cleanup, nonce_factory=default_uuid4_hex):
    root = Path(root)
    mutation_may_have_started = False
    try:
        for key in ('call', 'text', 'exists', 'identity', 'gate', 'ordinary',
                    'ordinary_models', 'installed_sha', 'verify_installed',
                    'transfer', 'digest', 'parse_kb', 'package', 'source',
                    'plan', 'manifest', 'models', 'serial', 'current'):
            if key not in ctx:
                raise ValueError('ctx missing binding: ' + key)
        for fn in ('call', 'text', 'exists', 'identity', 'gate', 'ordinary',
                   'ordinary_models', 'installed_sha', 'verify_installed',
                   'transfer', 'digest', 'parse_kb'):
            if not callable(ctx[fn]):
                raise TypeError('ctx binding not callable: ' + fn)
        if not callable(nonce_factory):
            raise TypeError('nonce_factory not callable')
        if not callable(cleanup):
            raise TypeError('cleanup not callable')
        if ctx['plan'].get('runId') != RUN:
            raise ValueError('runId binding mismatch')
        if ctx['plan'].get('sourceFingerprint') != FIXED_FP:
            raise ValueError('plan sourceFingerprint binding mismatch')
        if ctx['package'].get('sourceFingerprint') != FIXED_FP:
            raise ValueError('sourceFingerprint binding mismatch')
        if type(serial) is not str or not serial:
            raise ValueError('serial must be nonblank string')
        if serial != serial.strip() or any(c.isspace() or ord(c) < 0x20 or ord(c) == 0x7f for c in serial):
            raise ValueError('serial must not contain whitespace/control chars')
        if ctx.get('serial') != serial:
            raise ValueError('serial binding mismatch')
        plan = ctx['plan']
        if type(plan.get('caseCount')) is not int or plan.get('caseCount') != 1:
            raise ValueError('plan case/decode binding mismatch')
        if type(plan.get('maxNativeDecodeCalls')) is not int or plan.get('maxNativeDecodeCalls') != 2:
            raise ValueError('plan case/decode binding mismatch')
        nonce = nonce_factory()
        if type(nonce) is not str or not NONCE_RE.fullmatch(nonce):
            raise ValueError('nonce must be 32 lowercase hex')
        _validate_inputs(ctx, root)
        _preflight(root)
        identity = ctx['identity']()
        if ctx['text']('shell', 'am', 'get-current-user') != '0':
            raise ValueError('current user is not 0')
        gate = ctx['gate']()
        mem = ctx['parse_kb'](ctx['text']('shell', 'cat', '/proc/meminfo'), 'MemAvailable')
        if mem < 2 * 1024 * 1024 * 1024:
            raise ValueError('MemAvailable below 2GiB')
        if ctx['text']('shell', 'pm', 'list', 'packages', APP):
            raise ValueError('new alias already present')
        if (ctx['call']('shell', 'test', '-e', STAGE, accepted=(0, 1)).returncode == 0 or
                ctx['call']('shell', 'test', '-L', STAGE, accepted=(0, 1)).returncode == 0):
            raise AssertionError('stage present or dangling symlink')
        ordinary = ctx['ordinary']()
        ordinary_models = ctx['ordinary_models']()
        ordinary_apk_hashes = {alias: ctx['installed_sha'](alias) for alias in ordinary}
        _write(root, 'device-before.json', {
            'schemaVersion': 1, 'runId': RUN, 'sourceFingerprint': FIXED_FP,
            'serial': serial, 'currentUser': '0', 'identity': identity,
            'gate': gate, 'ordinary': ordinary,
            'ordinaryModels': ordinary_models, 'ordinaryAPKHashes': ordinary_apk_hashes,
            'stage': STAGE})
        children = _build_children(ctx, root)
        _write(root, 'ownership-stage-claimed.json', {
            'schemaVersion': 1, 'runId': RUN, 'sourceFingerprint': FIXED_FP,
            'serial': serial, 'stage': STAGE, 'nonce': nonce, 'children': children})
        mutation_may_have_started = True
        ctx['call']('shell', 'mkdir', STAGE)
        ctx['call']('shell', "printf '%s\\n' " + shlex.quote(nonce) + " > " + shlex.quote(STAGE + '/.odai-owner'))
        if (ctx['call']('shell', 'test', '-L', STAGE, accepted=(0, 1)).returncode == 0 or
                ctx['call']('shell', 'test', '-L', STAGE + '/.odai-owner', accepted=(0, 1)).returncode == 0 or
                ctx['call']('shell', 'test', '-d', STAGE, accepted=(0, 1)).returncode != 0 or
                ctx['call']('shell', 'test', '-f', STAGE + '/.odai-owner', accepted=(0, 1)).returncode != 0):
            raise RuntimeError('stage or marker link/dir/regular verification failed')
        marker = ctx['call']('exec-out', 'head', '-c', '34', STAGE + '/.odai-owner').stdout
        if type(marker) is not bytes or len(marker) != 33 or marker != (nonce + '\n').encode('ascii'):
            raise RuntimeError('marker read mismatch')
        import re
        stat = ctx['text']('shell', 'stat', '-c', '%u %d %i', STAGE).split()
        if len(stat) != 3 or not all(re.fullmatch(r'(?:0|[1-9][0-9]*)', s) for s in stat):
            raise RuntimeError('stage stat field mismatch')
        uid, dev, ino = int(stat[0]), int(stat[1]), int(stat[2])
        if uid < 0 or dev < 0 or ino <= 0:
            raise RuntimeError('stage stat field range mismatch')
        _write(root, 'ownership-stage-created.json', {
            'schemaVersion': 1, 'runId': RUN, 'sourceFingerprint': FIXED_FP,
            'serial': serial, 'stage': STAGE, 'nonce': nonce,
            'stageIdentity': {'uid': str(uid), 'dev': str(dev), 'ino': str(ino)}})
        for name, alias in (('main.apk', APP), ('test.apk', TEST)):
            sha = ctx['package']['files'][name]['sha256']
            remote = ctx['transfer'](name, root / name, sha)
            _write(root, 'ownership-install-' + ('main' if alias == APP else 'test') + '-claimed.json', {
                'schemaVersion': 1, 'runId': RUN, 'sourceFingerprint': FIXED_FP,
                'serial': serial, 'alias': alias, 'sha256': sha})
            result = ctx['text']('shell', 'pm', 'install', '-t', remote, timeout=60)
            _write(root, 'install-' + name + '.json', {'result': result})
            if result != 'Success' or ctx['installed_sha'](alias) != sha:
                raise RuntimeError('install not verified: ' + name)
        instr = ctx['text']('shell', 'pm', 'list', 'instrumentation', APP)
        runner_expected = ('instrumentation:' + TEST +
                           '/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation (target=' + APP + ')')
        if runner_expected not in instr.splitlines():
            raise RuntimeError('runner component missing')
        ctx['call']('shell', 'run-as', APP, 'mkdir', '-p', MODEL + '/tokenizer')
        for name, row in ctx['models'].items():
            sha = row['sha256']
            local = Path(ctx['manifest']['directory']) / name
            remote = ctx['transfer'](name.replace('/', '-'), local, sha)
            ctx['call']('shell', 'run-as', APP, 'cp', remote, MODEL + '/' + name, timeout=60)
            if ctx['text']('shell', 'run-as', APP, 'sha256sum', MODEL + '/' + name).split()[0] != sha:
                raise RuntimeError('model not copied: ' + name)
        if ctx['parse_kb'](ctx['text']('shell', 'run-as', APP, 'cat', '/proc/self/status'), 'VmRSS') <= 0:
            raise RuntimeError('same-uid process not readable')
        if ctx['ordinary']() != ordinary or ctx['ordinary_models']() != ordinary_models:
            raise RuntimeError('ordinary snapshot changed')
        for name, row in ctx['models'].items():
            if ctx['text']('shell', 'run-as', APP, 'sha256sum', MODEL + '/' + name).split()[0] != row['sha256']:
                raise RuntimeError('model hash drift: ' + name)
        ctx['verify_installed']()
        if not ordinary_apk_hashes:
            raise RuntimeError('ordinary APK hash snapshot empty')
        for alias, initial_sha in ordinary_apk_hashes.items():
            if ctx['installed_sha'](alias) != initial_sha:
                raise RuntimeError('ordinary APK hash drift: ' + alias)
        prepared = {
            'runId': RUN, 'sourceFingerprint': FIXED_FP, 'models': ctx['models'],
            'ordinaryUnchanged': True, 'sameUidProcReadable': True, 'nativeCalls': 0}
        _write(root, 'prepared.json', prepared)
        return prepared
    except BaseException as exc:
        if mutation_may_have_started:
            try:
                cleanup()
            except BaseException as secondary:
                note = type(secondary).__name__ + ': ' + str(secondary)
                if hasattr(exc, 'add_note'):
                    exc.add_note(note)
                else:
                    exc.__notes__ = getattr(exc, '__notes__', []) + [note]
        raise