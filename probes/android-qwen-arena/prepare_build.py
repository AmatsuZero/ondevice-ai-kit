"""Prepare a distinct build only after exact external wrapper host acceptance; no run plan/device."""
from pathlib import Path
import hashlib, json, shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-arena-wrapper-20261111'
EVIDENCE = ROOT / '.omx/artifacts/w3-qwen-arena-wrapper-20261111'
SOURCE = OUT / 'source'
OLD = ROOT / 'artifacts/verification/w3-qwen-file-20261111'

def sha(path):
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()

def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new, 1)

def main():
    accepted = json.loads((EVIDENCE / 'candidates-accepted.json').read_bytes())
    assert accepted['wrapperHostPassed'] and accepted['policyHostPassed']
    for rel, digest in accepted['files'].items():
        assert sha(ROOT / rel) == digest, rel
    original = json.loads((OLD / 'candidate-accepted.json').read_text())
    for rel, digest in original['sourceFiles'].items():
        assert sha(OLD / 'source' / rel) == digest, rel
    assert not SOURCE.exists()
    SOURCE.mkdir(parents=True)
    for rel in ('gradlew', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties', 'settings.gradle.kts'):
        dest = SOURCE / rel; dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(OLD / 'source' / rel, dest)
    gradle = (OLD / 'source/build.gradle.kts').read_text().replace('io.ondeviceai.acceptance.qwenfile', 'io.ondeviceai.acceptance.qwenarena')
    gradle = replace_once(gradle, 'io.ondeviceai.acceptance.qwenarena.QwenFileInstrumentation', 'io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation')
    (SOURCE / 'build.gradle.kts').write_text(gradle)
    manifest = (OLD / 'source/AndroidManifest.xml').read_text().replace('Qwen FILE Acceptance', 'Qwen Arena Acceptance')
    (SOURCE / 'AndroidManifest.xml').write_text(manifest)
    for folder in ('main-src', 'test-src', 'test-assets'):
        (SOURCE / folder).mkdir()
    for name in ('QwenTwoStageCore.kt', 'QwenFileActivity.kt', 'QwenFileInputs.kt', 'QwenFileReport.kt', 'QwenLoadCore.kt'):
        shutil.copy2(OLD / 'source/main-src' / name, SOURCE / 'main-src' / name)
    shutil.copy2(ROOT / 'probes/android-qwen-arena/QwenArenaPolicy.kt', SOURCE / 'main-src/QwenArenaPolicy.kt')
    shutil.copy2(ROOT / 'probes/android-qwen-arena/QwenArenaInstrumentation.kt', SOURCE / 'test-src/QwenArenaInstrumentation.kt')
    shutil.copytree(OLD / 'source/test-assets', SOURCE / 'test-assets', dirs_exist_ok=True)
    files = {str(p.relative_to(SOURCE)): sha(p) for p in SOURCE.rglob('*') if p.is_file()}
    fp = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    receipt = {'sourceFiles': files, 'sourceFingerprint': fp, 'candidateReceipt': accepted,
               'oldSourceFingerprint': original['sourceFingerprint'], 'deviceOperations': 0,
               'newNativeRunFrozen': False, 'nativePolicyEffectVerified': False}
    (OUT / 'candidate-accepted.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({'files': len(files), 'sourceFingerprint': fp, 'newNativeRunFrozen': False}))

if __name__ == '__main__':
    main()
