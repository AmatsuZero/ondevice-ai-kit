"""Parent-only arena metadata binding plus unchanged FILE coverage/quality checks."""
from pathlib import Path
import copy
import hashlib
import importlib.util
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-arena-wrapper-20261111'
POLICY_BYTES = b'EnableCpuMemArena=0\n'
spec = importlib.util.spec_from_file_location('frozen_file_report_evaluator', ROOT / 'probes/android-qwen-file/verify_report.py')
baseline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline)


def policy_matches(report, private_root):
    receipt = report.get('allocatorPolicy')
    config = report.get('config') or {}
    if not isinstance(receipt, dict) or not isinstance(private_root, str):
        return False
    path = private_root + '/cpu-arena-off/cpu.config'
    return (report.get('probeVariant') == 'cpu-arena-off-v1'
            and report.get('nativePolicyEffectVerified') is False
            and set(receipt) == {'provider', 'configPath', 'bytes', 'sha256'}
            and receipt.get('configPath') == path
            and receipt.get('provider') == 'cpu:' + path
            and type(receipt.get('bytes')) is int and receipt['bytes'] == len(POLICY_BYTES)
            and receipt.get('sha256') == hashlib.sha256(POLICY_BYTES).hexdigest()
            and config.get('provider') == receipt['provider']
            and config.get('requestedEnableCpuMemArena') is False)


def evaluate(report, host, manifest, source_fingerprint, private_root):
    # The baseline's sole provider literal is adapted in a deep copy. The raw
    # report remains immutable and its actual provider is independently bound
    # to the exact receipt/path/hash below before execution can be accepted.
    normalized = copy.deepcopy(report)
    normalized.setdefault('config', {})['provider'] = 'cpu'
    result = baseline.evaluate(normalized, host, manifest, source_fingerprint)
    result['checks']['allocatorRequestedPolicy'] = policy_matches(report, private_root)
    result['executionAccepted'] = all(result['checks'].values())
    result['engineeringQualityAccepted'] = result['executionAccepted'] and result['engineeringQualityAccepted']
    result['nativePolicyEffectVerified'] = False
    result['baseEvaluatorProviderAdaptation'] = 'deep copy only; actual provider bound to exact requested-policy receipt'
    return result


def main(root=OUT, output_name='parent-arena-acceptance.json'):
    assert re.fullmatch(r'parent-arena-acceptance(?:-[a-z0-9-]+)?\.json', output_name)
    report_path = root / 'device-report.json'
    collection_path = root / 'allocator-policy-collected.json'
    if not report_path.is_file() or not collection_path.is_file():
        result = {'executionAccepted': False, 'engineeringQualityAccepted': False,
                  'reason': ('no device report collected' if not report_path.is_file()
                             else 'policy collection absent; resume same run collection'),
                  'actualDecodeCallsObserved': None,
                  'countsAreExactTerminalEvidence': False, 'completedConsistentRows': 0,
                  'nativePolicyEffectVerified': False, 'productionReady': False}
    else:
        raw = report_path.read_bytes()
        collection = json.loads(collection_path.read_text())
        result = evaluate(json.loads(raw), json.loads((root / 'host-result.json').read_text()),
                          json.loads((root / 'source/test-assets/cases.json').read_text()),
                          json.loads((root / 'package-verified.json').read_text())['sourceFingerprint'],
                          collection.get('privateRoot'))
        result['checks']['policyFileCollectedExactly'] = collection.get('fileBytesMatch') is True
        result['executionAccepted'] = all(result['checks'].values())
        result['engineeringQualityAccepted'] = result['executionAccepted'] and result['engineeringQualityAccepted']
        result['reportSHA256'] = hashlib.sha256(raw).hexdigest()
    with (root / output_name).open('x') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps({key: result[key] for key in ('executionAccepted', 'engineeringQualityAccepted', 'nativePolicyEffectVerified')}))


if __name__ == '__main__':
    assert len(sys.argv) <= 2
    main(output_name=sys.argv[1] if len(sys.argv) == 2 else 'parent-arena-acceptance.json')
