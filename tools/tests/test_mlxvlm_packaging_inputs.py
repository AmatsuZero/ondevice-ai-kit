import hashlib
import json
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest.mock import patch

from tools import package_mlxvlm_binary as packager
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


class NativeCacheInputsTests(unittest.TestCase):
    def git(self, repo, *args):
        return subprocess.check_output(
            ["rtk", "proxy", "git", "-C", str(repo), "-c", "core.hooksPath=/dev/null",
             "-c", "commit.gpgsign=false", *args], text=True, stderr=subprocess.PIPE).strip()

    def commit(self, repo):
        self.git(repo, "add", ".")
        self.git(repo, "-c", "user.name=Packaging Test", "-c", "user.email=packaging@example.invalid",
                 "commit", "-qm", "fixture")
        return self.git(repo, "rev-parse", "HEAD")

    def fixture(self, root):
        root = root.resolve()
        cache = root / "SourcePackages"
        mlx = cache / "checkouts/mlx-swift"
        mlx.mkdir(parents=True)
        self.git(mlx, "init", "-q")
        (mlx / "Package.swift").write_text('// fixed package fixture\n')
        for name in ("mlx", "mlx-c"):
            native = mlx / "Source/Cmlx" / name
            native.mkdir(parents=True)
            self.git(native, "init", "-q")
            (native / "probe.cpp").write_text('int fixed_input() { return 1; }\n')
            self.commit(native)
        revision = self.commit(mlx)
        snapshot = root / "snapshot"
        lock = snapshot / "packages/apple-mlx/Package.resolved"
        lock.parent.mkdir(parents=True)
        lock.write_text(json.dumps({"pins": [{"identity": "mlx-swift", "state": {"revision": revision}}]}))
        candidate = root / "candidate"
        candidate.mkdir()
        manifest = candidate / "manifest.json"
        manifest.write_text(json.dumps({"patchSha256": {}, "files": []}))
        return cache, snapshot, candidate, mlx

    def validate(self, cache, snapshot, candidate):
        # Only replace the pinned identities with this local fixture's hashes;
        # real Git/worktree inspection and the complete validator still run.
        with patch.multiple(packager, CANDIDATE=candidate,
                            CANDIDATE_SHA256=hashlib.sha256((candidate / "manifest.json").read_bytes()).hexdigest(),
                            EXPECTED_LOCK_SHA256=hashlib.sha256((snapshot / "packages/apple-mlx/Package.resolved").read_bytes()).hexdigest()):
            return packager.validate_cache(cache, snapshot)

    def test_clean_native_gitlinks_are_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            cache, snapshot, candidate, _ = self.fixture(Path(directory))
            self.assertEqual(len(self.validate(cache, snapshot, candidate)["pins"]), 1)

    def test_untracked_native_source_is_rejected(self):
        for name in ("mlx", "mlx-c"):
            with self.subTest(native=name), tempfile.TemporaryDirectory() as directory:
                cache, snapshot, candidate, mlx = self.fixture(Path(directory))
                (mlx / "Source/Cmlx" / name / "extra.cpp").write_text('int injected_input() { return 2; }\n')
                with self.assertRaises(ValueError):
                    self.validate(cache, snapshot, candidate)


class ProjectLicenseNoticesTests(unittest.TestCase):
    def fixture(self, root):
        distribution = root / "distribution"
        distribution.mkdir()
        cache = root / "SourcePackages"
        license_path = cache / "checkouts/eventsource/LICENSE"
        license_path.parent.mkdir(parents=True)
        license_path.write_bytes(b"Original third-party MIT license fixture\n")
        for name in ("metal-cpp/LICENSE.txt", "fmt/LICENSE", "mlx-c/LICENSE", "json/LICENSE.MIT", "mlx/LICENSE"):
            path = cache / "checkouts/mlx-swift/Source/Cmlx" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"Original native dependency license fixture\n")
        snapshot = root / "snapshot"
        (snapshot / "packages").mkdir(parents=True)
        lock = {"pins": [{"identity": "eventsource", "location": "https://example.invalid/eventsource",
                          "state": {"revision": "a" * 40}}]}
        return distribution, cache, lock, snapshot

    def test_project_license_is_copied_hashed_and_attributed_without_relicensing_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            distribution, cache, lock, snapshot = self.fixture(Path(directory))
            (snapshot / "packages/LICENSE").write_bytes(b"Internal-only project fixture\n")
            attributions, notices = packager._notices(distribution, cache, lock, "b" * 40, snapshot)
            copied = distribution / "notices/ondevice-ai-kit/LICENSE"
            self.assertTrue(copied.is_file(), "project license must ship in notices")
            self.assertEqual(copied.read_bytes(), b"Internal-only project fixture\n")
            self.assertEqual((distribution / "notices/eventsource/LICENSE").read_bytes(),
                             b"Original third-party MIT license fixture\n")
            self.assertEqual(next(a for a in attributions if a["module"] == "EventSource")["license"], "MIT")
            sdk = [a for a in attributions if a["revision"] == "b" * 40]
            self.assertEqual(len(sdk), 4)
            self.assertTrue(all(a["license"] == "See notices/ondevice-ai-kit/LICENSE" for a in sdk))
            self.assertIn({"path": "notices/ondevice-ai-kit/LICENSE",
                           "sha256": hashlib.sha256(b"Internal-only project fixture\n").hexdigest()}, notices)
            self.assertNotIn("no SDK source redistribution license", (distribution / "notices/LOCAL-VALIDATION-ONLY.txt").read_text())

    def test_legacy_snapshot_does_not_borrow_the_current_repository_license(self):
        with tempfile.TemporaryDirectory() as directory:
            distribution, cache, lock, snapshot = self.fixture(Path(directory))
            attributions, _ = packager._notices(distribution, cache, lock, "b" * 40, snapshot)
            self.assertFalse((distribution / "notices/ondevice-ai-kit/LICENSE").exists())
            sdk = [a for a in attributions if a["revision"] == "b" * 40]
            self.assertEqual(len(sdk), 4)
            self.assertTrue(all(a["license"] == "Project license unspecified; local validation only" for a in sdk))
