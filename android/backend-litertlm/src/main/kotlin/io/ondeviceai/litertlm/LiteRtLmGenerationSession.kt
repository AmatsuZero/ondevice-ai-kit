package io.ondeviceai.litertlm

import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.multimodal.MultimodalClientError
import io.ondeviceai.multimodal.MultimodalModelIdentity
import io.ondeviceai.multimodal.MultimodalRequest
import io.ondeviceai.multimodal.MultimodalTextOutput
import io.ondeviceai.multimodal.MultimodalTextUpdate
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

internal fun interface LiteRtLmCacheCleaner {
    fun delete(directory: File): Boolean
}

internal class LiteRtLmCleanupFailure : RuntimeException()

/** One generation owns its lease, native session and private input/cache directory. */
internal class LiteRtLmGenerationSession(
    private val store: ModelStore,
    private val ref: ModelRef,
    private val spec: LiteRtLmModelSpec,
    private val cacheRoot: File,
    private val factory: LiteRtLmEngineFactory,
    private val probeState: LiteRtLmProbeState,
    private val cacheCleaner: LiteRtLmCacheCleaner,
    private val terminationTimeoutMs: Long = 30_000L,
    private val cleanupDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Surviving cleanup owner. It is independent of the caller's coroutine/Job so the safe
    // release (native.close -> owned-cache delete -> lease.close, each at most once) can
    // outlive a caller that returned or was cancelled after a late exact-death timeout.
    // Per-generation scope: the launched coroutine holds the needed references and completes
    // on its own once the exact-death barrier resolves with success. If the exact barrier
    // never resolves (or resolves with failure), the owner never proceeds past awaitTerminated
    // and the lease/cache remain retained — fail-closed, not guaranteed released forever.
    private val cleanupScope = CoroutineScope(SupervisorJob() + cleanupDispatcher)

    suspend fun run(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput =
        withContext(Dispatchers.IO) {
        if (request.maxOutputTokens !in 1..spec.maxOutputTokens || !request.imageFile.isFile ||
            request.imageFile.length() <= 0L || request.imageFile.length() > spec.maxImageBytes) {
            throw MultimodalClientError.InvalidInput("invalid LiteRT-LM image or output budget")
        }

        val lease = store.acquire(ref)
        var native: LiteRtLmSession? = null
        var opening = false
        var started = false
        var cache: File? = null
        var result: MultimodalTextOutput? = null
        var failure: Throwable? = null
        val operationJob = currentCoroutineContext()[Job]
        val callbackLock = Any()
        var accepting = true
        var callbackError = false
        var lastText = ""
        var sequence = 0
        try {
            val manifest = lease.manifest
            if (!spec.matchesManifest(manifest)) throw LiteRtLmError.InvalidModel()
            currentCoroutineContext().ensureActive()
            cache = File(File(File(File(cacheRoot, "litertlm"), ref.modelId), ref.revision),
                UUID.randomUUID().toString())
            if (!cache.mkdirs()) throw IOException("private cache unavailable")
            val image = File(cache, "input.image")
            request.imageFile.inputStream().use { input ->
                image.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        count += size
                        if (count > spec.maxImageBytes) throw MultimodalClientError.InvalidInput("image exceeds LiteRT-LM budget")
                        output.write(buffer, 0, size)
                    }
                }
            }
            opening = true
            val opened = try {
                factory.open(spec.resolveModelPath(lease.directory),
                    LiteRtLmEngineConfig(backendPolicy = spec.backendPolicy, cacheDir = cache))
            } catch (rejection: LiteRtLmPreOwnershipRejection) {
                opening = false
                throw rejection.rejection
            } catch (cancellation: LiteRtLmPreOwnershipCancellation) {
                opening = false
                throw cancellation.cancellation
            }
            native = opened
            val expectedMode = when (probeState) {
                LiteRtLmProbeState.PROVEN_CUMULATIVE, LiteRtLmProbeState.SYNCHRONOUS -> LiteRtLmCallbackMode.CUMULATIVE
                LiteRtLmProbeState.PROVEN_DELTA -> LiteRtLmCallbackMode.DELTA
                LiteRtLmProbeState.UNPROVEN -> throw LiteRtLmError.RuntimeUnavailable()
            }
            if (opened.callbackMode != expectedMode) throw LiteRtLmError.DrainFailure()
            opening = false
            started = true
            val text = opened.generateImage(image, request.prompt, request.maxOutputTokens) { snapshot ->
                synchronized(callbackLock) {
                    if (accepting && operationJob?.isActive != false && snapshot.isNotEmpty()) {
                        val next = when (opened.callbackMode) {
                            LiteRtLmCallbackMode.CUMULATIVE -> if (snapshot.startsWith(lastText)) snapshot else null
                            LiteRtLmCallbackMode.DELTA -> lastText + snapshot
                        }
                        if (next == null) {
                            callbackError = true
                            accepting = false
                        } else if (next != lastText && next.isNotBlank()) {
                            lastText = next
                            update?.invoke(MultimodalTextUpdate(sequence++, next))
                        }
                    }
                }
            }
            if (synchronized(callbackLock) { callbackError }) throw LiteRtLmError.RuntimeFailure()
            if (text.isBlank()) throw LiteRtLmError.NoResult()
            result = MultimodalTextOutput(text, MultimodalModelIdentity(
                manifest.modelId, manifest.upstreamRevision, spec.runtimeRevision))
        } catch (caught: Throwable) {
            failure = caught
        }

        var unsafeNative = opening
        withContext(NonCancellable) {
            synchronized(callbackLock) { accepting = false }
            if (opening) throw LiteRtLmError.DrainFailure()
            try {
                if (native != null) {
                    unsafeNative = true
                    // Stage 1 — exact-death termination wait. This is the ONLY stage bounded by
                    // the caller's termination timeout. A timeout here is the late-exact-death
                    // signal and hands safe-release ownership to a surviving coroutine. native.close
                    // / owned-cache delete / lease.close below are NOT bounded by this timeout, so a
                    // timeout (or any failure) from them is a genuine cleanup error, never a
                    // late-death handoff that would launch another native.close.
                    try {
                        withTimeout(terminationTimeoutMs) {
                            if (started) native.cancelProcess()
                            native.awaitTerminated()
                        }
                    } catch (timeout: TimeoutCancellationException) {
                        // Late exact-death handoff. The barrier did not confirm within the caller's
                        // budget; the caller observes DrainFailure and the backend is still poisoned
                        // (unchanged). A genuine native barrier failure (incl. a native-reported
                        // timeout from this stage) is not treated as confirmed termination: the
                        // surviving coroutine re-awaits and only releases on true completion.
                        //
                        // Exact ordering / safety: ONLY exact barrier success permits the sequence
                        // native.close -> owned cache successful delete -> lease.close, each at most
                        // once. Barrier failure, native.close failure, or a cacheCleaner result of
                        // false/throw MUST NOT release the lease (fail-closed). On any failure the
                        // continuation returns without releasing the lease and without raising an
                        // uncaught exception.
                        val nativeRef = native
                        val cacheRef = cache
                        val leaseRef = lease
                        if (nativeRef != null && leaseRef != null) {
                            cleanupScope.launch {
                                try {
                                    nativeRef.awaitTerminated()
                                    nativeRef.close()
                                    if (cacheRef != null && cacheRef.exists() && !cacheCleaner.delete(cacheRef)) {
                                        return@launch
                                    }
                                    leaseRef.close()
                                } catch (_: Throwable) {
                                    return@launch
                                }
                            }
                        }
                        throw LiteRtLmError.DrainFailure()
                    }
                    // Timely stage: no termination timeout in scope. Any failure (incl. a
                    // TimeoutCancellationException raised by a timed native.close) is handled by the
                    // generic catch below and NEVER launches another native.close / cache delete.
                    native.close()
                    unsafeNative = false
                }
                if (cache != null && cache.exists() && !cacheCleaner.delete(cache)) {
                    throw IOException("private cache cleanup failed")
                }
                lease.close()
            } catch (_: Throwable) {
                if (unsafeNative) throw LiteRtLmError.DrainFailure()
                throw LiteRtLmCleanupFailure()
            }
        }
        failure?.let { throw when (it) {
            is CancellationException, is MultimodalClientError, is LiteRtLmError -> it
            else -> LiteRtLmError.RuntimeFailure()
        } }
        checkNotNull(result)
    }
}
