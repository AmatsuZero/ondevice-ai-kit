#!/usr/bin/env python3
"""Deterministic additive-noise WAV fixture generator.

Stdlib only. Reads an existing manifest + 16k mono16 PCM WAVs, verifies them,
then writes *new* derived fixtures with fixed-seed, zero-mean, gain-controlled
additive synthetic noise. The generated noise is SYNTHETIC ONLY: it is not
real-world noise and not a Chinese-language (or any) acoustic validation. These
are input data fixtures for an additional robustness case on existing
file-capable ASR; they are NOT an ASR accuracy / passing-quality claim.

Schema (mirrors the supplied actual context/manifest.json, schemaVersion 1):
  root: { "schemaVersion": 1, "source": { revision, dataset, license, ... },
          "clips": [ ... ] }   (+ a "derivative" block we add on output)
  each clip:
    { "id": str, "reference": str (nonempty), "wavPath": "audio/<id>.wav",
      "pcmWav": { "sha256": str, "bytes": int, "sampleRate": 16000,
                  "channels": 1, "sampleWidth": 2, "frames": int,
                  "durationSeconds": float } }
  A required "SOURCE-NOTICE.txt" next to manifest.json is carried verbatim
  (its exact bytes are preserved); a separate "DERIVATIVE-NOTICE.txt" is added.

The output manifest keeps clip ids/references/wavPath shape and pcmWav keys
intact (only bytes/sha256/frames/durationSeconds/sampleRate/channels/
sampleWidth refreshed) so the frozen iOS file probe parser
(W3AppleFileApp.swift) keeps working without source changes. Derivative facts
are recorded separately (top-level "derivative") so clip objects are not
restructured.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import random
import shutil
import stat
import struct
import sys
import tempfile
from pathlib import Path

ALGORITHM = "controlled-additive-noise"
ALGORITHM_REVISION = "controlled-additive-noise/1.0"
LINEAGE_NOTE = (
    "Derived by deterministic additive zero-mean Gaussian noise scaled to a "
    "target SNR relative to clean RMS, using a fixed seed. Additive synthetic "
    "noise is NOT real-world noise and NOT Chinese-language validation. Outputs "
    "are input data only; not an ASR accuracy / passing-quality claim."
)

# PCM parameters (fixed by the project's 16k mono16 baseline).
SAMPLE_RATE = 16000
CHANNELS = 1
BITS = 16                 # bits per sample in the WAV fmt chunk
SAMPLE_WIDTH = 2          # BYTES per sample; pcmWav.sampleWidth in context/manifest.json
PCM_MAX = 32767          # int16 positive peak (inclusive)
# Leave one quantization-unit of headroom so clean_q+noise_q can never exceed
# the int16 range after per-sample rounding.
PCM_LIMIT = (PCM_MAX - 1) / 32768.0
SNR_VALID_RANGE = (-60.0, 60.0)  # reject absurd SNR

# Required pcmWav keys for the actual schema (context/manifest.json).
PCM_REQUIRED_KEYS = ("sha256", "bytes", "sampleRate", "channels", "sampleWidth",
                     "frames", "durationSeconds")


class FixtureError(Exception):
    """Raised for any rejected/invalid input or precondition failure."""


# --------------------------------------------------------------------------- #
# Strict JSON loading
# --------------------------------------------------------------------------- #
def _reject_constant(_token: str):
    raise FixtureError(f"non-finite JSON constant not allowed: {_token}")


def _no_duplicate_keys(pairs):
    seen = set()
    for key, _ in pairs:
        if key in seen:
            raise FixtureError(f"duplicate JSON key not allowed: {key!r}")
        seen.add(key)
    return dict(pairs)


def load_manifest_json(path: str, base: str | None = None) -> dict:
    """Load manifest with strict checks: no NaN/Inf, no duplicate keys.

    The file is read through the safe regular-file reader so symlink leaves /
    ancestors and non-regular (FIFO/dir/device) inputs are rejected, and the
    read is non-blocking before any content is consumed.
    """
    if base is None:
        base = os.path.dirname(os.path.abspath(path))
    try:
        raw = _read_input_file(path, base)
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise FixtureError(f"manifest is not valid UTF-8: {exc}") from exc
    try:
        data = json.loads(
            text,
            parse_constant=_reject_constant,
            object_pairs_hook=_no_duplicate_keys,
        )
    except ValueError as exc:
        raise FixtureError(f"invalid manifest JSON: {exc}") from exc
    _reject_nonfinite(data)
    if not isinstance(data, dict):
        raise FixtureError("manifest root must be a JSON object")
    return data


def _reject_nonfinite(obj):
    """Recursively reject any non-finite number (defense in depth)."""
    if isinstance(obj, float):
        if not math.isfinite(obj):
            raise FixtureError("non-finite JSON number not allowed")
    elif isinstance(obj, dict):
        for v in obj.values():
            _reject_nonfinite(v)
    elif isinstance(obj, list):
        for v in obj:
            _reject_nonfinite(v)


# --------------------------------------------------------------------------- #
# Path safety
# --------------------------------------------------------------------------- #
def safe_join(base: str, rel: str) -> str:
    """Join and reject traversal outside base. Does not resolve symlinks."""
    base_abs = os.path.abspath(base)
    target = os.path.abspath(os.path.join(base_abs, rel))
    if target != base_abs and not target.startswith(base_abs + os.sep):
        raise FixtureError(f"path traversal not allowed: {rel!r}")
    return target


def _allowed_macos_alias(link_path: str) -> bool:
    """True only for the two macOS system symlink aliases whose readlink target
    lexically resolves to exactly /private/tmp or /private/var.

    macOS readlink returns a *relative* target ("private/tmp", "private/var"),
    so a lexical join against the link's parent followed by normpath is required;
    an absolute target is normalized directly. We never resolve the full path,
    so an arbitrary symlink cannot be waved through by hiding it behind an alias.
    """
    if link_path not in ("/tmp", "/var"):
        return False
    try:
        target = os.readlink(link_path)
    except OSError:
        return False
    if not os.path.isabs(target):
        resolved = os.path.normpath(os.path.join(os.path.dirname(link_path), target))
    else:
        resolved = os.path.normpath(target)
    return resolved in ("/private/tmp", "/private/var")


def _check_no_symlink_ancestors(path: str, base_abs: str) -> None:
    """Reject symlinked directory components anywhere from the input's parent
    directory up to the filesystem root.

    Every component (including the source_dir itself, which is one of these
    components when the input lives in it) is checked with lstat semantics: an
    arbitrary symlink planted anywhere in the chain is rejected. The only
    exceptions are the macOS /tmp and /var system aliases, verified lexically by
    _allowed_macos_alias. No full-path realpath is used to launder symlinks.
    """
    d = os.path.dirname(os.path.abspath(path))
    while True:
        if os.path.islink(d) and not _allowed_macos_alias(d):
            raise FixtureError(f"symlink ancestor not allowed: {d}")
        parent = os.path.dirname(d)
        if parent == d:  # reached filesystem root
            break
        d = parent


def _read_input_file(path: str, base: str) -> bytes:
    """Open a file safely: reject symlink leaves/ancestors and non-regular
    files (FIFO/device/dir). Uses O_NONBLOCK so a FIFO cannot block the open;
    it is then rejected via fstat before any read. Returns the raw bytes.
    """
    base_abs = os.path.abspath(base)
    if os.path.islink(path):
        raise FixtureError(f"symlink input not allowed: {path}")
    _check_no_symlink_ancestors(path, base_abs)
    # O_NOFOLLOW makes the symlink-leaf rejection atomic (closes the TOCTOU gap
    # left by the islink() pre-check); O_NONBLOCK keeps FIFO opens non-blocking.
    flags = os.O_RDONLY | os.O_NONBLOCK | getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(path, flags)
    except OSError as exc:
        raise FixtureError(f"cannot open input: {path}: {exc}") from exc
    try:
        st = os.fstat(fd)
        mode = st.st_mode
        if not stat.S_ISREG(mode):
            if stat.S_ISFIFO(mode):
                raise FixtureError(f"FIFO input not allowed: {path}")
            if stat.S_ISDIR(mode):
                raise FixtureError(f"directory used as input: {path}")
            if stat.S_ISLNK(mode):
                raise FixtureError(f"symlink input not allowed: {path}")
            raise FixtureError(f"unsafe input (not a regular file): {path}")
        raw = b""
        while True:
            chunk = os.read(fd, 1 << 16)
            if not chunk:
                break
            raw += chunk
    finally:
        os.close(fd)
    return raw


# --------------------------------------------------------------------------- #
# WAV (strict PCM16 mono 16k reader/writer)
# --------------------------------------------------------------------------- #
def parse_wav(raw: bytes, path: str):
    """Parse a canonical PCM16 mono 16k WAV. Returns (samples_int16, frames).

    Rejects: non-RIFF/WAVE, malformed RIFF length, truncated/missing fmt/data,
    duplicate fmt/data chunks, non-PCM / non-mono / non-16k / non-16-bit, and
    inconsistent byteRate/blockAlign. Does not overread past declared sizes.
    """
    n = len(raw)
    if n < 12 or raw[0:4] != b"RIFF" or raw[8:12] != b"WAVE":
        raise FixtureError(f"not a RIFF/WAVE file: {path}")
    riff_size = struct.unpack_from("<I", raw, 4)[0]
    if riff_size != n - 8:
        raise FixtureError(f"malformed RIFF length: {path}")

    pos = 12
    fmt = None
    data_off = None
    data_size = None
    seen_fmt = False
    seen_data = False
    # Consume the WHOLE RIFF region (bytes 12..n). Any trailing bytes that do
    # not form a complete, correctly-sized chunk are rejected rather than
    # silently ignored, so a writer cannot hide extra data past the last chunk.
    while pos < n:
        if pos + 8 > n:
            raise FixtureError(f"trailing bytes / incomplete chunk header: {path}")
        cid = raw[pos:pos + 4]
        csize = struct.unpack_from("<I", raw, pos + 4)[0]
        body = pos + 8
        if body + csize > n:
            raise FixtureError(f"truncated chunk {cid!r}: {path}")
        if cid == b"fmt ":
            if seen_fmt:
                raise FixtureError(f"duplicate fmt chunk: {path}")
            seen_fmt = True
            fmt = raw[body:body + csize]
        elif cid == b"data":
            if seen_data:
                raise FixtureError(f"duplicate data chunk: {path}")
            seen_data = True
            data_off = body
            data_size = csize
        pos = body + csize
        if csize & 1:  # chunks are word-aligned (pad byte must be present)
            if pos >= n:
                raise FixtureError(f"missing pad byte for odd chunk: {path}")
            pos += 1

    if pos != n:
        raise FixtureError(f"incomplete RIFF chunk consumption: {path}")
    if not seen_fmt or data_off is None:
        raise FixtureError(f"missing fmt/data chunk: {path}")
    if len(fmt) < 16:
        raise FixtureError(f"fmt chunk too small: {path}")

    audio_format, channels, sample_rate, byte_rate, block_align, bits = \
        struct.unpack_from("<HHIIHH", fmt, 0)
    if audio_format != 1:
        raise FixtureError(f"not PCM (fmt={audio_format}): {path}")
    if channels != CHANNELS:
        raise FixtureError(f"not mono (channels={channels}): {path}")
    if sample_rate != SAMPLE_RATE:
        raise FixtureError(f"bad sample rate ({sample_rate}): {path}")
    if bits != BITS:
        raise FixtureError(f"not 16-bit (bits={bits}): {path}")

    expected_byte_rate = sample_rate * channels * (bits // 8)
    expected_block_align = channels * (bits // 8)
    if byte_rate != expected_byte_rate:
        raise FixtureError(f"inconsistent fmt byteRate ({byte_rate} != "
                           f"{expected_byte_rate}): {path}")
    if block_align != expected_block_align:
        raise FixtureError(f"inconsistent fmt blockAlign ({block_align} != "
                           f"{expected_block_align}): {path}")

    if data_size & 1:
        raise FixtureError(f"odd data size: {path}")
    if data_off + data_size > n:
        raise FixtureError(f"truncated wav data: {path}")
    frames = data_size // 2
    if frames == 0:
        raise FixtureError(f"empty audio: {path}")
    samples = list(struct.unpack_from("<" + "h" * frames, raw, data_off))
    return samples, frames


def make_wav_bytes(samples) -> bytes:
    """Build a canonical 44-byte-header PCM16 mono 16k WAV from int16 samples.

    fmt header fields (little-endian, 6 values -> <HHIIHH):
      audio_format=1, channels=1, sample_rate=16000,
      byteRate=16000*1*2=32000, blockAlign=1*2=2, bits=16.
    """
    data = struct.pack("<" + "h" * len(samples), *samples)
    byte_rate = SAMPLE_RATE * CHANNELS * (BITS // 8)   # 32000
    block_align = CHANNELS * (BITS // 8)              # 2
    header = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVE"
    # fmt chunk: 4-byte id + 4-byte length(16) + 16 bytes of PCM fields.
    header += b"fmt " + struct.pack("<I", 16) + struct.pack(
        "<HHIIHH", 1, CHANNELS, SAMPLE_RATE, byte_rate, block_align, BITS
    )
    header += b"data" + struct.pack("<I", len(data))
    return header + data


def sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


# --------------------------------------------------------------------------- #
# Strict integer helper (rejects bool, which is a subclass of int)
# --------------------------------------------------------------------------- #
def _require_int(value, name: str, cid: str = ""):
    if isinstance(value, bool) or not isinstance(value, int):
        raise FixtureError(f"clip {cid!r} {name} must be an int (got "
                           f"{type(value).__name__})")
    return value


def _require_pos_int(value, name: str, cid: str = ""):
    v = _require_int(value, name, cid)
    if v <= 0:
        raise FixtureError(f"clip {cid!r} {name} must be positive")
    return v


# --------------------------------------------------------------------------- #
# Noise + mixing
# --------------------------------------------------------------------------- #
def _rms(values) -> float:
    if not values:
        return 0.0
    s = 0.0
    for v in values:
        s += v * v
    return math.sqrt(s / len(values))


def generate_noise_mix(clean_int16, snr_db: float, rng: random.Random):
    """Return (out_int16, common_gain, realized_snr_db, clipping_count).

    clean_int16: list[int] original PCM samples.
    Noise is zero-mean Gaussian, scaled to target SNR vs clean RMS, then a
    single deterministic common gain is applied to (clean+noise) so PCM never
    clips. Realized SNR is measured AFTER quantization against the quantized
    gain-scaled clean reference.
    """
    n = len(clean_int16)
    clean_f = [s / 32768.0 for s in clean_int16]
    clean_rms = _rms(clean_f)
    if clean_rms <= 1e-6:
        raise FixtureError("silent input rejected (clean RMS ~ 0)")

    noise = [rng.gauss(0.0, 1.0) for _ in range(n)]
    mean = sum(noise) / n
    noise = [x - mean for x in noise]
    noise_rms = _rms(noise)
    if noise_rms <= 0:
        raise FixtureError("degenerate noise rejected")
    target_noise_rms = clean_rms / (10.0 ** (snr_db / 20.0))
    scale = target_noise_rms / noise_rms
    noise = [x * scale for x in noise]

    raw = [clean_f[i] + noise[i] for i in range(n)]
    max_abs = max(abs(x) for x in raw)
    gain = 1.0
    if max_abs > PCM_LIMIT:
        gain = PCM_LIMIT / max_abs

    clean_q = [int(round(gain * clean_f[i] * 32768.0)) for i in range(n)]
    noise_q = [int(round(gain * noise[i] * 32768.0)) for i in range(n)]
    out = [clean_q[i] + noise_q[i] for i in range(n)]

    clip_count = 0
    for i in range(n):
        if out[i] < -32768 or out[i] > 32767:
            clip_count += 1
            out[i] = -32768 if out[i] < -32768 else 32767

    realized = 20.0 * math.log10(_rms(clean_q) / _rms(noise_q))
    return out, gain, realized, clip_count


# --------------------------------------------------------------------------- #
# Main generator
# --------------------------------------------------------------------------- #
def generate(source_dir, destination, *, snr_db: float = 20.0, seed: int = 20261108):
    source_dir = str(source_dir)
    destination = str(destination)

    # --- parameter sanity ------------------------------------------------- #
    if isinstance(snr_db, bool) or not isinstance(snr_db, (int, float)) \
            or not math.isfinite(snr_db):
        raise FixtureError("snr_db must be a finite non-boolean number")
    if snr_db < SNR_VALID_RANGE[0] or snr_db > SNR_VALID_RANGE[1]:
        raise FixtureError(f"snr_db out of valid range {SNR_VALID_RANGE}")
    if isinstance(seed, bool) or not isinstance(seed, int) or seed < 0:
        raise FixtureError("seed must be a non-negative int")

    if not os.path.isdir(source_dir):
        raise FixtureError(f"source_dir is not a directory: {source_dir}")
    if os.path.islink(source_dir):
        raise FixtureError(f"source_dir is a symlink: {source_dir}")

    # --- manifest (read safely) ------------------------------------------ #
    manifest_path = os.path.join(source_dir, "manifest.json")
    manifest = load_manifest_json(manifest_path, base=source_dir)

    schema_version = manifest.get("schemaVersion")
    if schema_version != 1:
        raise FixtureError("manifest schemaVersion must be 1")
    source = manifest.get("source")
    if not isinstance(source, dict):
        raise FixtureError("manifest missing source object")
    revision = source.get("revision")
    if not isinstance(revision, str) or not revision.strip():
        raise FixtureError("manifest source.revision is required")

    clips = manifest.get("clips")
    if not isinstance(clips, list) or len(clips) != 3:
        raise FixtureError("manifest must contain exactly 3 clips")

    # --- per-clip validation + derivation (all reads first) ------------- #
    rng = random.Random(seed)
    prepared = []
    seen_ids = []
    seen_wav = []
    for c in clips:
        if not isinstance(c, dict):
            raise FixtureError("each clip must be an object")
        cid = c.get("id")
        if not isinstance(cid, str) or not cid:
            raise FixtureError("clip id must be a nonempty string")
        if "/" in cid or ".." in cid or cid.startswith("/"):
            raise FixtureError(f"unsafe clip id (path escape): {cid!r}")
        if cid in seen_ids:
            raise FixtureError(f"duplicate clip id: {cid!r}")
        ref = c.get("reference")
        if not isinstance(ref, str) or ref.strip() == "":
            raise FixtureError(f"clip {cid!r} reference must be nonempty")
        wav_rel = c.get("wavPath")
        if not isinstance(wav_rel, str) or not wav_rel:
            raise FixtureError(f"clip {cid!r} missing wavPath")
        if wav_rel.startswith("/") or ".." in wav_rel \
                or not wav_rel.endswith(".wav"):
            raise FixtureError(f"unsafe wavPath: {wav_rel!r}")
        if wav_rel in seen_wav:
            raise FixtureError(f"duplicate wavPath: {wav_rel!r}")

        pcm = c.get("pcmWav")
        if not isinstance(pcm, dict):
            raise FixtureError(f"clip {cid!r} missing pcmWav")
        expected_bytes = _require_pos_int(pcm.get("bytes"), "pcmWav.bytes", cid)
        expected_hash = pcm.get("sha256")
        if not isinstance(expected_hash, str) or not expected_hash:
            raise FixtureError(f"clip {cid!r} pcmWav.sha256 required")
        expected_frames = _require_pos_int(pcm.get("frames"), "pcmWav.frames", cid)
        sr = _require_int(pcm.get("sampleRate"), "pcmWav.sampleRate", cid)
        ch = _require_int(pcm.get("channels"), "pcmWav.channels", cid)
        sw = _require_int(pcm.get("sampleWidth"), "pcmWav.sampleWidth", cid)
        if sr != SAMPLE_RATE or ch != CHANNELS or sw != SAMPLE_WIDTH:
            raise FixtureError(f"clip {cid!r} pcmWav PCM metadata mismatch")

        wav_path = safe_join(source_dir, wav_rel)
        raw = _read_input_file(wav_path, source_dir)
        samples, frames = parse_wav(raw, wav_path)

        actual_hash = sha256_bytes(raw)
        if actual_hash != expected_hash:
            raise FixtureError(f"clip {cid!r} hash mismatch (manifest vs file)")
        if len(raw) != expected_bytes:
            raise FixtureError(f"clip {cid!r} bytes mismatch (manifest vs file)")
        if frames != expected_frames:
            raise FixtureError(f"clip {cid!r} frames mismatch (manifest vs file)")

        out, gain, realized, clip_count = generate_noise_mix(samples, snr_db, rng)
        out_bytes = make_wav_bytes(out)
        out_hash = sha256_bytes(out_bytes)
        out_size = len(out_bytes)
        duration = round(frames / SAMPLE_RATE, 3)

        new_pcm = {
            "sha256": out_hash,
            "bytes": out_size,
            "sampleRate": SAMPLE_RATE,
            "channels": CHANNELS,
            "sampleWidth": SAMPLE_WIDTH,
            "frames": frames,
            "durationSeconds": duration,
        }
        out_clip = {
            "id": cid,
            "reference": ref,
            "wavPath": wav_rel,
            "pcmWav": new_pcm,
        }

        prepared.append({
            "id": cid,
            "orig_wav": wav_rel,
            "orig_hash": expected_hash,
            "orig_size": len(raw),
            "out_clip": out_clip,
            "out_bytes": out_bytes,
            "out_hash": out_hash,
            "out_size": out_size,
            "frames": frames,
            "gain": gain,
            "realized": realized,
            "clip_count": clip_count,
        })
        seen_ids.append(cid)
        seen_wav.append(wav_rel)

    # --- write phase (only after everything above succeeded) -------------- #
    if os.path.exists(destination):
        raise FixtureError(f"destination already exists: {destination}")

    parent = os.path.dirname(os.path.abspath(destination)) or "."
    os.makedirs(parent, exist_ok=True)
    staging = tempfile.mkdtemp(dir=parent, prefix=".noisy_staging_")
    try:
        out_manifest = {
            "schemaVersion": 1,
            "source": source,
            "clips": [p["out_clip"] for p in prepared],
            "derivative": {
                "algorithm": ALGORITHM,
                "revision": ALGORITHM_REVISION,
                "seed": seed,
                "targetSnrDb": snr_db,
                "lineage": LINEAGE_NOTE,
                "note": (
                    "Additive synthetic noise is NOT real-world noise and NOT "
                    "Chinese-language validation. Input data only; not an ASR "
                    "accuracy / passing-quality claim."
                ),
                "clips": [
                    {
                        "id": p["id"],
                        "sourceWavPath": p["orig_wav"],
                        "originalHash": p["orig_hash"],
                        "originalSize": p["orig_size"],
                        "derivedHash": p["out_hash"],
                        "derivedSize": p["out_size"],
                        "frames": p["frames"],
                        "commonGain": p["gain"],
                        "targetSnrDb": snr_db,
                        "realizedSnrDb": p["realized"],
                        "clippingCount": p["clip_count"],
                    }
                    for p in prepared
                ],
            },
        }
        with open(os.path.join(staging, "manifest.json"), "w", encoding="utf-8") as fh:
            json.dump(out_manifest, fh, indent=2, ensure_ascii=False)
            fh.write("\n")

        # Write each output WAV to exactly the declared, validated-safe wavPath
        # (kept verbatim in the output manifest), not to a path re-derived from
        # the id, so the manifest's wavPath can never disagree with the file.
        for p in prepared:
            out_path = safe_join(staging, p["orig_wav"])
            parent_dir = os.path.dirname(out_path)
            if parent_dir and parent_dir != staging:
                os.makedirs(parent_dir, exist_ok=True)
            with open(out_path, "wb") as fh:
                fh.write(p["out_bytes"])

        # SOURCE-NOTICE.txt is REQUIRED and carried verbatim (exact bytes).
        notice_path = os.path.join(source_dir, "SOURCE-NOTICE.txt")
        notice_bytes = _read_input_file(notice_path, source_dir)
        with open(os.path.join(staging, "SOURCE-NOTICE.txt"), "wb") as fh:
            fh.write(notice_bytes)

        # separate derivative note
        deriv = (
            "DERIVATIVE NOTICE\n"
            "================\n\n"
            f"Algorithm : {ALGORITHM} ({ALGORITHM_REVISION})\n"
            f"Seed      : {seed}\n"
            f"Target SNR: {snr_db} dB\n\n"
            f"{LINEAGE_NOTE}\n\n"
            "These are derived input data fixtures. They are NOT an ASR accuracy "
            "or passing-quality claim, and are NOT a Chinese-language or "
            "real-world acoustic validation.\n"
        )
        with open(os.path.join(staging, "DERIVATIVE-NOTICE.txt"), "w", encoding="utf-8") as fh:
            fh.write(deriv)

        os.rename(staging, destination)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return destination


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Generate deterministic additive-noise WAV fixtures."
    )
    parser.add_argument("--source", required=True, type=Path, help="source fixture dir")
    parser.add_argument("--destination", required=True, type=Path, help="output dir (must not exist)")
    parser.add_argument("--snr", type=float, default=20.0, help="target SNR in dB")
    parser.add_argument("--seed", type=int, default=20261108, help="fixed random seed")
    args = parser.parse_args(argv)
    generate(args.source, args.destination, snr_db=args.snr, seed=args.seed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
