"""Verify one archived W3 decode-window run from its contract and original bytes."""
import argparse
import hashlib
import json
from pathlib import Path

from window_metrics import parse_window


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_file(path, limit=2 * 1024 * 1024):
    require(path.is_file() and not path.is_symlink(), f"missing or nonregular file {path.name}")
    with path.open("rb") as stream:
        raw = stream.read(limit + 1)
    require(0 < len(raw) <= limit, f"invalid length for {path.name}")
    return raw


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def verify(contract, host, captured):
    rid, fingerprint = contract["runId"], contract["sourceFingerprint"]
    raw = read_file(host / "terminal-observed.json")
    report = json.loads(raw)
    terminal_sha = sha(raw)
    require(report["runId"] == rid and report["sourceFingerprint"] == fingerprint, "report identity")
    require(report["probeVariant"] == "qwen-native-decode-heap-window-v1", "probe variant")
    require(report["terminal"] is True and report["status"] == "completed" and report["phase"] == "terminal", "no completed recognition")
    final_name = f"report-{rid}.json"
    require(all(read_file(captured / n) == raw for n in (final_name, final_name + ".second")), "terminal double read")
    host_result = json.loads(read_file(host / "host-result.json"))
    require(host_result["runId"] == rid and host_result["sourceFingerprint"] == fingerprint, "host identity")
    finish = host_result["finish"]
    require(host_result["finishVerified"] is True and finish["terminalAckReceived"] is True
            and finish["terminalAckAttempted"] is True and finish["terminalStatus"] == "completed"
            and finish["instrumentationCode"] == -1 and finish["terminalReportSha256"] == terminal_sha,
            "positive finish and ACK")
    publication = host_result["observation"]["publication"]
    require(publication["published"] is True and publication["durability_verified"] is True
            and publication["stage_retained"] is True, "durable ACK publication")
    pid, ticks = report["pid"], report["processStartTicks"]
    expected_ack = f"ODAI-TERMINAL-ACK-v1\n{rid}\n{fingerprint}\n{pid}\n{ticks}\n{terminal_sha}\n".encode("ascii")
    ack = f"ack-{rid}.txt"
    require(all(read_file(p) == expected_ack for p in
                (host / "terminal-ack.txt", captured / ack, captured / (ack + ".stage"))), "ACK bytes")
    require(report["inputManifestSHA256"] == contract["caseManifest"], "manifest hash")
    require(report["selectedClipId"] == contract["fixture"] and report["verifiedInputCount"] == 1, "selected case")
    require(report["actualConstructors"] == 1 and report["constructorReturned"] is True
            and report["streamsCreated"] == report["nativeDecodeCalls"] == 2
            and report["recognizerReleaseAttempts"] == 1 and report["recognizerReleaseSucceeded"] is True
            and report["foreground"]["teardownAck"] is True, "recognition lifecycle")
    models = report["modelsVerified"]
    require(len(models) == len(contract["models"])
            and {m["relativePath"]: m["sha256"] for m in models} == contract["models"], "model digests")
    row, = report["rows"]
    require(row["id"] == contract["fixture"] and row["samples"] == contract["samples"]
            and row["locale"] == contract["locale"] and row["cohort"] == "synthetic-resource"
            and row["pcmSha256"] == contract["inputs"][f"test-assets/inputs/{row['id']}.pcm"]
            and row["status"] == "completed", "input identity")
    stages = row["caseState"]["stages"]
    require([s["language"] for s in stages] == ["", "English"], "language order")
    windows = report["decodeWindows"]
    require([x["stage"] for x in windows] == ["auto", "forced"], "window order")
    metrics = {}
    for index, (stage, window) in enumerate(zip(stages, windows)):
        label = window["stage"]
        basename = f"decode-window-{rid}-{label}-{index}.txt"
        require(stage["status"] == "completed" and stage["decodeAttempts"] == 1
                and stage["releaseSucceeded"] is True and stage["rawText"].strip(), "stage completion")
        require(window["basename"] == basename and window["sufficientCoverage"] is True, "raw window identity")
        data = read_file(captured / basename)
        require(len(data) == window["length"] and sha(data) == window["sha256"], "raw window bytes")
        metrics[label] = dict(parse_window(data, label), rawText=stage["rawText"], decodeNanos=stage["decodeNanos"])
    require(metrics["auto"]["exitNs"] <= metrics["forced"]["enterNs"], "overlapping decode windows")
    # Include process samples taken during startup and the terminal publication.
    binding = dict(runId=rid, sourceFingerprint=fingerprint, pid=pid, startTicks=ticks)
    receipt = json.loads(read_file(host / "terminal-receipt.json"))
    journal = json.loads(read_file(host / "terminal-before-link.json"))
    require(receipt["binding"] == journal["binding"] == dict(binding, terminalReportSha256=terminal_sha), "terminal binding")
    samples = [receipt["sampleBefore"], receipt["sampleAfter"], journal["sampleBeforeLink"]]
    startup = json.loads(read_file(host / "startup-observed.json"))
    require(startup["binding"] == binding and startup["reportSHA256"] == sha(read_file(host / "startup-report.json")), "startup binding")
    samples.append(startup["sample"])
    polls = sorted(host.glob("poll-sample-*.json"))
    require(len(polls) == host_result["observation"]["pollSamples"] > 0, "poll sample count")
    for index, path in enumerate(polls):
        require(path.name == f"poll-sample-{index:06}.json", "poll sample sequence")
        poll = json.loads(read_file(path))
        require(poll["binding"] == binding, "poll identity")
        samples.append(poll["sample"])
    require(poll["reportSHA256"] == terminal_sha, "final poll report")
    for sample in samples:
        require(sample["pid"] == pid and sample["startTicks"] == ticks, "sample identity")
        require(0 < sample["rssBytes"] <= contract["sampledRssStopAboveBytes"]
                and sample["deviceAvailableBytes"] >= contract["deviceAvailableStopBelowBytes"], "resource stop")
    return dict(runId=rid, sourceFingerprint=fingerprint, terminalReportSha256=terminal_sha,
                windows=metrics, sampledMaxRssBytes=max(s["rssBytes"] for s in samples),
                processSamples=len(samples))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("contract", type=Path)
    parser.add_argument("host", type=Path)
    parser.add_argument("captured", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(json.loads(read_file(args.contract)), args.host, args.captured), indent=2))
