#!/usr/bin/env python3
"""Build a hash-bound grouped P11 benchmark snapshot from retained raw reports."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path
from typing import Any

try:
    from tools import benchmark_report, mlxvlm_benchmark_report, validate_p11_readiness
except ModuleNotFoundError:  # Direct `python tools/p11_grouped_report.py` invocation.
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from tools import benchmark_report, mlxvlm_benchmark_report, validate_p11_readiness


SCHEMA_VERSION = 1
_KINDS = {"face", "mlxvlm", "tts-resource", "video-sr-resource"}
_KIND_CAPABILITIES = {
    "face": {"face-detection"},
    "mlxvlm": {"multimodal-apple"},
    "tts-resource": {"speech-tts"},
    "video-sr-resource": {"video-sr"},
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


def _verify_hash(repo_root: Path, entry: dict[str, Any], label: str) -> Path:
    path = _relative_path(repo_root, entry.get("path"), f"{label}.path")
    if not path.is_file():
        raise ValueError(f"missing source: {entry.get('path')}")
    expected = entry.get("sha256")
    if not isinstance(expected, str) or len(expected) != 64:
        raise ValueError(f"{label}.sha256 must be a SHA-256 digest")
    actual = _sha256(path)
    if actual != expected:
        raise ValueError(f"hash mismatch: {entry.get('path')}")
    return path


def _verify_file(repo_root: Path, entry: dict[str, Any], label: str) -> tuple[Path, dict[str, Any]]:
    path = _verify_hash(repo_root, entry, label)
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


def _nearest_rank(values: list[float | int]) -> dict[str, float | int]:
    ordered = sorted(values)
    count = len(ordered)
    return {"count": count, "p50": ordered[math.ceil(count * 0.5) - 1],
            "p95": ordered[math.ceil(count * 0.95) - 1]}


def _tts_group(
    raw: dict[str, Any], entry: dict[str, Any], repo_root: Path
) -> dict[str, Any]:
    summary_entry = entry.get("summary")
    if not isinstance(summary_entry, dict):
        raise ValueError("TTS summary is required")
    _, summary = _verify_file(repo_root, summary_entry, "TTS summary")
    if (summary.get("schemaVersion") != 1 or summary.get("kind") != "f11-ios-tts-resource-run"
            or summary.get("rawReport") != {"path": entry["path"], "sha256": entry["sha256"]}):
        raise ValueError("TTS summary raw hash mismatch")
    tts = raw.get("tts")
    device = summary.get("device")
    runs = raw.get("f11ResourceRuns")
    summary_runs = summary.get("runs")
    if (raw.get("schemaVersion") != 1 or raw.get("validationStatus") != "device"
            or not isinstance(tts, dict) or tts.get("status") != "success"
            or tts.get("backendId") != summary.get("backend")
            or tts.get("selectedLocale") != summary.get("locale")
            or tts.get("selectedVoice") != summary.get("voice")
            or not isinstance(device, dict) or raw.get("device") != device.get("model")
            or raw.get("OS") != device.get("os")
            or not isinstance(runs, list) or len(runs) != 5
            or not isinstance(summary_runs, dict) or summary_runs.get("actual") != 5
            or summary_runs.get("successful") != 5
            or not isinstance(raw.get("runId"), str) or not raw["runId"]
            or not isinstance(device.get("udid"), str) or not device["udid"]
            or not isinstance(raw.get("f11ResourceMethod"), str) or not raw["f11ResourceMethod"]
            or not isinstance(raw.get("f11SampleProfile"), str) or not raw["f11SampleProfile"]):
        raise ValueError("invalid TTS resource source identity")
    run_ids: set[str] = set()
    for index, run in enumerate(runs):
        if not isinstance(run, dict):
            raise ValueError("invalid TTS resource run")
        duration = run.get("durationMs")
        peak = run.get("rssPeakSampledBytes")
        start = run.get("rssStartBytes")
        end = run.get("rssEndBytes")
        run_id = run.get("runId")
        if (not isinstance(run_id, str) or not run_id or run_id in run_ids
                or run.get("sampleIndex") != index or run.get("status") != "success"
                or run.get("error") is not None
                or not isinstance(duration, (int, float)) or isinstance(duration, bool)
                or not math.isfinite(duration) or duration <= 0
                or any(type(value) is not int or value < 0 for value in (peak, start, end))
                or peak < max(start, end)
                or type(run.get("rssSampleCount")) is not int or run["rssSampleCount"] <= 0):
            raise ValueError("invalid TTS resource run")
        run_ids.add(run_id)
    commit = summary.get("sourceCommit")
    if not isinstance(commit, str) or len(commit) != 40:
        raise ValueError("invalid TTS source commit")
    return {
        "kind": "tts-resource", "p11Capability": "speech-tts",
        "source": entry["path"], "sourceSha256": entry["sha256"],
        "summary": dict(summary_entry), "scope": "isolated-sdk-tts-probe",
        "device": raw["device"], "OS": raw["OS"], "actualBackend": tts["backendId"],
        "identity": {"sdkCommit": commit, "deviceId": device.get("udid"),
                     "sessionRunId": raw.get("runId"), "voice": tts["selectedVoice"],
                     "locale": tts["selectedLocale"], "sampleProfile": raw.get("f11SampleProfile")},
        "runIds": [run["runId"] for run in runs],
        "timingsMs": {"duration": _nearest_rank([run["durationMs"] for run in runs])},
        "quality": {"status": "unverified", "reason": "fixed voice has no natural quality rubric"},
        "memory": {"status": "observed-sampled", "method": raw.get("f11ResourceMethod"),
                   "peakSampledBytes": _nearest_rank([run["rssPeakSampledBytes"] for run in runs])},
        "energy": {"status": "unmeasured"},
        "unverified": ["naturalVoiceQuality", "offlineNetworkBehavior", "truePeakMemory",
                       "energy", "gpuPeak", "minimumOS"],
    }


def _video_sr_groups(raw: dict[str, Any], entry: dict[str, Any], repo_root: Path) -> list[dict[str, Any]]:
    run_id = raw.get("runId")
    device = raw.get("device")
    os_version = raw.get("OS")
    baseline = raw.get("baseline")
    color_contract = raw.get("colorContract")
    quality_entries = entry.get("quality")
    output_entries = entry.get("outputs")
    input_entry = entry.get("input")
    reference_entry = entry.get("reference")
    if (not isinstance(run_id, str) or not run_id
            or not isinstance(device, str) or not re.fullmatch(r"iPhone[0-9]+,[0-9]+", device)
            or not isinstance(os_version, str) or not os_version
            or not isinstance(color_contract, str) or not color_contract
            or not isinstance(baseline, dict)
            or raw.get("output") == baseline.get("output")
            or not isinstance(quality_entries, dict) or not isinstance(output_entries, dict)
            or not isinstance(input_entry, dict) or not isinstance(reference_entry, dict)):
        raise ValueError("invalid Video SR session identity")

    _verify_hash(repo_root, input_entry, "Video SR input")
    _verify_hash(repo_root, reference_entry, "Video SR reference")
    if raw.get("inputFileSha256") != input_entry["sha256"]:
        raise ValueError("invalid Video SR input identity")

    groups = []
    input_hash = raw.get("inputFileSha256")
    reference_decoded_hash: str | None = None
    for suffix, source, backend in (
        ("sr", raw, "apple.videotoolbox.sr"),
        ("bicubic", baseline, "apple.coreimage.bicubic"),
    ):
        quality_entry = quality_entries.get(suffix)
        output_entry = output_entries.get(suffix)
        if not isinstance(quality_entry, dict) or not isinstance(output_entry, dict):
            raise ValueError(f"invalid Video SR {suffix} evidence entries")
        _verify_hash(repo_root, output_entry, f"Video SR {suffix} output")
        _, quality_report = _verify_file(repo_root, quality_entry, f"Video SR {suffix} quality")
        candidate = quality_report.get("candidate")
        reference = quality_report.get("reference")
        metrics = quality_report.get("metrics")
        decoded_hash = reference.get("decodedSha256") if isinstance(reference, dict) else None
        if (not isinstance(candidate, dict) or not isinstance(reference, dict)
                or not isinstance(metrics, dict)
                or quality_report.get("schemaVersion") != 1
                or quality_report.get("measurement") != "decoded-output-fidelity"
                or quality_report.get("qualityVerdict") != "unverified"
                or quality_report.get("colorComparability") != "matched-metadata"
                or quality_report.get("comparedFrames") != 48
                or candidate.get("path") != output_entry.get("path")
                or reference.get("path") != reference_entry.get("path")
                or not isinstance(decoded_hash, str)
                or not re.fullmatch(r"[0-9a-f]{64}", decoded_hash)
                or not isinstance(candidate.get("decodedSha256"), str)
                or not re.fullmatch(r"[0-9a-f]{64}", candidate["decodedSha256"])
                or (reference_decoded_hash is not None and decoded_hash != reference_decoded_hash)
                or type(metrics.get("psnrDb")) not in (int, float)
                or not math.isfinite(metrics["psnrDb"]) or metrics["psnrDb"] <= 0
                or type(metrics.get("ssim")) not in (int, float)
                or not math.isfinite(metrics["ssim"]) or not 0 <= metrics["ssim"] <= 1
                or source.get("output") != Path(output_entry["path"]).name):
            raise ValueError(f"invalid Video SR {suffix} quality identity")
        reference_decoded_hash = decoded_hash
        values = source.get("frameProcessingSeconds")
        memory = [source.get(field) for field in (
            "residentMemoryBaselineBytes", "residentMemoryPeakBytes",
            "residentMemoryEndingBytes", "residentMemoryPeakDeltaBytes",
        )]
        if (source.get("schemaVersion") != 1 or source.get("validationStatus") != "device"
                or source.get("state") != "success" or source.get("backendId") != backend
                or not isinstance(input_hash, str) or not re.fullmatch(r"[0-9a-f]{64}", input_hash)
                or source.get("inputFileSha256") != input_hash
                or source.get("inputFrameCount") != 48 or source.get("outputFrameCount") != 48
                or source.get("outputWidth") != 1280 or source.get("outputHeight") != 720
                or source.get("timestampsMonotonic") is not True
                or not isinstance(values, list) or len(values) != 48
                or any(type(value) not in (float, int) or not math.isfinite(value) or value <= 0
                       for value in values)
                or any(type(value) is not int or value < 0 for value in memory)
                or memory[1] < max(memory[0], memory[2]) or memory[3] != memory[1] - memory[0]
                or not isinstance(source.get("memoryMeasurement"), str)
                or not source["memoryMeasurement"]
                or not isinstance(source.get("output"), str) or not source["output"]):
            raise ValueError(f"invalid Video SR {suffix} source")
        groups.append({
            "kind": "video-sr-resource", "p11Capability": "video-sr",
            "source": entry["path"], "sourceSha256": entry["sha256"],
            "scope": "isolated-system-video-sr-probe",
            "device": device, "OS": os_version, "actualBackend": backend,
            "identity": {"sessionRunId": run_id, "inputFileSha256": input_hash,
                         "colorContract": color_contract, "output": source["output"]},
            "runIds": [run_id if suffix == "sr" else f"{run_id}:bicubic"],
            "timingsMs": {"frameProcessing": _nearest_rank([value * 1000 for value in values])},
            "quality": {"status": "unverified", "reason": "natural-video subjective quality unverified",
                        "measurement": "decoded-output-fidelity", "metrics": metrics,
                        "source": dict(quality_entry), "output": dict(output_entry),
                        "reference": dict(reference_entry),
                        "candidateDecodedSha256": candidate["decodedSha256"],
                        "referenceDecodedSha256": decoded_hash},
            "memory": {"status": "observed-sampled", "method": source["memoryMeasurement"],
                       "baselineBytes": memory[0], "peakSampledBytes": memory[1],
                       "endingBytes": memory[2], "peakDeltaSampledBytes": memory[3]},
            "energy": {"status": "unmeasured"},
            "unverified": ["subjectiveQuality", "truePeakMemory", "gpuPeak", "energy",
                           "minimumOS", "crossDevice", "productionSDK"],
        })
    return groups


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
    validate_p11_readiness.validate_summary(readiness, repo_root)
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
        elif entry["kind"] == "mlxvlm":
            groups.append(_mlx_group(raw, capability, entry["path"], digest, entry.get("expectedSamples")))
        elif entry["kind"] == "tts-resource":
            groups.append(_tts_group(raw, entry, repo_root))
        else:
            groups.extend(_video_sr_groups(raw, entry, repo_root))
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
    sources = manifest.get("sources") or []
    candidates = [manifest.get("readiness"), *sources,
                  *(entry.get("summary") for entry in sources if isinstance(entry, dict) and entry.get("summary"))]
    for source in sources:
        if not isinstance(source, dict) or source.get("kind") != "video-sr-resource":
            continue
        candidates.extend(source.get(key) for key in ("input", "reference"))
        for key in ("quality", "outputs"):
            nested = source.get(key)
            if isinstance(nested, dict):
                candidates.extend(nested.values())
    for index, entry in enumerate(candidates):
        if not isinstance(entry, dict):
            continue
        path = _relative_path(repo_root, entry.get("path"), f"input[{index}].path")
        if output_resolved == path or (output.exists() and output.samefile(path)):
            raise ValueError(f"output must not overwrite benchmark input: {entry.get('path')}")

    readiness_entry = manifest.get("readiness")
    if isinstance(readiness_entry, dict):
        readiness_path = _relative_path(repo_root, readiness_entry.get("path"), "readiness.path")
        readiness = _load_json(readiness_path)
        source_hashes = readiness.get("sourceHashes")
        if isinstance(source_hashes, dict):
            for source_path in source_hashes:
                path = _relative_path(repo_root, source_path, "readiness.sourceHashes path")
                if output_resolved == path or (output.exists() and output.samefile(path)):
                    raise ValueError(f"output must not overwrite readiness evidence: {source_path}")


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
