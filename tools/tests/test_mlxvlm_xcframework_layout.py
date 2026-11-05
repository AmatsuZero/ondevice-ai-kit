import plistlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from tools.inspect_mlxvlm_xcframework import _inspect_binary, inspect_xcframework


class MLXVLMXCFrameworkLayoutTests(unittest.TestCase):
    def setUp(self):
        self.binary_probe = patch(
            "tools.inspect_mlxvlm_xcframework._inspect_binary",
            side_effect=lambda binary: {
                "architectures": ["arm64"], "minimumOS": "18.0",
                "platforms": {"7" if "simulator" in str(binary) else "2"},
            },
        )
        self.binary_probe.start()
        self.addCleanup(self.binary_probe.stop)

    def make_fixture(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        artifact = root / "ODAIKitAppleMLXVLM.xcframework"
        for identifier, platform, variant in (
            ("ios-arm64", "ios", None),
            ("ios-arm64-simulator", "ios", "simulator"),
        ):
            framework = artifact / identifier / "ODAIKitAppleMLXVLM.framework"
            modules = framework / "Modules/ODAIKitAppleMLXVLM.swiftmodule"
            modules.mkdir(parents=True)
            (framework / "ODAIKitAppleMLXVLM").write_bytes(b"binary")
            (modules / f"{identifier}.swiftinterface").write_text("import ODAIKitCore\n")
            (framework / "Modules/module.modulemap").write_text("framework module ODAIKitAppleMLXVLM {}\n")
            (framework / "Resources.bundle").mkdir()
            library = {
                "LibraryIdentifier": identifier,
                "LibraryPath": "ODAIKitAppleMLXVLM.framework",
                "SupportedPlatform": platform,
                "SupportedArchitectures": ["arm64"],
                "MinimumOSVersion": "18.0",
            }
            if variant:
                library["SupportedPlatformVariant"] = variant
            (artifact / identifier).mkdir(exist_ok=True)
            plist = artifact / "Info.plist"
            libraries = [] if not plist.exists() else plistlib.loads(plist.read_bytes())["AvailableLibraries"]
            plist.write_bytes(plistlib.dumps({"AvailableLibraries": libraries + [library]}))
        return root, artifact

    def test_valid_device_and_simulator_layout_is_reported(self):
        _, artifact = self.make_fixture()
        result = inspect_xcframework(artifact, "ODAIKitAppleMLXVLM", "18.0")
        self.assertEqual(result["module"], "ODAIKitAppleMLXVLM")
        self.assertEqual(result["slices"], ["ios-arm64", "ios-arm64-simulator"])
        self.assertEqual(result["minimumOS"], "18.0")
        self.assertTrue(result["hasCModuleMap"])
        self.assertEqual(result["resourceBundles"], sorted([
            "ios-arm64/ODAIKitAppleMLXVLM.framework/Resources.bundle",
            "ios-arm64-simulator/ODAIKitAppleMLXVLM.framework/Resources.bundle",
        ]))

    def test_missing_simulator_slice_is_rejected(self):
        _, artifact = self.make_fixture()
        shutil.rmtree(artifact / "ios-arm64-simulator")
        plist = plistlib.loads((artifact / "Info.plist").read_bytes())
        plist["AvailableLibraries"] = plist["AvailableLibraries"][:1]
        (artifact / "Info.plist").write_bytes(plistlib.dumps(plist))
        with self.assertRaisesRegex(ValueError, "simulator"):
            inspect_xcframework(artifact, "ODAIKitAppleMLXVLM", "18.0")

    def test_missing_public_interface_is_rejected(self):
        _, artifact = self.make_fixture()
        (artifact / "ios-arm64/ODAIKitAppleMLXVLM.framework/Modules/ODAIKitAppleMLXVLM.swiftmodule/ios-arm64.swiftinterface").unlink()
        with self.assertRaisesRegex(ValueError, "interface"):
            inspect_xcframework(artifact, "ODAIKitAppleMLXVLM", "18.0")

    def test_missing_macho_architecture_is_rejected(self):
        _, artifact = self.make_fixture()
        self.binary_probe.stop()
        with patch("tools.inspect_mlxvlm_xcframework._inspect_binary",
                   return_value={"architectures": ["x86_64"], "minimumOS": "18.0", "platforms": {"2"}}):
            with self.assertRaisesRegex(ValueError, "arm64"):
                inspect_xcframework(artifact, "ODAIKitAppleMLXVLM", "18.0")

    def test_macho_platform_must_match_slice(self):
        _, artifact = self.make_fixture()
        self.binary_probe.stop()
        with patch("tools.inspect_mlxvlm_xcframework._inspect_binary",
                   return_value={"architectures": ["arm64"], "minimumOS": "18.0", "platforms": {"2"}}):
            with self.assertRaisesRegex(ValueError, "platform"):
                inspect_xcframework(artifact, "ODAIKitAppleMLXVLM", "18.0")

    def test_nested_symlink_escape_is_rejected(self):
        root, artifact = self.make_fixture()
        outside = root / "outside-binary"
        outside.write_bytes(b"not a Mach-O")
        target = artifact / "ios-arm64/ODAIKitAppleMLXVLM.framework/ODAIKitAppleMLXVLM"
        target.unlink()
        target.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "symlink"):
            inspect_xcframework(artifact, "ODAIKitAppleMLXVLM", "18.0")

    def test_top_level_symlink_is_rejected(self):
        root, artifact = self.make_fixture()
        link = root / "linked.xcframework"
        link.symlink_to(artifact, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlink"):
            inspect_xcframework(link, "ODAIKitAppleMLXVLM", "18.0")

    @unittest.skipUnless(shutil.which("xcrun"), "Xcode toolchain unavailable")
    def test_actual_macho_header_reports_platform_and_floor(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        binary = Path(directory.name) / "Probe"
        sdk = subprocess.check_output(["xcrun", "--sdk", "iphoneos", "--show-sdk-path"], text=True).strip()
        subprocess.run([
            "xcrun", "clang", "-target", "arm64-apple-ios17.0", "-isysroot", sdk,
            "-dynamiclib", "-x", "c", "-o", str(binary), "-",
        ], input="void probe(void) {}\n", text=True, capture_output=True, check=True)
        self.assertEqual(_inspect_binary(binary), {
            "architectures": ["arm64"], "minimumOS": "17.0", "platforms": {"2"},
        })

    @unittest.skipUnless(shutil.which("xcrun"), "Xcode toolchain unavailable")
    def test_actual_static_archive_reports_member_platform(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk = subprocess.check_output(["xcrun", "--sdk", "iphoneos", "--show-sdk-path"], text=True).strip()
            obj = root / "Probe.o"
            subprocess.run([
                "xcrun", "clang", "-target", "arm64-apple-ios18.0", "-isysroot", sdk,
                "-c", "-x", "c", "-o", str(obj), "-",
            ], input="void probe(void) {}\n", text=True, capture_output=True, check=True)
            archive = root / "Probe"
            subprocess.run(["xcrun", "libtool", "-static", "-o", str(archive), str(obj)],
                           capture_output=True, check=True)
            self.assertEqual(_inspect_binary(archive), {
                "architectures": ["arm64"], "minimumOS": "18.0", "platforms": {"2"},
            })

    @unittest.skipUnless(shutil.which("xcrun"), "Xcode toolchain unavailable")
    def test_static_archive_with_mixed_platform_members_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            objects = []
            for platform, triple in [("iphoneos", "arm64-apple-ios18.0"),
                                     ("iphonesimulator", "arm64-apple-ios18.0-simulator")]:
                sdk = subprocess.check_output(["xcrun", "--sdk", platform, "--show-sdk-path"], text=True).strip()
                obj = root / f"{platform}.o"
                subprocess.run(["xcrun", "clang", "-target", triple, "-isysroot", sdk,
                                "-c", "-x", "c", "-o", str(obj), "-"],
                               input=f"void {platform}(void) {{}}\n", text=True, capture_output=True, check=True)
                objects.append(str(obj))
            archive = root / "Mixed"
            subprocess.run(["xcrun", "libtool", "-static", "-o", str(archive), *objects],
                           capture_output=True, check=True)
            with self.assertRaisesRegex(ValueError, "platform"):
                _inspect_binary(archive)

    @unittest.skipUnless(shutil.which("xcrun"), "Xcode toolchain unavailable")
    def test_static_archive_reports_highest_member_deployment_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk = subprocess.check_output(["xcrun", "--sdk", "iphoneos", "--show-sdk-path"], text=True).strip()
            objects = []
            # Modern SDKs remap the nonexistent iOS 19 deployment version to 26.
            for version in (18, 26):
                obj = root / f"Probe{version}.o"
                subprocess.run(["xcrun", "clang", "-target", f"arm64-apple-ios{version}.0",
                                "-isysroot", sdk, "-c", "-x", "c", "-o", str(obj), "-"],
                               input=f"void probe{version}(void) {{}}", text=True, capture_output=True, check=True)
                objects.append(str(obj))
            archive = root / "MixedTargets"
            subprocess.run(["xcrun", "libtool", "-static", "-o", str(archive), *objects],
                           capture_output=True, check=True)
            self.assertEqual(_inspect_binary(archive)["minimumOS"], "26.0")

    def test_binary_requiring_higher_os_than_declared_is_rejected(self):
        _, artifact = self.make_fixture()
        self.binary_probe.stop()
        with patch("tools.inspect_mlxvlm_xcframework._inspect_binary",
                   side_effect=lambda binary: {"architectures": ["arm64"], "minimumOS": "19.0",
                                              "platforms": {"7" if "simulator" in str(binary) else "2"}}):
            with self.assertRaisesRegex(ValueError, "minimum OS"):
                inspect_xcframework(artifact, "ODAIKitAppleMLXVLM", "18.0")


if __name__ == "__main__":
    unittest.main()
