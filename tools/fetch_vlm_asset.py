#!/usr/bin/env python3
"""Stage pinned model files, then atomically publish only verified assets."""

import hashlib
import json
import os
import shutil
import sys
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

try:
    from validate_vlm_manifest import validate_manifest
except ModuleNotFoundError:  # imported by the test loader from repository root
    from tools.validate_vlm_manifest import validate_manifest


@dataclass(frozen=True)
class VerifiedAsset:
    directory: Path
    total_bytes: int


def _hash_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def fetch_vlm_asset(manifest: dict, destination: Path, source: Path) -> VerifiedAsset:
    """Verify an existing local staging directory and publish as one directory rename."""
    if manifest.get("status") != "verified":
        raise ValueError("verified manifest required")
    if destination.exists():
        raise FileExistsError(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=f".{destination.name}.", dir=destination.parent))
    total = 0
    try:
        for item in manifest["files"]:
            relative = Path(item["path"])
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("unsafe file path")
            origin = source / relative
            if not origin.is_file() or origin.is_symlink():
                raise FileNotFoundError(origin)
            target = temporary / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(origin, target)
            actual_hash, actual_size = _hash_file(target)
            if actual_hash != item["sha256"]:
                raise ValueError(f"sha256 mismatch: {relative}")
            if actual_size != item["bytes"]:
                raise ValueError(f"size mismatch: {relative}")
            total += actual_size
        os.replace(temporary, destination)
        return VerifiedAsset(destination, total)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


def download_candidate(manifest: dict, destination: Path, base_url: str = "https://huggingface.co/") -> dict:
    """Download pinned files into ignored staging and report hashes; never publish them.

    Existing ``.part`` files are resumed with HTTP Range requests. A file is
    renamed into place only after its expected size and SHA256 are verified.
    """
    if manifest.get("status") != "candidate":
        raise ValueError("candidate manifest required")
    destination.mkdir(parents=True, exist_ok=True)
    observed = {}
    for item in manifest["files"]:
        relative = Path(item["path"])
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError("unsafe file path")
        url = (base_url.rstrip("/") + "/" + quote(manifest["modelId"], safe="/")
               + "/resolve/" + manifest["modelRevision"] + "/" + quote(item["path"], safe="/"))
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_name(target.name + ".part")
        if target.is_file():
            digest, size = _hash_file(target)
            if size == item["bytes"] and (item.get("sha256") in (None, digest)) and (
                item.get("sourceSha256") in (None, digest)
            ):
                observed[item["path"]] = {"sha256": digest, "bytes": size}
                continue
        offset = partial.stat().st_size if partial.exists() else 0
        request = urllib.request.Request(url)
        if offset:
            request.add_header("Range", f"bytes={offset}-")
        with urllib.request.urlopen(request, timeout=90) as response:
            append = offset > 0 and response.status == 206
            if not append:
                offset = 0
            with partial.open("ab" if append else "wb") as output:
                shutil.copyfileobj(response, output, length=1024 * 1024)
        digest, size = _hash_file(partial)
        if size != item["bytes"]:
            raise ValueError(f"size mismatch: {relative}")
        expected_hash = item.get("sha256")
        if expected_hash and digest != expected_hash:
            raise ValueError(f"sha256 mismatch: {relative}")
        source_hash = item.get("sourceSha256")
        if source_hash and digest != source_hash:
            raise ValueError(f"source sha256 mismatch: {relative}")
        partial.replace(target)
        observed[item["path"]] = {"sha256": digest, "bytes": size}
    return observed


if __name__ == "__main__":
    if len(sys.argv) != 4 or sys.argv[1] not in ("stage", "publish"):
        raise SystemExit("usage: fetch_vlm_asset.py stage|publish MANIFEST DESTINATION")
    mode, manifest_path, destination_path = sys.argv[1:]
    manifest = validate_manifest(Path(manifest_path))
    destination = Path(destination_path)
    if mode == "stage":
        print(json.dumps(download_candidate(manifest, destination), indent=2, sort_keys=True))
    else:
        source = destination.parent / "staging"
        print(fetch_vlm_asset(manifest, destination, source))
