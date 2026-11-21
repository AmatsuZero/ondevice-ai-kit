import hashlib
import json
import os
import shlex
import shutil
import tempfile
import unittest
from pathlib import Path

import adb_terminal_observation  # tests-first: real RED until implementation exists
from adb_terminal_ack_publisher import RemoteAckPublicationError

RUN_ID = 'fresh-01'
FP = 'a' * 64
PID = 123
START = '456'
REPORT = json.dumps({'runId': RUN_ID, 'sourceFingerprint': FP, 'pid': PID, 'terminal': True, 'status': 'completed'}).encode()
REPORT_MISMATCH = json.dumps({'runId': RUN_ID, 'sourceFingerprint': FP, 'pid': PID, 'terminal': True, 'status': 'failed'}).encode()
ACK = ('\n'.join(['ODAI-TERMINAL-ACK-v1', RUN_ID, FP, str(PID), START, hashlib.sha256(REPORT).hexdigest()]) + '\n').encode()
MEMINFO = b'MemAvailable:\t1048576 kB\n'
STATUS = b'Pid:\t123\nVmRSS:\t262144 kB\n'
STATUS2 = b'Pid:\t123\nVmRSS:\t2097152 kB\n'  # 2GiB > 1.5GiB bound
STAT = b'123 (qwen) S ' + b'0 ' * 18 + b'456 ' + b'0 ' * 10 + b'\n'

PKG = 'io.ondeviceai.acceptance.qwenterminal'

def make_runner(root, rels, mode='ok'):
    calls = []
    report_count = 0
    status_count = 0
    def runner(argv, timeout, stdin):
        nonlocal report_count, status_count
        calls.append((list(argv), stdin))
        assert argv[0] == 'adb', argv
        assert argv[1] == '-s', argv
        assert argv[3] in ('exec-out', 'exec-in'), argv
        toks = shlex.split(argv[4])
        assert toks[0] == 'run-as', toks
        assert toks[1] == PKG, toks
        outer = toks[2]
        if outer == 'head':
            assert len(toks) == 6 and toks[3] == '-c', toks
            n = int(toks[4]); path = toks[5]
            assert n == 65537, toks
            if path.endswith('/stat'):
                return STAT
            if path.endswith('/status'):
                local = status_count; status_count += 1
                return STATUS2 if (mode == 'changed' and local >= 2) else STATUS
            if path.endswith('/meminfo'):
                return MEMINFO
            raise AssertionError('unrecognized head path: ' + path)
        if outer == 'sh' and toks[3] == '-c':
            stoks = shlex.split(toks[4])
            if 'head' in stoks:
                idx = stoks.index('head')
                ci = stoks.index('-c', idx)
                n = int(stoks[ci + 1]); path = stoks[ci + 2]
                if path.endswith('.stage'):
                    assert n == 1025, stoks
                    return ACK
                assert n == 1048577, stoks
                local = report_count; report_count += 1
                return REPORT_MISMATCH if (mode == 'mismatch' and local >= 1) else REPORT
            assert argv[3] == 'exec-in', argv
            if mode == 'transport':
                raise RuntimeError('transport down')
            assert stdin == ACK, 'publisher exec-in stdin must equal exact ACK'
            return b''
        if outer == 'toybox':
            assert len(toks) >= 4, toks
            sub = toks[3]
            if sub == 'fsync':
                return b''
            if sub == 'ln' and '-T' in toks:
                for key in ('observed', 'receipt', 'ack', 'before'):
                    p = os.path.join(root, rels[key])
                    if not os.path.exists(p):
                        raise AssertionError('local file missing before link: ' + key)
                if Path(os.path.join(root, rels['observed'])).read_bytes() != REPORT:
                    raise AssertionError('observed bytes mismatch before link')
                if Path(os.path.join(root, rels['ack'])).read_bytes() != ACK:
                    raise AssertionError('ack bytes mismatch before link')
                return b''
            raise AssertionError('unrecognized toybox subcommand: ' + sub)
        raise AssertionError('unrecognized outer command: ' + outer)
    runner.calls = calls
    return runner

class TestAdbTerminalObservation(unittest.TestCase):
    def setUp(self):
        self._roots = []
    def tearDown(self):
        for r in self._roots:
            shutil.rmtree(r, ignore_errors=True)
    def _fresh(self):
        root = os.path.realpath(tempfile.mkdtemp(dir='/private/tmp', prefix='odai-'))
        rels = {'observed': 'w3/observed/report.json', 'receipt': 'w3/receipt/report.json', 'ack': 'w3/ack/ack.txt', 'before': 'w3/before-link/journal.json'}
        for r in rels.values():
            os.makedirs(os.path.join(root, os.path.dirname(r)), exist_ok=True)
        self._roots.append(root)
        return root, rels
    def binding(self):
        return {'runId': RUN_ID, 'sourceFingerprint': FP, 'pid': PID, 'startTicks': START}
    def clock(self, vals):
        it = iter(vals)
        return lambda: next(it)
    def labels(self, runner):
        out = []
        for argv, stdin in runner.calls:
            toks = shlex.split(argv[4])
            assert toks[0] == 'run-as' and toks[1] == PKG, toks
            outer = toks[2]
            if outer == 'head':
                path = toks[5]
                if path.endswith('/stat'):
                    out.append('stat')
                elif path.endswith('/status'):
                    out.append('status')
                elif path.endswith('/meminfo'):
                    out.append('meminfo')
                else:
                    raise AssertionError('unlabeled head ' + path)
            elif outer == 'sh' and toks[3] == '-c':
                stoks = shlex.split(toks[4])
                if 'head' in stoks:
                    idx = stoks.index('head')
                    ci = stoks.index('-c', idx)
                    path = stoks[ci + 2]
                    out.append('pub-ack' if path.endswith('.stage') else 'report')
                else:
                    out.append('pub-write')
            elif outer == 'toybox':
                sub = toks[3]
                if sub == 'fsync':
                    out.append('pub-fsync')
                elif sub == 'ln':
                    out.append('pub-link')
                else:
                    raise AssertionError('unlabeled toybox ' + sub)
            else:
                raise AssertionError('unlabeled call ' + str(argv))
        return out
    def _call_kw(self, root, rels, binding, vals, runner):
        return dict(root=root, observed_report_relative=rels['observed'], receipt_relative=rels['receipt'], ack_relative=rels['ack'], before_link_relative=rels['before'], binding=binding, adb='adb', serial='serial-01', package='io.ondeviceai.acceptance.qwenterminal', timeout_seconds=2.0, monotonic_ns=self.clock(vals), runner=runner)
    def test_success_ordering_and_durable_files(self):
        root, rels = self._fresh()
        runner = make_runner(root, rels, 'ok')
        res = adb_terminal_observation.publish_remote_terminal_observation(**self._call_kw(root, rels, self.binding(), (100, 200, 300, 400, 500, 600), runner))
        self.assertIn('observation', res)
        self.assertIn('publication', res)
        labs = self.labels(runner)
        expected = (['stat', 'status', 'meminfo', 'stat'] + ['report', 'report'] + ['stat', 'status', 'meminfo', 'stat'] + ['pub-write', 'pub-ack', 'pub-fsync', 'stat', 'status', 'meminfo', 'stat', 'pub-link', 'pub-fsync'])
        self.assertEqual(labs, expected, msg='call ordering mismatch')
        for key in ('observed', 'receipt', 'ack', 'before'):
            self.assertTrue(os.path.exists(os.path.join(root, rels[key])), msg='missing durable ' + key)
        rcpt = json.loads(Path(os.path.join(root, rels['receipt'])).read_text())
        self.assertEqual((rcpt['schemaVersion'], rcpt['clockDomain'], rcpt['terminalReportSha256']), (1, 'host-monotonic-ns', hashlib.sha256(REPORT).hexdigest()))
        self.assertEqual(rcpt['binding']['runId'], RUN_ID)
        jour = json.loads(Path(os.path.join(root, rels['before'])).read_text())
        self.assertEqual((jour['schemaVersion'], jour['clockDomain']), (1, 'host-monotonic-ns'))
        self.assertIn('sampleBeforeLink', jour)
    def test_invalid_config_isolated(self):
        with self.subTest('stale output'):
            root, rels = self._fresh()
            Path(os.path.join(root, rels['observed'])).write_bytes(b'stale')
            runner = make_runner(root, rels, 'ok')
            with self.assertRaises((ValueError, OSError)):
                adb_terminal_observation.publish_remote_terminal_observation(**self._call_kw(root, rels, self.binding(), (1, 2, 3, 4, 5, 6), runner))
            self.assertEqual(runner.calls, [], msg='no transport on stale output')
        with self.subTest('missing binding key'):
            root, rels = self._fresh()
            b = self.binding(); del b['pid']
            runner = make_runner(root, rels, 'ok')
            with self.assertRaises(ValueError):
                adb_terminal_observation.publish_remote_terminal_observation(**self._call_kw(root, rels, b, (1, 2, 3, 4, 5, 6), runner))
            self.assertEqual(runner.calls, [], msg='no transport on bad binding')
        with self.subTest('noncallable clock'):
            root, rels = self._fresh()
            runner = make_runner(root, rels, 'ok')
            kw = self._call_kw(root, rels, self.binding(), (1, 2, 3, 4, 5, 6), runner)
            kw['monotonic_ns'] = 123
            with self.assertRaises(ValueError):
                adb_terminal_observation.publish_remote_terminal_observation(**kw)
            self.assertEqual(runner.calls, [], msg='no transport on bad clock')
    def test_report_mismatch_before_link(self):
        with self.subTest('report mismatch prevents persist and link'):
            root, rels = self._fresh()
            runner = make_runner(root, rels, 'mismatch')
            with self.assertRaises(ValueError):
                adb_terminal_observation.publish_remote_terminal_observation(**self._call_kw(root, rels, self.binding(), (1, 2, 3, 4, 5, 6), runner))
            labs = self.labels(runner)
            self.assertNotIn('pub-write', labs, msg='no publisher before mismatch')
            self.assertNotIn('pub-link', labs, msg='mismatch must prevent link')
            for key in ('observed', 'receipt', 'ack', 'before'):
                self.assertFalse(os.path.exists(os.path.join(root, rels[key])), msg='no local output on ' + key)
        with self.subTest('before-link changed sample prevents link'):
            root, rels = self._fresh()
            runner = make_runner(root, rels, 'changed')
            with self.assertRaises(RemoteAckPublicationError) as ctx:
                adb_terminal_observation.publish_remote_terminal_observation(**self._call_kw(root, rels, self.binding(), (1, 2, 3, 4, 5, 6), runner))
            err = ctx.exception
            self.assertEqual(err.phase, 'before-link')
            self.assertFalse(err.publication_possible)
            self.assertIsInstance(err.__cause__, ValueError)
            labs = self.labels(runner)
            self.assertIn('pub-write', labs, msg='publisher stage-write reached')
            self.assertIn('stat', labs, msg='third sample reached')
            self.assertNotIn('pub-link', labs, msg='changed sample must prevent link')
            for key in ('observed', 'receipt', 'ack'):
                self.assertTrue(os.path.exists(os.path.join(root, rels[key])), msg='evidence ' + key + ' must persist')
            self.assertFalse(os.path.exists(os.path.join(root, rels['before'])), msg='journal absent on before-link failure')
    def test_clock_and_transport_failures(self):
        with self.subTest('clock regression stops without retry'):
            root, rels = self._fresh()
            runner = make_runner(root, rels, 'ok')
            with self.assertRaises(ValueError):
                adb_terminal_observation.publish_remote_terminal_observation(**self._call_kw(root, rels, self.binding(), (300, 200, 300, 400, 500, 600), runner))
            labs = self.labels(runner)
            self.assertTrue(all(not l.startswith('pub') for l in labs), msg='no publisher on clock regression')
            self.assertNotIn('report', labs[5:], msg='no report2 on clock regression')
        with self.subTest('transport failure preserves evidence'):
            root, rels = self._fresh()
            runner = make_runner(root, rels, 'transport')
            with self.assertRaises(RemoteAckPublicationError) as ctx:
                adb_terminal_observation.publish_remote_terminal_observation(**self._call_kw(root, rels, self.binding(), (1, 2, 3, 4, 5, 6), runner))
            err = ctx.exception
            self.assertIsInstance(err.__cause__, RuntimeError)
            self.assertIn('transport down', str(err.__cause__))
            labs = self.labels(runner)
            self.assertIn('pub-write', labs, msg='publisher started')
            self.assertNotIn('pub-link', labs, msg='link must not happen on transport failure')
            for key in ('observed', 'receipt', 'ack'):
                self.assertTrue(os.path.exists(os.path.join(root, rels[key])), msg='evidence ' + key + ' must persist')

if __name__ == '__main__':
    unittest.main()