package io.ondeviceai.identity

import io.ondeviceai.core.*
import io.ondeviceai.modelstore.*
import kotlinx.coroutines.*

/** Pins verified local model assets around a separately supplied runtime.
 * No runtime, weights, threshold or recognition capability is supplied here.
 * Successful open transfers runtime ownership; do not reuse or mutate it afterward.
 */
class LocalIdentityBackend private constructor(
    private val runtime: IdentityBackend, private val lease: ModelLease, override val embeddingSpace: EmbeddingSpaceId
) : IdentityBackend {
    override val backendId = embeddingSpace.backendId
    override val backendVersion = embeddingSpace.backendVersion
    override val modelBinding = embeddingSpace.modelBinding
    private val lock = Any()
    private var closed = false
    private var runtimeClosed = false
    private var inFlight = 0
    private var releasing = false
    private val released = CompletableDeferred<Unit>()

    companion object {
        suspend fun open(modelStore: ModelStore, modelRef: ModelRef, runtime: IdentityBackend): LocalIdentityBackend {
            val space = runtime.embeddingSpace
            val mismatch = IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, space.backendId, space.modelBinding)
            if (space.modelBinding.kind != ModelBindingKind.MODEL || space.modelBinding.id != modelRef.modelId || space.modelBinding.revision != modelRef.revision ||
                runtime.backendId != space.backendId || runtime.backendVersion != space.backendVersion || runtime.modelBinding != space.modelBinding) throw mismatch
            val lease = try { modelStore.acquire(modelRef) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, space.backendId, space.modelBinding) }
            try {
                val manifest = lease.manifest
                if (manifest.ref != modelRef || manifest.backendId != space.backendId || !versionInRange(space.backendVersion, manifest.backendVersion) || manifest.capability != "face-identity" ||
                    manifest.processing.preprocessRevision != space.preprocessRevision) throw mismatch
                val backend = LocalIdentityBackend(runtime, lease, space)
                backend.checkBinding()
                if (runtime.availability().status != CapabilityStatus.AVAILABLE) throw backend.error(IdentityErrorCode.BACKEND_UNAVAILABLE)
                backend.checkBinding()
                return backend
            } catch (error: Throwable) {
                withContext(NonCancellable) { lease.close() }
                throw error
            }
        }
        private fun versionInRange(value: String, range: VersionRange): Boolean {
            fun parse(raw: String): List<Int>? {
                val parts = raw.split('.')
                if (parts.isEmpty() || parts.any { it.isEmpty() || !it.all { c -> c in '0'..'9' } }) return null
                return parts.map { it.toIntOrNull() ?: return null }
            }
            fun compare(a: List<Int>, b: List<Int>): Int {
                for (i in 0 until maxOf(a.size, b.size)) {
                    val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                    if (x != y) return x.compareTo(y)
                }
                return 0
            }
            val current = parse(value) ?: return false
            val minimum = parse(range.minimum) ?: return false
            val maximum = parse(range.maximumExclusive) ?: return false
            return compare(current, minimum) >= 0 && compare(current, maximum) < 0
        }
    }

    private fun error(code: IdentityErrorCode) = IdentityError(code, backendId, modelBinding)
    private fun checkBinding() {
        if (runtime.backendId != backendId || runtime.backendVersion != backendVersion || runtime.modelBinding != modelBinding || runtime.embeddingSpace != embeddingSpace)
            throw error(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
    }
    private inline fun <T> perform(body: () -> T): T {
        synchronized(lock) {
            if (closed) throw error(IdentityErrorCode.CLOSED)
            inFlight++
        }
        try {
            checkBinding()
            val value = body()
            synchronized(lock) { if (closed) throw error(IdentityErrorCode.CLOSED) }
            checkBinding()
            return value
        } finally {
            synchronized(lock) { inFlight-- }
            releaseIfDrained()
        }
    }
    override fun availability(): CapabilityAvailability = try {
        perform {
            runtime.availability().also { if (it.backendId != backendId) throw error(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH) }
        }
    } catch (_: Exception) {
        CapabilityAvailability(CapabilityStatus.UNAVAILABLE,
            if (synchronized(lock) { closed }) UnavailableReason.CLOSED else UnavailableReason.MISSING_ASSETS, backendId)
    }
    private fun validate(embedding: IdentityEmbedding): IdentityEmbedding {
        if (embedding.embeddingSpace != embeddingSpace) throw error(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        return embedding
    }
    override fun embed(probe: IdentityProbe) = perform { validate(runtime.embed(probe)) }
    override fun embed(probe: IdentityProbe, stopSignal: IdentityStopSignal) = perform { withStop(stopSignal) { validate(runtime.embed(probe, stopSignal)) } }
    override fun evaluateQuality(probe: IdentityProbe) = perform { runtime.evaluateQuality(probe) }
    override fun evaluateQuality(probe: IdentityProbe, stopSignal: IdentityStopSignal) = perform { withStop(stopSignal) { runtime.evaluateQuality(probe, stopSignal) } }
    private inline fun <T> withStop(signal: IdentityStopSignal, body: () -> T): T {
        signal.check()
        return try { body().also { signal.check() } }
        catch (error: Exception) { signal.check(); throw error }
    }

    /** Stops admission; requests runtime stop without releasing assets beneath active calls. */
    override fun close() {
        synchronized(lock) { if (closed) return; closed = true }
        try { runtime.close() }
        finally {
            synchronized(lock) { runtimeClosed = true }
            releaseIfDrained()
        }
    }
    private fun releaseIfDrained() {
        synchronized(lock) {
            if (!runtimeClosed || inFlight != 0 || releasing) return
            releasing = true
        }
        CoroutineScope(Dispatchers.IO).launch {
            try { lease.close(); released.complete(Unit) }
            catch (error: Throwable) { released.completeExceptionally(error); throw error }
        }
    }
    /** Join after engine shutdown before removing the model revision. */
    suspend fun closeAndWait() { close(); released.await() }
}
