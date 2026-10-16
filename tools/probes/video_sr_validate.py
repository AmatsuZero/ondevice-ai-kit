#!/usr/bin/env python3
"""Validate deterministic P7 video fixtures without claiming model SR."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

def metadata(path: Path) -> dict:
    output = subprocess.check_output([
        "ffprobe", "-v", "error", "-count_frames", "-show_streams",
        "-show_entries", "stream=codec_type,width,height,nb_read_frames",
        "-of", "json", str(path),
    ], text=True)
    return json.loads(output)


def frame_times(path: Path) -> list[float]:
    output = subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_frames",
        "-show_entries", "frame=best_effort_timestamp_time", "-of", "json", str(path),
    ], text=True)
    return [float(frame["best_effort_timestamp_time"]) for frame in json.loads(output)["frames"]]


def decoded_sha256(path: Path) -> str:
    process = subprocess.Popen([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(path),
        "-map", "0:v:0", "-pix_fmt", "yuv420p", "-f", "rawvideo", "-",
    ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    digest = hashlib.sha256()
    assert process.stdout is not None and process.stderr is not None
    with process.stdout, process.stderr:
        while chunk := process.stdout.read(1024 * 1024):
            digest.update(chunk)
        error = process.stderr.read().decode(errors="replace")
    if process.wait() != 0:
        raise ValueError(f"failed to decode {path}: {error}")
    return digest.hexdigest()


def validate(paths: list[Path], scale: int = 2) -> None:
    if len(paths) != 3:
        raise ValueError("expected reference, input, and bicubic files")
    if scale not in (2, 4):
        raise ValueError("expected 2x or 4x scale")
    expected = ((1280, 720), (1280 // scale, 720 // scale), (1280, 720))
    for path, (width, height) in zip(paths, expected):
        streams = metadata(path)["streams"]
        videos = [stream for stream in streams if stream["codec_type"] == "video"]
        if len(videos) != 1 or any(stream["codec_type"] == "audio" for stream in streams):
            raise ValueError(f"invalid video/audio streams: {path}")
        video = videos[0]
        if (video["width"], video["height"], int(video["nb_read_frames"])) != (width, height, 48):
            raise ValueError(f"invalid dimensions or frame count: {path}")
        times = frame_times(path)
        if len(times) != 48 or abs(times[0]) > 0.001 or any(
            abs((right - left) - 1 / 24) > 0.001 for left, right in zip(times, times[1:])
        ):
            raise ValueError(f"invalid frame timestamps: {path}")
        digest = decoded_sha256(path)
        print(f"{path}: {width}x{height}, 48 frames at 24 fps, no audio, decoded_sha256={digest}")


if __name__ == "__main__":
    arguments = sys.argv[1:]
    scale = 2
    if arguments and arguments[0].startswith("--scale="):
        scale = int(arguments.pop(0).split("=", 1)[1])
    validate([Path(value) for value in arguments], scale=scale)
