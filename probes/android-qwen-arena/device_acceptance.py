"""Parent-only distinct diagnostic routing; reuses immutable FILE device guards."""
from pathlib import Path
import hashlib
import importlib.util
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-arena-wrapper-20261111'
APP = 'io.ondeviceai.acceptance.qwenarena'
RUN = 'w3-qwen-arena-20261111-01'
RUNNER = APP + '.test/io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation'


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


baseline = load('frozen_file_device_guards', ROOT / 'probes/android-qwen-file/device_acceptance.py')
verifier = load('arena_policy_report_binding', Path(__file__).with_name('verify_report.py'))
budget_reason = baseline.budget_reason


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_frozen_contract(path=OUT / 'diagnostic-frozen.json'):
    contract = json.loads(path.read_text())
    assert contract['runId'] == RUN and contract['app'] == APP and contract['runner'] == RUNNER
    assert contract['independentSourceReviewAccepted'] is True
    assert contract['independentParentReviewAccepted'] is True
    assert contract['nativePolicyEffectVerified'] is False
    assert contract['automaticRetry'] is False
    prefix = str(OUT.relative_to(ROOT)) + '/'
    required = {
        prefix + name for name in ('main.apk', 'test.apk', 'package-verified.json',
                                  'candidate-accepted.json', 'run-plan.json', 'production-before.json')
    } | {
        'probes/android-qwen-arena/device_acceptance.py',
        'probes/android-qwen-arena/verify_report.py',
        'probes/android-qwen-arena/check_parent.py',
        'probes/android-qwen-file/device_acceptance.py',
        'probes/android-qwen-file/verify_report.py',
        'probes/android-qwen-file/check_parent.py',
        'probes/android-qwen-arena/QwenArenaInstrumentation.kt',
        'probes/android-qwen-arena/QwenArenaPolicy.kt',
        'probes/android-qwen-file/QwenFileActivity.kt',
        'docs/validation/w3-qwen-arena-wrapper-review-20261111.json',
        'docs/validation/w3-qwen-arena-parent-review-20261111.json',
        'docs/validation/w3-qwen-arena-diagnostic-protocol-20261111.md',
    }
    assert required <= set(contract['files']), 'required frozen artifact hashes absent'
    for rel, sha in contract['files'].items():
        target = (ROOT / rel).resolve()
        assert target.is_relative_to(ROOT.resolve()) and digest(target) == sha, rel
    plan = json.loads((OUT / 'run-plan.json').read_text())
    package = json.loads((OUT / 'package-verified.json').read_text())
    expected = {'runId': RUN, 'sourceFingerprint': package['sourceFingerprint'],
                'probeVariant': 'cpu-arena-off-v1', 'caseCount': 65, 'maxNativeDecodeCalls': 130,
                'manifestSHA256': '8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
                'sampledRssStopAboveBytes': 1610612736, 'deviceAvailableStopBelowBytes': 536870912,
                'caseStopAboveSeconds': 120, 'automaticRetry': False, 'microphoneStarts': 0,
                'systemAsrRequests': 0, 'productionChanges': False, 'currentUser': '0'}
    for key, value in expected.items():
        assert type(plan.get(key)) is type(value) and plan[key] == value, key
    assert plan['order'] == [row['id'] for row in json.loads((OUT / 'source/test-assets/cases.json').read_text())['cases']]
    return contract


def adb(*args):
    target = ROOT / '.omx/artifacts/w3-model-reselection-20261110/android-target-local.json'
    serial = json.loads(target.read_text())['serial']
    return subprocess.run(['rtk', 'proxy', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'),
                           '-s', serial, *args], capture_output=True, check=True, timeout=30).stdout


def collect_policy():
    raw = OUT / 'device-report.json'
    result = {'privateRoot': None, 'receiptMatches': False, 'fileBytesMatch': False,
              'nativePolicyEffectVerified': False}
    if raw.is_file():
        report = json.loads(raw.read_text())
        if report.get('allocatorPolicy') is not None:
            private_root = adb('shell', 'run-as', APP, 'readlink', '-f', 'no_backup').decode().strip()
            assert private_root == '/data/user/0/' + APP + '/no_backup', private_root
            result['privateRoot'] = private_root
            result['receiptMatches'] = verifier.policy_matches(report, private_root)
            if result['receiptMatches']:
                actual = adb('exec-out', 'run-as', APP, 'head', '-c', '21', 'no_backup/cpu-arena-off/cpu.config')
                result['fileBytesMatch'] = actual == verifier.POLICY_BYTES
                result['boundedReadSHA256'] = hashlib.sha256(actual).hexdigest()
                result['boundedReadBytes'] = len(actual)
                result['readLimitBytes'] = 21
    with (OUT / 'allocator-policy-collected.json').open('x') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(result))


def verify_prior_collection():
    collection = json.loads((OUT / 'collection.json').read_text())
    raw = (OUT / 'device-report.json').read_bytes()
    package = json.loads((OUT / 'package-verified.json').read_text())
    report = json.loads(raw)
    assert collection['reportPresent'] is True and collection['twoReadsIdentical'] is True
    assert raw == (OUT / 'device-report-confirm.json').read_bytes()
    assert hashlib.sha256(raw).hexdigest() == collection['sha256']
    assert report['runId'] == RUN and report['sourceFingerprint'] == package['sourceFingerprint']
    assert len(collection['modelHashesAfterMatch']) == 6 and all(collection['modelHashesAfterMatch'].values())
    for name, app in [('main.apk', APP), ('test.apk', APP + '.test')]:
        path = adb('shell', 'pm', 'path', app).decode().strip().removeprefix('package:')
        assert path.startswith('/data/app/') and path.endswith('/base.apk') and '\n' not in path
        assert adb('shell', 'sha256sum', path).decode().split()[0] == package['files'][name]['sha256']
    remote_report = 'files/w3-qwen-arena/report-' + RUN + '.json'
    assert adb('shell', 'run-as', APP, 'sha256sum', remote_report).decode().split()[0] == collection['sha256']


def main(action):
    assert action in ('preflight', 'prepare', 'run', 'collect', 'collect-policy', 'restore')
    verify_frozen_contract()
    assert adb('shell', 'am', 'get-current-user').decode().strip() == '0'
    baseline.OUT = OUT
    baseline.APP = APP
    baseline.TEST = APP + '.test'
    baseline.RUN = RUN
    baseline.STAGE = '/data/local/tmp/' + RUN
    baseline.REPORT = 'files/w3-qwen-arena/report-' + RUN + '.json'
    baseline.RUNNER = RUNNER
    if action == 'collect-policy':
        # Resume only the bounded read after baseline collection has completed;
        # never repeat instrumentation or overwrite already collected evidence.
        verify_prior_collection()
        baseline.main('preflight')
        collect_policy()
        return
    baseline.main(action)
    if action == 'collect':
        collect_policy()


if __name__ == '__main__':
    main(sys.argv[1])
