#!/usr/bin/env python3
"""Validate fixed synthetic native MLXVLM video lifecycle evidence; never infer quality."""
import argparse
from fractions import Fraction
import json
import math
from pathlib import Path
import re
import uuid

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "schemaVersion": 1, "state": "success", "actualBackend": "mlx.smolvlm2-500m",
    "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
    "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268",
    "runtimeRevision": "6e338e5df49424fc0db7c6d58184d622f92ed18a",
    "fixtureHash": "9786524a69579ffd69d75c49048a566f31eaec090b1487b69aa4ce6d55e1ee51",
    "prompt": "Describe what happens in this video in one brief sentence.", "maxOutputTokens": 32,
    "scope": "fixed-synthetic-video-lifecycle-only",
    "unverified": ["naturalVideoQuality", "metalGpuPeak", "energy", "minimumOS", "androidBackend"],
}
GATES = {"leaseProtectedBeforeGeneration", "leaseProtectedAfterCancellation", "leaseReleasedAfterClose",
         "closedRejectsVideo", "closedRejectsImage", "sourceUnchanged", "noOwnedSnapshotsRemaining"}


def _require(condition, message):
    if not condition: raise ValueError(message)


def _keys(value, keys, name):
    _require(isinstance(value, dict) and set(value) == set(keys), "invalid fields: " + name)


def _duration(value):
    _require(type(value) in (int, float) and math.isfinite(value) and value >= 0, "invalid duration")
    return value


def _time(value):
    _keys(value, {"value", "timescale"}, "rational time")
    _require(type(value["value"]) is int and -(2**63) <= value["value"] < 2**63
             and type(value["timescale"]) is int and 0 < value["timescale"] < 2**31, "invalid rational time")
    return Fraction(value["value"], value["timescale"])


def _metadata(data):
    _keys(data, {"snapshotSHA256", "actualTimes", "requestedTimes", "clipDuration", "sampledSpan", "widths", "heights"}, "metadata")
    _require(data["snapshotSHA256"] == EXPECTED["fixtureHash"], "owned copy hash mismatch")
    for key in ("actualTimes", "requestedTimes", "widths", "heights"):
        _require(isinstance(data[key], list) and len(data[key]) == 8, "expected eight real frames")
    actual = [_time(t) for t in data["actualTimes"]]
    _require(actual == [Fraction(v,14848) for v in [0,3584,7168,10752,14848,18432,22016,25600]], "actual frame times changed")
    # Preserve the actual native rational representation as well as the value.
    _require(all(t["timescale"] == 14848 for t in data["actualTimes"]), "actual CMTime timescale changed")
    _require([_time(t) for t in data["requestedTimes"]] == [Fraction(v,4) for v in range(8)], "sampling grid changed")
    _require(_time(data["clipDuration"]) == 2 and _time(data["sampledSpan"]) == actual[-1]-actual[0], "clip/span mismatch")
    _require(all(type(v) is int and v == 160 for v in data["widths"])
             and all(type(v) is int and v == 90 for v in data["heights"]), "fixture dimensions changed")


def _run(item, kind, pid):
    _keys(item, {"kind", "state", "pid", "output", "metadata", "events", "loadMs", "prepareMs", "preparedFrameCount", "snapshotRemoved"}, "run")
    _require(item["kind"] == kind and item["state"] == ("cancelled" if kind == "cancelled" else "success"), "wrong run state/order")
    _require(type(item["pid"]) is int and item["pid"] == pid, "same-process gate failed")
    _require(isinstance(item["output"], str) and (kind == "cancelled" or item["output"].strip()), "empty output")
    _require(type(item["preparedFrameCount"]) is int and item["preparedFrameCount"] == 8 and item["snapshotRemoved"] is True, "prepare/cleanup gate failed")
    _metadata(item["metadata"])
    events = item["events"]
    _require(isinstance(events, list), "missing phases")
    names, times = [], []
    for event in events:
        _keys(event, {"name", "ms"}, "event")
        _require(isinstance(event["name"], str), "invalid event name")
        names.append(event["name"]); times.append(_duration(event["ms"]))
    _require(times == sorted(times), "misordered phase timings")
    fixed = ["begin", "decoded", "modelLoaded", "prepareStarted", "prepared", "snapshotClosed", "returned", "drained"]
    allowed = set(fixed) | {"update"} | ({"cancelRequested"} if kind == "cancelled" else set())
    _require(set(names) == allowed and all(names.count(n) == 1 for n in fixed), "missing/duplicate/unexpected phase")
    _require(names[0] == "begin" and names[-1] == "drained", "invalid phase boundaries")
    positions = [names.index(n) for n in fixed]
    _require(positions == sorted(positions), "decode/prepare/join/cleanup misordered")
    updates = [i for i,n in enumerate(names) if n == "update"]
    _require(updates and min(updates) > names.index("prepared") and max(updates) < names.index("returned"), "update outside generation")
    if kind == "cancelled":
        _require(names.count("cancelRequested") == 1 and min(updates) < names.index("cancelRequested") < names.index("snapshotClosed"), "not active first-update cancellation")
    load, prepare = _duration(item["loadMs"]), _duration(item["prepareMs"])
    _require(load <= times[names.index("modelLoaded")] and prepare <= times[names.index("prepared")], "inconsistent measured intervals")


def validate(raw):
    _keys(raw, set(EXPECTED) | {"runId", "sourceCommit", "device", "OS", "pid", "modelFiles", "runs", "gates"}, "report")
    for key, expected in EXPECTED.items():
        _require(type(raw[key]) is type(expected) and raw[key] == expected, "fixed identity mismatch: " + key)
    for key in ("runId", "sourceCommit", "device", "OS"):
        _require(isinstance(raw[key], str) and raw[key].strip(), "missing identity: " + key)
    try: uuid.UUID(raw["runId"])
    except ValueError: raise ValueError("invalid runId") from None
    _require(re.fullmatch(r"[0-9a-f]{40}", raw["sourceCommit"]), "invalid source commit")
    _require(re.fullmatch(r"iPhone[0-9]+,[0-9]+", raw["device"]), "requires physical iPhone identity")
    _require(type(raw["pid"]) is int and raw["pid"] > 0, "invalid PID")
    manifest = json.loads((ROOT / "apple-mlx/Sources/ODAIKitAppleMLXVLM/Resources/smolvlm2-model.json").read_text())
    expected_files = [{k:f[k] for k in ("path","bytes","sha256")} for f in manifest["files"]]
    _require(raw["modelFiles"] == expected_files, "model file inventory mismatch")
    _keys(raw["gates"], GATES, "gates")
    _require(all(v is True for v in raw["gates"].values()), "missing lifecycle gate")
    _require(isinstance(raw["runs"],list) and len(raw["runs"]) == 3, "expected three lifecycle runs")
    for item,kind in zip(raw["runs"], ["success", "cancelled", "reentry"]): _run(item,kind,raw["pid"])
    return raw


def summarize(raw):
    # Keep measured raw phase/time evidence; no acceptance or quality is inferred.
    return validate(raw)


def _unique_object(pairs):
    result = {}
    for key,value in pairs:
        _require(key not in result, "duplicate JSON key")
        result[key] = value
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input",type=Path)
    parser.add_argument("--output",type=Path)
    args = parser.parse_args()
    try:
        raw = json.loads(args.input.read_text(), object_pairs_hook=_unique_object,
                         parse_constant=lambda v: (_ for _ in ()).throw(ValueError(v)))
        data = json.dumps(summarize(raw),ensure_ascii=False,indent=2,allow_nan=False) + "\n"
        if args.output:
            args.output.parent.mkdir(parents=True,exist_ok=True); args.output.write_text(data)
        else: print(data,end="")
    except (ValueError,OSError) as error: parser.exit(1,str(error)+"\n")


if __name__ == "__main__": main()
