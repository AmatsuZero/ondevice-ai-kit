package io.ondeviceai.voiceeffects

import io.ondeviceai.core.UnavailableReason
import java.io.File

enum class VoiceEffectsPreset { ORIGINAL, HIGH, LOW }

/** Keep the input unchanged until process returns, or drain/close completes after cancellation. */
data class VoiceEffectsRequest(val inputFile: File, val preset: VoiceEffectsPreset)

/** SDK-owned output. Valid across close until releaseOutput(id) succeeds. */
data class VoiceEffectsOutput(
    val id: String,
    val audioFile: File,
    val backendId: String,
    val sampleRate: Int,
    val frameCount: Long,
    val durationSeconds: Double
)

enum class VoiceEffectsFailureCode { CLOSED, BUSY, INVALID_INPUT, UNSUPPORTED_INPUT, BACKEND_UNAVAILABLE, PLATFORM }

/** Stable, path-free failures. Raw backend/IO exceptions are never attached as causes. */
sealed class VoiceEffectsError(val code: VoiceEffectsFailureCode, message: String) : IllegalStateException(message) {
    class Closed : VoiceEffectsError(VoiceEffectsFailureCode.CLOSED, "voice effects client closed")
    class Busy : VoiceEffectsError(VoiceEffectsFailureCode.BUSY, "voice effects client busy")
    class InvalidInput : VoiceEffectsError(VoiceEffectsFailureCode.INVALID_INPUT, "invalid voice effects input")
    class UnsupportedInput : VoiceEffectsError(VoiceEffectsFailureCode.UNSUPPORTED_INPUT, "unsupported voice effects input")
    class BackendUnavailable(val reason: UnavailableReason = UnavailableReason.BACKEND_NOT_LINKED) :
        VoiceEffectsError(VoiceEffectsFailureCode.BACKEND_UNAVAILABLE, "voice effects backend unavailable")
    class Platform : VoiceEffectsError(VoiceEffectsFailureCode.PLATFORM, "voice effects platform failure") {
        val domain: String = "io.ondeviceai.voiceeffects"
        val platformCode: Int = 1
    }
}

/**
 * Native render seam only; not a cross-platform selection protocol. Input is a
 * validated, client-owned PCM buffer; do not retain it after drain returns.
 * Return the same number of finite samples in [-1, 1]. Client handles ORIGINAL
 * without rendering so its copy remains byte-exact.
 *
 * render must stop access before returning, or drain must await any outstanding
 * work, even following cancellation/failure. drain/close run on IO in a
 * NonCancellable context. Do not call back into this client's lifecycle methods.
 */
interface VoiceEffectsBackend {
    val backendId: String
    suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset): FloatArray
    suspend fun drain() {}
    suspend fun close() {}
}
