'''Tests for AdbProcessSampler (W3 Android host process-sampling adapter).

These are TESTS FIRST: no implementation of adb_process_sampler exists yet.
Local tests prove adapter output/failure behavior and call sequence only;
they do NOT prove actual device sampling or W3 completion.
'''

import shlex
import subprocess
import unittest
from unittest import mock

from adb_process_sampler import AdbProcessSampler


class AdbProcessSamplerTest(unittest.TestCase):
    ADB = '/usr/bin/adb'
    SERIAL = 'ZXC987.qwe'
    PACKAGE = 'com.example.app'
    PID = 4242
    START_TICKS = '1234567890'
    TIMEOUT = 1.5

    def _stat_fields(self, itrealvalue='0', starttime=None):
        if starttime is None:
            starttime = self.START_TICKS
        return ['S', '1', '1', '1', '0', '-1', '0', '1',
                '0', '0', '0', '0', '0', '0', '0',
                '20', '0', '1', itrealvalue, starttime]

    def _stat(self, pid=None, comm='myservice', itrealvalue='0', starttime=None):
        if pid is None:
            pid = self.PID
        fields = self._stat_fields(itrealvalue=itrealvalue, starttime=starttime)
        return '{} ({}) {}'.format(pid, comm, ' '.join(fields))

    def _status(self, pid=None, vmrss='4096 kB', extra=''):
        if pid is None:
            pid = self.PID
        T = chr(9)
        N = chr(10)
        if vmrss is None:
            vmrss_line = ''
        else:
            vmrss_line = 'VmRSS:' + T + vmrss + N
        return ('Name:' + T + self.PACKAGE + N +
                'Pid:' + T + str(pid) + N +
                'Uid:' + T + '1000' + N +
                vmrss_line + extra)

    def _meminfo(self, available='1048576 kB', extra=''):
        T = chr(9)
        N = chr(10)
        if available is None:
            line = ''
        else:
            line = 'MemAvailable:' + T + available + N
        return ('MemTotal:        2000000 kB' + N +
                'MemFree:          100000 kB' + N +
                line + extra)

    def _paths(self):
        return ['/proc/{}/stat'.format(self.PID),
                '/proc/{}/status'.format(self.PID),
                '/proc/meminfo',
                '/proc/{}/stat'.format(self.PID)]

    def _happy(self):
        return [self._stat().encode(),
                self._status().encode(),
                self._meminfo().encode(),
                self._stat().encode()]

    def _never_runner(self):
        def r(argv, timeout_seconds):
            raise AssertionError('runner must not be called on invalid config')
        return r

    def _recording_runner(self, responses, recorder):
        seq = {'i': 0}
        def r(argv, timeout_seconds):
            i = seq['i']
            seq['i'] += 1
            recorder.append((argv, timeout_seconds))
            if i < len(responses):
                return responses[i]
            raise AssertionError('unexpected extra runner call #{}'.format(i))
        return r

    def _assert_call(self, argv, serial, package, path, timeout):
        self.assertIsInstance(argv, list)
        self.assertEqual(argv[0], self.ADB)
        self.assertEqual(argv[1], '-s')
        self.assertEqual(argv[2], serial)
        self.assertEqual(argv[3], 'exec-out')
        self.assertIsInstance(argv[4], str)
        self.assertEqual(shlex.split(argv[4]),
                         ['run-as', package, 'head', '-c', '65537', path])

    def test_constructor_valid_does_not_call_runner(self):
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner(self._happy(), recorder))
        self.assertEqual(recorder, [])
        self.assertEqual(sampler.sample()['pid'], self.PID)

    def test_constructor_invalid_config_raises_value_error(self):
        valid = dict(adb=self.ADB, serial=self.SERIAL, package=self.PACKAGE,
                     pid=self.PID, start_ticks=self.START_TICKS)
        cases = {
            'adb_none': {**valid, 'adb': None},
            'adb_empty': {**valid, 'adb': ''},
            'adb_nul': {**valid, 'adb': 'a' + chr(0) + 'b'},
            'adb_bool': {**valid, 'adb': True},
            'serial_empty': {**valid, 'serial': ''},
            'serial_space': {**valid, 'serial': 'a b'},
            'serial_slash': {**valid, 'serial': 'a/b'},
            'serial_long': {**valid, 'serial': 'x' * 129},
            'package_single': {**valid, 'package': 'single'},
            'package_leading_digit': {**valid, 'package': '1bad'},
            'package_dot_lead': {**valid, 'package': '.com'},
            'package_empty_id': {**valid, 'package': 'com..x'},
            'pid_zero': {**valid, 'pid': 0},
            'pid_negative': {**valid, 'pid': -5},
            'pid_overflow': {**valid, 'pid': 2147483648},
            'pid_bool': {**valid, 'pid': True},
            'pid_float': {**valid, 'pid': 1.5},
            'pid_str': {**valid, 'pid': '4242'},
            'ticks_empty': {**valid, 'start_ticks': ''},
            'ticks_zero': {**valid, 'start_ticks': '0'},
            'ticks_negative': {**valid, 'start_ticks': '-1'},
            'ticks_alpha': {**valid, 'start_ticks': 'abc'},
            'ticks_overflow': {**valid, 'start_ticks': '9223372036854775808'},
            'ticks_lead_space': {**valid, 'start_ticks': ' 12'},
            'ticks_noncanon': {**valid, 'start_ticks': '0123'},
            'timeout_zero': {**valid, 'timeout_seconds': 0},
            'timeout_negative': {**valid, 'timeout_seconds': -1.0},
            'timeout_too_big': {**valid, 'timeout_seconds': 2.1},
            'timeout_bool': {**valid, 'timeout_seconds': True},
            'timeout_str': {**valid, 'timeout_seconds': '1'},
            'timeout_inf': {**valid, 'timeout_seconds': float('inf')},
            'timeout_nan': {**valid, 'timeout_seconds': float('nan')},
        }
        for name, kw in cases.items():
            with self.subTest(name=name):
                with self.assertRaises(ValueError):
                    AdbProcessSampler(runner=self._never_runner(), **kw)

    def test_constructor_valid_boundaries(self):
        base = dict(adb=self.ADB, serial=self.SERIAL, package=self.PACKAGE,
                    timeout_seconds=2.0)
        ok = [
            {**base, 'pid': 1, 'start_ticks': '1'},
            {**base, 'pid': 2147483647, 'start_ticks': '9223372036854775807'},
        ]
        for kw in ok:
            with self.subTest(**kw):
                sampler = AdbProcessSampler(runner=self._never_runner(), **kw)
                self.assertIsNotNone(sampler)

    def test_sample_happy_path_keys_and_ordering(self):
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner(self._happy(), recorder))
        result = sampler.sample()
        self.assertEqual(len(recorder), 4)
        paths = self._paths()
        for i, (argv, to) in enumerate(recorder):
            self._assert_call(argv, self.SERIAL, self.PACKAGE, paths[i], to)
            self.assertEqual(to, self.TIMEOUT)
        self.assertEqual(set(result.keys()),
                         {'pid', 'startTicks', 'rssBytes', 'deviceAvailableBytes'})
        self.assertIsInstance(result['pid'], int)
        self.assertIsInstance(result['startTicks'], str)
        self.assertIsInstance(result['rssBytes'], int)
        self.assertIsInstance(result['deviceAvailableBytes'], int)
        self.assertEqual(result['pid'], self.PID)
        self.assertEqual(result['startTicks'], self.START_TICKS)
        self.assertEqual(result['rssBytes'], 4096 * 1024)
        self.assertEqual(result['deviceAvailableBytes'], 1048576 * 1024)

    def test_sample_comm_with_spaces_and_paren(self):
        comm = 'my comm) with ) and spaces'
        responses = [self._stat(comm=comm).encode(),
                     self._status().encode(),
                     self._meminfo().encode(),
                     self._stat(comm=comm).encode()]
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner(responses, recorder))
        result = sampler.sample()
        self.assertEqual(result['rssBytes'], 4096 * 1024)
        self.assertEqual(len(recorder), 4)

    def test_sample_starttime_field22_not_field21(self):
        st1 = self._stat().encode()
        status = self._status().encode()
        meminfo = self._meminfo().encode()
        st2 = self._stat(itrealvalue=self.START_TICKS, starttime='999').encode()
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner([st1, status, meminfo, st2], recorder))
        with self.assertRaises(ValueError):
            sampler.sample()
        self.assertEqual(len(recorder), 4)

    def test_sample_pid_mismatch_first_stat(self):
        bad = self._stat(pid=9999).encode()
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner([bad] + self._happy()[1:], recorder))
        with self.assertRaises(ValueError):
            sampler.sample()
        self.assertEqual(len(recorder), 1)

    def test_sample_starttime_mismatch_first_stat(self):
        bad = self._stat(starttime='0').encode()
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner([bad] + self._happy()[1:], recorder))
        with self.assertRaises(ValueError):
            sampler.sample()
        self.assertEqual(len(recorder), 1)

    def test_sample_pid_mismatch_second_stat(self):
        st2 = self._stat(pid=9999).encode()
        good = self._happy()
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner([good[0], good[1], good[2], st2], recorder))
        with self.assertRaises(ValueError):
            sampler.sample()
        self.assertEqual(len(recorder), 4)

    def test_sample_status_pid_mismatch(self):
        status = self._status(pid=9999).encode()
        good = self._happy()
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner([good[0], status, good[2], good[3]], recorder))
        with self.assertRaises(ValueError):
            sampler.sample()
        self.assertEqual(len(recorder), 2)

    def test_status_vmrss_key_rules(self):
        good = self._happy()
        variants = {
            'duplicate': self._status(extra='VmRSS:' + chr(9) + '8192 kB' + chr(10)).encode(),
            'duplicate_pid': self._status(extra='Pid:' + chr(9) + '4242' + chr(10)).encode(),
            'missing': self._status(vmrss=None).encode(),
            'negative': self._status(vmrss='-5 kB').encode(),
            'noninteger': self._status(vmrss='abc kB').encode(),
            'float': self._status(vmrss='1.5 kB').encode(),
            'no_unit': self._status(vmrss='4096').encode(),
            'upper_kb': self._status(vmrss='4096 KB').encode(),
            'lower_kb': self._status(vmrss='4096 kb').encode(),
        }
        for name, status in variants.items():
            with self.subTest(name=name):
                recorder = []
                sampler = AdbProcessSampler(
                    self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
                    timeout_seconds=self.TIMEOUT,
                    runner=self._recording_runner([good[0], status, good[2], good[3]], recorder))
                with self.assertRaises(ValueError):
                    sampler.sample()
                self.assertEqual(len(recorder), 2)

    def test_meminfo_memavailable_key_rules(self):
        good = self._happy()
        variants = {
            'duplicate': self._meminfo(extra='MemAvailable:' + chr(9) + '8192 kB' + chr(10)).encode(),
            'missing': self._meminfo(available=None).encode(),
            'negative': self._meminfo(available='-5 kB').encode(),
            'noninteger': self._meminfo(available='abc kB').encode(),
            'no_unit': self._meminfo(available='1048576').encode(),
            'upper_kb': self._meminfo(available='1048576 KB').encode(),
        }
        for name, meminfo in variants.items():
            with self.subTest(name=name):
                recorder = []
                sampler = AdbProcessSampler(
                    self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
                    timeout_seconds=self.TIMEOUT,
                    runner=self._recording_runner([good[0], good[1], meminfo, good[3]], recorder))
                with self.assertRaises(ValueError):
                    sampler.sample()
                self.assertEqual(len(recorder), 3)

    def test_sample_converted_value_overflow(self):
        good = self._happy()
        big = '9007199254740993'
        variants = {
            'vmrss_overflow': (self._status(vmrss=big + ' kB').encode(), 2),
            'memavailable_overflow': (self._meminfo(available=big + ' kB').encode(), 3),
        }
        for name, (bad, expect_calls) in variants.items():
            with self.subTest(name=name):
                if name == 'vmrss_overflow':
                    responses = [good[0], bad, good[2], good[3]]
                else:
                    responses = [good[0], good[1], bad, good[3]]
                recorder = []
                sampler = AdbProcessSampler(
                    self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
                    timeout_seconds=self.TIMEOUT,
                    runner=self._recording_runner(responses, recorder))
                with self.assertRaises(ValueError):
                    sampler.sample()
                self.assertEqual(len(recorder), expect_calls)

    def test_sample_response_bounds(self):
        good = self._happy()
        variants = {
            'empty': b'',
            'oversized': b'x' * 65537,
            'non_ascii': bytes([255]),
            'nul': b'abc' + bytes([0]) + b'def',
        }
        for name, bad in variants.items():
            with self.subTest(name=name):
                recorder = []
                sampler = AdbProcessSampler(
                    self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
                    timeout_seconds=self.TIMEOUT,
                    runner=self._recording_runner([bad] + good[1:], recorder))
                with self.assertRaises(ValueError):
                    sampler.sample()
                self.assertEqual(len(recorder), 1)

    def test_sample_oversized_at_later_read(self):
        good = self._happy()
        recorder = []
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=self.TIMEOUT,
            runner=self._recording_runner([good[0], good[1], b'x' * 65537, good[3]], recorder))
        with self.assertRaises(ValueError):
            sampler.sample()
        self.assertEqual(len(recorder), 3)

    def test_sample_runner_failure_propagates_and_stops(self):
        for pos in (1, 2, 3, 4):
            with self.subTest(position=pos):
                recorder = []
                good = self._happy()
                def runner(argv, timeout_seconds, pos=pos, good=good):
                    i = len(recorder)
                    recorder.append((argv, timeout_seconds))
                    if i + 1 == pos:
                        raise RuntimeError('boom-{}'.format(pos))
                    return good[i]
                sampler = AdbProcessSampler(
                    self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
                    timeout_seconds=self.TIMEOUT, runner=runner)
                with self.assertRaises(RuntimeError):
                    sampler.sample()
                self.assertEqual(len(recorder), pos)

    @mock.patch('subprocess.run')
    def test_default_runner_invokes_subprocess(self, mock_run):
        stat = self._stat().encode()
        status = self._status().encode()
        meminfo = self._meminfo().encode()
        mock_run.side_effect = [
            subprocess.CompletedProcess([], 0, stdout=stat),
            subprocess.CompletedProcess([], 0, stdout=status),
            subprocess.CompletedProcess([], 0, stdout=meminfo),
            subprocess.CompletedProcess([], 0, stdout=stat),
        ]
        sampler = AdbProcessSampler(
            self.ADB, self.SERIAL, self.PACKAGE, self.PID, self.START_TICKS,
            timeout_seconds=2.0)
        result = sampler.sample()
        self.assertEqual(mock_run.call_count, 4)
        paths = self._paths()
        for i, call in enumerate(mock_run.call_args_list):
            args, kwargs = call
            argv = args[0]
            self._assert_call(argv, self.SERIAL, self.PACKAGE, paths[i], 2.0)
            self.assertTrue(kwargs.get('check'))
            self.assertEqual(kwargs.get('timeout'), 2.0)
            self.assertIs(kwargs.get('stdout'), subprocess.PIPE)
            self.assertIs(kwargs.get('stderr'), subprocess.PIPE)
        self.assertEqual(result['rssBytes'], 4096 * 1024)
        self.assertEqual(result['deviceAvailableBytes'], 1048576 * 1024)


if __name__ == '__main__':
    unittest.main()