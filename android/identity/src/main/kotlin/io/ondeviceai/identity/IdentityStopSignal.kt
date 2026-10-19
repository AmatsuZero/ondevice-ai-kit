package io.ondeviceai.identity

enum class IdentityStopReason { CANCELLED, AUTHORIZATION_REVOKED, CLOSED }

fun interface IdentityStopRegistration : AutoCloseable {
    override fun close()
}

/** Thread-safe cooperative stop boundary. Callbacks are always invoked outside the lock. */
class IdentityStopSignal {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private var stopped: IdentityStopReason? = null
    private var activeDeliveries = 0
    private var nextId = 0L
    private val handlers = linkedMapOf<Long, () -> Unit>()

    fun check() {
        val reason = synchronized(lock) { stopped } ?: return
        throw IdentityError(reason.errorCode)
    }

    fun registerStopHandler(callback: () -> Unit): IdentityStopRegistration {
        requireNotNull(callback)
        var immediate: IdentityStopReason? = null
        val id: Long
        synchronized(lock) {
            val reason = stopped
            if (reason != null) {
                immediate = reason
                id = -1L
            } else {
                id = nextId++
                handlers[id] = callback
            }
        }
        immediate?.let { invokeSafely(callback) }
        return IdentityStopRegistration {
            if (id >= 0) synchronized(lock) { handlers.remove(id) }
        }
    }

    fun requestStop(reason: IdentityStopReason) { prepareStopDelivery(reason)?.invoke() }

    internal fun prepareStopDelivery(reason: IdentityStopReason): (() -> Unit)? {
        val pending: List<() -> Unit>
        synchronized(lock) {
            if (stopped != null) return null
            stopped = reason
            pending = handlers.values.toList()
            handlers.clear()
            activeDeliveries++
        }
        return {
            try { pending.forEach(::invokeSafely) }
            finally { synchronized(lock) { activeDeliveries--; lock.notifyAll() } }
        }
    }

    internal fun awaitDelivery() {
        if (isDelivering) return
        var interrupted = false
        try {
            synchronized(lock) {
                while (activeDeliveries != 0) try { lock.wait() } catch (_: InterruptedException) { interrupted = true }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    private fun invokeSafely(callback: () -> Unit) {
        deliveryDepth.set((deliveryDepth.get() ?: 0) + 1)
        try { callback() } catch (_: Exception) { /* one backend must not block others */ }
        finally { deliveryDepth.set((deliveryDepth.get() ?: 0) - 1) }
    }

    internal companion object {
        private val deliveryDepth = ThreadLocal.withInitial { 0 }
        val isDelivering: Boolean get() = (deliveryDepth.get() ?: 0) > 0
    }

    private val IdentityStopReason.errorCode: IdentityErrorCode
        get() = when (this) {
            IdentityStopReason.CANCELLED -> IdentityErrorCode.CANCELLED
            IdentityStopReason.AUTHORIZATION_REVOKED -> IdentityErrorCode.AUTHORIZATION_REVOKED
            IdentityStopReason.CLOSED -> IdentityErrorCode.CLOSED
        }
}
