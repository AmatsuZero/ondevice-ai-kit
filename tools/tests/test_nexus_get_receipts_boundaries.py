"""Parent-owned acceptance boundaries for retained Nexus GET evidence."""
import copy
import hashlib
import importlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


class NexusGetReceiptBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / "bodies"
        self.root.mkdir()
        self.version = "0.144.6-ondevice-f02.6"
        self.path = f"org/jetbrains/skiko/skiko/{self.version}/skiko-{self.version}.jar"
        self.body = b"expected retained bytes"
        self.file = self.root / self.path
        self.file.parent.mkdir(parents=True)
        self.file.write_bytes(self.body)
        self.url = "https://repo.example.invalid/releases/" + self.path
        self.digest = hashlib.sha256(self.body).hexdigest()
        self.plan = {"schemaVersion": 1, "version": self.version, "fileCount": 1,
                     "archiveBytes": 10, "archiveSha256": "0" * 64,
                     "hostedCandidate": "https://repo.example.invalid/releases/",
                     "files": [{"path": self.path, "url": self.url,
                                "bytes": len(self.body), "sha256": self.digest}]}
        self.receipts = {"schemaVersion": 1, "kind": "nexus-get-receipts",
                         "version": self.version, "fileCount": 1,
                         "entries": [{"path": self.path, "requestUrl": self.url,
                                      "finalUrl": self.url, "method": "GET", "status": 200,
                                      "authenticated": False, "redirects": [],
                                      "bytes": len(self.body), "sha256": self.digest}]}

    def checker(self):
        spec = importlib.util.find_spec("tools.nexus_get_receipts")
        self.assertIsNotNone(spec, "missing retained GET receipt validator")
        return importlib.import_module("tools.nexus_get_receipts")

    def validate(self):
        return self.checker().validate_receipts(self.plan, self.receipts, self.root)

    def reject(self):
        with self.assertRaisesRegex(ValueError, "^GET receipt validation failed$"):
            self.validate()

    def test_success_preserves_inputs_and_does_not_grant_transport_or_consumer_approval(self):
        before = copy.deepcopy((self.plan, self.receipts))
        self.assertEqual(self.validate(), {
            "schemaVersion": 1, "kind": "nexus-get-receipt-consistency",
            "version": self.version, "validatedFileCount": 1,
            "validatedBytes": len(self.body), "validationStatus": "pass-bounded",
            "networkPerformedByThisTool": False, "anonymousExecutionVerified": False,
            "remoteConsumerValidated": False, "uploaded": False})
        self.assertEqual((self.plan, self.receipts), before)
        self.assertEqual(self.file.read_bytes(), self.body)

    def test_correct_declarations_cannot_mask_changed_bytes(self):
        self.file.write_bytes(b"X" * len(self.body))
        self.reject()

    def test_oversized_actual_body_is_rejected(self):
        self.file.write_bytes(self.body + b"X")
        self.reject()

    def test_duplicate_and_missing_receipts_rejected(self):
        for entries in ([], self.receipts["entries"] * 2):
            with self.subTest(entries=len(entries)):
                self.receipts["entries"] = entries
                self.reject()

    def test_boolean_and_float_integer_aliases_rejected(self):
        for where, key, value in (("root", "schemaVersion", True),
                                  ("root", "fileCount", 1.0),
                                  ("entry", "status", 200.0),
                                  ("entry", "bytes", float(len(self.body)))):
            with self.subTest(where=where, key=key):
                target = self.receipts if where == "root" else self.receipts["entries"][0]
                original = target[key]
                target[key] = value
                self.reject()
                target[key] = original

    def test_transport_contract_and_unexpected_fields_rejected(self):
        for key, value in (("method", "HEAD"), ("status", 302), ("authenticated", True),
                           ("authenticated", 0), ("redirects", [self.url]),
                           ("finalUrl", self.url + "?cache=1"), ("requestUrl", self.url + "#x"),
                           ("authorization", "do-not-print")):
            with self.subTest(key=key, value=value):
                original = self.receipts["entries"][0]
                self.receipts["entries"][0] = dict(original, **{key: value})
                self.reject()
                self.receipts["entries"][0] = original

    def test_final_symlink_rejected_even_when_target_has_right_bytes(self):
        target = self.root / "other"
        self.file.rename(target)
        self.file.symlink_to(target)
        self.reject()

    def test_intermediate_symlink_rejected_even_when_target_has_right_bytes(self):
        original = self.root / "org"
        moved = self.root / "other"
        original.rename(moved)
        original.symlink_to(moved, target_is_directory=True)
        self.reject()

    def test_symlink_body_root_rejected(self):
        alias = self.root.parent / "alias"
        alias.symlink_to(self.root, target_is_directory=True)
        self.root = alias
        self.reject()

    def test_missing_nofollow_support_rejected(self):
        with patch.object(os, "O_NOFOLLOW", 0):
            self.reject()

    def test_plan_validation_not_skipped(self):
        self.plan["files"][0]["url"] += "?wrong"
        self.reject()
