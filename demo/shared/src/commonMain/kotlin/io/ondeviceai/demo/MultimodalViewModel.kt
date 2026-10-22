package io.ondeviceai.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ondeviceai.binding.*

enum class MultimodalPhase { IDLE, RUNNING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }

data class MultimodalState(
    val phase: MultimodalPhase = MultimodalPhase.IDLE,
    val backends: List<MultimodalBridgeBackend> = emptyList(),
    val refreshing: Boolean = false,
    val queryFailure: MultimodalBridgeFailure? = null,
    val selection: MultimodalBridgeSelection = MultimodalBridgeSelection.Default,
    val inputReference: String? = null,
    val inputDisplayName: String? = null,
    val prompt: String = "Describe this image.",
    val tokenBudget: String = "32",
    val update: MultimodalBridgeUpdate? = null,
    val output: MultimodalBridgeOutput? = null,
    val failure: MultimodalBridgeFailure? = null,
    val closed: Boolean = false,
    val closing: Boolean = false,
    val closeFailure: MultimodalBridgeFailure? = null,
) {
    val controlsEnabled get() = !closed && phase != MultimodalPhase.RUNNING && phase != MultimodalPhase.CANCELLING
    val canGenerate get() = controlsEnabled && !inputReference.isNullOrBlank() && prompt.isNotBlank() &&
        (tokenBudget.toIntOrNull() ?: 0) > 0
}

/** UI-serialized shared state only; the bridge owns all native input leases and cleanup. */
class MultimodalViewModel(private val bridge: MultimodalBridge = UnavailableMultimodalBridge()) {
    var state by mutableStateOf(MultimodalState()); private set
    private var generation = 0L
    private var pending = false
    private var active: BridgeTask? = null
    private var queryGeneration = 0L
    private var query: BridgeTask? = null

    fun refreshBackends() {
        if (state.closed) return
        val ticket = ++queryGeneration
        query?.cancel(); query = null
        state = state.copy(refreshing = true, queryFailure = null)
        var completed = false
        fun terminal(backends: List<MultimodalBridgeBackend> = state.backends, failure: MultimodalBridgeFailure? = null) {
            if (completed || state.closed || ticket != queryGeneration) return
            completed = true; query = null
            state = state.copy(backends = backends.toList(), refreshing = false, queryFailure = failure)
        }
        val handle = bridge.queryMultimodalBackends(object : MultimodalBridgeBackendsCallback {
            override fun onMultimodalBackends(backends: List<MultimodalBridgeBackend>) = terminal(backends)
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = terminal(failure = failure)
            override fun onMultimodalCancelled() = terminal()
        })
        if (!completed) {
            if (!state.closed && ticket == queryGeneration) query = handle else handle.cancel()
        }
    }

    fun selectBackend(selection: MultimodalBridgeSelection) {
        if (state.closed || selection == state.selection) return
        invalidateRequest(); state = state.copy(selection = selection)
    }
    fun selectImage(reference: String?, displayName: String?) {
        if (state.closed) return
        if (reference != state.inputReference) invalidateRequest()
        state = state.copy(inputReference = reference, inputDisplayName = if (reference == null) null else displayName)
    }
    fun setPrompt(prompt: String) {
        if (state.closed || prompt == state.prompt) return
        invalidateRequest(); state = state.copy(prompt = prompt)
    }
    fun setTokenBudget(text: String) {
        if (state.closed || text == state.tokenBudget) return
        invalidateRequest(); state = state.copy(tokenBudget = text)
    }
    fun inputFailed() {
        if (state.closed) return
        cancel()
        state = state.copy(phase = if (pending) state.phase else MultimodalPhase.FAILED,
            failure = fail(MultimodalBridgeFailureCode.INVALID_INPUT))
    }

    fun generate() {
        if (!state.controlsEnabled) {
            state = state.copy(failure = fail(if (state.closed) MultimodalBridgeFailureCode.CLOSED else MultimodalBridgeFailureCode.BUSY))
            return
        }
        if (!state.canGenerate) { inputFailed(); return }
        val request = MultimodalBridgeRequest(checkNotNull(state.inputReference), state.prompt, checkNotNull(state.tokenBudget.toIntOrNull()))
        val ticket = ++generation
        pending = true; active = null
        state = state.copy(phase = MultimodalPhase.RUNNING, update = null, output = null, failure = null)
        val handle = bridge.generateMultimodal(request, state.selection, object : MultimodalBridgeGenerateCallback {
            override fun onMultimodalUpdate(update: MultimodalBridgeUpdate) {
                if (pending && ticket == generation && !state.closed && state.phase == MultimodalPhase.RUNNING &&
                    update.sequence > (state.update?.sequence ?: -1)) state = state.copy(update = update)
            }
            override fun onMultimodalResult(output: MultimodalBridgeOutput) = terminal(ticket, MultimodalPhase.SUCCEEDED, output = output)
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = terminal(ticket, MultimodalPhase.FAILED, failure = failure)
            override fun onMultimodalCancelled() = terminal(ticket, MultimodalPhase.CANCELLED)
        })
        // Synchronous terminal must not attach an already finished task to later cancellation.
        if (pending && ticket == generation) {
            active = handle
            if (state.closed || state.phase == MultimodalPhase.CANCELLING) handle.cancel()
        }
    }

    fun cancel() {
        if (!pending || state.phase == MultimodalPhase.CANCELLING) return
        state = state.copy(phase = MultimodalPhase.CANCELLING)
        active?.cancel()
    }
    fun leavePage() { cancel(); cancelQuery() }
    fun background() = leavePage()

    fun close() {
        if (state.closed) return
        state = state.copy(closed = true, closing = true, output = null, update = null)
        cancel(); cancelQuery()
        fun finish(failure: MultimodalBridgeFailure? = null) {
            if (!state.closing) return
            pending = false; active = null; generation++
            state = state.copy(closing = false, phase = if (failure == null) MultimodalPhase.CANCELLED else MultimodalPhase.FAILED,
                closeFailure = failure, failure = null)
        }
        bridge.closeMultimodal(object : MultimodalBridgeCloseCallback {
            override fun onMultimodalClosed() = finish()
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = finish(failure)
        })
    }

    private fun terminal(ticket: Long, phase: MultimodalPhase, output: MultimodalBridgeOutput? = null,
                         failure: MultimodalBridgeFailure? = null) {
        if (!pending || ticket != generation) return
        val cancelled = state.closed || state.phase == MultimodalPhase.CANCELLING
        pending = false; active = null
        state = state.copy(phase = if (cancelled) MultimodalPhase.CANCELLED else phase,
            output = if (cancelled) null else output, failure = if (cancelled) null else failure)
    }
    private fun invalidateRequest() {
        cancel()
        state = state.copy(update = null, output = null, failure = null)
    }
    private fun cancelQuery() {
        queryGeneration++; query?.cancel(); query = null
        state = state.copy(refreshing = false)
    }
    private fun fail(code: MultimodalBridgeFailureCode) = MultimodalBridgeFailure(code, null)
}
