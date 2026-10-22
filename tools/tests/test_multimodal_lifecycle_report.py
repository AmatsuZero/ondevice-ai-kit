import copy
import unittest
from tools.multimodal_lifecycle_report import validate

COMMIT = "a" * 40
IDENTITY = {"backendId": "mlx.smolvlm2-500m", "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx", "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268", "runtimeRevision": "6e338e5df49424fc0db7c6d58184d622f92ed18a"}

def valid_report():
    scenarios = {}
    for name in ["success", "cancel", "reentry", "background", "close"]:
        events = [{"event": "request"}, {"event": "update", "sequence": 0}]
        if name not in ["success", "reentry"]:
            events.append({"event": name})
        events += [{"event": "inputReleased"}, {"event": "terminal", "phase": "SUCCEEDED" if name in ["success", "reentry"] else "CANCELLED"}]
        scenarios[name] = {"events": events}
        if name in ["success", "reentry"]:
            scenarios[name]["output"] = {**IDENTITY, "text": "synthetic result"}
    return {"schemaVersion": 1, "sourceCommit": COMMIT, "runId": "c0a9eb9a-6c15-4d87-824d-c088c0591381", "pid": 123, "platform": "physical", "os": "27.0.1", "fixtureHash": "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606", "identity": dict(IDENTITY), "status": "passed", "gates": {name: True for name in ["availability", "success", "cancel", "reentry", "background", "close", "modelLeaseProtected", "modelLeaseReleased", "inputsReleased", "closedRejected"]}, "scenarios": scenarios}


class MultimodalLifecycleReportTests(unittest.TestCase):
    def testAcceptsCompleteEvidenceWithExplicitUiBoundary(self):
        result = validate(valid_report(), COMMIT)
        self.assertEqual(result["status"], "passed")
        self.assertEqual(result["uiAcceptance"], "unverified")

    def testRejectsMissingFalseOrTypeConfusedGate(self):
        for value in [False, 1, "true", None]:
            report = valid_report(); report["gates"]["close"] = value
            with self.assertRaises(ValueError): validate(report, COMMIT)
        report = valid_report(); del report["gates"]["cancel"]
        with self.assertRaises(ValueError): validate(report, COMMIT)

    def testRejectsWrongProvenanceOrIdentity(self):
        for field, value in [("sourceCommit", "b" * 40), ("fixtureHash", "wrong"), ("platform", "simulator"), ("pid", True), ("status", "failed"), ("runId", ""), ("os", "")]:
            report = valid_report(); report[field] = value
            with self.assertRaises(ValueError): validate(report, COMMIT)
        for field in IDENTITY:
            report = valid_report(); report["identity"][field] = "wrong"
            with self.assertRaises(ValueError): validate(report, COMMIT)
            report = valid_report(); report["scenarios"]["reentry"]["output"][field] = "wrong"
            with self.assertRaises(ValueError): validate(report, COMMIT)

    def testRejectsMissingOrMisorderedActualEvents(self):
        for name in valid_report()["scenarios"]:
            for index in range(len(valid_report()["scenarios"][name]["events"])):
                report = valid_report(); del report["scenarios"][name]["events"][index]
                with self.assertRaises(ValueError): validate(report, COMMIT)
        report = valid_report(); e = report["scenarios"]["cancel"]["events"]; e[-1], e[-2] = e[-2], e[-1]
        with self.assertRaises(ValueError): validate(report, COMMIT)
        report = valid_report(); e = report["scenarios"]["background"]["events"]; e[1], e[2] = e[2], e[1]
        with self.assertRaises(ValueError): validate(report, COMMIT)

    def testRejectsDuplicateSequenceOrFalseTerminalSuccess(self):
        for value in [0, -1, True]:
            report = valid_report(); report["scenarios"]["success"]["events"].insert(2, {"event": "update", "sequence": value})
            with self.assertRaises(ValueError): validate(report, COMMIT)
        report = valid_report(); report["scenarios"]["cancel"]["events"][-1]["phase"] = "SUCCEEDED"
        with self.assertRaises(ValueError): validate(report, COMMIT)
        report = valid_report(); report["scenarios"]["success"]["output"]["text"] = ""
        with self.assertRaises(ValueError): validate(report, COMMIT)
        report = valid_report(); report["scenarios"]["close"]["output"] = copy.deepcopy(report["scenarios"]["success"]["output"])
        with self.assertRaises(ValueError): validate(report, COMMIT)
