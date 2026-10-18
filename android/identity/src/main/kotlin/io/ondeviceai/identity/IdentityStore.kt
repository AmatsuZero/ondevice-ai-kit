package io.ondeviceai.identity

import android.content.Context
import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Persistent candidate admission and logical revocation. Physical purge is a later slice. */
class IdentityStore internal constructor(
    root: File,
    private val purpose: String,
    private val authorizationProvider: IdentityAuthorizationProvider,
    keyProvider: IdentityKeyProvider,
    private val wallClock: IdentityWallClock,
    @Suppress("unused") private val monotonicClock: IdentityMonotonicClock
) : AutoCloseable {
    private val journal: IdentityJournal
    val storeId: String
    private var authorization: IdentityAuthorizationState
    private var candidates: Map<String, String>
    private var revocations: Map<String, RevocationRecord>
    private var closed = false
    private var operating = false

    init {
        val initial = IdentityAuthorizationState(purpose)
        val opened = IdentityJournal(root.toPath(), "store", keyProvider)
        try {
            val record = opened.read()
            if (record == null) {
                val id = UUID.randomUUID().toString()
                opened.commit(encode(id, initial, emptyMap(), emptyMap()))
                storeId = id; authorization = initial; candidates = emptyMap(); revocations = emptyMap()
            } else {
                val metadata = decode(record)
                storeId = metadata.storeId; authorization = metadata.authorization; candidates = metadata.candidates; revocations = metadata.revocations
            }
        } catch (error: Exception) {
            opened.close()
            throw error
        }
        journal = opened
    }

    @Synchronized fun createCandidate(grant: AuthorizationGrant): CandidateRef = mutation {
        val nextAuthorization = authorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
        val ref = CandidateRef(UUID.randomUUID().toString(), storeId)
        val nextCandidates = candidates + (ref.id to grant.grantId)
        val bytes = encode(storeId, nextAuthorization, nextCandidates)
        nextAuthorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
        try { journal.commit(bytes) }
        catch (error: Exception) { closed = true; throw error }
        authorization = nextAuthorization
        candidates = nextCandidates
        ref
    }

    @Synchronized internal fun recordedCandidateGrant(ref: CandidateRef): AuthorizationGrant {
        admitted()
        if (ref.storeId != storeId) throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        val grantId = candidates[ref.id] ?: throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        val grant = authorization.recordedGrant(grantId) ?: throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        if (revocations.values.any { ref.id in it.candidateIds } || grant.authorizationEpoch < authorization.highestEpoch(grant.revocationDomainId))
            throw IdentityError(IdentityErrorCode.CANDIDATE_REVOKED)
        return grant
    }

    @Synchronized fun revokeGrant(grantId: String, ownership: OwnershipAuthorization): RevocationReceipt = mutation {
        token(grantId)
        IdentityAuthorizationState.requireOwnership(ownership, storeId, authorizationProvider, wallClock.nowMillis)
        revocations.entries.firstOrNull { grantId in it.value.grantIds }?.let { return@mutation RevocationReceipt(it.key, storeId) }
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
        receipt
    }

    @Synchronized override fun close() {
        if (closed) return
        if (operating) throw IdentityError(IdentityErrorCode.BUSY)
        closed = true
        journal.close()
    }

    private fun admitted() {
        if (closed) throw IdentityError(IdentityErrorCode.CLOSED)
        if (operating) throw IdentityError(IdentityErrorCode.BUSY)
    }

    // JVM monitors are reentrant. Host callbacks cannot recursively publish a second state.
    private inline fun <T> mutation(block: () -> T): T {
        admitted(); operating = true
        try { return block() } finally { operating = false }
    }

    private data class Metadata(val storeId: String, val authorization: IdentityAuthorizationState, val candidates: Map<String, String>, val revocations: Map<String, RevocationRecord>)
    private data class RevocationRecord(val domainId: String, val epoch: Long, val grantIds: List<String>, val candidateIds: List<String>) {
        fun fields() = mapOf("candidateIds" to candidateIds, "domainId" to domainId, "epoch" to epoch, "grantIds" to grantIds)
    }

    private fun encode(storeId: String, authorization: IdentityAuthorizationState, candidates: Map<String, String>, revocations: Map<String, RevocationRecord> = this.revocations): ByteArray {
        val json = IdentityContractJSON.encode(mapOf(
            "authorization" to authorization.canonicalJSON(),
            "candidates" to candidates,
            "revocations" to revocations.mapValues { it.value.fields() },
            "storeId" to storeId,
            "version" to 2
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
        return Metadata(id, auth, candidates, revocations)
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
