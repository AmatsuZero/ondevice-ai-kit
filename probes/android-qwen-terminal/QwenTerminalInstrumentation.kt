package io.ondeviceai.acceptance.qwenfile

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.Debug
import android.util.AtomicFile
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import io.ondeviceai.acceptance.qwenload.ModelSpec
import io.ondeviceai.acceptance.qwenload.QwenModelFiles
import io.ondeviceai.acceptance.qwenarena.ArenaPolicyReceipt
import io.ondeviceai.acceptance.qwenarena.QwenArenaPolicy
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import io.ondeviceai.acceptance.terminal.TerminalAckFile
import io.ondeviceai.acceptance.terminal.TerminalObservationBinding

/**
 * Distinct Qwen terminal diagnostic wrapper for the file-recognition harness.
 *
 * Same qwenfile package/lifecycle as the baseline FILE wrapper, consuming the
 * unchanged Activity/input/report/core modules, but with its own report child
 * `w3-qwen-terminal`, the accepted CPU arena-off allocator policy prepared and
 * verified exactly once before the single native recognizer construction, and
 * truthful requested-policy snapshot metadata layered on the unchanged shared
 * serializer. It additionally records Android native-heap boundary events
 * (Debug.getNativeHeap* sizes) at known lifecycle points via HeapBoundaryRecorder
 * for parent-side RSS alignment; it does not claim per-run peak, allocator
 * retention, or root cause.
 *
 * The allocator policy is REQUESTED configuration only: this class never claims
 * a verified native effect, applied/readback state, or memory savings. It
 * performs no JSON serialization and no input parsing of its own beyond the
 * shared serializer.
 */
class QwenTerminalInstrumentation @JvmOverloads constructor(
    private val readProcessStat: (Int) -> ByteArray = { terminalReadProcessStat(it) },
    private val waitForTerminalAck: (TerminalAckFile, TerminalObservationBinding) -> Unit = { file, binding -> terminalAwaitAck(file, binding) }
) : Instrumentation() {

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
        var receipt: ArenaPolicyReceipt? = null
        var processStartTicks: String? = null
        var terminalAckFile: TerminalAckFile? = null
        var mainError: Throwable? = null
        var teardownError: Throwable? = null
        var recognizer: OfflineRecognizer? = null
        var resultCode = Activity.RESULT_CANCELED

        // ---- 0. heap boundary telemetry setup -------------------------------
        val SELECTED_CLIP_ID = "fleurs-en_us-1776"
        val SELECTED_ORIGINAL_INDEX = 25
        val DIAGNOSTIC_SCOPE = "Single public clip fleurs-en_us-1776 (en-US, 76800 samples, PCM SHA256 2821c39358fc8049953945d8042d1833d86cb2245ca468cf5afc31ee1fe1a438) at manifest zero-based index 25 is deliberately repeated by the terminal protocol; the other 64 verified inputs are not replayed. No quality or accuracy claim is made for this replay."
        var verifiedInputCount = 0
        // Heap boundary recorder: non-atomic three-read sampler, no threads/timers/
        // GC/trim. Snapshot never captures; only these lifecycle points call capture.
        val heapRecorder = HeapBoundaryRecorder(
            nowNanos = { SystemClock.elapsedRealtimeNanos() },
            heapSizeBytes = { Debug.getNativeHeapSize() },
            heapAllocatedBytes = { Debug.getNativeHeapAllocatedSize() },
            heapFreeBytes = { Debug.getNativeHeapFreeSize() }
        )
        // Derives current CaseState from the bound row (retains final stages even
        // after activeCaseIndex is cleared) and the live activeCaseIndex.
        fun captureHeap(label: String, cleanup: Boolean = false) {
            val cs = state.rows.firstOrNull()?.caseState
            heapRecorder.capture(label, state.activeCaseIndex, cs, cleanup)
        }

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
        fun write(json: JSONObject): ByteArray {
            val af = AtomicFile(reportFile ?: throw IOException("report not reserved"))
            var s: FileOutputStream? = null
            try {
                val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
                s = af.startWrite()
                s.write(bytes)
                s.flush()
                af.finishWrite(s)
                s = null
                return bytes
            } catch (t: Throwable) {
                s?.let { try { af.failWrite(it) } catch (_: Throwable) {} }
                if (state.wrapperPersistErrorClass == null) {
                    state = state.copy(wrapperPersistErrorClass = t.javaClass.name)
                }
                throw t
            }
        }

        // Arena requested-policy snapshot helper: wraps the unchanged shared
        // serializer and adds truthful requested-policy metadata at EVERY write
        // point (initial/running/receipt/failed/terminal/best-effort). This is a
        // requested configuration only; no native effect is ever claimed.
        fun arenaSnapshot(terminal: Boolean, status: String): JSONObject {
            val root = QwenFileReport.snapshot(state, terminal = terminal, status = status)
            root.put("probeVariant", "cpu-arena-off-heap-terminal-ack-v1")
            root.put("processStartTicks", processStartTicks ?: JSONObject.NULL)
            root.put("terminalObservationProtocol", "ODAI-TERMINAL-ACK-v1")
            root.put("terminalAckTimeoutMillis", 30000)
            root.put("selectedClipId", SELECTED_CLIP_ID)
            root.put("selectedOriginalIndex", SELECTED_ORIGINAL_INDEX)
            root.put("verifiedInputCount", verifiedInputCount)
            root.put("diagnosticScope", DIAGNOSTIC_SCOPE)
            root.put("nativePolicyEffectVerified", false)
            val r = receipt
            if (r == null) {
                root.put("allocatorPolicy", JSONObject.NULL)
            } else {
                val ap = JSONObject()
                ap.put("provider", r.provider)
                ap.put("configPath", r.configPath)
                ap.put("bytes", r.bytes)
                ap.put("sha256", r.sha256)
                root.put("allocatorPolicy", ap)
            }
            val config = root.getJSONObject("config")
            if (r == null) {
                config.put("provider", JSONObject.NULL)
            } else {
                config.put("provider", r.provider)
            }
            config.put("requestedEnableCpuMemArena", false)
            // Snapshot never captures; it only serializes already-recorded events.
            root.put("heapEvents", heapRecorder.eventsSnapshot())
            root.put("heapTelemetryErrorClass", heapRecorder.errorClass ?: JSONObject.NULL)
            return root
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
            processStartTicks = terminalProcessStartTicks(state.pid, readProcessStat(state.pid))

            // ---- 2. exclusive reservation BEFORE any Activity/native ----------
            state = state.copy(phase = "reserve")
            val root = targetContext.filesDir.canonicalFile
            val dir = child(root, "w3-qwen-terminal")
            if (!dir.isDirectory) {
                if (dir.exists() || !dir.mkdirs()) throw IOException("cannot create report dir")
                if (Files.isSymbolicLink(dir.toPath())) throw IOException("report dir is symlink")
            }
            terminalAckFile = TerminalAckFile(dir, rid).apply { requireAbsent() }
            val base = child(dir, "report-$rid.json")
            for (f in arrayOf(base, File(base.path + ".new"), File(base.path + ".bak"))) {
                if (Files.isSymbolicLink(f.toPath()) || f.exists()) throw IOException("report/variant exists")
            }
            if (!base.createNewFile()) throw IOException("reserve failed")
            reportFile = base

            // First running report must land, else no native is attempted.
            state = state.copy(phase = "running")
            // Heap boundary probe BEFORE the first running write; a failure here
            // blocks all native dispatch and is recorded as the real error.
            captureHeap("initial-before-report")
            write(arenaSnapshot(terminal = false, status = "running"))

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
            write(arenaSnapshot(terminal = false, status = "running"))

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
            require(inputs.size == 65) { "expected 65 verified inputs, got ${inputs.size}" }
            require(inputs[25].id == "fleurs-en_us-1776") { "expected inputs[25].id fleurs-en_us-1776, got ${inputs[25].id}" }
            val selectedInputs = listOf(inputs[25])
            verifiedInputCount = inputs.size
            val rows = selectedInputs.map { FileRow(it) }
            state = state.copy(modelsVerified = verified, rows = rows)
            // Persist and a fresh foreground gate AFTER verification, before native.
            write(arenaSnapshot(terminal = false, status = "running"))
            if (!fgGate()) throw IOException("foreground gate failed after verify")

            // ---- 6b. accepted CPU arena-off allocator policy ------------------
            // Prepare exactly once in the framework-provided private root, set
            // phase allocator-policy, persist the binding receipt, then reverify
            // the exact receipt/file bytes and a fresh foreground gate AFTER
            // persistence and immediately before the native constructor. Any
            // failure here prevents all native calls; no fallback/retry.
            state = state.copy(phase = "allocator-policy")
            val policyRoot = targetContext.noBackupFilesDir.canonicalFile
            val arenaReceipt = QwenArenaPolicy.prepare(policyRoot)
            receipt = arenaReceipt
            write(arenaSnapshot(terminal = false, status = "running"))
            QwenArenaPolicy.verify(policyRoot, arenaReceipt)
            if (!fgGate()) throw IOException("foreground gate failed after policy verify")

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
                    numThreads = 2, debug = false, provider = arenaReceipt.provider
                ),
                decodingMethod = "greedy_search"
            )
            // Read the constructor start time before incrementing, then count
            // immediately before the actual native constructor call.
            captureHeap("constructor-before")
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
            // Heap boundary probe AFTER the actual constructor return, BEFORE the
            // running write that reports it.
            captureHeap("constructor-returned")
            write(arenaSnapshot(terminal = false, status = "running"))

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
                // Heap boundary probe AFTER the current case state is copied into the
                // bound row, BEFORE persisting the running report.
                captureHeap("core-persist")
                write(arenaSnapshot(terminal = false, status = "running"))
            }

            val runner = TwoStageCaseRunner(factory = factory, gate = { fgGate() }, persist = { corePersist(it) })

            for (i in selectedInputs.indices) {
                val input = selectedInputs[i]
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
                    write(arenaSnapshot(terminal = false, status = "running"))

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
                    // Preserve the returned CaseState actual first error into root state before
                    // any fallible heap capture/write; never overwrite an existing non-null root error.
                    if (state.errorClass == null && cs.errorClass != null) {
                        state = state.copy(errorClass = cs.errorClass, errorMessage = cs.errorMessage)
                    }
                    // Heap boundary probe AFTER the final case-state copy, BEFORE the write.
                    captureHeap("case-returned")
                    write(arenaSnapshot(terminal = false, status = "running"))

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
                // Heap boundary probe AFTER the actual release attempt/result is copied
                // into state. Cleanup=true so a telemetry fault never blocks the release
                // that already succeeded, nor the final report.
                captureHeap("recognizer-released", cleanup = true)
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
            // Heap boundary probe AFTER the teardown attempt/result, even when no
            // Activity was obtained. Cleanup=true so a telemetry fault never blocks
            // the final report.
            captureHeap("activity-torn-down", cleanup = true)

            // Terminal snapshot ONLY after the actual destroy ack.
            val allRowsCompleted = state.rows.all { it.status == "completed" }
            val noErrors = mainError == null && teardownError == null &&
                state.errorClass == null && state.wrapperPersistErrorClass == null &&
                state.recognizerReleaseErrorClass == null && state.teardownErrorClass == null &&
                heapRecorder.errorClass == null
            val allOk = noErrors && allRowsCompleted && state.constructorReturned &&
                state.recognizerReleaseSucceeded && state.recognizerReleaseAttempts == 1 &&
                state.teardownAck && state.rows.size == 1
            state = state.copy(phase = if (allOk) "terminal" else state.phase)

            var terminalAckAttempted = false
            var terminalAckReceived = false
            var terminalAckErrorClass: String? = null
            var terminalAckErrorMessage: String? = null
            var terminalReportSha256: String? = null

            // No report reservation => do not attempt final write.
            if (reportFile != null) {
                try {
                    val persisted = write(arenaSnapshot(terminal = true, status = if (allOk) "completed" else "failed"))
                    try {
                        terminalReportSha256 = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(persisted)
                            .joinToString("") { "%02x".format(it) }
                        val binding = TerminalObservationBinding(
                            runId = requireNotNull(state.runId) { "missing runId" },
                            sourceFingerprint = requireNotNull(state.sourceFingerprint) { "missing sourceFingerprint" },
                            pid = state.pid,
                            startTicks = requireNotNull(processStartTicks) { "missing processStartTicks" },
                            terminalReportSha256 = terminalReportSha256
                        )
                        terminalAckAttempted = true
                        waitForTerminalAck(requireNotNull(terminalAckFile) { "missing terminalAckFile" }, binding)
                        terminalAckReceived = true
                    } catch (t: Throwable) {
                        terminalAckErrorClass = t.javaClass.name
                        terminalAckErrorMessage = t.message
                    }
                } catch (t: Throwable) {
                    err(t)
                }
            }

            resultCode = if (allOk && terminalAckReceived) Activity.RESULT_OK else Activity.RESULT_CANCELED
            val bundle = Bundle()
            bundle.putBoolean("terminalAckAttempted", terminalAckAttempted)
            bundle.putBoolean("terminalAckReceived", terminalAckReceived)
            terminalAckErrorClass?.let { bundle.putString("terminalAckErrorClass", it) }
            terminalAckErrorMessage?.let { bundle.putString("terminalAckErrorMessage", it) }
            terminalReportSha256?.let { bundle.putString("terminalReportSha256", it) }
            finish(resultCode, bundle)
        }
    }
}

class HeapBoundaryRecorder(
    private val nowNanos: () -> Long,
    private val heapSizeBytes: () -> Long,
    private val heapAllocatedBytes: () -> Long,
    private val heapFreeBytes: () -> Long
) {
    var errorClass: String? = null
        private set
    private var firstFault: Throwable? = null
    private val events = mutableListOf<JSONObject>()
    private var lastElapsed: Long? = null

    fun requireHealthy() {
        if (firstFault != null) throw firstFault!!
    }

    fun capture(label: String, activeCaseIndex: Int?, caseState: CaseState?, cleanup: Boolean = false) {
        if (firstFault != null && !cleanup) throw firstFault!!

        if (events.size >= 128) {
            val fault = IllegalStateException("event-limit-exceeded")
            if (firstFault == null) { firstFault = fault; errorClass = fault.javaClass.name }
            if (!cleanup) throw fault
            return
        }

        val seq = events.size
        val event = JSONObject()
        event.put("seq", seq)
        event.put("label", label)

        var elapsed: Long? = null
        var size: Long? = null
        var allocated: Long? = null
        var free: Long? = null
        var samplingFault: Throwable? = null

        try {
            val e = nowNanos()
            require(e >= 0) { "elapsedRealtimeNanos must be nonnegative" }
            if (lastElapsed != null) require(e >= lastElapsed!!) { "elapsedRealtimeNanos must be nondecreasing" }
            elapsed = e
        } catch (t: Throwable) { samplingFault = t }

        if (samplingFault == null) {
            try {
                val s = heapSizeBytes()
                require(s >= 0) { "nativeHeapSizeBytes must be nonnegative" }
                size = s
            } catch (t: Throwable) { samplingFault = t }
        }
        if (samplingFault == null) {
            try {
                val a = heapAllocatedBytes()
                require(a >= 0) { "nativeHeapAllocatedBytes must be nonnegative" }
                allocated = a
            } catch (t: Throwable) { samplingFault = t }
        }
        if (samplingFault == null) {
            try {
                val f = heapFreeBytes()
                require(f >= 0) { "nativeHeapFreeBytes must be nonnegative" }
                free = f
            } catch (t: Throwable) { samplingFault = t }
        }

        if (elapsed != null) { event.put("elapsedRealtimeNanos", elapsed); lastElapsed = elapsed }
        else event.put("elapsedRealtimeNanos", JSONObject.NULL)
        if (size != null) event.put("nativeHeapSizeBytes", size)
        else event.put("nativeHeapSizeBytes", JSONObject.NULL)
        if (allocated != null) event.put("nativeHeapAllocatedBytes", allocated)
        else event.put("nativeHeapAllocatedBytes", JSONObject.NULL)
        if (free != null) event.put("nativeHeapFreeBytes", free)
        else event.put("nativeHeapFreeBytes", JSONObject.NULL)
        event.put("activeCaseIndex", activeCaseIndex ?: JSONObject.NULL)
        event.put("casePhase", caseState?.phase ?: JSONObject.NULL)

        val stagesArr = JSONArray()
        if (caseState != null) {
            for (s in caseState.stages) {
                val js = JSONObject()
                js.put("language", s.language)
                js.put("status", s.status)
                js.put("createAttempts", s.createAttempts)
                js.put("decodeAttempts", s.decodeAttempts)
                js.put("resultAttempts", s.resultAttempts)
                js.put("releaseAttempts", s.releaseAttempts)
                js.put("releaseSucceeded", s.releaseSucceeded)
                stagesArr.put(js)
            }
        }
        event.put("stages", stagesArr)

        if (samplingFault != null) {
            event.put("errorClass", samplingFault!!.javaClass.name)
            if (firstFault == null) { firstFault = samplingFault; errorClass = samplingFault!!.javaClass.name }
        } else {
            event.put("errorClass", JSONObject.NULL)
        }

        events.add(event)

        if (samplingFault != null && !cleanup) throw samplingFault!!
    }

    fun eventsSnapshot(): JSONArray {
        val arr = JSONArray()
        for (e in events) arr.put(JSONObject(e.toString()))
        return arr
    }
}

private fun terminalReadProcessStat(pid: Int): ByteArray {
    if (pid <= 0) throw IllegalArgumentException("invalid pid: $pid")
    val path = "/proc/$pid/stat"
    val fis = java.io.FileInputStream(path)
    try {
        val buf = ByteArray(65537)
        var off = 0
        while (off < buf.size) {
            val n = fis.read(buf, off, buf.size - off)
            if (n < 0) break
            if (n == 0) throw java.io.IOException("zero-progress read on $path")
            off += n
        }
        return buf.copyOf(off)
    } finally {
        fis.close()
    }
}

private fun terminalProcessStartTicks(pid: Int, raw: ByteArray): String {
    if (pid <= 0) throw IllegalArgumentException("invalid pid: $pid")
    val n = raw.size
    if (n < 1 || n > 65536) throw IllegalArgumentException("raw size out of range: $n")
    for (b in raw) {
        val v = b.toInt() and 0xFF
        if (v == 0) throw IllegalArgumentException("NUL byte in stat")
        if (v > 127) throw IllegalArgumentException("non-ASCII byte in stat: $v")
    }
    val text = String(raw, java.nio.charset.StandardCharsets.US_ASCII)
    val body = if (text.endsWith("\n")) text.substring(0, text.length - 1) else text
    val prefix = "$pid ("
    if (!body.startsWith(prefix)) throw IllegalArgumentException("pid prefix mismatch or malformed: expected $prefix")
    val sep = ") "
    val idx = body.lastIndexOf(sep)
    if (idx < 0) throw IllegalArgumentException("missing ') ' delimiter")
    val tail = body.substring(idx + sep.length)
    val fields = run {
        val list = mutableListOf<String>()
        var i = 0
        while (i < tail.length) {
            while (i < tail.length && tail[i].isWhitespace()) i++
            if (i >= tail.length) break
            val start = i
            while (i < tail.length && !tail[i].isWhitespace()) i++
            list.add(tail.substring(start, i))
        }
        list
    }
    if (fields.size < 20) throw IllegalArgumentException("truncated stat: ${fields.size} fields")
    val state = fields[0]
    if (state.length != 1) throw IllegalArgumentException("malformed state: $state")
    val st = state[0]
    if (st !in 'A'..'Z' && st !in 'a'..'z') throw IllegalArgumentException("malformed state: $state")
    val ticksStr = fields[19]
    if (ticksStr.isEmpty()) throw IllegalArgumentException("empty start_ticks")
    for (c in ticksStr) {
        if (c < '0' || c > '9') throw IllegalArgumentException("non-digit start_ticks: $ticksStr")
    }
    if (ticksStr[0] == '0') throw IllegalArgumentException("leading-zero start_ticks: $ticksStr")
    val ticks = ticksStr.toLongOrNull() ?: throw IllegalArgumentException("start_ticks overflow: $ticksStr")
    if (ticks <= 0) throw IllegalArgumentException("non-positive start_ticks: $ticks")
    return ticksStr
}

private fun terminalAwaitAck(
    file: io.ondeviceai.acceptance.terminal.TerminalAckFile,
    binding: io.ondeviceai.acceptance.terminal.TerminalObservationBinding
) {
    io.ondeviceai.acceptance.terminal.TerminalObservationBarrier(binding).awaitAck(
        readAck = { file.readOrNull() },
        nowMillis = { android.os.SystemClock.elapsedRealtime() },
        sleepMillis = { Thread.sleep(it) },
        timeoutMillis = 30000
    )
}
