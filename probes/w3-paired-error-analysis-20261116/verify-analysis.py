"""Parent acceptance of public offline analysis; never operates a device."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unicodedata

ROOT = Path(__file__).resolve().parents[2]
TASK = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools"))
from speech_error_metrics import _normalize, score_corpus, score_pair

SOURCE = Path(sys.argv[1]).resolve()
DATA = json.loads((TASK / "canonical-input.json").read_text())
env = os.environ.copy()
env["PYTHONPATH"] = str(ROOT / "tools")
checks = 0


def check(condition):
    global checks
    assert condition
    checks += 1


def execute(input_path, output_path):
    return subprocess.run(
        [sys.executable, "-B", str(SOURCE), str(input_path), "--output", str(output_path)],
        capture_output=True, text=True, env=env,
    )


with tempfile.TemporaryDirectory(prefix="odai-w3-paired-check-") as temp:
    temp = Path(temp)
    first, second = temp / "first.json", temp / "second.json"
    check(execute(TASK / "canonical-input.json", first).returncode == 0)
    check(execute(TASK / "canonical-input.json", second).returncode == 0)
    check(first.read_bytes() == second.read_bytes())
    output = json.loads(first.read_text())
    check(output["qualityAccepted"] is False and output["newRecognitionRequests"] == 0)
    check(output["normalization"] == dict(unicodeNormalization="NFC", casefold=True, whitespace="collapse", punctuationAndDigits="preserved", alignmentTieOrder=["equal", "substitution", "deletion", "insertion"]))
    universe = DATA["runs"][0]["rows"]
    check(output["universe"] == [row["id"] for row in universe])
    originals = {run["label"]: {row["id"]: row for row in run["rows"]} for run in DATA["runs"]}
    summaries = {run["label"]: run for run in output["runs"]}
    observed = {row["id"]: row for row in output["rows"]}
    for run in DATA["runs"]:
        label = run["label"]
        check(summaries[label]["missing"] == 65 - len(run["rows"]))
        check(summaries[label]["successNonempty"] == sum(row["status"] == "success" and row["hypothesis"] != "" for row in run["rows"]))
        check(summaries[label]["successEmpty"] == sum(row["status"] == "success" and row["hypothesis"] == "" for row in run["rows"]))
        check(summaries[label]["typedErrors"] == sum(row["status"] == "typed-error" for row in run["rows"]))
        for reference in universe:
            actual = observed[reference["id"]]["perRun"][label]
            original = originals[label].get(reference["id"])
            if original is None:
                check(actual == dict(status="missing", hypothesis=None, error=None, eligible=False, score=None, diagnostics=None))
                continue
            check(actual["hypothesis"] == original["hypothesis"] and actual["error"] == original["error"])
            eligible = original["cohort"] in ("mandarin", "english-near", "english-far") and original["status"] == "success" and bool(_normalize(original["hypothesis"], True))
            check(actual["eligible"] is eligible)
            if not eligible:
                check(actual["score"] is None and actual["diagnostics"] is None)
                continue
            score = score_pair(reference["reference"], actual["hypothesis"], casefold=True)
            check(actual["score"] == score)
            for metric in ("word", "character"):
                diag = actual["diagnostics"][metric]
                ref, hyp = _normalize(reference["reference"], True), _normalize(actual["hypothesis"], True)
                ref_units = ref.split() if metric == "word" else list(ref.replace(" ", ""))
                hyp_units = hyp.split() if metric == "word" else list(hyp.replace(" ", ""))
                check([edit["reference"] for edit in diag["edits"] if edit["op"] != "insertion"] == ref_units)
                check([edit["hypothesis"] for edit in diag["edits"] if edit["op"] != "deletion"] == hyp_units)
                buckets = {category: dict(substitutions=0, deletions=0, insertions=0, total=0) for category in ("formattingOnly", "contentOrMixed")}
                for edit in diag["edits"]:
                    if edit["op"] == "equal":
                        check(edit["reference"] == edit["hypothesis"] and edit["formattingOnly"] is False)
                        continue
                    sides = [edit["reference"], edit["hypothesis"]]
                    stripped = ["".join(c for c in text if not unicodedata.category(c).startswith("P")) for text in sides]
                    formatting = stripped[0] == stripped[1] and any(unicodedata.category(c).startswith("P") for text in sides for c in text)
                    check(edit["formattingOnly"] is formatting)
                    category = "formattingOnly" if formatting else "contentOrMixed"
                    field = {"substitution": "substitutions", "deletion": "deletions", "insertion": "insertions"}[edit["op"]]
                    buckets[category][field] += 1
                    buckets[category]["total"] += 1
                check(all(diag[category] == bucket for category, bucket in buckets.items()))
                check(all(sum(bucket[field] for bucket in buckets.values()) == score[metric][field] for field in ("substitutions", "deletions", "insertions")))
        for cohort, summary in summaries[label]["cohorts"].items():
            eligible = [row for row in universe if row["id"] in originals[label] and row["cohort"] == cohort and observed[row["id"]]["perRun"][label]["eligible"]]
            check(summary["eligibleIDs"] == [row["id"] for row in eligible])
            if not eligible:
                check(summary["metrics"] is None)
                continue
            pairs = [dict(id=row["id"], reference=row["reference"], hypothesis=originals[label][row["id"]]["hypothesis"]) for row in eligible]
            check(summary["metrics"] == score_corpus(pairs, casefold=True)["aggregate"])
            for metric in ("word", "character"):
                for category in ("formattingOnly", "contentOrMixed"):
                    for field in ("substitutions", "deletions", "insertions", "total"):
                        check(summary["diagnostics"][metric][category][field] == sum(observed[row["id"]]["perRun"][label]["diagnostics"][metric][category][field] for row in eligible))
    for pair in output["paired"]:
        check(set(pair["cohorts"]) == {"mandarin", "english-near", "english-far"})
        for cohort, result in pair["cohorts"].items():
            ids = [row["id"] for row in universe if row["cohort"] == cohort and all(observed[row["id"]]["perRun"][label]["eligible"] for label in (pair["left"], pair["right"]))]
            check(result["ids"] == ids and result["count"] == len(ids))
            check(result["rightFewer"] + result["rightEqual"] + result["rightMore"] == len(ids))
            metric = "character" if cohort == "mandarin" else "word"
            deltas = []
            for cid, row in zip(ids, result["perID"], strict=True):
                errors = [observed[cid]["perRun"][pair[side]]["score"][metric]["errors"] for side in ("left", "right")]
                delta = errors[1] - errors[0]
                deltas.append(delta)
                check(row == dict(id=cid, metric=metric, leftErrors=errors[0], rightErrors=errors[1], delta=delta))
            check((result["rightFewer"], result["rightEqual"], result["rightMore"]) == (sum(delta < 0 for delta in deltas), sum(delta == 0 for delta in deltas), sum(delta > 0 for delta in deltas)))
            for side in ("left", "right"):
                label = pair[side]
                pairs = [dict(id=cid, reference=observed[cid]["reference"], hypothesis=originals[label][cid]["hypothesis"]) for cid in ids]
                check(result[side + "Aggregate"] == (score_corpus(pairs, casefold=True)["aggregate"] if pairs else None))
    mutants = []
    for field, value in [("pcmSHA256", "0" * 64), ("reference", "wrong"), ("samples", 1), ("locale", "en-US"), ("cohort", "english-near"), ("id", "unknown"), ("hypothesis", None), ("status", "unknown")]:
        mutant = copy.deepcopy(DATA)
        mutant["runs"][1]["rows"][0][field] = value
        mutants.append((field, mutant))
    for name in ("duplicate-case", "duplicate-label", "planned-count", "universe-count", "error-hypothesis"):
        mutant = copy.deepcopy(DATA)
        if name == "duplicate-case": mutant["runs"][1]["rows"].append(copy.deepcopy(mutant["runs"][1]["rows"][0]))
        elif name == "duplicate-label": mutant["runs"][1]["label"] = "apple-iphone"
        elif name == "planned-count": mutant["runs"][1]["planned"] = 24
        elif name == "universe-count": mutant["runs"][0]["rows"].pop()
        else: mutant["runs"][0]["rows"][52]["hypothesis"] = ""
        mutants.append((name, mutant))
    negative_receipts = []
    for name, mutant in mutants:
        inp, out = temp / "mutant.json", temp / "rejected.json"
        inp.write_text(json.dumps(mutant))
        result = execute(inp, out)
        check(result.returncode != 0 and "ValueError" in result.stderr and not out.exists())
        negative_receipts.append(dict(name=name, rejected=True))
    receipt = dict(checks=checks, negativeCases=negative_receipts, deterministic=True,
                   sourceSHA256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                   inputSHA256=hashlib.sha256((TASK / "canonical-input.json").read_bytes()).hexdigest(),
                   outputSHA256=hashlib.sha256(first.read_bytes()).hexdigest(), newRecognitionRequests=0)
    print(json.dumps(receipt))
