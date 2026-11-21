"""Primary tests for an actual fake-ADB child and terminal ACK lifecycle."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(Path(__file__).resolve().parent),
              str(ROOT/'probes/android-terminal-observation')]
import test_terminal_startup_parent as startup_fixture

RUN='w3-qwen-terminal-20261114-01'
APP='io.ondeviceai.acceptance.qwenterminal'
RUNNER=APP+'.test/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation'
INSTRUMENT=r'''
if sys.argv[3:8]==['shell','am','instrument','-w','-r']:
    assert sys.argv[8:]==['-e','runId','w3-qwen-terminal-20261114-01','-e','sourceFingerprint','a'*64,'io.ondeviceai.acceptance.qwenterminal.test/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation']
    import time,hashlib
    (p/'child-started').touch()
    mode=(p/'finish-mode').read_text() if (p/'finish-mode').exists() else 'ok'
    if mode=='early': sys.exit(0)
    deadline=time.monotonic()+10
    ack=p/'remote/files/w3-qwen-terminal/ack-w3-qwen-terminal-20261114-01.txt'
    while not ack.exists():
        if time.monotonic()>deadline: sys.exit(22)
        time.sleep(.005)
    report=(p/'remote/files/w3-qwen-terminal/report-w3-qwen-terminal-20261114-01.json').read_bytes()
    value=json.loads(report)
    print('INSTRUMENTATION_RESULT: terminalAckAttempted=true')
    print('INSTRUMENTATION_RESULT: terminalAckReceived='+('false' if mode=='bad-ack' else 'true'))
    print('INSTRUMENTATION_RESULT: terminalReportSha256='+hashlib.sha256(report).hexdigest())
    print('INSTRUMENTATION_CODE: '+('-1' if value['status']=='completed' else '0'))
    sys.exit(0)
'''


class TerminalRunParent(unittest.TestCase):
    def setUp(self):
        self.fixture=startup_fixture.TerminalStartupParent()
        self.fixture.setUp();self.addCleanup(self.fixture.doCleanups)
        self.f=self.fixture.f;self.module=self.fixture.module
        self.assertTrue(hasattr(self.module,'run_diagnostic'),'missing one-shot lifecycle driver')
        self.f.binding['runId']=RUN
        self.f.final=self.f.remote/('ack-'+RUN+'.txt')
        self.f.stage=self.f.remote/('ack-'+RUN+'.txt.stage')
        self.report=dict(self.fixture.report,runId=RUN,terminal=True,status='completed',phase='terminal')
        old=self.f.remote/'report-fresh-01.json';old.unlink()
        self.raw=json.dumps(self.report).encode()
        (self.f.remote/('report-'+RUN+'.json')).write_bytes(self.raw)
        source=self.f.adb.read_text().replace('fresh-01',RUN)
        needle="assert sys.argv[1:3]==['-s','serial-01'] and len(sys.argv)==5"
        self.f.adb.write_text(source.replace(needle,INSTRUMENT+'\n'+needle))
        self.now=0;self.guards=0;self.launch_checks=0;self.stops=0
        self.children=[]

    def clock(self):
        self.assertFalse(self.f.final.exists(),'clock after ACK publication')
        return self.now

    def verify_launch(self):
        self.launch_checks+=1
        self.assertFalse((self.f.output/'run-started.json').exists())

    def before_transport(self): self.guards+=1
    def stop_alias(self): self.stops+=1

    def popen(self,argv,**kwargs):
        self.assertTrue((self.f.output/'run-started.json').is_file(),'consumption must precede Popen')
        self.assertGreaterEqual(self.guards,1)
        child=subprocess.Popen(argv,**kwargs);self.children.append(child)
        self.addCleanup(self.cleanup_child,child)
        return child

    def cleanup_child(self,child):
        if child.poll() is None:
            child.terminate()
            try: child.wait(timeout=2)
            except subprocess.TimeoutExpired: child.kill();child.wait(timeout=2)

    def run_once(self,**overrides):
        kw=dict(adb=str(self.f.adb),serial='serial-01',source_fingerprint='a'*64,
                verify_launch=self.verify_launch,before_transport=self.before_transport,
                stop_alias=self.stop_alias,popen=self.popen,monotonic_ns=self.clock,
                sleep=lambda value:None)
        kw.update(overrides)
        return self.module.run_diagnostic(self.f.output,**kw)

    def test_actual_child_ack_and_finish_are_required_without_post_ack_clock(self):
        result=self.run_once()
        self.assertTrue(self.f.final.is_file())
        self.assertEqual(result['finish']['instrumentationCode'],-1)
        self.assertIs(result['qualityAccepted'],False)
        self.assertEqual(self.launch_checks,1);self.assertEqual(self.stops,0)
        self.assertEqual((self.f.output/'terminal-observed.json').read_bytes(),self.raw)
        self.assertIs(json.loads((self.f.output/'host-result.json').read_bytes())['finishVerified'],True)
        self.assertEqual(self.children[0].returncode,0)

    def test_popen_failure_consumes_run_preserves_original_and_cannot_relaunch(self):
        failure=OSError('Popen failed sentinel')
        def fail(*args,**kw):
            self.assertTrue((self.f.output/'run-started.json').is_file());raise failure
        with self.assertRaises(OSError) as caught: self.run_once(popen=fail)
        self.assertIs(caught.exception,failure)
        self.assertEqual(json.loads((self.f.output/'host-result.json').read_bytes())['primaryError']['class'],'OSError')
        with self.assertRaises((OSError,ValueError)): self.run_once(popen=lambda *a,**k:self.fail('relaunch'))

    def test_launch_gate_failure_precedes_consumption_and_child(self):
        failure=ValueError('locked identity')
        def gate(): raise failure
        with self.assertRaises(ValueError) as caught: self.run_once(verify_launch=gate)
        self.assertIs(caught.exception,failure)
        self.assertEqual(list(self.f.output.iterdir()),[])
        self.assertEqual(self.children,[])

    def test_total_clock_origin_is_before_popen_not_startup(self):
        def slow_launch(*args,**kwargs):
            self.now=241_000_000_000
            return self.popen(*args,**kwargs)
        with self.assertRaises(ValueError): self.run_once(popen=slow_launch)
        self.assertFalse(self.f.final.exists())
        self.assertEqual(self.stops,1)

    def test_false_bundle_ack_does_not_pass_on_shell_exit_zero(self):
        (self.f.root/'finish-mode').write_text('bad-ack')
        with self.assertRaises(ValueError): self.run_once()
        self.assertTrue(self.f.final.exists())
        result=json.loads((self.f.output/'host-result.json').read_bytes())
        self.assertIs(result['finishVerified'],False)
        self.assertIs(result['qualityAccepted'],False)

    def test_failed_report_can_be_observed_without_quality_acceptance(self):
        self.report['status']='failed';self.raw=json.dumps(self.report).encode()
        (self.f.remote/('report-'+RUN+'.json')).write_bytes(self.raw)
        result=self.run_once()
        self.assertEqual(result['finish']['instrumentationCode'],0)
        self.assertEqual(result['finish']['terminalStatus'],'failed')
        self.assertIs(result['qualityAccepted'],False)

    def test_resource_failure_stops_exact_alias_and_preserves_primary_error(self):
        source=self.f.adb.read_text();self.f.adb.write_text(source.replace('VmRSS:\\t2048 kB','VmRSS:\\t2097152 kB'))
        with self.assertRaises(ValueError): self.run_once()
        self.assertEqual(self.stops,1)
        self.assertFalse(self.f.final.exists())
        result=json.loads((self.f.output/'host-result.json').read_bytes())
        self.assertIs(result['finishVerified'],False)

    def test_cleanup_failure_cannot_mask_original_publication_or_transport_error(self):
        failure=RuntimeError('frozen transport rejection')
        def runner(*args): raise failure
        def stop(): self.stops+=1;raise OSError('cleanup failed sentinel')
        with self.assertRaises(RuntimeError) as caught: self.run_once(runner=runner,stop_alias=stop)
        self.assertIs(caught.exception,failure)
        result=json.loads((self.f.output/'host-result.json').read_bytes())
        self.assertEqual(result['primaryError']['message'],str(failure))
        self.assertTrue(any('cleanup failed sentinel' in row['message'] for row in result['secondaryErrors']))

    def test_initial_freeze_guard_after_consumption_failure_still_records_no_relaunch(self):
        failure=ValueError('freeze drift before Popen')
        def guard(): raise failure
        with self.assertRaises(ValueError) as caught: self.run_once(before_transport=guard)
        self.assertIs(caught.exception,failure)
        self.assertTrue((self.f.output/'run-started.json').is_file())
        self.assertFalse((self.f.root/'child-started').exists())

    def test_each_actual_transport_and_launch_has_one_fresh_guard(self):
        self.run_once()
        self.assertEqual(self.guards,len(self.f.calls()))
        for argv in self.f.calls():
            self.assertNotIn('w3-qwen-heap-20261111-01',' '.join(argv))
            self.assertNotIn('w3-qwen-file-20261111-01',' '.join(argv))

    def test_post_ack_wait_timeout_preserves_original_and_bounds_host_cleanup(self):
        waits=[];failure=subprocess.TimeoutExpired('actual-fake-adb',15)
        parent=self
        class Proxy:
            def __init__(self,child): self.child=child
            @property
            def returncode(self): return self.child.returncode
            def poll(self):
                parent.assertFalse(parent.f.final.exists(),'post-ACK process poll')
                return self.child.poll()
            def wait(self,timeout):
                waits.append(timeout)
                if timeout==15: raise failure
                return self.child.wait(timeout=timeout)
            def terminate(self): return self.child.terminate()
            def kill(self): return self.child.kill()
        def launch(*args,**kw): return Proxy(self.popen(*args,**kw))
        with self.assertRaises(subprocess.TimeoutExpired) as caught: self.run_once(popen=launch)
        self.assertIs(caught.exception,failure)
        self.assertTrue(self.f.final.exists())
        self.assertEqual(waits[0],15)
        self.assertTrue(all(0<value<=5 for value in waits[1:]))
        self.assertEqual(self.stops,1)

    def test_success_wait_does_not_poll_process_after_ack(self):
        parent=self
        class Proxy:
            def __init__(self,child): self.child=child
            @property
            def returncode(self): return self.child.returncode
            def poll(self):
                parent.assertFalse(parent.f.final.exists(),'post-ACK process poll')
                return self.child.poll()
            def wait(self,timeout):
                parent.assertEqual(timeout,15)
                return self.child.wait(timeout=timeout)
            def terminate(self): return self.child.terminate()
            def kill(self): return self.child.kill()
        def launch(*args,**kw): return Proxy(self.popen(*args,**kw))
        self.assertIs(self.run_once(popen=launch)['finishVerified'],True)

    def test_invalid_transport_identity_refuses_before_consumption_or_gate(self):
        for n,override in enumerate(({'source_fingerprint':'z'*64},{'serial':'bad serial'},
                                     {'adb':'bad\0adb'})):
            self.f.output=self.f.root/f'invalid-run-{n}';self.f.output.mkdir()
            before=self.launch_checks
            with self.subTest(override=override):
                with self.assertRaises((TypeError,ValueError)): self.run_once(**override)
                self.assertEqual(self.launch_checks,before)
                self.assertEqual(list(self.f.output.iterdir()),[])

    def test_actual_publication_uncertainty_is_preserved_in_failure_receipt(self):
        from adb_terminal_ack_publisher import RemoteAckPublicationError
        (self.f.root/'dir-sync-fault').touch()
        with self.assertRaises(RemoteAckPublicationError) as caught: self.run_once()
        self.assertEqual(caught.exception.phase,'directory-sync')
        self.assertIs(caught.exception.publication_possible,True)
        row=json.loads((self.f.output/'host-result.json').read_bytes())['primaryError']
        self.assertEqual(row['phase'],'directory-sync')
        self.assertIs(row['publication_possible'],True)
        self.assertIs(row['stage_may_remain'],True)
        self.assertIs(row['durability_verified'],False)

    def test_published_marker_failure_is_consumed_and_recorded_without_child(self):
        import terminal_file_io
        original=terminal_file_io.write_new_durable
        failure=terminal_file_io.PublishedButNotDurableError('marker directory sync failed')
        def writer(root,relative,payload):
            original(root,relative,payload)
            if relative=='run-started.json': raise failure
        with patch.object(terminal_file_io,'write_new_durable',side_effect=writer):
            with self.assertRaises(terminal_file_io.PublishedButNotDurableError) as caught: self.run_once()
        self.assertIs(caught.exception,failure)
        self.assertEqual(self.children,[])
        self.assertTrue((self.f.output/'run-started.json').is_file())
        self.assertEqual(json.loads((self.f.output/'host-result.json').read_bytes())['primaryError']['class'],
                         'PublishedButNotDurableError')

    def test_host_cleanup_errors_are_recorded_without_masking_original(self):
        failure=RuntimeError('original transport error')
        def runner(*args): raise failure
        parent=self
        class Proxy:
            def __init__(self,child): self.child=child
            @property
            def returncode(self): return self.child.returncode
            def poll(self): return self.child.poll()
            def terminate(self): raise OSError('host terminate failed')
            def kill(self): raise OSError('host kill failed')
            def wait(self,timeout): return self.child.wait(timeout=timeout)
        def launch(*args,**kw): return Proxy(self.popen(*args,**kw))
        with self.assertRaises(RuntimeError) as caught: self.run_once(popen=launch,runner=runner)
        self.assertIs(caught.exception,failure)
        errors=json.loads((self.f.output/'host-result.json').read_bytes())['secondaryErrors']
        self.assertTrue(any(row['message']=='host terminate failed' for row in errors))
        self.assertTrue(any(row['message']=='host kill failed' for row in errors))

    def test_log_flush_failure_is_not_silently_accepted(self):
        actual_open=open;failure=OSError('instrumentation log flush failed')
        class LogProxy:
            def __init__(self,stream): self.stream=stream
            def fileno(self): return self.stream.fileno()
            def flush(self): raise failure
            def close(self): return self.stream.close()
        def opener(*args,**kw): return LogProxy(actual_open(*args,**kw))
        with patch.object(self.module,'open',side_effect=opener,create=True):
            with self.assertRaises(OSError) as caught: self.run_once()
        self.assertIs(caught.exception,failure)
        row=json.loads((self.f.output/'host-result.json').read_bytes())
        self.assertIs(row['finishVerified'],False)

    def test_prelaunch_frozen_guard_crossing_budget_prevents_popen(self):
        def slow_guard():
            self.before_transport()
            self.now=241_000_000_000
        with self.assertRaises(ValueError): self.run_once(before_transport=slow_guard)
        self.assertTrue((self.f.output/'run-started.json').is_file())
        self.assertEqual(self.children,[])

    def test_boolean_process_returncode_is_not_success(self):
        class Proxy:
            def __init__(self,child): self.child=child
            @property
            def returncode(self): return False
            def poll(self): return self.child.poll()
            def wait(self,timeout): return self.child.wait(timeout=timeout)
            def terminate(self): return self.child.terminate()
            def kill(self): return self.child.kill()
        def launch(*args,**kw): return Proxy(self.popen(*args,**kw))
        with self.assertRaises(ValueError): self.run_once(popen=launch)
        self.assertIs(json.loads((self.f.output/'host-result.json').read_bytes())['finishVerified'],False)

    def test_interrupt_after_consumption_preserves_evidence_and_stops_child(self):
        failure=KeyboardInterrupt('user interrupted diagnostic')
        def runner(*args): raise failure
        with self.assertRaises(KeyboardInterrupt) as caught: self.run_once(runner=runner)
        self.assertIs(caught.exception,failure)
        self.assertEqual(self.stops,1)
        self.assertTrue((self.f.output/'run-started.json').is_file())
        row=json.loads((self.f.output/'host-result.json').read_bytes())
        self.assertEqual(row['primaryError']['class'],'KeyboardInterrupt')
        self.assertIs(row['finishVerified'],False)

    def test_system_exit_survives_secondary_interrupt_during_exact_stop(self):
        failure=SystemExit('primary exit sentinel')
        def runner(*args): raise failure
        def stop():
            self.stops+=1
            raise KeyboardInterrupt('secondary stop interrupt')
        with self.assertRaises(SystemExit) as caught:
            self.run_once(runner=runner,stop_alias=stop)
        self.assertIs(caught.exception,failure)
        self.assertEqual(self.stops,1)
        self.assertIsNotNone(self.children[0].returncode)
        row=json.loads((self.f.output/'host-result.json').read_bytes())
        self.assertEqual(row['primaryError']['class'],'SystemExit')
        self.assertEqual(row['secondaryErrors'][0]['class'],'KeyboardInterrupt')
        self.assertIs(row['finishVerified'],False)

    def test_finish_hash_uses_terminal_raw_bytes_after_first_running_report(self):
        running=dict(self.report,terminal=False,status='running',phase='constructor-before')
        initial_raw=json.dumps(running).encode()
        remote=self.f.remote/('report-'+RUN+'.json');remote.write_bytes(initial_raw)
        reads=0
        def runner(argv,timeout,stdin):
            nonlocal reads
            raw=subprocess.run(argv,input=stdin,capture_output=True,check=True,timeout=timeout).stdout
            if 'head -c 1048577' in argv[-1]:
                reads+=1
                if reads==1: remote.write_bytes(self.raw)
            return raw
        result=self.run_once(runner=runner)
        self.assertEqual((self.f.output/'startup-report.json').read_bytes(),initial_raw)
        self.assertEqual((self.f.output/'terminal-observed.json').read_bytes(),self.raw)
        self.assertNotEqual(hashlib.sha256(initial_raw).hexdigest(),hashlib.sha256(self.raw).hexdigest())
        self.assertEqual(result['finish']['terminalReportSha256'],hashlib.sha256(self.raw).hexdigest())


if __name__=='__main__': unittest.main(verbosity=2)
