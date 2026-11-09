package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import android.view.WindowManager
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import io.ondeviceai.sherpaasr.SherpaWhisperModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Operator-only paced resource observation. No microphone, saved audio or transcripts. */
class SustainedAsrInstrumentation : Instrumentation() {
    private lateinit var destination: AtomicFile
    private lateinit var report: JSONObject
    private val cycleRows = JSONArray()
    private val evidence = mutableListOf<SustainedCycleEvidence>()
    private val rssLock = Any()
    private val rss = ArrayList<Pair<Long, Long>>()
    private val sampling = AtomicBoolean(false)
    private val sampleError = AtomicReference<Throwable?>(null)
    private var sampler: Thread? = null
    private var activity: ForegroundProbeActivity? = null
    private var hadKeepScreenOn = false
    private var lifecycleHealthy = true
    private var persistenceHealthy = true
    private var warmups = 0
    private var attempts = 0
    private var successes = 0

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = requireNotNull(arguments)
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        fun fingerprint(key: String): String = requireNotNull(args.getString(key)).also {
            require(it.matches(Regex("[0-9a-f]{64}"))) { "invalid $key" }
        }
        val sourceSHA = fingerprint("sourceFingerprint")
        val apkSHA = fingerprint("installedAppSha256")
        val parent = targetContext.filesDir
        val directory = File(parent, "odai-asr-sustained-reports")
        check(!symlink(directory))
        check(directory.isDirectory || directory.mkdir())
        check(directory.canonicalPath == parent.canonicalPath + "/" + directory.name)
        val file = File(directory, "$runId.json")
        for (p in listOf(file, File(file.path + ".new"), File(file.path + ".bak"))) {
            check(!symlink(p) && !p.exists()) { "report path already reserved" }
        }
        check(file.createNewFile())
        destination = AtomicFile(file)
        report = JSONObject().put("schemaVersion", 1).put("kind", "android-asr-sustained")
            .put("runId", runId).put("status", "running").put("terminal", false)
            .put("pid", Process.myPid()).put("deviceModel", Build.MODEL).put("sdkInt", Build.VERSION.SDK_INT)
            .put("sourceFingerprint", sourceSHA).put("installedAppSha256", apkSHA)
            .put("backendId", "sherpa.onnx.whisper").put("locale", "en-US")
            .put("inputSHA256", INPUT_SHA).put("microphoneStarts", 0)
            .put("audioPersisted", false).put("transcriptPersisted", false)
            .put("plannedCycles", 20).put("plannedMeasuredRequests", 40)
            .put("minimumCycleMs", 30000).put("samplingIntervalMs", 20)
            .put("rssMethod", "proc-self-status-VmRSS; sampled process including probe; not true peak")
            .put("timingMethod", "transcribe wall includes model verification/VAD/native decode/release and postconditions")
            .put("budgetApproved", false).put("productionReady", false)
            .put("cycles", cycleRows)
        persist()
        start()
    }

    override fun onStart() {
        var raw: ByteArray? = null
        var input: FloatArray? = null
        var retained: FloatArray? = null
        var completedWork = false
        var teardownSucceeded = false
        var startNs = 0L
        var measuredEndNs = 0L
        try {
            val started = startActivitySync(Intent(targetContext, ForegroundProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = started as ForegroundProbeActivity
            check(activity!!.resumeArrived.await(5, TimeUnit.SECONDS)) { "resume not observed" }
            runOnMainSync {
                hadKeepScreenOn = started.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                started.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            requireForeground()
            raw = context.assets.open("english.pcm").use { it.readBytes() }
            check(raw.isNotEmpty() && raw.size % 2 == 0 && raw.size / 2 <= 160000)
            check(MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) } == INPUT_SHA)
            val shorts = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val samples = FloatArray(shorts.remaining()) { shorts.get() / 32768f }
            input = samples
            check(samples.all { it.isFinite() })
            val snapshot = samples.copyOf()
            retained = snapshot
            val model = SherpaWhisperModel(File(targetContext.noBackupFilesDir, MODEL_DIR))
            model.verify()
            report.put("inputSamples", samples.size).put("beforeRunRssBytes", rssBytes())
            startSampler()
            startNs = SystemClock.elapsedRealtimeNanos()
            report.put("startedElapsedNs", startNs)
            repeat(20) { index ->
                requireForeground()
                val cycleStart = SystemClock.elapsedRealtimeNanos()
                val row = JSONObject().put("index", index + 1).put("status", "running")
                    .put("startedElapsedNs", cycleStart).put("requests", JSONArray())
                    .put("closeAttempted", false).put("closeSucceeded", false)
                cycleRows.put(row)
                var backend: SherpaAsrBackend? = null
                var failure: Throwable? = null
                var rowSuccesses = 0
                try {
                    backend = SherpaAsrBackend(targetContext, model)
                    val recognizer = backend
                    runBlocking {
                        if (index == 0) {
                            row.put("warmupAttempted", true)
                            decode(recognizer, samples, snapshot, row, true)
                            warmups++
                        }
                        repeat(2) {
                            decode(recognizer, samples, snapshot, row, false)
                            rowSuccesses++
                        }
                    }
                } catch (error: Throwable) {
                    failure = error
                    row.put("status", "failed").put("errorClass", error.javaClass.name)
                } finally {
                    val closing = backend
                    if (closing != null) {
                        row.put("closeAttempted", true)
                        val closeStart = SystemClock.elapsedRealtimeNanos()
                        try {
                            runBlocking { closing.close() }
                            row.put("closeSucceeded", true)
                        } catch (error: Throwable) {
                            row.put("closeErrorClass", error.javaClass.name)
                            if (failure == null) failure = error
                        }
                        val closedNs = SystemClock.elapsedRealtimeNanos()
                        row.put("closedElapsedNs", closedNs).put("closeDurationNs", closedNs - closeStart)
                        try { row.put("afterCloseImmediateRssBytes", rssBytes()) }
                        catch (error: Throwable) {
                            row.put("rssErrorClass", error.javaClass.name)
                            if (failure == null) failure = error
                        }
                    }
                    row.put("measuredSuccesses", rowSuccesses)
                    row.put("callerInputUnchanged", samples.contentEquals(snapshot))
                    // Preserve successful/failed partial work before any paced delay.
                    persist()
                }
                failure?.let { throw it }
                val closeWaitStart = row.getLong("closedElapsedNs")
                waitUntil(closeWaitStart + 1_000_000_000L)
                row.put("afterCloseWaitRssBytes", rssBytes())
                    .put("actualCloseWaitNs", SystemClock.elapsedRealtimeNanos() - closeWaitStart)
                val idleStart = SystemClock.elapsedRealtimeNanos()
                waitUntil(cycleStart + 30_000_000_000L)
                val ended = SystemClock.elapsedRealtimeNanos()
                row.put("actualPacingIdleNs", ended - idleStart)
                    .put("endedElapsedNs", ended).put("durationNs", ended - cycleStart)
                    .put("status", "completed")
                evidence.add(SustainedCycleEvidence(index + 1, cycleStart, ended, rowSuccesses,
                    row.getBoolean("closeSucceeded"), row.getBoolean("callerInputUnchanged")))
                persist()
            }
            measuredEndNs = SystemClock.elapsedRealtimeNanos()
            report.put("measuredWindowEndedNs", measuredEndNs)
                .put("pacedTotalNs", measuredEndNs - startNs)
                .put("finalProcessImmediateRssBytes", rssBytes())
            val finalWaitStart = SystemClock.elapsedRealtimeNanos()
            waitUntil(finalWaitStart + 1_000_000_000L)
            report.put("finalProcessWaitRssBytes", rssBytes())
                .put("actualFinalWaitNs", SystemClock.elapsedRealtimeNanos() - finalWaitStart)
            completedWork = true
        } catch (error: Throwable) {
            report.put("errorClass", error.javaClass.name)
        } finally {
            try { stopSampler() } catch (error: Throwable) {
                sampleError.compareAndSet(null, error)
            }
            raw?.fill(0); input?.fill(0f); retained?.fill(0f)
            val own = activity
            if (own != null) {
                try {
                    requireForeground()
                } catch (_: Throwable) { lifecycleHealthy = false }
                try {
                    runOnMainSync {
                        if (!hadKeepScreenOn) own.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        own.finish()
                        teardownSucceeded = own.isFinishing || own.isDestroyed || own.destroyAcked
                    }
                } catch (error: Throwable) {
                    report.put("teardownErrorClass", error.javaClass.name)
                }
            }
            activity = null
            report.put("foregroundLifecycleHealthy", lifecycleHealthy)
                .put("foregroundTeardownSucceeded", teardownSucceeded)
                .put("warmupSuccesses", warmups).put("measuredAttempts", attempts)
                .put("measuredSuccesses", successes).put("completedCycles", evidence.size)
                .put("samplerJoined", sampler?.isAlive != true)
                .put("samplerHealthy", sampleError.get() == null)
            sampleError.get()?.let { report.put("samplerErrorClass", it.javaClass.name) }
            synchronized(rssLock) {
                report.put("rssSampleCount", rss.size)
                report.put("rss", JSONArray(rss.map { (ns, bytes) -> JSONObject().put("elapsedNs", ns).put("bytes", bytes) }))
                if (rss.isNotEmpty()) report.put("rssPeakSampledBytes", rss.maxOf { it.second })
            }
            val complete = completedWork && sustainedRunComplete(evidence, warmups, attempts, successes,
                measuredEndNs - startNs, sampleError.get() == null && rss.isNotEmpty() && sampler?.isAlive != true,
                lifecycleHealthy, teardownSucceeded, persistenceHealthy)
            report.put("status", if (complete) "completed" else "failed").put("terminal", true)
            var durable = false
            try {
                persist()
                durable = true
            } catch (error: Throwable) {
                report.put("status", "failed").put("persistErrorClass", error.javaClass.name)
                try { persist(); durable = true } catch (_: Throwable) { /* Binder must fail. */ }
            }
            finish(if (durable && report.optString("status") == "completed") -1 else 1,
                Bundle().apply { putString("status", if (durable) report.optString("status") else "failed") })
        }
    }

    private suspend fun decode(backend: SherpaAsrBackend, input: FloatArray, retained: FloatArray,
                               cycle: JSONObject, warmup: Boolean) {
        requireForeground()
        sampleError.get()?.let { throw it }
        if (!warmup) attempts++
        val row = JSONObject().put("phase", if (warmup) "warmup" else "measurement")
            .put("status", "running")
        cycle.getJSONArray("requests").put(row)
        val start = SystemClock.elapsedRealtimeNanos()
        row.put("startedElapsedNs", start)
        try {
            val result = backend.transcribe(input, "en-US")
            val matched = result.transcript.lowercase().filter { it.isLetterOrDigit() } == "thisisanondevicespeechtest"
            val unchanged = input.contentEquals(retained)
            row.put("backendId", result.backendId).put("expectedMatched", matched)
                .put("callerInputUnchanged", unchanged)
            check(result.backendId == "sherpa.onnx.whisper" && matched && unchanged)
            requireForeground()
            sampleError.get()?.let { throw it }
            if (!warmup) successes++
            row.put("status", "completed")
        } catch (error: Throwable) {
            row.put("status", "failed").put("errorClass", error.javaClass.name)
            throw error
        } finally {
            val end = SystemClock.elapsedRealtimeNanos()
            row.put("endedElapsedNs", end).put("durationNs", end - start)
        }
    }

    private fun requireForeground() {
        val own = checkNotNull(activity)
        if (!own.resumeAcked || own.pauseAcked || own.destroyAcked) {
            lifecycleHealthy = false
            error("foreground lifecycle interrupted")
        }
    }

    private fun waitUntil(deadlineNs: Long) {
        while (true) {
            requireForeground()
            sampleError.get()?.let { throw it }
            val remaining = deadlineNs - SystemClock.elapsedRealtimeNanos()
            if (remaining <= 0) return
            Thread.sleep(minOf(50L, maxOf(1L, remaining / 1_000_000L)))
        }
    }

    private fun startSampler() {
        sampling.set(true)
        sampler = thread(name = "odai-asr-rss") {
            try {
                while (sampling.get()) {
                    val value = rssBytes()
                    synchronized(rssLock) { rss.add(SystemClock.elapsedRealtimeNanos() to value) }
                    Thread.sleep(20)
                }
            } catch (error: Throwable) { sampleError.set(error) }
        }
    }

    private fun stopSampler() {
        sampling.set(false)
        val worker = sampler ?: return
        // Join even when the instrumentation worker was interrupted; restore its flag later.
        var interrupted = false
        while (worker.isAlive) {
            try { worker.join() } catch (_: InterruptedException) { interrupted = true }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
            error("interrupted sampler join")
        }
    }

    private fun persist() {
        report.put("warmupSuccesses", warmups).put("measuredAttempts", attempts)
            .put("measuredSuccesses", successes).put("completedCycles", evidence.size)
            .put("persistenceHealthy", persistenceHealthy)
        synchronized(rssLock) { report.put("rssSampleCount", rss.size) }
        val stream = try { destination.startWrite() } catch (error: Throwable) {
            persistenceHealthy = false
            throw error
        }
        try {
            stream.write((report.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            destination.finishWrite(stream)
        } catch (error: Throwable) {
            persistenceHealthy = false
            destination.failWrite(stream)
            throw error
        }
    }

    private fun rssBytes(): Long {
        val fields = File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }.trim().split(Regex("\\s+"))
        }
        check(fields.size == 3 && fields[2] == "kB")
        return Math.multiplyExact(fields[1].toLong(), 1024L).also { check(it > 0) }
    }

    private fun symlink(file: File): Boolean = try {
        OsConstants.S_ISLNK(Os.lstat(file.path).st_mode)
    } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) false else throw error
    }

    private companion object {
        const val INPUT_SHA = "fc0f3f5bb9b06a4c21e12321d5fb0bc7d3e3151dca855e5ed9693907ce39df46"
        const val MODEL_DIR = "sherpa-whisper-tiny-65176e2d-vad9e2449e1"
    }
}
