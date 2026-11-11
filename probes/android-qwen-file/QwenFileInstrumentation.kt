package io.ondeviceai.acceptance.qwenfile

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.AtomicFile
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import io.ondeviceai.acceptance.qwenload.ModelSpec
import io.ondeviceai.acceptance.qwenload.QwenModelFiles
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Thin Android orchestration wrapper for the Qwen file-recognition acceptance harness.
 *
 * This file ONLY orchestrates already-accepted modules:
 *  - [QwenFileActivity]      opt-in visible foreground gate
 *  - [QwenFileInputs]        fixture verify / PCM read (pure JVM)
 *  - [QwenModelFiles]        model-byte verification (qwenload package)
 *  - [QwenFileReport]        explicit JSONObject serializer, owns no logic
 *  - [TwoStageCaseRunner]    owns the native stream lifecycle + counters
 *
 * It performs the Android lifecycle, foreground/system gating, exclusive report
 * reservation, atomic persistence, exactly-once native recognizer construction
 * and cleanup. It contains NO input parser and NO JSON serializer of its own.
 */
class QwenFileInstrumentation : Instrumentation() {

    private var args: Bundle? = null
    private var activityRef: QwenFileActivity? = null

    override fun onCreate(arguments: Bundle?) {
        args = arguments
        start()
    }

    override fun onStart() {
        runAll()
    }

    private fun runAll() {
        var reportFile: File? = null
        var state = FileRunState()
        var mainError: Throwable? = null
        var teardownError: Throwable? = null
        var recognizer: OfflineRecognizer? = null
        var resultCode = Activity.RESULT_CANCELED

        // Preserve the first actual exception's class/message in state (and the
        // first mainError), never overwriting an earlier core/wrapper error.
        fun err(t: Throwable) {
            if (mainError == null) mainError = t
            if (state.errorClass == null) {
                state = state.copy(errorClass = t.javaClass.name, errorMessage = t.message)
            }
        }

        fun sysAwake(): Boolean {
            val pm = targetContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = targetContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return pm.isInteractive && !km.isKeyguardLocked
        }

        // Reads the live instance flags on the main thread and combines them with
        // the system awake/unlocked gate. Updates state foreground flags.
        fun fgGate(): Boolean {
            var allow = false
            runOnMainSync {
                val a = activityRef
                state = state.copy(
                    resumed = a?.resumeAcked == true,
                    paused = a?.pauseAcked == true,
                    destroyed = a?.destroyAcked == true
                )
                allow = state.resumed && !state.paused && !state.destroyed && sysAwake()
            }
            return allow
        }

        // Canonicalize ONLY the framework-provided root; fixed descendants are
        // checked component-by-component for symlinks/escape (never realpath here).
        fun child(root: File, name: String): File {
            require(name.isNotEmpty() && name != "." && name != ".." &&
                    name.indexOf('/') < 0 && name.indexOf('\\') < 0) { "bad child name" }
            val c = File(root, name)
            if (Files.isSymbolicLink(c.toPath())) throw IOException("symlink child rejected: $name")
            return c
        }

        // AtomicFile persistence. Captures the first actual wrapperPersistErrorClass
        // and rethrows; best-effort failWrite so a failed write is never claimed durable.
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
                if (state.wrapperPersistErrorClass == null) {
                    state = state.copy(wrapperPersistErrorClass = t.javaClass.name)
                }
                throw t
            }
        }

        try {
            // ---- 1. validate -------------------------------------------------
            state = state.copy(phase = "validate")
            val rid = args?.getString("runId")
            val fp = args?.getString("sourceFingerprint")
            if (rid == null || !Regex("[A-Za-z0-9-]{1,80}").matches(rid) ||
                fp == null || !Regex("[a-f0-9]{64}").matches(fp)) {
                throw IOException("invalid runId/sourceFingerprint")
            }
            state = state.copy(runId = rid, sourceFingerprint = fp, pid = android.os.Process.myPid())

            // ---- 2. exclusive reservation BEFORE any Activity/native ----------
            state = state.copy(phase = "reserve")
            val root = targetContext.filesDir.canonicalFile
            val dir = child(root, "w3-qwen-file")
            if (!dir.isDirectory) {
                if (dir.exists() || !dir.mkdirs()) throw IOException("cannot create report dir")
                if (Files.isSymbolicLink(dir.toPath())) throw IOException("report dir is symlink")
            }
            val base = child(dir, "report-$rid.json")
            for (f in arrayOf(base, File(base.path + ".new"), File(base.path + ".bak"))) {
                if (Files.isSymbolicLink(f.toPath()) || f.exists()) throw IOException("report/variant exists")
            }
            if (!base.createNewFile()) throw IOException("reserve failed")
            reportFile = base

            // First running report must land, else no native is attempted.
            state = state.copy(phase = "running")
            write(QwenFileReport.snapshot(state, terminal = false, status = "running"))

            // ---- 3. system-only gate before the Activity exists --------------
            state = state.copy(phase = "gate")
            if (!sysAwake()) throw IOException("system not awake/unlocked")

            // Exact non-exported Activity, NEW_TASK, bounded resume off main.
            state = state.copy(phase = "activity")
            val intent = Intent(targetContext, QwenFileActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val a = startActivitySync(intent) as? QwenFileActivity
                ?: throw IOException("exact Activity not obtained")
            activityRef = a
            if (!a.resumeArrived.await(5, TimeUnit.SECONDS)) throw IOException("resume timeout")
            if (!fgGate()) throw IOException("foreground gate failed")
            write(QwenFileReport.snapshot(state, terminal = false, status = "running"))

            // ---- 6. model + fixture verification (no JNI yet) ----------------
            state = state.copy(phase = "verify")
            val modelRoot = child(
                targetContext.noBackupFilesDir.canonicalFile,
                "qwen3-asr-0.6b-int8-393f8a14"
            )
            val verified: List<ModelSpec> = QwenModelFiles.verify(modelRoot)
            val inputs = QwenFileInputs.verifyAll { asset ->
                context.assets.open(asset).use { it.readBytes() }
            }
            val rows = inputs.map { FileRow(it) }
            state = state.copy(modelsVerified = verified, rows = rows)
            // Persist and a fresh foreground gate AFTER verification, before native.
            write(QwenFileReport.snapshot(state, terminal = false, status = "running"))
            if (!fgGate()) throw IOException("foreground gate failed after verify")

            // ---- 7. single native recognizer construction --------------------
            state = state.copy(phase = "load")
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
            // Read the constructor start time before incrementing, then count
            // immediately before the actual native constructor call.
            val t0 = System.nanoTime()
            state = state.copy(actualConstructors = state.actualConstructors + 1)
            try {
                recognizer = OfflineRecognizer(null, cfg)
            } catch (t: Throwable) {
                // Record the actual constructor error; release/finish still run in finally.
                err(t)
                throw t
            }
            // Evaluate timing into a local before copying to keep the receiver live.
            val loadNanos = try { System.nanoTime() - t0 } catch (t: Throwable) { err(t); null }
            state = state.copy(constructorReturned = true, loadNanos = loadNanos)
            write(QwenFileReport.snapshot(state, terminal = false, status = "running"))

            // ---- 8-9. batch: single recognizer protected by try/finally ------
            state = state.copy(phase = "run")
            val assetReader: (String) -> ByteArray = { asset ->
                context.assets.open(asset).use { it.readBytes() }
            }
            // Core alone owns stream counters and releases; wrapper never releases streams.
            val factory: () -> CaseStream = {
                val stream = recognizer!!.createStream()
                object : CaseStream {
                    override fun setLanguage(language: String) { stream.setOption("language", language) }
                    override fun accept(samples: FloatArray, sampleRate: Int) {
                        stream.acceptWaveform(samples, sampleRate)
                    }
                    override fun decode() { recognizer!!.decode(stream) }
                    override fun text(): String = recognizer!!.getResult(stream).text
                    override fun release() { stream.release() }
                }
            }

            // Core persist callback: copy the returned case state into the bound
            // parent row so actual partial counters survive, then persist running.
            fun corePersist(cs: CaseState) {
                val idx = state.activeCaseIndex
                if (idx != null && idx in state.rows.indices) {
                    val dur = state.caseStartedElapsedRealtimeNanos?.let {
                        SystemClock.elapsedRealtimeNanos() - it
                    }
                    val rs = state.rows.toMutableList()
                    rs[idx] = rs[idx].copy(status = cs.status, caseState = cs, caseNanos = dur)
                    state = state.copy(rows = rs)
                }
                write(QwenFileReport.snapshot(state, terminal = false, status = "running"))
            }

            val runner = TwoStageCaseRunner(factory = factory, gate = { fgGate() }, persist = { corePersist(it) })

            for (i in inputs.indices) {
                val input = inputs[i]
                if (!fgGate()) {
                    // Pass an actual IOException; the outer catch records its real
                    // class/message via err(t) instead of a hardcoded string.
                    throw IOException("foreground gate closed before case $i")
                }
                var samples: FloatArray? = null
                try {
                    samples = QwenFileInputs.readSamples(input, assetReader)
                } catch (t: Throwable) {
                    if (state.errorClass == null) {
                        state = state.copy(errorClass = t.javaClass.name, errorMessage = t.message)
                    }
                    break
                }
                try {
                    // compute start time first, then copy live state
                    val start = SystemClock.elapsedRealtimeNanos()
                    state = state.copy(activeCaseIndex = i, caseStartedElapsedRealtimeNanos = start)
                    val rs = state.rows.toMutableList()
                    rs[i] = rs[i].copy(status = "running")
                    state = state.copy(rows = rs)
                    write(QwenFileReport.snapshot(state, terminal = false, status = "running"))

                    val cs = runner.execute(input.locale, samples!!)
                    // compute duration first, then copy live state
                    val dur = SystemClock.elapsedRealtimeNanos() - start
                    val rs2 = state.rows.toMutableList()
                    rs2[i] = rs2[i].copy(
                        status = if (cs.status == "completed") "completed" else "error",
                        caseState = cs,
                        caseNanos = dur
                    )
                    // Retain the case binding on failure; clear only after a completed case.
                    state = state.copy(
                        rows = rs2,
                        activeCaseIndex = if (cs.status == "completed") null else state.activeCaseIndex,
                        caseStartedElapsedRealtimeNanos = if (cs.status == "completed") null else state.caseStartedElapsedRealtimeNanos
                    )
                    write(QwenFileReport.snapshot(state, terminal = false, status = "running"))

                    // Core reported error: stop the entire batch immediately.
                    if (cs.status != "completed") {
                        if (state.errorClass == null) {
                            state = state.copy(errorClass = cs.errorClass, errorMessage = cs.errorMessage)
                        }
                        break
                    }
                } catch (t: Throwable) {
                    // Wrapper/unknown failure: preserve the actual throwable and the
                    // case binding (cleared only after a completed case).
                    if (state.errorClass == null) {
                        state = state.copy(errorClass = t.javaClass.name, errorMessage = t.message)
                    }
                    val rs = state.rows.toMutableList()
                    if (i in rs.indices && rs[i].status == "running") rs[i] = rs[i].copy(status = "error")
                    state = state.copy(rows = rs)
                    break
                } finally {
                    // Own the full FloatArray; always clear it, even on errors.
                    samples?.fill(0f)
                }
            }
        } catch (t: Throwable) {
            err(t)
        } finally {
            // ---- 10. recognizer release attempt ONCE, even on earlier failure --
            val acquired = recognizer
            if (acquired != null) {
                state = state.copy(recognizerReleaseAttempts = state.recognizerReleaseAttempts + 1)
                // Evaluate timing into locals; a clock failure must never skip the
                // actual release nor the Activity finish.
                val rt0 = try { System.nanoTime() } catch (t: Throwable) { err(t); 0L }
                var released = false
                try {
                    acquired.release()
                    released = true
                } catch (t: Throwable) {
                    // Preserve primary failure; only record the release error class.
                    if (state.recognizerReleaseErrorClass == null) {
                        state = state.copy(recognizerReleaseErrorClass = t.javaClass.name)
                    }
                }
                val rn = try { System.nanoTime() - rt0 } catch (t: Throwable) { err(t); null }
                state = state.copy(recognizerReleaseSucceeded = released, recognizerReleaseNanos = rn)
            }

            // ---- 11. Activity teardown AFTER recognizer cleanup --------------
            val act = activityRef
            if (act != null) {
                try {
                    runOnMainSync { act.finish() }
                    val arrived = act.destroyArrived.await(5, TimeUnit.SECONDS)
                    state = state.copy(
                        resumed = act.resumeAcked,
                        paused = act.pauseAcked,
                        destroyed = act.destroyAcked
                    )
                    val ta = arrived && act.destroyAcked
                    state = state.copy(teardownAck = ta)
                    if (!ta) {
                        teardownError = IOException("destroy not acknowledged")
                        if (state.teardownErrorClass == null) {
                            state = state.copy(teardownErrorClass = teardownError!!.javaClass.name)
                        }
                    }
                } catch (t: Throwable) {
                    teardownError = t
                    if (state.teardownErrorClass == null) {
                        state = state.copy(teardownErrorClass = t.javaClass.name)
                    }
                } finally {
                    activityRef = null
                }
            }

            // Terminal snapshot ONLY after the actual destroy ack.
            val allRowsCompleted = state.rows.all { it.status == "completed" }
            val noErrors = mainError == null && teardownError == null &&
                state.errorClass == null && state.wrapperPersistErrorClass == null &&
                state.recognizerReleaseErrorClass == null && state.teardownErrorClass == null
            val allOk = noErrors && allRowsCompleted && state.constructorReturned &&
                state.recognizerReleaseSucceeded && state.recognizerReleaseAttempts == 1 &&
                state.teardownAck && state.rows.size == 65
            state = state.copy(phase = if (allOk) "terminal" else state.phase)

            // No report reservation => do not attempt final write.
            if (reportFile != null) {
                try {
                    write(QwenFileReport.snapshot(state, terminal = true, status = if (allOk) "completed" else "failed"))
                    resultCode = if (allOk) Activity.RESULT_OK else Activity.RESULT_CANCELED
                } catch (t: Throwable) {
                    err(t)
                    resultCode = Activity.RESULT_CANCELED
                }
            } else {
                resultCode = Activity.RESULT_CANCELED
            }

            finish(resultCode, Bundle())
        }
    }
}
