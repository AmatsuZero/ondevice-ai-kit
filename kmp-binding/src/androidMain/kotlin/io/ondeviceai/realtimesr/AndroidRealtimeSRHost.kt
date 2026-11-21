package io.ondeviceai.realtimesr

import io.ondeviceai.binding.RealtimeSRBackend
import io.ondeviceai.binding.RealtimeSRStatus
import io.ondeviceai.binding.RealtimeSRMode
import io.ondeviceai.binding.SelectableRealtimeSRHost

fun interface RendererSwitch {
    fun onSwitch(mode: PlaybackMode, generation: Long)
}

class AndroidRealtimeSRHost(
    private val policy: PresentationPolicy = PresentationPolicy(),
    private val rendererSwitch: RendererSwitch? = null,
) : SelectableRealtimeSRHost {

    var isModeAvailable: (PlaybackMode) -> Boolean = { true }

    override fun applyEnabled(enabled: Boolean) {
        if (!enabled) {
            val generation = policy.setEnabled(false)
            rendererSwitch?.onSwitch(policy.mode, generation)
            return
        }

        val currentMode = policy.mode
        if (currentMode == PlaybackMode.ORIGINAL) {
            if (rendererSwitch == null || !isModeAvailable(PlaybackMode.AUTO)) return
        } else if (rendererSwitch == null || !isModeAvailable(currentMode)) {
            val generation = policy.select(PlaybackMode.ORIGINAL)
            rendererSwitch?.onSwitch(PlaybackMode.ORIGINAL, generation)
            return
        }

        val generation = policy.setEnabled(true)
        rendererSwitch?.onSwitch(policy.mode, generation)
    }

    override fun readStatus(): RealtimeSRStatus = policy.readStatus()

    fun select(mode: PlaybackMode) {
        val generation = policy.select(mode)
        rendererSwitch?.onSwitch(mode, generation)
    }

    override fun supportedModes(): List<RealtimeSRMode> = buildList {
        add(RealtimeSRMode.ORIGINAL)
        if (rendererSwitch != null) {
            if (isModeAvailable(PlaybackMode.AUTO)) add(RealtimeSRMode.AUTO)
            if (isModeAvailable(PlaybackMode.CUNNY)) add(RealtimeSRMode.CUNNY)
            if (isModeAvailable(PlaybackMode.SPATIAL)) add(RealtimeSRMode.SPATIAL)
        }
    }

    override fun selectedMode(): RealtimeSRMode = when (policy.mode) {
        PlaybackMode.ORIGINAL -> RealtimeSRMode.ORIGINAL
        PlaybackMode.AUTO -> RealtimeSRMode.AUTO
        PlaybackMode.CUNNY -> RealtimeSRMode.CUNNY
        PlaybackMode.SPATIAL -> RealtimeSRMode.SPATIAL
    }

    override fun selectMode(mode: RealtimeSRMode): Boolean {
        val playbackMode = when (mode) {
            RealtimeSRMode.ORIGINAL -> PlaybackMode.ORIGINAL
            RealtimeSRMode.AUTO -> PlaybackMode.AUTO
            RealtimeSRMode.CUNNY -> PlaybackMode.CUNNY
            RealtimeSRMode.SPATIAL -> PlaybackMode.SPATIAL
            RealtimeSRMode.SYSTEM, RealtimeSRMode.LITERT -> return false
        }

        if (playbackMode != PlaybackMode.ORIGINAL &&
            (rendererSwitch == null || !isModeAvailable(playbackMode))
        ) {
            return false
        }

        if (policy.mode == playbackMode) return true

        val generation = policy.select(playbackMode)
        rendererSwitch?.onSwitch(playbackMode, generation)
        return true
    }

    fun onPresented(
        generation: Long,
        ordinal: Long,
        backend: RealtimeSRBackend,
        enhancementRequested: Boolean,
    ): Boolean = policy.presented(generation, ordinal, backend, enhancementRequested)
}
