#!/usr/bin/env python3
"""Bounded synthetic tests for prepare_noisy_fixture.

Synthetic fixtures only. Tests do NOT re-mirror the generator's internal SNR
math: the SNR check independently recomputes SNR from the source clean audio
and the output audio using only public I/O plus the honestly-reported common
gain. No real audio, network, device, or production code is touched.

Input WAV fixtures are built INDEPENDENTLY via the stdlib `wave` module (not by
the module under test's make_wav_bytes) so a shared-writer bug cannot hide
behind the test's own generator. Output WAVs are verified by opening them with
the independent stdlib `wave` reader (not the module's own parse_wav), checking
the canonical 44-byte header and the fmt chunk's 4-byte length == 16.

Corrections in this file (W3 final format correction):
  * pcmWav.sampleWidth is BYTES (2); the WAV bits field stays 16.
  * struct-level WAV crafters convert float samples to int16 and emit the
    correct RIFF size (WAVE tag counted), so parse_wav reaches the intended
    branch instead of failing on struct/float or bogus length.
  * metadata-injection tests build a fully valid source (independent stdlib
    WAVs at safe paths) first, THEN mutate only the manifest, so no invalid
    path (e.g. audio/a/b.wav) is ever constructed during setup.
  * duplicate-id case uses DISTINCT valid wavPaths so the duplicate-id check
    is exercised independently of the duplicate-wavPath check.
  * all file reads/writes use context managers (no leaked descriptors).
"""

import hashlib
import json
import math
import os
import pathlib
import shutil
import struct
import sys
import tempfile
import unittest
import wave

# Make the tools root importable so the sibling package `probes` can be
# resolved without PYTHONPATH or a fixed working directory.
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from probes.prepare_noisy_fixture import (
    BITS,
    CHANNELS,
    SAMPLE_RATE,
    SAMPLE_WIDTH,
    FixtureError,
    generate,
    parse_wav,
)


# --------------------------------------------------------------------------- #
# Small I/O helpers (context-managed; avoid leaking file descriptors).
# --------------------------------------------------------------------------- #
def _read_bytes(path):
    with open(path, "rb") as fh:
        return fh.read()


def _write_bytes(path, data):
    with open(path, "wb") as fh:
        fh.write(data)


def _write_text(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def _read_json(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def _write_json(path, obj):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh)


def _sha(path):
    return hashlib.sha256(_read_bytes(path)).hexdigest()


class PrepareNoisyFixtureTest(unittest.TestCase):
    # ------------------------------------------------------------------ utils
    @staticmethod
    def _as_int16(floats):
        """Clamp float signal to the int16 range (struct 'h' needs ints)."""
        return [max(-32768, min(32767, int(round(s * 32767)))) for s in floats]

    def _sine(self, amp, freq=440.0, n=16000):
        return [amp * math.sin(2.0 * math.pi * freq * k / 16000.0) for k in range(n)]

    def _write_wav(self, path, floats):
        """Build a WAV using the stdlib wave module (independent of the unit
        under test) and return the int16 sample list."""
        samples = self._as_int16(floats)
        with wave.open(path, "wb") as w:
            w.setnchannels(CHANNELS)
            w.setsampwidth(SAMPLE_WIDTH)   # bytes per sample = 2
            w.setframerate(SAMPLE_RATE)
            w.writeframes(struct.pack("<" + "h" * len(samples), *samples))
        return samples

    def _read_wav_samples(self, path):
        """Read int16 samples via the independent stdlib wave reader."""
        with wave.open(path, "rb") as w:
            raw = w.readframes(w.getnframes())
        return list(struct.unpack("<" + "h" * (len(raw) // 2), raw))

    def _build_source(self, signals, refs=None, ids=None, notice=True):
        """Build a fully valid source tree: stdlib WAVs at audio/<id>.wav and a
        manifest whose pcmWav metadata (hash/bytes/frames/sampleWidth/...) is
        consistent. Invalid-metadata tests mutate this manifest AFTERWARD."""
        src = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, src, ignore_errors=True)
        audio = os.path.join(src, "audio")
        os.makedirs(audio)
        clips = []
        for i, sig in enumerate(signals):
            cid = (ids or [f"clip{i}" for _ in signals])[i]
            wpath = f"audio/{cid}.wav"
            apath = os.path.join(src, wpath)
            samples = self._write_wav(apath, sig)
            raw = _read_bytes(apath)
            frames = len(samples)
            clips.append({
                "id": cid,
                "reference": (refs[i] if refs else f"reference text number {i}"),
                "wavPath": wpath,
                "pcmWav": {
                    "sha256": _sha(apath),
                    "bytes": len(raw),
                    "sampleRate": SAMPLE_RATE,
                    "channels": CHANNELS,
                    "sampleWidth": SAMPLE_WIDTH,   # 2 bytes (NOT bits=16)
                    "frames": frames,
                    "durationSeconds": round(frames / SAMPLE_RATE, 3),
                },
            })
        manifest = {
            "schemaVersion": 1,
            "source": {
                "dataset": "hf-internal-testing/librispeech_asr_dummy",
                "datasetUrl": "https://huggingface.co/datasets/hf-internal-testing/librispeech_asr_dummy",
                "revision": "5be91486e11a2d616f4ec5db8d3fd248585ac07a",
                "config": "clean",
                "split": "validation",
                "attribution": "LibriSpeech ASR corpus, as distributed by hf-internal-testing/librispeech_asr_dummy.",
                "license": "CC BY 4.0",
                "licenseUrl": "https://creativecommons.org/licenses/by/4.0/",
            },
            "clips": clips,
        }
        _write_json(os.path.join(src, "manifest.json"), manifest)
        if notice:
            _write_text(os.path.join(src, "SOURCE-NOTICE.txt"),
                        "SOURCE NOTICE verbatim content.\n")
        return src

    def _out_dir(self):
        parent = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, parent, ignore_errors=True)
        return os.path.join(parent, "out")

    def _run(self, src, dest, **kw):
        return generate(src, dest, **kw)

    def _expect_reject(self, src):
        dest = self._out_dir()
        with self.assertRaises(FixtureError):
            self._run(src, dest)
        self.assertFalse(os.path.exists(dest))  # no partial outputs
        return dest

    # ---- independent WAV crafters (struct-level, not make_wav_bytes) ------- #
    # These build raw WAV bytes parsed directly by parse_wav. They use int16
    # samples and a RIFF size that counts the 4-byte "WAVE" tag (riff_size =
    # 4 + len(body) = n - 8), so parse_wav reaches the branch under test.
    def _craft_wav(self, samples, byte_rate=None, block_align=None, dup_fmt=False):
        samples = self._as_int16(samples)
        data = struct.pack("<" + "h" * len(samples), *samples)
        br = SAMPLE_RATE * CHANNELS * (BITS // 8) if byte_rate is None else byte_rate
        ba = CHANNELS * (BITS // 8) if block_align is None else block_align
        fmt = struct.pack("<HHIIHH", 1, CHANNELS, SAMPLE_RATE, br, ba, BITS)
        body = b"fmt " + struct.pack("<I", len(fmt)) + fmt
        if dup_fmt:
            body += b"fmt " + struct.pack("<I", len(fmt)) + fmt
        body += b"data" + struct.pack("<I", len(data)) + data
        return b"RIFF" + struct.pack("<I", 4 + len(body)) + b"WAVE" + body

    def _craft_wav_with_extra_chunk(self, samples, extra=b"LIST", extra_body=b"abcd"):
        samples = self._as_int16(samples)
        data = struct.pack("<" + "h" * len(samples), *samples)
        byte_rate = SAMPLE_RATE * CHANNELS * (BITS // 8)
        block_align = CHANNELS * (BITS // 8)
        fmt = struct.pack("<HHIIHH", 1, CHANNELS, SAMPLE_RATE, byte_rate,
                          block_align, BITS)
        extra_full = extra + struct.pack("<I", len(extra_body)) + extra_body
        if len(extra_body) & 1:
            extra_full += b"\x00"
        body = b"fmt " + struct.pack("<I", len(fmt)) + fmt
        body += extra_full
        body += b"data" + struct.pack("<I", len(data)) + data
        return b"RIFF" + struct.pack("<I", 4 + len(body)) + b"WAVE" + body

    # ----------------------------------------------------------------- tests
    def test_byte_deterministic(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25, 330), self._sine(0.35)])
        d1, d2 = self._out_dir(), self._out_dir()
        self._run(src, d1)
        self._run(src, d2)
        self.assertEqual(
            _read_bytes(os.path.join(d1, "manifest.json")),
            _read_bytes(os.path.join(d2, "manifest.json")),
        )
        for i in range(3):
            self.assertEqual(
                _read_bytes(os.path.join(d1, "audio", f"clip{i}.wav")),
                _read_bytes(os.path.join(d2, "audio", f"clip{i}.wav")),
            )

    def test_source_immutable(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        before = {
            f: _sha(os.path.join(src, f))
            for f in ("manifest.json", "audio/clip0.wav", "audio/clip1.wav",
                      "audio/clip2.wav", "SOURCE-NOTICE.txt")
        }
        dest = self._out_dir()
        self._run(src, dest)
        for f, h in before.items():
            self.assertEqual(_sha(os.path.join(src, f)), h)

    def test_snr_near_target(self):
        # Independent decomposition: read both WAVs with stdlib wave (NOT the
        # module's parse_wav); recompute SNR from clean_q + residual noise.
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        dest = self._out_dir()
        self._run(src, dest)
        man = _read_json(os.path.join(dest, "manifest.json"))
        for i in range(3):
            cid = man["clips"][i]["id"]
            ssamples = self._read_wav_samples(os.path.join(src, "audio", f"{cid}.wav"))
            osamples = self._read_wav_samples(os.path.join(dest, "audio", f"{cid}.wav"))
            gain = man["derivative"]["clips"][i]["commonGain"]
            clean_q = [int(round(gain * s)) for s in ssamples]
            noise = [o - c for o, c in zip(osamples, clean_q)]

            def rms(vals):
                return math.sqrt(sum(v * v for v in vals) / len(vals)) / 32768.0

            snr = 20.0 * math.log10(rms(clean_q) / rms(noise))
            self.assertAlmostEqual(snr, 20.0, delta=0.2)
            self.assertEqual(man["derivative"]["clips"][i]["clippingCount"], 0)

    def test_loud_input_no_clipping(self):
        src = self._build_source([self._sine(0.98), self._sine(0.95, 330), self._sine(0.99, 220)])
        dest = self._out_dir()
        self._run(src, dest)
        man = _read_json(os.path.join(dest, "manifest.json"))
        for i in range(3):
            cid = man["clips"][i]["id"]
            osamples = self._read_wav_samples(os.path.join(dest, "audio", f"{cid}.wav"))
            self.assertTrue(all(-32768 <= x <= 32767 for x in osamples))
            self.assertEqual(man["derivative"]["clips"][i]["clippingCount"], 0)
            self.assertAlmostEqual(
                man["derivative"]["clips"][i]["realizedSnrDb"], 20.0, delta=0.2
            )

    def test_output_bytes_changed(self):
        """Output WAV bytes must actually differ from the input WAV bytes."""
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        dest = self._out_dir()
        self._run(src, dest)
        for i in range(3):
            cid = f"clip{i}"
            self.assertNotEqual(
                _read_bytes(os.path.join(src, "audio", f"{cid}.wav")),
                _read_bytes(os.path.join(dest, "audio", f"{cid}.wav")),
            )

    def test_output_schema_compatible(self):
        """Output manifest must match the actual parser's expected schema."""
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        dest = self._out_dir()
        self._run(src, dest)
        man = _read_json(os.path.join(dest, "manifest.json"))
        self.assertEqual(man["schemaVersion"], 1)
        self.assertEqual(man["source"]["revision"],
                         "5be91486e11a2d616f4ec5db8d3fd248585ac07a")
        self.assertEqual(len(man["clips"]), 3)
        c = man["clips"][0]
        self.assertEqual(c["id"], "clip0")
        self.assertEqual(c["reference"], "reference text number 0")
        self.assertEqual(c["wavPath"], "audio/clip0.wav")
        pcm = c["pcmWav"]
        for k in ("sha256", "bytes", "sampleRate", "channels", "sampleWidth",
                  "frames", "durationSeconds"):
            self.assertIn(k, pcm)
        self.assertEqual(pcm["sampleRate"], SAMPLE_RATE)
        self.assertEqual(pcm["channels"], CHANNELS)
        self.assertEqual(pcm["sampleWidth"], SAMPLE_WIDTH)  # 2 bytes, WAV bits=16
        self.assertEqual(pcm["sampleWidth"], 2)
        self.assertEqual(pcm["frames"], 16000)
        self.assertAlmostEqual(pcm["durationSeconds"], 1.0, places=3)
        self.assertIn("derivative", man)
        self.assertTrue(os.path.isfile(os.path.join(dest, "SOURCE-NOTICE.txt")))
        self.assertTrue(os.path.isfile(os.path.join(dest, "DERIVATIVE-NOTICE.txt")))
        # SOURCE-NOTICE preserved byte-for-byte.
        self.assertEqual(
            _read_bytes(os.path.join(src, "SOURCE-NOTICE.txt")),
            _read_bytes(os.path.join(dest, "SOURCE-NOTICE.txt")),
        )

    def test_output_wav_stdlib_metadata_header(self):
        """Independent stdlib wave.open on OUTPUT (not the module's parse_wav):
        verify PCM metadata, canonical 44-byte header, fmt chunk length == 16,
        RIFF size, and that manifest bytes/sha256 describe the real file."""
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        dest = self._out_dir()
        self._run(src, dest)
        man = _read_json(os.path.join(dest, "manifest.json"))
        for c in man["clips"]:
            path = os.path.join(dest, c["wavPath"])
            with wave.open(path, "rb") as w:
                self.assertEqual(w.getnchannels(), CHANNELS)
                self.assertEqual(w.getsampwidth(), SAMPLE_WIDTH)   # 2 bytes
                self.assertEqual(w.getframerate(), SAMPLE_RATE)
                self.assertEqual(w.getcomptype(), "NONE")
                self.assertEqual(w.getnframes(), c["pcmWav"]["frames"])
            raw = _read_bytes(path)
            self.assertEqual(raw[0:4], b"RIFF")
            self.assertEqual(raw[8:12], b"WAVE")
            self.assertEqual(raw[12:16], b"fmt ")
            self.assertEqual(struct.unpack_from("<I", raw, 16)[0], 16)  # fmt len
            self.assertEqual(raw[36:40], b"data")
            self.assertEqual(struct.unpack_from("<I", raw, 4)[0], len(raw) - 8)
            self.assertEqual(c["pcmWav"]["bytes"], len(raw))
            self.assertEqual(c["pcmWav"]["sha256"], _sha(path))

    def test_output_written_to_declared_wavpath(self):
        """Output WAVs are written to exactly the declared (safe) wavPath, not
        to a path re-derived from the id -> manifest and files never disagree."""
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        for i in range(3):
            c = man["clips"][i]
            data = _read_bytes(os.path.join(src, c["wavPath"]))
            alt = f"audio/alt{i}.wav"
            _write_bytes(os.path.join(src, alt), data)
            c["wavPath"] = alt
        _write_json(os.path.join(src, "manifest.json"), man)
        dest = self._out_dir()
        self._run(src, dest)
        outman = _read_json(os.path.join(dest, "manifest.json"))
        for i in range(3):
            self.assertEqual(outman["clips"][i]["wavPath"], f"audio/alt{i}.wav")
            self.assertTrue(os.path.isfile(os.path.join(dest, "audio", f"alt{i}.wav")))
            self.assertFalse(os.path.exists(os.path.join(dest, "audio", f"clip{i}.wav")))

    # --------------------------------------------------- metadata / header
    def test_legal_extra_riff_chunk(self):
        """A legal extra RIFF chunk before data must be accepted (no overread)."""
        samples = self._sine(0.3, n=2000)
        wav = self._craft_wav_with_extra_chunk(samples)
        got, frames = parse_wav(wav, "extra")
        self.assertEqual(frames, len(samples))
        self.assertEqual(len(got), len(samples))

    def test_reject_inconsistent_byte_rate(self):
        samples = self._sine(0.3, n=1000)
        wav = self._craft_wav(samples, byte_rate=999)
        with self.assertRaises(FixtureError):
            parse_wav(wav, "bad_rate")

    def test_reject_inconsistent_block_align(self):
        samples = self._sine(0.3, n=1000)
        wav = self._craft_wav(samples, block_align=4)
        with self.assertRaises(FixtureError):
            parse_wav(wav, "bad_align")

    def test_reject_duplicate_fmt_chunk(self):
        samples = self._sine(0.3, n=1000)
        wav = self._craft_wav(samples, dup_fmt=True)
        with self.assertRaises(FixtureError):
            parse_wav(wav, "dup_fmt")

    def test_reject_malformed_riff_length(self):
        samples = self._sine(0.3, n=1000)
        wav = self._craft_wav(samples)
        bad = wav[:4] + struct.pack("<I", 999999) + wav[8:]
        with self.assertRaises(FixtureError):
            parse_wav(bad, "bad_riff")

    def test_reject_trailing_bytes(self):
        """Full RIFF chunk consumption: trailing bytes past the last chunk are
        rejected rather than silently ignored. Keep the RIFF size consistent so
        parse_wav reaches the chunk loop, where the trailing bytes are caught."""
        samples = self._sine(0.3, n=1000)
        wav = self._craft_wav(samples)
        extended = wav + b"\x00\x00\x00\x00"
        extended = (extended[:4] + struct.pack("<I", len(extended) - 8)
                    + extended[8:])
        with self.assertRaises(FixtureError):
            parse_wav(extended, "trailing")

    def test_reject_truncated(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        p = os.path.join(src, "audio", "clip0.wav")
        raw = _read_bytes(p)
        _write_bytes(p, raw[: len(raw) // 2])  # header claims more than present
        self._expect_reject(src)

    def test_reject_invalid_header(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        _write_bytes(os.path.join(src, "audio", "clip0.wav"),
                     b"NOTAWAV" + b"\x00" * 64)
        self._expect_reject(src)

    def test_reject_hash_mismatch(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["pcmWav"]["sha256"] = "0" * 64
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_bytes_mismatch(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["pcmWav"]["bytes"] = 123
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_frames_mismatch(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["pcmWav"]["frames"] = 999
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_unsafe_path(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["wavPath"] = "../escape.wav"
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_absolute_wavpath(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["wavPath"] = "/etc/passwd"
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_duplicate_wavpath(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][1]["wavPath"] = man["clips"][0]["wavPath"]
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_unsafe_clip_id(self):
        # Build fully valid independent stdlib WAVs first, THEN mutate only the
        # manifest id. No audio/a/b.wav is constructed during setup.
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["id"] = "a/b"        # id with a path separator
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_fifo(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        fifo = os.path.join(src, "audio", "clipfifo.wav")
        os.mkfifo(fifo)
        try:
            man = _read_json(os.path.join(src, "manifest.json"))
            man["clips"][0]["wavPath"] = "audio/clipfifo.wav"
            _write_json(os.path.join(src, "manifest.json"), man)
            self._expect_reject(src)
        finally:
            try:
                os.remove(fifo)
            except OSError:
                pass

    def test_reject_symlink(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        real = os.path.join(src, "audio", "clip0.wav")
        link = os.path.join(src, "audio", "clip_link.wav")
        os.symlink(real, link)
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["wavPath"] = "audio/clip_link.wav"
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_source_dir_symlink(self):
        """source_dir itself being a symlink is rejected."""
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        parent = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, parent, ignore_errors=True)
        link = os.path.join(parent, "linked_src")
        os.symlink(src, link)
        self._expect_reject(link)

    def test_reject_ancestor_symlink(self):
        """An arbitrary symlink in the input path's ancestor chain is rejected
        (only macOS /tmp and /var system aliases are allowed)."""
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        audio = os.path.join(src, "audio")
        real = os.path.join(src, "real_audio")
        os.rename(audio, real)          # real dir holding the WAVs
        os.symlink(real, audio)         # audio/ is now an arbitrary symlink
        self._expect_reject(src)

    def test_reject_exactly_three(self):
        for n in (2, 4):
            src = self._build_source([self._sine(0.3)] * n)
            self._expect_reject(src)

    def test_reject_duplicate_ids(self):
        # DISTINCT valid wavPaths; only ids collide, so the duplicate-id check
        # fires independently instead of being masked by duplicate wavPaths.
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        self.assertNotEqual(man["clips"][0]["wavPath"], man["clips"][1]["wavPath"])
        man["clips"][1]["id"] = man["clips"][0]["id"]  # duplicate id, distinct wavPath
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_empty_ref(self):
        src = self._build_source(
            [self._sine(0.3), self._sine(0.25), self._sine(0.35)],
            refs=["ok", "", "also ok"],
        )
        self._expect_reject(src)

    def test_reject_missing_source_notice(self):
        """SOURCE-NOTICE.txt is required and must be carried verbatim."""
        src = self._build_source(
            [self._sine(0.3), self._sine(0.25), self._sine(0.35)], notice=False
        )
        self._expect_reject(src)

    def test_reject_bool_snr(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        dest = self._out_dir()
        with self.assertRaises(FixtureError):
            self._run(src, dest, snr_db=True)

    def test_reject_nonfinite_snr(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        dest = self._out_dir()
        with self.assertRaises(FixtureError):
            self._run(src, dest, snr_db=float("inf"))

    def test_reject_bool_seed(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        dest = self._out_dir()
        with self.assertRaises(FixtureError):
            self._run(src, dest, seed=True)

    def test_reject_bool_integer_fields(self):
        """pcmWav integer metadata must reject bool (strict int rejection)."""
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        man = _read_json(os.path.join(src, "manifest.json"))
        man["clips"][0]["pcmWav"]["bytes"] = True  # bool, not int
        _write_json(os.path.join(src, "manifest.json"), man)
        self._expect_reject(src)

    def test_reject_silent(self):
        src = self._build_source([self._sine(0.0), self._sine(0.25), self._sine(0.3)])
        self._expect_reject(src)

    def test_reject_nan_json(self):
        src = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, src, ignore_errors=True)
        os.makedirs(os.path.join(src, "audio"))
        man = (
            '{"schemaVersion":1,"source":{"revision":"r"},'
            '"clips":[{"id":"c0","reference":"r","wavPath":"audio/c0.wav",'
            '"pcmWav":{"sha256":"x","bytes":1,"sampleRate":16000,'
            '"channels":1,"sampleWidth":2,"frames":1,"durationSeconds":0.1}}]}'
        )
        _write_text(os.path.join(src, "manifest.json"), man)
        _write_text(os.path.join(src, "SOURCE-NOTICE.txt"), "n\n")
        # NaN makes pcmWav.frames non-finite -> rejected.
        man2 = man.replace('"frames":1', '"frames":NaN')
        _write_text(os.path.join(src, "manifest.json"), man2)
        self._expect_reject(src)

    def test_reject_duplicate_keys(self):
        src = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, src, ignore_errors=True)
        man = (
            '{"schemaVersion":1,"source":{"revision":"r"},'
            '"clips":[{"id":"c0","id":"c1","reference":"r","wavPath":"a",'
            '"pcmWav":{"sha256":"x","bytes":1,"sampleRate":16000,'
            '"channels":1,"sampleWidth":2,"frames":1,"durationSeconds":0.1}}]}'
        )
        _write_text(os.path.join(src, "manifest.json"), man)
        _write_text(os.path.join(src, "SOURCE-NOTICE.txt"), "n\n")
        self._expect_reject(src)

    def test_reject_destination_exists(self):
        src = self._build_source([self._sine(0.3), self._sine(0.25), self._sine(0.35)])
        d = tempfile.mkdtemp()  # existing destination
        self.addCleanup(shutil.rmtree, d, ignore_errors=True)
        _write_text(os.path.join(d, "sentinel.txt"), "keep")
        with self.assertRaises(FixtureError):
            self._run(src, d)
        self.assertTrue(os.path.exists(os.path.join(d, "sentinel.txt")))
        self.assertFalse(os.path.exists(os.path.join(d, "manifest.json")))

    def test_existing_destination_unchanged_on_invalid(self):
        src = self._build_source([self._sine(0.0), self._sine(0.25), self._sine(0.3)])  # silent
        d = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, d, ignore_errors=True)
        _write_text(os.path.join(d, "sentinel.txt"), "keep")
        with self.assertRaises(FixtureError):
            self._run(src, d)
        self.assertTrue(os.path.exists(os.path.join(d, "sentinel.txt")))
        self.assertFalse(os.path.exists(os.path.join(d, "manifest.json")))


if __name__ == "__main__":
    unittest.main(verbosity=2)
