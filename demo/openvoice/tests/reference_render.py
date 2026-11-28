#!/usr/bin/env python3
"""Offline OpenVoice C-ABI render checks; stdlib only, no downloaded fixtures."""
import ctypes
import hashlib
import math
import os
import struct
import sys
import tempfile
import time
import wave

RATE = 22050
HOP = 256


def fail(msg):
    print("FAIL: " + msg, file=sys.stderr)
    raise SystemExit(1)


def open_lib(path):
    lib = ctypes.CDLL(path)
    ref = lib.openvoice_render_reference
    ref.argtypes = [ctypes.c_char_p] * 4
    ref.restype = ctypes.c_int
    fixed = lib.openvoice_render
    fixed.argtypes = [ctypes.c_char_p] * 4
    fixed.restype = ctypes.c_int
    return ref, fixed


def wav_info(path):
    with wave.open(path, "rb") as w:
        n, ch, sw, fr = w.getnframes(), w.getnchannels(), w.getsampwidth(), w.getframerate()
        data = w.readframes(n)
    return n, ch, sw, fr, data


def check_pcm16_mono(label, path):
    n, ch, sw, fr, data = wav_info(path)
    if ch != 1 or sw != 2 or fr != RATE:
        fail("%s: expected PCM16 mono %d Hz, got ch=%d sw=%d fr=%d" % (label, RATE, ch, sw, fr))
    if n <= 0 or not data or not any(data):
        fail("%s: empty or all-zero PCM" % label)
    return n, data


def expected_frames(content):
    n, _, _, fr, _ = wav_info(content)
    resampled = int(n * RATE / float(fr) + 0.5)   # std::lround to 22050
    floored = resampled - (resampled % HOP)        # floor to synthesis hop
    return floored, resampled


FIXTURE_RATE = 16000


def make_wav(path, seconds):
    n = int(seconds * FIXTURE_RATE)
    frames = b"".join(
        struct.pack("<h", int(12000 * math.sin(2 * math.pi * 220 * i / FIXTURE_RATE)))
        for i in range(n)
    )
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(FIXTURE_RATE)
        w.writeframes(frames)


def main():
    if len(sys.argv) != 6:
        fail("usage: reference_render.py <native-lib> <assets> <content> <refA> <refB>")
    lib_path, assets, content, ref_a, ref_b = sys.argv[1:]
    for label, p in (("native-lib", lib_path), ("assets", assets), ("content", content),
                     ("refA", ref_a), ("refB", ref_b)):
        if not os.path.exists(p):
            fail("%s missing: %s" % (label, p))

    ref_render, fixed_render = open_lib(lib_path)

    with tempfile.TemporaryDirectory() as tmp:
        def call(fn, args, name):
            dst = os.path.join(tmp, name).encode()
            t0 = time.monotonic()
            rc = fn(*args, dst)
            return rc, dst, time.monotonic() - t0

        assets_b = assets.encode()
        ref_args = [assets_b, os.fsencode(ref_a), os.fsencode(content)]
        rc1, o1, t1 = call(ref_render, ref_args, "a1.wav")
        rc2, o2, t2 = call(ref_render, ref_args, "a2.wav")
        rc_b, ob, t_b = call(ref_render, [assets_b, os.fsencode(ref_b), os.fsencode(content)], "b.wav")
        if rc1 or rc2 or rc_b:
            fail("reference render nonzero: %d/%d/%d" % (rc1, rc2, rc_b))

        def read_bytes(p):
            with open(p, "rb") as f:
                return f.read()

        d1, d2, db = read_bytes(o1), read_bytes(o2), read_bytes(ob)
        if d1 != d2:
            fail("repeated refA renders are not byte-identical")
        if d1 == db:
            fail("refB render identical to refA for the same content")

        n1, pcm1 = check_pcm16_mono("refA", o1.decode())
        nb, pcmb = check_pcm16_mono("refB", ob.decode())
        want, resampled = expected_frames(content)
        if n1 != want or nb != want:
            fail("frames A=%d B=%d expected exact floored=%d (lround=%d)" % (n1, nb, want, resampled))

        _, _, _, _, src_pcm = wav_info(content)
        if not any(src_pcm):
            fail("content PCM is all-zero")
        if pcm1 == src_pcm or pcmb == src_pcm:
            fail("rendered PCM identical to source PCM")

        print("A %s frames=%d rate=%d %.3fs" % (hashlib.sha256(d1).hexdigest()[:16], n1, RATE, t1))
        print("B %s frames=%d rate=%d %.3fs" % (hashlib.sha256(db).hexdigest()[:16], nb, RATE, t_b))
        print("expect lround=%d floor(hop=%d)=%d" % (resampled, HOP, want))

        bad_ref = os.path.join(tmp, "sub05.wav")
        make_wav(bad_ref, 0.2)
        rc3, d3, _ = call(ref_render, [assets_b, os.fsencode(bad_ref), os.fsencode(content)], "bad_ref.wav")
        if rc3 == 0 or os.path.exists(d3):
            fail("sub-0.5s reference unexpectedly succeeded or wrote output")

        bad_content = os.path.join(tmp, "tiny_content.wav")
        make_wav(bad_content, 0.2)
        rc4, d4, _ = call(ref_render, [assets_b, os.fsencode(ref_a), os.fsencode(bad_content)], "bad_content.wav")
        if rc4 == 0 or os.path.exists(d4):
            fail("sub-0.5s/invalid content unexpectedly succeeded or wrote output")

        short = os.path.join(tmp, "short_eligible.wav")
        make_wav(short, 0.4)
        rc5, of, t5 = call(fixed_render, [assets_b, os.fsencode(short), b"voiceA"], "fixed.wav")
        if rc5 != 0:
            fail("fixed renderer failed on eligible short input: %d" % rc5)
        nf, _ = check_pcm16_mono("fixed", of.decode())
        print("fixed frames=%d rate=%d %.3fs" % (nf, RATE, t5))

        print("OK")


if __name__ == "__main__":
    main()