"""Parent acceptance for carrying startup progress into polling."""
import copy
import json
import unittest

import test_remote_observation_parent as fixtures
from terminal_polling import observe_until_terminal


class InitialProgressParent(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.RemoteObservationParent()
        self.f.setUp()
        self.addCleanup(self.f.tearDown)
        self.raw = dict(
            runId='fresh-01', sourceFingerprint='a' * 64, pid=123,
            processStartTicks='456', terminal=True, status='completed',
            phase='terminal', activeCaseIndex=None,
            terminalObservationProtocol='ODAI-TERMINAL-ACK-v1',
            terminalAckTimeoutMillis=30000,
            probeVariant='cpu-arena-off-heap-terminal-ack-v1',
        )
        self.set_report(self.raw)

    def set_report(self, report):
        self.raw = report
        (self.f.remote / 'report-fresh-01.json').write_bytes(json.dumps(report).encode())

    def observe(self, *, initial_progress, monotonic_ns=None, process_poll=None):
        values = dict(adb=str(self.f.adb), serial='serial-01',
                      package='io.ondeviceai.acceptance.qwenterminal',
                      process_poll=process_poll or (lambda: None),
                      monotonic_ns=monotonic_ns or (lambda: 0),
                      sleep=lambda seconds: self.fail('unexpected sleep'),
                      initial_progress=initial_progress)
        return observe_until_terminal(self.f.output, self.f.binding, **values)

    def test_invalid_initial_progress_rejects_before_callbacks_and_transport(self):
        class DictSubclass(dict):
            pass
        variants = [True, [], {},
                    {'phase': 'decode', 'activeCaseIndex': 0},
                    {'phase': 'decode', 'activeCaseIndex': 0, 'startedNs': 0, 'extra': 1},
                    {'phase': '', 'activeCaseIndex': 0, 'startedNs': 0},
                    {'phase': 'decode', 'activeCaseIndex': True, 'startedNs': 0},
                    {'phase': 'decode', 'activeCaseIndex': 1, 'startedNs': 0},
                    {'phase': 'decode', 'activeCaseIndex': 0, 'startedNs': -1},
                    {'phase': 'decode', 'activeCaseIndex': 0, 'startedNs': True},
                    DictSubclass(phase='decode', activeCaseIndex=0, startedNs=0)]
        for value in variants:
            with self.subTest(value=repr(value)):
                polls = []
                with self.assertRaises((TypeError, ValueError)):
                    self.observe(initial_progress=value,
                                 process_poll=lambda: polls.append(True))
                self.assertEqual(polls, [])
                self.assertEqual(self.f.calls(), [])

    def test_initial_anchor_expired_fails_before_first_poll_but_persists_start(self):
        progress = {'phase': 'decode', 'activeCaseIndex': 0, 'startedNs': 0}
        with self.assertRaises(ValueError):
            self.observe(initial_progress=progress, monotonic_ns=lambda: 121_000_000_000)
        self.assertEqual(self.f.calls(), [])
        self.assertTrue((self.f.output / 'poll-start.json').is_file())
        self.assertFalse(self.f.final.exists())

    def test_initial_anchor_exact_boundary_is_inclusive_for_terminal_report(self):
        progress = {'phase': 'terminal', 'activeCaseIndex': None, 'startedNs': 0}
        result = self.observe(initial_progress=progress,
                              monotonic_ns=lambda: 120_000_000_000)
        self.assertEqual(result['pollSamples'], 1)
        self.assertFalse(result['qualityAccepted'])
        self.assertTrue(self.f.final.is_file())

    def test_startup_anchor_crossing_each_callback_stops_before_new_report(self):
        for stage, expected_calls in [('poll', 0), ('sample', 4), ('report', 5)]:
            with self.subTest(stage=stage):
                self.f = fixtures.RemoteObservationParent()
                self.f.setUp()
                self.addCleanup(self.f.tearDown)
                self.set_report(dict(self.raw, terminal=False, status='running', phase='decode'))
                state = {'late': False}
                def poll():
                    if stage == 'poll': state['late'] = True
                def clock():
                    count = self.f.root / 'stat-count'
                    if stage == 'sample' and count.exists() and int(count.read_text()) >= 2:
                        state['late'] = True
                    if stage == 'report' and len(self.f.calls()) >= 5:
                        state['late'] = True
                    return 121_000_000_000 if state['late'] else 0
                with self.assertRaises(ValueError):
                    self.observe(initial_progress={'phase': 'decode', 'activeCaseIndex': 0, 'startedNs': 0},
                                 monotonic_ns=clock, process_poll=poll)
                self.assertEqual(len(self.f.calls()), expected_calls)
                self.assertFalse(self.f.final.exists())

    def test_initial_progress_is_copied_into_start_record_before_caller_mutation(self):
        progress = {'phase': 'decode', 'activeCaseIndex': 0, 'startedNs': 0}
        def mutate():
            progress['phase'] = 'mutated'
        with self.assertRaises(ValueError):
            self.observe(initial_progress=progress,
                         monotonic_ns=lambda: 121_000_000_000,
                         process_poll=mutate)
        payload = json.loads((self.f.output / 'poll-start.json').read_bytes())
        self.assertEqual(payload['initialProgress'],
                         {'phase': 'decode', 'activeCaseIndex': 0, 'startedNs': 0})


if __name__ == '__main__':
    unittest.main(verbosity=2)
