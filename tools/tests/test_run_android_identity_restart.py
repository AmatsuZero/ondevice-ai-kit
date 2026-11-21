import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import textwrap
import unittest


RUNNER = Path(__file__).resolve().parents[1] / "probes/run_android_identity_restart.py"
SERIAL = "15365375C9007986"
RUN_ID = "15179388-0fa7-42af-9537-e8901cf0aec5"
PACKAGE = "io.ondeviceai.identity.test"
INSTRUMENTATION = f"{PACKAGE}/io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation"


FAKE_ADB = r'''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
import time

state_path = Path(os.environ["FAKE_ADB_STATE"])
state = json.loads(state_path.read_text()) if state_path.exists() else {
    "report": None, "pid": None, "alive": False, "nextPid": 7000,
    "forceStops": 0, "launchNonces": [], "cleanupRuns": 0, "dirtyRoot": False,
}

def save():
    state_path.write_text(json.dumps(state))

def argument(name):
    index = args.index(name)
    return args[index + 1]

args = sys.argv[1:]
if args[:2] == ["-s", os.environ["FAKE_ADB_SERIAL"]]:
    args = args[2:]
else:
    print("wrong serial", file=sys.stderr)
    raise SystemExit(2)

if args == ["get-state"]:
    print("device")
    raise SystemExit(0)
if args[:1] == ["install"]:
    print("Success")
    raise SystemExit(0)
if args[:2] == ["shell", "pidof"]:
    if state["alive"]:
        print(state["pid"])
    raise SystemExit(0)
if args[:3] == ["shell", "am", "force-stop"]:
    state["alive"] = False
    state["forceStops"] += 1
    save()
    raise SystemExit(0)
if args[:3] == ["shell", "run-as", os.environ["FAKE_ADB_PACKAGE"]]:
    command = args[3:]
    if command[:2] == ["kill", "-9"]:
        if str(state["pid"]) != command[2]:
            raise SystemExit(1)
        state["alive"] = False
        save()
        raise SystemExit(0)
    if command[:1] == ["rm"]:
        state["report"] = None
        save()
        raise SystemExit(0)
    if command[:1] == ["cat"]:
        if state["report"] is None:
            raise SystemExit(1)
        print(json.dumps(state["report"]))
        raise SystemExit(0)
if args[:3] == ["shell", "am", "instrument"]:
    extras = {args[index + 1]: args[index + 2]
              for index, value in enumerate(args[:-2]) if value == "-e"}
    probe, run_id, restart_stage = extras["probe"], extras["runID"], extras["restartStage"]
    launch_nonce = extras["launchNonce"]
    if probe != "restart" or run_id != os.environ["FAKE_ADB_RUN_ID"]:
        raise SystemExit(2)
    state["nextPid"] += 1
    state["pid"] = state["nextPid"]
    state["alive"] = True
    state["launchNonces"].append(launch_nonce)
    if restart_stage == "create":
        state["dirtyRoot"] = True
    if restart_stage == "cleanup":
        state["cleanupRuns"] += 1
    flags = {
        "create": {"enrollmentCommitted": True, "writerStillOpen": True},
        "delete": {"membershipRecovered": True, "decryptRecovered": True,
                    "deletionCommitted": True, "leaseHeld": True, "payloadPending": True},
        "recover": {"tombstoneRecovered": True, "receiptStable": True,
                     "payloadPurged": True, "templateKeyPurged": True, "closed": True},
        "cleanup": {"cleanup": True},
    }[restart_stage]
    if os.environ.get("FAKE_ADB_DROP_FLAG_STAGE") == restart_stage:
        flags.pop(next(iter(flags)))
    report_nonce = ("stale-nonce" if os.environ.get("FAKE_ADB_STALE_REPORT_STAGE") == restart_stage
                    else launch_nonce)
    state["report"] = {
        "probe": "identityRestart", "platform": "android", "runID": run_id,
        "stage": restart_stage, "launchNonce": report_nonce, "pid": state["pid"],
        "status": ("fail" if os.environ.get("FAKE_ADB_FAIL_STAGE") == restart_stage else
                   ("ready" if restart_stage in ("create", "delete") else "pass")),
        "flags": flags,
    }
    save()
    if restart_stage == "cleanup" and state["report"]["status"] == "pass":
        state["dirtyRoot"] = False
        save()
    if (restart_stage in ("create", "delete") or state["report"]["status"] == "fail"
            or os.environ.get("FAKE_ADB_HANG_STAGE") == restart_stage):
        while True:
            time.sleep(0.01)
            state = json.loads(state_path.read_text())
            if not state["alive"]:
                raise SystemExit(137)
    raise SystemExit(0)
print("unsupported fake adb call: " + " ".join(args), file=sys.stderr)
raise SystemExit(2)
'''


class AndroidIdentityRestartRunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.state = root / "adb-state.json"
        self.adb = root / "fake-adb"
        self.adb.write_text(textwrap.dedent(FAKE_ADB))
        self.adb.chmod(self.adb.stat().st_mode | stat.S_IXUSR)
        self.apk = root / "identity-debug-androidTest.apk"
        self.apk.write_bytes(b"synthetic apk")

    def tearDown(self):
        self.temp.cleanup()

    def run_runner(self, evidence, extra_env=None):
        env = os.environ | {
            "FAKE_ADB_STATE": str(self.state), "FAKE_ADB_SERIAL": SERIAL,
            "FAKE_ADB_PACKAGE": PACKAGE, "FAKE_ADB_RUN_ID": RUN_ID,
        } | (extra_env or {})
        return subprocess.run([
            sys.executable, str(RUNNER), "--adb", str(self.adb), "--serial", SERIAL,
            "--apk", str(self.apk), "--evidence-dir", str(evidence), "--run-id", RUN_ID,
            "--poll-interval", "0.01", "--timeout", "2", "--command-timeout", "0.5",
        ], env=env, capture_output=True, text=True)

    def test_runner_records_four_stage_external_kill_chain(self):
        evidence = Path(self.temp.name) / "evidence"
        result = self.run_runner(evidence)
        self.assertEqual(result.returncode, 0, result.stderr)
        controller = json.loads((evidence / "controller.json").read_text())
        self.assertEqual(controller["schemaVersion"], 1)
        self.assertEqual(controller["serial"], SERIAL)
        self.assertEqual(controller["runID"], RUN_ID)
        self.assertEqual([stage["stage"] for stage in controller["stages"]],
                         ["create", "delete", "recover", "cleanup"])
        self.assertEqual([stage["killSignal"] for stage in controller["stages"][:2]], [9, 9])
        self.assertTrue(all(stage["deadAfterKill"] for stage in controller["stages"][:2]))
        self.assertEqual([stage["launchExit"] for stage in controller["stages"][2:]], [0, 0])
        for stage in ("create", "delete", "recover", "cleanup"):
            report = json.loads((evidence / f"report-{stage}.json").read_text())
            self.assertEqual(report["stage"], stage)
            self.assertEqual(report["platform"], "android")
        nonces = [stage["launchNonce"] for stage in controller["stages"]]
        self.assertEqual(len(set(nonces)), 4)
        self.assertEqual(nonces, [json.loads((evidence / f"report-{stage}.json").read_text())["launchNonce"]
                                  for stage in ("create", "delete", "recover", "cleanup")])
        state = json.loads(self.state.read_text())
        self.assertGreaterEqual(state["forceStops"], 4)

    def test_runner_refuses_to_overwrite_existing_evidence(self):
        evidence = Path(self.temp.name) / "evidence"
        evidence.mkdir()
        (evidence / "sentinel").write_text("keep")
        result = self.run_runner(evidence)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((evidence / "sentinel").read_text(), "keep")

    def test_runner_force_stops_probe_process_after_stage_failure(self):
        evidence = Path(self.temp.name) / "evidence"
        result = self.run_runner(evidence, {"FAKE_ADB_FAIL_STAGE": "create"})
        self.assertNotEqual(result.returncode, 0)
        state = json.loads(self.state.read_text())
        self.assertFalse(state["alive"])
        self.assertFalse((evidence / "controller.json").exists())
        self.assertGreaterEqual(state["cleanupRuns"], 1)
        self.assertFalse(state["dirtyRoot"])
        cleanup = json.loads((evidence / "failure-cleanup.json").read_text())
        self.assertEqual(cleanup["status"], "pass")

    def test_runner_rejects_incomplete_final_evidence(self):
        evidence = Path(self.temp.name) / "evidence"
        result = self.run_runner(evidence, {"FAKE_ADB_DROP_FLAG_STAGE": "recover"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("lifecycle flags/status mismatch", result.stderr)

    def test_runner_rejects_stale_report_nonce_and_stops_package(self):
        evidence = Path(self.temp.name) / "evidence"
        result = self.run_runner(evidence, {"FAKE_ADB_STALE_REPORT_STAGE": "create"})
        self.assertNotEqual(result.returncode, 0)
        state = json.loads(self.state.read_text())
        self.assertGreaterEqual(state["forceStops"], 2)

    def test_launcher_timeout_still_runs_exact_failure_cleanup(self):
        evidence = Path(self.temp.name) / "evidence"
        result = self.run_runner(evidence, {"FAKE_ADB_HANG_STAGE": "recover"})
        self.assertNotEqual(result.returncode, 0)
        state = json.loads(self.state.read_text())
        self.assertFalse(state["alive"])
        self.assertFalse(state["dirtyRoot"])
        self.assertEqual(json.loads((evidence / "failure-cleanup.json").read_text())["status"], "pass")


if __name__ == "__main__":
    unittest.main()
