import argparse
import hashlib
import json
import sys
from pathlib import Path

LETTERS = ("A", "B", "C")
ARMS = ("baseline", "candidate")


def load(path):
    raw = path.read_bytes()
    return hashlib.sha256(raw).hexdigest(), json.loads(raw)


def require(cond, msg):
    if not cond:
        raise ValueError(msg)


def is_int(x):
    return type(x) is int


def check_freeze(freeze, arm, expected):
    require(freeze["arm"] == arm, f"{arm}: freeze arm")
    for path, digest in expected.items():
        require(freeze["inputs"].get(str(path)) == digest, f"{arm}: freeze digest {path}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("SCREEN")
    ap.add_argument("--selection-sha256", required=True)
    args = ap.parse_args()

    root = Path(args.SCREEN).resolve()
    selection_sha, selection = load(root / "selection.json")
    contract_sha, contract = load(root / "experiment-contract.json")
    protocol_sha, protocol = load(root / "paired-protocol-verification.json")
    _, paired = load(root / "paired-model-verification.json")

    require(selection_sha == args.selection_sha256, "CLI selection sha")
    require(contract["selectionSHA256"] == selection_sha, "contract selection sha")
    require(protocol["metadataSelectionSHA256"] == selection_sha, "protocol selection sha")
    require(paired["contractSHA256"] == contract_sha, "contract sha")
    require(protocol["state"] == "passed", "protocol state")
    require(paired["evidenceVerification"] == "passed", "evidenceVerification")

    gate = contract["qualityGate"]
    require(is_int(gate["total"]) and gate["total"] == 36, "gate total")
    require(is_int(gate["minimumStrictCorrect"]) and gate["minimumStrictCorrect"] == 20, "gate minStrict")
    require(is_int(gate["minimumNetGainOverNewSameSetBaseline"])
            and gate["minimumNetGainOverNewSameSetBaseline"] == 4, "gate minNetGain")

    items = selection["items"]
    require(len(items) == 36, "item count")
    ids = [it["id"] for it in items]
    vids = [it["videoId"] for it in items]
    require(len(set(ids)) == 36 and len(set(vids)) == 36, "duplicate id/videoId")

    counts = {}
    for arm in ARMS:
        armdir = root / arm
        _, freeze = load(armdir / "run-freeze.json")
        cases_sha, cases = load(armdir / "model-cases.json")
        result_sha, result = load(armdir / "model-output" / "result.json")

        check_freeze(freeze, arm, {
            root / "selection.json": selection_sha,
            root / "experiment-contract.json": contract_sha,
            root / "paired-protocol-verification.json": protocol_sha,
            armdir / "model-cases.json": cases_sha,
        })
        require(protocol["modelCases"][arm] == cases_sha, f"{arm}: protocol case digest")
        require(paired[arm]["resultSHA256"] == result_sha, f"{arm}: paired result digest")

        require(len(cases) == 36, f"{arm}: case count")
        require(result["state"] == "completed", f"{arm}: state")
        require(is_int(result["completedRows"]) and result["completedRows"] == 36, f"{arm}: completedRows")
        require(result["modelID"] == contract[arm]["modelId"], f"{arm}: modelID")
        require(result["modelRevision"] == contract[arm]["revision"], f"{arm}: modelRevision")

        rows = result["rows"]
        require(len(rows) == 36, f"{arm}: row count")
        correct = 0
        for item, case, row in zip(items, cases, rows):
            require(case["caseId"] == item["id"] == row["caseId"], f"{arm}: caseId")
            require(case["category"] == item["category"] == row["category"], f"{arm}: category")
            require(case["expectedLetter"] == item["answerLetter"] == row["expectedLetter"], f"{arm}: expectedLetter")
            letter = item["answerLetter"]
            require(letter in LETTERS, f"{arm}: letter domain")
            strict = row["strictPass"]
            require(type(strict) is bool, f"{arm}: strictPass type")
            require(row["valid"] is True, f"{arm}: valid")
            raw = row["rawOutput"]
            require(raw in LETTERS, f"{arm}: rawOutput")
            recomputed = raw == letter
            require(strict is recomputed, f"{arm}: strictPass value")
            correct += recomputed

        require(is_int(result["strictCorrect"]) and result["strictCorrect"] == correct, f"{arm}: report strictCorrect")
        require(is_int(paired[arm]["strictCorrect"]) and paired[arm]["strictCorrect"] == correct, f"{arm}: paired strictCorrect")
        counts[arm] = correct

    net_gain = counts["candidate"] - counts["baseline"]
    require(is_int(paired["netGain"]) and paired["netGain"] == net_gain, "netGain claim")
    gate_result = ("passed" if counts["candidate"] >= gate["minimumStrictCorrect"]
                   and net_gain >= gate["minimumNetGainOverNewSameSetBaseline"] else "failed")
    require(paired["qualityGate"] == gate_result, "qualityGate claim")

    out = {
        "selectionSHA256": selection_sha,
        "caseCount": len(items),
        "baselineStrictCorrect": counts["baseline"],
        "candidateStrictCorrect": counts["candidate"],
        "netGain": net_gain,
        "qualityGate": gate_result,
    }
    print(json.dumps(out, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError, TypeError, OSError) as exc:
        print(f"verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
