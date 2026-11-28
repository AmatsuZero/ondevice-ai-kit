#!/usr/bin/env python3
from pathlib import Path
import sys, hashlib, json, argparse

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "android-qwen-decode-window"))
from verify_collected_windows import verify, read_file
from phase_align import align_records, parse_record

MARKER = "--------- beginning of main"
INT_KEYS = ("idx", "t0_ns", "bytes", "t1_ns")


def samples_from(raw):
    out = []
    for line in raw.decode("utf-8").splitlines():
        if not line.startswith("SAMPLE "):
            continue
        fields = {}
        for tok in line[len("SAMPLE "):].split():
            if "=" not in tok:
                raise ValueError("bad SAMPLE token")
            k, v = tok.split("=", 1)
            if not k or not v:
                raise ValueError("bad SAMPLE pair")
            fields[k] = v
        if fields.get("kind") != "INTERIOR":
            continue
        if any(k not in fields for k in INT_KEYS):
            raise ValueError("SAMPLE missing int field")
        idx, t0, nbytes, t1 = (int(fields[k]) for k in INT_KEYS)
        if t0 > t1:
            raise ValueError("invalid bracket order")
        out.append((idx, t0, nbytes, t1))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("contract")
    ap.add_argument("host")
    ap.add_argument("captured")
    ap.add_argument("phase_log")
    a = ap.parse_args()
    contract_path = Path(a.contract)
    host = Path(a.host)
    captured = Path(a.captured)
    log_path = Path(a.phase_log)

    contract = json.loads(read_file(contract_path))
    if contract.get("phaseTrace", {}).get("schema") != "w3-phase-v1":
        raise ValueError("unsupported schema")

    v = verify(contract, host, captured)
    rid = v["runId"]

    terminal_bytes = read_file(host / "terminal-observed.json")
    if hashlib.sha256(terminal_bytes).hexdigest() != v["terminalReportSha256"]:
        raise ValueError("terminal digest mismatch")
    pid = json.loads(terminal_bytes)["pid"]
    if isinstance(pid, bool) or not isinstance(pid, int) or pid <= 0:
        raise ValueError("invalid pid")

    log_bytes = read_file(log_path)
    phase_log_sha256 = hashlib.sha256(log_bytes).hexdigest()
    payloads, records = [], []
    for line in log_bytes.decode("utf-8").splitlines():
        text = line.strip()
        if not text or text == MARKER:
            continue
        rec = parse_record(line)
        if len(rec) != 4:
            raise ValueError("bad phase record")
        times = rec[3]
        if not isinstance(times, list) or not all(isinstance(t, int) for t in times):
            raise ValueError("bad phase times")
        payloads.append(line)
        records.append(rec)
    if len(records) != 4 or not any(t != 0 for r in records for t in r[3]):
        raise ValueError("phase records invalid")

    stages = {}
    if len(payloads) != 4 or len(records) != 4:
        raise ValueError("expected precisely four payloads and records")
    windows = v["windows"]
    by_label = {"auto": [], "forced": []}
    for payload, (group, record_pid, tid, times) in zip(payloads, records):
        stamps = [t for t in times if t]
        if not stamps:
            raise ValueError(f"empty mark group {group!r}")
        matched = [
            label for label in ("auto", "forced")
            if all(windows[label]["enterNs"] < t < windows[label]["exitNs"] for t in stamps)
        ]
        if len(matched) != 1:
            raise ValueError(f"unknown or ambiguous mark group {group!r}: {stamps}")
        by_label[matched[0]].append(payload)
    for index, label in enumerate(("auto", "forced")):
        w = v["windows"][label]
        raw = read_file(captured / f"decode-window-{rid}-{label}-{index}.txt")
        if hashlib.sha256(raw).hexdigest() != w["rawSha256"]:
            raise ValueError(f"raw sha256 mismatch for stage {label}")
        phase = align_records(
            by_label[label], pid, w["enterNs"], w["exitNs"], samples_from(raw)
        )
        if phase["global_max_bytes"] != w["interiorMaxBytes"]:
            raise ValueError(f"interiorMaxBytes mismatch for stage {label}")
        if phase["sample_count"] != w["interiorCount"]:
            raise ValueError(f"interiorCount mismatch for stage {label}")
        stages[label] = {
            "interiorMaxMinusBaselineBytes": phase["global_max_bytes"] - w["baselineBytes"],
            "phase": phase,
        }

    print(json.dumps({
        "runId": rid,
        "pid": pid,
        "sourceFingerprint": v["sourceFingerprint"],
        "phaseLogSha256": phase_log_sha256,
        "stages": stages,
    }, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
