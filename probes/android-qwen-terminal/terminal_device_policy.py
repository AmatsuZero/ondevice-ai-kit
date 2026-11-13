import json
import hashlib
import re
import shlex
import terminal_file_io as tf
from terminal_device_collect import APP, RUN, FIXED_FP, MODELS, REPORT, _load_local as _load_terminal_local, _verify_remote, _require
from terminal_device_restore import _read_json
from adb_terminal_observation import _preflight_outputs

POLICY = b"EnableCpuMemArena=0\n"
POLICY_SHA256 = hashlib.sha256(POLICY).hexdigest()
FRAMEWORK = "/data/data/" + APP + "/no_backup"
SHELL = "/data/user/0/" + APP + "/no_backup"
REL = "no_backup/cpu-arena-off/cpu.config"
_SUFFIX = REL.split("no_backup/", 1)[1]
_CONFIG_PATH_FRAMEWORK = FRAMEWORK + "/" + _SUFFIX
_CONFIG_PATH_SHELL = SHELL + "/" + _SUFFIX

_NEW_OUTPUT_RELS = {
    "allocator-policy-collected.json": "allocator-policy-collected.json",
    "allocator-policy-first.bin": "allocator-policy-first.bin",
    "allocator-policy-confirm.bin": "allocator-policy-confirm.bin",
}

def _policy_metadata(report):
    _require(report.get("nativePolicyEffectVerified") is False, "nativePolicyEffectVerified must be False")
    config = report.get("config")
    _require(isinstance(config, dict), "config must be dict")
    _require(config.get("requestedEnableCpuMemArena") is False, "requestedEnableCpuMemArena must be False")
    _require("allocatorPolicy" in report, "allocatorPolicy key missing")
    policy = report["allocatorPolicy"]
    if policy is None:
        _require("provider" in config, "null policy requires explicit provider key")
        _require(config["provider"] is None, "null policy requires provider None")
        _require(report.get("status") == "failed", "null policy requires failed status")
        return None
    _require(isinstance(policy, dict), "allocatorPolicy must be dict or null")
    _require(set(policy.keys()) == {"provider", "configPath", "bytes", "sha256"}, "allocatorPolicy exact keys required")
    config_path = policy["configPath"]
    _require(config_path == _CONFIG_PATH_FRAMEWORK or config_path == _CONFIG_PATH_SHELL, "unexpected configPath")
    _require(policy["provider"] == "cpu:" + config_path, "provider mismatch")
    _require(isinstance(policy["bytes"], int) and not isinstance(policy["bytes"], bool), "bytes must be exact int")
    _require(policy["bytes"] == 20, "bytes must be 20")
    _require(policy["sha256"] == POLICY_SHA256, "sha256 mismatch")
    _require(config.get("provider") == policy["provider"], "config.provider mismatch")
    return dict(policy)

def _load_policy_local(root, ctx, serial):
    _preflight_outputs(root, _NEW_OUTPUT_RELS)
    data = _load_terminal_local(root, ctx, serial, check_outputs=False)
    collection = _read_json(root, "collection.json")
    _require(isinstance(collection, dict), "collection.json must be dict")
    _require(isinstance(collection.get("schemaVersion"), int) and not isinstance(collection.get("schemaVersion"), bool) and collection.get("schemaVersion") == 1, "collection schemaVersion must be exact int 1")
    _require(collection.get("runId") == RUN, "collection runId mismatch")
    _require(collection.get("sourceFingerprint") == FIXED_FP, "collection sourceFingerprint mismatch")
    _require(collection.get("serial") == data["serial"], "collection serial mismatch")
    _require(collection.get("status") == data["report"]["status"], "collection status mismatch")
    _require(collection.get("sha256") == data["sha256"], "collection sha256 mismatch")
    for flag in ("reportPresent", "twoReadsIdentical", "terminalAccepted", "ordinaryUnchanged"):
        _require(collection.get(flag) is True, "receipt flag must be True: " + flag)
    for flag in ("qualityAccepted", "truePeakMeasured", "nativePolicyEffectVerified", "automaticRetry"):
        _require(collection.get(flag) is False, "receipt flag must be False: " + flag)
    _require(collection.get("errors") == [], "receipt errors must be empty")
    model_hashes = collection.get("modelHashesAfterMatch")
    _require(isinstance(model_hashes, dict), "modelHashesAfterMatch must be dict")
    _require(set(model_hashes.keys()) == set(MODELS), "modelHashesAfterMatch exact keys required")
    for name in MODELS:
        _require(model_hashes.get(name) is True, "model hash mismatch: " + name)
    raw = data["raw"]
    for rel in ("device-report.json", "device-report-confirm.json"):
        _require(tf.read_stable(root, rel, 1048576) == raw, "device report mismatch: " + rel)
    data["policy"] = _policy_metadata(data["report"])
    return data

# Fragment B — owns ONLY _policy_report_current, _collect_policy_file and tiny strict helpers.
# Globals supplied by A (do NOT redefine): json, hashlib, re, shlex, tf,
#   APP, RUN, FIXED_FP, MODELS, REPORT, POLICY, POLICY_SHA256, FRAMEWORK, SHELL, REL.

_UID_RE = re.compile(r'[1-9]\d*')
_STAT_RE = re.compile(r'^(\d+) (\d+) (\d+) (\d+)$')


def _shell_text(ctx, *tokens):
    return ctx['text']('shell', shlex.join(list(tokens)), timeout=2)


def _stat_fields(ctx, path):
    out = _shell_text(ctx, 'run-as', APP, 'stat', '-c', '%u %d %i %s', path)
    if len(out.encode('ascii', 'strict')) > 128:
        raise ValueError('stat output exceeds 128 ASCII bytes')
    m = _STAT_RE.match(out)
    if not m:
        raise ValueError('noncanonical stat output: ' + out)
    fields = []
    for tok in m.groups():
        if len(tok) > 1 and tok[0] == '0':
            raise ValueError('noncanonical leading zero in stat: ' + tok)
        fields.append(int(tok))
    uid, dev, ino, size = fields
    if uid < 0 or uid > 2 ** 32 - 1:
        raise ValueError('uid out of range')
    if dev < 0 or dev > 2 ** 64 - 1:
        raise ValueError('dev out of range')
    if ino <= 0 or ino > 2 ** 64 - 1:
        raise ValueError('ino out of range')
    if size < 0 or size > 2 ** 63 - 1:
        raise ValueError('size out of range')
    return (uid, dev, ino, size)


def _run_as_uid(ctx):
    out = _shell_text(ctx, 'run-as', APP, 'id', '-u').strip()
    if len(out.encode('ascii', 'strict')) > 20:
        raise ValueError('uid output too long')
    if not _UID_RE.fullmatch(out):
        raise ValueError('noncanonical uid: ' + out)
    uid = int(out)
    if uid > 2 ** 32 - 1:
        raise ValueError('uid overflow')
    return uid


def _require_dir(ctx, path):
    _shell_text(ctx, 'run-as', APP, 'test', '!', '-L', path)
    _shell_text(ctx, 'run-as', APP, 'test', '-d', path)


def _require_file(ctx, path):
    _shell_text(ctx, 'run-as', APP, 'test', '!', '-L', path)
    _shell_text(ctx, 'run-as', APP, 'test', '-f', path)


def _policy_report_current(ctx, data):
    actual = ctx['text']('shell', 'run-as', APP, 'sha256sum', REPORT, timeout=2)
    expected = data['sha256'] + '  ' + REPORT
    if actual != expected:
        raise ValueError('report sha256 mismatch')


def _collect_policy_file(root, ctx, data):
    uid = _run_as_uid(ctx)

    roots = ('no_backup', FRAMEWORK, SHELL)
    arenas = tuple(r + '/cpu-arena-off' for r in roots)
    files = tuple(r + '/cpu-arena-off/cpu.config' for r in roots)

    root_stats = [_stat_fields(ctx, p) for p in roots]
    arena_stats = [_stat_fields(ctx, p) for p in arenas]
    file_stats = [_stat_fields(ctx, p) for p in files]

    if len(set(root_stats)) != 1:
        raise ValueError('root alias records disagree')
    if len(set(arena_stats)) != 1:
        raise ValueError('arena alias records disagree')
    if len(set(file_stats)) != 1:
        raise ValueError('file alias records disagree')
    if any(s[0] != uid for s in root_stats + arena_stats + file_stats):
        raise ValueError('stat uid does not match run-as uid')
    if file_stats[0][3] != 20:
        raise ValueError('policy file size is not 20 bytes')
    if len({root_stats[0][2], arena_stats[0][2], file_stats[0][2]}) != 3:
        raise ValueError('group inode collision')

    for p in roots:
        _require_dir(ctx, p)
    for p in arenas:
        _require_dir(ctx, p)
    for p in files:
        _require_file(ctx, p)

    initial = {'root': list(root_stats), 'arena': list(arena_stats), 'file': list(file_stats)}

    first = ctx['call']('exec-out', 'run-as', APP, 'head', '-c', '21', REL, timeout=2).stdout
    tf.write_new_durable(root, 'allocator-policy-first.bin', first)

    second = ctx['call']('exec-out', 'run-as', APP, 'head', '-c', '21', REL, timeout=2).stdout
    tf.write_new_durable(root, 'allocator-policy-confirm.bin', second)

    if not isinstance(first, bytes) or not isinstance(second, bytes):
        raise ValueError('raw read did not return bytes')
    if len(first) > 21 or len(second) > 21:
        raise ValueError('raw read exceeded 21 bytes')
    if first != second:
        raise ValueError('two raw reads are not identical')
    if first != POLICY:
        raise ValueError('raw bytes do not match POLICY')
    if hashlib.sha256(first).hexdigest() != POLICY_SHA256:
        raise ValueError('raw bytes sha256 does not match POLICY_SHA256')

    for p in roots:
        _require_dir(ctx, p)
    for p in arenas:
        _require_dir(ctx, p)
    for p in files:
        _require_file(ctx, p)

    rs = [_stat_fields(ctx, p) for p in roots]
    ar = [_stat_fields(ctx, p) for p in arenas]
    fs = [_stat_fields(ctx, p) for p in files]
    if rs != initial['root'] or ar != initial['arena'] or fs != initial['file']:
        raise ValueError('identity drift after reads')

    return {
        'receiptMatches': True,
        'fileBytesMatch': True,
        'twoReadsIdentical': True,
        'privateRoot': data['policy']['configPath'].removesuffix('/cpu-arena-off/cpu.config'),
        'readLimitBytes': 21,
        'boundedReadBytes': 20,
        'boundedReadSHA256': POLICY_SHA256,
        'directoryIdentity': list(initial['root'][0]),
        'arenaDirectoryIdentity': list(initial['arena'][0]),
        'fileIdentity': list(initial['file'][0]),
    }


def collect_policy(root, ctx, *, serial):
    data = _load_policy_local(root, ctx, serial)
    receipt = {
        'schemaVersion': 1,
        'runId': RUN,
        'sourceFingerprint': FIXED_FP,
        'serial': data['serial'],
        'reportSHA256': data['sha256'],
        'policyPresent': data['policy'] is not None,
        'privateRoot': None,
        'receiptMatches': False,
        'fileBytesMatch': False,
        'twoReadsIdentical': False,
        'ordinaryUnchanged': False,
        'errors': [],
        'nativePolicyEffectVerified': False,
        'qualityAccepted': False,
        'truePeakMeasured': False,
        'automaticRetry': False,
    }
    final_write_started = False
    try:
        _verify_remote(ctx, data)
        _policy_report_current(ctx, data)
        if data['policy'] is not None:
            receipt.update(_collect_policy_file(root, ctx, data))
        _verify_remote(ctx, data)
        _policy_report_current(ctx, data)
        receipt['ordinaryUnchanged'] = True
        final_write_started = True
        tf.write_new_durable(root, 'allocator-policy-collected.json', json.dumps(receipt, sort_keys=True, allow_nan=False, separators=(',', ':')).encode())
        return receipt
    except BaseException as primary:
        if final_write_started:
            raise
        receipt['receiptMatches'] = False
        receipt['fileBytesMatch'] = False
        receipt['errors'].append({'class': type(primary).__name__, 'message': str(primary)})
        try:
            tf.write_new_durable(root, 'allocator-policy-collected.json', json.dumps(receipt, sort_keys=True, allow_nan=False, separators=(',', ':')).encode())
        except BaseException as secondary:
            primary.add_note(type(secondary).__name__ + ': ' + str(secondary))
        raise
