#!/usr/bin/env python3
"""Validate and summarize the fixed Apple MLXVLM device benchmark."""

import argparse
import json
import math
import re
from pathlib import Path


EXPECTED = {
    "actualBackend": "mlx.smolvlm2-500m",
    "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
    "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268",
    "runtimeRevision": "6e338e5df49424fc0db7c6d58184d622f92ed18a",
    "fixtureHash": "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606",
    "prompt": "Describe this image in one brief sentence.",
    "maxOutputTokens": 32,
}
MEMORY_METHOD = "process-resident-mach-task-basic-info-10ms"
UNVERIFIED = ["metalGpuPeak", "energy", "naturalImageQuality"]
RUBRIC = "synthetic-red-square-v1"


def _duration(value, name):
    if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
        raise ValueError(f"invalid {name}")
    return value


def _integer(value, name):
    if type(value) is not int or value < 0:
        raise ValueError(f"invalid {name}")
    return value


def validate(run):
    if "errorType" in run:
        raise ValueError("benchmark run failed")
    if run.get("schemaVersion") != 1:
        raise ValueError("unsupported schema")
    for key in ("runId", "sdkCommit", "device", "OS"):
        if not isinstance(run.get(key), str) or not run[key]:
            raise ValueError(f"missing metadata: {key}")
    if not re.fullmatch(r"[0-9a-f]{40}", run["sdkCommit"]):
        raise ValueError("invalid SDK commit identity")
    for key, expected in EXPECTED.items():
        if run.get(key) != expected:
            raise ValueError(f"fixed identity mismatch: {key}")
    if run.get("warmup") != {"state": "success"}:
        raise ValueError("warm-up did not succeed")
    iterations = run.get("iterations")
    if not isinstance(iterations, list) or len(iterations) != 5:
        raise ValueError("expected exactly five measured iterations")
    for expected_index, item in enumerate(iterations, 1):
        if not isinstance(item, dict) or item.get("index") != expected_index:
            raise ValueError("invalid iteration sequence")
        load = _duration(item.get("loadMs"), "loadMs")
        first = _duration(item.get("firstUpdateMs"), "firstUpdateMs")
        total = _duration(item.get("totalMs"), "totalMs")
        if load > first or first > total:
            raise ValueError("misordered timings")
        output = item.get("output")
        if not isinstance(output, str) or not output.strip():
            raise ValueError("empty output")
        quality = item.get("quality")
        expected_pass = "red" in output.casefold() and "square" in output.casefold()
        if quality != {"rubric": RUBRIC, "passed": expected_pass}:
            raise ValueError("rubric result does not match output")
    memory = run.get("memory")
    if not isinstance(memory, dict) or memory.get("method") != MEMORY_METHOD:
        raise ValueError("memory measurement must be labeled as process RSS")
    if memory.get("state") == "unverified":
        if set(memory) != {"method", "state", "reason"} or not memory.get("reason"):
            raise ValueError("unverified RSS must not contain invented bytes")
    else:
        baseline = _integer(memory.get("baselineBytes"), "baselineBytes")
        peak = _integer(memory.get("peakBytes"), "peakBytes")
        delta = _integer(memory.get("deltaBytes"), "deltaBytes")
        if baseline == 0 or peak < baseline or delta != peak - baseline:
            raise ValueError("invalid process RSS delta")
    if run.get("unverified") != UNVERIFIED:
        raise ValueError("required unverified resource and quality fields are missing")
    if run.get("qualityScope") != "synthetic-fixture-only":
        raise ValueError("quality evidence is limited to the synthetic fixture")
    return run


def _statistics(values):
    values = sorted(values)
    count = len(values)
    return {
        "count": count,
        "p50": values[math.ceil(count * 0.50) - 1],
        "p95": values[math.ceil(count * 0.95) - 1],
    }


def summarize(raw):
    run = validate(raw)
    iterations = run["iterations"]
    return {
        "schemaVersion": 1,
        "runId": run["runId"],
        "sdkCommit": run["sdkCommit"],
        "device": run["device"],
        "OS": run["OS"],
        **EXPECTED,
        "sampleCount": len(iterations),
        "percentileMethod": "nearest-rank",
        "timingsMs": {
            "load": _statistics([item["loadMs"] for item in iterations]),
            "firstUpdate": _statistics([item["firstUpdateMs"] for item in iterations]),
            "total": _statistics([item["totalMs"] for item in iterations]),
        },
        "memory": run["memory"],
        "quality": {
            "rubric": RUBRIC,
            "passed": sum(item["quality"]["passed"] for item in iterations),
            "total": len(iterations),
        },
        "qualityScope": run["qualityScope"],
        "unverified": run["unverified"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    raw = json.loads(args.input.read_text(), parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summarize(raw), ensure_ascii=False, indent=2, allow_nan=False) + "\n")


if __name__ == "__main__":
    main()
