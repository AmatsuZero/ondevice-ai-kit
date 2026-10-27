#!/usr/bin/env python3
"""Validate native benchmark runs and group comparable samples (nearest-rank percentiles)."""
import argparse
import json
import math
from pathlib import Path
GROUP_FIELDS=("device","OS","sdkCommit","capability","actualBackend","modelRevision","preprocessRevision","fixtureHash","measurementMethod","validationStatus")
PHASES=("coldStart","modelLoad","warmup","inference")
def validate(run):
    if run.get("schemaVersion")!=1 or run.get("units")!="ms":raise ValueError("unsupported schema or units")
    for key in ("runId",)+GROUP_FIELDS:
        if key=="modelRevision":
            if key not in run or (run[key] is not None and not isinstance(run[key],str)):raise ValueError("invalid modelRevision")
        elif not isinstance(run.get(key),str) or not run[key]:raise ValueError("missing metadata: "+key)
    fixture=run["fixtureHash"]
    if len(fixture)!=64 or any(c not in "0123456789abcdef" for c in fixture):raise ValueError("invalid fixture hash")
    if run["validationStatus"] not in ("unit-fixture","simulator","device"):raise ValueError("invalid validation status")
    timing=run.get("timingsMs")
    if not isinstance(timing,dict) or set(timing)!=set(PHASES):raise ValueError("missing timing phases")
    for values in timing.values():
        if not isinstance(values,list) or any(type(x) not in (int,float) or not math.isfinite(x) or x<0 for x in values):raise ValueError("invalid duration")
    if run.get("energyStatus")!="unmeasured" or run.get("energy") is not None:
        raise ValueError("schema v1 does not support energy measurements without a defined instrument schema")
    memory=run.get("peakMemory")
    if memory is not None:
        if not isinstance(memory,dict) or type(memory.get("bytes")) is not int or memory["bytes"]<0 or not isinstance(memory.get("method"),str) or not memory["method"]:raise ValueError("invalid memory measurement")
    if not isinstance(run.get("quality"),dict):raise ValueError("quality must be an inspectable object")
    return run

def statistics(values):
    values=sorted(values);n=len(values)
    return dict(count=n,p50=values[math.ceil(n*.50)-1] if n else None,p95=values[math.ceil(n*.95)-1] if n else None)

def summarize(runs):
    groups={}
    seen_run_ids=set()
    for raw in runs:
        run=validate(raw)
        if run["runId"] in seen_run_ids:raise ValueError("duplicate runId: "+run["runId"])
        seen_run_ids.add(run["runId"])
        # Memory methods differ between platforms and instruments; do not combine them.
        memory_method=(run.get("peakMemory") or {}).get("method")
        key=tuple(run[field] for field in GROUP_FIELDS)+(memory_method,)
        if key not in groups:
            groups[key]=dict(metadata={field:run[field] for field in GROUP_FIELDS},memoryMethod=memory_method,runs=[],timingsMs={phase:[] for phase in PHASES})
        group=groups[key];group["runs"].append({field:run.get(field) for field in ("runId","peakMemory","energy","energyStatus","thermal","quality")})
        for phase in PHASES:group["timingsMs"][phase].extend(run["timingsMs"][phase])
    for group in groups.values():group["timingsMs"]={phase:statistics(values) for phase,values in group["timingsMs"].items()}
    return dict(schemaVersion=1,percentileMethod="nearest-rank",groups=list(groups.values()))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("inputs",nargs="+");parser.add_argument("--output",required=True);args=parser.parse_args()
    def invalid_constant(value):raise ValueError("non-finite JSON number: "+value)
    inputs=[Path(name) for name in args.inputs]
    output=Path(args.output)
    for source in inputs:
        if output.resolve()==source.resolve() or (output.exists() and output.samefile(source)):
            parser.error("output must not overwrite benchmark input: "+str(source))
    runs=[json.loads(source.read_text(),parse_constant=invalid_constant) for source in inputs]
    contents=json.dumps(summarize(runs),ensure_ascii=False,indent=2,allow_nan=False)+"\n"
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(contents)
if __name__=="__main__":main()
