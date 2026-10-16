import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("fetch_vlm_asset", Path(__file__).parents[1] / "fetch_vlm_asset.py")
asset = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(asset)


class AssetTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.source = self.root / "source"
        self.source.mkdir()
        (self.source / "config.json").write_bytes(b"fixture")
        self.manifest = {
            "status": "verified",
            "files": [{"path": "config.json", "bytes": 7,
                       "sha256": hashlib.sha256(b"fixture").hexdigest()}],
        }

    def test_atomic_publish(self):
        destination = self.root / "published"
        result = asset.fetch_vlm_asset(self.manifest, destination, self.source)
        self.assertEqual(result.directory, destination)
        self.assertEqual((destination / "config.json").read_bytes(), b"fixture")

    def test_partial_asset_not_published(self):
        self.manifest["files"].append({"path": "missing.json", "bytes": 1,
                                       "sha256": hashlib.sha256(b"x").hexdigest()})
        destination = self.root / "published"
        with self.assertRaises(FileNotFoundError):
            asset.fetch_vlm_asset(self.manifest, destination, self.source)
        self.assertFalse(destination.exists())

    def test_wrong_hash_not_published(self):
        self.manifest["files"][0]["sha256"] = "0" * 64
        destination = self.root / "published"
        with self.assertRaisesRegex(ValueError, "sha256"):
            asset.fetch_vlm_asset(self.manifest, destination, self.source)
        self.assertFalse(destination.exists())

    def test_candidate_rejected(self):
        self.manifest["status"] = "candidate"
        with self.assertRaisesRegex(ValueError, "verified"):
            asset.fetch_vlm_asset(self.manifest, self.root / "published", self.source)


if __name__ == "__main__":
    unittest.main()
