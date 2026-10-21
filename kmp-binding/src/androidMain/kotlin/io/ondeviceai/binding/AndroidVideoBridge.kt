package io.ondeviceai.binding

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.video.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/** A host file lease; release may suspend while native resources are being relinquished. */
interface AndroidVideoInputLease {
    val file: File
    suspend fun release()
}

interface AndroidVideoInputProvider {
    fun acquire(reference: String): AndroidVideoInputLease
}

/** Owns the optional client. All admission and callbacks use the supplied UI dispatcher. */
class AndroidVideoBridge(
    private val client: VideoProcessingClient?,
    private val inputs: AndroidVideoInputProvider,
    scope: CoroutineScope,
) : VideoBridge {
    // Cleanup outlives the host job. This scope also serves releases after close.
    private val scope = CoroutineScope(SupervisorJob() + scope.coroutineContext.minusKey(Job))
    private val workers = mutableSetOf<VideoTask>()
    private var active: VideoTask? = null
    private var closed = false
    private var closeStarted = false
    private var closeFinished = false
    private var closeFailure: VideoBridgeFailure? = null
    private val closeWaiters = mutableListOf<VideoBridgeCloseCallback>()
    private class Output(val lease: VideoOutputLease) {
        val waiters = mutableListOf<VideoBridgeReleaseCallback>()
        var releasing = false
    }
    private val outputs = mutableMapOf<String, Output>()
    // A private nonce plus issued sequence recognizes released tokens without tombstones.
    private val outputPrefix = "video-${UUID.randomUUID()}:"
    private var issuedOutputs = 0L

    override fun queryBackends(callback: VideoBridgeBackendsCallback): BridgeTask {
        admission(false)?.let { callback.onFailure(it); return CompletedVideoTask }
        val client = client ?: run { callback.onFailure(fail(VideoBridgeFailureCode.BACKEND_UNAVAILABLE, "backendNotLinked")); return CompletedVideoTask }
        return submit(false, operation = {
            client.listBackends().map { descriptor ->
                VideoBridgeBackend(descriptor.backendId,
                    BridgeAvailability(descriptor.availability.status == CapabilityStatus.AVAILABLE,
                        descriptor.availability.reason?.name, descriptor.backendId, descriptor.availability.minOS),
                    descriptor.supportedScaleFactors.toList(), descriptor.supportedAudioPolicies.map {
                        if (it == VideoAudioPolicy.PRESERVE) VideoBridgeAudioPolicy.PRESERVE else VideoBridgeAudioPolicy.DISCARD
                    })
            }
        }, result = callback::onResult, failure = callback::onFailure, cancelled = callback::onCancelled)
    }

    override fun process(request: VideoBridgeRequest, selection: VideoBridgeSelection, callback: VideoBridgeProcessCallback): BridgeTask {
        admission(true)?.let { callback.onFailure(it); return CompletedVideoTask }
        val client = client ?: run { callback.onFailure(fail(VideoBridgeFailureCode.BACKEND_UNAVAILABLE, "backendNotLinked")); return CompletedVideoTask }
        val input = try { inputs.acquire(request.inputReference) } catch (_: Exception) {
            callback.onFailure(fail(VideoBridgeFailureCode.INVALID_INPUT)); return CompletedVideoTask
        }
        val handle = VideoTask()
        return submit(true, handle, operation = {
            var result: VideoProcessingResult? = null
            var error: Exception? = null
            try {
                coroutineContext.ensureActive()
                result = client.process(VideoProcessingRequest(input.file, request.scaleFactor,
                    if (request.audioPolicy == VideoBridgeAudioPolicy.PRESERVE) VideoAudioPolicy.PRESERVE else VideoAudioPolicy.DISCARD),
                    when (selection) {
                        VideoBridgeSelection.Default -> VideoBackendSelection.Default
                        is VideoBridgeSelection.Explicit -> VideoBackendSelection.Explicit(selection.backendId)
                    }) { update ->
                    // Native progress arrives from its worker, never invoke UI callbacks there.
                    scope.launch {
                        if (active === handle && !handle.cancelled && !closed && update.completedFrames >= handle.completedFrames) {
                            handle.completedFrames = update.completedFrames
                            callback.onProgress(VideoBridgeProgress(update.completedFrames, update.totalFrames))
                        }
                    }
                }
            } catch (failure: Exception) { error = failure }
            withContext(NonCancellable) {
                try { client.drain(); input.release() }
                catch (failure: Exception) { error = failure }
                if (error != null || handle.cancelled || closed) {
                    // No token has escaped. A late result still belongs to this worker.
                    try { result?.output?.release() } catch (failure: Exception) { error = failure }
                    throw error ?: CancellationException()
                }
            }
            checkNotNull(result)
        }, result = { value ->
            val reference = outputPrefix + (++issuedOutputs)
            outputs[reference] = Output(value.output)
            val info = value.output.info
            callback.onResult(VideoBridgeOutput(reference, value.backendId, info.width, info.height,
                info.frameCount, info.durationSeconds, info.audioTrackCount))
        }, failure = callback::onFailure, cancelled = callback::onCancelled,
            discard = { it.output.release() })
    }

    /** Native host-only resolver. A releasing/released output cannot start a new read. */
    fun outputFile(reference: String): File? = outputs[reference]?.takeUnless { it.releasing }?.lease?.file

    override fun releaseOutput(reference: String, callback: VideoBridgeReleaseCallback) {
        val output = outputs[reference]
        if (output == null) {
            if (wasIssued(reference)) callback.onReleased()
            else callback.onFailure(fail(VideoBridgeFailureCode.INVALID_INPUT))
            return
        }
        output.waiters += callback
        if (output.releasing) return
        output.releasing = true
        scope.launch {
            var failure: VideoBridgeFailure? = null
            try { output.lease.release() } catch (error: Exception) { failure = map(error) }
            if (failure == null) outputs.remove(reference) else output.releasing = false
            val waiters = output.waiters.toList()
            output.waiters.clear()
            waiters.forEach { waiter -> failure?.let(waiter::onFailure) ?: waiter.onReleased() }
        }
    }

    override fun close(callback: VideoBridgeCloseCallback) {
        if (closeFinished) { closeFailure?.let(callback::onFailure) ?: callback.onClosed(); return }
        closed = true
        closeWaiters += callback
        if (closeStarted) return
        closeStarted = true
        val pending = workers.toList()
        pending.forEach { it.cancel() }
        scope.launch {
            pending.forEach { it.worker?.join() }
            try { client?.close() } catch (error: Exception) { closeFailure = map(error) }
            closeFinished = true
            val waiters = closeWaiters.toList()
            closeWaiters.clear()
            waiters.forEach { waiter -> closeFailure?.let(waiter::onFailure) ?: waiter.onClosed() }
        }
    }

    private fun admission(process: Boolean): VideoBridgeFailure? = when {
        closed -> fail(VideoBridgeFailureCode.CLOSED)
        process && active != null -> fail(VideoBridgeFailureCode.BUSY)
        else -> null
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun <T> submit(process: Boolean, handle: VideoTask = VideoTask(), operation: suspend () -> T,
                           result: (T) -> Unit, failure: (VideoBridgeFailure) -> Unit, cancelled: () -> Unit,
                           discard: suspend (T) -> Unit = {}): BridgeTask {
        workers += handle
        if (process) active = handle
        // ATOMIC guarantees cleanup even if cancelled before its first dispatched instruction.
        handle.worker = scope.launch(start = CoroutineStart.ATOMIC) {
            var outcome = try { Result.success(operation()) } catch (error: Exception) { Result.failure(error) }
            // Finish and cancel contend for one decision. A cancelled successful result
            // is still ours to release, even if cancellation raced operation return.
            val wasCancelled = handle.finish() || closed
            if (wasCancelled && outcome.isSuccess) {
                try { withContext(NonCancellable) { discard(outcome.getOrThrow()) } }
                catch (error: Exception) { outcome = Result.failure(error) }
            }
            workers -= handle
            if (active === handle) active = null
            val error = outcome.exceptionOrNull()
            if (error != null && error !is CancellationException) failure(map(error))
            else if (wasCancelled || error is CancellationException) cancelled()
            else result(outcome.getOrThrow())
        }
        return handle
    }

    private fun wasIssued(reference: String): Boolean {
        if (!reference.startsWith(outputPrefix)) return false
        val suffix = reference.removePrefix(outputPrefix)
        val number = suffix.toLongOrNull() ?: return false
        return number in 1..issuedOutputs && suffix == number.toString()
    }

    private fun map(error: Throwable): VideoBridgeFailure = when (error) {
        is VideoClientError.Closed -> fail(VideoBridgeFailureCode.CLOSED)
        is VideoClientError.Busy -> fail(VideoBridgeFailureCode.BUSY)
        is VideoClientError.BackendNotRegistered -> fail(VideoBridgeFailureCode.BACKEND_NOT_REGISTERED)
        is VideoClientError.BackendUnavailable -> fail(VideoBridgeFailureCode.BACKEND_UNAVAILABLE, error.reason.name)
        is VideoClientError.UnsupportedScaleFactor -> fail(VideoBridgeFailureCode.UNSUPPORTED_SCALE_FACTOR)
        is VideoClientError.UnsupportedAudioPolicy -> fail(VideoBridgeFailureCode.UNSUPPORTED_AUDIO_POLICY)
        else -> VideoBridgeFailure(VideoBridgeFailureCode.PLATFORM, null, "android.video", 1)
    }
    private fun fail(code: VideoBridgeFailureCode, reason: String? = null) = VideoBridgeFailure(code, reason)
    private class VideoTask : BridgeTask {
        private val control = Any()
        private val requested = AtomicBoolean()
        private var finished = false
        val cancelled get() = requested.get()
        var completedFrames = -1
        @Volatile var worker: Job? = null
            set(value) { field = value; if (cancelled) value?.cancel() }
        fun finish(): Boolean = synchronized(control) { finished = true; cancelled }
        override fun cancel() {
            val task = synchronized(control) {
                if (finished) return
                requested.set(true)
                worker
            }
            task?.cancel()
        }
    }
    private object CompletedVideoTask : BridgeTask { override fun cancel() = Unit }
}
