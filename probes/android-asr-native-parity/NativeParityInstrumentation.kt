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
import com.k2fsa.sherpa.onnx.*
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import io.ondeviceai.sherpaasr.SherpaWhisperModel
import io.ondeviceai.speech.SpeechRecognitionResult
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * One bounded SDK-session / direct-native parity diagnostic for the PUBLIC unsliced
 * Mandarin clip2006 (whose errors persist with and without VAD). Exactly ONE normal
 * SDK `SherpaAsrBackend.transcribe(rawFloats, "zh-CN")` call and ONE fresh direct
 * `OfflineRecognizer` call on the SAME verified prepared waveform. This is a
 * diagnostic, NOT a general scorer/observer, NOT a production fix, NOT a quality
 * approval and NOT a candidate picker. See RUNBOOK.md for one-pass acceptance.
 */
class NativeParityInstrumentation : Instrumentation() {

    private lateinit var destination: AtomicFile
    private lateinit var report: JSONObject

    private var activity: ForegroundProbeActivity? = null
    private var hadKeepScreenOn = false
    private var foregroundLifecycleHealthy = true
    private var foregroundTeardownSucceeded = false
    private var keepScreenOnRestored = false

    private var sdkAttempts = 0
    private var directAttempts = 0
    private var sdkCloseAttempted = false
    private var sdkCloseSucceeded = false
    private var directStreamReleaseAttempted = false
    private var directStreamReleased = false
    private var directRecognizerReleaseAttempted = false
    private var directRecognizerReleased = false
    private var callerSamplesUnchanged = false
    private var fingerprintAbsentBefore = false
    private var fingerprintMatched = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = requireNotNull(arguments)
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        fun hexArg(key: String): String = requireNotNull(args.getString(key)).also {
            require(it.matches(Regex("[0-9a-f]{64}"))) { "invalid $key" }
        }
        val sourceSHA = hexArg("sourceFingerprint")
        val apkSHA = hexArg("installedAppSha256")

        val parent = targetContext.filesDir
        val directory = File(parent, REPORT_DIR)
        check(!symlink(directory))
        check(directory.isDirectory || directory.mkdir())
        check(directory.canonicalPath == parent.canonicalPath + "/" + directory.name)
        val file = File(directory, "$runId.json")
        for (p in listOf(file, File(file.path + ".new"), File(file.path + ".bak"))) {
            check(!symlink(p) && !p.exists()) { "report path already reserved" }
        }
        check(file.createNewFile())
        destination = AtomicFile(file)

        report = JSONObject()
            .put("schemaVersion", 1)
            .put("kind", "android-native-parity-control")
            .put("runId", runId)
            .put("status", "running")
            .put("terminal", false)
            .put("pid", Process.myPid())
            .put("deviceModel", Build.MODEL)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("sourceFingerprint", sourceSHA)
            .put("installedAppSha256", apkSHA)
            .put("backendId", "sherpa.onnx.whisper")
            .put("locale", "zh-CN")
            .put("nativeLanguage", "zh")
            .put("modelDir", MODEL_DIR)
            .put("fingerprintFile", FINGERPRINT_FILE)
            .put("expectedInputSha256", INPUT_SHA256)
            .put("expectedInputBytes", EXPECTED_INPUT_BYTES)
            .put("expectedInputSamples", EXPECTED_INPUT_SAMPLES)
            .put("preparedRange", "[15744,108672)")
            .put("expectedPreparedSamples", EXPECTED_PREPARED_SAMPLES)
            .put("inputSha256", JSONObject.NULL)
            .put("inputShaMatchesExpected", false)
            .put("sdkHypothesis", JSONObject.NULL)
            .put("directHypothesis", JSONObject.NULL)
            .put("sdkPid", JSONObject.NULL)
            .put("directPid", JSONObject.NULL)
            .put("observedPreparedFloat32LeSha256", JSONObject.NULL)
            .put("observedPreparedSampleCount", JSONObject.NULL)
            .put("preparedFloat32LeSha256", JSONObject.NULL)
            .put("preparedSampleCount", JSONObject.NULL)
            .put("parity", false)
            .put("match", false)
            .put("samePid", false)
            .put("sdkAttempts", 0)
            .put("directAttempts", 0)
            .put("totalDecodeAttempts", 0)
            .put("attempts", 0)
            .put("sdkCloseAttempted", false)
            .put("sdkCloseSucceeded", false)
            .put("directStreamReleaseAttempted", false)
            .put("directStreamReleased", false)
            .put("directRecognizerReleaseAttempted", false)
            .put("directRecognizerReleased", false)
            .put("callerSamplesUnchanged", false)
            .put("fingerprintAbsentBefore", false)
            .put("preparedFingerprintMatched", false)
            .put("foregroundLifecycleHealthy", true)
            .put("foregroundTeardownSucceeded", false)
            .put("keepScreenOnRestored", false)
            .put("modelVerified", false)
            .put("microphoneStarts", 0)
            .put("audioPersisted", false)
            .put("qualityAccepted", false)
            .put("productionReady", false)
            .put("budgetApproved", false)
            .put("parityIsNotQualityProof", true)
        persist()
        start()
    }

    override fun onStart() {
        var raw: ByteArray? = null
        var rawFloats: FloatArray? = null
        var snapshot: FloatArray? = null
        var reconstructed: FloatArray? = null
        var sdkHypothesis: String? = null
        var directHypothesis: String? = null
        var sdkPid = 0
        var directPid = 0
        var parity = false
        var samePid = false
        var status = "failed"
        // Set only when the whole diagnostic path reaches its end WITHOUT a thrown error.
        // Terminal `status` is decided solely by the final completion gate (after teardown),
        // never asserted early. A `parity=false` is still a completed diagnostic when the
        // gate holds; it is NOT a quality pass.
        var reachedEnd = false
        val experimentStart = SystemClock.elapsedRealtimeNanos()
        report.put("experimentStartElapsedNs", experimentStart)

        try {
            // 1) Own foreground Activity (public SDK only). Errors caught INSIDE runOnMainSync.
            val started = startActivitySync(
                Intent(targetContext, ForegroundProbeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val probe = started as ForegroundProbeActivity
            activity = probe
            check(probe.resumeArrived.await(5, TimeUnit.SECONDS)) { "resume not observed" }
            runOnMainSyncCatching {
                hadKeepScreenOn = started.window.attributes.flags and
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                started.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            requireForeground()

            // 2) PUBLIC input PCM -> caller-owned rawFloats (never modified by SDK).
            val rawBytes = context.assets.open("mandarin-2006.pcm").use { it.readBytes() }
            raw = rawBytes
            check(rawBytes.size == EXPECTED_INPUT_BYTES && rawBytes.size % 2 == 0) { "unexpected input byte length" }
            val rawSha = sha256(rawBytes)
            report.put("inputSha256", rawSha)
                .put("inputBytes", rawBytes.size)
                .put("inputShaMatchesExpected", rawSha == INPUT_SHA256)
            check(rawSha == INPUT_SHA256) { "input SHA mismatch" }
            val shorts = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val samples = FloatArray(shorts.remaining()) { shorts.get() / 32768f }
            rawFloats = samples
            check(samples.size == EXPECTED_INPUT_SAMPLES) { "unexpected input sample count" }
            check(samples.all { it.isFinite() })
            report.put("inputSamples", samples.size)
            val retained = samples.copyOf()
            snapshot = retained

            // 3) Verify the EXISTING SherpaWhisperModel (no new model / download).
            val model = SherpaWhisperModel(File(targetContext.noBackupFilesDir, MODEL_DIR))
            model.verify()
            report.put("modelVerified", true)

            // 4) Fingerprint file must be ABSENT before invocation (absent-before required).
            val fingerprintFile = File(model.directory, FINGERPRINT_FILE)
            fingerprintAbsentBefore = !fingerprintFile.exists() && !symlink(fingerprintFile)
            check(fingerprintAbsentBefore) { "prepared fingerprint must be absent before run" }
            report.put("fingerprintAbsentBefore", true)

            // 5) ONE normal SDK call; close in finally; caller samples unchanged.
            requireForeground()
            var backend: SherpaAsrBackend? = null
            var sdkResult: SpeechRecognitionResult? = null
            val sdkStart = SystemClock.elapsedRealtimeNanos()
            try {
                val created = SherpaAsrBackend(targetContext, model)
                backend = created
                sdkAttempts++
                sdkResult = runBlocking { created.transcribe(samples, "zh-CN") }
            } finally {
                val closing = backend
                if (closing != null) {
                    sdkCloseAttempted = true
                    try {
                        runBlocking { closing.close() }
                        sdkCloseSucceeded = true
                    } catch (error: Throwable) {
                        // Retain the diagnostic: record the close error, do NOT let the
                        // terminal status become completed. The final completion gate fails
                        // closed when sdkCloseSucceeded is false, while both hypotheses and
                        // partial fields remain captured.
                        report.put("sdkCloseErrorClass", error.javaClass.name)
                    }
                }
            }
            val sdkEnd = SystemClock.elapsedRealtimeNanos()
            sdkPid = Process.myPid()
            val result = checkNotNull(sdkResult) { "SDK returned no result" }
            val sdkText = result.transcript
            sdkHypothesis = sdkText
            callerSamplesUnchanged = samples.contentEquals(retained)
            check(callerSamplesUnchanged) { "caller samples were modified" }
            report.put("sdkBackendId", result.backendId)
                .put("sdkHypothesis", sdkText)
                .put("sdkPid", sdkPid)
                .put("sdkStartElapsedNs", sdkStart)
                .put("sdkEndElapsedNs", sdkEnd)
                .put("sdkDurationNs", sdkEnd - sdkStart)
                .put("sdkAttempts", sdkAttempts)
                .put("sdkCloseAttempted", sdkCloseAttempted)
                .put("sdkCloseSucceeded", sdkCloseSucceeded)
                .put("callerSamplesUnchanged", callerSamplesUnchanged)
            persist()

            // 6) Read the observed prepared fingerprint written by the isolated copy.
            check(fingerprintFile.isFile && !symlink(fingerprintFile)) { "prepared fingerprint not written" }
            val parts = fingerprintFile.readText(Charsets.UTF_8).trim().split('|')
            check(parts.size == 2 &&
                parts[0].startsWith("preparedFloat32LeSha256=") &&
                parts[1].startsWith("preparedSampleCount=")) { "malformed prepared fingerprint" }
            val observedSha = parts[0].substringAfter("=")
            val observedCount = parts[1].substringAfter("=").toInt()
            check(observedSha.matches(Regex("[0-9a-f]{64}"))) { "malformed prepared SHA" }
            report.put("observedPreparedFloat32LeSha256", observedSha)
                .put("observedPreparedSampleCount", observedCount)

            // 7) EXPLICIT waveform equality BEFORE native decode (no range adjust, no decode on mismatch).
            val prepared = samples.copyOfRange(PREPARED_FROM, PREPARED_TO)
            reconstructed = prepared
            val reconstructedSha = float32LeSha256(prepared)
            val sizeEqual = observedCount == prepared.size
            val shaEqual = observedSha == reconstructedSha
            fingerprintMatched = sizeEqual && shaEqual
            report.put("reconstructedPreparedFloat32LeSha256", reconstructedSha)
                .put("reconstructedPreparedSampleCount", prepared.size)
                .put("preparedFloat32LeSha256", reconstructedSha)
                .put("preparedSampleCount", prepared.size)
                .put("preparedSizeEqual", sizeEqual)
                .put("preparedShaEqual", shaEqual)
                .put("preparedFingerprintMatched", fingerprintMatched)
            check(fingerprintMatched) {
                "prepared waveform fingerprint mismatch (size or SHA): do not adjust ranges or decode"
            }
            persist()

            // 8) ONE direct native call: fresh recognizer, no shared SDK session.
            requireForeground()
            val directStart = SystemClock.elapsedRealtimeNanos()
            directAttempts++
            val directText = directNativeDecode(model, prepared)
            val directEnd = SystemClock.elapsedRealtimeNanos()
            directPid = Process.myPid()
            directHypothesis = directText
            report.put("directHypothesis", directText)
                .put("directPid", directPid)
                .put("directStartElapsedNs", directStart)
                .put("directEndElapsedNs", directEnd)
                .put("directDurationNs", directEnd - directStart)
                .put("directAttempts", directAttempts)
                .put("directStreamReleaseAttempted", directStreamReleaseAttempted)
                .put("directStreamReleased", directStreamReleased)
                .put("directRecognizerReleaseAttempted", directRecognizerReleaseAttempted)
                .put("directRecognizerReleased", directRecognizerReleased)
            persist()

            // 9) Exact PUBLIC hypothesis comparison: a diagnostic finding only.
            // Status is intentionally NOT asserted here; it is decided by the final
            // completion gate after teardown.
            parity = sdkText == directText
            samePid = sdkPid == directPid
            report.put("parity", parity).put("match", parity).put("samePid", samePid)
            reachedEnd = true
        } catch (error: Throwable) {
            report.put("errorClass", error.javaClass.name)
        } finally {
            raw?.fill(0)
            rawFloats?.fill(0f)
            snapshot?.fill(0f)
            reconstructed?.fill(0f)

            // Teardown: restore ONLY the KEEP_SCREEN_ON bit; finish own Activity.
            val own = activity
            if (own != null) {
                try { requireForeground() } catch (_: Throwable) { foregroundLifecycleHealthy = false }
                try {
                    runOnMainSyncCatching {
                        if (!hadKeepScreenOn) {
                            own.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        keepScreenOnRestored = true
                        own.finish()
                        foregroundTeardownSucceeded =
                            own.isFinishing || own.isDestroyed || own.destroyAcked
                    }
                } catch (error: Throwable) {
                    report.put("teardownErrorClass", error.javaClass.name)
                    foregroundLifecycleHealthy = false
                }
            }
            activity = null

            val experimentEnd = SystemClock.elapsedRealtimeNanos()
            report.put("sdkHypothesis", sdkHypothesis ?: JSONObject.NULL)
                .put("directHypothesis", directHypothesis ?: JSONObject.NULL)
                .put("sdkPid", sdkPid).put("directPid", directPid)
                .put("parity", parity).put("match", parity).put("samePid", samePid)
                .put("sdkAttempts", sdkAttempts)
                .put("directAttempts", directAttempts)
                .put("totalDecodeAttempts", sdkAttempts + directAttempts)
                .put("attempts", sdkAttempts + directAttempts)
                .put("sdkCloseAttempted", sdkCloseAttempted)
                .put("sdkCloseSucceeded", sdkCloseSucceeded)
                .put("directStreamReleaseAttempted", directStreamReleaseAttempted)
                .put("directStreamReleased", directStreamReleased)
                .put("directRecognizerReleaseAttempted", directRecognizerReleaseAttempted)
                .put("directRecognizerReleased", directRecognizerReleased)
                .put("callerSamplesUnchanged", callerSamplesUnchanged)
                .put("fingerprintAbsentBefore", fingerprintAbsentBefore)
                .put("preparedFingerprintMatched", fingerprintMatched)
                .put("foregroundLifecycleHealthy", foregroundLifecycleHealthy)
                .put("foregroundTeardownSucceeded", foregroundTeardownSucceeded)
                .put("keepScreenOnRestored", keepScreenOnRestored)
                .put("experimentEndElapsedNs", experimentEnd)
                .put("totalDurationNs", experimentEnd - experimentStart)
                .put("microphoneStarts", 0)
                .put("audioPersisted", false)
                .put("qualityAccepted", false)
                .put("productionReady", false)

            // Final completion gate: evaluated AFTER teardown, BEFORE the durable report.
            // A parity=false is still a completed diagnostic if every safety/protocol
            // check holds; it is NOT a quality pass. We do NOT assert parity==true.
            // If the gate fails we fail closed and record the concrete reason.
            if (reachedEnd) {
                val gateReasons = mutableListOf<String>()
                if (sdkAttempts != 1) gateReasons.add("sdkAttempts!=$sdkAttempts")
                if (directAttempts != 1) gateReasons.add("directAttempts!=$directAttempts")
                if (!report.optBoolean("modelVerified")) gateReasons.add("modelNotVerified")
                if (!report.optBoolean("inputShaMatchesExpected")) gateReasons.add("inputShaMismatch")
                if (!callerSamplesUnchanged) gateReasons.add("callerSamplesChanged")
                if (!fingerprintMatched) gateReasons.add("preparedFingerprintMismatch")
                if (!sdkCloseSucceeded) gateReasons.add("sdkCloseFailed")
                if (!directStreamReleased) gateReasons.add("directStreamNotReleased")
                if (!directRecognizerReleased) gateReasons.add("directRecognizerNotReleased")
                if (!foregroundLifecycleHealthy) gateReasons.add("foregroundLifecycleUnhealthy")
                if (!foregroundTeardownSucceeded) gateReasons.add("foregroundTeardownFailed")
                if (!keepScreenOnRestored) gateReasons.add("keepScreenOnNotRestored")
                if (!samePid) gateReasons.add("pidMismatch")
                if (gateReasons.isEmpty()) {
                    status = "completed"
                } else {
                    status = "failed"
                    report.put("completionGateFailure", gateReasons.joinToString(";"))
                }
            }
            report.put("status", status).put("terminal", true)

            var durable = false
            try {
                persist()
                durable = true
            } catch (error: Throwable) {
                // A report-write error must NOT leave a completed status behind: fail
                // closed, keep the error class, then attempt to persist the failed report.
                // No inference retry.
                report.put("persistErrorClass", error.javaClass.name)
                status = "failed"
                report.put("status", status)
                try { persist(); durable = true } catch (_: Throwable) { /* Binder must fail. */ }
            }
            // Binder success ONLY after a durable final write.
            finish(if (durable && status == "completed") -1 else 1,
                Bundle().apply { putString("status", if (durable) status else "failed") })
        }
    }

    /** Fresh OfflineRecognizer matching production; stream/recognizer released on EVERY exit. */
    private fun directNativeDecode(model: SherpaWhisperModel, prepared: FloatArray): String {
        val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = model.directory.resolve("tiny-encoder.int8.onnx").absolutePath,
                    decoder = model.directory.resolve("tiny-decoder.int8.onnx").absolutePath,
                    language = "en", task = "transcribe"),
                tokens = model.directory.resolve("tiny-tokens.txt").absolutePath,
                numThreads = 4, debug = false, provider = "cpu")))
        try {
            // Constructor language "en"; setConfig(language "zh") before a fresh stream.
            val config = recognizer.config.copy(modelConfig = recognizer.config.modelConfig.copy(
                whisper = recognizer.config.modelConfig.whisper.copy(language = "zh")))
            recognizer.setConfig(config)
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(prepared, 16_000)
                recognizer.decode(stream)
                return recognizer.getResult(stream).text.trim()
            } finally {
                directStreamReleaseAttempted = true
                stream.release()
                directStreamReleased = true
            }
        } finally {
            directRecognizerReleaseAttempted = true
            recognizer.release()
            directRecognizerReleased = true
        }
    }

    /** Catch errors INSIDE runOnMainSync; handle them on the instrumentation thread. */
    private fun runOnMainSyncCatching(block: () -> Unit) {
        val captured = AtomicReference<Throwable?>(null)
        // SyncRunnable calls mTarget.run() with NO catch/finally: a throw on Main would
        // escape our failure report. Catch INSIDE the lambda, then rethrow off Main.
        runOnMainSync {
            try { block() } catch (error: Throwable) { captured.compareAndSet(null, error) }
        }
        // runOnMainSync's own failure propagates to the caller's try/catch (kept).
        captured.get()?.let { throw it }
    }

    private fun requireForeground() {
        val own = checkNotNull(activity)
        if (!own.resumeAcked || own.pauseAcked || own.destroyAcked) {
            foregroundLifecycleHealthy = false
            error("foreground lifecycle interrupted")
        }
    }

    private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** MUST match the replacement's float32 LE serialization byte-for-byte. */
    private fun float32LeSha256(samples: FloatArray): String {
        val buffer = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (index in samples.indices) buffer.putFloat(samples[index])
        return hex(MessageDigest.getInstance("SHA-256").digest(buffer.array()))
    }

    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val value = b.toInt() and 0xff
            out.append(digits[value ushr 4]).append(digits[value and 0x0f])
        }
        return out.toString()
    }

    private fun persist() {
        val stream = destination.startWrite()
        try {
            stream.write((report.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            destination.finishWrite(stream)
        } catch (error: Throwable) {
            destination.failWrite(stream)
            throw error
        }
    }

    private fun symlink(file: File): Boolean = try {
        OsConstants.S_ISLNK(Os.lstat(file.path).st_mode)
    } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) false else throw error
    }

    private companion object {
        const val REPORT_DIR = "odai-native-parity-reports"
        const val MODEL_DIR = "sherpa-whisper-tiny-65176e2d-vad9e2449e1"
        const val FINGERPRINT_FILE = "odai-native-parity-prepared.txt"
        const val INPUT_SHA256 = "333699e4542733689279698f52497c652dd23ed063f81a2ab861f6ab533f02b9"
        const val EXPECTED_INPUT_BYTES = 264960
        const val EXPECTED_INPUT_SAMPLES = 132480
        const val PREPARED_FROM = 15744
        const val PREPARED_TO = 108672
        const val EXPECTED_PREPARED_SAMPLES = 92928
    }
}
