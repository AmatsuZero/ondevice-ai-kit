"""Behavior tests for exact-revision, atomic F09 compatibility preparation."""
import hashlib
import importlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


def digest(data):
    return hashlib.sha256(data).hexdigest()


class CompatibilityOverlayTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.dependencies = self.root / "dependencies"
        self.candidate = self.root / "candidate"
        self.candidate.mkdir()
        self.revisions = {}
        for name in ("swift-collections", "mlx-swift-lm"):
            repo = self.dependencies / name
            repo.mkdir(parents=True)
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            (repo / "source.swift").write_text("let value = 1\n")
            (repo / "keep.txt").write_text("unchanged\n")
            subprocess.run(["git", "-C", str(repo), "add", "."], check=True)
            subprocess.run(["git", "-C", str(repo), "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false", "commit", "-qm", "fixture"], check=True)
            self.revisions[name] = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
        pins = [{"identity": name, "state": {"revision": revision, "version": "1.0.0"}} for name, revision in self.revisions.items()]
        pins.append({"identity": "mlx-swift", "state": {"revision": "a" * 40, "version": "0.32.3"}})
        self.lock = self.root / "Package.resolved"
        self.lock.write_text(json.dumps({"version": 3, "pins": pins}))
        patch = b"--- a/source.swift\n+++ b/source.swift\n@@ -1 +1 @@\n-let value = 1\n+let value = 2\n"
        self.manifest = {
            "schemaVersion": 1, "status": "draft-not-adopted",
            "productionLockSha256": digest(self.lock.read_bytes()),
            "revisions": self.revisions,
            "runtimeBinding": {"mlx-swift": "0.32.3@" + "a" * 40},
            "files": [{"package": name, "path": "source.swift", "originalSha256": digest(b"let value = 1\n"), "patchedSha256": digest(b"let value = 2\n")} for name in self.revisions],
            "patchSha256": {name: digest(patch) for name in self.revisions},
        }
        for name in self.revisions:
            (self.candidate / f"{name}.patch").write_bytes(patch)
        self.save_manifest()
        self.output = self.root / "prepared"

    def save_manifest(self):
        (self.candidate / "manifest.json").write_text(json.dumps(self.manifest))

    def apply(self):
        module = importlib.import_module("tools.apply_mlxvlm_compatibility_overlay")
        return module.apply_candidate(self.candidate, self.lock, self.dependencies, self.output)

    def commit_source(self, name):
        repo = self.dependencies / name
        subprocess.run(["git", "-C", str(repo), "add", "."], check=True)
        subprocess.run(["git", "-C", str(repo), "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false", "commit", "-qm", "updated fixture"], check=True)
        revision = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
        self.revisions[name] = revision
        lock = json.loads(self.lock.read_text())
        next(pin for pin in lock["pins"] if pin["identity"] == name)["state"]["revision"] = revision
        self.lock.write_text(json.dumps(lock))
        self.manifest["productionLockSha256"] = digest(self.lock.read_bytes())
        self.save_manifest()

    def test_prepares_committed_bytes_and_provenance_without_mutating_inputs(self):
        # Ignoring Git objects would copy the dirty/bogus source and fail the patch.
        source = self.dependencies / "swift-collections/source.swift"
        source.write_text("dirty workspace value\n")
        (source.parent / "untracked.swift").write_text("do not copy\n")
        self.apply()
        for name in self.revisions:
            self.assertEqual((self.output / name / "source.swift").read_text(), "let value = 2\n")
            self.assertEqual((self.output / name / "keep.txt").read_text(), "unchanged\n")
            self.assertFalse((self.output / name / ".git").exists())
        self.assertEqual(source.read_text(), "dirty workspace value\n")
        self.assertFalse((self.output / "swift-collections/untracked.swift").exists())
        receipt = json.loads((self.output / "overlay-receipt.json").read_text())
        self.assertEqual(receipt["policyStatus"], "draft-not-adopted")
        self.assertEqual(receipt["buildStatus"], "not-run")
        self.assertEqual(receipt["revisions"], self.revisions)
        self.assertEqual(receipt["manifestSha256"], digest((self.candidate / "manifest.json").read_bytes()))

    def test_rejects_lock_and_patch_drift_before_output(self):
        for kind in ("lock", "patch"):
            with self.subTest(kind=kind):
                target = self.lock if kind == "lock" else self.candidate / "mlx-swift-lm.patch"
                original = target.read_bytes()
                target.write_bytes(original + b"\n")
                with self.assertRaisesRegex(ValueError, "SHA-256"):
                    self.apply()
                self.assertFalse(self.output.exists())
                target.write_bytes(original)

    def test_rejects_input_and_output_hash_drift_and_cleans_stage(self):
        for key in ("originalSha256", "patchedSha256"):
            with self.subTest(key=key):
                original = self.manifest["files"][0][key]
                self.manifest["files"][0][key] = "0" * 64
                self.save_manifest()
                with self.assertRaisesRegex(ValueError, "SHA-256"):
                    self.apply()
                self.assertFalse(self.output.exists())
                self.assertEqual(list(self.root.glob(".mlxvlm-overlay-*")), [])
                self.manifest["files"][0][key] = original

    def test_rejects_runtime_and_revision_mismatch_even_with_matching_lock_hash(self):
        for field in ("runtimeBinding", "revisions"):
            with self.subTest(field=field):
                original = dict(self.manifest[field])
                self.manifest[field][next(iter(original))] = "0.32.4@" + "b" * 40 if field == "runtimeBinding" else "b" * 40
                self.save_manifest()
                with self.assertRaisesRegex(ValueError, "binding"):
                    self.apply()
                self.assertFalse(self.output.exists())
                self.manifest[field] = original

    def test_existing_output_and_input_overlap_are_preserved(self):
        self.output.mkdir()
        (self.output / "keep").write_text("preserve")
        with self.assertRaisesRegex(ValueError, "exists"):
            self.apply()
        self.assertEqual((self.output / "keep").read_text(), "preserve")
        self.output = self.dependencies / "new-output"
        with self.assertRaisesRegex(ValueError, "overlap"):
            self.apply()
        self.assertFalse(self.output.exists())

    def test_rejects_undeclared_patch_paths_and_path_escape(self):
        self.manifest["files"][0]["path"] = "../escape.swift"
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "path"):
            self.apply()
        self.assertFalse(self.output.exists())
        self.manifest["files"][0]["path"] = "source.swift"
        patch_path = self.candidate / "swift-collections.patch"
        data = patch_path.read_bytes().replace(b"source.swift", b"keep.txt")
        patch_path.write_bytes(data)
        self.manifest["patchSha256"]["swift-collections"] = digest(data)
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "declared"):
            self.apply()
        self.assertFalse(self.output.exists())

    def test_patch_failure_does_not_publish_partial_output(self):
        patch_path = self.candidate / "swift-collections.patch"
        data = patch_path.read_bytes().replace(b"-let value = 1", b"-missing source line")
        patch_path.write_bytes(data)
        self.manifest["patchSha256"]["swift-collections"] = digest(data)
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "patch failed"):
            self.apply()
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.root.glob(".mlxvlm-overlay-*")), [])

    def test_export_attributes_cannot_drop_committed_dependency_files(self):
        # git archive honors export-ignore: those bytes are still needed to build.
        repo = self.dependencies / "swift-collections"
        (repo / ".gitattributes").write_text("keep.txt export-ignore\n")
        self.commit_source("swift-collections")
        self.apply()
        self.assertEqual((self.output / "swift-collections/keep.txt").read_text(), "unchanged\n")

    def test_committed_symlink_is_rejected_without_copy_or_source_mutation(self):
        repo = self.dependencies / "swift-collections"
        (repo / "link").symlink_to("keep.txt")
        self.commit_source("swift-collections")
        with self.assertRaisesRegex(ValueError, "symlink"):
            self.apply()
        self.assertFalse(self.output.exists())
        self.assertTrue((repo / "link").is_symlink())

    def test_explicit_committed_internal_file_link_is_preserved_with_link_provenance(self):
        repo = self.dependencies / "mlx-swift-lm"
        (repo / "CLAUDE.md").symlink_to("keep.txt")
        self.commit_source("mlx-swift-lm")
        self.manifest["symlinks"] = [{"package": "mlx-swift-lm", "path": "CLAUDE.md",
                                      "target": "keep.txt", "sha256": digest(b"keep.txt")}]
        self.save_manifest()
        self.apply()
        link = self.output / "mlx-swift-lm/CLAUDE.md"
        self.assertTrue(link.is_symlink())
        self.assertEqual(str(link.readlink()), "keep.txt")
        self.assertEqual(link.read_text(), "unchanged\n")
        receipt = json.loads((self.output / "overlay-receipt.json").read_text())
        self.assertEqual(receipt["symlinks"], {"swift-collections": {}, "mlx-swift-lm": {"CLAUDE.md": {"target": "keep.txt", "sha256": digest(b"keep.txt")}}})
        self.assertNotIn("CLAUDE.md", receipt["files"]["mlx-swift-lm"])

    def test_declared_link_cannot_hide_changed_target_or_escape(self):
        repo = self.dependencies / "mlx-swift-lm"
        (repo / "CLAUDE.md").symlink_to("keep.txt")
        self.commit_source("mlx-swift-lm")
        for target, hash_value in (("../escape", digest(b"../escape")),
                                   ("source.swift", digest(b"source.swift")),
                                   ("keep.txt", "0" * 64)):
            with self.subTest(target=target, hash_value=hash_value):
                self.manifest["symlinks"] = [{"package": "mlx-swift-lm", "path": "CLAUDE.md", "target": target, "sha256": hash_value}]
                self.save_manifest()
                with self.assertRaises(ValueError):
                    self.apply()
                self.assertFalse(self.output.exists())

    def test_declared_link_target_must_be_a_committed_regular_file(self):
        repo = self.dependencies / "mlx-swift-lm"
        (repo / "CLAUDE.md").symlink_to("missing.txt")
        self.commit_source("mlx-swift-lm")
        self.manifest["symlinks"] = [{"package": "mlx-swift-lm", "path": "CLAUDE.md", "target": "missing.txt", "sha256": digest(b"missing.txt")}]
        self.save_manifest()
        with self.assertRaises(ValueError):
            self.apply()
        self.assertFalse(self.output.exists())

    def test_missing_runtime_pin_cannot_match_empty_state_string(self):
        self.manifest["runtimeBinding"] = {"absent-package": "None@None"}
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "binding"):
            self.apply()
        self.assertFalse(self.output.exists())

    def test_legacy_patch_sections_cannot_hide_undeclared_writes(self):
        patch = self.candidate / "swift-collections.patch"
        data = patch.read_bytes() + b"Index: keep.txt\n*** keep.txt\n--- keep.txt\n***************\n*** 1 ****\n- unchanged\n--- 1 ----\n+ changed\n"
        patch.write_bytes(data)
        self.manifest["patchSha256"]["swift-collections"] = digest(data)
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "declared"):
            self.apply()
        self.assertFalse(self.output.exists())
        self.assertEqual((self.dependencies / "swift-collections/keep.txt").read_text(), "unchanged\n")

    def test_missing_partial_clone_blob_fails_without_lazy_fetch(self):
        repo = self.dependencies / "swift-collections"
        oid = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD:keep.txt"], text=True).strip()
        (repo / ".git/objects" / oid[:2] / oid[2:]).unlink()
        for key, value in (("remote.origin.promisor", "true"), ("remote.origin.url", (self.root / "absent-remote").as_uri()), ("extensions.partialClone", "origin")):
            subprocess.run(["git", "-C", str(repo), "config", key, value], check=True)
        trace = self.root / "git-trace.log"
        with patch.dict(os.environ, {"GIT_TRACE": str(trace)}):
            with self.assertRaises(ValueError):
                self.apply()
        self.assertFalse(self.output.exists())
        self.assertNotIn(" fetch ", trace.read_text())


if __name__ == "__main__":
    unittest.main()
