package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.AtomicFile
import io.ondeviceai.litertlm.*
import io.ondeviceai.modelstore.*
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Operator-only fixed-input characterization; existing model, synthetic image, no microphone or camera. */
class LiteRtLmResourceInstrumentation : Instrumentation() {
    private lateinit var reportFile: AtomicFile
    private lateinit var report: JSONObject
    private val samples = JSONArray()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val runId = requireNotNull(arguments?.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        val directory = File(targetContext.filesDir, "odai-litertlm-resource-reports")
        check(directory.isDirectory || directory.mkdir())
        val file = File(directory, "$runId.json")
        check(!file.exists() && !File(file.path + ".new").exists() &&
            !File(file.path + ".bak").exists())
        check(file.createNewFile()) { "Run ID already reserved" }
        reportFile = AtomicFile(file)
        report = JSONObject().put("schemaVersion", 1).put("kind", "android-litertlm-resource")
            .put("runId", runId).put("mode", "inference")
            .put("status", "running").put("terminal", false)
            .put("backendId", "litertlm.android").put("pid", android.os.Process.myPid())
            .put("deviceModel", android.os.Build.MODEL).put("sdkInt", android.os.Build.VERSION.SDK_INT)
            .put("inputSHA256", INPUT_SHA256).put("microphoneStarts", 0)
            .put("modelId", MODEL_ID).put("modelRevision", REVISION)
            .put("runtimeRevision", RUNTIME).put("modelSHA256", MODEL_SHA256)
            .put("backendPolicy", "CPU_TEXT_GPU_VISION").put("maxOutputTokens", 32)
            .put("promptSHA256", digest(PROMPT.toByteArray(Charsets.UTF_8)))
            .put("cacheFilesRemoved", false)
            .put("warmupCount", 1).put("measurementCount", 5).put("samplingIntervalMs", 20)
            .put("measurementMethod", "proc-self-status-VmRSS-sampled-process")
            .put("timingMethod", "sdk-generate-wall-including-verify-snapshot-native-create-send-release")
            .put("samples", samples).put("finalCloseSucceeded", false)
            .put("callerInputUnchanged", false)
        persist()
        start()
    }

    override fun onStart() {
        var backend: LiteRtLmBackend? = null
        val directory = File(reportFile.baseFile.parentFile!!, "work-${report.getString("runId")}")
        var ownsWork = false
        val image = File(directory, "input.png")
        val cache = File(directory, "cache")
        try {
            check(directory.mkdir())
            ownsWork = true
            check(!image.exists() && !cache.exists())
            check(image.createNewFile())
            context.assets.open("solid-red-256.png").use { input ->
                image.outputStream().use { output -> input.copyTo(output) }
            }
            check(digest(image.readBytes()) == INPUT_SHA256)
            startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Existing store only: host must prove its staging is empty before starting.
            val root = File(targetContext.noBackupFilesDir, "litertlm/store")
            check(File(root, "staging").listFiles()?.isEmpty() == true)
            val manifest = ModelManifest.parse(File(root,
                "installed/$MODEL_ID/$REVISION/.odai-manifest.json").readText())
            check(manifest.ref.modelId == MODEL_ID && manifest.ref.revision == REVISION)
            check(manifest.files.single { it.path == "model.litertlm" }.sha256 == MODEL_SHA256)
            val architecture = android.os.Build.SUPPORTED_ABIS.first { it == "arm64-v8a" || it == "x86_64" }
            val store = ModelStore(root, ModelCompatibility("litertlm.android", "0.18.1",
                android.os.Build.VERSION.SDK_INT.toString(), architecture))
            val model = LiteRtLmModel(manifest.ref, "model.litertlm", setOf("model-card.md"),
                LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION)
            val recognizer = LiteRtLm.createBackend(store, model, cache)
            backend = recognizer
            report.put("beforeRssBytes", rssBytes())
            runBlocking {
                repeat(6) { index ->
                    val readings = ArrayList<JSONObject>()
                    readings.add(reading())
                    val running = AtomicBoolean(true)
                    val samplerError = AtomicReference<Throwable?>(null)
                    val sampler = thread(name = "odai-litertlm-rss") {
                        try {
                            while (running.get()) {
                                readings.add(reading())
                                SystemClock.sleep(20)
                            }
                        } catch (error: Throwable) { samplerError.set(error) }
                    }
                    val started = SystemClock.elapsedRealtimeNanos()
                    var ended = 0L
                    val result = try { recognizer.generate(MultimodalRequest(image, PROMPT, 32), null) }
                    finally {
                        ended = SystemClock.elapsedRealtimeNanos()
                        running.set(false)
                        sampler.join()
                    }
                    samplerError.get()?.let { throw it }
                    readings.add(reading())
                    check(recognizer.backendId == "litertlm.android")
                    check(result.model.modelId == MODEL_ID && result.model.modelRevision == REVISION &&
                        result.model.runtimeRevision == RUNTIME)
                    val matched = Regex("\\bred\\b", RegexOption.IGNORE_CASE).containsMatchIn(result.text)
                    val unchanged = digest(image.readBytes()) == INPUT_SHA256
                    val cacheEmpty = !cache.exists() || cache.walkTopDown().none { it.isFile }
                    val row = JSONObject().put("index", index)
                        .put("phase", if (index < 1) "warmup" else "measurement")
                        .put("backendId", recognizer.backendId).put("expectedMatched", matched)
                        .put("callerInputUnchanged", unchanged).put("cacheFilesRemoved", cacheEmpty)
                        .put("modelId", result.model.modelId).put("modelRevision", result.model.modelRevision)
                        .put("runtimeRevision", result.model.runtimeRevision)
                        .put("outputSHA256", digest(result.text.toByteArray(Charsets.UTF_8)))
                        .put("outputLength", result.text.length).put("startedElapsedNs", started)
                        .put("endedElapsedNs", ended).put("durationNs", ended - started)
                        .put("rss", JSONArray(readings))
                        .put("rssPeakSampledBytes", readings.maxOf { it.getLong("bytes") })
                    SystemClock.sleep(200)
                    row.put("afterIdleRssBytes", rssBytes())
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        row.put("thermalStatus", targetContext.getSystemService(PowerManager::class.java)
                            .currentThermalStatus)
                    }
                    samples.put(row)
                    persist()
                    check(matched && unchanged && cacheEmpty) { "Fixed-input acceptance failed" }
                }
                val started = SystemClock.elapsedRealtimeNanos()
                recognizer.close()
                report.put("closeDurationNs", SystemClock.elapsedRealtimeNanos() - started)
                report.put("finalCloseSucceeded", true)
            }
            backend = null
            report.put("callerInputUnchanged", digest(image.readBytes()) == INPUT_SHA256)
                .put("cacheFilesRemoved", !cache.exists() || cache.walkTopDown().none { it.isFile })
                .put("afterCloseRssBytes", rssBytes()).put("afterCloseWaitMs", 1000)
            SystemClock.sleep(1000)
            report.put("afterCloseWaitRssBytes", rssBytes()).put("status", "completed")
        } catch (error: Throwable) {
            report.put("status", "failed").put("errorClass", error.javaClass.name)
        } finally {
            try { runBlocking { backend?.close() } }
            catch (error: Throwable) {
                report.put("status", "failed").put("cleanupErrorClass", error.javaClass.name)
            }
            try {
                if (ownsWork && image.exists()) {
                    check(digest(image.readBytes()) == INPUT_SHA256)
                    check(image.delete())
                }
                if (ownsWork && cache.exists()) {
                    check(cache.walkTopDown().none { it.isFile })
                    cache.walkBottomUp().forEach { entry ->
                        check(entry.isDirectory && entry.delete())
                    }
                }
                if (ownsWork) check(directory.delete())
            } catch (error: Throwable) {
                report.put("status", "failed").put("fixtureCleanupErrorClass", error.javaClass.name)
            }
            report.put("terminal", true)
            persist()
        }
        finish(if (report.getString("status") == "completed") -1 else 1,
            Bundle().apply { putString("status", report.getString("status")) })
    }

    private fun rssBytes(): Long {
        val fields = File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }.trim().split(Regex("\\s+"))
        }
        check(fields.size == 3 && fields[2] == "kB")
        return Math.multiplyExact(fields[1].toLong(), 1024L).also { check(it > 0) }
    }

    private fun reading(): JSONObject {
        val value = rssBytes()
        return JSONObject().put("elapsedNs", SystemClock.elapsedRealtimeNanos()).put("bytes", value)
    }

    private fun persist() {
        val stream = reportFile.startWrite()
        try {
            stream.write((report.toString() + "\n").toByteArray(Charsets.UTF_8))
            reportFile.finishWrite(stream)
        } catch (error: Throwable) {
            reportFile.failWrite(stream)
            throw error
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val INPUT_SHA256 = "991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa"
        const val MODEL_SHA256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
        const val MODEL_ID = "gemma-3n-e2b-it"
        const val REVISION = "c03b6f60b8da6c5400b6838a2cf26420f80c0a01"
        const val RUNTIME = "litertlm-android:0.18.1"
        const val PROMPT = "What is the color of this image? Respond with a single color word."
    }
}
