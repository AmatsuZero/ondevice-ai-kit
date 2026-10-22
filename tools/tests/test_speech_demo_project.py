import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = "scripts/generate-speech-demo.rb"
UI_SOURCE = "demo/iosApp/UITests/SpeechDemoUITests.swift"
SOURCES = [f"demo/iosApp/{name}.swift" for name in ["OnDeviceAIDemoApp", "DemoController", "IOSFaceBridge",
    "CameraSource", "CameraLifecycle", "PhotoSubmission", "SpeechHost", "SpeechFilePicker", "SpeechInputStore", "SpeechAudioSession"]] + [
    "kmp-binding/apple/IOSSpeechBridge.swift", "kmp-binding/apple/IOSSpeechAudioInput.swift"]
VIDEO_SOURCES = ["demo/iosApp/VideoHost.swift", "demo/iosApp/VideoInputStore.swift", "demo/iosApp/VideoFilePicker.swift",
                 "kmp-binding/apple/IOSVideoBridge.swift", "kmp-binding/apple/IOSVideoInput.swift"]

MLX_SOURCES = ["demo/iosApp/MultimodalHostPreparation.swift", "demo/iosApp/MultimodalModelHostFactory.swift", "demo/iosApp/MultimodalMLXHostFactory.swift"]

VALIDATION_SOURCES = ["probes/ios-multimodal-demo/AppAccessibility.swift", "probes/ios-multimodal-demo/MultimodalDemoValidation.swift", "probes/ios-multimodal-demo/MultimodalDemoActiveValidation.swift"]

MULTIMODAL_SOURCES = ["demo/iosApp/MultimodalHost.swift", "demo/iosApp/MultimodalInputStore.swift", "demo/iosApp/MultimodalFilePicker.swift", "kmp-binding/apple/IOSMultimodalBridge.swift", "kmp-binding/apple/IOSMultimodalInput.swift"]

class SpeechDemoProjectTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue((ROOT / SCRIPT).is_file(), "speech Demo generator is missing")
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name).resolve()
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / SCRIPT, self.root / SCRIPT)
        (self.root / "apple").mkdir()
        (self.root / "apple-mlx").mkdir()
        (self.root / "apple-mlx/Package.resolved").write_text("fixed-lock")
        for source in SOURCES + VIDEO_SOURCES + MULTIMODAL_SOURCES + MLX_SOURCES + VALIDATION_SOURCES + ["demo/iosApp/Info.plist", UI_SOURCE]:
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

    def generate(self, video=False, multimodal=False, mlx=False, validation=False, commit="a" * 40):
        return subprocess.run([self.ruby, str(self.root / SCRIPT)], capture_output=True, text=True,
            env={**os.environ, "ODAI_SPEECH_DEMO_DIR": str(self.output), "ODAI_DEVELOPMENT_TEAM": "LOCALTEAM", "ODAI_VIDEO_DEMO": "1" if video else "0", "ODAI_MULTIMODAL_DEMO": "1" if multimodal else "0", "ODAI_MULTIMODAL_MLX_DEMO": "1" if mlx else "0", "ODAI_MULTIMODAL_DEMO_VALIDATION": "1" if validation else "0", "ODAI_VALIDATION_SOURCE_COMMIT": commit})

    def testValidationRequiresEnhancedAndExactCommitBeforeOutput(self):
        for flags in [{"validation": True}, {"video": True, "multimodal": True, "validation": True}, {"video": True, "multimodal": True, "mlx": True, "validation": True, "commit": "dirty"}]:
            result = self.generate(**flags)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(self.output.exists())

    def testValidationSourcesResourceAndFlagAreExplicitOnly(self):
        result = self.generate(video=True, multimodal=True, mlx=True, validation=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.find { |target| target.name == 'SpeechDemo' }
puts JSON.generate({sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
 resources: t.resources_build_phase.files.map { |f| f.file_ref.real_path.to_s },
 settings: t.build_configurations.map(&:build_settings)})
""", str(self.output / "SpeechDemo.xcodeproj")], capture_output=True, text=True, check=True)
        data = json.loads(inspected.stdout)
        for source in VALIDATION_SOURCES:
            self.assertIn(str(self.root / source), data["sources"])
        resource = self.output / "validation-metadata.json"
        self.assertIn(str(resource), data["resources"])
        self.assertEqual(json.loads(resource.read_text()), {"sourceCommit": "a" * 40})
        for settings in data["settings"]:
            self.assertIn("ODAI_MULTIMODAL_DEMO_VALIDATION", settings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"])
        self.assertEqual(self.original.read_bytes(), b"protected project")

    def testMlxRequiresBothFlagsAndAddsIsolatedProductLockAndTarget(self):
        for flags in [{"mlx": True}, {"video": True, "mlx": True}]:
            result = self.generate(**flags)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(self.output.exists())
        result = self.generate(video=True, multimodal=True, mlx=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.find { |target| target.name == 'SpeechDemo' }
puts JSON.generate({sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
 packages: p.root_object.package_references.map(&:relative_path), products: t.package_product_dependencies.map(&:product_name),
 settings: t.build_configurations.map(&:build_settings)})
""", str(self.output / "SpeechDemo.xcodeproj")], capture_output=True, text=True, check=True)
        data = json.loads(inspected.stdout)
        self.assertEqual(sorted(data["sources"]), sorted(str(self.root / s) for s in SOURCES + VIDEO_SOURCES + MULTIMODAL_SOURCES + MLX_SOURCES))
        self.assertIn(str(self.root / "apple-mlx"), data["packages"])
        self.assertIn("ODAIKitAppleMLXVLM", data["products"])
        self.assertIn("ODAIKitModelStore", data["products"])
        for settings in data["settings"]:
            self.assertEqual(settings["IPHONEOS_DEPLOYMENT_TARGET"], "18.0")
            self.assertEqual(settings["PRODUCT_BUNDLE_IDENTIFIER"], "io.ondeviceai.multimodalmlxdemo")
            self.assertIn("ODAI_MULTIMODAL_MLX_DEMO", settings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"])
        lock = self.output / "SpeechDemo.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"
        self.assertEqual(lock.read_text(), "fixed-lock")
        self.assertEqual(self.original.read_bytes(), b"protected project")

    def testMlxMissingLockRejectsBeforeOutputMutation(self):
        (self.root / "apple-mlx/Package.resolved").unlink()
        result = self.generate(video=True, multimodal=True, mlx=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())

    def testMultimodalOptInRequiresVideoAndAddsOnlyBaseProducts(self):
        rejected = self.generate(multimodal=True)
        self.assertNotEqual(rejected.returncode, 0)
        self.assertFalse(self.output.exists())
        result = self.generate(video=True, multimodal=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.find { |target| target.name == 'SpeechDemo' }
puts JSON.generate({sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
 products: t.package_product_dependencies.map(&:product_name), settings: t.build_configurations.map(&:build_settings)})
""", str(self.output / "SpeechDemo.xcodeproj")], capture_output=True, text=True, check=True)
        data = json.loads(inspected.stdout)
        self.assertEqual(sorted(data["sources"]), sorted(str(self.root / s) for s in SOURCES + VIDEO_SOURCES + MULTIMODAL_SOURCES))
        self.assertIn("ODAIKitMultimodal", data["products"])
        self.assertNotIn("ODAIKitAppleMLXVLM", data["products"])
        self.assertTrue(all("ODAI_MULTIMODAL_DEMO" in c["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] for c in data["settings"]))
        self.assertEqual(self.original.read_bytes(), b"protected project")

    def testVideoOptInAddsOnlyHostVideoProductsAndPreservesOriginal(self):
        result = self.generate(video=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.find { |target| target.name == 'SpeechDemo' }
puts JSON.generate({sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
 products: t.package_product_dependencies.map(&:product_name), settings: t.build_configurations.map(&:build_settings)})
""", str(self.output / "SpeechDemo.xcodeproj")], capture_output=True, text=True, check=True)
        data = json.loads(inspected.stdout)
        self.assertEqual(sorted(data["sources"]), sorted(str(self.root / s) for s in SOURCES + VIDEO_SOURCES))
        self.assertIn("ODAIKitVideo", data["products"])
        self.assertIn("ODAIKitAppleVideo", data["products"])
        self.assertTrue(all("ODAI_VIDEO_DEMO" in c["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] for c in data["settings"]))
        self.assertEqual(self.original.read_bytes(), b"protected project")

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
            self.assertIn("INFOPLIST_KEY_NSMicrophoneUsageDescription", settings)
            for key in ["INFOPLIST_KEY_UIBackgroundModes", "INFOPLIST_KEY_UIFileSharingEnabled", "INFOPLIST_KEY_LSSupportsOpeningDocumentsInPlace"]:
                self.assertNotIn(key, settings)
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

    def testGeneratedUiTestsExerciseTheAppTarget(self):
        result = self.generate()
        self.assertEqual(result.returncode, 0, result.stderr)
        inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.find { |target| target.name == 'SpeechDemoUITests' }
puts JSON.generate(t.nil? ? nil : {sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
 dependencies: t.dependencies.map { |d| d.target.name }, settings: t.build_configurations.map(&:build_settings)})
""", str(self.output / "SpeechDemo.xcodeproj")], capture_output=True, text=True, check=True)
        data = json.loads(inspected.stdout)
        self.assertIsNotNone(data, "missing speech UI test target")
        self.assertEqual(data["sources"], [str(self.root / UI_SOURCE)])
        self.assertEqual(data["dependencies"], ["SpeechDemo"])
        for settings in data["settings"]:
            self.assertEqual(settings["TEST_TARGET_NAME"], "SpeechDemo")
            self.assertEqual(settings["DEVELOPMENT_TEAM"], "LOCALTEAM")


if __name__ == "__main__":
    unittest.main()
