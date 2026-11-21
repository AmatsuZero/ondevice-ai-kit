import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = "scripts/generate-video-bridge-tests.rb"
SOURCES = ["kmp-binding/apple/IOSVideoBridge.swift", "kmp-binding/apple/IOSVideoInput.swift",
           "probes/ios-video-bridge/IOSVideoBridgeTests.swift"]
HOST_SOURCES = ["demo/iosApp/VideoHost.swift", "demo/iosApp/VideoInputStore.swift", "demo/iosApp/VideoFilePicker.swift",
                "demo/iosApp/SpeechInputStore.swift", "kmp-binding/apple/IOSSpeechAudioInput.swift",
                "probes/ios-video-bridge/VideoHostTests.swift"]


class VideoBridgeProjectTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / SCRIPT, self.root / SCRIPT)
        (self.root / "apple").mkdir()
        for source in SOURCES + HOST_SOURCES:
            path = self.root / source
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("// fixture\n")
        for module, base in [("ODAIKitBinding", "kmp-binding"), ("ODAIKitDemo", "demo/shared")]:
            (self.root / base / f"build/bin/iosSimulatorArm64/debugFramework/{module}.framework").mkdir(parents=True)
        self.output = self.root / "artifacts/tests"
        self.ruby = os.environ.get("RUBY", "ruby")

    def generate(self, module="ODAIKitBinding"):
        return subprocess.run([self.ruby, str(self.root / SCRIPT)], capture_output=True, text=True,
                              env={**os.environ, "ODAI_VIDEO_BRIDGE_TEST_DIR": str(self.output),
                                   "ODAI_VIDEO_BRIDGE_KMP_MODULE": module})

    def testBothAbisLinkOnlyCoreAndVideoAndCorrectSources(self):
        for module in ["ODAIKitBinding", "ODAIKitDemo"]:
            with self.subTest(module=module):
                self.output = self.root / f"artifacts/{module}"
                generated = self.generate(module)
                self.assertEqual(generated.returncode, 0, generated.stderr)
                inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV[0]); t = p.targets.fetch(0)
puts JSON.generate({sources: t.source_build_phase.files.map { |f| f.file_ref.real_path.to_s },
  products: t.package_product_dependencies.map(&:product_name),
  settings: t.build_configurations.map(&:build_settings)})
""", str(self.output / "VideoBridgeTests.xcodeproj")], capture_output=True, text=True, check=True)
                data = json.loads(inspected.stdout)
                products = ["ODAIKitCore", "ODAIKitVideo"] + (["ODAIKitAppleVideo"] if module == "ODAIKitDemo" else [])
                self.assertEqual(sorted(data["products"]), sorted(products))
                sources = SOURCES + (HOST_SOURCES if module == "ODAIKitDemo" else [])
                self.assertEqual(sorted(data["sources"]), sorted(str(self.root / p) for p in sources))
                self.assertTrue(all(c["IPHONEOS_DEPLOYMENT_TARGET"] == "15.0" for c in data["settings"]))
                self.assertTrue(all(("ODAI_DEMO_BINDING" in c["SWIFT_ACTIVE_COMPILATION_CONDITIONS"]) == (module == "ODAIKitDemo") for c in data["settings"]))

    def testMissingInputAndUnknownModuleRejectBeforeOutput(self):
        path = self.root / SOURCES[0]
        path.unlink()
        result = self.generate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Missing input", result.stderr)
        self.assertFalse(self.output.exists())
        result = self.generate("Other")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())

    def testExistingOutputAndDanglingSymlinkArePreserved(self):
        self.output.mkdir(parents=True)
        sentinel = self.output / "keep"
        sentinel.write_bytes(b"user data")
        self.assertNotEqual(self.generate().returncode, 0)
        self.assertEqual(sentinel.read_bytes(), b"user data")
        other = self.root / "artifacts/link"
        other.symlink_to(self.root / "missing")
        self.output = other
        self.assertNotEqual(self.generate().returncode, 0)
        self.assertTrue(other.is_symlink())
        self.assertFalse((self.root / "missing").exists())


if __name__ == "__main__":
    unittest.main()
