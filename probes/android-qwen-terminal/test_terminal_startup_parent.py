"""Primary acceptance: startup discovery uses actual local fake-ADB subprocesses."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
OBS = ROOT / 'probes/android-terminal-observation'
sys.path.insert(0, str(OBS))
import test_remote_observation_parent as fixtures
from terminal_polling import _PollClock

TARGET = Path(os.environ.get('ODAI_LIFECYCLE_DIR', str(ROOT / 'probes/android-qwen-terminal'))) / 'terminal_device_lifecycle.py'

DISCOVERY = r'''
if sys.argv[3:] == ['shell', 'pidof', 'io.ondeviceai.acceptance.qwenterminal']:
    mode=(p/'pid-mode').read_text() if (p/'pid-mode').exists() else '123'
    if mode == 'none': sys.exit(1)
    sys.stdout.write(mode+'\n');sys.exit(0)
if len(sys.argv)==5 and sys.argv[3]=='exec-out' and 'ODAI_STARTUP_' in sys.argv[4]:
    remote=shlex.split(sys.argv[4]);assert remote[:4]==['run-as','io.ondeviceai.acceptance.qwenterminal','sh','-c']
    assert 'report-fresh-01.json' in remote[4]
    target=p/'remote/files/w3-qwen-terminal/report-fresh-01.json'
    if target.is_symlink(): sys.exit(17)
    sys.stdout.write('ODAI_STARTUP_PRESENT\n' if target.exists() else 'ODAI_STARTUP_ABSENT\n');sys.exit(0)
'''


class TerminalStartupParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing startup lifecycle implementation')
        spec = importlib.util.spec_from_file_location('terminal_lifecycle_under_test', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.f = fixtures.RemoteObservationParent()
        self.f.setUp()
        self.addCleanup(self.f.tearDown)
        source = self.f.adb.read_text()
        needle = "assert sys.argv[1:3]==['-s','serial-01'] and len(sys.argv)==5"
        self.assertEqual(source.count(needle), 1)
        self.f.adb.write_text(source.replace(needle, DISCOVERY+'\n'+needle))
        self.now = 0
        self.clock = _PollClock(lambda: self.now)
        self.clock()
        self.polls = 0
        self.report = dict(runId='fresh-01', sourceFingerprint='a'*64,
                           pid=123, processStartTicks='456', terminal=False,
                           status='running', phase='constructor-before', activeCaseIndex=None,
                           terminalObservationProtocol='ODAI-TERMINAL-ACK-v1',
                           terminalAckTimeoutMillis=30000,
                           probeVariant='cpu-arena-off-heap-terminal-ack-v1')
        self.set_report(self.report)

    def set_report(self, report):
        self.raw = json.dumps(report).encode()
        (self.f.remote/'report-fresh-01.json').write_bytes(self.raw)

    def poll(self):
        self.polls += 1
        return None

    def sleep(self, seconds):
        self.assertEqual(seconds, 0.25)
        self.now += 5_000_000_000

    def wait(self, **overrides):
        values = dict(adb=str(self.f.adb), serial='serial-01',
                      package='io.ondeviceai.acceptance.qwenterminal',
                      run_id='fresh-01', source_fingerprint='a'*64,
                      process_poll=self.poll, clock=self.clock, started_ns=0,
                      sleep=self.sleep)
        values.update(overrides)
        return self.module.wait_for_startup(self.f.output, **values)

    def test_actual_process_report_binding_and_exact_raw_persistence(self):
        result = self.wait()
        self.assertEqual(result['binding'], self.f.binding)
        self.assertEqual(result['initial_progress'], {
            'phase':'constructor-before','activeCaseIndex':None,'startedNs':0})
        self.assertEqual((self.f.output/'startup-report.json').read_bytes(), self.raw)
        receipt = json.loads((self.f.output/'startup-observed.json').read_bytes())
        self.assertEqual(receipt['binding'], self.f.binding)
        self.assertEqual(receipt['reportSHA256'], hashlib.sha256(self.raw).hexdigest())
        self.assertEqual(receipt['initialProgress'], result['initial_progress'])
        self.assertGreaterEqual(len(self.f.calls()), 7)
        self.assertFalse(self.f.final.exists())

    def test_exact_twenty_seconds_accepts_and_later_refuses_before_poll(self):
        self.now = 20_000_000_000
        self.assertEqual(self.wait()['binding'], self.f.binding)
        with self.subTest('expired'):
            other=self.f.root/'expired';other.mkdir()
            self.now=20_000_000_001
            polls=self.polls;calls=len(self.f.calls())
            with self.assertRaises(ValueError):
                self.module.wait_for_startup(other, adb=str(self.f.adb),serial='serial-01',
                    package='io.ondeviceai.acceptance.qwenterminal',run_id='fresh-01',
                    source_fingerprint='a'*64,process_poll=self.poll,clock=self.clock,
                    started_ns=0,sleep=self.sleep)
            self.assertEqual(self.polls,polls)
            self.assertEqual(len(self.f.calls()),calls)

    def test_no_process_and_no_report_expire_without_ack(self):
        (self.f.root/'pid-mode').write_text('none')
        with self.assertRaises(ValueError): self.wait()
        self.assertFalse((self.f.output/'startup-observed.json').exists())
        self.assertFalse(self.f.final.exists())

    def test_existing_process_without_first_report_expires(self):
        (self.f.remote/'report-fresh-01.json').unlink()
        with self.assertRaises(ValueError): self.wait()
        self.assertFalse((self.f.output/'startup-report.json').exists())
        self.assertFalse(self.f.final.exists())

    def test_early_exit_and_invalid_process_poll_do_not_transport(self):
        for value in (0,1,True,'running'):
            with self.subTest(value=value):
                out=self.f.root/('exit-'+repr(value));out.mkdir()
                with self.assertRaises((ValueError,RuntimeError)):
                    self.module.wait_for_startup(out,adb=str(self.f.adb),serial='serial-01',
                        package='io.ondeviceai.acceptance.qwenterminal',run_id='fresh-01',
                        source_fingerprint='a'*64,process_poll=lambda:value,clock=self.clock,
                        started_ns=0,sleep=self.sleep)
                self.assertEqual(self.f.calls(),[])

    def test_invalid_or_unstarted_clock_configuration_precedes_every_side_effect(self):
        cases=[{'source_fingerprint':'bad'},{'run_id':'../old'}, {'started_ns':True},
               {'started_ns':1},{'clock':lambda:0},{'clock':_PollClock(lambda:0)},
               {'serial':'bad serial'}, {'process_poll':None}, {'runner':False}]
        for n,overrides in enumerate(cases):
            self.f.output=self.f.root/f'invalid-config-{n}'
            self.f.output.mkdir()
            with self.subTest(overrides=overrides):
                with self.assertRaises((ValueError,TypeError)): self.wait(**overrides)
                self.assertEqual(self.f.calls(),[])
                self.assertEqual(self.polls,0)
                self.assertEqual(list(self.f.output.iterdir()),[])

    def test_stale_or_linked_output_precedes_clock_and_transport(self):
        for name in ('startup-start.json','startup-report.json','startup-observed.json'):
            path=self.f.output/name;path.write_bytes(b'KEEP')
            with self.subTest(name=name):
                with self.assertRaises((ValueError,OSError)): self.wait()
                self.assertEqual(path.read_bytes(),b'KEEP')
                self.assertEqual(self.f.calls(),[])
                self.assertEqual(self.polls,0)
            path.unlink()

    def test_malformed_pid_and_report_identity_fail_closed(self):
        (self.f.root/'pid-mode').write_text('123 124')
        with self.assertRaises(ValueError): self.wait()
        self.assertFalse((self.f.output/'startup-observed.json').exists())

    def test_report_metadata_mismatch_never_persists_verified_receipt(self):
        self.report['processStartTicks']='457';self.set_report(self.report)
        with self.assertRaises(ValueError): self.wait()
        self.assertFalse((self.f.output/'startup-observed.json').exists())
        self.assertFalse(self.f.final.exists())

    def test_resource_rejection_precedes_report_acceptance(self):
        source=self.f.adb.read_text();self.f.adb.write_text(source.replace('VmRSS:\\t2048 kB','VmRSS:\\t2097152 kB'))
        with self.assertRaises(ValueError): self.wait()
        self.assertFalse((self.f.output/'startup-report.json').exists())

    def test_transport_and_clock_failure_preserve_original_error(self):
        failure=RuntimeError('guard rejected changed freeze')
        def runner(*args): raise failure
        with self.assertRaises(RuntimeError) as caught: self.wait(runner=runner)
        self.assertIs(caught.exception,failure)
        self.assertFalse((self.f.output/'startup-observed.json').exists())

    def test_every_startup_callback_crossing_deadline_stops_before_next_transport(self):
        for boundary in range(1,9):
            with self.subTest(boundary=boundary):
                out=self.f.root/f'cross-{boundary}';out.mkdir();self.now=0
                clock=_PollClock(lambda:self.now);clock();count=0
                def runner(argv,timeout,stdin):
                    nonlocal count
                    count+=1
                    result=subprocess.run(argv,input=stdin,capture_output=True,check=True,timeout=timeout).stdout
                    if count==boundary: self.now=20_000_000_001
                    return result
                with self.assertRaises(ValueError):
                    self.module.wait_for_startup(out,adb=str(self.f.adb),serial='serial-01',
                        package='io.ondeviceai.acceptance.qwenterminal',run_id='fresh-01',
                        source_fingerprint='a'*64,process_poll=self.poll,clock=clock,
                        started_ns=0,sleep=self.sleep,runner=runner)
                self.assertEqual(count,boundary)
                self.assertFalse((out/'startup-observed.json').exists())

    def test_terminal_first_report_keeps_real_phase_and_case(self):
        self.report.update(terminal=True,status='failed',phase='decode',activeCaseIndex=0)
        self.set_report(self.report)
        result=self.wait()
        self.assertEqual(result['initial_progress'],{'phase':'decode','activeCaseIndex':0,'startedNs':0})
        self.assertFalse(self.f.final.exists())

    def test_disappeared_or_reused_pid_during_missing_report_is_not_retried(self):
        (self.f.remote/'report-fresh-01.json').unlink()
        def disappear(seconds):
            self.now+=1_000_000_000
            (self.f.root/'pid-mode').write_text('none')
        with self.assertRaises((RuntimeError,ValueError)): self.wait(sleep=disappear)
        self.assertLess(self.now,20_000_000_000)
        self.assertFalse((self.f.output/'startup-observed.json').exists())

    def test_clock_regression_and_total_budget_do_not_reset_at_startup(self):
        self.now=241_000_000_000
        with self.assertRaises(ValueError): self.wait()
        self.assertEqual(self.polls,0)
        self.assertEqual(self.f.calls(),[])

    def test_process_poll_crossing_startup_deadline_prevents_first_transport(self):
        def poll():
            self.now=20_000_000_001
            return None
        with self.assertRaises(ValueError): self.wait(process_poll=poll)
        self.assertEqual(self.f.calls(),[])

    def test_linked_remote_report_never_becomes_verified_receipt(self):
        target=self.f.remote/'report-fresh-01.json'
        other=self.f.root/'external-report.json';other.write_bytes(self.raw)
        target.unlink();target.symlink_to(other)
        with self.assertRaises((ValueError,subprocess.CalledProcessError)): self.wait()
        self.assertFalse((self.f.output/'startup-observed.json').exists())

    def test_same_pid_with_changed_start_ticks_refuses_before_next_sample(self):
        (self.f.remote/'report-fresh-01.json').unlink()
        def reuse(seconds):
            self.now+=1_000_000_000
            source=self.f.adb.read_text()
            self.f.adb.write_text(source.replace("['456']", "['457']"))
        with self.assertRaises((ValueError,RuntimeError)): self.wait(sleep=reuse)
        self.assertLess(self.now,20_000_000_000)
        self.assertFalse((self.f.output/'startup-observed.json').exists())

    def test_resource_budget_is_rechecked_while_waiting_for_first_report(self):
        target=self.f.remote/'report-fresh-01.json';target.unlink()
        def grow(seconds):
            self.now+=1_000_000_000
            source=self.f.adb.read_text()
            self.f.adb.write_text(source.replace('VmRSS:\\t2048 kB','VmRSS:\\t2097152 kB'))
            target.write_bytes(self.raw)
        with self.assertRaises(ValueError): self.wait(sleep=grow)
        self.assertFalse((self.f.output/'startup-observed.json').exists())

    def test_presence_marker_is_exact_bytes_not_trimmed_or_coerced(self):
        class BytesSubclass(bytes): pass
        for n,value in enumerate((b' ODAI_STARTUP_PRESENT\n',b'ODAI_STARTUP_PRESENT \n',b'ODAI_STARTUP_PRESENT',
                bytearray(b'ODAI_STARTUP_PRESENT\n'),BytesSubclass(b'ODAI_STARTUP_PRESENT\n'))):
            out=self.f.root/f'marker-{n}';out.mkdir()
            def runner(argv,timeout,stdin):
                result=subprocess.run(argv,input=stdin,capture_output=True,check=True,timeout=timeout).stdout
                return value if 'ODAI_STARTUP_' in argv[-1] else result
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    self.module.wait_for_startup(out,adb=str(self.f.adb),serial='serial-01',
                        package='io.ondeviceai.acceptance.qwenterminal',run_id='fresh-01',
                        source_fingerprint='a'*64,process_poll=self.poll,clock=self.clock,
                        started_ns=0,sleep=self.sleep,runner=runner)
                self.assertFalse((out/'startup-observed.json').exists())

    def test_process_poll_int_subclass_is_invalid_before_transport(self):
        class IntSubclass(int): pass
        with self.assertRaises(ValueError): self.wait(process_poll=lambda:IntSubclass(0))
        self.assertEqual(self.f.calls(),[])

    def test_startup_configuration_and_raw_readers_reject_subclasses(self):
        class IntSubclass(int): pass
        class BytesSubclass(bytes): pass
        class ClockSubclass(_PollClock): pass
        for n,overrides in enumerate(({'started_ns':IntSubclass(0)},
                {'sleep':None},{'clock':ClockSubclass(lambda:0)})):
            self.f.output=self.f.root/f'exact-config-{n}';self.f.output.mkdir()
            calls_before=len(self.f.calls())
            if 'clock' in overrides: overrides['clock']()
            with self.subTest(overrides=overrides):
                with self.assertRaises((ValueError,TypeError)): self.wait(**overrides)
                self.assertEqual(len(self.f.calls()),calls_before)
                self.assertEqual(list(self.f.output.iterdir()),[])
        for callback,args in [(self.module._startup_pid,(BytesSubclass(b'123\n'),)),
                (self.module._startup_ticks,(BytesSubclass(b'123 (q) S '+b'0 '*18+b'456\n'),123))]:
            with self.subTest(callback=callback.__name__):
                with self.assertRaises((ValueError,TypeError)): callback(*args)


if __name__=='__main__': unittest.main(verbosity=2)
