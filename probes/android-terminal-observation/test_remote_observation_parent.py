"""Actual local subprocess composition; does not invoke an Android device."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from adb_terminal_observation import publish_remote_terminal_observation
from adb_terminal_ack_publisher import RemoteAckPublicationError


FAKE = r'''
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
p=Path(__file__).parent
with (p/'calls.jsonl').open('a') as stream:
    stream.write(json.dumps(sys.argv[1:])+'\n')
assert sys.argv[1:3]==['-s','serial-01'] and len(sys.argv)==5
remote=shlex.split(sys.argv[4])
assert remote[:2]==['run-as','io.ondeviceai.acceptance.qwenterminal']
root=p/'remote'
if remote[2]=='head':
    assert remote[3:5]==['-c','65537']
    if (p/'pid-gone').exists():
        sys.stderr.write('simulated old process exit\n');sys.exit(18)
    if remote[5]=='/proc/123/stat':
        count=p/'stat-count';n=int(count.read_text())+1 if count.exists() else 1
        count.write_text(str(n))
        if n==6 and (p/'journal-fault').exists():
            (p/'observed/journal.json').write_bytes(b'KEEP')
        sys.stdout.write('123 (process with ) spaces) '+' '.join(['S']+['0']*18+['456'])+'\n')
    elif remote[5]=='/proc/123/status': sys.stdout.write('Pid:\t123\nVmRSS:\t2048 kB\n')
    elif remote[5]=='/proc/meminfo': sys.stdout.write('MemAvailable:\t1048576 kB\n')
    else: raise AssertionError('unexpected proc path')
elif remote[2:4]==['sh','-c']:
    assert len(remote)==5
    sys.exit(subprocess.run(['/bin/sh','-c',remote[4]],cwd=root).returncode)
else:
    assert remote[2]=='toybox'
    if remote[3]=='ln':
        assert remote[4]=='-T' and len(remote)==7
        os.link(root/remote[5],root/remote[6])
    elif remote[3]=='fsync':
        assert len(remote)==5
        target=root/remote[4]
        if target.is_dir() and (p/'dir-sync-fault').exists(): sys.exit(19)
        fd=os.open(target,os.O_RDONLY)
        try: os.fsync(fd)
        finally: os.close(fd)
        if target.name.endswith('.stage') and (p/'exit-before-link').exists():
            (p/'pid-gone').touch()
    else: raise AssertionError('unexpected toybox command')
'''


class RemoteObservationParent(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='remote-observer-parent-', dir='/private/tmp')
        self.root = Path(self.temp.name).resolve()
        self.output = self.root/'observed'
        self.output.mkdir()
        self.remote = self.root/'remote/files/w3-qwen-terminal'
        self.remote.mkdir(parents=True)
        self.binding = dict(runId='fresh-01', sourceFingerprint='a'*64, pid=123, startTicks='456')
        self.raw = json.dumps(dict(self.binding, terminal=True, status='completed')).encode()
        (self.remote/'report-fresh-01.json').write_bytes(self.raw)
        self.final = self.remote/'ack-fresh-01.txt'
        self.stage = self.remote/'ack-fresh-01.txt.stage'
        self.adb = self.root/'fake-adb with spaces'
        self.adb.write_text('#!'+sys.executable+'\n'+FAKE)
        self.adb.chmod(0o700)

    def tearDown(self):
        self.temp.cleanup()

    def observe(self, **kwargs):
        clock = kwargs.pop('monotonic_ns', iter([10, 20, 30, 40, 50, 60]).__next__)
        return publish_remote_terminal_observation(self.output, 'report.json',
            'receipt.json', 'ack.txt', 'journal.json', self.binding,
            adb=str(self.adb), serial='serial-01',
            package='io.ondeviceai.acceptance.qwenterminal', monotonic_ns=clock, **kwargs)

    def calls(self):
        p = self.root/'calls.jsonl'
        return [json.loads(line) for line in p.read_text().splitlines()] if p.exists() else []

    def test_actual_three_samples_and_remote_publication(self):
        result = self.observe()
        self.assertEqual(len(self.calls()), 19)
        self.assertEqual((self.output/'report.json').read_bytes(), self.raw)
        self.assertEqual(self.final.read_bytes(), self.stage.read_bytes())
        self.assertEqual(self.final.read_bytes(), (self.output/'ack.txt').read_bytes())
        self.assertEqual(self.final.stat().st_ino, self.stage.stat().st_ino)
        self.assertEqual(self.final.read_bytes().splitlines()[-1].decode(), hashlib.sha256(self.raw).hexdigest())
        receipt = json.loads((self.output/'receipt.json').read_bytes())
        journal = json.loads((self.output/'journal.json').read_bytes())
        self.assertEqual(receipt['clockDomain'], 'host-monotonic-ns')
        self.assertEqual(journal['clockDomain'], 'host-monotonic-ns')
        self.assertEqual(journal['sampleStartedNs'], 50)
        self.assertEqual(journal['sampleFinishedNs'], 60)
        self.assertEqual(journal['sampleBeforeLink']['pid'], 123)
        self.assertEqual(journal['binding'], receipt['binding'])
        self.assertEqual(result['observation'], receipt['observation'])
        self.assertIs(result['publication']['durability_verified'], True)

    def test_stale_outputs_refuse_before_callbacks(self):
        for name in ['report.json', 'receipt.json', 'ack.txt', 'journal.json']:
            p = self.output/name
            p.write_bytes(b'KEEP')
            with self.subTest(name=name):
                with self.assertRaises((ValueError, OSError)):
                    self.observe(monotonic_ns=lambda: self.fail('clock called before stale preflight'))
                self.assertEqual(self.calls(), [])
                self.assertEqual(p.read_bytes(), b'KEEP')
            p.unlink()

    def test_old_exit_race_blocks_link_and_retains_local_evidence(self):
        (self.root/'exit-before-link').touch()
        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.observe()
        self.assertEqual(raised.exception.phase, 'before-link')
        self.assertIs(raised.exception.publication_possible, False)
        self.assertIsInstance(raised.exception.__cause__, subprocess.CalledProcessError)
        self.assertFalse(self.final.exists())
        self.assertTrue(self.stage.is_file())
        self.assertTrue((self.output/'report.json').is_file())
        self.assertTrue((self.output/'receipt.json').is_file())
        self.assertTrue((self.output/'ack.txt').is_file())
        self.assertFalse((self.output/'journal.json').exists())
        self.assertEqual(len(self.calls()), 14)

    def test_journal_failure_prevents_link_without_cleanup(self):
        (self.root/'journal-fault').touch()
        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.observe()
        self.assertEqual(raised.exception.phase, 'before-link')
        self.assertIs(raised.exception.publication_possible, False)
        self.assertIsInstance(raised.exception.__cause__, (OSError, ValueError))
        self.assertFalse(self.final.exists())
        self.assertTrue(self.stage.is_file())
        self.assertEqual((self.output/'journal.json').read_bytes(), b'KEEP')
        self.assertEqual(len(self.calls()), 17)

    def test_directory_sync_failure_preserves_complete_final_and_journal(self):
        (self.root/'dir-sync-fault').touch()
        with self.assertRaises(RemoteAckPublicationError) as raised:
            self.observe()
        self.assertEqual(raised.exception.phase, 'directory-sync')
        self.assertIs(raised.exception.publication_possible, True)
        self.assertIs(raised.exception.durability_verified, False)
        self.assertEqual(self.final.read_bytes(), (self.output/'ack.txt').read_bytes())
        self.assertTrue((self.output/'journal.json').is_file())
        self.assertEqual(len(self.calls()), 19)

    def test_clock_regression_stops_after_first_read_without_outputs(self):
        with self.assertRaises(ValueError):
            self.observe(monotonic_ns=iter([10, 9]).__next__)
        self.assertEqual(len(self.calls()), 5)
        self.assertEqual(list(self.output.iterdir()), [])
        self.assertFalse(self.stage.exists())
        self.assertFalse(self.final.exists())


if __name__ == '__main__':
    unittest.main()
