#!/usr/bin/env python3
"""Measure decoded video fidelity with one score per validated frame pair."""
import argparse
from fractions import Fraction
import json
import math
from pathlib import Path
import re
import subprocess
import tempfile

from video_sr_validate import decoded_sha256, frame_times


def stream_metadata(path: Path) -> dict:
    result = subprocess.check_output([
        "ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
        "-show_entries", "stream=width,height,nb_read_frames,avg_frame_rate,pix_fmt,color_range,color_space,color_transfer,color_primaries",
        "-of", "json", str(path),
    ], text=True)
    streams = json.loads(result)["streams"]
    if len(streams) != 1:
        raise ValueError(f"expected one video stream: {path}")
    return streams[0]


def compare(reference: Path, candidate: Path) -> dict:
    reference, candidate = Path(reference), Path(candidate)
    ref, output = stream_metadata(reference), stream_metadata(candidate)
    count = int(ref["nb_read_frames"])
    if count < 2 or int(output["nb_read_frames"]) != count:
        raise ValueError("frame count must match and contain at least two frames")
    if (ref["width"], ref["height"]) != (output["width"], output["height"]):
        raise ValueError("video dimensions must match")
    fps = Fraction(ref["avg_frame_rate"])
    if fps <= 0 or Fraction(output["avg_frame_rate"]) != fps:
        raise ValueError("frame rates must match")
    times = [frame_times(path) for path in (reference, candidate)]
    interval = float(1 / fps)
    for values in times:
        if len(values) != count or any(right <= left for left, right in zip(values, values[1:])) or any(
            abs(value - values[0] - index * interval) > 0.0011
            for index, value in enumerate(values)
        ):
            raise ValueError("timestamps must describe the same constant frame cadence")

    # Containers can quantize PTS differently (e.g. 1/1000 versus 1/600).
    # Validate cadence first, then pair by frame index without duplicating an end frame.
    normalize = f"settb={fps.denominator}/{fps.numerator},setpts=N,format=yuv420p"
    measured = {}
    with tempfile.TemporaryDirectory(prefix="odai-video-quality-") as directory:
        for metric in ("psnr", "ssim"):
            stats = Path(directory) / f"{metric}.log"
            filters = (
                f"[0:v]{normalize}[reference];[1:v]{normalize}[candidate];"
                f"[reference][candidate]{metric}=stats_file={stats}:shortest=1:repeatlast=0"
            )
            result = subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "info", "-i", str(reference),
                "-i", str(candidate), "-filter_complex", filters,
                "-fps_mode", "passthrough", "-an", "-f", "null", "-",
            ], text=True, capture_output=True, check=True)
            if len(stats.read_text().splitlines()) != count:
                raise ValueError(f"{metric} did not compare exactly {count} frames")
            pattern = r"average:([0-9.]+|inf)" if metric == "psnr" else r"All:([0-9.]+)"
            matches = re.findall(pattern, result.stderr)
            if not matches:
                raise ValueError(f"{metric} summary missing")
            measured[metric] = float(matches[-1])

    color_keys = ("color_range", "color_space", "color_transfer", "color_primaries")
    color_known = all(ref.get(key) not in (None, "unknown", "unspecified") for key in color_keys)
    color_matches = all(ref.get(key) == output.get(key) for key in color_keys)
    return {
        "schemaVersion": 1,
        "ffmpegVersion": subprocess.check_output(["ffmpeg", "-version"], text=True).splitlines()[0],
        "measurement": "decoded-output-fidelity",
        "reference": {"path": str(reference), "decodedSha256": decoded_sha256(reference), "video": ref},
        "candidate": {"path": str(candidate), "decodedSha256": decoded_sha256(candidate), "video": output},
        "comparedFrames": count,
        "framePairing": "validated constant cadence, paired by decoded frame index",
        "comparisonPixelFormat": "yuv420p",
        "colorComparability": "matched-metadata" if color_known and color_matches else "unverified",
        "metrics": {
            "psnrDb": measured["psnr"] if math.isfinite(measured["psnr"]) else None,
            "psnrInfinite": math.isinf(measured["psnr"]),
            "ssim": measured["ssim"],
        },
        "qualityVerdict": "unverified",
        "limitations": "Encoded-output fidelity only; matching source, processing/encoding paths, natural-video suitability and visual quality need separate evidence.",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("candidate", type=Path)
    args = parser.parse_args()
    print(json.dumps(compare(args.reference, args.candidate), indent=2, sort_keys=True, allow_nan=False))
