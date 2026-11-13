"""Acceptance faults for raw policy persistence; no device or decoder."""
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / 'probes/android-qwen-terminal'),
                str(ROOT / 'probes/android-terminal-observation')]
TARGET = Path(os.environ.get('ODAI_POLICY_SOURCE', ROOT / 'probes/android-qwen-terminal/terminal_device_policy.py'))


class PolicyReadOrderParent(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('policy_order_candidate', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_first_raw_read_is_durable_before_second_transport(self):
        m = self.module
        primary = KeyboardInterrupt('second policy read interrupted')
        with tempfile.TemporaryDirectory() as root:
            root = Path(root).resolve()
            count = 0
            def call(*args, **kwargs):
                nonlocal count
                count += 1
                if count == 2:
                    raise primary
                return SimpleNamespace(stdout=m.POLICY)
            def text(*args, **kwargs):
                import shlex
                tokens = shlex.split(args[1])
                if tokens[-2:] == ['id', '-u']:
                    return '501'
                if 'stat' in tokens:
                    path = tokens[-1]
                    return ('501 1 103 20' if path.endswith('cpu.config')
                            else '501 1 102 64' if path.endswith('cpu-arena-off')
                            else '501 1 101 64')
                return ''
            with self.assertRaises(KeyboardInterrupt) as caught:
                m._collect_policy_file(root, {'call': call, 'text': text},
                    {'policy': {'configPath': m.FRAMEWORK + '/cpu-arena-off/cpu.config'}})
            self.assertIs(caught.exception, primary)
            self.assertEqual((Path(root) / 'allocator-policy-first.bin').read_bytes(), m.POLICY)
            self.assertFalse((Path(root) / 'allocator-policy-confirm.bin').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
