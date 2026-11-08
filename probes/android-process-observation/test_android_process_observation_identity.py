"""Targeted regression tests for the W8 observer PID-reuse identity race.

These tests are SEPARATE from the fixed original contract suite
(`test_android_process_observation`). They exercise the corrected behavior
where every status sample is bracketed by an identity stat read BEFORE and
AFTER the status read, and a PID-reuse / target-exit detected on the
POST-status stat fails the observation WITHOUT appending the possibly tainted
status sample.

The fake helpers (FakeRunner, FakeClock, make_stat, make_status) are imported
from the unchanged `test_android_process_observation` module, which the parent
places beside this file and the corrected module at execution time. No ADB,
device, real sleep, network, shell, or private media is used. No test is
executed here; the parent runner executes both suites.

Run with, e.g.:
    python -m unittest test_android_process_observation_identity
"""

import json
import os
import sys
import tempfile
import unittest

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

# Unchanged fakes supplied by the parent beside this file / the module.
from test_android_process_observation import (  # noqa: E402
    FakeRunner,
    FakeClock,
    make_stat,
    make_status,
)
import android_process_observation as module  # noqa: E402


class IdentityRaceRegressionTests(unittest.TestCase):
    """Post-status identity race must fail and drop the tainted sample."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def out_path(self, name="obs.json"):
        return os.path.join(self.tmp.name, name)

    def run_observe(self, runner, *, clock=None, sleeper=None,
                    duration_s=3.0, interval_s=1.0, output=None, **overrides):
        clock = clock or FakeClock()
        sleeper = sleeper or clock.sleep
        kwargs = dict(
            adb_serial="emulator-5554",
            package="io.ondeviceai.demo",
            pid=4242,
            process_name="ondeviceai.demo",
            duration_s=duration_s,
            interval_s=interval_s,
            output_path=output or self.out_path(),
            runner=runner,
            clock=clock,
            sleeper=sleeper,
        )
        kwargs.update(overrides)
        runner.output_path = kwargs["output_path"]
        return module.observe(**kwargs)

    def test_first_sample_reuse_failed_with_zero_samples(self):
        """Exact parent repro: duration1/interval10, PID reused during status.

        stat_fn initially reports starttime 987654 (bound identity). status_fn
        sets a reused flag and would report a mismatched RSS (99999 KiB). The
        subsequent (post-status) stat_fn reports starttime 111111. The observer
        must FAIL with PID_REUSE and retain ZERO samples (no tainted RSS).
        """
        reused = {"flag": False}

        def stat_fn(i):
            if reused["flag"]:
                return make_stat(starttime=111111)   # different process now
            return make_stat(starttime=987654)

        def status_fn(i):
            reused["flag"] = True
            return make_status(rss_kib=99999)         # belongs to reused pid

        runner = FakeRunner(stat_fn=stat_fn, status_fn=status_fn)
        clock = FakeClock()
        receipt = self.run_observe(
            runner, duration_s=1.0, interval_s=10.0,
            clock=clock, sleeper=clock.sleep,
            output=self.out_path("first-reuse.json"),
        )
        self.assertEqual(receipt["status"], "failed")
        self.assertTrue(receipt["terminal"] is True)
        self.assertIsNotNone(receipt["error"])
        self.assertEqual(receipt["error"]["code"], module.E_PID_REUSE)
        self.assertIn(receipt["error"]["stage"], module.STAGES)
        self.assertEqual(len(receipt["samples"]), 0)
        rss_values = [s["rssBytes"] for s in receipt["samples"]]
        self.assertNotIn(99999 * module.KIBIBYTE, rss_values)

    def test_reuse_after_one_valid_sample_retains_valid_only(self):
        """Reuse after one valid sample: failed, retains exactly the valid one.

        The first sample is fully valid (RSS 12345 KiB). On the SECOND status
        read the target is reused: post-status stat reports starttime 111111 and
        the status carried a foreign RSS (99999 KiB). The observer must FAIL,
        retain exactly ONE sample, and that sample must be the earlier valid one
        (RSS 12345 KiB), never the foreign 99999 KiB RSS.
        """
        reused = {"flag": False}

        def stat_fn(i):
            if reused["flag"]:
                return make_stat(starttime=111111)
            return make_stat(starttime=987654)

        def status_fn(i):
            if i >= 1:                       # second status read -> reuse
                reused["flag"] = True
                return make_status(rss_kib=99999)
            return make_status(rss_kib=12345)

        runner = FakeRunner(stat_fn=stat_fn, status_fn=status_fn)
        clock = FakeClock()
        receipt = self.run_observe(
            runner, duration_s=5.0, interval_s=1.0,
            clock=clock, sleeper=clock.sleep,
            output=self.out_path("reuse-after-valid.json"),
        )
        self.assertEqual(receipt["status"], "failed")
        self.assertEqual(receipt["error"]["code"], module.E_PID_REUSE)
        self.assertEqual(len(receipt["samples"]), 1)
        sample = receipt["samples"][0]
        self.assertEqual(sample["rssBytes"], 12345 * module.KIBIBYTE)
        self.assertNotEqual(sample["rssBytes"], 99999 * module.KIBIBYTE)
        self.assertEqual(sample["cpuTicks"], 150)   # identified pre-stat ticks

    def test_post_status_comm_mismatch_failed(self):
        """POST-status comm change fails (PID_REUSE) and drops the tainted sample.

        A flag is switched by status_fn: BEFORE the first status read stat_fn
        always reports the ORIGINAL identity (comm == process_name). AFTER
        status_fn sets the flag, the POST-status stat_fn reports a different
        comm. The corrected observer must detect the mismatch on the POST-status
        stat, FAIL with PID_REUSE, and retain ZERO samples (the tainted status
        sample is never appended). With duration1/interval10 the OLD
        no-postread observer reads only the PRE-status (original) stat, appends
        the sample, and COMPLETES -- so this test is genuinely RED against the
        old implementation and cannot pass it by accident.
        """
        post_status = {"flag": False}

        def stat_fn(i):
            if post_status["flag"]:
                return make_stat(comm="other.demo")
            return make_stat(comm="ondeviceai.demo")

        def status_fn(i):
            post_status["flag"] = True
            return make_status()          # status payload itself stays valid

        runner = FakeRunner(stat_fn=stat_fn, status_fn=status_fn)
        clock = FakeClock()
        output = self.out_path("post-comm.json")
        receipt = self.run_observe(
            runner, duration_s=1.0, interval_s=10.0,
            clock=clock, sleeper=clock.sleep,
            output=output,
        )
        self.assertEqual(receipt["status"], "failed")
        self.assertTrue(receipt["terminal"] is True)
        self.assertIsNotNone(receipt["error"])
        self.assertEqual(receipt["error"]["code"], module.E_PID_REUSE)
        self.assertIn(receipt["error"]["stage"], module.STAGES)
        self.assertEqual(len(receipt["samples"]), 0)
        self.assertNotIn("other.demo", json.dumps(receipt))
        # The terminal on-disk report must match the in-memory receipt.
        with open(output, "r", encoding="utf-8") as fh:
            on_disk = json.load(fh)
        self.assertEqual(on_disk["status"], "failed")
        self.assertTrue(on_disk["terminal"] is True)
        self.assertEqual(on_disk["error"]["code"], module.E_PID_REUSE)
        self.assertEqual(len(on_disk["samples"]), 0)

    def test_post_status_target_exit_failed(self):
        """POST-status pid loss fails (TARGET_EXIT) and drops the tainted sample.

        A flag is switched by status_fn. BEFORE the first status read stat_fn
        reports the ORIGINAL identity (pid 4242). AFTER status_fn sets the flag,
        the POST-status stat_fn returns a command-failure (returncode 1, empty
        stdout, bounded stderr secret). The corrected observer must treat the
        failed POST-status proc read as TARGET_EXIT, FAIL, and retain ZERO
        samples. The OLD no-postread observer never issues the POST-status read,
        appends the valid sample, and COMPLETES -- so this test is genuinely RED
        against the old implementation and cannot pass it by accident.
        """
        post_status = {"flag": False}
        bounded_secret = "BOUNDED_TARGET_EXIT_SECRET"

        def stat_fn(i):
            if post_status["flag"]:
                # command failure on the POST-status proc read
                return {"returncode": 1, "stdout": "", "stderr": bounded_secret}
            return make_stat(pid=4242)

        def status_fn(i):
            post_status["flag"] = True
            return make_status()

        runner = FakeRunner(stat_fn=stat_fn, status_fn=status_fn,
                            secret=bounded_secret)
        clock = FakeClock()
        output = self.out_path("post-exit.json")
        receipt = self.run_observe(
            runner, duration_s=1.0, interval_s=10.0,
            clock=clock, sleeper=clock.sleep,
            output=output,
        )
        self.assertEqual(receipt["status"], "failed")
        self.assertTrue(receipt["terminal"] is True)
        self.assertIsNotNone(receipt["error"])
        self.assertEqual(receipt["error"]["code"], module.E_TARGET_EXIT)
        self.assertIn(receipt["error"]["stage"], module.STAGES)
        self.assertEqual(len(receipt["samples"]), 0)
        # No bounded secret may leak into the structured receipt.
        self.assertNotIn(bounded_secret, json.dumps(receipt))
        # The terminal on-disk report must match the in-memory receipt.
        with open(output, "r", encoding="utf-8") as fh:
            on_disk = json.load(fh)
        self.assertEqual(on_disk["status"], "failed")
        self.assertTrue(on_disk["terminal"] is True)
        self.assertEqual(on_disk["error"]["code"], module.E_TARGET_EXIT)
        self.assertEqual(len(on_disk["samples"]), 0)

    def test_clean_run_still_completes_with_identified_ticks(self):
        """Sanity: a fully stable identity still completes and samples ticks."""
        runner = FakeRunner()
        clock = FakeClock()
        receipt = self.run_observe(
            runner, duration_s=3.0, interval_s=1.0,
            clock=clock, sleeper=clock.sleep,
            output=self.out_path("clean.json"),
        )
        self.assertEqual(receipt["status"], "completed")
        self.assertFalse(receipt.get("error"))
        self.assertGreaterEqual(len(receipt["samples"]), 2)
        for s in receipt["samples"]:
            self.assertEqual(s["rssBytes"], 12345 * module.KIBIBYTE)
            self.assertEqual(s["threads"], 17)
            self.assertEqual(s["cpuTicks"], 150)


if __name__ == "__main__":
    unittest.main()
