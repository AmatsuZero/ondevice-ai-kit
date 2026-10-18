package io.ondeviceai.identity

import android.content.Context
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Backend index construction receives the actual leased, pinned embeddings. */
typealias GalleryIndexBuilder = (List<IdentityEmbedding>) -> ByteArray

/** Immutable revisions; catalog persistence and IdentityStore remain separate serial gates. */
class IndexStore internal constructor(root: Path, private val identityStore: IdentityStore,
    keys: IdentityKeyProvider, private val buildIndex: GalleryIndexBuilder) : AutoCloseable {
    private val catalog = GalleryCatalog(root, identityStore.storeId, keys)
    val storeId = catalog.storeId
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val gate = java.lang.Object()
    private val builders = mutableMapOf<UUID, BuilderState>()
    private val indexes = mutableMapOf<String, ByteArray>()
    private val indexLineages = mutableMapOf<String, String>()
    private val readTickets = mutableMapOf<UUID, Pair<String, IdentityGalleryOperationTicket>>()
    private val deleted = mutableMapOf<String, DeletionReceipt>()
    private var closed = false
    private var writerReleased = false
    private var inFlight = 0
    private var pendingStops = 0
    private val lineageStops = mutableMapOf<String, Int>()
    private val deliveryDepth = ThreadLocal.withInitial { 0 }
    private enum class Status { OPEN, PUBLISHING, PUBLISHED, DISCARDED }
    private class BuilderState(val lineage: String, val base: GalleryRef?, val grant: AuthorizationGrant,
        val policy: ThresholdPolicy, val aggregation: String, val members: MutableMap<String, GallerySnapshotMember>,
        var status: Status = Status.OPEN, var busy: Boolean = false, var ticket: IdentityGalleryOperationTicket? = null)
    private data class Stop(val lineage: String, val delivery: () -> Unit)

    init {
        try {
            catalog.tombstoneReceipts().forEach { (lineage, receipt) ->
                deleted[lineage] = DeletionReceipt(receipt, storeId, galleryId = lineage)
                catalog.removeLineage(lineage)
            }
        } catch (error: Throwable) { catalog.close(); throw error }
    }

    fun createGalleryBuilder(baseGalleryRef: GalleryRef? = null, grant: AuthorizationGrant,
        policy: ThresholdPolicy, backend: IdentityBackend? = null, aggregationPolicyId: String = "subject-max-v1"): GalleryBuilder = operation {
        token(aggregationPolicyId)
        if (policy.kind != IdentityPolicyKind.IDENTIFICATION || policy.calibrationStatus != CalibrationStatus.VERIFIED ||
            policy.effectiveAuthorizationEpoch != grant.authorizationEpoch) throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED)
        if (backend != null && backend.embeddingSpace != policy.embeddingSpace) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        val members = synchronized(gate) {
            val base = baseGalleryRef?.let(catalog::snapshot)
            if (base != null && base.policy.embeddingSpace != policy.embeddingSpace)
                throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
            base?.members?.associateBy { it.ref.id }?.toMutableMap() ?: mutableMapOf()
        }
        // Copying opaque membership metadata permits explicit cleanup of a
        // revoked base. The operation is authorized now; every retained member
        // is revalidated together at publication and read admission.
        admit(grant, emptyList(), policy.embeddingSpace)
        synchronized(gate) {
            requireOpen()
            baseGalleryRef?.let(catalog::snapshot)
            val id = UUID.randomUUID()
            builders[id] = BuilderState(baseGalleryRef?.id ?: UUID.randomUUID().toString(), baseGalleryRef, grant, policy, aggregationPolicyId, members)
            GalleryBuilder(this, id)
        }
    }

    internal fun add(id: UUID, template: TemplateRef, grant: AuthorizationGrant) = mutateBuilder(id) { state ->
        if (template.id in state.members) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val candidate = identityStore.recordedTemplateCandidate(template)
        if (template.embeddingSpaceId != state.policy.embeddingSpace) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        val member = GallerySnapshotMember(template, candidate, grant)
        admit(state.grant, listOf(member), state.policy.embeddingSpace)
        synchronized(gate) { requireBuilderOpen(state); state.members[template.id] = member }
    }

    internal fun remove(id: UUID, template: TemplateRef, grant: AuthorizationGrant) = mutateBuilder(id) { state ->
        if (state.members[template.id]?.ref != template) throw IdentityError(IdentityErrorCode.TEMPLATE_NOT_FOUND)
        admit(state.grant, emptyList(), state.policy.embeddingSpace)
        admit(grant, emptyList(), state.policy.embeddingSpace)
        synchronized(gate) { requireBuilderOpen(state); state.members.remove(template.id); Unit }
    }

    internal fun publish(id: UUID): GalleryRef = mutateBuilder(id) { state ->
        synchronized(gate) {
            if (state.members.isEmpty()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            state.status = Status.PUBLISHING
        }
        val members = state.members.values.sortedBy { it.ref.id }
        val leases = mutableListOf<IdentityTemplateLease>()
        var ticket: IdentityGalleryOperationTicket? = null
        try {
            val admitted = identityStore.beginGalleryOperation(state.grant, members.map { IdentityGalleryMember(it.ref, it.grant) }, state.policy.embeddingSpace)
            ticket = admitted
            synchronized(gate) {
                state.ticket = admitted
                if (closed || state.status != Status.PUBLISHING) throw IdentityError(IdentityErrorCode.CANCELLED)
            }
            members.forEach { leases += identityStore.acquireTemplateLease(it.ref, it.grant, IdentityScope.IDENTIFICATION, state.policy.embeddingSpace) }
            if (Thread.currentThread().isInterrupted) admitted.stopSignal.requestStop(IdentityStopReason.CANCELLED)
            admitted.stopSignal.check()
            val bytes = buildIndex(leases.map { it.embedding }).clone()
            if (Thread.currentThread().isInterrupted) admitted.stopSignal.requestStop(IdentityStopReason.CANCELLED)
            admitted.stopSignal.check()
            if (bytes.isEmpty()) throw IdentityError(IdentityErrorCode.INDEX_UNAVAILABLE)
            val record = GallerySnapshotRecord.create(storeId, identityStore.storeId, state.lineage, state.base, members, state.grant, state.policy, state.aggregation)
            ticket = null
            val result = identityStore.consumeGalleryOperation(admitted) { catalog.publish(record); record.ref }
            synchronized(gate) {
                // A completed publication can be followed by deletion before this
                // thread resumes. Never repopulate a purged/closed cache.
                if (!closed && state.lineage !in deleted) { indexes[result.revision] = bytes; indexLineages[result.revision] = result.id }
                state.status = Status.PUBLISHED
            }
            result
        } catch (error: Throwable) {
            synchronized(gate) { state.status = Status.DISCARDED }
            throw if (error is IdentityError || error is Error) error else IdentityError(IdentityErrorCode.INDEX_UNAVAILABLE)
        } finally {
            ticket?.let { runCatching { identityStore.consumeGalleryOperation(it) { Unit } } }
            leases.forEach { it.close() }
            synchronized(gate) { state.ticket = null }
        }
    }

    internal fun closeBuilder(id: UUID) {
        val stops = synchronized(gate) { builders[id]?.let { discard(it, IdentityStopReason.CANCELLED) } ?: emptyList() }
        deliver(stops)
    }

    internal fun acquireIndexReadLease(ref: GalleryRef, grant: AuthorizationGrant): IndexReadLease = operation {
        val record = synchronized(gate) {
            val value = catalog.snapshot(ref)
            if (indexes[ref.revision] == null) throw IdentityError(IdentityErrorCode.REBUILD_REQUIRED)
            value
        }
        val ticket = identityStore.beginGalleryOperation(grant, record.members.map { IdentityGalleryMember(it.ref, it.grant) }, record.policy.embeddingSpace)
        try {
            synchronized(gate) {
                requireOpen(); catalog.snapshot(ref)
                readTickets[ticket.id] = ref.id to ticket
                IndexReadLease(record, ticket.stopSignal) { releaseReadLease(ticket) }
            }
        } catch (error: Throwable) { runCatching { identityStore.consumeGalleryOperation(ticket) { Unit } }; throw error }
    }

    fun deleteGallery(ref: GalleryRef, ownership: OwnershipAuthorization): DeletionReceipt = operation {
        identityStore.validateGalleryOwnership(ownership)
        val (receipt, stops) = synchronized(gate) {
            requireOpen()
            deleted[ref.id]?.let { return@synchronized it to emptyList<Stop>() }
            if (catalog.head(ref.id) == null) throw IdentityError(IdentityErrorCode.GALLERY_NOT_FOUND)
            val receipt = DeletionReceipt(UUID.randomUUID().toString(), storeId, galleryId = ref.id)
            catalog.tombstoneLineage(ref.id, receipt.id)
            deleted[ref.id] = receipt
            val stops = readTickets.values.filter { it.first == ref.id }.mapNotNull { prepareStop(ref.id, it.second.stopSignal, IdentityStopReason.AUTHORIZATION_REVOKED) }.toMutableList()
            builders.values.filter { it.lineage == ref.id }.forEach { stops += discard(it, IdentityStopReason.AUTHORIZATION_REVOKED) }
            receipt to stops
        }
        deliver(stops)
        synchronized(gate) { purge(receipt.galleryId!!); receipt }
    }

    fun awaitDeletion(receipt: DeletionReceipt): DeletionStatus = synchronized(gate) {
        if (receipt.storeId != storeId || receipt.galleryId == null || deleted[receipt.galleryId] != receipt) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        if (catalog.hasSnapshots(receipt.galleryId)) DeletionStatus.PURGE_PENDING else DeletionStatus.PURGED
    }

    override fun close() {
        val stops = synchronized(gate) {
            if (writerReleased) return
            if (closed) emptyList() else {
                catalog.sealPublications()
                closed = true
                val actions = builders.values.flatMap { discard(it, IdentityStopReason.CLOSED) }.toMutableList()
                readTickets.values.forEach { (lineage, ticket) -> prepareStop(lineage, ticket.stopSignal, IdentityStopReason.CLOSED)?.let(actions::add) }
                actions
            }
        }
        deliver(stops)
        synchronized(gate) {
            releaseWriterIfDrained()
            if ((deliveryDepth.get() ?: 0) > 0 || IdentityStopSignal.isDelivering) return
            var interrupted = false
            while (!writerReleased) {
                try { gate.wait() } catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun <T> operation(body: () -> T): T {
        synchronized(gate) { requireOpen(); inFlight++ }
        try { return body() } finally { synchronized(gate) { inFlight--; releaseWriterIfDrained() } }
    }
    private fun <T> mutateBuilder(id: UUID, body: (BuilderState) -> T): T = operation {
        val state = synchronized(gate) {
            val state = builders[id] ?: throw IdentityError(IdentityErrorCode.BUILDER_CLOSED)
            requireBuilderOpen(state)
            if (state.busy) throw IdentityError(IdentityErrorCode.BUSY)
            state.busy = true; state
        }
        try { body(state) } finally { synchronized(gate) { state.busy = false } }
    }
    private fun requireBuilderOpen(state: BuilderState) { requireOpen(); if (state.status != Status.OPEN) throw IdentityError(IdentityErrorCode.BUILDER_CLOSED) }
    private fun requireOpen() { if (closed) throw IdentityError(IdentityErrorCode.CLOSED) }
    private fun admit(grant: AuthorizationGrant, members: List<GallerySnapshotMember>, space: EmbeddingSpaceId) {
        val ticket = identityStore.beginGalleryOperation(grant, members.map { IdentityGalleryMember(it.ref, it.grant) }, space)
        identityStore.consumeGalleryOperation(ticket) { Unit }
    }
    private fun discard(state: BuilderState, reason: IdentityStopReason): List<Stop> {
        state.status = Status.DISCARDED
        return state.ticket?.let { prepareStop(state.lineage, it.stopSignal, reason)?.let(::listOf) } ?: emptyList()
    }
    private fun prepareStop(lineage: String, signal: IdentityStopSignal, reason: IdentityStopReason): Stop? {
        val delivery = signal.prepareStopDelivery(reason) ?: return null
        pendingStops++; lineageStops[lineage] = (lineageStops[lineage] ?: 0) + 1
        return Stop(lineage, delivery)
    }
    private fun deliver(stops: List<Stop>) {
        var failure: Throwable? = null
        stops.forEach { stop ->
            deliveryDepth.set((deliveryDepth.get() ?: 0) + 1)
            try { stop.delivery() } catch (error: Throwable) { if (failure == null) failure = error }
            finally {
                deliveryDepth.set((deliveryDepth.get() ?: 0) - 1)
                synchronized(gate) {
                    pendingStops--
                    val count = lineageStops.getValue(stop.lineage) - 1
                    if (count == 0) lineageStops.remove(stop.lineage) else lineageStops[stop.lineage] = count
                    purge(stop.lineage); releaseWriterIfDrained()
                }
            }
        }
        failure?.let { throw it }
    }
    private fun releaseReadLease(ticket: IdentityGalleryOperationTicket) {
        try { runCatching { identityStore.consumeGalleryOperation(ticket) { Unit } } }
        finally { synchronized(gate) { val lineage = readTickets.remove(ticket.id)?.first; if (lineage != null) purge(lineage); releaseWriterIfDrained() } }
    }
    private fun purge(lineage: String) {
        if (lineage !in deleted || readTickets.values.any { it.first == lineage } || (lineageStops[lineage] ?: 0) > 0) return
        catalog.removeLineage(lineage)
        indexLineages.filterValues { it == lineage }.keys.toList().forEach { indexes.remove(it); indexLineages.remove(it) }
    }
    private fun releaseWriterIfDrained() {
        if (closed && !writerReleased && inFlight == 0 && readTickets.isEmpty() && pendingStops == 0) {
            catalog.close(); indexes.clear(); indexLineages.clear(); writerReleased = true; gate.notifyAll()
        }
    }

    companion object {
        fun open(context: Context, storeName: String, identityStore: IdentityStore, keys: IdentityKeyProvider, buildIndex: GalleryIndexBuilder): IndexStore {
            token(storeName); if (storeName.length > 64) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val base = context.noBackupFilesDir.toPath().also { Files.createDirectories(it) }.toRealPath()
            val root = base.resolve("ondevice-index-$storeName"); Files.createDirectories(root)
            if (!root.toRealPath().startsWith(base)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return IndexStore(root, identityStore, keys, buildIndex)
        }
    }
}

class GalleryBuilder internal constructor(private val store: IndexStore, private val id: UUID) : AutoCloseable {
    fun addTemplate(template: TemplateRef, grant: AuthorizationGrant) = store.add(id, template, grant)
    fun removeTemplate(template: TemplateRef, grant: AuthorizationGrant) = store.remove(id, template, grant)
    fun publish(): GalleryRef = store.publish(id)
    override fun close() = store.closeBuilder(id)
}

class IndexReadLease internal constructor(private val record: GallerySnapshotRecord, private val signal: IdentityStopSignal, onRelease: () -> Unit) : AutoCloseable {
    private var release: (() -> Unit)? = onRelease
    internal fun snapshot(): GallerySnapshotRecord = synchronized(this) { if (release == null) throw IdentityError(IdentityErrorCode.CLOSED); signal.check(); record }
    internal fun registerStopHandler(callback: () -> Unit) = signal.registerStopHandler(callback)
    override fun close() { val callback = synchronized(this) { val value = release; release = null; value }; callback?.invoke() }
}
