import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

from phase_capture import capture, quality
from run_phase import consumption, execute


class PhaseCaptureTest(unittest.TestCase):
    def setUp(self):
        tmp = TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)

    def test_return_and_raw_bytes(self):
        header, raw = b"--------- beginning of main\n", b"partial raw payload\n"
        fetch, run, collect = Mock(side_effect=[header, raw]), Mock(return_value=7), Mock()
        self.assertEqual(capture(self.root, fetch, run, collect), 7)
        self.assertEqual((self.root / "phase-before.log").read_bytes(), header)
        self.assertEqual((self.root / "phase.log").read_bytes(), raw)
        run.assert_called_once_with()
        collect.assert_called_once_with()

    def test_stale_log_preserved_and_stops_before_launch(self):
        run, collect = Mock(), Mock()
        with self.assertRaises(ValueError):
            capture(self.root, lambda: b"old phase\n", run, collect)
        self.assertEqual((self.root / "phase-before.log").read_bytes(), b"old phase\n")
        run.assert_not_called()
        collect.assert_not_called()

    def test_existing_capture_remains_intact(self):
        (self.root / "phase.log").write_bytes(b"prior")
        fetch, run = Mock(), Mock()
        with self.assertRaises(FileExistsError):
            capture(self.root, fetch, run, Mock())
        self.assertEqual((self.root / "phase.log").read_bytes(), b"prior")
        fetch.assert_not_called()
        run.assert_not_called()

    def test_primary_error_survives_two_collection_failures(self):
        primary = RuntimeError("resource stop")
        fetch = Mock(side_effect=[b"", OSError("log transport")])
        collect = Mock(side_effect=OSError("raw window transport"))
        with self.assertRaises(RuntimeError) as caught:
            capture(self.root, fetch, Mock(side_effect=primary), collect)
        self.assertIs(caught.exception, primary)
        self.assertEqual(len(primary.__notes__), 2)
        collect.assert_called_once_with()

    def test_failed_host_still_saves_raw_log(self):
        with self.assertRaisesRegex(RuntimeError, "host failure"):
            capture(self.root, Mock(side_effect=[b"", b"partial\xff"]),
                    Mock(side_effect=RuntimeError("host failure")), Mock())
        self.assertEqual((self.root / "phase.log").read_bytes(), b"partial\xff")

    def test_successful_host_reports_collection_failure(self):
        collect = Mock(side_effect=OSError("raw window transport"))
        with self.assertRaisesRegex(OSError, "log transport") as caught:
            capture(self.root, Mock(side_effect=[b"", OSError("log transport")]), Mock(), collect)
        self.assertIn("raw window transport", str(caught.exception.__notes__))
        collect.assert_called_once_with()

    def test_per_stage_verbatim_comparison(self):
        manifest = b'{"cases":[{"id":"case","reference":"A sentence."}]}'
        digest = hashlib.sha256(manifest).hexdigest()
        for changed in ("A sentence", " A sentence.", "a sentence.", ""):
            self.assertEqual(quality(manifest, digest, "case", {"auto":"A sentence.", "forced":changed}),
                             {"auto":True, "forced":False, "bothExact":False})
        texts = {"auto":"A sentence.", "forced":"A sentence."}
        self.assertTrue(quality(manifest, digest, "case", texts)["bothExact"])
        with self.assertRaises(ValueError):
            quality(manifest, "0" * 64, "case", texts)
        with self.assertRaises(ValueError):
            quality(manifest, digest, "other", texts)

    @patch("run_phase.run_on_avd")
    def test_consumption_shared_across_attempts(self, start):
        marker = self.root / "attempt-original/observation/host/run-started.json"
        marker.parent.mkdir(parents=True)
        marker.write_text("{}")
        self.assertEqual(consumption(self.root), ["attempt-original/observation/host/run-started.json"])
        with self.assertRaisesRegex(RuntimeError, "already consumed"):
            execute({}, {}, self.root)
        start.assert_not_called()

    @patch("run_phase.run_on_avd")
    def test_losing_concurrent_launcher_does_not_reach_device(self, start):
        import fcntl
        import os
        fd = os.open(self.root, os.O_RDONLY)
        self.addCleanup(os.close, fd)
        fcntl.flock(fd, fcntl.LOCK_EX)
        with self.assertRaises(BlockingIOError):
            execute({}, {}, self.root)
        start.assert_not_called()

    @patch("run_phase.run_on_avd", side_effect=RuntimeError("prelaunch test stop"))
    def test_unstarted_attempt_preserves_evidence_and_remains_unconsumed(self, start):
        with self.assertRaisesRegex(RuntimeError, "prelaunch test stop"):
            execute({}, {"test":True}, self.root)
        self.assertEqual(consumption(self.root), [])
        self.assertEqual(json.loads(next(self.root.glob("*/preflight.json")).read_bytes()), {"test":True})


if __name__ == "__main__":
    unittest.main()
