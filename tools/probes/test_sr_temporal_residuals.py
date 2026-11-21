"""Offline unit tests for sr_temporal_residuals.

No ffmpeg, no media, no network, no device: every case is a tiny
hand-computed byte frame, a pure contract function, a path behavior, or a
CLI-level failure record. The parent is expected to run these tests beside
the delivered script.
"""

from __future__ import annotations

import importlib.util
import io
import json
import math
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sr_temporal_residuals as tool


class ResidualFunctionTests(unittest.TestCase):
    """Hand-computed 2-pixel byte-frame cases for the pure residual functions."""

    def test_frame_residual_and_sums(self):
        # e = [+2, -3]: sum = -1, sum abs = 5, sum squares = 4 + 9 = 13
        self.assertEqual(list(tool.frame_residual(bytes([10, 20]), bytes([12, 17]))),
                         [2, -3])
        self.assertEqual(tool.frame_residual_sums(bytes([10, 20]), bytes([12, 17])),
                         (-1, 5, 13))

    def test_frame_residual_length_mismatch_fails_closed(self):
        with self.assertRaises(ValueError):
            tool.frame_residual_sums(bytes([1, 2, 3]), bytes([1, 2]))

    def test_temporal_residual_subtracts_previous_error(self):
        # e_prev = [0, 0]; e_cur = [-10, -5]; r = e_cur - e_prev = [-10, -5]
        self.assertEqual(list(tool.temporal_residual(bytes([10, 10]), bytes([30, 20]),
                                                     bytes([10, 10]), bytes([20, 15]))),
                         [-10, -5])
        self.assertEqual(tool.temporal_residual_sums(bytes([10, 10]), bytes([30, 20]),
                                                    bytes([10, 10]), bytes([20, 15])),
                         (-15, 15, 125))

    def test_temporal_residual_length_mismatch_fails_closed(self):
        with self.assertRaises(ValueError):
            tool.temporal_residual(bytes([1]), bytes([1, 2]), bytes([1]), bytes([1]))


class MetricPipelineTests(unittest.TestCase):
    """Whole-metric behavior on 2-pixel, 3-frame lists (all values hand-computed)."""

    def test_spatial_and_temporal_hand_case(self):
        reference = [bytes([100, 100])] * 3
        candidate = [bytes([100, 100]), bytes([103, 103]), bytes([99, 102])]
        metrics = tool.compute_metrics(reference, candidate)
        frames = metrics["spatial"]["frames"]
        self.assertEqual([entry["frame1Based"] for entry in frames], [1, 2, 3])
        # frame 1: e = [0, 0]; frame 2: e = [3, 3]; frame 3: e = [-1, 2]
        self.assertEqual((frames[0]["bias"], frames[0]["mae"], frames[0]["rmse"]),
                         (0.0, 0.0, 0.0))
        self.assertEqual((frames[1]["bias"], frames[1]["mae"], frames[1]["rmse"]),
                         (3.0, 3.0, 3.0))
        self.assertAlmostEqual(frames[2]["bias"], 0.5)
        self.assertAlmostEqual(frames[2]["mae"], 1.5)
        self.assertAlmostEqual(frames[2]["rmse"], math.sqrt(2.5))
        aggregate = metrics["spatial"]["aggregate"]
        self.assertAlmostEqual(aggregate["bias"], 7 / 6)
        self.assertAlmostEqual(aggregate["mae"], 1.5)
        self.assertAlmostEqual(aggregate["rmse"], math.sqrt(23 / 6))
        self.assertEqual(metrics["spatial"]["pixelCount"], 6)
        # aggregate RMSE must NOT be the average of per-frame RMSE values
        self.assertNotAlmostEqual(aggregate["rmse"],
                                  (0.0 + 3.0 + math.sqrt(2.5)) / 3)
        temporal_frames = metrics["temporal"]["frames"]
        # interval ending at frame 2: r = [3, 3]; ending at frame 3: r = [-4, -1]
        self.assertEqual([entry["frame1Based"] for entry in temporal_frames], [2, 3])
        self.assertEqual((temporal_frames[0]["bias"], temporal_frames[0]["mae"],
                          temporal_frames[0]["rmse"]), (3.0, 3.0, 3.0))
        self.assertAlmostEqual(temporal_frames[1]["bias"], -2.5)
        self.assertAlmostEqual(temporal_frames[1]["mae"], 2.5)
        self.assertAlmostEqual(temporal_frames[1]["rmse"], math.sqrt(8.5))
        temporal = metrics["temporal"]["aggregate"]
        self.assertAlmostEqual(temporal["bias"], 0.25)
        self.assertAlmostEqual(temporal["mae"], 2.75)
        self.assertAlmostEqual(temporal["rmse"], math.sqrt(8.75))
        self.assertEqual(metrics["temporal"]["pixelCount"], 4)
        self.assertEqual(metrics["temporal"]["frameIntervals"], 2)
        json.dumps(metrics, allow_nan=False)

    def test_fixed_brightness_bias_is_purely_spatial(self):
        reference = [bytes([50, 60]), bytes([80, 90]), bytes([20, 30])]
        candidate = [bytes([60, 70]), bytes([90, 100]), bytes([30, 40])]
        metrics = tool.compute_metrics(reference, candidate)
        spatial = metrics["spatial"]["aggregate"]
        self.assertAlmostEqual(spatial["bias"], 10.0)
        self.assertAlmostEqual(spatial["mae"], 10.0)
        self.assertAlmostEqual(spatial["rmse"], 10.0)
        temporal = metrics["temporal"]["aggregate"]
        self.assertEqual((temporal["bias"], temporal["mae"], temporal["rmse"]),
                         (0.0, 0.0, 0.0))
        # the fixture is not vacuous: the reference itself moves across frames
        self.assertTrue(any(left != right for left, right in zip(reference, reference[1:])))

    def test_identical_candidate_is_all_zero(self):
        frames = [bytes([5, 250]), bytes([200, 7]), bytes([128, 128])]
        metrics = tool.compute_metrics(frames, list(frames))
        for section in ("spatial", "temporal"):
            aggregate = metrics[section]["aggregate"]
            self.assertEqual((aggregate["bias"], aggregate["mae"], aggregate["rmse"]),
                             (0.0, 0.0, 0.0))
            for entry in metrics[section]["frames"]:
                self.assertEqual((entry["bias"], entry["mae"], entry["rmse"]),
                                 (0.0, 0.0, 0.0))

    def test_genuine_motion_is_not_reported_as_temporal_error(self):
        # Frame difference is not flicker: the reference genuinely changes
        # frame-to-frame (motion), yet a temporally faithful candidate has a
        # zero temporal residual because the reference change cancels.
        reference = [bytes([10, 200]), bytes([200, 10]), bytes([10, 200])]
        metrics = tool.compute_metrics(reference, list(reference))
        self.assertNotEqual(reference[0], reference[1])
        for entry in metrics["temporal"]["frames"]:
            self.assertEqual((entry["bias"], entry["mae"], entry["rmse"]),
                             (0.0, 0.0, 0.0))

    def test_oscillating_error_is_visible_in_temporal_residual(self):
        reference = [bytes([100, 100])] * 3
        candidate = [bytes([110, 110]), bytes([90, 90]), bytes([110, 110])]
        metrics = tool.compute_metrics(reference, candidate)
        spatial = metrics["spatial"]["aggregate"]
        self.assertAlmostEqual(spatial["bias"], 20 / 6)
        self.assertAlmostEqual(spatial["mae"], 10.0)
        self.assertAlmostEqual(spatial["rmse"], 10.0)
        temporal = metrics["temporal"]["aggregate"]
        self.assertEqual((temporal["bias"], temporal["mae"], temporal["rmse"]),
                         (0.0, 20.0, 20.0))
        for entry in metrics["temporal"]["frames"]:
            self.assertEqual((entry["mae"], entry["rmse"]), (20.0, 20.0))

    def test_temporal_boundary_count_is_frames_minus_one(self):
        frames = [bytes([1, 2, 3])] * 5
        metrics = tool.compute_metrics(frames, list(frames))
        self.assertEqual(len(metrics["temporal"]["frames"]), 4)
        self.assertEqual([entry["frame1Based"] for entry in metrics["temporal"]["frames"]],
                         [2, 3, 4, 5])
        self.assertEqual(metrics["temporal"]["frameIntervals"], 4)
        self.assertEqual(metrics["temporal"]["pixelCount"], 12)
        self.assertEqual(metrics["spatial"]["pixelCount"], 15)

    def test_fail_closed_on_empty_single_mismatched_or_oversized_frames(self):
        two = [bytes([1, 2]), bytes([1, 2])]
        with self.assertRaises(ValueError):
            tool.compute_metrics([], [])
        with self.assertRaises(ValueError):
            tool.compute_metrics([bytes([1, 2])], [bytes([1, 2])])
        with self.assertRaises(ValueError):
            tool.compute_metrics(two, [bytes([1, 2])])
        with self.assertRaises(ValueError):
            tool.compute_metrics([bytes([1, 2])], [bytes([1, 2, 3])])
        with self.assertRaises(ValueError):
            tool.compute_metrics([bytes([1, 2]), bytes([1, 2, 3])], [bytes([1, 2]), bytes([1, 2])])


class ByteContractTests(unittest.TestCase):
    """Exact-byte frame reading, extra-byte rejection and the exact decode command."""

    def test_read_exact_frame_exact_truncated_and_eof(self):
        stream = io.BytesIO(bytes(4))
        self.assertEqual(tool.read_exact_frame(stream, 4, 0, "ref"), bytes(4))
        self.assertIsNone(tool.read_exact_frame(stream, 4, 1, "ref"))
        with self.assertRaises(tool.DecodeError):
            tool.read_exact_frame(io.BytesIO(bytes(3)), 4, 0, "ref")

    def test_require_no_extra_bytes(self):
        tool.require_no_extra_bytes(io.BytesIO(b""), "ref")
        with self.assertRaises(tool.DecodeError):
            tool.require_no_extra_bytes(io.BytesIO(b"\x01"), "ref")

    def test_luma_decode_command_states_exact_conversion(self):
        command = tool.luma_decode_command(Path("clip.mov"), "tv")
        self.assertEqual(command[0], "ffmpeg")
        self.assertIn("-nostdin", command)
        self.assertIn("-xerror", command)
        self.assertIn("-loglevel", command)
        self.assertIn("error", command)
        self.assertIn("-map", command)
        self.assertIn("0:v:0", command)
        # 'format' is a standalone filter chained with a comma after the scale
        # filter; it is not a scale colon-option (which ffmpeg rejects).
        self.assertIn("scale=w=iw:h=ih:in_range=tv:out_range=full,format=gray", command)
        self.assertIn("-pix_fmt", command)
        self.assertIn("gray", command)
        self.assertIn("-fps_mode", command)
        self.assertIn("passthrough", command)
        self.assertEqual(command[-3:], ["-f", "rawvideo", "-"])


class InputIdentityTests(unittest.TestCase):
    def test_identity_hashes_known_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input.bin"
            path.write_bytes(b"hello")
            identity = tool._input_identity(path, "reference")
            self.assertEqual(identity["sizeBytes"], 5)
            self.assertEqual(identity["inputSha256"],
                             "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
            self.assertEqual(identity["path"], str(path))

    def test_identity_rejects_missing_directory_and_empty_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(tool.DiagnosticError):
                tool._input_identity(root / "missing.bin", "reference")
            with self.assertRaises(tool.DiagnosticError):
                tool._input_identity(root, "reference")  # a directory is not a regular file
            empty = root / "empty.bin"
            empty.write_bytes(b"")
            with self.assertRaises(tool.DiagnosticError):
                tool._input_identity(empty, "reference")


class StreamPairContractTests(unittest.TestCase):
    """Frame-pairing checks mirrored from video_sr_quality.compare, tested purely."""

    @staticmethod
    def _meta(**overrides):
        meta = {"nb_read_frames": "3", "width": "2", "height": "1",
                "avg_frame_rate": "24/1", "pix_fmt": "yuv420p", "color_range": "tv",
                "color_space": "bt709", "color_transfer": "bt709",
                "color_primaries": "bt709"}
        meta.update(overrides)
        return meta

    @staticmethod
    def _times(count=3):
        return [index / 24 for index in range(count)]

    def test_valid_pair_passes(self):
        contract = tool._validated_stream_pair(self._meta(), self._meta(),
                                               self._times(), self._times())
        self.assertEqual(contract["count"], 3)
        self.assertEqual(contract["width"], 2)
        self.assertEqual(contract["inRange"], "tv")
        self.assertEqual(contract["colorComparability"], "matched-metadata")

    def test_single_frame_fails_closed(self):
        meta = self._meta(nb_read_frames="1")
        with self.assertRaises(tool.DiagnosticError):
            tool._validated_stream_pair(meta, meta, [0.0], [0.0])

    def test_frame_count_mismatch_fails_closed(self):
        with self.assertRaises(tool.DiagnosticError):
            tool._validated_stream_pair(self._meta(), self._meta(nb_read_frames="4"),
                                        self._times(), self._times(4))

    def test_dimension_and_framerate_mismatch_fail_closed(self):
        with self.assertRaises(tool.DiagnosticError):
            tool._validated_stream_pair(self._meta(), self._meta(width="4"),
                                        self._times(), self._times())
        with self.assertRaises(tool.DiagnosticError):
            tool._validated_stream_pair(self._meta(), self._meta(avg_frame_rate="25/1"),
                                        self._times(), self._times())

    def test_cadence_violation_fails_closed(self):
        jumping = [0.0, 1 / 24, 3 / 24]
        with self.assertRaises(tool.DiagnosticError):
            tool._validated_stream_pair(self._meta(), self._meta(),
                                        self._times(), jumping)

    def test_unknown_or_mismatched_color_range_fails_closed(self):
        with self.assertRaises(tool.DiagnosticError):
            tool._validated_stream_pair(self._meta(color_range="unspecified"), self._meta(),
                                        self._times(), self._times())
        with self.assertRaises(tool.DiagnosticError):
            tool._validated_stream_pair(self._meta(), self._meta(color_range="pc"),
                                        self._times(), self._times())

    def test_color_metadata_mismatch_is_descriptive_only(self):
        contract = tool._validated_stream_pair(self._meta(), self._meta(color_space="unknown"),
                                               self._times(), self._times())
        self.assertEqual(contract["colorComparability"], "unverified")


class OutputExclusivityTests(unittest.TestCase):
    """The output path is created exclusively; overwrite and symlinks are rejected."""

    def test_write_json_exclusive_round_trips(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "out.json"
            tool.write_json_exclusive(path, {"schemaVersion": 1, "status": "ok", "value": 1.5})
            self.assertEqual(json.loads(path.read_text(encoding="utf-8")),
                             {"schemaVersion": 1, "status": "ok", "value": 1.5})

    def test_write_json_exclusive_never_overwrites(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "out.json"
            path.write_bytes(b'{"sentinel": true}')
            with self.assertRaises(OSError):
                tool.write_json_exclusive(path, {"status": "failed"})
            self.assertEqual(path.read_bytes(), b'{"sentinel": true}')

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_write_json_exclusive_rejects_symlink_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.json"
            target.write_bytes(b'{"target": true}')
            link = root / "link.json"
            os.symlink(target, link)
            with self.assertRaises(OSError):
                tool.write_json_exclusive(link, {"status": "failed"})
            self.assertEqual(target.read_bytes(), b'{"target": true}')
            dangling = root / "dangling.json"
            os.symlink(root / "missing.json", dangling)
            with self.assertRaises(OSError):
                tool.write_json_exclusive(dangling, {"status": "failed"})
            self.assertFalse((root / "missing.json").exists())

    def test_write_json_exclusive_requires_existing_parent_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(OSError):
                tool.write_json_exclusive(Path(directory) / "missing" / "out.json", {})

    def test_non_finite_payload_writes_nothing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "out.json"
            with self.assertRaises(ValueError):
                tool.write_json_exclusive(path, {"value": float("nan")})
            self.assertFalse(path.exists())

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_validate_output_destination_rejects_existing_symlink_and_missing_parent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tool._validate_output_destination(root / "fresh.json")  # no exception
            existing = root / "existing.json"
            existing.write_bytes(b"{}")
            with self.assertRaises(tool.DiagnosticError):
                tool._validate_output_destination(existing)
            link = root / "link.json"
            os.symlink(existing, link)
            with self.assertRaises(tool.DiagnosticError):
                tool._validate_output_destination(link)
            with self.assertRaises(tool.DiagnosticError):
                tool._validate_output_destination(root / "missing" / "out.json")


class CommandLineTests(unittest.TestCase):
    """CLI-level fail-closed records; no ffmpeg or media is involved anywhere."""

    def test_missing_inputs_produce_failure_record_and_nonzero_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "out.json"
            code = tool.main([str(root / "missing-reference.mkv"),
                              str(root / "missing-candidate.mov"),
                              "--output", str(output)])
            self.assertEqual(code, 1)
            report = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "failed")
            self.assertTrue(report["error"])
            self.assertIsNone(report["metrics"])
            self.assertEqual(report["qualityVerdict"], "unverified")
            self.assertFalse(report["productionReady"])
            self.assertEqual(report["referencePath"], str(root / "missing-reference.mkv"))
            self.assertEqual(report["candidatePath"], str(root / "missing-candidate.mov"))
            json.dumps(report, allow_nan=False)

    def test_existing_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "out.json"
            output.write_bytes(b'{"sentinel": true}')
            code = tool.main([str(root / "missing-reference.mkv"),
                              str(root / "missing-candidate.mov"),
                              "--output", str(output)])
            self.assertEqual(code, 1)
            self.assertEqual(output.read_bytes(), b'{"sentinel": true}')

    def test_missing_output_directory_fails_without_writing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            code = tool.main([str(root / "reference.mkv"), str(root / "candidate.mov"),
                              "--output", str(root / "missing" / "out.json")])
            self.assertEqual(code, 1)
            self.assertFalse((root / "missing").exists())


class SiblingLoaderTests(unittest.TestCase):
    def test_load_sibling_functions_fails_closed_when_tools_are_absent(self):
        if importlib.util.find_spec("video_sr_quality") is not None:
            self.skipTest("video_sr_quality.py is present beside the tests (parent layout)")
        with self.assertRaises(tool.DiagnosticError):
            tool.load_sibling_functions()


if __name__ == "__main__":
    unittest.main()
