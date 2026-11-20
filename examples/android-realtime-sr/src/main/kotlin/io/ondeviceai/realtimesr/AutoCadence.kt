package io.ondeviceai.realtimesr

class AutoCadence {
    private var generation: Long? = null
    private var misses = 0
    private var backoffLevel = 0
    private var coolingDown = false
    private var cooldownUntilNs = 0L

    @Synchronized
    fun reset(generation: Long) {
        this.generation = generation
    }

    @Synchronized
    fun shouldTry(generation: Long, nowNs: Long): Boolean {
        if (this.generation != generation) return false
        if (coolingDown && nowNs - cooldownUntilNs >= 0L) coolingDown = false
        return !coolingDown
    }

    @Synchronized
    fun record(
        generation: Long,
        enhancementRequested: Boolean,
        submittedBackend: String?,
        reason: String?,
        nowNs: Long,
        late: Boolean = false
    ) {
        if (this.generation != generation || !enhancementRequested) return
        if (coolingDown && nowNs - cooldownUntilNs >= 0L) coolingDown = false

        val successfulSubmission = submittedBackend == CUNNY && reason != SWAP_FAILED && !late
        if (successfulSubmission) {
            misses = (misses - 1).coerceAtLeast(0)
            if (misses == 0) backoffLevel = 0
            return
        }
        if (coolingDown) return

        misses++
        if (misses >= MISSES_BEFORE_BACKOFF) {
            val shift = backoffLevel.coerceAtMost(MAX_BACKOFF_SHIFT)
            cooldownUntilNs = nowNs + (BASE_BACKOFF_NS shl shift)
            coolingDown = true
            backoffLevel = (backoffLevel + 1).coerceAtMost(MAX_BACKOFF_SHIFT)
            misses = 0
        }
    }

    private companion object {
        const val CUNNY = "CUNNY"
        const val SWAP_FAILED = "swap_failed"
        const val MISSES_BEFORE_BACKOFF = 3
        const val BASE_BACKOFF_NS = 4_000_000_000L
        const val MAX_BACKOFF_SHIFT = 2
    }
}
