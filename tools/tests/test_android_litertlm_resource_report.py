"""Offline acceptance checks for the fixed-input report reducer. No device access."""
import hashlib
import copy
import unittest

from tools.probes import android_litertlm_resource_report as module


def fixture():
    rows=[];timestamp=1000000000
    for i in range(6):
        duration=9999000000 if i<1 else i*1000000
        start=timestamp+1000000;end=start+duration
        rss=[{"elapsedNs":start-1,"bytes":1000+i},{"elapsedNs":start+duration//2,"bytes":2000+i},{"elapsedNs":end+1,"bytes":1500+i}]
        rows.append({"index":i,"phase":"warmup" if i<1 else "measurement","backendId":"litertlm.android","expectedMatched":True,"callerInputUnchanged":True,"cacheFilesRemoved":True,"modelId":"gemma-3n-e2b-it","modelRevision":"c03b6f60b8da6c5400b6838a2cf26420f80c0a01","runtimeRevision":"litertlm-android:0.18.1","outputSHA256":"a"*64,"outputLength":3,"durationNs":duration,"startedElapsedNs":start,"endedElapsedNs":end,"rss":rss,"rssPeakSampledBytes":2000+i,"afterIdleRssBytes":1500+i})
        timestamp=end+1000000
    return {"schemaVersion":1,"kind":"android-litertlm-resource","runId":"resource-check-1","status":"completed","terminal":True,"mode":"inference","backendId":"litertlm.android","pid":123,"inputSHA256":"991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa","microphoneStarts":0,"modelId":"gemma-3n-e2b-it","modelRevision":"c03b6f60b8da6c5400b6838a2cf26420f80c0a01","runtimeRevision":"litertlm-android:0.18.1","modelSHA256":"2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6","backendPolicy":"CPU_TEXT_GPU_VISION","maxOutputTokens":32,"promptSHA256":hashlib.sha256(b"What is the color of this image? Respond with a single color word.").hexdigest(),"cacheFilesRemoved":True,"warmupCount":1,"measurementCount":5,"samplingIntervalMs":20,"measurementMethod":"proc-self-status-VmRSS-sampled-process","timingMethod":"sdk-generate-wall-including-verify-snapshot-native-create-send-release","finalCloseSucceeded":True,"callerInputUnchanged":True,"closeDurationNs":0,"beforeRssBytes":1000,"afterCloseRssBytes":1001,"afterCloseWaitMs":1000,"afterCloseWaitRssBytes":1002,"samples":rows}


class SummaryChecks(unittest.TestCase):
    def test_nearest_rank_excludes_warmups(self):
        value=module.summarize(fixture())
        self.assertEqual(value["durationMs"],{"min":1.0,"max":5.0,"p50":3.0,"p95":5.0})
        self.assertEqual(value["rssPeakSampledBytes"],{"min":2001,"max":2005})
        self.assertEqual(value["afterIdleRssBytes"],{"min":1501,"max":1505,"first":1501,"last":1505})
        self.assertEqual(value["validationStatus"],"pass-bounded")
        self.assertEqual(value["qualityVerdict"],"unverified")
        self.assertIn("true peak memory",value["unverified"])
        self.assertIn("current whole-App source identity",value["unverified"])

    def test_rejects_noninteger_top_fields(self):
        for key in ["schemaVersion","pid","microphoneStarts","warmupCount","measurementCount","samplingIntervalMs","maxOutputTokens","closeDurationNs","beforeRssBytes","afterCloseRssBytes","afterCloseWaitMs","afterCloseWaitRssBytes"]:
            for replacement in [True,False,float(fixture()[key]),None]:
                with self.subTest(key=key,value=replacement):
                    report=fixture();report[key]=replacement
                    with self.assertRaises(ValueError):module.summarize(report)

    def test_rejects_noninteger_row_fields(self):
        for key in ["outputLength","index","durationNs","startedElapsedNs","endedElapsedNs","rssPeakSampledBytes","afterIdleRssBytes"]:
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
        for key,value in [("mode","recorder-pass"),("terminal",False),("status","running"),("status","failed"),("backendId","other"),("inputSHA256","0"*64),("finalCloseSucceeded",False),("callerInputUnchanged",False),("microphoneStarts",1),("cacheFilesRemoved",False),("modelId","other"),("modelRevision","other"),("runtimeRevision","other"),("modelSHA256","0"*64),("backendPolicy","CPU_ONLY"),("promptSHA256","bad"),("promptSHA256","0"*64),("runId","bad/path")]:
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
        for change in [lambda r:r["samples"].pop(),lambda r:r["samples"][0].update(expectedMatched=False),lambda r:r["samples"][0].update(phase="measurement"),lambda r:r["samples"][5].update(index=4),lambda r:r["samples"][5].update(endedElapsedNs=r["samples"][5]["endedElapsedNs"]+1)]:
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

    def test_rejects_row_identity_output_and_cache_mismatch(self):
        for key,value in [("modelId","other"),("modelRevision","other"),("runtimeRevision","other"),("outputSHA256","bad"),("outputLength",0),("cacheFilesRemoved",False),("expectedMatched",1),("callerInputUnchanged",1)]:
            with self.subTest(key=key,value=value):
                report=fixture();report["samples"][2][key]=value
                with self.assertRaises(ValueError):module.summarize(report)

    def test_does_not_mutate_raw_report(self):
        report=fixture();before=copy.deepcopy(report);module.summarize(report);self.assertEqual(report,before)

if __name__=="__main__":unittest.main()
