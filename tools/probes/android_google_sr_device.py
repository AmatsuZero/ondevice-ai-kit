#!/usr/bin/env python3
"""Probe Google SR availability on an Android device; this does not confirm SR."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path

PACKAGE = "io.ondeviceai.realtimesr.demo.google"
ACTIVITY = PACKAGE + "/io.ondeviceai.realtimesr.GoogleProbeActivity"
PROBE_OK = {"MISSING_SERVICES", "MODULE_NOT_READY", "UNSUPPORTED", "UNVERIFIED"}

def run(args, timeout=15):
    p = subprocess.run(args, text=True, capture_output=True, timeout=timeout)
    return p.returncode, (p.stdout + p.stderr).strip()

def find_aapt():
    found = shutil.which("aapt")
    if found:
        return found
    sdk = Path.home() / "Library/Android/sdk/build-tools"
    if not sdk.is_dir():
        raise RuntimeError("aapt not found on PATH or in Android SDK build-tools")
    candidates = sorted(sdk.glob("*/aapt"), reverse=True)
    if not candidates:
        raise RuntimeError("aapt not found on PATH or in Android SDK build-tools")
    return str(candidates[0])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", required=True, type=Path, help="New JSON output file")
    parser.add_argument("--project", type=Path, default=Path.cwd())
    parser.add_argument("--build", action="store_true", help="Build with JDK 17")
    parser.add_argument("--apk", type=Path, default=Path("examples/android-realtime-sr/build/outputs/apk/debug/android-realtime-sr-debug.apk"))
    args = parser.parse_args()
    if args.out.exists():
        parser.error("--out must be a new file")
    record = {"serial": args.serial, "status": "ERROR"}
    exit_code = 1
    try:
        project = args.project.resolve()
        apk = args.apk if args.apk.is_absolute() else project / args.apk
        if args.build:
            rc, version = run(["java", "-version"])
            if rc or not re.search(r'"17(?:\.|\")', version):
                raise RuntimeError("--build requires JDK 17")
            gradle = project / "gradlew"
            rc, output = run([str(gradle), "-p", str(project), "-PenableRealtimeSrDemo=true", "-PenableGoogleSr=true", ":examples:android-realtime-sr:assembleDebug"], timeout=900)
            if rc:
                raise RuntimeError("Gradle build failed: " + output[-2000:])
        if not apk.is_file():
            raise RuntimeError("APK not found: " + str(apk))
        rc, output = run([find_aapt(), "dump", "badging", str(apk)])
        if rc or not re.search(r"^package: name='" + re.escape(PACKAGE) + r"'", output, re.M):
            raise RuntimeError("APK package is not exactly " + PACKAGE)
        record["apkSha256"] = hashlib.sha256(apk.read_bytes()).hexdigest()
        adb = ["adb", "-s", args.serial]
        rc, output = run(adb + ["get-state"])
        if rc or output != "device":
            raise RuntimeError("Device is not available: " + output)
        rc, output = run(adb + ["shell", "getprop", "ro.build.version.sdk"])
        if rc:
            raise RuntimeError("Unable to read Android API level: " + output)
        record["apiLevel"] = output
        rc, output = run(adb + ["install", "-r", "-t", str(apk)], timeout=120)
        if rc or "Success" not in output.splitlines():
            raise RuntimeError("APK install failed: " + output)
        run_id = uuid.uuid4().hex
        record["runId"] = run_id
        rc, output = run(adb + ["shell", "run-as", PACKAGE, "mkdir", "-p", "files/sr-google"])
        if rc:
            raise RuntimeError("Unable to prepare probe result directory: " + output)
        rc, output = run(adb + ["shell", "am", "start", "-n", ACTIVITY, "--es", "runId", run_id])
        if rc or "Error" in output:
            raise RuntimeError("Unable to launch GoogleProbeActivity: " + output)
        deadline = time.monotonic() + 25
        result_path = "files/sr-google/" + run_id + ".json"
        while time.monotonic() < deadline:
            rc, output = run(adb + ["exec-out", "run-as", PACKAGE, "cat", result_path], timeout=5)
            if rc == 0:
                try:
                    candidate = json.loads(output)
                except json.JSONDecodeError:
                    candidate = None
                if (isinstance(candidate, dict) and candidate.get("runId") == run_id
                        and candidate.get("width") == 1920 and candidate.get("height") == 1080
                        and isinstance(candidate.get("state"), str) and candidate["state"]):
                    record["snapshot"] = candidate
                    record["status"] = candidate["state"]
                    record["apiLevel"] = candidate.get("sdkInt", record["apiLevel"])
                    record["gmsVersion"] = candidate.get("gmsVersion")
                    exit_code = 0 if candidate["state"] in PROBE_OK else 1
                    break
            time.sleep(0.5)
        else:
            record["status"] = "TIMEOUT"
            record["error"] = "No matching 1920x1080 probe result within 25 seconds"
    except Exception as error:
        record["error"] = str(error)
    try:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with args.out.open("x", encoding="utf-8") as output:
            json.dump(record, output, indent=2)
            output.write("\n")
    except Exception as error:
        print("ERROR: cannot write new output JSON: " + str(error), file=sys.stderr)
        return 1
    print(record["status"])
    return exit_code

if __name__ == "__main__":
    raise SystemExit(main())
