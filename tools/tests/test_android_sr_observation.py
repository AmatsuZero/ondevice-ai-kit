import json
import os
import sys
import unittest

# Locate the probes directory relative to this test file so the module can be
# imported regardless of how unittest discovery is invoked.
_PROBES_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'probes'
)
if _PROBES_DIR not in sys.path:
    sys.path.insert(0, _PROBES_DIR)

import android_sr_observation as observation


RUN_ID = 'run-42'


def make_report(terminal=False, status='running', run_id=RUN_ID, **extra):
    report = {'runId': run_id, 'terminal': terminal, 'status': status}
    report.update(extra)
    return report


def serialize(report, as_bytes=False):
    text = json.dumps(report)
    return text.encode('utf-8') if as_bytes else text


class ReadObservationTest(unittest.TestCase):

    def test_string_report_success(self):
        report = make_report(terminal=True, status='completed')
        result = observation.read_observation(0, serialize(report), run_id=RUN_ID)
        self.assertEqual(result, report)

    def test_bytes_report_success(self):
        report = make_report(terminal=True, status='failed')
        result = observation.read_observation(
            0, serialize(report, as_bytes=True), run_id=RUN_ID
        )
        self.assertEqual(result, report)

    def test_running_progress_returned_unchanged(self):
        report = make_report(
            terminal=False,
            status='running',
            progress=0.4,
            runs=[{'kind': 'esrgan', 'completedFrames': 12}],
            fixture='kawaida-waterfall',
        )
        result = observation.read_observation(0, serialize(report), run_id=RUN_ID)
        self.assertEqual(result, report)
        self.assertIs(result['terminal'], False)
        self.assertEqual(result['status'], 'running')
        self.assertEqual(result['runs'], [{'kind': 'esrgan', 'completedFrames': 12}])
        self.assertEqual(result['fixture'], 'kawaida-waterfall')

    def test_completed_terminal_report(self):
        report = make_report(terminal=True, status='completed')
        result = observation.read_observation(0, serialize(report), run_id=RUN_ID)
        self.assertEqual(result, report)

    def test_failed_terminal_report(self):
        report = make_report(terminal=True, status='failed', error='decode')
        result = observation.read_observation(0, serialize(report), run_id=RUN_ID)
        self.assertEqual(result, report)

    def test_nonzero_returncode_returns_none_even_with_json(self):
        payload = serialize(make_report(terminal=True, status='completed'))
        for rc in (255, 1, -1):
            with self.subTest(rc=rc):
                self.assertIsNone(
                    observation.read_observation(rc, payload, run_id=RUN_ID)
                )

    def test_zero_exit_pending_states_return_none(self):
        pending = [
            '',
            '   ',
            b'',
            b'  ',
            'cat: /sdcard/report.json: No such file or directory',
            '{"runId": "run-42", "status": "run',
            bytes([0xff, 0xfe]),
        ]
        for payload in pending:
            with self.subTest(payload=payload):
                self.assertIsNone(
                    observation.read_observation(0, payload, run_id=RUN_ID)
                )

    def test_wrong_run_id_raises(self):
        payload = serialize(make_report(run_id='other-run'))
        with self.assertRaises(observation.ObservationError):
            observation.read_observation(0, payload, run_id=RUN_ID)

    def test_missing_run_id_raises(self):
        payload = json.dumps({'terminal': True, 'status': 'completed'})
        with self.assertRaises(observation.ObservationError):
            observation.read_observation(0, payload, run_id=RUN_ID)

    def test_non_object_json_raises(self):
        for payload in ('[1, 2, 3]', '"running"', '123', 'true', 'null'):
            with self.subTest(payload=payload):
                with self.assertRaises(observation.ObservationError):
                    observation.read_observation(0, payload, run_id=RUN_ID)

    def test_inconsistent_terminal_status_raises(self):
        pairs = [
            (False, 'completed'),
            (False, 'failed'),
            (False, 'unknown'),
            (True, 'running'),
            (True, 'unknown'),
        ]
        for terminal, status in pairs:
            with self.subTest(terminal=terminal, status=status):
                payload = serialize(make_report(terminal=terminal, status=status))
                with self.assertRaises(observation.ObservationError):
                    observation.read_observation(0, payload, run_id=RUN_ID)

    def test_terminal_not_bool_raises(self):
        for terminal in (1, 0, 'false', None):
            with self.subTest(terminal=terminal):
                payload = json.dumps(
                    {'runId': RUN_ID, 'terminal': terminal, 'status': 'running'}
                )
                with self.assertRaises(observation.ObservationError):
                    observation.read_observation(0, payload, run_id=RUN_ID)
        payload = json.dumps({'runId': RUN_ID, 'status': 'running'})
        with self.assertRaises(observation.ObservationError):
            observation.read_observation(0, payload, run_id=RUN_ID)

    def test_programmer_input_types_raise(self):
        with self.assertRaises(observation.ObservationError):
            observation.read_observation(0, 123, run_id=RUN_ID)
        with self.assertRaises(observation.ObservationError):
            observation.read_observation('0', '{}', run_id=RUN_ID)
        with self.assertRaises(observation.ObservationError):
            observation.read_observation(0, '{}', run_id=None)

    def test_repeated_pending_reads_then_completion(self):
        completed = make_report(terminal=True, status='completed')
        payloads = [
            b'',
            '  ',
            'cat: /sdcard/report.json: No such file or directory',
            '{"runId": "run-42", "status": "run',
            serialize(make_report(terminal=False, status='running')),
            serialize(completed),
        ]
        observed = [
            observation.read_observation(0, payload, run_id=RUN_ID)
            for payload in payloads
        ]
        for result in observed[:4]:
            self.assertIsNone(result)
        self.assertEqual(observed[4]['status'], 'running')
        self.assertIs(observed[4]['terminal'], False)
        self.assertEqual(observed[5], completed)
        self.assertIs(observed[5]['terminal'], True)
        self.assertEqual(observed[5]['runId'], RUN_ID)

    def test_observation_error_is_valueerror(self):
        self.assertTrue(issubclass(observation.ObservationError, ValueError))


if __name__ == '__main__':
    unittest.main()
