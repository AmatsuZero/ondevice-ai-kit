"""Primary acceptance using real local subprocesses; never contacts Android."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from adb_terminal_report_reader import AdbTerminalReportReader


FAKE = r'''
import json
from pathlib import Path
import shlex
import subprocess
import sys
import time
p = Path(__file__).parent
with (p / 'calls.jsonl').open('a') as f:
    f.write(json.dumps(sys.argv[1:]) + '\n')
if (p / 'fail').exists():
    sys.stderr.write('standin transport failed\n')
    sys.exit(17)
if (p / 'delay').exists():
    time.sleep(2)
assert sys.argv[1:4] == ['-s', 'serial-01', 'exec-out']
assert len(sys.argv) == 5
remote = shlex.split(sys.argv[4])
assert remote[:4] == ['run-as', 'io.ondeviceai.acceptance.qwenterminal', 'sh', '-c']
assert len(remote) == 5
sys.exit(subprocess.run(['/bin/sh', '-c', remote[4]], cwd=p/'remote').returncode)
'''


class ReportReaderParentChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='w3-report-parent-', dir='/private/tmp')
        self.root = Path(self.temp.name).resolve()
        self.report = self.root/'remote/files/w3-qwen-terminal/report-fresh-01.json'
        self.report.parent.mkdir(parents=True)
        self.adb = self.root/'fake-adb with spaces'
        self.adb.write_text('#!' + sys.executable + '\n' + FAKE)
        self.adb.chmod(0o700)

    def tearDown(self):
        self.temp.cleanup()

    def reader(self, **kwargs):
        return AdbTerminalReportReader(str(self.adb), 'serial-01',
            'io.ondeviceai.acceptance.qwenterminal', 'fresh-01', **kwargs)

    def calls(self):
        p = self.root/'calls.jsonl'
        return [json.loads(line) for line in p.read_text().splitlines()] if p.exists() else []

    def test_real_default_subprocess_returns_fresh_exact_bytes(self):
        first = b'{"stage":"first"}\r\n\x00\xff'
        second = b'{"stage":"second"}\n'
        self.report.write_bytes(first)
        reader = self.reader()
        self.assertEqual(reader.read(), first)
        self.report.write_bytes(second)
        self.assertEqual(reader.read(), second)
        self.assertEqual(len(self.calls()), 2)
        self.assertEqual(self.report.read_bytes(), second)

    def test_real_shell_refuses_each_static_symlink(self):
        target = self.root/'target'
        target.write_bytes(b'KEEP')
        self.report.symlink_to(target)
        with self.assertRaises(subprocess.CalledProcessError): self.reader().read()
        self.assertEqual(target.read_bytes(), b'KEEP')
        self.report.unlink()
        parent = self.report.parent
        moved = parent.with_name('moved')
        parent.rename(moved)
        (moved/self.report.name).write_bytes(b'KEEP')
        parent.symlink_to(moved, target_is_directory=True)
        with self.assertRaises(subprocess.CalledProcessError): self.reader().read()
        parent.unlink()
        moved.rename(parent)
        files = parent.parent
        moved_files = files.with_name('moved-files')
        files.rename(moved_files)
        files.symlink_to(moved_files, target_is_directory=True)
        with self.assertRaises(subprocess.CalledProcessError): self.reader().read()
        self.assertEqual(len(self.calls()), 3)

    def test_missing_after_success_is_not_cached(self):
        self.report.write_bytes(b'first')
        reader = self.reader()
        self.assertEqual(reader.read(), b'first')
        self.report.unlink()
        with self.assertRaises(subprocess.CalledProcessError): reader.read()
        self.assertEqual(len(self.calls()), 2)

    def test_real_process_error_retains_stderr_and_does_not_retry(self):
        (self.root/'fail').touch()
        with self.assertRaises(subprocess.CalledProcessError) as raised: self.reader().read()
        self.assertEqual(raised.exception.returncode, 17)
        self.assertIn(b'standin transport failed', raised.exception.stderr)
        self.assertEqual(len(self.calls()), 1)

    def test_real_process_timeout_does_not_retry(self):
        (self.root/'delay').touch()
        with self.assertRaises(subprocess.TimeoutExpired): self.reader(timeout_seconds=0.25).read()
        self.assertEqual(len(self.calls()), 1)

    def test_real_bounded_head_accepts_limit_and_refuses_oversize_or_empty(self):
        reader = self.reader()
        limit = b'A' * 1048576
        self.report.write_bytes(limit)
        self.assertEqual(reader.read(), limit)
        self.report.write_bytes(limit + b'BC')
        with self.assertRaises(ValueError): reader.read()
        self.report.write_bytes(b'')
        with self.assertRaises(ValueError): reader.read()
        self.assertEqual(len(self.calls()), 3)
        self.assertEqual(self.report.read_bytes(), b'')

    def test_invalid_identity_types_fail_before_any_process(self):
        class StringSubclass(str):
            pass
        valid = [str(self.adb), 'serial-01', 'io.ondeviceai.acceptance.qwenterminal', 'fresh-01']
        for index in range(4):
            for invalid in [None, 7, b'bytes', StringSubclass(valid[index])]:
                with self.subTest(index=index, valueType=type(invalid).__name__):
                    args = valid.copy()
                    args[index] = invalid
                    with self.assertRaises(ValueError): AdbTerminalReportReader(*args)
        self.assertEqual(self.calls(), [])


if __name__ == '__main__':
    unittest.main()
