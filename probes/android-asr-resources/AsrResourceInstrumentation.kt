package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.AtomicFile
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import io.ondeviceai.sherpaasr.SherpaWhisperModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Operator-only fixed-input characterization; no microphone, media or transcript persistence. */
class AsrResourceInstrumentation : Instrumentation() {
    private lateinit var reportFile: AtomicFile
    private lateinit var report: JSONObject
    private val samples = JSONArray()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val runId = requireNotNull(arguments?.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        val directory = File(targetContext.filesDir, "odai-asr-resource-reports")
        check(directory.isDirectory || directory.mkdir())
        val file = File(directory, "$runId.json")
        check(!file.exists() && !File(file.path + ".new").exists() &&
            !File(file.path + ".bak").exists())
        check(file.createNewFile()) { "Run ID already reserved" }
        reportFile = AtomicFile(file)
        report = JSONObject().put("schemaVersion", 1).put("kind", "android-asr-resource")
            .put("runId", runId).put("mode", "inference")
            .put("status", "running").put("terminal", false)
            .put("backendId", "sherpa.onnx.whisper").put("pid", android.os.Process.myPid())
            .put("deviceModel", android.os.Build.MODEL).put("sdkInt", android.os.Build.VERSION.SDK_INT)
            .put("inputSHA256", INPUT_SHA256).put("microphoneStarts", 0)
            .put("audioPersisted", false).put("transcriptPersisted", false)
            .put("warmupCount", 5).put("measurementCount", 30).put("samplingIntervalMs", 20)
            .put("measurementMethod", "proc-self-status-VmRSS-sampled-process")
            .put("timingMethod", "sdk-transcribe-wall-including-verify-vad-native-create-decode-release")
            .put("samples", samples).put("finalCloseSucceeded", false)
            .put("callerInputUnchanged", false)
        persist()
        start()
    }

    override fun onStart() {
        var backend: SherpaAsrBackend? = null
        var bytes: ByteArray? = null
        var waveform: FloatArray? = null
        var original: FloatArray? = null
        try {
            startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            bytes = context.assets.open("english.pcm").use { it.readBytes() }
            check(MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) } == INPUT_SHA256)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val input = FloatArray(buffer.remaining()) { buffer.get() / 32768f }
            waveform = input
            val retained = input.copyOf()
            original = retained
            // Verify existing assets only. Do not install or repair a missing model.
            val model = SherpaWhisperModel(File(targetContext.noBackupFilesDir,
                "sherpa-whisper-tiny-65176e2d-vad9e2449e1"))
            model.verify()
            val recognizer = SherpaAsrBackend(targetContext, model)
            backend = recognizer
            report.put("beforeRssBytes", rssBytes())
            runBlocking {
                repeat(35) { index ->
                    val readings = ArrayList<JSONObject>()
                    readings.add(reading())
                    val running = AtomicBoolean(true)
                    val samplerError = AtomicReference<Throwable?>(null)
                    val sampler = thread(name = "odai-asr-rss") {
                        try {
                            while (running.get()) {
                                readings.add(reading())
                                SystemClock.sleep(20)
                            }
                        } catch (error: Throwable) { samplerError.set(error) }
                    }
                    val started = SystemClock.elapsedRealtimeNanos()
                    var ended = 0L
                    val result = try { recognizer.transcribe(input, "en-US") }
                    finally {
                        ended = SystemClock.elapsedRealtimeNanos()
                        running.set(false)
                        sampler.join()
                    }
                    samplerError.get()?.let { throw it }
                    readings.add(reading())
                    check(result.backendId == "sherpa.onnx.whisper")
                    val matched = result.transcript.lowercase().filter { it.isLetterOrDigit() } ==
                        "thisisanondevicespeechtest"
                    val unchanged = input.contentEquals(retained)
                    val row = JSONObject().put("index", index)
                        .put("phase", if (index < 5) "warmup" else "measurement")
                        .put("backendId", result.backendId).put("expectedMatched", matched)
                        .put("callerInputUnchanged", unchanged).put("startedElapsedNs", started)
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
                    check(matched && unchanged) { "Fixed-input acceptance failed" }
                }
                val started = SystemClock.elapsedRealtimeNanos()
                recognizer.close()
                report.put("closeDurationNs", SystemClock.elapsedRealtimeNanos() - started)
                report.put("finalCloseSucceeded", true)
            }
            backend = null
            report.put("callerInputUnchanged", input.contentEquals(retained))
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
            bytes?.fill(0)
            waveform?.fill(0f)
            original?.fill(0f)
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

    private companion object {
        const val INPUT_SHA256 = "fc0f3f5bb9b06a4c21e12321d5fb0bc7d3e3151dca855e5ed9693907ce39df46"
    }
}
