"""Parent-generated isolated Gradle harness; requires accepted actual candidate bytes."""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-file-20261111'
SOURCE = OUT / 'source'
EVIDENCE = ROOT / '.omx/artifacts/w3-qwen-file-20261111'


def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def main():
    accepted = json.loads((EVIDENCE/'candidates-accepted.json').read_bytes())
    for relative, digest in accepted['files'].items():
        assert sha(ROOT/relative) == digest, relative
    assert accepted['coreHostPassed'] and accepted['wrapperHostPassed']
    SOURCE.mkdir(exist_ok=False)
    original = ROOT/'artifacts/verification/w3-qwen-load-20261110/source'
    for relative in ('gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties','settings.gradle.kts'):
        dest=SOURCE/relative;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(original/relative,dest)
    gradle = (original/'build.gradle.kts').read_text().replace('io.ondeviceai.acceptance.qwenload','io.ondeviceai.acceptance.qwenfile').replace('QwenLoadInstrumentation','QwenFileInstrumentation')
    (SOURCE/'build.gradle.kts').write_text(gradle)
    manifest=(original/'AndroidManifest.xml').read_text().replace('io.ondeviceai.acceptance.qwenload.QwenLoadActivity','io.ondeviceai.acceptance.qwenfile.QwenFileActivity').replace('Qwen Load Acceptance','Qwen FILE Acceptance')
    (SOURCE/'AndroidManifest.xml').write_text(manifest)
    for d in ('main-src','test-src','test-assets/inputs'):
        (SOURCE/d).mkdir(parents=True,exist_ok=True)
    for name in ('QwenTwoStageCore.kt','QwenFileActivity.kt','QwenFileInputs.kt','QwenFileReport.kt'):
        shutil.copy2(ROOT/'probes/android-qwen-file'/name,SOURCE/'main-src'/name)
    shutil.copy2(ROOT/'probes/android-qwen-file/QwenFileInstrumentation.kt',SOURCE/'test-src/QwenFileInstrumentation.kt')
    shutil.copy2(ROOT/'probes/android-qwen-load/QwenLoadCore.kt',SOURCE/'main-src/QwenLoadCore.kt')
    manifest=Path(__file__).with_name('cases.json')
    assert sha(manifest)=='8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f'
    shutil.copy2(manifest,SOURCE/'test-assets/cases.json')
    protocol=json.loads((ROOT/'.omx/artifacts/w3-model-reselection-20261110/independent-protocol.json').read_bytes())
    assert len(protocol['cases'])==65
    for c in protocol['cases']:
        src=Path(c['pcmPath']);assert src.stat().st_size==2*c['samples'] and sha(src)==c['sha256']
        shutil.copy2(src,SOURCE/'test-assets/inputs'/(c['id']+'.pcm'))
    files={str(p.relative_to(SOURCE)):sha(p) for p in SOURCE.rglob('*') if p.is_file()}
    fp=hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    receipt={'sourceFiles':files,'sourceFingerprint':fp,'candidateReceipt':accepted,'deviceOperations':0}
    (OUT/'candidate-accepted.json').write_text(json.dumps(receipt,indent=2)+'\n')
    plan={'runId':'w3-qwen-file-20261111-01','sourceFingerprint':fp,'caseCount':65,'maxNativeDecodeCalls':130,
          'expectedNativeDecodeCallsIfAllAdmissionMatches':117,'manifestSHA256':sha(manifest),
          'order':[c['id'] for c in protocol['cases']], 'sampledRssStopAboveBytes':1536*1024**2,
          'deviceAvailableStopBelowBytes':512*1024**2,'caseStopAboveSeconds':120,
          'automaticRetry':False,'microphoneStarts':0,'systemAsrRequests':0,'productionChanges':False}
    (OUT/'run-plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    print(json.dumps({'sourceFingerprint':fp,'files':len(files),'inputCount':65,'nativeCalls':0,'deviceOperations':0}))


if __name__=='__main__':
    main()
