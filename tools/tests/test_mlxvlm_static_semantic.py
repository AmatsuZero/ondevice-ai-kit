import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.mlxvlm_static_semantic import StaticSemanticValidationError, build_summary, verify_provenance


EXPECTED = (
    ("static-semantic-frozen-0-2", "frozen-0-2", "pair-0", "frozen-control", "23d39b285ded6d2440488e20d4d90033a87676f81faf7c1ca0402270c76d1cb4"),
    ("static-semantic-frozen-2-4", "frozen-2-4", "pair-1", "frozen-control", "1ea2ff7a1372702e33f4f05ba7c092ce4e9405eb70d0d485c6b1803b160d7bb5"),
    ("static-semantic-frozen-4-6", "frozen-4-6", "pair-2", "frozen-control", "caad176f6c9547c1fc277e7f64697dc5137f20dfa306c68fd2cdd930a85cc8a4"),
)
SOURCE_COMMIT = "a" * 40
MODEL_REVISION = "fa57db46815177fbdfd65cc85a2b3416a8332268"
RUNTIME_REVISION = "6e338e5df49424fc0db7c6d58184d622f92ed18a"
LAUNCH = {"info": {"outcome": "success"}, "result": {"deviceIdentifier": "device", "process": {"processIdentifier": 31720}, "launchOptions": {"arguments": [f"--sdk-commit={SOURCE_COMMIT}"]}}}
INSTALL = {"info": {"outcome": "success"}, "result": {"deviceIdentifier": "device", "installedApplications": [{"bundleID": "io.ondeviceai.staticsemantic"}]}}


def rows(output="Several birds are visible against a blue sky with clouds."):
    return [
        {
            "runId": f"run-{task}", "taskId": task, "inputTaskId": input_id,
            "role": role, "pairId": pair, "inputSha256": input_hash,
            "output": output, "device": "iPhone17,2", "OS": "Version 27.0.1",
            "sourceCommit": SOURCE_COMMIT, "backend": "mlx.smolvlm2-500m",
            "modelRevision": MODEL_REVISION, "runtimeRevision": RUNTIME_REVISION,
            "prompt": "Describe the visible scene in one brief sentence.",
            "durationMs": 100.0,
        }
        for task, input_id, pair, role, input_hash in EXPECTED
    ]


def report(raw_rows=None):
    return {
        "schemaVersion": 1, "state": "success",
        "scope": "iphone-production-vlm-static-semantic-only",
        "prompt": "Describe the visible scene in one brief sentence.",
        "expectedRows": 3, "rows": rows() if raw_rows is None else raw_rows,
        "bundleIdentifier": "io.ondeviceai.staticsemantic", "sourceCommit": SOURCE_COMMIT,
        "actualBackend": "mlx.smolvlm2-500m", "manifestSha256": "666fbfebdc999aefa5b9db944a090024b9478c13107685af621fb842babe1694",
        "modelRevision": MODEL_REVISION, "runtimeRevision": RUNTIME_REVISION, "pid": 31720,
    }


class StaticSemanticTests(unittest.TestCase):
    def test_summary_scores_all_frozen_rows_and_keeps_temporal_unverified(self):
        result = build_summary(report(), source_sha256="source")
        self.assertEqual(result["state"], "pass-bounded")
        self.assertEqual(result["rowCount"], 3)
        self.assertEqual(result["semanticPassCount"], 3)
        self.assertEqual(result["semanticVerdict"], "pass-bounded")
        self.assertEqual(result["temporalQuality"], "unverified")
        self.assertTrue(all(row["score"]["semanticPass"] for row in result["rows"]))

    def test_summary_preserves_wrong_semantics_and_rejects_wrong_fixture(self):
        changed = rows()
        changed[0]["output"] = "A blue sky with clouds."
        result = build_summary(report(changed), source_sha256="source")
        self.assertEqual(result["semanticPassCount"], 2)
        self.assertEqual(result["semanticVerdict"], "observed-failure")

        changed = rows()
        changed[0]["inputSha256"] = "wrong"
        with self.assertRaisesRegex(StaticSemanticValidationError, "fixture"):
            build_summary(report(changed), source_sha256="source")

    def test_cli_binds_raw_report_hash_and_does_not_overwrite_input(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "device-report.json"
            source.write_text(json.dumps(report(), sort_keys=True))
            output = root / "summary.json"
            launch = root / "launch.json"
            install = root / "install.json"
            launch.write_text(json.dumps(LAUNCH))
            install.write_text(json.dumps(INSTALL))
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            subprocess.run([
                sys.executable, "-m", "tools.mlxvlm_static_semantic", str(source),
                "--output", str(output), "--expected-sha256", digest,
                "--launch-receipt", str(launch), "--install-receipt", str(install), "--source-commit", SOURCE_COMMIT,
            ], check=True, capture_output=True)
            self.assertEqual(json.loads(output.read_text())["state"], "pass-bounded")

            before = source.read_bytes()
            failed = subprocess.run([
                sys.executable, "-m", "tools.mlxvlm_static_semantic", str(source),
                "--output", str(source), "--expected-sha256", digest,
                "--launch-receipt", str(launch), "--install-receipt", str(install), "--source-commit", SOURCE_COMMIT,
            ], capture_output=True)
            self.assertNotEqual(failed.returncode, 0)
            self.assertEqual(source.read_bytes(), before)

    def test_provenance_rejects_wrong_pid_and_model(self):
        verify_provenance(report(), LAUNCH, INSTALL, source_commit=SOURCE_COMMIT)
        changed = report()
        changed["pid"] = 9
        with self.assertRaisesRegex(StaticSemanticValidationError, "receipt identity"):
            verify_provenance(changed, LAUNCH, INSTALL, source_commit=SOURCE_COMMIT)
        changed = report()
        changed["rows"][0]["modelRevision"] = "wrong"
        with self.assertRaisesRegex(StaticSemanticValidationError, "row provenance"):
            verify_provenance(changed, LAUNCH, INSTALL, source_commit=SOURCE_COMMIT)


if __name__ == "__main__":
    unittest.main()
