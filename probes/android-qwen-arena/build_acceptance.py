"""Reuse the checked offline FILE build/package verifier against the distinct arena package."""
from pathlib import Path
import json, sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'probes/android-qwen-file/build_acceptance.py'

def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new, 1)

source = BASE.read_text()
source = replace_once(source, "OUT = ROOT / 'artifacts/verification/w3-qwen-file-20261111'", "OUT = ROOT / 'artifacts/verification/w3-qwen-arena-wrapper-20261111'")
source = replace_once(source, "'io.ondeviceai.acceptance.qwenfile',", "'io.ondeviceai.acceptance.qwenarena',")
source = replace_once(source, "'io.ondeviceai.acceptance.qwenfile.test', ['QwenFileInstrumentation']", "'io.ondeviceai.acceptance.qwenarena.test', ['QwenArenaInstrumentation']")
source = replace_once(source, "'TwoStageCaseRunner']),", "'TwoStageCaseRunner', 'QwenArenaPolicy', 'ArenaPolicyReceipt', 'ArenaPolicyException']),")
extra = '''
test_manifest = command([aapt, 'dump', 'xmltree', test[0], 'AndroidManifest.xml'])
assert 'io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation' in test_manifest
assert 'io.ondeviceai.acceptance.qwenarena' in test_manifest
(OUT / 'test.apk.manifest.txt').write_text(test_manifest)
result['exactRunnerVerified'] = 'io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation'
result['nativePolicyEffectVerified'] = False
result['newNativeRunFrozen'] = False
'''
source = replace_once(source, "(OUT / 'package-verified.json').write_text(", extra+"\n(OUT / 'package-verified.json').write_text(")
exec(compile(source, str(BASE)+'[arena-build-adapter]', 'exec'), {'__file__': str(BASE), '__name__': '__main__'})
