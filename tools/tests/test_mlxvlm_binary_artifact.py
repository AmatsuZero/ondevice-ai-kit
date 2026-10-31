import hashlib
import json
import plistlib
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

from tools import validate_mlxvlm_binary_artifact as validator
from tools.validate_mlxvlm_binary_artifact import validate_manifest


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sha256_tree(path: Path) -> str:
    digest = hashlib.sha256()
    for child in sorted(p for p in path.rglob("*") if p.is_file()):
        digest.update(str(child.relative_to(path)).encode())
        digest.update(b"\0")
        digest.update(child.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


class MLXVLMBinaryArtifactTests(unittest.TestCase):
    def make_fixture(self):
        root = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: shutil.rmtree(root))
        artifact = root / "xcframeworks/ODAIKitAppleMLXVLM.xcframework"
        interface = artifact / "ios-arm64/ODAIKitAppleMLXVLM.framework/Modules/ODAIKitAppleMLXVLM.swiftmodule/ios-arm64.swiftinterface"
        simulator = artifact / "ios-arm64-simulator/ODAIKitAppleMLXVLM.framework/Modules/ODAIKitAppleMLXVLM.swiftmodule/ios-arm64-simulator.swiftinterface"
        interface.parent.mkdir(parents=True)
        simulator.parent.mkdir(parents=True)
        for slice_path in ("ios-arm64", "ios-arm64-simulator"):
            framework = artifact / slice_path / "ODAIKitAppleMLXVLM.framework"
            (framework / "Modules").mkdir(parents=True, exist_ok=True)
            (framework / "ODAIKitAppleMLXVLM").write_bytes(b"mach-o placeholder")
            (framework / "Modules/module.modulemap").write_text("framework module ODAIKitAppleMLXVLM {}\n")
        (artifact / "Info.plist").parent.mkdir(parents=True, exist_ok=True)
        (artifact / "Info.plist").write_bytes(plistlib.dumps({"AvailableLibraries": [
            {"LibraryIdentifier": "ios-arm64", "LibraryPath": "ODAIKitAppleMLXVLM.framework", "SupportedPlatform": "ios"},
            {"LibraryIdentifier": "ios-arm64-simulator", "LibraryPath": "ODAIKitAppleMLXVLM.framework", "SupportedPlatform": "ios", "SupportedPlatformVariant": "simulator"},
        ]}))
        interface.write_text("module ODAIKitAppleMLXVLM")
        simulator.write_text("module ODAIKitAppleMLXVLM simulator")
        resource = root / "resources/smolvlm2-model.json"
        metal = root / "resources/default.metallib"
        resource.parent.mkdir(parents=True)
        resource.write_text("model")
        metal.write_bytes(b"metal")
        model_patch = patch.object(validator, "EXPECTED_MODEL_SHA256", sha256_file(resource), create=True)
        metal_patch = patch.object(validator, "EXPECTED_METAL_SHA256", sha256_file(metal), create=True)
        model_patch.start()
        metal_patch.start()
        self.addCleanup(metal_patch.stop)
        self.addCleanup(model_patch.stop)
        manifest = root / "artifact.json"
        manifest.write_text(json.dumps({
            "schemaVersion": 1,
            "kind": "apple-mlxvlm-xcframework-set",
            "sourceCommit": "0123456789abcdef0123456789abcdef01234567",
            "toolchain": {"xcode": "27.1", "swift": "6.4"},
            "moduleClosure": ["ODAIKitAppleMLXVLM"],
            "sourceAttributions": [{"module": "ODAIKitAppleMLXVLM", "source": "https://github.com/AmatsuZero/ondevice-ai-kit", "revision": "0123456789abcdef0123456789abcdef01234567", "license": "MIT"}],
            "resources": [
                {"path": "resources/smolvlm2-model.json", "sha256": sha256_file(resource)},
                {"path": "resources/default.metallib", "sha256": sha256_file(metal)},
            ],
            "artifacts": [{
                "path": "xcframeworks/ODAIKitAppleMLXVLM.xcframework",
                "modules": ["ODAIKitAppleMLXVLM"],
                "minimumOS": "18.0",
                "sha256": sha256_tree(artifact),
                "slices": [
                    {"path": "ios-arm64", "platform": "iOS", "architectures": ["arm64"]},
                    {"path": "ios-arm64-simulator", "platform": "iOS Simulator", "architectures": ["arm64"]},
                ],
                "interfaces": [
                    {"path": str(interface.relative_to(artifact)), "sha256": sha256_file(interface)},
                    {"path": str(simulator.relative_to(artifact)), "sha256": sha256_file(simulator)},
                ],
            }],
        }, indent=2))
        return root, manifest, artifact, interface

    def test_valid_manifest_passes(self):
        root, manifest, *_ = self.make_fixture()
        validate_manifest(manifest, root)

    def test_missing_module_is_rejected(self):
        root, manifest, *_ = self.make_fixture()
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["modules"] = []
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "module closure"):
            validate_manifest(manifest, root)

    def test_artifact_hash_drift_is_rejected(self):
        root, manifest, artifact, *_ = self.make_fixture()
        (artifact / "ios-arm64/extra.bin").write_bytes(b"drift")
        with self.assertRaisesRegex(ValueError, "sha256"):
            validate_manifest(manifest, root)

    def test_resource_hash_drift_is_rejected(self):
        root, manifest, *_ = self.make_fixture()
        (root / "resources/smolvlm2-model.json").write_text("changed model")
        with self.assertRaisesRegex(ValueError, "resource.*sha256"):
            validate_manifest(manifest, root)

    def test_recomputed_hash_cannot_replace_canonical_resources(self):
        root, manifest, *_ = self.make_fixture()
        model = root / "resources/smolvlm2-model.json"
        model.write_text("replacement model")
        payload = json.loads(manifest.read_text())
        payload["resources"][0]["sha256"] = sha256_file(model)
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "canonical model"):
            validate_manifest(manifest, root)

    def test_required_resource_constants_match_design(self):
        self.assertEqual(validator.EXPECTED_MODEL_SHA256,
                         "c074e693cc0fb9cfb19d6e7a393ffb113108e88d1489a387e8cb06d309602d54")
        self.assertEqual(validator.EXPECTED_METAL_SHA256,
                         "953544ddd003eaef4f0f03c5dce821c2911704288a2ffebd231de7dd60383ee2")

    def test_interface_hash_drift_is_rejected(self):
        root, manifest, artifact, interface = self.make_fixture()
        interface.write_text("changed interface")
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["sha256"] = sha256_tree(artifact)
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "interfaces.*sha256"):
            validate_manifest(manifest, root)

    def test_symlink_escape_is_rejected(self):
        root, manifest, *_ = self.make_fixture()
        outside = root.parent / (root.name + "-outside-model.json")
        outside.write_text("outside")
        self.addCleanup(lambda: outside.unlink(missing_ok=True))
        link = root / "resources/escaped.json"
        link.symlink_to(outside)
        payload = json.loads(manifest.read_text())
        payload["resources"].append({"path": "resources/escaped.json", "sha256": sha256_file(outside)})
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "symlink"):
            validate_manifest(manifest, root)

    def test_source_checkout_path_is_rejected(self):
        root, manifest, *_ = self.make_fixture()
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["path"] = "SourcePackages/checkouts/MLX.xcframework"
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "source checkout"):
            validate_manifest(manifest, root)

    def test_symlink_inside_artifact_tree_is_rejected(self):
        root, manifest, artifact, *_ = self.make_fixture()
        outside = root.parent / (root.name + "-outside.bin")
        outside.write_bytes(b"outside")
        self.addCleanup(lambda: outside.unlink(missing_ok=True))
        (artifact / "ios-arm64/escaped.bin").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "symlink"):
            validate_manifest(manifest, root)

    def test_each_slice_requires_arm64_and_interface(self):
        root, manifest, *_ = self.make_fixture()
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["slices"][1]["architectures"] = ["x86_64"]
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "arm64"):
            validate_manifest(manifest, root)
        payload["artifacts"][0]["slices"][1]["architectures"] = ["arm64"]
        payload["artifacts"][0]["interfaces"] = payload["artifacts"][0]["interfaces"][:1]
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "interface"):
            validate_manifest(manifest, root)

    def test_duplicate_module_across_artifacts_is_rejected(self):
        root, manifest, artifact, *_ = self.make_fixture()
        payload = json.loads(manifest.read_text())
        second = json.loads(json.dumps(payload["artifacts"][0]))
        second["path"] = "xcframeworks/copy.xcframework"
        shutil.copytree(artifact, root / second["path"])
        payload["artifacts"].append(second)
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "duplicate module"):
            validate_manifest(manifest, root)

    def test_malformed_resource_path_and_modules_raise_value_error(self):
        root, manifest, *_ = self.make_fixture()
        original = json.loads(manifest.read_text())
        payload = json.loads(json.dumps(original))
        payload["resources"][0]["path"] = ["not", "a", "path"]
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "resource\\[0\\].path"):
            validate_manifest(manifest, root)
        payload = json.loads(json.dumps(original))
        payload["artifacts"][0]["modules"] = [{"bad": "module"}]
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "artifact\\[0\\].modules"):
            validate_manifest(manifest, root)

    def test_provenance_requires_full_commit_toolchain_and_attribution(self):
        root, manifest, *_ = self.make_fixture()
        original = json.loads(manifest.read_text())
        cases = [
            ("sourceCommit", "short", "sourceCommit"),
            ("toolchain", {}, "toolchain"),
            ("sourceAttributions", [], "sourceAttributions"),
        ]
        for field, value, message in cases:
            with self.subTest(field=field):
                payload = json.loads(json.dumps(original))
                payload[field] = value
                manifest.write_text(json.dumps(payload))
                with self.assertRaisesRegex(ValueError, message):
                    validate_manifest(manifest, root)

    def test_duplicate_slice_and_interface_paths_are_rejected(self):
        root, manifest, *_ = self.make_fixture()
        original = json.loads(manifest.read_text())
        for field in ("slices", "interfaces"):
            with self.subTest(field=field):
                payload = json.loads(json.dumps(original))
                payload["artifacts"][0][field].append(payload["artifacts"][0][field][0])
                manifest.write_text(json.dumps(payload))
                with self.assertRaisesRegex(ValueError, f"duplicate {field[:-1]}"):
                    validate_manifest(manifest, root)

    def test_declared_module_must_match_actual_xcframework(self):
        root, manifest, *_ = self.make_fixture()
        payload = json.loads(manifest.read_text())
        payload["moduleClosure"] = ["InventedModule"]
        payload["sourceAttributions"][0]["module"] = "InventedModule"
        payload["artifacts"][0]["modules"] = ["InventedModule"]
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "module|XCFramework"):
            validate_manifest(manifest, root)



if __name__ == "__main__":
    unittest.main()
