"""Requested-policy collection via real accepted collection and fake-ADB children."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
QT = ROOT / 'probes/android-qwen-terminal'
sys.path[:0] = [str(QT), str(ROOT / 'probes/android-terminal-observation')]
import test_terminal_collect_parent as collect_parent
import terminal_device_collect
import terminal_file_io as tf

# Route real candidate bytes in this isolated test process; never replace guards.
if os.environ.get('ODAI_POLICY_COLLECT_SOURCE'):
    spec = importlib.util.spec_from_file_location('terminal_device_collect', os.environ['ODAI_POLICY_COLLECT_SOURCE'])
    terminal_device_collect = importlib.util.module_from_spec(spec)
    sys.modules['terminal_device_collect'] = terminal_device_collect
    spec.loader.exec_module(terminal_device_collect)

APP = 'io.ondeviceai.acceptance.qwenterminal'
RUN = 'w3-qwen-terminal-20261112-01'
POLICY = b'EnableCpuMemArena=0\n'
REL = 'no_backup/cpu-arena-off/cpu.config'
FRAMEWORK = '/data/data/' + APP + '/no_backup'
SHELL = '/data/user/0/' + APP + '/no_backup'
TARGET = Path(os.environ.get('ODAI_POLICY_SOURCE', str(QT / 'terminal_device_policy.py')))


class TerminalPolicyParent(unittest.TestCase):
    bind_local = collect_parent.TerminalCollectParent.bind_local

    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing isolated terminal policy collector')
        spec = importlib.util.spec_from_file_location('terminal_policy_under_test', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def fixture(self, root=FRAMEWORK, absent=False):
        f = collect_parent.TerminalCollectParent.fixture(self, 'failed' if absent else 'completed')
        source = f.adb.read_text()
        old = " assert app is not None and not s.startswith('/') and '..' not in s.split('/'),s"
        new = (" for prefix in ('/data/data/'+APP+'/', '/data/user/0/'+APP+'/'):\n"
               "  if s.startswith(prefix):\n"
               "   assert app==APP;return device/'apps'/APP/s.removeprefix(prefix)\n" + old)
        self.assertEqual(source.count(old), 1)
        source = source.replace(old, new)
        old = "if tokens[:2]==['cat','/proc/self/status']:"
        self.assertEqual(source.count(old), 1)
        source = source.replace(old, "if tokens==['id','-u']:print(os.getuid());sys.exit(0)\n" + old)
        old = "if tokens[0]=='test':"
        source = source.replace(old,
            "if tokens[:3]==['test','!','-L']:\n"
            " assert len(tokens)==4;sys.exit(1 if mapped(tokens[3],app).is_symlink() else 0)\n" + old)
        old = " f=mapped(tokens[3],app);s=f.stat();print(str(s.st_uid)+' '+str(s.st_dev)+' '+str(s.st_ino));sys.exit(0)"
        new = (" f=mapped(tokens[3],app);s=f.stat()\n"
               " if tokens[2]=='%u %d %i %s':\n"
               "  uid=s.st_uid;ino=s.st_ino\n"
               "  mode=(p/'policy-stat-mode').read_text() if (p/'policy-stat-mode').exists() else ''\n"
               "  if mode=='owner' and tokens[3].endswith('cpu.config'):uid+=1\n"
               "  if mode=='inode' and tokens[3].startswith('/data/data/'):ino+=1\n"
               "  if mode=='post-inode' and (p/'policy-read-count').exists() and (p/'policy-read-count').read_text()=='2':ino+=1\n"
               "  if mode=='malformed' and tokens[3].endswith('cpu.config'):\n"
               "   print('01 '+str(s.st_dev)+' '+str(ino)+' '+str(s.st_size));sys.exit(0)\n"
               "  print(str(uid)+' '+str(s.st_dev)+' '+str(ino)+' '+str(s.st_size));sys.exit(0)\n"
               " print(str(s.st_uid)+' '+str(s.st_dev)+' '+str(s.st_ino));sys.exit(0)")
        self.assertEqual(source.count(old), 1)
        source = source.replace(old, new)
        old = " f=mapped(tokens[3],app);sys.stdout.buffer.write(f.read_bytes()[:int(tokens[2])]);sys.exit(0)"
        new = (" f=mapped(tokens[3],app)\n"
               " if tokens[3]=='" + REL + "':\n"
               "  count=int((p/'policy-read-count').read_text())+1 if (p/'policy-read-count').exists() else 1\n"
               "  (p/'policy-read-count').write_text(str(count))\n"
               "  if (p/'change-policy-second').exists() and count==2:f.write_bytes(f.read_bytes()+b' ')\n"
               " sys.stdout.buffer.write(f.read_bytes()[:int(tokens[2])]);sys.exit(0)")
        self.assertEqual(source.count(old), 1)
        f.adb.write_text(source.replace(old, new))
        f.policy_path = f.device / 'apps' / APP / REL
        if not absent:
            f.policy_path.parent.mkdir(parents=True)
            f.policy_path.write_bytes(POLICY)
        f.report.update(nativePolicyEffectVerified=False,
                        config=dict(provider=None if absent else 'cpu:' + root + '/cpu-arena-off/cpu.config',
                                    requestedEnableCpuMemArena=False),
                        allocatorPolicy=None if absent else dict(provider='cpu:' + root + '/cpu-arena-off/cpu.config',
                            configPath=root + '/cpu-arena-off/cpu.config', bytes=len(POLICY),
                            sha256=hashlib.sha256(POLICY).hexdigest()))
        f.raw = json.dumps(f.report).encode()
        f.remote_report.write_bytes(f.raw)
        self.bind_local(f)
        terminal_device_collect.collect(f.out, f.context, serial='serial-01')
        f.policy_calls_before = len((f.root / 'calls.jsonl').read_text().splitlines())
        f.policy_guards_before = f.guards
        f.policy_mutations_before = len(f.mutations())
        return f

    def rewrite_report(self, f):
        f.raw = json.dumps(f.report).encode()
        f.remote_report.write_bytes(f.raw)
        self.bind_local(f)
        for name in ['device-report.json', 'device-report-confirm.json']:
            (f.out / name).write_bytes(f.raw)
        path = f.out / 'collection.json'
        row = json.loads(path.read_text())
        row.update(sha256=hashlib.sha256(f.raw).hexdigest(), status=f.report['status'])
        path.write_text(json.dumps(row))

    def collect(self, f):
        return self.module.collect_policy(f.out, f.context, serial='serial-01')

    def assert_no_policy_transport(self, f):
        self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.policy_calls_before)
        for name in ['allocator-policy-collected.json', 'allocator-policy-first.bin', 'allocator-policy-confirm.bin']:
            self.assertFalse((f.out / name).exists())

    def test_actual_three_alias_identity_and_two_bounded_reads(self):
        for root in [FRAMEWORK, SHELL]:
            with self.subTest(root=root):
                f = self.fixture(root)
                result = self.collect(f)
                for flag in ['receiptMatches', 'fileBytesMatch', 'twoReadsIdentical', 'ordinaryUnchanged']:
                    self.assertIs(result[flag], True)
                for flag in ['nativePolicyEffectVerified', 'qualityAccepted', 'truePeakMeasured', 'automaticRetry']:
                    self.assertIs(result[flag], False)
                self.assertEqual(result['privateRoot'], root)
                self.assertEqual(result['boundedReadBytes'], 20)
                self.assertEqual(result['readLimitBytes'], 21)
                self.assertEqual(json.loads((f.out / 'allocator-policy-collected.json').read_text()), result)
                for name in ['allocator-policy-first.bin', 'allocator-policy-confirm.bin']:
                    self.assertEqual((f.out / name).read_bytes(), POLICY)
                self.assertEqual((f.root / 'policy-read-count').read_text(), '2')
                self.assertEqual((f.root / 'report-read-count').read_text(), '2')
                calls = [json.loads(x) for x in (f.root / 'calls.jsonl').read_text().splitlines()][f.policy_calls_before:]
                self.assertEqual(f.guards - f.policy_guards_before, len(calls))
                self.assertFalse(any('/proc/' in arg or arg == 'pidof' or arg == 'instrument'
                                     for call in calls for arg in call))
                self.assertEqual(f.mutations()[f.policy_mutations_before:], [])

    def test_absent_policy_is_truthful_failed_constructor_observation(self):
        f = self.fixture(absent=True)
        result = self.collect(f)
        self.assertIs(result['policyPresent'], False)
        self.assertIs(result['receiptMatches'], False)
        self.assertIs(result['fileBytesMatch'], False)
        self.assertIsNone(result['privateRoot'])
        self.assertIs(result['nativePolicyEffectVerified'], False)
        self.assertFalse((f.root / 'policy-read-count').exists())
        self.assertFalse((f.out / 'allocator-policy-first.bin').exists())

    def test_coherent_policy_metadata_corruption_refuses_locally(self):
        for mode in ['bytes-bool', 'foreign-path', 'sha', 'provider', 'requested-bool', 'native-effect']:
            with self.subTest(mode=mode):
                f = self.fixture()
                if mode == 'bytes-bool': f.report['allocatorPolicy']['bytes'] = True
                elif mode == 'foreign-path': f.report['allocatorPolicy']['configPath'] = FRAMEWORK + '/x/../cpu-arena-off/cpu.config'
                elif mode == 'sha': f.report['allocatorPolicy']['sha256'] = 'a' * 64
                elif mode == 'provider': f.report['config']['provider'] = 'cpu'
                elif mode == 'requested-bool': f.report['config']['requestedEnableCpuMemArena'] = 0
                elif mode == 'native-effect': f.report['nativePolicyEffectVerified'] = True
                self.rewrite_report(f)
                with self.assertRaises((OSError, ValueError, AssertionError)):
                    self.collect(f)
                self.assert_no_policy_transport(f)

    def test_existing_policy_outcome_or_unbound_collection_refuses_locally(self):
        for mode in ['old-outcome', 'false-collection', 'schema-bool', 'extra-model', 'confirm-diff']:
            with self.subTest(mode=mode):
                f = self.fixture()
                if mode == 'old-outcome':
                    (f.out / 'allocator-policy-collected.json').symlink_to(f.out / 'absent-protected')
                elif mode in ['false-collection', 'schema-bool', 'extra-model']:
                    path = f.out / 'collection.json';row = json.loads(path.read_text())
                    if mode == 'false-collection': row['terminalAccepted'] = False
                    elif mode == 'schema-bool': row['schemaVersion'] = True
                    else: row['modelHashesAfterMatch']['foreign'] = True
                    path.write_text(json.dumps(row))
                else: (f.out / 'device-report-confirm.json').write_bytes(f.raw + b' ')
                with self.assertRaises((OSError, ValueError, AssertionError)):
                    self.collect(f)
                self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.policy_calls_before)

    def test_wrong_owner_inode_or_symlink_preserves_remote_and_records_failure(self):
        for mode in ['owner', 'inode', 'post-inode', 'malformed', 'link']:
            with self.subTest(mode=mode):
                f = self.fixture()
                if mode == 'link':
                    foreign = f.root / 'foreign-policy';foreign.write_bytes(POLICY)
                    f.policy_path.unlink();f.policy_path.symlink_to(foreign)
                else: (f.root / 'policy-stat-mode').write_text(mode)
                with self.assertRaises((OSError, ValueError, AssertionError, RuntimeError)):
                    self.collect(f)
                receipt = json.loads((f.out / 'allocator-policy-collected.json').read_text())
                self.assertIs(receipt['fileBytesMatch'], False)
                self.assertTrue(receipt['errors'])
                self.assertEqual(f.policy_path.read_bytes(), POLICY)
                self.assertEqual(f.mutations()[f.policy_mutations_before:], [])

    def test_second_policy_change_retains_both_reads_and_false_outcome(self):
        f = self.fixture();(f.root / 'change-policy-second').touch()
        with self.assertRaises((OSError, ValueError, AssertionError)):
            self.collect(f)
        receipt = json.loads((f.out / 'allocator-policy-collected.json').read_text())
        self.assertIs(receipt['fileBytesMatch'], False)
        self.assertEqual((f.out / 'allocator-policy-first.bin').read_bytes(), POLICY)
        self.assertEqual((f.out / 'allocator-policy-confirm.bin').read_bytes(), POLICY + b' ')
        self.assertEqual(f.policy_path.read_bytes(), POLICY + b' ')

    def test_primary_interruption_survives_secondary_journal_baseexception(self):
        f = self.fixture();primary = KeyboardInterrupt('primary policy interrupted')
        actual = f.context['call']
        def call(*args, **kwargs):
            result = actual(*args, **kwargs)
            if args[0] == 'exec-out': raise primary
            return result
        f.context['call'] = call
        real_write = tf.write_new_durable
        def writer(root, relative, payload):
            if relative == 'allocator-policy-collected.json': raise SystemExit('secondary policy journal interrupted')
            return real_write(root, relative, payload)
        with patch.object(tf, 'write_new_durable', side_effect=writer):
            with self.assertRaises(KeyboardInterrupt) as caught: self.collect(f)
        self.assertIs(caught.exception, primary)
        self.assertIn('secondary policy journal interrupted', ' '.join(getattr(primary, '__notes__', [])))

    def test_published_policy_outcome_error_is_not_rewritten(self):
        f = self.fixture();primary = tf.PublishedButNotDurableError('policy outcome directory fsync uncertain')
        actual = tf.write_new_durable;writes = []
        def writer(root, relative, payload):
            writes.append(relative);actual(root, relative, payload)
            if relative == 'allocator-policy-collected.json': raise primary
        with patch.object(tf, 'write_new_durable', side_effect=writer):
            with self.assertRaises(tf.PublishedButNotDurableError) as caught: self.collect(f)
        self.assertIs(caught.exception, primary)
        self.assertEqual(writes.count('allocator-policy-collected.json'), 1)


if __name__ == '__main__':
    unittest.main(verbosity=2)
