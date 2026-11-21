"""Boundary checks in addition to the real-subprocess composition fixtures."""
import json
from pathlib import Path
import shlex
import tempfile
import unittest

from adb_terminal_observation import publish_remote_terminal_observation
from adb_terminal_ack_publisher import RemoteAckPublicationError


class DictSubclass(dict):
    pass


class RemoteObservationBoundaries(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir='/private/tmp', prefix='remote-boundary-')
        self.root = Path(self.temp.name).resolve()
        self.binding = dict(runId='fresh-01', sourceFingerprint='a'*64, pid=123, startTicks='456')
        self.raw = json.dumps(dict(self.binding, terminal=True, status='completed')).encode()
        self.calls = []
        self.stage = None
        self.bad_sample = None
        self.status_count = 0
        self.mutate_binding = False

    def tearDown(self):
        self.temp.cleanup()

    def runner(self, argv, timeout, stdin):
        self.calls.append((argv, stdin))
        if self.mutate_binding:
            self.binding['pid'] = 124
        remote = shlex.split(argv[4])
        if remote[2] == 'head':
            if remote[-1] == '/proc/123/stat':
                return ('123 (process) '+' '.join(['S']+['0']*18+['456'])+'\n').encode()
            if remote[-1] == '/proc/123/status':
                self.status_count += 1
                rss = 2097152 if self.bad_sample == self.status_count else 2048
                return ('Pid:\t123\nVmRSS:\t%d kB\n' % rss).encode()
            if remote[-1] == '/proc/meminfo':
                return b'MemAvailable:\t1048576 kB\n'
        if argv[3] == 'exec-in':
            self.stage = stdin
            return b''
        if remote[2:4] == ['sh', '-c']:
            return self.raw if 'report-fresh-01.json' in remote[4] else self.stage
        if remote[2:4] == ['toybox', 'ln']:
            self.assertTrue((self.root/'journal.json').is_file())
            return b''
        if remote[2:4] == ['toybox', 'fsync']:
            return b''
        raise AssertionError('unexpected transport command')

    def observe(self, **kwargs):
        options = dict(adb='adb', serial='serial-01',
            package='io.ondeviceai.acceptance.qwenterminal', runner=self.runner,
            monotonic_ns=iter([10, 20, 30, 40, 50, 60]).__next__)
        options.update(kwargs)
        binding = options.pop('binding', self.binding)
        return publish_remote_terminal_observation(self.root, 'report.json',
            'receipt.json', 'ack.txt', 'journal.json', binding, **options)

    def link_calls(self):
        return [call for call in self.calls if shlex.split(call[0][4])[2:4] == ['toybox', 'ln']]

    def test_invalid_binding_and_config_fail_before_any_callbacks(self):
        bad_bindings = [DictSubclass(self.binding), {}, dict(self.binding, extra=True)]
        for key, values in dict(runId=['bad\n', ''], sourceFingerprint=['A'*64, 'x'],
                pid=[True, 0, 2147483648], startTicks=[456, '0', '0456', '9223372036854775808']).items():
            bad_bindings.extend(dict(self.binding, **{key: value}) for value in values)
        cases = [dict(binding=value) for value in bad_bindings]
        cases += [dict(adb=None), dict(serial='bad\n'), dict(package='single'),
                  dict(timeout_seconds=10**1000), dict(timeout_seconds=float('nan')),
                  dict(timeout_seconds=True), dict(runner=123), dict(monotonic_ns=123)]
        for options in cases:
            with self.subTest(options=repr(options)):
                if 'monotonic_ns' not in options:
                    options['monotonic_ns'] = lambda: self.fail('invalid preflight reached clock')
                with self.assertRaises(ValueError):
                    self.observe(**options)
                self.assertEqual(self.calls, [])
                self.assertEqual(list(self.root.iterdir()), [])

    def test_receipt_binding_is_snapshotted_before_transport_mutation(self):
        self.mutate_binding = True
        result = self.observe()
        self.assertEqual(self.binding['pid'], 124)
        receipt = json.loads((self.root/'receipt.json').read_bytes())
        journal = json.loads((self.root/'journal.json').read_bytes())
        self.assertEqual(receipt['binding']['pid'], 123)
        self.assertEqual(journal['binding']['pid'], 123)
        self.assertEqual(receipt['sampleBefore']['pid'], 123)
        self.assertIs(result['publication']['published'], True)
        self.assertEqual(len(self.link_calls()), 1)

    def test_each_resource_sample_can_block_publication(self):
        for bad_sample in [1, 2, 3]:
            with self.subTest(sample=bad_sample):
                self.bad_sample = bad_sample
                if bad_sample < 3:
                    with self.assertRaises(ValueError):
                        self.observe()
                    self.assertFalse((self.root/'receipt.json').exists())
                else:
                    with self.assertRaises(RemoteAckPublicationError) as raised:
                        self.observe()
                    self.assertEqual(raised.exception.phase, 'before-link')
                    self.assertIsInstance(raised.exception.__cause__, ValueError)
                    self.assertFalse(raised.exception.publication_possible)
                    self.assertTrue((self.root/'receipt.json').is_file())
                self.assertEqual(self.link_calls(), [])
                self.assertFalse((self.root/'journal.json').exists())
                self.status_count = 0
                self.calls.clear()

    def test_before_link_clock_regression_stops_before_third_sample(self):
        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.observe(monotonic_ns=iter([10, 20, 30, 40, 39]).__next__)
        self.assertEqual(raised.exception.phase, 'before-link')
        self.assertIsInstance(raised.exception.__cause__, ValueError)
        self.assertEqual(self.status_count, 2)
        self.assertEqual(self.link_calls(), [])
        self.assertTrue((self.root/'ack.txt').is_file())
        self.assertFalse((self.root/'journal.json').exists())


if __name__ == '__main__':
    unittest.main()
