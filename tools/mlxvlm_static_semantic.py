#!/usr/bin/env python3
"""Validate a bounded static-frame semantic MLXVLM report.

This report is deliberately separate from the temporal exact-label suite. It
checks only whether frozen controls preserve the visible birds/sky semantic
rubric; temporal quality and resources remain unverified.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any

from .mlxvlm_natural_quality import score_output


class StaticSemanticValidationError(ValueError):
    pass


PROMPT = "Describe the visible scene in one brief sentence."
EXPECTED_ROWS = (
    ("static-semantic-frozen-0-2", "frozen-0-2", "pair-0", "frozen-control", "23d39b285ded6d2440488e20d4d90033a87676f81faf7c1ca0402270c76d1cb4"),
    ("static-semantic-frozen-2-4", "frozen-2-4", "pair-1", "frozen-control", "1ea2ff7a1372702e33f4f05ba7c092ce4e9405eb70d0d485c6b1803b160d7bb5"),
    ("static-semantic-frozen-4-6", "frozen-4-6", "pair-2", "frozen-control", "caad176f6c9547c1fc277e7f64697dc5137f20dfa306c68fd2cdd930a85cc8a4"),
)
EXPECTED_BUNDLE = "io.ondeviceai.staticsemantic"
EXPECTED_BACKEND = "mlx.smolvlm2-500m"
EXPECTED_MANIFEST = "666fbfebdc999aefa5b9db944a090024b9478c13107685af621fb842babe1694"
EXPECTED_MODEL = "fa57db46815177fbdfd65cc85a2b3416a8332268"
EXPECTED_RUNTIME = "6e338e5df49424fc0db7c6d58184d622f92ed18a"


def verify_provenance(report: dict[str, Any], launch: dict[str, Any], install: dict[str, Any], *, source_commit: str) -> None:
    expected = {
        "bundleIdentifier": EXPECTED_BUNDLE, "sourceCommit": source_commit,
        "actualBackend": EXPECTED_BACKEND, "manifestSha256": EXPECTED_MANIFEST,
        "modelRevision": EXPECTED_MODEL, "runtimeRevision": EXPECTED_RUNTIME,
    }
    if any(report.get(key) != value for key, value in expected.items()):
        raise StaticSemanticValidationError("report provenance mismatch")
    if launch.get("info", {}).get("outcome") != "success" or install.get("info", {}).get("outcome") != "success":
        raise StaticSemanticValidationError("device receipt failed")
    launch_result = launch.get("result", {})
    applications = install.get("result", {}).get("installedApplications", [])
    arguments = launch_result.get("launchOptions", {}).get("arguments", [])
    if (launch_result.get("process", {}).get("processIdentifier") != report.get("pid")
            or "--sdk-mlxvlm-static-semantic" not in arguments
            or f"--sdk-commit={source_commit}" not in arguments
            or f"--sdk-temporal-manifest-sha256={EXPECTED_MANIFEST}" not in arguments
            or not any(app.get("bundleID") == EXPECTED_BUNDLE for app in applications)):
        raise StaticSemanticValidationError("device receipt identity mismatch")
    if launch_result.get("deviceIdentifier") != install.get("result", {}).get("deviceIdentifier"):
        raise StaticSemanticValidationError("device receipt target mismatch")
    for row in report.get("rows", []):
        if any(row.get(key) != value for key, value in (
            ("sourceCommit", source_commit), ("backend", EXPECTED_BACKEND),
            ("modelRevision", EXPECTED_MODEL), ("runtimeRevision", EXPECTED_RUNTIME),
            ("prompt", PROMPT),
        )):
            raise StaticSemanticValidationError("row provenance mismatch")


def build_summary(report: dict[str, Any], *, source_sha256: str) -> dict[str, Any]:
    if report.get("state") != "success":
        raise StaticSemanticValidationError("source report is not successful")
    if report.get("scope") != "iphone-production-vlm-static-semantic-only":
        raise StaticSemanticValidationError("source report scope mismatch")
    if report.get("prompt") != PROMPT:
        raise StaticSemanticValidationError("prompt mismatch")
    rows = report.get("rows")
    if not isinstance(rows, list) or len(rows) != len(EXPECTED_ROWS):
        raise StaticSemanticValidationError("source report must contain exactly three rows")
    normalized = []
    for row, expected in zip(rows, EXPECTED_ROWS):
        task_id, input_id, pair_id, role, fixture_hash = expected
        if tuple(row.get(key) for key in ("taskId", "inputTaskId", "pairId", "role")) != (task_id, input_id, pair_id, role):
            raise StaticSemanticValidationError("source report row identity/order mismatch")
        if row.get("inputSha256") != fixture_hash:
            raise StaticSemanticValidationError(f"fixture hash mismatch: {task_id}")
        if row.get("prompt") != PROMPT:
            raise StaticSemanticValidationError(f"prompt mismatch: {task_id}")
        output = row.get("output")
        if not isinstance(output, str) or not output.strip():
            raise StaticSemanticValidationError(f"output missing: {task_id}")
        natural_score = score_output(output)
        categories = {key: natural_score["categories"][key] for key in ("subject", "scene")}
        score = {
            "rubricRevision": "static-frame-scene-rubric-v1",
            "categories": categories,
            "semanticPass": all(categories.values()),
        }
        normalized.append({
            "runId": row.get("runId"), "taskId": task_id, "inputTaskId": input_id,
            "pairId": pair_id, "role": role, "inputSha256": fixture_hash,
            "output": output, "durationMs": row.get("durationMs"), "score": score,
        })
    run_ids = [row["runId"] for row in normalized]
    if any(not isinstance(run_id, str) or not run_id for run_id in run_ids) or len(set(run_ids)) != len(run_ids):
        raise StaticSemanticValidationError("run IDs must be present and unique")
    return {
        "schemaVersion": 1, "state": "pass-bounded",
        "scope": "iphone-production-vlm-static-semantic-only",
        "sourceReportSha256": source_sha256, "prompt": PROMPT,
        "rowCount": len(normalized), "semanticPassCount": sum(row["score"]["semanticPass"] for row in normalized),
        "semanticVerdict": "pass-bounded" if all(row["score"]["semanticPass"] for row in normalized) else "observed-failure",
        "rows": normalized,
        "temporalQuality": "unverified", "gpuEnergy": "unverified", "sustainedResources": "unverified",
        "conclusion": "bounded frozen-frame semantic evidence only; not temporal-quality or resource acceptance",
    }


def _assert_safe_output(output: Path, *sources: Path) -> None:
    if output.is_symlink() or any(
        output.resolve() == source.resolve() or (output.exists() and os.path.samefile(source, output))
        for source in sources
    ):
        raise StaticSemanticValidationError("refusing to overwrite input evidence")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_report", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--launch-receipt", type=Path, required=True)
    parser.add_argument("--install-receipt", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    try:
        _assert_safe_output(args.output, args.source_report, args.launch_receipt, args.install_receipt)
        raw = args.source_report.read_bytes()
        actual = hashlib.sha256(raw).hexdigest()
        if actual != args.expected_sha256:
            raise StaticSemanticValidationError("source report hash mismatch")
        report = json.loads(raw)
        verify_provenance(report, json.loads(args.launch_receipt.read_text()),
                          json.loads(args.install_receipt.read_text()), source_commit=args.source_commit)
        result = build_summary(report, source_sha256=actual)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, json.JSONDecodeError, StaticSemanticValidationError) as error:
        print(f"static semantic validation failed: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
