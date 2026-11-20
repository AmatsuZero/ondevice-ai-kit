package io.ondeviceai.realtimesr

import io.ondeviceai.binding.RealtimeSRBackend
import io.ondeviceai.binding.RealtimeSRStatus

enum class PlaybackMode { AUTO, CUNNY, SPATIAL, ORIGINAL }

class PresentationPolicy(initialMode: PlaybackMode = PlaybackMode.ORIGINAL) {
    private val lock = Any()

    private var currentMode: PlaybackMode = initialMode
    private var currentGeneration = 0L
    private var enabledState = false
    private var preferred = RealtimeSRBackend.ORIGINAL
    private var presentedBackend: RealtimeSRBackend? = null
    private var fellBack = false
    private var lastOrdinal = -1L

    init {
        applyDefaults(currentMode)
    }

    val mode: PlaybackMode get() = synchronized(lock) { currentMode }
    val generation: Long get() = synchronized(lock) { currentGeneration }

    fun select(mode: PlaybackMode): Long = synchronized(lock) {
        currentMode = mode
        reset()
        applyDefaults(mode)
        currentGeneration
    }

    fun setEnabled(enabled: Boolean): Long = synchronized(lock) {
        val mode = when {
            !enabled -> PlaybackMode.ORIGINAL
            currentMode == PlaybackMode.CUNNY || currentMode == PlaybackMode.SPATIAL || currentMode == PlaybackMode.AUTO -> currentMode
            else -> PlaybackMode.AUTO
        }
        select(mode)
    }

    fun presented(
        generation: Long,
        ordinal: Long,
        backend: RealtimeSRBackend,
        enhancementRequested: Boolean,
    ): Boolean = synchronized(lock) {
        if (generation != currentGeneration) return@synchronized false
        if (ordinal < 0 || ordinal <= lastOrdinal) return@synchronized false
        if (backend != RealtimeSRBackend.CUNNY && backend != RealtimeSRBackend.SPATIAL && backend != RealtimeSRBackend.ORIGINAL) {
            return@synchronized false
        }
        val enhancementBackend = backend == RealtimeSRBackend.CUNNY || backend == RealtimeSRBackend.SPATIAL
        if (enhancementBackend && (!enabledState || !enhancementRequested)) return@synchronized false
        if (backend == RealtimeSRBackend.CUNNY && currentMode == PlaybackMode.SPATIAL) return@synchronized false
        if (backend == RealtimeSRBackend.SPATIAL && currentMode != PlaybackMode.SPATIAL) return@synchronized false
        lastOrdinal = ordinal
        presentedBackend = backend
        fellBack = currentMode != PlaybackMode.ORIGINAL &&
            enhancementRequested &&
            backend == RealtimeSRBackend.ORIGINAL
        true
    }

    fun readStatus(): RealtimeSRStatus = synchronized(lock) {
        RealtimeSRStatus(enabledState, preferred, presentedBackend, fellBack)
    }

    private fun reset() {
        currentGeneration++
        presentedBackend = null
        fellBack = false
        lastOrdinal = -1L
    }

    private fun applyDefaults(mode: PlaybackMode) {
        enabledState = mode != PlaybackMode.ORIGINAL
        preferred = when (mode) {
            PlaybackMode.ORIGINAL -> RealtimeSRBackend.ORIGINAL
            PlaybackMode.SPATIAL -> RealtimeSRBackend.SPATIAL
            PlaybackMode.AUTO, PlaybackMode.CUNNY -> RealtimeSRBackend.CUNNY
        }
    }
}
