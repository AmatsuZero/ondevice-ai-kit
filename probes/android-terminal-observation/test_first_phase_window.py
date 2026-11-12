"""First-loop progress deadline with actual local ADB adapters."""
import unittest
import test_terminal_poll_boundaries as previous
import test_remote_observation_parent as fixtures


class FirstPhaseWindow(unittest.TestCase):
    report = previous.TerminalPollBoundaries.report
    observe = previous.TerminalPollBoundaries.observe

    def fixture(self):
        f = fixtures.RemoteObservationParent()
        f.setUp()
        self.addCleanup(f.tearDown)
        self.report(f)
        return f

    def config(self, f, stage, elapsed):
        state = {'late': False}
        def poll():
            if stage == 'poll': state['late'] = True
            return None
        def clock():
            count = f.root/'stat-count'
            if stage == 'sample' and count.exists() and int(count.read_text()) >= 2:
                state['late'] = True
            if stage == 'report' and len(f.calls()) >= 5:
                state['late'] = True
            return elapsed if state['late'] else 0
        return dict(process_poll=poll, monotonic_ns=clock)

    def test_first_callback_cannot_exceed_phase_budget_before_phase_known(self):
        for stage, count in (('poll', 0), ('sample', 4), ('report', 5)):
            with self.subTest(stage=stage):
                f = self.fixture()
                with self.assertRaises(ValueError):
                    self.observe(f, **self.config(f, stage, 121_000_000_000))
                self.assertEqual(len(f.calls()), count)
                self.assertFalse(f.stage.exists())
                self.assertFalse(f.final.exists())
                self.assertFalse((f.output/'poll-sample-000000.json').exists())

    def test_first_phase_exact_120_seconds_remains_inclusive(self):
        for stage in ('poll', 'sample', 'report'):
            with self.subTest(stage=stage):
                f = self.fixture()
                result = self.observe(f, **self.config(f, stage, 120_000_000_000))
                self.assertEqual(result['pollSamples'], 1)
                self.assertFalse(result['qualityAccepted'])
                self.assertTrue(f.final.is_file())


if __name__ == '__main__':
    unittest.main(verbosity=2)
