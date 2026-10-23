"""Strict real OS background evidence for the enhanced project Demo."""
import argparse
import json
from pathlib import Path
if __package__ in {None, ""}:
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.multimodal_lifecycle_report import FIXTURE, IDENTITY, require
from uuid import UUID
import re
GATES = {"bootstrap","available","pageOpened","inputAction","pickerReply","backgroundReady","osWillResignActive","osDidEnterBackground","osWillEnterForeground","osDidBecomeActive","activeCancel","noAutoRestart","reentry","closed"}
def validate(r, expected_commit):
    require(isinstance(r,dict) and re.fullmatch(r"[0-9a-f]{40}",expected_commit) is not None,"object/commit")
    require(r.get("sourceCommit")==expected_commit and r.get("status")=="passed","source/status")
    require(type(r.get("schemaVersion")) is int and r["schemaVersion"]==1,"schema")
    require(r.get("platform")=="physical" and type(r.get("pid")) is int and r["pid"]>0,"physical PID")
    try: UUID(r.get("runId",""))
    except (ValueError,TypeError,AttributeError) as e: raise ValueError("run ID") from e
    require(r.get("fixtureHash")==FIXTURE,"fixture")
    require(r.get("touchAcceptance")=="unverified" and r.get("systemFileSelection")=="unverified" and r.get("osBackground")=="verified","boundaries")
    g=r.get("gates"); require(isinstance(g,dict) and set(g)==GATES and all(v is True for v in g.values()),"exact gates")
    ready=r.get("ready");require(isinstance(ready,dict),"ready")
    require(all(ready.get(k)==r.get(k) for k in ["pid","sourceCommit","runId"]),"ready provenance")
    require(type(ready.get("pid")) is int and ready.get("phase")=="RUNNING" and type(ready.get("sequence")) is int and ready["sequence"]>=0,"ready update")
    events=r.get("events");require(isinstance(events,list) and len(events)==4 and all(isinstance(e,dict) for e in events),"events")
    require([e.get("name") for e in events]==["willResignActive","didEnterBackground","willEnterForeground","didBecomeActive"],"OS order")
    require(events[0].get("phase")=="RUNNING" and type(events[0].get("sequence")) is int and events[0]["sequence"]>=ready["sequence"],"active before OS background")
    require(type(events[1].get("state")) is int and events[1]["state"]==2 and type(events[3].get("state")) is int and events[3]["state"]==0,"OS state")
    cancel=r.get("cancel");require(isinstance(cancel,dict) and cancel.get("phase")=="CANCELLED" and cancel.get("output") is None and cancel.get("failure") is None,"cancel")
    reentry=r.get("reentry");require(isinstance(reentry,dict) and reentry.get("terminal")=="SUCCEEDED","reentry")
    output=reentry.get("output");require(isinstance(output,dict) and all(output.get(k)==v for k,v in IDENTITY.items()) and isinstance(output.get("text"),str) and bool(output["text"].strip()),"identity/text")
    return {"status":"passed","sourceCommit":expected_commit,"pid":r["pid"],"runId":r["runId"],"osBackground":"verified","touchAcceptance":"unverified","systemFileSelection":"unverified"}
if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("report",type=Path);p.add_argument("--source-commit",required=True);a=p.parse_args()
    try: print(json.dumps(validate(json.loads(a.report.read_text()),a.source_commit),indent=2))
    except (ValueError,OSError) as e:p.exit(1,str(e)+"\n")
