package io.ondeviceai.core
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
class TaskGate {
    private val mutex = Mutex()
    private var active: String? = null
    private var terminal: TaskTerminal? = null
    private var closed = false
    private var released = CompletableDeferred(Unit)
    suspend fun accept(id: String) = mutex.withLock {
        if (closed) throw ClosedException()
        if (active != null) throw BusyException()
        active = id; terminal = null; released = CompletableDeferred()
    }
    suspend fun finish(id: String, terminal: TaskTerminal): Boolean = mutex.withLock {
        if (active != id || this.terminal != null) false
        else { this.terminal = terminal; true }
    }
    suspend fun release(id: String) = mutex.withLock {
        if (active == id) { active = null; released.complete(Unit) }
    }
    suspend fun close() {
        val waiter = mutex.withLock { closed = true; released }; waiter.await()
    }
}
