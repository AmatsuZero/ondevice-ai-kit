"""Transport-aware terminal observation coordinator.

Composes the ADB process sampler, terminal report reader and ACK publisher
around two report reads and two process samples, persisting durable evidence
and publishing the remote ACK.
"""

import copy
import hashlib
import json
import time
from typing import Dict

from terminal_file_io import (
    write_new_durable,
    _validate_root,
    _split_relative,
)
from terminal_observation_coordinator import (
    _probe_path,
    _validate_clock,
    _validate_sample,
)
from terminal_observer import acknowledgement
from adb_process_sampler import AdbProcessSampler
from adb_terminal_report_reader import AdbTerminalReportReader
from adb_terminal_ack_publisher import AdbTerminalAckPublisher

_IDENTITY_KEYS = {'runId', 'sourceFingerprint', 'pid', 'startTicks'}
_CLOCK_DOMAIN = 'host-monotonic-ns'


def _preflight_outputs(root, rels):
    root_resolved = _validate_root(root)
    values = list(rels.values())
    if len(set(values)) != len(values):
        raise ValueError('relative paths must be pairwise distinct')
    parts = {}
    for key, rel in rels.items():
        if type(rel) is not str:
            raise ValueError('%s must be an exact str' % key)
        parts[key] = _split_relative(rel)
    for key, rel in rels.items():
        if _probe_path(root_resolved, rel, parts[key]) is not None:
            raise OSError('output path already exists: %s' % rel)


def _strict_json(obj):
    return json.dumps(obj, allow_nan=False, ensure_ascii=False,
                      sort_keys=True, separators=(',', ':')).encode('utf-8')


def _adapt_runner(runner):
    def adapted(argv, timeout_seconds):
        return runner(argv, timeout_seconds, None)
    return adapted


def publish_remote_terminal_observation(root, observed_report_relative,
                                        receipt_relative, ack_relative,
                                        before_link_relative, binding, *,
                                        adb, serial, package,
                                        timeout_seconds=2.0,
                                        monotonic_ns=time.monotonic_ns,
                                        runner=None) -> Dict:
    # preflight: validate config, binding, clock, runner, root and output paths
    # before any transport or clock callback; no directories are created.
    if type(binding) is not dict:
        raise ValueError('binding must be a dict')
    if set(binding) != _IDENTITY_KEYS:
        raise ValueError('binding must have exactly the identity keys')
    for key in ('runId', 'sourceFingerprint', 'startTicks'):
        if type(binding[key]) is not str:
            raise ValueError('%s must be an exact str' % key)
    if (len(binding['sourceFingerprint']) != 64 or
            any(c not in '0123456789abcdef' for c in binding['sourceFingerprint'])):
        raise ValueError('sourceFingerprint must be 64 lowercase hex chars')
    if type(binding['pid']) is not int:
        raise ValueError('pid must be an exact int')
    if not callable(monotonic_ns):
        raise ValueError('monotonic_ns must be callable')
    if runner is not None and not callable(runner):
        raise ValueError('runner must be callable or None')

    # snapshot caller binding before callbacks
    binding_snapshot = copy.deepcopy(binding)

    _preflight_outputs(root, {
        'observed': observed_report_relative,
        'receipt': receipt_relative,
        'ack': ack_relative,
        'before_link': before_link_relative,
    })

    # constructors validate their own transport config and identity bounds
    adapted = None if runner is None else _adapt_runner(runner)
    reader = AdbTerminalReportReader(adb, serial, package,
                                     binding_snapshot['runId'],
                                     timeout_seconds=timeout_seconds,
                                     runner=adapted)
    sampler = AdbProcessSampler(adb, serial, package,
                                binding_snapshot['pid'],
                                binding_snapshot['startTicks'],
                                timeout_seconds=timeout_seconds,
                                runner=adapted)
    publisher = AdbTerminalAckPublisher(adb, serial, package,
                                        binding_snapshot['runId'],
                                        timeout_seconds=timeout_seconds,
                                        runner=runner)

    # ordered measurement
    t0 = monotonic_ns()
    _validate_clock(t0, 't0')
    sample_before = copy.deepcopy(sampler.sample())
    _validate_sample(sample_before, binding_snapshot)

    first = reader.read()
    t1 = monotonic_ns()
    _validate_clock(t1, 't1')
    if t1 < t0:
        raise ValueError('monotonic clock regression')

    second = reader.read()
    t2 = monotonic_ns()
    _validate_clock(t2, 't2')
    if t2 < t1:
        raise ValueError('monotonic clock regression')

    sample_after = copy.deepcopy(sampler.sample())
    _validate_sample(sample_after, binding_snapshot)
    t3 = monotonic_ns()
    _validate_clock(t3, 't3')
    if t3 < t2:
        raise ValueError('monotonic clock regression')

    observation = {
        'pidBefore': binding_snapshot['pid'],
        'pidAfter': binding_snapshot['pid'],
        'startTicksBefore': binding_snapshot['startTicks'],
        'startTicksAfter': binding_snapshot['startTicks'],
        'uptimeBeforeNs': t0,
        'firstReportReadNs': t1,
        'secondReportReadNs': t2,
        'uptimeAfterNs': t3,
        'rssBytes': max(sample_before['rssBytes'], sample_after['rssBytes']),
        'deviceAvailableBytes': min(sample_before['deviceAvailableBytes'],
                                    sample_after['deviceAvailableBytes']),
        'terminalObservationGap': None,
    }

    report_sha = hashlib.sha256(first).hexdigest()
    binding_full = copy.deepcopy(binding_snapshot)
    binding_full['terminalReportSha256'] = report_sha

    ack_bytes = acknowledgement(first, second, binding_full, observation)

    receipt = {
        'schemaVersion': 1,
        'binding': binding_full,
        'terminalReportSha256': report_sha,
        'observation': observation,
        'sampleBefore': sample_before,
        'sampleAfter': sample_after,
        'clockDomain': _CLOCK_DOMAIN,
    }
    receipt_bytes = _strict_json(receipt)

    # durable local evidence, then the actual remote publisher
    write_new_durable(root, observed_report_relative, first)
    write_new_durable(root, receipt_relative, receipt_bytes)
    write_new_durable(root, ack_relative, ack_bytes)

    def before_link():
        t4 = monotonic_ns()
        _validate_clock(t4, 't4')
        if t4 < t3:
            raise ValueError('monotonic clock regression')
        sample_link = copy.deepcopy(sampler.sample())
        _validate_sample(sample_link, binding_snapshot)
        t5 = monotonic_ns()
        _validate_clock(t5, 't5')
        if t5 < t4:
            raise ValueError('monotonic clock regression')
        journal = {
            'schemaVersion': 1,
            'binding': copy.deepcopy(binding_full),
            'terminalReportSha256': report_sha,
            'sampleBeforeLink': sample_link,
            'sampleStartedNs': t4,
            'sampleFinishedNs': t5,
            'clockDomain': _CLOCK_DOMAIN,
        }
        write_new_durable(root, before_link_relative, _strict_json(journal))

    publication = publisher.publish(ack_bytes, before_link=before_link)
    return {'observation': observation, 'publication': publication}