package io.ondeviceai.acceptance.qwenfile

import io.ondeviceai.acceptance.qwenload.QwenModelFiles

private var assertions=0
private fun expect(ok:Boolean){check(ok);assertions++}

fun main(){
 val input=InputCase("sample-01","zh-CN","mandarin",16000,"a".repeat(64),"\"原文。\"\n","inputs/sample-01.pcm")
 var state=FileRunState(runId="check01",sourceFingerprint="b".repeat(64),pid=123,
     rows=listOf(FileRow(input)),modelsVerified=QwenModelFiles.frozenSpecs)
 val initial=QwenFileReport.snapshot(state)
 expect(initial.getInt("schemaVersion")==1 && !initial.getBoolean("terminal") && initial.getString("status")=="running")
 expect(initial.getString("inputManifestSHA256")==QwenFileInputs.MANIFEST_SHA256)
 expect(initial.getInt("nativeDecodeCalls")==0 && initial.getInt("streamsCreated")==0)
 val row=initial.getJSONArray("rows").getJSONObject(0)
 expect(row.isNull("caseState") && row.isNull("caseNanos") && row.getString("reference")==input.reference)
 expect(row.getString("pcmSha256")==input.sha256 && row.getString("status")=="planned")
 val config=initial.getJSONObject("config")
 expect(config.getInt("featureSampleRate")==16000 && config.getInt("featureDim")==128 && config.getInt("numThreads")==2)
 expect(config.getString("provider")=="cpu" && config.getString("decodingMethod")=="greedy_search")
 expect(config.getInt("maxTotalLen")==512 && config.getInt("maxNewTokens")==128 && config.getInt("seed")==42)
 expect(config.getDouble("temperature")==1e-6 && config.getDouble("topP")==.8 && !config.getBoolean("debug"))
 expect(config.getString("languageMode")=="admit-auto-then-forced")
 expect(initial.getJSONArray("modelsVerified").length()==6 && initial.getInt("microphoneStarts")==0 && initial.getInt("networkRequests")==0)
 val stage=StageState("",status="error",createAttempts=1,createReturned=true,optionAttempts=1,acceptAttempts=1,decodeAttempts=1,
     resultAttempts=0,releaseAttempts=1,releaseSucceeded=false,errorClass="Primary",releaseErrorClass="Cleanup")
 val core=CaseState(status="error",phase="auto",stages=listOf(stage),errorClass="Primary",errorMessage="original",persistErrorClass="Disk")
 state=state.copy(rows=listOf(FileRow(input,status="error",caseState=core,caseNanos=1234567890123L)),
     activeCaseIndex=0,caseStartedElapsedRealtimeNanos=9007199254740993L,actualConstructors=1,constructorReturned=true,
     recognizerReleaseAttempts=1,recognizerReleaseSucceeded=false,recognizerReleaseErrorClass="Release",errorClass="Primary",errorMessage="original",
     wrapperPersistErrorClass="Disk",teardownErrorClass="Destroy",resumed=true,paused=true,destroyed=true,teardownAck=false)
 val failed=QwenFileReport.snapshot(state)
 expect(failed.getInt("nativeDecodeCalls")==1 && failed.getInt("streamsCreated")==1)
 expect(failed.getLong("caseStartedElapsedRealtimeNanos")==9007199254740993L)
 expect(failed.getInt("actualConstructors")==1 && failed.getBoolean("constructorReturned"))
 expect(failed.getInt("recognizerReleaseAttempts")==1 && !failed.getBoolean("recognizerReleaseSucceeded"))
 expect(failed.getString("recognizerReleaseErrorClass")=="Release" && failed.getString("wrapperPersistErrorClass")=="Disk")
 expect(failed.getString("errorMessage")=="original" && failed.getString("teardownErrorClass")=="Destroy")
 val fg=failed.getJSONObject("foreground");expect(fg.getBoolean("resumed") && fg.getBoolean("paused") && fg.getBoolean("destroyed") && !fg.getBoolean("teardownAck"))
 val result=failed.getJSONArray("rows").getJSONObject(0).getJSONObject("caseState")
 expect(result.isNull("rawText") && result.isNull("admitted") && result.getString("persistErrorClass")=="Disk")
 val serialized=result.getJSONArray("stages").getJSONObject(0)
 expect(serialized.getInt("decodeAttempts")==1 && serialized.isNull("rawText") && serialized.isNull("decodeNanos"))
 expect(serialized.getString("errorClass")=="Primary" && serialized.getString("releaseErrorClass")=="Cleanup")
 expect(initial.getInt("nativeDecodeCalls")==0 && initial.getJSONArray("rows").getJSONObject(0).isNull("caseState"))
 println("ASSERTIONS=$assertions FAILURES=0")
}
