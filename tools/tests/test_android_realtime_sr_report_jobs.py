import unittest
from tools.tests.test_android_realtime_sr_report import (assess, make_index, make_run, base_events, dropped_stage, ORIGINAL_ORD, LATE_ORD, UNCONFIRMED_ORD)


class ReportJobCoverageTests(unittest.TestCase):
    def test_gpu_unverified_when_timer_end_is_missing(self):
        events = dropped_stage(base_events(), "srEnd", {0})
        result = assess(make_index(), make_run(), events)
        self.assertEqual(result["gates"]["gpu"], "unverified")

    def test_slow_gpu_fallback_jobs_fail_gpu_gate_but_count_timely_display(self):
        events = base_events()
        slow_ordinals = set(range(6))
        for e in events:
            if e["stage"] == "swap" and e["sourceOrdinal"] in slow_ordinals:
                e["submittedBackend"] = "ORIGINAL"
                e["reason"] = "fallback"
            elif e["stage"] == "srEnd" and e["sourceOrdinal"] in slow_ordinals:
                e["gpuElapsedNs"] = 26_000_000
        result = assess(make_index(), make_run(), events)
        self.assertEqual(result["gates"]["gpu"], "fail")
        self.assertEqual(result["counts"]["onTimeOriginal"], 8)

    def test_gpu_threshold_is_inclusive_at_25ms_and_fails_above_it(self):
        events = base_events()
        for e in events:
            if e["stage"] == "srEnd":
                e["gpuElapsedNs"] = 25_000_000
        result = assess(make_index(), make_run(), events)
        self.assertEqual(result["gates"]["gpu"], "pass")

        events = base_events()
        changed = 0
        for e in events:
            if e["stage"] == "srEnd" and changed < 6:
                e["gpuElapsedNs"] = 25_000_001
                changed += 1
        self.assertEqual(changed, 6)
        result = assess(make_index(), make_run(), events)
        self.assertEqual(result["gates"]["gpu"], "fail")

    def test_original_mode_still_measures_end_to_end_and_missed(self):
        events = dropped_stage(dropped_stage(base_events(), "srStart"), "srEnd")
        for e in events:
            if e["stage"] == "swap":
                e["submittedBackend"] = "ORIGINAL"
                e["reason"] = "fallback"
        result = assess(make_index(), make_run(mode="ORIGINAL"), events)
        self.assertEqual(result["gates"]["gpu"], "notApplicable")
        self.assertEqual(result["gates"]["srDelivery"], "notApplicable")
        self.assertEqual(result["gates"]["endToEnd"], "pass")
        self.assertEqual(result["gates"]["missed"], "pass")
