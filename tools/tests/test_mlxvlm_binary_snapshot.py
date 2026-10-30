import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from tools.prepare_mlxvlm_binary_snapshot import snapshot


ROOT = Path(__file__).resolve().parents[2]


class MLXVLMBinarySnapshotTests(unittest.TestCase):
    def make_repo(self, directory: Path) -> tuple[Path, str]:
        repo = directory / "repo"
        repo.mkdir()
        subprocess.run(["git", "init", "-q", str(repo)], check=True)
        sources = {
            "apple/Package.swift": b"committed base manifest\n",
            "apple/Sources/Base.swift": b"committed base source\n",
            "apple-mlx/Package.swift": b"committed enhanced manifest\n",
            "apple-mlx/Sources/Enhanced.swift": b"committed enhanced source\n",
            "apple-mlx/Package.resolved": (ROOT / "apple-mlx/Package.resolved").read_bytes(),
        }
        for name, data in sources.items():
            path = repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        subprocess.run(["git", "-C", str(repo), "add", "apple", "apple-mlx"], check=True)
        subprocess.run([
            "git", "-C", str(repo), "-c", "user.name=Snapshot Test",
            "-c", "user.email=snapshot@example.invalid", "commit", "-qm", "fixture"
        ], check=True)
        commit = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
        return repo, commit

    def test_snapshot_uses_committed_bytes_and_excludes_untracked_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo, commit = self.make_repo(root)
            tracked = repo / "apple-mlx/Sources/Enhanced.swift"
            tracked.write_bytes(b"dirty enhanced source\n")
            untracked = repo / "apple-mlx/Sources/Untracked.swift"
            untracked.write_bytes(b"must not ship\n")
            output = root / "snapshot"
            self.assertEqual(snapshot(repo, commit, output), output)
            self.assertEqual((output / "packages/apple-mlx/Sources/Enhanced.swift").read_bytes(),
                             b"committed enhanced source\n")
            self.assertFalse((output / "packages/apple-mlx/Sources/Untracked.swift").exists())
            self.assertEqual(tracked.read_bytes(), b"dirty enhanced source\n")
            self.assertEqual(untracked.read_bytes(), b"must not ship\n")
            inventory = json.loads((output / "source-inventory.json").read_text())
            self.assertEqual(inventory["sourceCommit"], commit)
            self.assertIsNone(inventory["diagnosticPatchManifest"])
            self.assertEqual(inventory["lockSHA256"], hashlib.sha256(
                (output / "packages/apple-mlx/Package.resolved").read_bytes()
            ).hexdigest())
            self.assertEqual(len(inventory["files"]), 5)
            for entry in inventory["files"]:
                self.assertEqual(entry["sha256"], hashlib.sha256(
                    (output / entry["path"]).read_bytes()
                ).hexdigest())

    def test_lock_mismatch_is_rejected_before_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo, _ = self.make_repo(root)
            (repo / "apple-mlx/Package.resolved").write_bytes(b"wrong lock\n")
            subprocess.run(["git", "-C", str(repo), "add", "apple-mlx/Package.resolved"], check=True)
            subprocess.run([
                "git", "-C", str(repo), "-c", "user.name=Snapshot Test",
                "-c", "user.email=snapshot@example.invalid", "commit", "-qm", "wrong lock"
            ], check=True)
            commit = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
            output = root / "snapshot"
            with self.assertRaisesRegex(ValueError, "lock SHA-256"):
                snapshot(repo, commit, output)
            self.assertFalse(output.exists())

    def test_existing_output_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo, commit = self.make_repo(root)
            output = root / "snapshot"
            output.mkdir()
            sentinel = output / "sentinel"
            sentinel.write_text("keep")
            with self.assertRaisesRegex(ValueError, "output"):
                snapshot(repo, commit, output)
            self.assertEqual(sentinel.read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
