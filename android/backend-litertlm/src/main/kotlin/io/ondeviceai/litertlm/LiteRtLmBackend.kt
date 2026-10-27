package io.ondeviceai.litertlm

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.multimodal.MultimodalClientError
import io.ondeviceai.multimodal.MultimodalGenerationBackend
import io.ondeviceai.multimodal.MultimodalRequest
import io.ondeviceai.multimodal.MultimodalTextOutput
import io.ondeviceai.multimodal.MultimodalTextUpdate
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** Image-only opt-in backend. Production construction remains gated by the device drain probe. */
class LiteRtLmBackend internal constructor(
    private val store: ModelStore,
    private val ref: ModelRef,
    private val spec: LiteRtLmModelSpec,
    private val cacheRoot: File,
    private val factory: LiteRtLmEngineFactory,
    private val apiLevel: Int,
    private val supportedAbi: Boolean,
    private val probeState: LiteRtLmProbeState,
    private val cacheCleaner: LiteRtLmCacheCleaner = LiteRtLmCacheCleaner { it.deleteRecursively() },
    private val availabilitySource: suspend () -> CapabilityAvailability = {
        LiteRtLmAvailability.forModel(store, ref, spec, false, apiLevel, supportedAbi, probeState)
    },
) : MultimodalGenerationBackend {
    override val backendId: String = "litertlm.android"

    private val lock = Any()
    private var closed = false
    private var poisoned = false
    private var active: CompletableDeferred<Unit>? = null
    private var activeJob: Job? = null
    private var queries = 0
    private var queriesIdle = CompletableDeferred(Unit)
    private var closeCompletion: CompletableDeferred<Unit>? = null

    override suspend fun availability(): CapabilityAvailability {
        synchronized(lock) {
            if (poisoned) return unavailable(UnavailableReason.UNSUPPORTED_DEVICE)
            if (closed) return unavailable(UnavailableReason.CLOSED)
            if (queries++ == 0) queriesIdle = CompletableDeferred()
        }
        try {
            val value = availabilitySource()
            return synchronized(lock) {
                when {
                    poisoned -> unavailable(UnavailableReason.UNSUPPORTED_DEVICE)
                    closed -> unavailable(UnavailableReason.CLOSED)
                    else -> value
                }
            }
        } finally {
            synchronized(lock) { if (--queries == 0) queriesIdle.complete(Unit) }
        }
    }

    override suspend fun generate(request: MultimodalRequest,
        update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
        val job = currentCoroutineContext()[Job]
        val completion = synchronized(lock) {
            if (poisoned) throw LiteRtLmError.DrainFailure()
            if (closed) throw MultimodalClientError.Closed()
            if (active != null) throw MultimodalClientError.Busy()
            CompletableDeferred<Unit>().also { active = it; activeJob = job }
        }
        try {
            val available = availability()
            if (available.status != CapabilityStatus.AVAILABLE) {
                throw MultimodalClientError.BackendUnavailable(
                    backendId, available.reason ?: UnavailableReason.BACKEND_NOT_LINKED)
            }
            return LiteRtLmGenerationSession(store, ref, spec, cacheRoot, factory, probeState,
                cacheCleaner).run(request, update)
        } catch (failure: LiteRtLmError.DrainFailure) {
            synchronized(lock) { poisoned = true }
            throw failure
        } catch (_: LiteRtLmCleanupFailure) {
            synchronized(lock) { closed = true }
            throw LiteRtLmError.RuntimeFailure()
        } finally {
            withContext(NonCancellable) {
                synchronized(lock) {
                    active = null
                    activeJob = null
                    completion.complete(Unit)
                }
            }
        }
    }

    override suspend fun drain() {
        val completion = synchronized(lock) { active }
        withContext(NonCancellable) { completion?.await() }
        synchronized(lock) { if (poisoned) throw LiteRtLmError.DrainFailure() }
    }

    override suspend fun close() {
        val (completion, ownsClose, work, queriesDone) = synchronized(lock) {
            closed = true
            activeJob?.cancel(CancellationException("LiteRT-LM backend closed"))
            val existing = closeCompletion
            if (existing != null) CloseState(existing, false, active, queriesIdle)
            else CompletableDeferred<Unit>().let {
                closeCompletion = it
                CloseState(it, true, active, queriesIdle)
            }
        }
        withContext(NonCancellable) {
            if (ownsClose) {
                work?.await()
                queriesDone.await()
                if (synchronized(lock) { poisoned }) completion.completeExceptionally(LiteRtLmError.DrainFailure())
                else completion.complete(Unit)
            }
            completion.await()
        }
    }

    private data class CloseState(val completion: CompletableDeferred<Unit>, val ownsClose: Boolean,
        val work: CompletableDeferred<Unit>?, val queriesDone: CompletableDeferred<Unit>)

    private fun unavailable(reason: UnavailableReason) = CapabilityAvailability(
        CapabilityStatus.UNAVAILABLE, reason, backendId, "26")
}
