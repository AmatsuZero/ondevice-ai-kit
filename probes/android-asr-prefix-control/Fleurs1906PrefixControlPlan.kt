package io.ondeviceai.demo

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Isolated fixed-input diagnostic. No production VAD, model selection or scorer changes. */
object Fleurs1906PrefixControlPlan {
    const val PUBLIC_REFERENCE = "“这并不是告别。这是一个篇章的结束，也是新篇章的开始。”"
    const val REVISION = "70bb2e84b976b7e960aa89f1c648e09c59f894dd"
    const val CLIP_SHA256 = "8c8996345c5ffbf90f802ed67d9d3a8257ef12a0768453492ce351e03754912a"
    const val SLICE_SHA256 = "374dbab2ab6f565e9c15aa1a969252d7583ea68eea176d037aa86065ba865ef6"
    const val BASELINE_SHA256 = "3488fa8276e5fd655b23ebcc99e8d4c5fe49808580d90d9ccdf80731a8d7c5a3"
    const val PREFIX_SHA256 = "8e408ea77635aadf6e77dae482d3e3149a78461de587010e18aefab0547ca7c8"
    const val MODEL_DIR = "sherpa-whisper-tiny-65176e2d-vad9e2449e1"
    val MODEL_FILES = linkedMapOf(
        "silero_vad.onnx" to (643854L to "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"),
        "tiny-encoder.int8.onnx" to (12937772L to "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
        "tiny-decoder.int8.onnx" to (89855401L to "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
        "tiny-tokens.txt" to (816730L to "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
    )

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun prepare(clip: ByteArray): List<ByteArray> {
        require(clip.size == 332160 && sha256(clip) == CLIP_SHA256) { "whole PCM identity mismatch" }
        require(sha256(PUBLIC_REFERENCE.toByteArray(Charsets.UTF_8)) ==
            "6f20dd84a6c686c60507428e037063762c5a15bb7e9e9777c1047049b62a9152")
        val slice = clip.copyOfRange(83040 * 2, 166080 * 2)
        try {
            require(sha256(slice) == SLICE_SHA256) { "slice identity mismatch" }
            val baseline = slice.copyOfRange(2432 * 2, 36480 * 2)
            val prefix = slice.copyOfRange(0, 36480 * 2)
            require(sha256(baseline) == BASELINE_SHA256 && sha256(prefix) == PREFIX_SHA256)
            require(prefix.copyOfRange(2432 * 2, prefix.size).contentEquals(baseline))
            return listOf(baseline, prefix)
        } finally { slice.fill(0) }
    }

    /** Stream actual disk bytes before ANY native construction. Observed != expected metadata. */
    fun verifyModels(directory: File, observations: MutableList<Map<String, Any?>>,
                     expected: Map<String, Pair<Long, String>> = MODEL_FILES) {
        for ((name, pin) in expected) {
            val file = File(directory, name)
            require(file.isFile) { "missing model file: $name" }
            val hash = MessageDigest.getInstance("SHA-256")
            var size = 0L
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                    size += count
                }
            }
            val actual = hex(hash.digest())
            val matched = size == pin.first && actual == pin.second
            observations += linkedMapOf("name" to name, "expectedBytes" to pin.first,
                "expectedSha256" to pin.second, "observedBytes" to size,
                "observedSha256" to actual, "matched" to matched)
            require(matched) { "model byte identity mismatch: $name" }
        }
    }

    fun floats(bytes: ByteArray): FloatArray {
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get() / 32768f }
    }
    fun floatHash(values: FloatArray): String {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return sha256(buffer.array())
    }

    /** The actual device orchestration is also used by host failure-path acceptance. */
    fun runArms(crops: List<ByteArray>, create: () -> PrefixRecognizer,
                checkpoint: (PrefixResult) -> Unit = {}): PrefixResult {
        val result = PrefixResult()
        var recognizer: PrefixRecognizer? = null
        try {
            require(crops.size == 2 && crops.map { sha256(it) } == listOf(BASELINE_SHA256, PREFIX_SHA256))
            recognizer = create()
            recognizer.configure()
            for (index in crops.indices) {
                val arm = PrefixArm(index, sha256(crops[index]), crops[index].size / 2)
                result.arms += arm
                val samples = floats(crops[index])
                arm.float32LeSha256 = floatHash(samples)
                var stream: PrefixStream? = null
                try {
                    stream = recognizer.createStream()
                    arm.decodeAttempted = true
                    arm.startNs = System.nanoTime()
                    arm.hypothesis = stream.decode(samples)
                    arm.endNs = System.nanoTime()
                    arm.samplesUnchanged = floatHash(samples) == arm.float32LeSha256
                    check(arm.samplesUnchanged) { "native modified caller samples" }
                } catch (error: Throwable) {
                    result.errors += failure("arm$index", error)
                } finally {
                    if (stream != null) {
                        arm.streamReleaseAttempted = true
                        try { stream.release(); arm.streamReleased = true }
                        catch (error: Throwable) { result.errors += failure("stream$index.release", error) }
                    }
                    samples.fill(0f)
                    arm.samplesCleared = samples.all { it == 0f }
                }
                checkpoint(result)
                if (result.errors.isNotEmpty()) break
            }
        } catch (error: Throwable) {
            result.errors += failure("orchestration", error)
        } finally {
            if (recognizer != null) {
                result.recognizerReleaseAttempted = true
                try { recognizer.release(); result.recognizerReleased = true }
                catch (error: Throwable) { result.errors += failure("recognizer.release", error) }
            }
        }
        result.completed = result.errors.isEmpty() && result.recognizerReleased &&
            result.arms.size == 2 && result.arms.all {
                it.hypothesis != null && it.decodeAttempted && it.streamReleased && it.samplesCleared && it.samplesUnchanged
            }
        return result
    }

    fun failure(stage: String, error: Throwable): Map<String, Any?> =
        linkedMapOf("stage" to stage, "class" to error.javaClass.name, "message" to error.message)

    /** Small strict encoder shared by the real reports and host tests; no trailing commas. */
    fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (c in value) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> if (c.code < 32 || c.isSurrogate()) append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
        is Boolean, is Int, is Long -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") {
            require(it.key is String); json(it.key) + ":" + json(it.value)
        }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> error("unsupported JSON type ${value.javaClass.name}")
    }
}

interface PrefixRecognizer {
    fun configure()
    fun createStream(): PrefixStream
    fun release()
}
interface PrefixStream {
    fun decode(samples: FloatArray): String
    fun release()
}
class PrefixArm(val index: Int, val pcmSha256: String, val samples: Int) {
    var hypothesis: String? = null
    var float32LeSha256 = ""
    var decodeAttempted = false
    var streamReleaseAttempted = false
    var streamReleased = false
    var samplesCleared = false
    var samplesUnchanged = false
    var startNs = 0L
    var endNs = 0L
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "index" to index, "rangeInSlice" to listOf(if (index == 0) 2432 else 0, 36480),
        "pcmSha256" to pcmSha256, "samples" to samples, "float32LeSha256" to float32LeSha256,
        "hypothesis" to hypothesis, "decodeAttempted" to decodeAttempted,
        "streamReleaseAttempted" to streamReleaseAttempted, "streamReleased" to streamReleased,
        "samplesCleared" to samplesCleared, "samplesUnchanged" to samplesUnchanged,
        "startNs" to startNs, "endNs" to endNs,
    )
}
class PrefixResult {
    val arms = mutableListOf<PrefixArm>()
    val errors = mutableListOf<Map<String, Any?>>()
    var recognizerReleaseAttempted = false
    var recognizerReleased = false
    var completed = false
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "completed" to completed, "arms" to arms.map { it.toMap() }, "errors" to errors,
        "recognizerReleaseAttempted" to recognizerReleaseAttempted,
        "recognizerReleased" to recognizerReleased, "qualityVerdict" to "unverified",
    )
}
