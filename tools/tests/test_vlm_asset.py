import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from io import BytesIO

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

    def test_candidate_download_resumes_partial_file(self):
        manifest = {"status": "candidate", "modelId": "test/model", "modelRevision": "a" * 40,
                    "files": [{"path": "weight.bin", "bytes": 7,
                               "sourceSha256": hashlib.sha256(b"fixture").hexdigest()}]}
        destination = self.root / "staging"
        destination.mkdir()
        (destination / "weight.bin.part").write_bytes(b"fix")

        class Response(BytesIO):
            status = 206
            headers = {"Content-Range": "bytes 3-6/7"}

        def open_range(request, timeout):
            self.assertEqual(request.get_header("Range"), "bytes=3-")
            return Response(b"ture")

        with patch.object(asset.urllib.request, "urlopen", side_effect=open_range):
            observed = asset.download_candidate(manifest, destination, "https://example.invalid")
        self.assertEqual(observed["weight.bin"]["bytes"], 7)
        self.assertEqual((destination / "weight.bin").read_bytes(), b"fixture")
        self.assertFalse((destination / "weight.bin.part").exists())

    def test_candidate_download_rejects_wrong_resume_range(self):
        manifest = {"status": "candidate", "modelId": "test/model", "modelRevision": "a" * 40,
                    "files": [{"path": "weight.bin", "bytes": 7,
                               "sourceSha256": hashlib.sha256(b"fixture").hexdigest()}]}
        destination = self.root / "staging"
        destination.mkdir()
        (destination / "weight.bin.part").write_bytes(b"fix")

        class Response(BytesIO):
            status = 206
            headers = {"Content-Range": "bytes 0-3/7"}

        with patch.object(asset.urllib.request, "urlopen", return_value=Response(b"ture")):
            with self.assertRaisesRegex(ValueError, "Content-Range"):
                asset.download_candidate(manifest, destination, "https://example.invalid")
        self.assertEqual((destination / "weight.bin.part").read_bytes(), b"fix")
        self.assertFalse((destination / "weight.bin").exists())

    def test_candidate_download_skips_verified_existing_file(self):
        manifest = {"status": "candidate", "modelId": "test/model", "modelRevision": "a" * 40,
                    "files": [{"path": "weight.bin", "bytes": 7,
                               "sourceSha256": hashlib.sha256(b"fixture").hexdigest()}]}
        destination = self.root / "staging"
        destination.mkdir()
        (destination / "weight.bin").write_bytes(b"fixture")
        with patch.object(asset.urllib.request, "urlopen", side_effect=AssertionError("network called")):
            observed = asset.download_candidate(manifest, destination, "https://example.invalid")
        self.assertEqual(observed["weight.bin"]["bytes"], 7)

    def test_candidate_download_rejects_hash_mismatch(self):
        manifest = {"status": "candidate", "modelId": "test/model", "modelRevision": "a" * 40,
                    "files": [{"path": "weight.bin", "bytes": 7, "sha256": "0" * 64}]}
        destination = self.root / "staging"

        class Response(BytesIO):
            status = 200

        with patch.object(asset.urllib.request, "urlopen", return_value=Response(b"fixture")):
            with self.assertRaisesRegex(ValueError, "sha256"):
                asset.download_candidate(manifest, destination, "https://example.invalid")
        self.assertFalse((destination / "weight.bin").exists())


if __name__ == "__main__":
    unittest.main()
