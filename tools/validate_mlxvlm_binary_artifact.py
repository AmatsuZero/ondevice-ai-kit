"""Validate a relocatable Apple MLXVLM XCFramework artifact manifest."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
KIND = "apple-mlxvlm-xcframework-set"
EXPECTED_MODEL_SHA256 = "c074e693cc0fb9cfb19d6e7a393ffb113108e88d1489a387e8cb06d309602d54"
EXPECTED_METAL_SHA256 = "953544ddd003eaef4f0f03c5dce821c2911704288a2ffebd231de7dd60383ee2"


def _fail(message: str) -> None:
    raise ValueError(message)


def _require(condition: bool, message: str) -> None:
    if not condition:
        _fail(message)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _sha256_tree(path: Path) -> str:
    digest = hashlib.sha256()
    for child in sorted(item for item in path.rglob("*") if item.is_file()):
        relative = child.relative_to(path)
        digest.update(str(relative).encode())
        digest.update(b"\0")
        digest.update(child.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def _path(root: Path, value: Any, label: str) -> Path:
    _require(isinstance(value, str) and value, f"{label} must be a relative path")
    candidate = Path(value)
    _require(not candidate.is_absolute(), f"{label} must be a relative path")
    lowered = "/".join(candidate.parts).lower()
    _require(
        "sourcepackages/checkouts" not in lowered
        and "sourcepackages/checkouts" not in lowered.replace("\\", "/")
        and not any(part.lower() == "checkouts" for part in candidate.parts),
        f"{label} uses a source checkout path",
    )
    root = root.resolve()
    lexical = root / candidate
    resolved = lexical.resolve(strict=False)
    try:
        resolved.relative_to(root)
    except ValueError:
        _fail(f"{label} escapes artifact root through a symlink: {value}")
    return lexical


def _existing(root: Path, value: Any, label: str) -> Path:
    path = _path(root, value, label)
    _require(path.exists(), f"{label} is missing: {value}")
    _require(not path.is_symlink() or path.resolve().is_relative_to(root.resolve()),
             f"{label} escapes artifact root through a symlink: {value}")
    return path


def _reject_symlink_escapes(path: Path, root: Path, label: str) -> None:
    for item in path.rglob("*"):
        if item.is_symlink():
            try:
                item.resolve().relative_to(root.resolve())
            except ValueError:
                _fail(f"{label} contains a symlink escape: {item}")


def _hash_matches(path: Path, expected: Any, label: str) -> None:
    _require(isinstance(expected, str) and len(expected) == 64,
             f"{label} sha256 is invalid")
    actual = _sha256_file(path)
    _require(actual == expected.lower(), f"{label} sha256 drift: expected {expected}, got {actual}")


def validate_manifest(manifest_path: Path, artifact_root: Path) -> None:
    """Validate *manifest_path* and all files it names below *artifact_root*.

    ``ValueError`` is used deliberately so callers can present a path-specific
    failure without depending on a command-line implementation.
    """

    manifest_path = Path(manifest_path)
    root = Path(artifact_root).resolve()
    _require(root.is_dir(), f"artifact root is missing: {artifact_root}")
    _require(manifest_path.is_file(), f"manifest is missing: {manifest_path}")
    try:
        payload = json.loads(manifest_path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        _fail(f"manifest is not valid JSON: {error}")
    _require(isinstance(payload, dict), "manifest must be an object")
    _require(payload.get("schemaVersion") == SCHEMA_VERSION, "unsupported schemaVersion")
    _require(payload.get("kind") == KIND, "unsupported artifact kind")
    _require(isinstance(payload.get("sourceCommit"), str) and payload["sourceCommit"],
             "sourceCommit is required")
    _require(isinstance(payload.get("toolchain"), dict), "toolchain is required")

    closure = payload.get("moduleClosure")
    _require(isinstance(closure, list) and closure and all(isinstance(item, str) and item for item in closure),
             "moduleClosure is required")
    _require(len(set(closure)) == len(closure), "module closure contains duplicate modules")
    closure_set = set(closure)

    resources = payload.get("resources")
    _require(isinstance(resources, list) and resources, "resources are required")
    resource_paths: set[str] = set()
    for index, resource in enumerate(resources):
        label = f"resource[{index}]"
        _require(isinstance(resource, dict), f"{label} must be an object")
        path_value = resource.get("path")
        _require(isinstance(path_value, str), f"{label}.path must be a string")
        _require(path_value not in resource_paths, f"duplicate resource path: {path_value}")
        resource_paths.add(path_value)
        path = _existing(root, path_value, f"{label}.path")
        _require(path.is_file(), f"{label}.path is not a file: {path_value}")
        _hash_matches(path, resource.get("sha256"), label)
        actual = _sha256_file(path)
        if path.name == "smolvlm2-model.json":
            _require(actual == EXPECTED_MODEL_SHA256,
                     f"{label} canonical model sha256 mismatch: {actual}")
        if path.name == "default.metallib":
            _require(actual == EXPECTED_METAL_SHA256,
                     f"{label} canonical Metal sha256 mismatch: {actual}")
    _require(any(Path(value).name == "smolvlm2-model.json" for value in resource_paths),
             "canonical model JSON resource is missing")
    _require(any(Path(value).name == "default.metallib" for value in resource_paths),
             "Metal library resource is missing")

    artifacts = payload.get("artifacts")
    _require(isinstance(artifacts, list) and artifacts, "artifacts are required")
    shipped: set[str] = set()
    artifact_paths: set[str] = set()
    for index, artifact in enumerate(artifacts):
        label = f"artifact[{index}]"
        _require(isinstance(artifact, dict), f"{label} must be an object")
        artifact_value = artifact.get("path")
        _require(isinstance(artifact_value, str), f"{label}.path must be a string")
        _require(artifact_value not in artifact_paths, f"duplicate artifact path: {artifact_value}")
        artifact_paths.add(artifact_value)
        artifact_path = _existing(root, artifact_value, f"{label}.path")
        _require(artifact_path.is_dir(), f"{label}.path is not a directory: {artifact_value}")
        _reject_symlink_escapes(artifact_path, root, label)

        modules = artifact.get("modules")
        _require(isinstance(modules, list) and modules, f"{label} modules are required for module closure")
        _require(all(isinstance(item, str) and item for item in modules),
                 f"{label}.modules must contain nonempty strings")
        _require(len(set(modules)) == len(modules), f"{label} contains duplicate modules")
        _require(set(modules).issubset(closure_set), f"{label} module is outside module closure")
        _require(not shipped.intersection(modules),
                 f"{label} contains duplicate module across artifacts: {sorted(shipped.intersection(modules))}")
        shipped.update(modules)
        _require(isinstance(artifact.get("minimumOS"), str) and artifact["minimumOS"],
                 f"{label}.minimumOS is required")
        _hash_matches_tree(artifact_path, artifact.get("sha256"), label)

        slices = artifact.get("slices")
        _require(isinstance(slices, list) and slices, f"{label} slices are required")
        platforms: set[str] = set()
        slice_paths: set[Path] = set()
        for slice_index, slice_info in enumerate(slices):
            slice_label = f"{label}.slices[{slice_index}]"
            _require(isinstance(slice_info, dict), f"{slice_label} must be an object")
            slice_path = _existing(artifact_path, slice_info.get("path"), f"{slice_label}.path")
            _require(slice_path.is_dir(), f"{slice_label}.path is not a directory")
            slice_paths.add(slice_path)
            platform = slice_info.get("platform")
            _require(isinstance(platform, str), f"{slice_label}.platform is required")
            platforms.add(platform.lower())
            slice_architectures = slice_info.get("architectures")
            _require(isinstance(slice_architectures, list) and slice_architectures,
                     f"{slice_label}.architectures are required")
            normalized_architectures = {str(item).lower() for item in slice_architectures}
            _require("arm64" in normalized_architectures,
                     f"{slice_label} is missing arm64 architecture")
        _require(any("simulator" in platform for platform in platforms),
                 f"{label} is missing simulator slice")
        _require(any(platform in {"ios", "iphoneos", "device"} for platform in platforms),
                 f"{label} is missing device slice")

        interfaces = artifact.get("interfaces")
        _require(isinstance(interfaces, list) and interfaces, f"{label} interfaces are required")
        interface_paths: set[Path] = set()
        for interface_index, interface in enumerate(interfaces):
            interface_label = f"{label}.interfaces[{interface_index}]"
            _require(isinstance(interface, dict), f"{interface_label} must be an object")
            interface_path = _existing(artifact_path, interface.get("path"), f"{interface_label}.path")
            _require(interface_path.is_file(), f"{interface_label}.path is not a file")
            _require(interface_path.suffix == ".swiftinterface", f"{interface_label} is not a Swift interface")
            interface_paths.add(interface_path)
            _hash_matches(interface_path, interface.get("sha256"), interface_label)
        for slice_path in slice_paths:
            _require(any(path.is_relative_to(slice_path) for path in interface_paths),
                     f"{label} interface is missing for slice {slice_path.relative_to(artifact_path)}")

    _require(shipped == closure_set,
             f"module closure is incomplete: missing {sorted(closure_set - shipped)}")


def _hash_matches_tree(path: Path, expected: Any, label: str) -> None:
    _require(isinstance(expected, str) and len(expected) == 64,
             f"{label} sha256 is invalid")
    actual = _sha256_tree(path)
    _require(actual == expected.lower(), f"{label} sha256 drift: expected {expected}, got {actual}")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("artifact_root", type=Path)
    args = parser.parse_args()
    validate_manifest(args.manifest, args.artifact_root)
    print("valid")
