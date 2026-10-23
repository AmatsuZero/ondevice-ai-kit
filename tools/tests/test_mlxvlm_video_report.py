import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import mlxvlm_video_report as report


def valid_run():
    manifest = json.loads((ROOT / "apple-mlx/Sources/ODAIKitAppleMLXVLM/Resources/smolvlm2-model.json").read_text())
    def time(value, timescale): return {"value": value, "timescale": timescale}
    metadata = {"snapshotSHA256": "9786524a69579ffd69d75c49048a566f31eaec090b1487b69aa4ce6d55e1ee51",
        "actualTimes": [time(v,14848) for v in [0,3584,7168,10752,14848,18432,22016,25600]],
        "requestedTimes": [time(v,4) for v in range(8)], "clipDuration": time(2,1),
        "sampledSpan": time(25600,14848), "widths": [160]*8, "heights": [90]*8}
    runs = []
    for kind in ["success", "cancelled", "reentry"]:
        names = ["begin", "decoded", "modelLoaded", "prepareStarted", "prepared", "update"]
        if kind == "cancelled": names.append("cancelRequested")
        names += ["snapshotClosed", "returned", "drained"]
        runs.append({"kind": kind, "state": "cancelled" if kind == "cancelled" else "success", "pid": 123,
            "output": "" if kind == "cancelled" else "A moving shape.", "metadata": copy.deepcopy(metadata),
            "events": [{"name": name, "ms": i*10} for i,name in enumerate(names)],
            "loadMs": 5, "prepareMs": 5, "preparedFrameCount": 8, "snapshotRemoved": True})
    return {"schemaVersion": 1, "state": "success", "runId": "9f9fc798-de49-4ea2-9d1b-f8368ef0d36e",
        "sourceCommit": "eebc670" + "0"*33, "device": "iPhone17,2", "OS": "Version 27.0.1 (Build 24A1)", "pid": 123,
        "actualBackend": "mlx.smolvlm2-500m", "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
        "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268", "runtimeRevision": "6e338e5df49424fc0db7c6d58184d622f92ed18a",
        "fixtureHash": metadata["snapshotSHA256"], "prompt": "Describe what happens in this video in one brief sentence.", "maxOutputTokens": 32,
        "modelFiles": [{"path": f["path"], "bytes": f["bytes"], "sha256": f["sha256"]} for f in manifest["files"]],
        "scope": "fixed-synthetic-video-lifecycle-only", "unverified": ["naturalVideoQuality", "metalGpuPeak", "energy", "minimumOS", "androidBackend"],
        "runs": runs, "gates": {name: True for name in ["leaseProtectedBeforeGeneration", "leaseProtectedAfterCancellation", "leaseReleasedAfterClose", "closedRejectsVideo", "closedRejectsImage", "sourceUnchanged", "noOwnedSnapshotsRemaining"]}}


class VideoReportTests(unittest.TestCase):
    def reject(self, mutate):
        raw = valid_run(); mutate(raw)
        with self.assertRaises(ValueError): report.summarize(raw)

    def test_valid_evidence_keeps_clip_span_distinct_without_quality_promotion(self):
        result = report.summarize(valid_run())
        self.assertEqual(result["state"], "success")
        self.assertEqual(result["runs"][0]["metadata"]["clipDuration"], {"value": 2, "timescale": 1})
        self.assertEqual(result["runs"][0]["metadata"]["sampledSpan"], {"value": 25600, "timescale": 14848})
        self.assertNotIn("quality", result)
        self.assertIn("naturalVideoQuality", result["unverified"])

    def test_missing_or_changed_fixed_identity_and_failed_state_are_rejected(self):
        for key in ["runId", "sourceCommit", "device", "OS", "pid", "actualBackend", "modelId", "modelRevision", "runtimeRevision", "fixtureHash", "modelFiles", "runs", "gates", "unverified"]:
            with self.subTest(key=key): self.reject(lambda r: r.pop(key))
        for key in ["modelId", "modelRevision", "runtimeRevision", "fixtureHash", "actualBackend", "prompt", "sourceCommit", "device", "runId"]:
            with self.subTest(key=key): self.reject(lambda r: r.update({key: "wrong"}))
        self.reject(lambda r: r.update(state="failed"))
        self.reject(lambda r: r.update(errorType="RuntimeError"))
        self.reject(lambda r: r.update(pid=True))
        self.reject(lambda r: r.update(schemaVersion=True))
        self.reject(lambda r: r.update(maxOutputTokens=True))

    def test_all_fourteen_actual_model_hashes_and_sizes_are_required(self):
        self.reject(lambda r: r["modelFiles"].pop())
        self.reject(lambda r: r["modelFiles"][0].update(sha256="0"*64))
        self.reject(lambda r: r["modelFiles"][0].update(bytes=1))
        self.reject(lambda r: r["modelFiles"].append(r["modelFiles"][0]))

    def test_exact_frame_identity_and_rational_time_semantics_are_required(self):
        for field in ["snapshotSHA256", "requestedTimes", "actualTimes", "clipDuration", "sampledSpan", "widths", "heights"]:
            self.reject(lambda r: r["runs"][0]["metadata"].pop(field))
        self.reject(lambda r: r["runs"][0]["metadata"].update(snapshotSHA256="0"*64))
        self.reject(lambda r: r["runs"][0]["metadata"]["actualTimes"].pop(6))
        self.reject(lambda r: r["runs"][0]["metadata"]["actualTimes"].reverse())
        self.reject(lambda r: r["runs"][0]["metadata"]["actualTimes"][6].update(value=22017))
        self.reject(lambda r: r["runs"][0]["metadata"]["requestedTimes"][-1].update(value=8))
        self.reject(lambda r: r["runs"][0]["metadata"]["clipDuration"].update(timescale=0))
        self.reject(lambda r: r["runs"][0]["metadata"].update(sampledSpan={"value":2,"timescale":1}))
        self.reject(lambda r: r["runs"][0]["metadata"]["widths"].__setitem__(0,513))
        self.reject(lambda r: r["runs"][0].update(preparedFrameCount=7))

    def test_missing_phase_cancel_drain_cleanup_or_lease_gate_is_rejected(self):
        for name in ["begin", "decoded", "modelLoaded", "prepareStarted", "prepared", "update", "cancelRequested", "snapshotClosed", "returned", "drained"]:
            self.reject(lambda r: r["runs"][1].update(events=[e for e in r["runs"][1]["events"] if e["name"] != name]))
        for key in valid_run()["gates"]:
            self.reject(lambda r: r["gates"].update({key: False}))
        self.reject(lambda r: r["runs"][1].update(snapshotRemoved=False))
        self.reject(lambda r: r["runs"][0].update(events=r["runs"][0]["events"] + [{"name":"update","ms":100}]))
        self.reject(lambda r: r["runs"][1]["events"][6].update(ms=1000))
        self.reject(lambda r: r["runs"][0].update(loadMs=float("nan")))
        self.reject(lambda r: r["runs"][0].update(prepareMs=9999))

    def test_empty_outputs_changed_pid_order_and_inferred_quality_are_rejected(self):
        self.reject(lambda r: r["runs"][0].update(output=" "))
        self.reject(lambda r: r["runs"][2].update(output=""))
        self.reject(lambda r: r["runs"][2].update(pid=124))
        self.reject(lambda r: r["runs"].reverse())
        self.reject(lambda r: r.update(quality={"passed":True}))
        self.reject(lambda r: r["runs"][0].update(naturalQuality=True))
        self.reject(lambda r: r["unverified"].remove("naturalVideoQuality"))

    def test_cli_roundtrip_and_bad_json_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            raw=Path(d)/"raw.json"; out=Path(d)/"summary.json"
            raw.write_text(json.dumps(valid_run()))
            cmd=[sys.executable,str(ROOT/"tools/mlxvlm_video_report.py"),str(raw),"--output",str(out)]
            result=subprocess.run(cmd,capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertEqual(json.loads(out.read_text())["pid"],123)
            raw.write_text('{"schemaVersion":1,"schemaVersion":1}')
            self.assertNotEqual(subprocess.run(cmd,capture_output=True).returncode,0)
