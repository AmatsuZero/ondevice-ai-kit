"""Primary acceptance for real local ADB adapters in the running-process loop."""
import copy
import hashlib
import json
from pathlib import Path
import unittest

import test_remote_observation_parent as fixtures
from terminal_polling import observe_until_terminal


class TerminalPollParent(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.RemoteObservationParent()
        self.f.setUp()
        self.addCleanup(self.f.tearDown)
        self.clock_value = 0
        self.polls = 0
        self.sleeps = 0
        self.raw_report = dict(
            runId='fresh-01', sourceFingerprint='a' * 64, pid=123,
            processStartTicks='456', terminal=True, status='completed',
            phase='terminal', activeCaseIndex=None,
            terminalObservationProtocol='ODAI-TERMINAL-ACK-v1',
            terminalAckTimeoutMillis=30000,
            probeVariant='cpu-arena-off-heap-terminal-ack-v1',
        )
        self.set_report(self.raw_report)

    def set_report(self, obj):
        self.raw = json.dumps(obj).encode()
        (self.f.remote / 'report-fresh-01.json').write_bytes(self.raw)

    def clock(self):
        self.clock_value += 10
        return self.clock_value

    def poll(self):
        self.polls += 1
        if self.f.final.exists():
            self.fail('process polled after ACK publication')
        if self.polls > 3:
            self.fail('unexpected repeated polling')
        return None

    def sleep(self, seconds):
        self.sleeps += 1
        self.fail('terminal-only fixture must not sleep/retry')

    def observe(self, **kwargs):
        values = dict(adb=str(self.f.adb), serial='serial-01',
                      package='io.ondeviceai.acceptance.qwenterminal',
                      process_poll=self.poll, monotonic_ns=self.clock, sleep=self.sleep)
        values.update(kwargs)
        binding = values.pop('binding', self.f.binding)
        return observe_until_terminal(self.f.output, binding, **values)

    def test_invalid_configuration_and_binding_precede_all_callbacks(self):
        for key, value in [('sourceFingerprint', 'bad'), ('pid', True), ('startTicks', '0456')]:
            binding = copy.deepcopy(self.f.binding)
            binding[key] = value
            with self.subTest(key=key):
                with self.assertRaises(ValueError):
                    self.observe(binding=binding)
                self.assertEqual(self.polls, 0)
                self.assertEqual(self.clock_value, 0)
                self.assertEqual(self.f.calls(), [])
                self.assertEqual(list(self.f.output.iterdir()), [])

    def test_exact_process_exit_fails_before_transport(self):
        with self.assertRaises((RuntimeError, ValueError)):
            self.observe(process_poll=lambda: 0)
        self.assertEqual(self.f.calls(), [])
        self.assertFalse(self.f.final.exists())

    def test_actual_resource_budget_rejection_stops_before_report(self):
        source = self.f.adb.read_text()
        self.f.adb.write_text(source.replace('VmRSS:\\t2048 kB', 'VmRSS:\\t2097152 kB'))
        self.f.adb.chmod(0o700)
        with self.assertRaises(ValueError):
            self.observe()
        self.assertEqual(len(self.f.calls()), 4)
        self.assertEqual(self.sleeps, 0)
        self.assertFalse(self.f.final.exists())

    def test_actual_terminal_handshake_returns_without_post_ack_sampling(self):
        result = self.observe()
        self.assertEqual(len(self.f.calls()), 24)
        self.assertEqual(self.polls, 1)
        self.assertEqual(self.sleeps, 0)
        self.assertEqual(result['pollSamples'], 1)
        self.assertIs(result['qualityAccepted'], False)
        self.assertIs(result['truePeakMeasured'], False)
        self.assertEqual(self.f.final.read_bytes(), (self.f.output / 'terminal-ack.txt').read_bytes())
        self.assertEqual((self.f.output / 'terminal-observed.json').read_bytes(), self.raw)
        self.assertEqual(self.f.final.read_bytes().splitlines()[-1].decode(), hashlib.sha256(self.raw).hexdigest())
        sample = json.loads((self.f.output / 'poll-sample-000000.json').read_bytes())
        self.assertIsInstance(sample, dict)

    def test_actual_report_binding_mismatch_prevents_publication(self):
        self.raw_report['processStartTicks'] = '457'
        self.set_report(self.raw_report)
        with self.assertRaises(ValueError):
            self.observe()
        self.assertEqual(len(self.f.calls()), 5)
        self.assertFalse(self.f.stage.exists())
        self.assertFalse(self.f.final.exists())
        self.assertEqual(self.sleeps, 0)

    def test_sample_journal_failure_blocks_handshake_and_keeps_stale_bytes(self):
        stale = self.f.output / 'poll-sample-000000.json'
        stale.write_bytes(b'KEEP')
        with self.assertRaises((OSError, ValueError)):
            self.observe()
        self.assertEqual(stale.read_bytes(), b'KEEP')
        self.assertFalse(self.f.stage.exists())
        self.assertFalse(self.f.final.exists())
        self.assertEqual(self.sleeps, 0)


if __name__ == '__main__':
    unittest.main(verbosity=2)
