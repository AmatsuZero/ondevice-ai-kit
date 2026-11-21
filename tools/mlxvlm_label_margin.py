#!/usr/bin/env python3
"""Summarize selected MOVING/STILL first-token logits from a raw probe report.

This is a diagnostic boundary only. The pairwise softmax is normalized over the
two selected tokens, not over the vocabulary, and therefore is not a label
probability or a temporal-quality score.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
from typing import Any


class LabelMarginValidationError(ValueError):
    pass


EXPECTED_ROWS = (
    ("motion-0-2", "motion", "pair-0", 0),
    ("frozen-0-2", "frozen-control", "pair-0", 0),
    ("motion-2-4", "motion", "pair-1", 0),
    ("frozen-2-4", "frozen-control", "pair-1", 0),
    ("motion-4-6", "motion", "pair-2", 0),
    ("frozen-4-6", "frozen-control", "pair-2", 0),
    ("motion-0-2", "motion", "pair-0", 1),
)
TOKEN_NAMES = {"moving": "ĠMO", "still": "ĠST"}


def _pair_probability(moving: float, still: float) -> float:
    difference = moving - still
    if difference >= 0:
        return 1.0 / (1.0 + math.exp(-difference))
    exp_difference = math.exp(difference)
    return exp_difference / (1.0 + exp_difference)


def _selected(top5: list[dict[str, Any]], token_id: int, name: str) -> float:
    values = [item["logit"] for item in top5 if item.get("tokenId") == token_id]
    if len(values) != 1:
        raise LabelMarginValidationError(f"selected token {name} is missing or duplicated")
    value = values[0]
    if not isinstance(value, (int, float)) or not math.isfinite(value):
        raise LabelMarginValidationError(f"selected token {name} logit is invalid")
    return float(value)


def _validate_report(report: dict[str, Any]) -> list[dict[str, Any]]:
    if report.get("state") != "success":
        raise LabelMarginValidationError("source report is not successful")
    if report.get("scope") != "iphone-production-vlm-first-token-logit-boundary-only":
        raise LabelMarginValidationError("source report scope is not the first-token boundary")
    rows = report.get("rows")
    if not isinstance(rows, list) or len(rows) != len(EXPECTED_ROWS):
        raise LabelMarginValidationError("source report must contain exactly seven rows")
    for row, expected in zip(rows, EXPECTED_ROWS):
        identity = tuple(row.get(key) for key in ("taskId", "role", "pairId", "runIndex"))
        if identity != expected:
            raise LabelMarginValidationError("source report row identity/order mismatch")
        if row.get("logitShape") != [1, 1, 49280]:
            raise LabelMarginValidationError("source report logits shape mismatch")
        if not isinstance(row.get("logitsSha256"), str) or len(row["logitsSha256"]) != 64:
            raise LabelMarginValidationError("source report logits hash missing")
        if not isinstance(row.get("top5"), list) or len(row["top5"]) != 5:
            raise LabelMarginValidationError("source report top5 is incomplete")
    return rows


def build_summary(report: dict[str, Any], *, source_sha256: str) -> dict[str, Any]:
    rows = _validate_report(report)
    selected_ids = {"moving": 15720, "still": 5022}
    observations: list[dict[str, Any]] = []
    for row in rows:
        moving = _selected(row["top5"], selected_ids["moving"], "moving")
        still = _selected(row["top5"], selected_ids["still"], "still")
        margin = moving - still
        observations.append({
            "observationId": row["observationId"],
            "taskId": row["taskId"], "role": row["role"], "pairId": row["pairId"], "runIndex": row["runIndex"],
            "inputSha256": row["inputSha256"], "logitsSha256": row["logitsSha256"],
            "movingLogit": moving, "stillLogit": still, "movingMinusStill": margin,
            "movingPrefixVsStillPrefixProbability": _pair_probability(moving, still),
        })

    by_key = {(row["taskId"], row["runIndex"]): row for row in observations}
    repeat = (by_key[("motion-0-2", 0)], by_key[("motion-0-2", 1)])
    if repeat[0]["logitsSha256"] != repeat[1]["logitsSha256"]:
        raise LabelMarginValidationError("same-input repeat logits hash differs")
    if repeat[0]["movingLogit"] != repeat[1]["movingLogit"] or repeat[0]["stillLogit"] != repeat[1]["stillLogit"]:
        raise LabelMarginValidationError("same-input repeat selected logits differ")

    comparisons = []
    for pair_id in ("pair-0", "pair-1", "pair-2"):
        motion = next(row for row in observations if row["pairId"] == pair_id and row["role"] == "motion" and row["runIndex"] == 0)
        frozen = next(row for row in observations if row["pairId"] == pair_id and row["role"] == "frozen-control")
        comparisons.append({
            "pairId": pair_id,
            "motionMovingMinusStill": motion["movingMinusStill"],
            "frozenMovingMinusStill": frozen["movingMinusStill"],
            "motionMarginGreater": motion["movingMinusStill"] > frozen["movingMinusStill"],
        })

    return {
        "schemaVersion": 1,
        "state": "pass-bounded",
        "scope": "iphone-production-vlm-selected-label-first-token-margin-only",
        "sourceReportSha256": source_sha256,
        "selectedTokens": {
            "moving": {"id": selected_ids["moving"], "token": TOKEN_NAMES["moving"]},
            "still": {"id": selected_ids["still"], "token": TOKEN_NAMES["still"]},
        },
        "rowCount": len(observations),
        "rows": observations,
        "pairComparisons": comparisons,
        "sameInputRepeat": {"identical": True, "observationId": repeat[1]["observationId"]},
        "selectedTokenPairProbability": "normalized over the two prefix tokens ĠMO and ĠST only",
        "semanticConclusion": "unverified",
        "temporalQuality": "unverified",
    }


def _assert_safe_output(source: Path, tokenizer: Path, output: Path) -> None:
    output_resolved = output.resolve()
    if output.is_symlink():
        raise LabelMarginValidationError("refusing to overwrite symlink output")
    for input_path in (source, tokenizer):
        if output_resolved == input_path.resolve() or (output.exists() and os.path.samefile(input_path, output)):
            raise LabelMarginValidationError(f"refusing to overwrite input: {input_path.name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_report", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--expected-tokenizer-sha256", required=True)
    args = parser.parse_args()
    _assert_safe_output(args.source_report, args.tokenizer, args.output)
    raw = args.source_report.read_bytes()
    actual = hashlib.sha256(raw).hexdigest()
    if actual != args.expected_sha256:
        raise LabelMarginValidationError("source report hash mismatch")
    tokenizer_raw = args.tokenizer.read_bytes()
    tokenizer_sha256 = hashlib.sha256(tokenizer_raw).hexdigest()
    if tokenizer_sha256 != args.expected_tokenizer_sha256:
        raise LabelMarginValidationError("tokenizer hash mismatch")
    tokenizer = json.loads(tokenizer_raw)
    vocab = tokenizer.get("model", {}).get("vocab", {})
    for name, token in TOKEN_NAMES.items():
        if vocab.get(token) != {"moving": 15720, "still": 5022}[name]:
            raise LabelMarginValidationError(f"tokenizer {name} id mismatch")
    result = build_summary(json.loads(raw), source_sha256=actual)
    result["tokenizerSha256"] = tokenizer_sha256
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (LabelMarginValidationError, OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"label-margin validation failed: {error}")
