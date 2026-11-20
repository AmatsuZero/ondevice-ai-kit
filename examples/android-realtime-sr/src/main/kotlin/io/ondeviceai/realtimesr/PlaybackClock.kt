package io.ondeviceai.realtimesr

class PlaybackClock(sampleRate: Int) {
    private val rate: Long = sampleRate.toLong()
    private val lock = Any()

    private var anchored = false
    private var paused = false
    private var hasPrev = false

    private var headSamples = 0L
    private var prevHead32 = 0
    private var anchorHeadSamples = 0L
    private var anchorMediaUs = 0L

    private var observedNs = 0L
    private var observedMediaUs = 0L

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    fun observe(head32: Int, mediaAnchorUs: Long, nowNs: Long) {
        synchronized(lock) {
            if (!paused) {
                if (!anchored || !hasPrev) {
                    reanchor(head32, mediaAnchorUs, nowNs)
                } else {
                    val delta = (head32 - prevHead32).toLong()
                    if (delta < 0) {
                        reanchor(head32, mediaAnchorUs, nowNs)
                    } else {
                        headSamples += delta
                        prevHead32 = head32
                        commitObservation(nowNs)
                    }
                }
            }
        }
    }

    private fun reanchor(head32: Int, mediaAnchorUs: Long, nowNs: Long) {
        headSamples = head32.toLong() and 0xFFFF_FFFFL
        prevHead32 = head32
        hasPrev = true
        anchored = true
        anchorHeadSamples = headSamples
        anchorMediaUs = mediaAnchorUs
        commitObservation(nowNs)
    }

    private fun commitObservation(nowNs: Long) {
        observedNs = nowNs
        observedMediaUs = anchorMediaUs +
            (headSamples - anchorHeadSamples) * 1_000_000L / rate
    }

    fun positionUs(): Long? = synchronized(lock) { if (anchored) observedMediaUs else null }

    fun targetNs(ptsUs: Long): Long? = synchronized(lock) {
        if (!anchored) null else observedNs + (ptsUs - observedMediaUs) * 1_000L
    }

    fun pause() {
        synchronized(lock) { paused = true }
    }

    fun reset() {
        synchronized(lock) {
            anchored = false
            paused = false
            hasPrev = false
            headSamples = 0L
            prevHead32 = 0
            anchorHeadSamples = 0L
            anchorMediaUs = 0L
            observedNs = 0L
            observedMediaUs = 0L
        }
    }
}
