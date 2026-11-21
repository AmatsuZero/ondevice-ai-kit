'''
Tests-first candidate for terminal_polling.observe_until_terminal.

Uses the immutable RemoteObservationParent fixture via composition (no subclass).
Imports of the new unit under test are expected to fail RED until the
implementation is supplied separately; this candidate must not be edited by it.
'''
import hashlib
import json
import unittest

import test_remote_observation_parent as fixtures
from adb_terminal_ack_publisher import RemoteAckPublicationError

from terminal_polling import observe_until_terminal
from adb_process_sampler import AdbProcessSampler
from adb_terminal_report_reader import AdbTerminalReportReader

PROTOCOL = 'ODAI-TERMINAL-ACK-v1'
VARIANT = 'cpu-arena-off-heap-terminal-ack-v1'
ACK_TIMEOUT_MS = 30000
STAGE_TICKS = '456'
SERIAL = 'serial-01'
PACKAGE = 'io.ondeviceai.acceptance.qwenterminal'


class TerminalPollingTest(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.RemoteObservationParent()
        self.fixture.setUp()
        self.binding = self.fixture.binding
        self.report_path = self.fixture.remote / 'report-fresh-01.json'
        self.root = self.fixture.output
        self.adb = str(self.fixture.adb)

    def tearDown(self):
        self.fixture.tearDown()

    def write_report(self, terminal, status, phase, active_case_index=None):
        data = dict(self.binding, terminal=terminal, status=status,
                    processStartTicks=STAGE_TICKS,
                    terminalObservationProtocol=PROTOCOL,
                    terminalAckTimeoutMillis=ACK_TIMEOUT_MS,
                    probeVariant=VARIANT, phase=phase,
                    activeCaseIndex=active_case_index)
        raw = json.dumps(data).encode()
        self.report_path.write_bytes(raw)
        return raw

    def make_poll(self, max_calls=8):
        counter = {'n': 0}

        def poll():
            counter['n'] += 1
            if counter['n'] > max_calls:
                raise AssertionError('process_poll invoked too many times')
            return None

        return poll, counter

    def call_loop(self, poll, clock, sleep, **kw):
        return observe_until_terminal(
            self.root, self.binding, adb=self.adb, serial=SERIAL,
            package=PACKAGE, process_poll=poll, monotonic_ns=clock,
            sleep=sleep, runner=None, timeout_seconds=2.0, **kw)

    def test_running_report_reaches_terminal_and_publishes_once(self):
        self.write_report(terminal=False, status='running', phase='running')
        poll, counter = self.make_poll(max_calls=4)
        state = {'ns': 10**9, 'low': False}

        def clock():
            if state['low']:
                return 5 * 10**8
            state['ns'] += 1000
            return state['ns']

        def sleep(secs):
            self.write_report(terminal=True, status='completed', phase='terminal')

        result = self.call_loop(poll, clock, sleep)
        self.assertEqual(counter['n'], 2)
        self.assertTrue((self.root / 'poll-start.json').is_file())
        self.assertGreaterEqual(len(sorted(self.root.glob('poll-sample-*.json'))), 2)
        self.assertGreater(len(self.fixture.calls()), 0)
        observed = self.root / 'terminal-observed.json'
        receipt = self.root / 'terminal-receipt.json'
        ack = self.root / 'terminal-ack.txt'
        before_link = self.root / 'terminal-before-link.json'
        self.assertTrue(observed.is_file())
        self.assertTrue(receipt.is_file())
        self.assertTrue(ack.is_file())
        self.assertTrue(before_link.is_file())
        stage = self.fixture.remote / 'ack-fresh-01.txt.stage'
        final = self.fixture.remote / 'ack-fresh-01.txt'
        self.assertTrue(final.is_file())
        self.assertEqual(final.stat().st_ino, stage.stat().st_ino)
        self.assertEqual(ack.read_bytes(), final.read_bytes())
        self.assertEqual(ack.read_bytes().splitlines()[-1].decode(),
                         hashlib.sha256(self.report_path.read_bytes()).hexdigest())
        self.assertGreaterEqual(result['pollSamples'], 2)
        self.assertIs(result['qualityAccepted'], False)
        self.assertIs(result['truePeakMeasured'], False)
        with self.subTest(variant='failed-terminal'):
            fx = fixtures.RemoteObservationParent(); fx.setUp()
            try:
                data = dict(fx.binding, terminal=True, status='failed',
                            processStartTicks=STAGE_TICKS,
                            terminalObservationProtocol=PROTOCOL,
                            terminalAckTimeoutMillis=ACK_TIMEOUT_MS,
                            probeVariant=VARIANT, phase='terminal',
                            activeCaseIndex=None)
                (fx.remote / 'report-fresh-01.json').write_bytes(json.dumps(data).encode())
                res = observe_until_terminal(
                    fx.output, fx.binding, adb=str(fx.adb), serial=SERIAL,
                    package=PACKAGE, process_poll=(lambda: None),
                    monotonic_ns=iter(range(10**9, 10**12, 10**6)).__next__,
                    sleep=(lambda s: None), runner=None, timeout_seconds=2.0)
                self.assertGreaterEqual(res['pollSamples'], 1)
                self.assertIs(res['qualityAccepted'], False)
                self.assertIs(res['truePeakMeasured'], False)
                self.assertTrue((fx.output / 'terminal-ack.txt').is_file())
            finally:
                fx.tearDown()

    def test_exit_before_link_fault_propagates_without_retry(self):
        self.write_report(terminal=False, status='running', phase='running')
        (self.fixture.root / 'exit-before-link').touch()
        poll, counter = self.make_poll(max_calls=4)
        state = {'ns': 10**9, 'low': False}

        def clock():
            state['ns'] += 1000
            return state['ns']

        sleeps = {'n': 0}

        def sleep(secs):
            sleeps['n'] += 1
            self.write_report(terminal=True, status='completed', phase='terminal')

        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.call_loop(poll, clock, sleep)
        self.assertEqual(raised.exception.phase, 'before-link')
        self.assertIs(raised.exception.publication_possible, False)
        self.assertEqual(counter['n'], 2)
        self.assertEqual(sleeps['n'], 1)
        self.assertFalse((self.fixture.remote / 'ack-fresh-01.txt').exists())
        self.assertTrue((self.fixture.remote / 'ack-fresh-01.txt.stage').is_file())
        self.assertTrue((self.root / 'terminal-observed.json').is_file())
        self.assertTrue((self.root / 'terminal-receipt.json').is_file())
        self.assertTrue((self.root / 'terminal-ack.txt').is_file())
        self.assertFalse((self.root / 'terminal-before-link.json').exists())

    def test_rejects_invalid_metadata_and_stale_preflight(self):
        with self.subTest(case='identity-mismatch'):
            fx = fixtures.RemoteObservationParent(); fx.setUp()
            try:
                data = dict(fx.binding, runId='other-run', terminal=True,
                            status='completed', processStartTicks=STAGE_TICKS,
                            terminalObservationProtocol=PROTOCOL,
                            terminalAckTimeoutMillis=ACK_TIMEOUT_MS,
                            probeVariant=VARIANT, phase='terminal',
                            activeCaseIndex=None)
                (fx.remote / 'report-fresh-01.json').write_bytes(json.dumps(data).encode())
                with self.assertRaises(ValueError):
                    observe_until_terminal(
                        fx.output, fx.binding, adb=str(fx.adb), serial=SERIAL,
                        package=PACKAGE, process_poll=(lambda: None),
                        monotonic_ns=iter(range(10**9, 10**12, 10**6)).__next__,
                        sleep=(lambda s: None), runner=None, timeout_seconds=2.0)
                self.assertFalse((fx.output / 'terminal-ack.txt').exists())
                self.assertFalse((fx.output / 'terminal-observed.json').exists())
            finally:
                fx.tearDown()
        with self.subTest(case='malformed-metadata'):
            fx = fixtures.RemoteObservationParent(); fx.setUp()
            try:
                data = dict(fx.binding, terminal='yes', status='completed',
                            processStartTicks=STAGE_TICKS,
                            terminalObservationProtocol=PROTOCOL,
                            terminalAckTimeoutMillis=12345,
                            probeVariant=VARIANT, phase='terminal',
                            activeCaseIndex=None)
                (fx.remote / 'report-fresh-01.json').write_bytes(json.dumps(data).encode())
                with self.assertRaises(ValueError):
                    observe_until_terminal(
                        fx.output, fx.binding, adb=str(fx.adb), serial=SERIAL,
                        package=PACKAGE, process_poll=(lambda: None),
                        monotonic_ns=iter(range(10**9, 10**12, 10**6)).__next__,
                        sleep=(lambda s: None), runner=None, timeout_seconds=2.0)
                self.assertFalse((fx.output / 'terminal-ack.txt').exists())
            finally:
                fx.tearDown()
        with self.subTest(case='invalid-binding'):
            fx = fixtures.RemoteObservationParent(); fx.setUp()
            try:
                bad = dict(fx.binding); del bad['pid']
                with self.assertRaises(ValueError):
                    observe_until_terminal(
                        fx.output, bad, adb=str(fx.adb), serial=SERIAL,
                        package=PACKAGE, process_poll=(lambda: None),
                        monotonic_ns=iter(range(10**9, 10**12, 10**6)).__next__,
                        sleep=(lambda s: None), runner=None, timeout_seconds=2.0)
                self.assertEqual(fx.calls(), [])
                self.assertFalse((fx.output / 'poll-start.json').exists())
            finally:
                fx.tearDown()
        with self.subTest(case='stale-poll-start'):
            fx = fixtures.RemoteObservationParent(); fx.setUp()
            try:
                stale = b'STALE-POLL-START'
                (fx.output / 'poll-start.json').write_bytes(stale)
                with self.assertRaises((ValueError, OSError)):
                    observe_until_terminal(
                        fx.output, fx.binding, adb=str(fx.adb), serial=SERIAL,
                        package=PACKAGE, process_poll=(lambda: None),
                        monotonic_ns=iter(range(10**9, 10**12, 10**6)).__next__,
                        sleep=(lambda s: None), runner=None, timeout_seconds=2.0)
                self.assertEqual((fx.output / 'poll-start.json').read_bytes(), stale)
                self.assertEqual(fx.calls(), [])
            finally:
                fx.tearDown()

    def test_clock_regression_and_deadline_reject_without_publication(self):
        with self.subTest(case='clock-regression'):
            self.write_report(terminal=False, status='running', phase='running')
            poll, counter = self.make_poll(max_calls=4)
            state = {'ns': 10**9, 'low': False}

            def clock():
                if state['low']:
                    return 5 * 10**8
                state['ns'] += 1000
                return state['ns']

            def sleep(secs):
                state['low'] = True

            with self.assertRaises(ValueError):
                self.call_loop(poll, clock, sleep)
            self.assertEqual(counter['n'], 1)
            self.assertFalse((self.root / 'terminal-ack.txt').exists())
            self.assertTrue((self.root / 'poll-start.json').is_file())
        with self.subTest(case='progress-deadline'):
            fx = fixtures.RemoteObservationParent(); fx.setUp()
            try:
                data = dict(fx.binding, terminal=False, status='running',
                            processStartTicks=STAGE_TICKS,
                            terminalObservationProtocol=PROTOCOL,
                            terminalAckTimeoutMillis=ACK_TIMEOUT_MS,
                            probeVariant=VARIANT, phase='running',
                            activeCaseIndex=None)
                (fx.remote / 'report-fresh-01.json').write_bytes(json.dumps(data).encode())
                poll_c = {'n': 0}

                def poll():
                    poll_c['n'] += 1
                    if poll_c['n'] > 4:
                        raise AssertionError('endless loop')
                    return None

                st = {'ns': 10**9}

                def clk():
                    st['ns'] += 1000
                    return st['ns']

                def slp(secs):
                    st['ns'] += 130 * 10**9

                with self.assertRaises(ValueError):
                    observe_until_terminal(
                        fx.output, fx.binding, adb=str(fx.adb), serial=SERIAL,
                        package=PACKAGE, process_poll=poll, monotonic_ns=clk,
                        sleep=slp, runner=None, timeout_seconds=2.0)
                self.assertIn(poll_c['n'], (1, 2))
                self.assertEqual(len(fx.calls()), 5)
                self.assertFalse((fx.output / 'terminal-ack.txt').exists())
                self.assertTrue((fx.output / 'poll-start.json').is_file())
            finally:
                fx.tearDown()


if __name__ == '__main__':
    unittest.main()
