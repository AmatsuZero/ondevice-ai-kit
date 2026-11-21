package io.ondeviceai.realtimesr

import io.ondeviceai.binding.RealtimeSRMode
import io.ondeviceai.binding.RealtimeSRStatus

/** Open-result state is separate from display-confirmed frame state. */
internal class PlaybackOpenState {
    private var request = 0L
    @Volatile var error: String? = null
        private set
    private var directOutput = false

    @Synchronized fun begin(): Long {
        directOutput = false
        return ++request
    }

    @Synchronized fun current(token: Long) = token == request

    @Synchronized fun opened(token: Long, direct: Boolean): Boolean {
        if (!current(token)) return false
        directOutput = direct
        error = null
        return true
    }

    @Synchronized fun failed(token: Long, message: String): Boolean {
        if (!current(token)) return false
        error = message
        return true
    }

    @Synchronized fun reset() {
        begin()
        error = null
    }

    @Synchronized fun status(selected: RealtimeSRMode, confirmed: RealtimeSRStatus): RealtimeSRStatus =
        confirmed.copy(fellBackToOriginal = confirmed.fellBackToOriginal ||
            (directOutput && selected != RealtimeSRMode.ORIGINAL))
}
