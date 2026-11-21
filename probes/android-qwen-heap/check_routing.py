"""Offline routing tests. Temporary files are synthetic and never become device evidence."""
from pathlib import Path
import copy
import hashlib
import importlib.util
import json
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def module(name):
    path = Path(__file__).with_name(name+'.py')
    if not path.is_file():
        return None
    spec = importlib.util.spec_from_file_location('heap_routing_'+name, path)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


prepare = module('prepare_build')
build = module('build_acceptance')
device = module('device_acceptance')


def sha(data):
    return hashlib.sha256(data).hexdigest()


class PrepareRoutingChecks(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(prepare, 'heap build preparation route absent')

    def fixture(self, root):
        old = root/'old'; source = old/'source'; source.mkdir(parents=True)
        paths = {'gradlew': b'#!/bin/sh\n', 'gradle/wrapper/gradle-wrapper.jar': b'fixture',
                 'gradle/wrapper/gradle-wrapper.properties': b'fixture', 'settings.gradle.kts': b'fixture',
                 'build.gradle.kts': b'namespace = "io.ondeviceai.acceptance.qwenfile"\napplicationId = "io.ondeviceai.acceptance.qwenfile"\ntestInstrumentationRunner = "io.ondeviceai.acceptance.qwenfile.QwenFileInstrumentation"\n',
                 'AndroidManifest.xml': b'<manifest><application android:label="Qwen FILE Acceptance"/></manifest>',
                 'test-assets/cases.json': (ROOT/'probes/android-qwen-file/cases.json').read_bytes()}
        for name in prepare.SHARED_MAIN:
            paths['main-src/'+name] = ('fixture '+name).encode()
        for name, content in paths.items():
            target=source/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
        source_files={name:sha(content) for name,content in paths.items()}
        fp=sha(json.dumps(source_files,sort_keys=True,separators=(',',':')).encode())
        (old/'candidate-accepted.json').write_text(json.dumps(dict(sourceFiles=source_files,sourceFingerprint=fp)))
        for name in prepare.SHARED_MAIN:
            directory = 'android-qwen-load' if name == 'QwenLoadCore.kt' else 'android-qwen-file'
            target=root/'probes'/directory/name;target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(paths['main-src/'+name])
        for name in ('probes/android-qwen-heap/QwenHeapInstrumentation.kt',
                     'probes/android-qwen-arena/QwenArenaPolicy.kt'):
            target=root/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text('fixture '+name)
        required = [root/'probes/android-qwen-heap/QwenHeapInstrumentation.kt',
                    root/'probes/android-qwen-arena/QwenArenaPolicy.kt',
                    *[root/'probes/android-qwen-file'/name for name in prepare.SHARED_MAIN if name!='QwenLoadCore.kt']]
        host=dict(compileExitCode=0,runtimeExitCode=0,realNativeCalls=0,deviceOperations=0,
                  realCaseCore=True,hostAndroidAndNativeDoubles=True,output='ASSERTIONS=1\n',
                  sourceSHA256={str(path):sha(path.read_bytes()) for path in required})
        receipt=root/'host-result.json';receipt.write_text(json.dumps(host))
        hashes={}
        for name,relative in [('adapterSHA256','probes/android-qwen-heap/check_wrapper.py'),
                              ('scaffoldSHA256','probes/android-qwen-file/check_wrapper.py'),
                              ('heapChecksSHA256','probes/android-qwen-heap/HeapWrapperChecks.kt')]:
            target=root/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text('fixture '+relative)
            hashes[name]=sha(target.read_bytes())
        hashes.update(oldProbeSourcesModified=False,selectedOriginalIndex=25)
        (root/'base-hashes.json').write_text(json.dumps(hashes))
        return old,receipt

    def test_absent_host_receipt_never_creates_build_source(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-prepare-') as folder:
            root=Path(folder);out=root/'new'
            with self.assertRaises(FileNotFoundError):
                prepare.main(root/'missing.json',root=root,out=out,old=root/'old')
            self.assertFalse(out.exists())

    def test_failed_or_stale_host_receipt_cannot_prepare_source(self):
        for change in (
            lambda j:j.update(compileExitCode=1),lambda j:j.update(runtimeExitCode=True),
            lambda j:j.update(realCaseCore=False),lambda j:j.update(sourceSHA256={}),
            lambda j:j.update(deviceOperations=1),
        ):
            with self.subTest(change=change),tempfile.TemporaryDirectory(prefix='odai-heap-host-gate-') as folder:
                root=Path(folder);old,receipt=self.fixture(root);out=root/'new'
                host=json.loads(receipt.read_text());change(host);receipt.write_text(json.dumps(host))
                with self.assertRaises(AssertionError):prepare.main(receipt,root=root,out=out,old=old)
                self.assertFalse(out.exists())

    def test_valid_synthetic_host_prepares_distinct_alias_and_no_run_contract(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-source-') as folder:
            root=Path(folder);old,receipt=self.fixture(root);out=root/'new'
            before={str(p):sha(p.read_bytes()) for p in old.rglob('*') if p.is_file()}
            result=prepare.main(receipt,root=root,out=out,old=old)
            text=(out/'source/build.gradle.kts').read_text()
            self.assertIn('io.ondeviceai.acceptance.qwenheap',text)
            self.assertIn('io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation',text)
            self.assertTrue((out/'source/test-src/QwenHeapInstrumentation.kt').is_file())
            self.assertEqual(result['deviceOperations'],0)
            self.assertFalse(result['newNativeRunFrozen'])
            self.assertFalse((out/'run-plan.json').exists())
            self.assertFalse((out/'diagnostic-frozen.json').exists())
            self.assertEqual(before,{str(p):sha(p.read_bytes()) for p in old.rglob('*') if p.is_file()})
            with self.assertRaises(FileExistsError):prepare.main(receipt,root=root,out=out,old=old)

    def test_shared_source_mismatch_fails_before_writing_any_new_directory(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-old-hash-') as folder:
            root=Path(folder);old,receipt=self.fixture(root);out=root/'new'
            (old/'source/main-src/QwenFileActivity.kt').write_text('changed')
            with self.assertRaises(AssertionError):prepare.main(receipt,root=root,out=out,old=old)
            self.assertFalse(out.exists())

    def test_stale_host_tests_do_not_allow_build_preparation(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-host-tests-') as folder:
            root=Path(folder);old,receipt=self.fixture(root);out=root/'new'
            (root/'probes/android-qwen-heap/HeapWrapperChecks.kt').write_text('new tests after host receipt')
            with self.assertRaises(AssertionError):prepare.main(receipt,root=root,out=out,old=old)
            self.assertFalse(out.exists())


class BuildRoutingChecks(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(build,'heap build/package route absent')

    def test_build_route_rejects_missing_candidate_without_starting_process(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-no-build-') as folder:
            with patch.object(build,'OUT',Path(folder)),patch('subprocess.run') as process:
                with self.assertRaises(FileNotFoundError):build.main('build')
                process.assert_not_called()

    def test_invalid_action_cannot_start_process(self):
        with patch('subprocess.run') as process:
            with self.assertRaises(AssertionError):build.main('install')
            process.assert_not_called()

    def test_actual_package_adapter_compiles_without_executing_build_tools(self):
        with patch('subprocess.run') as process:
            compile(build.driver_source(),'heap-package-adapter-test','exec')
            process.assert_not_called()

    def test_changed_host_tests_after_source_preparation_cannot_start_build(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-recheck-host-') as folder:
            repo=Path(folder);old,host=PrepareRoutingChecks.fixture(self,repo);out=repo/'new'
            prepare.main(host,root=repo,out=out,old=old)
            (repo/'probes/android-qwen-heap/HeapWrapperChecks.kt').write_text('changed after preparation')
            with patch.object(build,'ROOT',repo),patch.object(build,'OUT',out),patch('subprocess.run') as process, \
                    patch.object(build,'driver_source',side_effect=RuntimeError('stale host reached build renderer')) as renderer:
                with self.assertRaises(AssertionError):build.main('build')
                process.assert_not_called()
                renderer.assert_not_called()

    def test_unchanged_preparation_passes_build_and_package_gate_without_tools(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-build-gate-') as folder:
            repo=Path(folder);old,host=PrepareRoutingChecks.fixture(self,repo);out=repo/'new'
            prepare.main(host,root=repo,out=out,old=old)
            with patch.object(build,'ROOT',repo),patch.object(build,'OUT',out),patch('subprocess.run') as process:
                build.verify_prepared('build')
                build.verify_prepared('verify')
                process.assert_not_called()

    def test_changed_load_core_after_preparation_is_rejected_before_build(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-build-core-') as folder:
            repo=Path(folder);old,host=PrepareRoutingChecks.fixture(self,repo);out=repo/'new'
            prepare.main(host,root=repo,out=out,old=old)
            (repo/'probes/android-qwen-load/QwenLoadCore.kt').write_text('changed after preparation')
            with patch.object(build,'ROOT',repo),patch.object(build,'OUT',out),patch('subprocess.run') as process, \
                    patch.object(build,'driver_source',side_effect=RuntimeError('changed core reached renderer')) as renderer:
                with self.assertRaises(AssertionError):build.main('build')
                process.assert_not_called()
                renderer.assert_not_called()


class DeviceRoutingChecks(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(device,'heap device guard route absent')

    def test_missing_freeze_stops_before_any_adb(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-no-device-') as folder:
            with patch.object(device,'OUT',Path(folder)),patch.object(device,'adb') as adb:
                with self.assertRaises(FileNotFoundError):device.main('preflight')
                adb.assert_not_called()

    def test_empty_frozen_hashes_cannot_authorize_any_device_call(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-empty-freeze-') as folder:
            root=Path(folder)
            contract=dict(runId='w3-qwen-heap-20261111-01',app='io.ondeviceai.acceptance.qwenheap',
                          runner='io.ondeviceai.acceptance.qwenheap.test/io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation',
                          sourceFingerprint='a'*64,independentSourceReviewAccepted=True,
                          independentParentReviewAccepted=True,nativePolicyEffectVerified=False,
                          automaticRetry=False,files={})
            (root/'diagnostic-frozen.json').write_text(json.dumps(contract))
            with patch.object(device,'OUT',root),patch.object(device,'adb') as adb:
                with self.assertRaises(AssertionError):device.main('prepare')
                adb.assert_not_called()

    def test_uptime_read_interval_is_bounded_without_claiming_native_peak(self):
        self.assertEqual(device.uptime_nanos('123.45 100.00'),123450000000)
        for text in ('NaN 0.00','123.4 0.0','-1.00 0.00','123.45; echo X','123.45 0.00\nX'):
            with self.subTest(text=text),self.assertRaises(ValueError):device.uptime_nanos(text)
        self.assertEqual(device.uptime_window(123450000000,123460000000),(122450000000,124460000000))
        with self.assertRaises(AssertionError):device.uptime_window(123460000000,123450000000)

    def test_policy_directory_alias_uses_uid_and_inode_identity(self):
        self.assertTrue(device.same_file_identity('1001 7 18 4096\n','1001 7 18 4096\n'))
        for value in ('1002 7 18 4096','1001 7 19 4096','1001 8 18 4096','1001 7 18','1001 7 18 4096\nextra'):
            self.assertFalse(device.same_file_identity('1001 7 18 4096',value))

    def test_actual_inherited_driver_adapter_compiles_without_executing(self):
        source=device.driver_source()
        compile(source,'heap-adapted-driver','exec')

    def freeze_fixture(self,repo):
        old,host=PrepareRoutingChecks.fixture(self,repo);out=repo/'new'
        receipt=prepare.main(host,root=repo,out=out,old=old);fp=receipt['sourceFingerprint']
        for name in ('main.apk','test.apk'):(out/name).write_bytes(b'fixture')
        package={'sourceFingerprint':fp,'exactRunnerVerified':'io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation',
                 'actualTestAssetsVerified':65,'runtimeLibraries':dict.fromkeys(['a','b','c','d'],'fixture'),
                 'permissions':{'main.apk':[],'test.apk':[]},
                 'files':{name:{'package':alias,'sha256':sha(b'fixture'),'bytes':7}
                          for name,alias in [('main.apk',device.APP),('test.apk',device.APP+'.test')]}}
        plan={'runId':device.RUN,'sourceFingerprint':fp,'caseCount':1,'maxNativeDecodeCalls':2,'maxStreamsCreated':2,
              'maxConstructors':1,'selectedOriginalIndex':25,'order':['fleurs-en_us-1776'],
              'manifestSHA256':'8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
              'sampledRssStopAboveBytes':1610612736,'deviceAvailableStopBelowBytes':536870912,
              'caseStopAboveSeconds':120,'automaticRetry':False,'microphoneStarts':0,'systemAsrRequests':0,
              'productionChanges':False,'currentUser':'0','clockCaptureSource':'proc-uptime-read-interval-v1',
              'preciseClockAlignmentVerified':False}
        for name,value in [('package-verified.json',package),('run-plan.json',plan),('production-before.json',{})]:
            (out/name).write_text(json.dumps(value))
        dependencies={'probes/android-qwen-file/device_acceptance.py','probes/android-qwen-file/verify_report.py',
                      'probes/android-qwen-file/cases.json',
                      'probes/android-qwen-arena/verify_report.py','tools/speech_error_metrics.py',
                      '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json',
                      'docs/superpowers/specs/2026-11-11-w3-qwen-heap-diagnostic.md',
                      'docs/validation/w3-qwen-heap-wrapper-review-20261111.json',
                      'docs/validation/w3-qwen-heap-parent-review-20261111.json'}
        dependencies|={'probes/android-qwen-heap/'+name for name in ('QwenHeapInstrumentation.kt','device_acceptance.py',
                       'prepare_build.py','build_acceptance.py','verify_report.py','check_parent.py','check_wrapper.py',
                       'HeapWrapperChecks.kt','check_routing.py')}
        for relative in dependencies:
            path=repo/relative
            if not path.exists():path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'synthetic placeholder')
        path=repo/device.PRIOR;path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes((ROOT/device.PRIOR).read_bytes())
        files={str(path.relative_to(repo)):sha(path.read_bytes()) for path in repo.rglob('*') if path.is_file()}
        labels={key:key for key in ('initial','constructor-before','constructor-returned','core-persist',
                                    'case-returned','recognizer-released','teardown')}
        contract=dict(runId=device.RUN,app=device.APP,runner=device.RUNNER,sourceFingerprint=fp,
                      independentSourceReviewAccepted=True,independentParentReviewAccepted=True,
                      automaticRetry=False,nativePolicyEffectVerified=False,heapBoundaryLabels=labels,
                      priorReportPath=device.PRIOR,priorReportSHA256=device.PRIOR_SHA,files=files)
        (out/'diagnostic-frozen.json').write_text(json.dumps(contract))
        return out,contract

    def test_complete_synthetic_freeze_passes_structural_guard_without_device_calls(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-freeze-ok-') as folder:
            repo=Path(folder);out,contract=self.freeze_fixture(repo)
            with patch.object(device,'ROOT',repo),patch.object(device,'OUT',out),patch.object(device,'adb') as adb:
                self.assertEqual(device.verify_frozen_contract()['sourceFingerprint'],contract['sourceFingerprint'])
                adb.assert_not_called()

    def test_rebound_relaxed_plan_and_missing_source_hash_still_fail_before_adb(self):
        for mutation in ('rss','source','review','root-source'):
            with self.subTest(mutation=mutation),tempfile.TemporaryDirectory(prefix='odai-heap-freeze-bad-') as folder:
                repo=Path(folder);out,contract=self.freeze_fixture(repo)
                if mutation=='rss':
                    path=out/'run-plan.json';plan=json.loads(path.read_text());plan['sampledRssStopAboveBytes']=2*1024**3
                    path.write_text(json.dumps(plan));contract['files'][str(path.relative_to(repo))]=sha(path.read_bytes())
                elif mutation=='source':contract['files'].pop(str((out/'source/test-src/QwenHeapInstrumentation.kt').relative_to(repo)))
                elif mutation=='review':contract['independentParentReviewAccepted']=False
                else:
                    relative='probes/android-qwen-file/QwenFileActivity.kt';(repo/relative).write_text('changed root source')
                    contract['files'][relative]=sha((repo/relative).read_bytes())
                (out/'diagnostic-frozen.json').write_text(json.dumps(contract))
                with patch.object(device,'ROOT',repo),patch.object(device,'OUT',out),patch.object(device,'adb') as adb:
                    with self.assertRaises(AssertionError):device.main('prepare')
                    adb.assert_not_called()

    def test_missing_base_manifest_hash_fails_before_any_adb(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-manifest-freeze-') as folder:
            repo=Path(folder);out,contract=self.freeze_fixture(repo)
            contract['files'].pop('probes/android-qwen-file/cases.json')
            (out/'diagnostic-frozen.json').write_text(json.dumps(contract))
            with patch.object(device,'ROOT',repo),patch.object(device,'OUT',out),patch.object(device,'adb') as adb:
                with self.assertRaises(AssertionError):device.main('prepare')
                adb.assert_not_called()

    def test_inherited_run_dispatch_preserves_exact_one_shot_scope(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-dispatch-') as folder:
            root=Path(folder);source=root/'source';source.mkdir()
            source_files={'fixture.txt':sha(b'fixture')};(source/'fixture.txt').write_bytes(b'fixture')
            values={'package-verified.json':{'sourceFingerprint':'a'*64,
                                            'files':{name:{'sha256':sha(b'fixture')} for name in ('main.apk','test.apk')}},
                    'candidate-accepted.json':{'sourceFiles':source_files},
                    'run-plan.json':{'runId':device.RUN,'sourceFingerprint':'a'*64,
                                     'manifestSHA256':'8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
                                     'maxNativeDecodeCalls':2,'caseCount':1},
                    'production-before.json':{},'prepared.json':{},
                    'android-target-local.json':{'serial':'synthetic-only'},
                    'model-manifest.json':{'files':{}}}
            for name,value in values.items():(root/name).write_text(json.dumps(value))
            for name in ('main.apk','test.apk'):(root/name).write_bytes(b'fixture')
            namespace={'__file__':str(device.BASE),'__name__':'heap_driver_test',
                       'uptime_nanos':device.uptime_nanos,'uptime_window':device.uptime_window}
            exec(compile(device.driver_source(),'heap-driver-test','exec'),namespace)
            namespace.update(OUT=root,EVIDENCE=root,APP=device.APP,TEST=device.APP+'.test',RUN=device.RUN,
                             REPORT='files/w3-qwen-heap/report-'+device.RUN+'.json',RUNNER=device.RUNNER)
            def response(args,**kwargs):
                if 'getprop' in args:
                    result={'ro.product.model':'TECNO KM9','ro.build.version.sdk':'35','ro.product.cpu.abi':'arm64-v8a'}[args[-1]]
                elif 'power' in args:result='mWakefulness=Awake'
                elif 'policy' in args:result='showing=false'
                elif '/proc/meminfo' in args:result='MemAvailable: 3145728 kB\n'
                elif 'path' in args:result='package:/data/app/synthetic/base.apk'
                elif 'sha256sum' in args:result=sha(b'fixture')+'  /data/app/synthetic/base.apk'
                else:result=''
                return type('Result',(),{'returncode':1 if 'test' in args else 0,'stdout':result.encode(),'stderr':b''})()
            proc=type('Process',(),{'poll':lambda self:0,'wait':lambda self,timeout:0,'returncode':0})()
            with patch('subprocess.run',side_effect=response),patch('subprocess.Popen',return_value=proc) as popen:
                namespace['main']('run')
            argv=popen.call_args.args[0]
            self.assertEqual(argv[-1],device.RUNNER)
            started=json.loads((root/'run-started.json').read_text())
            self.assertEqual((started['inputCount'],started['maxPlannedStreams'],started['maxPlannedDecode']),(1,2,2))
            with patch('subprocess.run',side_effect=response),patch('subprocess.Popen') as duplicate:
                with self.assertRaises(FileExistsError):namespace['main']('run')
                duplicate.assert_not_called()


if __name__=='__main__':unittest.main(verbosity=2)
