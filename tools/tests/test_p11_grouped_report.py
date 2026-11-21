import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import p11_grouped_report as report
from tools.probes import android_asr_resource_report, android_litertlm_resource_report
from tools.tests.test_android_asr_resource_report import fixture as android_asr_resource_run
from tools.tests.test_android_litertlm_resource_report import fixture as android_litertlm_resource_run


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


def tts_resource_run():
    return {
        "schemaVersion": 1, "runId": "tts-session", "device": "iPhone", "OS": "27.0.1",
        "validationStatus": "device",
        "f11SampleProfile": "one warm-up then five sequential fixed-text TTS requests",
        "f11ResourceMethod": "mach_task_basic_info.resident_size sampled every approximately 10 ms; sampled peak, not true peak",
        "tts": {"status": "success", "backendId": "apple.avspeech", "selectedLocale": "en-US",
                "selectedVoice": "com.apple.voice.compact.en-US.Samantha",
                "networkBehavior": "unmeasured"},
        "f11ResourceRuns": [
            {"runId": f"tts-{index}", "sampleIndex": index, "status": "success", "error": None,
             "durationMs": float(index + 1), "rssStartBytes": 100, "rssEndBytes": 110,
             "rssPeakSampledBytes": 200 + index, "rssSampleCount": 10}
            for index in range(5)
        ],
    }


def video_sr_run():
    def result(backend, timings, peak):
        return {
            "schemaVersion": 1, "validationStatus": "device", "state": "success",
            "backendId": backend, "inputFileSha256": "c" * 64,
            "inputFrameCount": 48, "outputFrameCount": 48,
            "outputWidth": 1280, "outputHeight": 720, "timestampsMonotonic": True,
            "frameProcessingSeconds": timings,
            "residentMemoryBaselineBytes": 100, "residentMemoryPeakBytes": peak,
            "residentMemoryEndingBytes": 110, "residentMemoryPeakDeltaBytes": peak - 100,
            "memoryMeasurement": "process resident_size sampled after each frame processing and encoding",
            "output": "sr.mov" if backend == "apple.videotoolbox.sr" else "bicubic.mov",
        }

    raw = result("apple.videotoolbox.sr", [index / 1000 for index in range(1, 49)], 300)
    raw.update({
        "runId": "video-session", "device": "iPhone17,2", "OS": "27.0.1",
        "colorContract": "probe-sRGB-v1; SR model transfer function unverified",
        "baseline": result("apple.coreimage.bicubic", [index / 10000 for index in range(1, 49)], 140),
    })
    return raw


def sdk_video_sr_run():
    return {
        "schemaVersion": 1, "state": "success", "scope": "isolated-public-sdk-consumer",
        "runId": "sdk-session", "deviceModel": "iPhone17,2", "osVersion": "27.0.1",
        "backendId": "apple.videotoolbox.sr", "inputSHA256": "", "audioPolicy": "discard",
        "measurement": "request wall time and approximately 10 ms sampled process RSS",
        "runs": [
            {"index": index, "runId": f"sdk-{index}", "backendId": "apple.videotoolbox.sr",
             "outputFile": f"sdk-{index}.mov", "outputSHA256": "", "width": 1280,
             "height": 720, "frameCount": 48, "durationSeconds": 2,
             "audioTrackCount": 0, "wallMilliseconds": float(index + 1),
             "rssBeforeBytes": 100, "rssAfterBytes": 110,
             "rssSampledPeakBytes": 200 + index, "rssSampleCount": 10}
            for index in range(1, 6)
        ],
    }


def sdk_file_asr_run():
    return {
        "schemaVersion": 1, "status": "success", "runId": "asr-session",
        "scope": "SpeechRecognitionClient.recognize -> AppleSpeechRecognizerBackend.recognize",
        "device": "iPhone", "OS": "27.0.1", "processIdentifier": 1234,
        "bundleId": "io.ondeviceai.probe.f11speech", "backendId": "apple.speech.recognizer",
        "inputSHA256": "", "audioSource": "bundled-asr-fixture.caf", "locale": "en-US",
        "expectedText": "The blue bicycle is beside the green house.",
        "requiresOnDeviceRecognition": True, "microphoneUsed": False,
        "networkBehavior": "unmeasured", "clientClosed": True,
        "measurementMethod": "process RSS sampled approximately every 10 ms",
        "sampleProfile": "one warm-up then five sequential fixed-file SDK recognitions",
        "warmup": {"status": "success", "actualBackendId": "apple.speech.recognizer",
                   "backendIdentityMatch": True, "normalizedMatch": True,
                   "transcript": "The blue bicycle is beside the greenhouse"},
        "resourceRuns": [
            {"sampleIndex": index, "runId": f"asr-{index}", "status": "success",
             "actualBackendId": "apple.speech.recognizer", "backendIdentityMatch": True,
             "normalizedMatch": True, "transcript": "The blue bicycle is beside the greenhouse",
             "durationMs": float(index + 1), "rssStartBytes": 100,
             "rssEndBytes": 110, "rssPeakSampledBytes": 200 + index,
             "rssSampleCount": 10}
            for index in range(5)
        ],
    }


class GroupedReportTests(unittest.TestCase):
    def android_asr_manifest(self, root):
        raw = android_asr_resource_run()
        raw.update(deviceModel="TECNO KM9", sdkInt=35)
        readiness = {"schemaVersion": 1, "rows": [
            {"capability": "speech-asr-android", "status": "pass-bounded", "unverified": ["quality"]}],
            "p11Gate": {"status": "open", "finalReleaseConclusion": "not-ready"}}
        manifest = self.write_manifest(root, [("android-asr", raw, "asr-android-resource",
                                              "speech-asr-android")], readiness=readiness)
        entry = manifest["sources"][0]
        input_path = root / "english.pcm"
        input_path.write_bytes(b"synthetic fixture bytes for hash-binding tests")
        input_hash = hashlib.sha256(input_path.read_bytes()).hexdigest()
        raw["inputSHA256"] = input_hash
        raw_path = root / entry["path"]
        raw_path.write_text(json.dumps(raw))
        entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
        entry["input"] = {"path": input_path.name, "sha256": input_hash}
        provenance = {"schemaVersion": 1, "runtimeScope": "installed-ordinary-app-fixed-input-resource",
                      "appSha256": "a" * 64, "installedAppSourceCommit": None,
                      "frozenBuildSourceCommit": "b" * 40,
                      "sourceHashes": {entry["path"]: entry["sha256"], input_path.name: input_hash}}
        provenance_path = root / "source-manifest.json"
        provenance_path.write_text(json.dumps(provenance))
        entry["sourceManifest"] = {"path": provenance_path.name,
                                   "sha256": hashlib.sha256(provenance_path.read_bytes()).hexdigest()}
        # The reducer's independent tests enforce the real pinned PCM hash. Here
        # only the fixture identity is substituted; real hash checks still execute.
        with patch.object(android_asr_resource_report, "INPUT_SHA256", input_hash):
            summary = android_asr_resource_report.summarize(raw)
        summary.update(rawReportSha256=entry["sha256"],
                       sourceManifestSha256=entry["sourceManifest"]["sha256"])
        summary_path = root / "asr-summary.json"
        summary_path.write_text(json.dumps(summary))
        entry["summary"] = {"path": summary_path.name,
                            "sha256": hashlib.sha256(summary_path.read_bytes()).hexdigest()}
        return manifest

    def test_android_asr_adds_thirty_measurements_with_runtime_boundary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.android_asr_manifest(root)
            with patch.object(android_asr_resource_report, "INPUT_SHA256", manifest["sources"][0]["input"]["sha256"]):
                result = report.generate(manifest, root)
        group = result["groups"][0]
        self.assertEqual(group["p11Capability"], "speech-asr-android")
        self.assertEqual(len(group["runIds"]), 30)
        self.assertEqual(len(set(group["runIds"])), 30)
        self.assertEqual(group["timingsMs"]["requestWall"], {"count": 30, "p50": 15.0, "p95": 29.0})
        self.assertIsNone(group["identity"]["installedAppSourceCommit"])
        self.assertIn("current SDK cleanup changes", group["unverified"])
        self.assertEqual(group["quality"]["status"], "unverified")
        self.assertEqual(result["coverage"][0]["reportStatus"], "reported")
        self.assertEqual(result["p11Gate"]["status"], "open")

    def test_android_asr_rejects_summary_and_input_identity_changes(self):
        for corruption in ("summary", "input", "source-manifest", "duplicate", "alias"):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                manifest = self.android_asr_manifest(root)
                entry = manifest["sources"][0]
                if corruption == "summary":
                    path = root / entry["summary"]["path"]
                    value = json.loads(path.read_text())
                    value["durationMs"]["p50"] = 999
                    path.write_text(json.dumps(value))
                    entry["summary"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                elif corruption == "input":
                    (root / entry["input"]["path"]).write_bytes(b"changed input")
                elif corruption == "source-manifest":
                    path = root / entry["sourceManifest"]["path"]
                    value = json.loads(path.read_text())
                    value["sourceHashes"][entry["path"]] = "f" * 64
                    path.write_text(json.dumps(value))
                    entry["sourceManifest"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                elif corruption == "alias":
                    entry["sourceManifest"] = dict(entry["summary"])
                else:
                    manifest["sources"].append(dict(entry))
                with patch.object(android_asr_resource_report, "INPUT_SHA256", entry["input"]["sha256"]):
                    with self.assertRaises(ValueError):
                        report.generate(manifest, root)

    def android_litertlm_manifest(self, root):
        raw = android_litertlm_resource_run()
        raw.update(deviceModel="TECNO KM9", sdkInt=35)
        readiness = {"schemaVersion": 1, "rows": [
            {"capability": "multimodal-android-litertlm", "status": "pass-bounded", "unverified": ["quality"]}],
            "p11Gate": {"status": "open", "finalReleaseConclusion": "not-ready"}}
        manifest = self.write_manifest(root, [("android-litertlm", raw, "litertlm-android-resource",
                                              "multimodal-android-litertlm")], readiness=readiness)
        entry = manifest["sources"][0]
        input_path = root / "red.png"
        input_path.write_bytes(b"synthetic fixture bytes for hash-binding tests")
        input_hash = hashlib.sha256(input_path.read_bytes()).hexdigest()
        raw["inputSHA256"] = input_hash
        raw_path = root / entry["path"]
        raw_path.write_text(json.dumps(raw))
        entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
        entry["input"] = {"path": input_path.name, "sha256": input_hash}
        provenance = {"schemaVersion": 1, "runtimeScope": "installed-ordinary-app-fixed-input-resource",
                      "appSha256": "a" * 64, "installedAppSourceCommit": None,
                      "frozenBuildSourceCommit": "b" * 40,
                      "modelSHA256": raw["modelSHA256"], "modelRevision": raw["modelRevision"],
                      "sourceHashes": {entry["path"]: entry["sha256"], input_path.name: input_hash}}
        provenance_path = root / "source-manifest.json"
        provenance_path.write_text(json.dumps(provenance))
        entry["sourceManifest"] = {"path": provenance_path.name,
                                   "sha256": hashlib.sha256(provenance_path.read_bytes()).hexdigest()}
        # The reducer's independent tests enforce the real pinned image hash. Here
        # only the fixture identity is substituted; real hash checks still execute.
        with patch.object(android_litertlm_resource_report, "INPUT_SHA256", input_hash):
            summary = android_litertlm_resource_report.summarize(raw)
        summary.update(rawReportSha256=entry["sha256"],
                       sourceManifestSha256=entry["sourceManifest"]["sha256"])
        summary_path = root / "litertlm-summary.json"
        summary_path.write_text(json.dumps(summary))
        entry["summary"] = {"path": summary_path.name,
                            "sha256": hashlib.sha256(summary_path.read_bytes()).hexdigest()}
        return manifest

    def test_android_litertlm_adds_five_measurements_with_runtime_boundary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.android_litertlm_manifest(root)
            with patch.object(android_litertlm_resource_report, "INPUT_SHA256", manifest["sources"][0]["input"]["sha256"]):
                result = report.generate(manifest, root)
        group = result["groups"][0]
        self.assertEqual(group["p11Capability"], "multimodal-android-litertlm")
        self.assertEqual(len(group["runIds"]), 5)
        self.assertEqual(len(set(group["runIds"])), 5)
        self.assertEqual(group["timingsMs"]["requestWall"], {"count": 5, "p50": 3.0, "p95": 5.0})
        self.assertIsNone(group["identity"]["installedAppSourceCommit"])
        self.assertIn("current whole-App source identity", group["unverified"])
        self.assertEqual(group["quality"]["status"], "unverified")
        self.assertEqual(result["coverage"][0]["reportStatus"], "reported")
        self.assertEqual(result["p11Gate"]["status"], "open")

    def test_android_litertlm_rejects_summary_and_input_identity_changes(self):
        for corruption in ("summary", "input", "source-manifest", "duplicate", "alias"):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                manifest = self.android_litertlm_manifest(root)
                entry = manifest["sources"][0]
                if corruption == "summary":
                    path = root / entry["summary"]["path"]
                    value = json.loads(path.read_text())
                    value["durationMs"]["p50"] = 999
                    path.write_text(json.dumps(value))
                    entry["summary"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                elif corruption == "input":
                    (root / entry["input"]["path"]).write_bytes(b"changed input")
                elif corruption == "source-manifest":
                    path = root / entry["sourceManifest"]["path"]
                    value = json.loads(path.read_text())
                    value["sourceHashes"][entry["path"]] = "f" * 64
                    path.write_text(json.dumps(value))
                    entry["sourceManifest"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                elif corruption == "alias":
                    entry["sourceManifest"] = dict(entry["summary"])
                else:
                    manifest["sources"].append(dict(entry))
                with patch.object(android_litertlm_resource_report, "INPUT_SHA256", entry["input"]["sha256"]):
                    with self.assertRaises(ValueError):
                        report.generate(manifest, root)

    def test_android_litertlm_rejects_model_or_whole_app_provenance_claim(self):
        for key,value in [("modelSHA256","0"*64),("modelRevision","other"),
                          ("installedAppSourceCommit","a"*40),("schemaVersion",True)]:
            with self.subTest(key=key), tempfile.TemporaryDirectory() as directory:
                root=Path(directory);manifest=self.android_litertlm_manifest(root)
                entry=manifest["sources"][0];path=root/entry["sourceManifest"]["path"]
                data=json.loads(path.read_text());data[key]=value
                path.write_text(json.dumps(data));entry["sourceManifest"]["sha256"]=hashlib.sha256(path.read_bytes()).hexdigest()
                summary_path=root/entry["summary"]["path"];summary=json.loads(summary_path.read_text())
                summary["sourceManifestSha256"]=entry["sourceManifest"]["sha256"]
                summary_path.write_text(json.dumps(summary));entry["summary"]["sha256"]=hashlib.sha256(summary_path.read_bytes()).hexdigest()
                with patch.object(android_litertlm_resource_report,"INPUT_SHA256",entry["input"]["sha256"]):
                    with self.assertRaises(ValueError):report.generate(manifest,root)

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
        evidence_path = root / "evidence.json"
        evidence_path.write_text("{}\n")
        readiness.setdefault("p11Stop", "Do not release while evidence gaps remain.")
        readiness.setdefault("requiredBeforeP11", ["verify all source evidence"])
        readiness.setdefault("sourceHashes", {})["evidence.json"] = hashlib.sha256(
            evidence_path.read_bytes()
        ).hexdigest()
        for row in readiness["rows"]:
            row.setdefault("evidence", ["evidence.json"])
        readiness["p11Gate"].setdefault("rowsWithUnverifiedGaps", sum(bool(row["unverified"]) for row in readiness["rows"]))
        readiness["p11Gate"].setdefault("rowsNotTerminalPass", sum(bool(row["unverified"]) or row["status"] != "pass" for row in readiness["rows"]))
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

    def tts_manifest(self, root, raw=None):
        raw = raw or tts_resource_run()
        manifest = self.write_manifest(root, [("tts", raw, "tts-resource", "speech-tts")])
        summary = {
            "schemaVersion": 1, "kind": "f11-ios-tts-resource-run", "backend": "apple.avspeech",
            "sourceCommit": "a" * 40, "device": {"model": "iPhone", "os": "27.0.1", "udid": "device-1"},
            "locale": "en-US", "voice": "com.apple.voice.compact.en-US.Samantha",
            "rawReport": {"path": manifest["sources"][0]["path"],
                          "sha256": manifest["sources"][0]["sha256"]},
            "runs": {"actual": 5, "successful": 5},
        }
        path = root / "tts-summary.json"
        path.write_text(json.dumps(summary))
        manifest["sources"][0]["summary"] = {
            "path": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()
        }
        return manifest

    def video_manifest(self, root, raw=None):
        raw = raw or video_sr_run()
        readiness = {
            "schemaVersion": 1,
            "rows": [{"capability": "video-sr", "status": "pass-bounded", "unverified": ["subjective quality"]}],
            "p11Gate": {"status": "open", "finalReleaseConclusion": "not-ready"},
        }
        manifest = self.write_manifest(
            root, [("video", raw, "video-sr-resource", "video-sr")], readiness=readiness
        )
        entry = manifest["sources"][0]
        for label, name, payload in (
            ("input", "input.mp4", b"input"), ("reference", "reference.mkv", b"reference"),
            ("sr", "sr.mov", b"super-resolution"), ("bicubic", "bicubic.mov", b"bicubic"),
        ):
            path = root / name
            path.write_bytes(payload)
            data = {"path": path.name, "sha256": hashlib.sha256(payload).hexdigest()}
            if label in ("input", "reference"):
                entry[label] = data
            else:
                entry.setdefault("outputs", {})[label] = data
        raw["inputFileSha256"] = entry["input"]["sha256"]
        raw["baseline"]["inputFileSha256"] = entry["input"]["sha256"]
        raw_path = root / entry["path"]
        raw_path.write_text(json.dumps(raw))
        entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
        for label in ("sr", "bicubic"):
            quality = {
                "schemaVersion": 1, "measurement": "decoded-output-fidelity",
                "qualityVerdict": "unverified", "colorComparability": "matched-metadata",
                "comparedFrames": 48,
                "reference": {"path": entry["reference"]["path"], "decodedSha256": "a" * 64},
                "candidate": {"path": entry["outputs"][label]["path"],
                              "decodedSha256": ("b" if label == "sr" else "c") * 64},
                "metrics": {"psnrDb": 36.5 if label == "sr" else 38.5, "ssim": 0.97},
            }
            path = root / f"quality-{label}.json"
            path.write_text(json.dumps(quality))
            entry.setdefault("quality", {})[label] = {
                "path": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()
            }
        return manifest

    def sdk_video_manifest(self, root, raw=None):
        raw = raw or sdk_video_sr_run()
        readiness = {"schemaVersion": 1, "rows": [
            {"capability": "video-sr", "status": "pass-bounded", "unverified": ["quality"]}],
            "p11Gate": {"status": "open", "finalReleaseConclusion": "not-ready"}}
        manifest = self.write_manifest(
            root, [("sdk", raw, "video-sr-sdk-resource", "video-sr")], readiness=readiness)
        entry = manifest["sources"][0]
        for field, name in (("input", "input.mp4"), ("reference", "reference.mkv")):
            path = root / name
            path.write_bytes(name.encode())
            entry[field] = {"path": name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        raw["inputSHA256"] = entry["input"]["sha256"]
        entry["outputs"] = []
        for run in raw["runs"]:
            path = root / run["outputFile"]
            path.write_bytes(run["runId"].encode())
            run["outputSHA256"] = hashlib.sha256(path.read_bytes()).hexdigest()
            entry["outputs"].append({"path": path.name, "sha256": run["outputSHA256"]})
        raw_path = root / entry["path"]
        raw_path.write_text(json.dumps(raw))
        entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
        summary = {"schemaVersion": 1, "state": "pass-bounded", "qualityVerdict": "unverified",
                   "rawReportSHA256": entry["sha256"], "runCount": 5,
                   "deviceModel": raw["deviceModel"], "osVersion": raw["osVersion"],
                   "inputSHA256": raw["inputSHA256"],
                   "outputSHA256": {run["outputFile"]: run["outputSHA256"] for run in raw["runs"]}}
        path = root / "sdk-summary.json"
        path.write_text(json.dumps(summary))
        entry["summary"] = {"path": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        entry["quality"] = {}
        for label, run in (("first", raw["runs"][0]), ("last", raw["runs"][-1])):
            quality = {"schemaVersion": 1, "measurement": "decoded-output-fidelity",
                       "qualityVerdict": "unverified", "colorComparability": "matched-metadata",
                       "comparedFrames": 48,
                       "reference": {"path": entry["reference"]["path"], "decodedSha256": "a" * 64},
                       "candidate": {"path": run["outputFile"], "decodedSha256": "b" * 64},
                       "metrics": {"psnrDb": 36.5, "ssim": 0.97}}
            path = root / f"quality-{label}.json"
            path.write_text(json.dumps(quality))
            entry["quality"][label] = {"path": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        return manifest

    def sdk_asr_manifest(self, root, raw=None):
        raw = raw or sdk_file_asr_run()
        readiness = {"schemaVersion": 1, "rows": [
            {"capability": "speech-asr-file", "status": "pass-bounded", "unverified": ["quality"]},
            {"capability": "video-sr", "status": "pass-bounded", "unverified": ["quality"]}],
            "p11Gate": {"status": "open", "finalReleaseConclusion": "not-ready"}}
        manifest = self.write_manifest(
            root, [("asr", raw, "asr-file-sdk-resource", "speech-asr-file")], readiness=readiness)
        entry = manifest["sources"][0]
        input_path = root / "asr.caf"
        input_path.write_bytes(b"synthetic speech fixture")
        entry["input"] = {"path": input_path.name,
                          "sha256": hashlib.sha256(input_path.read_bytes()).hexdigest()}
        raw["inputSHA256"] = entry["input"]["sha256"]
        raw_path = root / entry["path"]
        raw_path.write_text(json.dumps(raw))
        entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
        sources = {entry["path"]: entry["sha256"],
                   entry["input"]["path"]: entry["input"]["sha256"]}
        source_manifest = {"schemaVersion": 1, "sourceCommit": "a" * 40,
                           "sourceHashes": sources}
        source_path = root / "source-manifest.json"
        source_path.write_text(json.dumps(source_manifest))
        entry["sourceManifest"] = {"path": source_path.name,
                                   "sha256": hashlib.sha256(source_path.read_bytes()).hexdigest()}
        summary = {"schemaVersion": 1, "validationStatus": "pass-bounded",
                   "backendId": raw["backendId"], "bundleId": raw["bundleId"],
                   "scope": raw["scope"], "runId": raw["runId"],
                   "inputSHA256": raw["inputSHA256"], "processIdentifier": raw["processIdentifier"],
                   "measurementMethod": raw["measurementMethod"], "measuredRuns": 5,
                   "warmup": "success", "rawReportSha256": entry["sha256"],
                   "sourceManifestSha256": entry["sourceManifest"]["sha256"],
                   "durationMs": {"p50": 3.0, "p95": 5.0},
                   "rssPeakSampledBytes": {"min": 200, "max": 204},
                   "unverified": ["natural speech CER/WER", "energy"]}
        summary_path = root / "asr-summary.json"
        summary_path.write_text(json.dumps(summary))
        entry["summary"] = {"path": summary_path.name,
                            "sha256": hashlib.sha256(summary_path.read_bytes()).hexdigest()}
        return manifest

    def test_sdk_file_asr_recomputes_five_runs_without_quality_promotion(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = report.generate(self.sdk_asr_manifest(root), root)
        self.assertEqual(len(result["groups"]), 1)
        group = result["groups"][0]
        self.assertEqual(group["kind"], "asr-file-sdk-resource")
        self.assertEqual(group["p11Capability"], "speech-asr-file")
        self.assertEqual(group["runIds"], [f"asr-{index}" for index in range(5)])
        self.assertEqual(group["timingsMs"]["duration"], {"count": 5, "p50": 3.0, "p95": 5.0})
        self.assertEqual(group["memory"]["peakSampledBytes"], {"count": 5, "p50": 202, "p95": 204})
        self.assertEqual(group["quality"]["status"], "unverified")
        self.assertEqual(result["p11Gate"], {"status": "open", "finalReleaseConclusion": "not-ready"})

    def test_sdk_file_asr_rejects_bad_run_and_cross_file_identity(self):
        for corruption in ("duplicate", "failed", "bad-rss", "wrong-transcript",
                           "summary-raw", "manifest-input"):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                raw = sdk_file_asr_run()
                if corruption == "duplicate":
                    raw["resourceRuns"][1]["runId"] = raw["resourceRuns"][0]["runId"]
                elif corruption == "failed":
                    raw["resourceRuns"][1]["normalizedMatch"] = False
                elif corruption == "bad-rss":
                    raw["resourceRuns"][1]["rssPeakSampledBytes"] = 1
                elif corruption == "wrong-transcript":
                    raw["resourceRuns"][1]["transcript"] = "not the expected phrase"
                manifest = self.sdk_asr_manifest(root, raw)
                entry = manifest["sources"][0]
                if corruption == "summary-raw":
                    path = root / entry["summary"]["path"]
                    summary = json.loads(path.read_text())
                    summary["rawReportSha256"] = "b" * 64
                    path.write_text(json.dumps(summary))
                    entry["summary"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                elif corruption == "manifest-input":
                    path = root / entry["sourceManifest"]["path"]
                    source = json.loads(path.read_text())
                    source["sourceHashes"][entry["input"]["path"]] = "b" * 64
                    path.write_text(json.dumps(source))
                    entry["sourceManifest"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                with self.assertRaises(ValueError):
                    report.generate(manifest, root)

    def test_sdk_file_asr_rejects_wrong_capability_and_output_alias(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.sdk_asr_manifest(root)
            entry = manifest["sources"][0]
            entry["p11Capability"] = "video-sr"
            with self.assertRaisesRegex(ValueError, "cannot report"):
                report.generate(manifest, root)
            entry["p11Capability"] = "speech-asr-file"
            output = root / entry["input"]["path"]
            before = output.read_bytes()
            with self.assertRaisesRegex(ValueError, "output must not overwrite"):
                report.write_report(manifest, root, output)
            self.assertEqual(output.read_bytes(), before)

    def test_sdk_video_group_recomputes_five_full_requests_separately(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = report.generate(self.sdk_video_manifest(root), root)
        self.assertEqual(len(result["groups"]), 1)
        group = result["groups"][0]
        self.assertEqual(group["kind"], "video-sr-sdk-resource")
        self.assertEqual(group["runIds"], [f"sdk-{index}" for index in range(1, 6)])
        self.assertEqual(group["timingsMs"]["requestWall"], {"count": 5, "p50": 4.0, "p95": 6.0})
        self.assertEqual(group["memory"]["peakDeltaSampledBytes"], {"count": 5, "p50": 103, "p95": 105})
        self.assertEqual(group["quality"]["status"], "unverified")
        self.assertEqual(group["scope"], "isolated-public-sdk-consumer")
        self.assertEqual(result["grouping"], ["device", "OS", "p11Capability", "actualBackend", "kind"])
        self.assertEqual(result["p11Gate"], {"status": "open", "finalReleaseConclusion": "not-ready"})

    def test_sdk_video_rejects_invalid_run_and_evidence_identity(self):
        for corruption in ("duplicate-run", "bad-number", "wrong-output", "wrong-quality", "wrong-summary"):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                raw = sdk_video_sr_run()
                if corruption == "duplicate-run":
                    raw["runs"][1]["runId"] = raw["runs"][0]["runId"]
                elif corruption == "bad-number":
                    raw["runs"][1]["rssSampledPeakBytes"] = 1
                manifest = self.sdk_video_manifest(root, raw)
                entry = manifest["sources"][0]
                if corruption == "wrong-output":
                    entry["outputs"][1] = entry["outputs"][0]
                elif corruption == "wrong-quality":
                    path = root / entry["quality"]["first"]["path"]
                    quality = json.loads(path.read_text())
                    quality["candidate"]["path"] = raw["runs"][1]["outputFile"]
                    path.write_text(json.dumps(quality))
                    entry["quality"]["first"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                elif corruption == "wrong-summary":
                    path = root / entry["summary"]["path"]
                    summary = json.loads(path.read_text())
                    summary["rawReportSHA256"] = "c" * 64
                    path.write_text(json.dumps(summary))
                    entry["summary"]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                with self.assertRaises(ValueError):
                    report.generate(manifest, root)

    def test_sdk_video_rejects_nested_output_alias(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.sdk_video_manifest(root)
            output = root / manifest["sources"][0]["outputs"][2]["path"]
            before = output.read_bytes()
            with self.assertRaisesRegex(ValueError, "output must not overwrite"):
                report.write_report(manifest, root, output)
            self.assertEqual(output.read_bytes(), before)

    def test_sdk_video_rejects_hardlinked_output_even_with_updated_hashes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.sdk_video_manifest(root)
            entry = manifest["sources"][0]
            first = root / entry["outputs"][0]["path"]
            second = root / entry["outputs"][1]["path"]
            second.unlink()
            os.link(first, second)
            entry["outputs"][1]["sha256"] = entry["outputs"][0]["sha256"]
            raw_path = root / entry["path"]
            raw = json.loads(raw_path.read_text())
            raw["runs"][1]["outputSHA256"] = entry["outputs"][1]["sha256"]
            raw_path.write_text(json.dumps(raw))
            entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
            summary_path = root / entry["summary"]["path"]
            summary = json.loads(summary_path.read_text())
            summary["rawReportSHA256"] = entry["sha256"]
            summary["outputSHA256"][raw["runs"][1]["outputFile"]] = entry["outputs"][1]["sha256"]
            summary_path.write_text(json.dumps(summary))
            entry["summary"]["sha256"] = hashlib.sha256(summary_path.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "alias"):
                report.generate(manifest, root)

    def test_tts_resource_group_recomputes_five_runs_without_quality_promotion(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.tts_manifest(root)
            result = report.generate(manifest, root)
        group = result["groups"][0]
        self.assertEqual(group["p11Capability"], "speech-tts")
        self.assertEqual(group["runIds"], [f"tts-{index}" for index in range(5)])
        self.assertEqual(group["timingsMs"]["duration"], {"count": 5, "p50": 3.0, "p95": 5.0})
        self.assertEqual(group["memory"]["peakSampledBytes"], {"count": 5, "p50": 202, "p95": 204})
        self.assertEqual(group["identity"]["voice"], "com.apple.voice.compact.en-US.Samantha")
        self.assertEqual(group["quality"]["status"], "unverified")
        self.assertEqual(group["scope"], "isolated-sdk-tts-probe")
        self.assertEqual(result["coverage"][2]["reportStatus"], "reported")
        self.assertEqual(result["p11Gate"], {"status": "open", "finalReleaseConclusion": "not-ready"})

    def test_video_sr_groups_recompute_48_frame_timings_without_quality_promotion(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.video_manifest(root)
            result = report.generate(manifest, root)
        self.assertEqual(len(result["groups"]), 2)
        sr, bicubic = result["groups"]
        self.assertEqual(sr["actualBackend"], "apple.videotoolbox.sr")
        self.assertEqual(bicubic["actualBackend"], "apple.coreimage.bicubic")
        self.assertEqual(sr["timingsMs"]["frameProcessing"], {"count": 48, "p50": 24.0, "p95": 46.0})
        self.assertEqual(bicubic["timingsMs"]["frameProcessing"], {"count": 48, "p50": 2.4, "p95": 4.6})
        self.assertEqual(sr["runIds"], ["video-session"])
        self.assertEqual(bicubic["runIds"], ["video-session:bicubic"])
        self.assertEqual(sr["quality"]["status"], "unverified")
        self.assertEqual(sr["quality"]["metrics"], {"psnrDb": 36.5, "ssim": 0.97})
        self.assertEqual(sr["quality"]["source"], manifest["sources"][0]["quality"]["sr"])
        self.assertEqual(sr["memory"]["peakDeltaSampledBytes"], 200)
        self.assertEqual(result["coverage"][0]["reportStatus"], "reported")
        self.assertEqual(result["p11Gate"], {"status": "open", "finalReleaseConclusion": "not-ready"})

    def test_video_sr_rejects_missing_frame_samples_and_cross_backend_input_mismatch(self):
        for corruption in ("missing-samples", "different-input"):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                raw = video_sr_run()
                if corruption == "missing-samples":
                    raw["frameProcessingSeconds"] = raw["frameProcessingSeconds"][:-1]
                manifest = self.video_manifest(root, raw)
                if corruption == "different-input":
                    entry = manifest["sources"][0]
                    raw_path = root / entry["path"]
                    raw = json.loads(raw_path.read_text())
                    raw["baseline"]["inputFileSha256"] = "d" * 64
                    raw_path.write_text(json.dumps(raw))
                    entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
                with self.assertRaisesRegex(ValueError, "invalid Video SR"):
                    report.generate(manifest, root)

    def test_video_sr_rejects_duplicate_outputs_and_mismatched_quality_identity(self):
        for corruption in ("duplicate-output", "swapped-candidate", "mismatched-reference"):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                manifest = self.video_manifest(root)
                entry = manifest["sources"][0]
                if corruption == "duplicate-output":
                    raw_path = root / entry["path"]
                    raw = json.loads(raw_path.read_text())
                    raw["baseline"]["output"] = raw["output"]
                    raw_path.write_text(json.dumps(raw))
                    entry["sha256"] = hashlib.sha256(raw_path.read_bytes()).hexdigest()
                else:
                    quality_path = root / entry["quality"]["sr"]["path"]
                    quality = json.loads(quality_path.read_text())
                    if corruption == "swapped-candidate":
                        quality["candidate"]["path"] = entry["outputs"]["bicubic"]["path"]
                    else:
                        quality["reference"]["decodedSha256"] = "b" * 64
                    quality_path.write_text(json.dumps(quality))
                    entry["quality"]["sr"]["sha256"] = hashlib.sha256(quality_path.read_bytes()).hexdigest()
                with self.assertRaisesRegex(ValueError, "invalid Video SR"):
                    report.generate(manifest, root)

    def test_video_sr_quality_file_cannot_be_overwritten_by_grouped_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.video_manifest(root)
            quality = root / manifest["sources"][0]["quality"]["sr"]["path"]
            before = quality.read_bytes()
            with self.assertRaisesRegex(ValueError, "output must not overwrite"):
                report.write_report(manifest, root, quality)
            self.assertEqual(quality.read_bytes(), before)

    def test_tts_summary_must_match_raw_and_cannot_be_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.tts_manifest(root)
            raw = root / "0-tts.json"
            raw.write_text(raw.read_text() + "\n")
            manifest["sources"][0]["sha256"] = hashlib.sha256(raw.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "TTS summary raw hash mismatch"):
                report.generate(manifest, root)
            manifest = self.tts_manifest(root)
            summary = root / "tts-summary.json"
            before = summary.read_bytes()
            with self.assertRaisesRegex(ValueError, "output must not overwrite"):
                report.write_report(manifest, root, summary)
            self.assertEqual(summary.read_bytes(), before)

    def test_tts_malformed_summary_is_rejected_as_source_error(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.tts_manifest(root)
            summary = root / "tts-summary.json"
            payload = json.loads(summary.read_text())
            payload["runs"] = None
            summary.write_text(json.dumps(payload))
            manifest["sources"][0]["summary"]["sha256"] = hashlib.sha256(summary.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "invalid TTS resource source identity"):
                report.generate(manifest, root)

    def test_tts_rejects_bad_runs_and_wrong_capability(self):
        for change in ("duplicate", "failed", "negative"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                raw = tts_resource_run()
                if change == "duplicate":
                    raw["f11ResourceRuns"][1]["runId"] = "tts-0"
                elif change == "failed":
                    raw["f11ResourceRuns"][1]["status"] = "failed"
                else:
                    raw["f11ResourceRuns"][1]["durationMs"] = -1
                with self.assertRaisesRegex(ValueError, "invalid TTS resource run"):
                    report.generate(self.tts_manifest(root, raw), root)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.tts_manifest(root)
            manifest["sources"][0]["p11Capability"] = "face-detection"
            with self.assertRaisesRegex(ValueError, "cannot report"):
                report.generate(manifest, root)

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

    def test_source_kind_cannot_be_mapped_to_another_readiness_capability(self):
        for kind, payload, capability in (
            ("face", face_run(), "speech-tts"),
            ("face", face_run(), "multimodal-apple"),
            ("mlxvlm", mlx_run(), "speech-tts"),
            ("mlxvlm", mlx_run(), "face-detection"),
        ):
            with self.subTest(kind=kind, capability=capability), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                manifest = self.write_manifest(root, [(kind, payload, kind, capability)])
                output = root / "report.json"
                output.write_bytes(b"keep\n")
                with self.assertRaisesRegex(ValueError, f"kind {kind} cannot report {capability}"):
                    report.write_report(manifest, root, output)
                self.assertEqual(output.read_bytes(), b"keep\n")

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
            with self.assertRaisesRegex(ValueError, "must remain open"):
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

    def test_changed_readiness_evidence_fails_before_output_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [("face", face_run(), "face", "face-detection")])
            (root / "evidence.json").write_text('{"changed": true}\n')
            output = root / "report.json"
            output.write_bytes(b"keep\n")

            with self.assertRaisesRegex(ValueError, "source hash mismatch: evidence.json"):
                report.write_report(manifest, root, output)
            self.assertEqual(output.read_bytes(), b"keep\n")

    def test_output_cannot_alias_readiness_source_hash_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_manifest(root, [("face", face_run(), "face", "face-detection")])
            evidence = root / "evidence.json"
            original = evidence.read_bytes()
            with self.assertRaisesRegex(ValueError, "output must not overwrite"):
                report.write_report(manifest, root, evidence)
            self.assertEqual(evidence.read_bytes(), original)

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
        for target_name in ("0-face.json", "manifest.json", "evidence.json"):
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
