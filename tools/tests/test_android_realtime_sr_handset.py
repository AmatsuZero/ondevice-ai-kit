import unittest

from tools.probes.android_realtime_sr_handset import assess_legs


SEQUENCE = ["original-egl", "auto"]
GATES = ("gpu", "endToEnd", "missed", "srDelivery")


def make_runs():
    return [
        {
            "id": "original-1",
            "status": "success",
            "launch_case": "original-egl",
            "decode_completion": True,
            "gates": {
                "gpu": "notApplicable",
                "endToEnd": "pass",
                "missed": "pass",
                "srDelivery": "notApplicable",
            },
        },
        {
            "id": "auto-1",
            "status": "success",
            "launch_case": "auto",
            "decode_completion": True,
            "gates": {gate: "pass" for gate in GATES},
        },
    ]


class AssessLegsTests(unittest.TestCase):
    def test_passes_with_expected_evidence_for_each_leg(self):
        verdict, evidence = assess_legs(make_runs(), SEQUENCE)

        self.assertEqual(verdict, "pass")
        self.assertEqual(len(evidence), len(SEQUENCE))
        self.assertEqual([item["leg"] for item in evidence], SEQUENCE)
        self.assertEqual(evidence[0]["gates"]["gpu"], "notApplicable")
        self.assertEqual(evidence[0]["gates"]["endToEnd"], "pass")
        self.assertEqual(evidence[0]["gates"]["missed"], "pass")
        self.assertEqual(evidence[0]["gates"]["srDelivery"], "notApplicable")

    def test_each_unverified_auto_gate_makes_verdict_unverified(self):
        for gate in GATES:
            with self.subTest(gate=gate):
                runs = make_runs()
                runs[1]["gates"][gate] = "unverified"

                verdict, evidence = assess_legs(runs, SEQUENCE)

                self.assertEqual(verdict, "unverified")
                self.assertEqual(len(evidence), len(SEQUENCE))
                self.assertEqual(evidence[1]["gates"][gate], "unverified")

    def test_failure_takes_precedence_over_unverified_evidence(self):
        runs = make_runs()
        runs[1]["gates"]["gpu"] = "fail"
        runs[1]["gates"]["missed"] = "unverified"

        verdict, evidence = assess_legs(runs, SEQUENCE)

        self.assertEqual(verdict, "fail")
        self.assertEqual(len(evidence), len(SEQUENCE))

    def test_rejects_malformed_order(self):
        runs = make_runs()
        runs.reverse()

        with self.assertRaises(RuntimeError):
            assess_legs(runs, SEQUENCE)

    def test_rejects_duplicate_or_blank_ids(self):
        for invalid_id in ("original-1", " ", ""):
            with self.subTest(run_id=invalid_id):
                runs = make_runs()
                runs[1]["id"] = invalid_id

                with self.assertRaises(RuntimeError):
                    assess_legs(runs, SEQUENCE)

    def test_rejects_incomplete_decode(self):
        runs = make_runs()
        runs[1]["decode_completion"] = False

        with self.assertRaises(RuntimeError):
            assess_legs(runs, SEQUENCE)


if __name__ == "__main__":
    unittest.main()
