"""Parent-only regressions; synthetic reports never execute Android/native code."""
from pathlib import Path
import copy
import hashlib
import importlib.util
import json
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


baseline = load('file_parent_checks', ROOT / 'probes/android-qwen-file/check_parent.py')
verify = load('arena_verify', Path(__file__).with_name('verify_report.py'))
device = load('arena_device', Path(__file__).with_name('device_acceptance.py'))


class ArenaParentChecks(baseline.ParentChecks):
    def setUp(self):
        super().setUp()
        self.private_root = '/data/user/0/io.ondeviceai.acceptance.qwenarena/no_backup'
        self.report.update(probeVariant='cpu-arena-off-v1', nativePolicyEffectVerified=False,
                           allocatorPolicy={
                               'provider': 'cpu:' + self.private_root + '/cpu-arena-off/cpu.config',
                               'configPath': self.private_root + '/cpu-arena-off/cpu.config',
                               'bytes': 20,
                               'sha256': hashlib.sha256(b'EnableCpuMemArena=0\n').hexdigest()})
        self.report['config'].update(provider=self.report['allocatorPolicy']['provider'],
                                     requestedEnableCpuMemArena=False)

    def evaluate(self):
        return verify.evaluate(self.report, self.host, self.manifest, 'a' * 64, self.private_root)

    def test_report_is_never_changed_by_baseline_provider_adapter(self):
        before = copy.deepcopy(self.report)
        self.assertTrue(self.evaluate()['executionAccepted'])
        self.assertEqual(self.report, before)
        self.assertFalse(self.evaluate()['nativePolicyEffectVerified'])

    def test_policy_metadata_mismatch_cannot_accept_complete_quality(self):
        mutations = [
            lambda r: r.update(probeVariant='default'),
            lambda r: r.update(nativePolicyEffectVerified=True),
            lambda r: r.update(nativePolicyEffectVerified=0),
            lambda r: r.update(allocatorPolicy=None),
            lambda r: r['allocatorPolicy'].update(provider='cpu'),
            lambda r: r['allocatorPolicy'].update(configPath='/other/cpu.config'),
            lambda r: r['allocatorPolicy'].update(bytes=21),
            lambda r: r['allocatorPolicy'].update(bytes=20.0),
            lambda r: r['allocatorPolicy'].update(sha256='0' * 64),
            lambda r: r['allocatorPolicy'].update(applied=True),
            lambda r: r['config'].update(provider='cpu'),
            lambda r: r['config'].update(requestedEnableCpuMemArena=True),
            lambda r: r['config'].update(requestedEnableCpuMemArena=0),
        ]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                original = copy.deepcopy(self.report)
                mutation(self.report)
                result = self.evaluate()
                self.assertFalse(result['executionAccepted'])
                self.assertFalse(result['engineeringQualityAccepted'])
                self.report = original

    def test_nonterminal_prepolicy_failure_is_structured_and_never_exact(self):
        self.report.update(terminal=False, status='running', allocatorPolicy=None)
        self.report['config']['provider'] = None
        self.host['stopReason'] = 'resource_observation_failed'
        result = self.evaluate()
        self.assertFalse(result['executionAccepted'])
        self.assertIsNone(result['actualDecodeCallsObserved'])
        self.assertFalse(result['countsAreExactTerminalEvidence'])

    def test_no_frozen_contract_means_no_device_action(self):
        with self.assertRaises(FileNotFoundError):
            device.verify_frozen_contract(ROOT / '.omx/artifacts/w3-qwen-arena-wrapper-20261111/absent-contract.json')

    def test_arena_main_missing_report_is_structured_without_scoring(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-no-report-') as folder:
            root = Path(folder)
            verify.main(root)
            result = json.loads((root / 'parent-arena-acceptance.json').read_text())
            self.assertFalse(result['executionAccepted'])
            self.assertFalse(result['engineeringQualityAccepted'])
            self.assertFalse(result['countsAreExactTerminalEvidence'])
            self.assertEqual(result['completedConsistentRows'], 0)
            self.assertIsNone(result['actualDecodeCallsObserved'])

    def test_arena_main_failed_policy_collection_cannot_accept_quality(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-main-') as folder:
            root = Path(folder)
            (root / 'source/test-assets').mkdir(parents=True)
            for name, value in [('device-report.json', self.report), ('host-result.json', self.host),
                                ('package-verified.json', {'sourceFingerprint': 'a' * 64}),
                                ('source/test-assets/cases.json', self.manifest),
                                ('allocator-policy-collected.json', {'privateRoot': self.private_root, 'receiptMatches': True, 'fileBytesMatch': False})]:
                (root / name).write_text(json.dumps(value))
            verify.main(root)
            result = json.loads((root / 'parent-arena-acceptance.json').read_text())
            self.assertFalse(result['executionAccepted'])
            self.assertFalse(result['engineeringQualityAccepted'])
            self.assertFalse(result['checks']['policyFileCollectedExactly'])

    def test_arena_main_missing_policy_collection_is_structured(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-missing-policy-') as folder:
            root = Path(folder)
            (root / 'device-report.json').write_text(json.dumps(self.report))
            verify.main(root)
            result = json.loads((root / 'parent-arena-acceptance.json').read_text())
            self.assertFalse(result['executionAccepted'])
            self.assertFalse(result['engineeringQualityAccepted'])
            self.assertEqual(result['reason'], 'policy collection absent; resume same run collection')
            self.assertIsNone(result['actualDecodeCallsObserved'])

    def test_recovered_scoring_keeps_original_failure_bytes(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-score-recovery-') as folder:
            root = Path(folder)
            (root / 'source/test-assets').mkdir(parents=True)
            (root / 'device-report.json').write_text(json.dumps(self.report))
            verify.main(root)
            original = (root / 'parent-arena-acceptance.json').read_bytes()
            for name, value in [('host-result.json', self.host),
                                ('package-verified.json', {'sourceFingerprint': 'a' * 64}),
                                ('source/test-assets/cases.json', self.manifest),
                                ('allocator-policy-collected.json', {'privateRoot': self.private_root, 'receiptMatches': True, 'fileBytesMatch': True})]:
                (root / name).write_text(json.dumps(value))
            verify.main(root, output_name='parent-arena-acceptance-recovered.json')
            self.assertEqual((root / 'parent-arena-acceptance.json').read_bytes(), original)
            self.assertTrue(json.loads((root / 'parent-arena-acceptance-recovered.json').read_text())['engineeringQualityAccepted'])

    def test_run_identity_and_resource_gates_remain_distinct_and_fixed(self):
        self.assertEqual(device.APP, 'io.ondeviceai.acceptance.qwenarena')
        self.assertEqual(device.RUN, 'w3-qwen-arena-20261111-01')
        self.assertEqual(device.RUNNER, device.APP + '.test/io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation')
        self.assertIsNone(device.budget_reason(1610612736, 536870912, 120))
        self.assertEqual(device.budget_reason(1610612737, 536870912, 120), 'sampled_rss_above_1_5_gib')

    def test_policy_only_recovery_requires_prior_same_run_collection(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-policy-recovery-') as folder:
            with patch.object(device, 'OUT', Path(folder)), patch.object(device, 'adb') as remote:
                with self.assertRaises(FileNotFoundError):
                    device.verify_prior_collection()
                remote.assert_not_called()

    def test_policy_only_recovery_binds_report_and_installed_apks(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-bound-recovery-') as folder:
            root = Path(folder)
            report = copy.deepcopy(self.report)
            report.update(runId=device.RUN)
            raw = json.dumps(report).encode()
            digest = hashlib.sha256(raw).hexdigest()
            for name in ('device-report.json', 'device-report-confirm.json'):
                (root / name).write_bytes(raw)
            collection = {'reportPresent': True, 'twoReadsIdentical': True, 'sha256': digest,
                          'modelHashesAfterMatch': dict.fromkeys(('a','b','c','d','e','f'), True)}
            (root / 'collection.json').write_text(json.dumps(collection))
            package = {'sourceFingerprint': 'a' * 64, 'files': {
                'main.apk': {'sha256': 'b' * 64}, 'test.apk': {'sha256': 'c' * 64}}}
            (root / 'package-verified.json').write_text(json.dumps(package))
            replies = [b'package:/data/app/main/base.apk', ('b'*64+' file').encode(),
                       b'package:/data/app/test/base.apk', ('c'*64+' file').encode(),
                       (digest+' report').encode()]
            with patch.object(device, 'OUT', root), patch.object(device, 'adb', side_effect=replies):
                device.verify_prior_collection()
            collection['sha256'] = '0' * 64
            (root / 'collection.json').write_text(json.dumps(collection))
            with patch.object(device, 'OUT', root), patch.object(device, 'adb') as remote:
                with self.assertRaises(AssertionError):
                    device.verify_prior_collection()
                remote.assert_not_called()

    def test_policy_collection_is_bounded_and_rejects_extra_byte(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-collect-') as folder:
            root = Path(folder)
            (root / 'device-report.json').write_text(json.dumps(self.report))
            with patch.object(device, 'OUT', root), patch.object(device, 'adb') as remote:
                remote.side_effect = [self.private_root.encode(), verify.POLICY_BYTES + b'X']
                device.collect_policy()
                self.assertIn('head', remote.call_args.args)
                self.assertIn('21', remote.call_args.args)
            result = json.loads((root / 'allocator-policy-collected.json').read_text())
            self.assertFalse(result['fileBytesMatch'])
            self.assertFalse(result['nativePolicyEffectVerified'])

    def test_frozen_contract_cannot_omit_essential_hashes(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-freeze-') as folder:
            root = Path(folder)
            out = root / 'out'
            (out / 'source/test-assets').mkdir(parents=True)
            plan = json.loads((ROOT / 'artifacts/verification/w3-qwen-file-20261111/run-plan.json').read_text())
            plan.update(runId=device.RUN, sourceFingerprint='a' * 64, probeVariant='cpu-arena-off-v1', currentUser='0')
            (out / 'run-plan.json').write_text(json.dumps(plan))
            (out / 'package-verified.json').write_text(json.dumps({'sourceFingerprint': 'a' * 64}))
            (out / 'source/test-assets/cases.json').write_text(json.dumps(self.manifest))
            contract = {'runId': device.RUN, 'app': device.APP, 'runner': device.RUNNER,
                        'independentSourceReviewAccepted': True, 'independentParentReviewAccepted': True,
                        'nativePolicyEffectVerified': False, 'automaticRetry': False, 'files': {}}
            frozen = out / 'diagnostic-frozen.json'
            frozen.write_text(json.dumps(contract))
            with patch.object(device, 'ROOT', root), patch.object(device, 'OUT', out):
                with self.assertRaises(AssertionError):
                    device.verify_frozen_contract(frozen)

    def test_essential_hashes_cover_baseline_checks_and_new_sources(self):
        with tempfile.TemporaryDirectory(prefix='odai-arena-required-') as folder:
            root = Path(folder)
            out = root / 'out'
            (out / 'source/test-assets').mkdir(parents=True)
            plan = json.loads((ROOT / 'artifacts/verification/w3-qwen-file-20261111/run-plan.json').read_text())
            plan.update(runId=device.RUN, sourceFingerprint='a' * 64, probeVariant='cpu-arena-off-v1', currentUser='0')
            (out / 'run-plan.json').write_text(json.dumps(plan))
            (out / 'package-verified.json').write_text(json.dumps({'sourceFingerprint': 'a' * 64}))
            (out / 'source/test-assets/cases.json').write_text(json.dumps(self.manifest))
            base = ['out/' + n for n in ('main.apk', 'test.apk', 'package-verified.json', 'candidate-accepted.json', 'run-plan.json', 'production-before.json')]
            base += ['probes/android-qwen-arena/' + n for n in ('device_acceptance.py', 'verify_report.py', 'check_parent.py')]
            base += ['probes/android-qwen-file/' + n for n in ('device_acceptance.py', 'verify_report.py')]
            base += ['docs/validation/' + n for n in ('w3-qwen-arena-wrapper-review-20261111.json', 'w3-qwen-arena-parent-review-20261111.json', 'w3-qwen-arena-diagnostic-protocol-20261111.md')]
            extra = ['probes/android-qwen-file/check_parent.py',
                     'probes/android-qwen-arena/QwenArenaInstrumentation.kt',
                     'probes/android-qwen-arena/QwenArenaPolicy.kt',
                     'probes/android-qwen-file/QwenFileActivity.kt']
            files = {}
            for rel in base + extra:
                path = root / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                if not path.exists():
                    path.write_text('{}')
                files[rel] = hashlib.sha256(path.read_bytes()).hexdigest()
            contract = {'runId': device.RUN, 'app': device.APP, 'runner': device.RUNNER,
                        'independentSourceReviewAccepted': True, 'independentParentReviewAccepted': True,
                        'nativePolicyEffectVerified': False, 'automaticRetry': False, 'files': files}
            frozen = out / 'diagnostic-frozen.json'
            frozen.write_text(json.dumps(contract))
            with patch.object(device, 'ROOT', root), patch.object(device, 'OUT', out):
                device.verify_frozen_contract(frozen)
                for rel in extra:
                    with self.subTest(omitted=rel):
                        changed = copy.deepcopy(contract)
                        del changed['files'][rel]
                        frozen.write_text(json.dumps(changed))
                        with self.assertRaises(AssertionError):
                            device.verify_frozen_contract(frozen)


if __name__ == '__main__':
    unittest.main(verbosity=2)
