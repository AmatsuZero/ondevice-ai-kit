import argparse
import hashlib
import json
import math
import re
import subprocess
from collections import Counter, defaultdict
from fractions import Fraction
from pathlib import Path


_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_TIME_BASE = re.compile(r"(-?\d+)/(\d+)\Z")
_STAGES = {"decodeRelease", "latch", "srStart", "srEnd", "swap", "display"}
_NS_PER_SECOND = 1_000_000_000


def _integer(value, name, minimum=None):
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{name} must be an integer")
    if minimum is not None and value < minimum:
        raise ValueError(f"{name} must be at least {minimum}")
    return value


def _validate_index(index):
    if not isinstance(index, dict) or not _SHA256.fullmatch(
        index.get("fixtureSha256", "") if isinstance(index.get("fixtureSha256"), str) else ""
    ):
        raise ValueError("invalid index fixtureSha256")
    frames = index.get("frames")
    if not isinstance(frames, list) or not frames:
        raise ValueError("index frames must be a non-empty list")
    for ordinal, frame in enumerate(frames):
        if not isinstance(frame, dict):
            raise ValueError("each index frame must be an object")
        if _integer(frame.get("sourceOrdinal"), "sourceOrdinal", 0) != ordinal:
            raise ValueError("sourceOrdinal values must be sequential from zero")
        _integer(frame.get("pts"), "pts")
        time_base = frame.get("timeBase")
        match = _TIME_BASE.fullmatch(time_base) if isinstance(time_base, str) else None
        if not match or int(match.group(2)) == 0:
            raise ValueError("invalid frame timeBase")
    return frames


def _validate_run(run, fixture_sha):
    if not isinstance(run, dict):
        raise ValueError("run must be an object")
    if run.get("schemaVersion") != 1 or isinstance(run.get("schemaVersion"), bool):
        raise ValueError("unsupported schemaVersion")
    if not isinstance(run.get("runId"), str) or not run["runId"]:
        raise ValueError("runId must be a non-empty string")
    if run.get("fixtureSha256") != fixture_sha:
        raise ValueError("run fixtureSha256 does not match index")
    generation = _integer(run.get("generation"), "generation", 0)
    loops = _integer(run.get("loops"), "loops", 1)
    if run.get("mode") not in ("CUNNY", "ORIGINAL", "AUTO", "SPATIAL"):
        raise ValueError("mode must be CUNNY, ORIGINAL, AUTO, or SPATIAL")
    if not isinstance(run.get("displayTimingSupported"), bool):
        raise ValueError("displayTimingSupported must be boolean")
    if not isinstance(run.get("completed"), bool):
        raise ValueError("completed must be boolean")
    return generation, loops


def _summary(values):
    values = sorted(values)
    if not values:
        return {"count": 0, "minNs": None, "maxNs": None, "p95Ns": None}
    return {
        "count": len(values),
        "minNs": values[0],
        "maxNs": values[-1],
        "p95Ns": values[math.ceil(0.95 * len(values)) - 1],
    }


def _bucket_summary(rows):
    counts = Counter(row["classification"] for row in rows)
    return {
        "planned": len(rows),
        "onTimeSR": counts["onTimeSR"],
        "onTimeOriginal": counts["onTimeOriginal"],
        "late": counts["late"],
        "unconfirmed": counts["unconfirmed"],
        "missed": counts["late"] + counts["unconfirmed"],
    }


def assess(index, run, events):
    frames = _validate_index(index)
    generation, loops = _validate_run(run, index["fixtureSha256"])
    if not isinstance(events, list):
        raise ValueError("events must be a list")

    planned = {(loop, ordinal) for loop in range(loops) for ordinal in range(len(frames))}
    stages = {}
    ids = {"swap": {}, "display": {}}
    for event in events:
        if not isinstance(event, dict):
            raise ValueError("each event must be an object")
        if event.get("runId") != run["runId"]:
            raise ValueError("event belongs to a different run")
        if _integer(event.get("generation"), "event generation", 0) != generation:
            raise ValueError("event generation does not match run")
        loop = _integer(event.get("loop"), "event loop", 0)
        ordinal = _integer(event.get("sourceOrdinal"), "event sourceOrdinal", 0)
        if (loop, ordinal) not in planned:
            raise ValueError("unexpected event frame")
        stage = event.get("stage")
        if stage not in _STAGES:
            raise ValueError("unknown event stage")
        key = (loop, ordinal)
        stage_key = (key, stage)
        if stage_key in stages:
            raise ValueError("duplicate stage for frame")
        stages[stage_key] = event

        if stage == "latch":
            begin = _integer(event.get("latchBeginNs"), "latchBeginNs", 0)
            target = _integer(event.get("targetNs"), "targetNs", 0)
            next_target = _integer(event.get("nextTargetNs"), "nextTargetNs", 0)
            if next_target <= target:
                raise ValueError("invalid latch timing interval")
        elif stage == "srEnd":
            _integer(event.get("gpuElapsedNs"), "gpuElapsedNs", 0)
            if not isinstance(event.get("gpuTimingValid"), bool):
                raise ValueError("gpuTimingValid must be boolean")
        elif stage == "swap":
            backend = event.get("submittedBackend")
            if backend not in ("ORIGINAL", "CUNNY", "SPATIAL"):
                raise ValueError("invalid submittedBackend")
            if backend == "SPATIAL" and run["mode"] != "SPATIAL":
                raise ValueError("SPATIAL backend requires SPATIAL mode")
            if run["mode"] == "SPATIAL" and backend == "CUNNY":
                raise ValueError("SPATIAL mode cannot use CUNNY backend")
            if not isinstance(event.get("reason"), str):
                raise ValueError("swap reason must be a string")
        elif stage == "display":
            _integer(event.get("displayNs"), "displayNs", 1)

        if stage in ("swap", "display"):
            frame_id = event.get("eglFrameId")
            if frame_id is not None:
                _integer(frame_id, "eglFrameId", 0)
                if frame_id in ids[stage]:
                    raise ValueError("eglFrameId reused within generation")
                ids[stage][frame_id] = key

    swaps = {key: event for (key, stage), event in stages.items() if stage == "swap"}
    displays = {key: event for (key, stage), event in stages.items() if stage == "display"}
    latches = {key: event for (key, stage), event in stages.items() if stage == "latch"}
    gpu_ends = {key: event for (key, stage), event in stages.items() if stage == "srEnd"}
    gpu_starts = {key for key, stage in stages if stage == "srStart"}

    display_by_id = {
        event["eglFrameId"]: (key, event)
        for key, event in displays.items()
        if event.get("eglFrameId") is not None
    }
    rows = []
    drift = []
    latch_to_display = []
    confirmed_latch_to_display = []
    reason_counts = Counter()
    gpu_samples = []
    backend_samples = defaultdict(list)

    for loop, ordinal in sorted(planned):
        key = (loop, ordinal)
        swap_event = swaps.get(key)
        display_event = displays.get(key)
        latch_event = latches.get(key)
        gpu_event = gpu_ends.get(key)
        backend = swap_event.get("submittedBackend") if swap_event else None
        if swap_event:
            reason_counts[swap_event["reason"]] += 1

        matching_display = None
        if (
            run["displayTimingSupported"]
            and swap_event
            and swap_event.get("eglFrameId") is not None
        ):
            candidate = display_by_id.get(swap_event["eglFrameId"])
            if candidate and candidate[0] == key:
                matching_display = candidate[1]

        elapsed_ns = None
        in_window = False
        if matching_display and latch_event:
            display_ns = matching_display["displayNs"]
            elapsed_ns = display_ns - latch_event["latchBeginNs"]
            if elapsed_ns < 0:
                matching_display = None
            else:
                in_window = latch_event["targetNs"] <= display_ns < latch_event["nextTargetNs"]
                confirmed_latch_to_display.append(elapsed_ns)

        if not matching_display or not latch_event:
            classification = "unconfirmed"
        elif 30 * elapsed_ns > _NS_PER_SECOND or not in_window:
            classification = "late"
        elif backend == "CUNNY" or (run["mode"] == "SPATIAL" and backend == "SPATIAL"):
            classification = "onTimeSR"
        elif backend == "ORIGINAL":
            classification = "onTimeOriginal"
        else:
            classification = "unconfirmed"

        row = {
            "loop": loop,
            "sourceOrdinal": ordinal,
            "classification": classification,
            "backend": backend,
        }
        rows.append(row)

        if matching_display and latch_event:
            drift_ns = matching_display["displayNs"] - latch_event["targetNs"]
            drift.append(drift_ns)
            latch_to_display.append(matching_display["displayNs"] - latch_event["latchBeginNs"])
        if (
            key in gpu_starts
            and gpu_event
            and gpu_event["gpuTimingValid"]
            and gpu_event["gpuElapsedNs"] > 0
        ):
            elapsed = gpu_event["gpuElapsedNs"]
            gpu_samples.append(elapsed)
            backend_samples[backend or "unsubmitted"].append(elapsed)

    counts = _bucket_summary(rows)
    counts["missed"] = counts["late"] + counts["unconfirmed"]
    supported = run["displayTimingSupported"]
    complete = run["completed"]
    total = len(planned)

    gpu_jobs = gpu_starts | set(gpu_ends)
    gpu_timing_complete = bool(gpu_jobs) and all(
        key in gpu_starts
        and key in gpu_ends
        and gpu_ends[key]["gpuTimingValid"]
        and gpu_ends[key]["gpuElapsedNs"] > 0
        for key in gpu_jobs
    )

    if run["mode"] == "ORIGINAL":
        sr_gate = gpu_gate = "notApplicable"
    else:
        sr_gate = (
            "unverified" if not supported or not complete or not confirmed_latch_to_display
            else "pass" if counts["onTimeSR"] * 100 > total * 95 else "fail"
        )
        if not complete or not gpu_timing_complete:
            gpu_gate = "unverified"
        else:
            gpu_gate = "pass" if _summary(gpu_samples)["p95Ns"] <= 25_000_000 else "fail"

    if not supported or not complete or not confirmed_latch_to_display:
        end_gate = missed_gate = "unverified"
    else:
        end_gate = (
            "pass" if 30 * _summary(confirmed_latch_to_display)["p95Ns"] <= _NS_PER_SECOND
            else "fail"
        )
        missed_gate = "pass" if counts["missed"] * 100 < total * 5 else "fail"

    minutes = defaultdict(list)
    first_pts = frames[0]["pts"]
    time_base_match = _TIME_BASE.fullmatch(frames[0]["timeBase"])
    time_base = Fraction(int(time_base_match.group(1)), int(time_base_match.group(2)))
    loop_duration = (frames[-1]["pts"] - first_pts) * time_base + Fraction(1, 30)
    for row in rows:
        frame = frames[row["sourceOrdinal"]]
        source_time = (frame["pts"] - first_pts) * time_base
        minute = (row["loop"] * loop_duration + source_time) // 60
        minutes[minute].append(row)

    return {
        "runId": run["runId"],
        "fixtureSha256": index["fixtureSha256"],
        "counts": counts,
        "gates": {
            "gpu": gpu_gate,
            "endToEnd": end_gate,
            "missed": missed_gate,
            "srDelivery": sr_gate,
        },
        "aggregates": {
            "total": _bucket_summary(rows),
            "coldStart": _bucket_summary([row for row in rows if row["loop"] == 0]),
            "sourceNominalMinutes": [
                {"minute": minute, **_bucket_summary(bucket)}
                for minute, bucket in sorted(minutes.items())
            ],
        },
        "timing": {
            "gpuTotal": _summary(gpu_samples),
            "gpuByBackend": {
                backend: _summary(samples)
                for backend, samples in sorted(backend_samples.items())
            },
            "driftNs": _summary(drift),
            "latchToDisplayNs": _summary(latch_to_display),
            "gpuSamplesNs": gpu_samples,
            "driftSamplesNs": drift,
        },
        "reasonCounts": {"notApplicable": total} if run["mode"] == "ORIGINAL" else dict(sorted(reason_counts.items())),
        "maxConsecutiveMissed": _max_consecutive_missed(rows),
    }


def _max_consecutive_missed(rows):
    maximum = current = 0
    for row in rows:
        if row["classification"] in ("late", "unconfirmed"):
            current += 1
            maximum = max(maximum, current)
        else:
            current = 0
    return maximum


def index_fixture(path):
    path = Path(path)
    probe = subprocess.run(
        [
            "ffprobe", "-v", "error", "-select_streams", "v:0",
            "-show_entries", "stream=time_base:frame=pts",
            "-show_frames", "-of", "json", str(path),
        ],
        check=True, capture_output=True, text=True,
    )
    data = json.loads(probe.stdout)
    streams = data.get("streams", [])
    source_frames = data.get("frames", [])
    if not streams or not source_frames:
        raise ValueError("fixture has no video stream or frames")
    time_base = streams[0].get("time_base")
    match = _TIME_BASE.fullmatch(time_base) if isinstance(time_base, str) else None
    if not match or int(match.group(2)) == 0:
        raise ValueError("ffprobe returned an invalid stream time_base")
    frames = []
    for ordinal, frame in enumerate(source_frames):
        pts = frame.get("pts")
        _integer(pts, "ffprobe frame pts")
        frames.append({"sourceOrdinal": ordinal, "pts": pts, "timeBase": time_base})
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return {"fixtureSha256": digest.hexdigest(), "frames": frames}


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    index_parser = commands.add_parser("index")
    index_parser.add_argument("--fixture", required=True)
    index_parser.add_argument("--out", required=True)
    assess_parser = commands.add_parser("assess")
    assess_parser.add_argument("--index", required=True)
    assess_parser.add_argument("--run", required=True)
    assess_parser.add_argument("--out", required=True)
    args = parser.parse_args()

    if args.command == "index":
        result = index_fixture(args.fixture)
    else:
        with open(args.index, encoding="utf-8") as source:
            index = json.load(source)
        with open(Path(args.run) / "run.json", encoding="utf-8") as source:
            run = json.load(source)
        with open(Path(args.run) / "events.jsonl", encoding="utf-8") as source:
            events = [json.loads(line) for line in source if line.strip()]
        result = assess(index, run, events)

    with open(args.out, "w", encoding="utf-8") as output:
        json.dump(result, output, indent=2)
        output.write("\n")


if __name__ == "__main__":
    main()
