package io.ondeviceai.demo

import androidx.compose.runtime.*
import io.ondeviceai.binding.*

enum class SpeechPhase { IDLE, RUNNING, FINISHING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }
data class SpeechBackendState(
    val backends: List<SpeechBridgeBackend> = emptyList(),
    val refreshing: Boolean = false,
    val failure: SpeechBridgeFailure? = null,
)
data class SpeechState(
    val phase: SpeechPhase = SpeechPhase.IDLE,
    val tts: SpeechBackendState = SpeechBackendState(),
    val asr: SpeechBackendState = SpeechBackendState(),
    val ttsSelection: SpeechBridgeSelection = SpeechBridgeSelection.Default,
    val asrSelection: SpeechBridgeSelection = SpeechBridgeSelection.Default,
    val audioReference: String? = null,
    val audioDisplayName: String? = null,
    val ttsResult: SpeechBridgeTtsResult? = null,
    val asrResult: SpeechBridgeAsrResult? = null,
    val failure: SpeechBridgeFailure? = null,
    val closed: Boolean = false,
    val canFinishInput: Boolean = false,
) {
    val controlsEnabled: Boolean get() = !closed && phase != SpeechPhase.RUNNING && phase != SpeechPhase.FINISHING && phase != SpeechPhase.CANCELLING
}

interface SpeechDemoActions {
    fun selectAudioFile()
    fun clearAudioFile()
    fun openSpeechSettings()
    fun leavePage() {}
}

/** Admission, state changes and bridge callbacks are serialized on the platform UI thread. */
class SpeechViewModel(private val bridge: SpeechBridge) {
    var state by mutableStateOf(SpeechState()); private set
    private var audioGeneration = 0L
    private var audioPending = false
    private var microphonePending = false
    private var active: BridgeTask? = null
    private val queryGenerations = mutableMapOf<SpeechBridgeCapability, Long>()
    private val queries = mutableMapOf<SpeechBridgeCapability, BridgeTask>()

    fun refreshBackends(capability: SpeechBridgeCapability) {
        if (state.closed) return
        val generation = (queryGenerations[capability] ?: 0) + 1
        queryGenerations[capability] = generation
        queries.remove(capability)?.cancel()
        updateBackends(capability, backends(capability).copy(refreshing = true, failure = null))
        var completed = false
        fun terminal(value: SpeechBackendState) {
            if (completed || state.closed || queryGenerations[capability] != generation) return
            completed = true
            queries.remove(capability)
            updateBackends(capability, value)
        }
        val task = bridge.queryBackends(capability, object : SpeechBridgeBackendsCallback {
            override fun onResult(backends: List<SpeechBridgeBackend>) = terminal(SpeechBackendState(backends))
            override fun onFailure(failure: SpeechBridgeFailure) = terminal(backends(capability).copy(refreshing = false, failure = failure))
            override fun onCancelled() = terminal(backends(capability).copy(refreshing = false))
        })
        if (!completed && !state.closed && queryGenerations[capability] == generation) queries[capability] = task
    }

    fun selectBackend(capability: SpeechBridgeCapability, selection: SpeechBridgeSelection) {
        if (state.closed) return
        val previous = if (capability == SpeechBridgeCapability.TTS) state.ttsSelection else state.asrSelection
        if (previous == selection) return
        cancel()
        state = if (capability == SpeechBridgeCapability.TTS) state.copy(ttsSelection = selection)
                else state.copy(asrSelection = selection)
    }

    fun selectAudio(reference: String?, displayName: String?) {
        if (state.closed) return
        if (reference != state.audioReference) cancel()
        state = state.copy(audioReference = reference, audioDisplayName = if (reference == null) null else displayName)
    }

    fun inputFailed() {
        if (state.closed) return
        state = state.copy(phase = if (audioPending) state.phase else SpeechPhase.FAILED,
            failure = SpeechBridgeFailure(SpeechBridgeFailureCode.INVALID_INPUT, "audioImportFailed"))
    }

    fun speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection = state.ttsSelection) {
        val generation = begin() ?: return
        val task = bridge.speak(request, selection, object : SpeechBridgeTtsCallback {
            override fun onResult(synthesis: SpeechBridgeTtsResult) = terminal(generation, SpeechPhase.SUCCEEDED, tts = synthesis)
            override fun onFailure(failure: SpeechBridgeFailure) = terminal(generation, SpeechPhase.FAILED, failure = failure)
            override fun onCancelled() = terminal(generation, SpeechPhase.CANCELLED)
        })
        installHandle(generation, task)
    }

    fun recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection = state.asrSelection) {
        if (!state.controlsEnabled) { rejectAdmission(); return }
        val kind = when (request.input) {
            SpeechBridgeInput.Microphone -> SpeechBridgeInputKind.MICROPHONE
            is SpeechBridgeInput.AudioFile -> SpeechBridgeInputKind.AUDIO_FILE
        }
        // Only an exact known selection can establish input support. An unlisted explicit
        // backend must reach native admission; the query is never a registration allowlist.
        val descriptors = state.asr.backends
        val known = when (selection) {
            is SpeechBridgeSelection.Explicit -> descriptors.filter { it.backendId == selection.backendId }
            SpeechBridgeSelection.Default -> descriptors
        }
        if (known.isNotEmpty() && known.all { kind !in it.supportedInputs }) {
            state = state.copy(phase = SpeechPhase.FAILED, failure = failure(SpeechBridgeFailureCode.UNSUPPORTED_INPUT),
                               ttsResult = null, asrResult = null)
            return
        }
        val generation = begin(microphone = request.input == SpeechBridgeInput.Microphone) ?: return
        val task = bridge.recognize(request, selection, object : SpeechBridgeAsrCallback {
            override fun onResult(output: SpeechBridgeAsrResult) = terminal(generation, SpeechPhase.SUCCEEDED, asr = output)
            override fun onFailure(failure: SpeechBridgeFailure) = terminal(generation, SpeechPhase.FAILED, failure = failure)
            override fun onCancelled() = terminal(generation, SpeechPhase.CANCELLED)
        })
        installHandle(generation, task)
    }

    fun finishInput() {
        if (!audioPending || !microphonePending || state.phase != SpeechPhase.RUNNING) return
        val task = active
        if (task != null && task !is SpeechBridgeMicrophoneTask) return
        state = state.copy(phase = SpeechPhase.FINISHING, canFinishInput = false)
        (task as? SpeechBridgeMicrophoneTask)?.finishInput()
    }

    fun cancel() {
        if (!audioPending || state.phase == SpeechPhase.CANCELLING) return
        state = state.copy(phase = SpeechPhase.CANCELLING, canFinishInput = false)
        active?.cancel()
    }

    fun leavePage() { cancel(); cancelQueries(); state = state.copy(asrResult = null, ttsResult = null) }
    fun background() = leavePage()

    fun close() {
        if (state.closed) return
        state = state.copy(closed = true, asrResult = null, ttsResult = null)
        cancel(); cancelQueries()
        bridge.close(object : SpeechBridgeCloseCallback {
            override fun onClosed() {
                audioPending = false; active = null
                state = state.copy(phase = SpeechPhase.CANCELLED, asrResult = null, ttsResult = null, canFinishInput = false)
            }
            override fun onFailure(failure: SpeechBridgeFailure) {
                audioPending = false; active = null
                state = state.copy(phase = SpeechPhase.FAILED, failure = failure, asrResult = null, ttsResult = null, canFinishInput = false)
            }
        })
    }

    private fun begin(microphone: Boolean = false): Long? {
        if (!state.controlsEnabled) { rejectAdmission(); return null }
        audioPending = true
        microphonePending = microphone
        active = null
        state = state.copy(phase = SpeechPhase.RUNNING, ttsResult = null, asrResult = null, failure = null, canFinishInput = false)
        return ++audioGeneration
    }

    private fun installHandle(generation: Long, task: BridgeTask) {
        if (!audioPending || audioGeneration != generation) return
        active = task
        when (state.phase) {
            SpeechPhase.CANCELLING -> task.cancel()
            SpeechPhase.FINISHING -> {
                if (task is SpeechBridgeMicrophoneTask) task.finishInput()
                else state = state.copy(phase = SpeechPhase.RUNNING)
            }
            else -> state = state.copy(canFinishInput = microphonePending && task is SpeechBridgeMicrophoneTask)
        }
    }

    private fun terminal(generation: Long, phase: SpeechPhase, tts: SpeechBridgeTtsResult? = null,
                         asr: SpeechBridgeAsrResult? = null, failure: SpeechBridgeFailure? = null) {
        if (!audioPending || audioGeneration != generation) return
        audioPending = false; active = null
        val cancelled = state.closed || state.phase == SpeechPhase.CANCELLING
        state = state.copy(phase = if (cancelled) SpeechPhase.CANCELLED else phase,
            ttsResult = if (cancelled) null else tts, asrResult = if (cancelled) null else asr,
            failure = if (cancelled) null else failure, canFinishInput = false)
    }

    private fun cancelQueries() {
        SpeechBridgeCapability.entries.forEach { capability ->
            queryGenerations[capability] = (queryGenerations[capability] ?: 0) + 1
            queries.remove(capability)?.cancel()
            updateBackends(capability, backends(capability).copy(refreshing = false))
        }
    }
    private fun rejectAdmission() {
        state = state.copy(failure = failure(if (state.closed) SpeechBridgeFailureCode.CLOSED else SpeechBridgeFailureCode.BUSY))
    }
    private fun backends(capability: SpeechBridgeCapability) = if (capability == SpeechBridgeCapability.TTS) state.tts else state.asr
    private fun updateBackends(capability: SpeechBridgeCapability, value: SpeechBackendState) {
        state = if (capability == SpeechBridgeCapability.TTS) state.copy(tts = value) else state.copy(asr = value)
    }
    private fun failure(code: SpeechBridgeFailureCode) = SpeechBridgeFailure(code, null)
}
