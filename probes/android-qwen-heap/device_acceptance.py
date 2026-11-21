"""Parent device routing. All actions require a complete reviewed freeze before any adb."""
from pathlib import Path
import hashlib
import importlib.util
import json
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'artifacts/verification/w3-qwen-heap-20261111'
APP='io.ondeviceai.acceptance.qwenheap'
RUN='w3-qwen-heap-20261111-01'
RUNNER=APP+'.test/io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation'
BASE=ROOT/'probes/android-qwen-file/device_acceptance.py'
PRIOR='artifacts/verification/w3-qwen-arena-wrapper-20261111/device-report.json'
PRIOR_SHA='3a5d9c4c13f91768b073fc98591b4bc88ecd5df7ab82c22bc1aa188d89d7140d'
POLICY=b'EnableCpuMemArena=0\n'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def replace_once(text,old,new):
    assert text.count(old)==1,old
    return text.replace(old,new,1)


def uptime_nanos(text):
    match=re.fullmatch(r'([0-9]+)\.([0-9]{2}) [0-9]+\.[0-9]{2}\n?',text)
    if not match:
        raise ValueError('unexpected proc uptime format')
    result=int(match[1])*1_000_000_000+int(match[2])*10_000_000
    if result>2**63-1:
        raise ValueError('uptime out of bounds')
    return result


def uptime_window(before,after):
    assert type(before) is int and type(after) is int and 0<=before<=after<=2**63-1
    # Same conservative second tolerance as the old case-age guard. This is
    # an observation interval, not a verified cross-clock mapping or peak.
    return max(0,before-1_000_000_000),after+1_000_000_000


def same_file_identity(first,second):
    pattern=r'[0-9]+ [0-9]+ [0-9]+ [0-9]+\n?'
    return (type(first) is str and type(second) is str
            and re.fullmatch(pattern,first) is not None and re.fullmatch(pattern,second) is not None
            and first.split()==second.split())


def verify_frozen_contract():
    contract=json.loads((OUT/'diagnostic-frozen.json').read_bytes())
    assert contract['runId']==RUN and contract['app']==APP and contract['runner']==RUNNER
    assert contract['independentSourceReviewAccepted'] is True
    assert contract['independentParentReviewAccepted'] is True
    assert contract['automaticRetry'] is False and contract['nativePolicyEffectVerified'] is False
    files=contract['files'];assert isinstance(files,dict) and files,'required frozen artifact hashes absent'
    prefix=str(OUT.relative_to(ROOT))+'/'
    required={prefix+name for name in ('main.apk','test.apk','package-verified.json','candidate-accepted.json',
                                      'run-plan.json','production-before.json','source/test-assets/cases.json')}
    required|={PRIOR,'probes/android-qwen-file/device_acceptance.py','probes/android-qwen-file/verify_report.py',
               'probes/android-qwen-file/cases.json',
               'probes/android-qwen-arena/verify_report.py','tools/speech_error_metrics.py',
               '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json',
               'docs/superpowers/specs/2026-11-11-w3-qwen-heap-diagnostic.md',
               'docs/validation/w3-qwen-heap-wrapper-review-20261111.json',
               'docs/validation/w3-qwen-heap-parent-review-20261111.json'}
    required|={'probes/android-qwen-heap/'+name for name in ('QwenHeapInstrumentation.kt','device_acceptance.py',
               'prepare_build.py','build_acceptance.py','verify_report.py','check_parent.py','check_wrapper.py',
               'HeapWrapperChecks.kt','check_routing.py')}
    bindings={'probes/android-qwen-heap/QwenHeapInstrumentation.kt':'test-src/QwenHeapInstrumentation.kt',
              'probes/android-qwen-arena/QwenArenaPolicy.kt':'main-src/QwenArenaPolicy.kt'}
    bindings.update({'probes/android-qwen-file/'+name:'main-src/'+name for name in
                     ('QwenTwoStageCore.kt','QwenFileActivity.kt','QwenFileInputs.kt','QwenFileReport.kt')})
    bindings['probes/android-qwen-load/QwenLoadCore.kt']='main-src/QwenLoadCore.kt'
    required|=set(bindings)
    assert required<=set(files),'required frozen artifact hashes absent'
    for relative,wanted in files.items():
        target=(ROOT/relative).resolve()
        assert target.is_relative_to(ROOT.resolve()) and sha(target)==wanted,relative
    assert contract['priorReportPath']==PRIOR and contract['priorReportSHA256']==sha(ROOT/PRIOR)==PRIOR_SHA
    prior=json.loads((ROOT/PRIOR).read_bytes())['rows'][25]
    assert prior['id']=='fleurs-en_us-1776' and prior['status']=='planned' and prior['caseState'] is None
    package=json.loads((OUT/'package-verified.json').read_bytes())
    source=json.loads((OUT/'candidate-accepted.json').read_bytes())
    assert contract['sourceFingerprint']==package['sourceFingerprint']==source['sourceFingerprint']
    assert source['sourceFiles'] and source['sourceFingerprint']==hashlib.sha256(
        json.dumps(source['sourceFiles'],sort_keys=True,separators=(',',':')).encode()).hexdigest()
    for relative,copied in bindings.items():
        assert sha(ROOT/relative)==source['sourceFiles'][copied],'root and packaged source differ'
    for relative,wanted in source['sourceFiles'].items():
        target=(OUT/'source'/relative).resolve()
        assert target.is_relative_to((OUT/'source').resolve()) and sha(target)==wanted
        assert files.get(str(target.relative_to(ROOT.resolve())))==wanted,'source file not frozen'
    assert package['exactRunnerVerified']=='io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation'
    assert type(package['actualTestAssetsVerified']) is int and package['actualTestAssetsVerified']==65
    assert len(package['runtimeLibraries'])==4
    for name,alias in [('main.apk',APP),('test.apk',APP+'.test')]:
        row=package['files'][name]
        assert row['package']==alias and row['sha256']==sha(OUT/name)
        assert package['permissions'][name]==[]
    labels=contract['heapBoundaryLabels']
    wanted_labels={'initial','constructor-before','constructor-returned','core-persist',
                   'case-returned','recognizer-released','teardown'}
    assert isinstance(labels,dict) and set(labels)==wanted_labels
    assert all(type(value) is str and value for value in labels.values()) and len(set(labels.values()))==7
    plan=json.loads((OUT/'run-plan.json').read_bytes())
    wanted={'runId':RUN,'sourceFingerprint':contract['sourceFingerprint'],'caseCount':1,
            'maxNativeDecodeCalls':2,'maxStreamsCreated':2,'maxConstructors':1,'selectedOriginalIndex':25,
            'order':['fleurs-en_us-1776'],'manifestSHA256':'8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
            'sampledRssStopAboveBytes':1610612736,'deviceAvailableStopBelowBytes':536870912,
            'caseStopAboveSeconds':120,'automaticRetry':False,'microphoneStarts':0,'systemAsrRequests':0,
            'productionChanges':False,'currentUser':'0','clockCaptureSource':'proc-uptime-read-interval-v1',
            'preciseClockAlignmentVerified':False}
    for key,value in wanted.items():
        assert type(plan.get(key)) is type(value) and plan[key]==value,key
    return contract


def driver_source():
    source=BASE.read_text()
    source=replace_once(source,"assert plan['maxNativeDecodeCalls'] == 130 and plan['caseCount'] == 65",
                        "assert plan['maxNativeDecodeCalls'] == 2 and plan['caseCount'] == 1")
    source=replace_once(source,"'maxPlannedStreams': 130, 'maxPlannedDecode': 130, 'plannedMicrophone': 0, 'inputCount': 65",
                        "'maxPlannedStreams': 2, 'maxPlannedDecode': 2, 'plannedMicrophone': 0, 'inputCount': 1")
    source=replace_once(source,"0 <= report['nativeDecodeCalls'] <= 130 and 0 <= report['streamsCreated'] <= 130",
                        "0 <= report['nativeDecodeCalls'] <= 2 and 0 <= report['streamsCreated'] <= 2")
    source=replace_once(source,'120 * 66','120 * 2').replace('total_batch_window_above_7920_seconds','total_diagnostic_window_above_240_seconds')
    source=replace_once(source,"                        rss = parse_kb(",
                        "                        uptime_before = uptime_nanos(text('shell', 'cat', '/proc/uptime'))\n                        rss = parse_kb(")
    source=replace_once(source,"                        report_path = OUT / 'last-observed-report.json'",
                        "                        uptime_after = uptime_nanos(text('shell', 'cat', '/proc/uptime'))\n                        uptime_lower, uptime_upper = uptime_window(uptime_before, uptime_after)\n                        report_path = OUT / 'last-observed-report.json'")
    source=replace_once(source,"'windowAgeSeconds': age}",
                        "'windowAgeSeconds': age, 'deviceUptimeReadBeforeNs': uptime_before, 'deviceUptimeReadAfterNs': uptime_after, 'deviceUptimeWindowLowerNs': uptime_lower, 'deviceUptimeWindowUpperNs': uptime_upper, 'preciseClockAlignmentVerified': False}")
    return source


def adb(*args):
    serial=json.loads((ROOT/'.omx/artifacts/w3-model-reselection-20261110/android-target-local.json').read_bytes())['serial']
    return subprocess.run(['rtk','proxy',str(Path.home()/'Library/Android/sdk/platform-tools/adb'),'-s',serial,*args],
                          capture_output=True,check=True,timeout=30).stdout


def prior_collection():
    collection=json.loads((OUT/'collection.json').read_bytes())
    raw=(OUT/'device-report.json').read_bytes();report=json.loads(raw)
    assert collection['reportPresent'] is True and collection['twoReadsIdentical'] is True
    assert raw==(OUT/'device-report-confirm.json').read_bytes() and hashlib.sha256(raw).hexdigest()==collection['sha256']
    package=json.loads((OUT/'package-verified.json').read_bytes())
    assert report['runId']==RUN and report['sourceFingerprint']==package['sourceFingerprint']
    assert len(collection['modelHashesAfterMatch'])==6 and all(v is True for v in collection['modelHashesAfterMatch'].values())
    for name,alias in [('main.apk',APP),('test.apk',APP+'.test')]:
        path=adb('shell','pm','path',alias).decode().strip().removeprefix('package:')
        assert path.startswith('/data/app/') and path.endswith('/base.apk') and '\n' not in path
        assert adb('shell','sha256sum',path).decode().split()[0]==package['files'][name]['sha256']
    assert adb('shell','run-as',APP,'sha256sum','files/w3-qwen-heap/report-'+RUN+'.json').decode().split()[0]==collection['sha256']
    return raw,report


def collect_policy():
    raw,report=prior_collection()
    result={'privateRoot':None,'receiptMatches':False,'fileBytesMatch':False,
            'runId':RUN,'reportSHA256':hashlib.sha256(raw).hexdigest(),'nativePolicyEffectVerified':False}
    policy=report.get('allocatorPolicy')
    if policy is not None:
        framework='/data/data/'+APP+'/no_backup';shell='/data/user/0/'+APP+'/no_backup'
        assert policy['configPath'] in (framework+'/cpu-arena-off/cpu.config',shell+'/cpu-arena-off/cpu.config')
        root_id=adb('shell','run-as',APP,'stat','-c','%u %d %i %s','no_backup').decode()
        for path in (framework,shell):
            assert same_file_identity(root_id,adb('shell','run-as',APP,'stat','-c','%u %d %i %s',path).decode())
        suffix='/cpu-arena-off/cpu.config';file_id=adb('shell','run-as',APP,'stat','-c','%u %d %i %s','no_backup'+suffix).decode()
        for path in (framework+suffix,shell+suffix):
            assert same_file_identity(file_id,adb('shell','run-as',APP,'stat','-c','%u %d %i %s',path).decode())
        assert root_id.split()[0]==file_id.split()[0]
        for path in ('no_backup'+suffix,framework+suffix,shell+suffix):
            adb('shell','run-as',APP,'test','!','-L',path)
        first=adb('exec-out','run-as',APP,'head','-c','21','no_backup'+suffix)
        second=adb('exec-out','run-as',APP,'head','-c','21','no_backup'+suffix)
        assert first==second==POLICY
        spec=importlib.util.spec_from_file_location('heap_requested_policy',ROOT/'probes/android-qwen-arena/verify_report.py')
        verifier=importlib.util.module_from_spec(spec);spec.loader.exec_module(verifier)
        normalized=dict(report,probeVariant='cpu-arena-off-v1')
        private=policy['configPath'].removesuffix(suffix)
        result.update(privateRoot=private,receiptMatches=verifier.policy_matches(normalized,private),fileBytesMatch=True,
                      readLimitBytes=21,boundedReadBytes=len(first),boundedReadSHA256=hashlib.sha256(first).hexdigest(),
                      directoryIdentity=root_id.strip(),fileIdentity=file_id.strip(),twoReadsIdentical=True)
    with (OUT/'allocator-policy-collected.json').open('x') as stream:
        json.dump(result,stream,indent=2);stream.write('\n')
    return result


def main(action):
    assert action in ('preflight','prepare','run','collect','collect-policy','restore')
    verify_frozen_contract()
    assert adb('shell','am','get-current-user').decode().strip()=='0'
    namespace={'__file__':str(BASE),'__name__':'heap_inherited_device_driver',
               'uptime_nanos':uptime_nanos,'uptime_window':uptime_window}
    exec(compile(driver_source(),str(BASE)+'[heap-device-adapter]','exec'),namespace)
    namespace.update(OUT=OUT,APP=APP,TEST=APP+'.test',RUN=RUN,STAGE='/data/local/tmp/'+RUN,
                     REPORT='files/w3-qwen-heap/report-'+RUN+'.json',RUNNER=RUNNER)
    if action=='collect-policy':
        namespace['main']('preflight')
        collect_policy()
    else:
        namespace['main'](action)
        if action=='collect':
            collect_policy()


if __name__=='__main__':
    assert len(sys.argv)==2
    main(sys.argv[1])
