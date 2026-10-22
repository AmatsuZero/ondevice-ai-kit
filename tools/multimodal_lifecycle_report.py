"""Validate programmatic KMP host/VM device evidence; never infer UI or quality acceptance."""
import argparse
import json
import re
from pathlib import Path
from uuid import UUID

IDENTITY = {"backendId": "mlx.smolvlm2-500m", "modelId": "mlx-community/SmolVLM2-500M-Video-Instruct-mlx", "modelRevision": "fa57db46815177fbdfd65cc85a2b3416a8332268", "runtimeRevision": "6e338e5df49424fc0db7c6d58184d622f92ed18a"}
FIXTURE = "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606"
GATES = {"availability", "success", "cancel", "reentry", "background", "close", "modelLeaseProtected", "modelLeaseReleased", "inputsReleased", "closedRejected"}
SCENARIOS = {"success", "cancel", "reentry", "background", "close"}


def require(condition, label):
    if not condition:
        raise ValueError(label)


def validate(report: dict, expected_commit: str) -> dict:
    require(isinstance(report, dict), "report object")
    require(re.fullmatch(r"[0-9a-f]{40}", expected_commit) is not None, "expected source commit")
    require(type(report.get("schemaVersion")) is int and report["schemaVersion"] == 1, "schema")
    require(report.get("sourceCommit") == expected_commit, "source commit")
    require(report.get("status") == "passed", "device status")
    require(report.get("platform") == "physical", "physical device required")
    require(type(report.get("pid")) is int and report["pid"] > 0, "pid")
    require(isinstance(report.get("os"), str) and bool(report["os"].strip()), "OS")
    try:
        UUID(report.get("runId", ""))
    except (ValueError, TypeError, AttributeError) as error:
        raise ValueError("run ID") from error
    require(report.get("fixtureHash") == FIXTURE, "fixture hash")
    require(report.get("identity") == IDENTITY, "identity")
    gates = report.get("gates")
    require(isinstance(gates, dict) and set(gates) == GATES and all(v is True for v in gates.values()), "all exact gates true")
    scenarios = report.get("scenarios")
    require(isinstance(scenarios, dict) and set(scenarios) == SCENARIOS, "scenarios")
    for name, scenario in scenarios.items():
        require(isinstance(scenario, dict), name + " object")
        events = scenario.get("events")
        require(isinstance(events, list) and len(events) >= 4 and all(isinstance(e, dict) for e in events), name + " events")
        names = [e.get("event") for e in events]
        require(names[0] == "request" and names[-2:] == ["inputReleased", "terminal"], name + " ownership order")
        action = [] if name in {"success", "reentry"} else [name]
        require(names.count("request") == 1 and names.count("inputReleased") == 1 and names.count("terminal") == 1, name + " unique lifecycle")
        middle = names[1:-2]
        updates = middle if not action else middle[:-1]
        require(bool(updates) and all(e == "update" for e in updates) and (not action or middle[-1:] == action), name + " update before action")
        sequence = -1
        for event in events[1:1 + len(updates)]:
            value = event.get("sequence")
            require(type(value) is int and value > sequence, name + " update sequence")
            sequence = value
        succeeded = name in {"success", "reentry"}
        require(events[-1].get("phase") == ("SUCCEEDED" if succeeded else "CANCELLED"), name + " phase")
        output = scenario.get("output")
        if succeeded:
            require(isinstance(output, dict) and all(output.get(k) == v for k, v in IDENTITY.items()), name + " output identity")
            require(isinstance(output.get("text"), str) and bool(output["text"].strip()), name + " text")
        else:
            require(output is None, name + " cancelled output")
    return {"status": "passed", "sourceCommit": expected_commit, "runId": report["runId"], "pid": report["pid"], "scope": "programmatic KMP host/VM", "uiAcceptance": "unverified", "quality": "unverified", "resources": "unverified"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(validate(json.loads(args.report.read_text()), args.source_commit), indent=2))
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + "\n")
