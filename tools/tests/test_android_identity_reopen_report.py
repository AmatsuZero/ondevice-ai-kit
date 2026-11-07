"""Reject false sustained-reopen evidence; fixtures are synthetic and never device results."""
import copy
import unittest

from tools.probes import android_identity_reopen_report as reducer

ROOT_START = 1_000_000_000
SESSION_END = ROOT_START + 601_000_000_000
CYCLE_GAP = 1_000_000
CYCLE_SPAN = 30_000_000_000
SAMPLE_GAP = 11_000_000


def _build_samples(cycle_start):
    samples = []
    for s in range(10):
        start = cycle_start + s * SAMPLE_GAP
        duration = (s + 1) * 1_000_000
        points = [dict(elapsedNs=t, bytes=10_000 + s * 100 + j * 100)
                  for j, t in enumerate((start - 1, start + duration // 2, start + duration + 1))]
        samples.append(dict(index=s, phase='warmup' if s < 2 else 'measurement',
                            backendId='opencv.identity.android', faceCount=1, embeddingDimension=128,
                            embeddingNormSquared=1.0, callerInputUnchanged=True,
                            startedElapsedNs=start, endedElapsedNs=start + duration, durationNs=duration,
                            rss=points, rssPeakSampledBytes=max(p['bytes'] for p in points)))
    return samples


def _build_cycle(k):
    cycle_start = ROOT_START + CYCLE_GAP + k * (CYCLE_SPAN + CYCLE_GAP)
    cycle_end = cycle_start + CYCLE_SPAN
    return dict(index=k, startedElapsedNs=cycle_start, endedElapsedNs=cycle_end,
                beforeBackendOpenRssBytes=8000 + k, afterBackendOpenRssBytes=8100 + k,
                closeDurationNs=100, closeSucceeded=True, afterCloseRssBytes=8500 + k,
                afterCloseWaitRssBytes=8300 + k, modelLeaseReleased=True,
                samples=_build_samples(cycle_start))


def fixture():
    cycles = [_build_cycle(k) for k in range(20)]
    return dict(schemaVersion=1, kind='android-identity-reopen-resource', mode='inference',
                sdkCommit=reducer.COMMIT, status='completed', terminal=True,
                backendId='opencv.identity.android', runtimeVersion='4.12.0',
                modelRevision='zoo-47534e27-v1',
                preprocessRevision='opencv-4.12-yunet640-sface112-rgb-l2-v1',
                inputSHA256=reducer.INPUT_SHA256, modelSHA256=dict(reducer.MODEL_HASHES),
                enrollmentStarts=0, cameraStarts=0, imageWrites=0, embeddingPersisted=False,
                cycleCount=20, warmupPerCycle=2, measurementPerCycle=8, minimumCycleMs=30000,
                minimumSessionMs=600000, samplingIntervalMs=20, afterCloseWaitMs=1000,
                measurementMethod='proc-self-status-VmRSS-sampled-process',
                timingMethod='sdk-quality-and-embed-wall-per-reopened-backend',
                foregroundActivity='io.ondeviceai.demo.MainActivity', foregroundStartedBeforeTiming=True,
                foregroundPolicy='RESUMED-at-cycle-and-sample-boundaries',
                processScope='process-with-idle-main-activity', workDirectoryRemoved=True,
                sourceInputUnchanged=True, callerInputUnchanged=True,
                runId='reopen-test-one', pid=123, deviceModel='TECNO KM9', sdkInt=35,
                foregroundCheckCount=462, startedElapsedNs=ROOT_START, endedElapsedNs=SESSION_END,
                cycles=cycles)


class IdentityReopenTests(unittest.TestCase):
    def reject(self, mutate):
        report = fixture()
        mutate(report)
        with self.assertRaises(ValueError):
            reducer.summarize(report)

    def test_synthetic_acceptance_and_input_unchanged(self):
        report = fixture()
        before = copy.deepcopy(report)
        summary = reducer.summarize(report)
        self.assertEqual(report, before)
        self.assertEqual(summary['schemaVersion'], 1)
        self.assertEqual(summary['kind'], 'android-identity-reopen-summary')
        self.assertEqual(summary['runId'], 'reopen-test-one')
        self.assertEqual(summary['cycleCount'], 20)
        self.assertEqual(summary['measurementCount'], 160)
        self.assertEqual(summary['sessionDurationMs'], 601000.0)
        self.assertEqual(summary['measurementDurationMs'], dict(p50=6.0, p95=10.0))
        self.assertEqual(summary['rssPeakSampledBytes'], 11100)
        self.assertIs(type(summary['rssPeakSampledBytes']), int)
        self.assertEqual(summary['afterCloseWaitRssBytes'], [8300 + k for k in range(20)])
        self.assertEqual(summary['afterCloseWaitDeltaBytes'], 19)
        self.assertIs(summary['budgetPassed'], False)
        self.assertIs(summary['productionReady'], False)
        self.assertIn('no GPU, energy, quality, calibration or production memory budget', summary['limitations'][2])

    def test_exact_identity_model_runtime_commit_and_preprocess_bound(self):
        for key, value in [('inputSHA256', '0' * 64), ('modelRevision', 'other'),
                           ('runtimeVersion', '4.13.0'), ('sdkCommit', '0' * 40),
                           ('preprocessRevision', 'other'), ('backendId', 'other'),
                           ('kind', 'android-identity-resource'), ('modelSHA256', {}),
                           ('modelSHA256', {'face': '0' * 64}), ('status', 'failed'),
                           ('terminal', False), ('mode', 'recorder-pass')]:
            with self.subTest(key=key):
                self.reject(lambda r: r.__setitem__(key, value))

    def test_terminal_failure_selfchecks_missing_and_empty_rejected(self):
        for value in (None, [], {}, dict(fixture(), cycles=[]), dict(fixture(), cycles=[fixture()['cycles'][0]])):
            with self.assertRaises(ValueError):
                reducer.summarize(value)
        self.reject(lambda r: r.pop('runId'))
        self.reject(lambda r: r.__setitem__('runId', '../bad'))
        self.reject(lambda r: r.__setitem__('runId', 'x' * 81))

    def test_bool_float_integer_aliases_rejected_everywhere(self):
        root_ints = ('schemaVersion', 'pid', 'sdkInt', 'enrollmentStarts', 'cameraStarts',
                     'imageWrites', 'cycleCount', 'warmupPerCycle', 'measurementPerCycle',
                     'minimumCycleMs', 'minimumSessionMs', 'samplingIntervalMs', 'afterCloseWaitMs',
                     'foregroundCheckCount', 'startedElapsedNs', 'endedElapsedNs')
        for key in root_ints:
            for value in (True, False, float(fixture()[key])):
                with self.subTest(key=key, value=value):
                    self.reject(lambda r: r.__setitem__(key, value))
        for key in ('index', 'faceCount', 'embeddingDimension', 'startedElapsedNs', 'endedElapsedNs',
                    'durationNs', 'rssPeakSampledBytes'):
            for value in (True, float(fixture()['cycles'][0]['samples'][2][key])):
                with self.subTest(key=key):
                    self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__(key, value))
        for key in ('elapsedNs', 'bytes'):
            self.reject(lambda r: r['cycles'][0]['samples'][2]['rss'][1].__setitem__(key, True))
        for key in ('closeDurationNs', 'beforeBackendOpenRssBytes', 'afterBackendOpenRssBytes',
                    'afterCloseRssBytes', 'afterCloseWaitRssBytes'):
            for value in (True, False, float(fixture()['cycles'][0][key])):
                with self.subTest(key=key):
                    self.reject(lambda r: r['cycles'][0].__setitem__(key, value))

    def test_negative_or_zero_resource_fields_rejected(self):
        for key in ('pid', 'beforeBackendOpenRssBytes', 'afterBackendOpenRssBytes',
                    'afterCloseRssBytes', 'afterCloseWaitRssBytes'):
            self.reject(lambda r: r.__setitem__(key, 0))
        self.reject(lambda r: r['cycles'][0].__setitem__('closeDurationNs', -1))
        self.reject(lambda r: r['cycles'][0]['samples'][2]['rss'][1].__setitem__('bytes', 0))

    def test_norm_face_count_dimension_and_input_mutation_rejected(self):
        for value in (True, '1', float('nan'), float('inf'), 10**1000, -(10**1000), 0.0, 1.00002):
            self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('embeddingNormSquared', value))
        for key, value in [('faceCount', 0), ('faceCount', 2), ('embeddingDimension', 127),
                           ('callerInputUnchanged', False), ('backendId', 'other'),
                           ('phase', 'warmup')]:
            self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__(key, value))

    def test_cleanup_and_input_integrity_cannot_be_claimed_as_success(self):
        for key in ('workDirectoryRemoved', 'sourceInputUnchanged', 'callerInputUnchanged'):
            self.reject(lambda r: r.__setitem__(key, False))
            self.reject(lambda r: r.__setitem__(key, 1))
        for key in ('closeSucceeded', 'modelLeaseReleased'):
            self.reject(lambda r: r['cycles'][0].__setitem__(key, False))
            self.reject(lambda r: r['cycles'][0].__setitem__(key, 1))
        for key, value in [('embeddingPersisted', True), ('enrollmentStarts', 1),
                           ('cameraStarts', 1), ('imageWrites', 1), ('embedding', [1] * 128),
                           ('threshold', 0.5), ('classification', 'LIVE')]:
            self.reject(lambda r: r.__setitem__(key, value))

    def test_missing_or_unexpected_root_fields_rejected(self):
        self.reject(lambda r: r.pop('deviceModel'))
        self.reject(lambda r: r.__setitem__('extraField', 1))
        self.reject(lambda r: r.__setitem__('thermalStatus', 0))

    def test_foreground_check_count_and_sdk_int_bounds(self):
        for key, value in [('foregroundCheckCount', 461), ('foregroundCheckCount', 462.0),
                           ('sdkInt', 25), ('sdkInt', 26.0), ('foregroundActivity', 'other'),
                           ('foregroundPolicy', 'continuous-visible'),
                           ('foregroundStartedBeforeTiming', False), ('processScope', 'SDK-only')]:
            with self.subTest(key=key, value=value):
                self.reject(lambda r: r.__setitem__(key, value))

    def test_cycle_count_order_overlap_and_bounds_rejected(self):
        self.reject(lambda r: r['cycles'].pop())
        self.reject(lambda r: r['cycles'].append(r['cycles'][-1]))
        self.reject(lambda r: r['cycles'][0].__setitem__('index', 1))
        self.reject(lambda r: r['cycles'][1].__setitem__('startedElapsedNs', r['cycles'][0]['startedElapsedNs']))
        self.reject(lambda r: r['cycles'][0].__setitem__('endedElapsedNs', r['cycles'][0]['startedElapsedNs'] + 1000))
        self.reject(lambda r: r['cycles'][0].__setitem__('endedElapsedNs', ROOT_START + 29_000_000_000))
        self.reject(lambda r: r['cycles'][0].__setitem__('startedElapsedNs', ROOT_START - 1))

    def test_sample_count_order_overlap_bounds_and_forge_rejected(self):
        self.reject(lambda r: r['cycles'][0]['samples'].pop())
        self.reject(lambda r: r['cycles'][0]['samples'].append(r['cycles'][0]['samples'][-1]))
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('index', 1))
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('durationNs', 123))
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('rssPeakSampledBytes', 1))
        self.reject(lambda r: r['cycles'][0]['samples'][2]['rss'].reverse())
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('rss', r['cycles'][0]['samples'][2]['rss'][1:]))
        self.reject(lambda r: r['cycles'][0]['samples'][2]['rss'][1].__setitem__('elapsedNs', r['cycles'][0]['samples'][2]['startedElapsedNs'] - 1))
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('startedElapsedNs', ROOT_START))
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('endedElapsedNs', r['cycles'][0]['endedElapsedNs'] + 1))

    def test_no_embedding_bytes_or_extra_sample_fields(self):
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('embedding', [1] * 128))
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('thermalStatus', 0))
        self.reject(lambda r: r['cycles'][0]['samples'][2].__setitem__('afterIdleRssBytes', 9000))

    def test_late_short_cycle_rejected(self):
        # Parent reproduction: a later cycle (index 19) ending only 1s after its
        # own start must be rejected. The ten samples still fit, the root session
        # stays 601s, so the old session_start-relative check wrongly accepted it.
        report = fixture()
        late = report['cycles'][19]
        late['endedElapsedNs'] = late['startedElapsedNs'] + 1_000_000_000
        with self.assertRaises(ValueError):
            reducer.summarize(report)

    def test_valid_spaced_cycles_accepted(self):
        # Default paced fixture keeps every cycle >=30s after its own start and
        # must still be accepted with a scalar RSS peak.
        report = fixture()
        summary = reducer.summarize(report)
        self.assertEqual(summary['cycleCount'], 20)
        self.assertEqual(summary['sessionDurationMs'], 601000.0)
        self.assertIs(type(summary['rssPeakSampledBytes']), int)
        self.assertEqual(summary['rssPeakSampledBytes'], 11100)

    def test_warmup_rss_peak_included_in_scalar_output(self):
        # Parent reproduction: a higher warmup reading (sample 0) must raise the
        # scalar peak; output is a single int, not a measured-only {min,max} dict.
        report = fixture()
        warmup = report['cycles'][5]['samples'][0]
        warmup['rss'][2]['bytes'] = 900_000_000
        warmup['rssPeakSampledBytes'] = 900_000_000
        summary = reducer.summarize(report)
        self.assertIs(type(summary['rssPeakSampledBytes']), int)
        self.assertEqual(summary['rssPeakSampledBytes'], 900_000_000)


if __name__ == '__main__':
    unittest.main()
