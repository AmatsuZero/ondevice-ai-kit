"""Strict reduction of one fixed-image Identity resource session. No I/O."""
import math
import re

COMMIT = '636a6eb8854db5dff74244c42c3ce0e82bd2be9f'
INPUT_SHA256 = '88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5'
MODEL_HASHES = {
    'face_detection_yunet_2023mar.onnx': '8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4',
    'face_recognition_sface_2021dec.onnx': '0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79',
}


def summarize(report: dict) -> dict:
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    def field(row, key):
        require(isinstance(row, dict) and key in row, 'missing field: ' + key)
        return row[key]

    def integer(row, key, minimum=0):
        value = field(row, key)
        require(type(value) is int and minimum <= value <= 2**63 - 1, 'invalid integer: ' + key)
        return value

    def exact(row, key, expected):
        value = field(row, key)
        require(type(value) is type(expected) and value == expected, 'invalid field: ' + key)

    fixed = dict(schemaVersion=2, kind='android-identity-resource', mode='inference', sdkCommit=COMMIT,
                 status='completed', terminal=True, backendId='opencv.identity.android',
                 runtimeVersion='4.12.0', modelRevision='zoo-47534e27-v1',
                 preprocessRevision='opencv-4.12-yunet640-sface112-rgb-l2-v1', inputSHA256=INPUT_SHA256,
                 modelSHA256=MODEL_HASHES, enrollmentStarts=0, cameraStarts=0, imageWrites=0,
                 embeddingPersisted=False, warmupCount=5, measurementCount=30, samplingIntervalMs=20,
                 measurementMethod='proc-self-status-VmRSS-sampled-process',
                 timingMethod='sdk-quality-and-embed-wall-after-one-backend-open',
                 finalCloseSucceeded=True, modelLeaseReleased=True, workDirectoryRemoved=True,
                 sourceInputUnchanged=True, callerInputUnchanged=True, afterCloseWaitMs=1000,
                 foregroundActivity='io.ondeviceai.demo.MainActivity', foregroundStartedBeforeTiming=True,
                 foregroundCheckCount=72, foregroundPolicy='RESUMED-at-72-checkpoints',
                 processScope='process-with-idle-main-activity')
    positive = ('pid', 'beforeBackendOpenRssBytes', 'beforeRssBytes', 'afterCloseRssBytes', 'afterCloseWaitRssBytes')
    require(isinstance(report, dict), 'report must be an object')
    require(set(report) == set(fixed) | set(positive) | {'runId', 'deviceModel', 'sdkInt', 'samples', 'closeDurationNs'},
            'unexpected or missing report fields')
    for key, value in fixed.items():
        exact(report, key, value)
    for key in positive:
        integer(report, key, 1)
    integer(report, 'sdkInt', 26)
    integer(report, 'closeDurationNs')
    run_id = field(report, 'runId')
    require(isinstance(run_id, str) and re.fullmatch('[A-Za-z0-9-]{1,80}', run_id), 'invalid runId')
    require(isinstance(report['deviceModel'], str) and report['deviceModel'].strip(), 'invalid deviceModel')
    samples = field(report, 'samples')
    require(isinstance(samples, list) and len(samples) == 35, 'expected exactly 35 samples')
    previous_end = 0
    durations, peaks, idles, intervals = [], [], [], []
    sample_keys = {'index', 'phase', 'backendId', 'faceCount', 'embeddingDimension', 'embeddingNormSquared',
                   'callerInputUnchanged', 'startedElapsedNs', 'endedElapsedNs', 'durationNs',
                   'rss', 'rssPeakSampledBytes', 'afterIdleRssBytes'}
    for index, row in enumerate(samples):
        require(isinstance(row, dict) and sample_keys <= set(row) <= sample_keys | {'thermalStatus'},
                'unexpected or missing sample fields')
        exact(row, 'index', index)
        exact(row, 'phase', 'warmup' if index < 5 else 'measurement')
        exact(row, 'backendId', fixed['backendId'])
        exact(row, 'faceCount', 1)
        exact(row, 'embeddingDimension', 128)
        exact(row, 'callerInputUnchanged', True)
        norm = field(row, 'embeddingNormSquared')
        require(type(norm) in (int, float) and 0.99999 < norm < 1.00001 and math.isfinite(norm),
                'invalid normalized embedding diagnostic')
        if 'thermalStatus' in row:
            require(integer(row, 'thermalStatus') <= 6, 'invalid thermalStatus')
        start, end = integer(row, 'startedElapsedNs', 1), integer(row, 'endedElapsedNs', 1)
        duration = integer(row, 'durationNs', 1)
        require(start > previous_end and end - start == duration, 'invalid or overlapping call window')
        previous_end = end
        idle = integer(row, 'afterIdleRssBytes', 1)
        readings = field(row, 'rss')
        require(isinstance(readings, list) and len(readings) >= 3, 'insufficient RSS readings')
        previous_time, peak = 0, 0
        inside = False
        for point in readings:
            require(isinstance(point, dict) and set(point) == {'elapsedNs', 'bytes'}, 'invalid RSS point')
            timestamp, value = integer(point, 'elapsedNs', 1), integer(point, 'bytes', 1)
            require(timestamp > previous_time, 'RSS points must be strictly ordered')
            if previous_time:
                intervals.append((timestamp - previous_time) / 1e6)
            previous_time = timestamp
            peak = max(peak, value)
            inside |= start <= timestamp <= end
        require(readings[0]['elapsedNs'] <= start and readings[-1]['elapsedNs'] >= end and inside,
                'RSS points do not cover call window')
        require(integer(row, 'rssPeakSampledBytes', 1) == peak, 'incorrect RSS peak')
        if index >= 5:
            durations.append(duration / 1e6)
            peaks.append(peak)
            idles.append(idle)

    def percentiles(values):
        ordered = sorted(values)
        return dict(p50=round(ordered[math.ceil(len(ordered) * .5) - 1], 3),
                    p95=round(ordered[math.ceil(len(ordered) * .95) - 1], 3),
                    min=round(ordered[0], 3), max=round(ordered[-1], 3))

    return dict(schemaVersion=2, kind='android-identity-resource-summary', runId=run_id,
                pid=report['pid'], sdkCommit=COMMIT, backendId=fixed['backendId'],
                runtimeVersion=fixed['runtimeVersion'], modelRevision=fixed['modelRevision'],
                inputSHA256=INPUT_SHA256, modelSHA256=dict(MODEL_HASHES),
                warmupRuns=5, measuredRuns=30, durationMs=percentiles(durations),
                rssPeakSampledBytes=dict(min=min(peaks), max=max(peaks)),
                afterIdleRssBytes=dict(min=min(idles), max=max(idles), first=idles[0], last=idles[-1]),
                beforeRssBytes=report['beforeRssBytes'], beforeBackendOpenRssBytes=report['beforeBackendOpenRssBytes'],
                processScope=fixed['processScope'], foregroundPolicy=fixed['foregroundPolicy'],
                foregroundCheckCount=72, afterCloseRssBytes=report['afterCloseRssBytes'],
                afterCloseWaitRssBytes=report['afterCloseWaitRssBytes'],
                samplingIntervalsMs=dict(count=len(intervals), **percentiles(intervals)),
                timingMethod=fixed['timingMethod'], measurementMethod=fixed['measurementMethod'],
                qualityVerdict='unverified', validationStatus='pass-bounded',
                unverified=['recognition accuracy and thresholds', 'PAD', 'natural image quality',
                            'true peak memory', 'SDK-only memory attribution', 'GPU', 'energy',
                            'minimum OS', 'sustained resource budget', 'immediate native memory release',
                            'continuous foreground visibility between lifecycle checkpoints'])
