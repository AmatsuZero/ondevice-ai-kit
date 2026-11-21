import unittest
import shlex

from adb_terminal_report_reader import AdbTerminalReportReader


class _StrSub(str):
    pass


class _BytesSub(bytes):
    pass


BASE = dict(
    adb='adb',
    serial='serial-01',
    package='io.ondeviceai.acceptance.qwenterminal',
    run_id='fresh-01',
    timeout_seconds=2.0,
)


class TestAdbTerminalReportReader(unittest.TestCase):

    def test_constructor_invalid_config(self):
        bad = [
            {'adb': 123}, {'serial': []}, {'package': 5}, {'run_id': None},
            {'adb': _StrSub('adb')}, {'serial': _StrSub('serial-01')},
            {'package': _StrSub('io.ondeviceai.acceptance.qwenterminal')},
            {'run_id': _StrSub('fresh-01')},
            {'serial': 'bad\nx'}, {'serial': 'bad/x'},
            {'package': 'no-dots'}, {'package': '1bad.pkg'},
            {'run_id': 'bad\n'}, {'run_id': 'bad x'},
            {'timeout_seconds': 0}, {'timeout_seconds': -1},
            {'timeout_seconds': 3}, {'timeout_seconds': float('nan')},
            {'timeout_seconds': float('inf')}, {'timeout_seconds': True},
        ]
        for ov in bad:
            calls = []
            def runner(argv, timeout_seconds, _c=calls):
                _c.append(1)
                return b'x'
            with self.assertRaises(ValueError):
                AdbTerminalReportReader(runner=runner, **{**BASE, **ov})
            self.assertEqual(calls, [])

    def test_output_boundaries_and_types(self):
        for data in [b'x', b'a' * 1048576]:
            calls = []
            def runner(argv, timeout_seconds, _c=calls):
                _c.append(1)
                return data
            self.assertEqual(AdbTerminalReportReader(runner=runner, **BASE).read(), data)
        for data in [b'', b'a' * 1048577, bytearray(b'x'), _BytesSub(b'x'), 'str', [1]]:
            calls = []
            def runner(argv, timeout_seconds, _c=calls):
                _c.append(1)
                return data
            with self.assertRaises(ValueError):
                AdbTerminalReportReader(runner=runner, **BASE).read()
        weird = b'\r\n\x00\xff\xe4\xb8\x96'
        calls = []
        def runner(argv, timeout_seconds, _c=calls):
            _c.append(1)
            return weird
        self.assertEqual(AdbTerminalReportReader(runner=runner, **BASE).read(), weird)

    def test_fresh_reads_and_argv(self):
        vals = [b'one', b'two']
        calls = []
        def runner(argv, timeout_seconds, _c=calls):
            self.assertEqual(argv[0:4], ['adb', '-s', 'serial-01', 'exec-out'])
            self.assertEqual(len(argv), 5)
            toks = shlex.split(argv[4])
            self.assertEqual(toks[:4], ['run-as', 'io.ondeviceai.acceptance.qwenterminal', 'sh', '-c'])
            self.assertEqual(len(toks), 5)
            self.assertIn('files/w3-qwen-terminal/report-fresh-01.json', toks[4])
            self.assertIn('1048577', toks[4])
            self.assertEqual(timeout_seconds, 0.25)
            _c.append(1)
            return vals[len(_c) - 1]
        r = AdbTerminalReportReader('adb', 'serial-01', 'io.ondeviceai.acceptance.qwenterminal', 'fresh-01', timeout_seconds=0.25, runner=runner)
        self.assertEqual(r.read(), b'one')
        self.assertEqual(r.read(), b'two')
        self.assertEqual(len(calls), 2)

    def test_runner_exception_propagated(self):
        calls = []
        sentinel = RuntimeError('boom')
        def runner(argv, timeout_seconds, _c=calls):
            _c.append(1)
            raise sentinel
        r = AdbTerminalReportReader('adb', 'serial-01', 'io.ondeviceai.acceptance.qwenterminal', 'fresh-01', runner=runner)
        with self.assertRaises(RuntimeError) as cm:
            r.read()
        self.assertIs(cm.exception, sentinel)
        self.assertEqual(len(calls), 1)


if __name__ == '__main__':
    unittest.main()
