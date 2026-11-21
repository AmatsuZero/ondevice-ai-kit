"""Empirical binary ROC curve from identity scores (pure standard library).

The curve is a descriptive statistic over the supplied observations only. It
is not a calibrated policy, a population bound, or a production-accuracy
estimate, and it produces no interpolated operating threshold or EER.
"""

import math

try:
    from . import identity_calibration_statistics as stats
except ImportError:  # imported as a top-level module (no parent package)
    import identity_calibration_statistics as stats


def _valid_score(score):
    if score is None:
        return True
    t = type(score)
    if t is float:
        return math.isfinite(score)
    if t is int:  # bool is excluded (type(True) is bool, not int)
        try:
            return math.isfinite(float(score))
        except (OverflowError, ValueError):
            return False
    return False


def _validate(records, higher_is_positive):
    if type(higher_is_positive) is not bool:
        raise ValueError("higher_is_positive must be bool")
    if not isinstance(records, list):
        raise ValueError("records must be a list of dicts")
    for rec in records:
        if not isinstance(rec, dict) or set(rec) != {"positive", "score"}:
            raise ValueError("invalid record shape")
        if type(rec["positive"]) is not bool:
            raise ValueError("invalid positive flag")
        if not _valid_score(rec["score"]):
            raise ValueError("invalid score")


def _fresh_counts(base, pos_errors, neg_errors):
    """Build a fresh binary_counts-shaped result for one operating point."""
    def clone(b, errors):
        return {
            "trials": b["trials"],
            "responses": b["responses"],
            "nonResponses": b["nonResponses"],
            "errors": errors,
            "errorRateOnResponse": (errors / b["responses"]
                                    if b["responses"] else None),
            "nonResponseRate": b["nonResponseRate"],
        }
    return {
        "positive": clone(base["positive"], pos_errors),
        "negative": clone(base["negative"], neg_errors),
    }


def empirical_curve(records, higher_is_positive):
    _validate(records, higher_is_positive)

    # Base counts: binary_counts over a reject-all view (responders rejected,
    # nonresponders accepted=None). trials/responses/nonResponses are constant
    # across all operating points; only per-class errors change.
    reject_all_view = [
        {"positive": r["positive"],
         "accepted": None if r["score"] is None else False}
        for r in records
    ]
    base = stats.binary_counts(reject_all_view)
    pos_resp = base["positive"]["responses"]
    neg_resp = base["negative"]["responses"]

    # Group responding scores once; equal scores form one group so ties cross
    # a cutoff together. scores are finite ints/floats (None skipped).
    groups = {}
    for r in records:
        s = r["score"]
        if s is None:
            continue
        g = groups.get(s)
        if g is None:
            g = [0, 0]
            groups[s] = g
        g[0 if r["positive"] else 1] += 1

    # Descending for higher-is-positive, ascending for lower-is-positive.
    ordered_scores = sorted(groups, reverse=higher_is_positive)
    direction = ("higher-is-positive" if higher_is_positive
                 else "lower-is-positive")

    points = []

    def add_point(rule, threshold, pos_errors, neg_errors):
        counts = _fresh_counts(base, pos_errors, neg_errors)
        frr = counts["positive"]["errorRateOnResponse"]
        far = counts["negative"]["errorRateOnResponse"]
        tpr = 1.0 - frr if frr is not None else None
        points.append({
            "rule": rule,
            "threshold": threshold,
            "counts": counts,
            "farOnResponse": far,
            "frrOnResponse": frr,
            "tprOnResponse": tpr,
        })

    # Reject-all endpoint: no response accepted yet.
    add_point("reject-all", None, pos_resp, 0)

    # Sweep cutoffs; cumulative accepted responders grow by whole score groups,
    # so each point is O(1) given the sorted groups (O(n log n) total).
    seen_pos = seen_neg = 0
    for s in ordered_scores:
        gp, gn = groups[s]
        seen_pos += gp
        seen_neg += gn
        add_point("inclusive-threshold", s, pos_resp - seen_pos, seen_neg)

    # Trapezoidal integral of TPR over FAR across the ordered ROC points,
    # including the reject-all endpoint and the final accept-all cutoff.
    if pos_resp == 0 or neg_resp == 0:
        auc = None
    else:
        auc = 0.0
        for a, b in zip(points, points[1:]):
            auc += (b["farOnResponse"] - a["farOnResponse"]) * \
                   (a["tprOnResponse"] + b["tprOnResponse"]) / 2.0

    return {
        "schemaVersion": 1,
        "kind": "identity-empirical-binary-curve",
        "scoreDirection": direction,
        "trialCount": len(records),
        "points": points,
        "aucOnResponse": auc,
        "validationStatus": "unverified",
        "productionPolicyGenerated": False,
    }
