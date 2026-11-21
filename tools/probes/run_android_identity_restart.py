#!/usr/bin/env python3
"""Run the Android IdentityStore process-death probe and capture fresh evidence."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import uuid


PACKAGE = "io.ondeviceai.identity.test"
INSTRUMENTATION = f"{PACKAGE}/io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation"
VALIDATOR = Path(__file__).with_name("validate_android_identity_restart_report.py")
STAGES = ("create", "delete", "recover", "cleanup")
READY_STAGES = {"create", "delete"}
REPORT_PATH = "files/identity-restart-probe.json"
UUID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


class RunnerError(RuntimeError):
    pass


def adb_call(adb, serial, *arguments, timeout, check=True):
    command = [str(adb), "-s", serial, *map(str, arguments)]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RunnerError(f"adb command failed: {' '.join(command[:5])}: {error}") from error
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).strip().splitlines()
        raise RunnerError(f"adb command exit {result.returncode}: {' '.join(command[:5])}: {detail[-1] if detail else 'no output'}")
    return result


def atomic_write(path: Path, value: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            output.write(value)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def report_from_device(adb, serial, timeout):
    result = adb_call(adb, serial, "shell", "run-as", PACKAGE, "cat", REPORT_PATH,
                      timeout=timeout, check=False)
    if result.returncode != 0 or not result.stdout.strip():
        return None
    try:
        value = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise RunnerError(f"probe report is not JSON: {error}") from error
    if not isinstance(value, dict):
        raise RunnerError("probe report is not an object")
    return value


def wait_for_report(adb, serial, run_id, stage, launch_nonce, timeout, poll_interval):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        report = report_from_device(adb, serial, timeout=min(5.0, max(0.2, poll_interval * 10)))
        if (report is not None and report.get("runID") == run_id
                and report.get("stage") == stage and report.get("launchNonce") == launch_nonce):
            if report.get("status") == "fail":
                raise RunnerError(f"{stage} probe failed")
            expected = "ready" if stage in READY_STAGES else "pass"
            if report.get("status") == expected:
                return report
        time.sleep(poll_interval)
    raise RunnerError(f"timed out waiting for {stage} report")


def process_ids(adb, serial, timeout):
    result = adb_call(adb, serial, "shell", "pidof", PACKAGE, timeout=timeout, check=False)
    if result.returncode != 0:
        return set()
    ids = set()
    for token in result.stdout.split():
        if token.isdigit():
            ids.add(int(token))
    return ids


def wait_for_dead(adb, serial, pid, timeout, poll_interval):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if pid not in process_ids(adb, serial, timeout=min(5.0, max(0.2, poll_interval * 10))):
            return True
        time.sleep(poll_interval)
    return False


def stop_package(args):
    adb_call(args.adb, args.serial, "shell", "am", "force-stop", PACKAGE,
             timeout=args.command_timeout)
    deadline = time.monotonic() + args.timeout
    while time.monotonic() < deadline:
        if not process_ids(args.adb, args.serial, args.command_timeout):
            return
        time.sleep(args.poll_interval)
    raise RunnerError("test package remained alive after force-stop")


def run_stage(args, evidence, stage):
    launch_nonce = str(uuid.uuid4())
    stop_package(args)
    adb_call(args.adb, args.serial, "shell", "run-as", PACKAGE, "rm", "-f", REPORT_PATH,
             timeout=args.command_timeout)
    command = [str(args.adb), "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
               "-e", "debug", "false", "-e", "probe", "restart", "-e", "runID", args.run_id,
               "-e", "restartStage", stage, "-e", "launchNonce", launch_nonce, INSTRUMENTATION]
    try:
        launched = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    text=True)
    except OSError as error:
        raise RunnerError(f"failed to launch {stage}: {error}") from error
    try:
        report = wait_for_report(args.adb, args.serial, args.run_id, stage, launch_nonce,
                                 args.timeout, args.poll_interval)
        pid = report.get("pid")
        if type(pid) is not int or pid <= 0:
            raise RunnerError(f"{stage} report has invalid pid")
        if report.get("probe") != "identityRestart" or report.get("platform") != "android":
            raise RunnerError(f"{stage} report identity mismatch")
        if stage in READY_STAGES:
            launcher_alive_at_kill = launched.poll() is None
            if not launcher_alive_at_kill:
                raise RunnerError(f"{stage} launcher exited before device SIGKILL")
            if pid not in process_ids(args.adb, args.serial, args.command_timeout):
                raise RunnerError(f"{stage} report pid is not alive on device")
            kill = adb_call(args.adb, args.serial, "shell", "run-as", PACKAGE, "kill", "-9", pid,
                            timeout=args.command_timeout, check=False)
            dead = wait_for_dead(args.adb, args.serial, pid, args.timeout, args.poll_interval)
            if kill.returncode != 0 or not dead:
                raise RunnerError(f"{stage} SIGKILL did not terminate pid")
            try:
                transport_exit = launched.wait(timeout=args.command_timeout)
            except subprocess.TimeoutExpired:
                launched.kill()
                transport_exit = launched.wait()
            launch_exit = None
            record = {
                "stage": stage, "launchNonce": launch_nonce, "pid": pid, "serial": args.serial,
                "package": PACKAGE, "instrumentation": INSTRUMENTATION, "runID": args.run_id,
                "launchExit": launch_exit, "launchTransportExit": transport_exit,
                "launchAliveAtKill": launcher_alive_at_kill,
                "killExit": kill.returncode,
                "killSignal": 9, "deadAfterKill": dead,
            }
        else:
            try:
                launch_exit = launched.wait(timeout=args.command_timeout)
            except subprocess.TimeoutExpired as error:
                raise RunnerError(f"{stage} launcher timed out") from error
            if launch_exit != 0:
                raise RunnerError(f"{stage} instrumentation exited {launch_exit}")
            stop_package(args)
            record = {
                "stage": stage, "launchNonce": launch_nonce, "pid": pid, "serial": args.serial,
                "package": PACKAGE, "instrumentation": INSTRUMENTATION, "runID": args.run_id,
                "launchExit": launch_exit, "launchTransportExit": None,
                "launchAliveAtKill": None, "killExit": None,
                "killSignal": None, "deadAfterKill": None,
            }
        atomic_write(evidence / f"report-{stage}.json", json.dumps(report, sort_keys=True) + "\n")
        return record
    finally:
        if launched.poll() is None:
            adb_call(args.adb, args.serial, "shell", "am", "force-stop", PACKAGE,
                     timeout=args.command_timeout, check=False)
            launched.kill()
            launched.wait()


def attempt_failure_cleanup(args, evidence):
    try:
        record = run_stage(args, evidence, "cleanup")
        atomic_write(evidence / "failure-cleanup.json", json.dumps({
            "schemaVersion": 1, "runID": args.run_id, "serial": args.serial,
            "status": "pass", "stage": record,
        }, indent=2, sort_keys=True) + "\n")
    except RunnerError as error:
        atomic_write(evidence / "failure-cleanup.json", json.dumps({
            "schemaVersion": 1, "runID": args.run_id, "serial": args.serial,
            "status": "fail", "errorType": type(error).__name__,
        }, indent=2, sort_keys=True) + "\n")


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--timeout", type=float, default=45.0)
    parser.add_argument("--poll-interval", type=float, default=0.25)
    parser.add_argument("--command-timeout", type=float, default=15.0)
    args = parser.parse_args(argv)
    if not UUID_PATTERN.fullmatch(args.run_id):
        parser.error("--run-id must be a lowercase UUID")
    if args.timeout <= 0 or args.poll_interval <= 0 or args.command_timeout <= 0:
        parser.error("timeouts must be positive")
    return args


def main(argv=None):
    args = parse_args(argv)
    if not args.adb.is_file() or not args.apk.is_file():
        raise RunnerError("adb and APK must be existing files")
    if args.evidence_dir.exists():
        raise RunnerError("evidence directory already exists; use a fresh directory")
    args.evidence_dir.mkdir(parents=True)
    state = []
    try:
        status = adb_call(args.adb, args.serial, "get-state", timeout=args.command_timeout).stdout.strip()
        if status != "device":
            raise RunnerError(f"device state is {status or 'empty'}")
        adb_call(args.adb, args.serial, "install", "-r", args.apk, timeout=args.command_timeout)
        for stage in STAGES:
            record = run_stage(args, args.evidence_dir, stage)
            state.append(record)
            print(f"{stage}: status=ok pid={record['pid']}")
        controller = {"schemaVersion": 1, "serial": args.serial, "runID": args.run_id, "stages": state}
        atomic_write(args.evidence_dir / "controller.json", json.dumps(controller, indent=2, sort_keys=True) + "\n")
        validation = subprocess.run([
            sys.executable, str(VALIDATOR), str(args.evidence_dir),
            "--run-id", args.run_id, "--serial", args.serial,
        ], capture_output=True, text=True)
        if validation.returncode != 0:
            detail = (validation.stderr or validation.stdout).strip().splitlines()
            raise RunnerError(f"final evidence validation failed: {detail[-1] if detail else 'no output'}")
        return 0
    except RunnerError:
        # Keep partial evidence for diagnosis and clean this run's exact root/aliases.
        attempt_failure_cleanup(args, args.evidence_dir)
        raise


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RunnerError as error:
        print(f"Android identity restart runner failed: {error}", file=sys.stderr)
        raise SystemExit(2)
