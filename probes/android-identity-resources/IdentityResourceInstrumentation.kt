package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import androidx.lifecycle.Lifecycle
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.AtomicFile
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

/** Operator-only representation measurement. No enrollment, matching or camera access. */
class IdentityResourceInstrumentation : Instrumentation() {
    private lateinit var runId: String
    private lateinit var reportFile: AtomicFile
    private lateinit var report: JSONObject
    private val samples = JSONArray()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        runId = requireNotNull(arguments?.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        require(arguments?.getString("reportMode") == "inference")
        val directory = File(targetContext.filesDir, "odai-identity-resource-reports")
        if (!exists(directory)) check(directory.mkdir())
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
        val file = File(directory, "$runId.json")
        for (suffix in listOf("", ".new", ".bak")) check(!exists(File(file.path + suffix)))
        check(!exists(workDirectory()))
        Files.createFile(file.toPath()) // Exclusive reservation, never adopt a previous run.
        reportFile = AtomicFile(file)
        report = JSONObject().put("schemaVersion", 2).put("kind", "android-identity-resource")
            .put("runId", runId).put("mode", "inference")
            .put("status", "running").put("terminal", false)
            .put("sdkCommit", BuildConfig.ODAI_SDK_COMMIT)
            .put("backendId", OpenCvIdentityModel.BACKEND_ID)
            .put("runtimeVersion", OpenCvIdentityModel.VERSION)
            .put("modelRevision", OpenCvIdentityModel.ref.revision)
            .put("preprocessRevision", OpenCvIdentityModel.PREPROCESS)
            .put("modelSHA256", JSONObject().apply {
                OpenCvIdentityModel.manifest().files.forEach { put(it.path, it.sha256) }
            })
            .put("pid", android.os.Process.myPid()).put("deviceModel", Build.MODEL)
            .put("sdkInt", Build.VERSION.SDK_INT).put("inputSHA256", INPUT_SHA256)
            .put("enrollmentStarts", 0).put("cameraStarts", 0).put("imageWrites", 0)
            .put("embeddingPersisted", false).put("warmupCount", 5)
            .put("measurementCount", 30).put("samplingIntervalMs", 20)
            .put("measurementMethod", "proc-self-status-VmRSS-sampled-process")
            .put("timingMethod", "sdk-quality-and-embed-wall-after-one-backend-open")
            .put("samples", samples).put("finalCloseSucceeded", false)
            .put("modelLeaseReleased", false).put("workDirectoryRemoved", false)
            .put("sourceInputUnchanged", false).put("callerInputUnchanged", false)
            .put("foregroundActivity", "io.ondeviceai.demo.MainActivity")
            .put("foregroundStartedBeforeTiming", false).put("foregroundCheckCount", 0)
            .put("foregroundPolicy", "RESUMED-at-72-checkpoints")
            .put("processScope", "process-with-idle-main-activity")
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
        var store: ModelStore? = null
        var backend: OpenCvIdentityBackend? = null
        var input: ByteArray? = null
        var retained: ByteArray? = null
        try {
            check(BuildConfig.ODAI_SDK_COMMIT == SDK_COMMIT)
            // Ordinary UI constructor checks its installed model and clears staging.
            // Never launch it over staged imports, and never enable its enrollment session.
            val staging = File(targetContext.noBackupFilesDir, "opencv-identity/models/staging")
            check(staging.isDirectory && !Files.isSymbolicLink(staging.toPath()))
            check(requireNotNull(staging.list()).isEmpty())
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            checkForeground(activity)
            report.put("foregroundStartedBeforeTiming", true)
            persist()
            check(digest(sourceImage) == INPUT_SHA256)
            verifyModels(sourceModels)
            val image = sourceImage.readBytes()
            input = image
            check(digest(image) == INPUT_SHA256)
            retained = image.copyOf()
            check(!exists(work) && work.mkdir())
            ownsWork = true // Set only after this run created its own directory.
            val storeRoot = File(work, "store")
            check(storeRoot.mkdir())
            val models = ModelStore(storeRoot, ModelCompatibility(OpenCvIdentityModel.BACKEND_ID,
                OpenCvIdentityModel.VERSION, Build.VERSION.SDK_INT.toString(), Build.SUPPORTED_ABIS.first()))
            store = models
            runBlocking {
                models.install(OpenCvIdentityModel.manifest(), sourceModels)
                check(models.status(OpenCvIdentityModel.ref) == ModelStatus.INSTALLED)
                report.put("beforeBackendOpenRssBytes", rssBytes())
                val engine = OpenCvIdentityBackend.open(models)
                backend = engine
                check(engine.backendId == OpenCvIdentityModel.BACKEND_ID &&
                    engine.backendVersion == OpenCvIdentityModel.VERSION &&
                    engine.embeddingSpace == OpenCvIdentityModel.embeddingSpace)
                report.put("beforeRssBytes", rssBytes())
                repeat(35) { index ->
                    checkForeground(activity) // Outside the measured call window.
                    samples.put(measure(engine, image, checkNotNull(retained), index))
                    checkForeground(activity)
                    persist()
                }
                val started = SystemClock.elapsedRealtimeNanos()
                engine.closeAndWait()
                report.put("closeDurationNs", SystemClock.elapsedRealtimeNanos() - started)
                    .put("finalCloseSucceeded", true)
                backend = null
                report.put("afterCloseRssBytes", rssBytes()).put("afterCloseWaitMs", 1000)
                SystemClock.sleep(1000)
                report.put("afterCloseWaitRssBytes", rssBytes())
                checkForeground(activity)
                check(report.getInt("foregroundCheckCount") == 72)
                models.remove(OpenCvIdentityModel.ref)
                check(models.status(OpenCvIdentityModel.ref) == ModelStatus.ABSENT)
                report.put("modelLeaseReleased", true)
                store = null
            }
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
            // Never remove model files while a backend may still own their lease.
            try {
                runBlocking { backend?.closeAndWait() }
                backend = null
            } catch (error: Throwable) { fail("closeCleanupErrorClass", error) }
            if (backend == null) {
                try {
                    store?.let { models ->
                        runBlocking {
                            models.remove(OpenCvIdentityModel.ref)
                            check(models.status(OpenCvIdentityModel.ref) == ModelStatus.ABSENT)
                        }
                    }
                    store = null
                } catch (error: Throwable) { fail("modelCleanupErrorClass", error) }
            }
            if (ownsWork && backend == null && store == null) {
                try {
                    // walk() does not follow symlinks; cleanup stays inside the newly owned tree.
                    Files.walk(work.toPath()).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
                    }
                    check(!exists(work))
                    report.put("workDirectoryRemoved", true)
                } catch (error: Throwable) { fail("workCleanupErrorClass", error) }
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
        val started = SystemClock.elapsedRealtimeNanos()
        var ended: Long
        val result = try {
            // Guarantee an actual in-window reading, even if native calls finish before 20ms.
            // The worker cannot write until this append completes and the gate opens.
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
            .put("phase", if (index < 5) "warmup" else "measurement")
            .put("backendId", engine.backendId).put("faceCount", quality.faceCount)
            .put("embeddingDimension", embedding.values.size).put("embeddingNormSquared", norm)
            .put("callerInputUnchanged", unchanged).put("startedElapsedNs", started)
            .put("endedElapsedNs", ended).put("durationNs", ended - started)
            .put("rss", JSONArray(readings))
            .put("rssPeakSampledBytes", readings.maxOf { it.getLong("bytes") })
        SystemClock.sleep(200)
        row.put("afterIdleRssBytes", rssBytes())
        if (Build.VERSION.SDK_INT >= 29) {
            row.put("thermalStatus", targetContext.getSystemService(PowerManager::class.java).currentThermalStatus)
        }
        return row
    }

    private fun fail(key: String, error: Throwable) {
        report.put("status", "failed").put(key, error.javaClass.name)
    }

    private fun workDirectory() = File(targetContext.cacheDir, "odai-identity-resource-$runId")

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
        const val SDK_COMMIT = "636a6eb8854db5dff74244c42c3ce0e82bd2be9f"
        const val INPUT_SHA256 = "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5"
    }
}
