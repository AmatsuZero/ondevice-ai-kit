"""Actual local subprocess/file acceptance; does not execute Android toybox."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from adb_terminal_ack_publisher import AdbTerminalAckPublisher, RemoteAckPublicationError


FAKE = r'''
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import time
p = Path(__file__).parent
with (p/'calls.jsonl').open('a') as stream:
    stream.write(json.dumps(sys.argv[1:]) + '\n')
assert sys.argv[1:3] == ['-s', 'serial-01'] and len(sys.argv) == 5
remote = shlex.split(sys.argv[4])
assert remote[:2] == ['run-as', 'io.ondeviceai.acceptance.qwenterminal']
root = p/'remote'
if sys.argv[3] == 'exec-in':
    if (p/'delay').exists(): time.sleep(2)
    assert remote[2:4] == ['sh', '-c'] and len(remote) == 5
    data = sys.stdin.buffer.read()
    result = subprocess.run(['/bin/sh', '-c', remote[4]], input=data, cwd=root)
    if result.returncode == 0 and (p/'corrupt').exists():
        (root/'files/w3-qwen-terminal/ack-fresh-01.txt.stage').write_bytes(data[:-1])
    sys.exit(result.returncode)
assert sys.argv[3] == 'exec-out'
if remote[2:4] == ['sh', '-c']:
    sys.exit(subprocess.run(['/bin/sh', '-c', remote[4]], cwd=root).returncode)
assert remote[2] == 'toybox'
if remote[3] == 'ln':
    assert remote[4] == '-T' and len(remote) == 7
    os.link(root/remote[5], root/remote[6])
elif remote[3] == 'fsync':
    assert len(remote) == 5
    target = root/remote[4]
    if target.is_dir() and (p/'fail-dir-sync').exists():
        sys.stderr.write('standin directory sync failed\n')
        sys.exit(17)
    fd = os.open(target, os.O_RDONLY)
    try: os.fsync(fd)
    finally: os.close(fd)
else:
    raise AssertionError('unexpected command')
'''


class AckPublisherParentChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ack-publisher-parent-', dir='/private/tmp')
        self.root = Path(self.temp.name).resolve()
        self.directory = self.root/'remote/files/w3-qwen-terminal'
        self.directory.mkdir(parents=True)
        self.final = self.directory/'ack-fresh-01.txt'
        self.stage = self.directory/'ack-fresh-01.txt.stage'
        self.adb = self.root/'fake-adb with spaces'
        self.adb.write_text('#!' + sys.executable + '\n' + FAKE)
        self.adb.chmod(0o700)
        self.payload = ('ODAI-TERMINAL-ACK-v1\nfresh-01\n' + 'a'*64 + '\n321\n123\n' + 'b'*64 + '\n').encode()

    def tearDown(self):
        self.temp.cleanup()

    def publisher(self, **kwargs):
        return AdbTerminalAckPublisher(str(self.adb), 'serial-01',
            'io.ondeviceai.acceptance.qwenterminal', 'fresh-01', **kwargs)

    def calls(self):
        path = self.root/'calls.jsonl'
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def test_actual_complete_stage_is_hidden_until_link_and_retained(self):
        callback_calls = []
        def before_link():
            callback_calls.append(True)
            self.assertEqual(len(self.calls()), 3)
            self.assertFalse(self.final.exists())
            self.assertEqual(self.stage.read_bytes(), self.payload)
        receipt = self.publisher().publish(self.payload, before_link=before_link)
        self.assertEqual(callback_calls, [True])
        self.assertEqual(len(self.calls()), 5)
        self.assertEqual(self.final.read_bytes(), self.payload)
        self.assertEqual(self.stage.read_bytes(), self.payload)
        self.assertEqual(self.final.stat().st_ino, self.stage.stat().st_ino)
        self.assertIs(receipt['published'], True)
        self.assertIs(receipt['durability_verified'], True)
        self.assertIs(receipt['stage_retained'], True)

    def test_actual_preexisting_entries_are_not_overwritten(self):
        target = self.root/'KEEP-target'
        target.write_bytes(b'KEEP')
        for path in [self.final, self.stage]:
            for kind in ['file', 'directory', 'dangling-link']:
                with self.subTest(path=path.name, kind=kind):
                    if kind == 'file': path.write_bytes(b'KEEP')
                    elif kind == 'directory': path.mkdir()
                    else: path.symlink_to(self.root/'absent-target')
                    with self.assertRaises(RemoteAckPublicationError) as raised:
                        self.publisher().publish(self.payload, before_link=lambda: self.fail('unexpected callback'))
                    self.assertEqual(raised.exception.phase, 'stage-write')
                    self.assertIs(raised.exception.publication_possible, False)
                    if kind == 'file': self.assertEqual(path.read_bytes(), b'KEEP')
                    elif kind == 'directory': self.assertTrue(path.is_dir())
                    else: self.assertTrue(path.is_symlink())
                    if kind == 'directory': path.rmdir()
                    else: path.unlink()
        self.assertEqual(target.read_bytes(), b'KEEP')
        self.assertEqual(len(self.calls()), 6)

    def test_exec_in_exit_zero_with_short_stage_cannot_publish(self):
        (self.root/'corrupt').touch()
        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.publisher().publish(self.payload, before_link=lambda: self.fail('unexpected callback'))
        self.assertEqual(raised.exception.phase, 'stage-verify')
        self.assertIs(raised.exception.publication_possible, False)
        self.assertEqual(len(self.calls()), 2)
        self.assertFalse(self.final.exists())
        self.assertEqual(self.stage.read_bytes(), self.payload[:-1])

    def test_directory_sync_failure_retains_complete_visible_final(self):
        (self.root/'fail-dir-sync').touch()
        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.publisher().publish(self.payload, before_link=lambda: None)
        error = raised.exception
        self.assertEqual(error.phase, 'directory-sync')
        self.assertIs(error.publication_possible, True)
        self.assertIs(error.durability_verified, False)
        self.assertIs(error.stage_may_remain, True)
        self.assertIsInstance(error.__cause__, subprocess.CalledProcessError)
        self.assertIn(b'standin directory sync failed', error.__cause__.stderr)
        self.assertEqual(self.final.read_bytes(), self.payload)
        self.assertEqual(self.stage.read_bytes(), self.payload)
        self.assertEqual(len(self.calls()), 5)

    def test_before_link_failure_preserves_stage_without_final(self):
        sentinel = RuntimeError('resource gate failed')
        def reject(): raise sentinel
        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.publisher().publish(self.payload, before_link=reject)
        self.assertEqual(raised.exception.phase, 'before-link')
        self.assertIs(raised.exception.__cause__, sentinel)
        self.assertIs(raised.exception.publication_possible, False)
        self.assertEqual(self.stage.read_bytes(), self.payload)
        self.assertFalse(self.final.exists())
        self.assertEqual(len(self.calls()), 3)

    def test_real_stage_write_timeout_stops_without_retry(self):
        (self.root/'delay').touch()
        publisher = self.publisher(timeout_seconds=0.25)
        with self.assertRaises(RemoteAckPublicationError) as raised:
            publisher.publish(self.payload, before_link=lambda: self.fail('unexpected callback'))
        self.assertEqual(raised.exception.phase, 'stage-write')
        self.assertIsInstance(raised.exception.__cause__, subprocess.TimeoutExpired)
        self.assertIs(raised.exception.publication_possible, False)
        self.assertEqual(len(self.calls()), 1)
        self.assertFalse(self.final.exists())
        with self.assertRaises(RuntimeError): publisher.publish(self.payload, before_link=lambda: None)
        self.assertEqual(len(self.calls()), 1)


if __name__ == '__main__':
    unittest.main()
