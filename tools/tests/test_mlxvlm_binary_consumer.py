import errno
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import patch

from tools.tests import test_mlxvlm_binary_artifact as fixtures
from tools.generate_mlxvlm_binary_consumer import generate


class MLXVLMBinaryConsumerTests(unittest.TestCase):
    make_fixture = fixtures.MLXVLMBinaryArtifactTests.make_fixture

    def setUp(self):
        # Fixture files exercise generation, not a real MLXVLM binary build.
        probe = patch("tools.inspect_mlxvlm_xcframework._inspect_binary", side_effect=lambda path: {
            "architectures": ["arm64"], "minimumOS": "18.0",
            "platforms": {"7" if "simulator" in str(path) else "2"},
        })
        probe.start()
        self.addCleanup(probe.stop)

    def inputs(self):
        root, manifest, artifact, interface = self.make_fixture()
        payload = json.loads(manifest.read_text())
        original = payload["artifacts"][0]
        name = "ODAIKitMultimodal"
        extra = root / "xcframeworks" / f"{name}.xcframework"
        shutil.copytree(artifact, extra)
        for identifier in ("ios-arm64", "ios-arm64-simulator"):
            framework = extra / identifier / "ODAIKitAppleMLXVLM.framework"
            framework.rename(framework.with_name(f"{name}.framework"))
            framework = framework.with_name(f"{name}.framework")
            (framework / "ODAIKitAppleMLXVLM").rename(framework / name)
            modules = framework / "Modules/ODAIKitAppleMLXVLM.swiftmodule"
            modules.rename(modules.with_name(f"{name}.swiftmodule"))
        metadata = plistlib.loads((extra / "Info.plist").read_bytes())
        for library in metadata["AvailableLibraries"]:
            library["LibraryPath"] = f"{name}.framework"
        (extra / "Info.plist").write_bytes(plistlib.dumps(metadata))
        entry = json.loads(json.dumps(original))
        entry["path"] = str(extra.relative_to(root))
        entry["modules"] = [name]
        entry["sha256"] = fixtures.sha256_tree(extra)
        for interface in entry["interfaces"]:
            interface["path"] = interface["path"].replace("ODAIKitAppleMLXVLM", name)
        payload["artifacts"].append(entry)
        payload["moduleClosure"].append(name)
        payload["sourceAttributions"].append({**payload["sourceAttributions"][0], "module": name})
        for entry, name in zip(payload["resources"], ["OnDeviceAIKitAppleMLX_ODAIKitAppleMLXVLM", "mlx-swift_Cmlx"]):
            original = root / entry["path"]
            target = root / "resources" / f"{name}.bundle" / original.name
            target.parent.mkdir(parents=True)
            original.rename(target)
            entry["path"] = str(target.relative_to(root))
        manifest.write_text(json.dumps(payload))
        return root, manifest, artifact, interface

    def test_generated_project_is_relocatable_and_has_only_binary_dependencies(self):
        root, manifest, artifact, _ = self.inputs()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "consumer"
            generate(root, manifest, output)
            moved = Path(directory) / "moved consumer"
            output.rename(moved)
            project = moved / "MLXVLMBinaryConsumer.xcodeproj/project.pbxproj"
            objects = json.loads(subprocess.check_output(["plutil", "-convert", "json", "-o", "-", str(project)]))["objects"]
            self.assertFalse(any("SwiftPackage" in v.get("isa", "") for v in objects.values()))
            references = [v["path"] for v in objects.values() if v.get("isa") == "PBXFileReference" and "path" in v]
            framework_refs = [p for p in references if p.endswith(".xcframework")]
            self.assertEqual(set(framework_refs), {
                "artifacts/xcframeworks/ODAIKitAppleMLXVLM.xcframework",
                "artifacts/xcframeworks/ODAIKitMultimodal.xcframework",
            })
            def phase_paths(kind):
                return {objects[objects[file]["fileRef"]]["path"]
                        for phase in objects.values() if phase.get("isa") == kind
                        for file in phase.get("files", [])}
            self.assertEqual(phase_paths("PBXFrameworksBuildPhase"), set(framework_refs))
            self.assertEqual(phase_paths("PBXCopyFilesBuildPhase"), set(framework_refs))
            self.assertTrue(all(not Path(p).is_absolute() and ".." not in Path(p).parts for p in references))
            self.assertNotIn(str(root), project.read_text())
            self.assertEqual((moved / "artifacts" / artifact.relative_to(root) / "Info.plist").read_bytes(),
                             (artifact / "Info.plist").read_bytes())
            resource_refs = [Path(p).name for p in references if p.endswith(".bundle")]
            self.assertEqual(set(resource_refs), {
                "OnDeviceAIKitAppleMLX_ODAIKitAppleMLXVLM.bundle",
                "mlx-swift_Cmlx.bundle",
            })
            self.assertEqual({Path(p).name for p in phase_paths("PBXResourcesBuildPhase")}, set(resource_refs))
            for bundle in resource_refs:
                self.assertTrue((moved / "artifacts/resources" / bundle).is_dir())
            source = (moved / "ConsumerApp.swift").read_text()
            self.assertIn("import ODAIKitAppleMLXVLM", source)
            self.assertIn("MultimodalRequest(imageFile:", source)
            self.assertIn("MultimodalVideoRequest(videoFile:", source)
            self.assertIn("SmolVLM2Model.manifest()", source)
            self.assertIn("makeLibrary(URL:", source)
            self.assertIn("SHA256.hash", source)
            subprocess.run(["xcrun", "swiftc", "-frontend", "-parse", str(moved / "ConsumerApp.swift")], capture_output=True, check=True)
            receipt = json.loads((moved / "consumer-inputs.json").read_text())
            self.assertEqual(receipt["kind"], "apple-mlxvlm-binary-consumer")
            self.assertEqual(receipt["buildStatus"], "not-run")
            self.assertEqual(receipt["sourceCommit"], json.loads(manifest.read_text())["sourceCommit"])

    def test_corrupt_artifact_is_rejected_before_output(self):
        root, manifest, artifact, _ = self.inputs()
        (artifact / "unexpected").write_text("drift")
        output = root.parent / (root.name + "-consumer")
        with self.assertRaisesRegex(ValueError, "sha256"):
            generate(root, manifest, output)
        self.assertFalse(output.exists())

    def test_existing_output_is_preserved(self):
        root, manifest, *_ = self.inputs()
        output = root / "existing"
        output.mkdir()
        (output / "keep").write_text("preserve")
        with self.assertRaisesRegex(ValueError, "exists"):
            generate(root, manifest, output)
        self.assertEqual((output / "keep").read_text(), "preserve")

    def test_manifest_changes_during_xcodegen_do_not_change_consumer_identity(self):
        root, manifest, *_ = self.inputs()
        original = manifest.read_bytes()
        original_hash = hashlib.sha256(original).hexdigest()
        run = subprocess.run

        def change_manifest(*args, **kwargs):
            payload = json.loads(original)
            payload["sourceCommit"] = "f" * 40
            manifest.write_text(json.dumps(payload))
            return run(*args, **kwargs)

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "consumer"
            with patch("tools.generate_mlxvlm_binary_consumer.subprocess.run", side_effect=change_manifest):
                generate(root, manifest, output)
            self.assertNotEqual(manifest.read_bytes(), original)
            self.assertEqual((output / "artifact-manifest.json").read_bytes(), original)
            receipt = json.loads((output / "consumer-inputs.json").read_text())
            self.assertEqual(receipt["artifactManifestSHA256"], original_hash)
            self.assertEqual(receipt["sourceCommit"], json.loads(original)["sourceCommit"])
            self.assertIn('"artifactManifestSHA256": "' + original_hash + '"',
                          (output / "ConsumerApp.swift").read_text())

    def test_empty_output_created_during_xcodegen_is_preserved(self):
        root, manifest, *_ = self.inputs()
        run = subprocess.run
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "consumer"
            created = []

            def create_output(*args, **kwargs):
                result = run(*args, **kwargs)
                output.mkdir()
                created.append(output.stat())
                return result

            with patch("tools.generate_mlxvlm_binary_consumer.subprocess.run", side_effect=create_output):
                with self.assertRaises((FileExistsError, ValueError)):
                    generate(root, manifest, output)
            self.assertEqual((output.stat().st_dev, output.stat().st_ino),
                             (created[0].st_dev, created[0].st_ino))
            self.assertEqual(list(output.iterdir()), [])
            self.assertEqual(list(Path(directory).glob(".mlxvlm-binary-consumer-*")), [])

    def test_manifest_validation_and_copy_use_the_same_frozen_bytes(self):
        from tools.validate_mlxvlm_binary_artifact import validate_manifest
        root, manifest, *_ = self.inputs()
        original = manifest.read_bytes()

        def change_original(path, artifact_root):
            payload = json.loads(original)
            payload["sourceCommit"] = "f" * 40
            manifest.write_text(json.dumps(payload))
            return validate_manifest(path, artifact_root)

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "consumer"
            with patch("tools.generate_mlxvlm_binary_consumer.validate_manifest", side_effect=change_original):
                generate(root, manifest, output)
            self.assertEqual((output / "artifact-manifest.json").read_bytes(), original)

    def test_exclusive_publish_preserves_existing_empty_directory(self):
        from tools.generate_mlxvlm_binary_consumer import _publish_exclusive
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory) / "staged"
            stage.mkdir()
            (stage / "new-content").write_text("generated")
            output = Path(directory) / "consumer"
            output.mkdir()
            original = output.stat()
            with self.assertRaises(FileExistsError):
                _publish_exclusive(stage, output)
            self.assertEqual((output.stat().st_dev, output.stat().st_ino),
                             (original.st_dev, original.st_ino))
            self.assertEqual(list(output.iterdir()), [])
            self.assertEqual((stage / "new-content").read_text(), "generated")

    def test_exclusive_publish_preserves_existing_nonempty_directory(self):
        from tools.generate_mlxvlm_binary_consumer import _publish_exclusive
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory) / "staged"
            stage.mkdir()
            output = Path(directory) / "consumer"
            output.mkdir()
            (output / "keep").write_text("concurrent writer")
            with self.assertRaises(FileExistsError):
                _publish_exclusive(stage, output)
            self.assertEqual((output / "keep").read_text(), "concurrent writer")
            self.assertTrue(stage.is_dir())

    def test_unavailable_exclusive_api_never_falls_back_to_overwrite(self):
        from tools.generate_mlxvlm_binary_consumer import _publish_exclusive
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory) / "staged"
            stage.mkdir()
            output = Path(directory) / "consumer"
            with patch("tools.generate_mlxvlm_binary_consumer.ctypes.CDLL", return_value=object()):
                with self.assertRaises(OSError) as caught:
                    _publish_exclusive(stage, output)
            self.assertEqual(caught.exception.errno, errno.ENOTSUP)
            self.assertTrue(stage.is_dir())
            self.assertFalse(output.exists())

    def test_exclusive_publish_preserves_file_and_symlink_destinations(self):
        from tools.generate_mlxvlm_binary_consumer import _publish_exclusive
        for kind in ("file", "directory-symlink", "dangling-symlink"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                stage = root / "staged"
                stage.mkdir()
                (stage / "new").write_bytes(b"generated\0\xff")
                output = root / "consumer"
                target = root / "target"
                if kind == "file":
                    output.write_bytes(b"original")
                else:
                    if kind == "directory-symlink":
                        target.mkdir()
                        (target / "keep").write_bytes(b"original")
                    output.symlink_to(target)
                original = output.lstat()
                with self.assertRaises(FileExistsError):
                    _publish_exclusive(stage, output)
                current = output.lstat()
                self.assertEqual((original.st_dev, original.st_ino),
                                 (current.st_dev, current.st_ino))
                self.assertEqual((stage / "new").read_bytes(), b"generated\0\xff")
                if kind == "file":
                    self.assertEqual(output.read_bytes(), b"original")
                else:
                    self.assertEqual(output.readlink(), target)
                    if kind == "directory-symlink":
                        self.assertEqual((target / "keep").read_bytes(), b"original")
                    else:
                        self.assertFalse(target.exists())

    def test_exclusive_publish_competing_directories_have_one_winner(self):
        from tools.generate_mlxvlm_binary_consumer import _publish_exclusive
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stages = [root / "first", root / "second"]
            output = root / "consumer"
            for stage in stages:
                stage.mkdir()
                (stage / "owner").write_bytes(stage.name.encode())
            barrier = threading.Barrier(2)

            def publish(stage):
                barrier.wait()
                try:
                    _publish_exclusive(stage, output)
                except FileExistsError:
                    return "exists", stage
                return "published", stage

            with ThreadPoolExecutor(max_workers=2) as executor:
                results = list(executor.map(publish, stages))
            self.assertEqual(sorted(status for status, _ in results),
                             ["exists", "published"])
            winner = next(stage for status, stage in results if status == "published")
            loser = next(stage for status, stage in results if status == "exists")
            self.assertFalse(winner.exists())
            self.assertEqual((output / "owner").read_bytes(), winner.name.encode())
            self.assertEqual((loser / "owner").read_bytes(), loser.name.encode())

    def test_missing_simulator_and_raw_unbundled_resources_are_rejected(self):
        root, manifest, artifact, _ = self.inputs()
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["slices"] = payload["artifacts"][0]["slices"][:1]
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "simulator"):
            generate(root, manifest, root / "consumer")

    def test_raw_resource_cannot_masquerade_as_swiftpm_resource_bundle(self):
        root, manifest, *_ = self.inputs()
        payload = json.loads(manifest.read_text())
        resource = payload["resources"][0]
        original = root / resource["path"]
        target = root / "resources" / original.name
        original.rename(target)
        resource["path"] = str(target.relative_to(root))
        manifest.write_text(json.dumps(payload))
        with self.assertRaisesRegex(ValueError, "bundle"):
            generate(root, manifest, root / "consumer")

    def test_generator_failure_leaves_no_partial_consumer(self):
        root, manifest, *_ = self.inputs()
        output = root / "consumer"
        with patch("tools.generate_mlxvlm_binary_consumer.subprocess.run", side_effect=subprocess.CalledProcessError(1, "xcodegen")):
            with self.assertRaises(subprocess.CalledProcessError):
                generate(root, manifest, output)
        self.assertFalse(output.exists())

    def test_absolute_internal_artifact_symlink_does_not_escape_relocated_consumer(self):
        root, manifest, artifact, _ = self.inputs()
        source = artifact / "ios-arm64/ODAIKitAppleMLXVLM.framework/ODAIKitAppleMLXVLM"
        copy = source.with_name("real-binary")
        source.rename(copy)
        source.symlink_to(copy)
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["sha256"] = fixtures.sha256_tree(artifact)
        manifest.write_text(json.dumps(payload))
        output = root / "consumer"
        with self.assertRaisesRegex(ValueError, "symlink"):
            generate(root, manifest, output)
        self.assertFalse(output.exists())

    def test_resource_bundle_original_path_is_preserved_in_copy(self):
        root, manifest, *_ = self.inputs()
        original = root / "resources/OnDeviceAIKitAppleMLX_ODAIKitAppleMLXVLM.bundle"
        target = root / "other-assets" / original.name
        target.parent.mkdir()
        original.rename(target)
        payload = json.loads(manifest.read_text())
        payload["resources"][0]["path"] = str((target / "smolvlm2-model.json").relative_to(root))
        manifest.write_text(json.dumps(payload))
        output = root / "consumer"
        generate(root, manifest, output)
        self.assertTrue((output / "artifacts/other-assets" / original.name / "smolvlm2-model.json").is_file())
        from tools.validate_mlxvlm_binary_artifact import validate_manifest
        validate_manifest(output / "artifact-manifest.json", output / "artifacts")

    def test_noncanonical_artifact_paths_are_rejected_before_copy(self):
        root, manifest, *_ = self.inputs()
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["path"] = "xcframeworks/../" + payload["artifacts"][0]["path"]
        manifest.write_text(json.dumps(payload))
        output = root / "consumer"
        with self.assertRaisesRegex(ValueError, "canonical"):
            generate(root, manifest, output)
        self.assertFalse(output.exists())

    def test_invalid_manifest_shape_uses_the_validator_error_contract(self):
        root, manifest, *_ = self.inputs()
        manifest.write_text("[]")
        output = root / "consumer"
        with self.assertRaisesRegex(ValueError, "manifest must be an object"):
            generate(root, manifest, output)
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
