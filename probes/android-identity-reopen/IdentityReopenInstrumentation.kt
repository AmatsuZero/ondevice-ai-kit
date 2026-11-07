package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import io.ondeviceai.identity.IdentityProbe
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelStatus
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.opencvidentity.OpenCvIdentityBackend
import io.ondeviceai.opencvidentity.OpenCvIdentityModel
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android Identity sustained reopen contract, version 1.
 * Measures twenty paced reopen cycles over at least ten minutes.
 */
class IdentityReopenInstrumentation : Instrumentation() {
    private lateinit var runId: String
    private lateinit var reportFile: AtomicFile
    private lateinit var report: JSONObject
    private val cycles = JSONArray()
    private var lastReadingNs = 0L

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        runId = requireNotNull(arguments?.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        require(arguments?.getString("reportMode") == "inference")
        val directory = File(targetContext.filesDir, "odai-identity-reopen-reports")
        if (!exists(directory)) check(directory.mkdir())
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
        val file = File(directory, "$runId.json")
        for (suffix in listOf("", ".new", ".bak")) check(!exists(File(file.path + suffix)))
        check(!exists(workDirectory()))
        Files.createFile(file.toPath()) // Exclusive reservation, never adopt a previous run.
        reportFile = AtomicFile(file)
        report = JSONObject()
            .put("schemaVersion", 1)
            .put("kind", "android-identity-reopen-resource")
            .put("runId", runId)
            .put("mode", "inference")
            .put("status", "running")
            .put("terminal", false)
            .put("sdkCommit", BuildConfig.ODAI_SDK_COMMIT)
            .put("backendId", OpenCvIdentityModel.BACKEND_ID)
            .put("runtimeVersion", OpenCvIdentityModel.VERSION)
            .put("modelRevision", OpenCvIdentityModel.ref.revision)
            .put("preprocessRevision", OpenCvIdentityModel.PREPROCESS)
            .put("inputSHA256", INPUT_SHA256)
            .put("modelSHA256", JSONObject().apply {
                OpenCvIdentityModel.manifest().files.forEach { put(it.path, it.sha256) }
            })
            .put("pid", android.os.Process.myPid())
            .put("deviceModel", Build.MODEL)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("enrollmentStarts", 0)
            .put("cameraStarts", 0)
            .put("imageWrites", 0)
            .put("embeddingPersisted", false)
            .put("cycleCount", 20)
            .put("warmupPerCycle", 2)
            .put("measurementPerCycle", 8)
            .put("minimumCycleMs", 30000)
            .put("minimumSessionMs", 600000)
            .put("samplingIntervalMs", 20)
            .put("afterCloseWaitMs", 1000)
            .put("measurementMethod", "proc-self-status-VmRSS-sampled-process")
            .put("timingMethod", "sdk-quality-and-embed-wall-per-reopened-backend")
            .put("foregroundActivity", "io.ondeviceai.demo.MainActivity")
            .put("foregroundStartedBeforeTiming", false)
            .put("foregroundCheckCount", 0)
            .put("foregroundPolicy", "RESUMED-at-cycle-and-sample-boundaries")
            .put("processScope", "process-with-idle-main-activity")
            .put("workDirectoryRemoved", false)
            .put("sourceInputUnchanged", false)
            .put("callerInputUnchanged", false)
            .put("cycles", cycles)
        persist()
        start()
    }

    private fun checkForeground(activity: MainActivity) {
        val resumed = AtomicBoolean(false)
        // Assert on this instrumentation thread so a failed checkpoint reaches
        // the durable failure recorder rather than crashing the main looper.
        runOnMainSync {
            resumed.set(!activity.isFinishing && !activity.isDestroyed &&
                activity.lifecycle.currentState == Lifecycle.State.RESUMED)
        }
        check(resumed.get())
        report.put("foregroundCheckCount", report.getInt("foregroundCheckCount") + 1)
    }

    override fun onStart() {
        val sourceImage = File(targetContext.filesDir, "identity-validation/authorized-astronaut.png")
        val sourceModels = File(targetContext.filesDir, "identity-validation/models")
        val work = workDirectory()
        var ownsWork = false
        var currentStore: ModelStore? = null
        var currentBackend: OpenCvIdentityBackend? = null
        var input: ByteArray? = null
        var retained: ByteArray? = null
        var activity: MainActivity? = null
        var originalKeepScreenOn = false

        try {
            check(BuildConfig.ODAI_SDK_COMMIT == SDK_COMMIT)
            val staging = File(targetContext.noBackupFilesDir, "opencv-identity/models/staging")
            check(staging.isDirectory && !Files.isSymbolicLink(staging.toPath()))
            check(requireNotNull(staging.list()).isEmpty())

            val act = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = act
            waitForIdleSync()

            runOnMainSync {
                val flags = act.window.attributes.flags
                originalKeepScreenOn = (flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
                if (!originalKeepScreenOn) {
                    act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            checkForeground(act) // Initial foreground checkpoint (1 of 462)
            report.put("foregroundStartedBeforeTiming", true)

            check(digest(sourceImage) == INPUT_SHA256)
            verifyModels(sourceModels)
            val image = sourceImage.readBytes()
            input = image
            check(digest(image) == INPUT_SHA256)
            retained = image.copyOf()

            check(!exists(work) && work.mkdir())
            ownsWork = true

            val sessionStarted = SystemClock.elapsedRealtimeNanos()
            report.put("startedElapsedNs", sessionStarted)
            persist()

            repeat(20) { cycleIndex ->
                val cycleStarted = SystemClock.elapsedRealtimeNanos()
                val cycleSamples = JSONArray()
                val cycleObj = JSONObject()
                    .put("index", cycleIndex)
                    .put("startedElapsedNs", cycleStarted)
                    .put("samples", cycleSamples)
                cycles.put(cycleObj)

                checkForeground(act) // Cycle check 1 of 23: before open

                val storeRoot = File(work, "store-$cycleIndex")
                check(!exists(storeRoot) && storeRoot.mkdir())
                val models = ModelStore(storeRoot, ModelCompatibility(
                    OpenCvIdentityModel.BACKEND_ID,
                    OpenCvIdentityModel.VERSION,
                    Build.VERSION.SDK_INT.toString(),
                    Build.SUPPORTED_ABIS.first()
                ))
                currentStore = models

                runBlocking {
                    models.install(OpenCvIdentityModel.manifest(), sourceModels)
                    check(models.status(OpenCvIdentityModel.ref) == ModelStatus.INSTALLED)

                    val beforeBackendOpenRss = rssBytes()
                    cycleObj.put("beforeBackendOpenRssBytes", beforeBackendOpenRss)

                    val engine = OpenCvIdentityBackend.open(models)
                    currentBackend = engine
                    check(engine.backendId == OpenCvIdentityModel.BACKEND_ID &&
                        engine.backendVersion == OpenCvIdentityModel.VERSION &&
                        engine.embeddingSpace == OpenCvIdentityModel.embeddingSpace)

                    val afterBackendOpenRss = rssBytes()
                    cycleObj.put("afterBackendOpenRssBytes", afterBackendOpenRss)

                    repeat(10) { sampleIndex ->
                        checkForeground(act) // Before sample call
                        val sample = measure(engine, image, checkNotNull(retained), sampleIndex)
                        cycleSamples.put(sample)
                        checkForeground(act) // After sample call
                        persist()
                    }

                    val closeStart = SystemClock.elapsedRealtimeNanos()
                    engine.closeAndWait()
                    val closeDuration = maxOf(0L, SystemClock.elapsedRealtimeNanos() - closeStart)
                    cycleObj.put("closeDurationNs", closeDuration)
                        .put("closeSucceeded", true)
                    currentBackend = null

                    cycleObj.put("afterCloseRssBytes", rssBytes())
                    SystemClock.sleep(1000)
                    cycleObj.put("afterCloseWaitRssBytes", rssBytes())

                    checkForeground(act) // Cycle check 22 of 23: after close wait

                    models.remove(OpenCvIdentityModel.ref)
                    check(models.status(OpenCvIdentityModel.ref) == ModelStatus.ABSENT)
                    cycleObj.put("modelLeaseReleased", true)
                    currentStore = null
                }

                Files.walk(storeRoot.toPath()).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
                }
                check(!exists(storeRoot))

                val targetCycleEnd = cycleStarted + 30_000_000_000L
                val remainingCycleNs = targetCycleEnd - SystemClock.elapsedRealtimeNanos()
                if (remainingCycleNs > 0) {
                    val sleepMs = remainingCycleNs / 1_000_000L
                    if (sleepMs > 0) SystemClock.sleep(sleepMs)
                    while (SystemClock.elapsedRealtimeNanos() < targetCycleEnd) {
                        SystemClock.sleep(1)
                    }
                }

                checkForeground(act) // Cycle check 23 of 23: after pacing
                val cycleEnded = SystemClock.elapsedRealtimeNanos()
                check(cycleEnded >= targetCycleEnd)
                cycleObj.put("endedElapsedNs", cycleEnded)
                persist()
            }

            val targetSessionEnd = sessionStarted + 600_000_000_000L
            val remainingSessionNs = targetSessionEnd - SystemClock.elapsedRealtimeNanos()
            if (remainingSessionNs > 0) {
                val sleepMs = remainingSessionNs / 1_000_000L
                if (sleepMs > 0) SystemClock.sleep(sleepMs)
                while (SystemClock.elapsedRealtimeNanos() < targetSessionEnd) {
                    SystemClock.sleep(1)
                }
            }

            checkForeground(act) // Final foreground checkpoint (462 of 462)
            val sessionEnded = SystemClock.elapsedRealtimeNanos()
            check(sessionEnded >= targetSessionEnd)
            report.put("endedElapsedNs", sessionEnded)
            check(report.getInt("foregroundCheckCount") == 462)

            check(digest(sourceImage) == INPUT_SHA256)
            verifyModels(sourceModels)
            report.put("sourceInputUnchanged", true)
            val unchanged = image.contentEquals(checkNotNull(retained))
            report.put("callerInputUnchanged", unchanged)
            check(unchanged)
            report.put("status", "completed")
        } catch (error: Throwable) {
            fail("errorClass", error)
        } finally {
            try {
                runBlocking { currentBackend?.closeAndWait() }
                currentBackend = null
            } catch (error: Throwable) { fail("closeCleanupErrorClass", error) }

            if (currentBackend == null) {
                try {
                    currentStore?.let { models ->
                        runBlocking {
                            models.remove(OpenCvIdentityModel.ref)
                            check(models.status(OpenCvIdentityModel.ref) == ModelStatus.ABSENT)
                        }
                    }
                    currentStore = null
                } catch (error: Throwable) { fail("modelCleanupErrorClass", error) }
            }

            if (ownsWork && currentBackend == null && currentStore == null) {
                try {
                    Files.walk(work.toPath()).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
                    }
                    check(!exists(work))
                    report.put("workDirectoryRemoved", true)
                } catch (error: Throwable) { fail("workCleanupErrorClass", error) }
            }

            activity?.let { act ->
                val cleanupError = AtomicReference<Throwable?>(null)
                try {
                    runOnMainSync {
                        try {
                            if (!act.isFinishing && !act.isDestroyed) {
                                if (!originalKeepScreenOn) {
                                    act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                }
                                val currentKeepScreenOn = (act.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
                                check(currentKeepScreenOn == originalKeepScreenOn)
                            }
                        } catch (error: Throwable) {
                            cleanupError.set(error)
                        }
                    }
                    cleanupError.get()?.let { throw it }
                } catch (error: Throwable) {
                    fail("foregroundFlagCleanupErrorClass", error)
                }
            }

            input?.fill(0)
            retained?.fill(0)
            report.put("terminal", true)
            persist()
        }

        finish(if (report.getString("status") == "completed") -1 else 1,
            Bundle().apply { putString("status", report.getString("status")) })
    }

    private fun measure(engine: OpenCvIdentityBackend, input: ByteArray,
                        original: ByteArray, index: Int): JSONObject {
        val readings = ArrayList<JSONObject>()
        readings.add(reading())
        val running = AtomicBoolean(true)
        val samplerError = AtomicReference<Throwable?>(null)
        val gate = CountDownLatch(1)
        val sampler = thread(name = "odai-identity-rss") {
            try {
                gate.await()
                while (running.get()) {
                    readings.add(reading())
                    SystemClock.sleep(20)
                }
            } catch (error: Throwable) { samplerError.set(error) }
        }
        var started = SystemClock.elapsedRealtimeNanos()
        while (started <= readings.first().getLong("elapsedNs")) {
            started = SystemClock.elapsedRealtimeNanos()
        }
        var ended: Long
        val result = try {
            readings.add(reading())
            gate.countDown()
            val probe = IdentityProbe(input, "approved-astronaut-v1")
            engine.evaluateQuality(probe) to engine.embed(probe)
        } finally {
            ended = SystemClock.elapsedRealtimeNanos()
            running.set(false)
            gate.countDown()
            sampler.join()
        }
        samplerError.get()?.let { throw it }
        check(ended > started)
        readings.add(reading())
        val quality = result.first
        val embedding = result.second
        check(quality.faceCount == 1 && quality.score == 1.0)
        check(embedding.embeddingSpace == engine.embeddingSpace && embedding.values.size == 128)
        val norm = embedding.values.sumOf { it.toDouble() * it.toDouble() }
        check(norm.isFinite() && norm > .99999 && norm < 1.00001)
        val unchanged = input.contentEquals(original)
        check(unchanged)
        check(readings.size >= 3 && readings.first().getLong("elapsedNs") <= started &&
            readings.last().getLong("elapsedNs") >= ended &&
            readings.any { it.getLong("elapsedNs") in started..ended })
        check(readings.zipWithNext().all { (a, b) -> a.getLong("elapsedNs") < b.getLong("elapsedNs") })
        val row = JSONObject().put("index", index)
            .put("phase", if (index < 2) "warmup" else "measurement")
            .put("backendId", engine.backendId).put("faceCount", quality.faceCount)
            .put("embeddingDimension", embedding.values.size).put("embeddingNormSquared", norm)
            .put("callerInputUnchanged", unchanged).put("startedElapsedNs", started)
            .put("endedElapsedNs", ended).put("durationNs", ended - started)
            .put("rss", JSONArray(readings))
            .put("rssPeakSampledBytes", readings.maxOf { it.getLong("bytes") })
        return row
    }

    private fun fail(key: String, error: Throwable) {
        report.put("status", "failed").put(key, error.javaClass.name)
    }

    private fun workDirectory() = File(targetContext.cacheDir, "odai-identity-reopen-$runId")

    private fun exists(file: File) = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun verifyModels(directory: File) {
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
        val manifest = OpenCvIdentityModel.manifest()
        check(directory.list()?.toSet() == manifest.files.map { it.path }.toSet())
        manifest.files.forEach { model ->
            val file = File(directory, model.path)
            check(file.length() == model.bytes && digest(file) == model.sha256)
        }
    }

    private fun digest(file: File): String {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    private fun digest(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun rssBytes(): Long {
        val fields = File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }.trim().split(Regex("\\s+"))
        }
        check(fields.size == 3 && fields[2] == "kB")
        return Math.multiplyExact(fields[1].toLong(), 1024L).also { check(it > 0) }
    }

    private fun reading(): JSONObject {
        val bytes = rssBytes()
        var now = SystemClock.elapsedRealtimeNanos()
        while (now <= lastReadingNs) {
            SystemClock.sleep(1)
            now = SystemClock.elapsedRealtimeNanos()
        }
        lastReadingNs = now
        return JSONObject().put("elapsedNs", now).put("bytes", bytes)
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
        const val SDK_COMMIT = "636a6eb8854db5dff74244c42c3ce0e82bd2be9f"
        const val INPUT_SHA256 = "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5"
    }
}
