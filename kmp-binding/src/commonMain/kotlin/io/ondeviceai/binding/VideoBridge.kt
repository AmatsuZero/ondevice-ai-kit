package io.ondeviceai.binding

sealed interface VideoBridgeSelection {
    data object Default : VideoBridgeSelection
    data class Explicit(val backendId: String) : VideoBridgeSelection {
        init { require(backendId.isNotBlank()) }
    }
}

enum class VideoBridgeAudioPolicy { PRESERVE, DISCARD }

/** References are host-owned opaque tokens, never file paths or URLs. */
data class VideoBridgeRequest(
    val inputReference: String,
    val scaleFactor: Int = 4,
    val audioPolicy: VideoBridgeAudioPolicy = VideoBridgeAudioPolicy.PRESERVE,
) {
    init { require(inputReference.isNotBlank()); require(scaleFactor >= 2) }
}

data class VideoBridgeBackend(
    val backendId: String,
    val availability: BridgeAvailability,
    val supportedScaleFactors: List<Int>,
    val supportedAudioPolicies: List<VideoBridgeAudioPolicy>,
) {
    init { require(backendId.isNotBlank()); require(availability.backendId == backendId) }
}

data class VideoBridgeProgress(val completedFrames: Int, val totalFrames: Int?) {
    init {
        require(completedFrames >= 0)
        require(totalFrames == null || (totalFrames > 0 && completedFrames <= totalFrames))
    }
}

/** Output remains valid after close until explicit releaseOutput completes. */
data class VideoBridgeOutput(
    val reference: String,
    val backendId: String,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val durationSeconds: Double,
    val audioTrackCount: Int,
) {
    init {
        require(reference.isNotBlank() && backendId.isNotBlank())
        require(width > 0 && height > 0 && frameCount > 0)
        require(durationSeconds.isFinite() && durationSeconds > 0 && audioTrackCount >= 0)
    }
}

enum class VideoBridgeFailureCode {
    CLOSED, BUSY, INVALID_INPUT, UNSUPPORTED_SCALE_FACTOR, UNSUPPORTED_AUDIO_POLICY,
    BACKEND_NOT_REGISTERED, BACKEND_UNAVAILABLE, PLATFORM,
}

/** Unknown native exceptions use stable adapter domain/code, without exception text. */
data class VideoBridgeFailure(
    val code: VideoBridgeFailureCode,
    val reason: String?,
    val domain: String? = null,
    val platformCode: Int? = null,
) {
    init {
        if (code == VideoBridgeFailureCode.PLATFORM) {
            require(reason == null && !domain.isNullOrBlank() && platformCode != null)
        } else require(domain == null && platformCode == null)
    }
}

interface VideoBridgeBackendsCallback {
    fun onResult(backends: List<VideoBridgeBackend>)
    fun onFailure(failure: VideoBridgeFailure)
    fun onCancelled()
}

interface VideoBridgeProcessCallback {
    fun onProgress(progress: VideoBridgeProgress)
    fun onResult(output: VideoBridgeOutput)
    fun onFailure(failure: VideoBridgeFailure)
    fun onCancelled()
}

interface VideoBridgeReleaseCallback {
    fun onReleased()
    fun onFailure(failure: VideoBridgeFailure)
}

interface VideoBridgeCloseCallback {
    fun onClosed()
    fun onFailure(failure: VideoBridgeFailure)
}

/**
 * Admission and callbacks use the platform UI dispatcher; callbacks may be synchronous.
 * Cancel publishes one terminal only after native drain. Delivered outputs outlive close.
 * Release failure retains ownership for retry; a repeated successful release is idempotent.
 */
interface VideoBridge {
    fun queryBackends(callback: VideoBridgeBackendsCallback): BridgeTask
    fun process(request: VideoBridgeRequest, selection: VideoBridgeSelection, callback: VideoBridgeProcessCallback): BridgeTask
    fun releaseOutput(reference: String, callback: VideoBridgeReleaseCallback)
    fun close(callback: VideoBridgeCloseCallback)
}

private object CompletedVideoBridgeTask : BridgeTask { override fun cancel() = Unit }

/** Hosts without a linked video module expose explicit failure, never synthetic success. */
class UnavailableVideoBridge : VideoBridge {
    private var closed = false
    private fun admission() = if (closed) VideoBridgeFailure(VideoBridgeFailureCode.CLOSED, null)
        else VideoBridgeFailure(VideoBridgeFailureCode.BACKEND_UNAVAILABLE, "backendNotLinked")

    override fun queryBackends(callback: VideoBridgeBackendsCallback): BridgeTask {
        callback.onFailure(admission())
        return CompletedVideoBridgeTask
    }

    override fun process(request: VideoBridgeRequest, selection: VideoBridgeSelection, callback: VideoBridgeProcessCallback): BridgeTask {
        callback.onFailure(admission())
        return CompletedVideoBridgeTask
    }

    override fun releaseOutput(reference: String, callback: VideoBridgeReleaseCallback) {
        callback.onFailure(VideoBridgeFailure(VideoBridgeFailureCode.INVALID_INPUT, null))
    }

    override fun close(callback: VideoBridgeCloseCallback) { closed = true; callback.onClosed() }
}
