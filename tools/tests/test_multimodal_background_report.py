import copy, json, subprocess, sys, tempfile, unittest
from pathlib import Path
from tools.multimodal_background_report import validate
COMMIT='a'*40
IDENTITY={"backendId":"mlx.smolvlm2-500m","modelId":"mlx-community/SmolVLM2-500M-Video-Instruct-mlx","modelRevision":"fa57db46815177fbdfd65cc85a2b3416a8332268","runtimeRevision":"6e338e5df49424fc0db7c6d58184d622f92ed18a"}
GATES=["bootstrap","available","pageOpened","inputAction","pickerReply","backgroundReady","osWillResignActive","osDidEnterBackground","osWillEnterForeground","osDidBecomeActive","activeCancel","noAutoRestart","reentry","closed"]
def report():
 return {"schemaVersion":1,"sourceCommit":COMMIT,"runId":"c0a9eb9a-6c15-4d87-824d-c088c0591381","pid":123,"platform":"physical","status":"passed","fixtureHash":"60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606","touchAcceptance":"unverified","systemFileSelection":"unverified","osBackground":"verified","gates":{k:True for k in GATES},"ready":{"pid":123,"sourceCommit":COMMIT,"runId":"c0a9eb9a-6c15-4d87-824d-c088c0591381","phase":"RUNNING","sequence":0},"events":[{"name":n,"state":s,"phase":p,"sequence":0} for n,s,p in [("willResignActive",0,"RUNNING"),("didEnterBackground",2,"CANCELLED"),("willEnterForeground",2,"CANCELLED"),("didBecomeActive",0,"CANCELLED")]],"cancel":{"phase":"CANCELLED","output":None,"sequence":0},"reentry":{"terminal":"SUCCEEDED","output":{**IDENTITY,"text":"output"}}}
class Tests(unittest.TestCase):
 def testValidAndBothCliForms(self):
  item=report(); self.assertEqual(validate(item,COMMIT)["status"],"passed")
  with tempfile.TemporaryDirectory() as d:
   f=Path(d)/"r.json";f.write_text(json.dumps(item))
   for inv in [[str(Path(__file__).resolve().parents[1]/"multimodal_background_report.py")],["-m","tools.multimodal_background_report"]]:
    r=subprocess.run([sys.executable,*inv,str(f),"--source-commit",COMMIT],cwd=Path(__file__).resolve().parents[2],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stderr)
 def testRejectsMutations(self):
  for key,val in [("sourceCommit","b"*40),("platform","simulator"),("fixtureHash","x"),("pid",True),("osBackground","unverified")]:
   i=report();i[key]=val
   with self.assertRaises(ValueError):validate(i,COMMIT)
  for gate in ["osDidEnterBackground","activeCancel"]:
   i=report();i["gates"][gate]=False
   with self.assertRaises(ValueError):validate(i,COMMIT)
  for events in [["didEnterBackground","willResignActive","willEnterForeground","didBecomeActive"],["willResignActive","didEnterBackground","didBecomeActive"]]:
   i=report();i["events"]=events
   with self.assertRaises(ValueError):validate(i,COMMIT)
 def testRejectsStaleReadyOrInvalidCancelReentry(self):
  for key,val in [("sourceCommit","b"*40),("pid",999),("phase","SUCCEEDED")]:
   i=report();i["ready"][key]=val
   with self.assertRaises(ValueError):validate(i,COMMIT)
  i=report();i["cancel"]["phase"]="SUCCEEDED"
  with self.assertRaises(ValueError):validate(i,COMMIT)
  i=report();i["reentry"]["output"]["runtimeRevision"]="wrong"
  with self.assertRaises(ValueError):validate(i,COMMIT)

 def testRejectsInactiveCallbackCoercedGateAndCancelOutput(self):
  for value in [False,1,"true",None]:
   i=report();i["gates"]["noAutoRestart"]=value
   with self.assertRaises(ValueError):validate(i,COMMIT)
  for key,value in [("phase","SUCCEEDED"),("sequence",None)]:
   i=report();i["events"][0][key]=value
   with self.assertRaises(ValueError):validate(i,COMMIT)
  i=report();i["events"][1]["state"]=0
  with self.assertRaises(ValueError):validate(i,COMMIT)
  i=report();i["cancel"]["output"]={"text":"stale"}
  with self.assertRaises(ValueError):validate(i,COMMIT)
