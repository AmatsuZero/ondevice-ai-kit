"""Independent polling acceptance with actual local adapter subprocesses."""
import copy
import json
import subprocess
import unittest

import test_remote_observation_parent as fixtures
from adb_terminal_ack_publisher import RemoteAckPublicationError
from terminal_polling import observe_until_terminal


class TerminalPollBoundaries(unittest.TestCase):
    def fixture(self):
        f = fixtures.RemoteObservationParent()
        f.setUp()
        self.addCleanup(f.tearDown)
        return f

    def report(self, f, **updates):
        result = dict(runId='fresh-01', sourceFingerprint='a' * 64, pid=123,
                      processStartTicks='456', terminal=True, status='completed',
                      phase='terminal', activeCaseIndex=None,
                      terminalObservationProtocol='ODAI-TERMINAL-ACK-v1',
                      terminalAckTimeoutMillis=30000,
                      probeVariant='cpu-arena-off-heap-terminal-ack-v1')
        result.update(updates)
        (f.remote / 'report-fresh-01.json').write_bytes(json.dumps(result).encode())
        return result

    def observe(self, f, **updates):
        config = dict(adb=str(f.adb), serial='serial-01',
                      package='io.ondeviceai.acceptance.qwenterminal',
                      process_poll=lambda: None, monotonic_ns=lambda: 0,
                      sleep=lambda s: self.fail('unexpected sleep'))
        config.update(updates)
        return observe_until_terminal(f.output, f.binding, **config)

    def test_invalid_config_precedes_callbacks_and_writes(self):
        for key, value in [('process_poll', None), ('monotonic_ns', None),
                           ('sleep', None), ('runner', False),
                           ('timeout_seconds', 2.01), ('timeout_seconds', True),
                           ('timeout_seconds', 10 ** 1000), ('serial', 'bad\n'),
                           ('package', 'bad/name')]:
            with self.subTest(key=key, value=str(value)[:25]):
                f = self.fixture()
                with self.assertRaises(ValueError):
                    self.observe(f, **{key: value})
                self.assertEqual(f.calls(), [])
                self.assertEqual(list(f.output.iterdir()), [])

    def test_process_poll_type_and_failure_stop_before_transport(self):
        class IntSubclass(int):
            pass
        for value in (True, False, '0', 0.0, object(), IntSubclass(0)):
            with self.subTest(value=value):
                f = self.fixture()
                with self.assertRaises(ValueError):
                    self.observe(f, process_poll=lambda: value)
                self.assertEqual(f.calls(), [])
                self.assertTrue((f.output / 'poll-start.json').is_file())
        f = self.fixture()
        original = OSError('poll callback failure')
        def failed_poll():
            raise original
        with self.assertRaises(OSError) as caught:
            self.observe(f, process_poll=failed_poll)
        self.assertIs(caught.exception, original)
        self.assertEqual(f.calls(), [])

    def test_start_file_is_durable_before_first_clock_or_process_callback(self):
        f = self.fixture()
        self.report(f)
        events = []
        def check_start_file(event):
            self.assertTrue((f.output / 'poll-start.json').is_file(), event)
            data = json.loads((f.output / 'poll-start.json').read_bytes())
            self.assertEqual(data['binding'], f.binding)
            events.append(event)
        def clock():
            check_start_file('clock')
            return 0
        def poll():
            check_start_file('poll')
            return None
        result = self.observe(f, monotonic_ns=clock, process_poll=poll)
        self.assertEqual(result['pollSamples'], 1)
        self.assertEqual(events[0], 'clock')
        self.assertEqual(events.count('poll'), 1)

    def test_strict_report_metadata_rejects_before_any_ack(self):
        changes = [('pid', True), ('pid', 123.0), ('runId', 'fresh-01\n'),
                   ('sourceFingerprint', 'b' * 64), ('processStartTicks', '0456'),
                   ('terminal', 1), ('terminal', 'true'), ('status', 'running'),
                   ('phase', ''), ('phase', None), ('activeCaseIndex', True),
                   ('activeCaseIndex', 0.0), ('activeCaseIndex', 1),
                   ('terminalAckTimeoutMillis', 30000.0),
                   ('terminalAckTimeoutMillis', True),
                   ('terminalObservationProtocol', 'other'),
                   ('probeVariant', 'other')]
        for key, value in changes:
            with self.subTest(key=key, value=value):
                f = self.fixture()
                self.report(f, **{key: value})
                with self.assertRaises(ValueError):
                    self.observe(f)
                self.assertEqual(len(f.calls()), 5)
                self.assertFalse(f.stage.exists())
                self.assertFalse((f.output / 'terminal-ack.txt').exists())
        for raw in (b'{"terminal":true,"terminal":false}', b'{"value":NaN}',
                    b'{"value":Infinity}', b'[]', b'null'):
            with self.subTest(raw=raw):
                f = self.fixture()
                (f.remote / 'report-fresh-01.json').write_bytes(raw)
                with self.assertRaises(ValueError):
                    self.observe(f)
                self.assertEqual(len(f.calls()), 5)
                self.assertFalse(f.stage.exists())
        for key in ('processStartTicks', 'phase', 'activeCaseIndex', 'terminal',
                    'status', 'terminalObservationProtocol',
                    'terminalAckTimeoutMillis', 'probeVariant'):
            with self.subTest(missing=key):
                f = self.fixture()
                data = self.report(f)
                del data[key]
                (f.remote / 'report-fresh-01.json').write_bytes(json.dumps(data).encode())
                with self.assertRaises(ValueError):
                    self.observe(f)
                self.assertEqual(len(f.calls()), 5)
                self.assertFalse(f.stage.exists())

    def test_exact_resource_boundaries_and_failed_terminal_are_observable(self):
        f = self.fixture()
        self.report(f, status='failed')
        source = f.adb.read_text().replace('VmRSS:\\t2048 kB', 'VmRSS:\\t1572864 kB')
        source = source.replace('MemAvailable:\\t1048576 kB', 'MemAvailable:\\t524288 kB')
        f.adb.write_text(source)
        result = self.observe(f)
        self.assertEqual(result['observation']['rssBytes'], 1610612736)
        self.assertEqual(result['observation']['deviceAvailableBytes'], 536870912)
        self.assertIs(result['qualityAccepted'], False)
        self.assertIs(result['truePeakMeasured'], False)
        self.assertTrue(f.final.is_file())

    def test_progress_deadline_is_inclusive_and_sleep_requests_quarter_second(self):
        for nanoseconds, accepted in ((120_000_000_000, True), (120_000_000_001, False)):
            with self.subTest(nanoseconds=nanoseconds):
                f = self.fixture()
                self.report(f, terminal=False, status='running', phase='decode')
                state = {'now': 0, 'sleep': 0}
                def sleep(seconds):
                    self.assertEqual(seconds, 0.25)
                    state['sleep'] += 1
                    self.assertEqual(state['sleep'], 1)
                    state['now'] = nanoseconds
                    self.report(f)
                args = dict(monotonic_ns=lambda: state['now'], sleep=sleep)
                if accepted:
                    result = self.observe(f, **args)
                    self.assertEqual(result['pollSamples'], 2)
                    self.assertTrue(f.final.is_file())
                else:
                    with self.assertRaises(ValueError):
                        self.observe(f, **args)
                    self.assertEqual(len(f.calls()), 5)
                    self.assertFalse(f.stage.exists())

    def test_total_deadline_is_inclusive_across_progress_changes(self):
        for extra, accepted in ((0, True), (1, False)):
            with self.subTest(extra=extra):
                f = self.fixture()
                self.report(f, terminal=False, status='running', phase='p0')
                state = {'now': 0, 'sleep': 0}
                def sleep(seconds):
                    state['sleep'] += 1
                    self.assertLessEqual(state['sleep'], 3)
                    state['now'] += 80_000_000_000
                    if state['sleep'] == 3:
                        state['now'] += extra
                        self.report(f)
                    else:
                        self.report(f, terminal=False, status='running',
                                    phase='p' + str(state['sleep']))
                args = dict(monotonic_ns=lambda: state['now'], sleep=sleep)
                if accepted:
                    result = self.observe(f, **args)
                    self.assertEqual(result['pollSamples'], 4)
                    self.assertTrue(f.final.is_file())
                else:
                    with self.assertRaises(ValueError):
                        self.observe(f, **args)
                    self.assertEqual(len(f.calls()), 15)
                    self.assertFalse(f.stage.exists())

    def test_binding_snapshot_survives_external_mutation(self):
        f = self.fixture()
        original = copy.deepcopy(f.binding)
        self.report(f, terminal=False, status='running', phase='decode')
        def sleep(seconds):
            f.binding.update(runId='changed', pid=999, startTicks='999', sourceFingerprint='b' * 64)
            self.report(f)
        result = self.observe(f, sleep=sleep)
        self.assertEqual(result['pollSamples'], 2)
        receipt = json.loads((f.output / 'terminal-receipt.json').read_bytes())
        for key, value in original.items():
            self.assertEqual(receipt['binding'][key], value)
        self.assertEqual(f.final.read_bytes().splitlines()[1], b'fresh-01')

    def test_publication_uncertainty_propagates_without_retry_or_cleanup(self):
        for fault, phase, published in [('exit-before-link', 'before-link', False),
                                        ('dir-sync-fault', 'directory-sync', True)]:
            with self.subTest(fault=fault):
                f = self.fixture()
                self.report(f)
                (f.root / fault).touch()
                with self.assertRaises(RemoteAckPublicationError) as caught:
                    self.observe(f)
                self.assertEqual(caught.exception.phase, phase)
                self.assertIs(caught.exception.publication_possible, published)
                self.assertIs(caught.exception.durability_verified, False)
                self.assertTrue(f.stage.is_file())
                self.assertEqual(f.final.exists(), published)
                self.assertTrue((f.output / 'terminal-ack.txt').is_file())
                self.assertEqual(len(f.calls()), 24 if published else 19)

    def test_same_clock_guard_reaches_coordinator_before_link(self):
        for stage_time in (9, 240_000_000_011):
            with self.subTest(stage_time=stage_time):
                f = self.fixture()
                self.report(f)
                def clock():
                    return stage_time if f.stage.exists() else 10
                with self.assertRaises(RemoteAckPublicationError) as caught:
                    self.observe(f, monotonic_ns=clock)
                self.assertEqual(caught.exception.phase, 'before-link')
                self.assertIsInstance(caught.exception.__cause__, ValueError)
                self.assertIs(caught.exception.publication_possible, False)
                self.assertTrue(f.stage.is_file())
                self.assertFalse(f.final.exists())
                self.assertFalse((f.output / 'terminal-before-link.json').exists())

    def test_binding_shape_rejects_before_any_callback_or_output(self):
        class DictSubclass(dict):
            pass
        variants = [DictSubclass(runId='fresh-01', sourceFingerprint='a' * 64,
                                 pid=123, startTicks='456'), None, [],
                    {'runId': 'fresh-01', 'sourceFingerprint': 'a' * 64, 'pid': 123},
                    dict(runId='fresh-01', sourceFingerprint='a' * 64,
                         pid=123, startTicks='456', unexpected=True)]
        for binding in variants:
            with self.subTest(binding=binding):
                f = self.fixture()
                f.binding = binding
                with self.assertRaises(ValueError):
                    self.observe(f)
                self.assertEqual(f.calls(), [])
                self.assertEqual(list(f.output.iterdir()), [])

    def test_three_argument_runner_and_exact_raw_report_hash(self):
        f = self.fixture()
        self.report(f)
        raw = (f.remote / 'report-fresh-01.json').read_bytes()
        calls = []
        def runner(argv, timeout_seconds, stdin):
            calls.append((argv, timeout_seconds, stdin))
            return subprocess.run(argv, input=stdin, stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE, timeout=timeout_seconds,
                                  check=True).stdout
        result = self.observe(f, runner=runner)
        self.assertEqual(result['pollSamples'], 1)
        self.assertEqual(len(calls), 24)
        self.assertEqual(sum(stdin is not None for _, _, stdin in calls), 1)
        sample = json.loads((f.output / 'poll-sample-000000.json').read_bytes())
        self.assertEqual(sample['reportSHA256'], __import__('hashlib').sha256(raw).hexdigest())
        self.assertEqual(sample['clockDomain'], 'host-monotonic-ns')

    def test_sample_crossing_progress_deadline_stops_before_new_report(self):
        for stage, expected_calls in [('poll', 5), ('sample', 9), ('report', 10)]:
            with self.subTest(stage=stage):
                f = self.fixture()
                self.report(f, terminal=False, status='running', phase='decode')
                state = {'polls': 0, 'sleeps': 0, 'late': False}
                def sleep(seconds):
                    state['sleeps'] += 1
                    self.assertEqual(state['sleeps'], 1)
                    self.report(f)
                def poll():
                    state['polls'] += 1
                    if stage == 'poll' and state['polls'] == 2:
                        state['late'] = True
                    return None
                def clock():
                    count = f.root / 'stat-count'
                    if stage == 'sample' and count.exists() and int(count.read_text()) >= 4:
                        state['late'] = True
                    if stage == 'report' and len(f.calls()) >= 10:
                        state['late'] = True
                    return 121_000_000_000 if state['late'] else 0
                with self.assertRaises(ValueError):
                    self.observe(f, sleep=sleep, monotonic_ns=clock, process_poll=poll)
                self.assertEqual(len(f.calls()), expected_calls)
                self.assertFalse(f.stage.exists())
                self.assertFalse(f.final.exists())
                self.assertTrue((f.output / 'poll-sample-000000.json').is_file())
                self.assertFalse((f.output / 'poll-sample-000001.json').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
