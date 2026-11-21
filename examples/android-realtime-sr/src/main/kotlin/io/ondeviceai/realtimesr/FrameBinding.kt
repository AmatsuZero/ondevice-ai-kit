package io.ondeviceai.realtimesr

data class FrameId(val generation: Long, val loop: Int, val sourceOrdinal: Int)

class FrameBinding {
    private val lock = Any()
    private var generation = 0L
    private var pendingId: FrameId? = null
    private var pendingPtsUs = 0L

    fun offer(id: FrameId, ptsUs: Long): Boolean = synchronized(lock) {
        if (id.generation != generation || pendingId != null) {
            false
        } else {
            pendingId = id
            pendingPtsUs = ptsUs
            true
        }
    }

    fun reset(generation: Long) {
        synchronized(lock) {
            this.generation = generation
            pendingId = null
            pendingPtsUs = 0L
        }
    }

    fun confirm(generation: Long, textureTimestampNs: Long): FrameId? = synchronized(lock) {
        if (generation != this.generation) {
            null
        } else {
            val id = pendingId
            pendingId = null
            val pts = pendingPtsUs
            pendingPtsUs = 0L
            if (id != null && textureTimestampNs == pts * 1000L) id else null
        }
    }

    fun expire(): FrameId? = synchronized(lock) {
        val id = pendingId
        pendingId = null
        pendingPtsUs = 0L
        id
    }
}
