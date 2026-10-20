import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = "scripts/generate-speech-bridge-tests.rb"
SOURCES = ["kmp-binding/apple/IOSSpeechBridge.swift",
           "kmp-binding/apple/IOSSpeechAudioInput.swift",
           "demo/iosApp/SpeechInputStore.swift",
           "probes/ios-speech-bridge/IOSSpeechBridgeTests.swift"]


class SpeechBridgeProjectTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue((ROOT / SCRIPT).is_file(), "speech bridge generator is missing")
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / SCRIPT, self.root / SCRIPT)
        (self.root / "apple").mkdir()
        for source in SOURCES:
            path = self.root / source
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("// fixture\n")
        self.frameworks = {}
        for module, base in [("ODAIKitBinding", "kmp-binding"), ("ODAIKitDemo", "demo/shared")]:
            framework = self.root / base / f"build/bin/iosSimulatorArm64/debugFramework/{module}.framework"
            framework.mkdir(parents=True)
            self.frameworks[module] = framework
        self.output = self.root / "artifacts/tests"
        self.ruby = os.environ.get("RUBY", "ruby")

    def generate(self, module="ODAIKitBinding"):
        return subprocess.run([self.ruby, str(self.root / SCRIPT)], capture_output=True, text=True,
                              env={**os.environ, "ODAI_SPEECH_BRIDGE_TEST_DIR": str(self.output),
                                   "ODAI_SPEECH_BRIDGE_KMP_MODULE": module})

    def testBothAbisLinkOneKotlinFrameworkAndOnlyNativeSpeechProducts(self):
        for module in self.frameworks:
            with self.subTest(module=module):
                self.output = self.root / f"artifacts/{module}"
                generated = self.generate(module)
                self.assertEqual(generated.returncode, 0, generated.stderr)
                project = self.output / "SpeechBridgeTests.xcodeproj"
                inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.fetch(0)
puts JSON.generate({targets: p.targets.map(&:name),
  sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
  frameworks: t.frameworks_build_phase.files.filter_map { |f| f.file_ref&.real_path&.to_s },
  products: t.package_product_dependencies.map(&:product_name),
  settings: t.build_configurations.map(&:build_settings)})
""", str(project)], capture_output=True, text=True, check=True)
                data = json.loads(inspected.stdout)
                self.assertEqual(data["targets"], ["SpeechBridgeTests"])
                self.assertEqual(sorted(data["sources"]), sorted(str(self.root / p) for p in SOURCES))
                kotlin = [p for p in data["frameworks"] if "ODAIKit" in p]
                self.assertEqual(kotlin, [str(self.frameworks[module])])
                self.assertEqual(sorted(data["products"]), ["ODAIKitCore", "ODAIKitSpeech"])
                for config in data["settings"]:
                    self.assertEqual("ODAI_DEMO_BINDING" in config["SWIFT_ACTIVE_COMPILATION_CONDITIONS"],
                                     module == "ODAIKitDemo")
                    self.assertEqual(config["SUPPORTED_PLATFORMS"], "iphonesimulator")
                self.assertTrue((project / "xcshareddata/xcschemes/SpeechBridgeTests.xcscheme").is_file())

    def testEveryMissingInputRejectsBeforeCreatingOutput(self):
        for path in [self.frameworks["ODAIKitBinding"], self.root / "apple", *[self.root / s for s in SOURCES]]:
            with self.subTest(path=path):
                moved = path.with_name(path.name + ".held")
                path.rename(moved)
                try:
                    result = self.generate()
                    self.assertNotEqual(result.returncode, 0)
                    self.assertFalse(self.output.exists())
                    self.assertIn("Missing input", result.stderr)
                finally:
                    moved.rename(path)

    def testExistingOutputIsNeverOverwritten(self):
        self.output.mkdir(parents=True)
        sentinel = self.output / "keep"
        sentinel.write_bytes(b"user data")
        result = self.generate()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list(self.output.iterdir()), [sentinel])
        self.assertEqual(sentinel.read_bytes(), b"user data")

    def testDanglingOutputSymlinkIsNotFollowed(self):
        self.output.parent.mkdir(parents=True)
        missing = self.root / "missing"
        self.output.symlink_to(missing, target_is_directory=True)
        result = self.generate()
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(self.output.is_symlink())
        self.assertFalse(missing.exists())

    def testUnknownModuleRejectsWithoutOutput(self):
        result = self.generate("OtherModule")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
