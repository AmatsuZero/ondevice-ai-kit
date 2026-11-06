"""Hand-checked synthetic score protocols, not measured biometric performance."""
import copy
import importlib
import json
from pathlib import Path
import subprocess
import sys
import unittest

from tools.identity_calibration_manifest import validate_manifest
from tools.tests import test_identity_calibration_manifest as manifest_fixture


class CalibrationStatisticsTests(unittest.TestCase):
    def statistics(self):
        return importlib.import_module('tools.identity_calibration_statistics')

    def test_binary_counts_hand_checked_and_preserves_input(self):
        rows = [dict(positive=p, accepted=a) for p, a in
                [(True, True), (True, False), (True, None),
                 (False, True), (False, False), (False, False), (False, None)]]
        before = copy.deepcopy(rows)
        self.assertEqual(self.statistics().binary_counts(rows), {
            'positive': dict(trials=3, responses=2, nonResponses=1, errors=1,
                             errorRateOnResponse=.5, nonResponseRate=1/3),
            'negative': dict(trials=4, responses=3, nonResponses=1, errors=1,
                             errorRateOnResponse=1/3, nonResponseRate=.25)})
        self.assertEqual(rows, before)

    def test_empty_and_all_nonresponse_denominators_are_undefined(self):
        result = self.statistics().binary_counts([])
        for group in result.values():
            self.assertEqual(group['trials'], 0)
            self.assertIsNone(group['errorRateOnResponse'])
            self.assertIsNone(group['nonResponseRate'])
        result = self.statistics().binary_counts([dict(positive=True, accepted=None)])
        self.assertIsNone(result['positive']['errorRateOnResponse'])
        self.assertEqual(result['positive']['nonResponseRate'], 1)
        result = self.statistics().search_counts([])
        self.assertIsNone(result['known']['tpirOnResponse'])
        self.assertIsNone(result['unknown']['fpirOnResponse'])

    def test_search_counts_hand_checked_per_search(self):
        rows = [dict(known=k, outcome=o, tied=t) for k, o, t in [
            (True, 'correct', False), (True, 'wrong', False), (True, 'rejected', True),
            (True, 'nonresponse', False), (False, 'accepted', False),
            (False, 'rejected', False), (False, 'rejected', True), (False, 'nonresponse', False)]]
        result = self.statistics().search_counts(rows)
        self.assertEqual(result['known'], dict(trials=4, responses=3, nonResponses=1,
            correct=1, wrong=1, rejected=1, tpirOnResponse=1/3, fnirOnResponse=2/3, nonResponseRate=.25))
        self.assertEqual(result['unknown'], dict(trials=4, responses=3, nonResponses=1,
            accepted=1, rejected=2, fpirOnResponse=1/3, nonResponseRate=.25))
        self.assertEqual(result['tiedSearches'], 2)

    def test_statistics_reject_type_aliases_and_contradictory_outcomes(self):
        for row in [dict(positive=1, accepted=True), dict(positive=True, accepted=1),
                    dict(positive=True, accepted=True, extra=1), dict(positive=True)]:
            with self.subTest(row=row):
                with self.assertRaises(ValueError):
                    self.statistics().binary_counts([row])
        for row in [dict(known=1, outcome='correct', tied=False),
                    dict(known=True, outcome='accepted', tied=False),
                    dict(known=False, outcome='correct', tied=False),
                    dict(known=False, outcome='accepted', tied=True),
                    dict(known=True, outcome='nonresponse', tied=True),
                    dict(known=True, outcome='correct', tied=0)]:
            with self.subTest(row=row):
                with self.assertRaises(ValueError):
                    self.statistics().search_counts([row])
        for method in (self.statistics().binary_counts, self.statistics().search_counts):
            for invalid in ({}, None, (), [None]):
                with self.assertRaises(ValueError):
                    method(invalid)

    def test_search_statistics_reject_extra_fields(self):
        with self.assertRaises(ValueError):
            self.statistics().search_counts([dict(known=True, outcome='correct', tied=False, extra=1)])


class CalibrationScoreTests(unittest.TestCase):
    def setUp(self):
        self.fixture = manifest_fixture.CalibrationManifestTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        self.manifest = self.fixture.document

    def scores(self, rows=None):
        checked = validate_manifest(self.manifest, self.root)
        return dict(schemaVersion=1, kind='identity-calibration-scores',
                    manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
                    binding=copy.deepcopy(self.manifest['binding']), task=self.manifest['task'],
                    split='calibration', threshold=.5, rows=rows if rows is not None else [
                        dict(enrollmentId='calibration-0', probeId='calibration-2', score=.8),
                        dict(enrollmentId='calibration-1', probeId='calibration-3', score=.2),
                        dict(enrollmentId='calibration-0', probeId='calibration-3', score=.7),
                        dict(enrollmentId='calibration-1', probeId='calibration-2', score=.1)])

    def summarize(self, scores=None):
        module = importlib.import_module('tools.identity_calibration_scores')
        return module.summarize(self.manifest, self.scores() if scores is None else scores, self.root)

    def identification(self):
        self.manifest['task'] = 'identification'
        unknown = dict(id='calibration-unknown', subjectId='calibration-unknown-subject',
                       sessionId='probe', mediaGroupId='unknown-media', split='calibration',
                       role='probe', label='bona-fide', pai=None,
                       file=self.fixture.file('media/unknown.bin', b'synthetic unknown'))
        self.manifest['samples'].append(unknown)
        matrix = [('calibration-2', [.8, .4]), ('calibration-3', [.9, .3]),
                  ('calibration-unknown', [.7, .2])]
        return self.scores([dict(enrollmentId=f'calibration-{i}', probeId=p, score=v)
                            for p, values in matrix for i, v in enumerate(values)])

    def test_verification_hand_checked_aggregate_only_and_no_input_mutation(self):
        scores = self.scores()
        before = copy.deepcopy((self.manifest, scores))
        result = self.summarize(scores)
        self.assertEqual(result['metrics']['farOnResponse'], .5)
        self.assertEqual(result['metrics']['frrOnResponse'], .5)
        self.assertEqual(result['metrics']['counts']['positive']['trials'], 2)
        self.assertEqual(result['validationStatus'], 'pass-bounded')
        self.assertEqual(result['calibrationStatus'], 'unverified')
        self.assertEqual(result['thresholdOrigin'], 'caller-supplied-unverified')
        self.assertEqual(result['dataClass'], 'synthetic')
        self.assertEqual((self.manifest, scores), before)
        output = json.dumps(result)
        for row in self.manifest['samples']:
            self.assertNotIn(row['id'], output)
            self.assertNotIn(row['subjectId'], output)
            self.assertNotIn(row['file']['path'], output)
        self.assertNotIn('"rows"', output)

    def test_lower_direction_and_threshold_equality(self):
        self.manifest['binding']['scoreDirection'] = 'lower-is-match'
        scores = self.scores()
        scores['threshold'] = -.5
        for row in scores['rows']:
            row['score'] *= -1
        self.assertEqual(self.summarize(scores)['metrics']['farOnResponse'], .5)
        scores['threshold'] = .5
        for row in scores['rows']:
            row['score'] = .5
        result = self.summarize(scores)
        self.assertEqual(result['metrics']['farOnResponse'], 1)
        self.assertEqual(result['metrics']['frrOnResponse'], 0)

    def test_nonresponses_not_silently_counted_as_correct(self):
        scores = self.scores()
        for row in scores['rows']:
            row['score'] = None
        result = self.summarize(scores)
        self.assertIsNone(result['metrics']['farOnResponse'])
        self.assertIsNone(result['metrics']['frrOnResponse'])
        self.assertEqual(result['metrics']['counts']['positive']['nonResponseRate'], 1)

    def test_pad_per_pai_and_undefined_worst_group(self):
        self.fixture.pad()
        self.manifest['samples'][3]['pai'] = 'replay'
        scores = self.scores([dict(sampleId=f'calibration-{i}', score=v)
                              for i, v in enumerate([.9, .7, .4, .2])])
        result = self.summarize(scores)
        self.assertEqual(result['metrics']['bpcerOnResponse'], .5)
        self.assertEqual(result['metrics']['byPAI']['print']['apcerOnResponse'], 1)
        self.assertEqual(result['metrics']['byPAI']['replay']['apcerOnResponse'], 0)
        self.assertEqual(result['metrics']['apcerWorstPAIOnResponse'], 1)
        scores['rows'][3]['score'] = None
        result = self.summarize(scores)
        self.assertIsNone(result['metrics']['byPAI']['replay']['apcerOnResponse'])
        self.assertIsNone(result['metrics']['apcerWorstPAIOnResponse'])
        self.assertEqual(result['metrics']['byPAI']['replay']['counts']['nonResponseRate'], 1)

    def test_pad_lower_direction_includes_threshold_equality(self):
        self.fixture.pad()
        self.manifest['binding']['scoreDirection'] = 'lower-is-bona-fide'
        scores = self.scores([dict(sampleId=f'calibration-{i}', score=.5) for i in range(4)])
        result = self.summarize(scores)
        self.assertEqual(result['metrics']['bpcerOnResponse'], 0)
        self.assertEqual(result['metrics']['apcerWorstPAIOnResponse'], 1)

    def test_identification_fpir_uses_unknown_searches_not_gallery_pairs(self):
        scores = self.identification()
        result = self.summarize(scores)
        self.assertEqual(result['protocol']['galleryTemplates'], 2)
        self.assertEqual(result['protocol']['galleryIdentities'], 2)
        self.assertEqual(result['metrics']['known']['tpirOnResponse'], .5)
        self.assertEqual(result['metrics']['known']['wrong'], 1)
        self.assertEqual(result['metrics']['unknown']['trials'], 1)
        self.assertEqual(result['metrics']['unknown']['fpirOnResponse'], 1)

    def test_identification_distinct_identity_ties_reject(self):
        scores = self.identification()
        for row in scores['rows']:
            if row['probeId'] in ('calibration-2', 'calibration-unknown'):
                row['score'] = .8
        result = self.summarize(scores)
        self.assertEqual(result['metrics']['tiedSearches'], 2)
        self.assertEqual(result['metrics']['known']['tpirOnResponse'], 0)
        self.assertEqual(result['metrics']['unknown']['fpirOnResponse'], 0)

    def test_identification_same_identity_template_ties_are_not_ambiguous(self):
        scores = self.identification()
        source = copy.deepcopy(self.manifest['samples'][0])
        source.update(id='second-template', mediaGroupId='second-template', sessionId='enroll-2',
                      file=self.fixture.file('media/second.bin', b'second synthetic template'))
        self.manifest['samples'].append(source)
        for row in list(scores['rows']):
            if row['enrollmentId'] == 'calibration-0':
                scores['rows'].append(dict(row, enrollmentId='second-template'))
        scores['manifestCanonicalSHA256'] = validate_manifest(self.manifest, self.root)['manifestCanonicalSHA256']
        result = self.summarize(scores)
        self.assertEqual(result['protocol']['galleryTemplates'], 3)
        self.assertEqual(result['protocol']['galleryIdentities'], 2)
        self.assertEqual(result['metrics']['tiedSearches'], 0)
        self.assertEqual(result['metrics']['known']['tpirOnResponse'], .5)

    def test_identification_incomplete_response_is_not_a_completed_search(self):
        scores = self.identification()
        scores['rows'][4]['score'] = None
        result = self.summarize(scores)
        self.assertIsNone(result['metrics']['unknown']['fpirOnResponse'])
        self.assertEqual(result['metrics']['unknown']['nonResponseRate'], 1)

    def test_identification_lower_direction_and_absent_unknown_denominator(self):
        scores = self.identification()
        self.manifest['samples'] = self.manifest['samples'][:8]
        self.manifest['binding']['scoreDirection'] = 'lower-is-match'
        rows = [dict(r, score=-r['score']) for r in scores['rows'] if r['probeId'] != 'calibration-unknown']
        scores = self.scores(rows)
        scores['threshold'] = -.5
        result = self.summarize(scores)
        self.assertEqual(result['metrics']['known']['tpirOnResponse'], .5)
        self.assertEqual(result['metrics']['unknown']['trials'], 0)
        self.assertIsNone(result['metrics']['unknown']['fpirOnResponse'])

    def test_missing_or_duplicate_pad_and_search_cells_rejected(self):
        for task in ('identification', 'passive-pad'):
            if task == 'identification':
                scores = self.identification()
            else:
                self.manifest['samples'] = self.manifest['samples'][:8]
                self.fixture.pad()
                scores = self.scores([dict(sampleId=f'calibration-{i}', score=.5) for i in range(4)])
            for rows in (scores['rows'][:-1], scores['rows'] + [copy.deepcopy(scores['rows'][0])]):
                with self.subTest(task=task, count=len(rows)):
                    candidate = copy.deepcopy(scores)
                    candidate['rows'] = rows
                    with self.assertRaises(ValueError):
                        self.summarize(candidate)

    def test_rejects_hash_binding_task_purpose_and_split_mismatch(self):
        mutations = [lambda s: s.update(manifestCanonicalSHA256='0'*64),
                     lambda s: s['binding'].update(modelRevision='other'),
                     lambda s: s.update(task='identification'), lambda s: s.update(split='test')]
        for mutate in mutations:
            candidate = self.scores()
            mutate(candidate)
            with self.assertRaises(ValueError):
                self.summarize(candidate)
        self.manifest['purpose'] = 'local-evaluation'
        with self.assertRaises(ValueError):
            self.summarize(self.scores())

    def test_test_split_requires_and_accepts_evaluation_purpose(self):
        self.manifest['purpose'] = 'local-evaluation'
        scores = self.scores()
        scores['split'] = 'test'
        for row in scores['rows']:
            row['enrollmentId'] = row['enrollmentId'].replace('calibration-', 'test-')
            row['probeId'] = row['probeId'].replace('calibration-', 'test-')
        self.assertEqual(self.summarize(scores)['metrics']['farOnResponse'], .5)

    def test_rejects_wrong_roles_cross_split_unknown_and_duplicate_pairs(self):
        for key, value in [('enrollmentId', 'calibration-2'), ('probeId', 'calibration-0'),
                           ('probeId', 'test-2'), ('enrollmentId', 'unknown'), ('label', 'genuine')]:
            with self.subTest(key=key, value=value):
                scores = self.scores()
                scores['rows'][0][key] = value
                with self.assertRaises(ValueError):
                    self.summarize(scores)
        scores = self.scores()
        scores['rows'].append(copy.deepcopy(scores['rows'][0]))
        with self.assertRaises(ValueError):
            self.summarize(scores)

    def test_rejects_numeric_aliases_nonfinite_and_huge_integers(self):
        for key in ('threshold', 'score'):
            for value in (True, float('nan'), float('inf'), -float('inf'), 10**400, '0.5'):
                with self.subTest(key=key, valueType=type(value).__name__):
                    scores = self.scores()
                    if key == 'threshold':
                        scores[key] = value
                    else:
                        scores['rows'][0][key] = value
                    with self.assertRaises(ValueError):
                        self.summarize(scores)
        for key, value in [('schemaVersion', True), ('schemaVersion', 1.0), ('extra', 1), ('rows', [])]:
            scores = self.scores()
            scores[key] = value
            with self.assertRaises(ValueError):
                self.summarize(scores)

    def test_cli_success_duplicate_json_and_special_inputs(self):
        manifest = self.root / 'manifest.json'
        scores = self.root / 'scores.json'
        manifest.write_text(json.dumps(self.manifest))
        scores.write_text(json.dumps(self.scores()))
        command = [sys.executable, '-m', 'tools.identity_calibration_scores', str(manifest), str(scores)]
        cwd = Path(__file__).resolve().parents[2]
        result = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), self.summarize())
        for text in ('{"schemaVersion":1,"schemaVersion":1}', json.dumps(self.scores()).replace('0.5', 'NaN')):
            scores.write_text(text)
            result = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, '')
            self.assertNotIn(str(self.root), result.stderr)
        scores.unlink()
        scores.symlink_to(manifest)
        result = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(str(self.root), result.stderr)


if __name__ == '__main__':
    unittest.main()
