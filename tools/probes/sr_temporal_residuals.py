#!/usr/bin/env python3
"""Measure framewise luma residual and change-of-residual against a reference video.

Usage:
    python3 sr_temporal_residuals.py REFERENCE CANDIDATE --output PATH

The parent supplies real input filenames only. The parent must copy the
unchanged tools/probes/video_sr_quality.py and tools/probes/video_sr_validate.py
beside this script; their stream_metadata/frame_times helpers provide the same
frame-count, dimension, frame-rate and strict-cadence checks as the existing
fidelity tool, so prior frame pairing is not weakened.

Both inputs are decoded by ffmpeg to full-range GRAY8 luma (the exact
conversion is recorded in the report) and streamed frame by frame; no raw
files are written and stderr is redirected to a temporary regular file so
ffmpeg can never block on a full error pipe. For each decoded frame index t
the spatial residual e_t(p) = candidate_t(p) - reference_t(p) is summarized
per frame (signed bias, MAE, RMSE), and the temporal residual r_t(p) =
e_t(p) - e_{t-1}(p) for t >= 1 is summarized likewise. Aggregates divide
summed squared residuals by the total sample count (frames * pixels, or
(frames - 1) * pixels), not the average of per-frame RMSE values. The
reference-subtracted temporal residual is not motion-compensated, not
perceptual flicker, and not inherently a proved temporal instability; a
nonzero residual (or a numerically large one) is not a model defect claim,
and no 'significant' conclusion is drawn without a defined pass threshold.

Fail-closed diagnostic: empty or single-frame inputs, mismatched sizes, frame
counts or frame rates, unknown or mismatched color range, non-constant
cadence, truncated or extra decoded bytes, non-zero ffmpeg exit, any ffmpeg
error output, inputs modified during the run, or missing sibling modules abort
without metrics. No quality pass threshold is invented; qualityVerdict stays
"unverified" and productionReady stays false in every report.
"""

from __future__ import annotations

import argparse
import array
import hashlib
import json
import math
import operator
import os
from fractions import Fraction
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

SCHEMA_VERSION = 1
TOOL_NAME = "sr_temporal_residuals"
# Identical constant and comparison logic to tools/probes/video_sr_quality.py.
CADENCE_TOLERANCE_SECONDS = 0.0011
LIMITATIONS = (
    "Framewise full-range GRAY8 luma residual and change-of-residual against the supplied "
    "reference only. The reference-subtracted temporal residual removes the reference's own "
    "temporal change at each pixel; it is not motion-compensated, not perceptual flicker, "
    "not a subjective quality measure, and not inherently a proved temporal instability. "
    "A nonzero residual (or a numerically large one) is not a model defect claim, and no "
    "'significant' instability conclusion is drawn without a defined pass threshold. "
    "Matching color metadata describes container metadata only and does not prove identical "
    "actual transforms or processing paths. Luma-only comparison ignores chroma. PSNR/SSIM "
    "values from prior reports are encoded-output fidelity observations, not model quality "
    "verdicts. No pass threshold is defined."
)


class DiagnosticError(Exception):
    """The diagnostic must fail closed (report the failure, emit no metrics)."""


class DecodeError(DiagnosticError):
    """A decoded luma stream violated the strict byte or exit contract."""


# ---------------------------------------------------------------------------
# Pure metric functions (no I/O; hand-computable on tiny byte frames).
# ---------------------------------------------------------------------------


def frame_residual(reference: bytes, candidate: bytes) -> array.array:
    """Signed per-pixel spatial residual e = candidate - reference (pure)."""
    if len(reference) != len(candidate):
        raise ValueError(
            f"spatial frame byte counts differ: {len(reference)} vs {len(candidate)}")
    return array.array("i", map(operator.sub, candidate, reference))


def temporal_residual(reference_previous: bytes, reference_current: bytes,
                      candidate_previous: bytes, candidate_current: bytes) -> array.array:
    """Signed per-pixel temporal residual r = e_current - e_previous (pure).

    r_t(p) = (candidate_t(p) - reference_t(p))
           - (candidate_{t-1}(p) - reference_{t-1}(p)).
    This subtracts the reference's own temporal change at each pixel; it is
    not motion-compensated, not perceptual flicker, and not a frame-difference
    flicker measure. A nonzero result is not by itself a proved temporal
    instability or a model defect claim.
    """
    lengths = (len(reference_previous), len(reference_current),
               len(candidate_previous), len(candidate_current))
    if len(set(lengths)) != 1:
        raise ValueError(f"temporal frame byte counts differ: {lengths}")
    current = map(operator.sub, candidate_current, reference_current)
    previous = map(operator.sub, candidate_previous, reference_previous)
    return array.array("i", map(operator.sub, current, previous))


def _sums(residuals: array.array) -> tuple:
    """(sum, sum of absolute values, sum of squares) of signed residuals (pure)."""
    return (sum(residuals), sum(map(abs, residuals)),
            sum(map(operator.mul, residuals, residuals)))


def frame_residual_sums(reference: bytes, candidate: bytes) -> tuple:
    """(sum, sum abs, sum squares) of e = candidate - reference (pure)."""
    return _sums(frame_residual(reference, candidate))


def temporal_residual_sums(reference_previous: bytes, reference_current: bytes,
                           candidate_previous: bytes, candidate_current: bytes) -> tuple:
    """(sum, sum abs, sum squares) of r = e_current - e_previous (pure)."""
    return _sums(temporal_residual(reference_previous, reference_current,
                                   candidate_previous, candidate_current))


def _bias_mae_rmse(total: int, absolute: int, squared: int, sample_count: int) -> dict:
    """bias / MAE / RMSE derived from exact integer sums (pure)."""
    if sample_count <= 0:
        raise ValueError("sample count must be positive")
    return {"bias": total / sample_count,
            "mae": absolute / sample_count,
            "rmse": math.sqrt(squared / sample_count)}


def _aggregate(per_frame_sums: list, sample_count: int) -> dict:
    """Aggregate from summed residuals over all samples (never an average of RMSE)."""
    if not per_frame_sums or sample_count <= 0:
        raise ValueError("aggregate requires samples and a positive sample count")
    totals = [sum(column) for column in zip(*per_frame_sums)]
    return _bias_mae_rmse(totals[0], totals[1], totals[2], sample_count)


def compute_metrics(reference_frames, candidate_frames) -> dict:
    """Per-frame and aggregate residual metrics over luma frame iterables (pure).

    Streams the two frame iterables in lockstep (constant memory for
    generators). The spatial section summarizes e_t(p) = candidate_t(p) -
    reference_t(p) per frame; the temporal section summarizes r_t(p) =
    e_t(p) - e_{t-1}(p) for t >= 1 (frames - 1 intervals). Aggregates divide
    summed squared residuals by the total sample count, which is frames *
    pixels (spatial) or (frames - 1) * pixels (temporal). The temporal
    residual is reference-subtracted, not motion-compensated or perceptual
    flicker, and a nonzero value is not a proved temporal instability.
    """
    reference_iter, candidate_iter = iter(reference_frames), iter(candidate_frames)
    spatial = []
    temporal = []
    pixel_count = None
    previous_reference = None
    previous_candidate = None
    frame_count = 0
    while True:
        reference_frame = next(reference_iter, None)
        candidate_frame = next(candidate_iter, None)
        if reference_frame is None and candidate_frame is None:
            break
        if reference_frame is None or candidate_frame is None:
            raise ValueError("reference and candidate frame counts differ")
        if len(reference_frame) != len(candidate_frame):
            raise ValueError("reference and candidate frame byte counts differ")
        if pixel_count is None:
            pixel_count = len(reference_frame)
        elif len(reference_frame) != pixel_count:
            raise ValueError("frame byte counts differ across the stream")
        spatial.append(frame_residual_sums(reference_frame, candidate_frame))
        if previous_reference is not None:
            temporal.append(temporal_residual_sums(previous_reference, reference_frame,
                                                   previous_candidate, candidate_frame))
        previous_reference, previous_candidate = reference_frame, candidate_frame
        frame_count += 1
    if frame_count < 2 or not pixel_count:
        raise ValueError(f"at least two non-empty frames required, got {frame_count}")
    spatial_samples = frame_count * pixel_count
    temporal_samples = (frame_count - 1) * pixel_count
    return {
        "spatial": {
            "aggregate": _aggregate(spatial, spatial_samples),
            "pixelCount": spatial_samples,
            "frames": [{"frame1Based": index + 1,
                        **_bias_mae_rmse(total, absolute, squared, pixel_count)}
                       for index, (total, absolute, squared) in enumerate(spatial)],
        },
        "temporal": {
            "aggregate": _aggregate(temporal, temporal_samples),
            "pixelCount": temporal_samples,
            "frameIntervals": frame_count - 1,
            "frames": [{"frame1Based": index + 2,
                        **_bias_mae_rmse(total, absolute, squared, pixel_count)}
                       for index, (total, absolute, squared) in enumerate(temporal)],
        },
    }


# ---------------------------------------------------------------------------
# Strict luma decoding (ffmpeg subprocess streaming; no raw files on disk).
# ---------------------------------------------------------------------------


def luma_decode_command(path: Path, in_range: str) -> list:
    """Exact ffmpeg argv for full-range GRAY8 luma on stdout (no shell).

    The filter chain has two separate filters: scale converts the input range
    taken from the stream color_range (tv/limited or pc/full) to full output
    range (scale=w=iw:h=ih:in_range=...:out_range=full), and the separate
    format=gray filter sets the GRAY8 pixel format. 'format' is a standalone
    filter, not a scale colon-option, so it is chained with a comma. The chain
    extracts the luma plane only (no chroma, no YCbCr matrix, no transfer
    function) and does no resampling (w=iw:h=ih). -fps_mode passthrough keeps
    exactly one output frame per input frame and -f rawvideo writes plain
    bytes to stdout.
    """
    return [
        "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-xerror",
        "-i", str(path),
        "-map", "0:v:0",
        "-vf", f"scale=w=iw:h=ih:in_range={in_range}:out_range=full,format=gray",
        "-pix_fmt", "gray",
        "-fps_mode", "passthrough",
        "-f", "rawvideo", "-",
    ]


def read_exact_frame(stream, frame_size: int, frame_index: int, label: str):
    """Read exactly frame_size bytes; None at clean EOF; fail on truncation (pure)."""
    data = stream.read(frame_size)
    if len(data) == frame_size:
        return data
    if len(data) == 0:
        return None
    raise DecodeError(f"{label}: truncated frame {frame_index}: "
                      f"read {len(data)} of {frame_size} bytes")


def require_no_extra_bytes(stream, label: str) -> None:
    """Fail closed when the stream has any bytes after the expected frames (pure)."""
    if stream.read(1):
        raise DecodeError(f"{label}: extra bytes after the expected frame count")


class LumaDecoder:
    """Streams full-range GRAY8 luma frames from one input through ffmpeg.

    stderr is redirected to a temporary regular file (never a pipe) so ffmpeg
    can never block on error output while stdout is consumed. The child is
    always joined: close() terminates a still-running process and waits for
    it. No task-duration timeout is imposed anywhere.
    """

    def __init__(self, path: Path, label: str, width: int, height: int, in_range: str):
        self.label = label
        self.frame_size = width * height
        self.frames_read = 0
        self.bytes_read = 0
        self.sha256 = hashlib.sha256()
        self._stderr_file = tempfile.TemporaryFile(mode="w+b")
        try:
            self._process = subprocess.Popen(
                luma_decode_command(path, in_range),
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=self._stderr_file,
                shell=False,
            )
        except OSError as error:
            self._stderr_file.close()
            raise DiagnosticError(f"{label}: cannot start ffmpeg: {error}") from error
        except BaseException:
            self._stderr_file.close()
            raise

    def __enter__(self):
        return self

    def __exit__(self, *_exception) -> None:
        self.close()

    def read_frame(self):
        assert self._process.stdout is not None
        frame = read_exact_frame(self._process.stdout, self.frame_size,
                                 self.frames_read, self.label)
        if frame is None:
            return None
        self.frames_read += 1
        self.bytes_read += len(frame)
        self.sha256.update(frame)
        return frame

    def frames(self):
        while True:
            frame = self.read_frame()
            if frame is None:
                return
            yield frame

    def finish(self, expected_frames: int) -> None:
        """Fail closed unless the stream ended exactly as declared and ffmpeg exited 0."""
        if self.frames_read != expected_frames:
            raise DecodeError(f"{self.label}: decoded {self.frames_read} frames, "
                              f"expected {expected_frames}")
        if self.bytes_read != expected_frames * self.frame_size:
            raise DecodeError(f"{self.label}: decoded {self.bytes_read} bytes, "
                              f"expected {expected_frames * self.frame_size}")
        require_no_extra_bytes(self._process.stdout, self.label)
        code = self._process.wait()
        self._process.stdout.close()
        stderr_text = self._stderr_text()
        if code != 0:
            raise DecodeError(f"{self.label}: ffmpeg exited with {code}: {stderr_text.strip()}")
        if stderr_text.strip():
            raise DecodeError(f"{self.label}: ffmpeg reported errors: {stderr_text.strip()}")

    def _stderr_text(self) -> str:
        self._stderr_file.seek(0)
        return self._stderr_file.read().decode("utf-8", errors="replace")

    def close(self) -> None:
        try:
            if self._process.stdout is not None:
                self._process.stdout.close()
        finally:
            if self._process.poll() is None:
                self._process.terminate()
            self._process.wait()
            self._stderr_file.close()


# ---------------------------------------------------------------------------
# Parent-supplied unchanged helpers, input identity and pairing contract.
# ---------------------------------------------------------------------------


def load_sibling_functions() -> tuple:
    """Import the parent-supplied unchanged sibling tools (never modified here)."""
    try:
        from video_sr_quality import stream_metadata
        from video_sr_validate import decoded_sha256, frame_times
    except ImportError as error:
        raise DiagnosticError(
            "video_sr_quality.py and video_sr_validate.py must be copied UNCHANGED beside "
            f"sr_temporal_residuals.py before running: {error}") from error
    return stream_metadata, frame_times, decoded_sha256


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _input_identity(path: Path, label: str) -> dict:
    """Stat and hash one supplied input; only real, non-empty regular files pass."""
    try:
        info = os.stat(path)
    except OSError as error:
        raise DiagnosticError(f"{label} input is not readable: {path}: {error}") from error
    if not stat.S_ISREG(info.st_mode):
        raise DiagnosticError(f"{label} input is not a regular file: {path}")
    if info.st_size <= 0:
        raise DiagnosticError(f"{label} input is empty: {path}")
    return {"path": str(path), "sizeBytes": info.st_size, "inputSha256": _sha256_file(path)}


def _validated_stream_pair(reference_meta: dict, candidate_meta: dict,
                           reference_times: list, candidate_times: list) -> dict:
    """Frame pairing contract mirrored from tools/probes/video_sr_quality.py (not weakened)."""
    count = int(reference_meta["nb_read_frames"])
    if count < 2 or int(candidate_meta["nb_read_frames"]) != count:
        raise DiagnosticError("frame count must match and contain at least two frames")
    width, height = int(reference_meta["width"]), int(reference_meta["height"])
    if (width, height) != (int(candidate_meta["width"]), int(candidate_meta["height"])):
        raise DiagnosticError("video dimensions must match")
    if width <= 0 or height <= 0:
        raise DiagnosticError("video dimensions must be positive")
    fps = Fraction(reference_meta["avg_frame_rate"])
    if fps <= 0 or Fraction(candidate_meta["avg_frame_rate"]) != fps:
        raise DiagnosticError("frame rates must match")
    interval = float(1 / fps)
    for values in (reference_times, candidate_times):
        if len(values) != count or any(
                right <= left for left, right in zip(values, values[1:])) or any(
                abs(value - values[0] - index * interval) > CADENCE_TOLERANCE_SECONDS
                for index, value in enumerate(values)):
            raise DiagnosticError("timestamps must describe the same constant frame cadence")
    reference_range = reference_meta.get("color_range")
    if reference_range not in ("tv", "pc") or \
            candidate_meta.get("color_range") != reference_range:
        raise DiagnosticError(
            "color_range must be a known identical value (tv or pc) on both inputs so the "
            "luma decode contract can be stated exactly; reference="
            f"{reference_range!r}, candidate={candidate_meta.get('color_range')!r}")
    color_keys = ("color_range", "color_space", "color_transfer", "color_primaries")
    color_known = all(reference_meta.get(key) not in (None, "unknown", "unspecified")
                      for key in color_keys)
    color_matches = all(reference_meta.get(key) == candidate_meta.get(key)
                        for key in color_keys)
    return {
        "count": count,
        "width": width,
        "height": height,
        "inRange": reference_range,
        "colorComparability": "matched-metadata" if color_known and color_matches
        else "unverified",
    }


# ---------------------------------------------------------------------------
# Diagnostic orchestration.
# ---------------------------------------------------------------------------


def _ffmpeg_version() -> str:
    try:
        return subprocess.check_output(["ffmpeg", "-version"], text=True).splitlines()[0]
    except (OSError, subprocess.SubprocessError, IndexError) as error:
        raise DiagnosticError(f"ffmpeg is not runnable on PATH: {error}") from error


def run_diagnostic(reference: Path, candidate: Path) -> dict:
    """Full fail-closed diagnostic; returns the success report payload."""
    stream_metadata, frame_times, decoded_sha256 = load_sibling_functions()
    inputs = {label: _input_identity(path, label)
              for label, path in (("reference", reference), ("candidate", candidate))}
    ffmpeg_version = _ffmpeg_version()
    try:
        reference_meta = stream_metadata(reference)
        candidate_meta = stream_metadata(candidate)
        reference_times = frame_times(reference)
        candidate_times = frame_times(candidate)
    except OSError as error:
        raise DiagnosticError(f"ffprobe is not runnable: {error}") from error
    contract = _validated_stream_pair(reference_meta, candidate_meta,
                                      reference_times, candidate_times)
    count = contract["count"]
    try:
        inputs["reference"]["decodedSha256"] = decoded_sha256(reference)
        inputs["candidate"]["decodedSha256"] = decoded_sha256(candidate)
    except OSError as error:
        raise DiagnosticError(f"ffmpeg is not runnable: {error}") from error
    with LumaDecoder(reference, "reference", contract["width"], contract["height"],
                     contract["inRange"]) as reference_decoder:
        with LumaDecoder(candidate, "candidate", contract["width"], contract["height"],
                         contract["inRange"]) as candidate_decoder:
            metrics = compute_metrics(reference_decoder.frames(),
                                      candidate_decoder.frames())
            reference_decoder.finish(count)
            candidate_decoder.finish(count)
            inputs["reference"]["decodedLumaSha256"] = reference_decoder.sha256.hexdigest()
            inputs["candidate"]["decodedLumaSha256"] = candidate_decoder.sha256.hexdigest()
    for label, path in (("reference", reference), ("candidate", candidate)):
        after = _input_identity(path, label)
        if (after["sizeBytes"], after["inputSha256"]) != \
                (inputs[label]["sizeBytes"], inputs[label]["inputSha256"]):
            raise DiagnosticError(f"{label} input changed during the diagnostic: {path}")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "tool": TOOL_NAME,
        "status": "ok",
        "error": None,
        "measurement": "framewise-full-range-gray8-luma-residual-vs-reference",
        "ffmpegVersion": ffmpeg_version,
        "reference": {**inputs["reference"], "video": reference_meta},
        "candidate": {**inputs["candidate"], "video": candidate_meta},
        "inputUnchangedVerified": True,
        "framePairing": ("validated constant cadence, paired by decoded frame index; the "
                         "temporal residual pairs consecutive decoded frames"),
        "colorComparability": contract["colorComparability"],
        "decode": {
            "pixelFormat": "gray8",
            "colorRange": "full",
            "inRange": contract["inRange"],
            "frameSizeBytes": contract["width"] * contract["height"],
            "commandTemplate": " ".join(luma_decode_command(Path("<input>"), "<tv|pc>")),
            "transformation": (
                "ffmpeg swscale extracts the luma plane only (no chroma, no YCbCr-to-RGB "
                "matrix, no transfer-function transform) and converts the range declared "
                "by the stream color_range to full range via the scale filter "
                "(scale=w=iw:h=ih:in_range=<tv|pc>:out_range=full) with no resampling "
                "(w=iw:h=ih), then the separate format=gray filter sets GRAY8 ('format' "
                "is a standalone filter chained with a comma, not a scale option); "
                "rawvideo bytes stream to stdout and are never written to disk"
            ),
            "limitations": LIMITATIONS,
        },
        "comparedFrames": count,
        "metricDefinitions": {
            "spatialResidual":
                "e_t(p) = candidate_t(p) - reference_t(p) per pixel p in frame t",
            "temporalResidual": (
                "r_t(p) = e_t(p) - e_{t-1}(p) for t >= 1; subtracts the reference's own "
                "temporal change; the reference-subtracted residual is not "
                "motion-compensated, not perceptual flicker, and not inherently a proved "
                "temporal instability; a nonzero or numerically large value is not a model "
                "defect claim and no 'significant' conclusion is drawn without a defined "
                "threshold"
            ),
            "aggregate": (
                "bias = sum residual / sample count; MAE = sum |residual| / sample count; "
                "RMSE = sqrt(sum residual^2 / sample count); the sample count is frames * "
                "pixels for spatial and (frames - 1) * pixels for temporal metrics, so "
                "aggregate RMSE is never an average of per-frame RMSE values"
            ),
        },
        "metrics": metrics,
        "qualityVerdict": "unverified",
        "productionReady": False,
        "limitations": LIMITATIONS,
    }


# ---------------------------------------------------------------------------
# Output destination and CLI.
# ---------------------------------------------------------------------------


def _failure_payload(reference: Path, candidate: Path, error: BaseException) -> dict:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "tool": TOOL_NAME,
        "status": "failed",
        "error": f"{type(error).__name__}: {error}",
        "referencePath": str(reference),
        "candidatePath": str(candidate),
        "metrics": None,
        "qualityVerdict": "unverified",
        "productionReady": False,
        "limitations": LIMITATIONS,
    }


def _validate_output_destination(path: Path) -> None:
    if os.path.lexists(path):
        raise DiagnosticError(
            f"output path already exists or is a symlink (never overwritten): {path}")
    if not path.parent.is_dir():
        raise DiagnosticError(f"output parent directory does not exist: {path.parent}")


def write_json_exclusive(path: Path, payload: dict) -> None:
    """Serialize fully, then create the destination exclusively; never overwrite, no symlinks."""
    data = json.dumps(payload, indent=2, sort_keys=True, allow_nan=False).encode("utf-8")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o644)
    try:
        view = memoryview(data)
        while view:
            written = os.write(descriptor, view)
            if written <= 0:
                raise OSError("short write to output")
            view = view[written:]
    finally:
        os.close(descriptor)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("reference", type=Path,
                        help="reference video (real filename supplied by the parent)")
    parser.add_argument("candidate", type=Path,
                        help="candidate video (real filename supplied by the parent)")
    parser.add_argument("--output", type=Path, required=True,
                        help="diagnostic JSON destination; created exclusively, never "
                             "overwritten, no symlinks")
    arguments = parser.parse_args(argv)
    try:
        _validate_output_destination(arguments.output)
    except DiagnosticError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    try:
        payload = run_diagnostic(arguments.reference, arguments.candidate)
        status = "ok"
    except Exception as error:  # fail closed: every failure becomes a machine-readable record
        payload = _failure_payload(arguments.reference, arguments.candidate, error)
        status = "failed"
    try:
        write_json_exclusive(arguments.output, payload)
    except (OSError, ValueError) as error:
        print(f"error: cannot write {arguments.output}: {error}", file=sys.stderr)
        return 1
    print(f"{status} {arguments.output}")
    return 0 if status == "ok" else 1


if __name__ == "__main__":
    sys.exit(main())
