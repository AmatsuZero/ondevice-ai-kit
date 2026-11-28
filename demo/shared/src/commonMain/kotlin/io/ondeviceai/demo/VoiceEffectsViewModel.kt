package io.ondeviceai.demo

import androidx.compose.runtime.*
import io.ondeviceai.binding.*

enum class VoiceEffectsPhase { IDLE, RUNNING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }
enum class DemoVoiceEffect(val label: String, val key: String, val pitchPreset: VoiceEffectsBridgePreset? = null) {
    VOICE_A("音色 A", "voiceA"), VOICE_B("音色 B", "voiceB"), VOICE_C("音色 C", "voiceC"),
    ORIGINAL("原声", "original", VoiceEffectsBridgePreset.ORIGINAL),
    HIGH("升调", "high", VoiceEffectsBridgePreset.HIGH), LOW("降调", "low", VoiceEffectsBridgePreset.LOW),
}

/** The host renders into an owned WAV and keeps its reference valid until release. */
interface DemoVoiceTimbres {
    fun render(inputReference: String, key: String, callback: VoiceEffectsBridgeProcessCallback)
    fun renderReference(referenceInputReference: String, contentInputReference: String, callback: VoiceEffectsBridgeProcessCallback)
    fun ownsOutput(reference: String): Boolean
    fun releaseOutput(reference: String, callback: VoiceEffectsBridgeReleaseCallback)
}

interface DemoCoreMLVoiceTimbres : DemoVoiceTimbres {
    fun renderCoreML(inputReference: String, key: String, callback: VoiceEffectsBridgeProcessCallback)
}

data class VoiceEffectsState(
    val phase: VoiceEffectsPhase = VoiceEffectsPhase.IDLE,
    val inputReference: String? = null, val inputName: String? = null,
    val referenceReference: String? = null, val referenceName: String? = null,
    val referenceMode: Boolean = false,
    val effect: DemoVoiceEffect = DemoVoiceEffect.ORIGINAL, val exampleReference: String? = null,
    val useCoreML: Boolean = false,
    val output: VoiceEffectsBridgeOutput? = null, val importing: Boolean = false,
    val recording: Boolean = false, val mediaBusy: Boolean = false,
    val referenceImporting: Boolean = false, val referenceRecording: Boolean = false,
    val failure: VoiceEffectsBridgeFailure? = null, val releaseFailure: VoiceEffectsBridgeFailure? = null,
    val pendingReleases: Int = 0, val closed: Boolean = false,
) {
    val controlsEnabled get() = !closed && !importing && !recording && !referenceImporting && !referenceRecording && !mediaBusy &&
        phase != VoiceEffectsPhase.RUNNING && phase != VoiceEffectsPhase.CANCELLING
}
interface VoiceEffectsDemoActions {
    fun useExample()
    fun selectAudioFile()
    fun startRecording()
    fun stopRecording()
    fun selectReferenceAudioFile()
    fun startReferenceRecording()
    fun stopReferenceRecording()
    fun playReference()
    fun playOriginal()
    fun playResult(reference: String)
    fun stopPlayback()
    fun exportAudio(reference: String)
    fun leavePage()
    fun loadReferenceExample()
    fun clearVoiceInput(referenceSlot: Boolean)
    fun readVoiceWave(reference: String, reply: VoiceWaveReply)
    fun playVoiceAt(reference: String, fraction: Float)
    fun playbackFraction(reference: String): Float
}

interface VoiceWaveReply { fun ready(wave: VoiceWaveform?) }

/** UI-thread state. Host holds outputs while any player/exporter can read them. */
class VoiceEffectsViewModel(private val bridge: VoiceEffectsBridge, private val timbres: DemoVoiceTimbres? = null) {
    private val coreMLTimbres = timbres as? DemoCoreMLVoiceTimbres
    val hasTimbres get() = timbres != null
    val hasCoreMLTimbres get() = coreMLTimbres != null
    var onTerminal: DemoTerminalObserver? = null
    var state by mutableStateOf(VoiceEffectsState(effect = if (hasTimbres) DemoVoiceEffect.VOICE_A else DemoVoiceEffect.ORIGINAL)); private set
    private var generation = 0L
    private var pending = false
    private var active: BridgeTask? = null
    private val retired = mutableSetOf<String>()
    private val releasing = mutableSetOf<String>()
    private val holds = mutableMapOf<String, Int>()

    fun setExample(reference: String) {
        if (state.closed) return
        state = state.copy(exampleReference = reference)
        if (state.inputReference == null) useExample()
    }
    fun useExample() {
        if (state.controlsEnabled) selectInput(state.exampleReference, "内置普通话示例")
    }
    fun selectInput(reference: String?, name: String?) {
        if (state.closed || reference == null) return // picker cancellation preserves input
        if (reference != state.inputReference) { cancel(); clearOutput() }
        state = state.copy(inputReference = reference, inputName = name, failure = null,
            phase = if (pending || state.output != null) state.phase else VoiceEffectsPhase.IDLE)
    }
    fun clearInput() {
        if (state.closed) return
        cancel(); clearOutput()
        state = state.copy(inputReference = null, inputName = null, failure = null,
            phase = if (pending) VoiceEffectsPhase.CANCELLING else VoiceEffectsPhase.IDLE)
    }
    fun selectReferenceInput(reference: String?, name: String?) {
        if (state.closed || reference == null) return
        if (reference != state.referenceReference) { cancel(); clearOutput() }
        state = state.copy(referenceReference = reference, referenceName = name, failure = null,
            phase = if (pending || state.output != null) state.phase else VoiceEffectsPhase.IDLE)
    }
    fun clearReference() {
        if (state.closed) return
        cancel(); clearOutput()
        state = state.copy(referenceReference = null, referenceName = null, referenceImporting = false,
            referenceRecording = false, failure = null,
            phase = if (pending) VoiceEffectsPhase.CANCELLING else VoiceEffectsPhase.IDLE)
    }
    fun setReferenceMode(enabled: Boolean) {
        if (state.closed || state.referenceMode == enabled || (enabled && !hasTimbres)) return
        cancel(); clearOutput()
        state = state.copy(referenceMode = enabled, failure = null,
            phase = if (pending) VoiceEffectsPhase.CANCELLING else VoiceEffectsPhase.IDLE)
    }
    fun setReferenceImporting(value: Boolean) { if (!state.closed) state = state.copy(referenceImporting = value) }
    fun setReferenceRecording(value: Boolean) { if (!state.closed) state = state.copy(referenceRecording = value) }
    fun selectEffect(effect: DemoVoiceEffect) {
        if (!state.controlsEnabled || effect == state.effect || (effect.pitchPreset == null && !hasTimbres)) return
        clearOutput()
        state = state.copy(effect = effect, phase = VoiceEffectsPhase.IDLE, failure = null)
    }
    fun selectCoreML(enabled: Boolean) {
        if (!state.controlsEnabled || !hasCoreMLTimbres || state.effect.pitchPreset != null || state.useCoreML == enabled) return
        clearOutput()
        state = state.copy(useCoreML = enabled, phase = VoiceEffectsPhase.IDLE, failure = null)
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
        val reference = if (state.referenceMode) state.referenceReference ?: run { inputFailed("referenceMissing"); return } else null
        clearOutput(); val ticket = ++generation; pending = true; active = null
        state = state.copy(phase = VoiceEffectsPhase.RUNNING, failure = null)
        val callback = object : VoiceEffectsBridgeProcessCallback {
            override fun onResult(output: VoiceEffectsBridgeOutput) {
                if (!pending || ticket != generation || state.closed || state.phase == VoiceEffectsPhase.CANCELLING) {
                    if (state.output?.reference != output.reference) retire(output.reference)
                    terminal(ticket, VoiceEffectsPhase.CANCELLED); return
                }
                terminal(ticket, VoiceEffectsPhase.SUCCEEDED, output)
            }
            override fun onFailure(failure: VoiceEffectsBridgeFailure) = terminal(ticket, VoiceEffectsPhase.FAILED, failure = failure)
            override fun onCancelled() = terminal(ticket, VoiceEffectsPhase.CANCELLED)
        }
        if (state.referenceMode) {
            checkNotNull(timbres).renderReference(reference!!, input, callback)
            return
        }
        val effect = state.effect
        val preset = effect.pitchPreset
        if (preset == null) {
            // ONNX may still be reading its input after cancellation. Its terminal
            // callback discards a late file and only then re-enables the controls.
            if (state.useCoreML && coreMLTimbres != null) coreMLTimbres.renderCoreML(input, effect.key, callback)
            else checkNotNull(timbres).render(input, effect.key, callback)
            return
        }
        val task = bridge.process(VoiceEffectsBridgeRequest(input, preset), callback)
        if (pending && ticket == generation) { active = task; if (state.closed || state.phase == VoiceEffectsPhase.CANCELLING) task.cancel() }
    }
    fun cancel() {
        if (!pending) return
        state = state.copy(phase = VoiceEffectsPhase.CANCELLING); active?.cancel()
    }
    fun clearOutput() {
        val output = state.output ?: return
        state = state.copy(output = null, phase = VoiceEffectsPhase.IDLE); retire(output.reference)
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
            override fun onClosed() {
                if (!pending) state = state.copy(phase = VoiceEffectsPhase.CANCELLED)
            }
            override fun onFailure(failure: VoiceEffectsBridgeFailure) { state = state.copy(failure = failure) }
        })
    }
    private fun terminal(ticket: Long, phase: VoiceEffectsPhase, output: VoiceEffectsBridgeOutput? = null,
                         failure: VoiceEffectsBridgeFailure? = null) {
        if (!pending || ticket != generation) return
        val cancelled = state.closed || state.phase == VoiceEffectsPhase.CANCELLING
        pending = false; active = null
        state = state.copy(phase = if (cancelled) VoiceEffectsPhase.CANCELLED else phase,
            output = if (cancelled) null else output, failure = if (cancelled) null else failure)
        onTerminal?.invoke(DemoCapabilityId.VOICE_EFFECTS, when (state.phase) {
            VoiceEffectsPhase.SUCCEEDED -> DemoRecordOutcome.COMPLETED
            VoiceEffectsPhase.CANCELLED -> DemoRecordOutcome.CANCELLED
            else -> DemoRecordOutcome.FAILED
        }, "音频转换 · ${state.phase}")
    }
    private fun retire(reference: String) { retired += reference; release(reference) }
    private fun release(reference: String) {
        state = state.copy(pendingReleases = retired.size)
        if ((holds[reference] ?: 0) > 0 || !releasing.add(reference)) return
        val callback = object : VoiceEffectsBridgeReleaseCallback {
            override fun onReleased() {
                retired -= reference; releasing -= reference
                state = state.copy(pendingReleases = retired.size, releaseFailure = if (retired.isEmpty()) null else state.releaseFailure)
            }
            override fun onFailure(failure: VoiceEffectsBridgeFailure) {
                releasing -= reference; state = state.copy(releaseFailure = failure)
            }
        }
        if (timbres?.ownsOutput(reference) == true) timbres.releaseOutput(reference, callback)
        else bridge.releaseOutput(reference, callback)
    }
}
