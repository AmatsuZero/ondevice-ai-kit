"""Actual local fake-ADB acceptance for future outer launch callbacks."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
QT = ROOT / 'probes/android-qwen-terminal'
sys.path[:0] = [str(QT), str(ROOT / 'probes/android-terminal-observation')]
from terminal_prepare_fixture import PrepareFixture, APP, RUN
import terminal_device_prepare

TARGET = Path(os.environ.get('ODAI_TERMINAL_DRIVER', QT / 'device_acceptance.py'))


class TerminalLaunchParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing complete terminal device driver')
        spec = importlib.util.spec_from_file_location('launch_driver_under_test', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def fixture(self, prepared=True):
        f = PrepareFixture(self)
        source = f.adb.read_text().replace(
            "def mapped(s,app=None):",
            "def mapped(s,app=None):\n if s in ('/data','/data/local'):return device\n if s=='/data/local/tmp':return device/'stage'")
        f.adb.write_text(source)
        if prepared:
            f.call(terminal_device_prepare)
        return f

    def calls(self, f):
        return (f.root / 'calls.jsonl').read_text().splitlines()

    def test_preflight_is_read_only_and_checks_initial_available_memory(self):
        f = self.fixture(False)
        before = len(self.calls(f)); guards = f.guards
        result = self.module._preflight_context(f.out, f.context, serial='serial-01')
        self.assertEqual(result['availableBytes'], 3 * 1024**3)
        self.assertEqual(result['currentUser'], '0')
        self.assertEqual(f.mutations(), [])
        self.assertEqual(f.guards - guards, len(self.calls(f)) - before)
        self.assertFalse((f.out / 'device-before.json').exists())

    def test_preflight_bad_local_bindings_or_inputs_fail_before_transport(self):
        for mode in ('serial', 'apk', 'collection'):
            with self.subTest(mode=mode):
                f = self.fixture(False)
                if mode == 'serial': f.context['serial'] = 'foreign'
                elif mode == 'apk': (f.out/'main.apk').write_bytes(b'changed APK')
                else: (f.out/'collection.json').symlink_to(f.out/'foreign-absent')
                before = len(self.calls(f))
                with self.assertRaises((ValueError, AssertionError, OSError)):
                    self.module._preflight_context(f.out, f.context, serial='serial-01')
                self.assertEqual(len(self.calls(f)), before)

    def test_launch_refuses_stale_collection_outputs_before_transport(self):
        for name in ('collection.json', 'device-report.json', 'device-report-confirm.json', 'poll-sample-000003.json'):
            with self.subTest(name=name):
                f = self.fixture(); (f.out/name).symlink_to(f.out/'foreign-absent')
                before = len(self.calls(f))
                with self.assertRaises((ValueError, OSError)):
                    self.module._verify_launch(f.out, f.context, serial='serial-01')
                self.assertEqual(len(self.calls(f)), before)

    def test_real_prepared_launch_checks_are_read_only_and_guarded(self):
        f = self.fixture(); before = len(self.calls(f)); guards = f.guards
        mutations = list(f.mutations())
        self.assertIsNone(self.module._verify_launch(f.out, f.context, serial='serial-01'))
        self.assertEqual(f.mutations(), mutations)
        self.assertEqual(f.guards - guards, len(self.calls(f)) - before)
        self.assertFalse((f.out / 'run-started.json').exists())

    def test_bad_local_prepared_proof_refuses_before_additional_transport(self):
        for mode in ('run', 'ordinary-bool', 'proc', 'native-bool', 'models'):
            with self.subTest(mode=mode):
                f = self.fixture(); path = f.out / 'prepared.json'; row = json.loads(path.read_text())
                if mode == 'run': row['runId'] = 'foreign'
                elif mode == 'ordinary-bool': row['ordinaryUnchanged'] = 1
                elif mode == 'proc': row['sameUidProcReadable'] = False
                elif mode == 'native-bool': row['nativeCalls'] = False
                else: row['models'] = {}
                path.write_text(json.dumps(row)); before = len(self.calls(f)); mutations = list(f.mutations())
                with self.assertRaises((ValueError, AssertionError, OSError)):
                    self.module._verify_launch(f.out, f.context, serial='serial-01')
                self.assertEqual(len(self.calls(f)), before)
                self.assertEqual(f.mutations(), mutations)

    def test_stale_run_outputs_and_remote_ack_symlinks_block_launch(self):
        for mode in ('local-run', 'local-policy', 'remote-report', 'remote-ack', 'remote-stage'):
            with self.subTest(mode=mode):
                f = self.fixture(); before = len(self.calls(f)); mutations = list(f.mutations())
                if mode.startswith('local'):
                    name = 'run-started.json' if mode == 'local-run' else 'allocator-policy-collected.json'
                    (f.out / name).symlink_to(f.out / 'foreign-absent')
                else:
                    folder = f.device / 'apps' / APP / 'files/w3-qwen-terminal'; folder.mkdir(parents=True)
                    prefix = 'report-' if mode == 'remote-report' else 'ack-'
                    suffix = '.json' if mode == 'remote-report' else '.txt.stage' if mode == 'remote-stage' else '.txt'
                    (folder / (prefix + RUN + suffix)).symlink_to(f.root / 'foreign-absent')
                with self.assertRaises((ValueError, AssertionError, OSError, RuntimeError)):
                    self.module._verify_launch(f.out, f.context, serial='serial-01')
                if mode.startswith('local'): self.assertEqual(len(self.calls(f)), before)
                self.assertEqual(f.mutations(), mutations)

    def test_live_lock_user_memory_and_runner_drift_cannot_launch(self):
        for mode in ('locked', 'user', 'memory', 'runner'):
            with self.subTest(mode=mode):
                f = self.fixture(); mutations = list(f.mutations()); source = f.adb.read_text()
                if mode == 'locked': (f.root / 'locked').touch()
                elif mode == 'user': source = source.replace("print('0');sys.exit(0)", "print('10');sys.exit(0)")
                elif mode == 'memory': source = source.replace('MemAvailable: 3145728 kB', 'MemAvailable: 2 kB')
                else: source = source.replace('QwenTerminalInstrumentation', 'ForeignInstrumentation')
                f.adb.write_text(source)
                with self.assertRaises((ValueError, AssertionError, OSError, RuntimeError)):
                    self.module._verify_launch(f.out, f.context, serial='serial-01')
                self.assertEqual(f.mutations(), mutations)

    def test_stop_accepts_active_owned_alias_and_refuses_replaced_apk(self):
        for replaced in (False, True):
            with self.subTest(replaced=replaced):
                f = self.fixture(); source = f.adb.read_text().replace('No active instrumentation', 'ActiveInstrumentation expected terminal run')
                f.adb.write_text(source); mutations = list(f.mutations())
                if replaced:
                    (f.device / 'apps' / APP / 'base.apk').write_bytes(b'foreign installed APK')
                    with self.assertRaises((ValueError, AssertionError, OSError, RuntimeError)):
                        self.module._stop_owned_alias(f.out, f.context, serial='serial-01')
                    self.assertEqual(f.mutations(), mutations)
                else:
                    self.module._stop_owned_alias(f.out, f.context, serial='serial-01')
                    added = f.mutations()[len(mutations):]
                    self.assertEqual([r['label'] for r in added], ['force-stop'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
