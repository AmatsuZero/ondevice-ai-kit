'''Pure-dict validation for ondevice-ai-kit Android candidate reports.

This module owns only the validation of Android candidate reports. It performs
no I/O, no device interaction and no quality inference: every report that passes
validation is reported with qualityVerdict: 'unverified'. Downstream release
approval is out of scope.
'''

from __future__ import annotations

__all__ = ['ReportValidationError', 'validate_report']

MODEL_SHA256 = '1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b'

SUPPORTED_KINDS = ('asr-lifecycle', 'sr')

_CANCEL_FLAGS = (
    'nativeDecodeObserved',
    'busyWhileNativeDecode',
    'busyBeforeDrain',
    'noResultDelivered',
    'sameBackendReentryMatched',
    'callerInputUnchanged',
)
_CLOSE_FLAGS = (
    'nativeDecodeObserved',
    'waitedForActiveNativeRequest',
    'rejectedDuringDrain',
    'noResultDelivered',
    'rejectedAfterClose',
    'idempotent',
    'callerInputUnchanged',
)
_SR_FLAGS = (
    'muxedTracksAndPtsValidated',
    'firstFrameDecoded',
    'audioDiscarded',
    'outputSurvivedClose',
    'releaseRemovedOutput',
    'cancelDrainReentry',
)


class ReportValidationError(ValueError):
    '''Raised when a candidate report (or its detail) fails validation.

    code is a short, stable identifier of the failure class so callers can
    branch on the type of malformed input rather than parsing the message.
    '''

    def __init__(self, message, code='INVALID'):
        super().__init__(message)
        self.code = code


def _is_int(value):
    return isinstance(value, int) and not isinstance(value, bool)


def _require(cond, message, code):
    if not cond:
        raise ReportValidationError(message, code)


def _require_str(report, key, ctx=''):
    _require(key in report, f'{ctx}{key} missing', 'MISSING')
    value = report[key]
    _require(isinstance(value, str) and value != '', f'{ctx}{key} must be a nonempty string', 'TYPE')
    return value


def _require_int(report, key, *, positive=False, allow_zero=False, ctx=''):
    _require(key in report, f'{ctx}{key} missing', 'MISSING')
    value = report[key]
    _require(_is_int(value), f'{ctx}{key} must be an int (bool rejected)', 'TYPE')
    if positive:
        _require(value > 0, f'{ctx}{key} must be positive', 'VALUE')
    if allow_zero:
        _require(value >= 0, f'{ctx}{key} must be >= 0', 'VALUE')
    return value


def _require_true(value, name):
    _require(value is True, f'{name} must be exactly True', 'VALUE')


def _require_false(value, name):
    _require(value is False, f'{name} must be exactly False', 'VALUE')


def _validate_top_level(report, run_id, sdk_commit, kind):
    _require(isinstance(report, dict), 'report must be a dict', 'TYPE')

    sv = report.get('schemaVersion')
    _require(sv is not None, 'schemaVersion missing', 'MISSING')
    _require(_is_int(sv) and sv == 1, 'schemaVersion must be int 1 (bool rejected)', 'TYPE')

    rid = _require_str(report, 'runId')
    sc = _require_str(report, 'sdkCommit')
    rk = _require_str(report, 'kind')
    _require(rid == run_id, 'runId does not match expected run_id', 'IDENTITY')
    _require(sc == sdk_commit, 'sdkCommit does not match expected sdk_commit', 'IDENTITY')
    _require(rk == kind, 'kind does not match expected kind', 'IDENTITY')

    _require_int(report, 'pid', positive=True)
    _require(report.get('terminal') is True, 'terminal must be exactly True', 'VALUE')
    _require(report.get('status') == 'completed', 'status must be completed', 'VALUE')

    _require(report.get('mode') == 'inference', 'mode must be inference', 'VALUE')

    _require(kind in SUPPORTED_KINDS, f'unsupported kind: {kind!r}', 'VALUE')


def _validate_asr_lifecycle(report):
    result = report.get('result')
    _require(isinstance(result, dict), 'result missing or not a dict', 'MISSING')
    _require(result.get('status') == 'passed', 'result.status must be passed', 'VALUE')
    _require(result.get('scope') == 'sdk-fixed-synthetic-native-lifecycle', 'result.scope mismatch', 'VALUE')

    ms = result.get('microphoneStarts')
    _require(_is_int(ms) and ms == 0, 'microphoneStarts must be int 0 (bool rejected)', 'TYPE')
    _require_false(result.get('audioPersisted'), 'audioPersisted')
    _require_false(result.get('transcriptPersisted'), 'transcriptPersisted')
    _require_false(result.get('threadStacksPersisted'), 'threadStacksPersisted')

    for flag in _CANCEL_FLAGS:
        _require_true(result.get(f'cancel.{flag}'), f'cancel.{flag}')
    for flag in _CLOSE_FLAGS:
        _require_true(result.get(f'close.{flag}'), f'close.{flag}')


def _validate_sr(report, detail):
    result = report.get('result')
    _require(isinstance(result, dict), 'result missing or not a dict', 'MISSING')
    _require(result.get('status') == 'passed', 'result.status must be passed', 'VALUE')

    _require(detail is not None, 'sr validation requires the detail dict (SR private JSON)', 'DETAIL')
    _require(isinstance(detail, dict), 'detail must be a dict', 'TYPE')

    _require(detail.get('status') == 'passed', 'detail.status must be passed', 'VALUE')
    _require(detail.get('backend') == 'litert.esrgan.x4', 'detail.backend mismatch', 'VALUE')
    sr = detail
    _require(sr.get('modelSha256') == MODEL_SHA256, 'sr.modelSha256 mismatch', 'VALUE')
    _require(_is_int(sr.get('frames')) and sr['frames'] == 2, 'sr.frames must be int 2', 'VALUE')
    # Fixed approved 64x48 input (8277f1da...) uses the native x4 request.
    _require(_is_int(sr.get('width')) and sr['width'] == 256, 'sr.width must be int 256', 'VALUE')
    _require(_is_int(sr.get('height')) and sr['height'] == 192, 'sr.height must be int 192', 'VALUE')
    _require(_is_int(sr.get('audioTracks')) and sr['audioTracks'] == 1, 'sr.audioTracks must be int 1', 'VALUE')
    for flag in _SR_FLAGS:
        _require_true(sr.get(flag), f'sr.{flag}')


def validate_report(report, *, run_id, sdk_commit, kind, detail=None):
    '''Validate an Android candidate report and return a concise summary.

    Pure dict validation only. User media, turn scores and host exit codes are
    never inspected, so every accepted report carries qualityVerdict:
    'unverified' -- this is a candidate gate, not a release approval.

    Raises:
        ReportValidationError: on any structural, typing or value mismatch.
    '''
    _validate_top_level(report, run_id, sdk_commit, kind)

    if kind == 'asr-lifecycle':
        _validate_asr_lifecycle(report)
    else:
        _validate_sr(report, detail)

    return {
        'runId': run_id,
        'kind': kind,
        'status': 'passed',
        'qualityVerdict': 'unverified',
    }
