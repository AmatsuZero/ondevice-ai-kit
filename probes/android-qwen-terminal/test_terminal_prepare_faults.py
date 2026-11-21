"""Retain claims and the original failure at every partial prepare boundary."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
from terminal_prepare_fixture import PrepareFixture, ROOT, RUN
import terminal_file_io

TARGET = Path(os.environ.get('ODAI_PREPARE_SOURCE', str(ROOT / 'probes/android-qwen-terminal/terminal_device_prepare.py')))


class PrepareFaults(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing prepare implementation')
        spec = importlib.util.spec_from_file_location('prepare_faults_target', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_partial_device_effects_always_retain_claim_and_call_cleanup_once(self):
        for fault in ['after-stage-mkdir', 'after-owner', 'after-concat',
                      'after-test-install', 'after-private-mkdir', 'after-model-copy']:
            with self.subTest(fault=fault):
                fixture = PrepareFixture(self)
                fixture.fault(fault)
                with self.assertRaises(RuntimeError) as caught:
                    fixture.call(self.module)
                self.assertIn('19', str(caught.exception))
                self.assertEqual(fixture.cleanup_calls, 1)
                self.assertFalse((fixture.out / 'prepared.json').exists())
                claim = json.loads((fixture.out / 'ownership-stage-claimed.json').read_text())
                children = {p.name for p in (fixture.device / 'stage' / RUN).iterdir()}
                self.assertLessEqual(children, set(claim['children']))
                self.assertEqual(fixture.context['ordinary'](), fixture.before_ordinary)
                self.assertEqual(fixture.context['ordinary_models'](), fixture.before_models)

    def test_created_receipt_failure_after_owner_retains_primary_and_triggers_cleanup(self):
        fixture = PrepareFixture(self)
        real = terminal_file_io.write_new_durable
        failure = OSError('created receipt fsync failed')
        def writer(root, relative, payload):
            if relative == 'ownership-stage-created.json':
                raise failure
            return real(root, relative, payload)
        with patch.object(terminal_file_io, 'write_new_durable', side_effect=writer):
            with self.assertRaises(OSError) as caught:
                fixture.call(self.module)
        self.assertIs(caught.exception, failure)
        self.assertEqual(fixture.cleanup_calls, 1)
        self.assertTrue((fixture.device / 'stage' / RUN / '.odai-owner').is_file())
        self.assertFalse(any(row['label'] == 'push' for row in fixture.mutations()))

    def test_primary_interrupt_preserves_object_and_runs_cleanup_once(self):
        for failure in [KeyboardInterrupt('primary interrupt'), SystemExit('primary exit')]:
            with self.subTest(kind=type(failure).__name__):
                fixture = PrepareFixture(self)
                real = fixture.context['call']
                def call(*args, **kwargs):
                    if args[:2] == ('shell', 'mkdir'):
                        real(*args, **kwargs)
                        raise failure
                    return real(*args, **kwargs)
                fixture.context['call'] = call
                with self.assertRaises(type(failure)) as caught:
                    fixture.call(self.module)
                self.assertIs(caught.exception, failure)
                self.assertEqual(fixture.cleanup_calls, 1)
                self.assertTrue((fixture.device / 'stage' / RUN).is_dir())

    def test_frozen_guard_failure_after_marker_blocks_next_adb_and_preserves_error(self):
        fixture = PrepareFixture(self)
        failure = ValueError('frozen bytes changed after marker')
        def guard():
            if any(row['label'] == 'owner' for row in fixture.mutations()):
                raise failure
        fixture.context['call'].__globals__['_terminal_before_transport'] = guard
        with self.assertRaises(ValueError) as caught:
            fixture.call(self.module)
        self.assertIs(caught.exception, failure)
        self.assertEqual(fixture.cleanup_calls, 1)
        self.assertEqual([row['label'] for row in fixture.mutations()], ['mkdir', 'owner'])
        self.assertFalse((fixture.out / 'ownership-stage-created.json').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
