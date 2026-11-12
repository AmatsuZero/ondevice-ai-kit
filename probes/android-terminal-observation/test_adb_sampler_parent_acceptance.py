"""Parent acceptance of the real subprocess boundary, using a local fake ADB."""
import json
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest

from adb_process_sampler import AdbProcessSampler

PID = 731
TICKS = '123456789'
PACKAGE = 'io.ondeviceai.acceptance.sampler'
SERIAL = 'local-standin-1'


def stat_bytes(pid=PID, ticks=TICKS):
    # Field 21 is a decoy; starttime is field 22, after a comm containing ')'.
    fields = ['R'] + ['0'] * 17 + ['999999'] + [ticks]
    return (str(pid) + ' (worker ) nested name) ' + ' '.join(fields) + '\n').encode()


class AdbSamplerSubprocessAcceptance(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir='/private/tmp')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.adb = self.root / 'fake-adb'
        self.adb.write_text('#!' + sys.executable + '\n' + '''
import json
from pathlib import Path
import sys
import time
root = Path(__file__).resolve().parent
trace = root / 'trace.jsonl'
index = len(trace.read_text().splitlines()) if trace.exists() else 0
with trace.open('a') as out:
    out.write(json.dumps(sys.argv[1:]) + '\\n')
config = json.loads((root / 'config.json').read_text())
if index == config.get('failAt'):
    sys.stderr.write('fixture-adb-error-' + str(index))
    sys.exit(17)
if index == config.get('sleepAt'):
    time.sleep(10)
sys.stdout.buffer.write((root / ('reply-' + str(index))).read_bytes())
''')
        self.adb.chmod(0o700)
        self.prepare()

    def prepare(self, replies=None, **config):
        trace = self.root / 'trace.jsonl'
        if trace.exists():
            trace.unlink()
        replies = replies if replies is not None else [
            stat_bytes(),
            f'Name:\tworker\nPid:\t{PID}\nVmRSS:\t1234 kB\n'.encode(),
            b'MemTotal: 9000000 kB\nMemAvailable: 800000 kB\n',
            stat_bytes(),
        ]
        for i, reply in enumerate(replies):
            (self.root / ('reply-' + str(i))).write_bytes(reply)
        (self.root / 'config.json').write_text(json.dumps(config))

    def sampler(self, timeout=2.0):
        return AdbProcessSampler(str(self.adb), SERIAL, PACKAGE, PID, TICKS,
                                 timeout_seconds=timeout)

    def calls(self):
        p = self.root / 'trace.jsonl'
        return [json.loads(line) for line in p.read_text().splitlines()] if p.exists() else []

    def test_real_subprocess_success_and_exact_remote_commands(self):
        value = self.sampler().sample()
        self.assertEqual(value, {'pid': PID, 'startTicks': TICKS,
                                 'rssBytes': 1234 * 1024,
                                 'deviceAvailableBytes': 800000 * 1024})
        paths = [f'/proc/{PID}/stat', f'/proc/{PID}/status',
                 '/proc/meminfo', f'/proc/{PID}/stat']
        calls = self.calls()
        self.assertEqual(len(calls), 4)
        for argv, path in zip(calls, paths):
            self.assertEqual(argv[:3], ['-s', SERIAL, 'exec-out'])
            self.assertEqual(len(argv), 4)
            self.assertEqual(shlex.split(argv[3]),
                             ['run-as', PACKAGE, 'head', '-c', '65537', path])

    def test_real_subprocess_exit_failure_is_not_retried(self):
        for index in range(4):
            with self.subTest(index=index):
                self.prepare(failAt=index)
                with self.assertRaises(subprocess.CalledProcessError) as caught:
                    self.sampler().sample()
                self.assertEqual(caught.exception.returncode, 17)
                self.assertEqual(caught.exception.stderr,
                                 ('fixture-adb-error-' + str(index)).encode())
                self.assertEqual(len(self.calls()), index + 1)

    def test_real_subprocess_timeout_stops_after_first_command(self):
        self.prepare(sleepAt=0)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.sampler().sample()
        self.assertEqual(len(self.calls()), 1)

    def test_identity_mismatch_stops_at_the_observed_read(self):
        for index, replacement in [(0, stat_bytes(ticks='123456790')),
                                    (1, b'Pid: 732\nVmRSS: 1234 kB\n'),
                                    (3, stat_bytes(pid=732))]:
            with self.subTest(index=index):
                self.prepare()
                (self.root / ('reply-' + str(index))).write_bytes(replacement)
                with self.assertRaises(ValueError):
                    self.sampler().sample()
                self.assertEqual(len(self.calls()), index + 1)

    def test_measurements_are_not_replaced_by_acceptance_budget_defaults(self):
        self.prepare()
        (self.root / 'reply-1').write_bytes(f'Pid: {PID}\nVmRSS: 0 kB\n'.encode())
        (self.root / 'reply-2').write_bytes(b'MemAvailable: 0 kB\n')
        value = self.sampler().sample()
        self.assertEqual(value['rssBytes'], 0)
        self.assertEqual(value['deviceAvailableBytes'], 0)
        self.assertEqual(len(self.calls()), 4)

    def test_oversized_or_nonascii_transport_data_is_rejected(self):
        for data in (b'x' * 65537, stat_bytes() + b'\xff', stat_bytes() + b'\0'):
            with self.subTest(bytes=len(data)):
                self.prepare()
                (self.root / 'reply-0').write_bytes(data)
                with self.assertRaises(ValueError):
                    self.sampler().sample()
                self.assertEqual(len(self.calls()), 1)

    def test_configuration_rejects_string_subclasses(self):
        class StringSubclass(str):
            pass

        valid = dict(adb=str(self.adb), serial=SERIAL, package=PACKAGE,
                     pid=PID, start_ticks=TICKS)
        for key in ('adb', 'serial', 'package', 'start_ticks'):
            with self.subTest(key=key):
                config = dict(valid)
                config[key] = StringSubclass(config[key])
                with self.assertRaises(ValueError):
                    AdbProcessSampler(**config)
                self.assertEqual(self.calls(), [])

    def test_response_rejects_bytes_subclasses(self):
        class BytesSubclass(bytes):
            pass

        calls = []

        def runner(argv, timeout_seconds):
            calls.append(argv)
            return BytesSubclass(stat_bytes())

        sampler = AdbProcessSampler(str(self.adb), SERIAL, PACKAGE, PID, TICKS,
                                    runner=runner)
        with self.assertRaises(ValueError):
            sampler.sample()
        self.assertEqual(len(calls), 1)

    def test_empty_required_values_are_contract_errors(self):
        for index, response in [(1, b'Pid:\nVmRSS: 1234 kB\n'),
                                 (1, f'Pid: {PID}\nVmRSS:\n'.encode()),
                                 (2, b'MemAvailable:\n')]:
            with self.subTest(index=index, response=response):
                self.prepare()
                (self.root / ('reply-' + str(index))).write_bytes(response)
                with self.assertRaises(ValueError):
                    self.sampler().sample()
                self.assertEqual(len(self.calls()), index + 1)


if __name__ == '__main__':
    unittest.main()
