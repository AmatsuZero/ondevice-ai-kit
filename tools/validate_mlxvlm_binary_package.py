"""Validate existing per-framework ZIPs and an isolated local SwiftPM graph.

This is a preparation gate, not a consumer build, runtime or remote-URL gate.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tempfile
import zipfile

from tools.inspect_mlxvlm_xcframework import inspect_xcframework
from tools.validate_mlxvlm_binary_artifact import validate_manifest, _sha256_file, _sha256_tree


def _swift(workspace: Path, *args: str) -> str:
    command = ["rtk", "proxy", "swift", "package", "--disable-sandbox",
               "--cache-path", str(workspace / "cache"),
               "--config-path", str(workspace / "config"),
               "--security-path", str(workspace / "security"),
               "--disable-netrc", "--disable-keychain", *args]
    environment = dict(os.environ, CLANG_MODULE_CACHE_PATH=str(workspace / "module-cache"),
                       SWIFTPM_MODULECACHE_OVERRIDE=str(workspace / "module-cache"))
    result = subprocess.run(command, cwd=workspace, env=environment, text=True, capture_output=True, timeout=120)
    if result.returncode:
        raise ValueError(f"SwiftPM {args[0]} failed: {result.stderr.strip()}")
    return result.stdout


def _extract_matching(archive: Path, artifact: Path, destination: Path) -> None:
    expected = {str(p.relative_to(artifact.parent)): p
                for p in artifact.rglob("*") if p.is_file()}
    if any(p.is_symlink() for p in [artifact, *artifact.rglob("*")]):
        raise ValueError("artifact symlinks are unsupported by the local ZIP gate")
    try:
        with zipfile.ZipFile(archive) as source:
            seen: set[str] = set()
            files: set[str] = set()
            for entry in source.infolist():
                name = entry.filename
                path = PurePosixPath(name.rstrip("/"))
                if (not path.parts or path.is_absolute() or ".." in path.parts
                        or str(path) != name.rstrip("/") or "\\" in name):
                    raise ValueError(f"unsafe archive path: {name}")
                if path.parts[0] != artifact.name:
                    raise ValueError(f"XCFramework must be at archive root: {name}")
                if name in seen:
                    raise ValueError(f"duplicate ZIP entry: {name}")
                seen.add(name)
                mode = entry.external_attr >> 16
                if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) and not
                        (stat.S_ISREG(mode) or stat.S_ISDIR(mode))):
                    raise ValueError(f"unsupported ZIP entry type: {name}")
                if entry.is_dir():
                    if not any(p.startswith(str(path) + "/") for p in expected):
                        raise ValueError(f"unexpected ZIP directory: {name}")
                    continue
                if name not in expected:
                    raise ValueError(f"ZIP inventory contains undeclared file: {name}")
                files.add(name)
                original = expected[name]
                if entry.file_size != original.stat().st_size:
                    raise ValueError(f"ZIP content mismatch: {name}")
                target = destination / name
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(entry) as stream, target.open("wb") as output:
                    shutil.copyfileobj(stream, output)
                if _sha256_file(target) != _sha256_file(original):
                    raise ValueError(f"ZIP content mismatch: {name}")
            if files != set(expected):
                raise ValueError("ZIP inventory is missing artifact files")
    except (zipfile.BadZipFile, RuntimeError, NotImplementedError) as error:
        raise ValueError(f"invalid ZIP archive: {archive.name}: {error}") from error


def validate_package(artifact_root: Path, manifest_path: Path,
                     archives: dict[str, Path]) -> dict:
    """Read fixed artifacts/ZIPs; create and remove only a temporary local graph."""
    root = Path(artifact_root).resolve(strict=True)
    manifest_path = Path(manifest_path).resolve(strict=True)
    validate_manifest(manifest_path, root)
    payload = json.loads(manifest_path.read_text())
    entries = payload["artifacts"]
    if set(archives) != {e["path"] for e in entries}:
        raise ValueError("archive coverage must exactly match artifact paths")
    modules: list[str] = []
    basenames: set[str] = set()
    for entry in entries:
        path = entry["path"]
        if str(PurePosixPath(path)) != path or ".." in PurePosixPath(path).parts or "\\" in path:
            raise ValueError("artifact path must be canonical")
        if len(entry["modules"]) != 1 or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", entry["modules"][0]):
            raise ValueError("one Swift module name per artifact is required")
        module = entry["modules"][0]
        artifact = root / path
        if artifact.name in basenames:
            raise ValueError("duplicate XCFramework basename")
        basenames.add(artifact.name)
        modules.append(module)
        inspect_xcframework(artifact, module, entry["minimumOS"])
    if "ODAIKitAppleMLXVLM" not in modules:
        raise ValueError("AppleMLXVLM binary module is required")

    receipts = []
    with tempfile.TemporaryDirectory(prefix="mlxvlm-binary-package-") as directory:
        workspace = Path(directory)
        package = workspace / "BinaryPackage"
        binaries = package / "Binaries"
        binaries.mkdir(parents=True)
        archive_copies = package / "Archives"
        archive_copies.mkdir()
        for entry in entries:
            archive = Path(archives[entry["path"]]).resolve(strict=True)
            if archive.suffix != ".zip":
                raise ValueError("binary archive must have .zip extension")
            snapshot = archive_copies / (entry["modules"][0] + ".zip")
            shutil.copyfile(archive, snapshot)
            _extract_matching(snapshot, root / entry["path"], binaries)
            checksum = _swift(workspace, "compute-checksum", str(snapshot)).strip()
            if checksum != _sha256_file(snapshot):
                raise ValueError(f"SwiftPM checksum mismatch: {archive.name}")
            receipts.append({"artifact": entry["path"], "checksum": checksum,
                             "inventoryStatus": "matched"})
        targets = [f'.binaryTarget(name: {json.dumps(e["modules"][0])}, path: {json.dumps("Archives/" + e["modules"][0] + ".zip")})'
                   for e in entries]
        (package / "Package.swift").write_text(
            '// swift-tools-version: 6.0\nimport PackageDescription\n'
            'let package = Package(name: "MLXVLMBinaryClosure", platforms: [.iOS(.v18), .macOS(.v15)], '
            'products: [.library(name: "ODAIKitAppleMLXVLM", targets: ' + json.dumps(modules) +
            ')], targets: [' + ",\n".join(targets) + '])\n')
        graph = json.loads(_swift(workspace, "--package-path", str(package), "describe", "--type", "json"))
        actual = graph["targets"]
        if (graph.get("dependencies") or len(actual) != len(modules)
                or {t["name"] for t in actual} != set(modules)
                or any(t["type"] != "binary" for t in actual)):
            raise ValueError("SwiftPM package graph is not the declared binary closure")
        consumer = workspace / "Consumer"
        sources = consumer / "Sources" / "Probe"
        sources.mkdir(parents=True)
        (sources / "Probe.swift").write_text("import ODAIKitAppleMLXVLM\n")
        (consumer / "Package.swift").write_text(
            '// swift-tools-version: 6.0\nimport PackageDescription\n'
            'let package = Package(name: "BinaryConsumer", platforms: [.iOS(.v18), .macOS(.v15)], '
            'dependencies: [.package(name: "MLXVLMBinaryClosure", path: "../BinaryPackage")], '
            'targets: [.target(name: "Probe", dependencies: [.product(name: "ODAIKitAppleMLXVLM", package: "MLXVLMBinaryClosure")])])\n')
        consumer_graph = json.loads(_swift(workspace, "--package-path", str(consumer), "describe", "--type", "json"))
        dependencies = consumer_graph["dependencies"]
        if (len(dependencies) != 1 or dependencies[0].get("type") != "fileSystem"
                or Path(dependencies[0]["path"]).resolve() != package.resolve()):
            raise ValueError("consumer graph must depend only on the local binary package")
        if len(consumer_graph["targets"]) != 1 or consumer_graph["targets"][0]["name"] != "Probe":
            raise ValueError("consumer graph contains unexpected source targets")
        _swift(workspace, "--package-path", str(consumer), "resolve")
        resolved = json.loads(_swift(workspace, "--package-path", str(consumer), "show-dependencies", "--format", "json"))
        children = resolved.get("dependencies", [])
        if (len(children) != 1 or Path(children[0]["path"]).resolve() != package.resolve()
                or children[0].get("dependencies")):
            raise ValueError("resolved graph is not the isolated binary package")
        for entry in entries:
            candidates = list(consumer.glob(".build/artifacts/**/" + Path(entry["path"]).name))
            if len(candidates) != 1 or _sha256_tree(candidates[0]) != _sha256_tree(root / entry["path"]):
                raise ValueError("SwiftPM resolved ZIP contents do not match the artifact")
        version = subprocess.check_output(["rtk", "proxy", "swift", "--version"], text=True, stderr=subprocess.STDOUT).strip()
    return {"schemaVersion": 1, "kind": "apple-mlxvlm-local-package-validation",
            "sourceCommit": payload["sourceCommit"], "toolchain": version,
            "archives": receipts, "binaryTargets": modules,
            "localGraphStatus": "validated", "localZipResolutionStatus": "validated",
            "consumerBuildStatus": "not-run",
            "resourceRuntimeStatus": "not-run", "remoteArchiveResolutionStatus": "not-run"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("artifact_root", type=Path)
    parser.add_argument("--archive", action="append", default=[], metavar="ARTIFACT_PATH=ZIP_PATH")
    args = parser.parse_args()
    archives = {}
    for value in args.archive:
        name, separator, path = value.partition("=")
        if not separator or not name or not path or name in archives:
            parser.error("--archive requires unique ARTIFACT_PATH=ZIP_PATH values")
        archives[name] = Path(path)
    print(json.dumps(validate_package(args.artifact_root, args.manifest, archives), indent=2))


if __name__ == "__main__":
    main()
