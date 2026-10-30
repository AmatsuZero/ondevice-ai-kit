"""Copy only committed Apple package bytes into a fresh F09 build snapshot."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


EXPECTED_LOCK_SHA256 = "e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858"
_COMMIT = re.compile(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}\Z")


def _git(root: Path, *arguments: str) -> bytes:
    result = subprocess.run(["git", *arguments], cwd=root, capture_output=True)
    if result.returncode:
        raise ValueError(f"git {' '.join(arguments[:2])} failed: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout


def _included(path: str) -> bool:
    parts = Path(path).parts
    if len(parts) < 2 or parts[0] not in {"apple", "apple-mlx"}:
        return False
    if len(parts) == 2 and parts[1] in {"Package.swift", "Package.resolved", "diagnostic-patches.json"}:
        return True
    return len(parts) > 2 and parts[1] in {"Sources", "Tests"}


def snapshot(root: Path, source_commit: str, output: Path) -> Path:
    """Materialize package files from one exact commit, excluding workspace edits."""

    root = Path(root).resolve()
    output = Path(output).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError(f"snapshot output already exists: {output}")
    if not _COMMIT.fullmatch(source_commit):
        raise ValueError("source_commit must be a full immutable commit ID")
    resolved = _git(root, "rev-parse", "--verify", f"{source_commit}^{{commit}}").decode().strip()
    if resolved.lower() != source_commit.lower():
        raise ValueError("source_commit does not resolve to the requested exact commit")
    tree = _git(root, "ls-tree", "-r", "-z", source_commit, "--", "apple", "apple-mlx")
    entries: list[tuple[str, bytes]] = []
    for item in tree.split(b"\0"):
        if not item:
            continue
        metadata, raw_path = item.split(b"\t", 1)
        mode, kind, object_id = metadata.decode("ascii").split()
        path = raw_path.decode("utf-8")
        if not _included(path):
            continue
        if mode != "100644" and mode != "100755":
            raise ValueError(f"unsupported tracked package file mode at {path}: {mode}")
        if kind != "blob":
            raise ValueError(f"unsupported tracked package object at {path}: {kind}")
        data = _git(root, "cat-file", "blob", object_id)
        entries.append((path, data))

    package_paths = {name for name, _ in entries}
    for required in ("apple/Package.swift", "apple-mlx/Package.swift", "apple-mlx/Package.resolved"):
        if required not in package_paths:
            raise ValueError(f"committed package file missing: {required}")
    lock = next(data for name, data in entries if name == "apple-mlx/Package.resolved")
    lock_hash = hashlib.sha256(lock).hexdigest()
    if lock_hash != EXPECTED_LOCK_SHA256:
        raise ValueError(f"committed lock SHA-256 mismatch: {lock_hash}")

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".mlxvlm-snapshot-", dir=output.parent) as scratch:
        stage = Path(scratch) / "snapshot"
        stage.mkdir()
        inventory = []
        digest = hashlib.sha256()
        for name, data in entries:
            relative = Path("packages") / name
            destination = stage / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
            file_hash = hashlib.sha256(data).hexdigest()
            inventory.append({"path": str(relative), "sha256": file_hash})
            digest.update(str(relative).encode())
            digest.update(b"\0")
            digest.update(data)
            digest.update(b"\0")
        manifest = {
            "schemaVersion": 1,
            "kind": "apple-mlxvlm-committed-source-snapshot",
            "sourceCommit": resolved,
            "lockSHA256": lock_hash,
            "treeSHA256": digest.hexdigest(),
            "files": inventory,
            "diagnosticPatchManifest": None,
        }
        if "apple-mlx/diagnostic-patches.json" in package_paths:
            manifest["diagnosticPatchManifest"] = "packages/apple-mlx/diagnostic-patches.json"
        (stage / "source-inventory.json").write_text(json.dumps(manifest, indent=2) + "\n")
        stage.rename(output)
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("source_commit")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    try:
        print(snapshot(args.root, args.source_commit, args.output))
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
