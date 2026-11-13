"""Final installed/ordinary APK bytes must still match after model transfer."""
import importlib.util
import os
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from terminal_prepare_fixture import PrepareFixture, ROOT, APP

TARGET = Path(os.environ.get('ODAI_PREPARE_SOURCE', str(ROOT / 'probes/android-qwen-terminal/terminal_device_prepare.py')))


class PrepareFinalHashes(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing prepare implementation')
        spec = importlib.util.spec_from_file_location('prepare_final_hashes_target', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.fixture = PrepareFixture(self)

    def change_after_copy(self, target):
        original = self.fixture.adb.read_text()
        self.fixture.adb.write_text(original.replace(
            "mutate('copy');fault('after-model-copy')",
            "mutate('copy');(" + target + ").write_bytes(b'changed installed bytes');fault('after-model-copy')"))
        self.assertNotEqual(self.fixture.adb.read_text(), original)

    def assert_refused(self):
        with self.assertRaises((RuntimeError, AssertionError, ValueError)):
            self.fixture.call(self.module)
        self.assertEqual(self.fixture.cleanup_calls, 1)
        self.assertFalse((self.fixture.out / 'prepared.json').exists())

    def test_ordinary_apk_bytes_drift_with_same_pm_path_blocks_prepared(self):
        self.change_after_copy("device/'ordinary.apk'")
        self.assert_refused()
        self.assertEqual((self.fixture.device / 'ordinary.apk').read_bytes(), b'changed installed bytes')

    def test_new_alias_apk_drift_after_initial_install_verification_blocks_prepared(self):
        self.change_after_copy("device/'apps'/APP/'base.apk'")
        self.assert_refused()
        self.assertEqual((self.fixture.device / 'apps' / APP / 'base.apk').read_bytes(), b'changed installed bytes')


if __name__ == '__main__':
    unittest.main(verbosity=2)
