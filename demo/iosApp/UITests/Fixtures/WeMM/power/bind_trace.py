#!/usr/bin/env python3
"""Verify W6 UI observations against an exported Power Profiler trace."""
import argparse
import datetime as dt
import json
import math
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

BUNDLE_ID = "io.ondeviceai.w6.unifiedvideo.fixtures"
TEST_NAME = "testRealMovieAnswer"
OBSERVATION_TYPE = "answer-label-confirmation-upper-bound"
REQUIRED_SCHEMAS = {
    "DeviceChargingState",
    "SystemPowerLevel",
    "ProcessSubsystemPowerImpact",
    "device-thermal-state-intervals",
}
TOLERANCE_SECONDS = 0.001


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    try:
        with path.open(encoding="utf-8") as f:
            return json.load(f)
    except (OSError, UnicodeError, json.JSONDecodeError) as e:
        raise ValueError(f"cannot read valid JSON from {path}: {e}") from e


def required_text(obj, key, where):
    value = obj.get(key) if isinstance(obj, dict) else None
    require(isinstance(value, str) and value.strip(), f"{where}.{key} must be a non-empty string")
    return value


def safe_device_path(value, where):
    require(isinstance(value, str) and value.startswith("/"), f"{where} must be an absolute path")
    require(".." not in value.split("/"), f"{where} must not contain parent traversal")
    return value.replace("/private/var/", "/var/", 1) if value.startswith("/private/var/") else value


def epoch(value, where):
    require(isinstance(value, (int, float)) and not isinstance(value, bool), f"{where} must be numeric")
    result = float(value)
    require(math.isfinite(result), f"{where} must be finite")
    return result


def parse_date(value, where):
    require(isinstance(value, str) and value, f"{where} must be a timezone-qualified ISO timestamp")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        require(parsed.tzinfo is not None and parsed.utcoffset() is not None,
                f"{where} must include a timezone")
        result = parsed.timestamp()
    except (ValueError, OverflowError) as e:
        raise ValueError(f"{where} is not a valid timezone-qualified ISO timestamp") from e
    require(math.isfinite(result), f"{where} is outside the supported time range")
    return result


def verify(capture_dir, ui_path):
    binding = read_json(capture_dir / "binding.json")
    require(isinstance(binding, dict), "binding.json must contain an object")
    udid = required_text(binding, "device", "binding")
    pid = binding.get("pid")
    require(isinstance(pid, int) and not isinstance(pid, bool) and pid > 0,
            "binding.pid must be a positive integer")
    require(binding.get("bundle_id") == BUNDLE_ID, "binding.bundle_id does not match the W6 fixture")
    required_text(binding, "transport", "binding")
    app_path = safe_device_path(required_text(binding, "app_path", "binding"), "binding.app_path")
    executable_path = safe_device_path(required_text(binding, "executable_path", "binding"),
                                        "binding.executable_path")
    require(app_path.endswith("/SpeechDemo.app"), "binding.app_path must identify SpeechDemo.app")
    require(executable_path == app_path + "/SpeechDemo",
            "binding.executable_path must be SpeechDemo inside binding.app_path")

    status_path = capture_dir / "xctrace.exit-status"
    try:
        status = status_path.read_text(encoding="utf-8").strip()
    except (OSError, UnicodeError) as e:
        raise ValueError(f"cannot read xctrace.exit-status: {e}") from e
    require(status == "0", "xctrace.exit-status must contain exactly 0")

    try:
        root = ET.parse(capture_dir / "toc.xml").getroot()
    except (OSError, ET.ParseError) as e:
        raise ValueError(f"cannot parse toc.xml: {e}") from e
    require(root.tag == "trace-toc", "toc.xml root must be trace-toc")
    runs = root.findall("./run")
    require(len(runs) == 1 and runs[0].get("number") == "1",
            "toc.xml must contain exactly one ./run with number=1")
    run = runs[0]
    info = run.find("./info")
    require(info is not None, "run/info is missing")
    summary = info.find("./summary")
    require(summary is not None, "run/info/summary is missing")
    template = summary.findtext("./template-name")
    require(template == "Power Profiler", "trace template-name must be Power Profiler")
    start_text = summary.findtext("./start-date")
    end_text = summary.findtext("./end-date")
    start = parse_date(start_text, "summary.start-date")
    end = parse_date(end_text, "summary.end-date")
    require(end > start, "trace end-date must be later than start-date")

    target = info.find("./target")
    require(target is not None, "run/info/target is missing")
    device = target.find("./device")
    require(device is not None and device.get("platform") == "iOS",
            "run/info/target/device must have platform=iOS")
    trace_udid = device.get("uuid")
    require(isinstance(trace_udid, str) and trace_udid.casefold() == udid.casefold(),
            "trace target device UUID does not match the bound hardware UDID")
    target_process = target.find("./process")
    require(target_process is not None and target_process.get("type") == "attached"
            and target_process.get("name") == "SpeechDemo"
            and target_process.get("pid") == str(pid),
            "run/info/target/process must identify attached SpeechDemo with the bound PID")

    processes = run.findall("./processes/process")
    same_pid = [p for p in processes if p.get("pid") == str(pid)]
    require(len(same_pid) == 1, "run/processes must contain exactly one process with the bound PID")
    process = same_pid[0]
    require(process.get("name") == "SpeechDemo", "bound process name must be SpeechDemo")
    process_path = safe_device_path(process.get("path"), "run/processes/process.path")
    require(process_path == executable_path,
            "trace process path does not match the bound executable path")

    data = run.find("./data")
    require(data is not None, "run/data is missing")
    schemas = {table.get("schema") for table in data.findall("./table")}
    missing = REQUIRED_SCHEMAS - schemas
    require(not missing, "trace/data is missing required table schemas: " + ", ".join(sorted(missing)))

    ui = read_json(ui_path)
    require(isinstance(ui, dict), "ui-observations.json must contain an object")
    ui_device = ui.get("device")
    require(isinstance(ui_device, dict) and ui_device.get("platform") == "iOS"
            and isinstance(ui_device.get("deviceId"), str)
            and ui_device["deviceId"].casefold() == udid.casefold(),
            "UI observation device must be the bound iOS device")
    tests = ui.get("tests")
    require(isinstance(tests, list), "UI observations tests must be a list")
    matches = [t for t in tests if isinstance(t, dict) and t.get("test") == TEST_NAME]
    require(len(matches) == 1, f"UI observations must contain exactly one {TEST_NAME}")
    trials = matches[0].get("trials")
    require(isinstance(trials, list) and len(trials) == 2,
            f"{TEST_NAME} must contain exactly two trials")

    checked = []
    for expected_trial, item in enumerate(trials, 1):
        require(isinstance(item, dict) and type(item.get("trial")) is int
                and item.get("trial") == expected_trial,
                "trials must be ordered and numbered 1, 2")
        require(item.get("observationType") == OBSERVATION_TYPE,
                f"trial {expected_trial} is not a genuine answer-label confirmation")
        tap = epoch(item.get("tapEpoch"), f"trial {expected_trial}.tapEpoch")
        found = epoch(item.get("afterFindEpoch"), f"trial {expected_trial}.afterFindEpoch")
        upper = epoch(item.get("upperSeconds"), f"trial {expected_trial}.upperSeconds")
        require(found > tap and upper > 0, f"trial {expected_trial} times must be increasing and duration positive")
        require(abs((found - tap) - upper) <= TOLERANCE_SECONDS,
                f"trial {expected_trial}.upperSeconds must match its observed interval within 1 ms")
        checked.append((expected_trial, tap, found, upper))
    require(checked[0][2] <= checked[1][1], "trial intervals must be chronological and non-overlapping")
    observations = []
    for trial, tap, found, upper in checked:
        require(start <= tap and found <= end,
                f"trial {trial} interval falls outside the exported trace; intervals are never clamped")
        observations.append({
            "trial": trial,
            "observationType": OBSERVATION_TYPE,
            "tapRelativeNs": round((tap - start) * 1_000_000_000),
            "afterFindRelativeNs": round((found - start) * 1_000_000_000),
            "upperSeconds": upper,
        })
    return {
        "source": "verified Power Profiler TOC and independently supplied XCTest/device bindings",
        "deviceUdId": udid,
        "pid": pid,
        "bundleId": BUNDLE_ID,
        "trace": {"template": "Power Profiler", "startDate": start_text, "endDate": end_text},
        "alignment": "Trace-relative integer nanoseconds from exported wall timestamps and automated UI windows; not a fine-grained synchronized measurement.",
        "uncertainty": "At least the TOC timestamp rounding uncertainty (1 ms); no causal app-energy attribution.",
        "observations": observations,
        "limits": "No energy calculation, budget assignment, or thermal verdict is performed.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_directory", type=Path)
    parser.add_argument("ui_observations", type=Path)
    parser.add_argument("output_json", type=Path, help="new output path; existing files are not overwritten")
    args = parser.parse_args()
    try:
        result = verify(args.capture_directory, args.ui_observations)
        serialized = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
        with args.output_json.open("x", encoding="utf-8") as f:
            f.write(serialized)
    except (ValueError, OSError) as e:
        print(f"bind_trace.py: error: {e}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
