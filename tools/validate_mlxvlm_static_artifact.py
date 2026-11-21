"""Additional schema-v2 checks for locally staged static MLXVLM artifacts."""

from __future__ import annotations

import json
from pathlib import Path
import re
from typing import Any

from tools.validate_mlxvlm_binary_artifact import (
    EXPECTED_MODEL_SHA256,
    _existing,
    _hash_matches,
    _hash_matches_tree,
    _reject_symlink_escapes,
    _require,
)


PRODUCTION_LOCK_SHA256 = "e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858"
REQUIRED_C_MODULES = {"Cmlx", "cLogSupport", "_NumericsShims", "yyjson"}
REQUIRED_LINK_SETTINGS = {"libraries": {"c++"}, "frameworks": {"Accelerate", "Metal"}}


def _sha256(value: Any) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def _string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _unique(values: list[Any], label: str) -> set[str]:
    _require(all(isinstance(value, str) and value for value in values), f"{label} must contain strings")
    result = set(values)
    _require(len(result) == len(values), f"{label} contains duplicates")
    return result


def _validate_receipt(provenance: Any, resource: dict[str, Any], payload: dict[str, Any], root: Path, label: str) -> None:
    _require(isinstance(provenance, dict), f"{label}.provenance must be an object")
    path = _existing(root, provenance.get("path"), f"{label}.provenance.path")
    _require(path.is_file(), f"{label}.provenance.path is not a file")
    _hash_matches(path, provenance.get("sha256"), f"{label}.provenance")
    try:
        receipt = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"{label}.provenance receipt is not valid JSON: {error}") from error
    _require(isinstance(receipt, dict), f"{label}.provenance receipt must be an object")
    _require(receipt.get("sourceCommit") == payload["sourceCommit"], f"{label}.provenance sourceCommit mismatch")
    _require(receipt.get("lockSHA256") == PRODUCTION_LOCK_SHA256, f"{label}.provenance lockSHA256 mismatch")
    _require(receipt.get("toolchain") == payload["toolchain"], f"{label}.provenance toolchain mismatch")
    _require(receipt.get("platform") == resource["platform"], f"{label}.provenance platform mismatch")
    for field in ("sdkVersion", "sdkBuild", "metalCompiler"):
        _require(_string(receipt.get(field)), f"{label}.provenance {field} is required")
    for field in ("sourceInventorySHA256", "overlayManifestSHA256"):
        _require(_sha256(receipt.get(field)), f"{label}.provenance {field} is invalid")
    _require(receipt.get("metalOutputSHA256") == resource["sha256"], f"{label}.provenance metalOutputSHA256 mismatch")


def validate_static_manifest(payload: dict[str, Any], root: Path) -> None:
    """Validate v2 additions after the shared artifact validator has passed."""
    _require(payload.get("linking") == "static", "schema-v2 linking must be static")
    closure = set(payload["moduleClosure"])
    for index, artifact in enumerate(payload["artifacts"]):
        _require(artifact.get("linking") == "static", f"artifact[{index}].linking must be static")

    c_modules = payload.get("cModules")
    _require(isinstance(c_modules, list) and c_modules, "schema-v2 cModules are required")
    c_names: set[str] = set()
    for index, module in enumerate(c_modules):
        label = f"cModules[{index}]"
        _require(isinstance(module, dict), f"{label} must be an object")
        name = module.get("name")
        _require(_string(name) and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name) and name not in c_names,
                 f"{label}.name must be a unique C module identifier")
        c_names.add(name)
        owner = module.get("owner")
        _require(isinstance(owner, str) and owner in closure, f"{label}.owner must be in moduleClosure")
        directory = _existing(root, module.get("path"), f"{label}.path")
        _require(directory.is_dir(), f"{label}.path is not a directory")
        _reject_symlink_escapes(directory, root, label)
        _hash_matches_tree(directory, module.get("sha256"), label)
        module_map = directory / "module.modulemap"
        _require(module_map.is_file(), f"{label} is missing module.modulemap")
        _hash_matches(module_map, module.get("moduleMapSHA256"), f"{label}.moduleMap")
    _require(REQUIRED_C_MODULES.issubset(c_names), "schema-v2 required C modules are missing")

    settings = payload.get("linkSettings")
    _require(isinstance(settings, dict), "schema-v2 linkSettings must be an object")
    for field, expected in REQUIRED_LINK_SETTINGS.items():
        value = settings.get(field)
        _require(isinstance(value, list) and _unique(value, f"linkSettings.{field}") == expected,
                 f"linkSettings.{field} must equal {sorted(expected)}")

    resources = payload["resources"]
    by_platform: dict[str, set[str]] = {"device": set(), "simulator": set()}
    for index, resource in enumerate(resources):
        label = f"resource[{index}]"
        platform = resource.get("platform")
        _require(isinstance(platform, str) and platform in by_platform,
                 f"{label}.platform must be device or simulator")
        path = _existing(root, resource.get("path"), f"{label}.path")
        _require(path.is_file(), f"{label}.path is not a file")
        name = path.name
        if name in {"smolvlm2-model.json", "default.metallib"}:
            _require(name not in by_platform[platform], f"duplicate {name} resource for {platform}")
            by_platform[platform].add(name)
        if name == "smolvlm2-model.json":
            _require(resource.get("sha256") == EXPECTED_MODEL_SHA256,
                     f"{label} canonical model sha256 mismatch")
        if name == "default.metallib":
            _validate_receipt(resource.get("provenance"), resource, payload, root, label)
    for platform, names in by_platform.items():
        _require(names == {"smolvlm2-model.json", "default.metallib"},
                 f"schema-v2 {platform} resources must include model JSON and Metal library")

    notices = payload.get("notices")
    _require(isinstance(notices, list) and notices, "schema-v2 notices are required")
    notice_paths: set[str] = set()
    for index, notice in enumerate(notices):
        label = f"notices[{index}]"
        _require(isinstance(notice, dict), f"{label} must be an object")
        value = notice.get("path")
        _require(_string(value) and value not in notice_paths, f"{label}.path must be unique")
        notice_paths.add(value)
        path = _existing(root, value, f"{label}.path")
        _require(path.is_file(), f"{label}.path is not a file")
        _hash_matches(path, notice.get("sha256"), label)
