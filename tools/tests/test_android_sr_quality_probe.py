import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ControlledSrSourceTests(unittest.TestCase):
    def test_generated_media_changes_only_codec_observation(self):
        path = ROOT / 'probes/android-sr-quality/prepare.py'
        self.assertTrue(path.is_file(), 'controlled SR source preparation is missing')
        spec = importlib.util.spec_from_file_location('sr_prepare', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'generated'
            module.prepare(ROOT, output)
            source = ROOT / 'android/backend-litert-sr/src/main/kotlin/io/ondeviceai/litertsr'
            original = (source / 'AndroidSrMedia.kt').read_text()
            generated = (output / 'kotlin/AndroidSrMedia.kt').read_text()
            self.assertEqual(generated.replace('.also { SrCodecTrace.record(encoder, it.name) }', ''), original)
            for name in ['VideoColor.kt', 'EsrganInterpreter.kt', 'EsrganTiles.kt', 'LiteRtSrModel.kt']:
                self.assertEqual((output / 'kotlin' / name).read_bytes(), (source / name).read_bytes())
            inventory = json.loads((output / 'assets/sr-source-inventory.json').read_text())
            self.assertEqual(len(inventory['sources']), 5)
            self.assertEqual(inventory['scope'], 'test-only codec observation; production sources unchanged')

    def test_preparation_rejects_missing_insertion_before_output(self):
        path = ROOT / 'probes/android-sr-quality/prepare.py'
        self.assertTrue(path.is_file(), 'controlled SR source preparation is missing')
        spec = importlib.util.spec_from_file_location('sr_prepare', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = root / 'android/backend-litert-sr/src/main/kotlin/io/ondeviceai/litertsr'
            sources.mkdir(parents=True)
            for name in ['AndroidSrMedia.kt', 'VideoColor.kt', 'EsrganInterpreter.kt', 'EsrganTiles.kt', 'LiteRtSrModel.kt']:
                (sources / name).write_text('changed source without expected observation point')
            output = root / 'generated'
            with self.assertRaisesRegex(ValueError, 'codec observation'):
                module.prepare(root, output)
            self.assertFalse(output.exists())
