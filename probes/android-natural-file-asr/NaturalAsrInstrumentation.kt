package io.ondeviceai.demo

import android.app.Instrumentation
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import io.ondeviceai.sherpaasr.SherpaWhisperModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Public natural-speech ASR measurements against three real LibriSpeech snippets.
 * No microphone, no media persistence, no RSS sampling, no CER/WER. Public transcripts
 * are intentionally recorded. Backend identity and supplied fingerprints are parent-attested,
 * not internally measured or claimed to share source with the ordinary app.
 */
class NaturalAsrInstrumentation : Instrumentation() {
    private lateinit var reportFile: AtomicFile
    private lateinit var report: JSONObject
    private val rows = JSONArray()
    private val expectedSha = LinkedHashMap<String, String>()
    private lateinit var sourceFingerprint: String
    private lateinit var installedAppSha256: String
    private var resourceWindow: Boolean = false
    private var locale: String = "en-US"

    private data class Sample(val id: String, val reference: String)

    /** One validated, owned asset snapshot. */
    private data class Loaded(
        val id: String,
        val raw: ByteArray,
        val sha: String,
        val input: FloatArray,
        val retained: FloatArray
    )

    /** Half-open slice [start, end) of a clip's samples, honouring the production 160000 bound. */
    private data class Slice(val start: Int, val end: Int)

    private val SAMPLES = listOf(
        Sample("1272-128104-0000",
            "MISTER QUILTER IS THE APOSTLE OF THE MIDDLE CLASSES AND WE ARE GLAD TO WELCOME HIS GOSPEL"),
        Sample("1272-128104-0001",
            "NOR IS MISTER QUILTER'S MANNER LESS INTERESTING THAN HIS MATTER"),
        Sample("1272-128104-0002",
            "HE TELLS US THAT AT THIS FESTIVE SEASON OF THE YEAR WITH CHRISTMAS AND ROAST BEEF " +
                "LOOMING BEFORE US SIMILES DRAWN FROM EATING AND ITS RESULTS OCCUR MOST READILY TO THE MIND")
    )

    /** Fixed, parent-attested raw sample counts for the three clips (no tail drop, no resample). */
    private val EXPECTED_SAMPLES = listOf(93680, 77040, 199760)

    /** Production transcribe bound: 10 s at 16 kHz = 160000 samples. Not relaxed. */
    private val MAX_TRANSCRIBE_SAMPLES = 160000

    /**
     * Opt-in resource observation window (enabled only via `-e resourceWindow true`).
     * A parent observer samples RSS/threads/CPU ticks while the real backend is live.
     * No observer is implemented here; only honest holds and their requested/actual
     * durations are recorded. Disabled by default; the default report is byte-equivalent.
     */
    private val RESOURCE_OBSERVER_BIND_HOLD_MS = 5000L
    private val RESOURCE_REQUEST_HOLD_MS = 10000L

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = requireNotNull(arguments) { "arguments required" }
        val runId = requireNotNull(args.getString("runId")) { "runId required" }
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "runId unsafe" }

        sourceFingerprint = requireNotNull(args.getString("sourceFingerprint")) { "sourceFingerprint required" }
        require(sourceFingerprint.matches(Regex("[0-9a-f]{64}"))) { "sourceFingerprint must be 64 hex" }
        installedAppSha256 = requireNotNull(args.getString("installedAppSha256")) { "installedAppSha256 required" }
        require(installedAppSha256.matches(Regex("[0-9a-f]{64}"))) { "installedAppSha256 must be 64 hex" }

        // Opt-in resource observation window. Absent => disabled (default, byte-equivalent).
        // Only the exact literal "true" enables it; any other value is rejected rather than
        // silently treated as enabled.
        val resourceWindowArg = args.getString("resourceWindow")
        resourceWindow = when (resourceWindowArg) {
            null -> false
            "true" -> true
            else -> throw IllegalArgumentException(
                "resourceWindow must be 'true' or absent, got: '$resourceWindowArg'")
        }

        // Opt-in locale. Absent => en-US (default, byte-equivalent report). Only exact "en-US"
        // or "zh-CN" accepted; any other value is rejected here, before the report directory is
        // reserved or any model/native work begins.
        val localeArg = args.getString("locale")
        val localeProvided = localeArg != null
        locale = when (localeArg) {
            null -> "en-US"
            "en-US" -> "en-US"
            "zh-CN" -> "zh-CN"
            else -> throw IllegalArgumentException(
                "locale must be 'en-US' or 'zh-CN' or absent, got: '$localeArg'")
        }

        for (sample in SAMPLES) {
            val key = "inputSha_${sample.id}"
            val sha = requireNotNull(args.getString(key)) { "$key required" }
            require(sha.matches(Regex("[0-9a-f]{64}"))) { "$key must be 64 lower hex" }
            expectedSha[sample.id] = sha
        }

        // Preflight: reject report directory / file / .new / .bak symbolic links (incl. dangling),
        // and ensure the directory is an exact child of filesDir (no parent symlink escape).
        // A failed preflight throws here, before any model is loaded in onStart.
        val parent = targetContext.filesDir
        val reportsName = "odai-asr-natural-reports"
        val directory = File(parent, reportsName)
        check(directory.isDirectory || directory.mkdir()) { "Cannot reserve report directory" }
        check(!isSymlink(directory)) { "Report directory is a symbolic link" }
        check(directory.canonicalPath == parent.canonicalPath + File.separator + reportsName) {
            "Report directory path escape"
        }
        val file = File(directory, "$runId.json")
        val newFile = File(file.path + ".new")
        val bakFile = File(file.path + ".bak")
        check(!isSymlink(file) && !isSymlink(newFile) && !isSymlink(bakFile)) {
            "Report path is a symbolic link"
        }
        check(!file.exists() && !newFile.exists() && !bakFile.exists()) { "Run ID already reserved" }
        check(file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { "Path escape" }
        check(file.createNewFile()) { "Run ID already reserved" }
        reportFile = AtomicFile(file)

        report = JSONObject().put("schemaVersion", 1).put("kind", "android-asr-natural")
            .put("runId", runId).put("status", "running").put("terminal", false)
            .put("backend", "sherpa.onnx.whisper").put("pid", Process.myPid())
            .put("sourceFingerprint", sourceFingerprint)
            .put("installedAppSha256", installedAppSha256)
            .put("microphoneStarts", 0).put("audioPersisted", false).put("transcriptPersisted", true)
            .put("expectedRows", SAMPLES.size).put("rows", rows)
            .put("plannedRequests", 4).put("completedRequests", 0)
            .put("finalCloseSucceeded", false)
        if (resourceWindow) {
            report.put("resourceWindow", true)
        }
        if (localeProvided) {
            report.put("locale", locale)
        }
        persist()
        start()
    }

    override fun onStart() {
        var backend: SherpaAsrBackend? = null
        var closeAttempted = false
        var workCompleted = false
        var resourceHoldActualTotalMs = 0L
        var resourceHoldInterrupted = false
        val ownedBytes = mutableListOf<ByteArray>()
        val ownedWaveforms = mutableListOf<FloatArray>()
        val ownedOriginals = mutableListOf<FloatArray>()
        val ownedSlices = mutableListOf<FloatArray>()
        try {
            // Validate all raw assets BEFORE any model load. Own the raw byte arrays and their
            // derived snapshot arrays IMMEDIATELY after each read, before validation; any
            // future/unprocessed raw asset is therefore cleared on error in the finally block.
            val inputs = SAMPLES.map { sample ->
                require(sample.id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "unsafe id ${sample.id}" }
                val raw = context.assets.open("${sample.id}.pcm").use { it.readBytes() }
                ownedBytes.add(raw)
                require(raw.isNotEmpty()) { "empty asset ${sample.id}" }
                require(raw.size % 2 == 0) { "odd byte count ${sample.id}" }
                require(raw.size <= 30 * 16000 * 2) { "asset too large ${sample.id}" }
                val sha = MessageDigest.getInstance("SHA-256").digest(raw)
                    .joinToString("") { "%02x".format(it) }
                require(sha == expectedSha[sample.id]) { "sha mismatch ${sample.id}" }
                val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val input = FloatArray(buffer.remaining()) { buffer.get() / 32768f }
                val retained = input.copyOf()
                ownedWaveforms.add(input)
                ownedOriginals.add(retained)
                Loaded(sample.id, raw, sha, input, retained)
            }

            // Validation before model: actual source raw bytes/samples must match the fixed three
            // expected lengths, then the per-clip slice math must prove full coverage, no gap/overlap,
            // and every slice within the production 160000-sample (10 s) transcribe bound.
            inputs.forEachIndexed { index, loaded ->
                val expectedSamples = EXPECTED_SAMPLES[index]
                require(loaded.raw.size == expectedSamples * 2) {
                    "unexpected raw byte length ${loaded.id}: ${loaded.raw.size} != ${expectedSamples * 2}"
                }
                require(loaded.input.size == expectedSamples) {
                    "unexpected sample count ${loaded.id}: ${loaded.input.size} != $expectedSamples"
                }
                computeSlices(loaded.input.size, loaded.id).let { slices ->
                    var cursor = 0
                    for (slice in slices) {
                        require(slice.start == cursor) { "slice gap/overlap ${loaded.id}" }
                        require(slice.end > slice.start) { "empty slice ${loaded.id}" }
                        require(slice.end - slice.start <= MAX_TRANSCRIBE_SAMPLES) {
                            "slice ${loaded.id} exceeds ${MAX_TRANSCRIBE_SAMPLES} bound: ${slice.end - slice.start}"
                        }
                        cursor = slice.end
                    }
                    require(cursor == loaded.input.size) { "slice coverage incomplete ${loaded.id}" }
                }
            }

            // Opt-in resource observation window: before any model/backend work, durably publish a
            // nonterminal report (phase=resource-observer-bind) carrying the already-present pid and
            // resourceWindow=true, then hold the instrumentation worker so an external parent observer
            // can attach its RSS/thread/CPU-ticks sampler. No observer is implemented here.
            if (resourceWindow) {
                report.put("phase", "resource-observer-bind")
                persist()
                val bindStart = SystemClock.elapsedRealtimeNanos()
                var bindInterrupted = false
                try {
                    Thread.sleep(RESOURCE_OBSERVER_BIND_HOLD_MS)
                } catch (e: InterruptedException) {
                    bindInterrupted = true
                    Thread.currentThread().interrupt()
                }
                val bindActualMs = (SystemClock.elapsedRealtimeNanos() - bindStart) / 1_000_000L
                resourceHoldActualTotalMs += bindActualMs
                if (bindInterrupted) resourceHoldInterrupted = true
                report.put("resourceObserverBindRequestedMs", RESOURCE_OBSERVER_BIND_HOLD_MS)
                report.put("resourceObserverBindActualMs", bindActualMs)
                report.put("phase", "resource-observer-window")
            }

            // Verify existing installed model only; do not install or repair a missing model.
            val model = SherpaWhisperModel(
                File(targetContext.noBackupFilesDir, "sherpa-whisper-tiny-65176e2d-vad9e2449e1"))
            model.verify()
            val recognizer = SherpaAsrBackend(targetContext, model)
            backend = recognizer

            var completedRequests = 0
            runBlocking {
                SAMPLES.forEachIndexed { index, sample ->
                    val loaded = inputs[index]
                    val slices = computeSlices(loaded.input.size, loaded.id)
                    val segments = JSONArray()
                    val segHypotheses = mutableListOf<String>()
                    var rowStarted = 0L
                    var rowEnded = 0L
                    var rowBackendId = ""

                    slices.forEachIndexed { si, slice ->
                        // Copy the sub-range; the original buffers are never mutated.
                        val segInput = loaded.input.copyOfRange(slice.start, slice.end)
                        val segRetained = segInput.copyOf()
                        ownedSlices.add(segInput)
                        ownedSlices.add(segRetained)
                        require(segInput.size <= MAX_TRANSCRIBE_SAMPLES) {
                            "segment ${sample.id}#$si exceeds bound"
                        }

                        val started = SystemClock.elapsedRealtimeNanos()
                        var ended = 0L
                        val result = try {
                            recognizer.transcribe(segInput, locale)
                        } finally {
                            ended = SystemClock.elapsedRealtimeNanos()
                        }
                        check(result.backendId == "sherpa.onnx.whisper") { "backend id mismatch" }
                        val segUnchanged = segInput.contentEquals(segRetained)

                        completedRequests++
                        if (si == 0) rowStarted = started
                        rowEnded = ended
                        if (si == 0) rowBackendId = result.backendId
                        segHypotheses.add(result.transcript)

                        val seg = JSONObject()
                            .put("index", si)
                            .put("offsetSamples", slice.start)
                            .put("samples", segInput.size)
                            .put("startedElapsedNs", started)
                            .put("endedElapsedNs", ended)
                            .put("durationNs", ended - started)
                            .put("backendId", result.backendId)
                            .put("hypothesis", result.transcript)
                            .put("callerInputUnchanged", segUnchanged)
                        // After a successful recognition request only, hold the instrumentation worker
                        // so a parent observer can sample while the backend is still live. The hold is
                        // recorded separately from the inference durationNs above (which excludes it).
                        if (resourceWindow) {
                            val holdStart = SystemClock.elapsedRealtimeNanos()
                            var holdInterrupted = false
                            try {
                                Thread.sleep(RESOURCE_REQUEST_HOLD_MS)
                            } catch (e: InterruptedException) {
                                holdInterrupted = true
                                Thread.currentThread().interrupt()
                            }
                            val holdActualMs =
                                (SystemClock.elapsedRealtimeNanos() - holdStart) / 1_000_000L
                            resourceHoldActualTotalMs += holdActualMs
                            if (holdInterrupted) resourceHoldInterrupted = true
                            seg.put("requestedResourceHoldMs", RESOURCE_REQUEST_HOLD_MS)
                            seg.put("actualResourceHoldMs", holdActualMs)
                        }
                        segments.put(seg)

                        report.put("completedRequests", completedRequests)
                        // Preserve the caller-input immutability guarantee for the segment slice.
                        check(segUnchanged) { "caller input mutated ${sample.id} seg $si" }
                    }

                    val hypothesis = segHypotheses.joinToString(" ")
                    val wholeUnchanged = loaded.input.contentEquals(loaded.retained)
                    val row = JSONObject().put("id", sample.id)
                        .put("reference", sample.reference)
                        .put("hypothesis", hypothesis)
                        .put("inputSha256", loaded.sha).put("inputBytes", loaded.raw.size)
                        .put("sampleRate", 16000).put("samples", loaded.raw.size / 2)
                        .put("startedElapsedNs", rowStarted).put("endedElapsedNs", rowEnded)
                        .put("durationNs", rowEnded - rowStarted)
                        .put("backendId", rowBackendId)
                        .put("callerInputUnchanged", wholeUnchanged)
                        .put("segments", segments)
                    // Row durationNs is wall-clock time and may include observation idle (e.g. the
                    // between-segment resource hold); per-segment inference durationNs excludes it.
                    if (resourceWindow) {
                        row.put("durationIncludesResourceHold", true)
                    }
                    rows.put(row)
                    // Preserve the row before enforcing whole-clip caller-input immutability.
                    persist()
                    check(wholeUnchanged) { "caller input mutated ${sample.id}" }
                }
            }
            // All requests completed and caller input was unchanged for every clip and segment.
            workCompleted = true
        } catch (error: Throwable) {
            // Preserve the original exception class; cleanup errors are recorded separately.
            report.put("status", "failed").put("errorClass", error.javaClass.name)
        } finally {
            // ONE close path. Mark closeAttempted before calling; never retry on throw.
            if (backend != null && !closeAttempted) {
                closeAttempted = true
                try {
                    runBlocking { backend.close() }
                    report.put("finalCloseSucceeded", true)
                } catch (closeError: Throwable) {
                    report.put("finalCloseSucceeded", false)
                    report.put("cleanupErrorClass", closeError.javaClass.name)
                    if (report.optString("status") != "failed") {
                        report.put("status", "failed")
                    }
                }
            }
            // Do not mark finalCloseSucceeded true when no backend was ever created.
            // All slices and original buffers are owned and cleared even on transcribe throw.
            ownedBytes.forEach { it.fill(0) }
            ownedWaveforms.forEach { it.fill(0f) }
            ownedOriginals.forEach { it.fill(0f) }
            ownedSlices.forEach { it.fill(0f) }
            // Completed only after all requests, caller input unchanged, and close succeeded.
            if (workCompleted && report.optBoolean("finalCloseSucceeded")) {
                report.put("status", "completed")
            }
            report.put("terminal", true)
            // Report the resource-window hold plan/actual bound phase honestly. No resource metrics
            // are forged here; only requested vs actually elapsed hold durations are recorded.
            if (resourceWindow) {
                report.put("resourceWindowRequestedTotalHoldMs",
                    RESOURCE_OBSERVER_BIND_HOLD_MS +
                        RESOURCE_REQUEST_HOLD_MS * report.optInt("completedRequests"))
                report.put("resourceWindowActualHoldMs", resourceHoldActualTotalMs)
                if (resourceHoldInterrupted) report.put("resourceWindowInterrupted", true)
            }
        }

        // Never swallow the final persist and then report Binder success. Attempt a durable
        // terminal persist; on failure mark status failed / persistErrorClass and attempt a
        // failed-terminal persist once. If unwritable, report Binder failure retaining the prior
        // raw on disk (never claim success). Distinguish post-cleanup success from write success.
        var terminalPersisted = false
        try {
            persist()
            terminalPersisted = true
        } catch (persistError: Throwable) {
            report.put("status", "failed")
            report.put("persistErrorClass", persistError.javaClass.name)
            try {
                persist()
                terminalPersisted = true
            } catch (_: Throwable) {
                terminalPersisted = false
            }
        }
        report.put("finalPersistSucceeded", terminalPersisted)
        val binderStatus = if (terminalPersisted) report.getString("status") else "failed"
        // Always finish, even when persistence failed.
        finish(if (binderStatus == "completed") -1 else 1,
            Bundle().apply { putString("status", binderStatus) })
    }

    /**
     * Fixed chunking rule (chosen before decode): a clip with <= [MAX_TRANSCRIBE_SAMPLES] samples
     * is one entire slice; a longer clip is split at its integer midpoint into two equal slices.
     * Full coverage, no overlap, no gap. Third clip (199760) -> [0,99880), [99880,199760).
     */
    private fun computeSlices(samples: Int, id: String): List<Slice> {
        require(samples > 0) { "empty clip $id" }
        if (samples <= MAX_TRANSCRIBE_SAMPLES) {
            return listOf(Slice(0, samples))
        }
        val mid = samples / 2
        require(mid > 0 && samples - mid > 0) { "bad split $id" }
        return listOf(Slice(0, mid), Slice(mid, samples))
    }

    /** True iff the path exists and is a symbolic link (including a dangling one). */
    private fun isSymlink(path: File): Boolean {
        return try {
            val stat = Os.lstat(path.path)
            OsConstants.S_ISLNK(stat.st_mode)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) false else throw error
        }
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
}
