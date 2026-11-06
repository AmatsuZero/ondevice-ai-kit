"""Validate and reduce fixed-image Android LiteRT-LM resource observations. No I/O."""

import re
import math

INPUT_SHA256 = '991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa'
MODEL_SHA256 = '2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6'
MODEL_ID = 'gemma-3n-e2b-it'
MODEL_REVISION = 'c03b6f60b8da6c5400b6838a2cf26420f80c0a01'
RUNTIME_REVISION = 'litertlm-android:0.18.1'
BACKEND_POLICY = 'CPU_TEXT_GPU_VISION'
BACKEND_ID = 'litertlm.android'
PROMPT_SHA256 = '8c7e54ee85a5f0c77c8f85b7eb30e5cfb475c02472b9e2770352eb6aac925836'
HEX64 = re.compile(r'^[0-9a-f]{64}$')


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
    if req(report, 'kind') != 'android-litertlm-resource':
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
    if backend_id != BACKEND_ID:
        raise ValueError('backendId mismatch')
    pid = pos_int(req(report, 'pid'), 'pid')
    if req(report, 'inputSHA256') != INPUT_SHA256:
        raise ValueError('inputSHA256 mismatch')
    model_id = req(report, 'modelId')
    if model_id != MODEL_ID:
        raise ValueError('modelId mismatch')
    model_revision = req(report, 'modelRevision')
    if model_revision != MODEL_REVISION:
        raise ValueError('modelRevision mismatch')
    runtime_revision = req(report, 'runtimeRevision')
    if runtime_revision != RUNTIME_REVISION:
        raise ValueError('runtimeRevision mismatch')
    model_sha256 = req(report, 'modelSHA256')
    if model_sha256 != MODEL_SHA256:
        raise ValueError('modelSHA256 mismatch')
    backend_policy = req(report, 'backendPolicy')
    if backend_policy != BACKEND_POLICY:
        raise ValueError('backendPolicy mismatch')
    max_output_tokens = pos_int(req(report, 'maxOutputTokens'), 'maxOutputTokens')
    if max_output_tokens != 32:
        raise ValueError('maxOutputTokens must be 32')
    prompt_sha256 = req(report, 'promptSHA256')
    if prompt_sha256 != PROMPT_SHA256:
        raise ValueError('promptSHA256 must be 64 lowercase hex')
    cache_files_removed = req(report, 'cacheFilesRemoved')
    if cache_files_removed is not True:
        raise ValueError('cacheFilesRemoved must be true')
    if nonneg_int(req(report, 'microphoneStarts'), 'microphoneStarts') != 0:
        raise ValueError('microphoneStarts must be 0')
    if nonneg_int(req(report, 'warmupCount'), 'warmupCount') != 1:
        raise ValueError('warmupCount must be 1')
    if nonneg_int(req(report, 'measurementCount'), 'measurementCount') != 5:
        raise ValueError('measurementCount must be 5')
    if nonneg_int(req(report, 'samplingIntervalMs'), 'samplingIntervalMs') != 20:
        raise ValueError('samplingIntervalMs must be 20')
    measurement_method = req(report, 'measurementMethod')
    if measurement_method != 'proc-self-status-VmRSS-sampled-process':
        raise ValueError('measurementMethod mismatch')
    timing_method = req(report, 'timingMethod')
    if timing_method != 'sdk-generate-wall-including-verify-snapshot-native-create-send-release':
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
    if not isinstance(samples, list) or len(samples) != 6:
        raise ValueError('samples must be list of length 6')

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
        expected_phase = 'warmup' if i < 1 else 'measurement'
        if req(s, 'phase') != expected_phase:
            raise ValueError('sample ' + str(i) + ' phase mismatch')
        if req(s, 'backendId') != backend_id:
            raise ValueError('sample ' + str(i) + ' backendId mismatch')
        if req(s, 'modelId') != model_id:
            raise ValueError('sample ' + str(i) + ' modelId mismatch')
        if req(s, 'modelRevision') != model_revision:
            raise ValueError('sample ' + str(i) + ' modelRevision mismatch')
        if req(s, 'runtimeRevision') != runtime_revision:
            raise ValueError('sample ' + str(i) + ' runtimeRevision mismatch')
        if req(s, 'expectedMatched') is not True:
            raise ValueError('sample ' + str(i) + ' expectedMatched must be true')
        if req(s, 'callerInputUnchanged') is not True:
            raise ValueError('sample ' + str(i) + ' callerInputUnchanged must be true')
        if req(s, 'cacheFilesRemoved') is not True:
            raise ValueError('sample ' + str(i) + ' cacheFilesRemoved must be true')
        out_sha = req(s, 'outputSHA256')
        if not (isinstance(out_sha, str) and HEX64.fullmatch(out_sha)):
            raise ValueError('sample ' + str(i) + ' outputSHA256 must be 64 lowercase hex')
        pos_int(req(s, 'outputLength'), 'sample ' + str(i) + '.outputLength')
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
        if i >= 1:
            measured_ms.append(dur / 1000000.0)
            peak_list.append(peak)
            idle_list.append(idle)
            if first_idle is None:
                first_idle = idle
            last_idle = idle

    if len(measured_ms) != 5:
        raise ValueError('expected 5 measured rows')

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
        'kind': 'android-litertlm-resource-summary',
        'runId': run_id,
        'pid': pid,
        'backendId': backend_id,
        'modelId': model_id,
        'modelRevision': model_revision,
        'runtimeRevision': runtime_revision,
        'modelSHA256': model_sha256,
        'backendPolicy': backend_policy,
        'maxOutputTokens': max_output_tokens,
        'warmupRuns': 1,
        'measuredRuns': 5,
        'durationMs': duration_ms,
        'rssPeakSampledBytes': rss_peak_summary,
        'afterIdleRssBytes': after_idle_summary,
        'beforeRssBytes': before_rss,
        'afterCloseRssBytes': after_close_rss,
        'afterCloseWaitRssBytes': after_close_wait_rss,
        'timingMethod': timing_method,
        'measurementMethod': measurement_method,
        'promptSHA256': prompt_sha256,
        'inputSHA256': INPUT_SHA256,
        'qualityVerdict': 'unverified',
        'validationStatus': 'pass-bounded',
        'unverified': [
            'natural image understanding quality',
            'true peak memory',
            'SDK-only memory attribution',
            'GPU allocations',
            'energy',
            'minimum OS',
            'sustained resource budget',
            'current whole-App source identity',
        ],
    }
