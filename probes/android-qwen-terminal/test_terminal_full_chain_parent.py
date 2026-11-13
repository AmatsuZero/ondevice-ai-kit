"""Six real facade actions against one local fake device; no Android transport."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
QT = ROOT/'probes/android-qwen-terminal'
sys.path[:0] = [str(QT), str(ROOT/'probes/android-terminal-observation')]
from terminal_prepare_fixture import PrepareFixture, APP, TEST, RUN, FP

TARGET = Path(os.environ.get('ODAI_TERMINAL_DRIVER', QT/'device_acceptance.py'))
INSTRUMENT = r'''
if tokens[:2]==['am','instrument']:
 assert tokens==['am','instrument','-w','-r','-e','runId',RUN,'-e','sourceFingerprint','ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5',TEST+'/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation']
 assert claim('run-started.json')['automaticRetry'] is False
 mutate('instrument')
 folder=device/'apps'/APP/'files/w3-qwen-terminal';folder.mkdir(parents=True,exist_ok=True)
 policy=device/'apps'/APP/'no_backup/cpu-arena-off/cpu.config';policy.parent.mkdir(parents=True);policy.write_bytes(b'EnableCpuMemArena=0\n')
 raw=(p/'report-template.json').read_bytes();(folder/('report-'+RUN+'.json')).write_bytes(raw)
 end=time.monotonic()+10;ack=folder/('ack-'+RUN+'.txt')
 while not ack.exists():
  if time.monotonic()>end:sys.exit(22)
  time.sleep(.005)
 print('INSTRUMENTATION_RESULT: terminalAckAttempted=true')
 print('INSTRUMENTATION_RESULT: terminalAckReceived=true')
 print('INSTRUMENTATION_RESULT: terminalReportSha256='+hashlib.sha256(raw).hexdigest())
 print('INSTRUMENTATION_CODE: -1');sys.exit(0)
if tokens==['pidof',APP]:print('123');sys.exit(0)
if tokens==['dumpsys','activity','activities']:
 print('mResumedActivity: ActivityRecord{host u0 io.ondeviceai.demo/.MainActivity t1}');sys.exit(0)
'''
PRIVATE = r'''
if tokens[:2]==['sh','-c']:
 assert app==APP and len(tokens)==3
 child=subprocess.run(['/bin/sh','-c',tokens[2]],cwd=device/'apps'/APP,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 sys.stdout.buffer.write(child.stdout);sys.stderr.buffer.write(child.stderr);sys.exit(child.returncode)
if tokens[:2]==['id','-u']:print(os.getuid());sys.exit(0)
if tokens[:2]==['test','!']:
 assert app==APP and tokens[2]=='-L';sys.exit(1 if mapped(tokens[3],app).is_symlink() else 0)
if tokens[:2]==['stat','-c'] and tokens[2]=='%u %d %i %s':
 s=mapped(tokens[3],app).stat();print(s.st_uid,s.st_dev,s.st_ino,s.st_size);sys.exit(0)
if tokens[:2]==['head','-c'] and tokens[3].startswith('/proc/'):
 if tokens[3]=='/proc/123/stat':print('123 (local fake process) '+' '.join(['S']+['0']*18+['456']))
 elif tokens[3]=='/proc/123/status':print('Pid:\t123\nVmRSS:\t'+('2097152' if (p/'resource-high').exists() else '2048')+' kB')
 elif tokens[3]=='/proc/meminfo':print('MemAvailable:\t3145728 kB')
 else:raise AssertionError(tokens)
 sys.exit(0)
if tokens[:2]==['toybox','ln']:
 assert tokens[2]=='-T' and len(tokens)==5;os.link(mapped(tokens[3],app),mapped(tokens[4],app));sys.exit(0)
if tokens[:2]==['toybox','fsync']:
 fd=os.open(mapped(tokens[2],app),os.O_RDONLY)
 try:os.fsync(fd)
 finally:os.close(fd)
 sys.exit(0)
'''


class TerminalFullChainParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing complete driver')
        spec=importlib.util.spec_from_file_location('full_chain_under_test',TARGET)
        self.driver=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.driver)
        self.f=PrepareFixture(self);f=self.f
        source=f.adb.read_text().replace('import hashlib,json,os,shlex,shutil,sys',
                                           'import hashlib,json,os,shlex,shutil,sys,subprocess,time')
        source=source.replace("('shell','exec-out')", "('shell','exec-out','exec-in')")
        source=source.replace('def mapped(s,app=None):',
            "def mapped(s,app=None):\n if s in ('/data','/data/local'):return device\n if s=='/data/local/tmp':return device/'stage'\n for prefix in ('/data/data/'+APP+'/', '/data/user/0/'+APP+'/'):\n  if s.startswith(prefix):return device/'apps'/APP/s.removeprefix(prefix)")
        source=source.replace('app=None\n',INSTRUMENT+'\napp=None\n')
        source=source.replace("if tokens[:2]==['cat','/proc/self/status']:",PRIVATE+"\nif tokens[:2]==['cat','/proc/self/status']:")
        f.adb.write_text(source)
        cfg='/data/data/'+APP+'/no_backup/cpu-arena-off/cpu.config'
        report=dict(runId=RUN,sourceFingerprint=FP,pid=123,processStartTicks='456',
            terminal=True,status='completed',phase='terminal',activeCaseIndex=None,
            terminalObservationProtocol='ODAI-TERMINAL-ACK-v1',terminalAckTimeoutMillis=30000,
            probeVariant='cpu-arena-off-heap-terminal-ack-v1',
            inputManifestSHA256=f.context['plan']['manifestSHA256'],actualConstructors=1,
            nativeDecodeCalls=2,streamsCreated=2,microphoneStarts=0,networkRequests=0,
            nativePolicyEffectVerified=False,config=dict(provider='cpu:'+cfg,requestedEnableCpuMemArena=False),
            allocatorPolicy=dict(provider='cpu:'+cfg,configPath=cfg,bytes=20,
                                 sha256=hashlib.sha256(b'EnableCpuMemArena=0\n').hexdigest()))
        (f.root/'report-template.json').write_text(json.dumps(report))
        self.guard_count=0;self.actions=0
        def guard():self.guard_count+=1
        self.guard=guard

    def action(self, name):
        f=self.f;self.actions+=1
        with patch.object(self.driver,'ROOT',f.root),patch.object(self.driver,'OUT',f.out), \
             patch.object(self.driver,'BASE',ROOT/'probes/android-qwen-file/device_acceptance.py'), \
             patch.object(self.driver,'EVIDENCE',f.evidence), \
             patch.object(self.driver,'adb',return_value=str(f.adb)), \
             patch.object(Path,'home',return_value=f.root/'home'), \
             patch.object(self.driver,'verify_frozen_contract',side_effect=self.guard):
            return self.driver.main(name)

    def test_all_six_actual_actions_share_run_and_restore_exact_owned_state(self):
        f=self.f;before=len((f.root/'calls.jsonl').read_text().splitlines())
        self.assertEqual(self.action('preflight')['availableBytes'],3*1024**3)
        self.action('prepare')
        self.assertIs(self.action('run')['finishVerified'],True)
        self.assertIs(self.action('collect')['terminalAccepted'],True)
        policy=self.action('collect-policy');self.assertIs(policy['fileBytesMatch'],True)
        self.assertIs(policy['nativePolicyEffectVerified'],False)
        self.assertIs(self.action('restore')['allRestored'],True)
        self.assertEqual([x['label'] for x in f.mutations()].count('instrument'),1)
        self.assertFalse((f.device/'apps'/APP).exists());self.assertFalse((f.device/'apps'/TEST).exists())
        self.assertEqual((f.device/'ordinary.apk').read_bytes(),b'original ordinary app')
        self.assertEqual(self.guard_count,self.actions+len((f.root/'calls.jsonl').read_text().splitlines())-before)
        marker=(f.out/'run-started.json').read_bytes()
        with self.assertRaises((ValueError,OSError)):self.action('run')
        self.assertEqual((f.out/'run-started.json').read_bytes(),marker)
        self.assertEqual([x['label'] for x in f.mutations()].count('instrument'),1)

    def test_real_resource_failure_stops_collects_and_recovers_once(self):
        f=self.f;self.action('prepare');(f.root/'resource-high').touch()
        with self.assertRaises(ValueError) as caught:self.action('run')
        self.assertTrue(caught.exception.__notes__)
        result=json.loads((f.out/'host-result.json').read_text());self.assertIs(result['finishVerified'],False)
        self.assertIs(json.loads((f.out/'restoration.json').read_text())['allRestored'],True)
        labels=[x['label'] for x in f.mutations()]
        self.assertEqual(labels.count('instrument'),1)
        # Lifecycle stop/drain and independent restore each verify and stop APP.
        self.assertEqual(labels.count('force-stop'),2)
        self.assertEqual(labels.count('uninstall'),2)
        self.assertEqual((f.device/'ordinary.apk').read_bytes(),b'original ordinary app')


if __name__=='__main__':unittest.main(verbosity=2)
