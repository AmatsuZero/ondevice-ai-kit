package io.ondeviceai.realtimesr

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class SrRunActivity : PlayerActivity() {
    private lateinit var runId: String
    private lateinit var runCase: String
    private lateinit var dir: File
    private var writer: java.io.BufferedWriter? = null
    private val frames = mutableSetOf<FrameId>()
    private val sourceOrdinals = mutableSetOf<Int>()
    private var frameGeneration: Long? = null
    private var frameSequenceConsistent = true
    private var generation = 0L
    private var lastAudioUs = 0L
    private val swapBackends = mutableMapOf<String, Int>()
    private var displayCount = 0
    private var displayTimingSupported = false
    private var ended = false
    private var fixtureSha256: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runId = intent.getStringExtra("runId") ?: return finish()
        if (!runId.matches(Regex("[0-9a-f]{32}"))) return finish()
        runCase = intent.getStringExtra("srCase") ?: ""
        dir = File(File(filesDir, "sr-runs"), runId)
        if (!dir.isDirectory && !dir.mkdirs()) return finish()
        if (listOf("run.json", "events.jsonl", "terminal.json").any { File(dir, it).exists() }) return finish()
        writer = File(dir, "events.jsonl").bufferedWriter()
        val fixture = File(dir, "fixture.mp4")
        if (!fixture.isFile) return endRun(false, "fixture missing or SHA-256 mismatch")
        val actualFixtureSha256 = sha256(fixture)
        fixtureSha256 = actualFixtureSha256
        if (actualFixtureSha256 !in FIXTURE_SHA256S) return endRun(false, "fixture missing or SHA-256 mismatch")
        if (runCase !in setOf("original", "original-egl", "original-ready", "cunny", "spatial", "spatial-ready", "auto")) return endRun(false, "invalid srCase", "unsupported")
        try {
            selectMode(when (runCase) {
                "cunny" -> PlaybackMode.CUNNY
                "spatial", "spatial-ready" -> PlaybackMode.SPATIAL
                "auto" -> PlaybackMode.AUTO
                else -> PlaybackMode.ORIGINAL
            })
            selectSource(Uri.fromFile(fixture))
        } catch (e: Exception) {
            endRun(false, e.message ?: "playback setup failed")
        }
    }

    override fun useDirectOutput(): Boolean = runCase == "original"

    override fun useEarlySubmit(): Boolean =
        ::runCase.isInitialized && runCase in setOf("original-ready", "spatial-ready")

    override fun onFrameOffered(ptsUs: Long, targetNs: Long, id: FrameId) {
        if (ended) return
        generation = id.generation
        if (frameGeneration == null) frameGeneration = id.generation
        else if (frameGeneration != id.generation) frameSequenceConsistent = false
        if (id.loop != 0) frameSequenceConsistent = false
        sourceOrdinals.add(id.sourceOrdinal)
        if (frames.add(id)) {
            append(JSONObject().put("runId", runId).put("generation", id.generation).put("loop", id.loop)
                .put("sourceOrdinal", id.sourceOrdinal).put("stage", "decodeRelease")
                .put("ptsUs", ptsUs).put("targetNs", targetNs))
            if (frames.size % 30 == 0) writer?.flush()
        }
    }

    override fun onMediaPosition(mediaUs: Long) { lastAudioUs = mediaUs }

    override fun onSrEvent(id: FrameId, stage: String, fields: Map<String, Any?>) {
        if (ended || stage !in setOf("latch", "srStart", "srEnd", "swap", "display")) return
        generation = id.generation
        if (stage == "swap") {
            val backend = fields["submittedBackend"]?.toString() ?: "unknown"
            swapBackends[backend] = (swapBackends[backend] ?: 0) + 1
            val eglFrameId = (fields["eglFrameId"] as? Number)?.toLong()
            if (eglFrameId != null && eglFrameId >= 0L) displayTimingSupported = true
        }
        if (stage == "display") {
            val displayNs = (fields["displayNs"] as? Number)?.toLong()
            if (displayNs != null && displayNs > 0L) displayCount++
        }
        val line = JSONObject().put("runId", runId).put("generation", id.generation)
            .put("loop", id.loop).put("sourceOrdinal", id.sourceOrdinal).put("stage", stage)
        fields.forEach { (key, value) -> line.put(key, value ?: JSONObject.NULL) }
        append(line)
        if (fields["pausedPreview"] == true) writer?.flush()
    }

    override fun onRunEnded() {
        if (ended) return
        Handler(Looper.getMainLooper()).postDelayed({
            val valid = frames.size == 900 && sourceOrdinals == (0 until 900).toSet() &&
                frameSequenceConsistent && frameGeneration != null && lastAudioUs in 29_000_000L..31_000_000L
            val status = if (valid) "completed" else "failed"
            endRun(valid, if (valid) "decode/audio complete" else "decode/audio check failed", status)
        }, 750L)
    }

    override fun onRunError(message: String) {
        if (!::runId.isInitialized || !::dir.isInitialized) { finish(); return }
        endRun(false, message)
    }

    override fun onDestroy() {
        ended = true
        closeWriter()
        super.onDestroy()
    }

    private fun append(json: JSONObject) {
        writer?.apply { write(json.toString()); newLine() }
    }

    private fun closeWriter() {
        val currentWriter = writer ?: return
        writer = null
        currentWriter.close()
    }

    private fun endRun(completed: Boolean, detail: String, requestedStatus: String = "failed") {
        if (!::runId.isInitialized || !::dir.isInitialized) { finish(); return }
        if (ended) return
        ended = true
        closeWriter()
        val run = JSONObject().put("schemaVersion", 1).put("runId", runId).put("fixtureSha256", fixtureSha256 ?: JSONObject.NULL)
            .put("generation", generation).put("loops", 1).put("mode", when (runCase) {
                "cunny" -> "CUNNY"
                "spatial", "spatial-ready" -> "SPATIAL"
                "auto" -> "AUTO"
                else -> "ORIGINAL"
            })
            .put("earlySubmitReady", runCase in setOf("original-ready", "spatial-ready"))
            .put("displayTimingSupported", displayTimingSupported).put("completed", completed)
        val terminal = JSONObject().put("runId", runId).put("case", runCase).put("status", requestedStatus)
            .put("detail", detail).put("frameCallbacks", frames.size).put("lastAudioUs", lastAudioUs)
            .put("presentationSupported", displayTimingSupported).put("swapBackends", JSONObject(swapBackends as Map<*, *>))
            .put("displayCount", displayCount)
        atomicJson("run.json", run)
        atomicJson("terminal.json", terminal)
        finish()
    }

    private fun atomicJson(name: String, json: JSONObject) {
        val tmp = File(dir, "$name.tmp")
        FileOutputStream(tmp).use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
        check(tmp.renameTo(File(dir, name))) { "Could not finalize $name" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    companion object {
        private val FIXTURE_SHA256S = setOf(
            "df677798a8604a7a6289fcf50a0fef3844a047631e0041ae7d5563c55f3746ec",
            "e698bee8c98c3158eb5e99e26f1cbe13334b52557720b2129a3e31dd2fa67dcb"
        )
    }
}
