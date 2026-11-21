import hashlib
import json
import os
import pathlib
import tempfile
import unittest
from unittest.mock import patch

import tools.nexus_notice_materials as notice


VERSION = "0.6"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def make_case(files):
    temp = tempfile.TemporaryDirectory()
    root = pathlib.Path(temp.name)
    entries = []
    for path, body in files:
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
        entries.append({"path": path, "bytes": len(body), "sha256": digest(body)})
    index = json.dumps({"candidateVersion": VERSION, "files": entries},
                       separators=(",", ":")).encode()
    (root / "NOTICE-INDEX.json").write_bytes(index)
    return temp, root, index


def check(root, index, version=VERSION, sha=None):
    return notice.validate_materials(index, digest(index) if sha is None else sha,
                                     version, root)


class NoticeMaterialsTests(unittest.TestCase):
    def test_success_binds_index_and_material_without_mutating_inputs(self):
        temp, root, index = make_case([("LICENSE", b"license text")])
        try:
            before = {p.relative_to(root): p.read_bytes() for p in root.rglob("*") if p.is_file()}
            result = check(root, index)
            self.assertEqual(result["validatedMaterialCount"], 2)
            self.assertEqual(result["validationStatus"], "pass-bounded")
            self.assertEqual(index, (root / "NOTICE-INDEX.json").read_bytes())
            self.assertEqual(before, {p.relative_to(root): p.read_bytes() for p in root.rglob("*") if p.is_file()})
        finally:
            temp.cleanup()

    def test_rejects_bad_actual_bytes_missing_material_and_changed_index(self):
        for mode in ("bytes", "missing", "index"):
            temp, root, index = make_case([("LICENSE", b"expected")])
            try:
                if mode == "bytes":
                    (root / "LICENSE").write_bytes(b"changed")
                elif mode == "missing":
                    (root / "LICENSE").unlink()
                else:
                    (root / "NOTICE-INDEX.json").write_bytes(b"changed")
                with self.assertRaisesRegex(ValueError, "^notice material validation failed$"):
                    check(root, index)
            finally:
                temp.cleanup()

    def test_rejects_external_hash_and_version_mismatch(self):
        temp, root, index = make_case([("LICENSE", b"body")])
        try:
            for version, sha in ((VERSION, "0" * 64), ("other", digest(index))):
                with self.assertRaises(ValueError):
                    check(root, index, version, sha)
        finally:
            temp.cleanup()

    def test_rejects_duplicate_paths_index_entry_malformed_json_and_numbers(self):
        temp, root, index = make_case([("LICENSE", b"body")])
        try:
            entry = {"path": "LICENSE", "bytes": 4, "sha256": digest(b"body")}
            cases = [
                json.dumps({"candidateVersion": VERSION, "files": [entry, entry]}).encode(),
                json.dumps({"candidateVersion": VERSION, "files": [dict(entry, path="NOTICE-INDEX.json")]}).encode(),
                b"{",
                b'{"candidateVersion":"0.6","candidateVersion":"0.6","files":[]}',
                b'{"candidateVersion":"0.6","files":[{"path":"LICENSE","bytes":1e999,"sha256":"' + digest(b"body").encode() + b'"}]}',
                b'{"candidateVersion":"0.6","files":[{"path":"LICENSE","bytes":true,"sha256":"' + digest(b"body").encode() + b'"}]}',
                b'{"candidateVersion":"0.6","files":[{"path":"LICENSE","bytes":999999999999999999999999999999999999,"sha256":"' + digest(b"body").encode() + b'"}]}',
                b'{"candidateVersion":"0.6","files":[{"path":"LICENSE","bytes":NaN,"sha256":"' + digest(b"body").encode() + b'"}]}',
            ]
            for raw in cases:
                with self.subTest(raw=raw[:40]), self.assertRaises(ValueError):
                    check(root, raw)
        finally:
            temp.cleanup()

    def test_rejects_unsafe_paths_and_symlinks(self):
        temp, root, index = make_case([("LICENSE", b"body")])
        try:
            entry = {"path": "../LICENSE", "bytes": 4, "sha256": digest(b"body")}
            raw = json.dumps({"candidateVersion": VERSION, "files": [entry]}).encode()
            with self.assertRaises(ValueError):
                check(root, raw)
            (root / "LICENSE").unlink()
            (root / "elsewhere").write_bytes(b"body")
            (root / "LICENSE").symlink_to(root / "elsewhere")
            with self.assertRaises(ValueError):
                check(root, index)
        finally:
            temp.cleanup()

    def test_rejects_intermediate_symlink_fifo_and_missing_flag(self):
        temp, root, index = make_case([("dir/LICENSE", b"body")])
        try:
            (root / "dir").rename(root / "real-dir")
            (root / "dir").symlink_to(root / "real-dir", target_is_directory=True)
            with self.assertRaises(ValueError):
                check(root, index)
        finally:
            temp.cleanup()

        temp, root, index = make_case([("LICENSE", b"body")])
        try:
            (root / "LICENSE").unlink()
            os.mkfifo(root / "LICENSE")
            with self.assertRaises(ValueError):
                check(root, index)
        finally:
            temp.cleanup()

        temp, root, index = make_case([("LICENSE", b"body")])
        try:
            with patch.object(notice.os, "O_NOFOLLOW", 0):
                with self.assertRaises(ValueError):
                    check(root, index)
        finally:
            temp.cleanup()


if __name__ == "__main__":
    unittest.main()
