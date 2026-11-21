import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import p11_grouped_report as grouped
from tools.probes import android_identity_resource_report as reducer
from tools.tests.test_android_identity_resource_report import fixture


def write_json(root, name, value):
    path = root / name
    path.write_text(json.dumps(value))
    return dict(path=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def setup_manifest(root):
    raw = fixture()
    image = root / 'input.png'
    image.write_bytes(b'only an identity-binding test fixture')
    image_sha = hashlib.sha256(image.read_bytes()).hexdigest()
    raw['inputSHA256'] = image_sha
    entry = dict(kind='identity-android-resource', p11Capability='face-identity-liveness',
                 **write_json(root, 'raw.json', raw))
    entry['input'] = dict(path=image.name, sha256=image_sha)
    provenance = dict(schemaVersion=1, runtimeScope='current-candidate-fixed-input-resource',
                      appSha256='dfccaa95a809b4f37b8d384cd32f92c37c7422178803dc1ff5d44a88654879a7',
                      testApkSha256='a' * 64, installedAppSourceCommit=raw['sdkCommit'],
                      frozenBuildSourceCommit=raw['sdkCommit'], modelSHA256=raw['modelSHA256'],
                      modelRevision=raw['modelRevision'], runtimeVersion=raw['runtimeVersion'],
                      preprocessRevision=raw['preprocessRevision'],
                      sourceHashes={entry['path']: entry['sha256'], image.name: image_sha})
    entry['sourceManifest'] = write_json(root, 'source.json', provenance)
    with patch.object(reducer, 'INPUT_SHA256', image_sha):
        summary = dict(reducer.summarize(raw), rawReportSha256=entry['sha256'],
                       sourceManifestSha256=entry['sourceManifest']['sha256'])
    entry['summary'] = write_json(root, 'summary.json', summary)
    readiness = dict(schemaVersion=1, rows=[dict(capability='face-identity-liveness',
                     status='unverified-calibration-pad', unverified=['calibration', 'PAD'], evidence=['evidence.json'])],
                     p11Stop='Do not release while evidence gaps remain.',
                     requiredBeforeP11=['verify all source evidence'],
                     sourceHashes={'evidence.json': write_json(root, 'evidence.json', {})['sha256']},
                     p11Gate=dict(status='open', finalReleaseConclusion='not-ready',
                                  rowsWithUnverifiedGaps=1, rowsNotTerminalPass=1))
    return dict(schemaVersion=1, readiness=write_json(root, 'readiness.json', readiness), sources=[entry])


class IdentityGroupedTests(unittest.TestCase):
    def generate(self, root, manifest):
        with patch.object(reducer, 'INPUT_SHA256', manifest['sources'][0]['input']['sha256']):
            return grouped.generate(manifest, root)

    def test_representation_pipeline_adds_thirty_runs_without_policy_promotion(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = setup_manifest(root)
            result = self.generate(root, manifest)
            group = result['groups'][0]
            self.assertEqual(group['kind'], 'identity-android-resource')
            self.assertEqual(group['memory'].get('processScope'), 'process-with-idle-main-activity')
            self.assertEqual(len(group['runIds']), 30)
            self.assertEqual(group['timingsMs']['qualityAndEmbedWall']['p50'], 20)
            self.assertEqual(group['identity']['installedAppSourceCommit'], fixture()['sdkCommit'])
            self.assertEqual(group['quality']['status'], 'unverified')
            self.assertIn('PAD', group['unverified'])
            self.assertEqual(result['p11Gate'], dict(status='open', finalReleaseConclusion='not-ready'))

    def test_summary_changes_rejected_even_with_updated_summary_hash(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = setup_manifest(root)
            entry = manifest['sources'][0]
            summary = json.loads((root / entry['summary']['path']).read_text())
            summary['durationMs']['p50'] = 1
            entry['summary'] = write_json(root, 'summary.json', summary)
            with self.assertRaises(ValueError):
                self.generate(root, manifest)

    def test_summary_numeric_type_aliases_do_not_equal_recomputed_summary(self):
        for key, value in [('schemaVersion', True), ('warmupRuns', 5.0), ('measuredRuns', 30.0), ('pid', 123.0)]:
            with self.subTest(key=key), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                manifest = setup_manifest(root)
                entry = manifest['sources'][0]
                summary = json.loads((root / 'summary.json').read_text())
                summary[key] = value
                entry['summary'] = write_json(root, 'summary.json', summary)
                with self.assertRaises(ValueError):
                    self.generate(root, manifest)

    def test_exact_app_commit_model_and_integer_schema_provenance_required(self):
        for key, value in [('schemaVersion', True), ('appSha256', 'b' * 64),
                           ('installedAppSourceCommit', None), ('frozenBuildSourceCommit', 'b' * 40),
                           ('modelSHA256', {}), ('modelRevision', 'other'), ('runtimeVersion', '4.13.0'),
                           ('preprocessRevision', 'other'), ('runtimeScope', 'ordinary-app'),
                           ('testApkSha256', ''), ('sourceHashes', {})]:
            with self.subTest(key=key), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                manifest = setup_manifest(root)
                entry = manifest['sources'][0]
                provenance = json.loads((root / 'source.json').read_text())
                provenance[key] = value
                entry['sourceManifest'] = write_json(root, 'source.json', provenance)
                summary = json.loads((root / 'summary.json').read_text())
                summary['sourceManifestSha256'] = entry['sourceManifest']['sha256']
                entry['summary'] = write_json(root, 'summary.json', summary)
                with self.assertRaises(ValueError):
                    self.generate(root, manifest)

    def test_extra_source_hash_is_verified_not_just_recorded(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = setup_manifest(root)
            entry = manifest['sources'][0]
            provenance = json.loads((root / 'source.json').read_text())
            provenance['sourceHashes']['missing-source.kt'] = 'a' * 64
            entry['sourceManifest'] = write_json(root, 'source.json', provenance)
            summary = json.loads((root / 'summary.json').read_text())
            summary['sourceManifestSha256'] = entry['sourceManifest']['sha256']
            entry['summary'] = write_json(root, 'summary.json', summary)
            with self.assertRaises(ValueError):
                self.generate(root, manifest)

    def test_output_cannot_replace_extra_provenance_source_or_hardlink(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = setup_manifest(root)
            source = root / 'probe.kt'
            source.write_text('frozen source')
            entry = manifest['sources'][0]
            provenance = json.loads((root / 'source.json').read_text())
            provenance['sourceHashes'][source.name] = hashlib.sha256(source.read_bytes()).hexdigest()
            entry['sourceManifest'] = write_json(root, 'source.json', provenance)
            summary = json.loads((root / 'summary.json').read_text())
            summary['sourceManifestSha256'] = entry['sourceManifest']['sha256']
            entry['summary'] = write_json(root, 'summary.json', summary)
            self.generate(root, manifest)
            alias = root / 'source-alias.json'
            alias.hardlink_to(source)
            for output in (source, alias):
                with self.subTest(output=output.name), self.assertRaises(ValueError):
                    grouped._reject_output_aliases(manifest, root, output)
            self.assertEqual(source.read_text(), 'frozen source')

    def test_wrong_capability_and_duplicate_sessions_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = setup_manifest(root)
            manifest['sources'].append(dict(manifest['sources'][0]))
            with self.assertRaises(ValueError):
                self.generate(root, manifest)
            manifest['sources'] = manifest['sources'][:1]
            manifest['sources'][0]['p11Capability'] = 'face-detection'
            readiness = json.loads((root / 'readiness.json').read_text())
            readiness['rows'][0]['capability'] = 'face-detection'
            manifest['readiness'] = write_json(root, 'readiness.json', readiness)
            with self.assertRaises(ValueError):
                self.generate(root, manifest)

    def test_output_cannot_replace_image_or_provenance_even_via_hardlink(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = setup_manifest(root)
            for path in ('input.png', 'source.json'):
                with self.subTest(path=path), self.assertRaises(ValueError):
                    grouped._reject_output_aliases(manifest, root, root / path)
            alias = root / 'alias.json'
            alias.hardlink_to(root / 'input.png')
            with self.assertRaises(ValueError):
                grouped._reject_output_aliases(manifest, root, alias)


if __name__ == '__main__':
    unittest.main()
