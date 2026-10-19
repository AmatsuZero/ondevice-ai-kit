#!/usr/bin/env python3
"""Validate an iOS IdentityStore process-death probe evidence directory."""
import argparse
import json
from pathlib import Path


STAGES = ("create", "delete", "recover", "cleanup")
REQUIRED_FLAGS = {
    "create": ("enrollmentCommitted", "writerStillOpen"),
    "delete": ("membershipRecovered", "decryptRecovered", "deletionCommitted", "leaseHeld", "payloadPending"),
    "recover": ("tombstoneRecovered", "receiptStable", "payloadPurged", "templateKeyPurged", "closed"),
    "cleanup": ("cleanup",),
}


def load(directory, name):
    path = directory / name
    if not path.is_file():
        raise ValueError(f"missing {name}")
    return json.loads(path.read_text())


def process(report):
    value = report.get("pid")
    if type(value) is not int or value <= 0:
        raise ValueError("report has invalid pid")
    return value


def executable(report):
    value = report.get("executable")
    if not isinstance(value, str) or not value.endswith("/IOSIdentityStoreProbe"):
        raise ValueError("unexpected probe executable")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    try:
        if not args.run_id:
            raise ValueError("run id is empty")
        reports = {stage: load(args.directory, f"report-{stage}.json") for stage in STAGES}
        for stage, report in reports.items():
            if report.get("probe") != "identityRestart" or report.get("runID") != args.run_id or report.get("stage") != stage:
                raise ValueError(f"{stage} report identity mismatch")
            expected_status = "ready" if stage in ("create", "delete") else "pass"
            if report.get("status") != expected_status:
                raise ValueError(f"{stage} is not terminal as expected")
            flags = report.get("flags")
            if not isinstance(flags, dict) or not all(flags.get(flag) is True for flag in REQUIRED_FLAGS[stage]):
                raise ValueError(f"{stage} lifecycle flag missing")

        create_pid = process(reports["create"])
        delete_pid = process(reports["delete"])
        recover_pid = process(reports["recover"])
        cleanup_pid = process(reports["cleanup"])
        if len({create_pid, delete_pid, recover_pid, cleanup_pid}) != 4:
            raise ValueError("stages must be separate process launches")

        device = None
        installation = None
        for stage in STAGES:
            launch = load(args.directory, f"launch-{stage}.json")
            info = launch.get("info", {})
            result = launch.get("result", {})
            if info.get("outcome") != "success" or info.get("commandType") != "devicectl.device.process.launch":
                raise ValueError(f"{stage} launch failed")
            device = device or result.get("deviceIdentifier")
            if not device or result.get("deviceIdentifier") != device:
                raise ValueError("launch device changed")
            launch_process = result.get("process", {})
            if launch_process.get("processIdentifier") != process(reports[stage]):
                raise ValueError(f"{stage} launch pid mismatch")
            path = executable(launch_process)
            installation = installation or path
            if path != installation:
                raise ValueError("probe installation changed between launches")
            arguments = result.get("launchOptions", {}).get("arguments", [])
            if f"--run-id={args.run_id}" not in arguments or f"--restart-stage={stage}" not in arguments:
                raise ValueError(f"{stage} launch arguments mismatch")

        for stage, pid in (("create", create_pid), ("delete", delete_pid)):
            termination = load(args.directory, f"kill-{stage}.json")
            info = termination.get("info", {})
            result = termination.get("result", {})
            if info.get("outcome") != "success" or info.get("commandType") != "devicectl.device.process.terminate":
                raise ValueError(f"{stage} kill failed")
            if result.get("process", {}).get("processIdentifier") != pid:
                raise ValueError(f"{stage} kill pid mismatch")
            if executable(result.get("process", {})) != installation:
                raise ValueError(f"{stage} kill installation mismatch")
            signal = result.get("signal", {})
            if signal.get("name") != "SIGKILL" or signal.get("value") != 9:
                raise ValueError(f"{stage} was not SIGKILL")
            if not device or result.get("deviceIdentifier") != device:
                raise ValueError(f"{stage} kill device mismatch")
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as error:
        parser.exit(1, f"Identity restart report rejected: {error}\n")
    print("Identity restart report verified: two SIGKILL boundaries and durable recovery")


if __name__ == "__main__":
    main()
