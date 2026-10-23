import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GENERATOR = ROOT / "tools/generate_mlxvlm_consumer.py"


class MLXVLMSourceBundleTests(unittest.TestCase):
    def generate(self, output: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(GENERATOR), "--portable-source", str(output)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_portable_bundle_is_tracked_relocatable_and_hash_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "consumer"
            result = self.generate(output)
            self.assertEqual(result.returncode, 0, result.stderr)
            project = (output / "project.yml").read_text()
            self.assertIn("path: packages/apple-mlx", project)
            self.assertIn("path: packages/apple", project)
            self.assertNotIn(str(ROOT), project)
            bundle = json.loads((output / "source-bundle.json").read_text())
            self.assertEqual(bundle["formatVersion"], 1)
            self.assertEqual(bundle["kind"], "local-source")
            self.assertTrue(bundle["files"])
            self.assertEqual(bundle["lockSHA256"], hashlib.sha256(
                (output / "packages/apple-mlx/Package.resolved").read_bytes()
            ).hexdigest())
            self.assertEqual((output / "packages/apple-mlx/Package.resolved").read_bytes(),
                             (ROOT / "apple-mlx/Package.resolved").read_bytes())
            resource = "apple-mlx/Sources/ODAIKitAppleMLXVLM/Resources/smolvlm2-model.json"
            self.assertEqual((output / "packages" / resource).read_bytes(),
                             (ROOT / resource).read_bytes())
            for entry in bundle["files"]:
                path = output / entry["path"]
                self.assertTrue(path.is_file(), entry["path"])
                self.assertFalse(path.is_symlink(), entry["path"])
                self.assertEqual(entry["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
            self.assertTrue((output / "packages/apple-mlx/Sources/ODAIKitAppleMLXVLM/Resources/smolvlm2-model.json").is_file())
            self.assertTrue((output / "packages/apple/Tests/ODAIKitMultimodalTests").is_dir())
            self.assertTrue((output / "packages/apple-mlx/Tests/ODAIKitAppleMLXVLMTests").is_dir())
            moved = Path(directory) / "moved consumer"
            output.rename(moved)
            pbx = moved / "MLXVLMConsumer.xcodeproj/project.pbxproj"
            objects = json.loads(subprocess.check_output([
                "plutil", "-convert", "json", "-o", "-", str(pbx)
            ]))["objects"]
            paths = {v["relativePath"] for v in objects.values()
                     if v.get("isa") == "XCLocalSwiftPackageReference"}
            self.assertEqual(paths, {"packages/apple", "packages/apple-mlx"})

    def test_untracked_sentinel_is_not_copied_and_existing_output_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "consumer"
            with tempfile.NamedTemporaryFile(
                dir=ROOT / "apple-mlx/Sources/ODAIKitAppleMLXVLM",
                prefix="untracked-source-bundle-",
            ) as sentinel_file:
                sentinel = Path(sentinel_file.name)
                sentinel.write_text("must not ship")
                result = self.generate(output)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertFalse((output / "packages/apple-mlx/Sources/ODAIKitAppleMLXVLM" / sentinel.name).exists())
                keep = output / "keep.txt"
                keep.write_text("preserve")
                retry = self.generate(output)
                self.assertNotEqual(retry.returncode, 0)
                self.assertEqual(keep.read_text(), "preserve")

    def test_symlink_output_is_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "real"
            destination.mkdir()
            output = Path(directory) / "link"
            output.symlink_to(destination, target_is_directory=True)
            result = self.generate(output)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(list(destination.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
