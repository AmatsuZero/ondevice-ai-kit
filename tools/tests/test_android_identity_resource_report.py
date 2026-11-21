"""Reject false resource evidence; fixtures are synthetic and never device results."""
import copy
import unittest

from tools.probes import android_identity_resource_report as reducer


def fixture():
    rows = []
    for index in range(35):
        start = 1_000_000_000 + index * 100_000_000
        duration = (index + 1) * 1_000_000
        points = [dict(elapsedNs=t, bytes=10_000 + index * 100 + j * 100)
                  for j, t in enumerate((start - 1, start + duration // 2, start + duration + 1))]
        rows.append(dict(index=index, phase='warmup' if index < 5 else 'measurement',
                         backendId='opencv.identity.android', faceCount=1, embeddingDimension=128,
                         embeddingNormSquared=1.0, callerInputUnchanged=True,
                         startedElapsedNs=start, endedElapsedNs=start + duration, durationNs=duration,
                         rss=points, rssPeakSampledBytes=max(p['bytes'] for p in points),
                         afterIdleRssBytes=9000 + index))
    return dict(schemaVersion=2, kind='android-identity-resource', runId='identity-test-one', mode='inference',
                sdkCommit='636a6eb8854db5dff74244c42c3ce0e82bd2be9f', status='completed', terminal=True,
                pid=123, deviceModel='TECNO KM9', sdkInt=35, backendId='opencv.identity.android',
                runtimeVersion='4.12.0', modelRevision='zoo-47534e27-v1',
                preprocessRevision='opencv-4.12-yunet640-sface112-rgb-l2-v1',
                inputSHA256='88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5',
                modelSHA256={
                    'face_detection_yunet_2023mar.onnx': '8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4',
                    'face_recognition_sface_2021dec.onnx': '0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79'},
                enrollmentStarts=0, cameraStarts=0, imageWrites=0, embeddingPersisted=False,
                warmupCount=5, measurementCount=30, samplingIntervalMs=20,
                measurementMethod='proc-self-status-VmRSS-sampled-process',
                timingMethod='sdk-quality-and-embed-wall-after-one-backend-open', samples=rows,
                finalCloseSucceeded=True, modelLeaseReleased=True, workDirectoryRemoved=True,
                sourceInputUnchanged=True, callerInputUnchanged=True, closeDurationNs=100,
                beforeRssBytes=9000, beforeBackendOpenRssBytes=8000,
                foregroundActivity='io.ondeviceai.demo.MainActivity', foregroundStartedBeforeTiming=True,
                foregroundCheckCount=72, foregroundPolicy='RESUMED-at-72-checkpoints',
                processScope='process-with-idle-main-activity',
                afterCloseRssBytes=8500, afterCloseWaitMs=1000,
                afterCloseWaitRssBytes=8300)


class IdentityResourceTests(unittest.TestCase):
    def reject(self, mutate):
        report = fixture()
        mutate(report)
        with self.assertRaises(ValueError):
            reducer.summarize(report)

    def test_warmups_excluded_nearest_rank_and_peak_recomputed(self):
        report = fixture()
        before = copy.deepcopy(report)
        summary = reducer.summarize(report)
        self.assertEqual(report, before)
        self.assertEqual(summary['durationMs'], dict(p50=20.0, p95=34.0, min=6.0, max=35.0))
        self.assertEqual(summary['rssPeakSampledBytes'], dict(min=10700, max=13600))
        self.assertEqual(summary['qualityVerdict'], 'unverified')
        self.assertEqual(summary['measuredRuns'], 30)
        self.assertEqual(summary['kind'], 'android-identity-resource-summary')
        self.assertEqual(summary['sdkCommit'], report['sdkCommit'])
        self.assertEqual(summary['samplingIntervalsMs']['count'], 70)

    def test_exact_input_model_runtime_commit_and_preprocess_bound(self):
        for key, value in [('inputSHA256', '0' * 64), ('modelRevision', 'other'),
                           ('runtimeVersion', '4.13.0'), ('sdkCommit', '0' * 40),
                           ('preprocessRevision', 'other'), ('backendId', 'other'),
                           ('modelSHA256', {}), ('modelSHA256', {'face': '0' * 64})]:
            with self.subTest(key=key):
                self.reject(lambda r: r.__setitem__(key, value))

    def test_terminal_failure_selfchecks_missing_and_empty_rejected(self):
        for value in (None, [], {}, dict(fixture(), samples=[])):
            with self.assertRaises(ValueError):
                reducer.summarize(value)
        for key, value in [('terminal', False), ('status', 'failed'), ('mode', 'recorder-pass'),
                           ('kind', 'android-asr-resource'), ('runId', '../bad'), ('errorClass', 'failure')]:
            with self.subTest(key=key):
                self.reject(lambda r: r.__setitem__(key, value))

    def test_bool_float_integer_aliases_rejected_everywhere(self):
        for key in ('schemaVersion', 'pid', 'sdkInt', 'enrollmentStarts', 'cameraStarts', 'imageWrites',
                    'warmupCount', 'measurementCount', 'samplingIntervalMs', 'afterCloseWaitMs',
                    'beforeRssBytes', 'beforeBackendOpenRssBytes', 'foregroundCheckCount', 'afterCloseRssBytes', 'afterCloseWaitRssBytes', 'closeDurationNs'):
            for value in (True, False, float(fixture()[key])):
                with self.subTest(key=key, value=value):
                    self.reject(lambda r: r.__setitem__(key, value))
        for key in ('index', 'faceCount', 'embeddingDimension', 'startedElapsedNs', 'endedElapsedNs',
                    'durationNs', 'rssPeakSampledBytes', 'afterIdleRssBytes'):
            for value in (True, float(fixture()['samples'][5][key])):
                with self.subTest(key=key):
                    self.reject(lambda r: r['samples'][5].__setitem__(key, value))
        for key in ('elapsedNs', 'bytes'):
            self.reject(lambda r: r['samples'][5]['rss'][1].__setitem__(key, True))

    def test_negative_or_zero_resource_fields_rejected(self):
        for key in ('beforeRssBytes', 'afterCloseRssBytes', 'afterCloseWaitRssBytes', 'pid'):
            self.reject(lambda r: r.__setitem__(key, 0))
        self.reject(lambda r: r.__setitem__('closeDurationNs', -1))
        self.reject(lambda r: r['samples'][5]['rss'][1].__setitem__('bytes', 0))

    def test_norm_face_count_dimension_and_input_mutation_rejected(self):
        for value in (True, '1', float('nan'), float('inf'), 10**1000, -(10**1000), 0.0, 1.00002):
            self.reject(lambda r: r['samples'][5].__setitem__('embeddingNormSquared', value))
        for key, value in [('faceCount', 0), ('faceCount', 2), ('embeddingDimension', 127),
                           ('callerInputUnchanged', False), ('backendId', 'other')]:
            self.reject(lambda r: r['samples'][5].__setitem__(key, value))

    def test_duplicate_truncated_warmup_relabel_or_overlapping_samples_rejected(self):
        self.reject(lambda r: r['samples'].pop())
        self.reject(lambda r: r['samples'].append(r['samples'][-1]))
        self.reject(lambda r: r['samples'][5].__setitem__('index', 4))
        self.reject(lambda r: r['samples'][5].__setitem__('phase', 'warmup'))
        self.reject(lambda r: r['samples'][5].__setitem__('startedElapsedNs', r['samples'][4]['endedElapsedNs']))

    def test_forged_duration_peak_and_sampling_window_rejected(self):
        self.reject(lambda r: r['samples'][5].__setitem__('durationNs', 123))
        self.reject(lambda r: r['samples'][5].__setitem__('rssPeakSampledBytes', 1))
        self.reject(lambda r: r['samples'][5]['rss'].reverse())
        self.reject(lambda r: r['samples'][5].__setitem__('rss', r['samples'][5]['rss'][1:]))
        self.reject(lambda r: r['samples'][5]['rss'][1].__setitem__('elapsedNs', r['samples'][5]['startedElapsedNs'] - 1))

    def test_foreground_checkpoint_and_baseline_scope_required(self):
        try:
            summary = reducer.summarize(fixture())
        except ValueError as error:
            self.fail('expected controlled foreground report acceptance: ' + str(error))
        self.assertEqual(summary['beforeBackendOpenRssBytes'], 8000)
        self.assertEqual(summary['processScope'], 'process-with-idle-main-activity')
        for key, value in [('schemaVersion', 1), ('foregroundStartedBeforeTiming', False),
                           ('foregroundStartedBeforeTiming', 1), ('foregroundActivity', 'other'),
                           ('foregroundCheckCount', 71), ('foregroundCheckCount', 72.0),
                           ('foregroundPolicy', 'continuous-visible'), ('processScope', 'SDK-only'),
                           ('beforeBackendOpenRssBytes', 0)]:
            with self.subTest(key=key, value=value):
                self.reject(lambda r: r.__setitem__(key, value))
        self.reject(lambda r: r.pop('foregroundStartedBeforeTiming'))

    def test_privacy_cleanup_and_policy_fields_cannot_be_claimed_as_success(self):
        for key in ('finalCloseSucceeded', 'modelLeaseReleased', 'workDirectoryRemoved',
                    'sourceInputUnchanged', 'callerInputUnchanged'):
            self.reject(lambda r: r.__setitem__(key, False))
            self.reject(lambda r: r.__setitem__(key, 1))
        for key, value in [('embeddingPersisted', True), ('enrollmentStarts', 1),
                           ('cameraStarts', 1), ('imageWrites', 1), ('embedding', [1] * 128),
                           ('threshold', 0.5), ('classification', 'LIVE')]:
            self.reject(lambda r: r.__setitem__(key, value))
        self.reject(lambda r: r['samples'][5].__setitem__('embedding', [1] * 128))


if __name__ == '__main__':
    unittest.main()
