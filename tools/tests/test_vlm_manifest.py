import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("validate_vlm_manifest", Path(__file__).parents[1] / "validate_vlm_manifest.py")
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


BASE = {
    "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
    "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268",
    "runtime": "MLXVLM",
    "runtimeRevision": "b" * 40,
    "files": [{"path": "config.json", "sha256": "a" * 64, "bytes": 12}],
    "license": "apache-2.0",
    "platformMin": "iOS 18.0",
    "status": "candidate",
}


def write(value):
    handle = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False)
    json.dump(value, handle)
    handle.close()
    return Path(handle.name)


class ManifestTests(unittest.TestCase):
    def test_accepts_candidate_with_pinned_revisions(self):
        value = {**BASE, "files": [{"path": "config.json", "sha256": None, "bytes": None}]}
        self.assertEqual(validator.validate_manifest(write(value))["status"], "candidate")

    def test_rejects_floating_model_revision(self):
        value = {**BASE, "modelRevision": "main"}
        with self.assertRaisesRegex(ValueError, "floating"):
            validator.validate_manifest(write(value))

    def test_rejects_non_ios18_platform(self):
        value = {**BASE, "platformMin": "iOS 15.0"}
        with self.assertRaisesRegex(ValueError, "iOS 18"):
            validator.validate_manifest(write(value))

    def test_rejects_verified_manifest_without_file_hash(self):
        value = {**BASE, "status": "verified", "files": [{"path": "config.json", "bytes": 12}]}
        with self.assertRaisesRegex(ValueError, "sha256"):
            validator.validate_manifest(write(value))

    def test_rejects_traversal_file_path(self):
        value = {**BASE, "files": [{"path": "../secret", "sha256": "a" * 64, "bytes": 12}]}
        with self.assertRaisesRegex(ValueError, "path"):
            validator.validate_manifest(write(value))

    def test_rejects_unknown_runtime(self):
        value = {**BASE, "runtime": "unknown"}
        with self.assertRaisesRegex(ValueError, "runtime"):
            validator.validate_manifest(write(value))


if __name__ == "__main__":
    unittest.main()
