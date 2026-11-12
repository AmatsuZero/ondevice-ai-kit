import math
import re
import shlex
import subprocess


class RemoteAckPublicationError(RuntimeError):
    def __init__(self, message, *, phase, stage_may_remain, publication_possible, durability_verified):
        super().__init__(message)
        self.phase = phase
        self.stage_may_remain = stage_may_remain
        self.publication_possible = publication_possible
        self.durability_verified = durability_verified


_SERIAL_RE = re.compile(r'^[A-Za-z0-9._:-]{1,128}$')
_PACKAGE_RE = re.compile(r'^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$')
_RUN_ID_RE = re.compile(r'^[A-Za-z0-9-]{1,80}$')


def _default_runner(argv, timeout_seconds, stdin_bytes):
    return subprocess.run(argv, input=stdin_bytes, check=True, timeout=timeout_seconds, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


class AdbTerminalAckPublisher:
    def __init__(self, adb, serial, package, run_id, *, timeout_seconds=2.0, runner=None):
        if type(adb) is not str or len(adb) == 0 or '\x00' in adb:
            raise ValueError('adb must be a non-empty string without NUL')
        if type(serial) is not str or not _SERIAL_RE.fullmatch(serial):
            raise ValueError('serial must be ASCII [A-Za-z0-9._:-]{1,128}')
        if type(package) is not str or not _PACKAGE_RE.fullmatch(package):
            raise ValueError('package must be ASCII dotted identifier')
        if type(run_id) is not str or not _RUN_ID_RE.fullmatch(run_id):
            raise ValueError('run_id must be ASCII [A-Za-z0-9-]{1,80}')
        if type(timeout_seconds) not in (int, float) or (isinstance(timeout_seconds, float) and not math.isfinite(timeout_seconds)) or timeout_seconds <= 0 or timeout_seconds > 2:
            raise ValueError('timeout_seconds must be a finite number with 0 < value <= 2')
        self._adb = adb
        self._serial = serial
        self._package = package
        self._run_id = run_id
        self._timeout_seconds = timeout_seconds
        self._runner = runner if runner is not None else _default_runner
        self._used = False

    def publish(self, payload, *, before_link):
        if self._used:
            raise RuntimeError('publish already consumed this instance')
        if type(payload) is not bytes or not (1 <= len(payload) <= 1024):
            raise ValueError('payload must be bytes with 1 <= len(payload) <= 1024')
        if not callable(before_link):
            raise ValueError('before_link must be callable')
        self._used = True
        adb = self._adb
        serial = self._serial
        package = self._package
        dir_path = 'files/w3-qwen-terminal'
        final = dir_path + '/ack-' + self._run_id + '.txt'
        stage = final + '.stage'
        timeout = self._timeout_seconds
        runner = self._runner
        script = ('test -d ' + shlex.quote('files') + ' && ' + '! test -L ' + shlex.quote('files') + ' && ' + 'test -d ' + shlex.quote(dir_path) + ' && ' + '! test -L ' + shlex.quote(dir_path) + ' && ' + 'test ! -e ' + shlex.quote(final) + ' && ' + '! test -L ' + shlex.quote(final) + ' && ' + 'test ! -e ' + shlex.quote(stage) + ' && ' + '! test -L ' + shlex.quote(stage) + ' && ' + '(umask 077 && set -C && cat > ' + shlex.quote(stage) + ')')
        argv = [adb, '-s', serial, 'exec-in', shlex.join(['run-as', package, 'sh', '-c', script])]
        try:
            resp = runner(argv, timeout, payload)
        except Exception as exc:
            raise RemoteAckPublicationError('stage-write transport failed', phase='stage-write', stage_may_remain=True, publication_possible=False, durability_verified=False) from exc
        if not type(resp) is bytes or resp != b'':
            raise RemoteAckPublicationError('stage-write invalid response', phase='stage-write', stage_may_remain=True, publication_possible=False, durability_verified=False) from ValueError('expected empty bytes response')
        script = ('test -f ' + shlex.quote(stage) + ' && ' + 'test ! -L ' + shlex.quote(stage) + ' && ' + 'head -c 1025 ' + shlex.quote(stage))
        argv = [adb, '-s', serial, 'exec-out', shlex.join(['run-as', package, 'sh', '-c', script])]
        try:
            resp = runner(argv, timeout, None)
        except Exception as exc:
            raise RemoteAckPublicationError('stage-verify transport failed', phase='stage-verify', stage_may_remain=True, publication_possible=False, durability_verified=False) from exc
        if not type(resp) is bytes:
            raise RemoteAckPublicationError('stage-verify invalid response', phase='stage-verify', stage_may_remain=True, publication_possible=False, durability_verified=False) from ValueError('expected bytes response')
        if resp != payload:
            raise RemoteAckPublicationError('stage-verify payload mismatch', phase='stage-verify', stage_may_remain=True, publication_possible=False, durability_verified=False) from ValueError('stage content does not exactly match payload')
        argv = [adb, '-s', serial, 'exec-out', shlex.join(['run-as', package, 'toybox', 'fsync', stage])]
        try:
            resp = runner(argv, timeout, None)
        except Exception as exc:
            raise RemoteAckPublicationError('stage-sync transport failed', phase='stage-sync', stage_may_remain=True, publication_possible=False, durability_verified=False) from exc
        if not type(resp) is bytes or resp != b'':
            raise RemoteAckPublicationError('stage-sync invalid response', phase='stage-sync', stage_may_remain=True, publication_possible=False, durability_verified=False) from ValueError('expected empty bytes response')
        try:
            before_link()
        except Exception as exc:
            raise RemoteAckPublicationError('before_link callback failed', phase='before-link', stage_may_remain=True, publication_possible=False, durability_verified=False) from exc
        argv = [adb, '-s', serial, 'exec-out', shlex.join(['run-as', package, 'toybox', 'ln', '-T', stage, final])]
        try:
            resp = runner(argv, timeout, None)
        except Exception as exc:
            raise RemoteAckPublicationError('publish-link transport failed', phase='publish-link', stage_may_remain=True, publication_possible=True, durability_verified=False) from exc
        if not type(resp) is bytes or resp != b'':
            raise RemoteAckPublicationError('publish-link invalid response', phase='publish-link', stage_may_remain=True, publication_possible=True, durability_verified=False) from ValueError('expected empty bytes response')
        argv = [adb, '-s', serial, 'exec-out', shlex.join(['run-as', package, 'toybox', 'fsync', dir_path])]
        try:
            resp = runner(argv, timeout, None)
        except Exception as exc:
            raise RemoteAckPublicationError('directory-sync transport failed', phase='directory-sync', stage_may_remain=True, publication_possible=True, durability_verified=False) from exc
        if not type(resp) is bytes or resp != b'':
            raise RemoteAckPublicationError('directory-sync invalid response', phase='directory-sync', stage_may_remain=True, publication_possible=True, durability_verified=False) from ValueError('expected empty bytes response')
        return {'published': True, 'durability_verified': True, 'stage_retained': True, 'final_relative': final, 'stage_relative': stage}