"""Offline FILE package verifier adapted to the separately prepared terminal alias; no devices."""
from pathlib import Path
import hashlib
import importlib.util
import json
import sys

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'artifacts/verification/w3-qwen-terminal-20261112'
BASE=ROOT/'probes/android-qwen-file/build_acceptance.py'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def replace_once(text,old,new):
    assert text.count(old)==1,old
    return text.replace(old,new,1)


def verify_prepared(action):
    assert action in ('build','verify')
    receipt=json.loads((OUT/'candidate-accepted.json').read_bytes())
    files=receipt['sourceFiles'];assert files
    assert receipt['sourceFingerprint']==hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    required={'test-src/QwenTerminalInstrumentation.kt','test-src/TerminalObservationBarrier.kt',
             'test-src/TerminalAckFile.kt','main-src/QwenArenaPolicy.kt','build.gradle.kts','AndroidManifest.xml'}
    assert required<=set(files)
    for relative,wanted in files.items():
        target=(OUT/'source'/relative).resolve()
        assert target.is_relative_to((OUT/'source').resolve()) and sha(target)==wanted,relative
    for key,hash_key in [('path','sha256'),('baseHashesPath','baseHashesSHA256'),
                         ('terminalAdapterHashesPath','terminalAdapterHashesSHA256')]:
        path=(ROOT/receipt['hostReceipt'][key]).resolve()
        assert path.is_relative_to(ROOT.resolve()) and sha(path)==receipt['hostReceipt'][hash_key]
    spec=importlib.util.spec_from_file_location('terminal_build_preparation',Path(__file__).with_name('prepare_build.py'))
    preparation=importlib.util.module_from_spec(spec);spec.loader.exec_module(preparation)
    host_path=(ROOT/receipt['hostReceipt']['path']).resolve()
    _,base_path,terminal_adapter_path=preparation.verify_host(host_path,ROOT)
    assert base_path.resolve()==(ROOT/receipt['hostReceipt']['baseHashesPath']).resolve()
    assert terminal_adapter_path.resolve()==(ROOT/receipt['hostReceipt']['terminalAdapterHashesPath']).resolve()
    bindings={'probes/android-qwen-terminal/QwenTerminalInstrumentation.kt':'test-src/QwenTerminalInstrumentation.kt',
              'probes/android-terminal-observation/TerminalObservationBarrier.kt':'test-src/TerminalObservationBarrier.kt',
              'probes/android-terminal-observation/TerminalAckFile.kt':'test-src/TerminalAckFile.kt',
              'probes/android-qwen-arena/QwenArenaPolicy.kt':'main-src/QwenArenaPolicy.kt'}
    bindings.update({'probes/android-qwen-file/'+name:'main-src/'+name for name in preparation.SHARED_MAIN
                     if name != 'QwenLoadCore.kt'})
    bindings['probes/android-qwen-load/QwenLoadCore.kt']='main-src/QwenLoadCore.kt'
    for original,copied in bindings.items():
        assert sha(ROOT/original)==files[copied],'root and prepared source differ'
    if action=='build':
        assert not any((OUT/name).exists() for name in ('build.log','build-result.json','main.apk','test.apk','package-verified.json'))
    else:
        assert not any((OUT/name).exists() for name in ('main.apk','test.apk','package-verified.json'))


def driver_source():
    source=BASE.read_text()
    source=replace_once(source,"OUT = ROOT / 'artifacts/verification/w3-qwen-file-20261111'",
                         "OUT = ROOT / 'artifacts/verification/w3-qwen-terminal-20261112'")
    source=replace_once(source,"'io.ondeviceai.acceptance.qwenfile',","'io.ondeviceai.acceptance.qwenterminal',")
    source=replace_once(source,"'io.ondeviceai.acceptance.qwenfile.test', ['QwenFileInstrumentation']",
                         "'io.ondeviceai.acceptance.qwenterminal.test', ['QwenTerminalInstrumentation', 'TerminalAckFile', 'TerminalObservationBinding', 'TerminalObservationBarrier']")
    source=replace_once(source,"'TwoStageCaseRunner']),",
                         "'TwoStageCaseRunner', 'QwenArenaPolicy', 'ArenaPolicyReceipt', 'ArenaPolicyException']),")
    extra='''
test_manifest = command([aapt, 'dump', 'xmltree', test[0], 'AndroidManifest.xml'])
assert 'io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation' in test_manifest
assert 'io.ondeviceai.acceptance.qwenterminal' in test_manifest
(OUT / 'test.apk.manifest.txt').write_text(test_manifest)
with zipfile.ZipFile(AAR) as original_runtime:
    expected_runtime = {'lib/' + n.removeprefix('jni/') for n in original_runtime.namelist()
                        if n.startswith('jni/arm64-v8a/') and n.endswith('.so')}
assert len(expected_runtime) == 4 and set(result['runtimeLibraries']) == expected_runtime
result['exactRunnerVerified'] = 'io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation'
result['nativePolicyEffectVerified'] = False
result['newNativeRunFrozen'] = False
result['terminalObservationDeviceVerified'] = False
'''
    source=replace_once(source,"(OUT / 'package-verified.json').write_text(",extra+"\n(OUT / 'package-verified.json').write_text(")
    return source


def main(action):
    verify_prepared(action)
    source=driver_source()
    original_args=sys.argv
    try:
        sys.argv=[str(BASE),action]
        exec(compile(source,str(BASE)+'[terminal-package-adapter]','exec'),{'__file__':str(BASE),'__name__':'terminal_package_adapter'})
    finally:
        sys.argv=original_args


if __name__=='__main__':
    assert len(sys.argv)==2
    main(sys.argv[1])