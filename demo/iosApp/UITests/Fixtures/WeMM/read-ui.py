#!/usr/bin/env python3
import argparse
import json
import math
import subprocess
import sys

SUITE = "W6UnifiedVideoUITests"
NAMES = (
    "testRealMovieAnswer",
    "testCancelAfterVisibleTextAndAskAgain",
    "testLeavingDuringGenerationCancelsAndRestoresInput",
    "testUndecodableMovieReportsInputFailure",
)
TAP = 'Tap "multimodal-generate" Button'
OUTPUT = [
    'Waiting 180.0s for "multimodal-output" StaticText to exist',
    'Waiting 30.0s for "multimodal-output" StaticText to exist',
    'Find the "multimodal-output" StaticText',
]
UPDATE = [
    'Waiting 90.0s for "multimodal-update" StaticText to exist',
    'Find the "multimodal-update" StaticText',
]


def require(ok, message):
    if not ok:
        raise ValueError(message)


def get_json(path, kind, *args):
    cmd = ["xcrun", "xcresulttool", "get", "test-results", kind,
           "--path", path, *args]
    proc = subprocess.run(cmd, text=True, capture_output=True)
    if proc.returncode:
        raise RuntimeError(f"{' '.join(cmd)} failed: {proc.stderr.strip()}")
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Invalid JSON from xcresulttool {kind}: {exc}") from exc


def walk(nodes):
    require(isinstance(nodes, list), "testNodes/children must be arrays")
    for node in nodes:
        require(isinstance(node, dict), "Invalid test node")
        yield node
        children = node.get("children", [])
        yield from walk(children)


def check_device(device, device_id, platform=None):
    require(isinstance(device, dict), "Missing or invalid device")
    require(device.get("deviceId") == device_id, "Device ID mismatch")
    p = device.get("platform")
    require(isinstance(p, str) and p, "Missing device platform")
    require(platform is None or p == platform, "Device platform mismatch")
    return p


def measure(activities, expected, test_name):
    require(isinstance(activities, list), f"{test_name}: activities must be an array")
    taps = [i for i, a in enumerate(activities)
            if isinstance(a, dict) and a.get("title") == TAP]
    require(len(taps) == len(expected), f"{test_name}: unexpected generate-tap count")
    trials = []
    for number, (index, sequence) in enumerate(zip(taps, expected), 1):
        titles = [TAP, *sequence]
        end = index + len(titles)
        require(end < len(activities), f"{test_name}: missing following activity")
        involved = activities[index:end + 1]
        require(all(isinstance(a, dict) for a in involved),
                f"{test_name}: invalid activity in observed sequence")
        require([a.get("title") for a in involved[:-1]] == titles,
                f"{test_name}: required adjacent activity sequence changed")
        next_title = involved[-1].get("title")
        require(isinstance(next_title, str) and next_title and next_title != TAP,
                f"{test_name}: following activity has an invalid title or is another generate tap")
        times = []
        for activity in involved:
            require(activity.get("isAssociatedWithFailure") is False,
                    f"{test_name}: activity associated with failure")
            value = activity.get("startTime")
            require(type(value) in (int, float) and math.isfinite(value),
                    f"{test_name}: startTime is missing or invalid")
            times.append(float(value))
        require(all(a <= b for a, b in zip(times, times[1:])),
                f"{test_name}: activity timestamps are not monotone")
        trials.append({
            "trial": number,
            "observationType": ("visible-update-confirmation-upper-bound"
                                if "multimodal-update" in sequence[-1]
                                else "answer-label-confirmation-upper-bound"),
            "tapEpoch": times[0],
            "afterFindEpoch": times[-1],
            "upperSeconds": times[-1] - times[0],
        })
    return trials


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("xcresult")
    parser.add_argument("expectedDeviceId")
    parser.add_argument("--short-answers-only", action="store_true",
                        help="Expect only testRealMovieAnswer and its two color-order answers")
    args = parser.parse_args()
    names = ("testRealMovieAnswer",) if args.short_answers_only else NAMES

    summary = get_json(args.xcresult, "summary")
    tests = get_json(args.xcresult, "tests")
    require(isinstance(summary, dict) and isinstance(tests, dict), "Invalid result shape")
    require(summary.get("result") == "Passed", "XCResult did not pass")
    for key, expected in (("totalTestCount", len(names)), ("passedTests", len(names)),
                          ("failedTests", 0), ("skippedTests", 0)):
        require(type(summary.get(key)) is int and summary[key] == expected,
                f"Unexpected summary {key}")

    rows = summary.get("devicesAndConfigurations")
    require(isinstance(rows, list) and len(rows) == 1,
            "Expected exactly one summary device/configuration")
    summary_device = rows[0].get("device") if isinstance(rows[0], dict) else None
    platform = check_device(summary_device, args.expectedDeviceId)
    for key, expected in (("passedTests", len(names)), ("failedTests", 0), ("skippedTests", 0)):
        require(type(rows[0].get(key)) is int and rows[0][key] == expected,
                f"Unexpected device summary {key}")

    devices = tests.get("devices")
    require(isinstance(devices, list) and len(devices) == 1,
            "Expected exactly one tests.devices device")
    check_device(devices[0], args.expectedDeviceId, platform)
    expected_ids = {f"{SUITE}/{name}()" for name in names}
    case_nodes = [node for node in walk(tests.get("testNodes"))
                  if node.get("nodeType") == "Test Case"]
    identifiers = [node.get("nodeIdentifier") for node in case_nodes]
    require(len(identifiers) == len(expected_ids)
            and all(isinstance(identifier, str) for identifier in identifiers)
            and set(identifiers) == expected_ids,
            "Unexpected test case IDs, omissions, or duplicates")
    for node in case_nodes:
        require(node.get("result") == "Passed",
                f"Non-passing test: {node.get('nodeIdentifier')}")

    output_tests = []
    for name in names:
        identifier = f"{SUITE}/{name}()"
        result = get_json(args.xcresult, "activities", "--test-id", identifier)
        require(isinstance(result, dict) and result.get("testIdentifier") == identifier,
                f"Activity test scope mismatch: {identifier}")
        runs = result.get("testRuns")
        require(isinstance(runs, list) and len(runs) == 1,
                f"Expected one test run: {identifier}")
        run = runs[0]
        check_device(run.get("device"), args.expectedDeviceId, platform)
        activities = run.get("activities")
        require(isinstance(activities, list), f"Missing activities: {identifier}")
        if name == "testRealMovieAnswer":
            trials = measure(activities, [OUTPUT, OUTPUT], name)
            kind = "answer-label-confirmation-upper-bound"
        elif name in ("testCancelAfterVisibleTextAndAskAgain",
                      "testLeavingDuringGenerationCancelsAndRestoresInput"):
            trials = measure(activities, [UPDATE, OUTPUT], name)
            kind = "visible-update-then-answer-confirmation-upper-bound"
        else:
            trials = []
            kind = "negative-clip-state-no-latency-measured"
        output_tests.append({"test": name, "observationType": kind, "trials": trials})

    print(json.dumps({
        "method": "XCTest UI automation tap-to-following-action upper bound; includes automation and polling, not per-request latency",
        "device": {"deviceId": args.expectedDeviceId, "platform": platform},
        "tests": output_tests,
    }, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"read-ui.py: {exc}", file=sys.stderr)
        sys.exit(2)
