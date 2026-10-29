import copy
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.mlxvlm_label_margin import LabelMarginValidationError, build_summary


TOKENS = {"moving": 15720, "still": 5022}


def report_rows():
    rows = []
    values = (
        ("motion-0-2", "motion", "pair-0", 0, 20.50, 18.875),
        ("frozen-0-2", "frozen-control", "pair-0", 0, 20.25, 19.25),
        ("motion-2-4", "motion", "pair-1", 0, 20.25, 18.625),
        ("frozen-2-4", "frozen-control", "pair-1", 0, 20.125, 18.75),
        ("motion-4-6", "motion", "pair-2", 0, 20.50, 18.75),
        ("frozen-4-6", "frozen-control", "pair-2", 0, 20.25, 19.00),
        ("motion-0-2", "motion", "pair-0", 1, 20.50, 18.875),
    )
    for task_id, role, pair_id, run_index, moving, still in values:
        hash_input = f"{task_id}-0" if task_id == "motion-0-2" else f"{task_id}-{run_index}"
        rows.append({
            "observationId": f"{task_id}-run-{run_index + 1}",
            "taskId": task_id, "role": role, "pairId": pair_id, "runIndex": run_index,
            "inputSha256": f"{task_id}-input",
            "logitsSha256": hashlib.sha256(hash_input.encode()).hexdigest(),
            "logitShape": [1, 1, 49280],
            "top5": [
                {"tokenId": TOKENS["moving"], "logit": moving},
                {"tokenId": TOKENS["still"], "logit": still},
                {"tokenId": 26332, "logit": 17.0},
                {"tokenId": 372, "logit": 16.0},
                {"tokenId": 625, "logit": 15.0},
            ],
        })
    return rows


def report(rows=None):
    return {
        "state": "success",
        "scope": "iphone-production-vlm-first-token-logit-boundary-only",
        "manifestSha256": "manifest",
        "modelIdentity": {"modelId": "model", "modelRevision": "revision", "runtimeRevision": "runtime"},
        "rows": report_rows() if rows is None else rows,
    }


class LabelMarginTests(unittest.TestCase):
    def test_build_summary_extracts_selected_label_margin_and_pair_probability(self):
        summary = build_summary(report(), source_sha256="source")
        self.assertEqual(summary["selectedTokens"], {
            "moving": {"id": 15720, "token": "ĠMO"},
            "still": {"id": 5022, "token": "ĠST"},
        })
        self.assertEqual(summary["rowCount"], 7)
        self.assertAlmostEqual(summary["rows"][0]["movingMinusStill"], 1.625)
        self.assertGreater(summary["rows"][0]["movingPrefixVsStillPrefixProbability"], 0.8)
        self.assertTrue(summary["sameInputRepeat"]["identical"])
        self.assertEqual(summary["semanticConclusion"], "unverified")

    def test_build_summary_rejects_missing_selected_logits(self):
        rows = report_rows()
        rows[0]["top5"][1] = {"tokenId": 99999, "logit": 19.25}
        with self.assertRaisesRegex(LabelMarginValidationError, "selected token"):
            build_summary(report(rows), source_sha256="source")

    def test_cli_binds_source_report_hash_and_rejects_tampered_copy(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "device-report.json"
            source.write_text(json.dumps(report(), sort_keys=True))
            tokenizer = root / "tokenizer.json"
            tokenizer.write_text(json.dumps({"model": {"vocab": {"ĠMO": 15720, "ĠST": 5022}}}))
            output = root / "summary.json"
            subprocess.run([
                sys.executable, "tools/mlxvlm_label_margin.py", str(source),
                "--output", str(output), "--expected-sha256", hashlib.sha256(source.read_bytes()).hexdigest(),
                "--tokenizer", str(tokenizer), "--expected-tokenizer-sha256", hashlib.sha256(tokenizer.read_bytes()).hexdigest(),
            ], check=True, capture_output=True)
            self.assertEqual(json.loads(output.read_text())["state"], "pass-bounded")
            tampered = copy.deepcopy(report())
            tampered["rows"][0]["top5"][0]["logit"] = 0.0
            source.write_text(json.dumps(tampered, sort_keys=True))
            failed = subprocess.run([
                sys.executable, "tools/mlxvlm_label_margin.py", str(source),
                "--output", str(output), "--expected-sha256", "stale",
                "--tokenizer", str(tokenizer), "--expected-tokenizer-sha256", hashlib.sha256(tokenizer.read_bytes()).hexdigest(),
            ], capture_output=True)
            self.assertNotEqual(failed.returncode, 0)
            self.assertEqual(json.loads(output.read_text())["sourceReportSha256"], hashlib.sha256(json.dumps(report(), sort_keys=True).encode()).hexdigest())

    def test_cli_rejects_changed_tokenizer_even_if_selected_ids_still_match(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "device-report.json"
            source.write_text(json.dumps(report()))
            tokenizer = Path(root) / "tokenizer.json"
            tokenizer.write_text(json.dumps({"model": {"vocab": {"ĠMO": 15720, "ĠST": 5022}}}))
            expected = hashlib.sha256(tokenizer.read_bytes()).hexdigest()
            tokenizer.write_text(tokenizer.read_text() + " ")
            output = Path(root) / "summary.json"
            result = subprocess.run([
                sys.executable, "tools/mlxvlm_label_margin.py", str(source),
                "--output", str(output), "--expected-sha256", hashlib.sha256(source.read_bytes()).hexdigest(),
                "--tokenizer", str(tokenizer), "--expected-tokenizer-sha256", expected,
            ], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(b"tokenizer hash mismatch", result.stderr)
            self.assertFalse(output.exists())

    def test_cli_does_not_overwrite_raw_report(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "device-report.json"
            source.write_text(json.dumps(report()))
            tokenizer = Path(root) / "tokenizer.json"
            tokenizer.write_text(json.dumps({"model": {"vocab": {"ĠMO": 15720, "ĠST": 5022}}}))
            before = source.read_bytes()
            result = subprocess.run([
                sys.executable, "tools/mlxvlm_label_margin.py", str(source),
                "--output", str(source), "--expected-sha256", hashlib.sha256(before).hexdigest(),
                "--tokenizer", str(tokenizer), "--expected-tokenizer-sha256", hashlib.sha256(tokenizer.read_bytes()).hexdigest(),
            ], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(source.read_bytes(), before)

    def test_cli_does_not_overwrite_tokenizer_input(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "device-report.json"
            source.write_text(json.dumps(report()))
            tokenizer = Path(root) / "tokenizer.json"
            tokenizer.write_text(json.dumps({"model": {"vocab": {"ĠMO": 15720, "ĠST": 5022}}}))
            before = tokenizer.read_bytes()
            result = subprocess.run([
                sys.executable, "tools/mlxvlm_label_margin.py", str(source),
                "--output", str(tokenizer), "--expected-sha256", hashlib.sha256(source.read_bytes()).hexdigest(),
                "--tokenizer", str(tokenizer), "--expected-tokenizer-sha256", hashlib.sha256(tokenizer.read_bytes()).hexdigest(),
            ], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(tokenizer.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
