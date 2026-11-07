"""Offline consistency checks for retained Nexus GET receipts and bodies."""

import hashlib
import json
import math
import os
import pathlib
import stat
import sys

from tools.nexus_plan_archive import _validate_plan

_ERROR = "GET receipt validation failed"
_CHUNK_SIZE = 1024 * 1024
_RECEIPT_KEYS = {
    "schemaVersion", "kind", "version", "fileCount", "entries",
}
_ENTRY_KEYS = {
    "path", "requestUrl", "finalUrl", "method", "status",
    "authenticated", "redirects", "bytes", "sha256",
}


def _fail():
    raise ValueError(_ERROR)


def _signature(file_stat):
    return (
        file_stat.st_dev, file_stat.st_ino, file_stat.st_size,
        file_stat.st_mtime_ns, file_stat.st_ctime_ns,
    )


def _read_body(root_fd, path, expected_bytes, expected_sha256, flags):
    opened = []
    try:
        current_fd = root_fd
        parts = path.split("/")
        dir_flags, file_flags = flags
        for part in parts[:-1]:
            current_fd = os.open(part, dir_flags, dir_fd=current_fd)
            opened.append(current_fd)
        fd = os.open(parts[-1], file_flags, dir_fd=current_fd)
        opened.append(fd)

        original_stat = os.fstat(fd)
        if not stat.S_ISREG(original_stat.st_mode) or original_stat.st_size != expected_bytes:
            _fail()
        original_signature = _signature(original_stat)
        digest = hashlib.sha256()
        read_bytes = 0
        while True:
            remaining = expected_bytes - read_bytes + 1
            if remaining <= 0:
                _fail()
            chunk = os.read(fd, min(_CHUNK_SIZE, remaining))
            if not chunk:
                break
            digest.update(chunk)
            read_bytes += len(chunk)
            if read_bytes > expected_bytes:
                _fail()

        final_stat = os.fstat(fd)
        if (not stat.S_ISREG(final_stat.st_mode)
                or _signature(final_stat) != original_signature
                or read_bytes != expected_bytes
                or digest.hexdigest() != expected_sha256):
            _fail()
    finally:
        close_failed = False
        for fd in reversed(opened):
            try:
                os.close(fd)
            except OSError:
                close_failed = True
        if close_failed:
            _fail()


def validate_receipts(plan: dict, receipts: dict, bodies_root: pathlib.Path) -> dict:
    """Validate receipt metadata and the corresponding local response bodies."""
    try:
        version, expected = _validate_plan(plan)
        if not isinstance(bodies_root, pathlib.Path):
            _fail()
        if not isinstance(receipts, dict) or set(receipts) != _RECEIPT_KEYS:
            _fail()
        if (type(receipts["schemaVersion"]) is not int
                or receipts["schemaVersion"] != 1
                or receipts["kind"] != "nexus-get-receipts"
                or receipts["version"] != version
                or type(receipts["fileCount"]) is not int
                or receipts["fileCount"] != len(expected)
                or not isinstance(receipts["entries"], list)
                or len(receipts["entries"]) != len(expected)):
            _fail()

        seen = set()
        for entry in receipts["entries"]:
            if not isinstance(entry, dict) or set(entry) != _ENTRY_KEYS:
                _fail()
            path = entry["path"]
            if not isinstance(path, str) or path not in expected or path in seen:
                _fail()
            item = expected[path]
            if (not isinstance(entry["requestUrl"], str)
                    or entry["requestUrl"] != item["url"]
                    or not isinstance(entry["finalUrl"], str)
                    or entry["finalUrl"] != item["url"]
                    or entry["method"] != "GET"
                    or type(entry["status"]) is not int
                    or entry["status"] != 200
                    or entry["authenticated"] is not False
                    or type(entry["redirects"]) is not list
                    or entry["redirects"] != []
                    or type(entry["bytes"]) is not int
                    or entry["bytes"] != item["bytes"]
                    or not isinstance(entry["sha256"], str)
                    or entry["sha256"] != item["sha256"]):
                _fail()
            seen.add(path)
        if seen != set(expected):
            _fail()

        nofollow = getattr(os, "O_NOFOLLOW", 0)
        directory = getattr(os, "O_DIRECTORY", 0)
        nonblock = getattr(os, "O_NONBLOCK", 0)
        if (type(nofollow) is not int or nofollow <= 0
                or type(directory) is not int or directory <= 0
                or type(nonblock) is not int or nonblock <= 0
                or os.open not in os.supports_dir_fd):
            _fail()
        dir_flags = os.O_RDONLY | nofollow | directory | getattr(os, "O_CLOEXEC", 0)
        file_flags = os.O_RDONLY | nofollow | nonblock | getattr(os, "O_CLOEXEC", 0)
        root_fd = None
        try:
            root_fd = os.open(os.fspath(bodies_root), dir_flags)
            if not stat.S_ISDIR(os.fstat(root_fd).st_mode):
                _fail()
            for path, item in expected.items():
                _read_body(root_fd, path, item["bytes"], item["sha256"],
                           (dir_flags, file_flags))
        finally:
            if root_fd is not None:
                try:
                    os.close(root_fd)
                except OSError:
                    _fail()

        return {
            "schemaVersion": 1,
            "kind": "nexus-get-receipt-consistency",
            "version": version,
            "validatedFileCount": len(expected),
            "validatedBytes": sum(item["bytes"] for item in expected.values()),
            "validationStatus": "pass-bounded",
            "networkPerformedByThisTool": False,
            "anonymousExecutionVerified": False,
            "remoteConsumerValidated": False,
            "uploaded": False,
        }
    except Exception:
        raise ValueError(_ERROR) from None


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


def _load_json(path):
    with open(path, "r", encoding="utf-8") as source:
        return json.load(
            source, object_pairs_hook=_unique_object,
            parse_float=_finite_float, parse_constant=_reject_constant,
        )


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 3:
        print(_ERROR, file=sys.stderr)
        return 2
    try:
        plan = _load_json(args[0])
        receipts = _load_json(args[1])
        result = validate_receipts(plan, receipts, pathlib.Path(args[2]))
        print(json.dumps(result, separators=(",", ":")))
        return 0
    except Exception:
        print(_ERROR, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
