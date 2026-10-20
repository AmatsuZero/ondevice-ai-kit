#!/usr/bin/env python3
"""Validate Android IdentityStore process-death evidence from one adb device."""
import argparse
import json
from pathlib import Path
import re


STAGES = ("create", "delete", "recover", "cleanup")
UUID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
FLAGS = {
    "create": ("enrollmentCommitted", "writerStillOpen"),
    "delete": ("membershipRecovered", "decryptRecovered", "deletionCommitted", "leaseHeld", "payloadPending"),
    "recover": ("tombstoneRecovered", "receiptStable", "payloadPurged", "templateKeyPurged", "closed"),
    "cleanup": ("cleanup",),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    try:
        if not UUID_PATTERN.fullmatch(args.run_id):
            raise ValueError("run id is not a lowercase UUID")
        def load(name):
            path = args.directory / name
            if not path.is_file(): raise ValueError(f"missing {name}")
            return json.loads(path.read_text())
        reports = {stage: load(f"report-{stage}.json") for stage in STAGES}
        controller = load("controller.json")
        if controller.get("schemaVersion") != 1 or controller.get("runID") != args.run_id or controller.get("serial") != args.serial:
            raise ValueError("controller identity mismatch")
        stages = controller.get("stages")
        if not isinstance(stages, list) or [item.get("stage") for item in stages] != list(STAGES):
            raise ValueError("controller stage order mismatch")
        pids = []
        nonces = []
        for stage, item in zip(STAGES, stages):
            report = reports[stage]
            if report.get("probe") != "identityRestart" or report.get("platform") != "android" or report.get("runID") != args.run_id or report.get("stage") != stage:
                raise ValueError(f"{stage} report identity mismatch")
            expected = "ready" if stage in ("create", "delete") else "pass"
            if report.get("status") != expected or not isinstance(report.get("flags"), dict) or not all(report["flags"].get(flag) is True for flag in FLAGS[stage]):
                raise ValueError(f"{stage} lifecycle flags/status mismatch")
            pid = report.get("pid")
            launch_nonce = item.get("launchNonce")
            if not isinstance(launch_nonce, str) or not UUID_PATTERN.fullmatch(launch_nonce): raise ValueError(f"{stage} launch nonce invalid")
            if report.get("launchNonce") != launch_nonce: raise ValueError(f"{stage} launch nonce mismatch")
            if type(pid) is not int or pid <= 0 or item.get("pid") != pid: raise ValueError(f"{stage} pid mismatch")
            pids.append(pid)
            nonces.append(launch_nonce)
            if item.get("serial") != args.serial or item.get("package") != "io.ondeviceai.identity.test": raise ValueError(f"{stage} package/device mismatch")
            if item.get("instrumentation") != "io.ondeviceai.identity.test/io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation": raise ValueError(f"{stage} instrumentation mismatch")
            if item.get("runID") != args.run_id: raise ValueError(f"{stage} launch argument mismatch")
            if stage in ("create", "delete"):
                transport_exit = item.get("launchTransportExit")
                if (item.get("launchExit") is not None or type(transport_exit) is not int
                        or item.get("launchAliveAtKill") is not True or item.get("killExit") != 0
                        or item.get("killSignal") != 9 or item.get("deadAfterKill") is not True):
                    raise ValueError(f"{stage} kill evidence mismatch")
            elif (item.get("launchExit") != 0 or item.get("launchTransportExit") is not None
                  or item.get("launchAliveAtKill") is not None
                  or item.get("killExit") is not None
                  or item.get("killSignal") is not None or item.get("deadAfterKill") is not None):
                raise ValueError(f"{stage} launch evidence mismatch")
        if len(set(pids)) != len(STAGES): raise ValueError("stages must be distinct processes")
        if len(set(nonces)) != len(STAGES): raise ValueError("stages must use distinct launch nonces")
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as error:
        parser.exit(1, f"Android identity restart report rejected: {error}\n")
    print("Android identity restart report verified: two kill -9 boundaries and durable recovery")


if __name__ == "__main__":
    main()
