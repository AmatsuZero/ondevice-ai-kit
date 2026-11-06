"""Offline acceptance checks for the fixed-input report reducer. No device access."""
import copy
import unittest

from tools.probes import android_asr_resource_report as module


def fixture():
    rows=[];timestamp=1000000000
    for i in range(35):
        duration=9999000000 if i<5 else (i-4)*1000000
        start=timestamp+1000000;end=start+duration
        rss=[{"elapsedNs":start-1,"bytes":1000+i},{"elapsedNs":start+duration//2,"bytes":2000+i},{"elapsedNs":end+1,"bytes":1500+i}]
        rows.append({"index":i,"phase":"warmup" if i<5 else "measurement","backendId":"sherpa.onnx.whisper","expectedMatched":True,"callerInputUnchanged":True,"durationNs":duration,"startedElapsedNs":start,"endedElapsedNs":end,"rss":rss,"rssPeakSampledBytes":2000+i,"afterIdleRssBytes":1500+i})
        timestamp=end+1000000
    return {"schemaVersion":1,"kind":"android-asr-resource","runId":"resource-check-1","status":"completed","terminal":True,"mode":"inference","backendId":"sherpa.onnx.whisper","pid":123,"inputSHA256":"fc0f3f5bb9b06a4c21e12321d5fb0bc7d3e3151dca855e5ed9693907ce39df46","microphoneStarts":0,"audioPersisted":False,"transcriptPersisted":False,"warmupCount":5,"measurementCount":30,"samplingIntervalMs":20,"measurementMethod":"proc-self-status-VmRSS-sampled-process","timingMethod":"sdk-transcribe-wall-including-verify-vad-native-create-decode-release","finalCloseSucceeded":True,"callerInputUnchanged":True,"closeDurationNs":0,"beforeRssBytes":1000,"afterCloseRssBytes":1001,"afterCloseWaitMs":1000,"afterCloseWaitRssBytes":1002,"samples":rows}


class SummaryChecks(unittest.TestCase):
    def test_nearest_rank_excludes_warmups(self):
        value=module.summarize(fixture())
        self.assertEqual(value["durationMs"],{"min":1.0,"max":30.0,"p50":15.0,"p95":29.0})
        self.assertEqual(value["rssPeakSampledBytes"],{"min":2005,"max":2034})
        self.assertEqual(value["afterIdleRssBytes"],{"min":1505,"max":1534,"first":1505,"last":1534})
        self.assertEqual(value["validationStatus"],"pass-bounded")
        self.assertEqual(value["qualityVerdict"],"unverified")
        self.assertIn("true peak memory",value["unverified"])
        self.assertIn("current SDK cleanup changes",value["unverified"])

    def test_rejects_noninteger_top_fields(self):
        for key in ["schemaVersion","pid","microphoneStarts","warmupCount","measurementCount","samplingIntervalMs","closeDurationNs","beforeRssBytes","afterCloseRssBytes","afterCloseWaitMs","afterCloseWaitRssBytes"]:
            for replacement in [True,False,float(fixture()[key]),None]:
                with self.subTest(key=key,value=replacement):
                    report=fixture();report[key]=replacement
                    with self.assertRaises(ValueError):module.summarize(report)

    def test_rejects_noninteger_row_fields(self):
        for key in ["index","durationNs","startedElapsedNs","endedElapsedNs","rssPeakSampledBytes","afterIdleRssBytes"]:
            for replacement in [True,False,float(fixture()["samples"][0][key]),None]:
                with self.subTest(key=key,value=replacement):
                    report=fixture();report["samples"][0][key]=replacement
                    with self.assertRaises(ValueError):module.summarize(report)

    def test_rejects_noninteger_rss_points(self):
        for key in ["elapsedNs","bytes"]:
            for replacement in [True,False,1.0,None]:
                with self.subTest(key=key,value=replacement):
                    report=fixture();report["samples"][0]["rss"][0][key]=replacement
                    with self.assertRaises(ValueError):module.summarize(report)

    def test_rejects_incomplete_self_test_and_mismatched_identity(self):
        for key,value in [("mode","recorder-pass"),("terminal",False),("status","running"),("status","failed"),("backendId","other"),("inputSHA256","0"*64),("finalCloseSucceeded",False),("callerInputUnchanged",False),("microphoneStarts",1),("audioPersisted",True),("transcriptPersisted",True),("runId","bad/path")]:
            with self.subTest(key=key,value=value):
                report=fixture();report[key]=value
                with self.assertRaises(ValueError):module.summarize(report)

    def test_requires_all_top_fields(self):
        for key in fixture():
            with self.subTest(key=key):
                report=fixture();del report[key]
                with self.assertRaises(ValueError):module.summarize(report)

    def test_requires_all_row_fields(self):
        for key in fixture()["samples"][0]:
            with self.subTest(key=key):
                report=fixture();del report["samples"][0][key]
                with self.assertRaises(ValueError):module.summarize(report)

    def test_rejects_wrong_rows_and_overlap(self):
        for change in [lambda r:r["samples"].pop(),lambda r:r["samples"][0].update(expectedMatched=False),lambda r:r["samples"][3].update(phase="measurement"),lambda r:r["samples"][6].update(index=5),lambda r:r["samples"][7].update(endedElapsedNs=r["samples"][7]["endedElapsedNs"]+1)]:
            report=fixture();change(report)
            with self.assertRaises(ValueError):module.summarize(report)
        report=fixture();row=report["samples"][1];row["startedElapsedNs"]=report["samples"][0]["endedElapsedNs"];row["endedElapsedNs"]=row["startedElapsedNs"]+row["durationNs"]
        with self.assertRaises(ValueError):module.summarize(report)

    def test_rejects_unproven_sampled_peaks(self):
        for mutate in [lambda s:s["rss"].pop(),lambda s:s.update(rssPeakSampledBytes=999),lambda s:s["rss"][1].update(elapsedNs=s["rss"][0]["elapsedNs"]),lambda s:s["rss"][0].update(elapsedNs=s["startedElapsedNs"]+1),lambda s:s["rss"][-1].update(elapsedNs=s["endedElapsedNs"]-1)]:
            report=fixture();mutate(report["samples"][0])
            with self.assertRaises(ValueError):module.summarize(report)
        report=fixture();row=report["samples"][0];row["rss"][1]["elapsedNs"]=row["startedElapsedNs"]-2;row["rss"][0]["elapsedNs"]=row["startedElapsedNs"]-3
        with self.assertRaises(ValueError):module.summarize(report)

    def test_does_not_mutate_raw_report(self):
        report=fixture();before=copy.deepcopy(report);module.summarize(report);self.assertEqual(report,before)

if __name__=="__main__":unittest.main()
