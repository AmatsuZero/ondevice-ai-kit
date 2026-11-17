package io.ondeviceai.demo

import androidx.compose.runtime.*
import io.ondeviceai.binding.*

enum class VoiceEffectsPhase { IDLE, RUNNING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }
data class VoiceEffectsState(
    val phase: VoiceEffectsPhase = VoiceEffectsPhase.IDLE,
    val inputReference: String? = null, val inputName: String? = null,
    val preset: VoiceEffectsBridgePreset = VoiceEffectsBridgePreset.ORIGINAL,
    val output: VoiceEffectsBridgeOutput? = null, val importing: Boolean = false,
    val recording: Boolean = false, val mediaBusy: Boolean = false,
    val failure: VoiceEffectsBridgeFailure? = null, val releaseFailure: VoiceEffectsBridgeFailure? = null,
    val pendingReleases: Int = 0, val closed: Boolean = false,
) {
    val controlsEnabled get() = !closed && !importing && !recording && !mediaBusy &&
        phase != VoiceEffectsPhase.RUNNING && phase != VoiceEffectsPhase.CANCELLING
}
interface VoiceEffectsDemoActions {
    fun selectAudioFile()
    fun startRecording()
    fun stopRecording()
    fun playOriginal()
    fun playResult(reference: String)
    fun stopPlayback()
    fun exportAudio(reference: String)
    fun leavePage()
}

/** UI-thread state. Host holds outputs while any player/exporter can read them. */
class VoiceEffectsViewModel(private val bridge: VoiceEffectsBridge) {
    var state by mutableStateOf(VoiceEffectsState()); private set
    private var generation = 0L
    private var pending = false
    private var active: BridgeTask? = null
    private val retired = mutableSetOf<String>()
    private val releasing = mutableSetOf<String>()
    private val holds = mutableMapOf<String, Int>()

    fun selectInput(reference: String?, name: String?) {
        if (state.closed || reference == null) return // picker cancellation preserves input
        if (reference != state.inputReference) { cancel(); clearOutput() }
        state = state.copy(inputReference = reference, inputName = name, failure = null)
    }
    fun selectPreset(preset: VoiceEffectsBridgePreset) {
        if (state.controlsEnabled) state = state.copy(preset = preset)
    }
    fun setImporting(value: Boolean) { if (!state.closed) state = state.copy(importing = value) }
    fun setRecording(value: Boolean) { if (!state.closed) state = state.copy(recording = value) }
    fun setMediaBusy(value: Boolean) { state = state.copy(mediaBusy = value) }
    fun inputFailed(reason: String = "audioImportFailed") {
        if (!state.closed) state = state.copy(phase = if (pending) state.phase else VoiceEffectsPhase.FAILED,
            failure = VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.INVALID_INPUT, reason))
    }
    fun process() {
        if (!state.controlsEnabled || pending) return
        val input = state.inputReference ?: run { inputFailed(); return }
        clearOutput(); val ticket = ++generation; pending = true; active = null
        state = state.copy(phase = VoiceEffectsPhase.RUNNING, failure = null)
        val task = bridge.process(VoiceEffectsBridgeRequest(input, state.preset), object : VoiceEffectsBridgeProcessCallback {
            override fun onResult(output: VoiceEffectsBridgeOutput) {
                if (!pending || ticket != generation || state.closed || state.phase == VoiceEffectsPhase.CANCELLING) {
                    if (state.output?.reference != output.reference) retire(output.reference)
                    terminal(ticket, VoiceEffectsPhase.CANCELLED); return
                }
                terminal(ticket, VoiceEffectsPhase.SUCCEEDED, output)
            }
            override fun onFailure(failure: VoiceEffectsBridgeFailure) = terminal(ticket, VoiceEffectsPhase.FAILED, failure = failure)
            override fun onCancelled() = terminal(ticket, VoiceEffectsPhase.CANCELLED)
        })
        if (pending && ticket == generation) { active = task; if (state.closed || state.phase == VoiceEffectsPhase.CANCELLING) task.cancel() }
    }
    fun cancel() {
        if (!pending) return
        state = state.copy(phase = VoiceEffectsPhase.CANCELLING); active?.cancel()
    }
    fun clearOutput() {
        val output = state.output ?: return
        state = state.copy(output = null); retire(output.reference)
    }
    fun holdOutput(reference: String): Boolean {
        if (state.output?.reference != reference || reference in releasing || state.closed) return false
        holds[reference] = (holds[reference] ?: 0) + 1; return true
    }
    fun endOutputUse(reference: String) {
        val count = holds[reference] ?: return
        if (count > 1) holds[reference] = count - 1 else holds.remove(reference)
        if (reference in retired) release(reference)
    }
    fun retryReleases() { retired.toList().forEach(::release) }
    fun leavePage() { cancel(); clearOutput() }
    fun close() {
        if (state.closed) return
        leavePage(); state = state.copy(closed = true)
        bridge.close(object : VoiceEffectsBridgeCloseCallback {
            override fun onClosed() { active = null; pending = false; state = state.copy(phase = VoiceEffectsPhase.CANCELLED) }
            override fun onFailure(failure: VoiceEffectsBridgeFailure) { state = state.copy(failure = failure) }
        })
    }
    private fun terminal(ticket: Long, phase: VoiceEffectsPhase, output: VoiceEffectsBridgeOutput? = null,
                         failure: VoiceEffectsBridgeFailure? = null) {
        if (!pending || ticket != generation) return
        val cancelled = state.closed || state.phase == VoiceEffectsPhase.CANCELLING
        pending = false; active = null
        state = state.copy(phase = if (cancelled) VoiceEffectsPhase.CANCELLED else phase,
            output = if (cancelled) null else output, failure = failure)
    }
    private fun retire(reference: String) { retired += reference; release(reference) }
    private fun release(reference: String) {
        state = state.copy(pendingReleases = retired.size)
        if ((holds[reference] ?: 0) > 0 || !releasing.add(reference)) return
        bridge.releaseOutput(reference, object : VoiceEffectsBridgeReleaseCallback {
            override fun onReleased() {
                retired -= reference; releasing -= reference
                state = state.copy(pendingReleases = retired.size, releaseFailure = if (retired.isEmpty()) null else state.releaseFailure)
            }
            override fun onFailure(failure: VoiceEffectsBridgeFailure) {
                releasing -= reference; state = state.copy(releaseFailure = failure)
            }
        })
    }
}
