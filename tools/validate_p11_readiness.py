"""Validate a P11 readiness snapshot without changing the snapshot."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


class ReadinessValidationError(ValueError):
    """Raised when a readiness snapshot cannot support its declared gate."""


_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_TERMINAL_PASS_STATUSES = frozenset({"pass"})


def _relative_path(root: Path, raw: Any, label: str) -> Path:
    if not isinstance(raw, str) or not raw or Path(raw).is_absolute():
        raise ReadinessValidationError(f"{label} must be a relative path")
    path = (root / raw).resolve()
    try:
        path.relative_to(root.resolve())
    except ValueError as exc:
        raise ReadinessValidationError(f"{label} escapes repository root") from exc
    return path


def _require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReadinessValidationError(f"{label} must be an object")
    return value


def validate_summary(summary: dict[str, Any], repo_root: Path) -> dict[str, int]:
    """Validate a decoded readiness summary and return checked counts.

    The function is intentionally read-only. It checks that evidence paths and
    source hashes still describe the checkout, then verifies the gate counts and
    the conservative not-ready conclusion whenever any row has an open gap.
    """

    if not isinstance(summary, dict) or summary.get("schemaVersion") != 1:
        raise ReadinessValidationError("schemaVersion must be 1")

    stop_contract = summary.get("p11Stop")
    if not isinstance(stop_contract, str) or not stop_contract.strip():
        raise ReadinessValidationError("p11Stop must be a non-empty string")
    required_before = summary.get("requiredBeforeP11")
    if (
        not isinstance(required_before, list)
        or not required_before
        or any(not isinstance(item, str) or not item.strip() for item in required_before)
    ):
        raise ReadinessValidationError(
            "requiredBeforeP11 must be a non-empty list of strings"
        )

    rows = summary.get("rows")
    if not isinstance(rows, list) or not rows:
        raise ReadinessValidationError("rows must be a non-empty array")

    evidence_checked = 0
    evidence_paths: set[str] = set()
    rows_with_gaps = 0
    rows_not_terminal = 0
    for index, row_value in enumerate(rows):
        row = _require_mapping(row_value, f"rows[{index}]")
        if not isinstance(row.get("capability"), str) or not row["capability"]:
            raise ReadinessValidationError(f"rows[{index}].capability is required")
        status = row.get("status")
        if not isinstance(status, str) or not status:
            raise ReadinessValidationError(f"rows[{index}].status is required")
        evidence = row.get("evidence")
        if not isinstance(evidence, list) or not evidence:
            raise ReadinessValidationError(f"rows[{index}].evidence must be non-empty")
        for evidence_path in evidence:
            resolved = _relative_path(repo_root, evidence_path, f"rows[{index}].evidence")
            if not resolved.is_file():
                raise ReadinessValidationError(f"missing evidence: {evidence_path}")
            evidence_paths.add(evidence_path)
            evidence_checked += 1
        unverified = row.get("unverified")
        if not isinstance(unverified, list):
            raise ReadinessValidationError(f"rows[{index}].unverified must be an array")
        has_gaps = bool(unverified)
        rows_with_gaps += int(has_gaps)
        if has_gaps or status not in _TERMINAL_PASS_STATUSES:
            rows_not_terminal += 1

    source_hashes = _require_mapping(summary.get("sourceHashes"), "sourceHashes")
    unhashed_evidence = sorted(evidence_paths - set(source_hashes))
    if unhashed_evidence:
        raise ReadinessValidationError(
            "unhashed evidence: " + ", ".join(unhashed_evidence)
        )
    source_hashes_checked = 0
    for raw_path, expected in source_hashes.items():
        if not isinstance(expected, str) or not _SHA256.fullmatch(expected):
            raise ReadinessValidationError(f"invalid source hash: {raw_path}")
        resolved = _relative_path(repo_root, raw_path, "sourceHashes path")
        if not resolved.is_file():
            raise ReadinessValidationError(f"missing source file: {raw_path}")
        actual = hashlib.sha256(resolved.read_bytes()).hexdigest()
        if actual != expected:
            raise ReadinessValidationError(f"source hash mismatch: {raw_path}")
        source_hashes_checked += 1

    gate = _require_mapping(summary.get("p11Gate"), "p11Gate")
    expected_counts = {
        "rowsWithUnverifiedGaps": rows_with_gaps,
        "rowsNotTerminalPass": rows_not_terminal,
    }
    for key, expected in expected_counts.items():
        if gate.get(key) != expected:
            raise ReadinessValidationError(
                f"p11Gate.{key} is {gate.get(key)!r}, expected {expected}"
            )
    if rows_not_terminal:
        if gate.get("status") != "open":
            raise ReadinessValidationError(
                "p11Gate.status must remain open while non-terminal rows exist"
            )
        if gate.get("finalReleaseConclusion") != "not-ready":
            raise ReadinessValidationError(
                "p11Gate.finalReleaseConclusion must remain not-ready while non-terminal rows exist"
            )

    return {
        "rows": len(rows),
        "rowsWithUnverifiedGaps": rows_with_gaps,
        "rowsNotTerminalPass": rows_not_terminal,
        "evidencePathsChecked": evidence_checked,
        "sourceHashesChecked": source_hashes_checked,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("summary", type=Path, help="P11 readiness summary JSON")
    parser.add_argument(
        "--repo-root", type=Path, default=Path.cwd(), help="checkout containing evidence paths"
    )
    args = parser.parse_args(argv)
    try:
        summary = json.loads(args.summary.read_text(encoding="utf-8"))
        result = validate_summary(summary, args.repo_root)
    except (OSError, json.JSONDecodeError, ReadinessValidationError) as exc:
        print(f"p11 readiness invalid: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
