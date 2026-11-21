import argparse, json, re, unicodedata; from pathlib import Path; from speech_error_metrics import _normalize, score_pair
COHORTS = ("mandarin", "english-near", "english-far", "silence", "nonspeech-noise")
LOCALES = ("zh-CN", "en-US"); STATUSES = ("success", "typed-error")
ROW_KEYS = {"id", "cohort", "locale", "samples", "pcmSHA256", "reference", "hypothesis", "status", "error"}
RUN_KEYS = {"id", "label", "planned", "rows"}; TOP_KEYS = {"schemaVersion", "scope", "runs"}
PUBLIC = ("cohort", "locale", "samples", "pcmSHA256", "reference"); METRICS = ("word", "character")
SHA_RE = re.compile(r"^[0-9a-f]{64}$"); APPLE = "apple-iphone"
def _text(v, n):
    if type(v) is not str: raise ValueError(n + " must be an exact str")
    if any(0xD800 <= ord(c) <= 0xDFFF for c in v): raise ValueError(n + " contains a surrogate")
    return v
def _ident(v, n):
    _text(v, n)
    if not v or v != v.strip(): raise ValueError(n + " must be nonempty and trimmed")
    return v
def _int(v, n):
    if type(v) is not int: raise ValueError(n + " must be an exact int")
    return v
def _fmt(ref, hyp):
    p = lambda c: unicodedata.category(c).startswith("P")
    return (any(p(c) for c in ref) or any(p(c) for c in hyp)) and "".join(c for c in ref if not p(c)) == "".join(c for c in hyp if not p(c))
def _uniq(pairs):
    out = {}
    for k, v in pairs:
        if k in out: raise ValueError("duplicate JSON object key")
        out[k] = v
    return out
def _const(v): raise ValueError("invalid JSON constant: " + str(v))
def _row(row, name, seen):
    if type(row) is not dict or set(row) != ROW_KEYS: raise ValueError(name + " invalid dict")
    cid = _ident(row["id"], name + ".id")
    if cid in seen: raise ValueError("duplicate case id: " + cid)
    seen.add(cid)
    if type(row["cohort"]) is not str or row["cohort"] not in COHORTS or type(row["locale"]) is not str or row["locale"] not in LOCALES: raise ValueError(name + " enum")
    samples = _int(row["samples"], name + ".samples")
    if samples < 0: raise ValueError(name + ".samples")
    sha = _text(row["pcmSHA256"], name + ".pcmSHA256")
    if not SHA_RE.match(sha): raise ValueError(name + ".pcmSHA256")
    reference = _text(row["reference"], name + ".reference")
    status = row["status"]
    if type(status) is not str or status not in STATUSES: raise ValueError(name + ".status")
    hyp, err = row["hypothesis"], row["error"]
    if status == "success":
        if hyp is None: raise ValueError(name + ": null success hypothesis")
        _text(hyp, name + ".hypothesis")
        if err is not None: raise ValueError(name + ": non-null success error")
    else:
        if hyp is not None: raise ValueError(name + ": non-null error hypothesis")
        if err is None: raise ValueError(name + ": null typed error")
    return dict(id=cid, cohort=row["cohort"], locale=row["locale"], samples=samples, pcmSHA256=sha, reference=reference, hypothesis=hyp, status=status, error=err)
def _validate(doc):
    if type(doc) is not dict or set(doc) != TOP_KEYS: raise ValueError("invalid top-level schema")
    if type(doc["schemaVersion"]) is not int or doc["schemaVersion"] != 1: raise ValueError("schemaVersion must be 1")
    _text(doc["scope"], "scope")
    if type(doc["runs"]) is not list or not doc["runs"]: raise ValueError("invalid runs")
    labels, runs = set(), []
    for ri, run in enumerate(doc["runs"]):
        if type(run) is not dict or set(run) != RUN_KEYS: raise ValueError("invalid run %d" % ri)
        rid = _ident(run["id"], "runs[%d].id" % ri); label = _ident(run["label"], "runs[%d].label" % ri)
        if label in labels: raise ValueError("duplicate run label: " + label)
        labels.add(label)
        planned = _int(run["planned"], "runs[%d].planned" % ri)
        if planned < 0 or type(run["rows"]) is not list: raise ValueError("invalid run %d" % ri)
        seen, rows = set(), []
        for ci, row in enumerate(run["rows"]): rows.append(_row(row, "runs[%d].rows[%d]" % (ri, ci), seen))
        runs.append(dict(id=rid, label=label, planned=planned, rows=rows))
    return runs
def _cross(runs):
    apples = [r for r in runs if r["label"] == APPLE]
    if len(apples) != 1: raise ValueError("require exactly one apple-iphone run")
    if any(r["planned"] != 65 for r in runs): raise ValueError("all runs must plan 65 rows")
    universe = [r["id"] for r in apples[0]["rows"]]
    if len(universe) != 65 or len(set(universe)) != 65: raise ValueError("apple requires exactly 65 unique rows")
    canon = {r["id"]: {k: r[k] for k in PUBLIC} for r in apples[0]["rows"]}
    for run in runs:
        for r in run["rows"]:
            if r["id"] not in canon: raise ValueError("unknown case id: " + r["id"])
            for k in PUBLIC:
                if r[k] != canon[r["id"]][k]: raise ValueError("cross-run mismatch %s.%s" % (r["id"], k))
    return universe, canon
def _align(ref, hyp, metric):
    ref, hyp = _normalize(ref, True), _normalize(hyp, True)
    ref = ref.split() if metric == "word" else [c for c in ref if not c.isspace()]
    hyp = hyp.split() if metric == "word" else [c for c in hyp if not c.isspace()]
    n, m = len(ref), len(hyp); d = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1): d[i][0] = i
    for j in range(m + 1): d[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (ref[i - 1] != hyp[j - 1]))
    ops = []; i, j = n, m
    while i or j:
        if i and j and ref[i-1] == hyp[j-1] and d[i][j] == d[i-1][j-1]:
            ops.append(("equal", ref[i-1], hyp[j-1])); i -= 1; j -= 1
        elif i and j and d[i][j] == d[i-1][j-1] + 1:
            ops.append(("substitution", ref[i-1], hyp[j-1])); i -= 1; j -= 1
        elif i and d[i][j] == d[i-1][j] + 1:
            ops.append(("deletion", ref[i-1], "")); i -= 1
        else:
            ops.append(("insertion", "", hyp[j-1])); j -= 1
    ops.reverse(); return ops
def _diag(ref, hyp):
    result = {}
    for metric in METRICS:
        detail = []
        counts = {c: dict(substitutions=0, deletions=0, insertions=0, total=0) for c in ("formattingOnly", "contentOrMixed")}
        for kind, ru, hu in _align(ref, hyp, metric):
            fmt = _fmt(ru, hu) if kind != "equal" else False
            detail.append(dict(op=kind, reference=ru, hypothesis=hu, formattingOnly=fmt))
            if kind != "equal":
                cat = "formattingOnly" if fmt else "contentOrMixed"
                field = {"substitution": "substitutions", "deletion": "deletions", "insertion": "insertions"}[kind]
                counts[cat][field] += 1; counts[cat]["total"] += 1
        total = counts["formattingOnly"]["total"] + counts["contentOrMixed"]["total"]
        result[metric] = dict(edits=detail, formattingOnly=counts["formattingOnly"], contentOrMixed=counts["contentOrMixed"], editTotal=total)
    return result
def _agg(scores):
    if not scores: return None
    totals = {m: dict(referenceUnits=0, hypothesisUnits=0, substitutions=0, deletions=0, insertions=0, errors=0) for m in METRICS}
    for sc in scores:
        for m in METRICS:
            for k in totals[m]: totals[m][k] += sc[m][k]
    out = {}
    for m in METRICS:
        ru = totals[m]["referenceUnits"]; out[m] = dict(totals[m], rate=totals[m]["errors"]/ru if ru else None, defined=ru != 0)
    return out
def _compute(run, canon, universe):
    by_id = {}
    for row in run["rows"]:
        cid = row["id"]; score = diag = None
        hyp = row["hypothesis"]
        elig = (row["status"] == "success" and type(hyp) is str and hyp != ""
                and row["cohort"] in COHORTS[:3] and _normalize(hyp, True) != "")
        if elig:
            score = score_pair(row["reference"], hyp, casefold=True); diag = _diag(row["reference"], hyp)
            for m in METRICS:
                fo, cm = diag[m]["formattingOnly"], diag[m]["contentOrMixed"]
                if (fo["substitutions"] + cm["substitutions"] != score[m]["substitutions"] or
                    fo["deletions"] + cm["deletions"] != score[m]["deletions"] or
                    fo["insertions"] + cm["insertions"] != score[m]["insertions"] or
                    diag[m]["editTotal"] != score[m]["errors"]):
                    raise ValueError("reconciliation %s.%s" % (cid, m))
                diag[m]["officialErrors"] = score[m]["errors"]; diag[m]["reconciles"] = True
        by_id[cid] = dict(row=row, eligible=elig, score=score, diag=diag)
    eligible = [cid for cid in universe if cid in by_id and by_id[cid]["eligible"]]
    expected = {c: 0 for c in COHORTS}
    for cid in universe: expected[canon[cid]["cohort"]] += 1
    cohorts = {c: dict(observed=0, successNonempty=0, successEmpty=0, typedErrors=0, missing=0) for c in COHORTS}
    sne = sem = typed = 0
    for cid, e in by_id.items():
        ch = cohorts[e["row"]["cohort"]]; ch["observed"] += 1
        if e["row"]["status"] == "typed-error": typed += 1; ch["typedErrors"] += 1
        elif e["row"]["hypothesis"] != "": sne += 1; ch["successNonempty"] += 1
        else: sem += 1; ch["successEmpty"] += 1
    for c in COHORTS: cohorts[c]["missing"] = expected[c] - cohorts[c]["observed"]
    for c in COHORTS:
        cids = [cid for cid in universe if cid in by_id and by_id[cid]["eligible"] and canon[cid]["cohort"] == c]
        cohorts[c]["eligibleIDs"] = cids
        cohorts[c]["metrics"] = _agg([by_id[cid]["score"] for cid in cids])
        dsum = {m: {cat: dict(substitutions=0, deletions=0, insertions=0, total=0) for cat in ("formattingOnly", "contentOrMixed")} for m in METRICS}
        for cid in cids:
            d = by_id[cid]["diag"]
            for m in METRICS:
                for cat in ("formattingOnly", "contentOrMixed"):
                    for f in ("substitutions", "deletions", "insertions", "total"):
                        dsum[m][cat][f] += d[m][cat][f]
        cohorts[c]["diagnostics"] = dsum
    summary = dict(id=run["id"], label=run["label"], planned=run["planned"], observed=len(by_id), missing=run["planned"]-len(by_id), successNonempty=sne, successEmpty=sem,
                   typedErrors=typed, cohorts=cohorts, eligibleIDs=eligible, metrics=_agg([by_id[cid]["score"] for cid in eligible]))
    return dict(label=run["label"], summary=summary, by_id=by_id, eligible_ids=eligible)
def _paired(left, right, universe, canon):
    result = dict(left=left["label"], right=right["label"], cohorts={})
    for cohort in COHORTS[:3]:
        metric = "character" if cohort == "mandarin" else "word"
        lset = {cid for cid in left["eligible_ids"] if canon[cid]["cohort"] == cohort}
        rset = {cid for cid in right["eligible_ids"] if canon[cid]["cohort"] == cohort}
        shared = [cid for cid in universe if cid in lset and cid in rset]
        per_id, fewer, equal, more = [], 0, 0, 0
        for cid in shared:
            le = left["by_id"][cid]["score"][metric]["errors"]; re = right["by_id"][cid]["score"][metric]["errors"]; delta = re - le
            per_id.append(dict(id=cid, metric=metric, leftErrors=le, rightErrors=re, delta=delta))
            if delta < 0: fewer += 1
            elif delta > 0: more += 1
            else: equal += 1
        result["cohorts"][cohort] = dict(metric=metric, metricKind="CER" if metric=="character" else "WER", ids=shared, count=len(shared),
            leftAggregate=_agg([left["by_id"][cid]["score"] for cid in shared]), rightAggregate=_agg([right["by_id"][cid]["score"] for cid in shared]),
            perID=per_id, rightFewer=fewer, rightEqual=equal, rightMore=more)
    return result
def main(argv=None):
    parser = argparse.ArgumentParser(description="Offline paired W3 error analysis")
    parser.add_argument("input"); parser.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    raw = Path(args.input).read_bytes()
    try: doc = json.loads(raw.decode("utf-8"), object_pairs_hook=_uniq, parse_constant=_const)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc: raise ValueError("invalid JSON input") from exc
    runs = _validate(doc); universe, canon = _cross(runs)
    data = [_compute(r, canon, universe) for r in runs]
    apple = {d["label"]: d for d in data}[APPLE]
    rows = []
    for cid in universe:
        per_run = {}
        for d in data:
            if cid in d["by_id"]:
                e = d["by_id"][cid]
                per_run[d["label"]] = dict(status=e["row"]["status"], hypothesis=e["row"]["hypothesis"], error=e["row"]["error"], eligible=e["eligible"], score=e["score"], diagnostics=e["diag"])
            else:
                per_run[d["label"]] = dict(status="missing", hypothesis=None, error=None, eligible=False, score=None, diagnostics=None)
        rows.append(dict(canon[cid], id=cid, perRun=per_run))
    paired = [_paired(apple, d, universe, canon) for d in data if d["label"] != APPLE]
    out = dict(schemaVersion=1, scope=doc["scope"], qualityAccepted=False, newRecognitionRequests=0, universe=universe, runs=[d["summary"] for d in data], rows=rows, paired=paired, normalization=dict(unicodeNormalization='NFC',casefold=True,whitespace='collapse',punctuationAndDigits='preserved',alignmentTieOrder=['equal','substitution','deletion','insertion']))
    Path(args.output).write_text(json.dumps(out, allow_nan=False, separators=(",", ":")) + "\n", encoding="utf-8")
    return 0
if __name__ == "__main__": raise SystemExit(main())