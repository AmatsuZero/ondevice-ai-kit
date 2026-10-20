package io.ondeviceai.binding

/** The capability whose native backend is being queried or invoked. */
enum class SpeechBridgeCapability { TTS, ASR }

/** A strict backend choice. Adapters must reject custom Swift conformers they do not recognize. */
sealed interface SpeechBridgeSelection {
    data object Default : SpeechBridgeSelection
    data class Explicit(val backendId: String) : SpeechBridgeSelection {
        init { require(backendId.isNotBlank()) }
    }
}

/** Input kinds are metadata only; the adapter still performs admission for every request. */
enum class SpeechBridgeInputKind { MICROPHONE, AUDIO_FILE }

sealed interface SpeechBridgeInput {
    data object Microphone : SpeechBridgeInput
    data class AudioFile(val reference: String) : SpeechBridgeInput {
        init { require(reference.isNotBlank()) }
    }
}

data class SpeechBridgeBackend(
    val capability: SpeechBridgeCapability,
    val backendId: String,
    val availability: BridgeAvailability,
    val supportedInputs: List<SpeechBridgeInputKind>,
    val offline: Boolean,
) {
    init {
        require(backendId.isNotBlank())
        require(availability.backendId == backendId)
    }
}

data class SpeechBridgeTtsRequest(val text: String, val locale: String, val voiceId: String? = null)
data class SpeechBridgeAsrRequest(val locale: String, val input: SpeechBridgeInput)

data class SpeechBridgeTtsResult(val backendId: String, val voiceId: String?)
data class SpeechBridgeAsrResult(val backendId: String, val transcript: String, val confidence: Float?)

enum class SpeechBridgeFailureCode {
    CLOSED,
    BUSY,
    INVALID_INPUT,
    UNSUPPORTED_INPUT,
    BACKEND_NOT_REGISTERED,
    BACKEND_UNAVAILABLE,
    TIMED_OUT,
    NO_RESULT,
    PLATFORM,
}

/** Platform diagnostics are intentionally reduced to stable domain/code fields. */
data class SpeechBridgeFailure(
    val code: SpeechBridgeFailureCode,
    val reason: String?,
    val domain: String? = null,
    val platformCode: Int? = null,
) {
    init {
        if (code == SpeechBridgeFailureCode.PLATFORM) {
            require(reason == null)
            require(!domain.isNullOrBlank())
            require(platformCode != null)
        } else {
            require(domain == null && platformCode == null)
        }
    }
}

interface SpeechBridgeBackendsCallback {
    fun onResult(backends: List<SpeechBridgeBackend>)
    fun onFailure(failure: SpeechBridgeFailure)
    fun onCancelled()
}

interface SpeechBridgeTtsCallback {
    fun onResult(synthesis: SpeechBridgeTtsResult)
    fun onFailure(failure: SpeechBridgeFailure)
    fun onCancelled()
}

interface SpeechBridgeAsrCallback {
    fun onResult(output: SpeechBridgeAsrResult)
    fun onFailure(failure: SpeechBridgeFailure)
    fun onCancelled()
}

interface SpeechBridgeCloseCallback {
    fun onClosed()
    fun onFailure(failure: SpeechBridgeFailure)
}

/** Optional microphone control. Finishing input waits for recognition; cancel discards it. */
interface SpeechBridgeMicrophoneTask : BridgeTask {
    fun finishInput()
}

/**
 * Calls are admitted on the platform UI/main dispatcher. A terminal callback may happen
 * synchronously and implementations deliver every callback on that same dispatcher.
 */
interface SpeechBridge {
    fun queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback): BridgeTask
    fun speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback): BridgeTask
    fun recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback): BridgeTask
    fun close(callback: SpeechBridgeCloseCallback)
}

private object CompletedSpeechBridgeTask : BridgeTask {
    override fun cancel() = Unit
}

private fun invalidRequest(request: SpeechBridgeTtsRequest): SpeechBridgeFailure? =
    if (request.text.isBlank() || request.locale.isBlank()) {
        SpeechBridgeFailure(SpeechBridgeFailureCode.INVALID_INPUT, "invalid speech request")
    } else null

private fun unavailableFailure(reason: String): SpeechBridgeFailure =
    SpeechBridgeFailure(SpeechBridgeFailureCode.BACKEND_UNAVAILABLE, reason)

/** Compatibility bridge used by hosts that do not include an optional speech module. */
class UnavailableSpeechBridge(private val reason: String = "speech module is not linked") : SpeechBridge {
    private var closed = false

    override fun queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback): BridgeTask {
        if (closed) callback.onFailure(SpeechBridgeFailure(SpeechBridgeFailureCode.CLOSED, "speech bridge is closed"))
        else callback.onFailure(unavailableFailure(reason))
        return CompletedSpeechBridgeTask
    }

    override fun speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback): BridgeTask {
        if (closed) callback.onFailure(SpeechBridgeFailure(SpeechBridgeFailureCode.CLOSED, "speech bridge is closed"))
        else callback.onFailure(invalidRequest(request) ?: unavailableFailure(reason))
        return CompletedSpeechBridgeTask
    }

    override fun recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback): BridgeTask {
        if (closed) callback.onFailure(SpeechBridgeFailure(SpeechBridgeFailureCode.CLOSED, "speech bridge is closed"))
        else if (request.locale.isBlank()) callback.onFailure(SpeechBridgeFailure(SpeechBridgeFailureCode.INVALID_INPUT, "invalid speech request"))
        else callback.onFailure(unavailableFailure(reason))
        return CompletedSpeechBridgeTask
    }

    override fun close(callback: SpeechBridgeCloseCallback) {
        closed = true
        callback.onClosed()
    }
}
