"""Strict reduction of one fixed-image Identity sustained-reopen session. No I/O."""
import math
import re

# Fixed root copied verbatim from the shared immutable fixed-root.json protocol.
COMMIT = '636a6eb8854db5dff74244c42c3ce0e82bd2be9f'
INPUT_SHA256 = '88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5'
MODEL_HASHES = {
    'face_detection_yunet_2023mar.onnx': '8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4',
    'face_recognition_sface_2021dec.onnx': '0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79',
}

FIXED = dict(
    schemaVersion=1, kind='android-identity-reopen-resource', mode='inference',
    sdkCommit=COMMIT, status='completed', terminal=True, backendId='opencv.identity.android',
    runtimeVersion='4.12.0', modelRevision='zoo-47534e27-v1',
    preprocessRevision='opencv-4.12-yunet640-sface112-rgb-l2-v1', inputSHA256=INPUT_SHA256,
    modelSHA256=MODEL_HASHES, enrollmentStarts=0, cameraStarts=0, imageWrites=0,
    embeddingPersisted=False, cycleCount=20, warmupPerCycle=2, measurementPerCycle=8,
    minimumCycleMs=30000, minimumSessionMs=600000, samplingIntervalMs=20, afterCloseWaitMs=1000,
    measurementMethod='proc-self-status-VmRSS-sampled-process',
    timingMethod='sdk-quality-and-embed-wall-per-reopened-backend',
    foregroundActivity='io.ondeviceai.demo.MainActivity', foregroundStartedBeforeTiming=True,
    foregroundPolicy='RESUMED-at-cycle-and-sample-boundaries',
    processScope='process-with-idle-main-activity', workDirectoryRemoved=True,
    sourceInputUnchanged=True, callerInputUnchanged=True,
)

ROOT_DYNAMIC = {'runId', 'pid', 'deviceModel', 'sdkInt', 'foregroundCheckCount',
                'startedElapsedNs', 'endedElapsedNs', 'cycles'}
CYCLE_FIELDS = {'index', 'startedElapsedNs', 'endedElapsedNs', 'beforeBackendOpenRssBytes',
                'afterBackendOpenRssBytes', 'closeDurationNs', 'closeSucceeded',
                'afterCloseRssBytes', 'afterCloseWaitRssBytes', 'modelLeaseReleased', 'samples'}
SAMPLE_FIELDS = {'index', 'phase', 'backendId', 'faceCount', 'embeddingDimension',
                 'embeddingNormSquared', 'callerInputUnchanged', 'startedElapsedNs',
                 'endedElapsedNs', 'durationNs', 'rss', 'rssPeakSampledBytes'}
RSS_POINT_FIELDS = {'elapsedNs', 'bytes'}


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

    require(isinstance(report, dict), 'report must be an object')
    require(set(report) == set(FIXED) | ROOT_DYNAMIC, 'unexpected or missing report fields')
    for key, value in FIXED.items():
        exact(report, key, value)
    run_id = field(report, 'runId')
    require(isinstance(run_id, str) and re.fullmatch('[A-Za-z0-9-]{1,80}', run_id), 'invalid runId')
    integer(report, 'pid', 1)
    require(isinstance(report['deviceModel'], str) and report['deviceModel'].strip(), 'invalid deviceModel')
    integer(report, 'sdkInt', 26)
    integer(report, 'foregroundCheckCount', 462)
    session_start = integer(report, 'startedElapsedNs', 1)
    session_end = integer(report, 'endedElapsedNs', 1)
    require(session_end >= session_start + 600_000_000_000, 'session shorter than minimum')

    cycles = field(report, 'cycles')
    require(isinstance(cycles, list) and len(cycles) == 20, 'expected exactly 20 cycles')
    measurement_durations, after_close_wait = [], []
    all_rss_peak = 0
    previous_cycle_end = 0
    for c_index, cycle in enumerate(cycles):
        require(isinstance(cycle, dict) and set(cycle) == CYCLE_FIELDS, 'unexpected or missing cycle fields')
        exact(cycle, 'index', c_index)
        exact(cycle, 'closeSucceeded', True)
        exact(cycle, 'modelLeaseReleased', True)
        cycle_start = integer(cycle, 'startedElapsedNs', 1)
        cycle_end = integer(cycle, 'endedElapsedNs', 1)
        require(cycle_end >= cycle_start + 30_000_000_000, 'cycle shorter than minimum')
        require(cycle_start >= session_start and cycle_end <= session_end, 'cycle outside session bounds')
        require(cycle_start >= previous_cycle_end, 'cycles must be ordered and nonoverlapping')
        previous_cycle_end = cycle_end
        integer(cycle, 'beforeBackendOpenRssBytes', 1)
        integer(cycle, 'afterBackendOpenRssBytes', 1)
        integer(cycle, 'closeDurationNs', 0)
        integer(cycle, 'afterCloseRssBytes', 1)
        after_close_wait.append(integer(cycle, 'afterCloseWaitRssBytes', 1))

        samples = field(cycle, 'samples')
        require(isinstance(samples, list) and len(samples) == 10, 'expected exactly 10 samples per cycle')
        previous_sample_end = 0
        for s_index, sample in enumerate(samples):
            require(isinstance(sample, dict) and set(sample) == SAMPLE_FIELDS, 'unexpected or missing sample fields')
            exact(sample, 'index', s_index)
            exact(sample, 'phase', 'warmup' if s_index < 2 else 'measurement')
            exact(sample, 'backendId', FIXED['backendId'])
            exact(sample, 'faceCount', 1)
            exact(sample, 'embeddingDimension', 128)
            exact(sample, 'callerInputUnchanged', True)
            norm = field(sample, 'embeddingNormSquared')
            require(type(norm) in (int, float) and -(2**63) <= norm <= 2**63 - 1,
                    'invalid normalized embedding diagnostic')
            if isinstance(norm, float):
                require(math.isfinite(norm), 'invalid normalized embedding diagnostic')
            require(0.99999 < norm < 1.00001, 'invalid normalized embedding diagnostic')
            sample_start = integer(sample, 'startedElapsedNs', 1)
            sample_end = integer(sample, 'endedElapsedNs', 1)
            duration = integer(sample, 'durationNs', 1)
            require(sample_start > session_start, 'sample must start after session start')
            require(sample_end - sample_start == duration, 'forged sample duration')
            require(sample_start >= cycle_start and sample_end <= cycle_end, 'sample outside cycle bounds')
            require(sample_start >= previous_sample_end, 'samples must be ordered and nonoverlapping')
            previous_sample_end = sample_end
            readings = field(sample, 'rss')
            require(isinstance(readings, list) and len(readings) >= 3, 'insufficient RSS readings')
            previous_time, peak, inside = 0, 0, False
            for point in readings:
                require(isinstance(point, dict) and set(point) == RSS_POINT_FIELDS, 'invalid RSS point')
                timestamp = integer(point, 'elapsedNs', 1)
                value = integer(point, 'bytes', 1)
                require(timestamp > previous_time, 'RSS points must be strictly ordered')
                previous_time = timestamp
                peak = max(peak, value)
                inside |= sample_start <= timestamp <= sample_end
            require(readings[0]['elapsedNs'] <= sample_start
                    and readings[-1]['elapsedNs'] >= sample_end and inside,
                    'RSS points do not cover call window')
            require(integer(sample, 'rssPeakSampledBytes', 1) == peak, 'incorrect RSS peak')
            all_rss_peak = max(all_rss_peak, peak)
            if s_index >= 2:
                measurement_durations.append(duration / 1e6)

    def percentiles(values):
        ordered = sorted(values)
        return dict(p50=round(ordered[math.ceil(len(ordered) * .5) - 1], 3),
                    p95=round(ordered[math.ceil(len(ordered) * .95) - 1], 3))

    return dict(
        schemaVersion=1, kind='android-identity-reopen-summary', runId=run_id,
        cycleCount=20, measurementCount=160,
        sessionDurationMs=round((session_end - session_start) / 1e6, 3),
        measurementDurationMs=percentiles(measurement_durations),
        rssPeakSampledBytes=all_rss_peak,
        afterCloseWaitRssBytes=after_close_wait,
        afterCloseWaitDeltaBytes=after_close_wait[-1] - after_close_wait[0],
        budgetPassed=False, productionReady=False,
        limitations=[
            'RSS sampled from process VmRSS; process-scope only, not true peak and not SDK-only attribution',
            'paced 20-cycle / >=10min observation, not indefinite monitoring or maximum-duty stress',
            'no GPU, energy, quality, calibration or production memory budget measured or verified',
            'embeddings and identity data absent; recognition accuracy and thresholds unverified',
        ],
    )
