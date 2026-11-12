"""Primary-owned local coordinator acceptance; no real process/device sampling."""
import os
import unittest
from pathlib import Path
from unittest import mock

import terminal_file_io as fio
import test_terminal_observation_coordinator as fixture


class IntSubclass(int):
    pass


class StrSubclass(str):
    pass


class CoordinatorBoundaries(unittest.TestCase):
    def fresh(self):
        harness = fixture.TestCoordinator()
        harness.setUp()
        self.addCleanup(harness.tearDown)
        return harness

    def test_each_sample_strict_types_and_resource_limits(self):
        bad_fields = [
            ('pid', True), ('pid', IntSubclass(fixture.PID)),
            ('startTicks', 1000000000000),
            ('startTicks', '01000000000000'),
            ('startTicks', StrSubclass(fixture.START_TICKS)),
            ('rssBytes', True), ('rssBytes', float(fixture.RSS)),
            ('rssBytes', IntSubclass(fixture.RSS)),
            ('rssBytes', 1610612737),
            ('deviceAvailableBytes', True),
            ('deviceAvailableBytes', 536870911),
            ('deviceAvailableBytes', 9223372036854775808),
        ]
        for stage in (0, 1):
            for key, value in bad_fields:
                with self.subTest(stage=stage, key=key, value=value):
                    harness = self.fresh()
                    samples = [dict(fixture.SAMPLE), dict(fixture.SAMPLE)]
                    samples[stage][key] = value
                    iterator = iter(samples)
                    with self.assertRaises(ValueError):
                        harness._run(sample_fn=lambda: next(iterator))
                    self.assertEqual(harness._last['write_calls'], [])
                    self.assertFalse(harness._last['ack'].get('called', False))
                    harness.assert_no_outputs()

    def test_before_sample_can_dominate_resource_aggregation(self):
        harness = self.fresh()
        before = dict(fixture.SAMPLE, rssBytes=2097152, deviceAvailableBytes=536870912)
        after = dict(fixture.SAMPLE)
        samples = iter([before, after])
        result = harness._run(sample_fn=lambda: next(samples))['result']
        self.assertEqual(result['rssBytes'], before['rssBytes'])
        self.assertEqual(result['deviceAvailableBytes'], before['deviceAvailableBytes'])

    def test_clock_types_and_bounds_at_each_position(self):
        for index in range(4):
            for bad in (True, 100.0, IntSubclass(100), -1, 9223372036854775808):
                with self.subTest(index=index, bad=bad):
                    harness = self.fresh()
                    clocks = [100, 200, 300, 400]
                    clocks[index] = bad
                    with self.assertRaises(ValueError):
                        harness._run(clock_seq=clocks)
                    self.assertEqual(harness._last['write_calls'], [])
                    harness.assert_no_outputs()

    def test_sample_failure_is_preserved_without_retry(self):
        for stage in (0, 1):
            with self.subTest(stage=stage):
                harness = self.fresh()
                fault = RuntimeError('fixture-sample-failure')
                calls = []

                def sample():
                    index = len(calls)
                    calls.append(index)
                    if index == stage:
                        raise fault
                    return dict(fixture.SAMPLE)

                with self.assertRaises(RuntimeError) as caught:
                    harness._run(sample_fn=sample)
                self.assertIs(caught.exception, fault)
                self.assertEqual(len(calls), stage + 1)
                self.assertEqual(harness._last['write_calls'], [])
                harness.assert_no_outputs()

    def test_stale_dangling_ack_symlink_prevents_all_output_writes(self):
        harness = self.fresh()
        target = Path(harness.root) / 'absent-target'
        ack = Path(harness.root) / harness.ack_rel
        ack.symlink_to(target)
        with self.assertRaises((ValueError, OSError)):
            harness._run()
        self.assertEqual(harness._last['write_calls'], [])
        self.assertTrue(ack.is_symlink())
        self.assertEqual(os.readlink(ack), str(target))
        self.assertFalse((Path(harness.root) / harness.obs_rel).exists())
        self.assertFalse((Path(harness.root) / harness.receipt_rel).exists())

    def test_path_aliases_are_rejected_before_writes(self):
        for attr, value in [('obs_rel', 'report.json'),
                            ('receipt_rel', 'observed.json'),
                            ('ack_rel', '../escape'),
                            ('ack_rel', 'nested//ack'),
                            ('ack_rel', StrSubclass('ack-copy'))]:
            with self.subTest(attr=attr, value=value):
                harness = self.fresh()
                setattr(harness, attr, value)
                with self.assertRaises(ValueError):
                    harness._run()
                self.assertEqual(harness._last['write_calls'], [])
                self.assertEqual((Path(harness.root) / harness.report_rel).read_bytes(),
                                 harness.report_bytes)

    def test_report_reads_use_the_declared_bound(self):
        harness = self.fresh()
        real_read = fio.read_stable
        bounds = []

        def read(root, relative, bound):
            bounds.append(bound)
            return real_read(root, relative, bound)

        with mock.patch.object(fio, 'read_stable', side_effect=read):
            harness._run()
        self.assertEqual(bounds, [1048576, 1048576])

    def test_clock_regression_stops_at_the_first_invalid_step(self):
        for clocks, expected_clocks, expected_reads in [
                ([100, 50, 300, 400], 2, 1),
                ([100, 200, 150, 400], 3, 2)]:
            with self.subTest(clocks=clocks):
                harness = self.fresh()
                with self.assertRaises(ValueError):
                    harness._run(clock_seq=clocks)
                self.assertEqual(len(harness._last['clk_calls']), expected_clocks)
                self.assertEqual(harness._last['read'].calls, expected_reads)
                self.assertEqual(len(harness._last['sample_calls']), 1)
                self.assertEqual(harness._last['write_calls'], [])

    def test_root_alias_is_rejected_before_callbacks(self):
        harness = self.fresh()
        alias = Path(harness.root) / 'root-alias'
        alias.symlink_to(harness.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            harness._run(root=str(alias), prepare_report=False)
        self.assertEqual(harness._last['clk_calls'], [])
        self.assertEqual(harness._last['sample_calls'], [])
        self.assertEqual(harness._last['write_calls'], [])

    def test_all_output_parents_preflight_before_callbacks_or_writes(self):
        for parent_kind in ('symlink', 'missing'):
            with self.subTest(parent=parent_kind):
                harness = self.fresh()
                if parent_kind == 'symlink':
                    real = Path(harness.root) / 'real'
                    real.mkdir()
                    (Path(harness.root) / 'alias').symlink_to(real, target_is_directory=True)
                    harness.ack_rel = 'alias/ack'
                else:
                    harness.ack_rel = 'missing/ack'
                with self.assertRaises((ValueError, OSError)):
                    harness._run()
                self.assertEqual(harness._last['write_calls'], [])
                self.assertEqual(harness._last['sample_calls'], [])
                self.assertEqual(harness._last['clk_calls'], [])
                self.assertFalse((Path(harness.root) / harness.obs_rel).exists())
                self.assertFalse((Path(harness.root) / harness.receipt_rel).exists())

    def test_receipt_rejects_nonfinite_extra_values(self):
        for location in ('binding', 'sample'):
            with self.subTest(location=location):
                harness = self.fresh()
                binding = fixture.build_binding(harness.report_bytes)
                sample = dict(fixture.SAMPLE)
                if location == 'binding':
                    binding['extra'] = float('nan')
                else:
                    sample['extra'] = float('nan')
                with self.assertRaises(ValueError):
                    harness._run(binding=binding, sample_fn=lambda: dict(sample))
                self.assertEqual(harness._last['write_calls'], [])
                harness.assert_no_outputs()

    def test_invalid_binding_type_is_a_contract_error(self):
        harness = self.fresh()
        with self.assertRaises(ValueError):
            harness._run(binding=[])
        self.assertEqual(harness._last['write_calls'], [])

    def test_missing_binding_identity_is_rejected_before_ack_or_writes(self):
        for key in ('pid', 'startTicks'):
            with self.subTest(missing=key):
                harness = self.fresh()
                binding = fixture.build_binding(harness.report_bytes)
                del binding[key]
                with self.assertRaises(ValueError):
                    harness._run(binding=binding)
                self.assertFalse(harness._last['ack'].get('called', False))
                self.assertEqual(harness._last['write_calls'], [])
                harness.assert_no_outputs()


if __name__ == '__main__':
    unittest.main()
