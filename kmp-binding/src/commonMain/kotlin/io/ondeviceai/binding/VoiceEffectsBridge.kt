package io.ondeviceai.binding

enum class VoiceEffectsBridgePreset { ORIGINAL, HIGH, LOW }
data class VoiceEffectsBridgeRequest(val inputReference: String, val preset: VoiceEffectsBridgePreset) {
    init { require(inputReference.isNotBlank()) }
}
data class VoiceEffectsBridgeOutput(val reference: String, val backendId: String, val sampleRate: Int,
    val frameCount: Long, val durationSeconds: Double) {
    init {
        require(reference.isNotBlank() && backendId.isNotBlank())
        require(sampleRate in listOf(16000, 44100, 48000) && frameCount in 1..sampleRate * 30L)
        require(durationSeconds.isFinite() && durationSeconds > 0)
    }
}
enum class VoiceEffectsBridgeFailureCode { CLOSED, BUSY, INVALID_INPUT, UNSUPPORTED_INPUT, BACKEND_UNAVAILABLE, PLATFORM }
data class VoiceEffectsBridgeFailure(val code: VoiceEffectsBridgeFailureCode, val reason: String?,
    val domain: String? = null, val platformCode: Int? = null) {
    init {
        if (code == VoiceEffectsBridgeFailureCode.PLATFORM) require(reason == null && !domain.isNullOrBlank() && platformCode != null)
        else require(domain == null && platformCode == null)
    }
}
interface VoiceEffectsBridgeProcessCallback {
    fun onResult(output: VoiceEffectsBridgeOutput)
    fun onFailure(failure: VoiceEffectsBridgeFailure)
    fun onCancelled()
}
interface VoiceEffectsBridgeReleaseCallback {
    fun onReleased()
    fun onFailure(failure: VoiceEffectsBridgeFailure)
}
interface VoiceEffectsBridgeCloseCallback {
    fun onClosed()
    fun onFailure(failure: VoiceEffectsBridgeFailure)
}
/** Admission and callbacks use the platform UI dispatcher. Outputs outlive close. */
interface VoiceEffectsBridge {
    fun process(request: VoiceEffectsBridgeRequest, callback: VoiceEffectsBridgeProcessCallback): BridgeTask
    fun releaseOutput(reference: String, callback: VoiceEffectsBridgeReleaseCallback)
    fun close(callback: VoiceEffectsBridgeCloseCallback)
}
class UnavailableVoiceEffectsBridge : VoiceEffectsBridge {
    private var closed = false
    override fun process(request: VoiceEffectsBridgeRequest, callback: VoiceEffectsBridgeProcessCallback): BridgeTask {
        callback.onFailure(VoiceEffectsBridgeFailure(if (closed) VoiceEffectsBridgeFailureCode.CLOSED
            else VoiceEffectsBridgeFailureCode.BACKEND_UNAVAILABLE, if (closed) null else "backendNotLinked"))
        return object : BridgeTask { override fun cancel() {} }
    }
    override fun releaseOutput(reference: String, callback: VoiceEffectsBridgeReleaseCallback) {
        callback.onFailure(VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.INVALID_INPUT, null))
    }
    override fun close(callback: VoiceEffectsBridgeCloseCallback) { closed = true; callback.onClosed() }
}
