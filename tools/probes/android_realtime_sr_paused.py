#!/usr/bin/env python3
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import uuid
from pathlib import Path

from tools.probes.android_realtime_sr_device import Adb, FIXTURE_PROFILES, PKG, device_sha, push_fixture


def text(value):
    value = getattr(value, "stdout", value)
    return value.decode(errors="replace") if isinstance(value, bytes) else str(value or "")


def rc(value):
    return getattr(value, "returncode", 0)


def pull(adb, remote, destination):
    data = adb.exec_out("run-as", PKG, "cat", remote)
    if data.returncode != 0:
        raise RuntimeError(f"Device file unavailable: {remote}")
    data = data.stdout
    if isinstance(data, str):
        data = data.encode()
    if not data or not data.startswith(b"\x89PNG\r\n\x1a\n") and remote.endswith(".png"):
        raise RuntimeError(f"Missing or invalid PNG: {remote}")
    destination.write_bytes(data)
    return data


def check_events(data, compare_spatial=False):
    events = [json.loads(line) for line in data.decode().splitlines() if line.strip()]
    selected = [row for row in events if row.get("stage") == "swap" and row.get("pausedPreview") is True]
    expected = ["ORIGINAL", "SPATIAL", "CUNNY", "ORIGINAL"] if compare_spatial else ["ORIGINAL", "CUNNY", "ORIGINAL"]
    if len(selected) != len(expected):
        raise RuntimeError(f"Expected {len(expected)} pausedPreview swaps, found {len(selected)}")
    labels = [row["submittedBackend"] for row in selected]
    if labels != expected:
        raise RuntimeError(f"Unexpected pausedPreview source order: {labels}")
    def field(row, name):
        return next((v for k, v in row.items() if k.lower() == name.lower()), None)
    pts = [field(row, "ptsUs") for row in selected]
    ordinals = [field(row, "sourceOrdinal") for row in selected]
    generations = [field(row, "generation") for row in selected]
    widths = [field(row, "renderWidth") for row in selected]
    heights = [field(row, "renderHeight") for row in selected]
    if any(v is None for v in pts + ordinals + generations + widths + heights) or len(set(pts)) != 1 or len(set(ordinals)) != 1:
        raise RuntimeError("PausedPreview swaps do not share exact ptsUs/sourceOrdinal")
    if len(set(zip(widths, heights))) != 1:
        raise RuntimeError("PausedPreview swaps do not share Surface geometry")
    if any(not isinstance(v, int) for v in generations) or not all(a < b for a, b in zip(generations, generations[1:])):
        raise RuntimeError("PausedPreview generations are not strictly ascending")
    for row in selected:
        reason = field(row, "reason")
        if not isinstance(reason, str) or reason not in {"enhanced", "enhancement_not_requested"}:
            raise RuntimeError(f"Swap did not report a successful reason: {reason!r}")
    return {"sources": labels, "ptsUs": pts[0], "sourceOrdinal": ordinals[0], "generations": generations,
            "surfaceGeometry": [widths[0], heights[0]]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("android-realtime-sr-paused"))
    parser.add_argument("--project", type=Path, default=Path.cwd())
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--test-apk", type=Path)
    parser.add_argument("--compare-spatial", action="store_true")
    args = parser.parse_args()
    if args.out.exists():
        parser.error("--out must be a new directory")
    args.out.mkdir(parents=True)
    host = {"success": False}
    adb = Adb(args.serial)
    try:
        fixture = args.fixture.read_bytes()
        fixture_sha = hashlib.sha256(fixture).hexdigest()
        if fixture_sha not in FIXTURE_PROFILES:
            raise RuntimeError("Fixture SHA is not in FIXTURE_PROFILES")
        fixture_profile = FIXTURE_PROFILES[fixture_sha]
        host["fixture_sha256"] = fixture_sha
        host["fixture_profile"] = fixture_profile
        project = args.project.resolve()
        target = args.apk.resolve() if args.apk is not None else project / "examples/android-realtime-sr/build/outputs/apk/debug/android-realtime-sr-debug.apk"
        tests = list((project / "examples/android-realtime-sr/build/outputs/apk/androidTest/debug").glob("*.apk"))
        if args.test_apk is not None:
            test_apk = args.test_apk.resolve()
        else:
            if len(tests) != 1:
                raise RuntimeError("Expected target APK and exactly one androidTest/debug APK")
            test_apk = tests[0]
        if not target.is_file():
            raise RuntimeError("Expected target APK and exactly one androidTest/debug APK")
        for apk, expected in ((target, PKG), (test_apk, PKG + ".test")):
            result = subprocess.run(["aapt", "dump", "badging", str(apk)], capture_output=True, text=True, check=False)
            match = re.search(r"^package: name='([^']+)'", result.stdout, re.M)
            if result.returncode or not match or match.group(1) != expected:
                raise RuntimeError(f"Unexpected package ID for {apk}: {match.group(1) if match else 'unavailable'}")
        for apk in (target, test_apk):
            installed = adb.raw("install", "-r", "-t", str(apk))
            if rc(installed) or "success" not in text(installed).lower().splitlines():
                raise RuntimeError(f"Install failed for {apk}: {text(installed)}")
        run_id = uuid.uuid4().hex
        host["runId"] = run_id
        fixture_remote = f"files/sr-runs/{run_id}/fixture.mp4"
        made = adb.sh("run-as", PKG, "mkdir", "-p", f"files/sr-runs/{run_id}")
        if made[0]:
            raise RuntimeError(f"Could not create private run directory: {text(made)}")
        push_fixture(adb, fixture_remote, fixture)
        device_fixture_sha = device_sha(adb, fixture_remote)
        host["device_fixture_sha256"] = device_fixture_sha
        if device_fixture_sha != fixture_sha:
            raise RuntimeError("Device fixture SHA mismatch")
        command = ["adb", "-s", args.serial, "shell", "am", "instrument", "-w", "-e", "runId", run_id]
        if args.compare_spatial:
            command.extend(["-e", "compareSpatial", "true"])
        command.append(f"{PKG}.test/io.ondeviceai.realtimesr.PlaybackUiProbe")
        try:
            instrument = subprocess.run(command, capture_output=True, text=True, timeout=140, check=False)
            host["instrumentation"] = {"returncode": instrument.returncode, "stdout": instrument.stdout, "stderr": instrument.stderr}
        except subprocess.TimeoutExpired as exc:
            host["instrumentation"] = {"timeout": True, "stdout": text(exc.stdout), "stderr": text(exc.stderr)}
        evidence = args.out / run_id
        evidence.mkdir(parents=True, exist_ok=True)
        base = f"files/sr-ui/{run_id}"
        capture_names = ["result.json", "stage-1.png", "stage-2.png", "stage-3.png"]
        if args.compare_spatial:
            capture_names.append("stage-4.png")
        for name in capture_names:
            try:
                pull(adb, f"{base}/{name}", evidence / name)
            except Exception as exc:
                host.setdefault("pullErrors", {})[name] = str(exc)
        try:
            events = adb.exec_out("run-as", PKG, "cat", f"files/sr-runs/{run_id}/events.jsonl")
            events = getattr(events, "stdout", events)
            if isinstance(events, str):
                events = events.encode()
            (evidence / "events.jsonl").write_bytes(events)
            host["swaps"] = check_events(events, args.compare_spatial)
        except Exception as exc:
            host["swapError"] = str(exc)
        result_path = evidence / "result.json"
        if result_path.exists():
            try:
                result = json.loads(result_path.read_text())
                host["result"] = result
                if result.get("runId") != run_id:
                    raise RuntimeError("Instrumentation result runId mismatch")
                if result.get("success") is not True:
                    raise RuntimeError("Instrumentation result reports failure")
            except Exception as exc:
                host["resultError"] = str(exc)
        capture_count = 4 if args.compare_spatial else 3
        pngs = [evidence / f"stage-{n}.png" for n in range(1, capture_count + 1)]
        if not all(p.is_file() for p in pngs):
            raise RuntimeError("One or more instrumentation PNGs are missing")
        inst = host.get("instrumentation", {})
        if inst.get("returncode") != 0 or "INSTRUMENTATION_CODE: -1" not in inst.get("stdout", ""):
            raise RuntimeError("Instrumentation did not report INSTRUMENTATION_CODE: -1")
        if "swaps" not in host or "resultError" in host or "result" not in host:
            raise RuntimeError("Instrumentation evidence validation failed")
        ffmpeg = shutil.which("ffmpeg")
        if ffmpeg:
            frames = []
            for path in pngs:
                decoded = subprocess.run([ffmpeg, "-v", "error", "-i", str(path), "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"], capture_output=True, check=False)
                if decoded.returncode or not decoded.stdout or len(decoded.stdout) % 4:
                    raise RuntimeError(f"Could not decode {path.name} with ffmpeg")
                frames.append(decoded.stdout)
            if len(set(map(len, frames))) != 1:
                raise RuntimeError("Stage PNG dimensions differ")
            pixels = len(frames[0]) // 4
            pairs = [(0, 1), (0, 2), (1, 2), (0, 3)] if args.compare_spatial else [(0, 1), (1, 2), (0, 2)]
            differing = [sum(frames[a][i:i+3] != frames[b][i:i+3] for i in range(0, len(frames[a]), 4)) for a, b in pairs]
            checksums = [hashlib.sha256(frame).hexdigest() for frame in frames]
            if args.compare_spatial:
                if checksums[0] != checksums[3] or differing[0] == 0 or differing[1] == 0:
                    raise RuntimeError("Expected byte-identical first/last originals and changed Spatial and CuNNy captures")
            elif differing[0] == 0 or differing[1] == 0 or checksums[0] != checksums[2]:
                raise RuntimeError("Expected byte-identical first/last originals and a changed CuNNy capture")
            host["pixels"] = {"sha256": checksums, "differingRgbPixels": differing, "comparisonPairs": pairs, "pixelCount": pixels}
            if args.compare_spatial:
                host["stages"] = ["Original", "Spatial", "CuNNy", "Original"]
        host["success"] = True
    except Exception as exc:
        host["error"] = str(exc)
    finally:
        (args.out / "host.json").write_text(json.dumps(host, indent=2) + "\n")
    print("PASS" if host["success"] else f"FAIL: {host.get('error', 'evidence validation failed')}")
    return 0 if host["success"] else 1


if __name__ == "__main__":
    sys.exit(main())
