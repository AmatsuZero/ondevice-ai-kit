"""Behavioral tests for identity_calibration_curves.empirical_curve.

Fixtures are hand-calculated: ROC points are swept over distinct responding
scores, and AUC is the trapezoidal integral of TPR over FAR across the ordered
points (reject-all endpoint through the final accept-all cutoff).
"""

import copy
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import identity_calibration_curves as curves
import identity_calibration_statistics as stats


def rec(positive, score):
    return {"positive": positive, "score": score}


PERFECT = [rec(True, 0.9), rec(True, 0.8), rec(False, 0.3), rec(False, 0.2)]
REVERSE = [rec(True, 0.2), rec(True, 0.3), rec(False, 0.8), rec(False, 0.9)]
TIES = [rec(True, 0.5), rec(True, 0.5), rec(False, 0.5), rec(False, 0.5)]
# mixed ties + partial ranking: AUC = 13/18
MIXED = [rec(True, 0.9), rec(True, 0.9), rec(True, 0.4),
         rec(False, 0.9), rec(False, 0.4), rec(False, 0.1)]
NONRESP = [rec(True, 0.9), rec(True, 0.8), rec(True, None),
           rec(False, 0.3), rec(False, None), rec(False, None)]
LOWER = [rec(True, 1), rec(True, 2), rec(False, 3), rec(False, 4)]
HIGHER_NEGATED = [rec(True, -1), rec(True, -2), rec(False, -3), rec(False, -4)]


class TestTopLevelShape(unittest.TestCase):

    def test_exact_keys_and_types(self):
        c = curves.empirical_curve(PERFECT, True)
        self.assertEqual(set(c), {
            "schemaVersion", "kind", "scoreDirection", "trialCount",
            "points", "aucOnResponse", "validationStatus",
            "productionPolicyGenerated",
        })
        self.assertIs(type(c["schemaVersion"]), int)
        self.assertEqual(c["schemaVersion"], 1)
        self.assertIs(type(c["productionPolicyGenerated"]), bool)
        self.assertIs(c["productionPolicyGenerated"], False)
        self.assertEqual(c["kind"], "identity-empirical-binary-curve")
        self.assertEqual(c["validationStatus"], "unverified")
        self.assertEqual(c["trialCount"], 4)
        self.assertEqual(c["scoreDirection"], "higher-is-positive")

    def test_point_keys_and_rate_consistency(self):
        c = curves.empirical_curve(NONRESP, True)
        for p in c["points"]:
            self.assertEqual(set(p), {
                "rule", "threshold", "counts", "farOnResponse",
                "frrOnResponse", "tprOnResponse",
            })
            self.assertEqual(set(p["counts"]), {"positive", "negative"})
            for cls in ("positive", "negative"):
                self.assertEqual(set(p["counts"][cls]), {
                    "trials", "responses", "nonResponses", "errors",
                    "errorRateOnResponse", "nonResponseRate",
                })
            self.assertEqual(p["farOnResponse"],
                             p["counts"]["negative"]["errorRateOnResponse"])
            self.assertEqual(p["frrOnResponse"],
                             p["counts"]["positive"]["errorRateOnResponse"])
            frr = p["frrOnResponse"]
            self.assertEqual(p["tprOnResponse"],
                             None if frr is None else 1.0 - frr)

    def test_endpoints_match_binary_counts(self):
        recs = [rec(True, 0.9), rec(True, None),
                rec(False, 0.3), rec(False, None)]
        c = curves.empirical_curve(recs, True)
        rejected = stats.binary_counts([
            {"positive": r["positive"],
             "accepted": None if r["score"] is None else False}
            for r in recs
        ])
        accepted = stats.binary_counts([
            {"positive": r["positive"],
             "accepted": None if r["score"] is None else True}
            for r in recs
        ])
        self.assertEqual(c["points"][0]["counts"], rejected)
        self.assertEqual(c["points"][-1]["counts"], accepted)


class TestKnownAucValues(unittest.TestCase):

    def test_perfect_ranking_auc_one(self):
        c = curves.empirical_curve(PERFECT, True)
        self.assertAlmostEqual(c["aucOnResponse"], 1.0)
        self.assertEqual([p["threshold"] for p in c["points"]],
                         [None, 0.9, 0.8, 0.3, 0.2])
        self.assertEqual([p["rule"] for p in c["points"]],
                         ["reject-all", "inclusive-threshold",
                          "inclusive-threshold", "inclusive-threshold",
                          "inclusive-threshold"])
        ra = c["points"][0]
        self.assertIsNone(ra["threshold"])
        self.assertEqual(ra["counts"]["positive"], {
            "trials": 2, "responses": 2, "nonResponses": 0, "errors": 2,
            "errorRateOnResponse": 1.0, "nonResponseRate": 0.0,
        })
        self.assertEqual(ra["counts"]["negative"], {
            "trials": 2, "responses": 2, "nonResponses": 0, "errors": 0,
            "errorRateOnResponse": 0.0, "nonResponseRate": 0.0,
        })
        self.assertEqual(ra["farOnResponse"], 0.0)
        self.assertEqual(ra["frrOnResponse"], 1.0)
        self.assertEqual(ra["tprOnResponse"], 0.0)
        fa = c["points"][-1]
        self.assertEqual(fa["farOnResponse"], 1.0)
        self.assertEqual(fa["frrOnResponse"], 0.0)
        self.assertEqual(fa["tprOnResponse"], 1.0)

    def test_reverse_ranking_auc_zero(self):
        c = curves.empirical_curve(REVERSE, True)
        self.assertAlmostEqual(c["aucOnResponse"], 0.0)

    def test_full_ties_auc_half(self):
        c = curves.empirical_curve(TIES, True)
        self.assertAlmostEqual(c["aucOnResponse"], 0.5)
        self.assertEqual(len(c["points"]), 2)  # reject-all + single tied cutoff
        self.assertEqual(c["points"][1]["threshold"], 0.5)

    def test_mixed_ties_partial_ranking_hand_value(self):
        c = curves.empirical_curve(MIXED, True)
        self.assertAlmostEqual(c["aucOnResponse"], 13 / 18)
        self.assertEqual(len(c["points"]), 4)  # ties grouped, 3 distinct scores
        p = c["points"][1]  # cutoff 0.9: 2 pos + 1 neg tied cross together
        self.assertEqual(p["threshold"], 0.9)
        self.assertEqual(p["counts"]["positive"]["errors"], 1)
        self.assertEqual(p["counts"]["negative"]["errors"], 1)
        self.assertAlmostEqual(p["farOnResponse"], 1 / 3)
        self.assertAlmostEqual(p["frrOnResponse"], 1 / 3)
        self.assertAlmostEqual(p["tprOnResponse"], 2 / 3)

    def test_lower_direction_exact_ascending_equivalent(self):
        lo = curves.empirical_curve(LOWER, False)
        hi = curves.empirical_curve(HIGHER_NEGATED, True)
        self.assertEqual(lo["scoreDirection"], "lower-is-positive")
        self.assertEqual(hi["scoreDirection"], "higher-is-positive")
        self.assertEqual([p["farOnResponse"] for p in lo["points"]],
                         [p["farOnResponse"] for p in hi["points"]])
        self.assertEqual([p["tprOnResponse"] for p in lo["points"]],
                         [p["tprOnResponse"] for p in hi["points"]])
        lo_th = [p["threshold"] for p in lo["points"]][1:]
        hi_th = [p["threshold"] for p in hi["points"]][1:]
        self.assertEqual(lo_th, [1, 2, 3, 4])
        self.assertEqual(lo_th, sorted(lo_th))  # ascending
        self.assertEqual([-t for t in hi_th], lo_th)
        self.assertAlmostEqual(lo["aucOnResponse"], 1.0)
        self.assertAlmostEqual(lo["aucOnResponse"], hi["aucOnResponse"])


class TestNonresponses(unittest.TestCase):

    def test_denominators_exclude_null_trials(self):
        c = curves.empirical_curve(NONRESP, True)
        self.assertEqual(c["trialCount"], 6)
        self.assertAlmostEqual(c["aucOnResponse"], 1.0)
        for p in c["points"]:
            pos, neg = p["counts"]["positive"], p["counts"]["negative"]
            self.assertEqual(pos["trials"], 3)
            self.assertEqual(pos["responses"], 2)
            self.assertEqual(pos["nonResponses"], 1)
            self.assertAlmostEqual(pos["nonResponseRate"], 1 / 3)
            self.assertEqual(neg["trials"], 3)
            self.assertEqual(neg["responses"], 1)
            self.assertEqual(neg["nonResponses"], 2)
            self.assertAlmostEqual(neg["nonResponseRate"], 2 / 3)
        # errorRateOnResponse uses responses (2 and 1), NOT trials (3 and 3)
        self.assertEqual(c["points"][0]["frrOnResponse"], 1.0)
        self.assertEqual(c["points"][-1]["farOnResponse"], 1.0)
        self.assertEqual(c["points"][-1]["counts"]["negative"]
                         ["errorRateOnResponse"], 1.0)


class TestAucNoneCases(unittest.TestCase):

    def test_auc_none_when_either_class_has_no_responses(self):
        cases = [
            ([], True),
            ([rec(True, 0.5), rec(True, 0.6)], True),      # only positives
            ([rec(False, 0.5), rec(False, 0.6)], True),    # only negatives
            ([rec(True, None), rec(False, None)], True),   # all nonresponse
            ([rec(True, None)], True),
        ]
        for i, (recs, flag) in enumerate(cases):
            with self.subTest(i=i):
                self.assertIsNone(curves.empirical_curve(recs, flag)
                                  ["aucOnResponse"])

    def test_single_class_still_emits_points(self):
        c = curves.empirical_curve([rec(True, 0.5), rec(True, 0.6)], True)
        self.assertGreaterEqual(len(c["points"]), 2)
        for p in c["points"]:
            self.assertIsNone(p["farOnResponse"])
            self.assertIsNone(p["counts"]["negative"]
                              ["errorRateOnResponse"])
            self.assertIsNotNone(p["tprOnResponse"])

    def test_empty_records_just_reject_all(self):
        c = curves.empirical_curve([], True)
        self.assertEqual(c["trialCount"], 0)
        self.assertEqual(len(c["points"]), 1)
        self.assertEqual(c["points"][0]["rule"], "reject-all")
        self.assertIsNone(c["points"][0]["threshold"])
        self.assertEqual(c["points"][0]["counts"]["positive"]["trials"], 0)
        self.assertEqual(c["points"][0]["counts"]["negative"]["trials"], 0)

    def test_all_nonresponse_just_reject_all(self):
        c = curves.empirical_curve([rec(True, None), rec(False, None)], True)
        self.assertEqual(len(c["points"]), 1)
        self.assertEqual(c["points"][0]["rule"], "reject-all")


class TestValidation(unittest.TestCase):

    def test_valid_scores_accepted(self):
        for s in (5, -3, 0, 0.0, 1.5, -2.25, None):
            with self.subTest(s=s):
                c = curves.empirical_curve([rec(True, s), rec(False, 0.0)],
                                          True)
                self.assertEqual(c["trialCount"], 2)

    def test_invalid_inputs(self):
        valid = [rec(True, 0.9), rec(False, 0.3)]
        bad = [
            ([rec(True, True)], True),                          # bool score
            ([{"positive": 1, "score": 0.5}], True),            # positive !bool
            ([{"positive": True, "score": 0.5, "x": 1}], True),  # extra key
            ([{"positive": True}], True),                        # missing key
            ([rec(True, float("nan"))], True),                   # NaN
            ([rec(True, float("inf"))], True),                   # +Inf
            ([rec(True, float("-inf"))], True),                  # -Inf
            ([rec(True, 10 ** 400)], True),                       # huge int
            ([rec(True, -(10 ** 400))], True),                   # huge neg int
            ([rec(True, 2 ** 1024)], True),                       # int overflow
            ([rec(True, "0.5")], True),                           # str score
            ("not a list", True),                                # records !list
            ([1], True),                                          # rec !dict
            (valid, 1.0),                                         # flag float
            (valid, 1),                                           # flag int
            (valid, "True"),                                      # flag str
            (valid, None),                                        # flag None
        ]
        for i, (recs, flag) in enumerate(bad):
            with self.subTest(i=i):
                with self.assertRaises(ValueError):
                    curves.empirical_curve(recs, flag)

    def test_error_message_does_not_leak_record_content(self):
        with self.assertRaises(ValueError) as ctx:
            curves.empirical_curve([rec(True, 10 ** 400)], True)
        self.assertNotIn(str(10 ** 400), str(ctx.exception))


class TestImmutability(unittest.TestCase):

    def test_inputs_unchanged(self):
        recs = [rec(True, 0.9), rec(True, 0.5),
                rec(False, 0.3), rec(False, None)]
        snap = copy.deepcopy(recs)
        refs = list(recs)
        curves.empirical_curve(recs, True)
        self.assertEqual(recs, snap)
        for a, b in zip(recs, refs):
            self.assertIs(a, b)

    def test_counts_are_fresh_and_not_aliased(self):
        recs = [rec(True, 0.9), rec(False, 0.3), rec(True, None)]
        c = curves.empirical_curve(recs, True)
        pts = c["points"]
        self.assertGreaterEqual(len(pts), 3)
        c0, c1 = pts[0]["counts"], pts[1]["counts"]
        self.assertIsNot(c0, c1)
        self.assertIsNot(c0["positive"], c1["positive"])
        self.assertIsNot(c0["positive"], c0["negative"])
        c0["positive"]["errors"] = 9999
        self.assertNotEqual(c1["positive"]["errors"], 9999)
        self.assertNotEqual(pts[-1]["counts"]["positive"]["errors"], 9999)


if __name__ == "__main__":
    unittest.main()
