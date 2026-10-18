package io.ondeviceai.identity

import android.content.Context
import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Persistent candidate/template admission and logical revocation. Physical purge is a later slice. */
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
    private var closed = false
    private var operating = false
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
                storeId = id; authorization = initial; candidates = emptyMap(); revocations = emptyMap(); templates = emptyMap(); stages = emptySet()
            } else {
                val metadata = decode(record)
                storeId = metadata.storeId; authorization = metadata.authorization; candidates = metadata.candidates; revocations = metadata.revocations; templates = metadata.templates; stages = metadata.stages
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
    }

    fun createCandidate(grant: AuthorizationGrant): CandidateRef {
        val (ref, signals) = mutation {
            val nextAuthorization = authorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
            val ref = CandidateRef(UUID.randomUUID().toString(), storeId)
            val nextCandidates = candidates + (ref.id to grant.grantId)
            val bytes = encode(storeId, nextAuthorization, nextCandidates)
            nextAuthorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
            try { journal.commit(bytes) }
            catch (error: Exception) { closed = true; throw error }
            authorization = nextAuthorization
            candidates = nextCandidates
            ref to invalidateOperations(grant.revocationDomainId, nextAuthorization.highestEpoch(grant.revocationDomainId))
        }
        signals.forEach { it.requestStop(IdentityStopReason.AUTHORIZATION_REVOKED) }
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
        if (revocations.values.any { ref.id in it.candidateIds } || grant.authorizationEpoch < authorization.highestEpoch(grant.revocationDomainId))
            throw IdentityError(IdentityErrorCode.CANDIDATE_REVOKED)
        return grant
    }

    fun revokeGrant(grantId: String, ownership: OwnershipAuthorization): RevocationReceipt {
        val (receipt, signals) = mutation {
            token(grantId)
            IdentityAuthorizationState.requireOwnership(ownership, storeId, authorizationProvider, wallClock.nowMillis)
            revocations.entries.firstOrNull { grantId in it.value.grantIds }?.let {
                return@mutation RevocationReceipt(it.key, storeId) to emptyList<IdentityStopSignal>()
            }
            val grant = authorization.recordedGrant(grantId) ?: throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
            val nextAuthorization = authorization.revoking(grantId)
            val epoch = nextAuthorization.highestEpoch(grant.revocationDomainId)
            val alreadyCovered = revocations.values.flatMap { it.grantIds }.toSet()
            val grants = nextAuthorization.recordedGrantIds(grant.revocationDomainId, epoch).minus(alreadyCovered).sorted()
            val candidateIds = candidates.filterValues { it in grants }.keys.sorted()
            val receipt = RevocationReceipt(UUID.randomUUID().toString(), storeId)
            val record = RevocationRecord(grant.revocationDomainId, epoch, grants, candidateIds)
            val nextRevocations = revocations + (receipt.id to record)
            val bytes = encode(storeId, nextAuthorization, candidates, nextRevocations)
            try { journal.commit(bytes) } catch (error: Exception) { closed = true; throw error }
            authorization = nextAuthorization; revocations = nextRevocations
            val signals = invalidateOperations(grant.revocationDomainId, epoch)
            receipt to signals
        }
        signals.forEach { it.requestStop(IdentityStopReason.AUTHORIZATION_REVOKED) }
        return receipt
    }

    private fun invalidateOperations(domain: String, beforeEpoch: Long) = operations.values.filter {
        it.ticket.grant.revocationDomainId == domain && it.ticket.grant.authorizationEpoch < beforeEpoch
    }.map { it.terminal = IdentityStopReason.AUTHORIZATION_REVOKED; it.ticket.stopSignal }

    internal fun beginOperation(candidate: CandidateRef, grant: AuthorizationGrant, scope: IdentityScope): IdentityOperationTicket = mutation {
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
        IdentityOperationTicket(candidate, grant, scope).also { operations[it.id] = Operation(it) }
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
        val grant = authorization.recordedGrant(record.grantId) ?: throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        if (grant.authorizationEpoch != authorization.highestEpoch(grant.revocationDomainId) || revocations.values.any { record.candidateId in it.candidateIds }) throw IdentityError(IdentityErrorCode.TEMPLATE_REVOKED)
        CandidateRef(record.candidateId, storeId)
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
                while (operations.isNotEmpty()) {
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

    private data class Metadata(val storeId: String, val authorization: IdentityAuthorizationState, val candidates: Map<String, String>, val revocations: Map<String, RevocationRecord>, val templates: Map<String, IdentityTemplateRecord>, val stages: Set<String>)
    private data class RevocationRecord(val domainId: String, val epoch: Long, val grantIds: List<String>, val candidateIds: List<String>) {
        fun fields() = mapOf("candidateIds" to candidateIds, "domainId" to domainId, "epoch" to epoch, "grantIds" to grantIds)
    }

    private fun encode(storeId: String, authorization: IdentityAuthorizationState, candidates: Map<String, String>, revocations: Map<String, RevocationRecord> = this.revocations, templates: Map<String, IdentityTemplateRecord> = this.templates, stages: Set<String> = this.stages): ByteArray {
        val json = IdentityContractJSON.encode(mapOf(
            "authorization" to authorization.canonicalJSON(),
            "candidates" to candidates,
            "revocations" to revocations.mapValues { it.value.fields() },
            "stages" to stages.sorted(),
            "storeId" to storeId,
            "templates" to templates.mapValues { it.value.fields() },
            "version" to 3
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
        val revocations = rawRevocations.mapValues { (receiptId, value) ->
            token(receiptId)
            @Suppress("UNCHECKED_CAST")
            val recordFields = value as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (recordFields.keys != setOf("candidateIds", "domainId", "epoch", "grantIds")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val domain = recordFields.string("domainId").also(::token); val epoch = recordFields.long("epoch")
            if (epoch <= 0 || epoch > auth.highestEpoch(domain)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (!domainEpochs.getOrPut(domain) { mutableSetOf() }.add(epoch)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            fun ids(key: String) = (recordFields[key] as? List<*>)?.map { it as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
                ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val grants = ids("grantIds"); val candidateIds = ids("candidateIds")
            if (grants.isEmpty()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (grants.size != grants.toSet().size || candidateIds.size != candidateIds.toSet().size) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            grants.forEach { grantId ->
                token(grantId); val grant = auth.recordedGrant(grantId) ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                if (grant.revocationDomainId != domain || grant.authorizationEpoch >= epoch || !usedGrants.add(grantId)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }
            candidateIds.forEach { candidate -> if (candidates[candidate] !in grants || !usedCandidates.add(candidate)) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            val expectedCandidates = candidates.filterValues { it in grants }.keys
            if (candidateIds.toSet() != expectedCandidates) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            RevocationRecord(domain, epoch, grants, candidateIds)
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
        return Metadata(id, auth, candidates, revocations, templates, stages)
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
