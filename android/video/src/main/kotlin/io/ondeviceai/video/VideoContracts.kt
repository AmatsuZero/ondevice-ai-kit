package io.ondeviceai.video

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.UnavailableReason
import java.io.File

enum class VideoAudioPolicy { PRESERVE, DISCARD }

data class VideoProcessingRequest(val inputFile: File, val scaleFactor: Int, val audioPolicy: VideoAudioPolicy = VideoAudioPolicy.PRESERVE) {
    init { require(scaleFactor >= 2) { "scale factor must be at least 2" } }
}

data class VideoOutputInfo(val width: Int, val height: Int, val frameCount: Int, val durationSeconds: Double, val audioTrackCount: Int) {
    init {
        require(width > 0 && height > 0 && frameCount > 0 && durationSeconds.isFinite() && durationSeconds > 0 && audioTrackCount >= 0) {
            "invalid video output metadata"
        }
    }
}

data class VideoProcessingProgress(val completedFrames: Int, val totalFrames: Int? = null) {
    init {
        require(completedFrames >= 0 && (totalFrames == null || (totalFrames > 0 && completedFrames <= totalFrames))) { "invalid video progress" }
    }
}

/** Backend-owned output; release is idempotent. Delivered outputs outlive client.close(). */
interface VideoOutputLease {
    val file: File
    val info: VideoOutputInfo
    suspend fun release()
}

data class VideoProcessingResult(val backendId: String, val output: VideoOutputLease)

sealed class VideoBackendSelection {
    data object Default : VideoBackendSelection()
    data class Explicit(val backendId: String) : VideoBackendSelection()
}

interface VideoSuperResolutionBackend {
    val backendId: String
    val supportedScaleFactors: List<Int>
    val supportedAudioPolicies: List<VideoAudioPolicy>
    suspend fun availability(): CapabilityAvailability
    suspend fun process(request: VideoProcessingRequest, progress: ((VideoProcessingProgress) -> Unit)? = null): VideoOutputLease
    suspend fun drain()
    suspend fun close()
}

data class VideoBackendDescriptor(
    val backendId: String, val availability: CapabilityAvailability,
    val supportedScaleFactors: List<Int>, val supportedAudioPolicies: List<VideoAudioPolicy>
)

sealed class VideoClientError(message: String) : IllegalStateException(message) {
    data class BackendNotRegistered(val backendId: String) : VideoClientError("backend not registered: $backendId")
    data class BackendUnavailable(val backendId: String, val reason: UnavailableReason) : VideoClientError("backend unavailable: $backendId")
    data class UnsupportedScaleFactor(val scaleFactor: Int) : VideoClientError("unsupported scale factor: $scaleFactor")
    data class UnsupportedAudioPolicy(val policy: VideoAudioPolicy) : VideoClientError("unsupported audio policy: $policy")
    class Busy : VideoClientError("busy")
    class Closed : VideoClientError("closed")
}
