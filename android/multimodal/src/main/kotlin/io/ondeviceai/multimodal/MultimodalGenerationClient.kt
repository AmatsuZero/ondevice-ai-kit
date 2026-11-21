package io.ondeviceai.multimodal

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import kotlinx.coroutines.*

/**
 * Owns its backends and coordinates one generation including admission and mandatory drain.
 * Update callbacks run serially under the coordination lock and must not block.
 */
class MultimodalGenerationClient(private val defaultBackendId: String, backends: List<MultimodalGenerationBackend>) {
    private sealed interface Request {
        data class Image(val value: MultimodalRequest) : Request
        data class Video(val value: MultimodalVideoRequest) : Request
    }

    private class Outcome(val output: MultimodalTextOutput?, val error: Throwable?)
    private class Operation(val update: ((MultimodalTextUpdate) -> Unit)?, val callerJob: Job?) {
        lateinit var worker: Job
        val result = CompletableDeferred<Outcome>()
        val done = CompletableDeferred<Unit>()
        var lastSequence = -1
    }

    private val table: Map<String, MultimodalGenerationBackend>
    private val lock = Any()
    private var closed = false
    private var active: Operation? = null
    private var queries = 0
    private var queriesIdle = CompletableDeferred(Unit)
    private var closeCompletion: CompletableDeferred<Unit>? = null

    init {
        if (backends.any { it.backendId.isBlank() } || backends.map { it.backendId }.toSet().size != backends.size) {
            throw MultimodalClientError.InvalidRegistration("empty or duplicate backend ID")
        }
        table = backends.associateBy { it.backendId }.toSortedMap()
        if (defaultBackendId !in table) throw MultimodalClientError.InvalidRegistration("default backend is not registered")
    }

    suspend fun listBackends(): List<MultimodalBackendDescriptor> {
        synchronized(lock) {
            if (closed) throw MultimodalClientError.Closed()
            if (queries++ == 0) queriesIdle = CompletableDeferred()
        }
        try {
            return table.values.map { backend ->
                val availability = backend.availability()
                currentCoroutineContext().ensureActive()
                synchronized(lock) { if (closed) throw MultimodalClientError.Closed() }
                MultimodalBackendDescriptor(backend.backendId, availability)
            }
        } finally {
            synchronized(lock) { if (--queries == 0) queriesIdle.complete(Unit) }
        }
    }

    suspend fun generate(
        request: MultimodalRequest,
        backend: MultimodalBackendSelection = MultimodalBackendSelection.Default,
        update: ((MultimodalTextUpdate) -> Unit)? = null
    ): MultimodalGenerationResult = generate(Request.Image(request), backend, update)

    suspend fun generate(
        request: MultimodalVideoRequest,
        backend: MultimodalBackendSelection = MultimodalBackendSelection.Default,
        update: ((MultimodalTextUpdate) -> Unit)? = null
    ): MultimodalGenerationResult = generate(Request.Video(request), backend, update)

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun generate(
        request: Request,
        backend: MultimodalBackendSelection,
        update: ((MultimodalTextUpdate) -> Unit)?
    ): MultimodalGenerationResult {
        val callerContext = currentCoroutineContext()
        callerContext.ensureActive()
        val (operation, selected) = synchronized(lock) {
            if (closed) throw MultimodalClientError.Closed()
            if (active != null) throw MultimodalClientError.Busy()
            val id = when (backend) {
                MultimodalBackendSelection.Default -> defaultBackendId
                is MultimodalBackendSelection.Explicit -> backend.backendId
            }
            val selected = table[id] ?: throw MultimodalClientError.BackendNotRegistered(id)
            if (request is Request.Video && selected !is MultimodalVideoGenerationBackend) {
                throw MultimodalClientError.InvalidInput("selected backend does not support video")
            }
            val operation = Operation(update, callerContext[Job])
            active = operation
            // ATOMIC guarantees the cleanup path even if close cancels before dispatch.
            operation.worker = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.ATOMIC) {
                run(request, selected, operation)
            }
            operation to selected
        }
        try {
            val outcome = operation.result.await()
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (closed || operation.worker.isCancelled) throw CancellationException("multimodal generation cancelled")
                outcome.error?.let { throw it }
                return MultimodalGenerationResult(selected.backendId, checkNotNull(outcome.output))
            }
        } catch (error: Throwable) {
            operation.worker.cancel()
            throw error
        } finally {
            withContext(NonCancellable) {
                operation.worker.join()
                synchronized(lock) { active = null; operation.done.complete(Unit) }
            }
        }
    }

    private suspend fun run(request: Request, backend: MultimodalGenerationBackend, operation: Operation) {
        var output: MultimodalTextOutput? = null
        var error: Throwable? = null
        var started = false
        try {
            val availability = backend.availability()
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (operation.callerJob?.isCancelled == true || operation.worker.isCancelled) throw CancellationException("multimodal generation cancelled")
            }
            if (availability.status != CapabilityStatus.AVAILABLE) {
                throw MultimodalClientError.BackendUnavailable(
                    backend.backendId, availability.reason ?: UnavailableReason.BACKEND_NOT_LINKED)
            }
            started = true
            val update: (MultimodalTextUpdate) -> Unit = { value -> publish(operation, value) }
            output = when (request) {
                is Request.Image -> backend.generate(request.value, update)
                is Request.Video -> (backend as MultimodalVideoGenerationBackend).generateVideo(request.value, update)
            }
        } catch (failure: Throwable) { error = failure }
        withContext(NonCancellable) {
            if (started) {
                try { backend.drain() } catch (failure: Throwable) {
                    error?.let { if (it !== failure) failure.addSuppressed(it) }
                    error = failure
                }
            }
            operation.result.complete(Outcome(output, error))
        }
    }

    private fun publish(operation: Operation, value: MultimodalTextUpdate) {
        synchronized(lock) {
            if (closed || active !== operation || operation.callerJob?.isCancelled == true || operation.worker.isCancelled || value.sequence <= operation.lastSequence) return
            operation.lastSequence = value.sequence
            operation.update?.invoke(value)
        }
    }

    suspend fun drain() {
        val completion = synchronized(lock) { active?.done }
        withContext(NonCancellable) { completion?.await() }
    }

    suspend fun close() {
        val (completion, ownsClose) = synchronized(lock) {
            closed = true
            active?.worker?.cancel()
            closeCompletion?.let { it to false } ?: CompletableDeferred<Unit>().let {
                closeCompletion = it
                it to true
            }
        }
        withContext(NonCancellable) {
            if (ownsClose) {
                drain()
                synchronized(lock) { queriesIdle }.await()
                var error: Throwable? = null
                for (backend in table.values) {
                    try { backend.close() } catch (failure: Throwable) {
                        if (error == null) error = failure else if (error !== failure) error.addSuppressed(failure)
                    }
                }
                if (error == null) completion.complete(Unit) else completion.completeExceptionally(error)
            }
            completion.await()
        }
    }
}
