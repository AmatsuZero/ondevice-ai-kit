import json
import unittest

from tools.probes import android_realtime_sr_device as probe
from tools.probes.android_realtime_sr_report import assess
from tools.tests.test_android_realtime_sr_report import make_index, make_run, latch


RUN_ID = '0123456789abcdef0123456789abcdef'
GENERATION = 7


def realistic_raw():
    run = {
        'schemaVersion': 1,
        'runId': RUN_ID,
        'fixtureSha256': probe.KNOWN_SHA,
        'generation': GENERATION,
        'loops': 1,
        'mode': 'ORIGINAL',
        'displayTimingSupported': False,
        'completed': True,
    }
    term = {
        'runId': RUN_ID,
        'case': 'original-egl',
        'status': 'completed',
        'detail': 'decode/audio complete',
        'frameCallbacks': 900,
        'lastAudioUs': 30_000_000,
        'presentationSupported': False,
        'swapBackends': {},
        'displayCount': 0,
    }
    rows = [
        {
            'runId': RUN_ID,
            'generation': GENERATION,
            'loop': 0,
            'sourceOrdinal': ordinal,
            'stage': 'decodeRelease',
            'ptsUs': ordinal * 1_000_000 // 30,
            'targetNs': ordinal * 1_000_000_000 // 30,
        }
        for ordinal in range(900)
    ]
    return term, run, ('\n'.join(json.dumps(row) for row in rows) + '\n').encode()


class ValidateDecodeTests(unittest.TestCase):
    def test_valid_realistic_raw(self):
        term, run, events = realistic_raw()
        self.assertIsNone(probe.validate_decode(term, run, events, RUN_ID, 'original-egl'))

    def test_original_ready_requires_early_submit_readiness(self):
        term, run, events = realistic_raw()
        term['case'] = 'original-ready'
        self.assertIsNotNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-ready')
        )

        run['earlySubmitReady'] = False
        self.assertIsNotNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-ready')
        )

        run['earlySubmitReady'] = True
        self.assertIsNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-ready')
        )

        term['case'] = 'original-egl'
        self.assertIsNotNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-egl')
        )

    def test_short_ready_expected_case_order(self):
        self.assertEqual(
            [launch for _, launch in probe.legs_for('short-ready')],
            [
                'original-ready',
                'spatial-ready',
                'spatial',
                'spatial-ready',
                'original-ready',
            ],
        )
        self.assertIn('spatial', probe.CASES)
        self.assertIn('spatial-ready', probe.CASES)

    def test_explicit_expected_sha_accepts_only_verified_fixture_profiles(self):
        term, run, events = realistic_raw()
        sha_540p = 'e698bee8c98c3158eb5e99e26f1cbe13334b52557720b2129a3e31dd2fa67dcb'
        run['fixtureSha256'] = sha_540p
        self.assertIsNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-egl', expected_sha=sha_540p)
        )

        run['fixtureSha256'] = probe.KNOWN_SHA
        self.assertIsNotNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-egl', expected_sha=sha_540p)
        )

        run['fixtureSha256'] = sha_540p
        self.assertIsNotNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-egl', expected_sha=probe.KNOWN_SHA)
        )

        unknown_sha = '0000000000000000000000000000000000000000000000000000000000000000'
        run['fixtureSha256'] = unknown_sha
        self.assertIsNotNone(
            probe.validate_decode(term, run, events, RUN_ID, 'original-egl', expected_sha=unknown_sha)
        )

    def test_rejects_wrong_pts(self):
        term, run, events = realistic_raw()
        rows = [json.loads(line) for line in events.splitlines()]
        rows[37]['ptsUs'] += 10
        events = ('\n'.join(json.dumps(row) for row in rows) + '\n').encode()
        self.assertIn('ptsUs', probe.validate_decode(term, run, events, RUN_ID, 'original-egl'))

    def test_rejects_bad_audio(self):
        term, run, events = realistic_raw()
        term['lastAudioUs'] = 28_000_000
        self.assertEqual(
            probe.validate_decode(term, run, events, RUN_ID, 'original-egl'),
            'invalid lastAudioUs',
        )

    def test_late_latch_remains_valid_with_forward_target_window(self):
        event = latch(0)
        event['latchBeginNs'] = event['targetNs'] + 1
        result = assess(make_index(1), make_run(), [event])
        self.assertEqual(result['counts']['unconfirmed'], 1)
        event['nextTargetNs'] = event['targetNs']
        with self.assertRaises(ValueError):
            assess(make_index(1), make_run(), [event])


if __name__ == '__main__':
    unittest.main()
