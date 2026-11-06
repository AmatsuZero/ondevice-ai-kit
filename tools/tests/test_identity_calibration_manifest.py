"""Synthetic files prove preflight behavior, never recognition or PAD quality."""
import copy
import hashlib
import importlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class CalibrationManifestTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.document = {
            'schemaVersion': 1, 'kind': 'identity-calibration-manifest',
            'dataClass': 'synthetic', 'task': 'verification',
            'dataset': dict(id='fixture', revision='v1', sourceURL='https://example.invalid/dataset'),
            'purpose': 'local-calibration', 'redistributeDataset': False,
            'licenseEvidence': self.file('license.txt', b'SYNTHETIC LICENSE ONLY'),
            'authorization': dict(allowedPurposes=['local-calibration', 'local-evaluation'],
                                  redistributionAllowed=False,
                                  evidence=self.file('authorization.txt', b'SYNTHETIC DECLARATION ONLY')),
            'binding': dict(backendId='fixture.backend', modelId='fixture.model',
                            modelRevision='v1', modelSHA256='a' * 64,
                            preprocessRevision='p1', scoreMetric='cosine', scoreDirection='higher-is-match'),
            'samples': [],
        }
        for split in ('calibration', 'test'):
            for index in range(4):
                key = f'{split}-{index}'
                self.document['samples'].append(dict(
                    id=key, subjectId=f'{split}-subject-{index % 2}',
                    sessionId='enroll' if index < 2 else 'probe', mediaGroupId=key,
                    split=split, role='enrollment' if index < 2 else 'probe',
                    label='bona-fide', pai=None, file=self.file(f'media/{key}.bin', key.encode())))

    def file(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return dict(path=name, sha256=hashlib.sha256(content).hexdigest())

    def validate(self, document=None):
        module = importlib.import_module('tools.identity_calibration_manifest')
        return module.validate_manifest(self.document if document is None else document, self.root)

    def pad(self):
        self.document['task'] = 'passive-pad'
        self.document['binding']['scoreMetric'] = 'real-score'
        self.document['binding']['scoreDirection'] = 'higher-is-bona-fide'
        for index, row in enumerate(self.document['samples']):
            row['role'] = 'presentation'
            row['label'] = 'attack' if index % 2 else 'bona-fide'
            row['pai'] = 'print' if index % 2 else None

    def run_cli(self, text):
        path = self.root / 'manifest.json'
        path.write_text(text)
        return subprocess.run([sys.executable, '-m', 'tools.identity_calibration_manifest', str(path)],
                              cwd=Path(__file__).resolve().parents[2], capture_output=True, text=True)

    def test_identity_report_preserves_input_and_stays_unverified(self):
        original = copy.deepcopy(self.document)
        before = {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        result = self.validate()
        self.assertEqual(result['validationStatus'], 'pass-bounded')
        self.assertEqual(result['authorizationStatus'], 'declared-scope-only')
        self.assertEqual(result['calibrationStatus'], 'unverified')
        self.assertEqual(result['dataClass'], 'synthetic')
        self.assertEqual(result['sampleCount'], 8)
        self.assertEqual(result['binding'], self.document['binding'])
        self.assertEqual(result['splits']['calibration'], dict(
            sampleCount=4, subjectCount=2, roles=dict(enrollment=2, probe=2),
            labels={'bona-fide': 4}, pai={}))
        canonical = json.dumps(self.document, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()
        self.assertEqual(result['manifestCanonicalSHA256'], hashlib.sha256(canonical).hexdigest())
        self.assertEqual(self.document, original)
        self.assertEqual(before, {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob('*') if p.is_file()})
        rendered = json.dumps(result)
        for row in self.document['samples']:
            self.assertNotIn(row['id'], rendered)
            self.assertNotIn(row['subjectId'], rendered)
            self.assertNotIn(row['file']['path'], rendered)
        self.assertNotIn(str(self.root), rendered)

    def test_genuine_subject_overlap_and_subject_scoped_sessions_are_valid(self):
        # Two subjects share the token 'enroll', and each has a separate probe session.
        for task in ('verification', 'identification'):
            for direction in ('higher-is-match', 'lower-is-match'):
                with self.subTest(task=task, direction=direction):
                    self.document['task'] = task
                    self.document['binding']['scoreDirection'] = direction
                    self.assertEqual(self.validate()['sampleCount'], 8)

    def test_passive_pad_reports_per_pai_and_class_counts(self):
        self.pad()
        result = self.validate()
        self.assertEqual(result['splits']['test']['labels'], {'bona-fide': 2, 'attack': 2})
        self.assertEqual(result['splits']['test']['pai'], {'print': 2})
        self.document['binding']['scoreDirection'] = 'lower-is-bona-fide'
        self.assertEqual(self.validate()['calibrationStatus'], 'unverified')

    def test_local_use_does_not_require_redistribution_permission(self):
        self.assertEqual(self.validate()['purpose'], 'local-calibration')
        self.document['purpose'] = 'local-evaluation'
        self.assertEqual(self.validate()['purpose'], 'local-evaluation')
        self.document['redistributeDataset'] = True
        with self.assertRaises(ValueError):
            self.validate()
        self.document['authorization']['redistributionAllowed'] = True
        self.assertEqual(self.validate()['validationStatus'], 'pass-bounded')

    def test_denies_undeclared_purpose(self):
        self.document['authorization']['allowedPurposes'] = ['local-evaluation']
        with self.assertRaises(ValueError):
            self.validate()

    def test_declared_real_data_still_cannot_become_verified(self):
        self.document['dataClass'] = 'real'
        self.assertEqual(self.validate()['calibrationStatus'], 'unverified')
        self.assertEqual(self.validate()['authorizationStatus'], 'declared-scope-only')

    def test_tampered_or_missing_evidence_or_media_rejected(self):
        for location in ('license', 'authorization', 'media'):
            with self.subTest(location=location):
                doc = copy.deepcopy(self.document)
                ref = (doc['licenseEvidence'] if location == 'license' else
                       doc['authorization']['evidence'] if location == 'authorization' else doc['samples'][0]['file'])
                ref['sha256'] = '0' * 64
                with self.assertRaises(ValueError):
                    self.validate(doc)
        (self.root / self.document['licenseEvidence']['path']).unlink()
        with self.assertRaises(ValueError):
            self.validate()

    def test_rejects_duplicate_ids_media_and_cross_split_groups_or_subjects(self):
        for case in ('id', 'file', 'subjectId', 'mediaGroupId'):
            with self.subTest(case=case):
                doc = copy.deepcopy(self.document)
                doc['samples'][4][case] = copy.deepcopy(doc['samples'][0][case])
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_rejects_content_duplicate_at_different_path(self):
        self.document['samples'][1]['file'] = self.file('different.bin', b'calibration-0')
        with self.assertRaises(ValueError):
            self.validate()

    def test_rejects_same_subject_session_in_enrollment_and_probe(self):
        self.document['samples'][2]['sessionId'] = self.document['samples'][0]['sessionId']
        with self.assertRaises(ValueError):
            self.validate()

    def test_rejects_source_media_group_shared_by_enrollment_and_probe(self):
        # Different file hashes/session labels do not make derivatives independent.
        self.document['samples'][2]['mediaGroupId'] = self.document['samples'][0]['mediaGroupId']
        with self.assertRaises(ValueError):
            self.validate()

    def test_rejects_source_url_with_control_whitespace_or_invalid_port(self):
        for url in ('https://exa\nmple.invalid/data', 'https://example.invalid:bad/data',
                    'https://example.invalid:65536/data', ' https://example.invalid/data'):
            with self.subTest(url=url):
                doc = copy.deepcopy(self.document)
                doc['dataset']['sourceURL'] = url
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_rejects_c1_control_characters_in_source_url(self):
        for control in ('\x80', '\x9f'):
            with self.subTest(control=ord(control)):
                doc = copy.deepcopy(self.document)
                doc['dataset']['sourceURL'] = 'https://example.invalid/' + control
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_detects_actual_file_mutation_while_descriptor_open(self):
        module = importlib.import_module('tools.identity_calibration_manifest')
        with self.assertRaises(ValueError):
            with module._regular_file(self.root, 'license.txt') as stream:
                self.assertTrue(stream.read())
                with (self.root / 'license.txt').open('ab') as writer:
                    writer.write(b'changed')

    def test_requires_both_splits_and_identity_roles(self):
        for case in ('split', 'role'):
            with self.subTest(case=case):
                doc = copy.deepcopy(self.document)
                if case == 'split':
                    doc['samples'] = doc['samples'][:4]
                else:
                    for row in doc['samples'][:4]:
                        row['role'] = 'enrollment'
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_rejects_invalid_identity_and_pad_labels_roles_pai(self):
        for is_pad in (False, True):
            if is_pad:
                self.pad()
            for key, value in [('label', 'unknown'), ('role', 'unknown'), ('pai', ''),
                               ('label', 'attack' if not is_pad else 'bona-fide')]:
                with self.subTest(pad=is_pad, key=key, value=value):
                    doc = copy.deepcopy(self.document)
                    row = doc['samples'][1 if is_pad else 0]
                    row[key] = value
                    with self.assertRaises(ValueError):
                        self.validate(doc)
        for row in self.document['samples'][:4]:
            row.update(label='bona-fide', pai=None)
        with self.assertRaises(ValueError):
            self.validate()

    def test_rejects_schema_aliases_unknown_fields_and_invalid_binding(self):
        for key, value in [('schemaVersion', True), ('schemaVersion', 1.0), ('schemaVersion', 2),
                           ('redistributeDataset', 0), ('task', 'active-pad'), ('dataClass', 'approved'),
                           ('purpose', 'production'), ('extra', 'unknown'), ('samples', [])]:
            with self.subTest(key=key, value=value):
                doc = copy.deepcopy(self.document)
                doc[key] = value
                with self.assertRaises(ValueError):
                    self.validate(doc)
        for key, value in [('scoreDirection', 'higher-is-bona-fide'), ('modelSHA256', 'A' * 64),
                           ('modelRevision', ''), ('scoreMetric', float('nan'))]:
            with self.subTest(binding=key):
                doc = copy.deepcopy(self.document)
                doc['binding'][key] = value
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_requires_exact_nested_fields_and_authorization_types(self):
        docs = []
        for key in self.document:
            doc = copy.deepcopy(self.document)
            del doc[key]
            docs.append(doc)
        for section in ('dataset', 'binding', 'authorization'):
            doc = copy.deepcopy(self.document)
            doc[section]['unexpected'] = 1
            docs.append(doc)
        for value in ([], ['local-calibration', 'local-calibration'], ['commercial'], 'local-calibration'):
            doc = copy.deepcopy(self.document)
            doc['authorization']['allowedPurposes'] = value
            docs.append(doc)
        doc = copy.deepcopy(self.document)
        doc['authorization']['redistributionAllowed'] = 1
        docs.append(doc)
        for doc in docs:
            with self.subTest(keys=list(doc)):
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_rejects_private_source_urls_and_non_token_identifiers(self):
        for url in ('http://example.invalid', 'https://user:password@example.invalid/data', 'https:///data'):
            doc = copy.deepcopy(self.document)
            doc['dataset']['sourceURL'] = url
            with self.assertRaises(ValueError):
                self.validate(doc)
        self.document['samples'][0]['subjectId'] = ' '
        with self.assertRaises(ValueError):
            self.validate()

    def test_rejects_escaping_or_ambiguous_reference_paths(self):
        for path in ('../outside', '/etc/passwd', 'media/../license.txt', './license.txt',
                     'media//x.bin', 'media\\x.bin', ''):
            with self.subTest(path=path):
                doc = copy.deepcopy(self.document)
                doc['licenseEvidence']['path'] = path
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_rejects_symlinks_directory_and_fifo_without_reading_them(self):
        (self.root / 'link').symlink_to(self.root / 'license.txt')
        (self.root / 'linked-dir').symlink_to(self.root / 'media', target_is_directory=True)
        os.mkfifo(self.root / 'fifo')
        for path in ('link', 'linked-dir/calibration-0.bin', 'media', 'fifo'):
            with self.subTest(path=path):
                doc = copy.deepcopy(self.document)
                doc['licenseEvidence']['path'] = path
                with self.assertRaises(ValueError):
                    self.validate(doc)

    def test_cli_success_emits_only_bounded_aggregate_json(self):
        result = self.run_cli(json.dumps(self.document))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, '')
        self.assertEqual(json.loads(result.stdout), self.validate())

    def test_cli_rejects_duplicate_nonfinite_and_malformed_json_without_private_values(self):
        raw = json.dumps(self.document)
        for text in ('{"schemaVersion":1,"schemaVersion":1}', raw.replace('"schemaVersion": 1', '"schemaVersion": NaN'),
                     raw.replace('"schemaVersion": 1', '"schemaVersion": Infinity'), '{'):
            with self.subTest(text=text[:35]):
                result = self.run_cli(text)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, '')
                self.assertNotIn(str(self.root), result.stderr)
                self.assertNotIn('calibration-subject-0', result.stderr)


if __name__ == '__main__':
    unittest.main()
