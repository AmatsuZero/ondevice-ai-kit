import hashlib
import json
import stat
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


TOOL = Path(__file__).resolve().parents[1] / "inspect_android_vlm_artifact.py"


class AndroidVlmArtifactTests(unittest.TestCase):
    def make_aar(self, root, entries):
        path = Path(root) / "sample.aar"
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return path

    def run_tool(self, aar, *args):
        return subprocess.run(
            [sys.executable, str(TOOL), str(aar), *args],
            text=True,
            capture_output=True,
        )

    def valid_entries(self):
        return {
            "AndroidManifest.xml": b"manifest",
            "classes.jar": b"classes",
            "jni/arm64-v8a/libfoo.so": b"so",
            "jni/x86_64/libbar.so": b"so",
            "META-INF/LICENSE.txt": b"license",
            "META-INF/NOTICE": b"notice",
        }

    def test_valid_synthetic_aar_reports_digest_size_abi_and_licenses(self):
        with tempfile.TemporaryDirectory() as root:
            aar = self.make_aar(root, self.valid_entries())
            result = self.run_tool(aar, "--json")
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads(result.stdout)
            self.assertEqual(report["status"], "passed")
            self.assertEqual(report["sha256"], hashlib.sha256(aar.read_bytes()).hexdigest())
            self.assertEqual(report["size"], aar.stat().st_size)
            self.assertTrue(report["required"]["manifest"])
            self.assertTrue(report["required"]["classes_jar"])
            self.assertEqual(report["jni_abis"], ["arm64-v8a", "x86_64"])
            self.assertEqual(report["meta_inf"]["licenses"], ["META-INF/LICENSE.txt"])
            self.assertEqual(report["meta_inf"]["notices"], ["META-INF/NOTICE"])
            self.assertEqual(report["license_files"], ["META-INF/LICENSE.txt"])
            self.assertEqual(report["notice_files"], ["META-INF/NOTICE"])
            self.assertEqual(report["sdk"]["min_sdk"], "unverified")

    def test_text_manifest_reports_min_sdk_and_root_license_notice(self):
        entries = {
            "AndroidManifest.xml": b'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-sdk android:minSdkVersion="24" />
</manifest>''',
            "classes.jar": b"classes",
            "jni/arm64-v8a/libfoo.so": b"so",
            "LICENSE": b"license",
            "THIRD_PARTY_NOTICE.txt": b"notice",
        }
        with tempfile.TemporaryDirectory() as root:
            aar = self.make_aar(root, entries)
            result = self.run_tool(aar, "--json")
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads(result.stdout)
            self.assertEqual(report["status"], "passed")
            self.assertEqual(report["sdk"]["min_sdk"], 24)
            self.assertEqual(report["sdk"]["target_sdk"], "unverified")
            self.assertEqual(report["sdk"]["status"], "partial")
            self.assertEqual(report["sdk"]["manifest_format"], "text-xml")
            self.assertEqual(report["license_files"], ["LICENSE"])
            self.assertEqual(report["notice_files"], ["THIRD_PARTY_NOTICE.txt"])

    def test_path_traversal_fails(self):
        with tempfile.TemporaryDirectory() as root:
            aar = self.make_aar(root, {**self.valid_entries(), "../escape": b"bad"})
            result = self.run_tool(aar, "--json")
            self.assertNotEqual(result.returncode, 0)
            report = json.loads(result.stdout)
            self.assertEqual(report["status"], "failed")
            self.assertTrue(report["path_safety"]["unsafe"])

    def test_symlink_entry_fails_closed(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "sample.aar"
            with zipfile.ZipFile(path, "w") as archive:
                for name, data in self.valid_entries().items():
                    archive.writestr(name, data)
                link = zipfile.ZipInfo("jni/arm64-v8a/link.so")
                link.external_attr = (stat.S_IFLNK | 0o777) << 16
                archive.writestr(link, b"classes.jar")
            result = self.run_tool(path, "--json")
            self.assertNotEqual(result.returncode, 0)
            report = json.loads(result.stdout)
            self.assertTrue(report["path_safety"]["unsafe"])

    def test_missing_required_files_fails(self):
        with tempfile.TemporaryDirectory() as root:
            aar = self.make_aar(root, {"classes.jar": b"classes"})
            result = self.run_tool(aar, "--json")
            self.assertNotEqual(result.returncode, 0)
            report = json.loads(result.stdout)
            self.assertFalse(report["required"]["manifest"])
            self.assertTrue(report["errors"])

    def test_aapt2_parses_sdk_versions(self):
        with tempfile.TemporaryDirectory() as root:
            aar = self.make_aar(root, self.valid_entries())
            aapt2 = Path(root) / "aapt2"
            aapt2.write_text("#!/bin/sh\necho \"package: name='x' minSdkVersion:'24' targetSdkVersion:'35'\"\n")
            aapt2.chmod(aapt2.stat().st_mode | stat.S_IXUSR)
            result = self.run_tool(aar, "--json", "--aapt2", str(aapt2))
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads(result.stdout)
            self.assertEqual(report["sdk"]["min_sdk"], 24)
            self.assertEqual(report["sdk"]["target_sdk"], 35)
            self.assertEqual(report["sdk"]["status"], "verified")

    def test_aapt2_unavailable_is_explicitly_unverified(self):
        with tempfile.TemporaryDirectory() as root:
            aar = self.make_aar(root, self.valid_entries())
            result = self.run_tool(aar, "--json", "--aapt2", str(Path(root) / "missing-aapt2"))
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads(result.stdout)
            self.assertEqual(report["sdk"]["min_sdk"], "unverified")
            self.assertEqual(report["sdk"]["status"], "unavailable")


if __name__ == "__main__":
    unittest.main()
