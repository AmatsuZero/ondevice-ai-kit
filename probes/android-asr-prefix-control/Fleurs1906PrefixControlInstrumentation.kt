package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import android.view.WindowManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.ondeviceai.sherpaasr.SherpaWhisperModel
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile

/** Exactly two sequential fixed native decodes. No capture, VAD invocation or crop scorer. */
class Fleurs1906PrefixControlInstrumentation : Instrumentation() {
    private val plan = Fleurs1906PrefixControlPlan
    private val report = linkedMapOf<String, Any?>()
    private lateinit var destination: AtomicFile
    private var activity: ForegroundProbeActivity? = null
    private var hadKeepScreenOn = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = requireNotNull(arguments)
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        fun digestArg(key: String) = requireNotNull(args.getString(key)).also {
            require(it.matches(Regex("[0-9a-f]{64}"))) { "invalid $key" }
        }
        val sourceSha = digestArg("sourceFingerprint")
        val mainSha = digestArg("installedAppSha256")
        val testSha = digestArg("installedTestSha256")
        val runtimeSha = digestArg("runtimeSha256")
        val parent = targetContext.filesDir
        val directory = File(parent, "odai-prefix-reports")
        check(!symlink(directory))
        check(directory.isDirectory || directory.mkdir())
        check(directory.canonicalPath == parent.canonicalPath + "/" + directory.name)
        val file = File(directory, "$runId.json")
        for (entry in listOf(file, File(file.path + ".new"), File(file.path + ".bak"))) {
            check(!symlink(entry) && !entry.exists()) { "run already reserved" }
        }
        check(file.createNewFile())
        destination = AtomicFile(file)
        report.putAll(linkedMapOf(
            "schemaVersion" to 1, "kind" to "w3-fixed-1906-prefix-control", "runId" to runId,
            "status" to "running", "terminal" to false, "pid" to Process.myPid(),
            "deviceModel" to Build.MODEL, "sdkInt" to Build.VERSION.SDK_INT,
            "sourceFingerprint" to sourceSha, "installedAppSha256" to mainSha,
            "installedTestSha256" to testSha, "runtimeSha256" to runtimeSha,
            "runtimeVersion" to "sherpa-onnx:2.0.1", "locale" to "zh-CN",
            "source" to linkedMapOf("dataset" to "google/fleurs", "split" to "cmn_hans_cn/test",
                "revision" to plan.REVISION, "license" to "CC-BY-4.0"),
            "reference" to plan.PUBLIC_REFERENCE,
            "referenceSha256" to plan.sha256(plan.PUBLIC_REFERENCE.toByteArray(Charsets.UTF_8)),
            "referenceScope" to "whole-clip; no independently time-aligned crop reference",
            "qualityVerdict" to "unverified", "microphoneStarts" to 0, "vadCalls" to 0,
            "modelVerified" to false, "audioArraysCleared" to false,
            "foregroundHealthy" to false, "foregroundTeardownSucceeded" to false,
            "keepScreenOnRestored" to false,
            "config" to linkedMapOf("sampleRate" to 16000, "featureDim" to 80,
                "constructorLanguage" to "en", "decodeLanguage" to "zh", "task" to "transcribe",
                "numThreads" to 4, "provider" to "cpu", "debug" to false),
        ))
        persist()
        start()
    }

    override fun onStart() {
        val errors = mutableListOf<Map<String, Any?>>()
        val observations = mutableListOf<Map<String, Any?>>()
        report["errors"] = errors
        report["models"] = observations
        var raw: ByteArray? = null
        var crops: List<ByteArray>? = null
        var result: PrefixResult? = null
        var foregroundHealthy = false
        var teardown = false
        var keepRestored = false
        try {
            val appHash = File(targetContext.applicationInfo.sourceDir).inputStream().use(::streamHash)
            val testHash = File(context.applicationInfo.sourceDir).inputStream().use(::streamHash)
            report["observedAppSha256"] = appHash
            report["observedTestSha256"] = testHash
            check(appHash == report["installedAppSha256"] && testHash == report["installedTestSha256"]) { "installed APK hash mismatch" }
            val runtimeHash = ZipFile(targetContext.applicationInfo.sourceDir).use { zip ->
                zip.getInputStream(checkNotNull(zip.getEntry("lib/arm64-v8a/libsherpa-onnx-jni.so"))).use(::streamHash)
            }
            report["observedRuntimeSha256"] = runtimeHash
            check(runtimeHash == report["runtimeSha256"]) { "runtime JNI hash mismatch" }
            val own = startActivitySync(Intent(targetContext, ForegroundProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ForegroundProbeActivity
            activity = own
            check(own.resumeArrived.await(5, TimeUnit.SECONDS)) { "resume not observed" }
            onMain {
                hadKeepScreenOn = own.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                own.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            requireForeground()
            val bytes = context.assets.open("fleurs-cmn-test-1906.pcm").use { it.readBytes() }
            raw = bytes
            val prepared = plan.prepare(bytes)
            crops = prepared
            report["input"] = linkedMapOf("clipBytes" to bytes.size, "clipSha256" to plan.sha256(bytes),
                "sliceRange" to listOf(83040, 166080), "sliceSha256" to plan.SLICE_SHA256,
                "sharedSuffixIdentical" to prepared[1].copyOfRange(4864, prepared[1].size).contentEquals(prepared[0]))
            val modelDir = File(targetContext.noBackupFilesDir, plan.MODEL_DIR)
            check(!symlink(modelDir) && modelDir.isDirectory)
            check(plan.MODEL_FILES == SherpaWhisperModel.files) { "production pin contract drift" }
            for (name in plan.MODEL_FILES.keys) check(!symlink(File(modelDir, name)))
            // These observed hashes are calculated from actual disk bytes before create() reaches JNI.
            plan.verifyModels(modelDir, observations)
            report["modelVerified"] = true
            persist()
            result = plan.runArms(prepared, { nativeRecognizer(modelDir) }) { partial ->
                report["execution"] = partial.toMap()
                persist()
            }
            report["execution"] = result.toMap()
            requireForeground()
            foregroundHealthy = true
        } catch (error: Throwable) {
            errors += plan.failure("instrumentation", error)
        } finally {
            raw?.fill(0)
            crops?.forEach { it.fill(0) }
            report["audioArraysCleared"] = (raw?.all { it == 0.toByte() } ?: true) &&
                (crops?.all { crop -> crop.all { it == 0.toByte() } } ?: true)
            val own = activity
            if (own != null) {
                try {
                    onMain {
                        if (!hadKeepScreenOn) own.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        keepRestored = (own.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) == hadKeepScreenOn
                        own.finish()
                    }
                    waitForIdleSync()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (!own.destroyAcked && System.nanoTime() < deadline) Thread.sleep(20)
                    teardown = own.destroyAcked
                    check(teardown && keepRestored) { "foreground teardown failed" }
                } catch (error: Throwable) { errors += plan.failure("foreground.cleanup", error) }
            }
            activity = null
            report["foregroundHealthy"] = foregroundHealthy
            report["foregroundTeardownSucceeded"] = teardown
            report["keepScreenOnRestored"] = keepRestored
            val completed = result?.completed == true && errors.isEmpty() && foregroundHealthy && teardown && keepRestored && report["audioArraysCleared"] == true
            report["status"] = if (completed) "completed" else "failed"
            report["terminal"] = true
            report["outputChanged"] = if (result?.completed == true)
                result.arms[0].hypothesis != result.arms[1].hypothesis else null
            var durable = false
            try { persist(); durable = true }
            catch (error: Throwable) {
                errors += plan.failure("terminal.persist", error)
                report["status"] = "failed"
                try { persist(); durable = true } catch (_: Throwable) { /* finish fails closed */ }
            }
            finish(if (durable && report["status"] == "completed") -1 else 1,
                Bundle().apply { putString("status", if (durable) report["status"] as String else "failed") })
        }
    }

    private fun nativeRecognizer(directory: File): PrefixRecognizer {
        requireForeground()
        val native = OfflineRecognizer(config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(directory, "tiny-encoder.int8.onnx").absolutePath,
                    decoder = File(directory, "tiny-decoder.int8.onnx").absolutePath,
                    language = "en", task = "transcribe"),
                tokens = File(directory, "tiny-tokens.txt").absolutePath,
                numThreads = 4, debug = false, provider = "cpu")))
        return object : PrefixRecognizer {
            override fun configure() {
                native.setConfig(native.config.copy(modelConfig = native.config.modelConfig.copy(
                    whisper = native.config.modelConfig.whisper.copy(language = "zh"))))
            }
            override fun createStream(): PrefixStream {
                requireForeground()
                val stream = native.createStream()
                return object : PrefixStream {
                    override fun decode(samples: FloatArray): String {
                        requireForeground()
                        stream.acceptWaveform(samples, 16000)
                        native.decode(stream)
                        return native.getResult(stream).text.trim()
                    }
                    override fun release() { stream.release() }
                }
            }
            override fun release() { native.release() }
        }
    }

    private fun requireForeground() {
        val own = checkNotNull(activity)
        check(own.resumeAcked && !own.pauseAcked && !own.destroyAcked) { "foreground interrupted" }
    }
    private fun streamHash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun onMain(block: () -> Unit) {
        val error = AtomicReference<Throwable?>(null)
        runOnMainSync { try { block() } catch (caught: Throwable) { error.set(caught) } }
        error.get()?.let { throw it }
    }
    private fun persist() {
        val stream = destination.startWrite()
        try {
            stream.write((plan.json(report) + "\n").toByteArray(Charsets.UTF_8))
            destination.finishWrite(stream)
        } catch (error: Throwable) { destination.failWrite(stream); throw error }
    }
    private fun symlink(file: File): Boolean = try { OsConstants.S_ISLNK(Os.lstat(file.path).st_mode) }
    catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) false else throw error }
}
