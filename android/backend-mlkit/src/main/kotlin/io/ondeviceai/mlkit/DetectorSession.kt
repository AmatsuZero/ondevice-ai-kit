package io.ondeviceai.mlkit
import io.ondeviceai.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Serializes admission and keeps native resources alive until actual backend completion. */
internal class DetectorSession(private val releaseResources:()->Unit) {
    private val gate=TaskGate()
    private val mutex=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var closed=false
    private var resourcesReleased=false
    private var active:Job?=null
    suspend fun isClosed():Boolean=mutex.withLock {closed}
    suspend fun <T> execute(operation:suspend ()->T):T {
        currentCoroutineContext().ensureActive()
        val id=UUID.randomUUID().toString()
        val worker=mutex.withLock {
            if(closed) throw ClosedException()
            gate.accept(id)
            scope.async { withContext(NonCancellable) {operation()} }.also {active=it}
        }
        try {
            val result=worker.await();currentCoroutineContext().ensureActive()
            gate.finish(id,TaskTerminal.COMPLETED)
            return result
        } catch(error:Throwable) {
            withContext(NonCancellable) {gate.finish(id,if(error is CancellationException)TaskTerminal.CANCELLED else TaskTerminal.FAILED)}
            throw error
        } finally {
            withContext(NonCancellable) {
                worker.cancel();worker.join()
                mutex.withLock {active=null;gate.release(id)}
            }
        }
    }
    suspend fun close() {
        mutex.withLock {closed=true;active?.cancel()}
        gate.close()
        mutex.withLock {
            if(!resourcesReleased) {
                resourcesReleased=true
                try {releaseResources()} finally {scope.cancel()}
            }
        }
    }
}
