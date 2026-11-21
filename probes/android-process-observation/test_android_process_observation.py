"""Behavioral contract tests for the W8 host-side sustained process observer.

TEST-FIRST. The shipped module is a stub: every callable raises
NotImplementedError, so the parent sees a real behavioral RED (imports and
signatures are correct). No full implementation is delivered in this task.

All external I/O is faked: no ADB, no device, no real sleep, no network, no
shell, no private media. Only a local temp file is used for the output receipt.
Tests assert observable receipts/values, not implementation internals.
"""

import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import android_process_observation as module


# ---- fake /proc payloads ---------------------------------------------------

def make_stat(pid=4242, comm="ondeviceai.demo", starttime=987654,
              utime=100, stime=50):
    # After the closing ')', fields 3.. start: rest[11]=field14 utime,
    # rest[12]=field15 stime, rest[19]=field22 starttime.
    rest = [
        "S", "1", "1", "1", "0", "-1", "4194560", "1", "0", "0", "0",
        str(utime), str(stime), "0", "0", "20", "0", "17", "0",
        str(starttime), "0", "0", "0",
    ]
    return f"{pid} ({comm}) " + " ".join(rest) + "\n"


def make_status(rss_kib=12345, threads=17):
    return (
        "Name:\tondeviceai.demo\n"
        "VmPeak:\t  200000 kB\n"
        "VmRSS:\t  " + str(rss_kib) + " kB\n"
        "Threads:\t" + str(threads) + "\n"
    )


def make_status_missing(field):
    text = make_status()
    if field == "VmRSS":
        return text.replace("VmRSS:\t  12345 kB\n", "")
    return text.replace("Threads:\t17\n", "")


class FakeResult:
    def __init__(self, returncode, stdout, stderr):
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr


def classify(argv):
    for token in argv:
        if token.endswith("/status"):
            return "status"
    for token in argv:
        if token.endswith("/stat"):
            return "stat"
    return "unknown"


class FakeRunner:
    """Subprocess stand-in. Dispatches on the /proc/.../stat|status path."""

    def __init__(self, stat_fn=None, status_fn=None, secret=None):
        self.calls = []                 # (argv, kwargs)
        self.file_snapshots = []        # output file content at each read
        self.stat_reads = 0
        self.status_reads = 0
        self._stat_fn = stat_fn or (lambda i: make_stat())
        self._status_fn = status_fn or (lambda i: make_status())
        self.secret = secret
        self.output_path = None

    def __call__(self, argv, **kwargs):
        self.calls.append((list(argv), dict(kwargs)))
        if self.output_path and os.path.exists(self.output_path):
            with open(self.output_path, "r", encoding="utf-8") as fh:
                self.file_snapshots.append(fh.read())
        kind = classify(argv)
        if kind == "stat":
            spec = self._stat_fn(self.stat_reads)
            self.stat_reads += 1
        else:
            spec = self._status_fn(self.status_reads)
            self.status_reads += 1
        if isinstance(spec, str):
            return FakeResult(0, spec, self.secret or "")
        return FakeResult(
            spec.get("returncode", 0),
            spec.get("stdout", ""),
            self.secret or spec.get("stderr", ""),
        )


class FakeClock:
    """Monotonic stand-in; sleep() advances the timeline (no real sleeping)."""

    def __init__(self, start=1000.0, jump_factor=1.0):
        self.t = start
        self.jump_factor = jump_factor

    def __call__(self):
        return self.t

    def sleep(self, delay):
        self.t += delay * self.jump_factor


# ---- test case -------------------------------------------------------------

class ObserverContractTests(unittest.TestCase):

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

    # -- parsing: units and raw ticks ---------------------------------------

    def test_parse_proc_stat_units_and_raw_ticks(self):
        parsed = module.parse_proc_stat(make_stat())
        self.assertEqual(parsed["pid"], 4242)
        self.assertEqual(parsed["starttime"], 987654)
        self.assertEqual(parsed["comm"], "ondeviceai.demo")
        self.assertEqual(parsed["utime"], 100)
        self.assertEqual(parsed["stime"], 50)
        # raw ticks only: no Hz/energy/seconds conversion keys
        self.assertNotIn("energy", parsed)
        self.assertNotIn("cpuSeconds", parsed)
        self.assertNotIn("joules", parsed)
        # truncated/inaccessible stat is an honest error, not a 0
        with self.assertRaises(module.ObservationError):
            module.parse_proc_stat("4242 (x) S 1 1\n")

    def test_parse_proc_stat_robust_comm_with_spaces_and_parens(self):
        weird = "weird (name) proc"
        parsed = module.parse_proc_stat(make_stat(comm=weird))
        self.assertEqual(parsed["comm"], weird)
        # fields after comm still parse correctly
        self.assertEqual(parsed["starttime"], 987654)
        self.assertEqual(parsed["utime"], 100)
        self.assertEqual(parsed["stime"], 50)

    def test_parse_proc_status_units(self):
        parsed = module.parse_proc_status(make_status())
        self.assertEqual(parsed["rss_bytes"], 12345 * 1024)  # KiB -> bytes
        self.assertEqual(parsed["threads"], 17)

    def test_parse_proc_status_missing_field_is_unsupported_not_zero(self):
        self.assertTrue(issubclass(module.UnsupportedField, module.ObservationError))
        for field in ("VmRSS", "Threads"):
            with self.subTest(field=field):
                with self.assertRaises(module.UnsupportedField):
                    module.parse_proc_status(make_status_missing(field))

    # -- safe arguments / explicit target -----------------------------------

    def test_build_read_argv_is_safe_list_and_explicit_target(self):
        argv = module.build_read_argv("emulator-5554", "io.ondeviceai.demo", 4242, "stat")
        self.assertIsInstance(argv, list)
        self.assertTrue(all(isinstance(a, str) for a in argv))
        self.assertEqual(argv[0], "adb")
        self.assertIn("-s", argv)
        self.assertIn("emulator-5554", argv)
        self.assertIn("run-as", argv)
        self.assertIn("io.ondeviceai.demo", argv)
        self.assertIn("/proc/4242/stat", argv)
        joined = " ".join(argv)
        for bad in (";", "|", "&", "$", "`", ">", "<", "\n", "cmdline", "shell=True"):
            self.assertNotIn(bad, joined)
        # explicit serial: no automatic discovery / all-devices
        self.assertNotIn("devices", argv)
        self.assertNotIn("-d", argv)
        self.assertNotIn("-e", argv)

    def test_build_read_argv_rejects_disallowed_package_and_pid(self):
        with self.assertRaises(module.ObservationError):
            module.build_read_argv("ser", "com.evil.app", 4242, "stat")
        for bad_pid in ("4242", True, -1, 0):
            with self.subTest(pid=bad_pid):
                with self.assertRaises(module.ObservationError):
                    module.build_read_argv("ser", "io.ondeviceai.demo", bad_pid, "stat")
        with self.assertRaises(module.ObservationError):
            module.build_read_argv("", "io.ondeviceai.demo", 4242, "stat")

    # -- one successful finite run with fake clock --------------------------

    def test_successful_finite_run_with_fake_clock(self):
        runner = FakeRunner()
        receipt = self.run_observe(runner, duration_s=3.0, interval_s=1.0,
                                   output=self.out_path("ok.json"))
        self.assertEqual(receipt["status"], "completed")
        self.assertTrue(receipt["terminal"] is True)
        self.assertFalse(receipt.get("error"))
        self.assertGreaterEqual(len(receipt["samples"]), 2)
        for s in receipt["samples"]:
            self.assertEqual(s["rssBytes"], 12345 * 1024)
            self.assertEqual(s["threads"], 17)
            self.assertEqual(s["cpuTicks"], 150)  # raw utime+stime, no Hz
        times = [s["tMonoS"] for s in receipt["samples"]]
        self.assertEqual(times, sorted(times))           # monotonic
        self.assertTrue(all(0 <= t < 3.0 for t in times))
        # durable running receipt existed before the first proc read
        running_seen = any(
            snap.strip() and json.loads(snap).get("status") == "running"
            for snap in runner.file_snapshots
        )
        self.assertTrue(running_seen)
        # safe host argv, never shell=True, no cmdline
        for argv, kwargs in runner.calls:
            self.assertIsInstance(argv, list)
            self.assertIsNot(kwargs.get("shell"), True)
            self.assertEqual(argv[0], "adb")
            self.assertIn("-s", argv)
            self.assertNotIn("cmdline", " ".join(argv))
        # final on-disk receipt is a completed, terminal, structured receipt
        with open(self.out_path("ok.json"), "r", encoding="utf-8") as fh:
            on_disk = json.load(fh)
        self.assertEqual(on_disk["status"], "completed")
        self.assertTrue(on_disk["terminal"] is True)

    # -- failure after samples ----------------------------------------------

    def test_pid_reuse_terminal_failure_retains_samples(self):
        runner = FakeRunner(
            stat_fn=lambda i: make_stat() if i < 3 else make_stat(starttime=111111),
            secret="SECRET_STDERR",
        )
        receipt = self.run_observe(runner, duration_s=5.0, interval_s=1.0,
                                   output=self.out_path("reuse.json"))
        self.assertEqual(receipt["status"], "failed")
        self.assertTrue(receipt["terminal"] is True)
        error = receipt["error"]
        self.assertIsNotNone(error)
        self.assertIn(error["code"], module.ERROR_CODES)
        self.assertEqual(error["code"], module.E_PID_REUSE)
        self.assertIn(error["stage"], module.STAGES)
        self.assertGreaterEqual(len(receipt["samples"]), 1)
        for s in receipt["samples"]:
            self.assertEqual(s["rssBytes"], 12345 * 1024)  # real retained samples
        self.assertNotIn("SECRET", json.dumps(receipt))     # no raw stderr leak

    def test_target_exit_after_samples_is_failed_not_success(self):
        def structured(i):
            return make_status() if i < 3 else {
                "returncode": 1, "stdout": "", "stderr": "SECRET_STDERR"}

        def unexpected(i):
            if i < 3:
                return make_status()
            raise RuntimeError("unexpected read failure SECRET_STDERR")

        for mode, fn in (("command_failure", structured),
                         ("unexpected_exception", unexpected)):
            with self.subTest(mode=mode):
                runner = FakeRunner(status_fn=fn, secret="SECRET_STDERR")
                receipt = self.run_observe(runner, duration_s=5.0, interval_s=1.0,
                                           output=self.out_path(f"exit-{mode}.json"))
                self.assertNotEqual(receipt["status"], "completed")
                self.assertEqual(receipt["status"], "failed")
                self.assertTrue(receipt["terminal"] is True)
                self.assertIn(receipt["error"]["code"], module.ERROR_CODES)
                self.assertGreaterEqual(len(receipt["samples"]), 1)
                self.assertNotIn("SECRET", json.dumps(receipt))

    def test_missing_field_after_samples_is_unsupported_not_zero(self):
        runner = FakeRunner(
            status_fn=lambda i: make_status() if i < 3 else make_status_missing("VmRSS")
        )
        receipt = self.run_observe(runner, duration_s=5.0, interval_s=1.0,
                                   output=self.out_path("missing.json"))
        self.assertEqual(receipt["status"], "failed")
        self.assertEqual(receipt["error"]["code"], module.E_UNSUPPORTED_FIELD)
        self.assertGreaterEqual(len(receipt["samples"]), 1)
        for s in receipt["samples"]:
            self.assertNotEqual(s["rssBytes"], 0)          # never a 0 sample
            self.assertEqual(s["rssBytes"], 12345 * 1024)

    # -- no overwrite / no invented samples ---------------------------------

    def test_no_overwrite_existing_output_file(self):
        path = self.out_path("existing.json")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("SENTINEL-DO-NOT-OVERWRITE")
        runner = FakeRunner()
        receipt = self.run_observe(runner, output=path)
        self.assertEqual(receipt["status"], "failed")
        self.assertEqual(receipt["error"]["code"], module.E_OUTPUT_EXISTS)
        with open(path, "r", encoding="utf-8") as fh:
            self.assertEqual(fh.read(), "SENTINEL-DO-NOT-OVERWRITE")
        self.assertEqual(runner.calls, [])                  # never started sampling

    def test_no_invented_samples_for_missed_intervals(self):
        clock = FakeClock(jump_factor=20.0)                 # a big timeline jump
        runner = FakeRunner()
        receipt = self.run_observe(runner, duration_s=10.0, interval_s=1.0,
                                   clock=clock, sleeper=clock.sleep,
                                   output=self.out_path("missed.json"))
        samples = receipt["samples"]
        self.assertLess(len(samples), 10)                   # no backfilled grid
        times = [s["tMonoS"] for s in samples]
        self.assertEqual(times, sorted(times))              # monotonic
        self.assertTrue(all(0 <= t < 10.0 for t in times))


if __name__ == "__main__":
    unittest.main()
