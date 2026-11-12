import hashlib
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import terminal_file_io as fio
import terminal_observer as obsmod
import terminal_observation_coordinator as coord

RUN_ID = 'run-ABCDEFGHIJKLMNOP'
FP = 'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789'
PID = 123456
START_TICKS = '1000000000000'
RSS = 1048576
AVAIL = 1073741824
SAMPLE = {'pid': PID, 'startTicks': START_TICKS, 'rssBytes': RSS, 'deviceAvailableBytes': AVAIL}
VALID_AVAIL_MIN = 536870912


def build_report(pid=PID, start_ticks=START_TICKS, status='completed'):
    obj = {'runId': RUN_ID, 'sourceFingerprint': FP, 'pid': pid,
           'startTicks': start_ticks, 'terminal': True, 'status': status}
    return json.dumps(obj, separators=(',', ':'), sort_keys=True).encode('utf-8')


def build_binding(report_bytes, pid=PID, start_ticks=START_TICKS):
    return {'runId': RUN_ID, 'sourceFingerprint': FP, 'pid': pid,
            'startTicks': start_ticks,
            'terminalReportSha256': hashlib.sha256(report_bytes).hexdigest()}


def write_report(root, rel, data):
    Path(root, rel).write_bytes(data)


class ReadSpy:
    def __init__(self, real, mode='real', report_path=None, events=None):
        self.real = real
        self.mode = mode
        self.report_path = report_path
        self.calls = 0
        self.first = None
        self.events = events

    def __call__(self, root, relative, max_bytes):
        self.calls += 1
        if self.mode == 'mismatch' and self.calls == 2:
            return self.first + b'x'
        data = self.real(root, relative, max_bytes)
        if self.mode == 'mismatch' and self.calls == 1:
            self.first = data
        if self.mode == 'grow' and self.calls == 1 and self.report_path:
            with open(self.report_path, 'ab') as fh:
                fh.write(b' ')
        if self.events is not None:
            self.events.append(('read', relative))
        return data


class TestCoordinator(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(dir='/private/tmp')
        self.report_rel = 'report.json'
        self.obs_rel = 'observed.json'
        self.receipt_rel = 'receipt.json'
        self.ack_rel = 'ack.txt'
        self.report_bytes = build_report()
        write_report(self.root, self.report_rel, self.report_bytes)

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def assert_no_outputs(self):
        for rel in (self.obs_rel, self.receipt_rel, self.ack_rel):
            self.assertFalse(Path(self.root, rel).exists())

    def _clock(self, seq, calls, events):
        def fn():
            if len(calls) >= len(seq):
                raise ValueError('clock sequence exhausted')
            v = seq[len(calls)]
            calls.append(v)
            if events is not None:
                events.append(('clock', v))
            return v
        return fn

    def _wrap_sample(self, fn, calls, events):
        def wrapped():
            calls.append(1)
            if events is not None:
                events.append(('sample', None))
            return fn()
        return wrapped

    def _run(self, report_bytes=None, binding=None, read_mode='real',
             write_fail=None, write_fail_cls=Exception, sample_fn=None,
             clock_seq=None, prepare_report=True, events=None,
             ack_mutator=None, root=None):
        root = root or self.root
        if report_bytes is None:
            report_bytes = self.report_bytes
        if prepare_report:
            write_report(root, self.report_rel, report_bytes)
        if binding is None:
            binding = build_binding(report_bytes)
        if events is None:
            events = []
        read_spy = ReadSpy(fio.read_stable, mode=read_mode,
                           report_path=os.path.join(root, self.report_rel),
                           events=events)
        sample_calls = []
        if sample_fn is not None:
            smp = self._wrap_sample(sample_fn, sample_calls, events)
        else:
            smp = self._wrap_sample(lambda: dict(SAMPLE), sample_calls, events)
        clk_calls = []
        clk = self._clock(clock_seq if clock_seq is not None else [100, 200, 300, 400],
                          clk_calls, events)
        write_calls = []
        real_write = fio.write_new_durable

        def write_spy(r, relative, payload):
            write_calls.append(relative)
            if events is not None:
                events.append(('write', relative))
            if write_fail is not None and relative == write_fail:
                raise write_fail_cls('injected write failure')
            return real_write(r, relative, payload)

        ack_record = {}
        real_ack = obsmod.acknowledgement

        def ack_spy(first, second, b, observation):
            ack_record['called'] = True
            ack_record['observation'] = observation
            ack_record['binding_arg'] = b
            if ack_mutator is not None:
                ack_mutator(b)
            if events is not None:
                events.append(('ack', None))
            return real_ack(first, second, b, observation)

        patches = [
            mock.patch.object(coord, 'read_stable', read_spy, create=True),
            mock.patch.object(fio, 'read_stable', read_spy),
            mock.patch.object(coord, 'write_new_durable', write_spy, create=True),
            mock.patch.object(fio, 'write_new_durable', write_spy),
            mock.patch.object(coord, 'acknowledgement', ack_spy, create=True),
            mock.patch.object(obsmod, 'acknowledgement', ack_spy),
        ]
        for p in patches:
            p.start()
        self._last = {'read': read_spy, 'write_calls': write_calls,
                      'ack': ack_record, 'sample_calls': sample_calls,
                      'clk_calls': clk_calls, 'events': events}
        try:
            result = coord.publish_terminal_observation(
                Path(root), self.report_rel, self.obs_rel,
                self.receipt_rel, self.ack_rel, binding, smp, clk)
            self._last['result'] = result
            return self._last
        finally:
            for p in patches:
                p.stop()

    def test_happy_path(self):
        ctx = self._run()
        obs = ctx['result']
        self.assertEqual(ctx['read'].calls, 2)
        self.assertEqual(ctx['sample_calls'], [1, 1])
        self.assertEqual(ctx['clk_calls'], [100, 200, 300, 400])
        self.assertTrue(ctx['ack']['called'])
        self.assertEqual(ctx['ack']['observation'], obs)
        self.assertEqual(ctx['write_calls'], [self.obs_rel, self.receipt_rel, self.ack_rel])
        self.assertEqual(obs['pidBefore'], PID)
        self.assertEqual(obs['pidAfter'], PID)
        self.assertEqual(obs['startTicksBefore'], START_TICKS)
        self.assertEqual(obs['startTicksAfter'], START_TICKS)
        self.assertEqual(obs['uptimeBeforeNs'], 100)
        self.assertEqual(obs['firstReportReadNs'], 200)
        self.assertEqual(obs['secondReportReadNs'], 300)
        self.assertEqual(obs['uptimeAfterNs'], 400)
        self.assertEqual(obs['rssBytes'], RSS)
        self.assertEqual(obs['deviceAvailableBytes'], AVAIL)
        self.assertIsNone(obs['terminalObservationGap'])
        self.assertEqual(Path(self.root, self.obs_rel).read_bytes(), self.report_bytes)
        raw = Path(self.root, self.receipt_rel).read_bytes()
        receipt = json.loads(raw)
        self.assertEqual(receipt['schemaVersion'], 1)
        self.assertEqual(receipt['binding'], build_binding(self.report_bytes))
        self.assertEqual(receipt['terminalReportSha256'], hashlib.sha256(self.report_bytes).hexdigest())
        self.assertEqual(receipt['observation'], obs)
        self.assertEqual(receipt['sampleBefore'], SAMPLE)
        self.assertEqual(receipt['sampleAfter'], SAMPLE)
        self.assertEqual(raw.decode('utf-8'), json.dumps(json.loads(raw), ensure_ascii=False, sort_keys=True, separators=(',', ':')))
        self.assertEqual(Path(self.root, self.ack_rel).read_bytes(), obsmod.acknowledgement(self.report_bytes, self.report_bytes, build_binding(self.report_bytes), obs))

    def test_failed_report_acknowledged(self):
        rb = build_report(status='failed')
        ctx = self._run(report_bytes=rb)
        obs = ctx['result']
        self.assertTrue(ctx['ack']['called'])
        self.assertNotIn('quality', obs)
        self.assertEqual(Path(self.root, self.ack_rel).read_bytes(), obsmod.acknowledgement(rb, rb, build_binding(rb), obs))

    def test_report_mismatch_rejected(self):
        with self.assertRaises(ValueError):
            self._run(read_mode='mismatch')
        self.assert_no_outputs()

    def test_hash_mismatch_rejected(self):
        bad = build_binding(self.report_bytes)
        bad['terminalReportSha256'] = '0' * 64
        with self.assertRaises(ValueError):
            self._run(binding=bad)
        self.assert_no_outputs()

    def test_changed_pid_rejected(self):
        rb = build_report(pid=999999)
        binding = build_binding(rb, pid=999999)
        with self.assertRaises(ValueError):
            self._run(report_bytes=rb, binding=binding)
        self.assert_no_outputs()

    def test_changed_start_ticks_rejected(self):
        rb = build_report(start_ticks='2000000000000')
        binding = build_binding(rb, start_ticks='2000000000000')
        with self.assertRaises(ValueError):
            self._run(report_bytes=rb, binding=binding)
        self.assert_no_outputs()

    def test_invalid_first_sample_rejected(self):
        def bad():
            d = dict(SAMPLE)
            d['rssBytes'] = 0
            return d
        with self.assertRaises(ValueError):
            self._run(sample_fn=bad)
        self.assert_no_outputs()

    def test_invalid_second_sample_rejected(self):
        def bad():
            d = dict(SAMPLE)
            d['rssBytes'] = 0
            return d
        state = {'n': 0}
        def sample():
            state['n'] += 1
            return dict(SAMPLE) if state['n'] == 1 else bad()
        with self.assertRaises(ValueError):
            self._run(sample_fn=sample)
        self.assert_no_outputs()

    def test_resource_aggregation_before_after(self):
        before = {'pid': PID, 'startTicks': START_TICKS, 'rssBytes': RSS, 'deviceAvailableBytes': AVAIL}
        after = {'pid': PID, 'startTicks': START_TICKS, 'rssBytes': RSS * 2, 'deviceAvailableBytes': VALID_AVAIL_MIN}
        state = {'n': 0}
        def sample():
            state['n'] += 1
            return dict(before) if state['n'] == 1 else dict(after)
        ctx = self._run(sample_fn=sample)
        obs = ctx['result']
        self.assertEqual(obs['rssBytes'], RSS * 2)
        self.assertEqual(obs['deviceAvailableBytes'], VALID_AVAIL_MIN)
        self.assertEqual(obs['pidBefore'], PID)
        self.assertEqual(obs['pidAfter'], PID)
        self.assertEqual(obs['startTicksBefore'], START_TICKS)
        self.assertEqual(obs['startTicksAfter'], START_TICKS)

    def test_clock_regression_rejected(self):
        with self.assertRaises(ValueError):
            self._run(clock_seq=[500, 400, 300, 200])
        self.assert_no_outputs()

    def test_clock_bool_rejected(self):
        with self.assertRaises(ValueError):
            self._run(clock_seq=[True, False, True, False])
        self.assert_no_outputs()

    def test_stale_single_output_rejected(self):
        sentinels = {self.obs_rel: b'O', self.receipt_rel: b'R', self.ack_rel: b'A'}
        for rel, data in sentinels.items():
            with self.subTest(stale=rel):
                r = tempfile.mkdtemp(dir='/private/tmp')
                try:
                    Path(r, self.report_rel).write_bytes(self.report_bytes)
                    Path(r, rel).write_bytes(data)
                    with self.assertRaises(OSError):
                        self._run(root=r, binding=build_binding(self.report_bytes))
                    self.assertEqual(Path(r, rel).read_bytes(), data)
                    for o in (self.obs_rel, self.receipt_rel, self.ack_rel):
                        if o != rel:
                            self.assertFalse(Path(r, o).exists())
                finally:
                    shutil.rmtree(r, ignore_errors=True)

    def test_symlink_report_leaves_no_output(self):
        real = os.path.join(self.root, 'real_report.json')
        with open(real, 'wb') as f:
            f.write(self.report_bytes)
        os.remove(os.path.join(self.root, self.report_rel))
        os.symlink(real, os.path.join(self.root, self.report_rel))
        with self.assertRaises(ValueError):
            self._run(prepare_report=False)
        self.assert_no_outputs()

    def test_report_growth_leaves_no_output(self):
        with self.assertRaises(ValueError):
            self._run(read_mode='grow')
        self.assert_no_outputs()

    def test_observed_report_write_failure_no_ack(self):
        with self.assertRaises(OSError):
            self._run(write_fail=self.obs_rel, write_fail_cls=OSError)
        self.assert_no_outputs()

    def test_receipt_write_failure_no_ack(self):
        with self.assertRaises(OSError):
            self._run(write_fail=self.receipt_rel, write_fail_cls=OSError)
        self.assertTrue(Path(self.root, self.obs_rel).exists())
        self.assertEqual(Path(self.root, self.obs_rel).read_bytes(), self.report_bytes)
        self.assertFalse(Path(self.root, self.receipt_rel).exists())
        self.assertFalse(Path(self.root, self.ack_rel).exists())

    def test_ack_write_failure_propagates_keeps_durable(self):
        with self.assertRaises(OSError):
            self._run(write_fail=self.ack_rel, write_fail_cls=OSError)
        self.assertTrue(Path(self.root, self.obs_rel).exists())
        self.assertTrue(Path(self.root, self.receipt_rel).exists())
        self.assertFalse(Path(self.root, self.ack_rel).exists())
        receipt = json.loads(Path(self.root, self.receipt_rel).read_bytes())
        self.assertEqual(receipt['terminalReportSha256'], hashlib.sha256(self.report_bytes).hexdigest())

    def test_postpublication_error_propagates_no_retry(self):
        with self.assertRaises(fio.PublishedButNotDurableError):
            self._run(write_fail=self.ack_rel, write_fail_cls=fio.PublishedButNotDurableError)
        self.assertEqual(self._last['write_calls'].count(self.ack_rel), 1)
        self.assertTrue(Path(self.root, self.obs_rel).exists())
        self.assertTrue(Path(self.root, self.receipt_rel).exists())

    def test_event_sequence(self):
        events = []
        ctx = self._run(events=events)
        kinds = [e[0] for e in events]
        self.assertEqual(kinds, ['clock', 'sample', 'read', 'clock', 'read', 'clock', 'sample', 'clock', 'ack', 'write', 'write', 'write'])
        self.assertEqual([e[1] for e in events if e[0] == 'write'], [self.obs_rel, self.receipt_rel, self.ack_rel])
        self.assertEqual([e[1] for e in events if e[0] == 'read'], [self.report_rel, self.report_rel])
        self.assertEqual(ctx['clk_calls'], [100, 200, 300, 400])
        self.assertEqual(ctx['sample_calls'], [1, 1])

    def test_mutable_sample_snapshot_preserved(self):
        shared = {'pid': PID, 'startTicks': START_TICKS, 'rssBytes': RSS, 'deviceAvailableBytes': AVAIL}
        calls = {'n': 0}
        def sample():
            calls['n'] += 1
            shared['rssBytes'] = RSS + calls['n']
            return shared
        ctx = self._run(sample_fn=sample)
        receipt = json.loads(Path(self.root, self.receipt_rel).read_bytes())
        sb = receipt['sampleBefore']
        sa = receipt['sampleAfter']
        self.assertEqual(sb['rssBytes'], RSS + 1)
        self.assertEqual(sa['rssBytes'], RSS + 2)
        self.assertNotEqual(sb['rssBytes'], sa['rssBytes'])
        self.assertEqual(ctx['result']['rssBytes'], RSS + 2)

    def test_binding_snapshot_not_rewritten_by_callback(self):
        original = build_binding(self.report_bytes)
        def sample():
            original['pid'] = 999999
            original['terminalReportSha256'] = '0' * 64
            return dict(SAMPLE)
        self._run(binding=original, sample_fn=sample)
        receipt = json.loads(Path(self.root, self.receipt_rel).read_bytes())
        self.assertEqual(receipt['binding'], build_binding(self.report_bytes))

    def test_observation_uses_actual_callbacks(self):
        def weird_sample():
            return {'pid': PID, 'startTicks': START_TICKS, 'rssBytes': 524288, 'deviceAvailableBytes': 805306368}
        ctx = self._run(sample_fn=weird_sample, clock_seq=[11, 22, 33, 44])
        obs = ctx['result']
        self.assertEqual(obs['uptimeBeforeNs'], 11)
        self.assertEqual(obs['firstReportReadNs'], 22)
        self.assertEqual(obs['secondReportReadNs'], 33)
        self.assertEqual(obs['uptimeAfterNs'], 44)
        self.assertEqual(obs['rssBytes'], 524288)
        self.assertEqual(obs['deviceAvailableBytes'], 805306368)
        self.assertEqual(ctx['ack']['observation'], obs)


if __name__ == '__main__':
    unittest.main()