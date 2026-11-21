"""Validate and reduce the pinned synthetic Android ASR resource report. No I/O."""

import re
import math

INPUT_SHA256 = "fc0f3f5bb9b06a4c21e12321d5fb0bc7d3e3151dca855e5ed9693907ce39df46"

def summarize(report: dict) -> dict:
    if not isinstance(report, dict):
        raise ValueError('report must be a dict')

    def req(d, key):
        if key not in d:
            raise ValueError('missing field: ' + key)
        return d[key]

    def is_int(v):
        return type(v) is int

    def pos_int(v, name):
        if not is_int(v) or v <= 0:
            raise ValueError('field ' + name + ' must be positive integer')
        return v

    def nonneg_int(v, name):
        if not is_int(v) or v < 0:
            raise ValueError('field ' + name + ' must be non-negative integer')
        return v

    if nonneg_int(req(report, 'schemaVersion'), 'schemaVersion') != 1:
        raise ValueError('schemaVersion must be 1')
    if req(report, 'kind') != 'android-asr-resource':
        raise ValueError('kind mismatch')
    run_id = req(report, 'runId')
    if not (isinstance(run_id, str) and re.fullmatch(r'[A-Za-z0-9-]{1,80}', run_id)):
        raise ValueError('runId invalid')
    if req(report, 'status') != 'completed':
        raise ValueError('status must be completed')
    if req(report, 'terminal') is not True:
        raise ValueError('terminal must be true')
    if req(report, 'mode') != 'inference':
        raise ValueError('mode must be inference (self-test rejected)')
    backend_id = req(report, 'backendId')
    if backend_id != 'sherpa.onnx.whisper':
        raise ValueError('backendId mismatch')
    pid = pos_int(req(report, 'pid'), 'pid')
    if req(report, 'inputSHA256') != INPUT_SHA256:
        raise ValueError('inputSHA256 mismatch')
    if nonneg_int(req(report, 'microphoneStarts'), 'microphoneStarts') != 0:
        raise ValueError('microphoneStarts must be 0')
    if req(report, 'audioPersisted') is not False:
        raise ValueError('audioPersisted must be false')
    if req(report, 'transcriptPersisted') is not False:
        raise ValueError('transcriptPersisted must be false')
    if nonneg_int(req(report, 'warmupCount'), 'warmupCount') != 5:
        raise ValueError('warmupCount must be 5')
    if nonneg_int(req(report, 'measurementCount'), 'measurementCount') != 30:
        raise ValueError('measurementCount must be 30')
    if nonneg_int(req(report, 'samplingIntervalMs'), 'samplingIntervalMs') != 20:
        raise ValueError('samplingIntervalMs must be 20')
    measurement_method = req(report, 'measurementMethod')
    if measurement_method != 'proc-self-status-VmRSS-sampled-process':
        raise ValueError('measurementMethod mismatch')
    timing_method = req(report, 'timingMethod')
    if timing_method != 'sdk-transcribe-wall-including-verify-vad-native-create-decode-release':
        raise ValueError('timingMethod mismatch')
    if req(report, 'finalCloseSucceeded') is not True:
        raise ValueError('finalCloseSucceeded must be true')
    if req(report, 'callerInputUnchanged') is not True:
        raise ValueError('callerInputUnchanged must be true')
    nonneg_int(req(report, 'closeDurationNs'), 'closeDurationNs')
    before_rss = pos_int(req(report, 'beforeRssBytes'), 'beforeRssBytes')
    after_close_rss = pos_int(req(report, 'afterCloseRssBytes'), 'afterCloseRssBytes')
    if nonneg_int(req(report, 'afterCloseWaitMs'), 'afterCloseWaitMs') != 1000:
        raise ValueError('afterCloseWaitMs must be 1000')
    after_close_wait_rss = pos_int(req(report, 'afterCloseWaitRssBytes'), 'afterCloseWaitRssBytes')

    samples = req(report, 'samples')
    if not isinstance(samples, list) or len(samples) != 35:
        raise ValueError('samples must be list of length 35')

    prev_end = 0
    measured_ms = []
    peak_list = []
    idle_list = []
    first_idle = None
    last_idle = None

    for i, s in enumerate(samples):
        if not isinstance(s, dict):
            raise ValueError('sample ' + str(i) + ' not a dict')
        if nonneg_int(req(s, 'index'), 'index') != i:
            raise ValueError('sample index mismatch at ' + str(i))
        expected_phase = 'warmup' if i < 5 else 'measurement'
        if req(s, 'phase') != expected_phase:
            raise ValueError('sample ' + str(i) + ' phase mismatch')
        if req(s, 'backendId') != backend_id:
            raise ValueError('sample ' + str(i) + ' backendId mismatch')
        if req(s, 'expectedMatched') is not True:
            raise ValueError('sample ' + str(i) + ' expectedMatched must be true')
        if req(s, 'callerInputUnchanged') is not True:
            raise ValueError('sample ' + str(i) + ' callerInputUnchanged must be true')
        dur = pos_int(req(s, 'durationNs'), 'sample ' + str(i) + '.durationNs')
        start = pos_int(req(s, 'startedElapsedNs'), 'sample ' + str(i) + '.startedElapsedNs')
        end = pos_int(req(s, 'endedElapsedNs'), 'sample ' + str(i) + '.endedElapsedNs')
        if end - start != dur:
            raise ValueError('sample ' + str(i) + ' ended-started != durationNs')
        if start <= prev_end:
            raise ValueError('sample ' + str(i) + ' not strictly sequential')
        prev_end = end
        idle = pos_int(req(s, 'afterIdleRssBytes'), 'sample ' + str(i) + '.afterIdleRssBytes')
        rss = req(s, 'rss')
        if not isinstance(rss, list) or len(rss) < 3:
            raise ValueError('sample ' + str(i) + ' rss length>=3 required')
        prev_t = None
        bytes_list = []
        for j, pt in enumerate(rss):
            if not isinstance(pt, dict):
                raise ValueError('sample ' + str(i) + ' rss[' + str(j) + '] not dict')
            t = pos_int(req(pt, 'elapsedNs'), 'sample ' + str(i) + ' rss[' + str(j) + '].elapsedNs')
            b = pos_int(req(pt, 'bytes'), 'sample ' + str(i) + ' rss[' + str(j) + '].bytes')
            if prev_t is not None and t <= prev_t:
                raise ValueError('sample ' + str(i) + ' rss not strictly increasing')
            prev_t = t
            bytes_list.append(b)
        if rss[0]['elapsedNs'] > start:
            raise ValueError('sample ' + str(i) + ' rss first time after start')
        if rss[-1]['elapsedNs'] < end:
            raise ValueError('sample ' + str(i) + ' rss last time before end')
        inside = any(start <= pt['elapsedNs'] <= end for pt in rss)
        if not inside:
            raise ValueError('sample ' + str(i) + ' no rss point inside call')
        peak = max(bytes_list)
        if pos_int(req(s, 'rssPeakSampledBytes'), 'rssPeakSampledBytes') != peak:
            raise ValueError('sample ' + str(i) + ' rssPeakSampledBytes mismatch')
        if i >= 5:
            measured_ms.append(dur / 1000000.0)
            peak_list.append(peak)
            idle_list.append(idle)
            if first_idle is None:
                first_idle = idle
            last_idle = idle

    if len(measured_ms) != 30:
        raise ValueError('expected 30 measured rows')

    def pct(vals, p):
        sv = sorted(vals)
        pos = math.ceil(p * len(sv)) - 1
        if pos < 0:
            pos = 0
        return sv[pos]

    ds = sorted(measured_ms)
    duration_ms = {
        'p50': round(pct(ds, 0.50), 3),
        'p95': round(pct(ds, 0.95), 3),
        'min': round(min(ds), 3),
        'max': round(max(ds), 3),
    }
    rss_peak_summary = {'min': min(peak_list), 'max': max(peak_list)}
    after_idle_summary = {'min': min(idle_list), 'max': max(idle_list), 'first': first_idle, 'last': last_idle}

    return {
        'schemaVersion': 1,
        'kind': 'android-asr-resource-summary',
        'runId': run_id,
        'pid': pid,
        'backendId': backend_id,
        'warmupRuns': 5,
        'measuredRuns': 30,
        'durationMs': duration_ms,
        'rssPeakSampledBytes': rss_peak_summary,
        'afterIdleRssBytes': after_idle_summary,
        'beforeRssBytes': before_rss,
        'afterCloseRssBytes': after_close_rss,
        'afterCloseWaitRssBytes': after_close_wait_rss,
        'timingMethod': timing_method,
        'measurementMethod': measurement_method,
        'qualityVerdict': 'unverified',
        'validationStatus': 'pass-bounded',
        'unverified': [
            'natural speech CER/WER',
            'true peak memory',
            'SDK-only memory attribution',
            'GPU',
            'energy',
            'minimum OS',
            'sustained resource budget',
            'current SDK cleanup changes',
        ],
    }
