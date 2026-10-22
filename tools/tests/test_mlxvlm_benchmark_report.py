import copy
import unittest

from tools import mlxvlm_benchmark_report as report


def valid_run():
    iterations = []
    for index in range(5):
        load = 100.0 + index
        first = 140.0 + index
        total = 180.0 + index
        iterations.append({
            "index": index + 1,
            "loadMs": load,
            "firstUpdateMs": first,
            "totalMs": total,
            "output": "A red square on a white background.",
            "quality": {"rubric": "synthetic-red-square-v1", "passed": True},
        })
    return {
        "schemaVersion": 1,
        "runId": "run-1",
        "sdkCommit": "a" * 40,
        "device": "iPhone17,2",
        "OS": "Version 27.0.1 (Build 24A446)",
        "actualBackend": "mlx.smolvlm2-500m",
        "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
        "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268",
        "runtimeRevision": "6e338e5df49424fc0db7c6d58184d622f92ed18a",
        "fixtureHash": "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606",
        "prompt": "Describe this image in one brief sentence.",
        "maxOutputTokens": 32,
        "warmup": {"state": "success"},
        "iterations": iterations,
        "memory": {
            "method": "process-resident-mach-task-basic-info-10ms",
            "baselineBytes": 100,
            "peakBytes": 250,
            "deltaBytes": 150,
        },
        "unverified": ["metalGpuPeak", "energy", "naturalImageQuality"],
        "qualityScope": "synthetic-fixture-only",
    }


class MLXVLMBenchmarkReportTests(unittest.TestCase):
    def test_valid_run_summarizes_nearest_rank_timings_and_rss(self):
        summary = report.summarize(valid_run())
        self.assertEqual(summary["sampleCount"], 5)
        self.assertEqual(summary["timingsMs"]["load"], {"count": 5, "p50": 102.0, "p95": 104.0})
        self.assertEqual(summary["timingsMs"]["firstUpdate"]["p50"], 142.0)
        self.assertEqual(summary["timingsMs"]["total"]["p95"], 184.0)
        self.assertEqual(summary["memory"]["deltaBytes"], 150)
        self.assertEqual(summary["quality"], {"rubric": "synthetic-red-square-v1", "passed": 5, "total": 5})
        self.assertEqual(summary["unverified"], ["metalGpuPeak", "energy", "naturalImageQuality"])

    def test_rejects_identity_drift_and_wrong_sample_count(self):
        for key, value in (
            ("actualBackend", "other"),
            ("modelRevision", "other"),
            ("runtimeRevision", "other"),
            ("fixtureHash", "0" * 64),
        ):
            run = valid_run()
            run[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                report.validate(run)
        run = valid_run()
        run["iterations"].pop()
        with self.assertRaisesRegex(ValueError, "five measured iterations"):
            report.validate(run)

    def test_rejects_misordered_or_non_finite_timings(self):
        for field, value in (("loadMs", 181.0), ("firstUpdateMs", 99.0), ("totalMs", float("nan"))):
            run = valid_run()
            run["iterations"][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                report.validate(run)

    def test_rejects_resource_or_quality_overclaims(self):
        run = valid_run()
        run["memory"]["method"] = "Metal GPU peak"
        with self.assertRaisesRegex(ValueError, "process RSS"):
            report.validate(run)
        run = valid_run()
        run["qualityScope"] = "natural-images-passed"
        with self.assertRaisesRegex(ValueError, "synthetic fixture"):
            report.validate(run)
        run = valid_run()
        run["unverified"].remove("metalGpuPeak")
        with self.assertRaisesRegex(ValueError, "unverified"):
            report.validate(run)

    def test_rejects_false_rubric_when_output_contains_required_concepts(self):
        run = valid_run()
        run["iterations"][0]["quality"]["passed"] = False
        with self.assertRaisesRegex(ValueError, "rubric result"):
            report.validate(run)

    def test_rejects_missing_source_identity_and_failed_run(self):
        for key, value in (("sdkCommit", "unverified"), ("errorType", "StoreFailure")):
            run = valid_run()
            run[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                report.validate(run)

    def test_missing_rss_remains_unverified_without_invented_bytes(self):
        run = valid_run()
        run["memory"] = {"method": report.MEMORY_METHOD, "state": "unverified", "reason": "task_info failed"}
        self.assertEqual(report.summarize(run)["memory"]["state"], "unverified")
        run["memory"]["peakBytes"] = 0
        with self.assertRaises(ValueError):
            report.validate(run)


if __name__ == "__main__":
    unittest.main()
