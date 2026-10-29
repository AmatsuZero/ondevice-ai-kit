#!/usr/bin/env python3
"""Validate bounded MLXVLM motion/control task-set evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
from typing import Any

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.mlxvlm_natural_quality import score_output


class TemporalValidationError(ValueError):
    """Raised when temporal evidence cannot support the fixed contract."""


TEMPORAL_PROMPT = "Are the birds moving across the sky or staying still? Answer exactly MOVING or STILL."
SEMANTIC_PROMPT = "Describe what happens in this video in one brief sentence."


def normalize_temporal_answer(output: str) -> str:
    if not isinstance(output, str):
        raise TemporalValidationError("exact temporal answer required")
    normalized = output.strip().upper()
    if normalized not in {"MOVING", "STILL"}:
        raise TemporalValidationError("exact temporal answer required")
    return normalized


def score_temporal_row(raw: dict[str, Any], *, task: dict[str, Any]) -> dict[str, Any]:
    output = raw.get("output")
    try:
        normalized = normalize_temporal_answer(output)
    except TemporalValidationError:
        normalized = output.strip().upper() if isinstance(output, str) else ""
    expected = task["expectedLabel"]
    return {
        "expectedLabel": expected,
        "normalizedAnswer": normalized,
        "pass": normalized == expected,
    }


def score_semantic_row(raw: dict[str, Any], *, task: dict[str, Any]) -> dict[str, Any]:
    del task
    return score_output(raw.get("output", ""))


def _required(row: dict[str, Any], key: str) -> Any:
    value = row.get(key)
    if value is None or (isinstance(value, str) and not value.strip()):
        raise TemporalValidationError(f"missing {key}")
    return value


def _task_contract(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    derivatives = manifest.get("derivatives")
    if not isinstance(derivatives, list) or len(derivatives) != 6:
        raise TemporalValidationError("manifest must contain six derivatives")
    tasks: dict[str, dict[str, Any]] = {}
    for derivative in derivatives:
        source_task = _required(derivative, "taskId")
        role = _required(derivative, "role")
        if role not in {"motion", "frozen-control"}:
            raise TemporalValidationError("unknown derivative role")
        task_id = f"semantic-{source_task}" if role == "motion" else f"temporal-{source_task}"
        if role == "motion":
            tasks[task_id] = {
                "taskId": task_id, "taskKind": "semantic-motion", "inputTaskId": source_task,
                "inputSha256": derivative["sha256"], "prompt": SEMANTIC_PROMPT,
            }
            temporal_id = f"temporal-{source_task}"
            tasks[temporal_id] = {
                "taskId": temporal_id, "taskKind": "temporal-motion", "inputTaskId": source_task,
                "inputSha256": derivative["sha256"], "prompt": TEMPORAL_PROMPT,
                "expectedLabel": "MOVING", "pairId": derivative["pairId"],
            }
        else:
            tasks[task_id] = {
                "taskId": task_id, "taskKind": "temporal-frozen-control", "inputTaskId": source_task,
                "inputSha256": derivative["sha256"], "prompt": TEMPORAL_PROMPT,
                "expectedLabel": "STILL", "pairId": derivative["pairId"],
            }
    if len(tasks) != 9:
        raise TemporalValidationError("manifest task contract is not nine rows")
    return tasks


def _normalize_row(raw: dict[str, Any], task: dict[str, Any], *, source_report: Path, report_hash: str) -> dict[str, Any]:
    if _required(raw, "taskId") != task["taskId"]:
        raise TemporalValidationError("unknown task ID")
    for key in ("runId", "device", "OS", "sourceCommit", "backend", "modelRevision", "runtimeRevision", "durationMs"):
        _required(raw, key)
    if raw.get("inputTaskId") != task["inputTaskId"]:
        raise TemporalValidationError("input task mismatch")
    if raw.get("inputSha256") != task["inputSha256"]:
        raise TemporalValidationError("input hash mismatch")
    if raw.get("taskKind") != task["taskKind"]:
        raise TemporalValidationError("task kind mismatch")
    if raw.get("prompt") != task["prompt"]:
        raise TemporalValidationError("prompt mismatch")
    if Path(str(raw.get("sourceReport", ""))).name != source_report.name:
        raise TemporalValidationError("source report path mismatch")
    if raw.get("sourceReportSha256") != report_hash:
        raise TemporalValidationError("source report hash mismatch")
    if not isinstance(raw["durationMs"], (int, float)) or raw["durationMs"] <= 0:
        raise TemporalValidationError("durationMs must be positive")
    result = {
        "runId": raw["runId"], "taskId": task["taskId"], "taskKind": task["taskKind"],
        "inputTaskId": task["inputTaskId"], "inputSha256": task["inputSha256"],
        "device": raw["device"], "OS": raw["OS"], "sourceCommit": raw["sourceCommit"],
        "backend": raw["backend"], "modelRevision": raw["modelRevision"],
        "runtimeRevision": raw["runtimeRevision"], "prompt": raw["prompt"],
        "output": _required(raw, "output"), "durationMs": raw["durationMs"],
        "sourceReport": raw["sourceReport"], "sourceReportSha256": report_hash,
    }
    if task["taskKind"] == "semantic-motion":
        result["score"] = score_semantic_row(raw, task=task)
    else:
        result["score"] = score_temporal_row(raw, task=task)
    if "pairId" in task:
        result["pairId"] = task["pairId"]
    return result


def build_temporal_summary(rows: list[dict[str, Any]], *, manifest: dict[str, Any], source_report: Path) -> dict[str, Any]:
    tasks = _task_contract(manifest)
    if not source_report.is_file():
        raise TemporalValidationError("source report is missing")
    source_bytes = source_report.read_bytes()
    report_hash = hashlib.sha256(source_bytes).hexdigest()
    run_ids = [row.get("runId") for row in rows]
    if any(not isinstance(run_id, str) or not run_id.strip() for run_id in run_ids):
        raise TemporalValidationError("missing runId")
    if len(run_ids) != len(set(run_ids)):
        raise TemporalValidationError("duplicate runId")
    task_ids = [row.get("taskId") for row in rows]
    if len(task_ids) != len(set(task_ids)):
        raise TemporalValidationError("duplicate task ID")
    if len(rows) != len(tasks):
        raise TemporalValidationError("expected exactly nine rows")
    normalized = []
    for row in rows:
        task_id = row.get("taskId")
        task = tasks.get(task_id)
        if task is None:
            raise TemporalValidationError("unknown task ID")
        normalized.append(_normalize_row(row, task, source_report=source_report, report_hash=report_hash))
    source = json.loads(source_bytes)
    if (
        not isinstance(source, dict)
        or source.get("schemaVersion") != 1
        or source.get("state") != "success"
        or source.get("expectedRows") != len(tasks)
        or not isinstance(source.get("rows"), list)
        or len(source["rows"]) != len(tasks)
    ):
        raise TemporalValidationError("source report must contain a successful nine-row run")
    source_rows = source["rows"]
    for row in rows:
        original = {key: value for key, value in row.items() if key not in {"sourceReport", "sourceReportSha256"}}
        if source_rows.count(original) != 1:
            raise TemporalValidationError(f"source row mismatch: {row['taskId']}")
    ordered = sorted(normalized, key=lambda item: item["taskId"])
    temporal = [item for item in ordered if item["taskKind"] != "semantic-motion"]
    semantic = [item for item in ordered if item["taskKind"] == "semantic-motion"]
    pairs = []
    for pair_id in sorted({item["pairId"] for item in temporal}):
        members = [item for item in temporal if item["pairId"] == pair_id]
        if len(members) != 2:
            raise TemporalValidationError("pair membership mismatch")
        motion = next((item for item in members if item["taskKind"] == "temporal-motion"), None)
        frozen = next((item for item in members if item["taskKind"] == "temporal-frozen-control"), None)
        if motion is None or frozen is None:
            raise TemporalValidationError("pair membership mismatch")
        pairs.append({
            "pairId": pair_id,
            "motionTaskId": motion["taskId"], "frozenTaskId": frozen["taskId"],
            "motionPass": motion["score"]["pass"], "frozenPass": frozen["score"]["pass"],
            "pass": motion["score"]["pass"] and frozen["score"]["pass"],
        })
    return {
        "schemaVersion": 1,
        "manifestSourceSha256": manifest.get("source", {}).get("sourceSha256"),
        "sourceReport": str(source_report), "sourceReportSha256": report_hash,
        "runCount": len(ordered), "semanticPassCount": sum(item["score"]["semanticPass"] for item in semantic),
        "semanticResults": semantic, "temporalResults": temporal, "pairs": pairs,
        "temporalSuiteVerdict": "pass-bounded" if all(pair["pass"] for pair in pairs) else "observed-failure",
        "limitations": [
            "same source and scene only", "exact-label instruction following is coupled to temporal discrimination",
            "no human preference", "no GPU or energy measurement", "no sustained-resource acceptance",
            "no general temporal-quality claim",
        ],
    }


def _validate(report: Path, manifest: Path, source_report: Path, output: Path) -> None:
    for input_path in (report, manifest, source_report):
        if output.resolve() == input_path.resolve() or (output.exists() and output.samefile(input_path)):
            raise TemporalValidationError(f"output must not overwrite input: {input_path}")
    raw = json.loads(report.read_text(encoding="utf-8"))
    rows = raw if isinstance(raw, list) else raw.get("rows")
    if not isinstance(rows, list):
        raise TemporalValidationError("report must contain rows")
    result = build_temporal_summary(rows, manifest=json.loads(manifest.read_text(encoding="utf-8")), source_report=source_report)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--source-report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        _validate(args.report, args.manifest, args.source_report, args.output)
    except (OSError, KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
