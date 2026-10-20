package io.ondeviceai.binding

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.ClosedException
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.speech.SpeechBackendSelection
import io.ondeviceai.speech.SpeechClientError
import io.ondeviceai.speech.SpeechCapability
import io.ondeviceai.speech.SpeechRecognitionClient
import io.ondeviceai.speech.SpeechRecognitionRequest
import io.ondeviceai.speech.SpeechSynthesisClient
import io.ondeviceai.speech.SpeechSynthesisRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Android adapter for the capability-scoped speech clients.
 *
 * The clients are transferred to this object and are closed exactly once by [close].
 * Calls are admitted on the host's main scope; callbacks run on that same dispatcher.
 */
class AndroidSpeechBridge(
    private val tts: SpeechSynthesisClient?,
    private val asr: SpeechRecognitionClient?,
    scope: CoroutineScope,
) : SpeechBridge {
    private val gate = Any()
    private val workerScope = CoroutineScope(
        SupervisorJob() + scope.coroutineContext.minusKey(Job),
    )
    private val workers = mutableSetOf<Job>()
    private var audioWorker: Job? = null
    private var closed = false
    private var closeWorker: Job? = null
    private val closeWaiters = mutableListOf<SpeechBridgeCloseCallback>()
    private var closeFailure: SpeechBridgeFailure? = null
    private var closeFinished = false

    override fun queryBackends(
        capability: SpeechBridgeCapability,
        callback: SpeechBridgeBackendsCallback,
    ): BridgeTask {
        synchronized(gate) {
            if (closed) {
                callback.onFailure(closedFailure())
                return CompletedSpeechTask
            }
            val job = workerScope.launch(start = CoroutineStart.LAZY) {
                var terminal: QueryTerminal? = null
                try {
                    val descriptors = when (capability) {
                        SpeechBridgeCapability.TTS -> tts?.listBackends()
                        SpeechBridgeCapability.ASR -> asr?.listBackends()
                    } ?: throw MissingClientException(capability)
                    coroutineContext.ensureActive()
                    terminal = QueryTerminal.Result(descriptors.map { it.toBridgeBackend() })
                } catch (_: CancellationException) {
                    terminal = QueryTerminal.Cancelled
                } catch (error: Exception) {
                    terminal = QueryTerminal.Failure(mapFailure(error))
                } finally {
                    val self = coroutineContext[Job]
                    try {
                        terminal?.let { outcome ->
                            when (outcome) {
                                is QueryTerminal.Result -> callback.onResult(outcome.backends)
                                is QueryTerminal.Failure -> callback.onFailure(outcome.failure)
                                QueryTerminal.Cancelled -> callback.onCancelled()
                            }
                        }
                    } finally {
                        synchronized(gate) { if (self != null) workers.remove(self) }
                    }
                }
            }
            workers += job
            job.start()
            return JobSpeechTask(job)
        }
    }

    override fun speak(
        request: SpeechBridgeTtsRequest,
        selection: SpeechBridgeSelection,
        callback: SpeechBridgeTtsCallback,
    ): BridgeTask = startAudio(callback) {
        if (request.text.isBlank() || request.locale.isBlank()) {
            throw InvalidSpeechRequestException()
        }
        val client = tts ?: throw MissingClientException(SpeechBridgeCapability.TTS)
        val result = client.speak(
            SpeechSynthesisRequest(request.text, request.locale, request.voiceId),
            selection.native(),
        )
        coroutineContext.ensureActive()
        AudioTerminal.Result(SpeechBridgeTtsResult(result.backendId, result.voiceId))
    }

    override fun recognize(
        request: SpeechBridgeAsrRequest,
        selection: SpeechBridgeSelection,
        callback: SpeechBridgeAsrCallback,
    ): BridgeTask {
        synchronized(gate) {
            if (closed) {
                callback.onFailure(closedFailure())
                return CompletedSpeechTask
            }
        }
        if (request.locale.isBlank()) {
            callback.onFailure(invalidInputFailure())
            return CompletedSpeechTask
        }
        if (request.input !is SpeechBridgeInput.Microphone) {
            callback.onFailure(unsupportedInputFailure())
            return CompletedSpeechTask
        }
        return startAudio(callback) {
            val client = asr ?: throw MissingClientException(SpeechBridgeCapability.ASR)
            val result = client.recognize(
                SpeechRecognitionRequest(request.locale),
                selection.native(),
            )
            coroutineContext.ensureActive()
            AudioTerminal.Result(SpeechBridgeAsrResult(result.backendId, result.transcript, result.confidence))
        }
    }

    override fun close(callback: SpeechBridgeCloseCallback) {
        synchronized(gate) {
            if (closeFinished) {
                closeFailure?.let(callback::onFailure) ?: callback.onClosed()
                return
            }
            closeWaiters += callback
            if (closeWorker != null) return
            closed = true
            val jobsToDrain = workers.toList()
            closeWorker = workerScope.launch(start = CoroutineStart.LAZY) {
                var failure: SpeechBridgeFailure? = null
                withContext(NonCancellable) {
                    jobsToDrain.forEach(Job::cancel)
                    jobsToDrain.joinAll()
                    closeClient(tts) { error -> if (failure == null) failure = mapFailure(error) }
                    closeClient(asr) { error -> if (failure == null) failure = mapFailure(error) }
                    val waiters = synchronized(gate) {
                        closeFailure = failure
                        closeFinished = true
                        closeWaiters.toList().also { closeWaiters.clear() }
                    }
                    waiters.forEach { waiter -> failure?.let(waiter::onFailure) ?: waiter.onClosed() }
                    workerScope.cancel()
                }
            }
            closeWorker?.start()
        }
    }

    private fun <T> startAudio(
        callback: AudioCallback<T>,
        operation: suspend () -> AudioTerminal<T>,
    ): BridgeTask {
        synchronized(gate) {
            if (closed) {
                callback.onFailure(closedFailure())
                return CompletedSpeechTask
            }
            if (audioWorker != null) {
                callback.onFailure(busyFailure())
                return CompletedSpeechTask
            }
            val job = workerScope.launch(start = CoroutineStart.LAZY) {
                var terminal: AudioTerminal<T>? = null
                try {
                    terminal = operation()
                } catch (_: CancellationException) {
                    terminal = AudioTerminal.Cancelled
                } catch (error: Exception) {
                    terminal = AudioTerminal.Failure(mapFailure(error))
                } finally {
                    val self = coroutineContext[Job]
                    synchronized(gate) {
                        if (audioWorker === self) audioWorker = null
                    }
                    try {
                        terminal?.let { outcome ->
                            when (outcome) {
                                is AudioTerminal.Result -> callback.onResult(outcome.value)
                                is AudioTerminal.Failure -> callback.onFailure(outcome.failure)
                                AudioTerminal.Cancelled -> callback.onCancelled()
                            }
                        }
                    } finally {
                        synchronized(gate) { if (self != null) workers.remove(self) }
                    }
                }
            }
            audioWorker = job
            workers += job
            job.start()
            return JobSpeechTask(job)
        }
    }

    private suspend fun closeClient(client: Any?, onFailure: (Throwable) -> Unit) {
        if (client == null) return
        try {
            when (client) {
                is SpeechSynthesisClient -> client.close()
                is SpeechRecognitionClient -> client.close()
            }
        } catch (error: Exception) {
            onFailure(error)
        }
    }

    private fun SpeechBridgeSelection.native() = when (this) {
        SpeechBridgeSelection.Default -> SpeechBackendSelection.Default
        is SpeechBridgeSelection.Explicit -> SpeechBackendSelection.Explicit(backendId)
    }

    private fun io.ondeviceai.speech.SpeechBackendDescriptor.toBridgeBackend() = SpeechBridgeBackend(
        capability = when (capability) {
            SpeechCapability.TTS -> SpeechBridgeCapability.TTS
            SpeechCapability.ASR -> SpeechBridgeCapability.ASR
        },
        backendId = backendId,
        availability = BridgeAvailability(
            available = availability.status == CapabilityStatus.AVAILABLE,
            reason = availability.reason?.name
                ?: availability.status.takeUnless { it == CapabilityStatus.AVAILABLE }?.name,
            backendId = backendId,
            minimumOS = availability.minOS,
        ),
        supportedInputs = if (capability == SpeechCapability.ASR) {
            listOf(SpeechBridgeInputKind.MICROPHONE)
        } else {
            emptyList()
        },
        offline = availability.offline,
    )

    private fun mapFailure(error: Throwable): SpeechBridgeFailure = when (error) {
        is MissingClientException -> SpeechBridgeFailure(
            SpeechBridgeFailureCode.BACKEND_UNAVAILABLE,
            UnavailableReason.BACKEND_NOT_LINKED.name,
        )
        is InvalidSpeechRequestException -> invalidInputFailure()
        is SpeechClientError.BackendNotRegistered -> SpeechBridgeFailure(
            SpeechBridgeFailureCode.BACKEND_NOT_REGISTERED,
            error.backendId,
        )
        is SpeechClientError.BackendUnavailable -> SpeechBridgeFailure(
            SpeechBridgeFailureCode.BACKEND_UNAVAILABLE,
            error.reason.name,
        )
        is ClosedException -> closedFailure()
        is IllegalArgumentException -> invalidInputFailure()
        is IllegalStateException -> if (error.message == "closed") closedFailure() else platformFailure()
        else -> platformFailure()
    }

    private fun closedFailure() = SpeechBridgeFailure(SpeechBridgeFailureCode.CLOSED, "closed")
    private fun busyFailure() = SpeechBridgeFailure(SpeechBridgeFailureCode.BUSY, "busy")
    private fun invalidInputFailure() = SpeechBridgeFailure(SpeechBridgeFailureCode.INVALID_INPUT, "invalid input")
    private fun unsupportedInputFailure() = SpeechBridgeFailure(SpeechBridgeFailureCode.UNSUPPORTED_INPUT, "audio file input is not supported on Android")
    private fun platformFailure() = SpeechBridgeFailure(SpeechBridgeFailureCode.PLATFORM, null, "android.speech", 1)

    private sealed interface QueryTerminal {
        data class Result(val backends: List<SpeechBridgeBackend>) : QueryTerminal
        data class Failure(val failure: SpeechBridgeFailure) : QueryTerminal
        data object Cancelled : QueryTerminal
    }

    private sealed interface AudioTerminal<out T> {
        data class Result<T>(val value: T) : AudioTerminal<T>
        data class Failure(val failure: SpeechBridgeFailure) : AudioTerminal<Nothing>
        data object Cancelled : AudioTerminal<Nothing>
    }

    private interface AudioCallback<T> {
        fun onResult(result: T)
        fun onFailure(failure: SpeechBridgeFailure)
        fun onCancelled()
    }

    private class MissingClientException(val capability: SpeechBridgeCapability) : IllegalStateException()
    private class InvalidSpeechRequestException : IllegalArgumentException()

    private object CompletedSpeechTask : BridgeTask {
        override fun cancel() = Unit
    }

    private class JobSpeechTask(private val job: Job) : BridgeTask {
        override fun cancel() { job.cancel() }
    }

    private fun startAudio(
        callback: SpeechBridgeTtsCallback,
        operation: suspend () -> AudioTerminal<SpeechBridgeTtsResult>,
    ): BridgeTask = startAudio(object : AudioCallback<SpeechBridgeTtsResult> {
        override fun onResult(result: SpeechBridgeTtsResult) = callback.onResult(result)
        override fun onFailure(failure: SpeechBridgeFailure) = callback.onFailure(failure)
        override fun onCancelled() = callback.onCancelled()
    }, operation)

    private fun startAudio(
        callback: SpeechBridgeAsrCallback,
        operation: suspend () -> AudioTerminal<SpeechBridgeAsrResult>,
    ): BridgeTask = startAudio(object : AudioCallback<SpeechBridgeAsrResult> {
        override fun onResult(result: SpeechBridgeAsrResult) = callback.onResult(result)
        override fun onFailure(failure: SpeechBridgeFailure) = callback.onFailure(failure)
        override fun onCancelled() = callback.onCancelled()
    }, operation)
}
