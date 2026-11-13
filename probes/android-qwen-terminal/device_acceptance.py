from pathlib import Path
import sys, json, re, shlex

_HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(_HERE))
sys.path.insert(0, str(_HERE.parent / 'android-terminal-observation'))

import terminal_device_prepare as _prepare
import terminal_device_restore as _restore
import terminal_device_collect as _collect
import terminal_device_policy as _policy
import terminal_device_lifecycle as _lifecycle
import terminal_device_contract as _contract
from terminal_source_adapter import adapt_source
from adb_terminal_observation import _preflight_outputs

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-terminal-20261112'
EVIDENCE = ROOT / '.omx/artifacts/w3-model-reselection-20261110'
APP = 'io.ondeviceai.acceptance.qwenterminal'
TEST = APP + '.test'
RUN = 'w3-qwen-terminal-20261112-01'
STAGE = '/data/local/tmp/' + RUN
REPORT = 'files/w3-qwen-terminal/report-' + RUN + '.json'
RUNNER = TEST + '/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation'
BASE = ROOT / 'probes/android-qwen-file/device_acceptance.py'
FP = 'ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5'

_VALID_ACTIONS = ('preflight', 'prepare', 'run', 'collect', 'collect-policy', 'restore')


def verify_frozen_contract():
    return _contract.verify_frozen_contract(ROOT, OUT)


def driver_source():
    return adapt_source(BASE.read_text(encoding='utf-8'))


def adb():
    return str(Path.home() / 'Library/Android/sdk/platform-tools/adb')


def _cleanup_once(root, ctx, *, serial):
    state = {'attempted': False}

    def closure():
        if state['attempted']:
            raise RuntimeError('cleanup already attempted; refusing retry')
        state['attempted'] = True
        result = _restore.restore(root, ctx, serial=serial)
        if type(result) is not dict or result.get('allRestored') is not True:
            raise RuntimeError('cleanup restore did not confirm allRestored: ' + repr(result))
        return result

    return closure


def _run_consumed_fresh_marker():
    try:
        marker = _restore._read_json(OUT, 'run-started.json')
    except BaseException as exc:
        return (False, 'run-started.json read error: ' + type(exc).__name__ + ': ' + str(exc))
    if type(marker) is not dict:
        return (False, 'run-started.json is not a JSON object')
    checks = [
        ('runId', marker.get('runId') == RUN),
        ('sourceFingerprint', marker.get('sourceFingerprint') == FP),
        ('startedNs', type(marker.get('startedNs')) is int and 0 <= marker.get('startedNs') <= 2**63 - 1),
        ('automaticRetry', marker.get('automaticRetry') is False),
        ('plannedConstructors', type(marker.get('plannedConstructors')) is int and marker.get('plannedConstructors') == 1),
        ('maxPlannedStreams', type(marker.get('maxPlannedStreams')) is int and marker.get('maxPlannedStreams') == 2),
        ('maxPlannedDecode', type(marker.get('maxPlannedDecode')) is int and marker.get('maxPlannedDecode') == 2),
        ('plannedMicrophone', type(marker.get('plannedMicrophone')) is int and marker.get('plannedMicrophone') == 0),
        ('inputCount', type(marker.get('inputCount')) is int and marker.get('inputCount') == 1),
    ]
    for name, ok in checks:
        if not ok:
            return (False, 'run-started.json field mismatch: ' + name)
    return (True, None)


def _dispatch_context(action, ctx):
    if type(action) is not str or action not in _VALID_ACTIONS:
        raise ValueError('invalid action: ' + repr(action))
    serial = ctx.get('serial')
    if type(serial) is not str or not serial:
        raise ValueError('ctx missing valid serial')
    if action == 'preflight':
        return _preflight_context(OUT, ctx, serial=serial)
    if action == 'prepare':
        closure = _cleanup_once(OUT, ctx, serial=serial)
        return _prepare.prepare(OUT, ctx, serial=serial, cleanup=closure)
    if action == 'collect':
        return _collect.collect(OUT, ctx, serial=serial)
    if action == 'collect-policy':
        return _policy.collect_policy(OUT, ctx, serial=serial)
    if action == 'restore':
        return _restore.restore(OUT, ctx, serial=serial)
    if action == 'run':
        _preflight_run_outputs(OUT)
        try:
            return _lifecycle.run_diagnostic(OUT, adb=adb(), serial=serial, source_fingerprint=FP, verify_launch=lambda: _verify_launch(OUT, ctx, serial=serial), before_transport=verify_frozen_contract, stop_alias=lambda: _stop_owned_alias(OUT, ctx, serial=serial))
        except BaseException as exc:
            consumed, reason = _run_consumed_fresh_marker()
            if consumed:
                try:
                    _collect.collect(OUT, ctx, serial=serial)
                except BaseException as secondary:
                    exc.add_note(type(secondary).__name__ + ': ' + str(secondary))
                try:
                    _policy.collect_policy(OUT, ctx, serial=serial)
                except BaseException as secondary:
                    exc.add_note(type(secondary).__name__ + ': ' + str(secondary))
                try:
                    _cleanup_once(OUT, ctx, serial=serial)()
                except BaseException as secondary:
                    exc.add_note(type(secondary).__name__ + ': ' + str(secondary))
            else:
                exc.add_note('no valid fresh run marker consumed (' + str(reason) + '); skipping destructive cleanup to preserve evidence')
            raise


def main(action):
    if type(action) is not str or action not in _VALID_ACTIONS:
        raise ValueError('invalid action: ' + repr(action))
    verify_frozen_contract()
    source = driver_source()
    ns = {'__name__': 'terminal_adapted_driver', '__file__': str(BASE)}
    ns['_terminal_before_transport'] = verify_frozen_contract

    def _terminal_dispatch(disp_action, actual_locals):
        ctx = {k: actual_locals[k] for k in ('call', 'text', 'exists', 'identity', 'gate', 'ordinary', 'ordinary_models', 'installed_sha', 'verify_installed', 'transfer', 'package', 'source', 'plan', 'manifest', 'models', 'serial', 'current', 'adb')}
        ctx['digest'] = ns['digest']
        ctx['parse_kb'] = ns['parse_kb']
        return _dispatch_context(disp_action, ctx)

    ns['_terminal_dispatch'] = _terminal_dispatch
    exec(compile(source, str(BASE), 'exec'), ns)
    for name in ('ROOT', 'OUT', 'EVIDENCE', 'APP', 'TEST', 'RUN', 'STAGE', 'REPORT', 'RUNNER'):
        ns[name] = globals()[name]
    return ns['main'](action)

def _require_not_link(ctx, path, app=False):
    if app:
        base = ['shell', 'run-as', APP, 'test', '-L', path]
    else:
        base = ['shell', 'test', '-L', path]
    if ctx['call'](*base, accepted=(0, 1)).returncode == 0:
        raise AssertionError('symlink present (reject dangling link): ' + path)

def _require_remote_absent(ctx, relative):
    parts = relative.split('/')
    for i in range(len(parts) - 1):
        ancestor = '/'.join(parts[:i + 1])
        _require_not_link(ctx, ancestor, app=True)
        if ctx['call']('shell', 'run-as', APP, 'test', '-e', ancestor, accepted=(0, 1)).returncode == 0:
            if ctx['call']('shell', 'run-as', APP, 'test', '-d', ancestor, accepted=(0, 1)).returncode != 0:
                raise AssertionError('remote ancestor is not a directory: ' + ancestor)
    _require_not_link(ctx, relative, app=True)
    if ctx['call']('shell', 'run-as', APP, 'test', '-e', relative, accepted=(0, 1)).returncode == 0:
        raise AssertionError('remote output already present: ' + relative)

def _validate_plan(ctx):
    plan = ctx.get('plan')
    if type(plan) is not dict:
        raise ValueError('ctx missing plan binding')
    if plan.get('runId') != RUN:
        raise ValueError('plan runId mismatch')
    if plan.get('sourceFingerprint') != FP:
        raise ValueError('plan sourceFingerprint mismatch')
    if type(plan.get('caseCount')) is not int or plan.get('caseCount') != 1:
        raise ValueError('plan caseCount must be int 1')
    if type(plan.get('maxNativeDecodeCalls')) is not int or plan.get('maxNativeDecodeCalls') != 2:
        raise ValueError('plan maxNativeDecodeCalls must be int 2')

def _preflight_run_outputs(root):
    local = {
        'run_started': 'run-started.json',
        'instr_log': 'instrumentation.log',
        'host_result': 'host-result.json',
        'startup_start': 'startup-start.json',
        'startup_report': 'startup-report.json',
        'startup_observed': 'startup-observed.json',
        'poll_start': 'poll-start.json',
        'terminal_observed': 'terminal-observed.json',
        'terminal_receipt': 'terminal-receipt.json',
        'terminal_ack': 'terminal-ack.txt',
        'terminal_before_link': 'terminal-before-link.json',
        'policy_collected': 'allocator-policy-collected.json',
        'policy_first': 'allocator-policy-first.bin',
        'policy_confirm': 'allocator-policy-confirm.bin',
        'restoration': 'restoration.json',
        'stage_recovered': 'ownership-stage-recovered.json',
        'collection': 'collection.json',
        'device_report': 'device-report.json',
        'device_report_confirm': 'device-report-confirm.json',
    }
    _preflight_outputs(root, local)
    root_path = Path(root)
    for entry in root_path.iterdir():
        if re.fullmatch(r'poll-sample-[0-9]{6}\.json', entry.name):
            raise OSError('stale poll snapshot present: ' + entry.name)

def _preflight_context(root, ctx, *, serial):
    _prepare._preflight(root)
    _validate_plan(ctx)
    _restore._validate_bindings(ctx, serial)
    _prepare._validate_inputs(ctx, Path(root))
    _preflight_run_outputs(root)
    if ctx['text']('shell', 'am', 'get-current-user') != '0':
        raise ValueError('current user is not 0')
    identity = ctx['identity']()
    gate = ctx['gate']()
    available = ctx['parse_kb'](ctx['text']('shell', 'cat', '/proc/meminfo'), 'MemAvailable')
    if available < 2 * 1024 * 1024 * 1024:
        raise ValueError('MemAvailable below 2GiB')
    if ctx['text']('shell', 'pm', 'list', 'packages', APP):
        raise ValueError('APP alias already present')
    if ctx['text']('shell', 'pm', 'list', 'packages', TEST):
        raise ValueError('TEST alias already present')
    for ancestor in ('/data', '/data/local', '/data/local/tmp'):
        if ctx['call']('shell', 'test', '-L', ancestor, accepted=(0, 1)).returncode == 0:
            raise AssertionError('staging ancestor is a symlink: ' + ancestor)
        if ctx['call']('shell', 'test', '-e', ancestor, accepted=(0, 1)).returncode != 0:
            raise AssertionError('staging ancestor absent: ' + ancestor)
        if ctx['call']('shell', 'test', '-d', ancestor, accepted=(0, 1)).returncode != 0:
            raise AssertionError('staging ancestor is not a directory: ' + ancestor)
    if (ctx['call']('shell', 'test', '-e', STAGE, accepted=(0, 1)).returncode == 0 or
            ctx['call']('shell', 'test', '-L', STAGE, accepted=(0, 1)).returncode == 0):
        raise AssertionError('staging path present or dangling symlink')
    ordinary = ctx['ordinary']()
    ordinary_models = ctx['ordinary_models']()
    ordinary_apk_hashes = {alias: ctx['installed_sha'](alias) for alias in ordinary}
    return {
        'currentUser': '0',
        'availableBytes': available,
        'identity': identity,
        'gate': gate,
        'ordinary': ordinary,
        'ordinaryModels': ordinary_models,
        'ordinaryAPKHashes': ordinary_apk_hashes,
        'stage': STAGE,
    }

def _verify_launch(root, ctx, *, serial):
    root = Path(root)
    _restore._validate_bindings(ctx, serial)
    _prepare._validate_inputs(ctx, Path(root))
    _validate_plan(ctx)
    prepared = _restore._read_json(root, 'prepared.json')
    if type(prepared) is not dict:
        raise ValueError('prepared.json must be a JSON object')
    if prepared.get('runId') != RUN:
        raise ValueError('prepared runId mismatch')
    if prepared.get('sourceFingerprint') != FP:
        raise ValueError('prepared sourceFingerprint mismatch')
    if type(prepared.get('models')) is not dict or prepared['models'] != ctx['models']:
        raise ValueError('prepared models mismatch')
    if prepared.get('ordinaryUnchanged') is not True:
        raise ValueError('prepared ordinaryUnchanged must be exactly True')
    if prepared.get('sameUidProcReadable') is not True:
        raise ValueError('prepared sameUidProcReadable must be exactly True')
    if type(prepared.get('nativeCalls')) is not int or prepared['nativeCalls'] != 0:
        raise ValueError('prepared nativeCalls must be int 0')
    locals_info = _restore._read_locals(root, serial, ctx)
    if locals_info.get('stage_claim') is None:
        raise ValueError('missing durable stage claim')
    if locals_info.get('stage_created') is None:
        raise ValueError('missing durable stage created claim')
    install_claims = locals_info.get('install_claims', {})
    if APP not in install_claims:
        raise ValueError('missing durable install-main claim')
    if TEST not in install_claims:
        raise ValueError('missing durable install-test claim')
    _preflight_run_outputs(root)
    _collect._verify_remote(ctx, {'before': locals_info, 'models': ctx['models']})
    runner_expected = 'instrumentation:' + RUNNER + ' (target=' + APP + ')'
    instr = ctx['text']('shell', 'pm', 'list', 'instrumentation', APP)
    if runner_expected not in instr.splitlines():
        raise RuntimeError('runner component missing from instrumentation list')
    available = ctx['parse_kb'](ctx['text']('shell', 'cat', '/proc/meminfo'), 'MemAvailable')
    if available < 2 * 1024 * 1024 * 1024:
        raise ValueError('MemAvailable below 2GiB')
    if ctx['parse_kb'](ctx['text']('shell', 'run-as', APP, 'cat', '/proc/self/status'), 'VmRSS') <= 0:
        raise RuntimeError('same-uid process /proc/self/status VmRSS not readable')
    _require_remote_absent(ctx, REPORT)
    _require_remote_absent(ctx, 'files/w3-qwen-terminal/ack-' + RUN + '.txt')
    _require_remote_absent(ctx, 'files/w3-qwen-terminal/ack-' + RUN + '.txt.stage')
    return None

def _stop_owned_alias(root, ctx, *, serial):
    root = Path(root)
    _restore._validate_bindings(ctx, serial)
    _validate_plan(ctx)
    locals_info = _restore._read_locals(root, serial, ctx)
    install_claims = locals_info.get('install_claims', {})
    if APP not in install_claims:
        raise ValueError('missing durable install-main claim for owned alias')
    claim = install_claims[APP]
    frozen_sha = ctx['package']['files']['main.apk']['sha256']
    if claim.get('sha256') != frozen_sha:
        raise ValueError('install claim sha256 does not match frozen package main.apk')
    expected = {'ro.product.model': 'TECNO KM9', 'ro.build.version.sdk': '35', 'ro.product.cpu.abi': 'arm64-v8a'}
    actual = {k: ctx['text']('shell', 'getprop', k) for k in expected}
    if actual != expected:
        raise ValueError('device identity drift before stop: ' + str(actual))
    if ctx['text']('shell', 'am', 'get-current-user') != '0':
        raise ValueError('current user is not 0')
    installed = ctx['installed_sha'](APP)
    if installed != claim['sha256']:
        raise ValueError('installed APP hash does not match claim; refusing stop of foreign/replaced APK')
    ctx['call']('shell', 'am', 'force-stop', APP)

if __name__ == "__main__":
    main(sys.argv[1])
