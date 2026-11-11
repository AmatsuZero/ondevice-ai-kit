"""Frozen single-run KV288 emulator route; preserve all physical contracts."""
from pathlib import Path
import hashlib
import importlib.util
import json
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-kv288-emulator-20261111'
OLD = ROOT / 'artifacts/verification/w3-qwen-arena-emulator-20261111'
SERIAL = 'emulator-5582'
AVD = 'W3_Qwen_Kv288_20261111'
RUN = 'w3-qwen-kv288-emulator-20261111-01'
APP = 'io.ondeviceai.acceptance.qwenkv288'
RUNNER = APP + '.test/io.ondeviceai.acceptance.qwenfile.QwenKv288Instrumentation'


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def original_emulator():
    return load('kv288_original_emulator', OLD / 'device_acceptance_emulator.py')


def validate_emulator(serial, actual, qemu, console, user):
    assert serial == SERIAL
    assert actual == {'ro.product.model': 'Android SDK built for arm64',
                      'ro.build.version.sdk': '36', 'ro.product.cpu.abi': 'arm64-v8a'}
    assert qemu == '1' and user == '0'
    assert console.splitlines() == [AVD, 'OK']


def driver_source():
    # Existing adapter changes identity and Launcher restoration only; all65,
    # exclusive reservation and original resource stop conditions remain intact.
    return original_emulator().driver_source()


def policy_collector_source():
    path = ROOT / 'probes/android-qwen-heap/device_acceptance.py'
    source = path.read_text()
    old = "'files/w3-qwen-heap/report-'"
    assert source.count(old) == 2
    source = source.replace(old, "'files/w3-qwen-kv288/report-'")
    old = "ROOT/'probes/android-qwen-arena/verify_report.py'"
    assert source.count(old) == 1
    source = source.replace(old, "ROOT/'probes/android-qwen-kv288/verify_report.py'", 1)
    old = "normalized=dict(report,probeVariant='cpu-arena-off-v1')"
    assert source.count(old) == 1
    return source.replace(old, 'normalized=report', 1)


def verify_frozen_contract():
    original = original_emulator()
    prior = original.verify_frozen_contract()
    delivery = load('kv288_current_delivery', Path(__file__).with_name('check_delivery.py'))
    delivery.main()
    contract = json.loads((OUT / 'diagnostic-frozen.json').read_text())
    assert contract['runId'] == RUN and contract['emulatorSerial'] == SERIAL and contract['avdName'] == AVD
    assert contract['app'] == APP and contract['runner'] == RUNNER
    assert contract['supplementalEmulatorEvidenceOnly'] is True
    assert contract['independentSourceReviewAccepted'] is True and contract['independentParentReviewAccepted'] is True
    assert contract['automaticRetry'] is False and contract['nativePolicyEffectVerified'] is False
    files = contract['files']
    assert isinstance(files, dict) and files, 'required frozen artifact hashes absent'
    prefix = str(OUT.relative_to(ROOT)) + '/'
    required = {prefix + name for name in ('main.apk', 'test.apk', 'package-verified.json',
                'candidate-accepted.json', 'production-before.json', 'run-plan.json', 'source-review.json',
                'parent-review.json', 'source/test-assets/cases.json', 'host-green/result.json',
                'tools-suite-result.json', 'test.apk.runner-code.txt')}
    required |= {'probes/android-qwen-kv288/' + name for name in
                 ('QwenKv288Instrumentation.kt', 'device_acceptance.py', 'check_parent.py',
                  'check_delivery.py', 'verify_report.py', 'check_wrapper.py', 'prepare_build.py', 'build_acceptance.py')}
    required |= {'docs/superpowers/plans/2026-11-11-w3-qwen-kv288-emulator.md',
                 'docs/validation/w3-qwen-kv-capacity-preflight-20261111.json',
                 str((OLD / 'diagnostic-frozen.json').relative_to(ROOT)),
                 str((OLD / 'device-report.json').relative_to(ROOT)),
                 str((OLD / 'device-report-confirm.json').relative_to(ROOT)),
                 str((OLD / 'host-result.json').relative_to(ROOT)),
                 str((OLD / 'collection.json').relative_to(ROOT)),
                 str((OUT / 'source-review/dispatch.json').relative_to(ROOT))}
    required |= set(prior['files'])
    candidate = json.loads((OUT / 'candidate-accepted.json').read_text())
    required |= {prefix + 'source/' + rel for rel in candidate['sourceFiles']}
    assert required <= set(files), 'required frozen artifact hashes absent'
    for relative, wanted in files.items():
        target = (ROOT / relative).resolve()
        assert target.is_relative_to(ROOT.resolve()) and sha(target) == wanted, relative
    package = json.loads((OUT / 'package-verified.json').read_text())
    assert contract['sourceFingerprint'] == candidate['sourceFingerprint'] == package['sourceFingerprint']
    assert contract['prior512ReportSHA256'] == sha(OLD / 'device-report.json') == '696ee418d3de9c6fa7b51b8854ff163788e248495bb8d1d8728a826b1d5266b2'
    plan = json.loads((OUT / 'run-plan.json').read_text())
    expected = {'runId': RUN, 'sourceFingerprint': package['sourceFingerprint'],
                'caseCount': 65, 'maxNativeDecodeCalls': 130, 'maxTotalLen': 288, 'maxNewTokens': 128,
                'manifestSHA256': '8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
                'sampledRssStopAboveBytes': 1610612736, 'deviceAvailableStopBelowBytes': 536870912,
                'caseStopAboveSeconds': 120, 'automaticRetry': False, 'microphoneStarts': 0,
                'systemAsrRequests': 0, 'productionChanges': False, 'probeVariant': 'cpu-arena-off-kv288-v1', 'currentUser': '0'}
    for key, value in expected.items():
        assert type(plan.get(key)) is type(value) and plan[key] == value, key
    assert plan['order'] == [row['id'] for row in json.loads((OUT / 'source/test-assets/cases.json').read_text())['cases']]
    assert plan['order'] == json.loads((OLD / 'run-plan.json').read_text())['order']
    review = json.loads((OUT / 'parent-review.json').read_text())
    assert review['verdict'] == 'accept' and review['findings'] == []
    return contract


def emulator_adb(*args):
    remote = ['shell', shlex.join(args[1:])] if args and args[0] == 'shell' else list(args)
    return subprocess.run(['rtk', 'proxy', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'),
                           '-s', SERIAL, *remote], capture_output=True, check=True, timeout=30).stdout


def main(action):
    assert action in ('preflight', 'prepare', 'run', 'collect', 'collect-policy', 'restore')
    verify_frozen_contract()
    base = ROOT / 'probes/android-qwen-file/device_acceptance.py'
    namespace = {'__file__': str(base), '__name__': 'kv288_inherited_device_driver',
                 'SERIAL': SERIAL, 'validate_emulator': validate_emulator}
    exec(compile(driver_source(), str(base) + '[kv288-emulator-adapter]', 'exec'), namespace)
    namespace.update(OUT=OUT, APP=APP, TEST=APP + '.test', RUN=RUN,
                     STAGE='/data/local/tmp/' + RUN, REPORT='files/w3-qwen-kv288/report-' + RUN + '.json', RUNNER=RUNNER)
    namespace['main']('preflight' if action == 'collect-policy' else action)
    if action in ('collect', 'collect-policy'):
        # Only same-run bounded read collection; never calls the collector's main.
        path = ROOT / 'probes/android-qwen-heap/device_acceptance.py'
        state = {'__file__': str(path), '__name__': 'kv288_read_only_policy_collector'}
        exec(compile(policy_collector_source(), str(path) + '[kv288-policy-adapter]', 'exec'), state)
        state.update(OUT=OUT, RUN=RUN, APP=APP, adb=emulator_adb)
        state['collect_policy']()


if __name__ == '__main__':
    assert len(sys.argv) == 2
    main(sys.argv[1])
