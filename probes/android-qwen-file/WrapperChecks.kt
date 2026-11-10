package checks

import io.ondeviceai.acceptance.qwenfile.QwenFileInstrumentation
import android.os.Bundle
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private var assertions = 0
private val failures = mutableListOf<String>()
private fun expect(ok: Boolean, msg: String = "assertion") { check(ok) { msg }; assertions++ }
private fun case(name: String, body: () -> Unit) {
    try { body(); println("PASS $name") } catch (t: Throwable) { failures += "$name: $t" }
}
private fun reset() {
    Runtime.root = Files.createTempDirectory(File("/private/tmp").toPath(), "odai-qwen-file-wrapper-").toFile()
    Runtime.writes=0; Runtime.failWriteAt=0; Runtime.creates=0; Runtime.releases=0; Runtime.verifies=0
    Runtime.locked=false; Runtime.verifyFail=false; Runtime.constructorFail=false; Runtime.releaseFail=false
    Runtime.teardownAck=true; Runtime.lockAfterVerify=false; Runtime.code=null
    Runtime.finished=java.util.concurrent.CountDownLatch(1)
    Runtime.streams=0; Runtime.streamReleases=0; Runtime.decodes=0; Runtime.streamReleaseFail=false
    Runtime.decodeFail=false; Runtime.emptyAuto=false; Runtime.badAsset=null; Runtime.failWhen=null
    Runtime.streamEvents.clear(); Runtime.accepted.clear()
    File(Runtime.root,"no_backup/qwen3-asr-0.6b-int8-393f8a14/tokenizer").mkdirs()
}
private fun report()=File(Runtime.root,"files/w3-qwen-file/report-check01.json")
private fun start(id: String="check01") {
    val b=Bundle(); b.putString("runId",id); b.putString("sourceFingerprint","a".repeat(64))
    QwenFileInstrumentation().onCreate(b)
    expect(Runtime.finished.await(7,TimeUnit.SECONDS),"instrumentation did not finish")
}
private fun json()=JSONObject(report().readText())
private fun failed() { expect(Runtime.code==0 && json().getString("status")=="failed",json().toString()) }
private fun allZero() { expect(Runtime.accepted.all { a -> a.all { it==0f } },"PCM not cleared after case") }

fun main(args: Array<String>) {
    Runtime.assetsRoot=File(args.single())
    case("complete65 cases with real assets unchanged PCM and fixed runtime config") {
        reset(); try {
            start(); val j=json()
            expect(j.getString("status")=="completed" && j.getBoolean("terminal"),j.toString())
            expect(Runtime.creates==1 && Runtime.releases==1 && Runtime.verifies==1)
            expect(Runtime.streams==130 && Runtime.streamReleases==130 && Runtime.decodes==130)
            expect(j.getInt("nativeDecodeCalls")==130 && j.getInt("streamsCreated")==130)
            expect(j.getInt("actualConstructors")==1 && j.getBoolean("constructorReturned"))
            expect(j.getInt("recognizerReleaseAttempts")==1 && j.getBoolean("recognizerReleaseSucceeded"))
            expect(j.getJSONObject("foreground").getBoolean("teardownAck"))
            expect(j.getInt("microphoneStarts")==0 && j.getInt("networkRequests")==0)
            expect(j.getJSONArray("rows").length()==65)
            for(i in 0 until 65) {
                val row=j.getJSONArray("rows").getJSONObject(i)
                expect(row.getString("status")=="completed")
                expect(row.getJSONObject("caseState").getString("rawText")=="forced text")
                expect(row.getLong("caseNanos")>=0)
            }
            for(k in listOf("errorClass","recognizerReleaseErrorClass","wrapperPersistErrorClass","teardownErrorClass")) expect(j.isNull(k),k)
            allZero()
        } finally { Runtime.root.deleteRecursively() }
    }
    case("strict empty auto uses only one decode per case") {
        reset(); try {
            Runtime.emptyAuto=true; start(); val j=json()
            expect(j.getString("status")=="completed")
            expect(Runtime.streams==65 && Runtime.decodes==65 && Runtime.streamReleases==65)
            for(i in 0 until 65) {
                val c=j.getJSONArray("rows").getJSONObject(i).getJSONObject("caseState")
                expect(!c.getBoolean("admitted") && c.getString("rawText")=="")
            }
            allZero()
        } finally { Runtime.root.deleteRecursively() }
    }
    case("invalid argument has no write or native side effect") {
        reset(); try { start("../bad"); expect(Runtime.writes==0 && Runtime.creates==0 && !report().exists()) }
        finally { Runtime.root.deleteRecursively() }
    }
    for(variant in listOf("", ".new", ".bak")) case("reserved report variant $variant preserved") {
        reset(); try {
            report().parentFile.mkdirs(); val p=File(report().path+variant); p.writeText("preserve")
            start(); expect(p.readText()=="preserve" && Runtime.writes==0 && Runtime.creates==0)
        } finally { Runtime.root.deleteRecursively() }
    }
    case("report symlink cannot redirect writes") {
        reset(); try {
            report().parentFile.mkdirs(); val other=File(Runtime.root,"other").also{it.writeText("preserve")}
            Files.createSymbolicLink(report().toPath(),other.toPath()); start()
            expect(other.readText()=="preserve" && Runtime.writes==0 && Runtime.creates==0)
        } finally { Runtime.root.deleteRecursively() }
    }
    case("initial write failure blocks constructor") {
        reset(); try { Runtime.failWriteAt=1; start(); expect(Runtime.creates==0 && Runtime.code==0) }
        finally { Runtime.root.deleteRecursively() }
    }
    case("initial lock blocks validation and constructor") {
        reset(); try { Runtime.locked=true; start(); failed(); expect(Runtime.creates==0 && Runtime.verifies==0) }
        finally { Runtime.root.deleteRecursively() }
    }
    case("model corruption blocks constructor") {
        reset(); try { Runtime.verifyFail=true; start(); failed(); expect(Runtime.creates==0 && Runtime.verifies==1) }
        finally { Runtime.root.deleteRecursively() }
    }
    for(asset in listOf("cases.json","inputs/fleurs-cmn_hans_cn-1805.pcm")) case("asset corruption $asset blocks all native") {
        reset(); try { Runtime.badAsset=asset; start(); failed(); expect(Runtime.creates==0 && Runtime.streams==0) }
        finally { Runtime.root.deleteRecursively() }
    }
    case("lock after model validation blocks constructor") {
        reset(); try { Runtime.lockAfterVerify=true; start(); failed(); expect(Runtime.creates==0) }
        finally { Runtime.root.deleteRecursively() }
    }
    case("constructor failure counts attempt without release") {
        reset(); try {
            Runtime.constructorFail=true; start(); failed()
            expect(Runtime.creates==1 && Runtime.releases==0 && Runtime.streams==0)
            expect(json().getInt("actualConstructors")==1 && !json().getBoolean("constructorReturned"))
        } finally { Runtime.root.deleteRecursively() }
    }
    case("decode failure preserves actual count releases stream and recognizer stops remaining rows") {
        reset(); try {
            Runtime.decodeFail=true; start(); failed(); val j=json()
            expect(Runtime.decodes==1 && Runtime.streamReleases==1 && Runtime.releases==1 && Runtime.streams==1)
            expect(j.getInt("nativeDecodeCalls")==1)
            expect(j.getJSONArray("rows").getJSONObject(0).getString("status")=="error")
            expect(j.getJSONArray("rows").getJSONObject(1).getString("status")=="planned")
            allZero()
        } finally { Runtime.root.deleteRecursively() }
    }
    case("stream release failure stops further decoding but still releases recognizer") {
        reset(); try {
            Runtime.streamReleaseFail=true; start(); failed()
            expect(Runtime.streamReleases==1 && Runtime.decodes==1 && Runtime.streams==1 && Runtime.releases==1)
            expect(!json().getJSONArray("rows").getJSONObject(0).getJSONObject("caseState").getJSONArray("stages").getJSONObject(0).getBoolean("releaseSucceeded"))
            allZero()
        } finally { Runtime.root.deleteRecursively() }
    }
    case("persistence failure after constructor releases without decoding") {
        reset(); try {
            Runtime.failWhen={j -> j.optBoolean("constructorReturned") && j.optInt("nativeDecodeCalls")==0}
            start(); failed(); expect(Runtime.creates==1 && Runtime.releases==1 && Runtime.decodes==0)
            expect(!json().isNull("wrapperPersistErrorClass"))
        } finally { Runtime.root.deleteRecursively() }
    }
    case("persistence failure after decode preserves count and both cleanup levels") {
        reset(); try {
            Runtime.failWhen={j -> j.optInt("nativeDecodeCalls")>0}
            start(); expect(Runtime.code==0)
            expect(Runtime.decodes==1 && Runtime.streamReleases==1 && Runtime.releases==1)
            allZero()
        } finally { Runtime.root.deleteRecursively() }
    }
    case("recognizer release failure cannot complete") {
        reset(); try {
            Runtime.releaseFail=true; start(); failed()
            expect(Runtime.releases==1 && !json().getBoolean("recognizerReleaseSucceeded"))
            expect(!json().isNull("recognizerReleaseErrorClass")); allZero()
        } finally { Runtime.root.deleteRecursively() }
    }
    case("destroy acknowledgement missing prevents full completion") {
        reset(); try {
            Runtime.teardownAck=false; start(); failed()
            expect(Runtime.releases==1 && !json().getJSONObject("foreground").getBoolean("teardownAck"))
            expect(!json().isNull("teardownErrorClass"))
        } finally { Runtime.root.deleteRecursively() }
    }
    case("terminal persistence failure returns canceled") {
        reset(); try {
            Runtime.failWhen={j -> j.optBoolean("terminal")}; start()
            expect(Runtime.code==0 && Runtime.releases==1)
            expect(!json().getBoolean("terminal")); allZero()
        } finally { Runtime.root.deleteRecursively() }
    }
    println("ASSERTIONS=$assertions FAILURES=${failures.size}")
    failures.forEach{println("FAIL $it")}; check(failures.isEmpty())
}
