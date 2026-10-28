#!/usr/bin/env python3
"""Build a hash-bound grouped P11 benchmark snapshot from retained raw reports."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

try:
    from tools import benchmark_report, mlxvlm_benchmark_report
except ModuleNotFoundError:  # Direct `python tools/p11_grouped_report.py` invocation.
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from tools import benchmark_report, mlxvlm_benchmark_report


SCHEMA_VERSION = 1
_KINDS = {"face", "mlxvlm"}
_KIND_CAPABILITIES = {
    "face": {"face-detection"},
    "mlxvlm": {"multimodal-apple"},
}


def _json_constant(value: str) -> None:
    raise ValueError(f"non-finite JSON number: {value}")


def _relative_path(repo_root: Path, raw: Any, label: str) -> Path:
    if not isinstance(raw, str) or not raw or Path(raw).is_absolute():
        raise ValueError(f"{label} must be a repository-relative path")
    root = repo_root.resolve()
    resolved = (root / raw).resolve()
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"{label} escapes repository root: {raw}") from exc
    return resolved


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"), parse_constant=_json_constant)
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"invalid JSON: {path}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return value


def _verify_file(repo_root: Path, entry: dict[str, Any], label: str) -> tuple[Path, dict[str, Any]]:
    path = _relative_path(repo_root, entry.get("path"), f"{label}.path")
    if not path.is_file():
        raise ValueError(f"missing source: {entry.get('path')}")
    expected = entry.get("sha256")
    if not isinstance(expected, str) or len(expected) != 64:
        raise ValueError(f"{label}.sha256 must be a SHA-256 digest")
    actual = _sha256(path)
    if actual != expected:
        raise ValueError(f"hash mismatch: {entry.get('path')}")
    return path, _load_json(path)


def _face_group(raw: dict[str, Any], capability: str, source: str, digest: str) -> dict[str, Any]:
    summary = benchmark_report.summarize([raw])
    group = summary["groups"][0]
    quality = group["runs"][0]["quality"]
    if quality:
        quality_value: dict[str, Any] = {"status": "observed", "value": quality}
    else:
        quality_value = {"status": "unverified", "reason": "source quality object is empty"}
    peak_memory = group["runs"][0]["peakMemory"]
    if peak_memory is None:
        memory = {"status": "unmeasured", "method": group["memoryMethod"]}
        unverified = ["quality", "peakMemory", "energy", "gpuPeak"]
    else:
        memory = {"status": "observed", **peak_memory}
        unverified = ["quality", "energy", "gpuPeak"]
    return {
        "kind": "face",
        "p11Capability": capability,
        "source": source,
        "sourceSha256": digest,
        "device": group["metadata"]["device"],
        "OS": group["metadata"]["OS"],
        "actualBackend": group["metadata"]["actualBackend"],
        "identity": {
            field: group["metadata"][field]
            for field in (
                "sdkCommit",
                "capability",
                "modelRevision",
                "preprocessRevision",
                "fixtureHash",
                "measurementMethod",
                "validationStatus",
            )
        },
        "runIds": [item["runId"] for item in group["runs"]],
        "timingsMs": group["timingsMs"],
        "quality": quality_value,
        "memory": memory,
        "energy": {"status": "unmeasured"},
        "unverified": unverified,
    }


def _mlx_group(
    raw: dict[str, Any], capability: str, source: str, digest: str, expected_samples: Any
) -> dict[str, Any]:
    if type(expected_samples) is not int or expected_samples <= 0:
        raise ValueError(f"invalid expectedSamples for {source}")
    summary = mlxvlm_benchmark_report.summarize(raw, expected_samples=expected_samples)
    quality_status = (
        "pass-bounded"
        if summary["quality"]["passed"] == summary["quality"]["total"]
        else "fail"
    )
    return {
        "kind": "mlxvlm",
        "p11Capability": capability,
        "source": source,
        "sourceSha256": digest,
        "device": summary["device"],
        "OS": summary["OS"],
        "actualBackend": summary["actualBackend"],
        "identity": {
            field: summary[field]
            for field in (
                "sdkCommit",
                "modelId",
                "modelRevision",
                "runtimeRevision",
                "fixtureHash",
                "qualityScope",
            )
        },
        "runIds": [summary["runId"]],
        "timingsMs": summary["timingsMs"],
        "quality": {
            "status": quality_status,
            **summary["quality"],
            "scope": summary["qualityScope"],
        },
        "memory": summary["memory"],
        "energy": {"status": "unmeasured"},
        "unverified": list(summary["unverified"]),
    }


def _reject_duplicate_run_ids(groups: list[dict[str, Any]]) -> None:
    seen: set[str] = set()
    for group in groups:
        for run_id in group["runIds"]:
            if run_id in seen:
                raise ValueError(f"duplicate runId: {run_id}")
            seen.add(run_id)


def _readiness(repo_root: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    readiness_entry = manifest.get("readiness")
    if not isinstance(readiness_entry, dict):
        raise ValueError("readiness must be an object")
    _, readiness = _verify_file(repo_root, readiness_entry, "readiness")
    if readiness.get("schemaVersion") != 1 or not isinstance(readiness.get("rows"), list):
        raise ValueError("unsupported readiness snapshot")
    gate = readiness.get("p11Gate")
    if (
        not isinstance(gate, dict)
        or not isinstance(gate.get("status"), str)
        or not isinstance(gate.get("finalReleaseConclusion"), str)
    ):
        raise ValueError("readiness p11Gate is required")
    capabilities: set[str] = set()
    has_nonterminal_row = False
    for index, row in enumerate(readiness["rows"]):
        if (
            not isinstance(row, dict)
            or not isinstance(row.get("capability"), str)
            or not row["capability"]
            or not isinstance(row.get("status"), str)
            or not isinstance(row.get("unverified"), list)
        ):
            raise ValueError(f"readiness rows[{index}] is invalid")
        if row["capability"] in capabilities:
            raise ValueError(f"duplicate readiness capability: {row['capability']}")
        capabilities.add(row["capability"])
        has_nonterminal_row |= bool(row["unverified"]) or row["status"] != "pass"
    if has_nonterminal_row and (
        gate["status"] != "open" or gate["finalReleaseConclusion"] != "not-ready"
    ):
        raise ValueError("readiness gate must remain open/not-ready while gaps exist")
    return readiness


def generate(manifest: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    if not isinstance(manifest, dict) or manifest.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("unsupported manifest schema")
    readiness = _readiness(repo_root, manifest)
    readiness_capabilities = {row["capability"] for row in readiness["rows"]}
    entries = manifest.get("sources")
    if not isinstance(entries, list) or not entries:
        raise ValueError("sources must be a non-empty array")
    groups: list[dict[str, Any]] = []
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict) or entry.get("kind") not in _KINDS:
            raise ValueError(f"sources[{index}].kind is unsupported")
        path, raw = _verify_file(repo_root, entry, f"sources[{index}]")
        capability = entry.get("p11Capability")
        if not isinstance(capability, str) or not capability:
            raise ValueError(f"sources[{index}].p11Capability is required")
        if capability not in readiness_capabilities:
            raise ValueError(f"sources[{index}].p11Capability is not present in readiness: {capability}")
        if capability not in _KIND_CAPABILITIES[entry["kind"]]:
            raise ValueError(f"sources[{index}].kind {entry['kind']} cannot report {capability}")
        digest = entry["sha256"]
        if entry["kind"] == "face":
            groups.append(_face_group(raw, capability, entry["path"], digest))
        else:
            groups.append(_mlx_group(raw, capability, entry["path"], digest, entry.get("expectedSamples")))
    _reject_duplicate_run_ids(groups)
    reported = {group["p11Capability"] for group in groups}
    coverage = []
    for index, row in enumerate(readiness["rows"]):
        if (
            not isinstance(row, dict)
            or not isinstance(row.get("capability"), str)
            or not isinstance(row.get("status"), str)
            or not isinstance(row.get("unverified"), list)
        ):
            raise ValueError(f"readiness rows[{index}] is invalid")
        coverage.append({
            "capability": row["capability"],
            "readinessStatus": row["status"],
            "reportStatus": "reported" if row["capability"] in reported else "unverified",
            "unverified": row["unverified"],
        })
    gate = readiness["p11Gate"]
    return {
        "schemaVersion": SCHEMA_VERSION,
        "percentileMethod": "nearest-rank",
        "grouping": ["device", "OS", "p11Capability", "actualBackend"],
        "inputs": {
            "readiness": dict(manifest["readiness"]),
            "sources": [dict(entry) for entry in entries],
        },
        "groups": groups,
        "coverage": coverage,
        "p11Gate": {
            "status": gate["status"],
            "finalReleaseConclusion": gate.get("finalReleaseConclusion"),
        },
    }


def _is_alias(output: Path, source: Path) -> bool:
    return output.resolve() == source.resolve() or (output.exists() and output.samefile(source))


def _reject_output_aliases(
    manifest: dict[str, Any], repo_root: Path, output: Path, manifest_path: Path | None = None
) -> None:
    if manifest_path is not None and _is_alias(output, manifest_path):
        raise ValueError(f"output must not overwrite benchmark input: {manifest_path}")
    output_resolved = output.resolve()
    candidates = [manifest.get("readiness"), *(manifest.get("sources") or [])]
    for index, entry in enumerate(candidates):
        if not isinstance(entry, dict):
            continue
        path = _relative_path(repo_root, entry.get("path"), f"input[{index}].path")
        if output_resolved == path or (output.exists() and output.samefile(path)):
            raise ValueError(f"output must not overwrite benchmark input: {entry.get('path')}")


def write_report(
    manifest: dict[str, Any], repo_root: Path, output: Path, manifest_path: Path | None = None
) -> dict[str, Any]:
    _reject_output_aliases(manifest, repo_root, output, manifest_path=manifest_path)
    result = generate(manifest, repo_root)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    args = parser.parse_args(argv)
    try:
        manifest = _load_json(args.manifest)
        write_report(manifest, args.repo_root, args.output, manifest_path=args.manifest)
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
