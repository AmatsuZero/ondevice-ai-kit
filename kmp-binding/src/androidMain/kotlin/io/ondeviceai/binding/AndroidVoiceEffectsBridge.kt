package io.ondeviceai.binding

import io.ondeviceai.voiceeffects.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AndroidVoiceEffectsInputLease {
    val file: File
    suspend fun release()
}
interface AndroidVoiceEffectsInputProvider {
    fun acquire(reference: String): AndroidVoiceEffectsInputLease
}

/** All calls and callbacks use the host's UI dispatcher; IO belongs to the native client. */
class AndroidVoiceEffectsBridge(
    private val client: VoiceEffectsClient?,
    private val inputs: AndroidVoiceEffectsInputProvider,
    scope: CoroutineScope,
) : VoiceEffectsBridge {
    private val scope = CoroutineScope(SupervisorJob() + scope.coroutineContext.minusKey(Job))
    private val prefix = "voice-${UUID.randomUUID()}:"
    private var issued = 0L
    private var closed = false
    private var active: Operation? = null
    private var closing = false
    private var closeFinished = false
    private val closeWaiters = mutableListOf<VoiceEffectsBridgeCloseCallback>()
    private class Output(val value: VoiceEffectsOutput) {
        var releasing = false
        val waiters = mutableListOf<VoiceEffectsBridgeReleaseCallback>()
    }
    private val outputs = mutableMapOf<String, Output>()
    private inner class Operation : BridgeTask {
        var job: Job? = null
        var cancelled = false
        var finished = false
        var discarded: VoiceEffectsOutput? = null
        var discardDone: CompletableDeferred<Unit>? = null
        var input: AndroidVoiceEffectsInputLease? = null
        var nativeReturned = false
        var inputReleased = false
        val inputDone = CompletableDeferred<Unit>()
        val cleanup = Mutex()
        override fun cancel() {
            if (finished) return
            cancelled = true
            job?.cancel()
            // Never join first: native process can be waiting for drain's cleanup retry.
            scope.launch { recover(this@Operation) }
        }
    }
    override fun process(request: VoiceEffectsBridgeRequest, callback: VoiceEffectsBridgeProcessCallback): BridgeTask {
        val rejection = when {
            closed -> fail(VoiceEffectsBridgeFailureCode.CLOSED)
            active != null -> fail(VoiceEffectsBridgeFailureCode.BUSY)
            client == null -> fail(VoiceEffectsBridgeFailureCode.BACKEND_UNAVAILABLE, "backendNotLinked")
            else -> null
        }
        if (rejection != null) { callback.onFailure(rejection); return Done }
        val native = checkNotNull(client)
        val input = try { inputs.acquire(request.inputReference) } catch (_: Exception) {
            callback.onFailure(fail(VoiceEffectsBridgeFailureCode.INVALID_INPUT)); return Done
        }
        val operation = Operation()
        operation.input = input
        active = operation
        @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
        val worker = scope.launch(start = CoroutineStart.ATOMIC) {
            var output: VoiceEffectsOutput? = null
            var error: Exception? = null
            try {
                currentCoroutineContext().ensureActive()
                output = native.process(VoiceEffectsRequest(input.file, when (request.preset) {
                    VoiceEffectsBridgePreset.ORIGINAL -> VoiceEffectsPreset.ORIGINAL
                    VoiceEffectsBridgePreset.HIGH -> VoiceEffectsPreset.HIGH
                    VoiceEffectsBridgePreset.LOW -> VoiceEffectsPreset.LOW
                }))
            } catch (failure: Exception) { error = failure }
            withContext(NonCancellable) {
                // Native process returned only after releasing input. drain is still
                // required for cancelled-before-start and native cleanup recovery.
                operation.nativeReturned = true
                recover(operation)
                operation.inputDone.await()
                if ((operation.cancelled || closed) && output != null) {
                    operation.discarded = output
                    operation.discardDone = CompletableDeferred()
                    recover(operation)
                    operation.discardDone!!.await()
                }
            }
            operation.finished = true
            if (active === operation) active = null
            if (operation.cancelled || closed || error is CancellationException) callback.onCancelled()
            else if (error != null) callback.onFailure(map(checkNotNull(error)))
            else {
                val value = checkNotNull(output)
                val reference = prefix + (++issued)
                outputs[reference] = Output(value)
                callback.onResult(VoiceEffectsBridgeOutput(reference, value.backendId, value.sampleRate,
                    value.frameCount, value.durationSeconds))
            }
        }
        operation.job = worker
        if (operation.cancelled) worker.cancel()
        return operation
    }
    private suspend fun recover(operation: Operation): Boolean {
        return try {
            client?.drain()
            operation.cleanup.withLock {
                if (operation.nativeReturned && !operation.inputReleased) {
                    operation.input?.release()
                    operation.inputReleased = true
                    operation.inputDone.complete(Unit)
                }
                operation.discarded?.let {
                    client?.releaseOutput(it.id)
                    operation.discarded = null
                    operation.discardDone?.complete(Unit)
                }
            }
            true
        } catch (_: Exception) {
            // Retain ownership and BUSY. A repeated cancel or close retries the
            // exact pending cleanup; no terminal is claimed while files survive.
            false
        }
    }
    fun outputFile(reference: String): File? = outputs[reference]?.takeUnless { it.releasing }?.value?.audioFile
    override fun releaseOutput(reference: String, callback: VoiceEffectsBridgeReleaseCallback) {
        val output = outputs[reference]
        if (output == null) {
            val suffix = reference.removePrefix(prefix)
            val sequence = suffix.toLongOrNull()
            if (reference.startsWith(prefix) && sequence != null && sequence in 1..issued && suffix == sequence.toString()) callback.onReleased()
            else callback.onFailure(fail(VoiceEffectsBridgeFailureCode.INVALID_INPUT))
            return
        }
        output.waiters += callback
        if (output.releasing) return
        output.releasing = true
        scope.launch {
            val failure = try { checkNotNull(client).releaseOutput(output.value.id); null }
                catch (error: Exception) { map(error) }
            if (failure == null) outputs.remove(reference) else output.releasing = false
            val waiters = output.waiters.toList(); output.waiters.clear()
            waiters.forEach { if (failure == null) it.onReleased() else it.onFailure(failure) }
        }
    }
    override fun close(callback: VoiceEffectsBridgeCloseCallback) {
        closed = true
        if (closeFinished) { callback.onClosed(); return }
        closeWaiters += callback
        active?.cancel()
        if (closing) return
        closing = true
        scope.launch {
            val failure = try {
                // Initiate native closure before joining the bridge process.
                client?.close()
                active?.let {
                    if (!recover(it)) throw VoiceEffectsError.Platform()
                    it.job?.join()
                }
                closeFinished = true; null
            } catch (error: Exception) { map(error) }
            closing = false
            val waiters = closeWaiters.toList(); closeWaiters.clear()
            waiters.forEach { if (failure == null) it.onClosed() else it.onFailure(failure) }
        }
    }
    private fun map(error: Throwable): VoiceEffectsBridgeFailure = when (error) {
        is VoiceEffectsError.Closed -> fail(VoiceEffectsBridgeFailureCode.CLOSED)
        is VoiceEffectsError.Busy -> fail(VoiceEffectsBridgeFailureCode.BUSY)
        is VoiceEffectsError.InvalidInput -> fail(VoiceEffectsBridgeFailureCode.INVALID_INPUT)
        is VoiceEffectsError.UnsupportedInput -> fail(VoiceEffectsBridgeFailureCode.UNSUPPORTED_INPUT)
        is VoiceEffectsError.BackendUnavailable -> fail(VoiceEffectsBridgeFailureCode.BACKEND_UNAVAILABLE, "backendNotLinked")
        else -> VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.PLATFORM, null, "android.voiceeffects", 1)
    }
    private fun fail(code: VoiceEffectsBridgeFailureCode, reason: String? = null) = VoiceEffectsBridgeFailure(code, reason)
    private object Done : BridgeTask { override fun cancel() {} }
}
