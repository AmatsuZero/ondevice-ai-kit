package io.ondeviceai.acceptance.qwenload

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.AtomicFile
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Wrapper only: orchestration, gating, report persistence, lifecycle. Core owned elsewhere. */
class QwenLoadInstrumentation : Instrumentation() {

    private var args: Bundle? = null
    private var activityRef: QwenLoadActivity? = null

    override fun onCreate(arguments: Bundle?) {
        args = arguments
        start()
    }

    override fun onStart() {
        runAll()
    }

    private fun runAll() {
        var reportFile: File? = null
        var runId: String? = null
        var fingerprint: String? = null
        var phase = "initial"
        var mainError: Throwable? = null
        var teardownError: Throwable? = null
        var writeError: Throwable? = null
        var core = LoadState()
        var verified: List<ModelSpec> = emptyList()
        var resumed = false
        var paused = false
        var destroyed = false
        var teardownAck = false
        var resultCode = Activity.RESULT_CANCELED

        fun err(t: Throwable) { if (mainError == null) mainError = t }

        fun sysAwake(): Boolean {
            val pm = targetContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = targetContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return pm.isInteractive && !km.isKeyguardLocked
        }

        fun fgGate(): Boolean {
            var allow = false
            runOnMainSync {
                val a = activityRef
                resumed = a?.resumeAcked == true
                paused = a?.pauseAcked == true
                destroyed = a?.destroyAcked == true
                allow = resumed && !paused && !destroyed && sysAwake()
            }
            return allow
        }

        // Canonicalize ONLY the framework-provided root; fixed descendants are checked
        // component-by-component for symlinks/escape (never realpath/canonical here).
        fun child(root: File, name: String): File {
            require(name.isNotEmpty() && name != "." && name != ".." &&
                    name.indexOf('/') < 0 && name.indexOf('\\') < 0) { "bad child name" }
            val c = File(root, name)
            if (Files.isSymbolicLink(c.toPath())) throw IOException("symlink child rejected: $name")
            return c
        }

        fun write(json: JSONObject) {
            val af = AtomicFile(reportFile ?: throw IOException("report not reserved"))
            var s: FileOutputStream? = null
            try {
                s = af.startWrite()
                s.write(json.toString().toByteArray(StandardCharsets.UTF_8))
                s.flush()
                af.finishWrite(s)
                s = null
            } catch (t: Throwable) {
                s?.let { try { af.failWrite(it) } catch (_: Throwable) {} }
                writeError = t
                throw t
            }
        }

        fun configJson() = JSONObject().apply {
            put("featureSampleRate", 16000); put("featureDim", 128)
            put("decodingMethod", "greedy_search"); put("provider", "cpu")
            put("numThreads", 2); put("debug", false)
            put("maxTotalLen", 512); put("maxNewTokens", 128)
            put("temperature", 1e-6); put("topP", 0.8); put("seed", 42)
            put("hotwords", ""); put("modelType", ""); put("tokens", "")
            put("convFrontend", "conv_frontend.onnx"); put("encoder", "encoder.int8.onnx")
            put("decoder", "decoder.int8.onnx"); put("tokenizer", "modelRoot/tokenizer directory")
        }

        fun rows() = JSONArray().apply {
            verified.forEach { spec -> put(JSONObject().apply {
                put("relativePath", spec.relativePath); put("bytes", spec.bytes); put("sha256", spec.sha256)
            }) }
        }

        fun snapshot(terminal: Boolean, status: String) = JSONObject().apply {
            put("schemaVersion", 1)
            put("runId", runId ?: JSONObject.NULL)
            put("sourceFingerprint", fingerprint ?: JSONObject.NULL)
            put("pid", android.os.Process.myPid())
            put("terminal", terminal); put("status", status); put("phase", phase)
            put("foreground", JSONObject().apply {
                put("resumed", resumed); put("paused", paused)
                put("destroyed", destroyed); put("teardownAck", teardownAck)
            })
            put("plannedConstructors", 1); put("actualConstructors", core.constructorAttempts)
            put("streamsCreated", 0); put("nativeDecodeCalls", 0)
            put("microphoneStarts", 0); put("networkRequests", 0)
            put("config", configJson()); put("modelsVerified", rows())
            put("core", JSONObject().apply {
                put("status", core.status); put("phase", core.phase)
                put("constructorAttempts", core.constructorAttempts)
                put("constructorReturned", core.constructorReturned)
                put("releaseAttempts", core.releaseAttempts)
                put("releaseSucceeded", core.releaseSucceeded)
                put("loadNanos", core.loadNanos ?: JSONObject.NULL)
                put("releaseNanos", core.releaseNanos ?: JSONObject.NULL)
                put("errorClass", core.errorClass ?: JSONObject.NULL)
                put("errorMessage", core.errorMessage ?: JSONObject.NULL)
                put("releaseErrorClass", core.releaseErrorClass ?: JSONObject.NULL)
                put("persistErrorClass", core.persistErrorClass ?: JSONObject.NULL)
            })
            put("errorClass", mainError?.javaClass?.name ?: JSONObject.NULL)
            put("errorMessage", mainError?.message ?: JSONObject.NULL)
            put("teardownErrorClass", teardownError?.javaClass?.name ?: JSONObject.NULL)
            put("wrapperPersistErrorClass", writeError?.javaClass?.name ?: JSONObject.NULL)
        }

        try {
            // Only two configurable args; fingerprint is parent-bound, not device-computed.
            phase = "validate"
            val rid = args?.getString("runId")
            val fp = args?.getString("sourceFingerprint")
            if (rid == null || !Regex("[A-Za-z0-9-]{1,80}").matches(rid) ||
                fp == null || !Regex("[a-f0-9]{64}").matches(fp)) {
                throw IOException("invalid runId/sourceFingerprint")
            }
            runId = rid; fingerprint = fp

            // Exclusive reservation BEFORE any Activity/native work; no prior run overwrite.
            phase = "reserve"
            val root = targetContext.filesDir.canonicalFile
            val dir = child(root, "w3-qwen-load")
            if (!dir.isDirectory) {
                if (dir.exists() || !dir.mkdirs()) throw IOException("cannot create report dir")
                if (Files.isSymbolicLink(dir.toPath())) throw IOException("report dir is symlink")
            }
            val base = child(dir, "report-$runId.json")
            for (f in arrayOf(base, File(base.path + ".new"), File(base.path + ".bak"))) {
                if (Files.isSymbolicLink(f.toPath()) || f.exists()) throw IOException("report/variant exists")
            }
            if (!base.createNewFile()) throw IOException("reserve failed")
            reportFile = base

            // First running report must land, else no native is attempted.
            phase = "running"
            write(snapshot(false, "running"))

            // Initial system-only gate before the Activity exists (no lock bypass).
            phase = "gate"
            if (!sysAwake()) throw IOException("system not awake/unlocked")

            // Exact non-exported Activity, NEW_TASK, bounded resume off main.
            phase = "activity"
            val intent = Intent(targetContext, QwenLoadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val a = startActivitySync(intent) as? QwenLoadActivity ?: throw IOException("exact Activity not obtained")
            activityRef = a
            if (!a.resumeArrived.await(5, TimeUnit.SECONDS)) throw IOException("resume timeout")
            if (!fgGate()) throw IOException("foreground gate failed")
            write(snapshot(false, "running"))

            // Verified bytes (core); no JNI until this and running persistence succeed.
            phase = "verify"
            val modelRoot = child(targetContext.noBackupFilesDir.canonicalFile,
                "qwen3-asr-0.6b-int8-393f8a14")
            verified = QwenModelFiles.verify(modelRoot)
            write(snapshot(false, "running"))

            // Single native recognizer construction via the core coordinator.
            phase = "load"
            val cfg = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 128),
                modelConfig = OfflineModelConfig(
                    qwen3Asr = OfflineQwen3AsrModelConfig(
                        convFrontend = File(modelRoot, "conv_frontend.onnx").path,
                        encoder = File(modelRoot, "encoder.int8.onnx").path,
                        decoder = File(modelRoot, "decoder.int8.onnx").path,
                        tokenizer = File(modelRoot, "tokenizer").path,
                        maxTotalLen = 512, maxNewTokens = 128,
                        temperature = 1e-6f, topP = 0.8f, seed = 42
                    ),
                    numThreads = 2, debug = false, provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
            core = LoadCoordinator(
                factory = {
                    val r = OfflineRecognizer(null, cfg)
                    LoadHandle { r.release() }
                },
                gate = { fgGate() },
                persist = { st -> core = st; write(snapshot(false, "running")) }
            ).execute()
        } catch (t: Throwable) {
            err(t)
        }

        try {
            // Always tear down the acquired Activity and capture the real destroy ack.
            val acquired = activityRef
            if (acquired != null) {
                try {
                    runOnMainSync { acquired.finish() }
                    val arrived = acquired.destroyArrived.await(5, TimeUnit.SECONDS)
                    resumed = acquired.resumeAcked
                    paused = acquired.pauseAcked
                    destroyed = acquired.destroyAcked
                    teardownAck = arrived && acquired.destroyAcked
                    if (!teardownAck) teardownError = IOException("destroy not acknowledged")
                } catch (t: Throwable) {
                    teardownError = t
                } finally {
                    activityRef = null
                }
            }

            // Android status is computed here, never copied from core.status.
            val allOk = mainError == null && teardownError == null && writeError == null &&
                    teardownAck && core.status == "completed" &&
                    core.constructorReturned && core.releaseSucceeded &&
                    core.errorClass == null && core.errorMessage == null &&
                    core.releaseErrorClass == null && core.persistErrorClass == null
            phase = if (allOk) "terminal" else phase
            try {
                write(snapshot(true, if (allOk) "completed" else "failed"))
                resultCode = if (allOk) Activity.RESULT_OK else Activity.RESULT_CANCELED
            } catch (t: Throwable) {
                err(t)
                resultCode = Activity.RESULT_CANCELED
            }
        } catch (t: Throwable) {
            err(t)
            resultCode = Activity.RESULT_CANCELED
        } finally {
            finish(resultCode, Bundle())
        }
    }
}
