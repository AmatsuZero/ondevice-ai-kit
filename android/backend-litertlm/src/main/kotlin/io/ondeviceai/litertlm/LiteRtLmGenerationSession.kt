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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
) {
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
            val opened = factory.open(spec.resolveModelPath(lease.directory),
                LiteRtLmEngineConfig(cacheDir = cache))
            native = opened
            val expectedMode = when (probeState) {
                LiteRtLmProbeState.PROVEN_CUMULATIVE -> LiteRtLmCallbackMode.CUMULATIVE
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
                    withTimeout(30_000L) {
                        if (started) native.cancelProcess()
                        native.awaitTerminated()
                    }
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
