"""Strict shared AX active-cancel evidence, excluding touch/picker/OS acceptance."""
import argparse
import json
from pathlib import Path
import re
from uuid import UUID
if __package__ in {None, ""}:
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.multimodal_lifecycle_report import FIXTURE, IDENTITY, require

GATES = {"bootstrap", "available", "pageOpened", "inputAction", "pickerReply", "activeCancel", "reentry", "activeLeave", "noAutoRestart", "returnReentry", "closed"}


def validate(report: dict, expected_commit: str) -> dict:
    require(isinstance(report, dict), "report")
    require(re.fullmatch(r"[0-9a-f]{40}", expected_commit) is not None, "expected commit")
    require(type(report.get("schemaVersion")) is int and report["schemaVersion"] == 1, "schema")
    require(report.get("sourceCommit") == expected_commit and report.get("status") == "passed", "source/status")
    require(report.get("platform") == "physical" and type(report.get("pid")) is int and report["pid"] > 0, "physical PID")
    try:
        UUID(report.get("runId", ""))
    except (ValueError, TypeError, AttributeError) as error:
        raise ValueError("run ID") from error
    require(report.get("fixtureHash") == FIXTURE, "fixture")
    for key in ["touchAcceptance", "systemFileSelection", "osBackground"]:
        require(report.get(key) == "unverified", key + " boundary")
    gates = report.get("gates")
    require(isinstance(gates, dict) and set(gates) == GATES and all(v is True for v in gates.values()), "exact gates")
    scenarios = report.get("scenarios")
    require(isinstance(scenarios, list) and len(scenarios) == 4 and all(isinstance(v, dict) for v in scenarios), "scenarios")
    require([v.get("name") for v in scenarios] == ["cancel", "reentry", "leave", "returnReentry"], "scenario order")
    for item in scenarios:
        require(item.get("actionAccepted") is True, "action accepted")
        if item["name"] in {"cancel", "leave"}:
            first, action = item.get("observedSequence"), item.get("actionSequence")
            require(type(first) is int and type(action) is int and 0 <= first <= action, "actual update before action")
            require(item.get("actionPhase") == "RUNNING" and item.get("terminal") == "CANCELLED", "active cancellation")
            require(item.get("output") is None, "cancelled output")
        else:
            require(item.get("terminal") == "SUCCEEDED", "reentry terminal")
            output = item.get("output")
            require(isinstance(output, dict) and all(output.get(k) == v for k, v in IDENTITY.items()), "output identity")
            require(isinstance(output.get("text"), str) and bool(output["text"].strip()), "nonempty output")
    return {"status": "passed", "sourceCommit": expected_commit, "runId": report["runId"], "pid": report["pid"], "scope": "App-local AX active cancel/leave/reentry", "touchAcceptance": "unverified", "systemFileSelection": "unverified", "osBackground": "unverified"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(validate(json.loads(args.report.read_text()), args.source_commit), indent=2))
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + "\n")
