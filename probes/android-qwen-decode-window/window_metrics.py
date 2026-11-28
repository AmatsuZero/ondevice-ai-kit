"""解析单条 Android 内存窗口 dump，只依据原始样本重算指标。

约定：
  * 输入为单个窗口的 UTF-8 dump（label 只能是 'auto' 或 'forced'）；
  * 所有结果由 BASELINE/ENTER/EXIT/SAMPLE/AFTER 重算得到；
    SUMMARY 中的计数、读数、覆盖结论与独立计算结果逐项核对；
  * 任何不合规输入都抛出 ValueError。
"""

import hashlib
import re

_SIZE_CAP = 2 * 1024 * 1024
_MAX_SAMPLES = 12000
_MIN_INTERIOR = 3
_MAX_GAP_NS = 100_000_000
_ALLOWED_LABELS = frozenset(("auto", "forced"))
_NOTE = (
    "NOTE max_is_interior_only; sampled_max_is_not_true_peak; "
    "sampled_values_do_not_prove_allocator_or_leak"
)
_UINT = re.compile(r"[0-9]+\Z")


def _uint(text, where):
    if not isinstance(text, str) or not _UINT.match(text):
        raise ValueError("invalid nonnegative integer for %s: %r" % (where, text))
    return int(text)


def _field(fields, key, where):
    if key not in fields:
        raise ValueError("missing %s on %s" % (key, where))
    return fields[key]


def _record(line):
    parts = line.split()
    if not parts:
        raise ValueError("empty line")
    fields = {}
    for item in parts[1:]:
        if "=" not in item:
            raise ValueError("malformed field %r on %s" % (item, parts[0]))
        key, value = item.split("=", 1)
        if not key or not value or key in fields:
            raise ValueError("bad/duplicate key %r on %s" % (key, parts[0]))
        fields[key] = value
    return parts[0], fields


def parse_window(raw: bytes, label: str) -> dict:
    """返回该窗口重算后的指标；输入非法时抛出 ValueError。"""
    if type(raw) is not bytes:
        raise ValueError("raw must be bytes")
    if type(label) is not str:
        raise ValueError("label must be str")
    if label not in _ALLOWED_LABELS:
        raise ValueError("label must be 'auto' or 'forced'")
    if len(raw) > _SIZE_CAP:
        raise ValueError("dump exceeds 2 MiB size cap")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError("dump is not valid UTF-8") from exc

    lines = text.splitlines()
    pos = 0

    def take(cmd):
        nonlocal pos
        if pos >= len(lines):
            raise ValueError("missing %s record" % cmd)
        got, fields = _record(lines[pos])
        if got != cmd:
            raise ValueError("expected %s, got %s" % (cmd, got))
        pos += 1
        return fields

    # 严格固定的记录顺序
    begin = take("WINDOW_BEGIN")
    if _field(begin, "label", "WINDOW_BEGIN") != label:
        raise ValueError("WINDOW_BEGIN label mismatch")

    base = take("BASELINE")
    enter_rec = take("ENTER")

    samples = []
    while pos < len(lines):
        cmd, fields = _record(lines[pos])
        if cmd != "SAMPLE":
            break
        samples.append(fields)
        pos += 1
    if len(samples) > _MAX_SAMPLES:
        raise ValueError("too many samples")

    exit_rec = take("EXIT")
    after = take("AFTER")
    summary = take("SUMMARY")

    # 有且仅有一条 NOTE，其后紧跟 WINDOW_END，再无其它记录
    if pos >= len(lines) or lines[pos] != _NOTE:
        raise ValueError("expected exact NOTE record")
    pos += 1

    end = take("WINDOW_END")
    if pos != len(lines):
        raise ValueError("trailing records after WINDOW_END")

    if _field(end, "label", "WINDOW_END") != label:
        raise ValueError("WINDOW_END label mismatch")

    # 标量字段
    b_t0 = _uint(_field(base, "t0_ns", "BASELINE"), "BASELINE.t0_ns")
    b_t1 = _uint(_field(base, "t1_ns", "BASELINE"), "BASELINE.t1_ns")
    baseline_bytes = _uint(_field(base, "bytes", "BASELINE"), "BASELINE.bytes")
    enter_ns = _uint(_field(enter_rec, "t_ns", "ENTER"), "ENTER.t_ns")
    exit_ns = _uint(_field(exit_rec, "t_ns", "EXIT"), "EXIT.t_ns")
    a_t0 = _uint(_field(after, "t0_ns", "AFTER"), "AFTER.t0_ns")
    a_t1 = _uint(_field(after, "t1_ns", "AFTER"), "AFTER.t1_ns")
    after_bytes = _uint(_field(after, "bytes", "AFTER"), "AFTER.bytes")

    if not (b_t0 <= b_t1 <= enter_ns <= exit_ns <= a_t0 <= a_t1):
        raise ValueError("window time bounds are not monotonic")

    # SUMMARY 只做门禁：overflow / sampleErrors / label
    if _field(summary, "label", "SUMMARY") != label:
        raise ValueError("SUMMARY label mismatch")
    if _field(summary, "overflow", "SUMMARY") != "false":
        raise ValueError("SUMMARY reports overflow")
    if _uint(_field(summary, "sampleErrors", "SUMMARY"), "SUMMARY.sampleErrors") != 0:
        raise ValueError("SUMMARY reports sampleErrors")

    # 仅用严格内部样本重算最大间隙、计数与采样最大值
    last_read = 0
    previous = enter_ns
    maximum_gap_ns = 0
    interior_values = []

    for index, sample in enumerate(samples):
        where = "SAMPLE[%d]" % index
        idx = _uint(_field(sample, "idx", where), where + ".idx")
        t0 = _uint(_field(sample, "t0_ns", where), where + ".t0_ns")
        t1 = _uint(_field(sample, "t1_ns", where), where + ".t1_ns")
        value = _uint(_field(sample, "bytes", where), where + ".bytes")
        kind = _field(sample, "kind", where)

        if idx != index:
            raise ValueError("non-sequential sample index at %s" % where)
        if not (last_read <= t0 <= t1):
            raise ValueError("sample time bounds invalid at %s" % where)
        if kind not in ("INTERIOR", "EDGE"):
            raise ValueError("invalid sample category at %s" % where)

        is_interior = enter_ns < t0 and t1 < exit_ns
        if kind != ("INTERIOR" if is_interior else "EDGE"):
            raise ValueError("sample category/position mismatch at %s" % where)

        last_read = t1
        if is_interior:
            maximum_gap_ns = max(maximum_gap_ns, t1 - previous)
            previous = t0
            interior_values.append(value)

    maximum_gap_ns = max(maximum_gap_ns, exit_ns - previous)

    interior_count = len(interior_values)
    if interior_count < _MIN_INTERIOR:
        raise ValueError("insufficient interior coverage")
    if maximum_gap_ns > _MAX_GAP_NS:
        raise ValueError("conservative interior gap exceeds 100 ms")

    interior_max = max(interior_values)

    # SUMMARY 整数门禁：必须与独立重算值一致
    edge_count = len(samples) - interior_count
    if _uint(_field(summary, "interior", "SUMMARY"), "SUMMARY.interior") != interior_count:
        raise ValueError("SUMMARY interior mismatch")
    if _uint(_field(summary, "edge", "SUMMARY"), "SUMMARY.edge") != edge_count:
        raise ValueError("SUMMARY edge mismatch")
    if _uint(_field(summary, "total", "SUMMARY"), "SUMMARY.total") != len(samples):
        raise ValueError("SUMMARY total mismatch")
    if _uint(_field(summary, "maxSampledBytes", "SUMMARY"), "SUMMARY.maxSampledBytes") != interior_max:
        raise ValueError("SUMMARY maxSampledBytes mismatch")
    if _uint(_field(summary, "baselineBytes", "SUMMARY"), "SUMMARY.baselineBytes") != baseline_bytes:
        raise ValueError("SUMMARY baselineBytes mismatch")
    if _uint(_field(summary, "afterBytes", "SUMMARY"), "SUMMARY.afterBytes") != after_bytes:
        raise ValueError("SUMMARY afterBytes mismatch")
    if _field(summary, "coverage", "SUMMARY") != "OK":
        raise ValueError("SUMMARY coverage mismatch")

    return {
        "label": label,
        "enterNs": enter_ns,
        "exitNs": exit_ns,
        "durationNs": exit_ns - enter_ns,
        "baselineBytes": baseline_bytes,
        "afterBytes": after_bytes,
        "interiorMaxBytes": interior_max,
        "interiorCount": interior_count,
        "maximumGapNs": maximum_gap_ns,
        "afterMinusBaselineBytes": after_bytes - baseline_bytes,
        "interiorMaxMinusBaselineBytes": interior_max - baseline_bytes,
        "coverage": "OK",
        "rawSha256": hashlib.sha256(raw).hexdigest(),
    }
