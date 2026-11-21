import math
import re
import shlex
import subprocess

_ADB_REPORT = "files/w3-qwen-terminal/report-{}.json"

_SERIAL_RE = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")
_PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$")
_RUN_ID_RE = re.compile(r"^[A-Za-z0-9-]{1,80}$")


def _default_runner(argv, timeout_seconds):
    return subprocess.run(
        argv, check=True, timeout=timeout_seconds,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).stdout


class AdbTerminalReportReader:
    def __init__(self, adb, serial, package, run_id, *, timeout_seconds=2.0, runner=None):
        if type(adb) is not str or not adb or "\x00" in adb:
            raise ValueError("adb must be a nonempty str without NUL")
        if type(serial) is not str or not _SERIAL_RE.fullmatch(serial):
            raise ValueError("invalid serial")
        if type(package) is not str or not _PACKAGE_RE.fullmatch(package):
            raise ValueError("invalid package")
        if type(run_id) is not str or not _RUN_ID_RE.fullmatch(run_id):
            raise ValueError("invalid run_id")
        if type(timeout_seconds) not in (int, float) or isinstance(timeout_seconds, bool):
            raise ValueError("timeout must be int or float")
        if not (0 < timeout_seconds <= 2.0) or not math.isfinite(timeout_seconds):
            raise ValueError("timeout must be finite and in (0, 2.0]")

        report = _ADB_REPORT.format(run_id)
        script = (
            "test -d files && test ! -L files"
            " && test -d files/w3-qwen-terminal && test ! -L files/w3-qwen-terminal"
            " && test -f {r} && test ! -L {r} && head -c 1048577 {r}"
        ).format(r=shlex.quote(report))
        remote = shlex.join(["run-as", package, "sh", "-c", script])

        self._adb = adb
        self._serial = serial
        self._remote = remote
        self._timeout = timeout_seconds
        self._runner = runner if runner is not None else _default_runner

    def read(self):
        out = self._runner(
            [self._adb, "-s", self._serial, "exec-out", self._remote],
            self._timeout,
        )
        if type(out) is not bytes or not (1 <= len(out) <= 1048576):
            raise ValueError("read result must be 1..1048576 bytes")
        return out