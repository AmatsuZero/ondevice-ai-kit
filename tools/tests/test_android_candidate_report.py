'''Tests for tools.probes.validate_android_candidate_report.'''

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, 'probes'))

from validate_android_candidate_report import (  # noqa: E402
    ReportValidationError,
    validate_report,
)

# Derive fixtures from the native report contract, independently of the validator.
MODEL_SHA256 = '1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b'
CANCEL_FLAGS = (
    'nativeDecodeObserved', 'busyWhileNativeDecode', 'busyBeforeDrain',
    'noResultDelivered', 'sameBackendReentryMatched', 'callerInputUnchanged',
)
CLOSE_FLAGS = (
    'nativeDecodeObserved', 'waitedForActiveNativeRequest', 'rejectedDuringDrain',
    'noResultDelivered', 'rejectedAfterClose', 'idempotent', 'callerInputUnchanged',
)
SR_FLAGS = (
    'muxedTracksAndPtsValidated', 'firstFrameDecoded', 'audioDiscarded',
    'outputSurvivedClose', 'releaseRemovedOutput', 'cancelDrainReentry',
)


def _valid_asr(run_id, sdk_commit):
    return {
        'schemaVersion': 1,
        'runId': run_id,
        'sdkCommit': sdk_commit,
        'kind': 'asr-lifecycle',
        'pid': 4242,
        'terminal': True,
        'status': 'completed',
        'mode': 'inference',
        'result': {
            'status': 'passed',
            'scope': 'sdk-fixed-synthetic-native-lifecycle',
            'microphoneStarts': 0,
            'audioPersisted': False,
            'transcriptPersisted': False,
            'threadStacksPersisted': False,
            **{'cancel.' + f: True for f in CANCEL_FLAGS},
            **{'close.' + f: True for f in CLOSE_FLAGS},
        },
    }


def _valid_sr(run_id, sdk_commit):
    report = {
        'schemaVersion': 1,
        'runId': run_id,
        'sdkCommit': sdk_commit,
        'kind': 'sr',
        'pid': 7,
        'terminal': True,
        'status': 'completed',
        'mode': 'inference',
        'result': {'status': 'passed'},
    }
    detail = {
        'status': 'passed',
        'backend': 'litert.esrgan.x4',
        'modelSha256': MODEL_SHA256,
        'frames': 2,
        'width': 128,
        'height': 96,
        'audioTracks': 1,
        **{f: True for f in SR_FLAGS},
    }
    return report, detail


class ValidTests(unittest.TestCase):
    def test_valid_asr_lifecycle_pass(self):
        out = validate_report(_valid_asr('r1', 'c1'), run_id='r1', sdk_commit='c1', kind='asr-lifecycle')
        self.assertEqual(out, {'runId': 'r1', 'kind': 'asr-lifecycle', 'status': 'passed', 'qualityVerdict': 'unverified'})

    def test_valid_sr_pass(self):
        report, detail = _valid_sr('r2', 'c2')
        out = validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)
        self.assertEqual(out['status'], 'passed')
        self.assertEqual(out['qualityVerdict'], 'unverified')


class BadIdentityTests(unittest.TestCase):
    def test_run_id_mismatch(self):
        with self.assertRaises(ReportValidationError) as ctx:
            validate_report(_valid_asr('r1', 'c1'), run_id='WRONG', sdk_commit='c1', kind='asr-lifecycle')
        self.assertEqual(ctx.exception.code, 'IDENTITY')

    def test_sdk_commit_mismatch(self):
        with self.assertRaises(ReportValidationError):
            validate_report(_valid_asr('r1', 'c1'), run_id='r1', sdk_commit='WRONG', kind='asr-lifecycle')

    def test_kind_mismatch(self):
        with self.assertRaises(ReportValidationError):
            validate_report(_valid_asr('r1', 'c1'), run_id='r1', sdk_commit='c1', kind='sr')


class BadTypeTests(unittest.TestCase):
    def test_schema_version_bool(self):
        rep = _valid_asr('r1', 'c1')
        rep['schemaVersion'] = True
        with self.assertRaises(ReportValidationError) as ctx:
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')
        self.assertEqual(ctx.exception.code, 'TYPE')

    def test_schema_version_wrong_int(self):
        rep = _valid_asr('r1', 'c1')
        rep['schemaVersion'] = 2
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_pid_bool(self):
        rep = _valid_asr('r1', 'c1')
        rep['pid'] = True
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_pid_zero(self):
        rep = _valid_asr('r1', 'c1')
        rep['pid'] = 0
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_run_id_empty(self):
        rep = _valid_asr('r1', 'c1')
        rep['runId'] = ''
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_terminal_not_true(self):
        rep = _valid_asr('r1', 'c1')
        rep['terminal'] = 'yes'
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_microphone_starts_bool(self):
        rep = _valid_asr('r1', 'c1')
        rep['result']['microphoneStarts'] = False
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_recorder_mode_rejected(self):
        rep = _valid_asr('r1', 'c1')
        rep['mode'] = 'recorder-pass'
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')


class PartialTests(unittest.TestCase):
    def test_missing_top_level(self):
        rep = _valid_asr('r1', 'c1')
        del rep['status']
        with self.assertRaises(ReportValidationError) as ctx:
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')
        self.assertIsInstance(ctx.exception, ReportValidationError)

    def test_missing_result_scope(self):
        rep = _valid_asr('r1', 'c1')
        del rep['result']['scope']
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_sr_missing_detail(self):
        report, _ = _valid_sr('r2', 'c2')
        with self.assertRaises(ReportValidationError) as ctx:
            validate_report(report, run_id='r2', sdk_commit='c2', kind='sr')
        self.assertEqual(ctx.exception.code, 'DETAIL')

    def test_sr_detail_missing_sr_block(self):
        report, detail = _valid_sr('r2', 'c2')
        del detail['backend']
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)


class NegativeFlagsTests(unittest.TestCase):
    def test_asr_cancel_flag_false(self):
        rep = _valid_asr('r1', 'c1')
        rep['result']['cancel.noResultDelivered'] = False
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_asr_persist_flag_true(self):
        rep = _valid_asr('r1', 'c1')
        rep['result']['audioPersisted'] = True
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_sr_flag_false(self):
        report, detail = _valid_sr('r2', 'c2')
        detail['firstFrameDecoded'] = False
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)

    def test_sr_model_hash_wrong(self):
        report, detail = _valid_sr('r2', 'c2')
        detail['modelSha256'] = 'deadbeef'
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)

    def test_sr_frames_wrong(self):
        report, detail = _valid_sr('r2', 'c2')
        detail['frames'] = 3
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)

    def test_sr_outer_only_insufficient(self):
        report = {
            'schemaVersion': 1,
            'runId': 'r2',
            'sdkCommit': 'c2',
            'kind': 'sr',
            'pid': 7,
            'terminal': True,
            'status': 'completed',
            'result': {'status': 'passed'},
        }
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id='r2', sdk_commit='c2', kind='sr')


class ContractBoundaryTests(unittest.TestCase):
    def test_each_native_asr_flag_is_required(self):
        keys = ['cancel.' + flag for flag in CANCEL_FLAGS] + ['close.' + flag for flag in CLOSE_FLAGS]
        for key in keys:
            rep = _valid_asr('r1', 'c1')
            del rep['result'][key]
            with self.subTest(key=key), self.assertRaises(ReportValidationError):
                validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_each_native_sr_flag_is_required(self):
        for key in SR_FLAGS:
            report, detail = _valid_sr('r2', 'c2')
            del detail[key]
            with self.subTest(key=key), self.assertRaises(ReportValidationError):
                validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)

    def test_all_privacy_flags_must_be_false(self):
        for key in ('audioPersisted', 'transcriptPersisted', 'threadStacksPersisted'):
            rep = _valid_asr('r1', 'c1')
            rep['result'][key] = True
            with self.subTest(key=key), self.assertRaises(ReportValidationError):
                validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_nonpassed_asr_result_is_rejected(self):
        for status in ('failed', 'completed', None):
            rep = _valid_asr('r1', 'c1')
            rep['result']['status'] = status
            with self.subTest(status=status), self.assertRaises(ReportValidationError):
                validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_nonpassed_sr_detail_is_rejected(self):
        report, detail = _valid_sr('r2', 'c2')
        detail['status'] = 'failed'
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)

    def test_wrong_sr_scalar_is_rejected(self):
        for key, value in (('backend', 'wrong'), ('width', 1), ('height', 1), ('audioTracks', 2),
                           ('frames', True), ('width', True), ('height', True), ('audioTracks', True)):
            report, detail = _valid_sr('r2', 'c2')
            detail[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ReportValidationError):
                validate_report(report, run_id='r2', sdk_commit='c2', kind='sr', detail=detail)

    def test_microphone_must_not_start(self):
        rep = _valid_asr('r1', 'c1')
        rep['result']['microphoneStarts'] = 1
        with self.assertRaises(ReportValidationError):
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')

    def test_mode_must_be_explicit_inference(self):
        for mode in (None, 'native', 'recorder-fail', 'unknown'):
            rep = _valid_asr('r1', 'c1')
            rep['mode'] = mode
            with self.subTest(mode=mode), self.assertRaises(ReportValidationError):
                validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')



class SelfCheckTests(unittest.TestCase):
    def test_selfcheck_failure_is_typed(self):
        rep = _valid_asr('r1', 'c1')
        rep['result']['scope'] = 'wrong-scope'
        with self.assertRaises(ReportValidationError) as ctx:
            validate_report(rep, run_id='r1', sdk_commit='c1', kind='asr-lifecycle')
        self.assertIsInstance(ctx.exception, ValueError)
        self.assertTrue(ctx.exception.code)


if __name__ == '__main__':
    unittest.main()
