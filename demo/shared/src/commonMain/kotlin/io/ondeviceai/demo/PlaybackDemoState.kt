package io.ondeviceai.demo

import io.ondeviceai.binding.RealtimeSRMode

/** Immutable cross-platform UI state. Pixel buffers and textures remain owned by the native host. */
data class PlaybackDemoState(
    val hasVideo: Boolean,
    val isPlaying: Boolean,
    val inputName: String?,
    val status: String,
    val supportedModes: List<RealtimeSRMode>,
    val selectedMode: RealtimeSRMode?,
    val presentedMode: RealtimeSRMode?,
    val fallbackToOriginal: Boolean,
) {
    companion object {
        fun read(player: PlaybackEnhancementDemoActions?, sr: RealtimeSRDemoActions?) = PlaybackDemoState(
            player?.hasVideo == true, player?.isPlaying == true, player?.inputName,
            player?.playbackStatus ?: "尚未接入播放器", sr?.supportedModes?.toList().orEmpty(),
            sr?.selectedMode, sr?.presentedMode, sr?.fallbackToOriginal == true,
        )
    }
}
