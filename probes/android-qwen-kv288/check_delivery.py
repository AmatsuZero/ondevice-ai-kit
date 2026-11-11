"""Check current immutable KV288 host/source/package receipts without device calls."""
from pathlib import Path
import hashlib
import importlib.util
import json

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-kv288-emulator-20261111'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    wrapper = ROOT / 'probes/android-qwen-kv288/QwenKv288Instrumentation.kt'
    candidate = json.loads((OUT / 'candidate-accepted.json').read_text())
    host = json.loads((OUT / 'host-green/result.json').read_text())
    package = json.loads((OUT / 'package-verified.json').read_text())
    review = json.loads((OUT / 'source-review.json').read_text())
    assert host['compileExitCode'] == host['runtimeExitCode'] == 0
    assert host['sourceSHA256'][str(wrapper)] == sha(wrapper)
    assert 'ASSERTIONS=6003 FAILURES=0' in host['output']
    assert 'FAILURES=16' in json.loads((OUT / 'host-red/result.json').read_text())['output']
    assert review['verdict'] == 'accept' and review['findings'] == []
    dispatch = json.loads((OUT / 'source-review/dispatch.json').read_text())
    for relative, wanted in dispatch['files'].items():
        assert sha(ROOT / relative) == wanted, relative
    assert candidate['sourceFiles']
    fingerprint = hashlib.sha256(json.dumps(candidate['sourceFiles'], sort_keys=True,
                                           separators=(',', ':')).encode()).hexdigest()
    assert fingerprint == candidate['sourceFingerprint'] == package['sourceFingerprint']
    for relative, wanted in candidate['sourceFiles'].items():
        assert sha(OUT / 'source' / relative) == wanted, relative
    assert candidate['sourceFiles']['test-src/QwenKv288Instrumentation.kt'] == sha(wrapper)
    original = ROOT / 'artifacts/verification/w3-qwen-arena-wrapper-20261111'
    old = json.loads((original / 'candidate-accepted.json').read_text())
    for relative, wanted in old['sourceFiles'].items():
        assert sha(original / 'source' / relative) == wanted, relative
        if relative not in ('build.gradle.kts', 'AndroidManifest.xml', 'test-src/QwenArenaInstrumentation.kt'):
            assert candidate['sourceFiles'][relative] == wanted, relative
    assert package['runtimeLibraries'] == json.loads((original / 'package-verified.json').read_text())['runtimeLibraries']
    for name, alias in [('main.apk', 'io.ondeviceai.acceptance.qwenkv288'),
                        ('test.apk', 'io.ondeviceai.acceptance.qwenkv288.test')]:
        assert sha(OUT / name) == package['files'][name]['sha256']
        assert package['files'][name]['package'] == alias and package['permissions'][name] == []
    assert package['actualTestAssetsVerified'] == 65 and len(package['runtimeLibraries']) == 4
    assert package['exactRunnerVerified'] == 'io.ondeviceai.acceptance.qwenfile.QwenKv288Instrumentation'
    assert json.loads((OUT / 'build-result.json').read_text())['exitCode'] == 0
    lint = (OUT / 'source/build/reports/lint-results-debug.xml').read_text()
    assert 'severity="Error"' not in lint
    dex = (OUT / 'test.apk.runner-code.txt').read_text()
    assert 'const/16 v50, 0x120' in dex and 'const/16 v51, 0x80' in dex
    assert 'const/16 v4, 0x120' in dex and 'const-string v2, "maxTotalLen"' in dex
    for name in ('arena', 'heap'):
        path = ROOT / ('probes/android-qwen-' + name + '/device_acceptance.py')
        spec = importlib.util.spec_from_file_location('guard_' + name, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.verify_frozen_contract()
        if name == 'heap':
            assert all(not (module.OUT / n).exists() for n in ('prepared.json', 'run-started.json', 'collection.json'))
    assert json.loads((OUT / 'tools-suite-result.json').read_text())['exitCode'] == 0
    print(json.dumps({'accepted': True, 'sourceFingerprint': fingerprint,
                      'hostGroups': 32, 'hostAssertions': 6003, 'packageAssets': 65,
                      'runtimeLibraries': 4, 'newDeviceCalls': 0, 'physicalHeapRunConsumed': False}))


if __name__ == '__main__':
    main()
