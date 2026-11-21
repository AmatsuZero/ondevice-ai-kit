import copy
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

from tools import nexus_get_receipts as checker


class NexusGetReceiptsTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.base = pathlib.Path(temp.name)
        self.root = self.base / "bodies"
        self.root.mkdir()
        self.plan = {
            "schemaVersion": 1, "version": "1.2.3", "fileCount": 2,
            "archiveBytes": 1, "archiveSha256": "a" * 64,
            "hostedCandidate": "https://example.invalid/repository/", "files": [],
        }
        self.receipts = {
            "schemaVersion": 1, "kind": "nexus-get-receipts",
            "version": "1.2.3", "fileCount": 2, "entries": [],
        }
        for module, name, content in (
                ("skiko", "skiko-1.2.3.jar", b"first"),
                ("skiko-iosarm64", "skiko-iosarm64-1.2.3.jar", b"second")):
            path = f"org/jetbrains/skiko/{module}/1.2.3/{name}"
            item = {"path": path, "url": self.plan["hostedCandidate"] + path,
                    "bytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}
            self.plan["files"].append(item)
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            self.receipts["entries"].append({
                "path": path, "requestUrl": item["url"], "finalUrl": item["url"],
                "method": "GET", "status": 200, "authenticated": False,
                "redirects": [], "bytes": item["bytes"], "sha256": item["sha256"],
            })

    def validate(self, plan=None, receipts=None, root=None):
        return checker.validate_receipts(
            self.plan if plan is None else plan,
            self.receipts if receipts is None else receipts,
            self.root if root is None else root,
        )

    def test_success_and_bounded_result(self):
        result = self.validate()
        self.assertEqual(result["validatedFileCount"], 2)
        self.assertEqual(result["validatedBytes"], 11)
        self.assertEqual(result["validationStatus"], "pass-bounded")
        self.assertFalse(result["networkPerformedByThisTool"])
        self.assertFalse(result["anonymousExecutionVerified"])
        self.assertFalse(result["remoteConsumerValidated"])
        self.assertFalse(result["uploaded"])

    def test_body_bytes_are_checked_not_just_declarations(self):
        (self.root / self.plan["files"][0]["path"]).write_bytes(b"wrong")
        with self.assertRaisesRegex(ValueError, "^GET receipt validation failed$"):
            self.validate()

    def test_entry_coverage_and_strict_metadata(self):
        cases = []
        missing = copy.deepcopy(self.receipts)
        missing["entries"].pop()
        cases.append(missing)
        duplicate = copy.deepcopy(self.receipts)
        duplicate["entries"][1] = copy.deepcopy(duplicate["entries"][0])
        cases.append(duplicate)
        extra = copy.deepcopy(self.receipts)
        extra["entries"][1]["path"] += "-extra"
        cases.append(extra)
        for field, value in (("method", "HEAD"), ("status", 302),
                             ("status", True), ("status", 200.0),
                             ("requestUrl", "https://other.invalid/"),
                             ("finalUrl", "https://other.invalid/"),
                             ("redirects", ["https://other.invalid/"]),
                             ("authenticated", True), ("bytes", True)):
            changed = copy.deepcopy(self.receipts)
            changed["entries"][0][field] = value
            cases.append(changed)
        extra_field = copy.deepcopy(self.receipts)
        extra_field["unexpected"] = 1
        cases.append(extra_field)
        extra_entry_field = copy.deepcopy(self.receipts)
        extra_entry_field["entries"][0]["unexpected"] = 1
        cases.append(extra_entry_field)
        for receipts in cases:
            with self.subTest(receipts=receipts):
                with self.assertRaises(ValueError):
                    self.validate(receipts=receipts)

    def test_plan_contract_is_reused(self):
        plan = copy.deepcopy(self.plan)
        plan["files"][0]["url"] = "https://other.invalid/"
        with self.assertRaises(ValueError):
            self.validate(plan=plan)

    def test_symlinks_are_rejected(self):
        path = self.root / self.plan["files"][0]["path"]
        target = self.base / "target"
        target.write_bytes(b"first")
        path.unlink()
        path.symlink_to(target)
        with self.assertRaises(ValueError):
            self.validate()

        shutil.rmtree(self.root / "org")
        (self.root / "org").symlink_to(self.base, target_is_directory=True)
        with self.assertRaises(ValueError):
            self.validate()

        alias = self.base / "root-link"
        alias.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            self.validate(root=alias)

    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO unsupported")
    def test_fifo_is_rejected(self):
        path = self.root / self.plan["files"][0]["path"]
        path.unlink()
        os.mkfifo(path)
        with self.assertRaises(ValueError):
            self.validate()

    def test_missing_nofollow_support_fails_closed(self):
        with mock.patch.object(checker.os, "O_NOFOLLOW", 0):
            with self.assertRaises(ValueError):
                self.validate()

    def test_mutation_of_open_file_is_rejected(self):
        original_read = os.read
        changed = False
        read_chunks = []
        target = self.root / self.plan["files"][0]["path"]

        def mutate_after_read(fd, size):
            nonlocal changed
            data = original_read(fd, size)
            read_chunks.append(data)
            if data and not changed:
                with target.open("r+b") as writer:
                    writer.write(b"X")
                changed = True
            return data

        with mock.patch.object(checker.os, "read", side_effect=mutate_after_read):
            with self.assertRaises(ValueError):
                self.validate()
        self.assertTrue(changed)
        self.assertEqual(read_chunks, [b"first", b""])
        self.assertEqual(target.read_bytes(), b"Xirst")

    def test_cli_success_and_silent_generic_errors(self):
        plan_file = self.base / "plan.json"
        receipts_file = self.base / "receipts.json"
        plan_file.write_text(json.dumps(self.plan), encoding="utf-8")
        receipts_file.write_text(json.dumps(self.receipts), encoding="utf-8")
        command = [sys.executable, "-m", "tools.nexus_get_receipts"]
        cwd = pathlib.Path(__file__).resolve().parents[2]
        good = subprocess.run(command + [str(plan_file), str(receipts_file), str(self.root)],
                              cwd=cwd, capture_output=True, text=True)
        self.assertEqual(good.returncode, 0)
        self.assertEqual(json.loads(good.stdout)["kind"], "nexus-get-receipt-consistency")
        self.assertEqual(good.stderr, "")

        for raw in ("{", '{"schemaVersion":1,"schemaVersion":1}'):
            receipts_file.write_text(raw, encoding="utf-8")
            bad = subprocess.run(command + [str(plan_file), str(receipts_file), str(self.root)],
                                 cwd=cwd, capture_output=True, text=True)
            self.assertEqual(bad.returncode, 1)
            self.assertEqual(bad.stdout, "")
            self.assertEqual(bad.stderr, "GET receipt validation failed\n")


if __name__ == "__main__":
    unittest.main()
