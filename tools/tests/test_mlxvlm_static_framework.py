from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from tools.package_mlxvlm_static_slices import framework_slice


@unittest.skipUnless(shutil.which("xcrun"), "Xcode required")
class StaticFrameworkTests(unittest.TestCase):
    def test_framework_contains_real_archive_and_textual_interface(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            products = root / "products"
            products.mkdir()
            sdk = subprocess.check_output(["xcrun", "--sdk", "iphoneos", "--show-sdk-path"], text=True).strip()
            subprocess.run(["xcrun", "clang", "-target", "arm64-apple-ios18.0", "-isysroot", sdk,
                            "-c", "-x", "c", "-o", str(products / "Probe.o"), "-"],
                           input="void probe(void) {}", text=True, capture_output=True, check=True)
            modules = products / "Probe.swiftmodule"
            modules.mkdir()
            (modules / "arm64-apple-ios.swiftinterface").write_text("public func probe()\n")
            (modules / "arm64-apple-ios.swiftmodule").write_bytes(b"compiler-specific")
            target = root / "Probe.framework"
            result = framework_slice(products, "Probe", target, [])
            self.assertEqual(result["platforms"], {"2"})
            self.assertTrue((target / "Probe").read_bytes().startswith(b"!<arch>"))
            self.assertEqual((target / "Modules/Probe.swiftmodule/arm64-apple-ios.swiftinterface").read_text(),
                             "public func probe()\n")
            self.assertFalse(list(target.rglob("*.swiftmodule/arm64-apple-ios.swiftmodule")))
            self.assertTrue((products / "Probe.o").is_file())

    def test_existing_framework_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "Probe.framework"
            target.mkdir()
            (target / "keep").write_text("original")
            with self.assertRaisesRegex(ValueError, "exists"):
                framework_slice(root / "missing", "Probe", target, [])
            self.assertEqual((target / "keep").read_text(), "original")

    def test_missing_input_does_not_publish_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "Probe.framework"
            with self.assertRaisesRegex(ValueError, "missing"):
                framework_slice(root, "Probe", target, [])
            self.assertFalse(target.exists())
