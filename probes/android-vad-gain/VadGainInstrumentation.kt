package io.ondeviceai.demo

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.ondeviceai.demo.ForegroundProbeActivity
import io.ondeviceai.sherpaasr.Detector
import io.ondeviceai.sherpaasr.VadGainMeasurement
import io.ondeviceai.sherpaasr.VadObservation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private data class Clip(val id: String, val pcmName: String, val rawBytes: Int, val rawSha: String, val reference: String)

private val CLIPS = listOf(
    Clip("ami-ES2002a-D-330", "ami-ES2002a-D-330.pcm", 112032, "6f9370f42ec71311823ff5820cb97b88beb9c04db838685c2100a82830dbaf61", "And I'm Andrew and I'm uh our marketing"),
    Clip("ami-ES2002a-C-294", "ami-ES2002a-C-294.pcm", 114144, "0d65b45518193214e1be06a2bbffd5cafa03696727a0c5addea7e61c72e4ff0d", "Well, my favourite animal would be a monkey."),
    Clip("ami-ES2002a-B-146", "ami-ES2002a-B-146.pcm", 183424, "3e884a0ffe26fc41bc9c499102471231ac2c11479581ed3a85fad0cbebc6a0f0", "Okay, uh we now need to discuss the project finance. Um")
)

private val RUN_ID_RE = Regex("[A-Za-z0-9-]{1,80}")
private val FP_RE = Regex("[0-9a-f]{64}")
private val MODEL_SHA = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
private val MODEL_BYTES = 643854

private class BatchFailure(msg: String) : Exception(msg)

class VadGainInstrumentation : Instrumentation() {
    private var reportFile: AtomicFile? = null
    private var runId = ""
    private var fp = ""
    private val rows = JSONArray()
    private var releasesSucceeded = false
    private var status = "error"
    private var lastError: String? = null
    private var teardownErrorClass: String? = null
    private var fgStarted = false
    private var fgResumed = false
    private var fgResumeAcked = false
    private var fgPauseAcked = false
    private var fgFinishing = false
    private var fgDestroyed = false
    private var fgDestroyAcked = false
    private var modelInfo = JSONObject()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val rid = arguments?.getString("runId") ?: ""
        val fpr = arguments?.getString("sourceFingerprint") ?: ""
        if (!RUN_ID_RE.matches(rid) || !FP_RE.matches(fpr)) {
            finish(Activity.RESULT_CANCELED, Bundle.EMPTY)
            return
        }
        runId = rid
        fp = fpr
        setupReportDir()
        persist(buildReport("running", false))
        start()
    }

    override fun onStart() {
        super.onStart()
        var sources: List<FloatArray>? = null
        var probe: ForegroundProbeActivity? = null
        var persistOk = false
        var teardownOk = false
        try {
            if (!interactiveUnlocked()) fail("foreground_blocked")
            val started = startActivitySync(
                Intent(targetContext, ForegroundProbeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as ForegroundProbeActivity
            probe = started
            fgStarted = true
            if (!started.resumeArrived.await(5000, TimeUnit.MILLISECONDS)) fail("no_resume")
            runOnMainSync {
                fgResumeAcked = started.resumeAcked
                fgResumed = started.resumeAcked && !started.pauseAcked
            }
            if (!fgResumed) fail("no_resume_ack")
            sources = preflight()
            runArms(sources, started)
            releasesSucceeded = true
            var interactiveOk = false
            runOnMainSync {
                interactiveOk = interactiveUnlocked() && started.resumeAcked && !started.pauseAcked
            }
            if (!interactiveOk) {
                fail("foreground_interrupted")
            }
            status = "completed"
        } catch (e: Throwable) {
            status = "error"
            if (lastError == null) lastError = e.javaClass.simpleName
        } finally {
            try {
                teardownOk = if (probe != null) {
                    runOnMainSync { probe.finish() }
                    val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                    while (!probe.destroyAcked && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
                    runOnMainSync {
                        fgPauseAcked = probe.pauseAcked
                        fgResumeAcked = probe.resumeAcked
                        fgDestroyed = probe.isDestroyed
                        fgFinishing = probe.isFinishing
                        fgDestroyAcked = probe.destroyAcked
                    }
                    val acknowledged = fgFinishing || fgDestroyed || fgDestroyAcked
                    if (!acknowledged) throw IllegalStateException("foreground teardown not acknowledged")
                    acknowledged
                } else {
                    true
                }
            } catch (te: Throwable) {
                status = "error"
                teardownErrorClass = te.javaClass.simpleName
                if (lastError == null) lastError = teardownErrorClass
            }
            sources?.let { list -> for (s in list) s.fill(0f) }
            sources = null
            try {
                persist(buildReport(status, true))
                persistOk = true
            } catch (pe: Throwable) {
                status = "error"
                if (teardownErrorClass == null) teardownErrorClass = pe.javaClass.simpleName
                if (lastError == null) lastError = teardownErrorClass
            }
            reportFile = null
        }
        val okResult = status == "completed" && releasesSucceeded && teardownOk && persistOk
        finish(if (okResult) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle.EMPTY)
    }

    private fun fail(reason: String): Nothing {
        throw BatchFailure(reason)
    }

    private fun setupReportDir() {
        val parent = targetContext.filesDir.canonicalFile
        require(!isSymlink(parent.path)) { "filesDir symlink" }
        val dir = File(parent, "w3-vad-gain")
        require(!isSymlink(dir.path)) { "report dir symlink" }
        if (dir.exists()) require(dir.isDirectory) { "report dir not a directory" }
        else require(dir.mkdirs() || dir.isDirectory) { "cannot create report dir" }
        require(dir.canonicalPath == parent.canonicalPath + "/w3-vad-gain") { "report dir path escape" }
        require(!isSymlink(dir.path)) { "report dir symlink after create" }
        val file = File(dir, "report-$runId.json")
        require(file.canonicalPath == dir.canonicalPath + "/report-$runId.json") { "report file path escape" }
        require(!file.exists()) { "report already exists" }
        require(!isSymlink(file.path)) { "report file symlink" }
        val newFile = File(file.path + ".new")
        val bakFile = File(file.path + ".bak")
        require(!newFile.exists()) { "report .new exists" }
        require(!isSymlink(newFile.path)) { "report .new symlink" }
        require(!bakFile.exists()) { "report .bak exists" }
        require(!isSymlink(bakFile.path)) { "report .bak symlink" }
        require(file.createNewFile()) { "cannot reserve report" }
        reportFile = AtomicFile(file)
    }

    private fun isSymlink(path: String): Boolean {
        return try {
            OsConstants.S_ISLNK(Os.lstat(path).st_mode)
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.ENOENT) false else throw e
        }
    }

    private fun sha256(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
    }

    private fun readPcm(clip: Clip): FloatArray {
        val bytes = requireNotNull(context).assets.open(clip.pcmName).use { it.readBytes() }
        try {
            require(bytes.size == clip.rawBytes) { "asset length mismatch ${clip.id}" }
            require(sha256(bytes) == clip.rawSha) { "asset sha mismatch ${clip.id}" }
            val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(bytes.size / 2)
            for (i in out.indices) out[i] = bb.short / 32768f
            return out
        } finally {
            bytes.fill(0)
        }
    }

    private fun preflight(): List<FloatArray> {
        val decoded = mutableListOf<FloatArray>()
        var mbytes: ByteArray? = null
        var ok = false
        try {
            for (clip in CLIPS) decoded.add(readPcm(clip))
            for (s in decoded) for (v in s) require(kotlin.math.abs(v * 10f) < 1f) { "gain10 saturates" }
            val base = targetContext.noBackupFilesDir
            val modelDir = File(base, "sherpa-whisper-tiny-65176e2d-vad9e2449e1")
            val modelFile = File(modelDir, "silero_vad.onnx")
            val canonBase = base.canonicalPath
            val canonModel = modelFile.canonicalPath
            require(canonModel == canonBase + "/sherpa-whisper-tiny-65176e2d-vad9e2449e1/silero_vad.onnx") { "model path escape" }
            require(!isSymlink(modelDir.path)) { "model dir symlink" }
            require(!isSymlink(modelFile.path)) { "model symlink" }
            require(modelFile.isFile) { "model missing" }
            mbytes = modelFile.readBytes()
            require(mbytes.size == MODEL_BYTES) { "model size mismatch" }
            val msha = sha256(mbytes)
            require(msha == MODEL_SHA) { "model sha mismatch" }
            modelInfo = JSONObject().put("path", canonModel).put("bytes", mbytes.size).put("sha256", msha)
            ok = true
            return decoded.toList()
        } finally {
            mbytes?.fill(0)
            if (!ok) for (s in decoded) s.fill(0f)
        }
    }

    private fun interactiveUnlocked(): Boolean {
        val pm = targetContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = targetContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }

    private fun runArms(sources: List<FloatArray>, probe: ForegroundProbeActivity) {
        val modelPath = modelInfo.getString("path")
        val gains = listOf(1f, 10f)
        for (ci in CLIPS.indices) {
            val clip = CLIPS[ci]
            val source = sources[ci]
            for (gain in gains) {
                var proceed = false
                runOnMainSync {
                    proceed = interactiveUnlocked() && probe.resumeAcked && !probe.pauseAcked
                }
                if (!proceed) fail("arm_blocked")
                val obs = VadGainMeasurement.measure(source, gain) {
                    val vad = Vad(
                        config = VadModelConfig(
                            sileroVadModelConfig = SileroVadModelConfig(model = modelPath, threshold = 0.5f, windowSize = 512),
                            sampleRate = 16000,
                            numThreads = 1,
                            provider = "cpu",
                            debug = false
                        )
                    )
                    object : Detector {
                        override fun compute(window: FloatArray) = vad.compute(window)
                        override fun release() = vad.release()
                    }
                }
                appendRow(clip, gain, obs)
                persist(buildReport("running", false))
            }
        }
    }

    private fun appendRow(clip: Clip, gain: Float, obs: VadObservation) {
        val o = JSONObject()
            .put("inputCount", obs.inputCount)
            .put("processedCount", obs.processedCount)
            .put("classifierCalls", obs.classifierCalls)
            .put("speechWindows", obs.speechWindows)
            .put("speechSamples", obs.speechSamples)
            .put("detected", obs.detected)
            .put("preparedSamples", obs.preparedSamples)
            .put("gain", obs.gain)
            .put("probabilityMin", obs.probabilityMin ?: JSONObject.NULL)
            .put("probabilityMax", obs.probabilityMax ?: JSONObject.NULL)
            .put("probabilityMean", obs.probabilityMean ?: JSONObject.NULL)
        rows.put(
            JSONObject()
                .put("clipId", clip.id)
                .put("pcmSHA", clip.rawSha)
                .put("gain", gain)
                .put("reference", clip.reference)
                .put("observation", o)
        )
    }

    private fun buildReport(status: String, terminal: Boolean): JSONObject {
        return JSONObject()
            .put("schemaVersion", 1)
            .put("kind", "android-vad-gain")
            .put("runId", runId)
            .put("sourceFingerprint", fp)
            .put("pid", Process.myPid())
            .put("rows", rows)
            .put("expectedRows", 6)
            .put("microphoneStarts", 0)
            .put("recognizerCalls", 0)
            .put("audioPersisted", false)
            .put("status", status)
            .put("terminal", terminal)
            .put("releasesSucceeded", releasesSucceeded)
            .put("errorClass", lastError ?: JSONObject.NULL)
            .put("teardownErrorClass", teardownErrorClass ?: JSONObject.NULL)
            .put("foreground", JSONObject()
                .put("started", fgStarted)
                .put("resumed", fgResumed)
                .put("resumeAcked", fgResumeAcked)
                .put("pauseAcked", fgPauseAcked)
                .put("finishing", fgFinishing)
                .put("destroyed", fgDestroyed)
                .put("destroyAcked", fgDestroyAcked))
            .put("model", modelInfo)
    }

    private fun persist(report: JSONObject) {
        val af = reportFile ?: throw IllegalStateException("reportFile not reserved")
        val p = af.startWrite()
        try {
            p.channel.truncate(0)
            p.write(report.toString(2).toByteArray(Charsets.UTF_8))
            p.fd.sync()
            af.finishWrite(p)
        } catch (e: Throwable) {
            af.failWrite(p)
            throw e
        }
    }
}