import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


VALIDATOR = Path(__file__).resolve().parents[1] / "probes/validate_identity_restart_report.py"
RUN_ID = "6c47f7f3-1d3a-4ad7-a061-85ffab26d78f"
FLAGS = {
    "create": ["enrollmentCommitted", "writerStillOpen"],
    "delete": ["membershipRecovered", "decryptRecovered", "deletionCommitted", "leaseHeld", "payloadPending"],
    "recover": ["tombstoneRecovered", "receiptStable", "payloadPurged", "templateKeyPurged", "closed"],
    "cleanup": ["cleanup"],
}


def fixture():
    reports = {}
    for index, (stage, flags) in enumerate(FLAGS.items()):
        reports[f"report-{stage}.json"] = {
            "probe": "identityRestart", "runID": RUN_ID, "stage": stage, "pid": 100 + index,
            "status": "ready" if stage in ("create", "delete") else "pass",
            "flags": dict.fromkeys(flags, True),
        }
        if stage in ("create", "delete"):
            reports[f"kill-{stage}.json"] = {
                "info": {"outcome": "success", "commandType": "devicectl.device.process.terminate"},
                "result": {"deviceIdentifier": "device", "process": {"processIdentifier": 100 + index,
                    "executable": "file:///probe/IOSIdentityStoreProbe.app/IOSIdentityStoreProbe"},
                    "signal": {"name": "SIGKILL", "value": 9}},
            }
    for stage in FLAGS:
        reports[f"launch-{stage}.json"] = {
            "info": {"outcome": "success", "commandType": "devicectl.device.process.launch"},
            "result": {"deviceIdentifier": "device", "process": {
                "processIdentifier": reports[f"report-{stage}.json"]["pid"],
                "executable": "file:///probe/IOSIdentityStoreProbe.app/IOSIdentityStoreProbe"},
                "launchOptions": {"arguments": [f"--run-id={RUN_ID}", f"--restart-stage={stage}"]}},
        }
    return reports


class IdentityRestartReportTests(unittest.TestCase):
    def run_validator(self, reports, run_id=RUN_ID):
        with tempfile.TemporaryDirectory() as directory:
            for name, report in reports.items():
                (Path(directory) / name).write_text(json.dumps(report))
            return subprocess.run([sys.executable, str(VALIDATOR), directory, "--run-id", run_id],
                                  capture_output=True, text=True)

    def test_complete_external_kill_chain_passes(self):
        result = self.run_validator(fixture())
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_create_launch_must_exist_and_match(self):
        for mutation in ("missing", "wrong_arguments", "wrong_pid"):
            with self.subTest(mutation=mutation):
                changed = fixture()
                if mutation == "missing":
                    del changed["launch-create.json"]
                elif mutation == "wrong_arguments":
                    changed["launch-create.json"]["result"]["launchOptions"]["arguments"] = []
                else:
                    changed["launch-create.json"]["result"]["process"]["processIdentifier"] = 999
                self.assertNotEqual(self.run_validator(changed).returncode, 0)

    def test_incomplete_stale_or_same_process_chain_is_rejected(self):
        baseline = fixture()
        mutations = [
            ("report-delete.json", "runID", "stale"),
            ("report-create.json", "status", "running"),
            ("report-recover.json", "status", "fail"),
            ("report-delete.json", "pid", 100),
            ("report-cleanup.json", "flags", {"cleanup": False}),
            ("report-create.json", "flags", {}),
        ]
        for filename, key, value in mutations:
            with self.subTest(filename=filename, key=key):
                changed = copy.deepcopy(baseline)
                changed[filename][key] = value
                self.assertNotEqual(self.run_validator(changed).returncode, 0)
        self.assertNotEqual(self.run_validator(baseline, "wrong-run").returncode, 0)

    def test_wrong_signal_process_device_or_launch_is_rejected(self):
        for mutation in ("signal", "pid", "device", "launch", "executable", "other_installation", "missing"):
            with self.subTest(mutation=mutation):
                changed = fixture()
                if mutation == "signal":
                    changed["kill-create.json"]["result"]["signal"] = {"name": "SIGTERM", "value": 15}
                elif mutation == "pid":
                    changed["kill-delete.json"]["result"]["process"]["processIdentifier"] = 999
                elif mutation == "device":
                    changed["launch-recover.json"]["result"]["deviceIdentifier"] = "other-device"
                elif mutation == "launch":
                    changed["launch-delete.json"]["result"]["launchOptions"]["arguments"] = ["--restart-stage=create"]
                elif mutation == "executable":
                    changed["kill-create.json"]["result"]["process"]["executable"] = "file:///unrelated-app"
                elif mutation == "other_installation":
                    changed["kill-create.json"]["result"]["process"]["executable"] = "file:///old/IOSIdentityStoreProbe.app/IOSIdentityStoreProbe"
                else:
                    del changed["kill-create.json"]
                self.assertNotEqual(self.run_validator(changed).returncode, 0)


if __name__ == "__main__":
    unittest.main()
