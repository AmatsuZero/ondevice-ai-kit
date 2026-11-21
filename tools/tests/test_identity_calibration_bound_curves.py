"""Wiring tests for metrics['empiricalCurves'] in the bound score report.

These tests reuse the existing synthetic manifest fixture (CalibrationManifestTests);
they do not rebuild fixtures or reimplement empirical_curve. AUC expectations are
hand-calculated independently as the Mann-Whitney statistic over responding
positive/negative pairs (1 for a strictly better-ranked positive, 0.5 for a tie,
0 for a worse-ranked positive), divided by n_pos_resp * n_neg_resp. This equals
the trapezoidal ROC integral of the supplied empirical_curve for finite inputs,
and it is derived here without ever calling empirical_curve to produce a value.

The curves are descriptive response-only observations; nothing here is a
production policy, confidence interval, EER, interpolation or pass threshold.
"""
import copy
import hashlib
import importlib
import json
from pathlib import Path
import subprocess
import sys
import unittest
from unittest import mock

from tools.identity_calibration_manifest import validate_manifest
from tools.tests import test_identity_calibration_manifest as manifest_fixture


class BoundCurveScoreTests(unittest.TestCase):
    def setUp(self):
        self.fixture = manifest_fixture.CalibrationManifestTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        self.manifest = self.fixture.document
        self.module = importlib.import_module('tools.identity_calibration_scores')
        self.real_curve = importlib.import_module('tools.identity_calibration_curves').empirical_curve

    def verification_rows(self, scores):
        # first two pairs are genuine (equal subjectId), last two are impostor
        pairs = [('calibration-0', 'calibration-2'), ('calibration-1', 'calibration-3'),
                 ('calibration-0', 'calibration-3'), ('calibration-1', 'calibration-2')]
        return [dict(enrollmentId=e, probeId=p, score=s) for (e, p), s in zip(pairs, scores)]

    def make_scores(self, rows, threshold=.5):
        checked = validate_manifest(self.manifest, self.root)
        return dict(schemaVersion=1, kind='identity-calibration-scores',
                    manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
                    binding=copy.deepcopy(self.manifest['binding']), task=self.manifest['task'],
                    split='calibration', threshold=threshold, rows=rows)

    def summarize(self, scores):
        return self.module.summarize(self.manifest, scores, self.root)

    def identification_scores(self):
        self.manifest['task'] = 'identification'
        unknown = dict(id='calibration-unknown', subjectId='calibration-unknown-subject',
                       sessionId='probe', mediaGroupId='unknown-media', split='calibration',
                       role='probe', label='bona-fide', pai=None,
                       file=self.fixture.file('media/unknown.bin', b'synthetic unknown'))
        self.manifest['samples'].append(unknown)
        matrix = [('calibration-2', [.8, .4]), ('calibration-3', [.9, .3]),
                  ('calibration-unknown', [.7, .2])]
        rows = [dict(enrollmentId=f'calibration-{i}', probeId=p, score=v)
                for p, values in matrix for i, v in enumerate(values)]
        return self.make_scores(rows)

    # ---- verification: hand-calculated AUC, ties, directions, nonresponse ----

    def test_verification_auc_hand_calculated_and_points(self):
        rows = self.verification_rows([.9, .8, .3, .2])
        scores = self.make_scores(rows)
        before = copy.deepcopy((self.manifest, scores))
        result = self.summarize(scores)
        overall = result['metrics']['empiricalCurves']['overall']
        # 2 genuine (.9,.8), 2 impostor (.3,.2): all 4 pos-neg pairs favour the
        # positive -> Mann-Whitney 4/4 = 1.0 (hand calculated).
        self.assertEqual(overall['trialCount'], 4)
        self.assertEqual(overall['scoreDirection'], 'higher-is-positive')
        self.assertAlmostEqual(overall['aucOnResponse'], 1.0)
        self.assertEqual([p['threshold'] for p in overall['points']], [None, .9, .8, .3, .2])
        self.assertEqual([p['rule'] for p in overall['points']],
                         ['reject-all'] + ['inclusive-threshold'] * 4)
        first, last = overall['points'][0], overall['points'][-1]
        self.assertAlmostEqual(first['frrOnResponse'], 1.0)
        self.assertAlmostEqual(first['farOnResponse'], 0.0)
        self.assertAlmostEqual(first['tprOnResponse'], 0.0)
        self.assertAlmostEqual(last['frrOnResponse'], 0.0)
        self.assertAlmostEqual(last['farOnResponse'], 1.0)
        self.assertAlmostEqual(last['tprOnResponse'], 1.0)
        self.assertEqual((self.manifest, scores), before)

    def test_verification_equal_scores_ties_and_inclusive_threshold(self):
        rows = self.verification_rows([.5, .5, .5, .5])
        result = self.summarize(self.make_scores(rows, threshold=.5))
        overall = result['metrics']['empiricalCurves']['overall']
        # all four scores tie: one inclusive cutoff; 4 pos-neg pairs are ties
        # -> Mann-Whitney 0.5*4 / 4 = 0.5 (hand calculated).
        self.assertEqual(len(overall['points']), 2)
        tied = overall['points'][1]
        self.assertEqual(tied['threshold'], .5)
        self.assertEqual(tied['rule'], 'inclusive-threshold')
        self.assertAlmostEqual(overall['aucOnResponse'], 0.5)
        self.assertAlmostEqual(tied['farOnResponse'], 1.0)
        self.assertAlmostEqual(tied['frrOnResponse'], 0.0)
        self.assertAlmostEqual(tied['tprOnResponse'], 1.0)
        # inclusive threshold: score == threshold is accepted, ties are not rejected
        self.assertEqual(result['metrics']['farOnResponse'], 1.0)
        self.assertEqual(result['metrics']['frrOnResponse'], 0.0)

    def test_both_directions_use_validated_exact_higher_bool(self):
        base = [.9, .8, .3, .2]
        with mock.patch.object(self.module, 'empirical_curve', wraps=self.real_curve) as spy_hi:
            result_hi = self.summarize(self.make_scores(self.verification_rows(base), threshold=.5))
            self.assertEqual(spy_hi.call_count, 1)
            self.assertIs(spy_hi.call_args[0][1], True)  # higher-is-match exact bool
        hi = result_hi['metrics']['empiricalCurves']['overall']
        self.assertEqual(hi['scoreDirection'], 'higher-is-positive')
        self.assertAlmostEqual(hi['aucOnResponse'], 1.0)
        self.assertEqual([p['threshold'] for p in hi['points']], [None, .9, .8, .3, .2])

        self.manifest['binding']['scoreDirection'] = 'lower-is-match'
        rows_lo = self.verification_rows([-s for s in base])
        with mock.patch.object(self.module, 'empirical_curve', wraps=self.real_curve) as spy_lo:
            result_lo = self.summarize(self.make_scores(rows_lo, threshold=-.5))
            self.assertEqual(spy_lo.call_count, 1)
            self.assertIs(spy_lo.call_args[0][1], False)  # lower-is-match exact bool
        lo = result_lo['metrics']['empiricalCurves']['overall']
        # negated genuine (-.9,-.8) rank below impostor (-.3,-.2); ascending sweep
        # still gives 4/4 favourable pairs -> AUC 1.0 (hand calculated).
        self.assertEqual(lo['scoreDirection'], 'lower-is-positive')
        self.assertAlmostEqual(lo['aucOnResponse'], 1.0)
        self.assertEqual([p['threshold'] for p in lo['points']], [None, -.9, -.8, -.3, -.2])

    def test_none_nonresponse_preserved_and_records_have_only_positive_and_score(self):
        scores = self.make_scores(self.verification_rows([.9, None, .3, None]))
        with mock.patch.object(self.module, 'empirical_curve', wraps=self.real_curve) as spy:
            result = self.summarize(scores)
            self.assertEqual(spy.call_count, 1)
            records = spy.call_args[0][0]
            self.assertIs(spy.call_args[0][1], True)
        # responders: 1 positive (.9), 1 negative (.3) -> AUC 1/1 = 1.0 (hand)
        overall = result['metrics']['empiricalCurves']['overall']
        self.assertEqual(overall['trialCount'], 4)
        self.assertAlmostEqual(overall['aucOnResponse'], 1.0)
        self.assertEqual([p['threshold'] for p in overall['points']], [None, .9, .3])
        for p in overall['points']:
            self.assertEqual(p['counts']['positive']['trials'], 2)
            self.assertEqual(p['counts']['positive']['nonResponses'], 1)
            self.assertEqual(p['counts']['negative']['trials'], 2)
            self.assertEqual(p['counts']['negative']['nonResponses'], 1)
        # None is kept as a None-scored record with its exact positive flag;
        # records carry no IDs, subject names, paths or media fields.
        self.assertEqual(records, [
            {'positive': True, 'score': .9},
            {'positive': True, 'score': None},
            {'positive': False, 'score': .3},
            {'positive': False, 'score': None},
        ])
        for rec in records:
            self.assertEqual(set(rec), {'positive', 'score'})
            self.assertIs(type(rec['positive']), bool)

    def test_one_class_and_all_nonresponse_yield_null_auc(self):
        # one class (only genuine pairs) -> no negative responders -> null AUC
        only_pos = [dict(enrollmentId='calibration-0', probeId='calibration-2', score=.9),
                    dict(enrollmentId='calibration-1', probeId='calibration-3', score=.8)]
        overall = self.summarize(self.make_scores(only_pos))['metrics']['empiricalCurves']['overall']
        self.assertIsNone(overall['aucOnResponse'])
        self.assertEqual(overall['trialCount'], 2)
        self.assertGreaterEqual(len(overall['points']), 2)
        # all nonresponse -> only the reject-all endpoint, null AUC
        overall = self.summarize(self.make_scores(self.verification_rows([None] * 4))) \
            ['metrics']['empiricalCurves']['overall']
        self.assertIsNone(overall['aucOnResponse'])
        self.assertEqual(overall['trialCount'], 4)
        self.assertEqual(len(overall['points']), 1)
        self.assertEqual(overall['points'][0]['rule'], 'reject-all')

    def test_curve_is_independent_of_caller_threshold_and_fail_closed(self):
        rows = self.verification_rows([.9, .8, .3, .2])
        r1 = self.summarize(self.make_scores(rows, threshold=.5))
        r2 = self.summarize(self.make_scores(rows, threshold=.95))
        # descriptive observations only: the curve does not depend on any chosen policy
        self.assertEqual(r1['metrics']['empiricalCurves'], r2['metrics']['empiricalCurves'])
        # existing threshold decisions still change with the caller threshold
        self.assertAlmostEqual(r1['metrics']['frrOnResponse'], 0.0)
        self.assertAlmostEqual(r2['metrics']['frrOnResponse'], 1.0)
        overall = r1['metrics']['empiricalCurves']['overall']
        self.assertEqual(overall['schemaVersion'], 1)
        self.assertEqual(overall['kind'], 'identity-empirical-binary-curve')
        self.assertEqual(overall['validationStatus'], 'unverified')
        self.assertIs(overall['productionPolicyGenerated'], False)
        self.assertIs(type(overall['productionPolicyGenerated']), bool)

    # ---- passive-pad: overall vs per-PAI subsets, nonresponses ----

    def test_pad_curves_overall_and_per_pai_correct_subsets(self):
        self.fixture.pad()
        self.manifest['samples'][3]['pai'] = 'replay'  # two PAIs: print, replay
        rows = [dict(sampleId=f'calibration-{i}', score=v) for i, v in enumerate([.9, .8, .6, .2])]
        scores = self.make_scores(rows)
        before = copy.deepcopy((self.manifest, scores))
        result = self.summarize(scores)
        curves = result['metrics']['empiricalCurves']
        self.assertEqual(set(curves), {'overall', 'byPAI'})
        self.assertEqual(list(curves['byPAI']), ['print', 'replay'])  # deterministic sort
        overall = curves['overall']
        # (T,.9),(F,.8),(T,.6),(F,.2); 2 pos, 2 neg -> 3/4 = .75 (hand)
        self.assertEqual(overall['trialCount'], 4)
        self.assertEqual(overall['scoreDirection'], 'higher-is-positive')
        self.assertAlmostEqual(overall['aucOnResponse'], .75)
        # print: all bona-fide (.9,.6) + print attack (.8); 2 pos,1 neg -> 1/2 = .5 (hand)
        self.assertEqual(curves['byPAI']['print']['trialCount'], 3)
        self.assertAlmostEqual(curves['byPAI']['print']['aucOnResponse'], .5)
        # replay: all bona-fide (.9,.6) + replay attack (.2); 2 pos,1 neg -> 2/2 = 1.0 (hand)
        self.assertEqual(curves['byPAI']['replay']['trialCount'], 3)
        self.assertAlmostEqual(curves['byPAI']['replay']['aucOnResponse'], 1.0)
        # existing byPAI threshold metrics unchanged
        self.assertEqual(result['metrics']['byPAI']['print']['apcerOnResponse'], 1)
        self.assertEqual(result['metrics']['byPAI']['replay']['apcerOnResponse'], 0)
        self.assertEqual(result['metrics']['apcerWorstPAIOnResponse'], 1)
        self.assertEqual((self.manifest, scores), before)

    def test_pad_bona_fide_nonresponse_preserved_in_every_group(self):
        self.fixture.pad()
        self.manifest['samples'][3]['pai'] = 'replay'
        rows = [dict(sampleId='calibration-0', score=.9),
                dict(sampleId='calibration-1', score=.8),
                dict(sampleId='calibration-2', score=None),  # bona-fide nonresponse
                dict(sampleId='calibration-3', score=.2)]
        with mock.patch.object(self.module, 'empirical_curve', wraps=self.real_curve) as spy:
            result = self.summarize(self.make_scores(rows))
            self.assertEqual(spy.call_count, 3)  # overall + print + replay
            for call in spy.call_args_list:
                self.assertIn({'positive': True, 'score': None}, call[0][0])
        curves = result['metrics']['empiricalCurves']
        overall = curves['overall']
        # responders: pos 1 (.9), neg 2 (.8,.2) -> 2/2 = 1.0 (hand)
        self.assertEqual(overall['trialCount'], 4)
        self.assertAlmostEqual(overall['aucOnResponse'], 1.0)
        for p in overall['points']:
            self.assertEqual(p['counts']['positive']['nonResponses'], 1)
        for pai in ('print', 'replay'):
            group = curves['byPAI'][pai]
            # each PAI group = 2 bona-fide (one is None) + 1 attack = 3 trials
            self.assertEqual(group['trialCount'], 3)
            for p in group['points']:
                self.assertEqual(p['counts']['positive']['nonResponses'], 1)
            # responders: pos 1 (.9), neg 1 -> 1/1 = 1.0 (hand)
            self.assertAlmostEqual(group['aucOnResponse'], 1.0)

    def test_pad_all_nonresponse_yields_null_auc(self):
        self.fixture.pad()
        self.manifest['samples'][3]['pai'] = 'replay'
        rows = [dict(sampleId=f'calibration-{i}', score=None) for i in range(4)]
        curves = self.summarize(self.make_scores(rows))['metrics']['empiricalCurves']
        self.assertIsNone(curves['overall']['aucOnResponse'])
        for pai in ('print', 'replay'):
            self.assertIsNone(curves['byPAI'][pai]['aucOnResponse'])
            self.assertEqual(len(curves['byPAI'][pai]['points']), 1)

    # ---- identification unchanged, provenance, privacy, rejection, CLI ----

    def test_identification_metrics_unchanged_and_no_curve_key(self):
        scores = self.identification_scores()
        before = copy.deepcopy((self.manifest, scores))
        result = self.summarize(scores)
        m = result['metrics']
        self.assertNotIn('empiricalCurves', m)
        self.assertEqual(set(m), {'known', 'unknown', 'tiedSearches'})
        self.assertEqual(result['protocol']['galleryTemplates'], 2)
        self.assertEqual(result['protocol']['galleryIdentities'], 2)
        self.assertEqual(m['known']['tpirOnResponse'], .5)
        self.assertEqual(m['known']['wrong'], 1)
        self.assertEqual(m['unknown']['trials'], 1)
        self.assertEqual(m['unknown']['fpirOnResponse'], 1)
        self.assertEqual(m['tiedSearches'], 0)
        self.assertEqual((self.manifest, scores), before)

    def test_identification_ties_unchanged(self):
        scores = self.identification_scores()
        for row in scores['rows']:
            if row['probeId'] in ('calibration-2', 'calibration-unknown'):
                row['score'] = .8
        result = self.summarize(scores)
        self.assertEqual(result['metrics']['tiedSearches'], 2)
        self.assertEqual(result['metrics']['known']['tpirOnResponse'], 0)
        self.assertEqual(result['metrics']['unknown']['fpirOnResponse'], 0)
        self.assertNotIn('empiricalCurves', result['metrics'])

    def test_provenance_hashes_status_and_residual_limitations_unchanged(self):
        rows = self.verification_rows([.9, .8, .3, .2])
        scores = self.make_scores(rows)
        canonical = json.dumps(scores, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()
        expected_scores_sha = hashlib.sha256(canonical).hexdigest()
        expected_manifest_sha = validate_manifest(self.manifest, self.root)['manifestCanonicalSHA256']
        result = self.summarize(scores)
        self.assertEqual(result['scoresCanonicalSHA256'], expected_scores_sha)
        self.assertEqual(result['manifestCanonicalSHA256'], expected_manifest_sha)
        self.assertEqual(result['schemaVersion'], 1)
        self.assertEqual(result['kind'], 'identity-calibration-score-summary')
        self.assertEqual(result['validationStatus'], 'pass-bounded')
        self.assertEqual(result['calibrationStatus'], 'unverified')
        self.assertEqual(result['thresholdOrigin'], 'caller-supplied-unverified')
        self.assertEqual(result['scoreDirection'], 'higher-is-match')
        self.assertEqual(result['dataClass'], 'synthetic')
        self.assertEqual(result['rateDenominators'],
                         'error-rates-on-responses; nonresponse-rates-on-total-trials')
        # only new metrics key is empiricalCurves; existing metrics preserved
        self.assertEqual(set(result['metrics']),
                         {'farOnResponse', 'frrOnResponse', 'counts', 'empiricalCurves'})
        self.assertEqual(result['metrics']['farOnResponse'], 0)
        self.assertEqual(result['metrics']['frrOnResponse'], 0)
        u = result['unverified']
        self.assertEqual(len(u), 7)
        self.assertNotIn('ROC/DET and confidence inference', u)
        for kept in ('legal authority and actual score provenance', 'labels and undisclosed leakage',
                     'independent trials and population representativeness',
                     'production thresholds and policies', 'PAD or recognition acceptance',
                     'release readiness'):
            self.assertIn(kept, u)
        residual = [entry for entry in u if 'empirical' in entry and 'descriptive' in entry]
        self.assertEqual(len(residual), 1)
        self.assertIn('confidence inference', residual[0])
        self.assertIn('response-only', residual[0])
        joined = ' '.join(u).lower()
        self.assertNotIn('curves absent', joined)
        self.assertNotIn('production ready', joined)

    def test_no_row_ids_or_paths_in_output(self):
        result = self.summarize(self.make_scores(self.verification_rows([.9, .8, .3, .2])))
        output = json.dumps(result)
        for sample in self.manifest['samples']:
            self.assertNotIn(sample['id'], output)
            self.assertNotIn(sample['subjectId'], output)
            self.assertNotIn(sample['file']['path'], output)
        self.assertNotIn('"rows"', output)
        curve_text = json.dumps(result['metrics']['empiricalCurves'])
        self.assertNotIn('calibration-0', curve_text)
        self.assertNotIn('subject', curve_text)

    def test_invalid_binding_direction_protocol_reject_before_curve_call(self):
        base_rows = self.verification_rows([.9, .8, .3, .2])
        verification_manifest = copy.deepcopy(self.manifest)

        def build(manifest, rows):
            checked = validate_manifest(manifest, self.root)
            return dict(schemaVersion=1, kind='identity-calibration-scores',
                        manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
                        binding=copy.deepcopy(manifest['binding']), task=manifest['task'],
                        split='calibration', threshold=.5, rows=rows)

        def expect_reject(manifest, scores):
            with mock.patch.object(self.module, 'empirical_curve', wraps=self.real_curve) as spy:
                with self.assertRaises(ValueError):
                    self.module.summarize(manifest, scores, self.root)
                spy.assert_not_called()

        cases = []
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['manifestCanonicalSHA256'] = '0' * 64
        cases.append(('hash binding', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['task'] = 'identification'
        cases.append(('task binding', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['split'] = 'test'
        cases.append(('split/purpose', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['binding']['modelRevision'] = 'other'
        cases.append(('model binding', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['binding']['scoreDirection'] = 'lower-is-match'  # disagrees with manifest binding
        cases.append(('direction binding', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['extra'] = 1
        cases.append(('extra scores field', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['rows'][0]['extra'] = 1
        cases.append(('extra row field', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['rows'].append(copy.deepcopy(s['rows'][0]))  # duplicate verification pair
        cases.append(('duplicate pair', verification_manifest, s))
        s = build(verification_manifest, copy.deepcopy(base_rows))
        s['rows'][0]['score'] = float('nan')
        cases.append(('nonfinite score', verification_manifest, s))
        for label, manifest, scores in cases:
            with self.subTest(label=label):
                expect_reject(manifest, scores)
        # manifest direction invalid for the task (PAD-only direction on verification)
        s = build(verification_manifest, copy.deepcopy(base_rows))
        bad_manifest = copy.deepcopy(verification_manifest)
        bad_manifest['binding']['scoreDirection'] = 'higher-is-bona-fide'
        expect_reject(bad_manifest, s)
        # PAD incomplete coverage: _pad rejects after its loop, before curve calls
        self.fixture.pad()
        pad_manifest = self.manifest
        pad_rows = [dict(sampleId=f'calibration-{i}', score=v) for i, v in enumerate([.9, .7, .4, .2])]
        s = build(pad_manifest, pad_rows)
        s['rows'] = pad_rows[:-1]
        expect_reject(pad_manifest, s)

    def test_invalid_score_values_and_schema_aliases_still_rejected(self):
        base = [.9, .8, .3, .2]
        for bad in (True, float('nan'), float('inf'), -float('inf'), 10 ** 400, '0.5'):
            with self.subTest(score=repr(bad)):
                scores = self.make_scores(self.verification_rows(base))
                scores['rows'][0]['score'] = bad
                with self.assertRaises(ValueError):
                    self.summarize(scores)
        for key, bad in [('schemaVersion', True), ('schemaVersion', 1.0),
                         ('rows', []), ('extra', 1)]:
            with self.subTest(key=key, value=repr(bad)):
                scores = self.make_scores(self.verification_rows(base))
                scores[key] = bad
                with self.assertRaises(ValueError):
                    self.summarize(scores)

    def test_original_cli_contract_still_emits_curves_and_rejects_bad_input(self):
        manifest = self.root / 'manifest.json'
        scores_path = self.root / 'scores.json'
        manifest.write_text(json.dumps(self.manifest))
        scores = self.make_scores(self.verification_rows([.9, .8, .3, .2]))
        scores_path.write_text(json.dumps(scores))
        command = [sys.executable, '-m', 'tools.identity_calibration_scores',
                   str(manifest), str(scores_path)]
        cwd = Path(__file__).resolve().parents[2]
        ok = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
        self.assertEqual(ok.returncode, 0, ok.stderr)
        report = json.loads(ok.stdout)
        self.assertEqual(report, self.summarize(scores))
        self.assertIn('empiricalCurves', report['metrics'])
        # duplicate JSON key -> exit 2, no stdout
        scores_path.write_text('{"schemaVersion":1,"schemaVersion":1}')
        dup = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
        self.assertNotEqual(dup.returncode, 0)
        self.assertEqual(dup.stdout, '')
        # symlinked scores document -> rejected without following the link
        scores_path.unlink()
        scores_path.symlink_to(manifest)
        link = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
        self.assertNotEqual(link.returncode, 0)


if __name__ == '__main__':
    unittest.main()
