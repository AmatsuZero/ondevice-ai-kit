package io.ondeviceai.identity

internal fun token(value: String) {
    if (!Regex("[A-Za-z0-9._:-]{1,256}").matches(value)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
}

@ConsistentCopyVisibility
data class CandidateRef internal constructor(val id: String, val storeId: String) {
    init { token(id); token(storeId) }
    internal fun validate(issued: CandidateRef) {
        if (this != issued) throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
    }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf("id" to id, "storeId" to storeId))
    companion object {
        fun decodeJSON(json: String): CandidateRef {
            val fields = IdentityContractJSON.parse(json)
            if (fields.keys != setOf("id", "storeId")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return CandidateRef(fields["id"] as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT),
                fields["storeId"] as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT))
        }
    }
}
@ConsistentCopyVisibility
data class GalleryRef internal constructor(val id: String, val revision: String) {
    init { token(id); token(revision) }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf("id" to id, "revision" to revision))
    companion object {
        fun decodeJSON(json: String): GalleryRef {
            val f = IdentityContractJSON.parse(json)
            if (f.keys != setOf("id", "revision")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return GalleryRef(f["id"] as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT),
                f["revision"] as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT))
        }
    }
}
enum class IdentityScope { ENROLLMENT, VERIFICATION, IDENTIFICATION, LIVENESS }
interface IdentityWallClock { val nowMillis: Long }
interface IdentityMonotonicClock { val nowNanos: Long }
data object SystemIdentityWallClock : IdentityWallClock { override val nowMillis get() = System.currentTimeMillis() }
data object SystemIdentityMonotonicClock : IdentityMonotonicClock { override val nowNanos get() = System.nanoTime() }

/** Host declaration only. Admission also needs a trusted provider and persisted epoch. */
class AuthorizationGrant(
    val grantId: String, val revocationDomainId: String, scope: Set<IdentityScope>, val purpose: String,
    val issuedAtMillis: Long, val expiresAtMillis: Long?, val authorizationEpoch: Long, val evidenceRef: String
) {
    val scope: Set<IdentityScope> = java.util.Collections.unmodifiableSet(scope.toSet())
    init {
        listOf(grantId, revocationDomainId, purpose, evidenceRef).forEach(::token)
        if (scope.isEmpty() || issuedAtMillis < 0 || authorizationEpoch < 0 ||
            expiresAtMillis?.let { it <= issuedAtMillis } == true) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }
    internal fun validate(required: IdentityScope, expectedPurpose: String, epoch: Long, clock: IdentityWallClock) {
        if (epoch != authorizationEpoch) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
        val now = clock.nowMillis
        if (now < issuedAtMillis || purpose != expectedPurpose || required !in scope)
            throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        if (expiresAtMillis?.let { now >= it } == true) throw IdentityError(IdentityErrorCode.AUTHORIZATION_EXPIRED)
    }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf(
        "authorizationEpoch" to authorizationEpoch, "evidenceRef" to evidenceRef,
        "expiresAtMillis" to expiresAtMillis, "grantId" to grantId,
        "issuedAtMillis" to issuedAtMillis, "purpose" to purpose,
        "revocationDomainId" to revocationDomainId, "scope" to scope.map { it.name.lowercase() }.sorted()
    ))
    companion object {
        fun decodeJSON(json: String): AuthorizationGrant {
            val f = fields(json, setOf("authorizationEpoch", "evidenceRef", "expiresAtMillis", "grantId", "issuedAtMillis", "purpose", "revocationDomainId", "scope"))
            val scopes = (f["scope"] as? List<*>)?.map { value ->
                IdentityScope.entries.firstOrNull { it.name.lowercase() == value as? String }
                    ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }?.toSet() ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return AuthorizationGrant(f.string("grantId"), f.string("revocationDomainId"), scopes,
                f.string("purpose"), f.long("issuedAtMillis"), f["expiresAtMillis"]?.let { f.long("expiresAtMillis") },
                f.long("authorizationEpoch"), f.string("evidenceRef"))
        }
    }
}
sealed interface IdentityBackendSelection {
    data object Default : IdentityBackendSelection
    data class Explicit(val id: String) : IdentityBackendSelection
    fun canonicalJSON() = when (this) {
        Default -> IdentityContractJSON.encode(mapOf("backendId" to null, "kind" to "default"))
        is Explicit -> IdentityContractJSON.encode(mapOf("backendId" to id, "kind" to "explicit"))
    }
    companion object {
        fun decodeJSON(json: String): IdentityBackendSelection {
            val f = fields(json, setOf("backendId", "kind"))
            return when (f.string("kind")) {
                "default" -> if (f["backendId"] == null) Default else throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                "explicit" -> Explicit(f.string("backendId").also(::token))
                else -> throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }
        }
    }
}
data class IdentityBackendRegistration<B>(val backendId: String, val backend: B, val available: Boolean = true)
class IdentityBackendRegistry<B>(defaultBackendId: String, registrations: List<IdentityBackendRegistration<B>>) {
    private val defaultId = defaultBackendId.also(::token)
    private val entries: Map<String, IdentityBackendRegistration<B>>
    init {
        val table = linkedMapOf<String, IdentityBackendRegistration<B>>()
        for (entry in registrations) {
            token(entry.backendId)
            if (table.put(entry.backendId, entry) != null) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        }
        if (defaultId !in table) throw IdentityError(IdentityErrorCode.BACKEND_NOT_REGISTERED)
        entries = table
    }
    fun select(selection: IdentityBackendSelection): B {
        val id = when (selection) {
            IdentityBackendSelection.Default -> defaultId
            is IdentityBackendSelection.Explicit -> selection.id
        }
        val entry = entries[id] ?: throw IdentityError(IdentityErrorCode.BACKEND_NOT_REGISTERED)
        if (!entry.available) throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, id)
        return entry.backend
    }
}
data class IdentityScoreFields(val score: Double? = null, val metric: String? = null, val threshold: Double? = null) {
    init {
        if (score != null || metric != null || threshold != null) {
            if (score == null || !score.isFinite() || metric == null || threshold == null || !threshold.isFinite())
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            token(metric)
        }
    }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf("metric" to metric, "score" to score, "threshold" to threshold))
}
