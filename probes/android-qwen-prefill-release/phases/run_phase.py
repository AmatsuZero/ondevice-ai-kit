"""Prepare or execute the single W3 phase-marked Android acceptance run."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from phase_avd import CONFIG, NAME, run_on_avd
from phase_capture import quality

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
ARTIFACTS = REPO / ".omx" / "artifacts"
CONTRACT = HERE / "run-contract.json"
EXPECTED_SHA = "898e85be15edd9bc80bf1bb388ec5bfbc44b7229c5cbbe2e0cd6991b2d609181"
RUN_ID = "w3-station-phases-20261122-01"


def checked_file(path, expected):
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if digest != expected:
        raise ValueError(f"Digest mismatch for {path}")
    return path.stat().st_size


def preflight():
    checked_file(CONTRACT, EXPECTED_SHA)
    contract = json.loads(CONTRACT.read_bytes())
    if contract["runId"] != RUN_ID:
        raise ValueError("Unexpected diagnostic run")
    source = REPO / contract["phaseTrace"]["sourceRoot"]
    checked_file(source / "contract.json", EXPECTED_SHA)
    inputs = contract["inputs"]
    for name, digest in inputs.items():
        checked_file(source / "project" / name, digest)
    fp = hashlib.sha256(json.dumps(inputs, sort_keys=True, separators=(",", ":")).encode())
    if fp.hexdigest() != contract["sourceFingerprint"]:
        raise ValueError("Source fingerprint mismatch")
    for data in contract["apks"].values():
        if checked_file(source / data["relativePath"], data["sha256"]) != data["size"]:
            raise ValueError("APK size mismatch")
    host = source / contract["phaseTrace"]["hostDirectory"]
    for name, digest in contract["hostInputHashes"].items():
        checked_file(host / name, digest)
    checked_file(ARTIFACTS / "w3-window-notebook-20261122-01" / "run_once.py",
                 "a5752983e1e0495205e7ea716e3a2dd0671b5cecdc418a1fc32ad022910908de")
    checked_file(ARTIFACTS / "w3-decode-window-20261122-GXmgFj" / "rename_noreplace",
                 contract["ackHelperSHA256"])
    manifest = json.loads((ARTIFACTS / "w3-model-reselection-20261110" / "model-manifest.json").read_bytes())
    model_root = Path(manifest["directory"])
    size = sum(checked_file(model_root / name, digest)
               for name, digest in contract["models"].items())
    return contract, {
        "runId": RUN_ID, "contract": EXPECTED_SHA, "avdName": NAME,
        "avdConfigSha256": hashlib.sha256(CONFIG.encode()).hexdigest(),
        "totalModelBytes": size, "sourceInputs": len(inputs), "models": len(contract["models"]),
        "apks": len(contract["apks"]), "hostInputs": len(contract["hostInputHashes"]),
    }


def consumption(run_root):
    return sorted(str(p.relative_to(run_root)) for p in run_root.glob("*/observation/host/run-started.json"))


def finish(contract, attempt):
    sys.path.insert(0, str(REPO / "probes/android-qwen-decode-window"))
    from verify_collected_windows import verify
    from verify_collected_windows import read_file

    root = attempt / "observation"
    host, captured, phase_log = root / "host", root / "captured", root / "phase.log"
    verified = verify(contract, host, captured)
    raw = subprocess.check_output([sys.executable, "-B", str(HERE / "analyze_phase_capture.py"),
                                   str(CONTRACT), str(host), str(captured), str(phase_log)])
    aligned = json.loads(raw)
    if (aligned["runId"], aligned["sourceFingerprint"]) != (verified["runId"], verified["sourceFingerprint"]):
        raise ValueError("Phase and decode identities differ")
    fixture = REPO / contract["phaseTrace"]["sourceRoot"] / "project/test-assets/cases.json"
    texts = {label: stage["rawText"] for label, stage in verified["windows"].items()}
    aligned["referenceMatch"] = quality(read_file(fixture), contract["caseManifest"], contract["fixture"], texts)
    aligned["sampledMaxRssBytes"] = verified["sampledMaxRssBytes"]
    aligned["processSamples"] = verified["processSamples"]
    with (attempt / "analysis.json").open("x") as stream:
        json.dump(aligned, stream, indent=2)
        stream.write("\n")
    return aligned["referenceMatch"]["bothExact"]


def execute(contract, receipt, run_root):
    run_root.mkdir(exist_ok=True)
    fd = os.open(run_root, os.O_RDONLY)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if consumption(run_root):
            raise RuntimeError("Single run already consumed")
        attempt = Path(tempfile.mkdtemp(prefix="attempt-", dir=run_root))
        with (attempt / "preflight.json").open("x") as stream:
            json.dump(receipt, stream, indent=2)
            stream.write("\n")
        print(attempt, flush=True)
        # The child holds the same lock through collection if the parent goes away.
        def observe():
            subprocess.run([sys.executable, "-B", str(HERE / "phase_arm.py"), str(attempt)],
                           pass_fds=(fd,), check=True)

        run_on_avd(attempt, observe)
        if not finish(contract, attempt):
            raise ValueError(f"Sentence differs from reference; results saved in {attempt / 'analysis.json'}")
    finally:
        os.close(fd)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("preflight", "execute"), nargs="?", default="preflight")
    mode = parser.parse_args().mode
    contract, receipt = preflight()
    run_root = ARTIFACTS / RUN_ID
    if mode == "execute":
        execute(contract, receipt, run_root)
    else:
        print(json.dumps(dict(receipt, consumed=consumption(run_root)), indent=2))
