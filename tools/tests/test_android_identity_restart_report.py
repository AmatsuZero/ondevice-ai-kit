import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


VALIDATOR = Path(__file__).resolve().parents[1] / "probes/validate_android_identity_restart_report.py"
RUN_ID = "15179388-0fa7-42af-9537-e8901cf0aec5"
FLAGS = {
    "create": ["enrollmentCommitted", "writerStillOpen"],
    "delete": ["membershipRecovered", "decryptRecovered", "deletionCommitted", "leaseHeld", "payloadPending"],
    "recover": ["tombstoneRecovered", "receiptStable", "payloadPurged", "templateKeyPurged", "closed"],
    "cleanup": ["cleanup"],
}
NONCES = {
    "create": "00000000-0000-4000-8000-000000000001",
    "delete": "00000000-0000-4000-8000-000000000002",
    "recover": "00000000-0000-4000-8000-000000000003",
    "cleanup": "00000000-0000-4000-8000-000000000004",
}


def fixture():
    reports = {}
    stages = []
    for offset, (stage, flags) in enumerate(FLAGS.items()):
        pid = 8000 + offset
        reports[f"report-{stage}.json"] = {
            "probe": "identityRestart", "platform": "android", "runID": RUN_ID,
            "stage": stage, "launchNonce": NONCES[stage], "pid": pid,
            "status": "ready" if stage in ("create", "delete") else "pass",
            "flags": dict.fromkeys(flags, True),
        }
        stages.append({
            "stage": stage, "pid": pid, "serial": "device-serial",
            "launchNonce": NONCES[stage],
            "package": "io.ondeviceai.identity.test",
            "instrumentation": "io.ondeviceai.identity.test/io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation",
            "runID": RUN_ID, "launchExit": None if stage in ("create", "delete") else 0,
            "launchTransportExit": 0 if stage in ("create", "delete") else None,
            "launchAliveAtKill": True if stage in ("create", "delete") else None,
            "killSignal": 9 if stage in ("create", "delete") else None,
            "killExit": 0 if stage in ("create", "delete") else None,
            "deadAfterKill": True if stage in ("create", "delete") else None,
        })
    reports["controller.json"] = {"schemaVersion": 1, "serial": "device-serial", "runID": RUN_ID, "stages": stages}
    return reports


class AndroidIdentityRestartReportTests(unittest.TestCase):
    def run_validator(self, reports, run_id=RUN_ID):
        with tempfile.TemporaryDirectory() as directory:
            for name, report in reports.items():
                (Path(directory) / name).write_text(json.dumps(report))
            return subprocess.run([sys.executable, str(VALIDATOR), directory, "--run-id", run_id,
                                   "--serial", "device-serial"], capture_output=True, text=True)

    def test_complete_external_kill_chain_passes(self):
        result = self.run_validator(fixture())
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_stale_incomplete_or_same_process_chain_is_rejected(self):
        mutations = [
            ("report-create.json", "runID", "stale"),
            ("report-create.json", "status", "running"),
            ("report-recover.json", "status", "fail"),
            ("report-delete.json", "pid", 8000),
            ("report-cleanup.json", "flags", {"cleanup": False}),
        ]
        for filename, key, value in mutations:
            with self.subTest(filename=filename, key=key):
                changed = copy.deepcopy(fixture()); changed[filename][key] = value
                self.assertNotEqual(self.run_validator(changed).returncode, 0)
        self.assertNotEqual(self.run_validator(fixture(), "wrong-run").returncode, 0)

    def test_malformed_run_id_is_rejected_even_when_every_file_matches(self):
        changed = fixture()
        changed["controller.json"]["runID"] = "same-but-not-uuid"
        for item in changed["controller.json"]["stages"]:
            item["runID"] = "same-but-not-uuid"
        for stage in FLAGS:
            changed[f"report-{stage}.json"]["runID"] = "same-but-not-uuid"

        self.assertNotEqual(self.run_validator(changed, "same-but-not-uuid").returncode, 0)

    def test_wrong_controller_identity_or_kill_is_rejected(self):
        for mutation in ("serial", "run_id", "pid", "package", "instrumentation", "signal", "kill_exit",
                         "nonkill_kill_exit", "nonce", "report_nonce", "malformed_nonce_both",
                         "launcher_dead", "alive", "malformed_stage", "missing"):
            with self.subTest(mutation=mutation):
                changed = fixture(); controller = changed["controller.json"]
                if mutation == "serial": controller["serial"] = "other"
                elif mutation == "run_id": controller["runID"] = "other"
                elif mutation == "pid": controller["stages"][0]["pid"] = 9999
                elif mutation == "package": controller["stages"][0]["package"] = "unrelated"
                elif mutation == "instrumentation": controller["stages"][0]["instrumentation"] = "unrelated/runner"
                elif mutation == "signal": controller["stages"][0]["killSignal"] = 15
                elif mutation == "kill_exit": controller["stages"][0]["killExit"] = 1
                elif mutation == "nonkill_kill_exit": controller["stages"][2]["killExit"] = 0
                elif mutation == "nonce": controller["stages"][0]["launchNonce"] = "other"
                elif mutation == "report_nonce": changed["report-create.json"]["launchNonce"] = "other"
                elif mutation == "malformed_nonce_both":
                    controller["stages"][0]["launchNonce"] = "same-but-not-uuid"
                    changed["report-create.json"]["launchNonce"] = "same-but-not-uuid"
                elif mutation == "launcher_dead": changed["controller.json"]["stages"][0]["launchAliveAtKill"] = False
                elif mutation == "alive": controller["stages"][0]["deadAfterKill"] = False
                elif mutation == "malformed_stage": controller["stages"][0] = "create"
                else: del changed["controller.json"]
                self.assertNotEqual(self.run_validator(changed).returncode, 0)


if __name__ == "__main__":
    unittest.main()
