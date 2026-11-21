package io.ondeviceai.video

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import kotlinx.coroutines.*

/** One processing slot includes admission, mandatory drain and undelivered-output cleanup.
 * Progress callbacks are serialized on the coordination lock and must not block.
 */
class VideoProcessingClient(private val defaultBackendId: String, backends: List<VideoSuperResolutionBackend>) {
    private class Outcome(val output: VideoOutputLease?, val error: Throwable?)
    private class Operation(val progress: ((VideoProcessingProgress) -> Unit)?) {
        lateinit var worker: Job
        val result = CompletableDeferred<Outcome>()
        val done = CompletableDeferred<Unit>()
        var completedFrames = -1
    }

    private val table: Map<String, VideoSuperResolutionBackend>
    private val lock = Any()
    private var closed = false
    private var active: Operation? = null
    private var queries = 0
    private var queriesIdle = CompletableDeferred(Unit)
    private var closeCompletion: CompletableDeferred<Unit>? = null

    init {
        require(backends.all { it.backendId.isNotBlank() }) { "empty backend ID" }
        require(backends.map { it.backendId }.toSet().size == backends.size) { "duplicate backend ID" }
        table = backends.associateBy { it.backendId }.toSortedMap()
        require(defaultBackendId in table) { "default backend is not registered" }
    }

    suspend fun listBackends(): List<VideoBackendDescriptor> {
        synchronized(lock) {
            if (closed) throw VideoClientError.Closed()
            if (queries++ == 0) queriesIdle = CompletableDeferred()
        }
        try {
            return table.values.map { backend ->
                val availability = backend.availability()
                synchronized(lock) { if (closed) throw VideoClientError.Closed() }
                currentCoroutineContext().ensureActive()
                VideoBackendDescriptor(backend.backendId, availability,
                    backend.supportedScaleFactors.toList(), backend.supportedAudioPolicies.toList())
            }
        } finally {
            synchronized(lock) { if (--queries == 0) queriesIdle.complete(Unit) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun process(request: VideoProcessingRequest, backend: VideoBackendSelection = VideoBackendSelection.Default,
                        progress: ((VideoProcessingProgress) -> Unit)? = null): VideoProcessingResult {
        currentCoroutineContext().ensureActive()
        val (operation, selected) = synchronized(lock) {
            if (closed) throw VideoClientError.Closed()
            if (active != null) throw VideoClientError.Busy()
            val id = when (backend) {
                VideoBackendSelection.Default -> defaultBackendId
                is VideoBackendSelection.Explicit -> backend.backendId
            }
            val selected = table[id] ?: throw VideoClientError.BackendNotRegistered(id)
            val operation = Operation(progress)
            active = operation
            // ATOMIC ensures the worker's try/finally runs even if close cancels before dispatch.
            operation.worker = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.ATOMIC) {
                run(request, selected, operation)
            }
            operation to selected
        }
        var outcome: Outcome? = null
        try {
            outcome = operation.result.await()
            outcome.error?.let { throw it }
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (closed || operation.worker.isCancelled) throw CancellationException("video processing cancelled")
                return VideoProcessingResult(selected.backendId, checkNotNull(outcome.output))
            }
        } catch (error: Throwable) {
            operation.worker.cancel()
            withContext(NonCancellable) {
                val settled = outcome ?: operation.result.await()
                // Cleanup failure overrides cancellation rather than silently losing output ownership.
                settled.output?.release()
            }
            throw error
        } finally {
            withContext(NonCancellable) {
                operation.worker.join()
                synchronized(lock) { active = null; operation.done.complete(Unit) }
            }
        }
    }

    private suspend fun run(request: VideoProcessingRequest, backend: VideoSuperResolutionBackend, operation: Operation) {
        var output: VideoOutputLease? = null
        var error: Throwable? = null
        var started = false
        try {
            val availability = backend.availability()
            currentCoroutineContext().ensureActive()
            if (availability.status != CapabilityStatus.AVAILABLE) {
                throw VideoClientError.BackendUnavailable(backend.backendId, availability.reason ?: UnavailableReason.BACKEND_NOT_LINKED)
            }
            if (request.scaleFactor !in backend.supportedScaleFactors) throw VideoClientError.UnsupportedScaleFactor(request.scaleFactor)
            if (request.audioPolicy !in backend.supportedAudioPolicies) throw VideoClientError.UnsupportedAudioPolicy(request.audioPolicy)
            started = true
            output = backend.process(request) { update -> publish(operation, update) }
        } catch (failure: Throwable) { error = failure }
        withContext(NonCancellable) {
            if (started) {
                try { backend.drain() } catch (failure: Throwable) {
                    error?.let(failure::addSuppressed)
                    error = failure
                }
            }
            operation.result.complete(Outcome(output, error))
        }
    }

    private fun publish(operation: Operation, update: VideoProcessingProgress) {
        synchronized(lock) {
            if (closed || active !== operation || operation.worker.isCancelled || update.completedFrames < operation.completedFrames) return
            operation.completedFrames = update.completedFrames
            operation.progress?.invoke(update)
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
                var error: Throwable? = null
                drain()
                synchronized(lock) { queriesIdle }.await()
                for (backend in table.values) {
                    try { backend.close() } catch (failure: Throwable) {
                        if (error == null) error = failure else error.addSuppressed(failure)
                    }
                }
                if (error == null) completion.complete(Unit) else completion.completeExceptionally(error)
            }
            completion.await()
        }
    }
}
