import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.mlxvlm_temporal_task_set import (
    TemporalValidationError,
    build_temporal_summary,
    normalize_temporal_answer,
    score_semantic_row,
    score_temporal_row,
)


def manifest() -> dict:
    derivatives = []
    for index, interval in enumerate(((0, 2), (2, 4), (4, 6))):
        motion = f"motion-{interval[0]}-{interval[1]}"
        frozen = f"frozen-{interval[0]}-{interval[1]}"
        pair = f"pair-{index}"
        for task_id, role, control_of in ((motion, "motion", None), (frozen, "frozen-control", motion)):
            derivatives.append({
                "taskId": task_id, "pairId": pair, "role": role,
                "sourceInterval": list(interval), "controlOf": control_of,
                "sha256": f"{task_id}-hash", "path": f"{task_id}.mp4",
            })
    return {"schemaVersion": 1, "source": {"sourceSha256": "source-hash"}, "derivatives": derivatives}


def raw_rows():
    rows = []
    for derivative in manifest()["derivatives"]:
        task_id = derivative["taskId"]
        if derivative["role"] == "motion":
            rows.append({
                "runId": f"semantic-{task_id}", "taskId": f"semantic-{task_id}",
                "taskKind": "semantic-motion", "inputTaskId": task_id,
                "inputSha256": derivative["sha256"], "output": "Birds are flying in a blue sky.",
                "prompt": "Describe what happens in this video in one brief sentence.",
            })
            rows.append({
                "runId": f"temporal-{task_id}", "taskId": f"temporal-{task_id}",
                "taskKind": "temporal-motion", "inputTaskId": task_id,
                "inputSha256": derivative["sha256"], "output": "MOVING",
                "prompt": "Are the birds moving across the sky or staying still? Answer exactly MOVING or STILL.",
            })
        else:
            rows.append({
                "runId": f"temporal-{task_id}", "taskId": f"temporal-{task_id}",
                "taskKind": "temporal-frozen-control", "inputTaskId": task_id,
                "inputSha256": derivative["sha256"], "output": "STILL",
                "prompt": "Are the birds moving across the sky or staying still? Answer exactly MOVING or STILL.",
            })
    for row in rows:
        row.update({
            "device": "iPhone17,2", "OS": "Version 27.0.1", "sourceCommit": "a" * 40,
            "backend": "mlx.smolvlm2-500m", "modelRevision": "model", "runtimeRevision": "runtime",
            "durationMs": 100.0, "sourceReport": "raw-device-report.json", "sourceReportSha256": "raw-report-hash",
        })
    return rows


class TemporalTaskSetTests(unittest.TestCase):
    def test_temporal_answer_accepts_only_exact_labels(self):
        self.assertEqual(normalize_temporal_answer("MOVING"), "MOVING")
        self.assertEqual(normalize_temporal_answer(" still "), "STILL")
        for output in ("MOVING now", "MOVING and STILL", "birds move", ""):
            with self.assertRaisesRegex(TemporalValidationError, "exact"):
                normalize_temporal_answer(output)

    def test_temporal_scoring_rejects_wrong_or_ambiguous_labels(self):
        task = {"taskId": "motion-0-2", "role": "motion", "expectedLabel": "MOVING"}
        self.assertTrue(score_temporal_row({"output": "MOVING"}, task=task)["pass"])
        for output in ("STILL", "MOVING now", "MOVING and STILL", "birds move"):
            result = score_temporal_row({"output": output}, task=task)
            self.assertFalse(result["pass"])

    def test_semantic_scoring_reuses_bounded_rubric(self):
        result = score_semantic_row({"output": "Birds are flying in a blue sky."}, task={"taskId": "motion-0-2"})
        self.assertTrue(result["semanticPass"])
        self.assertEqual(result["rubricRevision"], "natural-video-semantic-rubric-v1")

    def test_summary_rejects_duplicate_unknown_missing_and_wrong_count(self):
        rows = raw_rows()
        with tempfile.TemporaryDirectory() as root:
            report = Path(root) / "raw-device-report.json"
            report.write_text("raw")
            report_hash = hashlib.sha256(report.read_bytes()).hexdigest()
            for row in rows:
                row["sourceReportSha256"] = report_hash
            expected = len(rows)
            self.assertEqual(expected, 9)
            with self.assertRaisesRegex(TemporalValidationError, "duplicate"):
                build_temporal_summary(rows + [dict(rows[0], runId="other")], manifest=manifest(), source_report=report)
            with self.assertRaisesRegex(TemporalValidationError, "unknown"):
                build_temporal_summary([dict(row, taskId="unknown") if i == 0 else row for i, row in enumerate(rows)], manifest=manifest(), source_report=report)
            with self.assertRaisesRegex(TemporalValidationError, "exactly"):
                build_temporal_summary(rows[:-1], manifest=manifest(), source_report=report)

    def test_summary_rejects_stale_source_report_hash_and_is_deterministic(self):
        rows = raw_rows()
        with tempfile.TemporaryDirectory() as root:
            report = Path(root) / "raw-device-report.json"
            report.write_text("raw")
            with self.assertRaisesRegex(TemporalValidationError, "source report"):
                build_temporal_summary(rows, manifest=manifest(), source_report=report)
            actual = hashlib.sha256(report.read_bytes()).hexdigest()
            for row in rows:
                row["sourceReportSha256"] = actual
            first = build_temporal_summary(rows, manifest=manifest(), source_report=report)
            second = build_temporal_summary(list(reversed(rows)), manifest=manifest(), source_report=report)
            self.assertEqual(json.dumps(first, sort_keys=True), json.dumps(second, sort_keys=True))
            self.assertEqual(first["temporalSuiteVerdict"], "pass-bounded")
            self.assertEqual(len(first["pairs"]), 3)


if __name__ == "__main__":
    unittest.main()
