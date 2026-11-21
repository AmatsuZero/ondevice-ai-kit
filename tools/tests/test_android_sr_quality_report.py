"""Unit tests for validate_android_sr_quality_report."""

import os
import sys
import unittest

_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(_THIS_DIR, os.pardir, "probes"))

from validate_android_sr_quality_report import (  # noqa: E402
    validate_report,
    ReportValidationError,
)

RUN_ID = "run-evidence-0001"
INPUT_SHA = "a" * 64
MODEL_SHA = "b" * 64
SOURCE_HASHES = {
    "models/sr.tflite": "c" * 64,
    "config/sr_config.json": "d" * 64,
}
OUTPUT_SIZE = (1280, 720)


def _codec(dec_name="c2.android.avc.decoder", enc_name="c2.mtk.avc.encoder"):
    return [
        {"encoder": False, "name": dec_name},
        {"encoder": True, "name": enc_name},
    ]


def make_valid_report():
    def run(kind, osha):
        return {
            "kind": kind,
            "status": "completed",
            "completedFrames": 48,
            "frames": 48,
            "width": OUTPUT_SIZE[0],
            "height": OUTPUT_SIZE[1],
            "outputBytes": 100000,
            "outputSha256": osha,
            "codecs": _codec(),
        }
    return {
        "schemaVersion": 1,
        "runId": RUN_ID,
        "pid": 1234,
        "terminal": True,
        "status": "completed",
        "qualityVerdict": "unverified",
        "inputSha256": INPUT_SHA,
        "modelSha256": MODEL_SHA,
        "sourceInventory": {
            "scope": "benchmark",
            "sources": dict(SOURCE_HASHES),
        },
        "runs": [
            run("nearest", "e" * 64),
            run("bilinear", "f" * 64),
            run("esrgan", "1" * 64),
        ],
    }


def call(report):
    return validate_report(
        report,
        run_id=RUN_ID,
        input_sha256=INPUT_SHA,
        model_sha256=MODEL_SHA,
        source_hashes=SOURCE_HASHES,
        output_size=OUTPUT_SIZE,
    )


class ValidReportTests(unittest.TestCase):
    def test_valid_report(self):
        result = call(make_valid_report())
        self.assertEqual(result["runId"], RUN_ID)
        self.assertEqual(result["output_count"], 3)
        self.assertEqual(result["total_paired_frames"], 144)
        self.assertEqual(result["qualityVerdict"], "unverified")



class NegativeReportTests(unittest.TestCase):
    def test_report_not_dict(self):
        with self.assertRaises(ReportValidationError):
            validate_report([], run_id=RUN_ID, input_sha256=INPUT_SHA,
                            model_sha256=MODEL_SHA, source_hashes=SOURCE_HASHES,
                            output_size=OUTPUT_SIZE)

    def test_malformed_nested_shapes(self):
        for key in ("sourceInventory", "runs"):
            for value in (None, True, 1, "x", [], {}):
                with self.subTest(key=key, value=value):
                    report = make_valid_report()
                    report[key] = value
                    with self.assertRaises(ReportValidationError):
                        call(report)
        for key in ("codecs", "outputSha256"):
            for value in (None, True, 1, {}, []):
                with self.subTest(key=key, value=value):
                    report = make_valid_report()
                    report["runs"][0][key] = value
                    with self.assertRaises(ReportValidationError):
                        call(report)

    def test_boolean_numeric_fields(self):
        for key in ("pid", "schemaVersion"):
            report = make_valid_report()
            report[key] = True
            with self.subTest(key=key), self.assertRaises(ReportValidationError):
                call(report)
        for key in ("frames", "completedFrames", "width", "height", "outputBytes"):
            report = make_valid_report()
            report["runs"][0][key] = True
            with self.subTest(key=key), self.assertRaises(ReportValidationError):
                call(report)

    def test_invalid_expected_size(self):
        for size in (None, 1, "1280", (), (1280,), (True, 720)):
            with self.subTest(size=size), self.assertRaises(ReportValidationError):
                validate_report(make_valid_report(), run_id=RUN_ID,
                                input_sha256=INPUT_SHA, model_sha256=MODEL_SHA,
                                source_hashes=SOURCE_HASHES, output_size=size)

    def test_terminal_false(self):
        report = make_valid_report()
        report["terminal"] = False
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_terminal_string(self):
        report = make_valid_report()
        report["terminal"] = "true"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_failed_status(self):
        report = make_valid_report()
        report["status"] = "failed"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_running_status(self):
        report = make_valid_report()
        report["status"] = "running"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_quality_verdict_not_unverified(self):
        report = make_valid_report()
        report["qualityVerdict"] = "pass"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_wrong_run_id(self):
        report = make_valid_report()
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id="other", input_sha256=INPUT_SHA,
                            model_sha256=MODEL_SHA, source_hashes=SOURCE_HASHES,
                            output_size=OUTPUT_SIZE)

    def test_wrong_input_sha(self):
        report = make_valid_report()
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id=RUN_ID, input_sha256="z" * 64,
                            model_sha256=MODEL_SHA, source_hashes=SOURCE_HASHES,
                            output_size=OUTPUT_SIZE)

    def test_wrong_model_sha(self):
        report = make_valid_report()
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id=RUN_ID, input_sha256=INPUT_SHA,
                            model_sha256="z" * 64, source_hashes=SOURCE_HASHES,
                            output_size=OUTPUT_SIZE)

    def test_wrong_source_hashes(self):
        report = make_valid_report()
        with self.assertRaises(ReportValidationError):
            validate_report(report, run_id=RUN_ID, input_sha256=INPUT_SHA,
                            model_sha256=MODEL_SHA,
                            source_hashes={"x": "c" * 64},
                            output_size=OUTPUT_SIZE)

    def test_duplicate_kinds(self):
        report = make_valid_report()
        report["runs"][1]["kind"] = "nearest"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_out_of_order_kinds(self):
        report = make_valid_report()
        report["runs"][0]["kind"] = "bilinear"
        report["runs"][1]["kind"] = "nearest"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_wrong_dimensions(self):
        report = make_valid_report()
        report["runs"][0]["width"] = OUTPUT_SIZE[0] + 1
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_wrong_frame_count(self):
        report = make_valid_report()
        report["runs"][0]["frames"] = 47
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_wrong_completed_frames(self):
        report = make_valid_report()
        report["runs"][1]["completedFrames"] = 49
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_schema_version_bool(self):
        report = make_valid_report()
        report["schemaVersion"] = True
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_schema_version_string(self):
        report = make_valid_report()
        report["schemaVersion"] = "1"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_pid_string(self):
        report = make_valid_report()
        report["pid"] = "1234"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_pid_nonpositive(self):
        report = make_valid_report()
        report["pid"] = 0
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_missing_top_level_key(self):
        report = make_valid_report()
        del report["status"]
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_runs_not_list(self):
        report = make_valid_report()
        report["runs"] = {}
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_run_not_dict(self):
        report = make_valid_report()
        report["runs"][0] = []
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_width_not_int(self):
        report = make_valid_report()
        report["runs"][0]["width"] = "1280"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_output_bytes_string(self):
        report = make_valid_report()
        report["runs"][0]["outputBytes"] = "100000"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_codecs_wrong_length(self):
        report = make_valid_report()
        report["runs"][0]["codecs"] = _codec()[:1]
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_codec_not_decoder_first(self):
        report = make_valid_report()
        report["runs"][0]["codecs"][0]["encoder"] = True
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_codec_not_encoder_second(self):
        report = make_valid_report()
        report["runs"][0]["codecs"][1]["encoder"] = False
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_codec_empty_name(self):
        report = make_valid_report()
        report["runs"][0]["codecs"][1]["name"] = ""
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_mismatched_codec(self):
        report = make_valid_report()
        report["runs"][2]["codecs"][1]["name"] = "c2.qcom.avc.encoder"
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_malformed_hash_uppercase(self):
        report = make_valid_report()
        report["runs"][0]["outputSha256"] = "E" * 64
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_malformed_hash_length(self):
        report = make_valid_report()
        report["runs"][0]["outputSha256"] = "a" * 63
        with self.assertRaises(ReportValidationError):
            call(report)

    def test_source_inventory_missing(self):
        report = make_valid_report()
        del report["sourceInventory"]
        with self.assertRaises(ReportValidationError):
            call(report)


if __name__ == "__main__":
    unittest.main()
