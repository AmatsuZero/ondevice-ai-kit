"""Offline identity, collection and scoring regression checks; no device calls."""
from pathlib import Path
import copy
import importlib.util
import json
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ParentChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.route = load('kv288_route_checks', Path(__file__).with_name('device_acceptance.py'))
        cls.verifier = load('kv288_score_checks', Path(__file__).with_name('verify_report.py'))

    def fixture(self):
        out = self.route.OLD
        report = json.loads((out / 'device-report.json').read_text())
        report['config']['maxTotalLen'] = 288
        report['probeVariant'] = 'cpu-arena-off-kv288-v1'
        return report, json.loads((out / 'host-result.json').read_text()), json.loads((out / 'source/test-assets/cases.json').read_text())

    def test_identity_exact_and_nine_negatives(self):
        actual = {'ro.product.model': 'Android SDK built for arm64', 'ro.build.version.sdk': '36', 'ro.product.cpu.abi': 'arm64-v8a'}
        values = dict(serial=self.route.SERIAL, actual=actual, qemu='1', console=self.route.AVD + '\r\nOK\r\n', user='0')
        self.route.validate_emulator(**values)
        negatives = [dict(serial='emulator-5580'), dict(qemu='0'), dict(user='10'), dict(console='Medium_Phone\nOK'),
                     dict(console=self.route.AVD + '\nKO'), dict(console=self.route.AVD + '\nOK\nextra')]
        for k, v in [('ro.product.model', 'TECNO KM9'), ('ro.build.version.sdk', '35'), ('ro.product.cpu.abi', 'x86_64')]:
            negatives.append(dict(actual=dict(actual, **{k: v})))
        for wrong in negatives:
            with self.subTest(wrong=wrong), self.assertRaises(AssertionError):
                self.route.validate_emulator(**dict(values, **wrong))

    def test_original_budget_and_65_batch_preserved(self):
        source = self.route.driver_source()
        ns = {'__file__': str(ROOT / 'probes/android-qwen-file/device_acceptance.py'), '__name__': 'offline_driver'}
        exec(compile(source, '<kv288-driver>', 'exec'), ns)
        self.assertIsNone(ns['budget_reason'](1610612736, 536870912, 120))
        for args in [(1610612737, 536870912, 120), (1, 536870911, 120), (1, 536870912, 120.01)]:
            self.assertIsNotNone(ns['budget_reason'](*args))
        for text in ("(OUT / name).open('x')", '120 * 66', "plan['maxNativeDecodeCalls'] == 130 and plan['caseCount'] == 65", "assert not exists(REPORT, app=True)"):
            self.assertIn(text, source)

    def test_scoring_requires_actual288_and_preserves_original_metrics(self):
        report, host, manifest = self.fixture()
        original = copy.deepcopy(report)
        private = report['allocatorPolicy']['configPath'].removesuffix('/cpu-arena-off/cpu.config')
        result = self.verifier.evaluate(report, host, manifest, report['sourceFingerprint'], private)
        self.assertTrue(result['executionAccepted'])
        self.assertTrue(result['engineeringQualityAccepted'])
        self.assertEqual(report, original)
        for value in (512, 256, '288', None):
            bad = copy.deepcopy(report); bad['config']['maxTotalLen'] = value
            with self.subTest(capacity=value):
                self.assertFalse(self.verifier.evaluate(bad, host, manifest, report['sourceFingerprint'], private)['executionAccepted'])
        bad = copy.deepcopy(report); bad['probeVariant'] = 'cpu-arena-off-v1'
        self.assertFalse(self.verifier.evaluate(bad, host, manifest, report['sourceFingerprint'], private)['executionAccepted'])

    def test_policy_collector_accepts_actual_variant_and_bounded_same_inode_reads(self):
        report, _, _ = self.fixture()
        app = self.route.APP
        private = '/data/user/0/' + app + '/no_backup'
        policy = report['allocatorPolicy']
        policy['configPath'] = private + '/cpu-arena-off/cpu.config'
        policy['provider'] = 'cpu:' + policy['configPath']
        report['config']['provider'] = policy['provider']
        calls = []
        def adb(*args):
            calls.append(args)
            if args[:5] == ('shell', 'run-as', app, 'stat', '-c'):
                return b'10000 1 3 20\n' if args[-1].endswith('cpu.config') else b'10000 1 2 4096\n'
            if args[:4] == ('shell', 'run-as', app, 'test'):
                return b''
            if args == ('exec-out', 'run-as', app, 'head', '-c', '21', 'no_backup/cpu-arena-off/cpu.config'):
                return b'EnableCpuMemArena=0\n'
            raise AssertionError(args)
        path = ROOT / 'probes/android-qwen-heap/device_acceptance.py'
        state = {'__file__': str(path), '__name__': 'offline_collector'}
        exec(compile(self.route.policy_collector_source(), '<kv288-collector>', 'exec'), state)
        with tempfile.TemporaryDirectory(prefix='odai-kv288-policy-', dir='/private/tmp') as tmp:
            state.update(OUT=Path(tmp), APP=app, RUN=self.route.RUN, adb=adb,
                         prior_collection=lambda: (json.dumps(report).encode(), report))
            result = state['collect_policy']()
            self.assertTrue(result['receiptMatches'])
            self.assertTrue(result['fileBytesMatch'])
            self.assertEqual(result['boundedReadBytes'], 20)
            self.assertEqual(result['readLimitBytes'], 21)
            self.assertEqual(len([c for c in calls if c[0] == 'exec-out']), 2)

    def test_shell_arguments_are_quoted_and_serial_is_fixed(self):
        done = subprocess.CompletedProcess([], 0, stdout=b'ok', stderr=b'')
        with patch.object(self.route.subprocess, 'run', return_value=done) as run:
            self.route.emulator_adb('shell', 'run-as', self.route.APP, 'stat', '-c', '%u %d %i %s', 'no_backup')
        args = run.call_args.args[0]
        self.assertEqual(args[args.index('-s') + 1], self.route.SERIAL)
        self.assertEqual(args[-2], 'shell')
        self.assertIn("'%u %d %i %s'", args[-1])

    def test_missing_contract_blocks_every_action_before_adb(self):
        with tempfile.TemporaryDirectory(prefix='odai-kv288-no-contract-', dir='/private/tmp') as tmp:
            with patch.object(self.route, 'OUT', Path(tmp)), patch.object(self.route, 'emulator_adb') as adb:
                for action in ('preflight', 'prepare', 'run', 'collect', 'collect-policy', 'restore'):
                    with self.subTest(action=action), self.assertRaises(FileNotFoundError):
                        self.route.main(action)
                adb.assert_not_called()

    def test_frozen_contract_missing_essential_hashes_rejected_before_adb(self):
        frozen = self.route.OUT / 'diagnostic-frozen.json'
        if not frozen.exists():
            self.skipTest('full freeze does not yet exist; rerun before device preparation')
        contract = self.route.verify_frozen_contract()
        read_text = Path.read_text
        essential = ['probes/android-qwen-kv288/QwenKv288Instrumentation.kt',
                     'probes/android-qwen-kv288/check_parent.py',
                     str((self.route.OUT / 'source/test-assets/cases.json').relative_to(ROOT)),
                     str((self.route.OUT / 'parent-review.json').relative_to(ROOT))]
        for missing in essential + [None]:
            bad = copy.deepcopy(contract)
            if missing is None:
                bad['files'] = {}
            else:
                del bad['files'][missing]
            def read(path, *args, **kwargs):
                return json.dumps(bad) if path == frozen else read_text(path, *args, **kwargs)
            with self.subTest(missing=missing), patch.object(Path, 'read_text', read), patch.object(self.route, 'emulator_adb') as adb:
                with self.assertRaisesRegex(AssertionError, 'required frozen artifact hashes absent'):
                    self.route.main('prepare')
                adb.assert_not_called()


if __name__ == '__main__':
    unittest.main()
