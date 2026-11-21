"""Independent identity evaluation develop/evaluate tests (synthetic, verification-only).

These tests exercise the real public API of ``tools.identity_independent_evaluation``
(``develop`` / ``evaluate``) plus its two-phase CLI. They reuse the existing manifest
fixture ``CalibrationManifestTests.setUp`` (synthetic files, two disjoint subjects per
split, enrollment ids 0/1 and probe ids 2/3, full 2x2 enrollment x probe matrix).

No real photos, identifiers or scores are involved. All scores are hand-computed
synthetic separations: genuine .8/.9 and impostor .1/.2 (and the lower-direction
negation), used only to drive a deterministic threshold selection.

Integrity pin contract (FINAL core)
-----------------------------------
``evaluate`` requires a *keyword-only* ``expected_policy_digest`` — the original
64-lowercase-hex ``policyArtifactDigest`` captured by the evaluator at successful
``develop`` and retained SEPARATELY (out of band, never recomputed from the received
policy). The received policy is bound to that pin AND to a fresh recomputation of its
own embedded digest. Every valid and negative ``evaluate`` call in this suite supplies
that original pin; a tampered policy is never used to derive a replacement pin.

The module is verification-only; it never performs extraction, curves, FAR/FRR math,
inference, consent handling or production threshold activation. Tests assert the
desired strict invariants: no input mutation, calibrationStatus stays 'unverified',
productionReady/productionPolicyGenerated stay False, aggregate privacy (no private
identifiers in reports), frozen-threshold evaluation with no reselection, and the
documented negative boundaries.
"""

import copy
import hashlib
import importlib
import json
import os
import tempfile
from pathlib import Path
import subprocess
import sys
import unittest

from tools.identity_calibration_manifest import validate_manifest
from tools.tests import test_identity_calibration_manifest as manifest_fixture


# The repository root is two levels up from tools/tests (matches the existing
# fixture's subprocess convention so ``python -m tools...`` resolves the package.
REPO_ROOT = Path(__file__).resolve().parents[2]


class IndependentEvaluationTests(unittest.TestCase):
    def setUp(self):
        self.fixture = manifest_fixture.CalibrationManifestTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        # Calibration-purpose manifest (develop uses this).
        self.manifest = self.fixture.document
        # Same dataset declaration, evaluation purpose (evaluate uses this). Only the
        # top-level purpose differs; the dataset contract digest excludes purpose.
        self.eval_manifest = copy.deepcopy(self.manifest)
        self.eval_manifest['purpose'] = 'local-evaluation'
        self.spec = dict(targetFar=0.5, maxFrr=0.5, minSubjects=2,
                         minGenuineResponses=2, minImpostorResponses=2, maxNonresponses=0)

    def module(self):
        return importlib.import_module('tools.identity_independent_evaluation')

    # ------------------------------------------------------------------
    # synthetic score builders (hand-computed separations)
    # ------------------------------------------------------------------

    def calibration_scores(self, higher=True):
        if higher:
            genuine, impostor = (.8, .9), (.1, .2)
        else:
            genuine, impostor = (-.8, -.9), (-.1, -.2)
        checked = validate_manifest(self.manifest, self.root)
        return dict(schemaVersion=1, kind='identity-calibration-scores',
                    manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
                    binding=copy.deepcopy(self.manifest['binding']), task='verification',
                    split='calibration', threshold=0.5, rows=[
                        dict(enrollmentId='calibration-0', probeId='calibration-2', score=genuine[0]),
                        dict(enrollmentId='calibration-1', probeId='calibration-3', score=genuine[1]),
                        dict(enrollmentId='calibration-0', probeId='calibration-3', score=impostor[0]),
                        dict(enrollmentId='calibration-1', probeId='calibration-2', score=impostor[1])])

    def evaluation_scores(self, higher=True, impostor=None, threshold=None):
        if higher:
            genuine = (.85, .95)
            impostor = impostor if impostor is not None else (.15, .25)
            thr = threshold if threshold is not None else 0.8
        else:
            genuine = (-.85, -.95)
            impostor = impostor if impostor is not None else (-.15, -.25)
            thr = threshold if threshold is not None else -0.8
        checked = validate_manifest(self.eval_manifest, self.root)
        return dict(schemaVersion=1, kind='identity-calibration-scores',
                    manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
                    binding=copy.deepcopy(self.eval_manifest['binding']), task='verification',
                    split='test', threshold=thr, rows=[
                        dict(enrollmentId='test-0', probeId='test-2', score=genuine[0]),
                        dict(enrollmentId='test-1', probeId='test-3', score=genuine[1]),
                        dict(enrollmentId='test-0', probeId='test-3', score=impostor[0]),
                        dict(enrollmentId='test-1', probeId='test-2', score=impostor[1])])

    def develop(self, manifest=None, scores=None, spec=None):
        return self.module().develop(
            self.manifest if manifest is None else manifest,
            self.calibration_scores() if scores is None else scores,
            self.spec if spec is None else spec, self.root)

    # ------------------------------------------------------------------
    # positive: develop selects a frozen candidate policy
    # ------------------------------------------------------------------

    def _develop_selects_frozen_policy_and_preserves_inputs(self):
        manifest_before = copy.deepcopy(self.manifest)
        scores = self.calibration_scores()
        scores_before = copy.deepcopy(scores)
        spec_before = copy.deepcopy(self.spec)

        policy = self.develop(scores=scores)

        # inputs are never mutated by the orchestrator
        self.assertEqual(self.manifest, manifest_before)
        self.assertEqual(scores, scores_before)
        self.assertEqual(self.spec, spec_before)

        # evidence-only candidate, never a production policy
        self.assertEqual(policy['kind'], 'identity-independent-candidate-policy')
        self.assertEqual(policy['calibrationStatus'], 'unverified')
        self.assertIs(policy['productionReady'], False)
        self.assertIs(policy['productionPolicyGenerated'], False)
        self.assertEqual(policy['decisionRule'], 'inclusive-threshold')
        self.assertEqual(policy['tieRule'], 'stricter-threshold-among-equal-frr')
        self.assertEqual(policy['selectedThreshold'], 0.8)

        # digest is self-consistent
        self.assertEqual(policy['policyArtifactDigest'], self.module()._policy_digest(policy))

        # aggregate privacy: no private identifiers, paths or raw rows
        rendered = json.dumps(policy)
        for token in ('calibration-0', 'calibration-2', 'calibration-subject-0',
                      'calibration-subject-1', '"rows"', str(self.root)):
            self.assertNotIn(token, rendered)

        # coverage aggregate present and hand-checked
        cov = policy['coverage']['calibration']
        self.assertEqual(cov['subjectCount'], 2)
        self.assertEqual(cov['pairCount'], 4)
        self.assertEqual(cov['genuineResponses'], 2)
        self.assertEqual(cov['impostorResponses'], 2)
        self.assertEqual(cov['nonresponses'], 0)
        return policy

    def test_develop_selects_frozen_policy_and_preserves_inputs(self):
        # Thin wrapper: the real assertions live in the private helper so the
        # value it returns is not counted as a test return value.
        self._develop_selects_frozen_policy_and_preserves_inputs()

    def test_lower_direction_negation_selects_negated_threshold(self):
        # Both the model binding (scoreDirection) and the hand-computed scores
        # must agree on the direction; negation of scores alone does not change
        # the binding, so set the binding to 'lower-is-match' to match.
        self.manifest['binding']['scoreDirection'] = 'lower-is-match'
        self.eval_manifest['binding']['scoreDirection'] = 'lower-is-match'
        policy = self.develop(scores=self.calibration_scores(higher=False))
        self.assertEqual(policy['selectedThreshold'], -0.8)
        report = self.module().evaluate(policy, self.eval_manifest,
                                        self.evaluation_scores(higher=False), self.root,
                                        expected_policy_digest=policy['policyArtifactDigest'])
        self.assertEqual(report['fixedThreshold'], -0.8)
        self.assertEqual(report['selectionStatus'], 'met')

    # ------------------------------------------------------------------
    # positive: evaluate freezes the threshold on an independent split
    # ------------------------------------------------------------------

    def test_evaluate_uses_frozen_threshold_on_independent_split(self):
        policy = self._develop_selects_frozen_policy_and_preserves_inputs()
        # The original captured pin is the digest from successful develop, retained
        # separately by the evaluator; it is never recomputed from the policy.
        pin = policy['policyArtifactDigest']
        test_scores = self.evaluation_scores()
        eval_before = copy.deepcopy(self.eval_manifest)
        scores_before = copy.deepcopy(test_scores)
        policy_before = copy.deepcopy(policy)

        report = self.module().evaluate(policy, self.eval_manifest, test_scores, self.root,
                                        expected_policy_digest=pin)

        # no input mutation
        self.assertEqual(self.eval_manifest, eval_before)
        self.assertEqual(test_scores, scores_before)
        self.assertEqual(policy, policy_before)

        # frozen threshold is used verbatim; independent split is met
        self.assertEqual(report['fixedThreshold'], 0.8)
        self.assertEqual(report['selectionStatus'], 'met')
        self.assertEqual(report['metrics']['farOnResponse'], 0)
        self.assertEqual(report['metrics']['frrOnResponse'], 0)
        self.assertEqual(report['policyArtifactDigest'], policy['policyArtifactDigest'])

        # still an evidence-only report
        self.assertEqual(report['calibrationStatus'], 'unverified')
        self.assertIs(report['productionReady'], False)
        self.assertIs(report['productionPolicyGenerated'], False)

        # aggregate privacy
        rendered = json.dumps(report)
        for token in ('test-0', 'test-2', 'test-subject-0', 'test-subject-1',
                      '"rows"', str(self.root)):
            self.assertNotIn(token, rendered)

        cov = report['coverage']['test']
        self.assertEqual(cov['subjectCount'], 2)
        self.assertEqual(cov['pairCount'], 4)
        self.assertEqual(cov['genuineResponses'], 2)
        self.assertEqual(cov['impostorResponses'], 2)

    # ------------------------------------------------------------------
    # key invariant: changed test outcomes must NOT reselect the threshold
    # ------------------------------------------------------------------

    def test_changed_test_outcomes_do_not_reselect_threshold(self):
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        # Impostor scores flipped above the frozen threshold -> FAR=1, but the
        # operating point is still the frozen 0.8 (no reselection occurs).
        test_scores = self.evaluation_scores(impostor=(.9, .9))
        report = self.module().evaluate(policy, self.eval_manifest, test_scores, self.root,
                                        expected_policy_digest=pin)
        self.assertEqual(report['fixedThreshold'], 0.8)
        self.assertEqual(report['selectionStatus'], 'unmet')
        self.assertEqual(report['metrics']['farOnResponse'], 1)
        self.assertEqual(report['metrics']['frrOnResponse'], 0)
        self.assertEqual(report['policyArtifactDigest'], policy['policyArtifactDigest'])

    # ------------------------------------------------------------------
    # negative: cross-split subject / media-group / media-hash leaks
    # ------------------------------------------------------------------

    def _leak_doc(self, base, kind):
        doc = copy.deepcopy(base)
        if kind == 'subject':
            # a test subject reuses a calibration subject id
            doc['samples'][4]['subjectId'] = 'calibration-subject-0'
        elif kind == 'media-group':
            # a test sample reuses a calibration mediaGroupId
            doc['samples'][4]['mediaGroupId'] = self.manifest['samples'][0]['mediaGroupId']
        elif kind == 'hash':
            # a test sample points at the same bytes as a calibration sample
            src = self.root / self.manifest['samples'][0]['file']['path']
            payload = src.read_bytes()
            dst_path = 'media/test-leak.bin'
            (self.root / dst_path).write_bytes(payload)
            doc['samples'][4]['file'] = dict(
                path=dst_path, sha256=hashlib.sha256(payload).hexdigest())
        elif kind == 'session':
            # within calibration: same (subject, session) used for enrollment and probe
            doc['samples'][2]['sessionId'] = 'enroll'
            doc['samples'][2]['subjectId'] = 'calibration-subject-0'
        return doc

    def test_rejects_cross_split_leaks(self):
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        for which in ('develop', 'evaluate'):
            for kind in ('subject', 'media-group', 'hash'):
                with self.subTest(which=which, kind=kind):
                    base = self.manifest if which == 'develop' else self.eval_manifest
                    doc = self._leak_doc(base, kind)
                    if which == 'develop':
                        with self.assertRaises(ValueError):
                            self.develop(manifest=doc)
                    else:
                        with self.assertRaises(ValueError):
                            self.module().evaluate(policy, doc,
                                                  self.evaluation_scores(), self.root,
                                                  expected_policy_digest=pin)

    def test_rejects_same_subject_enrollment_probe_session_leak(self):
        doc = self._leak_doc(self.manifest, 'session')
        with self.assertRaises(ValueError):
            self.develop(manifest=doc)

    # ------------------------------------------------------------------
    # negative: missing / duplicate / cross-split score cells
    # ------------------------------------------------------------------

    def test_rejects_missing_duplicate_and_cross_split_cells(self):
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        # missing calibration cell
        scores = self.calibration_scores()
        scores['rows'] = scores['rows'][:-1]
        with self.assertRaises(ValueError):
            self.develop(scores=scores)
        # duplicate calibration cell
        scores = self.calibration_scores()
        scores['rows'] = scores['rows'] + [copy.deepcopy(scores['rows'][0])]
        with self.assertRaises(ValueError):
            self.develop(scores=scores)
        # missing test cell
        ts = self.evaluation_scores()
        ts['rows'] = ts['rows'][:-1]
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, ts, self.root,
                                   expected_policy_digest=pin)
        # duplicate test cell
        ts = self.evaluation_scores()
        ts['rows'] = ts['rows'] + [copy.deepcopy(ts['rows'][0])]
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, ts, self.root,
                                   expected_policy_digest=pin)
        # cross-split cell referenced from test scores
        ts = self.evaluation_scores()
        ts['rows'][0]['enrollmentId'] = 'calibration-0'
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, ts, self.root,
                                   expected_policy_digest=pin)

    # ------------------------------------------------------------------
    # negative: inadequate subject / genuine / impostor denominators
    # ------------------------------------------------------------------

    def test_rejects_inadequate_denominators(self):
        for key, floor in (('minSubjects', 3), ('minGenuineResponses', 3),
                           ('minImpostorResponses', 3)):
            with self.subTest(key=key):
                spec = dict(self.spec)
                spec[key] = floor
                with self.assertRaises(ValueError):
                    self.develop(spec=spec)

    # ------------------------------------------------------------------
    # negative: no feasible candidate threshold
    # ------------------------------------------------------------------

    def test_rejects_no_feasible_candidate(self):
        scores = self.calibration_scores()
        for row in scores['rows']:
            row['score'] = 0.8  # inseparable classes -> no operating point meets spec
        spec = dict(targetFar=0.0, maxFrr=0.5, minSubjects=2,
                    minGenuineResponses=2, minImpostorResponses=2, maxNonresponses=0)
        with self.assertRaises(ValueError):
            self.develop(scores=scores, spec=spec)

    # ------------------------------------------------------------------
    # negative: excessive nonresponses (then admissible once limit raised)
    # ------------------------------------------------------------------

    def test_rejects_excessive_nonresponses(self):
        # Build a larger synthetic calibration matrix (3x3 enrollment x probe)
        # with disjoint synthetic subjects so a single nonresponse keeps both
        # class denominators >= 2 (3 genuine -> 2, 6 impostor unchanged).
        manifest = copy.deepcopy(self.manifest)
        manifest['samples'] = [s for s in manifest['samples'] if s['split'] == 'test']
        for i in range(3):
            subject = 'c3-subject-%d' % i
            manifest['samples'].append(dict(
                id='c3-enroll-%d' % i, subjectId=subject, sessionId='enroll',
                mediaGroupId='c3-enroll-%d' % i, split='calibration', role='enrollment',
                label='bona-fide', pai=None,
                file=self.fixture.file('media/c3-enroll-%d.bin' % i,
                                       ('c3-enroll-%d' % i).encode())))
            manifest['samples'].append(dict(
                id='c3-probe-%d' % i, subjectId=subject, sessionId='probe',
                mediaGroupId='c3-probe-%d' % i, split='calibration', role='probe',
                label='bona-fide', pai=None,
                file=self.fixture.file('media/c3-probe-%d.bin' % i,
                                       ('c3-probe-%d' % i).encode())))
        checked = validate_manifest(manifest, self.root)
        rows = []
        for e in range(3):
            for p in range(3):
                rows.append(dict(enrollmentId='c3-enroll-%d' % e,
                                 probeId='c3-probe-%d' % p,
                                 score=(.8 if e == p else .2)))
        scores = dict(schemaVersion=1, kind='identity-calibration-scores',
                      manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
                      binding=copy.deepcopy(manifest['binding']), task='verification',
                      split='calibration', threshold=0.5, rows=rows)
        # one genuine response becomes a nonresponse; denominators stay valid.
        scores['rows'][0]['score'] = None
        with self.assertRaises(ValueError):
            self.develop(manifest=manifest, scores=scores)
        spec = dict(self.spec, maxNonresponses=1)
        policy = self.develop(manifest=manifest, scores=scores, spec=spec)
        cov = policy['coverage']['calibration']
        self.assertEqual(cov['nonresponses'], 1)
        self.assertEqual(cov['genuineResponses'], 2)
        self.assertEqual(cov['impostorResponses'], 6)

    # ------------------------------------------------------------------
    # negative: missing / tampered evidence and media
    # ------------------------------------------------------------------

    def test_rejects_missing_or_tampered_evidence_and_media(self):
        # tampered license digest
        doc = copy.deepcopy(self.manifest)
        doc['licenseEvidence']['sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            self.develop(manifest=doc)
        # referenced media file absent (no shared-file deletion)
        doc = copy.deepcopy(self.manifest)
        doc['samples'][0]['file'] = dict(path='media/missing.bin', sha256='0' * 64)
        with self.assertRaises(ValueError):
            self.develop(manifest=doc)

    # ------------------------------------------------------------------
    # negative: model SHA / revision / preprocess / direction mix, dataset mismatch
    # ------------------------------------------------------------------

    def test_rejects_binding_and_dataset_mismatch(self):
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        base_direction = self.manifest['binding']['scoreDirection']
        for key in ('modelSHA256', 'modelRevision', 'preprocessRevision', 'scoreDirection',
                    'dataset'):
            with self.subTest(key=key):
                doc = copy.deepcopy(self.eval_manifest)
                if key == 'modelSHA256':
                    doc['binding'][key] = 'b' * 64
                elif key == 'scoreDirection':
                    doc['binding'][key] = ('lower-is-match' if base_direction == 'higher-is-match'
                                           else 'higher-is-match')
                elif key == 'dataset':
                    doc['dataset']['revision'] = 'v2'
                else:
                    doc['binding'][key] = 'other'
                with self.assertRaises(ValueError):
                    self.module().evaluate(policy, doc,
                                          self.evaluation_scores(), self.root,
                                          expected_policy_digest=pin)

    # ------------------------------------------------------------------
    # negative: policy digest tamper (field changed, embedded digest NOT
    # rehashed -> recomputed != embedded)
    # ------------------------------------------------------------------

    def test_rejects_policy_digest_tamper(self):
        policy = self.develop()
        original_pin = policy['policyArtifactDigest']
        tampered = copy.deepcopy(policy)
        tampered['unverified'] = tampered['unverified'] + ['extra unverified item']
        # The evaluator still supplies the ORIGINAL pin; the tampered policy's
        # embedded digest no longer recomputes, so it is rejected cleanly.
        with self.assertRaises(ValueError):
            self.module().evaluate(tampered, self.eval_manifest,
                                  self.evaluation_scores(), self.root,
                                  expected_policy_digest=original_pin)

    # ------------------------------------------------------------------
    # negative: threshold tamper + embedded digest rehashed, but the
    # externally retained ORIGINAL pin is kept => still rejected
    # ------------------------------------------------------------------

    def test_rejects_tampered_threshold_rehashed_with_original_pin(self):
        policy = self.develop()
        original_pin = policy['policyArtifactDigest']
        tampered = copy.deepcopy(policy)
        # tamper the frozen threshold (an integrity-relevant field)
        tampered['selectedThreshold'] = tampered['selectedThreshold'] + 0.05
        # recompute the EMBEDDED digest so the received policy is internally
        # self-consistent (rehashed to match) -- this is exactly the attack the
        # external pin defeats: the embedded digest equals the recomputed one, but
        # it no longer equals the original pin captured at develop time.
        tampered['policyArtifactDigest'] = self.module()._policy_digest(tampered)
        with self.assertRaises(ValueError):
            self.module().evaluate(tampered, self.eval_manifest,
                                  self.evaluation_scores(), self.root,
                                  expected_policy_digest=original_pin)

    # ------------------------------------------------------------------
    # negative: missing / malformed expected_policy_digest
    # ------------------------------------------------------------------

    def test_rejects_missing_or_malformed_pin(self):
        policy = self.develop()
        test_scores = self.evaluation_scores()
        # malformed: not a 64-hex digest (rejected by _require_hex64)
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, test_scores, self.root,
                                   expected_policy_digest='not-a-hex-digest')
        # malformed: None is not a hex string
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, test_scores, self.root,
                                   expected_policy_digest=None)
        # missing keyword entirely -> Python-enforced (keyword-only, required)
        with self.assertRaises(TypeError):
            self.module().evaluate(policy, self.eval_manifest, test_scores, self.root)

    # ------------------------------------------------------------------
    # negative: test threshold change is rejected (no reselection path)
    # ------------------------------------------------------------------

    def test_rejects_test_threshold_change(self):
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        ts = self.evaluation_scores()
        ts['threshold'] = 0.81  # differs from the frozen 0.8
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, ts, self.root,
                                   expected_policy_digest=pin)

    # ------------------------------------------------------------------
    # negative: unsupported task and split mismatch
    # ------------------------------------------------------------------

    def test_rejects_unsupported_task_and_split_mismatch(self):
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        doc = copy.deepcopy(self.manifest)
        doc['task'] = 'identification'
        with self.assertRaises(ValueError):
            self.develop(manifest=doc)
        scores = self.calibration_scores()
        scores['split'] = 'test'
        with self.assertRaises(ValueError):
            self.develop(scores=scores)
        ts = self.evaluation_scores()
        ts['split'] = 'calibration'
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, ts, self.root,
                                   expected_policy_digest=pin)

    # ------------------------------------------------------------------
    # negative: strict JSON bool / non-finite / large-integer numerics
    # ------------------------------------------------------------------

    def test_rejects_nonfinite_bool_and_huge_numeric(self):
        # spec targets reject bool / NaN / Inf via strict typing and range
        for key, value in [('targetFar', True), ('targetFar', float('nan')),
                           ('targetFar', float('inf')), ('maxFrr', -float('inf'))]:
            with self.subTest(scope='spec', key=key, value=type(value).__name__):
                spec = copy.deepcopy(self.spec)
                spec[key] = value
                with self.assertRaises(ValueError):
                    self.develop(spec=spec)
        # a 400-digit integer violates the 0..1 range on the spec target
        spec = copy.deepcopy(self.spec)
        spec['targetFar'] = 10 ** 400
        with self.assertRaises(ValueError):
            self.develop(spec=spec)
        # frozen test threshold rejects bool / NaN / Inf / huge-int / non-numeric
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        for value in (True, float('nan'), float('inf'), -float('inf'), 10 ** 400, '0.8'):
            with self.subTest(scope='test-threshold', value=type(value).__name__):
                ts = self.evaluation_scores()
                ts['threshold'] = value
                with self.assertRaises(ValueError):
                    self.module().evaluate(policy, self.eval_manifest, ts, self.root,
                                           expected_policy_digest=pin)

    # ------------------------------------------------------------------
    # negative: malformed score root / rows / huge score value must raise
    # ValueError (not AttributeError / KeyError / OverflowError)
    # ------------------------------------------------------------------

    def test_rejects_malformed_score_root_rows_and_huge_integer(self):
        policy = self.develop()
        pin = policy['policyArtifactDigest']
        # malformed score root: not a document dict at all
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, ['not', 'a', 'doc'],
                                   self.root, expected_policy_digest=pin)
        # malformed rows: rows is not a list
        bad = self.evaluation_scores()
        bad['rows'] = 'not-a-list'
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, bad, self.root,
                                   expected_policy_digest=pin)
        # malformed row: missing required key ('score')
        bad = self.evaluation_scores()
        bad['rows'][0] = dict(enrollmentId='test-0', probeId='test-2')
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, bad, self.root,
                                   expected_policy_digest=pin)
        # huge integer as the frozen test threshold (OverflowError -> ValueError)
        bad = self.evaluation_scores(threshold=10 ** 400)
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, bad, self.root,
                                   expected_policy_digest=pin)
        # huge integer as a raw score value (must not escape as OverflowError)
        bad = self.evaluation_scores()
        bad['rows'][0]['score'] = 10 ** 400
        with self.assertRaises(ValueError):
            self.module().evaluate(policy, self.eval_manifest, bad, self.root,
                                   expected_policy_digest=pin)

    # ------------------------------------------------------------------
    # CLI: two phases on synthetic files (temp outputs only)
    # ------------------------------------------------------------------

    def _write(self, name, document):
        path = self.root / name
        path.write_text(json.dumps(document))
        return path

    def _develop_cli(self, manifest_path, calib_path, spec_path, out_path):
        return subprocess.run(
            [sys.executable, '-m', 'tools.identity_independent_evaluation', 'develop',
             '--manifest', str(manifest_path), '--calibration-scores', str(calib_path),
             '--spec', str(spec_path), '--output', str(out_path)],
            cwd=REPO_ROOT, capture_output=True, text=True)

    def _evaluate_cli(self, policy_path, manifest_path, test_path, out_path, pin):
        return subprocess.run(
            [sys.executable, '-m', 'tools.identity_independent_evaluation', 'evaluate',
             '--policy', str(policy_path), '--manifest', str(manifest_path),
             '--test-scores', str(test_path), '--expected-policy-digest', pin,
             '--output', str(out_path)],
            cwd=REPO_ROOT, capture_output=True, text=True)

    def test_cli_develop_and_evaluate_two_phases(self):
        manifest_path = self._write('manifest.json', self.manifest)
        calib_path = self._write('calibration_scores.json', self.calibration_scores())
        spec_path = self._write('spec.json', self.spec)
        policy_path = self.root / 'policy.json'
        result = self._develop_cli(manifest_path, calib_path, spec_path, policy_path)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, '')
        policy = json.loads(policy_path.read_text())
        self.assertEqual(policy['selectedThreshold'], 0.8)
        self.assertIs(policy['productionReady'], False)
        self.assertEqual(policy['calibrationStatus'], 'unverified')

        # The original captured pin is the develop-time policyArtifactDigest,
        # retained by the evaluator and supplied verbatim to evaluate.
        pin = policy['policyArtifactDigest']
        eval_manifest_path = self._write('eval_manifest.json', self.eval_manifest)
        test_path = self._write('test_scores.json', self.evaluation_scores())
        report_path = self.root / 'report.json'
        result = self._evaluate_cli(policy_path, eval_manifest_path, test_path, report_path, pin)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, '')
        report = json.loads(report_path.read_text())
        self.assertEqual(report['fixedThreshold'], 0.8)
        self.assertEqual(report['selectionStatus'], 'met')
        self.assertEqual(report['metrics']['farOnResponse'], 0)

    def test_cli_develop_and_evaluate_under_system_temp_prefixes(self):
        # Baseline plus macOS system-volume aliases (/var, /tmp, /private/tmp)
        # must succeed: exactly one leading alias is normalized; the remainder of
        # the path is traversed concretely. Skipped where a prefix is absent or
        # not writable (e.g. /private/tmp on Linux).
        candidates = []
        for base in (Path(tempfile.gettempdir()), Path('/tmp'),
                     Path('/private/tmp'), Path('/var/tmp')):
            try:
                if base.exists() and os.access(base, os.W_OK):
                    candidates.append(base)
            except OSError:
                pass
        if not candidates:
            self.skipTest('no writable system temp prefix available')

        manifest_path = self._write('manifest.json', self.manifest)
        calib_path = self._write('calibration_scores.json', self.calibration_scores())
        spec_path = self._write('spec.json', self.spec)
        for base in candidates:
            with self.subTest(base=str(base)):
                out_dir = Path(tempfile.mkdtemp(dir=str(base)))
                policy_path = out_dir / 'policy.json'
                result = self._develop_cli(manifest_path, calib_path, spec_path, policy_path)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertTrue(policy_path.exists())
                policy = json.loads(policy_path.read_text())
                pin = policy['policyArtifactDigest']
                eval_manifest_path = self._write('eval_manifest.json', self.eval_manifest)
                test_path = self._write('test_scores.json', self.evaluation_scores())
                report_path = out_dir / 'report.json'
                result = self._evaluate_cli(policy_path, eval_manifest_path, test_path,
                                            report_path, pin)
                self.assertEqual(result.returncode, 0, result.stderr)
                report = json.loads(report_path.read_text())
                self.assertEqual(report['fixedThreshold'], 0.8)
                self.assertEqual(report['selectionStatus'], 'met')

    # ------------------------------------------------------------------
    # CLI: output exists / symlink / FIFO are rejected (nonblocking) and
    # existing bytes are preserved
    # ------------------------------------------------------------------

    def test_cli_rejects_existing_symlink_fifo_output_and_preserves_bytes(self):
        manifest_path = self._write('manifest.json', self.manifest)
        calib_path = self._write('calibration_scores.json', self.calibration_scores())
        spec_path = self._write('spec.json', self.spec)

        # pre-existing regular file: must not be overwritten (O_EXCL fails)
        out = self.root / 'out.json'
        out.write_text('ORIGINAL BYTES PRESERVED')
        result = self._develop_cli(manifest_path, calib_path, spec_path, out)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(out.read_text(), 'ORIGINAL BYTES PRESERVED')

        # symlinked output: must not be followed / written (O_NOFOLLOW)
        link = self.root / 'link.json'
        link.symlink_to(out)
        result = self._develop_cli(manifest_path, calib_path, spec_path, link)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(link.is_symlink())

        # pre-existing FIFO output: O_EXCL fails before any write (no block); the
        # FIFO is left in place, never opened or removed.
        fifo = self.root / 'fifo.json'
        os.mkfifo(fifo)
        try:
            result = self._develop_cli(manifest_path, calib_path, spec_path, fifo)
            self.assertNotEqual(result.returncode, 0)
            self.assertTrue(fifo.exists())
        finally:
            fifo.unlink()

    # ------------------------------------------------------------------
    # CLI: arbitrary / inner parent symlink output is rejected
    # ------------------------------------------------------------------

    def test_cli_rejects_inner_parent_symlink_output(self):
        manifest_path = self._write('manifest.json', self.manifest)
        calib_path = self._write('calibration_scores.json', self.calibration_scores())
        spec_path = self._write('spec.json', self.spec)
        # An arbitrary (non-alias) parent directory that is itself a symlink must
        # be refused; only the exact /var and /tmp aliases may be normalized.
        real = self.root / 'realdir'
        real.mkdir()
        link = self.root / 'linkdir'
        link.symlink_to(real)
        out = link / 'out.json'
        result = self._develop_cli(manifest_path, calib_path, spec_path, out)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(link.is_symlink())

    # ------------------------------------------------------------------
    # CLI: duplicate JSON key and non-finite spec are rejected (no private values)
    # ------------------------------------------------------------------

    def test_cli_rejects_duplicate_json_and_nonfinite_spec(self):
        manifest_path = self._write('manifest.json', self.manifest)
        calib_path = self._write('calibration_scores.json', self.calibration_scores())
        spec_path = self.root / 'spec.json'
        spec_path.write_text('{"targetFar":0.5,"targetFar":0.5}')  # duplicate key
        out = self.root / 'out.json'
        result = self._develop_cli(manifest_path, calib_path, spec_path, out)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, '')
        self.assertNotIn(str(self.root), result.stderr)

        spec_path.write_text('{"targetFar":NaN,"maxFrr":0.5,"minSubjects":2,'
                             '"minGenuineResponses":2,"minImpostorResponses":2,'
                             '"maxNonresponses":0}')
        result = self._develop_cli(manifest_path, calib_path, spec_path, out)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, '')

    # ------------------------------------------------------------------
    # CLI: evaluate requires the --expected-policy-digest flag (mandatory)
    # ------------------------------------------------------------------

    def test_cli_evaluate_requires_expected_policy_digest_flag(self):
        manifest_path = self._write('manifest.json', self.manifest)
        calib_path = self._write('calibration_scores.json', self.calibration_scores())
        spec_path = self._write('spec.json', self.spec)
        policy_path = self.root / 'policy.json'
        self.assertEqual(self._develop_cli(manifest_path, calib_path, spec_path,
                                           policy_path).returncode, 0)
        eval_manifest_path = self._write('eval_manifest.json', self.eval_manifest)
        test_path = self._write('test_scores.json', self.evaluation_scores())
        report_path = self.root / 'report.json'
        # Omit the mandatory flag entirely -> argparse fails (exit != 0).
        result = subprocess.run(
            [sys.executable, '-m', 'tools.identity_independent_evaluation', 'evaluate',
             '--policy', str(policy_path), '--manifest', str(eval_manifest_path),
             '--test-scores', str(test_path), '--output', str(report_path)],
            cwd=REPO_ROOT, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(report_path.exists())

    # ------------------------------------------------------------------
    # CLI: evaluate rejects a tampered policy digest (pin retained externally)
    # ------------------------------------------------------------------

    def test_cli_evaluate_rejects_tampered_policy(self):
        manifest_path = self._write('manifest.json', self.manifest)
        calib_path = self._write('calibration_scores.json', self.calibration_scores())
        spec_path = self._write('spec.json', self.spec)
        policy_path = self.root / 'policy.json'
        self.assertEqual(self._develop_cli(manifest_path, calib_path, spec_path,
                                           policy_path).returncode, 0)
        policy = json.loads(policy_path.read_text())
        # The evaluator captured this original pin at develop time and retains it
        # out of band; it is NOT recomputed from the received (tampered) policy.
        original_pin = policy['policyArtifactDigest']
        policy['unverified'].append('tamper')
        policy_path.write_text(json.dumps(policy))

        eval_manifest_path = self._write('eval_manifest.json', self.eval_manifest)
        test_path = self._write('test_scores.json', self.evaluation_scores())
        report_path = self.root / 'report.json'
        result = self._evaluate_cli(policy_path, eval_manifest_path, test_path,
                                    report_path, original_pin)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(report_path.exists())


if __name__ == '__main__':
    unittest.main()
