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

from tools.probes import android_asr_resource_report, android_litertlm_resource_report


SCHEMA_VERSION = 1
_KINDS = {"face", "mlxvlm", "tts-resource", "video-sr-resource", "video-sr-sdk-resource",
          "asr-file-sdk-resource", "asr-android-resource", "litertlm-android-resource"}
_KIND_CAPABILITIES = {
    "face": {"face-detection"},
    "mlxvlm": {"multimodal-apple"},
    "tts-resource": {"speech-tts"},
    "video-sr-resource": {"video-sr"},
    "video-sr-sdk-resource": {"video-sr"},
    "asr-file-sdk-resource": {"speech-asr-file"},
    "asr-android-resource": {"speech-asr-android"},
    "litertlm-android-resource": {"multimodal-android-litertlm"},
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


def _normalized_asr_text(value: str) -> str:
    return "".join(character for character in value.lower() if character.isalnum())


def _asr_file_sdk_group(raw: dict[str, Any], entry: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    summary_entry = entry.get("summary")
    source_entry = entry.get("sourceManifest")
    input_entry = entry.get("input")
    if not all(isinstance(item, dict) for item in (summary_entry, source_entry, input_entry)):
        raise ValueError("invalid SDK file ASR evidence entries")
    evidence_paths = [_relative_path(repo_root, item.get("path"), "SDK file ASR evidence path")
                      for item in (entry, summary_entry, source_entry, input_entry)]
    for index, path in enumerate(evidence_paths):
        if any(_is_alias(path, earlier) for earlier in evidence_paths[:index]):
            raise ValueError(f"SDK file ASR evidence alias: {path}")
    _, summary = _verify_file(repo_root, summary_entry, "SDK file ASR summary")
    _, source_manifest = _verify_file(repo_root, source_entry, "SDK file ASR source manifest")
    _verify_hash(repo_root, input_entry, "SDK file ASR input")
    source_hashes = source_manifest.get("sourceHashes")
    if (source_manifest.get("schemaVersion") != 1
            or not isinstance(source_manifest.get("sourceCommit"), str)
            or not source_manifest["sourceCommit"]
            or not isinstance(source_hashes, dict)
            or source_hashes.get(entry["path"]) != entry["sha256"]
            or source_hashes.get(input_entry["path"]) != input_entry["sha256"]):
        raise ValueError("invalid SDK file ASR source manifest identity")

    backend = "apple.speech.recognizer"
    scope = "SpeechRecognitionClient.recognize -> AppleSpeechRecognizerBackend.recognize"
    session_id = raw.get("runId")
    runs = raw.get("resourceRuns")
    warmup = raw.get("warmup")
    if (raw.get("schemaVersion") != 1 or raw.get("status") != "success"
            or raw.get("scope") != scope or raw.get("backendId") != backend
            or not isinstance(raw.get("device"), str) or not raw["device"]
            or not isinstance(raw.get("OS"), str) or not raw["OS"]
            or not isinstance(session_id, str) or not session_id
            or not isinstance(raw.get("bundleId"), str) or not raw["bundleId"]
            or type(raw.get("processIdentifier")) is not int or raw["processIdentifier"] <= 0
            or raw.get("inputSHA256") != input_entry["sha256"]
            or raw.get("audioSource") != "bundled-asr-fixture.caf"
            or raw.get("locale") != "en-US"
            or not isinstance(raw.get("expectedText"), str) or not raw["expectedText"]
            or raw.get("requiresOnDeviceRecognition") is not True
            or raw.get("microphoneUsed") is not False
            or raw.get("networkBehavior") != "unmeasured"
            or raw.get("clientClosed") is not True
            or not isinstance(raw.get("measurementMethod"), str) or not raw["measurementMethod"]
            or not isinstance(raw.get("sampleProfile"), str) or not raw["sampleProfile"]
            or not isinstance(warmup, dict) or warmup.get("status") != "success"
            or warmup.get("actualBackendId") != backend
            or warmup.get("backendIdentityMatch") is not True
            or warmup.get("normalizedMatch") is not True
            or not isinstance(warmup.get("transcript"), str) or not warmup["transcript"]
            or not _normalized_asr_text(raw["expectedText"])
            or _normalized_asr_text(warmup["transcript"]) != _normalized_asr_text(raw["expectedText"])
            or not isinstance(runs, list) or len(runs) != 5):
        raise ValueError("invalid SDK file ASR source identity")

    run_ids: set[str] = set()
    durations: list[float] = []
    peaks: list[int] = []
    for index, run in enumerate(runs):
        if not isinstance(run, dict):
            raise ValueError("invalid SDK file ASR run")
        run_id = run.get("runId")
        duration = run.get("durationMs")
        start, end, peak = (run.get(field) for field in (
            "rssStartBytes", "rssEndBytes", "rssPeakSampledBytes"))
        if (not isinstance(run_id, str) or not run_id or run_id == session_id
                or run_id in run_ids or run.get("sampleIndex") != index
                or run.get("status") != "success"
                or run.get("actualBackendId") != backend
                or run.get("backendIdentityMatch") is not True
                or run.get("normalizedMatch") is not True
                or not isinstance(run.get("transcript"), str) or not run["transcript"]
                or _normalized_asr_text(run["transcript"]) != _normalized_asr_text(raw["expectedText"])
                or type(duration) not in (int, float)
                or not math.isfinite(duration) or duration <= 0
                or any(type(value) is not int or value < 0 for value in (start, end, peak))
                or peak < max(start, end)
                or type(run.get("rssSampleCount")) is not int or run["rssSampleCount"] <= 0):
            raise ValueError("invalid SDK file ASR run")
        run_ids.add(run_id)
        durations.append(duration)
        peaks.append(peak)
    timing = _nearest_rank(durations)
    if (summary.get("schemaVersion") != 1 or summary.get("validationStatus") != "pass-bounded"
            or summary.get("backendId") != backend
            or summary.get("bundleId") != raw["bundleId"]
            or summary.get("scope") != scope or summary.get("runId") != session_id
            or summary.get("inputSHA256") != input_entry["sha256"]
            or summary.get("processIdentifier") != raw["processIdentifier"]
            or summary.get("measurementMethod") != raw["measurementMethod"]
            or summary.get("measuredRuns") != 5 or summary.get("warmup") != "success"
            or summary.get("rawReportSha256") != entry["sha256"]
            or summary.get("sourceManifestSha256") != source_entry["sha256"]
            or summary.get("durationMs") != {"p50": timing["p50"], "p95": timing["p95"]}
            or summary.get("rssPeakSampledBytes") != {"min": min(peaks), "max": max(peaks)}
            or not isinstance(summary.get("unverified"), list) or not summary["unverified"]):
        raise ValueError("invalid SDK file ASR summary identity")
    return {
        "kind": "asr-file-sdk-resource", "p11Capability": "speech-asr-file",
        "source": entry["path"], "sourceSha256": entry["sha256"],
        "summary": dict(summary_entry), "sourceManifest": dict(source_entry),
        "scope": scope, "device": raw["device"], "OS": raw["OS"],
        "actualBackend": backend,
        "identity": {"sessionRunId": session_id, "sourceCommit": source_manifest["sourceCommit"],
                     "inputFileSha256": raw["inputSHA256"], "input": dict(input_entry),
                     "bundleId": raw["bundleId"], "processIdentifier": raw["processIdentifier"],
                     "locale": raw["locale"], "sampleProfile": raw["sampleProfile"]},
        "runIds": [run["runId"] for run in runs],
        "timingsMs": {"duration": timing},
        "quality": {"status": "unverified", "reason": "fixed synthetic transcript match is not natural speech CER/WER"},
        "memory": {"status": "observed-sampled", "method": raw["measurementMethod"],
                   "peakSampledBytes": _nearest_rank(peaks)},
        "energy": {"status": "unmeasured"},
        "unverified": ["microphone", "naturalSpeechCERWER", "offlineNetworkBehavior",
                       "truePeakMemory", "gpuPeak", "energy", "minimumOS", "crossDevice"],
    }


def _android_asr_group(raw: dict[str, Any], entry: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    computed = android_asr_resource_report.summarize(raw)
    evidence = [entry.get(key) for key in ("summary", "sourceManifest", "input")]
    if not all(isinstance(item, dict) for item in evidence):
        raise ValueError("invalid Android ASR evidence entries")
    paths = [_relative_path(repo_root, item.get("path"), "Android ASR evidence path")
             for item in (entry, *evidence)]
    for index, path in enumerate(paths):
        if any(_is_alias(path, earlier) for earlier in paths[:index]):
            raise ValueError("Android ASR evidence alias")
    _, summary = _verify_file(repo_root, entry["summary"], "Android ASR summary")
    _, provenance = _verify_file(repo_root, entry["sourceManifest"], "Android ASR provenance")
    _verify_hash(repo_root, entry["input"], "Android ASR input")
    expected_summary = {**computed, "rawReportSha256": entry["sha256"],
                        "sourceManifestSha256": entry["sourceManifest"]["sha256"]}
    hashes = provenance.get("sourceHashes")
    if (summary != expected_summary or provenance.get("schemaVersion") != 1
            or provenance.get("runtimeScope") != "installed-ordinary-app-fixed-input-resource"
            or not isinstance(provenance.get("appSha256"), str)
            or not re.fullmatch(r"[0-9a-f]{64}", provenance["appSha256"])
            or provenance.get("installedAppSourceCommit") is not None
            or not isinstance(provenance.get("frozenBuildSourceCommit"), str)
            or not re.fullmatch(r"[0-9a-f]{40}", provenance["frozenBuildSourceCommit"])
            or not isinstance(hashes, dict)
            or hashes.get(entry["path"]) != entry["sha256"]
            or hashes.get(entry["input"]["path"]) != entry["input"]["sha256"]
            or raw["inputSHA256"] != entry["input"]["sha256"]
            or not isinstance(raw.get("deviceModel"), str) or not raw["deviceModel"]
            or type(raw.get("sdkInt")) is not int or raw["sdkInt"] < 26):
        raise ValueError("invalid Android ASR summary or provenance identity")
    measured = raw["samples"][5:]
    return {
        "kind": "asr-android-resource", "p11Capability": "speech-asr-android",
        "source": entry["path"], "sourceSha256": entry["sha256"],
        "summary": dict(entry["summary"]), "sourceManifest": dict(entry["sourceManifest"]),
        "device": raw["deviceModel"], "OS": f"Android API{raw['sdkInt']}",
        "actualBackend": raw["backendId"], "scope": raw["timingMethod"],
        "identity": {"sessionRunId": raw["runId"], "processIdentifier": raw["pid"],
                     "appSha256": provenance["appSha256"], "input": dict(entry["input"]),
                     "installedAppSourceCommit": None,
                     "frozenTestBuildSourceCommit": provenance["frozenBuildSourceCommit"]},
        "runIds": [f"{raw['runId']}-sample-{row['index']}" for row in measured],
        "timingsMs": {"requestWall": _nearest_rank([row["durationNs"] / 1e6 for row in measured])},
        "quality": {"status": "unverified", "reason": "fixed synthetic match is not natural CER/WER"},
        "memory": {"status": "observed-sampled", "method": raw["measurementMethod"],
                   "peakSampledBytes": _nearest_rank([row["rssPeakSampledBytes"] for row in measured])},
        "energy": {"status": "unmeasured"}, "unverified": list(computed["unverified"]),
    }


def _android_litertlm_group(raw: dict[str, Any], entry: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    computed = android_litertlm_resource_report.summarize(raw)
    evidence = [entry.get(key) for key in ("summary", "sourceManifest", "input")]
    if not all(isinstance(item, dict) for item in evidence):
        raise ValueError("invalid Android LiteRT-LM evidence entries")
    paths = [_relative_path(repo_root, item.get("path"), "Android LiteRT-LM evidence path")
             for item in (entry, *evidence)]
    for index, path in enumerate(paths):
        if any(_is_alias(path, earlier) for earlier in paths[:index]):
            raise ValueError("Android LiteRT-LM evidence alias")
    _, summary = _verify_file(repo_root, entry["summary"], "Android LiteRT-LM summary")
    _, provenance = _verify_file(repo_root, entry["sourceManifest"], "Android LiteRT-LM provenance")
    _verify_hash(repo_root, entry["input"], "Android LiteRT-LM input")
    expected_summary = {**computed, "rawReportSha256": entry["sha256"],
                        "sourceManifestSha256": entry["sourceManifest"]["sha256"]}
    hashes = provenance.get("sourceHashes")
    if (summary != expected_summary or type(provenance.get("schemaVersion")) is not int or provenance["schemaVersion"] != 1
            or provenance.get("runtimeScope") != "installed-ordinary-app-fixed-input-resource"
            or not isinstance(provenance.get("appSha256"), str)
            or not re.fullmatch(r"[0-9a-f]{64}", provenance["appSha256"])
            or provenance.get("installedAppSourceCommit") is not None
            or not isinstance(provenance.get("frozenBuildSourceCommit"), str)
            or not re.fullmatch(r"[0-9a-f]{40}", provenance["frozenBuildSourceCommit"])
            or not isinstance(hashes, dict)
            or hashes.get(entry["path"]) != entry["sha256"]
            or hashes.get(entry["input"]["path"]) != entry["input"]["sha256"]
            or raw["inputSHA256"] != entry["input"]["sha256"]
            or provenance.get("modelSHA256") != raw["modelSHA256"]
            or provenance.get("modelRevision") != raw["modelRevision"]
            or not isinstance(raw.get("deviceModel"), str) or not raw["deviceModel"]
            or type(raw.get("sdkInt")) is not int or raw["sdkInt"] < 26):
        raise ValueError("invalid Android LiteRT-LM summary or provenance identity")
    measured = raw["samples"][1:]
    return {
        "kind": "litertlm-android-resource", "p11Capability": "multimodal-android-litertlm",
        "source": entry["path"], "sourceSha256": entry["sha256"],
        "summary": dict(entry["summary"]), "sourceManifest": dict(entry["sourceManifest"]),
        "device": raw["deviceModel"], "OS": f"Android API{raw['sdkInt']}",
        "actualBackend": raw["backendId"], "scope": raw["timingMethod"],
        "identity": {"sessionRunId": raw["runId"], "processIdentifier": raw["pid"],
                     "appSha256": provenance["appSha256"], "input": dict(entry["input"]),
                     "installedAppSourceCommit": None,
                     "frozenTestBuildSourceCommit": provenance["frozenBuildSourceCommit"],
                     "modelId": raw["modelId"], "modelRevision": raw["modelRevision"],
                     "runtimeRevision": raw["runtimeRevision"], "modelSHA256": raw["modelSHA256"],
                     "promptSHA256": raw["promptSHA256"], "maxOutputTokens": raw["maxOutputTokens"],
                     "backendPolicy": raw["backendPolicy"]},
        "runIds": [f"{raw['runId']}-sample-{row['index']}" for row in measured],
        "timingsMs": {"requestWall": _nearest_rank([row["durationNs"] / 1e6 for row in measured])},
        "quality": {"status": "unverified", "reason": "fixed red-word match is not natural image quality"},
        "memory": {"status": "observed-sampled", "method": raw["measurementMethod"],
                   "peakSampledBytes": _nearest_rank([row["rssPeakSampledBytes"] for row in measured])},
        "energy": {"status": "unmeasured"}, "unverified": list(computed["unverified"]),
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


def _video_sr_sdk_group(raw: dict[str, Any], entry: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    summary_entry = entry.get("summary")
    input_entry = entry.get("input")
    reference_entry = entry.get("reference")
    output_entries = entry.get("outputs")
    quality_entries = entry.get("quality")
    if (not all(isinstance(value, dict) for value in (summary_entry, input_entry, reference_entry,
                                                       quality_entries))
            or not isinstance(output_entries, list) or len(output_entries) != 5):
        raise ValueError("invalid SDK Video SR evidence entries")
    evidence_entries = [entry, summary_entry, input_entry, reference_entry,
                        *output_entries, *quality_entries.values()]
    if not all(isinstance(item, dict) for item in evidence_entries):
        raise ValueError("invalid SDK Video SR evidence entries")
    evidence_paths = [_relative_path(repo_root, item.get("path"), "SDK Video SR evidence path")
                      for item in evidence_entries]
    for index, path in enumerate(evidence_paths):
        if any(_is_alias(path, earlier) for earlier in evidence_paths[:index]):
            raise ValueError(f"SDK Video SR evidence alias: {path}")
    _, summary = _verify_file(repo_root, summary_entry, "SDK Video SR summary")
    _verify_hash(repo_root, input_entry, "SDK Video SR input")
    _verify_hash(repo_root, reference_entry, "SDK Video SR reference")
    runs = raw.get("runs")
    device = raw.get("deviceModel")
    os_version = raw.get("osVersion")
    session_id = raw.get("runId")
    backend = "apple.videotoolbox.sr"
    if (raw.get("schemaVersion") != 1 or raw.get("state") != "success"
            or raw.get("scope") != "isolated-public-sdk-consumer"
            or raw.get("backendId") != backend or raw.get("audioPolicy") != "discard"
            or not isinstance(device, str) or not re.fullmatch(r"iPhone[0-9]+,[0-9]+", device)
            or not isinstance(os_version, str) or not os_version
            or not isinstance(session_id, str) or not session_id
            or raw.get("inputSHA256") != input_entry["sha256"]
            or not isinstance(raw.get("measurement"), str) or not raw["measurement"]
            or not isinstance(runs, list) or len(runs) != 5
            or summary.get("schemaVersion") != 1 or summary.get("state") != "pass-bounded"
            or summary.get("qualityVerdict") != "unverified"
            or summary.get("rawReportSHA256") != entry["sha256"]
            or summary.get("runCount") != 5 or summary.get("deviceModel") != device
            or summary.get("osVersion") != os_version
            or summary.get("inputSHA256") != input_entry["sha256"]):
        raise ValueError("invalid SDK Video SR source identity")

    run_ids: set[str] = set()
    output_hashes: dict[str, str] = {}
    walls: list[float] = []
    peaks: list[int] = []
    deltas: list[int] = []
    for index, (run, output_entry) in enumerate(zip(runs, output_entries), start=1):
        if not isinstance(run, dict) or not isinstance(output_entry, dict):
            raise ValueError("invalid SDK Video SR run")
        _verify_hash(repo_root, output_entry, f"SDK Video SR output {index}")
        run_id = run.get("runId")
        wall = run.get("wallMilliseconds")
        before, after, peak = (run.get(key) for key in (
            "rssBeforeBytes", "rssAfterBytes", "rssSampledPeakBytes"))
        if (not isinstance(run_id, str) or not run_id or run_id == session_id
                or run_id in run_ids or run.get("index") != index
                or run.get("backendId") != backend
                or not isinstance(run.get("outputFile"), str)
                or run["outputFile"] != Path(output_entry["path"]).name
                or run.get("outputSHA256") != output_entry["sha256"]
                or run["outputFile"] in output_hashes
                or run.get("width") != 1280 or run.get("height") != 720
                or run.get("frameCount") != 48 or run.get("durationSeconds") != 2
                or run.get("audioTrackCount") != 0
                or type(wall) not in (int, float) or not math.isfinite(wall) or wall <= 0
                or any(type(value) is not int or value < 0 for value in (before, after, peak))
                or peak < max(before, after)
                or type(run.get("rssSampleCount")) is not int or run["rssSampleCount"] <= 0):
            raise ValueError("invalid SDK Video SR run")
        run_ids.add(run_id)
        output_hashes[run["outputFile"]] = output_entry["sha256"]
        walls.append(wall)
        peaks.append(peak)
        deltas.append(peak - before)
    if summary.get("outputSHA256") != output_hashes:
        raise ValueError("invalid SDK Video SR summary outputs")

    quality_metrics = {}
    for label, run in (("first", runs[0]), ("last", runs[-1])):
        quality_entry = quality_entries.get(label)
        if not isinstance(quality_entry, dict):
            raise ValueError(f"invalid SDK Video SR {label} quality entry")
        _, quality = _verify_file(repo_root, quality_entry, f"SDK Video SR {label} quality")
        candidate = quality.get("candidate")
        reference = quality.get("reference")
        metrics = quality.get("metrics")
        if (quality.get("schemaVersion") != 1
                or quality.get("measurement") != "decoded-output-fidelity"
                or quality.get("qualityVerdict") != "unverified"
                or quality.get("colorComparability") != "matched-metadata"
                or quality.get("comparedFrames") != 48
                or not isinstance(candidate, dict) or not isinstance(reference, dict)
                or candidate.get("path") != output_entries[run["index"] - 1]["path"]
                or reference.get("path") != reference_entry["path"]
                or not isinstance(candidate.get("decodedSha256"), str)
                or not re.fullmatch(r"[0-9a-f]{64}", candidate["decodedSha256"])
                or not isinstance(reference.get("decodedSha256"), str)
                or not re.fullmatch(r"[0-9a-f]{64}", reference["decodedSha256"])
                or not isinstance(metrics, dict)
                or type(metrics.get("psnrDb")) not in (int, float)
                or not math.isfinite(metrics["psnrDb"]) or metrics["psnrDb"] <= 0
                or type(metrics.get("ssim")) not in (int, float)
                or not math.isfinite(metrics["ssim"]) or not 0 <= metrics["ssim"] <= 1):
            raise ValueError(f"invalid SDK Video SR {label} quality identity")
        quality_metrics[label] = {"source": dict(quality_entry), "metrics": metrics,
                                  "candidateDecodedSha256": candidate["decodedSha256"],
                                  "referenceDecodedSha256": reference["decodedSha256"]}
    if quality_metrics["first"]["referenceDecodedSha256"] != quality_metrics["last"]["referenceDecodedSha256"]:
        raise ValueError("invalid SDK Video SR quality reference identity")
    return {
        "kind": "video-sr-sdk-resource", "p11Capability": "video-sr",
        "source": entry["path"], "sourceSha256": entry["sha256"],
        "summary": dict(summary_entry), "scope": raw["scope"],
        "device": device, "OS": os_version, "actualBackend": backend,
        "identity": {"sessionRunId": session_id, "inputFileSha256": raw["inputSHA256"],
                     "measurement": raw["measurement"], "firstRunIncludesWarmup": True},
        "runIds": [run["runId"] for run in runs],
        "timingsMs": {"requestWall": _nearest_rank(walls)},
        "quality": {"status": "unverified", "reason": "single-clip fidelity is not subjective SR quality",
                    "measurement": "decoded-output-fidelity", "firstAndLast": quality_metrics,
                    "reference": dict(reference_entry), "input": dict(input_entry),
                    "outputs": [dict(item) for item in output_entries]},
        "memory": {"status": "observed-sampled", "method": raw["measurement"],
                   "peakSampledBytes": _nearest_rank(peaks),
                   "peakDeltaSampledBytes": _nearest_rank(deltas)},
        "energy": {"status": "unmeasured"},
        "unverified": ["subjectiveQuality", "sustainedResources", "truePeakMemory",
                       "gpuPeak", "energy", "minimumOS", "crossDevice"],
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
        elif entry["kind"] == "asr-file-sdk-resource":
            groups.append(_asr_file_sdk_group(raw, entry, repo_root))
        elif entry["kind"] == "asr-android-resource":
            groups.append(_android_asr_group(raw, entry, repo_root))
        elif entry["kind"] == "litertlm-android-resource":
            groups.append(_android_litertlm_group(raw, entry, repo_root))
        elif entry["kind"] == "video-sr-resource":
            groups.extend(_video_sr_groups(raw, entry, repo_root))
        else:
            groups.append(_video_sr_sdk_group(raw, entry, repo_root))
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
        "grouping": ["device", "OS", "p11Capability", "actualBackend", "kind"],
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
        if isinstance(source, dict) and source.get("kind") in ("asr-file-sdk-resource", "asr-android-resource", "litertlm-android-resource"):
            candidates.extend(source.get(key) for key in ("sourceManifest", "input"))
        if not isinstance(source, dict) or source.get("kind") not in ("video-sr-resource", "video-sr-sdk-resource"):
            continue
        candidates.extend(source.get(key) for key in ("input", "reference"))
        if source.get("kind") == "video-sr-sdk-resource":
            nested = source.get("outputs")
            if isinstance(nested, list):
                candidates.extend(nested)
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
