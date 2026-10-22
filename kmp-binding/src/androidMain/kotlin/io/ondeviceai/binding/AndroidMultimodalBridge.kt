package io.ondeviceai.binding

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.multimodal.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

interface AndroidMultimodalInputLease {
    val file: File
    suspend fun release()
}

interface AndroidMultimodalInputProvider {
    fun acquire(reference: String): AndroidMultimodalInputLease
}

/** Owns the optional native client. Admission, providers and callbacks use the host UI dispatcher. */
class AndroidMultimodalBridge(
    private val client: MultimodalGenerationClient?,
    private val inputs: AndroidMultimodalInputProvider,
    scope: CoroutineScope,
) : MultimodalBridge {
    // Detached ownership ensures host Job cancellation cannot skip native drain/lease cleanup.
    private val scope = CoroutineScope(SupervisorJob() + scope.coroutineContext.minusKey(Job))
    private val workers = mutableSetOf<MultimodalTask>()
    private var active: MultimodalTask? = null
    private var closed = false
    private var closeStarted = false
    private var closeFinished = false
    private var closeFailure: MultimodalBridgeFailure? = null
    private val closeWaiters = mutableListOf<MultimodalBridgeCloseCallback>()

    override fun queryMultimodalBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask {
        admission(false)?.let { callback.onMultimodalFailure(it); return CompletedTask }
        val client = client ?: run { callback.onMultimodalFailure(unlinked()); return CompletedTask }
        return submit(false, operation = {
            coroutineContext.ensureActive()
            client.listBackends().map { descriptor ->
                MultimodalBridgeBackend(descriptor.backendId,
                    BridgeAvailability(descriptor.availability.status == CapabilityStatus.AVAILABLE,
                        descriptor.availability.reason?.name, descriptor.backendId, descriptor.availability.minOS))
            }
        }, result = callback::onMultimodalBackends, failure = callback::onMultimodalFailure,
            cancelled = callback::onMultimodalCancelled)
    }

    override fun generateMultimodal(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection,
                                   callback: MultimodalBridgeGenerateCallback): BridgeTask {
        admission(true)?.let { callback.onMultimodalFailure(it); return CompletedTask }
        val client = client ?: run { callback.onMultimodalFailure(unlinked()); return CompletedTask }
        val input = try { inputs.acquire(request.inputReference) } catch (_: Exception) {
            callback.onMultimodalFailure(fail(MultimodalBridgeFailureCode.INVALID_INPUT)); return CompletedTask
        }
        val handle = MultimodalTask()
        return submit(true, handle, operation = {
            try {
                coroutineContext.ensureActive()
                client.generate(MultimodalRequest(input.file, request.prompt, request.maxOutputTokens),
                    when (selection) {
                        MultimodalBridgeSelection.Default -> MultimodalBackendSelection.Default
                        is MultimodalBridgeSelection.Explicit -> MultimodalBackendSelection.Explicit(selection.backendId)
                    }) { update ->
                    scope.launch {
                        if (active === handle && !closed && !handle.cancelled && update.sequence > handle.lastSequence) {
                            handle.lastSequence = update.sequence
                            callback.onMultimodalUpdate(MultimodalBridgeUpdate(update.sequence, update.text))
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    try { client.drain() } finally { input.release() }
                }
            }
        }, result = { value ->
            val model = value.output.model
            callback.onMultimodalResult(MultimodalBridgeOutput(value.backendId, value.output.text,
                MultimodalBridgeIdentity(model.modelId, model.modelRevision, model.runtimeRevision)))
        }, failure = callback::onMultimodalFailure, cancelled = callback::onMultimodalCancelled)
    }

    override fun closeMultimodal(callback: MultimodalBridgeCloseCallback) {
        if (closeFinished) { closeFailure?.let(callback::onMultimodalFailure) ?: callback.onMultimodalClosed(); return }
        closed = true; closeWaiters += callback
        if (closeStarted) return
        closeStarted = true
        val pending = workers.toList()
        pending.forEach { it.cancel() }
        scope.launch {
            pending.forEach { it.worker?.join() }
            try { client?.close() } catch (error: Exception) { closeFailure = map(error) }
            closeFinished = true
            val waiters = closeWaiters.toList(); closeWaiters.clear()
            waiters.forEach { waiter -> closeFailure?.let(waiter::onMultimodalFailure) ?: waiter.onMultimodalClosed() }
            scope.cancel()
        }
    }

    private fun admission(generate: Boolean): MultimodalBridgeFailure? = when {
        closed -> fail(MultimodalBridgeFailureCode.CLOSED)
        generate && active != null -> fail(MultimodalBridgeFailureCode.BUSY)
        else -> null
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun <T> submit(generate: Boolean, handle: MultimodalTask = MultimodalTask(), operation: suspend () -> T,
                           result: (T) -> Unit, failure: (MultimodalBridgeFailure) -> Unit, cancelled: () -> Unit): BridgeTask {
        workers += handle
        if (generate) active = handle
        // ATOMIC starts even if cancel wins before dispatch, guaranteeing input finally cleanup.
        handle.worker = scope.launch(start = CoroutineStart.ATOMIC) {
            val outcome = try { Result.success(operation()) } catch (error: Exception) { Result.failure(error) }
            val wasCancelled = handle.finish() || closed
            workers -= handle
            if (active === handle) active = null
            val error = outcome.exceptionOrNull()
            // Cancellation owns the terminal even when native completion/error arrived late.
            if (wasCancelled || error is CancellationException) cancelled()
            else if (error != null) failure(map(error))
            else result(outcome.getOrThrow())
        }
        return handle
    }

    private fun map(error: Throwable): MultimodalBridgeFailure = when (error) {
        is MultimodalClientError.InvalidInput -> fail(MultimodalBridgeFailureCode.INVALID_INPUT)
        is MultimodalClientError.Closed -> fail(MultimodalBridgeFailureCode.CLOSED)
        is MultimodalClientError.Busy -> fail(MultimodalBridgeFailureCode.BUSY)
        is MultimodalClientError.BackendNotRegistered -> fail(MultimodalBridgeFailureCode.BACKEND_NOT_REGISTERED)
        is MultimodalClientError.BackendUnavailable -> fail(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, error.reason.name)
        else -> MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, null, "android.multimodal", 1)
    }
    private fun fail(code: MultimodalBridgeFailureCode, reason: String? = null) = MultimodalBridgeFailure(code, reason)
    private fun unlinked() = fail(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, "backendNotLinked")
    private class MultimodalTask : BridgeTask {
        private val control = Any()
        private val requested = AtomicBoolean()
        private var finished = false
        val cancelled get() = requested.get()
        var lastSequence = -1
        @Volatile var worker: Job? = null
            set(value) { field = value; if (cancelled) value?.cancel() }
        fun finish(): Boolean = synchronized(control) { finished = true; cancelled }
        override fun cancel() {
            val job = synchronized(control) {
                if (finished) return
                requested.set(true); worker
            }
            job?.cancel()
        }
    }
    private object CompletedTask : BridgeTask { override fun cancel() = Unit }
}
