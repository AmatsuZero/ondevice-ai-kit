package io.ondeviceai.identity

import android.content.Context
import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Persistent candidate admission only. Enrollment and deletion are later lifecycle slices. */
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
    private var closed = false

    init {
        val initial = IdentityAuthorizationState(purpose)
        val opened = IdentityJournal(root.toPath(), "store", keyProvider)
        try {
            val record = opened.read()
            if (record == null) {
                val id = UUID.randomUUID().toString()
                opened.commit(encode(id, initial, emptyMap()))
                storeId = id; authorization = initial; candidates = emptyMap()
            } else {
                val metadata = decode(record)
                storeId = metadata.storeId; authorization = metadata.authorization; candidates = metadata.candidates
            }
        } catch (error: Exception) {
            opened.close()
            throw error
        }
        journal = opened
    }

    @Synchronized fun createCandidate(grant: AuthorizationGrant): CandidateRef {
        admitted()
        val nextAuthorization = authorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
        val ref = CandidateRef(UUID.randomUUID().toString(), storeId)
        val nextCandidates = candidates + (ref.id to grant.grantId)
        val bytes = encode(storeId, nextAuthorization, nextCandidates)
        nextAuthorization.admitting(grant, IdentityScope.ENROLLMENT, authorizationProvider, wallClock.nowMillis)
        try { journal.commit(bytes) }
        catch (error: Exception) { closed = true; throw error }
        authorization = nextAuthorization
        candidates = nextCandidates
        return ref
    }

    @Synchronized internal fun recordedCandidateGrant(ref: CandidateRef): AuthorizationGrant {
        admitted()
        if (ref.storeId != storeId) throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        val grantId = candidates[ref.id] ?: throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
        return authorization.recordedGrant(grantId) ?: throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        journal.close()
    }

    private fun admitted() { if (closed) throw IdentityError(IdentityErrorCode.CLOSED) }

    private data class Metadata(val storeId: String, val authorization: IdentityAuthorizationState, val candidates: Map<String, String>)

    private fun encode(storeId: String, authorization: IdentityAuthorizationState, candidates: Map<String, String>): ByteArray {
        val json = IdentityContractJSON.encode(mapOf(
            "authorization" to authorization.canonicalJSON(),
            "candidates" to candidates,
            "storeId" to storeId,
            "version" to 1
        ))
        if (json.toByteArray(StandardCharsets.UTF_8).size > 65536) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    private fun decode(bytes: ByteArray): Metadata {
        val json = try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
        val fields = fields(json, setOf("authorization", "candidates", "storeId", "version"))
        if (fields.long("version") != 1L) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
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
        return Metadata(id, auth, candidates)
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
