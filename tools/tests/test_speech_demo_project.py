import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = "scripts/generate-speech-demo.rb"
SOURCES = [f"demo/iosApp/{name}.swift" for name in ["OnDeviceAIDemoApp", "DemoController", "IOSFaceBridge",
    "CameraSource", "CameraLifecycle", "PhotoSubmission", "SpeechHost", "SpeechFilePicker", "SpeechInputStore"]] + [
    "kmp-binding/apple/IOSSpeechBridge.swift", "kmp-binding/apple/IOSSpeechAudioInput.swift"]


class SpeechDemoProjectTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue((ROOT / SCRIPT).is_file(), "speech Demo generator is missing")
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name).resolve()
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / SCRIPT, self.root / SCRIPT)
        (self.root / "apple").mkdir()
        for source in SOURCES + ["demo/iosApp/Info.plist"]:
            path = self.root / source
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("fixture")
        self.frameworks = [self.root / f"demo/shared/build/bin/{platform}/debugFramework/ODAIKitDemo.framework"
                           for platform in ["iosSimulatorArm64", "iosArm64"]]
        for framework in self.frameworks:
            framework.mkdir(parents=True)
        self.original = self.root / "demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj"
        self.original.parent.mkdir()
        self.original.write_bytes(b"protected project")
        self.output = self.root / "artifacts/speech-demo"
        self.ruby = os.environ.get("RUBY", "ruby")

    def generate(self):
        return subprocess.run([self.ruby, str(self.root / SCRIPT)], capture_output=True, text=True,
            env={**os.environ, "ODAI_SPEECH_DEMO_DIR": str(self.output), "ODAI_DEVELOPMENT_TEAM": "LOCALTEAM"})

    def testFreshProjectSelectsCorrectAbiAndDoesNotRewriteOriginal(self):
        result = self.generate()
        self.assertEqual(result.returncode, 0, result.stderr)
        project = self.output / "SpeechDemo.xcodeproj"
        inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.find { |target| target.name == 'SpeechDemo' }
puts JSON.generate({sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
 products: t.package_product_dependencies.map(&:product_name),
 frameworks: t.frameworks_build_phase.files.filter_map { |f| f.file_ref&.path },
 settings: t.build_configurations.map(&:build_settings), scripts: t.shell_script_build_phases.map(&:shell_script)})
""", str(project)], capture_output=True, text=True, check=True)
        data = json.loads(inspected.stdout)
        self.assertEqual(sorted(data["sources"]), sorted(str(self.root / s) for s in SOURCES))
        self.assertEqual(sorted(data["products"]), ["ODAIKitAppleSpeech", "ODAIKitAppleVision", "ODAIKitCore", "ODAIKitFace", "ODAIKitSpeech"])
        self.assertEqual(len([p for p in data["frameworks"] if "ODAIKitDemo" in p]), 1)
        self.assertFalse(data["scripts"], "generation/build must not perform account or signing operations")
        for settings in data["settings"]:
            self.assertEqual(settings["ODAI_KONAN_TARGET[sdk=iphonesimulator*]"], "iosSimulatorArm64")
            self.assertEqual(settings["ODAI_KONAN_TARGET[sdk=iphoneos*]"], "iosArm64")
            self.assertIn("ODAI_SPEECH_DEMO", settings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"])
            self.assertIn("ODAI_DEMO_BINDING", settings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"])
            self.assertEqual(settings["PRODUCT_BUNDLE_IDENTIFIER"], "io.ondeviceai.ondeviceaidemo")
            self.assertEqual(settings["DEVELOPMENT_TEAM"], "LOCALTEAM")
            self.assertIn("INFOPLIST_KEY_NSSpeechRecognitionUsageDescription", settings)
            self.assertNotIn("INFOPLIST_KEY_NSMicrophoneUsageDescription", settings)
        self.assertEqual(self.original.read_bytes(), b"protected project")

    def testMissingEitherAbiOrSourceHasNoOutputSideEffect(self):
        for path in [*self.frameworks, *[self.root / s for s in SOURCES]]:
            with self.subTest(path=path):
                held = path.with_name(path.name + ".held")
                path.rename(held)
                try:
                    result = self.generate()
                    self.assertNotEqual(result.returncode, 0)
                    self.assertFalse(self.output.exists())
                    self.assertEqual(self.original.read_bytes(), b"protected project")
                finally:
                    held.rename(path)

    def testExistingOutputIsNeverOverwritten(self):
        self.output.mkdir(parents=True)
        sentinel = self.output / "keep"
        sentinel.write_bytes(b"keep")
        result = self.generate()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list(self.output.iterdir()), [sentinel])
        self.assertEqual(sentinel.read_bytes(), b"keep")


if __name__ == "__main__":
    unittest.main()
