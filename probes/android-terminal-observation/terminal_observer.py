import hashlib
import json

_MAX_BYTES = 1048576
_MAX_PID = 2147483647
_MAX_NS = 9223372036854775807
_MAX_TICKS = 9223372036854775807
_MAX_RSS = 1610612736
_MIN_AVAIL = 536870912
_MAX_AVAIL = 9223372036854775807
_MAX_DEPTH = 1000
_RUNID_CHARS = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-")
_HEX = set("0123456789abcdef")


def _is_int(x):
    return type(x) is int


def _is_hex64(s):
    return type(s) is str and len(s) == 64 and all(c in _HEX for c in s)


def _finite_float(tok):
    v = float(tok)
    if v != v or v in (float('inf'), float('-inf')):
        raise ValueError("non-finite number")
    return v


def _parse_report(raw):
    if not isinstance(raw, (bytes, bytearray)):
        raise ValueError("report must be bytes")
    if len(raw) == 0 or len(raw) > _MAX_BYTES:
        raise ValueError("report length invalid")
    depth = 0
    maxd = 0
    instr = False
    esc = False
    for b in raw:
        if instr:
            if esc:
                esc = False
            elif b == 0x5C:
                esc = True
            elif b == 0x22:
                instr = False
        else:
            if b == 0x22:
                instr = True
            elif b == 0x7B or b == 0x5B:
                depth += 1
                if depth > maxd:
                    maxd = depth
            elif b == 0x7D or b == 0x5D:
                depth -= 1
    if maxd > _MAX_DEPTH:
        raise ValueError("excessive nesting")
    try:
        s = bytes(raw).decode("utf-8")
    except UnicodeDecodeError:
        raise ValueError("invalid utf-8")
    def pairs(p):
        seen = set()
        for k, v in p:
            if k in seen:
                raise ValueError("duplicate key")
            seen.add(k)
        return dict(p)
    def const(c):
        if c == "true":
            return True
        if c == "false":
            return False
        if c == "null":
            return None
        raise ValueError("non-standard constant")
    i = 0
    n = len(s)
    while i < n and s[i] in " \t\n\r":
        i += 1
    try:
        obj, end = json.JSONDecoder(object_pairs_hook=pairs, parse_constant=const, parse_float=_finite_float).raw_decode(s, i)
    except RecursionError:
        raise ValueError("invalid json")
    except (json.JSONDecodeError, ValueError):
        raise ValueError("invalid json")
    if any(ch not in " \t\n\r" for ch in s[end:]):
        raise ValueError("trailing text")
    if not isinstance(obj, dict):
        raise ValueError("root not object")
    return obj


def acknowledgement(first, second, binding, observation):
    if type(first) is not bytes or type(second) is not bytes:
        raise ValueError("first/second must be bytes")
    first = bytes(first)
    second = bytes(second)
    if len(first) == 0 or len(second) == 0:
        raise ValueError("empty report")
    if len(first) > _MAX_BYTES or len(second) > _MAX_BYTES:
        raise ValueError("report too large")
    if first != second:
        raise ValueError("reports differ")
    if not isinstance(binding, dict) or not isinstance(observation, dict):
        raise ValueError("binding/observation must be dict")

    report = _parse_report(first)

    run_id = binding.get("runId")
    fp = binding.get("sourceFingerprint")
    pid = binding.get("pid")
    start_ticks = binding.get("startTicks")
    sha = binding.get("terminalReportSha256")

    if not (type(run_id) is str and 1 <= len(run_id) <= 80 and all(c in _RUNID_CHARS for c in run_id)):
        raise ValueError("invalid runId")
    if not _is_hex64(fp):
        raise ValueError("invalid sourceFingerprint")
    if not _is_int(pid) or pid < 1 or pid > _MAX_PID:
        raise ValueError("invalid pid")
    if not (type(start_ticks) is str and start_ticks != ""
            and start_ticks[0] in "123456789"
            and all(c in "0123456789" for c in start_ticks)):
        raise ValueError("invalid startTicks")
    if int(start_ticks) > _MAX_TICKS:
        raise ValueError("startTicks overflow")
    if not _is_hex64(sha):
        raise ValueError("invalid terminalReportSha256")
    if hashlib.sha256(first).hexdigest() != sha:
        raise ValueError("hash mismatch")

    if report.get("runId") != run_id:
        raise ValueError("report runId mismatch")
    if report.get("sourceFingerprint") != fp:
        raise ValueError("report fp mismatch")
    if not _is_int(report.get("pid")) or report.get("pid") != pid:
        raise ValueError("report pid mismatch")
    if report.get("terminal") is not True:
        raise ValueError("report terminal invalid")
    if report.get("status") not in ("completed", "failed"):
        raise ValueError("report status invalid")

    obs = observation
    if not _is_int(obs.get("pidBefore")) or obs.get("pidBefore") != pid:
        raise ValueError("pidBefore mismatch")
    if not _is_int(obs.get("pidAfter")) or obs.get("pidAfter") != pid:
        raise ValueError("pidAfter mismatch")
    if not type(obs.get("startTicksBefore")) is str or obs.get("startTicksBefore") != start_ticks:
        raise ValueError("startTicksBefore mismatch")
    if not type(obs.get("startTicksAfter")) is str or obs.get("startTicksAfter") != start_ticks:
        raise ValueError("startTicksAfter mismatch")
    t_before = obs.get("uptimeBeforeNs")
    t_r1 = obs.get("firstReportReadNs")
    t_r2 = obs.get("secondReportReadNs")
    t_after = obs.get("uptimeAfterNs")
    for v in (t_before, t_r1, t_r2, t_after):
        if not _is_int(v) or v < 0 or v > _MAX_NS:
            raise ValueError("time invalid")
    if not (t_before <= t_r1 <= t_r2 <= t_after):
        raise ValueError("time order")
    rss = obs.get("rssBytes")
    if not _is_int(rss) or rss < 1 or rss > _MAX_RSS:
        raise ValueError("rss invalid")
    avail = obs.get("deviceAvailableBytes")
    if not _is_int(avail) or avail < _MIN_AVAIL or avail > _MAX_AVAIL:
        raise ValueError("avail invalid")
    if "terminalObservationGap" not in obs or obs.get("terminalObservationGap") is not None:
        raise ValueError("gap invalid")

    nl = chr(10)
    return (nl.join([
        "ODAI-TERMINAL-ACK-v1",
        run_id,
        fp,
        str(pid),
        start_ticks,
        sha,
    ])).encode("ascii") + bytes([10])
