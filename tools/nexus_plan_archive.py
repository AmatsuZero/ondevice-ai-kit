"""Read-only consistency checks for a Nexus plan and its tar.gz archive."""

import hashlib
import json
import math
import os
import pathlib
import re
import stat
import sys
import tarfile
import unicodedata
import urllib.parse
import zlib

_CHUNK_SIZE = 1024 * 1024
_SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")
_VERSION_RE = re.compile(r"[A-Za-z0-9.-]+\Z")
_MODULES = {"skiko", "skiko-iosarm64", "skiko-iossimulatorarm64"}
_ERROR = "archive/plan validation failed"


def _fail():
    raise ValueError(_ERROR)


def _positive_int(value):
    return type(value) is int and value > 0


def _sha256(value):
    return isinstance(value, str) and _SHA256_RE.fullmatch(value) is not None


def _safe_plan_path(value, version):
    if not isinstance(value, str) or "\\" in value or "\x00" in value:
        return False
    if value.startswith("/"):
        return False
    parts = value.split("/")
    if len(parts) != 6 or any(part in ("", ".", "..") for part in parts):
        return False
    if parts[:3] != ["org", "jetbrains", "skiko"] or parts[3] not in _MODULES:
        return False
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", parts[5]) is None:
        return False
    prefix = parts[3] + "-" + version
    return parts[4] == version and parts[5].startswith(prefix) and len(parts[5]) > len(prefix)


def _valid_hosted_candidate(value):
    if not isinstance(value, str) or not value.endswith("/"):
        return False
    if "\\" in value or any(char.isspace() or unicodedata.category(char) == 'Cc' for char in value):
        return False
    try:
        parsed = urllib.parse.urlsplit(value)
        if parsed.scheme.lower() != "https" or not parsed.netloc or not parsed.hostname:
            return False
        if parsed.username is not None or parsed.password is not None:
            return False
        if parsed.query or parsed.fragment or not parsed.path.endswith("/"):
            return False
        parsed.port
    except ValueError:
        return False
    return True


def _validate_plan(plan):
    if not isinstance(plan, dict):
        _fail()
    required = (
        "schemaVersion", "version", "fileCount", "archiveBytes",
        "archiveSha256", "hostedCandidate", "files",
    )
    if any(key not in plan for key in required):
        _fail()
    if type(plan["schemaVersion"]) is not int or plan["schemaVersion"] != 1:
        _fail()
    version = plan["version"]
    if (not isinstance(version, str) or version in (".", "..")
            or _VERSION_RE.fullmatch(version) is None):
        _fail()
    if not _positive_int(plan["fileCount"]) or not _positive_int(plan["archiveBytes"]):
        _fail()
    if not _sha256(plan["archiveSha256"]):
        _fail()
    candidate = plan["hostedCandidate"]
    if not _valid_hosted_candidate(candidate):
        _fail()
    files = plan["files"]
    if not isinstance(files, list) or len(files) != plan["fileCount"]:
        _fail()

    expected = {}
    for item in files:
        if not isinstance(item, dict) or set(item) != {"path", "url", "bytes", "sha256"}:
            _fail()
        path = item["path"]
        if not _safe_plan_path(path, version) or path in expected:
            _fail()
        if not _positive_int(item["bytes"]) or not _sha256(item["sha256"]):
            _fail()
        if not isinstance(item["url"], str) or item["url"] != candidate + path:
            _fail()
        expected[path] = item
    return version, expected


def _signature(file_stat):
    return (
        file_stat.st_dev, file_stat.st_ino, file_stat.st_size,
        file_stat.st_mtime_ns, file_stat.st_ctime_ns,
    )


def _ensure_unchanged(fd, original):
    current = os.fstat(fd)
    if not stat.S_ISREG(current.st_mode) or _signature(current) != original:
        _fail()


def validate_archive(plan: dict, archive: pathlib.Path) -> dict:
    """Validate the local archive against a plan without extracting files."""
    version, expected = _validate_plan(plan)
    if not isinstance(archive, pathlib.Path):
        _fail()

    nofollow, nonblock = getattr(os, "O_NOFOLLOW", 0), getattr(os, "O_NONBLOCK", 0)
    if not _positive_int(nofollow) or not _positive_int(nonblock):
        _fail()
    flags = os.O_RDONLY | nofollow | nonblock
    flags |= getattr(os, "O_CLOEXEC", 0)
    fd = None
    stream = None
    try:
        fd = os.open(os.fspath(archive), flags)
        initial_stat = os.fstat(fd)
        if not stat.S_ISREG(initial_stat.st_mode):
            _fail()
        original = _signature(initial_stat)
        if initial_stat.st_size != plan["archiveBytes"]:
            _fail()

        archive_hash = hashlib.sha256()
        read_bytes = 0
        while True:
            chunk = os.read(fd, _CHUNK_SIZE)
            if not chunk:
                break
            archive_hash.update(chunk)
            read_bytes += len(chunk)
        _ensure_unchanged(fd, original)
        if read_bytes != plan["archiveBytes"] or archive_hash.hexdigest() != plan["archiveSha256"]:
            _fail()

        os.lseek(fd, 0, os.SEEK_SET)
        stream = os.fdopen(fd, "rb", closefd=False)
        seen = set()
        with tarfile.open(fileobj=stream, mode="r|gz") as tar:
            for member in tar:
                if member.type not in (tarfile.REGTYPE, tarfile.AREGTYPE) or member.sparse is not None:
                    _fail()
                path = member.name
                if not _safe_plan_path(path, version) or path not in expected or path in seen:
                    _fail()
                item = expected[path]
                if member.size != item["bytes"]:
                    _fail()
                contents = tar.extractfile(member)
                if contents is None:
                    _fail()
                digest = hashlib.sha256()
                count = 0
                with contents:
                    while True:
                        chunk = contents.read(_CHUNK_SIZE)
                        if not chunk:
                            break
                        digest.update(chunk)
                        count += len(chunk)
                if count != item["bytes"] or digest.hexdigest() != item["sha256"]:
                    _fail()
                seen.add(path)
        _ensure_unchanged(fd, original)
        if seen != set(expected):
            _fail()
    except ValueError:
        raise
    except (OSError, EOFError, UnicodeError, zlib.error, tarfile.TarError):
        _fail()
    finally:
        if stream is not None:
            stream.close()
        if fd is not None:
            os.close(fd)

    return {
        "schemaVersion": 1,
        "kind": "nexus-plan-archive-consistency",
        "version": version,
        "validatedFileCount": len(expected),
        "archiveSha256": plan["archiveSha256"],
        "archiveBytes": plan["archiveBytes"],
        "validationStatus": "pass-bounded",
        "uploaded": False,
        "remoteConsumerValidated": False,
    }


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(_ERROR)
        result[key] = value
    return result


def _finite_float(value):
    number = float(value)
    if not math.isfinite(number):
        raise ValueError(_ERROR)
    return number


def _reject_constant(_value):
    raise ValueError(_ERROR)


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 2:
        print(_ERROR, file=sys.stderr)
        return 2
    try:
        with open(args[0], "r", encoding="utf-8") as plan_file:
            plan = json.load(
                plan_file,
                object_pairs_hook=_unique_object,
                parse_float=_finite_float,
                parse_constant=_reject_constant,
            )
        result = validate_archive(plan, pathlib.Path(args[1]))
        print(json.dumps(result, separators=(",", ":")))
        return 0
    except (OSError, UnicodeError, ValueError, TypeError, json.JSONDecodeError):
        print(_ERROR, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
