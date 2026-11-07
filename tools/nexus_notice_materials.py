"""Offline consistency check for retained Nexus notice materials."""

import hashlib
import json
import math
import os
import pathlib
import re
import stat
import sys

from tools.nexus_get_receipts import _read_body

_ERROR = "notice material validation failed"
_MAX_INDEX_BYTES = 1024 * 1024
_HEX = re.compile(r"[0-9a-f]{64}\Z")
_VERSION = re.compile(r"[A-Za-z0-9.-]+\Z")
_COMPONENT = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*\Z")


def _fail():
    raise ValueError(_ERROR)


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            _fail()
        result[key] = value
    return result


def _finite_float(value):
    number = float(value)
    if not math.isfinite(number):
        _fail()
    return number


def _bounded_int(value):
    number = int(value)
    if abs(number) > sys.maxsize:
        _fail()
    return number


def _reject_constant(_value):
    _fail()


def validate_materials(index_bytes: bytes, index_sha256: str,
                       candidate_version: str,
                       collected_root: pathlib.Path) -> dict:
    """Check retained bytes; provenance metadata receives no approval ruling."""
    try:
        if (type(index_bytes) is not bytes or not 0 < len(index_bytes) <= _MAX_INDEX_BYTES
                or not isinstance(index_sha256, str) or not _HEX.fullmatch(index_sha256)
                or hashlib.sha256(index_bytes).hexdigest() != index_sha256
                or not isinstance(candidate_version, str)
                or not _VERSION.fullmatch(candidate_version)
                or candidate_version in (".", "..")
                or not isinstance(collected_root, pathlib.Path)):
            _fail()

        index = json.loads(
            index_bytes.decode("utf-8"), object_pairs_hook=_unique_object,
            parse_float=_finite_float, parse_int=_bounded_int,
            parse_constant=_reject_constant,
        )
        if (not isinstance(index, dict)
                or not {"candidateVersion", "files"}.issubset(index)
                or index["candidateVersion"] != candidate_version
                or not isinstance(index["files"], list) or not index["files"]):
            _fail()
        if "schemaVersion" in index and (
                type(index["schemaVersion"]) is not int or index["schemaVersion"] != 1):
            _fail()

        expected = {}
        for item in index["files"]:
            if not isinstance(item, dict) or set(item) != {"path", "bytes", "sha256"}:
                _fail()
            path = item["path"]
            if (not isinstance(path, str) or path == "NOTICE-INDEX.json"
                    or not path.isascii()
                    or any(not _COMPONENT.fullmatch(part) for part in path.split("/"))
                    or path in expected
                    or type(item["bytes"]) is not int or item["bytes"] <= 0
                    or not isinstance(item["sha256"], str)
                    or not _HEX.fullmatch(item["sha256"])):
                _fail()
            expected[path] = item

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
            root_fd = os.open(os.fspath(collected_root), dir_flags)
            if not stat.S_ISDIR(os.fstat(root_fd).st_mode):
                _fail()
            index_item = {"bytes": len(index_bytes), "sha256": index_sha256}
            _read_body(root_fd, "NOTICE-INDEX.json", index_item["bytes"],
                       index_item["sha256"], (dir_flags, file_flags))
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
            "kind": "nexus-notice-material-consistency",
            "candidateVersion": candidate_version,
            "indexSha256": index_sha256,
            "validatedMaterialCount": len(expected) + 1,
            "validationStatus": "pass-bounded",
            "networkPerformedByThisTool": False,
            "remoteConsumerValidated": False,
            "legalApproval": False,
        }
    except Exception:
        raise ValueError(_ERROR) from None
