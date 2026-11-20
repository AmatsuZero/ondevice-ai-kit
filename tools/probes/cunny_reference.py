"""Stdlib-only scalar port of the installed CuNNy 2x image upscaler.

Coefficients are never hard-coded here. They are parsed via regular expressions
(never ``eval``) from the trusted Metal source text passed to :func:`upscale`
as ``metal``. The three kernels ``cunny0`` / ``cunny1`` / ``cunny2`` are
validated to contain exactly 18 / 36 / 18 taps with the expected per-``r``
distribution, per-layer biases, and ``s0_*`` / ``s1_*`` input groups.

Notes for maintainers of the tests:

* Layer biases are genuine coefficients, so an all-zero (black) input does
  not map to an all-zero output. Do not change the math to satisfy such a
  test; this is a faithful scalar transcription of the Metal kernels.
* Exact agreement with the device/Metal result depends on the concrete
  coefficients in the installed reference and on Metal's ``half`` precision
  for intermediates (see ``APPROX_HALF_INTERMEDIATES``). If the tests carry
  no numerically grounded reference coefficients/outputs, only structural
  invariants (shape, finiteness, clamping, kernel completeness) are
  meaningfully assertable.
"""

from __future__ import annotations

import math
import re
import struct
from collections import Counter
from dataclasses import dataclass
from functools import lru_cache
from typing import Sequence

__all__ = ["upscale", "bilinear_baseline"]

_FLOAT = r"[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?"

_RE_KERNEL = re.compile(r"kernel\s+void\s+cunny([012])\s*\(")
_RE_V4_TERM = re.compile(
    r"r(?P<r>\d+)\s*\+=\s*V4\(\s*(?P<v>[^)]*?)\s*\)\s*\*\s*"
    r"s(?P<g>\d+)_(?P<row>\d+)_(?P<col>\d+)\s*;",
    re.DOTALL,
)
_RE_M4_TERM = re.compile(
    r"r(?P<r>\d+)\s*\+=\s*M4\(\s*(?P<v>[^)]*?)\s*\)\s*\*\s*"
    r"s(?P<g>\d+)_(?P<row>\d+)_(?P<col>\d+)\s*;",
    re.DOTALL,
)
_RE_BIAS = re.compile(
    r"r(?P<r>\d+)\s*\+=\s*V4\(\s*(?P<v>[^)]*?)\s*\)\s*;",
    re.DOTALL,
)

# Optional approximation of the Metal `half` intermediates.
APPROX_HALF_INTERMEDIATES = False
_HALF = struct.Struct("<e")


def _half(v: float) -> float:
    return _HALF.unpack(_HALF.pack(v))[0]


@dataclass(frozen=True)
class _Term:
    r: int
    g: int
    row: int
    col: int
    vals: tuple[float, ...]
    wide: bool


@dataclass(frozen=True)
class _Layer:
    terms: tuple[_Term, ...]
    biases: dict[int, tuple[float, ...]]


@dataclass(frozen=True)
class _Weights:
    layers: tuple[_Layer, _Layer, _Layer]


# (wide, term count, per-r counts, bias targets, input groups)
_EXPECTED = (
    (False, 18, {0: 9, 1: 9}, {0, 1}, {0}),
    (True, 36, {0: 18, 1: 18}, {0, 1}, {0, 1}),
    (True, 18, {0: 18}, {0}, {0, 1}),
)


def _floats(text: str, n: int, where: str) -> tuple[float, ...]:
    parts = [p.strip() for p in text.split(",")]
    if any(not re.fullmatch(_FLOAT, p) for p in parts):
        raise ValueError(f"bad float list in {where}: {text!r}")
    vals = tuple(float(p) for p in parts)
    if len(vals) != n:
        raise ValueError(f"expected {n} floats in {where}, got {len(vals)}")
    if not all(math.isfinite(v) for v in vals):
        raise ValueError(f"non-finite coefficient in {where}")
    return vals


def _sections(metal: str) -> dict[int, str]:
    ms = list(_RE_KERNEL.finditer(metal))
    if not ms:
        raise ValueError("no 'kernel void cunny0/1/2(' found in metal source")
    out: dict[int, str] = {}
    for i, m in enumerate(ms):
        k = int(m.group(1))
        if k in out:
            raise ValueError(f"duplicate kernel cunny{k}")
        end = ms[i + 1].start() if i + 1 < len(ms) else len(metal)
        out[k] = metal[m.end():end]
    for k in (0, 1, 2):
        if k not in out:
            raise ValueError(f"missing kernel cunny{k}")
    return out


def _layer(section: str, wide: bool) -> _Layer:
    terms: list[_Term] = []
    biases: dict[int, tuple[float, ...]] = {}
    rx = _RE_M4_TERM if wide else _RE_V4_TERM
    n = 16 if wide else 4
    for m in rx.finditer(section):
        terms.append(
            _Term(
                int(m["r"]),
                int(m["g"]),
                int(m["row"]),
                int(m["col"]),
                _floats(m["v"], n, "coefficient"),
                wide,
            )
        )
    for m in _RE_BIAS.finditer(section):
        r = int(m["r"])
        if r in biases:
            raise ValueError(f"duplicate bias for r{r}")
        biases[r] = _floats(m["v"], 4, f"bias r{r}")
    return _Layer(tuple(terms), biases)


def _validate(layer: _Layer, idx: int) -> None:
    wide, count, rcounts, brs, groups = _EXPECTED[idx]
    if len(layer.terms) != count:
        raise ValueError(
            f"cunny{idx}: expected {count} terms, got {len(layer.terms)}"
        )
    if dict(Counter(t.r for t in layer.terms)) != rcounts:
        raise ValueError(f"cunny{idx}: wrong term distribution over r targets")
    if set(layer.biases) != brs:
        raise ValueError(f"cunny{idx}: wrong bias targets")
    seen: set[tuple[int, int, int, int]] = set()
    seen_groups: set[int] = set()
    for t in layer.terms:
        if t.wide != wide:
            raise ValueError(f"cunny{idx}: mixed V4/M4 coefficients")
        if t.row not in (0, 1, 2) or t.col not in (0, 1, 2):
            raise ValueError(f"cunny{idx}: tap outside the 3x3 range")
        if t.g not in groups:
            raise ValueError(f"cunny{idx}: unexpected input group s{t.g}")
        key = (t.r, t.g, t.row, t.col)
        if key in seen:
            raise ValueError(f"cunny{idx}: duplicate term {key}")
        seen.add(key)
        seen_groups.add(t.g)
    if seen_groups != groups:
        raise ValueError(f"cunny{idx}: input groups {seen_groups} != {groups}")


@lru_cache(maxsize=16)
def _parse(metal: str) -> _Weights:
    sec = _sections(metal)
    layers = tuple(_layer(sec[k], _EXPECTED[k][0]) for k in (0, 1, 2))
    for k, layer in enumerate(layers):
        _validate(layer, k)
    return _Weights(layers)


def _normalize(samples: Sequence[Sequence[float]]) -> list[list[float]]:
    if not isinstance(samples, (list, tuple)) or not samples:
        raise ValueError("samples must be a non-empty rectangular 2-D sequence")
    width: int | None = None
    out: list[list[float]] = []
    for row in samples:
        if not isinstance(row, (list, tuple)) or not row:
            raise ValueError("sample rows must be non-empty sequences")
        if width is None:
            width = len(row)
        elif len(row) != width:
            raise ValueError("samples must be rectangular")
        nr: list[float] = []
        for v in row:
            f = float(v)
            if not math.isfinite(f):
                raise ValueError("samples must contain only finite numbers")
            nr.append(f)
        out.append(nr)
    return out


def _clampi(v: int, hi: int) -> int:
    return 0 if v < 0 else (hi if v > hi else v)


def _sample(img: list[list[float]], y: float, x: float) -> float:
    h = len(img)
    w = len(img[0])
    y0 = math.floor(y)
    x0 = math.floor(x)
    wy = y - y0
    wx = x - x0
    y0c = _clampi(y0, h - 1)
    y1c = _clampi(y0 + 1, h - 1)
    x0c = _clampi(x0, w - 1)
    x1c = _clampi(x0 + 1, w - 1)
    top = img[y0c][x0c] * (1.0 - wx) + img[y0c][x1c] * wx
    bot = img[y1c][x0c] * (1.0 - wx) + img[y1c][x1c] * wx
    return top * (1.0 - wy) + bot * wy


def _commit(vec: list[float], bias: tuple[float, ...], act: str) -> list[float]:
    out: list[float] = []
    for k in range(4):
        v = vec[k] + bias[k]
        if act == "relu":
            if v < 0.0:
                v = 0.0
        elif act == "tanh":
            v = math.tanh(v)
        out.append(_half(v) if APPROX_HALF_INTERMEDIATES else v)
    return out


def _layer0(source: list[list[float]], layer: _Layer) -> list[list[list[float]]]:
    h, w = len(source), len(source[0])
    out: list[list[list[float]]] = [[[] for _ in range(w)] for _ in range(h)]
    terms = layer.terms
    b0, b1 = layer.biases[0], layer.biases[1]
    for y in range(h):
        for x in range(w):
            a = [0.0] * 8
            for t in terms:
                sv = _sample(source, y + t.row - 1, x + t.col - 1)
                base = t.r * 4
                v = t.vals
                a[base] += v[0] * sv
                a[base + 1] += v[1] * sv
                a[base + 2] += v[2] * sv
                a[base + 3] += v[3] * sv
            out[y][x] = [
                _commit(a[0:4], b0, "relu"),
                _commit(a[4:8], b1, "relu"),
            ]
    return out


def _convolve(
    prev: list[list[list[float]]], layer: _Layer, act: str
) -> list[list[list[float]]]:
    h, w = len(prev), len(prev[0])
    nout = len(layer.biases)
    out: list[list[list[float]]] = [[[] for _ in range(w)] for _ in range(h)]
    terms = layer.terms
    for y in range(h):
        for x in range(w):
            a = [0.0] * (nout * 4)
            for t in terms:
                sy = _clampi(y + t.row - 1, h - 1)
                sx = _clampi(x + t.col - 1, w - 1)
                v = prev[sy][sx][t.g]
                m = t.vals
                base = t.r * 4
                a[base] += m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12] * v[3]
                a[base + 1] += m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13] * v[3]
                a[base + 2] += m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14] * v[3]
                a[base + 3] += m[3] * v[0] + m[7] * v[1] + m[11] * v[2] + m[15] * v[3]
            out[y][x] = [
                _commit(a[r * 4:r * 4 + 4], layer.biases[r], act)
                for r in range(nout)
            ]
    return out


def bilinear_baseline(samples: Sequence[Sequence[float]]) -> list[list[float]]:
    """Plain 2x bilinear upsample with edge clamping, independent of the net."""
    src = _normalize(samples)
    h, w = len(src), len(src[0])
    return [
        [
            _sample(src, (oy + 0.5) / 2.0 - 0.5, (ox + 0.5) / 2.0 - 0.5)
            for ox in range(2 * w)
        ]
        for oy in range(2 * h)
    ]


def upscale(metal: str, samples: list[list[float]]) -> list[list[float]]:
    """Upscale ``samples`` by 2x using the coefficients parsed from ``metal``."""
    weights = _parse(metal)
    src = _normalize(samples)
    h, w = len(src), len(src[0])

    l0 = _layer0(src, weights.layers[0])
    l1 = _convolve(l0, weights.layers[1], "relu")
    res = _convolve(l1, weights.layers[2], "tanh")

    oh, ow = 2 * h, 2 * w
    out: list[list[float]] = [[0.0] * ow for _ in range(oh)]
    for oy in range(oh):
        for ox in range(ow):
            base = _sample(
                src, (oy + 0.5) / 2.0 - 0.5, (ox + 0.5) / 2.0 - 0.5
            )
            y = oy >> 1
            x = ox >> 1
            ch = 2 * (oy & 1) + (ox & 1)
            v = base + res[y][x][0][ch]
            out[oy][ox] = 0.0 if v < 0.0 else (1.0 if v > 1.0 else v)
    return out


def _main(argv: list[str] | None = None) -> None:
    import argparse
    import json
    import sys

    ap = argparse.ArgumentParser(description="Run the scalar CuNNy 2x upscaler.")
    ap.add_argument("metal", help="path to the trusted installed Metal reference")
    ap.add_argument("--samples", default="-", help="JSON 2-D samples, '-' for stdin")
    args = ap.parse_args(argv)
    with open(args.metal, "r", encoding="utf-8") as fh:
        metal = fh.read()
    text = sys.stdin.read() if args.samples == "-" else args.samples
    samples = json.loads(text)
    for row in upscale(metal, samples):
        print(json.dumps(row))


if __name__ == "__main__":
    _main()
