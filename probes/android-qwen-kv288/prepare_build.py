"""Prepare a distinct KV288 source tree after actual candidate host acceptance."""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-kv288-emulator-20261111'
OLD = ROOT / 'artifacts/verification/w3-qwen-arena-wrapper-20261111'
SOURCE = OUT / 'source'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    candidate = ROOT / 'probes/android-qwen-kv288/QwenKv288Instrumentation.kt'
    host = json.loads((OUT / 'host-green/result.json').read_text())
    assert host['compileExitCode'] == host['runtimeExitCode'] == 0
    assert host['sourceSHA256'][str(candidate)] == sha(candidate)
    integration = json.loads((OUT / 'helper-wrapper/integration.json').read_text())
    assert integration['candidateSHA256'] == sha(candidate)
    assert integration['mechanicallyIntegratedExactEdits'] and integration['toolRequests'] == 0
    old = json.loads((OLD / 'candidate-accepted.json').read_text())
    for relative, wanted in old['sourceFiles'].items():
        assert sha(OLD / 'source' / relative) == wanted, relative
    assert not SOURCE.exists()
    SOURCE.mkdir()
    for relative in old['sourceFiles']:
        if relative == 'test-src/QwenArenaInstrumentation.kt':
            continue
        destination = SOURCE / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(OLD / 'source' / relative, destination)
    gradle = SOURCE / 'build.gradle.kts'
    text = gradle.read_text()
    assert text.count('QwenArenaInstrumentation') == 1
    gradle.write_text(text.replace('io.ondeviceai.acceptance.qwenarena', 'io.ondeviceai.acceptance.qwenkv288')
                     .replace('QwenArenaInstrumentation', 'QwenKv288Instrumentation'))
    manifest = SOURCE / 'AndroidManifest.xml'
    text = manifest.read_text()
    assert text.count('Qwen Arena Acceptance') == 1
    manifest.write_text(text.replace('Qwen Arena Acceptance', 'Qwen KV288 Acceptance'))
    (SOURCE / 'test-src').mkdir(parents=True, exist_ok=True)
    shutil.copy2(candidate, SOURCE / 'test-src/QwenKv288Instrumentation.kt')
    production = json.loads((OLD / 'production-before.json').read_text())
    for relative, wanted in production.items():
        assert sha(ROOT / relative) == wanted, relative
    shutil.copy2(OLD / 'production-before.json', OUT / 'production-before.json')
    files = {str(p.relative_to(SOURCE)): sha(p) for p in SOURCE.rglob('*') if p.is_file()}
    fingerprint = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    receipt = {'sourceFiles': files, 'sourceFingerprint': fingerprint,
               'oldSourceFingerprint': old['sourceFingerprint'],
               'wrapperHostPassed': True, 'candidateIntegration': integration,
               'deviceOperations': 0, 'newNativeRunFrozen': False,
               'nativePolicyEffectVerified': False, 'maxTotalLen': 288, 'maxNewTokens': 128}
    (OUT / 'candidate-accepted.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({'files': len(files), 'sourceFingerprint': fingerprint, 'deviceOperations': 0}))


if __name__ == '__main__':
    main()
