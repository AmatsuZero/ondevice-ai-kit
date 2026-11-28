"""phase_align.py -- W3Phase 解码阶段对齐诊断（仅用 Python 3 标准库）。

公开接口：
    parse_record(s)                                -> (group, pid, tid, times)
    align_records(records, expected_pid,
                  enter_ns, exit_ns, samples)      -> JSON 友好的 dict

输入必须是 W3Phase 埋点发出的、逐字一致的 ``logcat -v raw`` 消息负载。
本模块不做分配器或内存泄漏推断：只有当采样区间完全、严格地落在某个
“两端标记齐全且有正时长”的阶段内时才归因；其余一律记为 unknown。

说明：step==1 的门控、被命名调用区间内不得存在未标记操作等约束属于
埋点/发射侧职责；本诊断只依据已给出的标记与采样做判定。
"""

import re

__all__ = ["parse_record", "align_records"]

# 逐字匹配的线格式：字段顺序、空格、逗号后无空格、十进制非负整数。
# 用 \Z 而非 $，避免末尾多出的换行被误接受。
_RECORD_RE = re.compile(
    r"v1 group=(?P<group>audio|text) "
    r"pid=(?P<pid>[0-9]+) "
    r"tid=(?P<tid>[0-9]+) "
    r"ns=(?P<ns>[0-9]+(?:,[0-9]+)*)\Z"
)

_EXPECTED_COUNTS = {"audio": 4, "text": 14}

# 阶段定义：(起点下标, 终点下标, 精确名称)。
# audio 的 1-2 之间是没有埋点的空档，刻意不命名，落入者归 unknown。
_AUDIO_PHASES = (
    (0, 1, "conv_forward"),
    (2, 3, "encoder_forward"),
)

_TEXT_PHASES = (
    (0, 1, "text_setup"),
    (1, 2, "empty_kv"),
    (2, 3, "prefill_setup"),
    (3, 4, "prefill_forward"),
    (4, 5, "prefill_unpack"),
    (5, 6, "prefill_kv_apply"),
    (6, 7, "prefill_kv_clear"),
    (7, 8, "first_token_setup"),
    (8, 9, "first_token_select"),
    (9, 10, "logits_release"),
    (10, 11, "incremental_setup"),
    (11, 12, "first_incremental_forward"),
    (12, 13, "first_incremental_unpack_and_kv_apply"),
)

_UNKNOWN = "unknown"
_PHASE_NAMES = tuple(name for _, _, name in _AUDIO_PHASES + _TEXT_PHASES)


def _is_int(value):
    """整数判定；bool 虽是 int 子类，但不视为合法整数。"""
    return isinstance(value, int) and not isinstance(value, bool)


def parse_record(s):
    """解析单条 W3Phase 原始负载，返回 (group, pid, tid, times)。

    group 为 "audio" 或 "text"；times 为十进制 CLOCK_BOOTTIME 纳秒的
    int 列表，0 表示缺测。任何不符合规范线格式的输入都抛 ValueError。
    """
    if not isinstance(s, str):
        raise ValueError("record must be a string")
    match = _RECORD_RE.match(s)
    if match is None:
        raise ValueError("malformed W3Phase record: %r" % (s,))
    group = match.group("group")
    pid = int(match.group("pid"))
    tid = int(match.group("tid"))
    times = [int(part) for part in match.group("ns").split(",")]
    expected = _EXPECTED_COUNTS[group]
    if len(times) != expected:
        raise ValueError(
            "group %s expects %d timestamps, got %d" % (group, expected, len(times))
        )
    return group, pid, tid, times


def _measured_prefix(times):
    """校验组内时间戳：0 只能作为末尾缺测，非零部分非递减。

    返回非零（已测）前缀；内部空洞或时间倒退都会抛 ValueError。
    """
    first_zero = len(times)
    for i, value in enumerate(times):
        if value == 0:
            first_zero = i
            break
    if any(value != 0 for value in times[first_zero:]):
        raise ValueError("interior hole: zero timestamp followed by nonzero")
    prefix = times[:first_zero]
    previous = 0
    for value in prefix:
        if value < previous:
            raise ValueError("timestamps are not non-decreasing")
        previous = value
    return prefix


def _classify(t0, t1, audio_times, text_times):
    """把 [t0, t1] 归入唯一严格包含的阶段，否则返回 unknown。"""
    hits = []
    for phases, times in ((_AUDIO_PHASES, audio_times), (_TEXT_PHASES, text_times)):
        for lo_i, hi_i, name in phases:
            lo = times[lo_i]
            hi = times[hi_i]
            # 缺标记或零时长区间都不可归因。
            if lo == 0 or hi == 0 or lo >= hi:
                continue
            # 严格内部：lo < t0 <= t1 < hi。
            if lo < t0 and t1 < hi:
                hits.append(name)
    if len(hits) == 1:
        return hits[0]
    # 无命中（触及/跨越边界、空档、首尾之外）或多个命中（歧义）均算 unknown。
    return _UNKNOWN


def align_records(records, expected_pid, enter_ns, exit_ns, samples):
    # 1) 记录集合：恰好两条，且一条 audio、一条 text。
    if not isinstance(records, (list, tuple)) or len(records) != 2:
        raise ValueError("records must contain exactly two raw messages")
    parsed = [parse_record(record) for record in records]

    groups = {}
    for group, pid, tid, times in parsed:
        if group in groups:
            raise ValueError("duplicate group: %s" % group)
        groups[group] = (pid, tid, times)
    if set(groups) != {"audio", "text"}:
        raise ValueError("expected exactly one audio group and one text group")
    audio_pid, audio_tid, audio_times = groups["audio"]
    text_pid, text_tid, text_times = groups["text"]

    # 2) pid/tid 合法性及跨组一致性：同一进程、同一线程、且等于期望 pid。
    if not _is_int(expected_pid) or expected_pid <= 0:
        raise ValueError("expected_pid must be a positive integer")
    for pid, tid in ((audio_pid, audio_tid), (text_pid, text_tid)):
        if pid <= 0 or tid <= 0:
            raise ValueError("pid/tid must be positive")
    if audio_pid != expected_pid or text_pid != expected_pid:
        raise ValueError("record pid does not match expected_pid")
    if audio_pid != text_pid:
        raise ValueError("pid differs between groups")
    if audio_tid != text_tid:
        raise ValueError("tid differs between groups")

    # 3) 解码窗口与标记位置：所有已测标记必须落在 [enter_ns, exit_ns] 内，
    #    且各组内部非递减（由 _measured_prefix 校验），保证整体时间线一致。
    if not _is_int(enter_ns) or not _is_int(exit_ns):
        raise ValueError("enter_ns/exit_ns must be integers")
    if enter_ns < 0 or exit_ns < enter_ns:
        raise ValueError("invalid decode window")
    for group, times in (("audio", audio_times), ("text", text_times)):
        for stamp in _measured_prefix(times):
            if stamp <= enter_ns or stamp >= exit_ns:
                raise ValueError("ambiguous marker at decode window boundary in %s group" % group)
    if any(text_times):
        if not all(audio_times):
            raise ValueError("audio markers incomplete while text markers present")
        if audio_times[-1] > text_times[0]:
            raise ValueError("audio decode marker overlaps first text marker")

    # 4) 采样：非空、整数非负、严格窗口内、idx 严格递增、区间单调不重叠。
    try:
        iterator = iter(samples)
    except TypeError:
        raise ValueError("samples must be a nonempty iterable")
    normalized = []
    for item in iterator:
        if not isinstance(item, (tuple, list)) or len(item) != 4:
            raise ValueError("each sample must be (idx, t0, bytes, t1)")
        idx, t0, nbytes, t1 = item
        if any(not _is_int(v) or v < 0 for v in (idx, t0, nbytes, t1)):
            raise ValueError("sample fields must be nonnegative integers")
        if t0 > t1:
            raise ValueError("sample bracket is not monotonic")
        if not (enter_ns < t0 and t1 < exit_ns):
            raise ValueError("sample is not strictly interior to decode window")
        normalized.append((idx, t0, nbytes, t1))
    if not normalized:
        raise ValueError("samples must be nonempty")
    for previous, current in zip(normalized, normalized[1:]):
        if current[0] <= previous[0]:
            raise ValueError("sample idx must be strictly increasing")
        if current[1] < previous[3]:
            raise ValueError("sample brackets overlap")

    # 5) 归因与统计。
    stats = {name: {"count": 0, "max_bytes": None} for name in _PHASE_NAMES}
    unknown_count = 0
    classified = []
    for idx, t0, nbytes, t1 in normalized:
        phase = _classify(t0, t1, audio_times, text_times)
        if phase == _UNKNOWN:
            unknown_count += 1
        else:
            slot = stats[phase]
            slot["count"] += 1
            if slot["max_bytes"] is None or nbytes > slot["max_bytes"]:
                slot["max_bytes"] = nbytes
        classified.append((idx, nbytes, phase))

    global_max = max(nbytes for _, nbytes, _ in classified)
    maxima = [
        {"idx": idx, "bytes": nbytes, "phase": phase}
        for idx, nbytes, phase in classified
        if nbytes == global_max
    ]

    return {
        "pid": expected_pid,
        "tid": audio_tid,
        "groups": {
            "audio": list(audio_times),
            "text": list(text_times),
        },
        "window": {"enter_ns": enter_ns, "exit_ns": exit_ns},
        "sample_count": len(normalized),
        "unknown_count": unknown_count,
        "global_max_bytes": global_max,
        "phases": stats,
        "maxima": maxima,
    }
