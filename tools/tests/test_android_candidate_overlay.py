"""The generated test runners preserve the frozen native checks and source files."""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GENERATOR = ROOT / 'probes/android-candidate-report/generate.py'
SOURCES = {
    'asr-lifecycle': ROOT / 'demo/androidApp/src/sherpaasrAndroidTest/kotlin/io/ondeviceai/demo/SherpaAsrLifecycleInstrumentation.kt',
    'sr': ROOT / 'demo/androidApp/src/litertsrAndroidTest/kotlin/io/ondeviceai/demo/VideoSrInstrumentation.kt',
}


class CandidateOverlayTests(unittest.TestCase):
    def load_generator(self):
        self.assertTrue(GENERATOR.is_file(), 'candidate report generator is not implemented')
        spec = importlib.util.spec_from_file_location('candidate_overlay_generator', GENERATOR)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_generated_runners_preserve_all_native_checks(self):
        generate = self.load_generator().generate
        for kind, source in SOURCES.items():
            with self.subTest(kind=kind):
                base = source.read_text()
                output = generate(base, kind=kind)
                name = ('PersistentSherpaAsrLifecycleInstrumentation' if kind == 'asr-lifecycle'
                        else 'PersistentVideoSrInstrumentation')
                self.assertIn(f'class {name} : Instrumentation()', output)
                marker = '    override fun onStart() {'
                actual = output.split(marker, 1)[1]
                actual = actual.replace('\n        if (recorderCheck()) return', '', 1)
                if kind == 'sr':
                    actual = actual.replace('\n        startActivitySync(android.content.Intent(targetContext, MainActivity::class.java)\n'
                                            '            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))', '', 1)
                    self.assertNotIn('stackTraceToString()', actual)
                    actual = actual.replace('"errorClass", error.javaClass.name',
                                            '"error", error.stackTraceToString()')
                self.assertEqual(actual, base.split(marker, 1)[1])
                self.assertEqual(source.read_text(), base)

    def test_changed_native_source_is_rejected(self):
        generate = self.load_generator().generate
        for kind, source in SOURCES.items():
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                generate(source.read_text() + '\n// changed\n', kind=kind)

    def test_unknown_kind_is_rejected(self):
        with self.assertRaises(ValueError):
            self.load_generator().generate(SOURCES['sr'].read_text(), kind='identity')

    def test_cli_never_overwrites_an_existing_output(self):
        self.load_generator()
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'Runner.kt'
            args = ['rtk', 'proxy', 'python3', str(GENERATOR), str(SOURCES['sr']),
                    str(output), '--kind', 'sr']
            first = subprocess.run(args, capture_output=True, text=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            generated = output.read_bytes()
            second = subprocess.run(args, capture_output=True, text=True)
            self.assertNotEqual(second.returncode, 0)
            self.assertEqual(output.read_bytes(), generated)


if __name__ == '__main__':
    unittest.main()
