"""Parent-owned checks for notice-index binding, not license approval."""
import hashlib
import importlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


class NoticeBindingBoundaryTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name) / 'collected'
        self.root.mkdir()
        self.version = '0.144.6-ondevice-f02.6'
        self.body = b'original license text'
        self.index = {'candidateVersion': self.version, 'files': [
            {'path': 'skia/LICENSE', 'bytes': len(self.body),
             'sha256': hashlib.sha256(self.body).hexdigest()}]}
        (self.root / 'skia').mkdir()
        (self.root / 'skia/LICENSE').write_bytes(self.body)
        self.reference()

    def reference(self):
        self.raw = json.dumps(self.index).encode()
        self.digest = hashlib.sha256(self.raw).hexdigest()
        (self.root / 'NOTICE-INDEX.json').write_bytes(self.raw)

    def validate(self):
        self.assertIsNotNone(importlib.util.find_spec('tools.nexus_notice_materials'),
                             'missing notice material checker')
        module = importlib.import_module('tools.nexus_notice_materials')
        return module.validate_materials(self.raw, self.digest, self.version, self.root)

    def reject(self):
        with self.assertRaisesRegex(ValueError, '^notice material validation failed$'):
            self.validate()

    def test_success_certifies_two_bytes_sources_without_legal_or_remote_approval(self):
        result = self.validate()
        self.assertEqual(result['validatedMaterialCount'], 2)
        self.assertEqual(result['indexSha256'], self.digest)
        self.assertIs(result['legalApproval'], False)
        self.assertIs(result['remoteConsumerValidated'], False)
        self.assertIs(result['networkPerformedByThisTool'], False)
        self.assertEqual((self.root / 'skia/LICENSE').read_bytes(), self.body)

    def test_retained_index_cannot_differ_from_external_reference(self):
        (self.root / 'NOTICE-INDEX.json').write_bytes(self.raw + b' ')
        self.reject()

    def test_external_hash_is_required_even_when_every_retained_byte_matches(self):
        self.digest = '0' * 64
        self.reject()

    def test_version_mismatch_rejected(self):
        self.version = '0.144.6-ondevice-f02.4'
        self.reject()

    def test_correct_declarations_do_not_mask_changed_actual_material(self):
        (self.root / 'skia/LICENSE').write_bytes(b'X' * len(self.body))
        self.reject()

    def test_relative_escape_cannot_read_an_external_matching_material(self):
        outside = self.root.parent / 'outside'
        outside.write_bytes(self.body)
        self.index['files'][0]['path'] = '../outside'
        self.reference()
        self.reject()
        self.assertEqual(outside.read_bytes(), self.body)

    def test_frozen_reference_provenance_metadata_is_preserved(self):
        self.index.update(schemaVersion=1, status='draft-file-notices-not-approved',
                          scope='local materials', sources={'skia': 'a' * 40},
                          remaining=['independent approval'])
        self.reference()
        result = self.validate()
        self.assertEqual(result['validatedMaterialCount'], 2)
        self.assertIs(result['legalApproval'], False)

    def test_optional_schema_version_is_strict(self):
        for value in (True, 1.0, 2):
            with self.subTest(schemaVersion=value):
                self.index['schemaVersion'] = value
                self.reference()
                self.reject()
