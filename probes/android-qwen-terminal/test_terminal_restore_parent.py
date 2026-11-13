"""Actual fake-ADB ownership recovery; no Android or native decoding."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
QT = ROOT / 'probes/android-qwen-terminal'
sys.path.insert(0, str(QT))
from terminal_prepare_fixture import PrepareFixture, APP, TEST, RUN, FP, STAGE
import terminal_device_prepare
import terminal_file_io

TARGET = Path(os.environ.get('ODAI_RESTORE_SOURCE', str(QT / 'terminal_device_restore.py')))


class TerminalRestoreParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing ownership-proven restoration implementation')
        spec = importlib.util.spec_from_file_location('restore_under_test', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def fixture(self, fault=None):
        f = PrepareFixture(self)
        script = f.adb.read_text()
        script = script.replace(
            "if tokens==['dumpsys','activity','processes']:print('No active instrumentation');sys.exit(0)",
            "if tokens==['dumpsys','activity','activities']:\n print('mResumedActivity: ActivityRecord{1 u0 io.ondeviceai.demo/.MainActivity t1}' if (p/'foreground').exists() else 'mResumedActivity: ActivityRecord{2 u0 '+APP+'/.MainActivity t2}');sys.exit(0)\n"
            "if tokens==['dumpsys','activity','processes']:print('No active instrumentation');sys.exit(0)")
        script = script.replace("mutate('return-demo');print('Starting: Intent')",
                                "(p/'foreground').touch();mutate('return-demo');print('Starting: Intent')")
        script = script.replace(
            "def fault(label):",
            " if (p/'restore-faults.json').exists():\n"
            "  labels=json.loads((p/'restore-faults.json').read_text());done=p/('restore-fault-'+label)\n"
            "  if label in labels and not done.exists():done.touch();sys.exit(23)\n"
            "def fault(label):")
        f.adb.write_text(script)
        if fault:
            f.fault(fault)
            with self.assertRaises(RuntimeError):
                f.call(terminal_device_prepare)
        else:
            f.call(terminal_device_prepare)
        self.assertFalse((f.out / 'collection.json').exists())
        f.restore_start = len(f.mutations())
        return f

    def restore(self, f):
        return self.module.restore(f.out, f.context, serial='serial-01')

    def assert_ordinary(self, f):
        self.assertEqual(f.context['ordinary'](), f.before_ordinary)
        self.assertEqual(f.context['ordinary_models'](), f.before_models)
        self.assertEqual((f.device / 'ordinary.apk').read_bytes(), b'original ordinary app')

    def assert_cleaned(self, f, receipt):
        self.assertIs(receipt['allRestored'], True)
        self.assertIs(receipt['ordinaryUnchanged'], True)
        self.assertIs(receipt['returnedToDemo'], True)
        self.assertEqual(receipt['runId'], RUN)
        self.assertEqual(receipt['sourceFingerprint'], FP)
        self.assertEqual(receipt['errors'], [])
        self.assertFalse((f.device / 'stage' / RUN).exists())
        self.assertFalse((f.device / 'apps' / APP).exists())
        self.assertFalse((f.device / 'apps' / TEST).exists())
        self.assertTrue((f.root / 'foreground').exists())
        self.assert_ordinary(f)
        self.assertEqual(json.loads((f.out / 'restoration.json').read_text()), receipt)

    def test_success_without_collection_removes_only_owned_aliases_and_flat_stage(self):
        f = self.fixture()
        self.assert_cleaned(f, self.restore(f))
        calls = [json.loads(line) for line in (f.root / 'calls.jsonl').read_text().splitlines()]
        self.assertFalse(any('-rf' in arg for call in calls for arg in call))

    def test_partial_push_concat_install_and_model_copy_recover_without_success_receipts(self):
        for fault in ['partial-push', 'after-concat', 'after-main-install',
                      'after-test-install', 'after-model-copy']:
            with self.subTest(fault=fault):
                f = self.fixture(fault)
                self.assert_cleaned(f, self.restore(f))

    def test_marker_proof_can_recover_when_created_journal_is_missing(self):
        f = self.fixture('after-owner')
        self.assertFalse((f.out / 'ownership-stage-created.json').exists())
        self.assert_cleaned(f, self.restore(f))
        observed = json.loads((f.out / 'ownership-stage-recovered.json').read_text())
        self.assertEqual(observed['nonce'], '1' * 32)
        self.assertEqual(set(observed['stageIdentity']), {'uid', 'dev', 'ino'})

    def test_mkdir_without_marker_is_retained_with_explicit_failure(self):
        f = self.fixture('after-stage-mkdir')
        receipt = self.restore(f)
        self.assertIs(receipt['allRestored'], False)
        self.assertTrue(receipt['errors'])
        self.assertTrue((f.device / 'stage' / RUN).is_dir())
        self.assertFalse(any(row['label'].startswith('remove-') for row in f.mutations()[f.restore_start:]))
        self.assert_ordinary(f)

    def test_unexpected_child_or_link_prevents_any_stage_deletion(self):
        for mode in ['unknown', 'link']:
            with self.subTest(mode=mode):
                f = self.fixture()
                stage = f.device / 'stage' / RUN
                if mode == 'unknown':
                    (stage / 'unexpected').write_bytes(b'protected unknown')
                else:
                    (stage / 'main.apk').unlink()
                    (stage / 'main.apk').symlink_to(f.device / 'ordinary.apk')
                before = sorted(p.name for p in stage.iterdir())
                receipt = self.restore(f)
                self.assertIs(receipt['allRestored'], False)
                self.assertTrue(receipt['errors'])
                self.assertEqual(sorted(p.name for p in stage.iterdir()), before)
                self.assertFalse(any(row['label'].startswith('remove-') for row in f.mutations()[f.restore_start:]))
                self.assert_ordinary(f)

    def test_nonce_inode_and_installed_sha_mismatch_are_not_adopted(self):
        for mode in ['nonce', 'inode', 'APK']:
            with self.subTest(mode=mode):
                f = self.fixture()
                stage = f.device / 'stage' / RUN
                if mode == 'nonce':
                    (stage / '.odai-owner').write_bytes(b'2' * 32 + b'\n')
                elif mode == 'inode':
                    created = f.out / 'ownership-stage-created.json'
                    row = json.loads(created.read_text())
                    row['stageIdentity']['ino'] = str(int(row['stageIdentity']['ino']) + 100)
                    created.write_text(json.dumps(row))
                else:
                    (f.device / 'apps' / APP / 'base.apk').write_bytes(b'foreign alias APK')
                receipt = self.restore(f)
                self.assertIs(receipt['allRestored'], False)
                self.assertTrue(receipt['errors'])
                if mode == 'APK':
                    self.assertEqual((f.device / 'apps' / APP / 'base.apk').read_bytes(), b'foreign alias APK')
                    self.assertFalse(any(row['label'] == 'force-stop' for row in f.mutations()[f.restore_start:]))
                else:
                    self.assertTrue(stage.is_dir())
                    self.assertFalse(any(row['label'].startswith('remove-') for row in f.mutations()[f.restore_start:]))
                self.assert_ordinary(f)

    def test_foreign_local_binding_or_existing_outcome_refuses_before_mutation(self):
        for mode in ['serial', 'outcome']:
            with self.subTest(mode=mode):
                f = self.fixture()
                if mode == 'serial':
                    path = f.out / 'ownership-stage-claimed.json'
                    row = json.loads(path.read_text())
                    row['serial'] = 'foreign-serial'
                    path.write_text(json.dumps(row))
                else:
                    (f.out / 'restoration.json').write_bytes(b'protected prior outcome')
                with self.assertRaises((ValueError, AssertionError, OSError)):
                    self.restore(f)
                self.assertEqual(f.mutations()[f.restore_start:], [])

    def test_invalid_or_corrupted_claim_is_not_treated_as_missing_ownership(self):
        for mode in ['created-json', 'created-link', 'before-bool-schema', 'before-user',
                     'before-duplicate', 'before-missing-hash', 'claimed-extra-child', 'install-foreign-sha']:
            with self.subTest(mode=mode):
                f = self.fixture()
                if mode == 'created-json':
                    (f.out / 'ownership-stage-created.json').write_bytes(b'{corrupted')
                elif mode == 'created-link':
                    created = f.out / 'ownership-stage-created.json'
                    moved = f.out / 'protected-created.json'
                    created.rename(moved)
                    created.symlink_to(moved)
                elif mode.startswith('before-'):
                    target = f.out / 'device-before.json'
                    row = json.loads(target.read_text())
                    if mode == 'before-bool-schema':
                        row['schemaVersion'] = True
                    elif mode == 'before-user':
                        row['currentUser'] = '10'
                    elif mode == 'before-missing-hash':
                        row['ordinaryAPKHashes'] = {}
                    if mode == 'before-duplicate':
                        target.write_text(target.read_text()[:-1] + ',"schemaVersion":1}')
                    else:
                        target.write_text(json.dumps(row))
                elif mode == 'claimed-extra-child':
                    target = f.out / 'ownership-stage-claimed.json'
                    row = json.loads(target.read_text())
                    row['children'].append('protected-unclaimed')
                    target.write_text(json.dumps(row))
                    (f.device / 'stage' / RUN / 'protected-unclaimed').write_bytes(b'unknown original')
                else:
                    target = f.out / 'ownership-install-main-claimed.json'
                    row = json.loads(target.read_text())
                    apk = f.device / 'apps' / APP / 'base.apk'
                    apk.write_bytes(b'foreign alias APK')
                    row['sha256'] = f.sha(apk)
                    target.write_text(json.dumps(row))
                with self.assertRaises((ValueError, AssertionError, OSError)):
                    self.restore(f)
                self.assertEqual(f.mutations()[f.restore_start:], [])

    def test_stale_nonresumed_demo_record_is_not_foreground_success(self):
        f = self.fixture()
        original = f.adb.read_text()
        f.adb.write_text(original.replace(
            "mResumedActivity: ActivityRecord{1 u0 io.ondeviceai.demo/.MainActivity t1}",
            "mLastPausedActivity: ActivityRecord{1 u0 io.ondeviceai.demo/.MainActivity t1}"))
        self.assertNotEqual(f.adb.read_text(), original)
        receipt = self.restore(f)
        self.assertIs(receipt['allRestored'], False)
        self.assertIs(receipt['returnedToDemo'], False)
        self.assertTrue(receipt['errors'])

    def test_present_null_claim_is_invalid_and_cannot_be_adopted_as_absent(self):
        for name in ['ownership-stage-claimed.json', 'ownership-stage-created.json',
                     'ownership-install-main-claimed.json', 'ownership-install-test-claimed.json']:
            with self.subTest(name=name):
                f = self.fixture()
                (f.out / name).write_bytes(b'null')
                with self.assertRaises((ValueError, AssertionError, OSError)):
                    self.restore(f)
                self.assertEqual(f.mutations()[f.restore_start:], [])

    def test_resumed_component_requires_exact_activity_and_user_zero(self):
        for component in ['io.ondeviceai.demo/.MainActivityForeign',
                          'foreign.io.ondeviceai.demo/.MainActivity',
                          'io.ondeviceai.demo/io.ondeviceai.demo.MainActivityForeign',
                          'io.ondeviceai.demo/.MainActivity t1} u10']:
            with self.subTest(component=component):
                f = self.fixture()
                original = f.adb.read_text()
                record = ('mResumedActivity: ActivityRecord{1 u10 io.ondeviceai.demo/.MainActivity t1}'
                          if component.endswith(' u10') else
                          'mResumedActivity: ActivityRecord{1 u0 ' + component + ' t1}')
                f.adb.write_text(original.replace(
                    'mResumedActivity: ActivityRecord{1 u0 io.ondeviceai.demo/.MainActivity t1}', record))
                self.assertNotEqual(f.adb.read_text(), original)
                receipt = self.restore(f)
                self.assertIs(receipt['allRestored'], False)
                self.assertIs(receipt['returnedToDemo'], False)
                self.assertTrue(receipt['errors'])

    def test_ordinary_apk_content_drift_blocks_all_recovery_mutation(self):
        f = self.fixture()
        (f.device / 'ordinary.apk').write_bytes(b'changed original ordinary APK')
        receipt = self.restore(f)
        self.assertIs(receipt['allRestored'], False)
        self.assertIs(receipt['ordinaryUnchanged'], False)
        self.assertTrue(receipt['errors'])
        self.assertEqual(f.mutations()[f.restore_start:], [])
        self.assertEqual((f.device / 'ordinary.apk').read_bytes(), b'changed original ordinary APK')

    def test_multiple_uncertain_transport_failures_are_recorded_without_false_success(self):
        f = self.fixture()
        (f.root / 'restore-faults.json').write_text(json.dumps(['uninstall', 'remove-file']))
        receipt = self.restore(f)
        self.assertIs(receipt['allRestored'], False)
        self.assertGreaterEqual(len(receipt['errors']), 2)
        self.assertEqual(json.loads((f.out / 'restoration.json').read_text()), receipt)
        self.assert_ordinary(f)

    def test_observed_ownership_must_be_durable_before_first_stage_delete(self):
        f = self.fixture('after-owner')
        actual = terminal_file_io.write_new_durable
        failure = OSError('observed ownership fsync failed')
        def writer(root, relative, payload):
            if relative == 'ownership-stage-recovered.json':
                raise failure
            return actual(root, relative, payload)
        with patch.object(terminal_file_io, 'write_new_durable', side_effect=writer):
            receipt = self.restore(f)
        self.assertIs(receipt['allRestored'], False)
        self.assertTrue(receipt['errors'])
        self.assertTrue((f.device / 'stage' / RUN / '.odai-owner').is_file())
        self.assertFalse(any(row['label'].startswith('remove-') for row in f.mutations()[f.restore_start:]))

    def test_device_user_mismatch_prevents_all_recovery_mutation(self):
        f = self.fixture()
        original = f.adb.read_text()
        f.adb.write_text(original.replace("if tokens==['am','get-current-user']:print('0')",
                                        "if tokens==['am','get-current-user']:print('10')"))
        self.assertNotEqual(f.adb.read_text(), original)
        receipt = self.restore(f)
        self.assertIs(receipt['allRestored'], False)
        self.assertTrue(receipt['errors'])
        self.assertEqual(f.mutations()[f.restore_start:], [])

    def test_stage_inode_replacement_during_probes_is_rejected_before_first_delete(self):
        f = self.fixture()
        stage = f.device / 'stage' / RUN
        moved = f.device / 'stage' / 'protected-original-stage'
        original = f.adb.read_text()
        f.adb.write_text(original.replace(
            "f=mapped(tokens[2],app);print('\\n'.join(sorted(x.name for x in f.iterdir())))",
            "f=mapped(tokens[2],app);moved=device/'stage'/'protected-original-stage'\n"
            " if not moved.exists():f.rename(moved);shutil.copytree(moved,f)\n"
            " print('\\n'.join(sorted(x.name for x in f.iterdir())))"))
        self.assertNotEqual(f.adb.read_text(), original)
        receipt = self.restore(f)
        self.assertTrue(moved.is_dir())
        self.assertIs(receipt['allRestored'], False)
        self.assertTrue(receipt['errors'])
        self.assertTrue((stage / '.odai-owner').is_file())
        self.assertFalse(any(row['label'].startswith('remove-') for row in f.mutations()[f.restore_start:]))

    def test_demo_start_transport_failure_returns_durable_failure(self):
        f = self.fixture()
        (f.root / 'restore-faults.json').write_text(json.dumps(['return-demo']))
        receipt = self.restore(f)
        self.assertIs(receipt['allRestored'], False)
        self.assertTrue(receipt['errors'])
        self.assertIs(receipt['returnedToDemo'], False)
        self.assertEqual(json.loads((f.out / 'restoration.json').read_text()), receipt)

    def test_secondary_journal_interrupt_never_masks_primary_recovery_interrupt(self):
        f = self.fixture()
        actual_call = f.context['call']
        primary = KeyboardInterrupt('primary remove interrupt')
        def call(*args, **kwargs):
            result = actual_call(*args, **kwargs)
            if args[:3] == ('shell', 'rm', '-f'):
                raise primary
            return result
        f.context['call'] = call
        actual_write = terminal_file_io.write_new_durable
        def writer(root, relative, payload):
            if relative == 'restoration.json':
                raise SystemExit('secondary journal interruption')
            return actual_write(root, relative, payload)
        with patch.object(terminal_file_io, 'write_new_durable', side_effect=writer):
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.restore(f)
        self.assertIs(caught.exception, primary)
        self.assertIn('secondary journal interruption', ' '.join(getattr(primary, '__notes__', [])))

    def test_recovery_interrupt_preserves_object_and_does_not_continue_deleting(self):
        f = self.fixture()
        actual = f.context['call']
        failure = KeyboardInterrupt('interrupt after one exact owned file')
        def call(*args, **kwargs):
            result = actual(*args, **kwargs)
            if args[:3] == ('shell', 'rm', '-f'):
                raise failure
            return result
        f.context['call'] = call
        with self.assertRaises(KeyboardInterrupt) as caught:
            self.restore(f)
        self.assertIs(caught.exception, failure)
        removals = [row for row in f.mutations()[f.restore_start:] if row['label'].startswith('remove-')]
        self.assertEqual(len(removals), 1)
        self.assertTrue((f.device / 'stage' / RUN / '.odai-owner').is_file())
        self.assert_ordinary(f)
        receipt = json.loads((f.out / 'restoration.json').read_text())
        self.assertIs(receipt['allRestored'], False)
        self.assertTrue(any('KeyboardInterrupt' in error and str(failure) in error
                            for error in receipt['errors']), receipt)

    def test_outcome_persistence_failure_cannot_return_restored_success(self):
        f = self.fixture()
        real = terminal_file_io.write_new_durable
        failure = OSError('restoration outcome fsync failed')
        def writer(root, relative, payload):
            if relative == 'restoration.json':
                raise failure
            return real(root, relative, payload)
        with patch.object(terminal_file_io, 'write_new_durable', side_effect=writer):
            with self.assertRaises(OSError) as caught:
                self.restore(f)
        self.assertIs(caught.exception, failure)
        self.assertFalse((f.out / 'restoration.json').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
