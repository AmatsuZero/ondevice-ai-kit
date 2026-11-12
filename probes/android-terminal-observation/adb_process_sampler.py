import math
import re
import shlex
import subprocess


_SERIAL_RE = re.compile(r'[A-Za-z0-9._:-]{1,128}')
_PACKAGE_RE = re.compile(r'[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+')
_TICKS_RE = re.compile(r'[1-9][0-9]*')
_POS_DEC_RE = re.compile(r'[1-9][0-9]*')
_UNSIGNED_RE = re.compile(r'[0-9]+')

_MAX_SIGNED64 = 9223372036854775807
_MAX_PID = 2147483647
_MAX_RESPONSE = 65536
_HEAD_COUNT = '65537'


def _validate_config(adb, serial, package, pid, start_ticks, timeout_seconds):
    if type(adb) is not str or adb == '' or '\0' in adb:
        raise ValueError('adb must be a nonempty string without NUL')
    if type(serial) is not str or not _SERIAL_RE.fullmatch(serial):
        raise ValueError('invalid serial')
    if type(package) is not str or not _PACKAGE_RE.fullmatch(package):
        raise ValueError('invalid package')
    if type(pid) is not int or pid < 1 or pid > _MAX_PID:
        raise ValueError('invalid pid')
    if type(start_ticks) is not str or not _TICKS_RE.fullmatch(start_ticks):
        raise ValueError('invalid start_ticks')
    if int(start_ticks) > _MAX_SIGNED64:
        raise ValueError('invalid start_ticks')
    if (type(timeout_seconds) is not int and type(timeout_seconds) is not float) \
            or not math.isfinite(timeout_seconds) \
            or timeout_seconds <= 0 or timeout_seconds > 2.0:
        raise ValueError('invalid timeout_seconds')


class AdbProcessSampler:
    def __init__(self, adb, serial, package, pid, start_ticks, *,
                 timeout_seconds=2.0, runner=None):
        _validate_config(adb, serial, package, pid, start_ticks, timeout_seconds)
        self._adb = adb
        self._serial = serial
        self._package = package
        self._pid = pid
        self._start_ticks = start_ticks
        self._timeout = timeout_seconds
        self._runner = self._default_runner if runner is None else runner

    @staticmethod
    def _default_runner(argv, timeout_seconds):
        result = subprocess.run(argv, check=True, timeout=timeout_seconds,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return result.stdout

    def _read(self, path):
        remote = shlex.join(['run-as', self._package, 'head', '-c',
                             _HEAD_COUNT, path])
        argv = [self._adb, '-s', self._serial, 'exec-out', remote]
        data = self._runner(argv, self._timeout)
        if type(data) is not bytes:
            raise ValueError('response must be bytes')
        n = len(data)
        if n == 0 or n > _MAX_RESPONSE:
            raise ValueError('response size out of bounds')
        if 0 in data:
            raise ValueError('response contains NUL')
        try:
            text = data.decode('ascii')
        except UnicodeDecodeError:
            raise ValueError('response is not ASCII')
        return text

    def _read_stat(self, text):
        idx = text.rfind(') ')
        if idx < 0:
            raise ValueError('malformed stat')
        head = text[:idx]
        tail = text[idx + 2:]
        open_idx = head.find('(')
        if open_idx < 0:
            raise ValueError('malformed stat')
        pid_str = head[:open_idx].strip()
        if pid_str != str(self._pid):
            raise ValueError('stat pid mismatch')
        fields = tail.split()
        if len(fields) < 20:
            raise ValueError('stat has too few fields')
        starttime = fields[19]
        if not _POS_DEC_RE.fullmatch(starttime):
            raise ValueError('invalid starttime')
        if int(starttime) > _MAX_SIGNED64:
            raise ValueError('starttime overflow')
        if starttime != self._start_ticks:
            raise ValueError('starttime mismatch')

    def _parse_status(self, text):
        pid_line = None
        rss_line = None
        for line in text.splitlines():
            if line.startswith('Pid:'):
                if pid_line is not None:
                    raise ValueError('duplicate Pid')
                pid_line = line
            elif line.startswith('VmRSS:'):
                if rss_line is not None:
                    raise ValueError('duplicate VmRSS')
                rss_line = line
        if pid_line is None or rss_line is None:
            raise ValueError('missing Pid or VmRSS')
        m = re.match(r'^Pid:[ \t]*(.+?)[ \t]*$', pid_line)
        if m is None or m.group(1).strip() == '':
            raise ValueError('invalid Pid value')
        if m.group(1).strip() != str(self._pid):
            raise ValueError('status pid mismatch')
        m = re.match(r'^VmRSS:[ \t]*(.+?)[ \t]*$', rss_line)
        if m is None or m.group(1).strip() == '':
            raise ValueError('invalid VmRSS value')
        return self._parse_kb(m.group(1).strip())

    def _parse_meminfo(self, text):
        avail_line = None
        for line in text.splitlines():
            if line.startswith('MemAvailable:'):
                if avail_line is not None:
                    raise ValueError('duplicate MemAvailable')
                avail_line = line
        if avail_line is None:
            raise ValueError('missing MemAvailable')
        m = re.match(r'^MemAvailable:[ \t]*(.+?)[ \t]*$', avail_line)
        if m is None or m.group(1).strip() == '':
            raise ValueError('invalid MemAvailable value')
        return self._parse_kb(m.group(1).strip())

    @staticmethod
    def _parse_kb(raw):
        parts = raw.split()
        if len(parts) != 2:
            raise ValueError('invalid kB value')
        num, unit = parts
        if unit != 'kB':
            raise ValueError('invalid unit')
        if not _UNSIGNED_RE.fullmatch(num):
            raise ValueError('invalid number')
        val = int(num)
        result = val * 1024
        if result > _MAX_SIGNED64:
            raise ValueError('kB overflow')
        return result

    def sample(self):
        self._read_stat(self._read('/proc/%d/stat' % self._pid))
        rss = self._parse_status(self._read('/proc/%d/status' % self._pid))
        avail = self._parse_meminfo(self._read('/proc/meminfo'))
        self._read_stat(self._read('/proc/%d/stat' % self._pid))
        return {
            'pid': self._pid,
            'startTicks': self._start_ticks,
            'rssBytes': rss,
            'deviceAvailableBytes': avail,
        }