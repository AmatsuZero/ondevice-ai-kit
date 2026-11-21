import copy
import hashlib
import io
import json
import os
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from tools.nexus_plan_archive import validate_archive
from tools import nexus_plan_archive as checker


VERSION = "0.144.6-ondevice-f02.6"
CANDIDATE = "https://repo.example.invalid/releases/"


def path_for(module, filename=None):
    filename = filename or f"{module}-{VERSION}-1.0.0.jar"
    return f"org/jetbrains/skiko/{module}/{VERSION}/{filename}"


class NexusPlanArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.counter = 0

    def tearDown(self):
        self.temp.cleanup()

    def make_archive(self, members):
        self.counter += 1
        archive = self.root / f"archive {self.counter}.tar.gz"
        with tarfile.open(archive, "w:gz") as tar:
            for name, data, kind in members:
                info = tarfile.TarInfo(name)
                info.mtime = 0
                if kind == "file":
                    info.size = len(data)
                    tar.addfile(info, io.BytesIO(data))
                else:
                    info.type = kind
                    if kind in (tarfile.SYMTYPE, tarfile.LNKTYPE):
                        info.linkname = "target"
                    tar.addfile(info)
        return archive

    def make_plan(self, planned, members=None):
        members = members if members is not None else [
            (name, data, "file") for name, data in planned.items()
        ]
        archive = self.make_archive(members)
        archive_data = archive.read_bytes()
        files = [
            {
                "path": name,
                "url": CANDIDATE + name,
                "bytes": len(data),
                "sha256": hashlib.sha256(data).hexdigest(),
            }
            for name, data in planned.items()
        ]
        plan = {
            "schemaVersion": 1,
            "version": VERSION,
            "fileCount": len(files),
            "archiveBytes": len(archive_data),
            "archiveSha256": hashlib.sha256(archive_data).hexdigest(),
            "hostedCandidate": CANDIDATE,
            "files": files,
            "publisherNote": {"kept": True},
        }
        return plan, archive

    def test_success_with_multiple_files(self):
        planned = {
            path_for("skiko"): b"jar bytes\x00one",
            path_for("skiko-iosarm64"): b"jar bytes two",
            path_for("skiko-iossimulatorarm64"): b"jar bytes three",
        }
        plan, archive = self.make_plan(planned)
        result = validate_archive(plan, archive)
        self.assertEqual(result, {
            "schemaVersion": 1,
            "kind": "nexus-plan-archive-consistency",
            "version": VERSION,
            "validatedFileCount": 3,
            "archiveSha256": plan["archiveSha256"],
            "archiveBytes": plan["archiveBytes"],
            "validationStatus": "pass-bounded",
            "uploaded": False,
            "remoteConsumerValidated": False,
        })

    def test_member_digest_fails_even_with_refreshed_archive_digest(self):
        name = path_for("skiko")
        plan, archive = self.make_plan({name: b"planned bytes"}, [(name, b"altered bytes", "file")])
        with self.assertRaisesRegex(ValueError, "validation failed"):
            validate_archive(plan, archive)

    def test_missing_extra_and_duplicate_paths_fail(self):
        first, second = path_for("skiko"), path_for("skiko-iosarm64")
        planned = {first: b"one", second: b"two"}
        cases = [
            [(first, b"one", "file")],
            [(first, b"one", "file"), (second, b"two", "file"),
             (path_for("skiko-iossimulatorarm64"), b"extra", "file")],
            [(first, b"one", "file"), (first, b"one", "file"), (second, b"two", "file")],
        ]
        for members in cases:
            with self.subTest(members=members):
                plan, archive = self.make_plan(planned, members)
                with self.assertRaises(ValueError):
                    validate_archive(plan, archive)

        plan, archive = self.make_plan({first: b"one"})
        plan["files"].append(copy.deepcopy(plan["files"][0]))
        plan["fileCount"] = 2
        with self.assertRaises(ValueError):
            validate_archive(plan, archive)

    def test_links_special_members_and_unsafe_names_fail(self):
        expected = path_for("skiko")
        cases = [
            (expected, b"", tarfile.SYMTYPE),
            (expected, b"", tarfile.LNKTYPE),
            (expected, b"", tarfile.DIRTYPE),
            (expected, b"", tarfile.FIFOTYPE),
            ("../../escape", b"x", "file"),
        ]
        for name, data, kind in cases:
            with self.subTest(name=name, kind=kind):
                plan, archive = self.make_plan({expected: b"expected"}, [(name, data, kind)])
                with self.assertRaises(ValueError):
                    validate_archive(plan, archive)

    def test_strict_plan_fields_and_values(self):
        name = path_for("skiko")
        plan, archive = self.make_plan({name: b"bytes"})
        mutations = [
            lambda p: p.update(schemaVersion=True),
            lambda p: p.update(fileCount=1.0),
            lambda p: p.update(version="../escape"),
            lambda p: p["files"][0].update(bytes=True),
            lambda p: p["files"][0].update(bytes=5.0),
            lambda p: p["files"][0].update(extra=True),
            lambda p: p["files"][0].update(path=name.replace("/skiko/", "/../")),
            lambda p: p["files"][0].update(url="https://elsewhere.invalid/file"),
            lambda p: p["files"][0].update(sha256="A" * 64),
            lambda p: p.update(hostedCandidate="******repo.example.invalid/releases/"),
        ]
        for mutate in mutations:
            candidate = copy.deepcopy(plan)
            mutate(candidate)
            with self.subTest(plan=candidate):
                with self.assertRaises(ValueError):
                    validate_archive(candidate, archive)

        for suffix in ('?download=1', '#ignored', '%2fescape', ' with-space', '\nheader'):
            with self.subTest(suffix=suffix):
                unsafe = name + suffix
                candidate, unsafe_archive = self.make_plan({unsafe: b'bytes'})
                with self.assertRaises(ValueError):
                    validate_archive(candidate, unsafe_archive)

        for control in ('\x7f', '\x80', '\x9f'):
            with self.subTest(control=ord(control)):
                candidate = copy.deepcopy(plan)
                candidate['hostedCandidate'] = CANDIDATE + control + '/'
                candidate['files'][0]['url'] = candidate['hostedCandidate'] + name
                with self.assertRaises(ValueError):
                    validate_archive(candidate, archive)

    def test_archive_size_and_hash_mismatches_fail(self):
        name = path_for("skiko")
        plan, archive = self.make_plan({name: b"bytes"})
        for key, value in (("archiveBytes", plan["archiveBytes"] + 1),
                           ("archiveSha256", "0" * 64)):
            candidate = copy.deepcopy(plan)
            candidate[key] = value
            with self.subTest(key=key):
                with self.assertRaises(ValueError):
                    validate_archive(candidate, archive)

    def test_cli_success_and_generic_failure(self):
        name = path_for("skiko")
        plan, archive = self.make_plan({name: b"bytes"})
        plan_file = self.root / "plan with spaces.json"
        plan_file.write_text(json.dumps(plan), encoding="utf-8")
        cwd = Path(__file__).resolve().parents[2]
        command = [sys.executable, "-m", "tools.nexus_plan_archive", str(plan_file), str(archive)]
        success = subprocess.run(command, cwd=cwd, capture_output=True, text=True, check=False)
        self.assertEqual(success.returncode, 0)
        self.assertEqual(json.loads(success.stdout)["validationStatus"], "pass-bounded")
        self.assertEqual(success.stderr, "")

        plan_file.write_text('{"schemaVersion":1,"schemaVersion":1}', encoding="utf-8")
        failure = subprocess.run(command, cwd=cwd, capture_output=True, text=True, check=False)
        self.assertNotEqual(failure.returncode, 0)
        self.assertEqual(failure.stdout, "")
        self.assertNotIn(str(plan_file), failure.stderr)

        for text in (json.dumps(plan)[:-1] + ', "extension": NaN}',
                     json.dumps(plan)[:-1] + ', "extension": 1e999}'):
            plan_file.write_text(text)
            failure = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
            self.assertNotEqual(failure.returncode, 0)
            self.assertEqual(failure.stdout, '')

    def test_rejects_non_regular_archives_and_missing_nofollow_support(self):
        plan, archive = self.make_plan({path_for('skiko'): b'bytes'})
        link = self.root / 'linked-archive'
        link.symlink_to(archive)
        fifo = self.root / 'fifo'
        os.mkfifo(fifo)
        for invalid in (link, fifo, self.root):
            with self.subTest(path=invalid.name):
                with self.assertRaises(ValueError):
                    validate_archive(plan, invalid)
        with patch.object(checker.os, 'O_NOFOLLOW', 0):
            with self.assertRaises(ValueError):
                validate_archive(plan, archive)

    def test_detects_actual_archive_mutation_during_validation(self):
        real_fstat = os.fstat
        for target_call in (2, 3):
            with self.subTest(target=target_call):
                plan, archive = self.make_plan({path_for('skiko'): b'bytes'})
                calls = 0

                def mutate_before_stat(fd):
                    nonlocal calls
                    calls += 1
                    if calls == target_call:
                        with archive.open('ab') as writer:
                            writer.write(b'actual mutation')
                    return real_fstat(fd)

                with patch.object(checker.os, 'fstat', side_effect=mutate_before_stat):
                    with self.assertRaises(ValueError):
                        validate_archive(plan, archive)


if __name__ == "__main__":
    unittest.main()
