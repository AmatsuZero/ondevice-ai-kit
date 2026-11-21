import hashlib
import importlib.util
import json
import plistlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class IdentityBinaryConsumerTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / "scripts/generate-binary-consumer.rb", self.root / "scripts")
        source = self.root / "examples/ios-identity-native"
        source.mkdir(parents=True)
        # The generator tests project membership; real API compilation is a separate gate.
        (source / "IdentityConsumerApp.swift").write_text("import ODAIKitIdentity\n")
        for product in ["ODAIKitCore", "ODAIKitModelStore", "ODAIKitIdentity"]:
            (self.root / f"artifacts/xcframeworks/{product}.xcframework").mkdir(parents=True)
        self.ruby = os.environ.get("RUBY", "ruby")
        self.output = self.root / "artifacts/IdentityBinaryConsumer"

    def generate(self):
        return subprocess.run([self.ruby, str(self.root / "scripts/generate-binary-consumer.rb"),
                               "--identity"], capture_output=True, text=True)

    def testIdentityConsumerLinksOnlyItsThreeNativeArtifacts(self):
        result = self.generate()
        self.assertEqual(result.returncode, 0, result.stderr)
        project = self.output / "IdentityBinaryConsumer.xcodeproj"
        inspected = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
p = Xcodeproj::Project.open(ARGV.fetch(0))
t = p.targets.fetch(0)
puts JSON.generate(t.frameworks_build_phase.files.map { |f| File.basename(f.file_ref.path) }.sort)
""", str(project)], capture_output=True, text=True, check=True)
        self.assertEqual(json.loads(inspected.stdout),
                         ["Foundation.framework", "ODAIKitCore.xcframework", "ODAIKitIdentity.xcframework", "ODAIKitModelStore.xcframework"])

    def testExistingConsumerIsRefusedWithoutChangingAnyFile(self):
        self.output.mkdir(parents=True)
        sentinel = self.output / "host-owned.txt"
        sentinel.write_text("keep")
        before = sorted(str(p.relative_to(self.output)) for p in self.output.rglob("*"))
        result = self.generate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Refusing to replace existing", result.stderr)
        self.assertEqual(sentinel.read_text(), "keep")
        self.assertEqual(sorted(str(p.relative_to(self.output)) for p in self.output.rglob("*")), before)

    def testMissingIdentityArtifactDoesNotCreateConsumer(self):
        (self.root / "artifacts/xcframeworks/ODAIKitIdentity.xcframework").rmdir()
        result = self.generate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Missing framework", result.stderr)
        self.assertFalse(self.output.exists())


class IdentityArtifactReportTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.artifact = Path(temporary.name) / "ODAIKitIdentity.xcframework"
        self.framework = self.artifact / "ios-arm64/ODAIKitIdentity.framework"
        self.interface = self.framework / "Modules/ODAIKitIdentity.swiftmodule/arm64-apple-ios.swiftinterface"
        self.interface.parent.mkdir(parents=True)
        self.interface.write_text("import ODAIKitCore\nimport ODAIKitModelStore\n"
            "public struct ModelBinding {}\npublic struct ThresholdPolicy {}\npublic struct PADPolicy {}\n")
        (self.framework / "ODAIKitIdentity").write_bytes(b"native binary fixture")
        (self.artifact / "Info.plist").write_bytes(plistlib.dumps({"AvailableLibraries": [{
            "LibraryIdentifier": "ios-arm64", "LibraryPath": "ODAIKitIdentity.framework",
            "SupportedPlatform": "ios"}]}))
        spec = importlib.util.spec_from_file_location("identity_distribution_report", ROOT / "tools/identity_distribution_report.py")
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def testReportsActualBindingInterfacesAndBinaryHashesWithoutProductionClaims(self):
        result = self.module.report(self.artifact)
        self.assertEqual(result["bindingContracts"], ["ModelBinding", "ThresholdPolicy", "PADPolicy"])
        self.assertIsNone(result["selectedProductionModel"])
        self.assertIsNone(result["calibrationEvidence"])
        self.assertEqual(result["slices"][0]["binarySha256"], hashlib.sha256(b"native binary fixture").hexdigest())
        self.assertEqual(result["slices"][0]["imports"], ["ODAIKitCore", "ODAIKitModelStore"])

    def testMissingPolicyInterfaceCannotBeReportedAsComplete(self):
        self.interface.write_text("public struct ModelBinding {}")
        with self.assertRaisesRegex(ValueError, "Missing binding contract"):
            self.module.report(self.artifact)

    def testBundledWeightsCannotBeReportedAsWeightFree(self):
        (self.framework / "model.safetensors").write_bytes(b"unapproved fixture")
        with self.assertRaisesRegex(ValueError, "Model weights"):
            self.module.report(self.artifact)

    def testKmpOrModelRuntimeImportFailsIsolation(self):
        with self.interface.open("a") as stream:
            stream.write("import ODAIKitDemo\n")
        with self.assertRaisesRegex(ValueError, "Non-native dependency"):
            self.module.report(self.artifact)
