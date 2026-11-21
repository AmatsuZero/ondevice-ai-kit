"""Outer-driver safety acceptance. No device operations are allowed here."""
import hashlib
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'probes/android-qwen-terminal/device_acceptance.py'
FILE_DRIVER = ROOT / 'probes/android-qwen-file/device_acceptance.py'
HEAP_DRIVER = ROOT / 'probes/android-qwen-heap/device_acceptance.py'


def load_driver():
    spec = importlib.util.spec_from_file_location('terminal_device_acceptance_under_test', TARGET)
    module = importlib.util.module_from_spec(spec)
    original_path = sys.path[:]
    sys.path[:0] = [str(TARGET.parent), str(ROOT / 'probes/android-terminal-observation')]
    try:
        with patch('subprocess.run', side_effect=AssertionError('subprocess during import')), \
             patch('subprocess.Popen', side_effect=AssertionError('Popen during import')):
            spec.loader.exec_module(module)
    finally:
        sys.path[:] = original_path
    return module


class TerminalDeviceRoutingParent(unittest.TestCase):
    def test_new_driver_identity_cannot_address_consumed_runs(self):
        driver = load_driver()
        self.assertEqual(driver.APP, 'io.ondeviceai.acceptance.qwenterminal')
        self.assertEqual(driver.RUN, 'w3-qwen-terminal-20261114-01')
        self.assertEqual(driver.RUNNER,
                         'io.ondeviceai.acceptance.qwenterminal.test/'
                         'io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation')
        self.assertEqual(driver.OUT, ROOT / 'artifacts/verification/w3-qwen-terminal-20261114')
        self.assertEqual(driver.BASE, FILE_DRIVER)

    def test_source_adapter_is_pure_compilable_and_preserves_old_drivers(self):
        before = {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in (FILE_DRIVER, HEAP_DRIVER)}
        driver = load_driver()
        with patch('subprocess.run', side_effect=AssertionError('ADB during source adaptation')), \
             patch('subprocess.Popen', side_effect=AssertionError('launch during source adaptation')):
            source = driver.driver_source()
        self.assertIs(type(source), str)
        self.assertTrue(source.strip())
        compile(source, '<actual terminal device source adapter>', 'exec')
        self.assertEqual(before, {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in before})

    def test_frozen_contract_failure_precedes_every_valid_device_action(self):
        driver = load_driver()
        for action in ('preflight', 'prepare', 'run', 'collect', 'collect-policy', 'restore'):
            with self.subTest(action=action):
                failure = ValueError('frozen artifact changed: sentinel')
                with patch.object(driver, 'verify_frozen_contract', side_effect=failure) as gate, \
                     patch.object(driver, 'adb', side_effect=AssertionError('ADB before freeze')), \
                     patch('subprocess.run', side_effect=AssertionError('subprocess before freeze')), \
                     patch('subprocess.Popen', side_effect=AssertionError('Popen before freeze')):
                    with self.assertRaises(ValueError) as caught:
                        driver.main(action)
                self.assertIs(caught.exception, failure)
                gate.assert_called_once_with()

    def test_invalid_action_cannot_touch_contract_or_transport(self):
        driver = load_driver()
        for action in (None, '', 'rerun', 'delete-all', True):
            with self.subTest(action=action):
                with patch.object(driver, 'verify_frozen_contract', side_effect=AssertionError('invalid action reached freeze')), \
                     patch.object(driver, 'adb', side_effect=AssertionError('invalid action reached ADB')), \
                     patch('subprocess.run', side_effect=AssertionError('invalid action reached subprocess')), \
                     patch('subprocess.Popen', side_effect=AssertionError('invalid action launched instrumentation')):
                    with self.assertRaises((ValueError, AssertionError)) as caught:
                        driver.main(action)
                # AssertionError is compatible with existing parent routing, but
                # must originate in validation, never from our side-effect guards.
                self.assertNotIn('reached', str(caught.exception))
                self.assertNotIn('launched instrumentation', str(caught.exception))


if __name__ == '__main__':
    unittest.main(verbosity=2)
