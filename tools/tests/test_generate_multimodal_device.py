import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from tools import generate_multimodal_device as generator


class MultimodalDeviceGeneratorTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for name in generator.SOURCES + ["apple-mlx/Package.resolved", "probes/ios-multimodal-device/project.yml"]:
            path = self.root / name; path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text((generator.ROOT / name).read_text() if name.endswith("project.yml") else "fixture")
        (self.root / "apple").mkdir(exist_ok=True)
        (self.root / "demo/shared/build/bin/iosArm64/debugFramework/ODAIKitDemo.framework").mkdir(parents=True)
        self.output = self.root / "output"
        patcher = patch.object(generator, "ROOT", self.root); patcher.start(); self.addCleanup(patcher.stop)

    def testGeneratesActualIsolatedProjectAndExactLock(self):
        generator.generate(self.output, "a" * 40)
        lock = self.output / "MultimodalDevice.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"
        self.assertEqual(lock.read_text(), "fixture")
        project = (self.output / "MultimodalDevice.xcodeproj/project.pbxproj").read_text()
        for value in ["io.ondeviceai.multimodallifecycle", "18.0", "ODAI_DEMO_BINDING", "ODAIKitDemo.framework", "ODAIKitAppleMLXVLM", "MultimodalDeviceSuite.swift", "a" * 40]:
            self.assertIn(value, project)
        self.assertFalse((self.root / "demo/iosApp/OnDeviceAIDemo.xcodeproj").exists())

    def testMissingInputRejectsBeforeOutput(self):
        (self.root / "apple-mlx/Package.resolved").unlink()
        with self.assertRaises(ValueError): generator.generate(self.output, "a" * 40)
        self.assertFalse(self.output.exists())

    def testExistingOutputAndInvalidCommitRejectWithoutMutation(self):
        self.output.mkdir(); sentinel = self.output / "keep"; sentinel.write_text("preserve")
        with self.assertRaises(ValueError): generator.generate(self.output, "a" * 40)
        self.assertEqual(list(self.output.iterdir()), [sentinel])
        self.assertEqual(sentinel.read_text(), "preserve")
        with self.assertRaises(ValueError): generator.generate(self.root / "invalid", "dirty")
        self.assertFalse((self.root / "invalid").exists())
