import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = "scripts/generate-multimodal-bridge-tests.rb"
SOURCES = ["kmp-binding/apple/IOSMultimodalBridge.swift", "kmp-binding/apple/IOSMultimodalInput.swift",
           "probes/ios-multimodal-bridge/IOSMultimodalBridgeTests.swift",
           "probes/ios-multimodal-bridge/IOSMultimodalVideoBridgeTests.swift"]
INPUT_SOURCES = ["demo/iosApp/MultimodalInputStore.swift", "demo/iosApp/SpeechInputStore.swift",
                 "kmp-binding/apple/IOSSpeechAudioInput.swift", "probes/ios-multimodal-bridge/MultimodalInputStoreTests.swift",
                 "demo/iosApp/MultimodalHost.swift", "probes/ios-multimodal-bridge/MultimodalHostTests.swift",
                 "demo/iosApp/MultimodalFilePicker.swift", "probes/ios-multimodal-bridge/MultimodalFilePickerTests.swift",
                 "demo/iosApp/MultimodalModelHostFactory.swift", "probes/ios-multimodal-bridge/MultimodalModelHostFactoryTests.swift",
                 "demo/iosApp/MultimodalHostPreparation.swift", "probes/ios-multimodal-bridge/MultimodalHostPreparationTests.swift",
                 "probes/ios-multimodal-demo/AppAccessibility.swift", "probes/ios-multimodal-bridge/AppAccessibilityTests.swift",
                 "probes/ios-multimodal-demo/AppBackgroundObservation.swift", "probes/ios-multimodal-bridge/AppBackgroundObservationTests.swift"]


class MultimodalBridgeProjectTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / SCRIPT, self.root / SCRIPT)
        (self.root / "apple").mkdir()
        for source in SOURCES + INPUT_SOURCES:
            path = self.root / source
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("// fixture\n")
        for module, base in [("ODAIKitBinding", "kmp-binding"), ("ODAIKitDemo", "demo/shared")]:
            (self.root / base / f"build/bin/iosSimulatorArm64/debugFramework/{module}.framework").mkdir(parents=True)
        self.output = self.root / "artifacts/tests"
        self.ruby = os.environ.get("RUBY", "ruby")

    def generate(self, module="ODAIKitBinding"):
        return subprocess.run([self.ruby, str(self.root / SCRIPT)], capture_output=True, text=True,
                              env={**os.environ, "ODAI_MULTIMODAL_BRIDGE_TEST_DIR": str(self.output),
                                   "ODAI_MULTIMODAL_BRIDGE_KMP_MODULE": module})

    def testBothAbisLinkActualFrameworkAndOnlyBaseMultimodalProducts(self):
        for module, base in [("ODAIKitBinding", "kmp-binding"), ("ODAIKitDemo", "demo/shared")]:
            with self.subTest(module=module):
                self.output = self.root / f"artifacts/{module}"
                generated = self.generate(module)
                self.assertEqual(generated.returncode, 0, generated.stderr)
                inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.fetch(0)
puts JSON.generate({sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
  products: t.package_product_dependencies.map(&:product_name),
  linked: t.frameworks_build_phase.files.map { |f| f.file_ref&.real_path&.to_s }.compact,
  settings: t.build_configurations.map(&:build_settings)})
""", str(self.output / "MultimodalBridgeTests.xcodeproj")], capture_output=True, text=True)
                self.assertEqual(inspected.returncode, 0, inspected.stderr)
                data = json.loads(inspected.stdout)
                self.assertEqual(sorted(data["products"]), ["ODAIKitCore", "ODAIKitModelStore", "ODAIKitMultimodal"] if module == "ODAIKitDemo" else ["ODAIKitCore", "ODAIKitMultimodal"])
                framework = self.root / base / f"build/bin/iosSimulatorArm64/debugFramework/{module}.framework"
                self.assertEqual([p for p in data["linked"] if Path(p).name != "Foundation.framework"], [str(framework)])
                expected = SOURCES + (INPUT_SOURCES if module == "ODAIKitDemo" else [])
                self.assertEqual(sorted(data["sources"]), sorted(str(self.root / p) for p in expected))
                self.assertTrue(all(c["IPHONEOS_DEPLOYMENT_TARGET"] == "15.0" for c in data["settings"]))
                self.assertTrue(all(("ODAI_DEMO_BINDING" in c["SWIFT_ACTIVE_COMPILATION_CONDITIONS"]) == (module == "ODAIKitDemo") for c in data["settings"]))

    def testMissingInputAndUnknownModuleRejectBeforeOutput(self):
        (self.root / SOURCES[0]).unlink()
        result = self.generate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Missing input", result.stderr)
        self.assertFalse(self.output.exists())
        self.assertNotEqual(self.generate("Other").returncode, 0)
        self.assertFalse(self.output.exists())

    def testExistingOutputAndDanglingSymlinkArePreserved(self):
        self.output.mkdir(parents=True)
        sentinel = self.output / "keep"
        sentinel.write_bytes(b"user data")
        self.assertNotEqual(self.generate().returncode, 0)
        self.assertEqual(sentinel.read_bytes(), b"user data")
        self.output = self.root / "artifacts/link"
        self.output.symlink_to(self.root / "missing")
        self.assertNotEqual(self.generate().returncode, 0)
        self.assertTrue(self.output.is_symlink())


if __name__ == "__main__":
    unittest.main()
