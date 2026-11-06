package io.ondeviceai.litertsr

import android.app.Instrumentation
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.AtomicFile
import io.ondeviceai.video.VideoAudioPolicy
import io.ondeviceai.video.VideoProcessingRequest
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Test-only same-codec controls; no media capture, network or model download. */
class ControlledSrInstrumentation : Instrumentation() {
    private lateinit var runId: String
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        runId = requireNotNull(arguments?.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        start()
    }

    override fun onStart() {
        val directory = File(targetContext.filesDir, "sr-quality/$runId")
        check(!directory.exists() && directory.mkdirs()) { "Run ID already exists" }
        val reportFile = AtomicFile(File(directory, "report.json"))
        val rows = JSONArray()
        val report = JSONObject().put("schemaVersion", 1).put("runId", runId)
            .put("pid", android.os.Process.myPid()).put("terminal", false).put("status", "running")
            .put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("runs", rows).put("qualityVerdict", "unverified")
        fun persist() {
            val stream = reportFile.startWrite()
            try {
                stream.write((report.toString(2) + "\n").toByteArray(Charsets.UTF_8))
                reportFile.finishWrite(stream)
            } catch (error: Throwable) { reportFile.failWrite(stream); throw error }
        }
        fun checkpoint() { check(!File(directory, "cancel").exists()) { "cancelled" } }
        var resultCode = 1
        var foreground: Activity? = null
        try {
            persist()
            foreground = startActivitySync(Intent(targetContext, QualityProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            report.put("foregroundActivityStarted", true); persist()
            val inputs = File(targetContext.filesDir, "controlled-sr-inputs")
            val input = File(inputs, "input.mp4")
            val model = File(inputs, "ESRGAN.tflite")
            check(sha(input) == "22b3e924ad5717a8cd278f92fdad3b39b1ed1886106a941e1d9b0287f6b026d8")
            check(LiteRtSrModel.valid(model)) { "fixed model missing or invalid" }
            report.put("inputSha256", sha(input)).put("modelSha256", sha(model))
                .put("sourceInventory", JSONObject(targetContext.assets.open("sr-source-inventory.json").bufferedReader().use { it.readText() }))
            val codecs = mutableListOf<String>()
            // Execute non-model controls first. Their outcomes are not model-quality passes.
            for (kind in listOf("nearest", "bilinear", "esrgan")) {
                checkpoint()
                val output = File(directory, "$kind.mp4")
                val row = JSONObject().put("kind", kind).put("status", "running")
                rows.put(row); SrCodecTrace.entries = JSONArray(); persist()
                var delivered = false
                try {
                    val process = { upscale: (IntArray, Int, Int) -> IntArray ->
                        AndroidSrMedia.process(VideoProcessingRequest(input, 4, VideoAudioPolicy.DISCARD),
                            output, ::checkpoint, { progress ->
                                row.put("completedFrames", progress.completedFrames); persist()
                            }, upscale)
                    }
                    val info = when (kind) {
                        "nearest" -> process { pixels, width, height ->
                            IntArray(width * height * 16) { index ->
                                pixels[(index / (width * 4) / 4) * width + index % (width * 4) / 4]
                            }
                        }
                        "bilinear" -> process { pixels, width, height ->
                            val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                            val scaled = try { Bitmap.createScaledBitmap(bitmap, width * 4, height * 4, true) }
                                finally { bitmap.recycle() }
                            try { IntArray(width * height * 16).also { scaled.getPixels(it, 0, width * 4, 0, 0, width * 4, height * 4) } }
                            finally { scaled.recycle() }
                        }
                        else -> EsrganInterpreter(model).use { interpreter ->
                            process { pixels, width, height -> EsrganTiles.upscale(pixels, width, height, ::checkpoint, interpreter::infer) }
                        }
                    }
                    check(info.width == 1280 && info.height == 720 && info.frameCount == 48 && info.audioTrackCount == 0)
                    val trace = SrCodecTrace.entries
                    check(trace.length() == 2)
                    val identity = trace.toString()
                    if (codecs.isNotEmpty()) check(identity == codecs.first()) { "codec selection differs between paths" }
                    codecs += identity
                    row.put("codecs", trace).put("outputSha256", sha(output)).put("outputBytes", output.length())
                        .put("width", info.width).put("height", info.height).put("frames", info.frameCount)
                        .put("status", "completed")
                    delivered = true; persist()
                } catch (error: Throwable) {
                    row.put("status", "failed").put("error", error.toString())
                        .put("codecs", SrCodecTrace.entries)
                    throw error
                } finally { if (!delivered) output.delete() }
            }
            report.put("status", "completed"); resultCode = -1
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.toString())
        } finally {
            report.put("terminal", true); persist()
            foreground?.let { activity -> runOnMainSync { activity.finish() } }
            finish(resultCode, Bundle().apply { putString("status", report.getString("status")) })
        }
    }

    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val bytes = ByteArray(1024 * 1024)
            while (true) { val count = stream.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

internal object SrCodecTrace {
    var entries = JSONArray()
    fun record(encoder: Boolean, name: String) {
        entries.put(JSONObject().put("encoder", encoder).put("name", name))
    }
}
