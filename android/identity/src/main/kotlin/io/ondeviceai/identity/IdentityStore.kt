package io.ondeviceai.identity

import android.content.Context
import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Persistent candidate/template admission and logical revocation. Template deletion retains immutable metadata after payload/key cleanup. */
class IdentityStore internal constructor(
    root: File,
    private val purpose: String,
    private val authorizationProvider: IdentityAuthorizationProvider,
    keyProvider: IdentityKeyProvider,
    private val wallClock: IdentityWallClock,
    private val monotonicClock: IdentityMonotonicClock
) : AutoCloseable {
    private val journal: IdentityJournal
    private val payload: IdentityTemplatePayload
    val storeId: String
    private var authorization: IdentityAuthorizationState
    private var candidates: Map<String, String>
    private var revocations: Map<String, RevocationRecord>
    private var templates: Map<String, IdentityTemplateRecord> = emptyMap()
    private var stages: Set<String> = emptySet()
    private var deletions: Map<String, IdentityTemplateDeletion> = emptyMap()
    private var candidateDeletions: Map<String, IdentityCandidateDeletion> = emptyMap()
    private var closed = false
    private var operating = false
    private val leases = mutableMapOf<String, MutableSet<IdentityTemplateLease>>()
    private val pendingPurgeStops = mutableMapOf<String, Int>()
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val gate = java.lang.Object()
    private var writerReleased = false
    private data class Operation(val ticket: IdentityOperationTicket, var terminal: IdentityStopReason? = null)
    private val operations = mutableMapOf<UUID, Operation>()

    init {
        val initial = IdentityAuthorizationState(purpose)
        val opened = IdentityJournal(root.toPath(), "store", keyProvider)
        try {
            val record = opened.read()
            if (record == null) {
                val id = UUID.randomUUID().toString()
                opened.commit(encode(id, initial, emptyMap(), emptyMap()))
                storeId = id; authorization = initial; candidates = emptyMap(); revocations = emptyMap(); templates = emptyMap(); stages = emptySet(); deletions = emptyMap(); candidateDeletions = emptyMap()
            } else {
                val metadata = decode(record)
                storeId = metadata.storeId; authorization = metadata.authorization; candidates = metadata.candidates; revocations = metadata.revocations; templates = metadata.templates; stages = metadata.stages; deletions = metadata.deletions; candidateDeletions = metadata.candidateDeletions
                if (metadata.version < 6L) {
                    // Legacy records did not persist immutable template targets. Commit the
                    // derived target/fact set before any recovery purge can run.
                    opened.commit(encode(storeId, authorization, candidates, revocations, templates, stages, deletions, candidateDeletions))
                }
            }
            payload = IdentityTemplatePayload(root.toPath(), storeId, keyProvider)
            if (stages.isNotEmpty()) {
                try {
                    stages.forEach(payload::remove)
                    opened.commit(encode(storeId, authorization, candidates, revocations, templates, emptySet()))
                    stages = emptySet()
                } catch (error: Exception) { opened.close(); throw error }
            }
        } catch (error: Exception) {
            opened.close()
            throw error
        }
        journal = opened
        try {
            // Decode validated the entire metadata before any recovery cleanup.
            deletions.filterValues { it.status != DeletionStatus.PURGED }.keys.forEach(::purgeTemplate)
        } catch (error: Exception) { opened.close(); throw error }
    }

    fun createCandidate(grant: AuthorizationGrant): CandidateRef {
        val (ref, signals, cleanup) = mutation {
            val nextAuthorization = authorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
            val ref = CandidateRef(UUID.randomUUID().toString(), storeId)
            val nextCandidates = candidates + (ref.id to grant.grantId)
            val oldTemplates = templates.values.filter {
                it.ref.authorizationEpoch < nextAuthorization.highestEpoch(grant.revocationDomainId) &&
                    authorization.recordedGrant(it.grantId)?.revocationDomainId == grant.revocationDomainId
            }.map { it.ref.id }.sorted()
            val nextDeletions = addDeletionFacts(oldTemplates, deletions)
            val bytes = encode(storeId, nextAuthorization, nextCandidates, deletions = nextDeletions)
            nextAuthorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
            try { journal.commit(bytes) }
            catch (error: Exception) { closed = true; throw error }
            authorization = nextAuthorization
            candidates = nextCandidates
            deletions = nextDeletions
            holdPurge(oldTemplates)
            Triple(ref, invalidateOperations(grant.revocationDomainId, nextAuthorization.highestEpoch(grant.revocationDomainId)), oldTemplates)
        }
        stopAndPurge(signals, cleanup)
        return ref
    }

    internal fun recordedCandidateGrant(ref: CandidateRef): AuthorizationGrant = synchronized(gate) {
        admitted()
        candidateGrant(ref)
    }

    private fun candidateGrant(ref: CandidateRef): AuthorizationGrant {
        if (ref.storeId != storeId) throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        val grantId = candidates[ref.id] ?: throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        val grant = authorization.recordedGrant(grantId) ?: throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        if (ref.id in candidateDeletions || revocations.values.any { ref.id in it.candidateIds } || grant.authorizationEpoch < authorization.highestEpoch(grant.revocationDomainId))
            throw IdentityError(IdentityErrorCode.CANDIDATE_REVOKED)
        return grant
    }

    fun revokeGrant(grantId: String, ownership: OwnershipAuthorization): RevocationReceipt {
        val (receipt, signals, cleanup) = mutation {
            token(grantId)
            IdentityAuthorizationState.requireOwnership(ownership, storeId, authorizationProvider, wallClock.nowMillis)
            revocations.entries.firstOrNull { grantId in it.value.grantIds }?.let {
                return@mutation Triple(RevocationReceipt(it.key, storeId), emptyList<IdentityStopSignal>(), emptyList<String>())
            }
            val grant = authorization.recordedGrant(grantId) ?: throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
            val nextAuthorization = authorization.revoking(grantId)
            val epoch = nextAuthorization.highestEpoch(grant.revocationDomainId)
            val alreadyCovered = revocations.values.flatMap { it.grantIds }.toSet()
            val grants = nextAuthorization.recordedGrantIds(grant.revocationDomainId, epoch).minus(alreadyCovered).sorted()
            val candidateIds = candidates.filterValues { it in grants }.keys.sorted()
            val receipt = RevocationReceipt(nextReceiptId(), storeId)
            val templateIds = templates.values.filter { it.candidateId in candidateIds }.map { it.ref.id }.sorted()
            val nextDeletions = addDeletionFacts(templateIds, deletions, setOf(receipt.id))
            val record = RevocationRecord(grant.revocationDomainId, epoch, grants, candidateIds, templateIds)
            val nextRevocations = revocations + (receipt.id to record)
            val bytes = encode(storeId, nextAuthorization, candidates, nextRevocations, deletions = nextDeletions)
            try { journal.commit(bytes) } catch (error: Exception) { closed = true; throw error }
            authorization = nextAuthorization; revocations = nextRevocations; deletions = nextDeletions
            val signals = invalidateOperations(grant.revocationDomainId, epoch)
            holdPurge(templateIds)
            Triple(receipt, signals, templateIds)
        }
        stopAndPurge(signals, cleanup)
        return receipt
    }

    private fun invalidateOperations(domain: String, beforeEpoch: Long) = operations.values.filter {
        it.ticket.grant.revocationDomainId == domain && it.ticket.grant.authorizationEpoch < beforeEpoch
    }.map { it.terminal = IdentityStopReason.AUTHORIZATION_REVOKED; it.ticket.stopSignal }

    internal fun beginOperation(candidate: CandidateRef, grant: AuthorizationGrant, scope: IdentityScope,
                                 templateTargets: List<TemplateRef>? = null): IdentityOperationTicket = mutation {
        val membership = candidateGrant(candidate)
        if (membership.revocationDomainId != grant.revocationDomainId || membership.purpose != grant.purpose ||
            membership.authorizationEpoch != grant.authorizationEpoch) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        val next = authorization.admitting(grant, scope, authorizationProvider, wallClock.nowMillis)
        if (next.canonicalJSON() != authorization.canonicalJSON()) {
            val bytes = encode(storeId, next, candidates)
            next.admitting(grant, scope, authorizationProvider, wallClock.nowMillis)
            try { journal.commit(bytes) } catch (error: Exception) { closed = true; throw error }
            authorization = next
        }
        val targetIds = templateTargets?.map { ref ->
            val record = templates[ref.id]
            if (record == null || record.candidateId != candidate.id || ref.id in deletions || candidate.id in candidateDeletions)
                throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
            ref.validate(record.ref)
            ref.id
        }?.toSet()
        IdentityOperationTicket(candidate, grant, scope, targetIds).also { operations[it.id] = Operation(it) }
    }

    /** Always consumes the ticket. Template publication will use this same gate. */
    internal fun finishOperation(ticket: IdentityOperationTicket, backendError: Exception? = null): Unit = mutation(allowClosed = true) {
        consumeOperation(ticket) { if (backendError != null) throw backendError }
    }

    private inline fun <T> consumeOperation(ticket: IdentityOperationTicket, publication: () -> T): T {
        val operation = operations[ticket.id]?.takeIf { it.ticket === ticket } ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        try {
            if (operation.terminal == IdentityStopReason.AUTHORIZATION_REVOKED) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
            if (closed) throw IdentityError(IdentityErrorCode.CLOSED)
            ticket.stopSignal.check()
            candidateGrant(ticket.candidate)
            ticket.templateTargets?.forEach { id ->
                val record = templates[id]
                if (record == null || record.candidateId != ticket.candidate.id || id in deletions || ticket.candidate.id in candidateDeletions)
                    throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
                val stored = authorization.recordedGrant(record.grantId)
                if (stored == null || stored.authorizationEpoch != ticket.grant.authorizationEpoch ||
                    stored.revocationDomainId != ticket.grant.revocationDomainId)
                    throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
            }
            authorization.admitting(ticket.grant, ticket.scope, authorizationProvider, wallClock.nowMillis)
            return publication()
        } finally {
            operations.remove(ticket.id)
            if (operations.isEmpty()) gate.notifyAll()
        }
    }

    /** Backend callbacks execute without the store mutation gate; publication rejoins this gate. */
    fun enroll(probe: IdentityProbe, candidate: CandidateRef, grant: AuthorizationGrant, options: EnrollmentOptions,
               backend: IdentityBackend, stopSignal: IdentityStopSignal? = null): EnrollmentResult {
        val started = monotonicClock.nowNanos
        val ticket = beginOperation(candidate, grant, IdentityScope.ENROLLMENT)
        val registration = stopSignal?.registerStopHandler { ticket.stopSignal.requestStop(IdentityStopReason.CANCELLED) }
        var resolved: EmbeddingSpaceId? = null
        try {
            val binding = IdentityEnrollmentBinding(backend)
            resolved = binding.space
            val prepared = try { Result.success(binding.prepare(probe, options, ticket.stopSignal)) }
            catch (error: IdentityError) { Result.failure(error) }
            catch (_: Exception) { Result.failure(IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)) }
            return mutation(allowClosed = true) {
                consumeOperation(ticket) {
                    val work = prepared.getOrThrow()
                    var ref: TemplateRef? = null
                    if (work.embedding != null) {
                        val issued = TemplateRef(UUID.randomUUID().toString(), binding.space, ticket.grant.authorizationEpoch, UUID.randomUUID().toString())
                        val pending = stages + issued.id
                        var published = false
                        try {
                            journalCommit(encode(storeId, authorization, candidates, revocations, templates, pending)); stages = pending
                            payload.write(issued.id, work.embedding)
                            ticket.stopSignal.check()
                            authorization.admitting(ticket.grant, ticket.scope, authorizationProvider, wallClock.nowMillis)
                            val next = templates + (issued.id to IdentityTemplateRecord(issued, ticket.candidate.id, ticket.grant.grantId))
                            journalCommit(encode(storeId, authorization, candidates, revocations, next, pending - issued.id))
                            templates = next; stages = pending - issued.id; ref = issued; published = true
                        } catch (error: IdentityError) { throw error }
                        catch (_: Exception) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
                        finally { if (!published) closed = true }
                    }
                    val id = ticket.id.toString()
                    EnrollmentResult(if (ref == null) EnrollmentOutcome.INSUFFICIENT_QUALITY else EnrollmentOutcome.ENROLLED, ref, binding.space, work.quality,
                        binding.space.backendId, binding.space.backendVersion, binding.space.modelBinding, probe.revision, options.revision,
                        options.qualityPolicyId, id, id, binding.space.preprocessRevision, elapsed(started, monotonicClock.nowNanos))
                }
            }
        } catch (error: IdentityError) {
            throw IdentityError(error.code, resolved?.backendId, resolved?.modelBinding, error.policyId, ticket.id.toString())
        } catch (_: Exception) {
            throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, resolved?.backendId, resolved?.modelBinding, sessionId = ticket.id.toString())
        } finally {
            registration?.close()
            // Also drains if a backend throws a fatal JVM Error before publication.
            synchronized(gate) { operations.remove(ticket.id); if (operations.isEmpty()) gate.notifyAll() }
        }
    }

    private fun elapsed(start: Long, end: Long): Long = if (end < start) 0 else try { Math.subtractExact(end, start) } catch (_: ArithmeticException) { Long.MAX_VALUE }

    internal fun recordedTemplateCandidate(ref: TemplateRef): CandidateRef = synchronized(gate) {
        admitted(); val record = templates[ref.id] ?: throw IdentityError(IdentityErrorCode.TEMPLATE_NOT_FOUND)
        ref.validate(record.ref)
        if (ref.id in deletions || record.candidateId in candidateDeletions) throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        val grant = authorization.recordedGrant(record.grantId) ?: throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        if (grant.authorizationEpoch != authorization.highestEpoch(grant.revocationDomainId) || revocations.values.any { record.candidateId in it.candidateIds }) throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        CandidateRef(record.candidateId, storeId)
    }

    internal fun acquireTemplateLease(
        ref: TemplateRef,
        grant: AuthorizationGrant,
        scope: IdentityScope,
        embeddingSpace: EmbeddingSpaceId
    ): IdentityTemplateLease = mutation {
        if (scope != IdentityScope.VERIFICATION && scope != IdentityScope.IDENTIFICATION)
            throw IdentityError(IdentityErrorCode.UNSUPPORTED_OPTION)
        val record = templates[ref.id] ?: throw IdentityError(IdentityErrorCode.TEMPLATE_NOT_FOUND)
        ref.validate(record.ref)
        if (ref.id in deletions || record.candidateId in candidateDeletions) throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        if (ref.embeddingSpaceId != embeddingSpace) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        val capability = if (scope == IdentityScope.VERIFICATION) IdentityCapability.VERIFICATION else IdentityCapability.IDENTIFICATION
        if (capability !in embeddingSpace.supportedCapabilities) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        val templateGrant = authorization.recordedGrant(record.grantId)
            ?: throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        val candidateGrantId = candidates[record.candidateId]
        val candidateGrant = candidateGrantId?.let(authorization::recordedGrant)
            ?: throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        if (candidateGrant.authorizationEpoch != authorization.highestEpoch(candidateGrant.revocationDomainId) ||
            revocations.values.any { record.candidateId in it.candidateIds })
            throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        if (templateGrant.revocationDomainId != grant.revocationDomainId || templateGrant.purpose != grant.purpose ||
            templateGrant.authorizationEpoch != grant.authorizationEpoch || templateGrant.purpose != purpose)
            throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        val admitted = authorization.admitting(grant, scope, authorizationProvider, wallClock.nowMillis)
        if (admitted.canonicalJSON() != authorization.canonicalJSON()) {
            journalCommit(encode(storeId, admitted, candidates, revocations, templates, stages))
            authorization = admitted
        }
        val embedding = payload.read(ref.id, embeddingSpace)
        val rechecked = authorization.admitting(grant, scope, authorizationProvider, wallClock.nowMillis)
        if (rechecked.canonicalJSON() != authorization.canonicalJSON()) {
            journalCommit(encode(storeId, rechecked, candidates, revocations, templates, stages))
            authorization = rechecked
        }
        val lease = IdentityTemplateLease(embedding) { releaseLease(ref.id, it) }
        leases.getOrPut(ref.id) { linkedSetOf() }.add(lease)
        lease
    }

    /** Ownership is checked independently on every call, including repeated deletion. */
    fun deleteTemplate(ref: TemplateRef, ownership: OwnershipAuthorization): DeletionReceipt {
        val (receipt, signals, targets) = mutation {
            IdentityAuthorizationState.requireOwnership(ownership, storeId, authorizationProvider, wallClock.nowMillis)
            val record = templates[ref.id] ?: throw IdentityError(IdentityErrorCode.TEMPLATE_NOT_FOUND)
            ref.validate(record.ref)
            deletions[ref.id]?.let {
                return@mutation Triple(DeletionReceipt(it.receiptId, storeId, templateId = ref.id), emptyList<IdentityStopSignal>(), emptyList<String>())
            }
            val receipt = DeletionReceipt(nextReceiptId(), storeId, templateId = ref.id)
            commitDeletions(deletions + (ref.id to IdentityTemplateDeletion(receipt.id, DeletionStatus.PURGE_PENDING)))
            val targets = listOf(ref.id)
            holdPurge(targets)
            Triple(receipt, invalidateTemplateOperations(targets.toSet()), targets)
        }
        stopAndPurge(signals, targets)
        return receipt
    }

    /** Candidate deletion commits its immutable target set and all missing template tombstones together. */
    fun deleteCandidate(candidate: CandidateRef, ownership: OwnershipAuthorization): DeletionReceipt {
        val (receipt, signals, targetIds) = mutation {
            IdentityAuthorizationState.requireOwnership(ownership, storeId, authorizationProvider, wallClock.nowMillis)
            if (candidate.storeId != storeId || candidate.id !in candidates) throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
            candidateDeletions[candidate.id]?.let {
                return@mutation Triple(DeletionReceipt(it.receiptId, storeId, candidateId = candidate.id), emptyList<IdentityStopSignal>(), emptyList<String>())
            }
            val targetIds = templates.values.filter { it.candidateId == candidate.id }.map { it.ref.id }.sorted()
            val nextDeletions = targetIds.fold(deletions) { next, templateId ->
                if (templateId in next) next else next + (templateId to IdentityTemplateDeletion(nextReceiptId(), DeletionStatus.PURGE_PENDING))
            }
            val receipt = DeletionReceipt(nextReceiptId(), storeId, candidateId = candidate.id)
            val nextCandidates = candidateDeletions + (candidate.id to IdentityCandidateDeletion(receipt.id, targetIds))
            journalCommit(encode(storeId, authorization, candidates, revocations, templates, stages, nextDeletions, nextCandidates))
            deletions = nextDeletions; candidateDeletions = nextCandidates
            holdPurge(targetIds)
            Triple(receipt, invalidateCandidateOperations(candidate.id), targetIds)
        }
        stopAndPurge(signals, targetIds)
        return receipt
    }

    /** Returns the current durable status. Deadline-bound waiting is a separate overload. */
    fun awaitDeletion(receipt: DeletionReceipt): DeletionStatus = synchronized(gate) {
        admitted()
        if (receipt.storeId != storeId) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        receipt.templateId?.let { templateId ->
            val record = deletions[templateId]
            if (record == null || record.receiptId != receipt.id) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return@synchronized record.status
        }
        val candidateId = receipt.candidateId ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val record = candidateDeletions[candidateId]
        if (record == null || record.receiptId != receipt.id) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val statuses = record.templateIds.map { deletions[it]?.status ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
        when {
            statuses.all { it == DeletionStatus.PURGED } -> DeletionStatus.PURGED
            statuses.any { it == DeletionStatus.PURGE_FAILED } -> DeletionStatus.PURGE_FAILED
            else -> DeletionStatus.PURGE_PENDING
        }
    }

    /** Resolve revocation against the same durable per-template purge facts as deletion receipts. */
    fun awaitDeletion(receipt: RevocationReceipt): DeletionStatus = synchronized(gate) {
        admitted()
        if (receipt.storeId != storeId) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val record = revocations[receipt.id] ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val statuses = record.templateIds.map { deletions[it]?.status ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
        when {
            statuses.all { it == DeletionStatus.PURGED } -> DeletionStatus.PURGED
            statuses.any { it == DeletionStatus.PURGE_FAILED } -> DeletionStatus.PURGE_FAILED
            else -> DeletionStatus.PURGE_PENDING
        }
    }

    private fun commitDeletions(next: Map<String, IdentityTemplateDeletion>) {
        journalCommit(encode(storeId, authorization, candidates, deletions = next, candidateDeletions = candidateDeletions))
        deletions = next
    }

    private fun nextReceiptId(): String {
        val used = revocations.keys + deletions.values.map { it.receiptId } + candidateDeletions.values.map { it.receiptId }
        return generateSequence { UUID.randomUUID().toString() }.first { it !in used }
    }

    private fun addDeletionFacts(targetIds: List<String>, existing: Map<String, IdentityTemplateDeletion>, reserved: Set<String> = emptySet()): Map<String, IdentityTemplateDeletion> {
        val occupied = (revocations.keys + existing.values.map { it.receiptId } + candidateDeletions.values.map { it.receiptId } + reserved).toMutableSet()
        var next = existing
        targetIds.forEach { id ->
            if (id !in next) {
                val receiptId = generateSequence { UUID.randomUUID().toString() }.first { occupied.add(it) }
                next = next + (id to IdentityTemplateDeletion(receiptId, DeletionStatus.PURGE_PENDING))
            }
        }
        return next
    }

    private fun invalidateCandidateOperations(candidateId: String) = operations.values.filter { it.ticket.candidate.id == candidateId }
        .map { it.terminal = IdentityStopReason.AUTHORIZATION_REVOKED; it.ticket.stopSignal }

    private fun invalidateTemplateOperations(targets: Set<String>) = operations.values.filter {
        it.ticket.templateTargets?.let { bound -> bound.intersect(targets).isNotEmpty() } ?: false
    }.map { it.terminal = IdentityStopReason.AUTHORIZATION_REVOKED; it.ticket.stopSignal }

    private fun purgeTemplate(id: String) {
        if (closed || writerReleased || !leases[id].isNullOrEmpty() || id in pendingPurgeStops) return
        val record = deletions[id]?.takeIf { it.status != DeletionStatus.PURGED } ?: return
        val status = try { payload.remove(id); DeletionStatus.PURGED } catch (_: Exception) { DeletionStatus.PURGE_FAILED }
        // Never catch metadata failure as a payload error. An ambiguous commit
        // retires this writer; only a reopened writer may retry from its tombstone.
        try { commitDeletions(deletions + (id to record.copy(status = status))) }
        catch (error: Exception) { closed = true; throw error }
    }

    private fun holdPurge(targets: List<String>) {
        targets.forEach { pendingPurgeStops[it] = (pendingPurgeStops[it] ?: 0) + 1 }
    }

    private fun stopAndPurge(signals: List<IdentityStopSignal>, targets: List<String>) {
        signals.forEach { it.requestStop(IdentityStopReason.AUTHORIZATION_REVOKED) }
        synchronized(gate) {
            targets.forEach { id ->
                val remaining = (pendingPurgeStops[id] ?: 1) - 1
                if (remaining == 0) pendingPurgeStops.remove(id) else pendingPurgeStops[id] = remaining
            }
            // Stop callbacks may close/retire the store; only the live writer may purge.
            if (!closed) mutation { targets.forEach(::purgeTemplate) }
        }
    }

    private fun releaseLease(templateId: String, lease: IdentityTemplateLease) = synchronized(gate) {
        val held = leases[templateId] ?: return@synchronized
        held.remove(lease)
        if (held.isEmpty()) leases.remove(templateId)
        // Host key-provider callbacks cannot reenter publication during cleanup.
        if (!closed && !writerReleased) {
            try { mutation { purgeTemplate(templateId) } } catch (_: Exception) { /* Recovery retries the durable tombstone. */ }
        }
        if (leases.isEmpty()) gate.notifyAll()
    }

    private fun journalCommit(bytes: ByteArray) { try { journal.commit(bytes) } catch (error: Exception) { closed = true; throw error } }

    override fun close() {
        val pending = synchronized(gate) {
            if (writerReleased) return
            if (operating) throw IdentityError(IdentityErrorCode.BUSY)
            closed = true
            operations.values.map {
                if (it.terminal != IdentityStopReason.AUTHORIZATION_REVOKED) it.terminal = IdentityStopReason.CLOSED
                it.ticket.stopSignal to it.terminal!!
            }
        }
        pending.forEach { (signal, reason) -> signal.requestStop(reason) }
        var interrupted = false
        try {
            synchronized(gate) {
                while (operations.isNotEmpty() || leases.isNotEmpty()) {
                    try { gate.wait() } catch (_: InterruptedException) { interrupted = true }
                }
                if (!writerReleased) { journal.close(); writerReleased = true }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun admitted() {
        if (closed) throw IdentityError(IdentityErrorCode.CLOSED)
        if (operating) throw IdentityError(IdentityErrorCode.BUSY)
    }

    // JVM monitors are reentrant. Host callbacks cannot recursively publish a second state.
    private inline fun <T> mutation(allowClosed: Boolean = false, block: () -> T): T = synchronized(gate) {
        if (!allowClosed && closed) throw IdentityError(IdentityErrorCode.CLOSED)
        if (operating) throw IdentityError(IdentityErrorCode.BUSY)
        operating = true
        try { return block() } finally { operating = false }
    }

    private data class Metadata(val version: Long, val storeId: String, val authorization: IdentityAuthorizationState, val candidates: Map<String, String>, val revocations: Map<String, RevocationRecord>, val templates: Map<String, IdentityTemplateRecord>, val stages: Set<String>, val deletions: Map<String, IdentityTemplateDeletion>, val candidateDeletions: Map<String, IdentityCandidateDeletion>)
    private data class RevocationRecord(val domainId: String, val epoch: Long, val grantIds: List<String>, val candidateIds: List<String>, val templateIds: List<String>) {
        fun fields() = mapOf("candidateIds" to candidateIds, "domainId" to domainId, "epoch" to epoch, "grantIds" to grantIds, "templateIds" to templateIds)
    }

    private fun encode(storeId: String, authorization: IdentityAuthorizationState, candidates: Map<String, String>, revocations: Map<String, RevocationRecord> = this.revocations, templates: Map<String, IdentityTemplateRecord> = this.templates, stages: Set<String> = this.stages, deletions: Map<String, IdentityTemplateDeletion> = this.deletions, candidateDeletions: Map<String, IdentityCandidateDeletion> = this.candidateDeletions): ByteArray {
        val json = IdentityContractJSON.encode(mapOf(
            "authorization" to authorization.canonicalJSON(),
            "candidates" to candidates,
            "revocations" to revocations.mapValues { it.value.fields() },
            "stages" to stages.sorted(),
            "storeId" to storeId,
            "templates" to templates.mapValues { it.value.fields() },
            "deletions" to deletions.mapValues { it.value.fields() },
            "candidateDeletions" to candidateDeletions.mapValues { it.value.fields() },
            "version" to 6
        ))
        if (json.toByteArray(StandardCharsets.UTF_8).size > 65536) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    private fun decode(bytes: ByteArray): Metadata {
        val json = try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
        val root = IdentityContractJSON.parse(json)
        val version = root.long("version")
        val fields = when (version) {
            1L -> root.also { if (it.keys != setOf("authorization", "candidates", "storeId", "version")) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            2L -> root.also { if (it.keys != setOf("authorization", "candidates", "revocations", "storeId", "version")) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            3L -> root.also { if (it.keys != setOf("authorization", "candidates", "revocations", "stages", "storeId", "templates", "version")) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            4L -> root.also { if (it.keys != setOf("authorization", "candidates", "revocations", "stages", "storeId", "templates", "deletions", "version")) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            5L -> root.also { if (it.keys != setOf("authorization", "candidates", "revocations", "stages", "storeId", "templates", "deletions", "candidateDeletions", "version")) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            6L -> root.also { if (it.keys != setOf("authorization", "candidates", "revocations", "stages", "storeId", "templates", "deletions", "candidateDeletions", "version")) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            else -> throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        }
        val id = fields.string("storeId").also(::token)
        val auth = IdentityAuthorizationState.restore(fields.string("authorization"), purpose)
        @Suppress("UNCHECKED_CAST")
        val rawCandidates = fields["candidates"] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val candidates = rawCandidates.mapValues { (candidateId, grantId) ->
            token(candidateId)
            val idValue = grantId as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            token(idValue)
            if (auth.recordedGrant(idValue) == null) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            idValue
        }
        @Suppress("UNCHECKED_CAST")
        val rawRevocations = if (version == 1L) emptyMap() else fields["revocations"] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val usedGrants = mutableSetOf<String>(); val usedCandidates = mutableSetOf<String>()
        val domainEpochs = mutableMapOf<String, MutableSet<Long>>()
        val persistedTargets = mutableMapOf<String, List<String>>()
        var revocations = rawRevocations.mapValues { (receiptId, value) ->
            token(receiptId)
            @Suppress("UNCHECKED_CAST")
            val recordFields = value as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val requiredKeys = if (version >= 6L) setOf("candidateIds", "domainId", "epoch", "grantIds", "templateIds") else setOf("candidateIds", "domainId", "epoch", "grantIds")
            if (recordFields.keys != requiredKeys) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val domain = recordFields.string("domainId").also(::token); val epoch = recordFields.long("epoch")
            if (epoch <= 0 || epoch > auth.highestEpoch(domain)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (!domainEpochs.getOrPut(domain) { mutableSetOf() }.add(epoch)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            fun ids(key: String) = (recordFields[key] as? List<*>)?.map { it as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
                ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val grants = ids("grantIds"); val candidateIds = ids("candidateIds")
            val templateIds = if (version >= 6L) ids("templateIds") else emptyList()
            if (grants.isEmpty()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (grants.size != grants.toSet().size || candidateIds.size != candidateIds.toSet().size) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            grants.forEach { grantId ->
                token(grantId); val grant = auth.recordedGrant(grantId) ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                if (grant.revocationDomainId != domain || grant.authorizationEpoch >= epoch || !usedGrants.add(grantId)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }
            candidateIds.forEach { candidate -> if (candidates[candidate] !in grants || !usedCandidates.add(candidate)) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            val expectedCandidates = candidates.filterValues { it in grants }.keys
            if (candidateIds.toSet() != expectedCandidates) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (templateIds.size != templateIds.toSet().size || templateIds != templateIds.sorted()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            persistedTargets[receiptId] = templateIds
            RevocationRecord(domain, epoch, grants, candidateIds, templateIds)
        }
        for (record in revocations.values) {
            val required = auth.recordedGrantIds(record.domainId, record.epoch)
            val covered = revocations.values.filter { it.domainId == record.domainId && it.epoch <= record.epoch }
                .flatMap { it.grantIds }.toSet()
            if (required != covered) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        }
        @Suppress("UNCHECKED_CAST")
        val rawTemplates = if (version < 3L) emptyMap() else fields["templates"] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val templates = rawTemplates.mapValues { (templateId, value) ->
            requireRecordId(templateId)
            @Suppress("UNCHECKED_CAST")
            val fields = value as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            IdentityTemplateRecord.restore(fields).also { if (it.ref.id != templateId) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
        }
        val pending = if (version < 3L) emptyList() else (fields["stages"] as? List<*>)?.map { it as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) } ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val stages = pending.toSet()
        if (stages.size != pending.size || stages.intersect(templates.keys).isNotEmpty()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        stages.forEach(::requireRecordId)
        templates.values.forEach { record ->
            val candidateGrant = candidates[record.candidateId]?.let(auth::recordedGrant) ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val grant = auth.recordedGrant(record.grantId) ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (grant.authorizationEpoch != record.ref.authorizationEpoch || grant.authorizationEpoch != candidateGrant.authorizationEpoch ||
                grant.revocationDomainId != candidateGrant.revocationDomainId || grant.purpose != candidateGrant.purpose || IdentityScope.ENROLLMENT !in grant.scope)
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        }
        revocations = revocations.mapValues { (receiptId, record) ->
            val expected = templates.values.filter { it.candidateId in record.candidateIds }.map { it.ref.id }.sorted()
            val targets = if (version >= 6L) persistedTargets.getValue(receiptId) else expected
            if (targets != expected) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            targets.forEach(::requireRecordId)
            record.copy(templateIds = targets)
        }
        val revokedTemplates = mutableSetOf<String>()
        revocations.values.forEach { record -> record.templateIds.forEach { if (!revokedTemplates.add(it)) throw IdentityError(IdentityErrorCode.INVALID_INPUT) } }
        @Suppress("UNCHECKED_CAST")
        val rawDeletions = if (version < 4L) emptyMap() else fields["deletions"] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        var deletions = rawDeletions.mapValues { (templateId, value) ->
            @Suppress("UNCHECKED_CAST")
            val record = IdentityTemplateDeletion.restore(value as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT))
            if (templateId !in templates) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            record
        }
        val deletionReceiptIds = deletions.values.map { it.receiptId }
        if (deletionReceiptIds.toSet().size != deletionReceiptIds.size || deletionReceiptIds.any { it in revocations })
            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        @Suppress("UNCHECKED_CAST")
        val rawCandidateDeletions = if (version < 5L) emptyMap() else fields["candidateDeletions"] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val deletedCandidates = mutableSetOf<String>()
        val candidateReceiptIds = mutableSetOf<String>()
        val candidateDeletions = rawCandidateDeletions.mapValues { (candidateId, value) ->
            token(candidateId)
            if (candidateId !in candidates || !deletedCandidates.add(candidateId)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val record = IdentityCandidateDeletion.restore(value as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT))
            if (!candidateReceiptIds.add(record.receiptId) || record.receiptId in revocations || record.receiptId in deletions.values.map { it.receiptId }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val expected = templates.values.filter { it.candidateId == candidateId }.map { it.ref.id }.sorted()
            if (record.templateIds != expected || record.templateIds.any { it !in deletions }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            record
        }
        val staleTemplates = templates.values.filter { template ->
            val grant = auth.recordedGrant(template.grantId) ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            grant.authorizationEpoch < auth.highestEpoch(grant.revocationDomainId)
        }.map { it.ref.id }
        if (version >= 6L) {
            if (staleTemplates.any { it !in deletions } || revocations.values.any { record -> record.templateIds.any { it !in deletions } })
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        } else {
            // All legacy metadata, including existing candidate facts, has been validated.
            // New facts receive their own globally unique receipt IDs before migration commit.
            val occupied = (revocations.keys + deletionReceiptIds + candidateReceiptIds).toMutableSet()
            for (templateId in staleTemplates.sorted()) if (templateId !in deletions) {
                val receiptId = generateSequence { UUID.randomUUID().toString() }.first { occupied.add(it) }
                deletions = deletions + (templateId to IdentityTemplateDeletion(receiptId, DeletionStatus.PURGE_PENDING))
            }
        }
        return Metadata(version, id, auth, candidates, revocations, templates, stages, deletions, candidateDeletions)
    }

    private fun requireRecordId(id: String) {
        if (!Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }

    companion object {
        fun open(
            context: Context,
            storeName: String,
            purpose: String,
            authorizationProvider: IdentityAuthorizationProvider,
            keyProvider: IdentityKeyProvider,
            wallClock: IdentityWallClock = SystemIdentityWallClock,
            monotonicClock: IdentityMonotonicClock = SystemIdentityMonotonicClock
        ): IdentityStore {
            token(storeName)
            if (storeName.length > 64) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val base = context.noBackupFilesDir.toPath().also { Files.createDirectories(it) }.toRealPath()
            val root = base.resolve("ondevice-identity-$storeName")
            Files.createDirectories(root)
            if (!root.toRealPath().startsWith(base)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return IdentityStore(root.toFile(), purpose, authorizationProvider, keyProvider, wallClock, monotonicClock)
        }
    }
}
