package io.ondeviceai.binding

sealed interface MultimodalBridgeSelection {
    data object Default : MultimodalBridgeSelection
    data class Explicit(val backendId: String) : MultimodalBridgeSelection {
        init { require(backendId.isNotBlank()) }
    }
}

/** Single image; inputReference is a host-owned opaque token, never a path or URL. */
data class MultimodalBridgeRequest(val inputReference: String, val prompt: String, val maxOutputTokens: Int = 128) {
    init { require(inputReference.isNotBlank()); require(prompt.isNotBlank()); require(maxOutputTokens > 0) }
}
/** Single video; opaque token resolved only by an explicitly supplied video provider. */
data class MultimodalBridgeVideoRequest(val inputReference: String, val prompt: String, val maxOutputTokens: Int = 128) {
    init { require(inputReference.isNotBlank()); require(prompt.isNotBlank()); require(maxOutputTokens > 0) }
}
data class MultimodalBridgeBackend(val backendId: String, val availability: BridgeAvailability) {
    init { require(backendId.isNotBlank() && availability.backendId == backendId) }
}
/** Cumulative text; sequence is nonnegative and strictly increasing within an operation. */
data class MultimodalBridgeUpdate(val sequence: Int, val text: String) {
    init { require(sequence >= 0) }
}
data class MultimodalBridgeIdentity(val modelId: String, val modelRevision: String, val runtimeRevision: String) {
    init { require(modelId.isNotBlank() && modelRevision.isNotBlank() && runtimeRevision.isNotBlank()) }
}
/** Immutable text and identity remain valid after close; no output file ownership. */
data class MultimodalBridgeOutput(val backendId: String, val text: String, val model: MultimodalBridgeIdentity) {
    init { require(backendId.isNotBlank() && text.isNotBlank()) }
}
enum class MultimodalBridgeFailureCode { CLOSED, BUSY, INVALID_INPUT, BACKEND_NOT_REGISTERED, BACKEND_UNAVAILABLE, PLATFORM }
data class MultimodalBridgeFailure(
    val code: MultimodalBridgeFailureCode, val reason: String?, val domain: String? = null, val platformCode: Int? = null,
) {
    init {
        if (code == MultimodalBridgeFailureCode.PLATFORM) require(reason == null && !domain.isNullOrBlank() && platformCode != null)
        else require(domain == null && platformCode == null)
    }
}
interface MultimodalBridgeBackendsCallback {
    fun onMultimodalBackends(backends: List<MultimodalBridgeBackend>)
    fun onMultimodalFailure(failure: MultimodalBridgeFailure)
    fun onMultimodalCancelled()
}
interface MultimodalBridgeGenerateCallback {
    fun onMultimodalUpdate(update: MultimodalBridgeUpdate)
    fun onMultimodalResult(output: MultimodalBridgeOutput)
    fun onMultimodalFailure(failure: MultimodalBridgeFailure)
    fun onMultimodalCancelled()
}
interface MultimodalBridgeCloseCallback {
    fun onMultimodalClosed()
    fun onMultimodalFailure(failure: MultimodalBridgeFailure)
}
/**
 * UI-dispatcher admission/provider/callbacks (possibly synchronous); cancel may be any-thread.
 * One terminal after native drain/input release; close joins queries and generation.
 */
interface MultimodalBridge {
    fun queryMultimodalBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask
    fun generateMultimodal(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection, callback: MultimodalBridgeGenerateCallback): BridgeTask
    fun closeMultimodal(callback: MultimodalBridgeCloseCallback)
}
/** Optional video extension. Legacy image-only bridges remain valid MultimodalBridge implementations. */
interface MultimodalVideoBridge : MultimodalBridge {
    fun generateMultimodalVideo(request: MultimodalBridgeVideoRequest, selection: MultimodalBridgeSelection,
                                callback: MultimodalBridgeGenerateCallback): BridgeTask
}
private object CompletedMultimodalBridgeTask : BridgeTask { override fun cancel() = Unit }
class UnavailableMultimodalBridge : MultimodalVideoBridge {
    private var closed = false
    private fun failure() = if (closed) MultimodalBridgeFailure(MultimodalBridgeFailureCode.CLOSED, null)
        else MultimodalBridgeFailure(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, "backendNotLinked")
    override fun queryMultimodalBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask {
        callback.onMultimodalFailure(failure()); return CompletedMultimodalBridgeTask
    }
    override fun generateMultimodal(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection, callback: MultimodalBridgeGenerateCallback): BridgeTask {
        callback.onMultimodalFailure(failure()); return CompletedMultimodalBridgeTask
    }
    override fun generateMultimodalVideo(request: MultimodalBridgeVideoRequest, selection: MultimodalBridgeSelection, callback: MultimodalBridgeGenerateCallback): BridgeTask {
        callback.onMultimodalFailure(failure()); return CompletedMultimodalBridgeTask
    }
    override fun closeMultimodal(callback: MultimodalBridgeCloseCallback) { closed = true; callback.onMultimodalClosed() }
}
