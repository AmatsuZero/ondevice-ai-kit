#!/usr/bin/env python3
"""Validate bounded semantic quality evidence for a fixed natural MLXVLM video."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class QualityValidationError(ValueError):
    """Raised when a report cannot support the fixed quality contract."""


@dataclass(frozen=True)
class QualityIdentity:
    fixture_sha256: str
    backend: str
    model_revision: str
    runtime_revision: str
    prompt: str
    expected_runs: int | None = None
    source_report: str | None = None
    source_report_sha256: str | None = None


_SUBJECT = ("birds", "swifts")
_ACTION = ("fly", "flying", "flight", "in flight", "soar", "soaring")
_SCENE = ("blue", "cloud", "clouds")
_RUBRIC_REVISION = "natural-video-semantic-rubric-v1"


def _required(mapping: dict[str, Any], key: str) -> Any:
    value = mapping.get(key)
    if value is None or (isinstance(value, str) and not value.strip()):
        raise QualityValidationError(f"missing {key}")
    return value


def _contains_any(text: str, values: tuple[str, ...]) -> bool:
    return any(re.search(rf"(?<![a-z]){re.escape(value)}(?![a-z])", text) for value in values)


def score_output(output: str, rubric: str = _RUBRIC_REVISION) -> dict[str, Any]:
    if rubric != _RUBRIC_REVISION:
        raise QualityValidationError(f"unsupported rubric revision: {rubric}")
    text = output.strip().lower()
    categories = {
        "subject": _contains_any(text, _SUBJECT),
        "action": _contains_any(text, _ACTION),
        "scene": "sky" in text and _contains_any(text, _SCENE),
    }
    return {
        "rubricRevision": rubric,
        "categories": categories,
        "semanticPass": all(categories.values()),
        "unverified": ["naturalTemporalQuality", "gpuEnergy", "sustainedResources"],
    }


def normalize_run(raw: dict[str, Any], *, expected: QualityIdentity) -> dict[str, Any]:
    required = {
        "runId": "runId",
        "device": "device",
        "OS": "osVersion",
        "sourceCommit": "sourceCommit",
        "actualBackend": "backend",
        "modelRevision": "modelRevision",
        "runtimeRevision": "runtimeRevision",
        "fixtureHash": "fixtureSha256",
        "prompt": "prompt",
        "output": "output",
        "durationMs": "durationMs",
        "sourceReport": "sourceReport",
        "sourceReportSha256": "sourceReportSha256",
    }
    values = {target: _required(raw, source) for source, target in required.items()}
    if values["backend"] != expected.backend:
        raise QualityValidationError("backend identity mismatch")
    if values["fixtureSha256"] != expected.fixture_sha256:
        raise QualityValidationError("fixture identity mismatch")
    if values["modelRevision"] != expected.model_revision:
        raise QualityValidationError("model revision mismatch")
    if values["runtimeRevision"] != expected.runtime_revision:
        raise QualityValidationError("runtime revision mismatch")
    if values["prompt"] != expected.prompt:
        raise QualityValidationError("prompt mismatch")
    if expected.source_report is not None and values["sourceReport"] != expected.source_report:
        raise QualityValidationError("source report path mismatch")
    if expected.source_report_sha256 is not None and values["sourceReportSha256"] != expected.source_report_sha256:
        raise QualityValidationError("source report hash mismatch")
    if raw.get("qualityScope") != _RUBRIC_REVISION:
        raise QualityValidationError("qualityScope mismatch")
    if not isinstance(values["output"], str) or not values["output"].strip():
        raise QualityValidationError("output must be non-empty")
    if not isinstance(values["durationMs"], (int, float)) or values["durationMs"] <= 0:
        raise QualityValidationError("durationMs must be positive")
    score = score_output(values["output"])
    return {**values, "qualityScope": raw.get("qualityScope", "natural-video-semantic-rubric-v1"), "score": score}


def build_summary(runs: list[dict[str, Any]], *, expected: QualityIdentity, rubric: str = _RUBRIC_REVISION) -> dict[str, Any]:
    if not runs:
        raise QualityValidationError("no runs")
    ids = [run["runId"] for run in runs]
    if len(ids) != len(set(ids)):
        raise QualityValidationError("duplicate runId")
    if expected.expected_runs is not None and len(runs) != expected.expected_runs:
        raise QualityValidationError(f"expected {expected.expected_runs} runs, got {len(runs)}")
    for run in runs:
        if run["score"]["rubricRevision"] != rubric:
            raise QualityValidationError("rubric revision mismatch")
    ordered = sorted(runs, key=lambda run: run["runId"])
    counts = {category: sum(run["score"]["categories"][category] for run in ordered) for category in ("subject", "action", "scene")}
    return {
        "schemaVersion": 1,
        "rubricRevision": rubric,
        "expectedIdentity": {
            "fixtureSha256": expected.fixture_sha256,
            "backend": expected.backend,
            "modelRevision": expected.model_revision,
            "runtimeRevision": expected.runtime_revision,
            "prompt": expected.prompt,
        },
        "runCount": len(ordered),
        "semanticPassCount": sum(run["score"]["semanticPass"] for run in ordered),
        "categoryPassCounts": counts,
        "runs": ordered,
        "unverified": ["naturalTemporalQuality", "gpuEnergy", "sustainedResources"],
        "conclusion": "bounded semantic evidence only; not a complete natural-quality, temporal-quality, or resource acceptance",
    }


def _load_identity(path: Path) -> QualityIdentity:
    data = json.loads(path.read_text(encoding="utf-8"))
    source_report = data.get("sourceReport")
    source_report_sha256 = data.get("sourceReportSha256")
    if source_report is not None:
        source = (path.parent / source_report).resolve()
        if not source.is_file():
            raise QualityValidationError("source report is missing")
        actual = hashlib.sha256(source.read_bytes()).hexdigest()
        if actual != source_report_sha256:
            raise QualityValidationError("source report hash mismatch")
    return QualityIdentity(
        fixture_sha256=data["fixtureSha256"],
        backend=data["backend"],
        model_revision=data["modelRevision"],
        runtime_revision=data["runtimeRevision"],
        prompt=data["prompt"],
        expected_runs=data.get("expectedRuns"),
        source_report=source_report,
        source_report_sha256=source_report_sha256,
    )


def _validate(report: Path, identity: Path, output: Path) -> None:
    expected = _load_identity(identity)
    raw = json.loads(report.read_text(encoding="utf-8"))
    raw_runs = raw if isinstance(raw, list) else raw.get("runs")
    if not isinstance(raw_runs, list):
        raise QualityValidationError("report must contain a runs array")
    runs = [normalize_run(run, expected=expected) for run in raw_runs]
    result = build_summary(runs, expected=expected)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    validate = sub.add_parser("validate")
    validate.add_argument("report", type=Path)
    validate.add_argument("--expected", required=True, type=Path)
    validate.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        _validate(args.report, args.expected, args.output)
    except (OSError, KeyError, json.JSONDecodeError, QualityValidationError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
