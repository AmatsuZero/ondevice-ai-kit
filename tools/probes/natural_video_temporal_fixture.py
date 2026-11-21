#!/usr/bin/env python3
"""Prepare and validate fixed motion/frozen controls from a pinned natural video."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Any


def file_identity(path: Path) -> dict[str, int | str]:
    sha1 = hashlib.sha1()
    sha256 = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            sha1.update(chunk)
            sha256.update(chunk)
            size += len(chunk)
    return {"bytes": size, "sourceSha1": sha1.hexdigest(), "sourceSha256": sha256.hexdigest()}


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _version(program: str) -> str:
    return subprocess.check_output([program, "-version"], text=True).splitlines()[0]


def _metadata(path: Path) -> dict[str, Any]:
    raw = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-count_frames", "-show_streams", "-show_format",
        "-of", "json", str(path),
    ], text=True))
    video = [stream for stream in raw["streams"] if stream.get("codec_type") == "video"]
    audio = [stream for stream in raw["streams"] if stream.get("codec_type") == "audio"]
    if len(video) != 1:
        raise ValueError("expected one video stream")
    stream = video[0]
    return {
        "codec": stream.get("codec_name"),
        "hasBFrames": int(stream.get("has_b_frames", 0)),
        "pixelFormat": stream.get("pix_fmt"),
        "width": stream.get("width"),
        "height": stream.get("height"),
        "fps": stream.get("r_frame_rate"),
        "frames": int(stream.get("nb_read_frames", 0)),
        "durationSeconds": float(stream.get("duration", raw.get("format", {}).get("duration", 0))),
        "audioStreams": len(audio),
        "colorRange": stream.get("color_range"),
        "colorSpace": stream.get("color_space"),
        "colorTransfer": stream.get("color_transfer"),
        "colorPrimaries": stream.get("color_primaries"),
    }


def _decoded_frame_hashes(path: Path) -> list[str]:
    output = subprocess.check_output([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(path),
        "-map", "0:v:0", "-f", "framehash", "-hash", "sha256", "-",
    ], text=True)
    return [line.split(",")[-1].strip() for line in output.splitlines() if line and not line.startswith("#")]


def _expected_source(manifest: dict[str, Any]) -> dict[str, Any]:
    source = manifest.get("source")
    if not isinstance(source, dict):
        raise ValueError("missing source identity")
    required = {"bytes", "sourceSha1", "sourceSha256"}
    if not required.issubset(source):
        raise ValueError("incomplete source identity")
    return {key: source[key] for key in ("bytes", "sourceSha1", "sourceSha256")}


def _assert_metadata(metadata: dict[str, Any]) -> None:
    expected = {
        "codec": "h264", "hasBFrames": 0, "pixelFormat": "yuv420p", "width": 320, "height": 180,
        "fps": "24/1", "frames": 48, "audioStreams": 0,
    }
    for key, value in expected.items():
        if metadata.get(key) != value:
            raise ValueError(f"metadata mismatch: {key}")
    if abs(metadata["durationSeconds"] - 2.0) > 0.001:
        raise ValueError("metadata mismatch: duration")


def _publish(stage: Path, destination: Path) -> None:
    destination.mkdir()
    published: list[Path] = []
    try:
        files = sorted(path for path in stage.iterdir() if path.name != "manifest.json")
        files.append(stage / "manifest.json")
        for source in files:
            target = destination / source.name
            os.link(source, target)
            published.append(target)
    except BaseException:
        for target in published:
            target.unlink(missing_ok=True)
        try:
            destination.rmdir()
        except OSError:
            pass
        raise


def _encode_motion(source: Path, output: Path, start_frame: int, end_frame: int) -> None:
    filters = (
        f"fps=24,trim=start_frame={start_frame}:end_frame={end_frame},setpts=PTS-STARTPTS,"
        "scale=320:180:flags=lanczos,setsar=1,"
        "setparams=range=tv:color_primaries=bt709:color_trc=bt709:colorspace=bt709"
    )
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(source),
        "-map", "0:v:0", "-vf", filters, "-frames:v", "48", "-an", "-c:v", "libx264",
        "-preset", "ultrafast", "-crf", "0", "-bf", "0", "-pix_fmt", "yuv420p", "-color_range", "tv",
        "-colorspace", "bt709", "-color_trc", "bt709", "-color_primaries", "bt709",
        "-movflags", "+faststart", str(output),
    ], check=True)


def _encode_control(motion: Path, output: Path) -> None:
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(motion), "-frames:v", "48", "-an", "-vf",
        "select=eq(n\\,0),loop=loop=47:size=1:start=0,setpts=N/(24*TB),"
        "scale=320:180:flags=lanczos,setsar=1,"
        "setparams=range=tv:color_primaries=bt709:color_trc=bt709:colorspace=bt709",
        "-c:v", "libx264", "-preset", "ultrafast", "-crf", "0", "-bf", "0", "-pix_fmt", "yuv420p",
        "-color_range", "tv", "-colorspace", "bt709", "-color_trc", "bt709",
        "-color_primaries", "bt709", "-movflags", "+faststart", str(output),
    ], check=True)


def prepare_temporal_fixture(source: Path, destination: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    if os.path.lexists(destination):
        raise FileExistsError(destination)
    expected_source = _expected_source(manifest)
    actual_source = file_identity(source)
    if actual_source != expected_source:
        raise ValueError("source identity mismatch")
    windows = manifest.get("windows")
    if not isinstance(windows, list) or len(windows) != 3:
        raise ValueError("expected three motion windows")
    expected_windows = [(0, 2), (2, 4), (4, 6)]
    if [(item.get("start"), item.get("end")) for item in windows] != expected_windows:
        raise ValueError("motion windows mismatch")

    destination.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{destination.name}.", dir=destination.parent))
    try:
        derivatives: list[dict[str, Any]] = []
        for index, window in enumerate(windows):
            motion_id = window["taskId"]
            pair_id = window["pairId"]
            if window.get("role") != "motion" or motion_id != f"motion-{index * 2}-{index * 2 + 2}":
                raise ValueError("motion task identity mismatch")
            motion_name = f"{motion_id}.mp4"
            motion = stage / motion_name
            _encode_motion(source, motion, window["start"] * 24, window["end"] * 24)
            motion_metadata = _metadata(motion)
            _assert_metadata(motion_metadata)
            motion_hashes = _decoded_frame_hashes(motion)
            derivatives.append({
                "taskId": motion_id, "pairId": pair_id, "role": "motion",
                "sourceInterval": [window["start"], window["end"]], "path": motion_name,
                "sha256": _sha256(motion), "bytes": motion.stat().st_size, "metadata": motion_metadata,
                "decodedFirstFrameSha256": motion_hashes[0],
                "decodedUniqueFrameCount": len(set(motion_hashes)),
            })

            control_id = motion_id.replace("motion-", "frozen-")
            control_name = f"{control_id}.mp4"
            control = stage / control_name
            _encode_control(motion, control)
            control_metadata = _metadata(control)
            _assert_metadata(control_metadata)
            control_hashes = _decoded_frame_hashes(control)
            derivatives.append({
                "taskId": control_id, "pairId": pair_id, "role": "frozen-control",
                "sourceInterval": [window["start"], window["end"]], "controlOf": motion_id,
                "path": control_name, "sha256": _sha256(control), "bytes": control.stat().st_size,
                "metadata": control_metadata,
                "decodedFirstFrameSha256": control_hashes[0],
                "decodedUniqueFrameCount": len(set(control_hashes)),
            })

        if file_identity(source) != expected_source:
            raise ValueError("source changed during preparation")
        result = {
            "schemaVersion": 1,
            "source": actual_source,
            "colorBasis": manifest.get("colorBasis", "explicit-output-bt709"),
            "ffmpegVersion": _version("ffmpeg"),
            "ffprobeVersion": _version("ffprobe"),
            "derivatives": derivatives,
        }
        (stage / "manifest.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        _publish(stage, destination)
        return result
    finally:
        shutil.rmtree(stage, ignore_errors=True)


def validate_temporal_manifest(manifest_path: Path, fixture_root: Path) -> dict[str, Any]:
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    derivatives = data.get("derivatives")
    if not isinstance(derivatives, list) or len(derivatives) != 6:
        raise ValueError("expected six derivatives")
    seen: set[str] = set()
    by_pair: dict[str, list[dict[str, Any]]] = {}
    for item in derivatives:
        task_id = item.get("taskId")
        if not isinstance(task_id, str) or task_id in seen:
            raise ValueError("duplicate or missing task ID")
        seen.add(task_id)
        path = fixture_root / item["path"]
        if not path.is_file() or _sha256(path) != item.get("sha256") or path.stat().st_size != item.get("bytes"):
            raise ValueError("derivative identity mismatch")
        metadata = _metadata(path)
        _assert_metadata(metadata)
        if metadata != item.get("metadata"):
            raise ValueError("metadata manifest mismatch")
        frame_hashes = _decoded_frame_hashes(path)
        if (item.get("decodedFirstFrameSha256") != frame_hashes[0]
                or item.get("decodedUniqueFrameCount") != len(set(frame_hashes))):
            raise ValueError("decoded frame identity mismatch")
        by_pair.setdefault(item.get("pairId"), []).append(item)
    if len(by_pair) != 3:
        raise ValueError("pair count mismatch")
    for pair in by_pair.values():
        if len(pair) != 2:
            raise ValueError("pair membership mismatch")
        motion = next((item for item in pair if item.get("role") == "motion"), None)
        control = next((item for item in pair if item.get("role") == "frozen-control"), None)
        if motion is None or control is None or control.get("controlOf") != motion.get("taskId"):
            raise ValueError("pair relationship mismatch")
        if control.get("sourceInterval") != motion.get("sourceInterval"):
            raise ValueError("pair interval mismatch")
        if control.get("decodedUniqueFrameCount") != 1:
            raise ValueError("frozen control contains motion")
        if control.get("decodedFirstFrameSha256") != motion.get("decodedFirstFrameSha256"):
            raise ValueError("frozen control first frame mismatch")
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    prepare = sub.add_parser("prepare")
    prepare.add_argument("source", type=Path)
    prepare.add_argument("destination", type=Path)
    prepare.add_argument("--manifest", required=True, type=Path)
    validate = sub.add_parser("validate")
    validate.add_argument("manifest", type=Path)
    validate.add_argument("--root", required=True, type=Path)
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            data = prepare_temporal_fixture(args.source, args.destination, json.loads(args.manifest.read_text()))
        else:
            data = validate_temporal_manifest(args.manifest, args.root)
    except (OSError, KeyError, TypeError, ValueError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        parser.error(str(error))
    print(json.dumps(data, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
