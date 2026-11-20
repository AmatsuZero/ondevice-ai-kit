#!/usr/bin/env python3
"""Deterministic GLSL ES 3.1 compute shader generator for the pinned cunny SR kernels.

Reads the pinned upstream Metal kernel source, extracts the license header and the
exact coefficient lines for cunny0/cunny1/cunny2, and emits three complete GLES
compute shaders. Python standard library only; no network, device or third-party
access. The source file is never modified.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

LAYER_NAMES = {0: "cunny0", 1: "cunny1", 2: "cunny2"}
EXPECTED_COUNTS = {0: (18, 2), 1: (36, 2), 2: (18, 1)}  # (terms, biases)
EXPECTED_ACCS = {0: ["r0", "r1"], 1: ["r0", "r1"], 2: ["r0"]}

LICENSE_MARKER = "/* ------------------------------------------------------------------- */"
FLOAT_LITERAL = r"[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?"

RE_FUNC = re.compile(r"\bcunny(?P<idx>[0-9])\s*\([^{};]*\)\s*\{")
RE_V4_TERM = re.compile(
    r"^\s*(?P<acc>r[0-9]+)\s*\+=\s*V4\((?P<vec>[^()]*)\)\s*\*\s*"
    r"(?P<sym>s[0-9]+_[0-9]+_[0-9]+)\s*;\s*$"
)
RE_M4_TERM = re.compile(
    r"^\s*(?P<acc>r[0-9]+)\s*\+=\s*M4\((?P<mat>[^()]*)\)\s*\*\s*"
    r"(?P<sym>s[0-9]+_[0-9]+_[0-9]+)\s*;\s*$"
)
RE_V4_BIAS = re.compile(
    r"^\s*(?P<acc>r[0-9]+)\s*\+=\s*V4\((?P<vec>[^()]*)\)\s*;\s*$"
)
RE_COEFF_LOOK = re.compile(r"^\s*r[01]\s*\+=")


class GenError(Exception):
    pass


def norm_float(tok: str) -> str:
    """Preserve the numeric value while making the token a valid GLSL float."""
    idx = min([i for i, ch in enumerate(tok) if ch in "eE"] or [len(tok)])
    mant, exp = tok[:idx], tok[idx:]
    sign = ""
    if mant[:1] in ("+", "-"):
        sign, mant = mant[0], mant[1:]
    if mant.startswith("."):
        mant = "0" + mant
    if mant.endswith("."):
        mant += "0"
    if "." not in mant:
        mant += ".0"
    return sign + mant + exp


def parse_float_list(text: str, count: int, where: str) -> list[str]:
    parts = [p.strip() for p in text.split(",")]
    if len(parts) != count:
        raise GenError(f"{where}: expected {count} floats, got {len(parts)}: {text!r}")
    out: list[str] = []
    for p in parts:
        if re.fullmatch(FLOAT_LITERAL, p) is None:
            raise GenError(f"{where}: bad float literal {p!r}")
        out.append(norm_float(p))
    return out


def strip_comments(text: str) -> str:
    text = re.sub(
        r"/\*.*?\*/",
        lambda m: "\n" * m.group(0).count("\n"),
        text,
        flags=re.S,
    )
    return re.sub(r"//[^\n]*", "", text)


def extract_license(source: str) -> list[str]:
    if LICENSE_MARKER not in source:
        raise GenError(f"source missing license marker {LICENSE_MARKER!r}")
    header = source.split(LICENSE_MARKER, 1)[0].replace("\r\n", "\n").replace("\r", "\n")
    out: list[str] = []
    for ln in header.split("\n"):
        s = ln.strip()
        if not s:
            out.append("")
        elif s.startswith(("//", "/*", "*")):
            out.append(ln.rstrip())
        else:
            out.append("// " + ln.rstrip())
    while out and not out[0]:
        out.pop(0)
    while out and not out[-1]:
        out.pop()
    return out


def split_sections(code: str) -> dict[int, str]:
    matches = list(RE_FUNC.finditer(code))
    found: dict[int, re.Match] = {}
    for m in matches:
        idx = int(m.group("idx"))
        if idx not in (0, 1, 2):
            raise GenError(f"unexpected cunny index {idx}")
        if idx in found:
            raise GenError(f"duplicate cunny{idx} definition")
        found[idx] = m
    if set(found) != {0, 1, 2}:
        raise GenError("source must define cunny0, cunny1 and cunny2")
    ordered = sorted(found.values(), key=lambda m: m.start())
    sections: dict[int, str] = {}
    for i, m in enumerate(ordered):
        start = m.end()
        end = ordered[i + 1].start() if i + 1 < len(ordered) else len(code)
        sections[int(m.group("idx"))] = code[start:end]
    return sections


def parse_records(section: str, layer: int) -> list[dict]:
    recs: list[dict] = []
    for raw in section.replace("\r\n", "\n").split("\n"):
        line = raw.split("//", 1)[0]
        if not line.strip():
            continue
        m = RE_V4_TERM.match(line)
        if m:
            recs.append({
                "kind": "v4term",
                "acc": m.group("acc"),
                "sym": m.group("sym"),
                "vals": parse_float_list(m.group("vec"), 4, f"L{layer} V4 term"),
            })
            continue
        m = RE_M4_TERM.match(line)
        if m:
            recs.append({
                "kind": "m4term",
                "acc": m.group("acc"),
                "sym": m.group("sym"),
                "vals": parse_float_list(m.group("mat"), 16, f"L{layer} M4 term"),
            })
            continue
        m = RE_V4_BIAS.match(line)
        if m:
            recs.append({
                "kind": "bias",
                "acc": m.group("acc"),
                "vals": parse_float_list(m.group("vec"), 4, f"L{layer} bias"),
            })
            continue
        if RE_COEFF_LOOK.search(line):
            raise GenError(f"L{layer}: unrecognized coefficient line: {raw.strip()!r}")
    return recs


def validate(layer: int, recs: list[dict]) -> tuple[list[str], dict[str, str]]:
    exp_terms, exp_biases = EXPECTED_COUNTS[layer]
    terms = [r for r in recs if r["kind"] in ("v4term", "m4term")]
    biases = [r for r in recs if r["kind"] == "bias"]
    if len(terms) != exp_terms or len(biases) != exp_biases:
        raise GenError(
            f"L{layer}: expected {exp_terms} terms/{exp_biases} biases, "
            f"got {len(terms)}/{len(biases)}"
        )

    accs = EXPECTED_ACCS[layer]
    bias_accs: list[str] = []
    for b in biases:
        if b["acc"] not in bias_accs:
            bias_accs.append(b["acc"])
    if sorted(bias_accs) != sorted(accs):
        raise GenError(f"L{layer}: unexpected accumulator set {bias_accs}")

    if exp_terms % len(accs) != 0:
        raise GenError(f"L{layer}: term count not divisible by accumulator count")
    per = exp_terms // len(accs)

    counts = {a: 0 for a in accs}
    symtype: dict[str, str] = {}
    order: list[str] = []
    prefix = f"s{layer}_"
    for r in terms:
        a = r["acc"]
        if a not in counts:
            raise GenError(f"L{layer}: term for unknown accumulator {a}")
        counts[a] += 1
        sym = r["sym"]
        if not re.fullmatch(r"s0_[0-2]_[0-2]" if layer == 0 else r"s[01]_[0-2]_[0-2]", sym):
            raise GenError(f"L{layer}: symbol {sym} has wrong layer prefix")
        parts = sym.split("_")
        if len(parts) != 3 or not all(p.isdigit() for p in parts[1:]):
            raise GenError(f"L{layer}: malformed symbol {sym}")
        typ = "float" if r["kind"] == "v4term" else "vec4"
        if sym in symtype and symtype[sym] != typ:
            raise GenError(f"L{layer}: symbol {sym} used as both float and vec4")
        symtype[sym] = typ
        if sym not in order:
            order.append(sym)
    for a in accs:
        if counts[a] != per:
            raise GenError(f"L{layer}: accumulator {a} has {counts[a]} terms, expected {per}")
    return order, symtype


def emit_samples(layer: int, order: list[str], symtype: dict[str, str]) -> list[str]:
    lines: list[str] = []
    for sym in order:
        parts = sym.split("_")
        a, b = int(parts[1]), int(parts[2])
        typ = symtype[sym]
        if layer == 0:
            off = f"pos + ivec2({b - 1}, {a - 1})"
            if typ == "float":
                lines.append(f"    float {sym} = lumaAt({off});")
            else:
                lines.append(
                    f"    vec4 {sym} = vec4(lumaAt({off}), "
                    f"lumaAt({off} + ivec2(1, 0)), "
                    f"lumaAt({off} + ivec2(0, 1)), "
                    f"lumaAt({off} + ivec2(1, 1)));"
                )
        else:
            group, xo, yo = int(sym[1]), b - 1, a - 1
            fetch = f"fetchGroup(pos, ivec2({xo}, {yo}), {group})"
            if typ == "float":
                lines.append(f"    float {sym} = lumaOf({fetch});")
            else:
                lines.append(f"    vec4 {sym} = {fetch};")
    if not lines:
        lines.append("    // no explicit sample symbols")
    return lines


def emit_coeffs(recs: list[dict]) -> list[str]:
    lines: list[str] = []
    for r in recs:
        v = r["vals"]
        if r["kind"] == "v4term":
            lines.append(f"    {r['acc']} += vec4({', '.join(v)}) * {r['sym']};")
        elif r["kind"] == "m4term":
            cols = ", ".join("vec4(" + ", ".join(v[i:i + 4]) + ")" for i in range(0, 16, 4))
            lines.append(f"    {r['acc']} += mat4({cols}) * {r['sym']};")
        else:
            lines.append(f"    {r['acc']} += vec4({', '.join(v)});")
    return lines


def gen_shader(
    layer: int,
    recs: list[dict],
    order: list[str],
    symtype: dict[str, str],
    license_lines: list[str],
) -> str:
    name = LAYER_NAMES[layer]
    out: list[str] = []
    out.append("#version 310 es")
    out.extend(license_lines)
    out.append("")
    out.append(
        f"// Generated by generate_cunny_gles.py -- pinned kernel {name} "
        f"({len(recs)} coefficient lines, exact source order/values)."
    )
    out.append("// mat4 columns are built in Metal M4 order (both are column-major).")
    out.append("precision highp float;")
    out.append("precision highp int;")
    out.append("precision highp sampler2D;")
    out.append("precision highp image2D;")
    out.append("")
    out.append("layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;")
    out.append("layout(binding = 0) uniform sampler2D uInput;   // texture unit 0")
    if layer == 2:
        out.append("layout(binding = 1) uniform sampler2D uBase;    // texture unit 1")
    out.append("layout(rgba16f, binding = 0) writeonly highp uniform image2D uOutput;")
    out.append("")
    out.append("float lumaOf(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }")
    out.append("vec4 relu4(vec4 x) { return max(x, vec4(0.0)); }")
    out.append("vec4 tanh4(vec4 x) {")
    out.append("    vec4 e = exp(2.0 * clamp(x, vec4(-8.0), vec4(8.0)));")
    out.append("    return (e - 1.0) / (e + 1.0);")
    out.append("}")
    if layer == 0:
        out.extend([
            "float lumaAt(ivec2 p) {",
            "    ivec2 s = textureSize(uInput, 0);",
            "    ivec2 q = clamp(p, ivec2(0), s - 1);",
            "    return lumaOf(texelFetch(uInput,q,0).rgb);",
            "}",
        ])
    else:
        out.extend([
            "vec4 fetchGroup(ivec2 p, ivec2 off, int group) {",
            "    ivec2 s = textureSize(uInput, 0);",
            "    ivec2 lim = ivec2(s.x / 2 - 1, s.y - 1);",
            "    ivec2 q = clamp(p + off, ivec2(0), lim);",
            "    return texelFetch(uInput, ivec2(q.x * 2 + group, q.y), 0);",
            "}",
        ])
    out.append("")
    out.append("void main() {")
    out.append("    // Host dispatch: ceil(srcW/8) x ceil(srcH/8) workgroups; bounds enforced here.")
    if layer == 0:
        out.append("    ivec2 srcSize = textureSize(uInput, 0);")
    else:
        out.append("    ivec2 full = textureSize(uInput, 0);")
        out.append("    ivec2 srcSize = ivec2(full.x / 2, full.y);")
    out.append("    ivec2 outSize = imageSize(uOutput);")
    out.append("    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);")
    out.append("    if (pos.x >= srcSize.x || pos.y >= srcSize.y) return;")
    if layer in (0, 1):
        out.append("    if (pos.x * 2 + 1 >= outSize.x || pos.y >= outSize.y) return;")
    out.append("")
    out.extend(emit_samples(layer, order, symtype))
    out.append("")
    for a in EXPECTED_ACCS[layer]:
        out.append(f"    vec4 {a} = vec4(0.0);")
    out.append("")
    out.append("    // Pinned coefficients:")
    out.extend(emit_coeffs(recs))
    out.append("")
    if layer in (0, 1):
        tag = "inter1" if layer == 0 else "inter2"
        out.append(f"    // {tag}: ReLU")
        out.append("    vec4 g0 = relu4(r0);")
        out.append("    vec4 g1 = relu4(r1);")
        out.append("    imageStore(uOutput, ivec2(pos.x * 2 + 0, pos.y), g0);")
        out.append("    imageStore(uOutput, ivec2(pos.x * 2 + 1, pos.y), g1);")
    else:
        out.append("    // last layer: tanh, then residual + base luma, clamp")
        out.append("    vec4 res = tanh4(r0);")
        out.append("    for (int q = 0; q < 4; ++q) {")
        out.append("        ivec2 sub = ivec2(q & 1, q >> 1);")
        out.append("        ivec2 dst = pos * 2 + sub;")
        out.append("        if (dst.x >= outSize.x || dst.y >= outSize.y) continue;")
        out.append("        vec2 frac = mix(vec2(0.75), vec2(0.25), vec2(sub));")
        out.append("        ivec2 base = pos + (sub - 1);")
        out.append("        ivec2 hi = srcSize - 1;")
        out.append("        ivec2 c0 = clamp(base, ivec2(0), hi);")
        out.append("        ivec2 c1 = clamp(base + ivec2(1, 0), ivec2(0), hi);")
        out.append("        ivec2 c2 = clamp(base + ivec2(0, 1), ivec2(0), hi);")
        out.append("        ivec2 c3 = clamp(base + ivec2(1, 1), ivec2(0), hi);")
        out.append("        float l00 = lumaOf(texelFetch(uBase, c0, 0).rgb);")
        out.append("        float l10 = lumaOf(texelFetch(uBase, c1, 0).rgb);")
        out.append("        float l01 = lumaOf(texelFetch(uBase, c2, 0).rgb);")
        out.append("        float l11 = lumaOf(texelFetch(uBase, c3, 0).rgb);")
        out.append("        float baseLuma = mix(mix(l00, l10, frac.x), mix(l01, l11, frac.x), frac.y);")
        out.append("        float enhanced = clamp(res[q] + baseLuma, 0.0, 1.0);")
        out.append("        imageStore(uOutput, dst, vec4(enhanced, enhanced, enhanced, 1.0));")
        out.append("    }")
    out.append("}")
    out.append("")
    return "\n".join(out)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description="Generate cunny0/1/2 GLES 3.1 compute shaders from a pinned Metal kernel."
    )
    ap.add_argument("--source", required=True, type=Path, help="pinned Metal kernel source")
    ap.add_argument("--out", required=True, type=Path, help="output directory")
    args = ap.parse_args(argv)

    try:
        source = args.source.read_text(encoding="utf-8")
    except OSError as e:
        raise GenError(f"cannot read source: {e}")

    license_lines = extract_license(source)
    code = strip_comments(source)
    sections = split_sections(code)

    shaders: dict[int, str] = {}
    for layer in (0, 1, 2):
        recs = parse_records(sections[layer], layer)
        order, symtype = validate(layer, recs)
        shaders[layer] = gen_shader(layer, recs, order, symtype, license_lines)

    try:
        args.out.mkdir(parents=True, exist_ok=True)
        for layer in (0, 1, 2):
            path = args.out / f"cunny{layer}.comp"
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(shaders[layer])
    except OSError as e:
        raise GenError(f"cannot write output: {e}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except GenError as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(1)
