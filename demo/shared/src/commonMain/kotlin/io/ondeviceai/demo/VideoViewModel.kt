package io.ondeviceai.demo

import androidx.compose.runtime.*
import io.ondeviceai.binding.*

enum class VideoPhase { IDLE, RUNNING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }
data class VideoState(
    val phase: VideoPhase = VideoPhase.IDLE,
    val backends: List<VideoBridgeBackend> = emptyList(),
    val refreshing: Boolean = false,
    val queryFailure: VideoBridgeFailure? = null,
    val selection: VideoBridgeSelection = VideoBridgeSelection.Default,
    val audioPolicy: VideoBridgeAudioPolicy = VideoBridgeAudioPolicy.PRESERVE,
    val inputReference: String? = null,
    val inputDisplayName: String? = null,
    val progress: VideoBridgeProgress? = null,
    val output: VideoBridgeOutput? = null,
    val failure: VideoBridgeFailure? = null,
    val releaseFailure: VideoBridgeFailure? = null,
    val pendingReleases: Int = 0,
    val closed: Boolean = false,
) {
    val controlsEnabled get() = !closed && phase != VideoPhase.RUNNING && phase != VideoPhase.CANCELLING
}

interface VideoDemoActions {
    fun selectVideoFile()
    fun clearVideoFile()
    fun openVideo(reference: String)
    fun shareVideo(reference: String)
    fun leavePage() {}
}

/** Serialized on the host UI thread, including synchronous bridge callbacks. */
class VideoViewModel(private val bridge: VideoBridge) {
    var state by mutableStateOf(VideoState()); private set
    private var generation = 0L
    private var pending = false
    private var active: BridgeTask? = null
    private var queryGeneration = 0L
    private var query: BridgeTask? = null
    private val retired = mutableSetOf<String>()
    private val releasing = mutableSetOf<String>()

    fun refreshBackends() {
        if (state.closed) return
        val ticket = ++queryGeneration
        query?.cancel(); query = null
        state = state.copy(refreshing = true, queryFailure = null)
        var completed = false
        fun terminal(backends: List<VideoBridgeBackend> = state.backends, failure: VideoBridgeFailure? = null) {
            if (completed || state.closed || ticket != queryGeneration) return
            completed = true; query = null
            state = state.copy(backends = backends, refreshing = false, queryFailure = failure)
        }
        val handle = bridge.queryBackends(object : VideoBridgeBackendsCallback {
            override fun onResult(backends: List<VideoBridgeBackend>) = terminal(backends)
            override fun onFailure(failure: VideoBridgeFailure) = terminal(failure = failure)
            override fun onCancelled() = terminal()
        })
        if (!completed && !state.closed && ticket == queryGeneration) query = handle
    }

    fun selectBackend(selection: VideoBridgeSelection) {
        if (state.closed || selection == state.selection) return
        cancel(); state = state.copy(selection = selection)
    }
    fun selectAudioPolicy(policy: VideoBridgeAudioPolicy) {
        if (state.closed || policy == state.audioPolicy) return
        cancel(); state = state.copy(audioPolicy = policy)
    }
    fun selectVideo(reference: String?, displayName: String?) {
        if (state.closed) return
        if (reference != state.inputReference) { cancel(); clearOutput() }
        state = state.copy(inputReference = reference, inputDisplayName = if (reference == null) null else displayName)
    }
    fun inputFailed() {
        if (!state.closed) state = state.copy(phase = if (pending) state.phase else VideoPhase.FAILED,
            failure = VideoBridgeFailure(VideoBridgeFailureCode.INVALID_INPUT, "videoImportFailed"))
    }

    fun process() {
        if (!state.controlsEnabled) {
            state = state.copy(failure = VideoBridgeFailure(if (state.closed) VideoBridgeFailureCode.CLOSED else VideoBridgeFailureCode.BUSY, null))
            return
        }
        val input = state.inputReference
        if (input == null) { inputFailed(); return }
        clearOutput()
        val ticket = ++generation
        pending = true; active = null
        state = state.copy(phase = VideoPhase.RUNNING, progress = null, failure = null)
        val handle = bridge.process(VideoBridgeRequest(input, 4, state.audioPolicy), state.selection,
            object : VideoBridgeProcessCallback {
                override fun onProgress(progress: VideoBridgeProgress) {
                    if (pending && ticket == generation && state.phase == VideoPhase.RUNNING && !state.closed &&
                        progress.completedFrames >= (state.progress?.completedFrames ?: -1)) state = state.copy(progress = progress)
                }
                override fun onResult(output: VideoBridgeOutput) {
                    if (!pending || ticket != generation) {
                        if (state.output?.reference != output.reference) retire(output.reference)
                        return
                    }
                    if (state.closed || state.phase == VideoPhase.CANCELLING) {
                        retire(output.reference); terminal(ticket, VideoPhase.CANCELLED)
                    } else terminal(ticket, VideoPhase.SUCCEEDED, output = output)
                }
                override fun onFailure(failure: VideoBridgeFailure) = terminal(ticket, VideoPhase.FAILED, failure = failure)
                override fun onCancelled() = terminal(ticket, VideoPhase.CANCELLED)
            })
        if (pending && generation == ticket) { active = handle; if (state.phase == VideoPhase.CANCELLING || state.closed) handle.cancel() }
    }

    fun cancel() {
        if (!pending || state.phase == VideoPhase.CANCELLING) return
        state = state.copy(phase = VideoPhase.CANCELLING)
        active?.cancel()
    }
    fun clearOutput() {
        val output = state.output ?: return
        state = state.copy(output = null)
        retire(output.reference)
    }
    fun retryReleases() { retired.toList().forEach(::release) }
    fun leavePage() { cancel(); cancelQuery(); clearOutput() }
    fun background() = leavePage()
    fun close() {
        if (state.closed) return
        cancel(); cancelQuery(); clearOutput()
        state = state.copy(closed = true)
        bridge.close(object : VideoBridgeCloseCallback {
            override fun onClosed() { pending = false; active = null; state = state.copy(phase = VideoPhase.CANCELLED) }
            override fun onFailure(failure: VideoBridgeFailure) {
                pending = false; active = null; state = state.copy(phase = VideoPhase.FAILED, failure = failure)
            }
        })
    }

    private fun terminal(ticket: Long, phase: VideoPhase, output: VideoBridgeOutput? = null, failure: VideoBridgeFailure? = null) {
        if (!pending || generation != ticket) return
        val cancelled = state.closed || state.phase == VideoPhase.CANCELLING
        pending = false; active = null
        state = state.copy(phase = if (cancelled) VideoPhase.CANCELLED else phase,
            output = if (cancelled) null else output, failure = if (cancelled) null else failure)
    }
    private fun cancelQuery() {
        queryGeneration++; query?.cancel(); query = null; state = state.copy(refreshing = false)
    }
    private fun retire(reference: String) { retired += reference; release(reference) }
    private fun release(reference: String) {
        if (!releasing.add(reference)) return
        state = state.copy(pendingReleases = retired.size)
        bridge.releaseOutput(reference, object : VideoBridgeReleaseCallback {
            override fun onReleased() {
                retired -= reference; releasing -= reference
                state = state.copy(pendingReleases = retired.size, releaseFailure = if (retired.isEmpty()) null else state.releaseFailure)
            }
            override fun onFailure(failure: VideoBridgeFailure) {
                releasing -= reference; state = state.copy(pendingReleases = retired.size, releaseFailure = failure)
            }
        })
    }
}
