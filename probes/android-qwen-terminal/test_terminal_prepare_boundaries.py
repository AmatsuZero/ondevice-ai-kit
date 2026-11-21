"""Reject malformed bindings before mutation; execute the real inherited transport."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from terminal_prepare_fixture import PrepareFixture, ROOT, APP, TEST, RUN, MODELS

TARGET = Path(os.environ.get('ODAI_PREPARE_SOURCE', str(ROOT / 'probes/android-qwen-terminal/terminal_device_prepare.py')))


class PrepareBoundaries(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing prepare implementation')
        spec = importlib.util.spec_from_file_location('prepare_boundaries_target', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def rejected(self, fixture, **kwargs):
        with self.assertRaises((TypeError, ValueError, AssertionError)):
            fixture.call(self.module, **kwargs)
        self.assertEqual(fixture.mutations(), [], 'malformed input reached device mutation')
        self.assertFalse((fixture.out / 'device-before.json').exists())

    def test_nonce_subclass_or_trailing_newline_never_creates_stage(self):
        class Nonce(str):
            pass
        for nonce in ['1' * 32 + '\n', Nonce('1' * 32)]:
            with self.subTest(nonce=repr(nonce), kind=type(nonce).__name__):
                fixture = PrepareFixture(self)
                self.rejected(fixture, nonce_factory=lambda: nonce)

    def test_plan_exact_types_and_fingerprint_are_checked_before_snapshot(self):
        for field, value in [('caseCount', True), ('caseCount', 1.0),
                             ('maxNativeDecodeCalls', 2.0), ('sourceFingerprint', '0' * 64)]:
            with self.subTest(field=field, value=value):
                fixture = PrepareFixture(self)
                fixture.context['plan'][field] = value
                self.rejected(fixture)

    def test_serial_must_be_exact_nonblank_string(self):
        for serial in [True, '', 'serial-01\n']:
            with self.subTest(serial=serial):
                fixture = PrepareFixture(self)
                fixture.context['serial'] = serial
                self.rejected(fixture, serial=serial)

    def test_metadata_rejects_boolean_or_float_size_even_if_file_matches(self):
        for value in [True, 1.0]:
            with self.subTest(value=value):
                fixture = PrepareFixture(self)
                name = MODELS[0]
                path = fixture.models / name
                path.write_bytes(b'x')
                row = {'bytes': value, 'sha256': fixture.sha(path)}
                fixture.context['models'][name] = row
                fixture.context['manifest']['files'][name] = row
                self.rejected(fixture)

    def test_cleanup_callback_must_be_callable_before_stage_mutation(self):
        fixture = PrepareFixture(self)
        self.rejected(fixture, cleanup=None)

    def test_model_directory_symlink_is_not_used_as_verified_local_input(self):
        fixture = PrepareFixture(self)
        alias = fixture.root / 'model-alias'
        alias.symlink_to(fixture.models, target_is_directory=True)
        fixture.context['manifest']['directory'] = str(alias)
        self.rejected(fixture)

    def test_model_directory_ancestor_symlink_is_not_resolved_away(self):
        fixture = PrepareFixture(self)
        alias = fixture.root / 'ancestor-alias'
        alias.symlink_to(fixture.root, target_is_directory=True)
        fixture.context['manifest']['directory'] = str(alias / 'models')
        self.rejected(fixture)

    def test_marker_suffix_is_not_accepted_as_exact_ownership_token(self):
        fixture = PrepareFixture(self)
        original = fixture.adb.read_text()
        fixture.adb.write_text(original.replace(
            "mapped(target).write_bytes(value.encode())",
            "mapped(target).write_bytes(value.encode()+b'unexpected suffix')"))
        self.assertNotEqual(fixture.adb.read_text(), original)
        with self.assertRaises((ValueError, AssertionError, RuntimeError)):
            fixture.call(self.module)
        self.assertFalse((fixture.out / 'ownership-stage-created.json').exists())
        self.assertFalse(any(row['label'] == 'push' for row in fixture.mutations()))

    def test_marker_transport_requires_exact_bytes_before_created_receipt(self):
        class Bytes(bytes):
            pass
        for convert in [bytearray, Bytes]:
            with self.subTest(convert=convert.__name__):
                fixture = PrepareFixture(self)
                original = fixture.context['call']
                def call(*args, **kwargs):
                    result = original(*args, **kwargs)
                    if args[:2] == ('exec-out', 'head'):
                        result.stdout = convert(result.stdout)
                    return result
                fixture.context['call'] = call
                with self.assertRaises((TypeError, RuntimeError, ValueError)):
                    fixture.call(self.module)
                self.assertEqual(fixture.cleanup_calls, 1)
                self.assertFalse((fixture.out / 'ownership-stage-created.json').exists())
                self.assertFalse(any(row['label'] == 'push' for row in fixture.mutations()))

    def test_apk_transfer_receipts_preexist_without_any_device_effect(self):
        for name in ['transfer-main.apk.json', 'transfer-test.apk.json']:
            with self.subTest(name=name):
                fixture = PrepareFixture(self)
                stale = fixture.out / name
                stale.write_bytes(b'previous transfer evidence')
                with self.assertRaises((TypeError, ValueError, AssertionError, OSError)):
                    fixture.call(self.module)
                self.assertEqual(stale.read_bytes(), b'previous transfer evidence')
                self.assertEqual(fixture.mutations(), [])
                self.assertEqual(fixture.cleanup_calls, 0)

    def test_runner_requires_exact_component_and_target(self):
        fixture = PrepareFixture(self)
        original = fixture.adb.read_text()
        wrong = TEST + '/wrong.QwenTerminalInstrumentation (target=' + APP + ')'
        fixture.adb.write_text(original.replace(
            "print('instrumentation:'+TEST+'/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation (target='+APP+')')",
            'print(' + repr('instrumentation:' + wrong) + ')'))
        self.assertNotEqual(fixture.adb.read_text(), original)
        with self.assertRaises((ValueError, AssertionError, RuntimeError)):
            fixture.call(self.module)
        self.assertFalse((fixture.out / 'prepared.json').exists())
        self.assertEqual(fixture.cleanup_calls, 1)

    def test_stage_stat_requires_exact_three_decimal_fields(self):
        for output in ['501 2 3 4', '+501 2 3', '501 2 -3', '0501 2 3', '٥٠١ 2 3']:
            with self.subTest(output=output):
                fixture = PrepareFixture(self)
                original = fixture.adb.read_text()
                fixture.adb.write_text(original.replace(
                    "print(str(s.st_uid)+' '+str(s.st_dev)+' '+str(s.st_ino))",
                    'print(' + repr(output) + ')'))
                self.assertNotEqual(fixture.adb.read_text(), original)
                with self.assertRaises((ValueError, AssertionError, RuntimeError)):
                    fixture.call(self.module)
                self.assertFalse((fixture.out / 'ownership-stage-created.json').exists())
                self.assertFalse((fixture.out / 'prepared.json').exists())
                self.assertFalse(any(row['label'] == 'push' for row in fixture.mutations()))


if __name__ == '__main__':
    unittest.main(verbosity=2)
