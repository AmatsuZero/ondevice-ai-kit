package io.ondeviceai.realtimesr

import io.ondeviceai.binding.RealtimeSRBackend
import io.ondeviceai.binding.RealtimeSRHost
import io.ondeviceai.binding.RealtimeSRStatus

fun interface RendererSwitch {
    fun onSwitch(mode: PlaybackMode, generation: Long)
}

class AndroidRealtimeSRHost(
    private val policy: PresentationPolicy = PresentationPolicy(),
    private val rendererSwitch: RendererSwitch? = null,
) : RealtimeSRHost {

    override fun applyEnabled(enabled: Boolean) {
        val generation = policy.setEnabled(enabled)
        rendererSwitch?.onSwitch(policy.mode, generation)
    }

    override fun readStatus(): RealtimeSRStatus = policy.readStatus()

    fun select(mode: PlaybackMode) {
        val generation = policy.select(mode)
        rendererSwitch?.onSwitch(mode, generation)
    }

    fun onPresented(
        generation: Long,
        ordinal: Long,
        backend: RealtimeSRBackend,
        enhancementRequested: Boolean,
    ): Boolean = policy.presented(generation, ordinal, backend, enhancementRequested)
}
