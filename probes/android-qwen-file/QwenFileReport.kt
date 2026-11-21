package io.ondeviceai.acceptance.qwenfile

import io.ondeviceai.acceptance.qwenload.ModelSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Public DTO describing a single acceptance case row.
 *
 * Nullable fields stay nullable so the serializer can distinguish "absent"
 * (emitted as [JSONObject.NULL]) from default/empty values.
 */
data class FileRow(
    val input: InputCase,
    val status: String = "planned",
    val caseState: CaseState? = null,
    val caseNanos: Long? = null
)

/**
 * Public DTO holding the full run state for one Qwen file-run report snapshot.
 *
 * All fields default to match the caller/tests; [ModelSpec] comes from the
 * accepted verifier module. The wrapper owns lifecycle, status and `terminal`.
 */
data class FileRunState(
    val runId: String? = null,
    val sourceFingerprint: String? = null,
    val pid: Int = 0,
    val phase: String = "initial",
    val rows: List<FileRow> = emptyList(),
    val modelsVerified: List<ModelSpec> = emptyList(),
    val actualConstructors: Int = 0,
    val constructorReturned: Boolean = false,
    val loadNanos: Long? = null,
    val recognizerReleaseAttempts: Int = 0,
    val recognizerReleaseSucceeded: Boolean = false,
    val recognizerReleaseNanos: Long? = null,
    val recognizerReleaseErrorClass: String? = null,
    val activeCaseIndex: Int? = null,
    val caseStartedElapsedRealtimeNanos: Long? = null,
    val errorClass: String? = null,
    val errorMessage: String? = null,
    val wrapperPersistErrorClass: String? = null,
    val teardownErrorClass: String? = null,
    val resumed: Boolean = false,
    val paused: Boolean = false,
    val destroyed: Boolean = false,
    val teardownAck: Boolean = false
)

/**
 * Pure JSON serializer for the Qwen acceptance file-run report.
 *
 * No Android APIs, no frameworks, no reflection, no retries, no IO/native/
 * persistence/gates/validation. The wrapper owns lifecycle, status and
 * `terminal`; this object never infers or overrides them. Every snapshot
 * returns a fresh [JSONObject] and never mutates the supplied input state.
 *
 * Nullable text/times/errors are emitted as [JSONObject.NULL] explicitly so
 * callers can distinguish "absent" from "" / false / 0. Long timings are
 * stored as longs (never promoted to Double) to preserve values above 2^53.
 */
object QwenFileReport {

    fun snapshot(
        state: FileRunState,
        terminal: Boolean = false,
        status: String = "running"
    ): JSONObject {
        val root = JSONObject()

        root.put("schemaVersion", 1)
        putOptString(root, "runId", state.runId)
        putOptString(root, "sourceFingerprint", state.sourceFingerprint)
        root.put("inputManifestSHA256", QwenFileInputs.MANIFEST_SHA256)
        root.put("pid", state.pid)
        root.put("terminal", terminal)
        root.put("status", status)
        root.put("phase", state.phase)

        val foreground = JSONObject()
        foreground.put("resumed", state.resumed)
        foreground.put("paused", state.paused)
        foreground.put("destroyed", state.destroyed)
        foreground.put("teardownAck", state.teardownAck)
        root.put("foreground", foreground)

        root.put("actualConstructors", state.actualConstructors)
        root.put("constructorReturned", state.constructorReturned)
        putOptLong(root, "loadNanos", state.loadNanos)

        root.put("recognizerReleaseAttempts", state.recognizerReleaseAttempts)
        root.put("recognizerReleaseSucceeded", state.recognizerReleaseSucceeded)
        putOptLong(root, "recognizerReleaseNanos", state.recognizerReleaseNanos)
        putOptString(root, "recognizerReleaseErrorClass", state.recognizerReleaseErrorClass)

        putOptInt(root, "activeCaseIndex", state.activeCaseIndex)
        putOptLong(root, "caseStartedElapsedRealtimeNanos", state.caseStartedElapsedRealtimeNanos)

        putOptString(root, "errorClass", state.errorClass)
        putOptString(root, "errorMessage", state.errorMessage)
        putOptString(root, "wrapperPersistErrorClass", state.wrapperPersistErrorClass)
        putOptString(root, "teardownErrorClass", state.teardownErrorClass)

        // modelsVerified: explicit array; never substituted or invented.
        val models = JSONArray()
        for (spec in state.modelsVerified) {
            models.put(serializeModel(spec))
        }
        root.put("modelsVerified", models)

        // rows
        val rows = JSONArray()
        var streamsCreated = 0
        var nativeDecodeCalls = 0
        for (row in state.rows) {
            rows.put(serializeRow(row))
            val cs = row.caseState
            if (cs != null) {
                for (stage in cs.stages) {
                    if (stage.createReturned) streamsCreated += 1
                    nativeDecodeCalls += stage.decodeAttempts
                }
            }
        }
        root.put("rows", rows)

        root.put("streamsCreated", streamsCreated)
        root.put("nativeDecodeCalls", nativeDecodeCalls)
        root.put("microphoneStarts", 0)
        root.put("networkRequests", 0)

        root.put("config", buildConfig())

        return root
    }

    // ---- helpers -----------------------------------------------------------

    private fun serializeModel(spec: ModelSpec): JSONObject {
        val o = JSONObject()
        o.put("relativePath", spec.relativePath)
        o.put("bytes", spec.bytes)
        o.put("sha256", spec.sha256)
        return o
    }

    private fun serializeRow(row: FileRow): JSONObject {
        val o = JSONObject()
        val input = row.input
        o.put("id", input.id)
        o.put("locale", input.locale)
        o.put("cohort", input.cohort)
        o.put("samples", input.samples)
        o.put("pcmSha256", input.sha256)
        o.put("reference", input.reference) // preserve raw text exactly
        o.put("status", row.status)
        putOptLong(o, "caseNanos", row.caseNanos)
        if (row.caseState == null) {
            o.put("caseState", JSONObject.NULL)
        } else {
            o.put("caseState", serializeCaseState(row.caseState))
        }
        return o
    }

    private fun serializeCaseState(cs: CaseState): JSONObject {
        val o = JSONObject()
        o.put("status", cs.status)
        o.put("phase", cs.phase)
        putOptBoolean(o, "admitted", cs.admitted)
        putOptString(o, "rawText", cs.rawText)
        val stages = JSONArray()
        for (stage in cs.stages) {
            stages.put(serializeStage(stage))
        }
        o.put("stages", stages)
        putOptString(o, "errorClass", cs.errorClass)
        putOptString(o, "errorMessage", cs.errorMessage)
        putOptString(o, "persistErrorClass", cs.persistErrorClass)
        return o
    }

    private fun serializeStage(stage: StageState): JSONObject {
        val o = JSONObject()
        o.put("language", stage.language)
        o.put("status", stage.status)
        o.put("createAttempts", stage.createAttempts)
        o.put("createReturned", stage.createReturned)
        o.put("optionAttempts", stage.optionAttempts)
        o.put("acceptAttempts", stage.acceptAttempts)
        o.put("decodeAttempts", stage.decodeAttempts)
        o.put("resultAttempts", stage.resultAttempts)
        o.put("releaseAttempts", stage.releaseAttempts)
        o.put("releaseSucceeded", stage.releaseSucceeded)
        putOptString(o, "rawText", stage.rawText)
        putOptLong(o, "decodeNanos", stage.decodeNanos)
        putOptString(o, "errorClass", stage.errorClass)
        putOptString(o, "releaseErrorClass", stage.releaseErrorClass)
        return o
    }

    private fun buildConfig(): JSONObject {
        val c = JSONObject()
        c.put("featureSampleRate", 16000)
        c.put("featureDim", 128)
        c.put("decodingMethod", "greedy_search")
        c.put("provider", "cpu")
        c.put("numThreads", 2)
        c.put("debug", false)
        c.put("maxTotalLen", 512)
        c.put("maxNewTokens", 128)
        c.put("temperature", 1e-6)
        c.put("topP", 0.8)
        c.put("seed", 42)
        c.put("hotwords", "")
        c.put("modelType", "")
        c.put("tokens", "")
        c.put("convFrontend", "conv_frontend.onnx")
        c.put("encoder", "encoder.int8.onnx")
        c.put("decoder", "decoder.int8.onnx")
        c.put("tokenizer", "modelRoot/tokenizer directory")
        c.put("languageMode", "admit-auto-then-forced")
        return c
    }

    // ---- null-safe putters (explicit JSONObject.NULL) ----------------------

    private fun putOptString(o: JSONObject, key: String, value: String?) {
        if (value == null) o.put(key, JSONObject.NULL) else o.put(key, value)
    }

    private fun putOptLong(o: JSONObject, key: String, value: Long?) {
        if (value == null) o.put(key, JSONObject.NULL) else o.put(key, value)
    }

    private fun putOptInt(o: JSONObject, key: String, value: Int?) {
        if (value == null) o.put(key, JSONObject.NULL) else o.put(key, value)
    }

    private fun putOptBoolean(o: JSONObject, key: String, value: Boolean?) {
        if (value == null) o.put(key, JSONObject.NULL) else o.put(key, value)
    }
}
