"""Terminal observation coordinator.

Reads a terminal report twice around two process samples, builds a durable
observation record, and publishes it via local file primitives. Pure stdlib
plus the supplied terminal_file_io / terminal_observer modules.
"""

import copy
import errno
import hashlib
import json
import os
import stat
from pathlib import Path
from typing import Callable, Dict

from terminal_file_io import (
    read_stable,
    write_new_durable,
    _validate_root,
    _split_relative,
    _open_component,
)
from terminal_observer import acknowledgement

_MAX_BYTES = 1048576
_LONG_MAX = 2 ** 63 - 1
_RSS_MIN = 1
_RSS_MAX = 1610612736
_AVAIL_MIN = 536870912
_AVAIL_MAX = 2 ** 63 - 1


def _exact_int(x):
    return type(x) is int


def _validate_clock(value, label):
    if not _exact_int(value):
        raise ValueError('%s must be an exact int' % label)
    if value < 0 or value > _LONG_MAX:
        raise ValueError('%s out of range' % label)


def _validate_sample(sample, binding):
    if not isinstance(sample, dict):
        raise ValueError('sample must be a dict')
    pid = sample.get('pid')
    start_ticks = sample.get('startTicks')
    rss = sample.get('rssBytes')
    avail = sample.get('deviceAvailableBytes')
    if not _exact_int(pid) or pid != binding.get('pid'):
        raise ValueError('sample pid mismatch')
    if type(start_ticks) is not str or start_ticks != binding.get('startTicks'):
        raise ValueError('sample startTicks mismatch')
    if not _exact_int(rss) or rss < _RSS_MIN or rss > _RSS_MAX:
        raise ValueError('sample rssBytes out of range')
    if not _exact_int(avail) or avail < _AVAIL_MIN or avail > _AVAIL_MAX:
        raise ValueError('sample deviceAvailableBytes out of range')


def _probe_path(root_resolved, rel, parts):
    """Walk the parent chain of a relative path with nofollow/dir_fd semantics
    and return the final entry stat (follow_symlinks=False) or None if absent.
    Raises on symlink/missing/non-directory parents; never reads file data."""
    fds = []
    try:
        parent = _open_component(-1, str(root_resolved), True)
        fds.append(parent)
        for part in parts[:-1]:
            nfd = _open_component(parent, part, True)
            fds.append(nfd)
            parent = nfd
        dirfd = parent
        final = parts[-1]
        try:
            return os.stat(final, dir_fd=dirfd, follow_symlinks=False)
        except OSError as exc:
            if exc.errno == errno.ENOENT:
                return None
            raise
    finally:
        for fd in fds:
            os.close(fd)


def _preflight(root, rels):
    root_resolved = _validate_root(root)
    values = list(rels.values())
    if len(set(values)) != len(values):
        raise ValueError('relative paths must be pairwise distinct')
    parts = {}
    for key, rel in rels.items():
        if type(rel) is not str:
            raise ValueError('%s must be an exact str' % key)
        parts[key] = _split_relative(rel)
    probes = {key: _probe_path(root_resolved, rel, parts[key])
              for key, rel in rels.items()}
    rs = probes['report']
    if rs is None:
        raise ValueError('source report missing')
    if stat.S_ISLNK(rs.st_mode):
        raise ValueError('source report must not be a symlink')
    if not stat.S_ISREG(rs.st_mode):
        raise ValueError('source report must be a regular file')
    for key in ('observed', 'receipt', 'ack'):
        if probes[key] is not None:
            raise OSError('output path already exists: %s' % rels[key])


def _reject_nonfinite(obj, label):
    try:
        json.dumps(obj, allow_nan=False)
    except (ValueError, TypeError):
        raise ValueError('nonfinite value in %s' % label)


def publish_terminal_observation(root: Path, report_relative: str,
                                 observed_report_relative: str,
                                 receipt_relative: str, ack_relative: str,
                                 binding: Dict, sample: Callable[[], dict],
                                 monotonic_ns: Callable[[], int]) -> Dict:
    # 0. contract: binding type before any callback/output
    if type(binding) is not dict:
        raise ValueError('binding must be a dict')

    # 1. preflight: root, paths, parent chains, final entries (no callbacks,
    #    no writes, no data read)
    _preflight(root, {
        'report': report_relative,
        'observed': observed_report_relative,
        'receipt': receipt_relative,
        'ack': ack_relative,
    })

    # 2. snapshot binding before callbacks
    binding_snapshot = copy.deepcopy(binding)
    t0 = monotonic_ns()
    _validate_clock(t0, 't0')
    sample_before = copy.deepcopy(sample())
    _validate_sample(sample_before, binding_snapshot)

    # 3. two reads around the second sample
    first = read_stable(root, report_relative, _MAX_BYTES)
    t1 = monotonic_ns()
    _validate_clock(t1, 't1')
    if t1 < t0:
        raise ValueError('monotonic clock regression')
    second = read_stable(root, report_relative, _MAX_BYTES)
    t2 = monotonic_ns()
    _validate_clock(t2, 't2')
    if t2 < t1:
        raise ValueError('monotonic clock regression')
    sample_after = copy.deepcopy(sample())
    _validate_sample(sample_after, binding_snapshot)
    t3 = monotonic_ns()
    _validate_clock(t3, 't3')
    if t3 < t2:
        raise ValueError('monotonic clock regression')

    # 4. observation
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

    # 5. reject nonfinite extra values before any write
    _reject_nonfinite(binding_snapshot, 'binding')
    _reject_nonfinite(sample_before, 'sampleBefore')
    _reject_nonfinite(sample_after, 'sampleAfter')

    # 6. acknowledgement then durable writes (no retry / no rollback)
    ack_bytes = acknowledgement(first, second, binding_snapshot, observation)
    receipt = {
        'schemaVersion': 1,
        'binding': binding_snapshot,
        'terminalReportSha256': hashlib.sha256(first).hexdigest(),
        'observation': observation,
        'sampleBefore': sample_before,
        'sampleAfter': sample_after,
    }
    receipt_bytes = json.dumps(receipt, allow_nan=False, ensure_ascii=False,
                               sort_keys=True,
                               separators=(',', ':')).encode('utf-8')

    write_new_durable(root, observed_report_relative, first)
    write_new_durable(root, receipt_relative, receipt_bytes)
    write_new_durable(root, ack_relative, ack_bytes)
    return observation