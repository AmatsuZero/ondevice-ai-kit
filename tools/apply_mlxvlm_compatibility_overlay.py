"""Prepare exact-revision isolated F09 copies; never adopt policy or run a build."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tempfile


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _path(value: str) -> PurePosixPath:
    _require(isinstance(value, str) and bool(value), "path must be a nonempty string")
    path = PurePosixPath(value)
    _require(not path.is_absolute() and ".." not in path.parts and str(path) == value
             and "\\" not in value and not any(c in value for c in "\t\r\n"),
             f"noncanonical path: {value}")
    _require(path.parts and path.parts[0] != ".git", f"unsupported path: {value}")
    return path


def _git(repo: Path, *args: str) -> bytes:
    result = subprocess.run(["git", "--no-lazy-fetch", "-C", str(repo), *args],
                            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"}, capture_output=True)
    _require(result.returncode == 0, f"Git object read failed for {repo.name}: {' '.join(args[:2])}")
    return result.stdout


def _copy_committed(repo: Path, revision: str, destination: Path, links: dict) -> None:
    resolved = _git(repo, "rev-parse", "--verify", f"{revision}^{{commit}}").decode().strip()
    _require(resolved == revision, f"exact revision unavailable for {repo.name}")
    tree = _git(repo, "ls-tree", "-r", "-z", revision)
    entries = []
    for entry in tree.split(b"\0"):
        if not entry:
            continue
        metadata, name = entry.split(b"\t", 1)
        mode, kind, oid = metadata.split()
        _require((mode in {b"100644", b"100755"} or mode == b"120000" and name.decode() in links) and kind == b"blob",
                 f"unsupported symlink/submodule in {repo.name}: {name.decode()}")
        _path(name.decode())
        entries.append((name.decode(), mode, oid))
    regular = {name for name, mode, _ in entries if mode != b"120000"}
    _require({name for name, mode, _ in entries if mode == b"120000"} == set(links),
             f"declared symlink inventory mismatch: {repo.name}")
    for name, link in links.items():
        target = (_path(name).parent / _path(link["target"])).as_posix()
        _require(target in regular, f"symlink target must be a committed regular file: {repo.name}/{name}")
    # Read raw blobs: git archive can omit/export-substitute committed files.
    result = subprocess.run(["git", "--no-lazy-fetch", "-C", str(repo), "cat-file", "--batch"],
                            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
                            input=b"".join(oid + b"\n" for _, _, oid in entries), capture_output=True)
    _require(result.returncode == 0, f"Git blob read failed: {repo.name}")
    offset = 0
    destination.mkdir()
    for name, mode, oid in entries:
        end = result.stdout.find(b"\n", offset)
        header = result.stdout[offset:end].split()
        _require(end >= offset and len(header) == 3 and header[:2] == [oid, b"blob"]
                 and header[2].isdigit(), f"invalid Git blob response: {name}")
        size = int(header[2])
        data = result.stdout[end + 1:end + 1 + size]
        offset = end + 1 + size
        _require(len(data) == size and result.stdout[offset:offset + 1] == b"\n", f"truncated Git blob: {name}")
        offset += 1
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if mode == b"120000":
            _require(data.decode() == links[name]["target"] and _sha(data) == links[name]["sha256"],
                     f"symlink target/hash mismatch: {repo.name}/{name}")
            target.symlink_to(data.decode())
        else:
            target.write_bytes(data)
            target.chmod(0o755 if mode == b"100755" else 0o644)
    _require(offset == len(result.stdout), f"unexpected Git blob response: {repo.name}")


def _inventory(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): _sha(p.read_bytes())
            for p in sorted(root.rglob("*")) if p.is_file() and not p.is_symlink()}


def apply_candidate(candidate: Path, lock: Path, dependencies: Path, output: Path) -> Path:
    """Hash-check and patch committed dependency copies into one fresh directory.

    Candidate status is provenance, not permission to adopt a maintenance policy.
    The caller still owns that separate decision before using copies in a build.
    """
    candidate, lock, dependencies = (Path(p).resolve(strict=True) for p in (candidate, lock, dependencies))
    output = Path(output).absolute()
    _require(not output.exists() and not output.is_symlink(), f"output already exists: {output}")
    resolved_output = output.resolve()
    for source in (candidate, dependencies, lock):
        _require(not resolved_output.is_relative_to(source) and not source.is_relative_to(resolved_output),
                 f"output overlaps an input: {source}")
    raw_manifest = (candidate / "manifest.json").read_bytes()
    manifest = json.loads(raw_manifest)
    _require(isinstance(manifest, dict) and manifest.get("schemaVersion") == 1,
             "unsupported candidate manifest")
    _require(manifest.get("status") == "draft-not-adopted", "candidate policy status must remain draft-not-adopted")
    lock_data = lock.read_bytes()
    _require(_sha(lock_data) == manifest.get("productionLockSha256"), "production lock SHA-256 mismatch")
    lock_json = json.loads(lock_data)
    pins = lock_json.get("pins", [])
    _require(isinstance(pins, list) and all(isinstance(p, dict) for p in pins), "invalid lock pins")
    identities = [p.get("identity") for p in pins]
    _require(all(isinstance(p, str) for p in identities) and len(set(identities)) == len(identities), "duplicate/invalid lock pin")
    _require(all(isinstance(p.get("state"), dict) for p in pins), "invalid lock pin state")
    states = {p["identity"]: p["state"] for p in pins}
    revisions, bindings = manifest.get("revisions"), manifest.get("runtimeBinding")
    _require(isinstance(revisions, dict) and revisions and isinstance(bindings, dict) and bindings,
             "revision/runtime bindings are required")
    for name, revision in revisions.items():
        _require(isinstance(name, str) and re.fullmatch(r"[A-Za-z0-9_-]+", name) is not None,
                 "invalid package path")
        _require(isinstance(revision, str) and re.fullmatch(r"[0-9a-f]{40}", revision) is not None,
                 f"invalid revision binding: {name}")
        _require(states.get(name, {}).get("revision") == revision, f"revision binding mismatch: {name}")
    for name, binding in bindings.items():
        state = states.get(name, {})
        _require(isinstance(state.get("version"), str) and isinstance(state.get("revision"), str)
                 and re.fullmatch(r"[0-9a-f]{40}", state["revision"]) is not None
                 and binding == f"{state['version']}@{state['revision']}", f"runtime binding mismatch: {name}")
    links = {name: {} for name in revisions}
    link_entries = manifest.get("symlinks", [])
    _require(isinstance(link_entries, list), "symlinks must be an array")
    for entry in link_entries:
        _require(isinstance(entry, dict) and entry.get("package") in links, "invalid symlink package")
        name, path = entry["package"], str(_path(entry.get("path")))
        target = str(_path(entry.get("target")))
        _require(path not in links[name] and isinstance(entry.get("sha256"), str)
                 and re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]) is not None,
                 "duplicate symlink or invalid symlink SHA-256")
        links[name][path] = {"target": target, "sha256": entry["sha256"]}
    entries = manifest.get("files")
    _require(isinstance(entries, list) and entries, "declared files are required")
    declared = {name: {} for name in revisions}
    for entry in entries:
        _require(isinstance(entry, dict) and entry.get("package") in declared, "invalid file package")
        name, path = entry["package"], str(_path(entry.get("path")))
        _require(path not in declared[name], f"duplicate declared path: {name}/{path}")
        for key in ("originalSha256", "patchedSha256"):
            _require(isinstance(entry.get(key), str) and re.fullmatch(r"[0-9a-f]{64}", entry[key]) is not None,
                     f"invalid SHA-256 for {name}/{path}")
        declared[name][path] = entry
    patch_hashes = manifest.get("patchSha256")
    _require(isinstance(patch_hashes, dict) and set(patch_hashes) == set(revisions), "patch packages must match revision bindings")
    patches = {}
    for name in revisions:
        data = (candidate / f"{name}.patch").read_bytes()
        _require(_sha(data) == patch_hashes[name], f"patch SHA-256 mismatch: {name}")
        old = [line[6:] for line in data.decode().splitlines() if line.startswith("--- a/")]
        new = [line[6:] for line in data.decode().splitlines() if line.startswith("+++ b/")]
        headers = [line for line in data.decode().splitlines() if line.startswith(("--- ", "+++ "))]
        _require(old == new and len(headers) == 2 * len(old)
                 and len(old) == len(set(old)) and set(old) == set(declared[name]) and bool(old),
                 f"patch paths must match declared files: {name}")
        patches[name] = data

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".mlxvlm-overlay-", dir=output.parent) as scratch:
        stage = Path(scratch) / "prepared"
        stage.mkdir()
        inventories = {}
        for name, revision in revisions.items():
            repo = dependencies / name
            _require(repo.resolve().is_relative_to(dependencies) and not repo.is_symlink(), f"dependency symlink escape: {name}")
            target = stage / name
            _copy_committed(repo, revision, target, links[name])
            before = _inventory(target)
            for path, entry in declared[name].items():
                _require(before.get(path) == entry["originalSha256"], f"input SHA-256 mismatch: {name}/{path}")
            result = subprocess.run(["patch", "--batch", "--forward", "--fuzz=0", "-p1"],
                                    cwd=target, input=patches[name], capture_output=True)
            _require(result.returncode == 0, f"patch failed: {name}: {result.stdout.decode(errors='replace').strip()}")
            after = _inventory(target)
            expected = {**before, **{path: entry["patchedSha256"] for path, entry in declared[name].items()}}
            _require(after == expected, f"output SHA-256 inventory mismatch: {name}")
            actual_links = {p.relative_to(target).as_posix(): {"target": str(p.readlink()), "sha256": _sha(str(p.readlink()).encode())}
                            for p in target.rglob("*") if p.is_symlink()}
            _require(actual_links == links[name], f"output symlink inventory mismatch: {name}")
            inventories[name] = after
        # Inputs are never patched; recheck policy inputs before publishing copies.
        _require(lock.read_bytes() == lock_data and (candidate / "manifest.json").read_bytes() == raw_manifest,
                 "candidate/lock changed during preparation")
        for name, data in patches.items():
            _require((candidate / f"{name}.patch").read_bytes() == data, f"patch changed during preparation: {name}")
        receipt = {"schemaVersion": 1, "kind": "apple-mlxvlm-isolated-compatibility-copy",
                   "policyStatus": manifest["status"], "buildStatus": "not-run",
                   "manifestSha256": _sha(raw_manifest), "productionLockSha256": _sha(lock_data),
                   "revisions": revisions, "runtimeBinding": bindings,
                   "patchSha256": patch_hashes, "files": inventories, "symlinks": links}
        (stage / "overlay-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        _require(not output.exists() and not output.is_symlink(), f"output already exists: {output}")
        stage.rename(output)
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("lock", type=Path)
    parser.add_argument("dependencies", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    try:
        print(apply_candidate(args.candidate, args.lock, args.dependencies, args.output))
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
