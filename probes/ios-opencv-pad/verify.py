"""Verify local fixed-model smoke evidence; never interprets PAD scores."""
import hashlib
import json
import math
from pathlib import Path
import sys

repo = Path(__file__).resolve().parents[2]
evidence = Path(sys.argv[1]).resolve()


def read(name):
    return json.loads((evidence / name).read_text())


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


inventory = read("inventory-reviewed.json")
assert all(sha(repo / path) == digest for path, digest in inventory["sources"].items())
assert all(sha(repo / path) == digest for path, digest in inventory["nonCompiledSupportHashes"].items())
app = evidence / "final-reviewed-probe.app"
assert all(sha(app / path) == digest for path, digest in inventory["app"].items())
assert not any(p.name in {"astronaut.png", "anti-spoof-mn3.onnx"} for p in app.rglob("*"))
assert sha(repo / "apple-opencv/Vendor/opencv2.xcframework/ios-arm64/libopencv2.a") == inventory["nativeArchiveSHA256"]
model = (evidence / "input/anti-spoof-mn3.onnx").read_bytes()
assert len(model) == 12_270_179
assert hashlib.sha384(model).hexdigest() == (
    "6de4534964b723397b3e8c995cadcf43bc007cc2f9930b95ae25f76adccece5d1d4d058d0b15117b9e4a9f758424f92a")
assert hashlib.sha256(model).hexdigest() == "c4c99af04603b62d7e44f6f4daeb33e0daeccc696008c0b1d62f6f5cebbb3262"
assert sha(evidence / "input/astronaut.png") == "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5"
report = read("device-reviewed-report.json")
assert report["status"] == "passed" and len(report["checks"]) == 9 and all(report["checks"].values())
assert report["modelSHA256"] == hashlib.sha256(model).hexdigest()
assert report["modelSHA384"] == hashlib.sha384(model).hexdigest()
assert report["loadFailureDiagnostic"] == {"stage": "modelLoad", "category": "opencv", "nativeCode": -5}
assert report["inputSHA256"] == sha(evidence / "input/astronaut.png")
assert report["policyStatus"] == "UNVERIFIED"
assert report["decisionIssued"] is False and report["proofIssued"] is False
native = report["native"]
assert native["runtimeVersion"] == "4.12.0"
assert native["preprocessingRevision"] == "whole-frame-cubic128-rgb-nchw-v1"
assert native["inputShape"] == [1, 3, 128, 128] and native["outputShape"] == [1, 2]
runs = native["runs"]
assert len(runs) == 2
assert all(len(run["scores"]) == 2 and all(math.isfinite(x) for x in run["scores"]) for run in runs)
assert all(abs(a - b) <= 0.000001 for a, b in zip(runs[0]["scores"], runs[1]["scores"]))
install, launch = read("reviewed-install.json"), read("reviewed-launch.json")
assert install["info"]["outcome"] == launch["info"]["outcome"] == "success"
assert launch["result"]["process"]["executable"].startswith(install["result"]["installedApplications"][0]["installationURL"])
assert launch["result"]["launchOptions"]["arguments"] == ["--run-pad-raw-score"]
assert read("copy-input.json")["info"]["outcome"] == read("reviewed-copy-report.json")["info"]["outcome"] == "success"
assert read("reviewed-reset.json")["info"]["outcome"] == "success"
assert read("reviewed-terminate.json")["info"]["outcome"] == "success"
assert not any(p["processIdentifier"] == launch["result"]["process"]["processIdentifier"]
               for p in read("reviewed-final-processes.json")["result"]["runningProcesses"])
ordinary = read("reviewed-ordinary-launch.json")
assert ordinary["info"]["outcome"] == "success" and ordinary["result"]["launchOptions"]["arguments"] == []
prior = json.loads((repo / "artifacts/verification/apple-identity-review-20261104/normal-launch.json").read_text())
assert ordinary["result"]["process"]["executable"] == prior["result"]["process"]["executable"]
assert "** BUILD SUCCEEDED **" in (evidence / "review-fixed-build.log").read_text()
assert "checks passed" in (evidence / "preprocessing-green.log").read_text()
assert "checks passed" in (evidence / "report-green.log").read_text()
diagnostic_red = read("diagnostic-red-report.json")
assert diagnostic_red["status"] == "failed" and diagnostic_red["failedCheck"] == "sanitizedLoadFailure"
assert report["runId"] != diagnostic_red["runId"]
protected = json.loads((repo / "artifacts/verification/identity-gallery-demo-20261103/protected-guard-final.json").read_text())
assert len(protected) == 9 and all(sha(repo / path) == digest for path, digest in protected.items())
assert sha(repo / "apple-mlx/Package.resolved") == "e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858"
summary = {
    "status": "passed", "runId": report["runId"], "deviceChecks": len(report["checks"]),
    "nativeRuns": len(runs), "reportSHA256": sha(evidence / "device-reviewed-report.json"),
    "sourceAndAppInventoryMatches": True, "protectedFilesUnchanged": 9,
    "productionMlxLockUnchanged": True, "probeTerminated": True,
    "ordinaryAppRestoredWithoutValidationArguments": True,
    "boundary": "Whole-frame fixed-model import/inference smoke only; no calibrated crop, decision, proof or SDK integration.",
}
(evidence / "verification.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary))
