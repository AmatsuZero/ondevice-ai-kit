import hashlib
import json
from pathlib import Path
import plistlib
import shutil
import unittest
from unittest.mock import patch
import zipfile

from tools.tests import test_mlxvlm_binary_artifact as fixtures
from tools.tests import test_mlxvlm_binary_consumer as consumer_fixtures
from tools.validate_mlxvlm_binary_package import validate_package


class MLXVLMBinaryPackageTests(unittest.TestCase):
    make_fixture = fixtures.MLXVLMBinaryArtifactTests.make_fixture

    def setUp(self):
        # Only Mach-O inspection is substituted: fixtures are not built binaries.
        probe = patch("tools.inspect_mlxvlm_xcframework._inspect_binary", side_effect=lambda p: {
            "architectures": ["arm64"], "minimumOS": "18.0",
            "platforms": {"7" if "simulator" in str(p) else "2"},
        })
        probe.start()
        self.addCleanup(probe.stop)

    def inputs(self):
        root, manifest, artifact, _ = self.make_fixture()
        # SwiftPM decodes the complete XCFramework metadata, beyond our earlier
        # validator fixtures. Payloads remain deliberate non-Mach-O substitutes.
        metadata = plistlib.loads((artifact / "Info.plist").read_bytes())
        metadata.update(CFBundlePackageType="XFWK", XCFrameworkFormatVersion="1.0")
        for library in metadata["AvailableLibraries"]:
            library["SupportedArchitectures"] = ["arm64"]
        (artifact / "Info.plist").write_bytes(plistlib.dumps(metadata))
        payload = json.loads(manifest.read_text())
        payload["artifacts"][0]["sha256"] = fixtures.sha256_tree(artifact)
        manifest.write_text(json.dumps(payload))
        archive = root / "binary.zip"
        self.write_zip(archive, artifact)
        return root, manifest, artifact, archive

    def write_zip(self, archive, artifact, prefix=""):
        with zipfile.ZipFile(archive, "w") as output:
            for child in sorted(artifact.rglob("*")):
                if child.is_file():
                    output.write(child, prefix + str(child.relative_to(artifact.parent)))

    def validate(self, root, manifest, artifact, archive):
        return validate_package(root, manifest, {str(artifact.relative_to(root)): archive})

    def test_exact_root_zip_checksum_and_local_source_free_package_graph(self):
        root, manifest, artifact, archive = self.inputs()
        before = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in root.rglob("*") if p.is_file()}
        report = self.validate(root, manifest, artifact, archive)
        self.assertEqual(report["localGraphStatus"], "validated")
        self.assertEqual(report["localZipResolutionStatus"], "validated")
        self.assertEqual(report["binaryTargets"], ["ODAIKitAppleMLXVLM"])
        self.assertEqual(report["archives"][0]["checksum"], hashlib.sha256(archive.read_bytes()).hexdigest())
        self.assertEqual(report["consumerBuildStatus"], "not-run")
        self.assertEqual(report["resourceRuntimeStatus"], "not-run")
        self.assertEqual(report["remoteArchiveResolutionStatus"], "not-run")
        self.assertEqual(before, {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in root.rglob("*") if p.is_file()})

    def test_nested_xcframework_archive_is_rejected(self):
        root, manifest, artifact, archive = self.inputs()
        self.write_zip(archive, artifact, "wrapper/")
        with self.assertRaisesRegex(ValueError, "archive root"):
            self.validate(root, manifest, artifact, archive)

    def test_receipt_checksum_binds_the_archive_snapshot_consumed_by_swiftpm(self):
        root, manifest, artifact, archive = self.inputs()
        original_copy = shutil.copyfile
        consumed = []
        def changing_copy(source, destination, *args, **kwargs):
            if Path(source).resolve() == archive.resolve():
                # Same extracted content, different final ZIP bytes between
                # an earlier read and the snapshot consumed by SwiftPM.
                with zipfile.ZipFile(source, "a") as output:
                    output.comment = b"changed before snapshot"
            result = original_copy(source, destination, *args, **kwargs)
            consumed.append(hashlib.sha256(Path(destination).read_bytes()).hexdigest())
            return result
        with patch("tools.validate_mlxvlm_binary_package.shutil.copyfile", side_effect=changing_copy):
            report = self.validate(root, manifest, artifact, archive)
        self.assertEqual(report["archives"][0]["checksum"], consumed[0])

    def test_all_closure_modules_resolve_from_separate_local_archives(self):
        root, manifest, *_ = consumer_fixtures.MLXVLMBinaryConsumerTests.inputs(self)
        payload = json.loads(manifest.read_text())
        archives = {}
        for entry in payload["artifacts"]:
            artifact = root / entry["path"]
            metadata = plistlib.loads((artifact / "Info.plist").read_bytes())
            metadata.update(CFBundlePackageType="XFWK", XCFrameworkFormatVersion="1.0")
            for library in metadata["AvailableLibraries"]:
                library["SupportedArchitectures"] = ["arm64"]
            (artifact / "Info.plist").write_bytes(plistlib.dumps(metadata))
            entry["sha256"] = fixtures.sha256_tree(artifact)
            archive = root / (entry["modules"][0] + ".zip")
            self.write_zip(archive, artifact)
            archives[entry["path"]] = archive
        manifest.write_text(json.dumps(payload))
        report = validate_package(root, manifest, archives)
        self.assertEqual(set(report["binaryTargets"]), {"ODAIKitAppleMLXVLM", "ODAIKitMultimodal"})
        self.assertEqual(len(report["archives"]), 2)
        self.assertEqual(report["localZipResolutionStatus"], "validated")

    def test_archive_bytes_must_match_the_validated_artifact(self):
        root, manifest, artifact, archive = self.inputs()
        # Repack a stale binary after the artifact manifest was frozen.
        with zipfile.ZipFile(archive) as old:
            entries = {i.filename: old.read(i) for i in old.infolist()}
        key = next(k for k in entries if k.endswith("/ODAIKitAppleMLXVLM"))
        entries[key] = b"x" * len(entries[key])
        with zipfile.ZipFile(archive, "w") as output:
            for name, data in entries.items():
                output.writestr(name, data)
        with self.assertRaisesRegex(ValueError, "content mismatch"):
            self.validate(root, manifest, artifact, archive)

    def test_missing_closure_archive_is_rejected(self):
        root, manifest, *_ = self.inputs()
        with self.assertRaisesRegex(ValueError, "archive coverage"):
            validate_package(root, manifest, {})

    def test_zip_traversal_and_extra_sources_are_rejected(self):
        for name in ("../escape", "SourcePackages/checkouts/source.swift"):
            with self.subTest(name=name):
                root, manifest, artifact, archive = self.inputs()
                with zipfile.ZipFile(archive, "a") as output:
                    output.writestr(name, "unwanted source")
                with self.assertRaises(ValueError):
                    self.validate(root, manifest, artifact, archive)
                self.assertFalse((root.parent / "escape").exists())

    def test_zip_missing_entry_and_duplicate_entry_are_rejected(self):
        root, manifest, artifact, archive = self.inputs()
        with zipfile.ZipFile(archive) as old:
            entries = [(i.filename, old.read(i)) for i in old.infolist()]
        with zipfile.ZipFile(archive, "w") as output:
            for name, data in entries[1:]:
                output.writestr(name, data)
        with self.assertRaisesRegex(ValueError, "inventory"):
            self.validate(root, manifest, artifact, archive)
        self.write_zip(archive, artifact)
        with self.assertWarns(UserWarning):
            with zipfile.ZipFile(archive, "a") as output:
                output.writestr(*entries[0])
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.validate(root, manifest, artifact, archive)


if __name__ == "__main__":
    unittest.main()
