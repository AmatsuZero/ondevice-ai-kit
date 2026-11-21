"""Reproducible CuNNy offscreen-pixel parity checker; never writes media."""
import argparse
import hashlib
import json
import sys

from tools.probes.cunny_reference import upscale, bilinear_baseline

TILE_W, TILE_H, PAD = 192, 64, 4


def _read(path):
    with open(path, "rb") as f:
        return f.read()


def _norm(region, width):
    return [[region[y * width + x] / 255.0 for x in range(width)]
            for y in range(len(region) // width)]


def _flat(grid):
    if grid and isinstance(grid[0], (list, tuple)):
        return [v for row in grid for v in row]
    return list(grid)


def _q(v):
    return max(0, min(255, round(v * 255)))


def _sha(buf):
    return hashlib.sha256(bytes(buf)).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--metal", required=True)
    ap.add_argument("--input", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--width", type=int, required=True)
    ap.add_argument("--height", type=int, required=True)
    a = ap.parse_args()

    metal = _read(a.metal).decode('utf-8')
    src = _read(a.input)
    dst = _read(a.output)
    W, H = a.width, a.height
    if W <= 0 or H <= 0:
        ap.error('width and height must be positive')
    if len(src) != W * H or len(dst) != 4 * W * H:
        print(json.dumps({"error": "length mismatch", "input": len(src),
                          "expectedInput": W * H, "output": len(dst),
                          "expectedOutput": 4 * W * H}, sort_keys=True))
        return 2

    ow = 2 * W
    cpu, bil = bytearray(4 * W * H), bytearray(4 * W * H)
    for ty in range(0, H, TILE_H):
        for tx in range(0, W, TILE_W):
            tw, th = min(TILE_W, W - tx), min(TILE_H, H - ty)
            px0, py0 = max(0, tx - PAD), max(0, ty - PAD)
            px1, py1 = min(W, tx + tw + PAD), min(H, ty + th + PAD)
            pw = px1 - px0
            region = bytearray()
            for y in range(py0, py1):
                region.extend(src[y * W + px0:y * W + px1])
            samples = _norm(region, pw)
            up = _flat(upscale(metal, samples))
            bl = _flat(bilinear_baseline(samples))
            stride, ox, oy = 2 * pw, 2 * (tx - px0), 2 * (ty - py0)
            for r in range(2 * th):
                s = (oy + r) * stride + ox
                base = (2 * ty + r) * ow + 2 * tx
                cpu[base:base + 2 * tw] = [_q(v) for v in up[s:s + 2 * tw]]
                bil[base:base + 2 * tw] = [_q(v) for v in bl[s:s + 2 * tw]]

    max_abs, wx, wy, diff_cpu, diff_bil = 0, 0, 0, 0, 0
    for i, (o, c, b) in enumerate(zip(dst, cpu, bil)):
        d = abs(o - c)
        if d > max_abs:
            max_abs, wx, wy = d, i % ow, i // ow
        diff_cpu += (o != c)
        diff_bil += (o != b)

    print(json.dumps({
        "outputPixels": len(dst),
        "maxAbs8": max_abs,
        "x": wx, "y": wy,
        "worst": {"x": wx, "y": wy},
        "differentFromBilinear": diff_bil,
        "differentFromCPU": diff_cpu,
        "sha256": {"input": _sha(src), "output": _sha(dst), "cpu": _sha(cpu)},
    }, sort_keys=True))
    return 1 if max_abs > 1 else 0


if __name__ == "__main__":
    sys.exit(main())
