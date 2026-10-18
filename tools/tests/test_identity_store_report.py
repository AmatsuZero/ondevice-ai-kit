import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = ROOT / "tools/probes/validate_identity_store_report.py"


class IdentityStoreReportTests(unittest.TestCase):
    def test_rejects_running_or_stale_run_id(self):
        report = {
            "probe": "identityStore", "status": "pass", "runID": "new-run",
            "sameProcessOnly": True, "forceFailure": False,
            "flags": dict.fromkeys([
                "applicationSupportRoot", "backupExcluded", "encryptedFiles", "keychainAttributes", "cleanup",
                "sameProcessMembershipAndDecrypt", "receiptAndTombstonePersisted", "domainRevocationPersisted",
            ], True), "counts": {"managedKeyCount": 3}, "errorCode": None,
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            path.write_text(json.dumps(report))
            matching = subprocess.run(
                [sys.executable, str(VALIDATOR), str(path), "--run-id", "new-run"],
                capture_output=True, text=True,
            )
            stale = subprocess.run(
                [sys.executable, str(VALIDATOR), str(path), "--run-id", "old-run"],
                capture_output=True, text=True,
            )
            report["status"] = "running"
            path.write_text(json.dumps(report))
            running = subprocess.run(
                [sys.executable, str(VALIDATOR), str(path), "--run-id", "new-run"],
                capture_output=True, text=True,
            )
        self.assertEqual(matching.returncode, 0, matching.stderr)
        self.assertNotEqual(running.returncode, 0)
        self.assertNotEqual(stale.returncode, 0)


if __name__ == "__main__":
    unittest.main()
