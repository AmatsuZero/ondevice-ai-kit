package io.ondeviceai.demo

import java.io.File
import java.nio.file.Files

/** Host-only acceptance of the actual probe logic. No Android/JNI/model inference. */
fun main(args: Array<String>) {
    val out = File(args[0])
    val pcm = File(args[1]).readBytes()
    val models = File(args[2])
    var checks = 0
    fun verify(value: Boolean, label: String) { check(value) { label }; checks++ }
    fun rejects(label: String, action: () -> Unit) {
        var rejected = false
        try { action() } catch (_: IllegalArgumentException) { rejected = true }
        verify(rejected, label)
    }
    val p = Fleurs1906PrefixControlPlan
    verify(p.sha256(p.PUBLIC_REFERENCE.toByteArray()) == "6f20dd84a6c686c60507428e037063762c5a15bb7e9e9777c1047049b62a9152", "reference")
    val crops = p.prepare(pcm)
    verify(crops.map { it.size } == listOf(68096, 72960), "crop lengths")
    verify(crops.map { p.sha256(it) } == listOf(p.BASELINE_SHA256, p.PREFIX_SHA256), "crop hashes")
    verify(crops[1].copyOfRange(4864, crops[1].size).contentEquals(crops[0]), "suffix")
    rejects("corrupt PCM") { p.prepare(pcm.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }) }
    rejects("short PCM") { p.prepare(pcm.copyOf(pcm.size - 2)) }
    val observed = mutableListOf<Map<String, Any?>>()
    p.verifyModels(models, observed)
    verify(observed.size == 4 && observed.all { it["matched"] == true }, "real model files")
    val temporary = Files.createTempDirectory("w3-prefix-model-negative-").toFile()
    try {
        // Real on-disk bytes, not digest metadata pretending to be a model check.
        val tiny = byteArrayOf(0, 1, 2, 3)
        File(temporary, "fixture.bin").writeBytes(tiny)
        val expected = mapOf("fixture.bin" to (4L to p.sha256(tiny)))
        p.verifyModels(temporary, mutableListOf(), expected)
        checks++
        File(temporary, "fixture.bin").writeBytes(byteArrayOf(0, 1, 2))
        rejects("disk size mismatch") { p.verifyModels(temporary, mutableListOf(), expected) }
        File(temporary, "fixture.bin").writeBytes(byteArrayOf(0, 1, 2, 4))
        val mismatch = mutableListOf<Map<String, Any?>>()
        rejects("disk hash mismatch") { p.verifyModels(temporary, mismatch, expected) }
        verify(mismatch.single()["matched"] == false && mismatch.single()["observedSha256"] != p.sha256(tiny), "observed mismatch retained")
        check(File(temporary, "fixture.bin").delete())
        rejects("missing disk file") { p.verifyModels(temporary, mutableListOf(), expected) }
        val tokenName = "tiny-tokens.txt"
        val tokenFile = File(temporary, tokenName)
        val tokenBytes = File(models, tokenName).readBytes()
        val tokenPin = mapOf(tokenName to p.MODEL_FILES.getValue(tokenName))
        tokenFile.writeBytes(tokenBytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        rejects("actual pinned token hash mismatch") { p.verifyModels(temporary, mutableListOf(), tokenPin) }
        tokenFile.writeBytes(tokenBytes.copyOf(tokenBytes.size - 1))
        rejects("actual pinned token size mismatch") { p.verifyModels(temporary, mutableListOf(), tokenPin) }
        check(tokenFile.delete())
    } finally {
        check(temporary.listFiles()!!.isEmpty())
        check(temporary.delete())
    }

    class Fake(private val failure: String) : PrefixRecognizer {
        val calls = mutableListOf<String>()
        var arm = -1
        override fun configure() { calls += "configure"; if (failure == "configure") error("configure failure") }
        override fun createStream(): PrefixStream {
            arm++
            calls += "create$arm"
            if (failure == "create$arm") error("create failure")
            val index = arm
            return object : PrefixStream {
                override fun decode(samples: FloatArray): String {
                    calls += "decode$index"
                    check(samples.size == crops[index].size / 2)
                    if (failure == "decode$index") error("arbitrary \"error\"\n\\\t\u0000")
                    return "arm-$index"
                }
                override fun release() {
                    calls += "streamRelease$index"
                    if (failure == "streamRelease$index") error("stream release failed")
                }
            }
        }
        override fun release() { calls += "recognizerRelease"; if (failure == "recognizerRelease") error("recognizer release failed") }
    }
    for (failure in listOf("", "configure", "create0", "create1", "decode0", "decode1", "streamRelease0", "streamRelease1", "recognizerRelease")) {
        val fake = Fake(failure)
        val snapshots = mutableListOf<String>()
        val result = p.runArms(crops, { fake }) { snapshots += p.json(it.toMap()) }
        verify(result.completed == failure.isEmpty(), "completion: $failure")
        verify(fake.calls.last() == "recognizerRelease", "recognizer release: $failure")
        verify(result.recognizerReleased == (failure != "recognizerRelease"), "release flag after completion: $failure")
        if (failure in listOf("create1", "decode1", "streamRelease1", "recognizerRelease")) {
            verify(result.arms[0].hypothesis == "arm-0", "prior observation survives: $failure")
        }
        if (failure.startsWith("streamRelease")) {
            val index = failure.last().digitToInt()
            verify(result.arms[index].hypothesis == "arm-$index", "hypothesis before cleanup")
            verify(!result.arms[index].streamReleased, "failed release must remain false")
            if (index == 0) verify(result.arms.size == 1, "stop after release failure")
        }
        verify(result.arms.all { it.samplesCleared }, "sample cleanup: $failure")
        File(out, "offline-${if (failure.isEmpty()) "success" else failure}.json").writeText(p.json(result.toMap()))
    }
    val createFailure = p.runArms(crops, { error("constructor failure") })
    verify(!createFailure.completed && !createFailure.recognizerReleaseAttempted, "constructor failure")
    val checkpointFailure = p.runArms(crops, { Fake("") }) { error("checkpoint failure") }
    verify(!checkpointFailure.completed && checkpointFailure.recognizerReleased && checkpointFailure.arms.size == 1, "checkpoint failure stops and cleans")
    File(out, "offline-escaped.json").writeText(p.json(mapOf("error" to "quote\" slash\\\n\r\t\b\u000c\u0000中文")))
    verify(crops.map { p.sha256(it) } == listOf(p.BASELINE_SHA256, p.PREFIX_SHA256), "caller input preserved")
    println("PASS $checks host acceptance assertions; JNI calls=0")
}
