import hashlib
import json
import plistlib
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

from tools import validate_mlxvlm_binary_artifact as binary
from tools import validate_mlxvlm_static_artifact as static
from tools.validate_mlxvlm_binary_artifact import validate_manifest


def file_sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree_sha(path):
    digest = hashlib.sha256()
    for child in sorted(item for item in path.rglob("*") if item.is_file()):
        digest.update(str(child.relative_to(path)).encode())
        digest.update(b"\0")
        digest.update(child.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


class StaticArtifactManifestTests(unittest.TestCase):
    def make_fixture(self):
        root = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: shutil.rmtree(root))
        artifact = root / "xcframeworks/ODAIKitAppleMLXVLM.xcframework"
        interfaces = []
        libraries = []
        for identifier, variant in (("ios-arm64", None), ("ios-arm64-simulator", "simulator")):
            framework = artifact / identifier / "ODAIKitAppleMLXVLM.framework"
            interface = framework / "Modules/ODAIKitAppleMLXVLM.swiftmodule" / f"{identifier}.swiftinterface"
            interface.parent.mkdir(parents=True)
            interface.write_text("module ODAIKitAppleMLXVLM")
            (framework / "ODAIKitAppleMLXVLM").write_bytes(b"binary")
            (framework / "Modules/module.modulemap").write_text("framework module ODAIKitAppleMLXVLM {}\n")
            interfaces.append({"path": str(interface.relative_to(artifact)), "sha256": file_sha(interface)})
            library = {"LibraryIdentifier": identifier, "LibraryPath": "ODAIKitAppleMLXVLM.framework", "SupportedPlatform": "ios"}
            if variant:
                library["SupportedPlatformVariant"] = variant
            libraries.append(library)
        (artifact / "Info.plist").write_bytes(plistlib.dumps({"AvailableLibraries": libraries}))
        modules = []
        for name in ("Cmlx", "cLogSupport", "_NumericsShims", "yyjson"):
            directory = root / "CModules" / name
            directory.mkdir(parents=True)
            (directory / "module.modulemap").write_text(f"module {name} {{}}\n")
            modules.append({"name": name, "path": str(directory.relative_to(root)), "owner": "ODAIKitAppleMLXVLM", "sha256": tree_sha(directory), "moduleMapSHA256": file_sha(directory / "module.modulemap")})
        resources = []
        toolchain = {"xcode": "27.2", "swift": "6.4.2"}
        commit = "0123456789abcdef0123456789abcdef01234567"
        for platform in ("device", "simulator"):
            resource_dir = root / "resources" / platform
            resource_dir.mkdir(parents=True)
            model = resource_dir / "smolvlm2-model.json"
            model.write_text("model")
            metal = resource_dir / "default.metallib"
            metal.write_bytes(platform.encode())
            receipt = root / "provenance" / f"{platform}.json"
            receipt.parent.mkdir(exist_ok=True)
            receipt.write_text(json.dumps({"sourceCommit": commit, "lockSHA256": static.PRODUCTION_LOCK_SHA256, "toolchain": toolchain, "platform": platform, "sdkVersion": "27.2", "sdkBuild": "27B52", "metalCompiler": "metal 400", "sourceInventorySHA256": "a" * 64, "overlayManifestSHA256": "b" * 64, "metalOutputSHA256": file_sha(metal)}))
            resources += [{"path": str(model.relative_to(root)), "sha256": file_sha(model), "platform": platform}, {"path": str(metal.relative_to(root)), "sha256": file_sha(metal), "platform": platform, "provenance": {"path": str(receipt.relative_to(root)), "sha256": file_sha(receipt)}}]
        notice = root / "NOTICES.txt"
        notice.write_text("local static staging notices\n")
        manifest = {"schemaVersion": 2, "kind": "apple-mlxvlm-xcframework-set", "sourceCommit": commit, "toolchain": toolchain, "linking": "static", "moduleClosure": ["ODAIKitAppleMLXVLM"], "sourceAttributions": [{"module": "ODAIKitAppleMLXVLM", "source": "https://example.test/source", "revision": commit, "license": "MIT"}], "resources": resources, "artifacts": [{"path": str(artifact.relative_to(root)), "modules": ["ODAIKitAppleMLXVLM"], "minimumOS": "18.0", "linking": "static", "sha256": tree_sha(artifact), "slices": [{"path": "ios-arm64", "platform": "iOS", "architectures": ["arm64"]}, {"path": "ios-arm64-simulator", "platform": "iOS Simulator", "architectures": ["arm64"]}], "interfaces": interfaces}], "cModules": modules, "linkSettings": {"libraries": ["c++"], "frameworks": ["Metal", "Accelerate"]}, "notices": [{"path": "NOTICES.txt", "sha256": file_sha(notice)}]}
        manifest_path = root / "manifest.json"
        manifest_path.write_text(json.dumps(manifest))
        model_hash = file_sha(root / "resources/device/smolvlm2-model.json")
        patches = [patch.object(binary, "EXPECTED_MODEL_SHA256", model_hash), patch.object(static, "EXPECTED_MODEL_SHA256", model_hash)]
        for item in patches:
            item.start(); self.addCleanup(item.stop)
        return root, manifest_path, manifest

    def validate(self, root, path):
        validate_manifest(path, root)

    def write(self, path, payload):
        path.write_text(json.dumps(payload))

    def test_v2_static_manifest_passes(self):
        root, path, _ = self.make_fixture()
        self.validate(root, path)

    def test_v1_behavior_remains_available(self):
        root, path, payload = self.make_fixture()
        payload["schemaVersion"] = 1
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "canonical Metal"):
            self.validate(root, path)

    def test_metal_requires_matching_provenance(self):
        root, path, payload = self.make_fixture()
        payload["resources"][1].pop("provenance")
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "provenance"):
            self.validate(root, path)

    def test_metal_platform_and_output_hash_must_match_receipt(self):
        root, path, payload = self.make_fixture()
        receipt_path = root / payload["resources"][1]["provenance"]["path"]
        receipt = json.loads(receipt_path.read_text())
        receipt["platform"] = "simulator"
        receipt_path.write_text(json.dumps(receipt))
        payload["resources"][1]["provenance"]["sha256"] = file_sha(receipt_path)
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "platform mismatch"):
            self.validate(root, path)
        receipt["platform"] = "device"; receipt["metalOutputSHA256"] = "0" * 64
        receipt_path.write_text(json.dumps(receipt)); payload["resources"][1]["provenance"]["sha256"] = file_sha(receipt_path)
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "metalOutputSHA256"):
            self.validate(root, path)

    def test_both_platform_resources_are_required(self):
        root, path, payload = self.make_fixture()
        payload["resources"] = [item for item in payload["resources"] if item["platform"] != "simulator"]
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "simulator resources"):
            self.validate(root, path)

    def test_c_module_hash_and_owner_are_checked(self):
        root, path, payload = self.make_fixture()
        (root / payload["cModules"][0]["path"] / "module.modulemap").write_text("drift")
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "sha256 drift"):
            self.validate(root, path)
        root, path, payload = self.make_fixture()
        payload["cModules"][0]["owner"] = "Other"
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "owner"):
            self.validate(root, path)

    def test_c_module_owner_type_and_name_are_rejected(self):
        root, path, payload = self.make_fixture()
        payload["cModules"][0]["owner"] = ["ODAIKitAppleMLXVLM"]
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "owner"):
            self.validate(root, path)
        root, path, payload = self.make_fixture()
        payload["cModules"][0]["name"] = "bad/module"
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "module identifier"):
            self.validate(root, path)

    def test_resource_platform_type_is_rejected(self):
        root, path, payload = self.make_fixture()
        payload["resources"][0]["platform"] = ["device"]
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "platform"):
            self.validate(root, path)

    def test_c_module_nested_symlink_escape_is_rejected(self):
        root, path, payload = self.make_fixture()
        outside = root.parent / f"{root.name}-outside-header.h"
        outside.write_text("outside")
        self.addCleanup(lambda: outside.unlink(missing_ok=True))
        directory = root / payload["cModules"][0]["path"]
        (directory / "escaped.h").symlink_to(outside)
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "symlink"):
            self.validate(root, path)

    def test_notice_hash_drift_is_rejected(self):
        root, path, payload = self.make_fixture()
        (root / "NOTICES.txt").write_text("drift")
        self.write(path, payload)
        with self.assertRaisesRegex(ValueError, "notices.*sha256 drift"):
            self.validate(root, path)


if __name__ == "__main__":
    unittest.main()
