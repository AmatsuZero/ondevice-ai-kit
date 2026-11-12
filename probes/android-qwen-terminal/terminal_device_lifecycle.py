import hashlib
import json
import re
import shlex
import subprocess
import time
from terminal_polling import _PollClock, _binding_snapshot, _parse_report
from terminal_file_io import write_new_durable
from adb_terminal_observation import _preflight_outputs
from adb_process_sampler import AdbProcessSampler, _validate_config
from adb_terminal_report_reader import AdbTerminalReportReader
from terminal_observation_coordinator import _validate_sample

def _startup_config(adb, serial, package, run_id, source_fingerprint,
                    process_poll, clock, started_ns, runner, sleep):
    _binding_snapshot({
        'runId': run_id,
        'sourceFingerprint': source_fingerprint,
        'pid': 1,
        'startTicks': '1',
    })
    _validate_config(adb, serial, package, 1, '1', 2.0)
    if not callable(process_poll):
        raise TypeError('process_poll must be callable')
    if runner is not None and not callable(runner):
        raise TypeError('runner must be None or callable')
    if not callable(sleep):
        raise TypeError('sleep must be callable')
    if type(clock) is not _PollClock:
        raise TypeError('clock must be _PollClock')
    if type(started_ns) is not int:
        raise TypeError('started_ns must be int')
    if started_ns < 0 or started_ns > 2 ** 63 - 1:
        raise ValueError('started_ns out of int64 range')
    if type(clock.start_ns) is not int:
        raise TypeError('clock.start_ns must be int')
    if clock.start_ns != started_ns:
        raise ValueError('started_ns must equal clock.start_ns')
    if type(clock.last_ns) is not int:
        raise TypeError('clock.last_ns must be int')
    if clock.last_ns < 0 or clock.last_ns > 2 ** 63 - 1:
        raise ValueError('clock.last_ns out of range')
    if clock.last_ns < started_ns:
        raise ValueError('clock.last_ns must be >= started_ns')
    return None

def _startup_guard(clock, started_ns):
    now = clock()
    if now - started_ns > 20000000000:
        raise ValueError('startup guard exceeded 20s')
    return now

def _default_startup_runner(argv, timeout_seconds, stdin_payload):
    proc = subprocess.run(
        argv,
        input=stdin_payload,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout_seconds,
    )
    if proc.returncode == 0:
        return proc.stdout
    if (proc.returncode == 1 and len(argv) == 6 and
            argv[1] == '-s' and argv[3] == 'shell' and
            argv[4] == 'pidof' and proc.stdout == b''):
        return b''
    proc.check_returncode()
    return proc.stdout

def _startup_pid(raw):
    if type(raw) is not bytes:
        raise TypeError('raw must be bytes')
    if raw == b'':
        return None
    if len(raw) > 64:
        raise ValueError('raw length out of range')
    if 0 in raw:
        raise ValueError('raw contains NUL')
    text = raw.decode('ascii')
    if text.endswith(chr(10)):
        body = text[:-1]
    else:
        body = text
    if not body.isdigit() or body[0] == '0':
        raise ValueError('invalid pid')
    value = int(body)
    if value > 2 ** 31 - 1:
        raise ValueError('pid out of range')
    return value

def _startup_ticks(raw, pid):
    if type(raw) is not bytes:
        raise TypeError('raw must be bytes')
    if len(raw) < 1 or len(raw) > 65536:
        raise ValueError('raw length out of range')
    if 0 in raw:
        raise ValueError('raw contains NUL')
    text = raw.decode('ascii')
    lparen = text.find('(')
    if lparen == -1 or text[:lparen] != str(pid) + ' ':
        raise ValueError('pid prefix mismatch')
    close = text.rfind(') ')
    if close == -1:
        raise ValueError('missing ) ')
    tail = text[close + 2:]
    fields = tail.split()
    if len(fields) < 20:
        raise ValueError('not enough fields')
    ticks = fields[19]
    if not ticks.isdigit() or ticks[0] == '0':
        raise ValueError('invalid ticks')
    if int(ticks) > 2 ** 63 - 1:
        raise ValueError('ticks out of int64 range')
    return ticks

def _startup_presence_argv(adb, serial, package, run_id):
    nl = chr(92) + 'n'
    report = 'files/w3-qwen-terminal/report-' + run_id + '.json'
    script = (
        "if test -L 'files' || test -L 'files/w3-qwen-terminal' || "
        "test -L '" + report + "'; then exit 17; fi; "
        "if test -e '" + report + "'; then printf 'ODAI_STARTUP_PRESENT" + nl + "'; "
        "else printf 'ODAI_STARTUP_ABSENT" + nl + "'; fi"
    )
    remote = shlex.join(['run-as', package, 'sh', '-c', script])
    return [adb, '-s', serial, 'exec-out', remote]

def _startup_json(value):
    return json.dumps(value, sort_keys=True, allow_nan=False).encode('utf-8')

def wait_for_startup(root, *, adb, serial, package, run_id, source_fingerprint,
                     process_poll, clock, started_ns, runner=None, sleep=time.sleep):
    _startup_config(adb, serial, package, run_id, source_fingerprint,
                    process_poll, clock, started_ns, runner, sleep)

    _preflight_outputs(root, {
        'start': 'startup-start.json',
        'report': 'startup-report.json',
        'observed': 'startup-observed.json',
    })

    start_payload = _startup_json({
        'runId': run_id,
        'sourceFingerprint': source_fingerprint,
        'startedNs': started_ns,
    })
    write_new_durable(root, 'startup-start.json', start_payload)

    def transport(argv, timeout, stdin):
        _startup_guard(clock, started_ns)
        if runner is None:
            result = _default_startup_runner(argv, timeout, stdin)
        else:
            result = runner(argv, timeout, stdin)
        _startup_guard(clock, started_ns)
        if type(result) is not bytes:
            raise ValueError('startup transport result must be bytes')
        return result

    def guarded_poll():
        _startup_guard(clock, started_ns)
        outcome = process_poll()
        _startup_guard(clock, started_ns)
        if outcome is None:
            return None
        if type(outcome) is int:
            raise RuntimeError('process exited early with rc=%r' % (outcome,))
        raise ValueError('invalid process_poll result: %r' % (outcome,))

    def guarded_sleep(duration):
        _startup_guard(clock, started_ns)
        sleep(duration)
        _startup_guard(clock, started_ns)

    def two_arg_runner(argv, timeout):
        return transport(argv, timeout, None)

    deadline = clock.start_ns + 20000000000
    pid = None
    last_ticks = None
    sample = None
    binding = None
    report_raw = None
    while report_raw is None:
        guarded_poll()
        _startup_guard(clock, started_ns)

        pid_raw = transport([adb, '-s', serial, 'shell', 'pidof', package], 2.0, None)
        candidate = _startup_pid(pid_raw)
        if pid is None:
            if candidate is None:
                guarded_sleep(0.25)
                continue
            pid = candidate
        elif candidate is None or candidate != pid:
            raise RuntimeError('pid absent or changed: %r -> %r' % (pid, candidate))

        stat_argv = [adb, '-s', serial, 'exec-out',
                     shlex.join(['run-as', package, 'head', '-c', '65537',
                                 '/proc/' + str(pid) + '/stat'])]
        stat_raw = transport(stat_argv, 2.0, None)
        ticks = _startup_ticks(stat_raw, pid)
        if last_ticks is None:
            last_ticks = ticks
        elif ticks != last_ticks:
            raise ValueError('startup ticks changed before sample: %s -> %s'
                             % (last_ticks, ticks))

        binding = _binding_snapshot({
            'runId': run_id,
            'sourceFingerprint': source_fingerprint,
            'pid': pid,
            'startTicks': last_ticks,
        })
        sampler = AdbProcessSampler(adb, serial, package, pid, last_ticks,
                                    timeout_seconds=2.0, runner=two_arg_runner)
        measured = sampler.sample()
        _validate_sample(measured, binding)
        sample = measured

        presence_argv = _startup_presence_argv(adb, serial, package, run_id)
        now = _startup_guard(clock, started_ns)
        pres_raw = transport(presence_argv, 2.0, None)
        if pres_raw == b'ODAI_STARTUP_PRESENT\n':
            reader = AdbTerminalReportReader(adb, serial, package, run_id,
                                             timeout_seconds=2.0, runner=two_arg_runner)
            report_raw = reader.read()
        elif pres_raw == b'ODAI_STARTUP_ABSENT\n':
            if now > deadline:
                raise ValueError('startup terminal report not present within 20s')
            guarded_sleep(0.25)
        else:
            raise ValueError('unexpected startup presence marker: %r' % (pres_raw,))

    report = _parse_report(report_raw, binding)
    report_sha = hashlib.sha256(report_raw).hexdigest()
    initial_progress = {
        'phase': report['phase'],
        'activeCaseIndex': report['activeCaseIndex'],
        'startedNs': started_ns,
    }

    _startup_guard(clock, started_ns)
    write_new_durable(root, 'startup-report.json', report_raw)
    observed = _startup_json({
        'binding': binding,
        'reportSHA256': report_sha,
        'sample': sample,
        'initialProgress': initial_progress,
    })
    _startup_guard(clock, started_ns)
    write_new_durable(root, 'startup-observed.json', observed)
    _startup_guard(clock, started_ns)

    return {
        'binding': binding,
        'initial_progress': initial_progress,
        'reportSHA256': report_sha,
        'sample': sample,
    }

def parse_instrumentation_finish(raw, *, expected_report_sha256, terminal_status):
    import re
    if type(raw) is not bytes:
        raise TypeError('raw must be bytes')
    if not (1 <= len(raw) <= 1048576):
        raise ValueError('raw length must be 1..1048576')
    if b'\x00' in raw:
        raise ValueError('raw contains NUL')
    try:
        text = raw.decode('utf-8', 'strict')
    except UnicodeDecodeError:
        raise ValueError('raw is not valid UTF-8')
    if type(expected_report_sha256) is not str or re.fullmatch(r'[0-9a-f]{64}', expected_report_sha256) is None:
        raise ValueError('expected_report_sha256 must be lowercase 64-hex')
    if type(terminal_status) is not str or terminal_status not in ('completed', 'failed'):
        raise ValueError('terminal_status must be completed or failed')
    expected_code = -1 if terminal_status == 'completed' else 0
    RESULT = 'INSTRUMENTATION_RESULT:'
    CODE = 'INSTRUMENTATION_CODE:'
    ALLOWED = ('terminalAckAttempted', 'terminalAckReceived', 'terminalReportSha256')
    fields = {}
    code = None
    for raw_line in text.split('\n'):
        line = raw_line[:-1] if raw_line.endswith('\r') else raw_line
        if line == '':
            continue
        if line.startswith('INSTRUMENTATION_FAILED:') or line.startswith('INSTRUMENTATION_ABORTED:'):
            raise ValueError('instrumentation failure/abort envelope present')
        if line.startswith('INSTRUMENTATION_STATUS:') or line.startswith('INSTRUMENTATION_STATUS_CODE:'):
            continue
        if line.startswith(RESULT):
            content = line[len(RESULT):].removeprefix(' ')
            if '=' not in content:
                raise ValueError('malformed result line')
            key, value = content.split('=', 1)
            if key not in ALLOWED:
                raise ValueError('unknown result field: ' + key)
            if key in fields:
                raise ValueError('duplicate result field: ' + key)
            fields[key] = value
        elif line.startswith(CODE):
            if code is not None:
                raise ValueError('duplicate code line')
            c = line[len(CODE):].removeprefix(' ')
            if c not in ('-1', '0'):
                raise ValueError('invalid instrumentation code: ' + c)
            code = int(c)
        elif line.startswith('INSTRUMENTATION_'):
            raise ValueError('unrecognized INSTRUMENTATION protocol line')
        else:
            if line.startswith('Error:'):
                raise ValueError('Error envelope present')
            continue
    for k in ALLOWED:
        if k not in fields:
            raise ValueError('missing result field: ' + k)
    if fields['terminalAckAttempted'] != 'true':
        raise ValueError('terminalAckAttempted must be exactly true')
    if fields['terminalAckReceived'] != 'true':
        raise ValueError('terminalAckReceived must be exactly true')
    if fields['terminalReportSha256'] != expected_report_sha256:
        raise ValueError('terminalReportSha256 mismatch')
    if code is None:
        raise ValueError('missing instrumentation code')
    if code != expected_code:
        raise ValueError('instrumentation code does not match terminal_status')
    return {
        'terminalAckAttempted': True,
        'terminalAckReceived': True,
        'terminalReportSha256': expected_report_sha256,
        'instrumentationCode': code,
        'terminalStatus': terminal_status,
        'qualityAccepted': False,
    }


def run_diagnostic(root, *, adb, serial, source_fingerprint, verify_launch,
                   before_transport, stop_alias, runner=None,
                   popen=subprocess.Popen, monotonic_ns=time.monotonic_ns,
                   sleep=time.sleep):
    import json as _json
    import hashlib as _hashlib
    from pathlib import Path as _Path
    import terminal_polling as _tp
    import terminal_file_io as _tf

    APP = 'io.ondeviceai.acceptance.qwenterminal'
    RUN = 'w3-qwen-terminal-20261112-01'
    RUNNER = APP + '.test/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation'

    def _require_callable(name, value, allow_none):
        if value is None:
            if allow_none:
                return
            raise TypeError(name + ' must be callable')
        if not callable(value):
            raise TypeError(name + ' must be callable')

    _require_callable('verify_launch', verify_launch, False)
    _require_callable('before_transport', before_transport, False)
    _require_callable('stop_alias', stop_alias, False)
    _require_callable('runner', runner, True)
    _require_callable('popen', popen, False)
    _require_callable('monotonic_ns', monotonic_ns, False)
    _require_callable('sleep', sleep, False)

    _validate_config(adb, serial, APP, 1, '1', 2.0)
    _binding_snapshot({'runId': RUN, 'sourceFingerprint': source_fingerprint,
                       'pid': 1, 'startTicks': '1'})

    _preflight_outputs(root, {
        'run_started': 'run-started.json',
        'instr_log': 'instrumentation.log',
        'host_result': 'host-result.json',
        'startup_start': 'startup-start.json',
        'startup_report': 'startup-report.json',
        'startup_observed': 'startup-observed.json',
        'poll_start': 'poll-start.json',
        'terminal_observed': 'terminal-observed.json',
        'terminal_receipt': 'terminal-receipt.json',
        'terminal_ack': 'terminal-ack.txt',
        'terminal_before_link': 'terminal-before-link.json',
    })

    verify_launch()

    clock = _tp._PollClock(monotonic_ns)
    started = clock()

    consumed = False
    proc = None
    log = None

    def guarded(argv, timeout, stdin):
        before_transport()
        if runner is None:
            return _default_startup_runner(argv, timeout, stdin)
        return runner(argv, timeout, stdin)

    try:
        try:
            _tf.write_new_durable(root, 'run-started.json', _startup_json({
                'runId': RUN,
                'sourceFingerprint': source_fingerprint,
                'startedNs': started,
                'automaticRetry': False,
                'plannedConstructors': 1,
                'maxPlannedStreams': 2,
                'maxPlannedDecode': 2,
                'plannedMicrophone': 0,
                'inputCount': 1,
            }))
        except Exception as _w_exc:
            if getattr(_w_exc, 'published', False) is True:
                consumed = True
            raise
        consumed = True

        log = open(_Path(root) / 'instrumentation.log', 'xb')

        before_transport()

        argv = [adb, '-s', serial, 'shell', 'am', 'instrument', '-w', '-r',
                '-e', 'runId', RUN, '-e', 'sourceFingerprint', source_fingerprint, RUNNER]
        _startup_guard(clock, started)
        proc = popen(argv, stdout=log, stderr=subprocess.STDOUT)

        startup = wait_for_startup(
            root, adb=adb, serial=serial, package=APP, run_id=RUN,
            source_fingerprint=source_fingerprint, process_poll=proc.poll,
            clock=clock, started_ns=started, runner=guarded, sleep=sleep)
        binding = startup['binding']
        initial_progress = startup['initial_progress']

        observation = _tp.observe_until_terminal(
            root, binding, adb=adb, serial=serial, package=APP,
            process_poll=proc.poll, monotonic_ns=clock, sleep=sleep,
            runner=guarded, initial_progress=initial_progress)

        proc.wait(timeout=15)

        log.flush()
        log.close()
        log = None

        log_bytes = _tf.read_stable(root, 'instrumentation.log', 1 << 20)
        observed_bytes = _tf.read_stable(root, 'terminal-observed.json', 1 << 20)

        if type(proc.returncode) is not int or proc.returncode != 0:
            raise ValueError('instrumentation process returned ' + repr(proc.returncode))

        report_obj = _json.loads(observed_bytes)
        if not isinstance(report_obj, dict) or 'status' not in report_obj:
            raise ValueError('terminal-observed.json missing status')
        terminal_status = report_obj['status']

        finish = parse_instrumentation_finish(
            log_bytes,
            expected_report_sha256=_hashlib.sha256(observed_bytes).hexdigest(),
            terminal_status=terminal_status)

        result = {
            'runId': RUN,
            'sourceFingerprint': source_fingerprint,
            'finishVerified': True,
            'finish': finish,
            'observation': observation,
            'qualityAccepted': False,
            'truePeakMeasured': False,
            'automaticRetry': False,
        }
        _tf.write_new_durable(root, 'host-result.json', _startup_json(result))
        return result
    except BaseException as exc:
        if not consumed:
            raise
        primary_error = exc
        secondary = []
        try:
            stop_alias()
        except BaseException as stop_exc:
            secondary.append({'class': type(stop_exc).__name__, 'message': str(stop_exc)})
        if proc is not None:
            _terminated = False
            try:
                proc.terminate()
            except BaseException as _t_exc:
                secondary.append({'class': type(_t_exc).__name__, 'message': str(_t_exc), 'stage': 'terminate'})
            try:
                proc.wait(timeout=5)
                _terminated = True
            except BaseException as _w_exc:
                secondary.append({'class': type(_w_exc).__name__, 'message': str(_w_exc), 'stage': 'wait'})
            if not _terminated:
                try:
                    proc.kill()
                except BaseException as _k_exc:
                    secondary.append({'class': type(_k_exc).__name__, 'message': str(_k_exc), 'stage': 'kill'})
                try:
                    proc.wait(timeout=5)
                except BaseException as _kw_exc:
                    secondary.append({'class': type(_kw_exc).__name__, 'message': str(_kw_exc), 'stage': 'kill_wait'})
        if log is not None:
            try:
                log.close()
            except BaseException as _lc_exc:
                primary_error.add_note('instrumentation.log close failed during cleanup: ' + str(_lc_exc))
        receipt = {
            'runId': RUN,
            'sourceFingerprint': source_fingerprint,
            'finishVerified': False,
            'primaryError': {
                'class': type(primary_error).__name__,
                'message': str(primary_error),
            },
            'secondaryErrors': secondary,
            'qualityAccepted': False,
            'truePeakMeasured': False,
            'automaticRetry': False,
        }
        for _attr in ('phase', 'stage_may_remain', 'publication_possible',
                      'durability_verified', 'published', 'staging_may_remain'):
            if hasattr(primary_error, _attr):
                receipt['primaryError'][_attr] = getattr(primary_error, _attr)
        try:
            _tf.write_new_durable(root, 'host-result.json', _startup_json(receipt))
        except BaseException as _wr_exc:
            primary_error.add_note('host-result.json write failed: ' + str(_wr_exc))
        raise
