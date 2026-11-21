"""Parent acceptance for the facade: real local fake-ADB plus injected faults."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
QT = ROOT / 'probes/android-qwen-terminal'
sys.path[:0] = [str(QT), str(ROOT / 'probes/android-terminal-observation')]
from terminal_prepare_fixture import PrepareFixture, RUN, FP
import terminal_device_prepare

TARGET = Path(os.environ.get('ODAI_TERMINAL_DRIVER', QT / 'device_acceptance.py'))


class TerminalDispatchParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing complete terminal device driver')
        spec = importlib.util.spec_from_file_location('dispatch_driver_under_test', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def fixture(self, prepared=False):
        f = PrepareFixture(self)
        source = f.adb.read_text().replace(
            "def mapped(s,app=None):",
            "def mapped(s,app=None):\n if s in ('/data','/data/local'):return device\n if s=='/data/local/tmp':return device/'stage'")
        f.adb.write_text(source)
        if prepared:
            f.call(terminal_device_prepare)
        return f

    def marker(self, f, **changes):
        row = dict(runId=RUN, sourceFingerprint=FP, startedNs=1,
                   automaticRetry=False, plannedConstructors=1,
                   maxPlannedStreams=2, maxPlannedDecode=2,
                   plannedMicrophone=0, inputCount=1)
        row.update(changes)
        (f.out / 'run-started.json').write_text(json.dumps(row))

    def test_cleanup_success_and_each_uncertain_outcome_are_single_use(self):
        for result in ({'allRestored': True}, {'allRestored': False},
                       {'allRestored': 1}, None, KeyboardInterrupt('restore interrupted')):
            with self.subTest(result=result):
                f = self.fixture()
                with patch.object(self.module._restore, 'restore') as restore:
                    if isinstance(result, BaseException):
                        restore.side_effect = result
                    else:
                        restore.return_value = result
                    cleanup = self.module._cleanup_once(f.out, f.context, serial='serial-01')
                    if isinstance(result, dict) and result['allRestored'] is True:
                        cleanup()
                    else:
                        with self.assertRaises(BaseException) as caught:
                            cleanup()
                        if isinstance(result, BaseException):
                            self.assertIs(caught.exception, result)
                    with self.assertRaises((ValueError, RuntimeError)):
                        cleanup()
                    restore.assert_called_once_with(f.out, f.context, serial='serial-01')

    def test_five_nonrun_actions_use_real_context_and_exact_module(self):
        f = self.fixture()
        routes = {'preflight': (self.module, '_preflight_context'),
                  'prepare': (self.module._prepare, 'prepare'),
                  'collect': (self.module._collect, 'collect'),
                  'collect-policy': (self.module._policy, 'collect_policy'),
                  'restore': (self.module._restore, 'restore')}
        with patch.object(self.module, 'OUT', f.out):
            for action, (owner, name) in routes.items():
                with self.subTest(action=action), patch.object(owner, name, return_value=action) as target:
                    self.assertEqual(self.module._dispatch_context(action, f.context), action)
                    args, kwargs = target.call_args
                    self.assertEqual(args, (f.out, f.context))
                    self.assertEqual(kwargs['serial'], 'serial-01')
                    if action == 'prepare':
                        self.assertTrue(callable(kwargs['cleanup']))
                    else:
                        self.assertEqual(kwargs, {'serial': 'serial-01'})

    def test_run_passes_real_callbacks_and_keeps_stdin_capable_default(self):
        f = self.fixture(True)
        observed = []
        def run(root, **kw):
            self.assertEqual(root, f.out)
            self.assertEqual(kw['adb'], str(f.adb))
            self.assertEqual(kw['serial'], 'serial-01')
            self.assertEqual(kw['source_fingerprint'], FP)
            self.assertIsNone(kw.get('runner'))
            kw['verify_launch']()
            kw['before_transport']()
            kw['stop_alias']()
            observed.append('run')
            return {'sentinel': 'actual result'}
        with patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module, 'adb', return_value=str(f.adb)), \
             patch.object(self.module, 'verify_frozen_contract') as guard, \
             patch.object(self.module._lifecycle, 'run_diagnostic', side_effect=run), \
             patch.object(self.module._collect, 'collect') as collect, \
             patch.object(self.module._restore, 'restore') as restore:
            self.assertEqual(self.module._dispatch_context('run', f.context), {'sentinel': 'actual result'})
        self.assertEqual(observed, ['run'])
        guard.assert_called_once_with()
        collect.assert_not_called(); restore.assert_not_called()
        self.assertEqual(f.mutations()[-1]['label'], 'force-stop')

    def test_failure_before_consumption_cannot_collect_or_restore(self):
        f = self.fixture(True); primary = ValueError('locked before consumption')
        with patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module._lifecycle, 'run_diagnostic', side_effect=primary), \
             patch.object(self.module._collect, 'collect') as collect, \
             patch.object(self.module._policy, 'collect_policy') as policy, \
             patch.object(self.module._restore, 'restore') as restore:
            with self.assertRaises(ValueError) as caught:
                self.module._dispatch_context('run', f.context)
        self.assertIs(caught.exception, primary)
        collect.assert_not_called(); policy.assert_not_called(); restore.assert_not_called()

    def test_marker_proof_interrupt_cannot_replace_primary_or_trigger_cleanup(self):
        f = self.fixture(True); primary = ValueError('original run failure')
        with patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module._lifecycle, 'run_diagnostic', side_effect=primary), \
             patch.object(self.module._restore, '_read_json', side_effect=KeyboardInterrupt('secondary marker interrupt')), \
             patch.object(self.module._restore, 'restore') as restore:
            try:
                self.module._dispatch_context('run', f.context)
            except BaseException as caught:
                self.assertIs(caught, primary)
            else:
                self.fail('primary failure disappeared')
        restore.assert_not_called()
        self.assertTrue(any('secondary marker interrupt' in n for n in primary.__notes__))

    def test_stale_consumed_marker_never_enters_run_or_cleanup(self):
        f = self.fixture(True); self.marker(f)
        with patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module._lifecycle, 'run_diagnostic') as run, \
             patch.object(self.module._collect, 'collect') as collect, \
             patch.object(self.module._restore, 'restore') as restore:
            with self.assertRaises((ValueError, OSError)):
                self.module._dispatch_context('run', f.context)
        run.assert_not_called(); collect.assert_not_called(); restore.assert_not_called()

    def test_consumed_failure_collects_then_policy_then_restores_once(self):
        f = self.fixture(True); primary = KeyboardInterrupt('native run interrupted'); order = []
        def run(*a, **kw):
            self.marker(f); raise primary
        def collect(*a, **kw):
            order.append('collect'); return {'terminalAccepted': True}
        def policy(*a, **kw):
            order.append('policy'); raise OSError('policy read interrupted')
        def restore(*a, **kw):
            order.append('restore'); return {'allRestored': True}
        with patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module._lifecycle, 'run_diagnostic', side_effect=run), \
             patch.object(self.module._collect, 'collect', side_effect=collect) as c, \
             patch.object(self.module._policy, 'collect_policy', side_effect=policy) as p, \
             patch.object(self.module._restore, 'restore', side_effect=restore) as r:
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.module._dispatch_context('run', f.context)
        self.assertIs(caught.exception, primary)
        self.assertEqual(order, ['collect', 'policy', 'restore'])
        for mock in (c,p,r): mock.assert_called_once()
        self.assertTrue(any('policy read interrupted' in n for n in primary.__notes__))

    def test_collection_and_cleanup_failures_keep_primary_and_both_notes(self):
        f = self.fixture(True); primary = ValueError('original failure')
        def run(*a, **kw): self.marker(f); raise primary
        with patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module._lifecycle, 'run_diagnostic', side_effect=run), \
             patch.object(self.module._collect, 'collect', side_effect=RuntimeError('collection failed')) as c, \
             patch.object(self.module._policy, 'collect_policy', side_effect=ValueError('no valid collection')) as p, \
             patch.object(self.module._restore, 'restore', return_value={'allRestored': False}) as r:
            with self.assertRaises(ValueError) as caught:
                self.module._dispatch_context('run', f.context)
        self.assertIs(caught.exception, primary)
        c.assert_called_once(); r.assert_called_once()
        self.assertLessEqual(p.call_count, 1)
        self.assertTrue(any('collection failed' in n for n in primary.__notes__))
        self.assertTrue(any('restor' in n.lower() for n in primary.__notes__))

    def test_invalid_new_marker_preserves_failure_without_cleanup(self):
        for changes in ({'runId':'foreign'}, {'plannedConstructors':True},
                        {'startedNs':False}, {'automaticRetry':0}):
            with self.subTest(changes=changes):
                f = self.fixture(True); primary = ValueError('run failed')
                def run(*a, **kw): self.marker(f, **changes); raise primary
                with patch.object(self.module, 'OUT', f.out), \
                     patch.object(self.module._lifecycle, 'run_diagnostic', side_effect=run), \
                     patch.object(self.module._collect, 'collect') as c, \
                     patch.object(self.module._restore, 'restore') as r:
                    with self.assertRaises(ValueError) as caught:
                        self.module._dispatch_context('run', f.context)
                self.assertIs(caught.exception, primary); c.assert_not_called(); r.assert_not_called()

    def test_consumption_proof_requires_exact_integer_budgets_and_int64_clock(self):
        for changes in ({'plannedConstructors':True}, {'maxPlannedStreams':2.0},
                        {'maxPlannedDecode':2.0}, {'plannedMicrophone':False},
                        {'inputCount':True}, {'startedNs':2**63}):
            with self.subTest(changes=changes):
                f = self.fixture(); self.marker(f, **changes)
                with patch.object(self.module, 'OUT', f.out):
                    consumed, _ = self.module._run_consumed_fresh_marker()
                self.assertIs(consumed, False)

    def test_actual_main_injects_module_helpers_and_guards_every_call(self):
        f = self.fixture(); before = len((f.root/'calls.jsonl').read_text().splitlines())
        home = f.root / 'home'
        with patch.object(self.module, 'ROOT', f.root), \
             patch.object(self.module, 'BASE', ROOT/'probes/android-qwen-file/device_acceptance.py'), \
             patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module, 'EVIDENCE', f.evidence), \
             patch.object(Path, 'home', return_value=home), \
             patch.object(self.module, 'verify_frozen_contract') as guard:
            result = self.module.main('preflight')
        calls = len((f.root/'calls.jsonl').read_text().splitlines()) - before
        self.assertEqual(result['availableBytes'], 3*1024**3)
        self.assertEqual(guard.call_count, 1+calls)
        self.assertEqual(f.mutations(), [])

    def test_actual_main_preserves_adb_binding_for_collectors(self):
        f = self.fixture(); home = f.root/'home'
        with patch.object(self.module, 'ROOT', f.root), \
             patch.object(self.module, 'BASE', ROOT/'probes/android-qwen-file/device_acceptance.py'), \
             patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module, 'EVIDENCE', f.evidence), \
             patch.object(Path, 'home', return_value=home), \
             patch.object(self.module, 'verify_frozen_contract'), \
             patch.object(self.module, '_dispatch_context', side_effect=lambda action, ctx: ctx):
            ctx = self.module.main('collect')
        self.assertIn('adb', ctx)
        self.assertEqual(ctx['adb'], ['rtk', 'proxy', str(home/'Library/Android/sdk/platform-tools/adb'), '-s', 'serial-01'])
        self.assertTrue(callable(ctx['digest']))
        self.assertTrue(callable(ctx['parse_kb']))

    def test_actual_main_prepare_runs_accepted_module_and_persists_proof(self):
        f = self.fixture(); home = f.root/'home'
        with patch.object(self.module, 'ROOT', f.root), \
             patch.object(self.module, 'BASE', ROOT/'probes/android-qwen-file/device_acceptance.py'), \
             patch.object(self.module, 'OUT', f.out), \
             patch.object(self.module, 'EVIDENCE', f.evidence), \
             patch.object(Path, 'home', return_value=home), \
             patch.object(self.module, 'verify_frozen_contract'):
            row = self.module.main('prepare')
        self.assertEqual(row, json.loads((f.out/'prepared.json').read_text()))
        self.assertIs(row['ordinaryUnchanged'], True)
        self.assertTrue((f.out/'ownership-install-main-claimed.json').is_file())
        self.assertFalse((f.out/'run-started.json').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
