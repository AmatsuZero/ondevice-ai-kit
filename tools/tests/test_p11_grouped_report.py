import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools import p11_grouped_report as report


def face_run(run_id="face-1", device="phone"):
    return {
        "schemaVersion": 1,
        "runId": run_id,
        "device": device,
        "OS": "15",
        "sdkCommit": "a" * 40,
        "capability": "face",
        "actualBackend": "apple.vision.face",
        "modelRevision": None,
        "preprocessRevision": "upright-v1",
        "fixtureHash": "f" * 64,
        "units": "ms",
        "timingsMs": {
            "coldStart": [],
            "modelLoad": [],
            "warmup": [2],
            "inference": [3, 4],
        },
        "peakMemory": None,
        "energy": None,
        "energyStatus": "unmeasured",
        "thermal": None,
        "measurementMethod": "native-monotonic",
        "validationStatus": "device",
        "quality": {},
    }


def mlx_run(run_id="mlx-1"):
    iterations = [
        {
            "index": index,
            "loadMs": 100.0 + index,
            "firstUpdateMs": 140.0 + index,
            "totalMs": 180.0 + index,
            "output": "A red square on a white background.",
            "quality": {"rubric": "synthetic-red-square-v1", "passed": True},
        }
        for index in range(1, 6)
    ]
    return {
        "schemaVersion": 1,
        "runId": run_id,
        "sdkCommit": "b" * 40,
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


class GroupedReportTests(unittest.TestCase):
    def write_manifest(self, root, sources, readiness=None):
        if readiness is None:
            readiness = {
                "schemaVersion": 1,
                "rows": [
                    {"capability": "face-detection", "status": "pass-bounded", "unverified": ["memory"]},
                    {"capability": "multimodal-apple", "status": "pass-bounded", "unverified": ["quality"]},
                    {"capability": "speech-tts", "status": "pass-bounded", "unverified": ["quality"]},
                ],
                "p11Gate": {"status": "open", "finalReleaseConclusion": "not-ready"},
            }
        readiness_path = root / "readiness.json"
        readiness_path.write_text(json.dumps(readiness))
        manifest_sources = []
        for index, (name, payload, kind, capability) in enumerate(sources):
            path = root / f"{index}-{name}.json"
            path.write_text(json.dumps(payload))
            manifest_sources.append({
                "kind": kind,
                "path": path.name,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                "p11Capability": capability,
                **({"expectedSamples": 5} if kind == "mlxvlm" else {}),
            })
        manifest = {
            "schemaVersion": 1,
            "readiness": {
                "path": readiness_path.name,
                "sha256": hashlib.sha256(readiness_path.read_bytes()).hexdigest(),
            },
            "sources": manifest_sources,
        }
        return manifest

    def test_recomputes_supported_groups_and_explicit_gaps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [
                ("face", face_run(), "face", "face-detection"),
                ("mlx", mlx_run(), "mlxvlm", "multimodal-apple"),
            ])

            result = report.generate(manifest, root)

            self.assertEqual(result["schemaVersion"], 1)
            self.assertEqual(len(result["groups"]), 2)
            face = next(item for item in result["groups"] if item["p11Capability"] == "face-detection")
            self.assertEqual(face["timingsMs"]["inference"], {"count": 2, "p50": 3, "p95": 4})
            self.assertEqual(face["identity"]["sdkCommit"], "a" * 40)
            self.assertEqual(face["identity"]["fixtureHash"], "f" * 64)
            self.assertEqual(face["quality"], {"status": "unverified", "reason": "source quality object is empty"})
            self.assertEqual(face["unverified"], ["quality", "peakMemory", "energy", "gpuPeak"])
            mlx = next(item for item in result["groups"] if item["p11Capability"] == "multimodal-apple")
            self.assertEqual(mlx["timingsMs"]["total"], {"count": 5, "p50": 183.0, "p95": 185.0})
            self.assertEqual(mlx["quality"], {"status": "pass-bounded", "rubric": "synthetic-red-square-v1", "passed": 5, "total": 5, "scope": "synthetic-fixture-only"})
            self.assertEqual(mlx["identity"]["runtimeRevision"], "6e338e5df49424fc0db7c6d58184d622f92ed18a")
            self.assertEqual(mlx["unverified"], ["metalGpuPeak", "energy", "naturalImageQuality"])
            self.assertEqual(result["coverage"], [
                {"capability": "face-detection", "readinessStatus": "pass-bounded", "reportStatus": "reported", "unverified": ["memory"]},
                {"capability": "multimodal-apple", "readinessStatus": "pass-bounded", "reportStatus": "reported", "unverified": ["quality"]},
                {"capability": "speech-tts", "readinessStatus": "pass-bounded", "reportStatus": "unverified", "unverified": ["quality"]},
            ])
            self.assertEqual(result["inputs"]["readiness"], manifest["readiness"])
            self.assertEqual(result["p11Gate"], {"status": "open", "finalReleaseConclusion": "not-ready"})

    def test_duplicate_run_ids_are_rejected_across_source_kinds(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [
                ("face", face_run("same"), "face", "face-detection"),
                ("mlx", mlx_run("same"), "mlxvlm", "multimodal-apple"),
            ])
            with self.assertRaisesRegex(ValueError, "duplicate runId: same"):
                report.generate(manifest, root)

    def test_face_memory_measurement_is_preserved_without_quality_promotion(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            measured = face_run()
            measured["peakMemory"] = {"bytes": 128, "method": "process-rss"}
            manifest = self.write_manifest(root, [("face", measured, "face", "face-detection")])

            group = report.generate(manifest, root)["groups"][0]

            self.assertEqual(group["memory"], {"status": "observed", "bytes": 128, "method": "process-rss"})
            self.assertNotIn("peakMemory", group["unverified"])
            self.assertEqual(group["quality"]["status"], "unverified")

    def test_mlx_quality_failure_is_not_reported_as_a_bounded_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            failed = mlx_run()
            failed["iterations"][0]["output"] = "A blue circle."
            failed["iterations"][0]["quality"]["passed"] = False
            manifest = self.write_manifest(root, [("mlx", failed, "mlxvlm", "multimodal-apple")])

            quality = report.generate(manifest, root)["groups"][0]["quality"]

            self.assertEqual(quality["status"], "fail")
            self.assertEqual((quality["passed"], quality["total"]), (4, 5))

    def test_source_capability_must_exist_in_readiness(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [("face", face_run(), "face", "unknown-capability")])
            with self.assertRaisesRegex(ValueError, "not present in readiness"):
                report.generate(manifest, root)

    def test_ready_gate_is_rejected_while_readiness_rows_have_gaps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            readiness = {
                "schemaVersion": 1,
                "rows": [{"capability": "face-detection", "status": "pass-bounded", "unverified": ["energy"]}],
                "p11Gate": {"status": "closed", "finalReleaseConclusion": "ready"},
            }
            manifest = self.write_manifest(
                root, [("face", face_run(), "face", "face-detection")], readiness=readiness
            )
            with self.assertRaisesRegex(ValueError, "must remain open/not-ready"):
                report.generate(manifest, root)

    def test_changed_source_and_unsafe_output_fail_before_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [("face", face_run(), "face", "face-detection")])
            source = root / "0-face.json"
            source.write_text(source.read_text() + "\n")
            output = root / "report.json"
            output.write_bytes(b"keep\n")
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                report.write_report(manifest, root, output)
            self.assertEqual(output.read_bytes(), b"keep\n")
            manifest = self.write_manifest(root, [("face", face_run(), "face", "face-detection")])
            source = root / "0-face.json"
            output = root / "output.json"
            output.write_bytes(b"keep\n")
            manifest["output"] = output.name
            manifest["sources"][0]["path"] = output.name
            with self.assertRaisesRegex(ValueError, "output must not overwrite"):
                report.write_report(manifest, root, output)
            self.assertEqual(output.read_bytes(), b"keep\n")

    def test_direct_cli_runs_and_cannot_replace_the_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [("face", face_run(), "face", "face-detection")])
            manifest_path = root / "manifest.json"
            manifest_path.write_text(json.dumps(manifest))
            output = root / "report.json"
            command = [sys.executable, str(Path(report.__file__)), str(manifest_path),
                       "--repo-root", str(root), "--output", str(output)]
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(output.is_file())
            original = manifest_path.read_bytes()
            command[-1] = str(manifest_path)
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("output must not overwrite", result.stderr)
            self.assertEqual(manifest_path.read_bytes(), original)

    def test_output_symlink_and_hardlink_aliases_preserve_inputs(self):
        for target_name in ("0-face.json", "manifest.json"):
            for alias in ("symlink", "hardlink"):
                with self.subTest(target=target_name, alias=alias), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    manifest = self.write_manifest(root, [("face", face_run(), "face", "face-detection")])
                    manifest_path = root / "manifest.json"
                    manifest_path.write_text(json.dumps(manifest))
                    target = root / target_name
                    original = target.read_bytes()
                    output = root / "output.json"
                    if alias == "symlink":
                        output.symlink_to(target)
                    else:
                        os.link(target, output)
                    with self.assertRaisesRegex(ValueError, "output must not overwrite"):
                        report.write_report(manifest, root, output, manifest_path=manifest_path)
                    self.assertEqual(target.read_bytes(), original)

    def test_absolute_and_escaping_source_paths_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [("face", face_run(), "face", "face-detection")])
            for unsafe in (str(root / "0-face.json"), "../outside.json"):
                with self.subTest(unsafe=unsafe):
                    changed = json.loads(json.dumps(manifest))
                    changed["sources"][0]["path"] = unsafe
                    with self.assertRaisesRegex(ValueError, "repository-relative|escapes repository"):
                        report.generate(changed, root)


if __name__ == "__main__":
    unittest.main()
