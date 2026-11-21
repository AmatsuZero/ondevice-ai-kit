"""Host-side sustained process observation (W8).

Purpose
-------
A small, stdlib-only observer for ONE explicitly selected debuggable project
process during an EXISTING workload. It never starts a workload, installs apps,
changes settings/permissions, launches capture/inference, mutates model/input/
package, infers GPU/native drain, or reads private media. It reads only
/proc/<pid>/stat and /proc/<pid>/status via `run-as <package>` with a numeric
pid and a safe argv (host subprocess uses shell=False; never shell=True).

Scope of measurement
--------------------
Sampled per-process VmRSS (KiB -> bytes), thread count, and RAW CPU ticks
(stat utime + stime). Clock-tick Hz is NOT assumed; ticks are NEVER converted
to time, energy, or a "budget". True peak memory, GPU allocations/energy, and
production budgets are NOT measured and remain unverified by this observer.

Identity binding
----------------
At bind time parse comm (field 2, robustly with spaces/right parens) and
starttime (field 22) from /proc/<pid>/stat; require comm == process_name;
record starttime. Each status sample is bracketed by an identity stat read
immediately BEFORE and immediately AFTER the status read; BOTH must match the
bound (package, pid, comm, starttime). A changed starttime/comm or a vanished
proc entry (observed on either the pre- or post-status stat) is PID reuse /
target exit -> a FAILED/incomplete terminal receipt, never a success, and the
possibly tainted status sample is NOT appended while earlier valid samples are
preserved. `process_name` means exact kernel `comm`, which may differ from
Android's full processName. cmdline and raw/private paths are never read or
logged.

Missing/inaccessible proc fields and command failures are reported as bounded
unsupported/error (code + stage), NOT as a 0 value and NOT as raw stderr.

Output contract
---------------
The output file is created exclusively (no overwrite of a pre-existing file).
A durable `running` receipt is written (flush+fsync) immediately after create,
BEFORE the first proc read. The final structured `completed`/`failed` receipt
is written even on an unexpected read failure. Failed receipts retain bounded
error code/stage and all samples collected so far, and never contain raw
command stderr. Duration scheduling is monotonic; missed intervals are NOT
backfilled with invented samples.

Receipt dict shape (all stages):
  {
    "schemaVersion": 1,
    "kind": "android-process-observation",
    "status": "running" | "completed" | "failed",
    "terminal": bool,                       # False only for the running receipt
    "target": {"adbSerial","package","pid","processName","starttime"},
    "config": {"durationS","intervalS"},
    "samples": [ {"tMonoS","rssBytes","threads","cpuTicks"}, ... ],
    "error": null | {"code","stage","message"},   # message never raw stderr
    "outputPath": str
  }

parse_proc_stat(stat_text) -> {"pid","comm","starttime","utime","stime"}
parse_proc_status(status_text) -> {"rss_bytes","threads"}
build_read_argv(...) -> list[str] host argv for subprocess.run(shell=False).

The implementation uses only the Python standard library.
"""

from __future__ import annotations

import json
import math
import os
import re
import subprocess
import time

SCHEMA_VERSION = 1
KIND = "android-process-observation"
KIBIBYTE = 1024
ALLOWED_PACKAGE_PREFIX = "io.ondeviceai."

# Bounded terminal statuses.
STATUS_RUNNING = "running"
STATUS_COMPLETED = "completed"
STATUS_FAILED = "failed"

# Bounded error codes and stages (never free-form, never raw stderr).
E_UNSUPPORTED_FIELD = "UNSUPPORTED_FIELD"
E_COMMAND_FAILED = "COMMAND_FAILED"
E_PID_REUSE = "PID_REUSE"
E_TARGET_EXIT = "TARGET_EXIT"
E_OUTPUT_EXISTS = "OUTPUT_EXISTS"
E_INVALID_TARGET = "INVALID_TARGET"
E_INTERNAL = "INTERNAL"
ERROR_CODES = frozenset({
    E_UNSUPPORTED_FIELD, E_COMMAND_FAILED, E_PID_REUSE, E_TARGET_EXIT,
    E_OUTPUT_EXISTS, E_INVALID_TARGET, E_INTERNAL,
})

S_INIT = "init"
S_OPEN_OUTPUT = "open-output"
S_BIND_IDENTITY = "bind-identity"
S_SAMPLING = "sampling"
S_FINALIZE = "finalize"
STAGES = frozenset({S_INIT, S_OPEN_OUTPUT, S_BIND_IDENTITY, S_SAMPLING, S_FINALIZE})


class ObservationError(Exception):
    """A bounded observation error carrying a code and a stage."""

    def __init__(self, code, stage, message=""):
        text = message or f"{code}@{stage}"
        super().__init__(text)
        self.code = code
        self.stage = stage
        self.message = text


class UnsupportedField(ObservationError):
    """A required /proc field is missing/inaccessible (never a 0 value)."""

    def __init__(self, field, stage=S_SAMPLING):
        super().__init__(E_UNSUPPORTED_FIELD, stage, f"unsupported field: {field}")
        self.field = field


_SERIAL_RE = re.compile(r"[A-Za-z0-9._:-]+\Z")
_PACKAGE_RE = re.compile(r"io\.ondeviceai\.(?:[A-Za-z_][A-Za-z0-9_]*)(?:\.[A-Za-z_][A-Za-z0-9_]*)*\Z")
_DIGITS_RE = re.compile(r"[0-9]+\Z")
_STATUS_RSS_RE = re.compile(r"VmRSS:\s*([0-9]+)\s+kB\Z")
_STATUS_THREADS_RE = re.compile(r"Threads:\s*([0-9]+)\Z")
_ERROR_MESSAGES = {
    E_UNSUPPORTED_FIELD: "required proc data is missing or malformed",
    E_COMMAND_FAILED: "host command could not be completed",
    E_PID_REUSE: "process identity changed during observation",
    E_TARGET_EXIT: "target process exited or became inaccessible",
    E_OUTPUT_EXISTS: "output path already exists",
    E_INVALID_TARGET: "target or observation configuration is invalid",
    E_INTERNAL: "unexpected observation failure",
}


def _validate_target(adb_serial, package, pid, filename):
    if type(adb_serial) is not str or not _SERIAL_RE.fullmatch(adb_serial):
        raise ObservationError(E_INVALID_TARGET, S_INIT)
    if type(package) is not str or not _PACKAGE_RE.fullmatch(package):
        raise ObservationError(E_INVALID_TARGET, S_INIT)
    if type(pid) is not int or pid <= 0:
        raise ObservationError(E_INVALID_TARGET, S_INIT)
    if type(filename) is not str or filename not in ("stat", "status"):
        raise ObservationError(E_INVALID_TARGET, S_INIT)


def _positive_finite_seconds(value):
    if type(value) not in (int, float):
        return None
    try:
        converted = float(value)
    except OverflowError:
        return None
    if not math.isfinite(converted) or converted <= 0:
        return None
    return converted


def _write_receipt(fd, receipt):
    data = (json.dumps(receipt, ensure_ascii=True, allow_nan=False,
                       separators=(",", ":")) + "\n").encode("utf-8")
    os.lseek(fd, 0, os.SEEK_SET)
    os.ftruncate(fd, 0)
    remaining = memoryview(data)
    while remaining:
        written = os.write(fd, remaining)
        if written <= 0:
            raise OSError("receipt write made no progress")
        remaining = remaining[written:]
    os.fsync(fd)


def _read_proc(runner, adb_serial, package, pid, filename, stage, timeout):
    argv = build_read_argv(adb_serial, package, pid, filename)
    try:
        result = runner(
            argv,
            shell=False,
            capture_output=True,
            text=True,
            check=False,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as exc:
        raise ObservationError(E_COMMAND_FAILED, stage) from exc
    except OSError as exc:
        raise ObservationError(E_COMMAND_FAILED, stage) from exc
    if result.returncode != 0:
        raise ObservationError(E_TARGET_EXIT, stage)
    if not isinstance(result.stdout, str):
        raise UnsupportedField(filename, stage)
    return result.stdout


def _clock_value(clock, previous):
    value = clock()
    if type(value) not in (int, float):
        raise ObservationError(E_INTERNAL, S_SAMPLING)
    value = float(value)
    if not math.isfinite(value) or (previous is not None and value < previous):
        raise ObservationError(E_INTERNAL, S_SAMPLING)
    return value


def _parse_stat_at(stat_text, stage):
    try:
        return parse_proc_stat(stat_text)
    except ObservationError as exc:
        raise ObservationError(exc.code, stage) from exc


def parse_proc_stat(stat_text: str) -> dict:
    """Parse /proc/<pid>/stat: pid, comm (robust), starttime(field22), raw utime/stime."""
    if not isinstance(stat_text, str):
        raise UnsupportedField("stat", S_BIND_IDENTITY)
    text = stat_text.strip()
    opening = text.find(" (")
    closing = text.rfind(")")
    if opening <= 0 or closing <= opening or closing + 1 >= len(text):
        raise UnsupportedField("stat", S_BIND_IDENTITY)
    pid_text = text[:opening]
    fields = text[closing + 1:].split()
    if not _DIGITS_RE.fullmatch(pid_text) or len(fields) < 20:
        raise UnsupportedField("stat", S_BIND_IDENTITY)
    try:
        pid_value = int(pid_text)
        if pid_value <= 0:
            raise ValueError
        utime_text, stime_text, starttime_text = fields[11], fields[12], fields[19]
        if not all(_DIGITS_RE.fullmatch(value) for value in
                   (utime_text, stime_text, starttime_text)):
            raise ValueError
        return {
            "pid": pid_value,
            "comm": text[opening + 2:closing],
            "starttime": int(starttime_text),
            "utime": int(utime_text),
            "stime": int(stime_text),
        }
    except (ValueError, IndexError) as exc:
        raise UnsupportedField("stat", S_BIND_IDENTITY) from exc


def parse_proc_status(status_text: str) -> dict:
    """Parse /proc/<pid>/status: VmRSS KiB->bytes and Threads; missing -> UnsupportedField."""
    if not isinstance(status_text, str):
        raise UnsupportedField("status")
    values = {}
    for line in status_text.splitlines():
        if line.startswith("VmRSS:"):
            values.setdefault("VmRSS", []).append(line)
        elif line.startswith("Threads:"):
            values.setdefault("Threads", []).append(line)
    parsed = {}
    for field, pattern in (("VmRSS", _STATUS_RSS_RE), ("Threads", _STATUS_THREADS_RE)):
        lines = values.get(field, [])
        if len(lines) != 1:
            raise UnsupportedField(field)
        match = pattern.fullmatch(lines[0])
        if match is None:
            raise UnsupportedField(field)
        try:
            parsed[field] = int(match.group(1))
        except ValueError as exc:
            raise UnsupportedField(field) from exc
    return {
        "rss_bytes": parsed["VmRSS"] * KIBIBYTE,
        "threads": parsed["Threads"],
    }


def build_read_argv(adb_serial: str, package: str, pid: int, filename: str) -> list:
    """Build a safe host argv list (shell=False) for one run-as /proc read.

    Validates: explicit non-empty adb_serial, package under io.ondeviceai.*,
    pid a positive strict int (not bool), filename in {stat,status}. Returns a
    list such as ["adb","-s",serial,"shell","run-as",package,"cat",
    "/proc/<pid>/<filename>"]. No discovery (-d/-e/devices), no cmdline, no
    shell metacharacters, never shell=True.
    """
    _validate_target(adb_serial, package, pid, filename)
    return [
        "adb", "-s", adb_serial, "shell", "run-as", package,
        "cat", f"/proc/{pid}/{filename}",
    ]


def observe(
    adb_serial: str,
    package: str,
    pid: int,
    process_name: str,
    duration_s: float,
    interval_s: float,
    output_path: str,
    *,
    runner=subprocess.run,
    clock=time.monotonic,
    sleeper=time.sleep,
) -> dict:
    """Run a finite, monotonic observation and return the final receipt dict.

    runner(argv, **kwargs) must return an object with .returncode and .stdout
    (real default: subprocess.run with shell=False, capture_output, text).
    clock() is a host monotonic reading in seconds; sleeper(dt) waits on that
    timeline. process_name is the exact kernel comm, not necessarily Android's
    full processName. Tests inject fakes for all three; no real ADB/sleep is
    performed by those tests.
    """
    duration = _positive_finite_seconds(duration_s)
    interval = _positive_finite_seconds(interval_s)
    if duration is None or interval is None:
        raise ObservationError(E_INVALID_TARGET, S_INIT)
    if type(process_name) is not str or not process_name or "\x00" in process_name:
        raise ObservationError(E_INVALID_TARGET, S_INIT)
    if type(output_path) is not str or not output_path or "\x00" in output_path:
        raise ObservationError(E_INVALID_TARGET, S_INIT)
    _validate_target(adb_serial, package, pid, "stat")
    _validate_target(adb_serial, package, pid, "status")

    receipt = {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "status": STATUS_RUNNING,
        "terminal": False,
        "target": {
            "adbSerial": adb_serial,
            "package": package,
            "pid": pid,
            "processName": process_name,
            "starttime": None,
        },
        "config": {"durationS": duration, "intervalS": interval},
        "samples": [],
        "error": None,
        "outputPath": output_path,
    }
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    nofollow = getattr(os, "O_NOFOLLOW", None)
    if nofollow is None:
        raise ObservationError(E_INTERNAL, S_OPEN_OUTPUT)
    try:
        fd = os.open(output_path, flags | nofollow, 0o600)
    except FileExistsError:
        receipt["status"] = STATUS_FAILED
        receipt["terminal"] = True
        receipt["error"] = {
            "code": E_OUTPUT_EXISTS,
            "stage": S_OPEN_OUTPUT,
            "message": _ERROR_MESSAGES[E_OUTPUT_EXISTS],
        }
        return receipt
    except OSError as exc:
        raise ObservationError(E_INTERNAL, S_OPEN_OUTPUT) from exc

    stage = S_BIND_IDENTITY
    try:
        _write_receipt(fd, receipt)
        try:
            initial_stat = _parse_stat_at(
                _read_proc(runner, adb_serial, package, pid, "stat",
                           S_BIND_IDENTITY, duration),
                S_BIND_IDENTITY,
            )
            if initial_stat["pid"] != pid:
                raise ObservationError(E_TARGET_EXIT, S_BIND_IDENTITY)
            if initial_stat["comm"] != process_name:
                raise ObservationError(E_INVALID_TARGET, S_BIND_IDENTITY)
            receipt["target"]["starttime"] = initial_stat["starttime"]
            _write_receipt(fd, receipt)

            last_clock = _clock_value(clock, None)
            start = last_clock
            previous_sample_time = None
            stage = S_SAMPLING
            while True:
                now = _clock_value(clock, last_clock)
                last_clock = now
                elapsed = now - start
                if elapsed >= duration:
                    break
                if previous_sample_time is not None:
                    due = previous_sample_time + interval
                    if due <= previous_sample_time:
                        raise ObservationError(E_INTERNAL, S_SAMPLING)
                    if now < due:
                        sleep_for = min(due - now, duration - elapsed)
                        sleeper(sleep_for)
                        now = _clock_value(clock, last_clock)
                        if sleep_for > 0 and now == last_clock:
                            raise ObservationError(E_INTERNAL, S_SAMPLING)
                        last_clock = now
                        if now - start >= duration:
                            break

                # Bracket the status sample with an identity stat read BEFORE
                # and AFTER the status read. Both must match the bound identity
                # (pid, comm, starttime); otherwise the observation fails and
                # the current, possibly tainted, status is NOT appended. CPU
                # ticks come from the identified pre-status stat.
                pre_stat = _parse_stat_at(
                    _read_proc(runner, adb_serial, package, pid, "stat",
                               S_SAMPLING, duration),
                    S_SAMPLING,
                )
                if pre_stat["pid"] != pid:
                    raise ObservationError(E_TARGET_EXIT, S_SAMPLING)
                if (pre_stat["comm"] != process_name or
                        pre_stat["starttime"] != receipt["target"]["starttime"]):
                    raise ObservationError(E_PID_REUSE, S_SAMPLING)
                status = parse_proc_status(
                    _read_proc(runner, adb_serial, package, pid, "status",
                               S_SAMPLING, duration)
                )
                post_stat = _parse_stat_at(
                    _read_proc(runner, adb_serial, package, pid, "stat",
                               S_SAMPLING, duration),
                    S_SAMPLING,
                )
                if post_stat["pid"] != pid:
                    raise ObservationError(E_TARGET_EXIT, S_SAMPLING)
                if (post_stat["comm"] != process_name or
                        post_stat["starttime"] != receipt["target"]["starttime"]):
                    raise ObservationError(E_PID_REUSE, S_SAMPLING)
                sample_time = _clock_value(clock, last_clock)
                last_clock = sample_time
                elapsed = sample_time - start
                previous_sample_time = sample_time
                if elapsed >= duration:
                    break
                receipt["samples"].append({
                    "tMonoS": elapsed,
                    "rssBytes": status["rss_bytes"],
                    "threads": status["threads"],
                    "cpuTicks": pre_stat["utime"] + pre_stat["stime"],
                })
                _write_receipt(fd, receipt)
        except ObservationError as exc:
            receipt["status"] = STATUS_FAILED
            receipt["terminal"] = True
            receipt["error"] = {
                "code": (exc.code if isinstance(exc.code, str) and
                         exc.code in ERROR_CODES else E_INTERNAL),
                "stage": (exc.stage if isinstance(exc.stage, str) and
                          exc.stage in STAGES else stage),
                "message": (_ERROR_MESSAGES.get(exc.code, _ERROR_MESSAGES[E_INTERNAL])
                            if isinstance(exc.code, str) else
                            _ERROR_MESSAGES[E_INTERNAL]),
            }
        except Exception:
            receipt["status"] = STATUS_FAILED
            receipt["terminal"] = True
            receipt["error"] = {
                "code": E_INTERNAL,
                "stage": stage,
                "message": _ERROR_MESSAGES[E_INTERNAL],
            }
        else:
            receipt["status"] = STATUS_COMPLETED
            receipt["terminal"] = True
            receipt["error"] = None
        stage = S_FINALIZE
        _write_receipt(fd, receipt)
        return receipt
    finally:
        os.close(fd)
