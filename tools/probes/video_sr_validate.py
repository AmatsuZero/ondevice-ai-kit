#!/usr/bin/env python3
"""Validate deterministic P7 video fixtures without claiming model SR."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

EXPECTED = ((1280, 720), (640, 360), (1280, 720))


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


def validate(paths: list[Path]) -> None:
    if len(paths) != 3:
        raise ValueError("expected reference, input, and bicubic files")
    for path, (width, height) in zip(paths, EXPECTED):
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
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        print(f"{path}: {width}x{height}, 48 frames at 24 fps, no audio, sha256={digest}")


if __name__ == "__main__":
    validate([Path(value) for value in sys.argv[1:]])
