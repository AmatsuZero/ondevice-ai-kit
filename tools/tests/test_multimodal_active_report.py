import copy
import unittest
from tools.multimodal_active_report import validate

COMMIT = "a" * 40
IDENTITY = {"backendId": "mlx.smolvlm2-500m", "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx", "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268", "runtimeRevision": "6e338e5df49424fc0db7c6d58184d622f92ed18a"}


def report():
    scenarios = []
    for name in ["cancel", "reentry", "leave", "returnReentry"]:
        item = {"name": name, "actionAccepted": True}
        if name in ["cancel", "leave"]:
            item.update(observedSequence=0, actionSequence=1, actionPhase="RUNNING", terminal="CANCELLED")
        else:
            item.update(terminal="SUCCEEDED", output={**IDENTITY, "text": "synthetic output"})
        scenarios.append(item)
    return {"schemaVersion": 1, "sourceCommit": COMMIT, "runId": "c0a9eb9a-6c15-4d87-824d-c088c0591381", "pid": 123, "platform": "physical", "status": "passed", "fixtureHash": "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606", "gates": {k: True for k in ["bootstrap", "available", "pageOpened", "inputAction", "pickerReply", "activeCancel", "reentry", "activeLeave", "noAutoRestart", "returnReentry", "closed"]}, "scenarios": scenarios, "touchAcceptance": "unverified", "systemFileSelection": "unverified", "osBackground": "unverified"}


class MultimodalActiveReportTests(unittest.TestCase):
    def testAcceptsActualScopeAndLeavesPhysicalUiUnverified(self):
        summary = validate(report(), COMMIT)
        self.assertEqual(summary["status"], "passed")
        self.assertEqual(summary["touchAcceptance"], "unverified")

    def testRejectsProvenanceAndMissingOrCoercedGate(self):
        for key, value in [("sourceCommit", "b" * 40), ("platform", "simulator"), ("status", "unverified"), ("fixtureHash", "wrong"), ("pid", True), ("touchAcceptance", "passed")]:
            item = report(); item[key] = value
            with self.assertRaises(ValueError): validate(item, COMMIT)
        for value in [False, 1, "true", None]:
            item = report(); item["gates"]["activeCancel"] = value
            with self.assertRaises(ValueError): validate(item, COMMIT)
        item = report(); del item["gates"]["noAutoRestart"]
        with self.assertRaises(ValueError): validate(item, COMMIT)

    def testRejectsAbsentUpdateLateActionFalseCancelOrWrongSequence(self):
        for key, value in [("observedSequence", None), ("observedSequence", -1), ("observedSequence", True), ("actionSequence", -1), ("actionPhase", "SUCCEEDED"), ("actionAccepted", False), ("terminal", "SUCCEEDED")]:
            item = report(); item["scenarios"][0][key] = value
            with self.assertRaises(ValueError): validate(item, COMMIT)
        item = report(); item["scenarios"][2]["output"] = copy.deepcopy(item["scenarios"][1]["output"])
        with self.assertRaises(ValueError): validate(item, COMMIT)
        item = report(); item["scenarios"].reverse()
        with self.assertRaises(ValueError): validate(item, COMMIT)

    def testRejectsReentryIdentityOrEmptyOutput(self):
        for key in [*IDENTITY, "text"]:
            item = report(); item["scenarios"][1]["output"][key] = "" if key == "text" else "wrong"
            with self.assertRaises(ValueError): validate(item, COMMIT)
