"""Parent acceptance for pure adaptation of the immutable FILE driver."""
import ast
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'probes/android-qwen-file/device_acceptance.py'
TARGET=Path(os.environ.get('ODAI_SOURCE_ADAPTER',str(ROOT/'probes/android-qwen-terminal/terminal_source_adapter.py')))


class TerminalSourceAdapterParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(),'missing pure terminal source adapter')
        spec=importlib.util.spec_from_file_location('source_adapter_under_test',TARGET)
        self.module=importlib.util.module_from_spec(spec)
        with patch('subprocess.run',side_effect=AssertionError('subprocess on import')):
            spec.loader.exec_module(self.module)
        self.raw=BASE.read_text();self.old_sha=hashlib.sha256(BASE.read_bytes()).hexdigest()

    def adapt(self):return self.module.adapt_source(self.raw)

    def namespace(self):
        adapted=self.adapt();ns={'__name__':'__main__','__file__':str(BASE)}
        with patch('subprocess.run',side_effect=AssertionError('transport during source evaluation')), \
             patch('subprocess.Popen',side_effect=AssertionError('Popen during source evaluation')):
            exec(compile(adapted,'<adapted source>','exec'),ns)
        return ns

    def test_source_adaptation_is_pure_deterministic_and_old_file_unchanged(self):
        before=hashlib.sha256(BASE.read_bytes()).hexdigest()
        with patch('subprocess.run',side_effect=AssertionError('transport while adapting')), \
             patch('subprocess.Popen',side_effect=AssertionError('launch while adapting')):
            first=self.adapt();second=self.adapt()
        self.assertIs(type(first),str);self.assertEqual(first,second)
        ast.parse(first);self.assertEqual(hashlib.sha256(BASE.read_bytes()).hexdigest(),before)

    def test_changed_or_nonexact_source_is_rejected_before_callbacks(self):
        class Source(str):pass
        for raw in [Source(self.raw),self.raw.encode(),None,'',self.raw+'\n',self.raw.replace('130','131',1)]:
            with self.subTest(kind=type(raw)):
                with self.assertRaises((ValueError,TypeError)):self.module.adapt_source(raw)

    def test_inherited_safe_helper_bodies_are_preserved(self):
        original=ast.parse(self.raw);adapted=ast.parse(self.adapt())
        def functions(tree):return {n.name:ast.dump(n,include_attributes=False) for n in ast.walk(tree) if isinstance(n,ast.FunctionDef)}
        a=functions(original);b=functions(adapted)
        for name in ('digest','save','budget_reason','parse_kb','text','exists','identity','gate','ordinary','ordinary_models','installed_sha','verify_installed','transfer'):
            with self.subTest(name=name):self.assertEqual(a[name],b[name])

    def test_generated_source_has_no_old_run_loop_or_direct_popen(self):
        source=self.adapt();tree=ast.parse(source)
        self.assertNotIn("elif action == 'run':",source)
        self.assertNotIn('proc.poll()',source)
        self.assertFalse(any(isinstance(n,ast.Attribute) and n.attr=='Popen' for n in ast.walk(tree)))
        self.assertNotIn('130',source)
        self.assertIn("plan['maxNativeDecodeCalls'] == 2 and plan['caseCount'] == 1",source)
        self.assertIn('_terminal_dispatch(action, locals())',source)
        self.namespace()

    def fake_context(self):
        temp=tempfile.TemporaryDirectory(prefix='terminal-adapter-parent-');self.addCleanup(temp.cleanup)
        root=Path(temp.name).resolve();out=root/'out';evidence=root/'evidence';out.mkdir();evidence.mkdir()
        fp='a'*64;run='w3-qwen-terminal-20261114-01';app='io.ondeviceai.acceptance.qwenterminal'
        for name,value in [('package-verified.json',{'sourceFingerprint':fp,'files':{}}),('candidate-accepted.json',{'sourceFiles':{}}),('production-before.json',{}),('run-plan.json',{'runId':run,'sourceFingerprint':fp,'manifestSHA256':'8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f','maxNativeDecodeCalls':2,'caseCount':1})]:
            (out/name).write_text(json.dumps(value))
        (evidence/'android-target-local.json').write_text(json.dumps({'serial':'fake-serial'}))
        (evidence/'model-manifest.json').write_text(json.dumps({'directory':str(root),'files':{}}))
        fake=root/'adb';fake.write_text('#!'+sys.executable+'\n'+r'''
import json,sys
from pathlib import Path
p=Path(__file__).resolve().parent
with (p/'calls.jsonl').open('a') as f:f.write(json.dumps(sys.argv[1:])+'\n')
args=sys.argv[1:]
assert args[:2]==['-s','fake-serial']
if args[2:4]==['shell','getprop']:
 print({'ro.product.model':'TECNO KM9','ro.build.version.sdk':'35','ro.product.cpu.abi':'arm64-v8a'}[args[4]])
elif args[2:]==['shell','dumpsys','activity','processes']:print('No active instrumentation')
else:raise AssertionError(args)
''');fake.chmod(0o700)
        ns=self.namespace();ns.update(ROOT=root,OUT=out,EVIDENCE=evidence,APP=app,TEST=app+'.test',RUN=run,STAGE='/data/local/tmp/'+run,REPORT='files/w3-qwen-terminal/report-'+run+'.json',RUNNER=app+'.test/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation')
        # Resolve only the inherited Path.home adb executable to our actual fake child.
        home=root/'home';target=home/'Library/Android/sdk/platform-tools';target.mkdir(parents=True);(target/'adb').symlink_to(fake)
        return root,ns,home

    def test_actual_fake_adb_calls_are_guarded_and_dispatch_keeps_overridden_identity(self):
        root,ns,home=self.fake_context();guards=[];dispatch=[]
        def guard():
            calls=(root/'calls.jsonl').read_text().splitlines() if (root/'calls.jsonl').exists() else []
            guards.append(len(calls))
        sentinel=object()
        def route(action,context):dispatch.append((action,context));return sentinel
        ns.update(_terminal_before_transport=guard,_terminal_dispatch=route)
        with patch.object(Path,'home',return_value=home):result=ns['main']('prepare')
        self.assertIs(result,sentinel);self.assertEqual(guards,[0,1,2,3])
        self.assertEqual(dispatch[0][0],'prepare');context=dispatch[0][1]
        self.assertEqual(context['plan']['caseCount'],1)
        self.assertEqual(context['current']['ro.product.model'],'TECNO KM9')
        self.assertEqual(len((root/'calls.jsonl').read_text().splitlines()),4)

    def test_guard_failure_prevents_first_real_transport_and_dispatch(self):
        root,ns,home=self.fake_context();failure=ValueError('frozen changed sentinel')
        def guard():raise failure
        ns.update(_terminal_before_transport=guard,_terminal_dispatch=lambda *a:self.fail('dispatch after failed guard'))
        with patch.object(Path,'home',return_value=home):
            with self.assertRaises(ValueError) as caught:ns['main']('prepare')
        self.assertIs(caught.exception,failure);self.assertFalse((root/'calls.jsonl').exists())

    def test_original_batch_plan_refuses_before_transport_or_dispatch(self):
        root,ns,home=self.fake_context();path=ns['OUT']/'run-plan.json'
        plan=json.loads(path.read_text());plan.update(maxNativeDecodeCalls=130,caseCount=65)
        path.write_text(json.dumps(plan))
        ns.update(_terminal_before_transport=lambda:self.fail('batch plan reached guard'),
                  _terminal_dispatch=lambda *a:self.fail('batch plan reached dispatch'))
        with patch.object(Path,'home',return_value=home):
            with self.assertRaises(AssertionError) as caught:ns['main']('prepare')
        self.assertNotIn('reached',str(caught.exception));self.assertFalse((root/'calls.jsonl').exists())

    def test_generated_identity_is_new_even_before_outer_namespace_override(self):
        ns=self.namespace()
        self.assertEqual(ns['APP'],'io.ondeviceai.acceptance.qwenterminal')
        self.assertEqual(ns['TEST'],ns['APP']+'.test')
        self.assertEqual(ns['RUN'],'w3-qwen-terminal-20261114-01')
        self.assertEqual(ns['OUT'],ROOT/'artifacts/verification/w3-qwen-terminal-20261114')
        self.assertEqual(ns['REPORT'],'files/w3-qwen-terminal/report-'+ns['RUN']+'.json')
        self.assertEqual(ns['RUNNER'],ns['TEST']+'/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation')


if __name__=='__main__':unittest.main(verbosity=2)
