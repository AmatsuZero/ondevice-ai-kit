"""Reuse the frozen offline FILE build verifier for the distinct KV288 package."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'probes/android-qwen-file/build_acceptance.py'


def once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new, 1)


source = BASE.read_text()
for old, new in [
    ("OUT = ROOT / 'artifacts/verification/w3-qwen-file-20261111'", "OUT = ROOT / 'artifacts/verification/w3-qwen-kv288-emulator-20261111'"),
    ("'io.ondeviceai.acceptance.qwenfile',", "'io.ondeviceai.acceptance.qwenkv288',"),
    ("'io.ondeviceai.acceptance.qwenfile.test', ['QwenFileInstrumentation']", "'io.ondeviceai.acceptance.qwenkv288.test', ['QwenKv288Instrumentation']"),
    ("'TwoStageCaseRunner']),", "'TwoStageCaseRunner', 'QwenArenaPolicy', 'ArenaPolicyReceipt', 'ArenaPolicyException']),"),
]:
    source = once(source, old, new)
extra = '''
test_manifest = command([aapt, 'dump', 'xmltree', test[0], 'AndroidManifest.xml'])
assert 'io.ondeviceai.acceptance.qwenfile.QwenKv288Instrumentation' in test_manifest
assert 'io.ondeviceai.acceptance.qwenkv288' in test_manifest
(OUT / 'test.apk.manifest.txt').write_text(test_manifest)
assert len(result['runtimeLibraries']) == 4
result['exactRunnerVerified'] = 'io.ondeviceai.acceptance.qwenfile.QwenKv288Instrumentation'
result['nativePolicyEffectVerified'] = False
result['newNativeRunFrozen'] = False
result['requestedMaxTotalLen'] = 288
'''
source = once(source, "(OUT / 'package-verified.json').write_text(", extra + "\n(OUT / 'package-verified.json').write_text(")
exec(compile(source, str(BASE) + '[kv288-build-adapter]', 'exec'), {'__file__': str(BASE), '__name__': '__main__'})
