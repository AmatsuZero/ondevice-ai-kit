package io.ondeviceai.voiceeffects

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One slot covers input, render, drain, publication and undelivered-file cleanup.
 * The directory must be SDK-owned: callers must not replace/remove its entries.
 * No filesystem work occurs in the constructor or while holding the state lock.
 * A backend instance belongs to one client and must not be shared concurrently.
 */
class VoiceEffectsClient internal constructor(
    private val backend: VoiceEffectsBackend,
    private val outputDirectory: File,
    private val files: VoiceEffectsFiles
) {
    constructor(backend: VoiceEffectsBackend, outputDirectory: File) : this(backend, outputDirectory, VoiceEffectsFiles())

    init { require(backend.backendId == "android.windowed-pitch") { "unsupported voice effects backend ID" } }

    private class Operation {
        lateinit var worker: Deferred<VoiceEffectsOutput>
        val cleanupAttempted = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        var cancelled = false // accessed under lock
        var temporary: File? = null // worker writes; coordinator reads only after join
        var published: File? = null
        var directory: File? = null
    }

    private val lock = Any()
    private var active: Operation? = null
    private var closed = false
    private var closeCompletion: CompletableDeferred<Unit>? = null
    private val outputs = mutableMapOf<String, File>()
    private val releases = Mutex()
    // Failed cleanup retains exact owned paths so drain/close can retry deletion.
    private val pendingCleanup = mutableSetOf<File>()

    suspend fun process(request: VoiceEffectsRequest): VoiceEffectsOutput {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val operation = synchronized(lock) {
            if (closed) throw VoiceEffectsError.Closed()
            if (active != null) throw VoiceEffectsError.Busy()
            Operation().also { op ->
                active = op
                // LAZY cancellation before dispatch creates no files or backend access.
                // The deferred has no caller parent: caller cancellation cannot skip join.
                op.worker = CoroutineScope(Dispatchers.IO).async(start = CoroutineStart.LAZY) {
                    prepare(request, op)
                }
                op.worker.start()
            }
        }
        var result: VoiceEffectsOutput? = null
        var failure: Throwable? = null
        try {
            result = operation.worker.await()
        } catch (error: Throwable) {
            failure = safeFailure(error)
            synchronized(lock) { operation.cancelled = true; operation.worker.cancel() }
        }

        // Same dispatcher on entry/exit: NonCancellable has no return-dispatch
        // cancellation gap after ownership transfer. All blocking work is on IO.
        withContext(NonCancellable) {
            operation.worker.join()
            val delivered = synchronized(lock) {
                if (failure == null) {
                    // Linearization point: close and accepted cancellation use this
                    // same lock. No suspension occurs after transfer to the caller.
                    if (closed || operation.cancelled || !caller.isActive) {
                        operation.cancelled = true
                        failure = CancellationException("voice effects processing cancelled")
                        false
                    } else {
                        val output = checkNotNull(result)
                        outputs[output.id] = output.audioFile
                        true
                    }
                } else false
            }
            if (!delivered) {
                val cleanupFailure = withContext(Dispatchers.IO) { cleanup(operation) }
                if (cleanupFailure != null) {
                    val original = failure
                    if (original == null) failure = cleanupFailure else original.addSuppressed(cleanupFailure)
                }
            }
            synchronized(lock) {
                operation.cleanupAttempted.complete(Unit)
                if (pendingCleanup.isEmpty()) operation.cleaned.complete(Unit)
            }
            // If OS deletion was denied, neither a cancellation terminal nor the
            // slot is released. drain/close reports the failure and can retry the
            // exact retained paths. No polling or unbounded automatic retry loop.
            operation.cleaned.await()
            synchronized(lock) {
                check(active === operation)
                active = null
                operation.done.complete(Unit)
            }
        }
        failure?.let { throw it }
        return checkNotNull(result)
    }

    private suspend fun prepare(request: VoiceEffectsRequest, operation: Operation): VoiceEffectsOutput {
        val context = currentCoroutineContext()
        val checkCancelled = { context.ensureActive() }
        var renderStarted = false
        var failure: Throwable? = null
        var data: WaveData? = null
        var bytes: ByteArray? = null
        try {
            checkCancelled()
            val wave = files.read(request.inputFile, checkCancelled)
            data = wave
            checkCancelled()
            bytes = if (request.preset == VoiceEffectsPreset.ORIGINAL) wave.bytes else {
                val input = WaveCodec.decode(wave, checkCancelled)
                renderStarted = true
                val rendered = backend.render(input, wave.sampleRate, request.preset)
                checkCancelled()
                WaveCodec.encode(wave, rendered, checkCancelled)
            }
        } catch (error: Throwable) { failure = safeFailure(error) }

        // Backend work must actually release its input before any success or
        // failure is observable. Preserve cancellation if drain itself fails.
        if (renderStarted) withContext(NonCancellable) {
            try { backend.drain() } catch (error: Throwable) {
                val safe = safeFailure(error)
                val original = failure
                if (original == null) failure = safe else original.addSuppressed(safe)
            }
        }
        failure?.let { throw it }
        checkCancelled()
        val wave = checkNotNull(data)
        val id = files.nextId()
        // IDs come only from the SDK; no public input is used as a path component.
        val reserve = files.reserveDestination(outputDirectory, id)
        operation.directory = reserve
        val destination = File(reserve, "audio.wav")
        val temporary = files.createTemporary(reserve)
        operation.temporary = temporary
        files.writeClosed(temporary, checkNotNull(bytes), checkCancelled)
        checkCancelled()
        // Atomic move of closed file into reserved private directory.
        // No shared name race possible after reservation.
        files.publish(temporary, destination)
        operation.published = destination
        files.delete(temporary)
        operation.temporary = null
        checkCancelled()
        return VoiceEffectsOutput(id, destination, backend.backendId, wave.sampleRate,
            wave.frameCount, wave.frameCount.toDouble() / wave.sampleRate)
    }

    /** Waits the snapshot operation's actual input release and cleanup, even if cancelled. */
    suspend fun drain() {
        val operation = synchronized(lock) { active }
        withContext(NonCancellable) {
            operation?.cleanupAttempted?.await()
            withContext(Dispatchers.IO) { retryCleanup() }
            synchronized(lock) {
                if (pendingCleanup.isEmpty()) operation?.cleaned?.complete(Unit)
            }
            operation?.done?.await()
        }
    }

    /** Sticky admission closure. Every concurrent/repeated caller shares one completion. */
    suspend fun close() {
        val (completion, owner) = synchronized(lock) {
            closed = true
            active?.let { it.cancelled = true; it.worker.cancel() }
            closeCompletion?.let { it to false } ?: CompletableDeferred<Unit>().let {
                closeCompletion = it
                it to true
            }
        }
        withContext(NonCancellable) {
            if (owner) {
                var failure: Throwable? = null
                try { drain() } catch (error: Throwable) { failure = safeFailure(error) }
                try { withContext(Dispatchers.IO) { backend.close() } } catch (error: Throwable) {
                    val safe = safeFailure(error)
                    val original = failure
                    if (original == null) failure = safe else original.addSuppressed(safe)
                }
                val error = failure
                if (error == null) completion.complete(Unit) else completion.completeExceptionally(error)
            }
            completion.await()
        }
    }

    /** Unknown IDs are harmless no-ops. Failed deletion retains the ownership entry. */
    suspend fun releaseOutput(id: String) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                releases.withLock {
                    val file = synchronized(lock) { outputs[id] } ?: return@withLock
                    try { files.delete(file); files.delete(checkNotNull(file.parentFile)) } catch (error: Throwable) { throw safeFailure(error) }
                    synchronized(lock) { outputs.remove(id) }
                }
            }
        }
    }

    private fun cleanup(operation: Operation): Throwable? {
        var failure: Throwable? = null
        for (file in listOfNotNull(operation.temporary, operation.published, operation.directory)) {
            try { files.delete(file) } catch (_: Throwable) {
                synchronized(lock) { pendingCleanup.add(file) }
                failure = VoiceEffectsError.Platform()
            }
        }
        return failure
    }

    private fun retryCleanup() {
        // Serializes retries with other drain/close callers.
        // Only exact SDK-created paths enter this set, never directory contents.
        synchronized(cleanupLock) {
            val paths = synchronized(lock) { pendingCleanup.toList() }
            var failed = false
            for (file in paths.sortedByDescending { it.toPath().nameCount }) {
                try {
                    files.delete(file)
                    synchronized(lock) { pendingCleanup.remove(file) }
                } catch (_: Throwable) { failed = true }
            }
            if (failed) throw VoiceEffectsError.Platform()
        }
    }

    private val cleanupLock = Any()
}

internal fun safeFailure(error: Throwable): Throwable {
    // Keep the actual cancellation, including TimeoutCancellationException's
    // owner identity used by withTimeoutOrNull. File closing attaches only safe
    // exceptions via closedResource, never the raw close exception.
    if (error is CancellationException) return error
    // Reconstruct other failures; custom backends may attach raw diagnostics.
    val safe = when (error) {
        is VoiceEffectsError.Closed -> VoiceEffectsError.Closed()
        is VoiceEffectsError.Busy -> VoiceEffectsError.Busy()
        is VoiceEffectsError.InvalidInput -> VoiceEffectsError.InvalidInput()
        is VoiceEffectsError.UnsupportedInput -> VoiceEffectsError.UnsupportedInput()
        is VoiceEffectsError.BackendUnavailable -> VoiceEffectsError.BackendUnavailable(error.reason)
        else -> VoiceEffectsError.Platform()
    }
    if (error.suppressed.isNotEmpty()) safe.addSuppressed(VoiceEffectsError.Platform())
    return safe
}
