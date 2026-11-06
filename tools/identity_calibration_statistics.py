"""Identity calibration counting statistics (pure standard library).

Inputs are never mutated; returned dicts are freshly built. Boolean fields
must be exact `bool` (`bool` is an `int` subclass, so `type(x) is bool`).
Empty denominators yield None, never 0.
"""


def _bool(v):
    return type(v) is bool


def binary_counts(records):
    if not isinstance(records, list):
        raise ValueError("records must be a list of dicts")
    pos = {"trials":0,"responses":0,"nonResponses":0,"errors":0,"errorRateOnResponse":None,"nonResponseRate":None}
    neg = {"trials":0,"responses":0,"nonResponses":0,"errors":0,"errorRateOnResponse":None,"nonResponseRate":None}
    for rec in records:
        if not isinstance(rec, dict) or set(rec) != {"positive", "accepted"}:
            raise ValueError("record requires 'positive' and 'accepted'")
        p, a = rec["positive"], rec["accepted"]
        if not _bool(p) or (a is not None and not _bool(a)):
            raise ValueError("'positive' must be bool; 'accepted' bool or None")
        g = pos if p else neg
        g["trials"] += 1
        if a is None:
            g["nonResponses"] += 1
        else:
            g["responses"] += 1
            if (p and a is False) or (not p and a is True):
                g["errors"] += 1
    for g in (pos, neg):
        g["errorRateOnResponse"] = g["errors"]/g["responses"] if g["responses"] else None
        g["nonResponseRate"] = g["nonResponses"]/g["trials"] if g["trials"] else None
    return {"positive": pos, "negative": neg}


_KNOWN = ("correct","wrong","rejected","nonresponse")
_UNKNOWN = ("accepted","rejected","nonresponse")


def search_counts(records):
    if not isinstance(records, list):
        raise ValueError("records must be a list of dicts")
    known = {"trials":0,"responses":0,"nonResponses":0,"correct":0,"wrong":0,"rejected":0,"tpirOnResponse":None,"fnirOnResponse":None,"nonResponseRate":None}
    unknown = {"trials":0,"responses":0,"nonResponses":0,"accepted":0,"rejected":0,"fpirOnResponse":None,"nonResponseRate":None}
    tied = 0
    for rec in records:
        if not isinstance(rec, dict) or set(rec) != {"known", "outcome", "tied"}:
            raise ValueError("record requires 'known','outcome','tied'")
        k, o, t = rec["known"], rec["outcome"], rec["tied"]
        if not _bool(k) or not _bool(t) or not isinstance(o, str):
            raise ValueError("'known'/'tied' bool; 'outcome' str")
        if t and o != "rejected":
            raise ValueError("tied=true requires outcome 'rejected'")
        if k:
            if o not in _KNOWN: raise ValueError("invalid known outcome")
            g = known; g["trials"] += 1
            if o == "nonresponse": g["nonResponses"] += 1
            else:
                g["responses"] += 1
                if o == "correct": g["correct"] += 1
                elif o == "wrong": g["wrong"] += 1
                else: g["rejected"] += 1
        else:
            if o not in _UNKNOWN: raise ValueError("invalid unknown outcome")
            g = unknown; g["trials"] += 1
            if o == "nonresponse": g["nonResponses"] += 1
            else:
                g["responses"] += 1
                if o == "accepted": g["accepted"] += 1
                else: g["rejected"] += 1
        if t: tied += 1
    known["tpirOnResponse"] = known["correct"]/known["responses"] if known["responses"] else None
    known["fnirOnResponse"] = (known["wrong"]+known["rejected"])/known["responses"] if known["responses"] else None
    known["nonResponseRate"] = known["nonResponses"]/known["trials"] if known["trials"] else None
    unknown["fpirOnResponse"] = unknown["accepted"]/unknown["responses"] if unknown["responses"] else None
    unknown["nonResponseRate"] = unknown["nonResponses"]/unknown["trials"] if unknown["trials"] else None
    return {"known": known, "unknown": unknown, "tiedSearches": tied}
