import copy
import hashlib
import json
import math
import re
import time
from terminal_file_io import write_new_durable
from terminal_observation_coordinator import _validate_sample, _validate_clock
from adb_terminal_observation import _preflight_outputs, publish_remote_terminal_observation
from adb_process_sampler import AdbProcessSampler
from adb_terminal_report_reader import AdbTerminalReportReader
from adb_terminal_ack_publisher import AdbTerminalAckPublisher
_TICKS_RE = re.compile(r'[1-9][0-9]*')
_RUNID_RE = re.compile(r'[A-Za-z0-9-]{1,80}')
_FP_RE = re.compile(r'[a-f0-9]{64}')
def _is_exact_int(v):
    return type(v) is int
def _valid_ticks(s):
    if type(s) is not str or _TICKS_RE.fullmatch(s) is None:
        return False
    if len(s) > 19 or (len(s) == 19 and s > '9223372036854775807'):
        return False
    return True
def _binding_snapshot(binding):
    if type(binding) is not dict:
        raise ValueError('binding must be object')
    if set(binding.keys()) != {'runId', 'sourceFingerprint', 'pid', 'startTicks'}:
        raise ValueError('binding must have exactly 4 keys')
    run_id = binding['runId']
    src_fp = binding['sourceFingerprint']
    pid = binding['pid']
    st = binding['startTicks']
    if type(run_id) is not str or _RUNID_RE.fullmatch(run_id) is None:
        raise ValueError('invalid runId')
    if type(src_fp) is not str or _FP_RE.fullmatch(src_fp) is None:
        raise ValueError('invalid sourceFingerprint')
    if type(pid) is not int or not (1 <= pid <= 2147483647):
        raise ValueError('invalid pid')
    if not _valid_ticks(st):
        raise ValueError('invalid startTicks')
    return {'runId': run_id, 'sourceFingerprint': src_fp, 'pid': pid, 'startTicks': st}
def _reject_float(s):
    v = float(s)
    if math.isinf(v) or math.isnan(v):
        raise ValueError('nonfinite float')
    return v
def _reject_constant(c):
    raise ValueError('nonfinite literal: ' + c)
def _no_dup_pairs(pairs):
    seen = set()
    for k, v in pairs:
        if k in seen:
            raise ValueError('duplicate key')
        seen.add(k)
    return dict(pairs)
def _parse_report(raw, binding):
    if type(raw) is not bytes:
        raise ValueError('raw must be bytes')
    try:
        root = json.loads(raw, parse_float=_reject_float, parse_constant=_reject_constant, object_pairs_hook=_no_dup_pairs)
    except ValueError as e:
        raise ValueError('malformed JSON: ' + str(e))
    if type(root) is not dict:
        raise ValueError('root must be object')
    for k in ('runId','sourceFingerprint','pid','processStartTicks','terminalObservationProtocol','probeVariant','terminalAckTimeoutMillis','terminal','status','phase','activeCaseIndex'):
        if k not in root:
            raise ValueError('missing field: ' + k)
    if type(root['runId']) is not str or root['runId'] != binding['runId']:
        raise ValueError('runId mismatch')
    if type(root['sourceFingerprint']) is not str or root['sourceFingerprint'] != binding['sourceFingerprint']:
        raise ValueError('sourceFingerprint mismatch')
    if type(root['pid']) is not int or root['pid'] != binding['pid']:
        raise ValueError('pid mismatch')
    if type(root['processStartTicks']) is not str or root['processStartTicks'] != binding['startTicks']:
        raise ValueError('processStartTicks mismatch')
    if type(root['terminalObservationProtocol']) is not str or root['terminalObservationProtocol'] != 'ODAI-TERMINAL-ACK-v1':
        raise ValueError('invalid terminalObservationProtocol')
    if type(root['probeVariant']) is not str or root['probeVariant'] != 'cpu-arena-off-heap-terminal-ack-v1':
        raise ValueError('invalid probeVariant')
    if type(root['terminalAckTimeoutMillis']) is not int or root['terminalAckTimeoutMillis'] != 30000:
        raise ValueError('invalid terminalAckTimeoutMillis')
    terminal = root['terminal']
    if type(terminal) is not bool:
        raise ValueError('terminal must be bool')
    status = root['status']
    if type(status) is not str:
        raise ValueError('status must be str')
    if terminal is False:
        if status != 'running':
            raise ValueError('status must be running')
    elif status not in ('completed', 'failed'):
        raise ValueError('status must be completed/failed')
    if type(root['phase']) is not str or root['phase'] == '':
        raise ValueError('phase required')
    aci = root['activeCaseIndex']
    if aci is not None and not (type(aci) is int and aci == 0):
        raise ValueError('activeCaseIndex must be None or 0')
    return root
class _PollClock:
    def __init__(self, callback):
        if not callable(callback):
            raise ValueError('callback must be callable')
        self._callback = callback
        self.start_ns = None
        self.last_ns = None
    def __call__(self):
        value = self._callback()
        _validate_clock(value, 'host clock')
        if self.start_ns is None:
            self.start_ns = value
            self.last_ns = value
            return value
        if value < self.last_ns:
            raise ValueError('clock regression')
        if value - self.start_ns > 240000000000:
            raise ValueError('clock step too large')
        self.last_ns = value
        return value
def observe_until_terminal(root, binding, *, adb, serial, package, process_poll,
                           timeout_seconds=2.0, monotonic_ns=time.monotonic_ns,
                           sleep=time.sleep, runner=None):
    snapshot = _binding_snapshot(binding)
    if isinstance(timeout_seconds, bool) or not isinstance(timeout_seconds, (int, float)):
        raise ValueError('timeout_seconds must be int/float')
    if not (0 < timeout_seconds <= 2):
        raise ValueError('timeout_seconds out of range')
    if not callable(process_poll) or not callable(monotonic_ns) or not callable(sleep):
        raise ValueError('process_poll/monotonic_ns/sleep must be callable')
    if runner is not None and not callable(runner):
        raise ValueError('runner must be callable or None')
    pid = snapshot['pid']
    start_ticks = snapshot['startTicks']
    run_id = snapshot['runId']
    if runner is not None:
        _sample_runner = lambda argv, timeout: runner(argv, timeout, None)
    else:
        _sample_runner = None
    sampler = AdbProcessSampler(adb, serial, package, pid, start_ticks,
                                timeout_seconds=timeout_seconds, runner=_sample_runner)
    reader = AdbTerminalReportReader(adb, serial, package, run_id,
                                     timeout_seconds=timeout_seconds, runner=_sample_runner)
    AdbTerminalAckPublisher(adb, serial, package, run_id,
                            timeout_seconds=timeout_seconds, runner=runner)
    _preflight_outputs(root, {
        'logicalkey': 'poll-start.json',
        'observed': 'terminal-observed.json',
        'receipt': 'terminal-receipt.json',
        'ack': 'terminal-ack.txt',
        'beforelink': 'terminal-before-link.json',
    })
    clock = _PollClock(monotonic_ns)
    start_payload = {
        'binding': copy.deepcopy(snapshot),
        'timeout_seconds': timeout_seconds,
    }
    write_new_durable(root, 'poll-start.json',
                      json.dumps(start_payload, sort_keys=True, allow_nan=False).encode('utf-8'))
    start_ns = clock()
    poll_samples = 0
    index = 0
    anchor_ns = start_ns
    anchor_phase = None
    anchor_index = None
    while True:
        guard_ns = clock()
        if (guard_ns - anchor_ns) > 120000000000:
            raise ValueError('progress window expired')
        pp = process_poll()
        if pp is None:
            pass
        elif isinstance(pp, bool):
            raise ValueError('process_poll returned bool')
        elif type(pp) is int:
            raise RuntimeError('process exited early')
        else:
            raise ValueError('process_poll returned invalid type')
        poll_clock_ns = clock()
        if (poll_clock_ns - anchor_ns) > 120000000000:
            raise ValueError('progress window expired')
        sample = sampler.sample()
        _validate_sample(sample, snapshot)
        sample_ns = clock()
        if (sample_ns - anchor_ns) > 120000000000:
            raise ValueError('progress window expired')
        raw = reader.read()
        read_ns = clock()
        if (read_ns - anchor_ns) > 120000000000:
            raise ValueError('progress window expired')
        report = _parse_report(raw, snapshot)
        phase = report['phase']
        active_case_index = report['activeCaseIndex']
        if anchor_phase is None:
            anchor_phase = phase
            anchor_index = active_case_index
        elif (phase, active_case_index) != (anchor_phase, anchor_index):
            anchor_phase = phase
            anchor_index = active_case_index
            anchor_ns = read_ns
        payload = {
            'binding': copy.deepcopy(snapshot),
            'sample': copy.deepcopy(sample),
            'reportSHA256': hashlib.sha256(raw).hexdigest(),
            'clockDomain': 'host-monotonic-ns',
            'sampleNs': sample_ns,
            'readNs': read_ns,
        }
        write_new_durable(root, 'poll-sample-%06d.json' % index,
                          json.dumps(payload, sort_keys=True, allow_nan=False).encode('utf-8'))
        poll_samples += 1
        index += 1
        if report.get('terminal'):
            obs = publish_remote_terminal_observation(
                root, 'terminal-observed.json', 'terminal-receipt.json',
                'terminal-ack.txt', 'terminal-before-link.json', snapshot,
                adb=adb, serial=serial, package=package,
                timeout_seconds=timeout_seconds, monotonic_ns=clock, runner=runner)
            return {
                **obs,
                'pollSamples': poll_samples,
                'qualityAccepted': False,
                'truePeakMeasured': False,
            }
        guard_ns = clock()
        if (guard_ns - anchor_ns) > 120000000000:
            raise ValueError('progress window expired')
        sleep(0.25)
        clock()
