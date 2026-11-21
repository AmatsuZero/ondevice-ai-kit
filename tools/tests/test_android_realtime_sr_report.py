import unittest

from tools.probes.android_realtime_sr_report import assess


FIXTURE_SHA = "0123456789abcdef" * 4  # 64 lowercase hex chars
TARGET_STEP_NS = 40_000_000           # exactly 40 ms synthetic slot (independent of 30fps)
BASE_TARGET_NS = 1_000_000_000        # positive targetNs
DISPLAY_OFFSET_NS = 1_000_000         # presented just after target, still inside the slot

ORIGINAL_ORD = (10, 11)      # 2 on-time ORIGINAL fallbacks
LATE_ORD = (20,)             # 1 late display
UNCONFIRMED_ORD = (30, 31)   # 2 frames with no present evidence


def target_ns(ordinal):
    return BASE_TARGET_NS + ordinal * TARGET_STEP_NS


def make_index(n=100):
    frames = []
    prev = -1
    for ordinal in range(n):
        if ordinal == 8:
            pts = prev            # PTS[8] == PTS[7]
        else:
            prev += 1
            pts = prev
        frames.append({
            "sourceOrdinal": ordinal,
            "pts": pts,
            "timeBase": "1/30",   # preserve source denominator
        })
    return {"fixtureSha256": FIXTURE_SHA, "frames": frames}


def make_run(**overrides):
    run = {
        "schemaVersion": 1,
        "runId": "r",
        "fixtureSha256": FIXTURE_SHA,
        "generation": 1,
        "loops": 1,
        "mode": "CUNNY",
        "displayTimingSupported": True,
        "completed": True,
    }
    run.update(overrides)
    return run


def _base(ordinal, stage, run_id="r", generation=1, loop=0):
    return {
        "runId": run_id,
        "generation": generation,
        "loop": loop,
        "sourceOrdinal": ordinal,
        "stage": stage,
    }


def latch(ordinal, target=None):
    e = _base(ordinal, "latch")
    t = target_ns(ordinal) if target is None else target
    e.update({
        "latchBeginNs": t - 5_000_000,
        "targetNs": t,
        "nextTargetNs": t + TARGET_STEP_NS,
    })
    return e


def gpu(ordinal, stage, elapsed=2_000_000, valid=True):
    e = _base(ordinal, stage)
    e.update({"gpuElapsedNs": elapsed, "gpuTimingValid": valid})
    return e


def swap(ordinal, backend="CUNNY", reason="sr"):
    e = _base(ordinal, "swap")
    e.update({"eglFrameId": ordinal,
              "submittedBackend": backend,
              "reason": reason})
    return e


def display(ordinal, display_ns=None):
    e = _base(ordinal, "display")
    e["eglFrameId"] = ordinal
    e["displayNs"] = (target_ns(ordinal) + DISPLAY_OFFSET_NS
                      if display_ns is None else display_ns)
    return e


def base_events():
    """One coherent run: 95 onTimeSR / 2 onTimeOriginal / 1 late / 2 unconfirmed."""
    events = []
    for ordinal in range(100):
        events.append(latch(ordinal))
        events.append(gpu(ordinal, "srStart"))
        events.append(gpu(ordinal, "srEnd"))
        if ordinal in ORIGINAL_ORD:
            events.append(swap(ordinal, "ORIGINAL", "fallback"))
        else:
            events.append(swap(ordinal, "CUNNY", "sr"))
        if ordinal in UNCONFIRMED_ORD:
            continue  # no present evidence for this frame
        if ordinal in LATE_ORD:
            events.append(display(ordinal, target_ns(ordinal) + 3 * TARGET_STEP_NS))
        else:
            events.append(display(ordinal))
    return events


def dropped_stage(events, stage, ordinals=None):
    """Drop `stage` events (optionally only for the given ordinals)."""
    out = []
    for e in events:
        if e["stage"] == stage and (ordinals is None or e["sourceOrdinal"] in ordinals):
            continue
        out.append(e)
    return out


class SpatialAssessContractTests(unittest.TestCase):

    def test_spatial_frame_with_matching_display_counts_as_on_time_sr(self):
        events = [
            latch(0),
            gpu(0, "srStart"),
            gpu(0, "srEnd"),
            swap(0, "SPATIAL", "sr"),
            display(0),
        ]
        result = assess(make_index(1), make_run(mode="SPATIAL"), events)
        self.assertEqual(result["counts"]["onTimeSR"], 1)
        self.assertEqual(result["counts"]["unconfirmed"], 0)

    def test_spatial_frame_without_display_is_unconfirmed(self):
        events = [
            latch(0),
            gpu(0, "srStart"),
            gpu(0, "srEnd"),
            swap(0, "SPATIAL", "sr"),
        ]
        result = assess(make_index(1), make_run(mode="SPATIAL"), events)
        self.assertEqual(result["counts"]["onTimeSR"], 0)
        self.assertEqual(result["counts"]["unconfirmed"], 1)

    def test_successful_original_fallback_counts_as_on_time_original(self):
        events = [
            latch(0),
            gpu(0, "srStart"),
            gpu(0, "srEnd"),
            swap(0, "ORIGINAL", "fallback"),
            display(0),
        ]
        result = assess(make_index(1), make_run(mode="SPATIAL"), events)
        self.assertEqual(result["counts"]["onTimeOriginal"], 1)

    def test_rejects_backend_that_does_not_match_run_mode(self):
        with self.assertRaises(ValueError):
            assess(
                make_index(1),
                make_run(mode="CUNNY"),
                [latch(0), swap(0, "SPATIAL", "sr"), display(0)],
            )
        with self.assertRaises(ValueError):
            assess(
                make_index(1),
                make_run(mode="SPATIAL"),
                [latch(0), swap(0, "CUNNY", "sr"), display(0)],
            )


class AssessContractTests(unittest.TestCase):

    def test_result_exposes_counts_and_gates(self):
        result = assess(make_index(), make_run(), base_events())
        self.assertIn("counts", result)
        self.assertIn("gates", result)
        for key in ("srDelivery", "missed", "endToEnd", "gpu"):
            self.assertIn(key, result["gates"])

    def test_counts_match_95_2_1_2(self):
        result = assess(make_index(), make_run(), base_events())
        counts = result["counts"]
        self.assertEqual(counts["onTimeSR"], 95)
        self.assertEqual(counts["onTimeOriginal"], 2)
        self.assertEqual(counts["late"], 1)
        self.assertEqual(counts["unconfirmed"], 2)

    def test_sr_delivery_gate_fails_at_exactly_95_percent(self):
        result = assess(make_index(), make_run(), base_events())
        self.assertEqual(result["gates"]["srDelivery"], "fail")

    def test_end_to_end_unverified_without_present_evidence(self):
        events = dropped_stage(base_events(), "display")
        result = assess(make_index(), make_run(), events)
        self.assertEqual(result["gates"]["endToEnd"], "unverified")

    def test_gpu_unverified_when_gpu_timing_disjoint(self):
        events = base_events()
        for e in events:
            if e["stage"] in ("srStart", "srEnd"):
                e["gpuElapsedNs"] = 100 * TARGET_STEP_NS  # interval far outside the 40 ms slot
                e["gpuTimingValid"] = False
        result = assess(make_index(), make_run(), events)
        self.assertEqual(result["gates"]["gpu"], "unverified")

    def test_foreign_run_raises_value_error(self):
        events = base_events()
        events[0] = dict(events[0], runId="foreign-run")
        with self.assertRaises(ValueError):
            assess(make_index(), make_run(), events)

    def test_duplicate_display_raises_value_error(self):
        events = base_events()
        first_display = next(e for e in events if e["stage"] == "display")
        events.append(dict(first_display))  # second present event, same eglFrameId
        with self.assertRaises(ValueError):
            assess(make_index(), make_run(), events)


if __name__ == "__main__":
    unittest.main()
