import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tools.package_mlxvlm_binary import validate_snapshot, package, copy_public_c_module


class PackagingInputsTests(unittest.TestCase):
    def fixture(self, root):
        path = root / "snapshot"
        source = path / "packages/apple/Package.swift"
        source.parent.mkdir(parents=True)
        source.write_text("// committed SDK fixture")
        data = source.read_bytes()
        relative = "packages/apple/Package.swift"
        digest = hashlib.sha256(relative.encode() + b"\0" + data + b"\0").hexdigest()
        payload = {"kind": "apple-mlxvlm-committed-source-snapshot", "schemaVersion": 1,
                   "sourceCommit": "a" * 40, "lockSHA256": "e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858",
                   "treeSHA256": digest, "files": [{"path": relative, "sha256": hashlib.sha256(data).hexdigest()}]}
        (path / "source-inventory.json").write_text(json.dumps(payload))
        return path, source

    def test_committed_source_drift_fails_before_build_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            snapshot, source = self.fixture(root)
            source.write_text("uncommitted drift")
            with self.assertRaisesRegex(ValueError, "snapshot.*drift"):
                validate_snapshot(snapshot)

    def test_snapshot_escape_fails_before_reading_external_input(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            snapshot, source = self.fixture(root)
            external = root / "outside.swift"
            external.write_bytes(source.read_bytes())
            source.unlink(); source.symlink_to(external)
            with self.assertRaisesRegex(ValueError, "snapshot.*symlink"):
                validate_snapshot(snapshot)

    def test_existing_output_is_preserved_without_invoking_xcode(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); output = root / "keep"
            output.mkdir(); (output / "sentinel").write_text("preserve")
            with self.assertRaisesRegex(ValueError, "output exists"):
                package(root / "absent", root / "absent-cache", output)
            self.assertEqual((output / "sentinel").read_text(), "preserve")

    def test_uninventoried_package_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            snapshot, source = self.fixture(Path(directory))
            (source.parent / "extra.swift").write_text("untracked source")
            with self.assertRaisesRegex(ValueError, "snapshot.*inventory"):
                validate_snapshot(snapshot)

    def test_generated_clog_modulemap_uses_the_shipped_header(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "headers"
            source.mkdir(); (source / "cLogSupport.h").write_text("void probe(void);")
            output = root / "CModules/cLogSupport"
            copy_public_c_module(source, "cLogSupport", output)
            self.assertEqual((output / "module.modulemap").read_text(),
                             'module cLogSupport { header "cLogSupport.h" export * }\n')
            self.assertEqual((output / "cLogSupport.h").read_text(), "void probe(void);")
