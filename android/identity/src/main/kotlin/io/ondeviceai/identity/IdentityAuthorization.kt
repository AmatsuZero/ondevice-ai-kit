package io.ondeviceai.identity

/** Trusted host authority; the SDK never derives the current revocation epoch itself. */
interface IdentityAuthorizationProvider {
    fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long
    fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long)
}

/** Immutable, journal-ready authorization metadata. It does not publish or persist by itself. */
internal class IdentityAuthorizationState private constructor(
    private val purpose: String,
    private val epochs: Map<String, Long>,
    private val grants: Map<String, AuthorizationGrant>
) {
    constructor(purpose: String) : this(purpose.also(::token), emptyMap(), emptyMap())

    fun highestEpoch(domain: String): Long = epochs[domain.also(::token)] ?: 0

    fun admitting(
        grant: AuthorizationGrant,
        scope: IdentityScope,
        provider: IdentityAuthorizationProvider,
        nowMillis: Long
    ): IdentityAuthorizationState {
        if (nowMillis < 0) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        if (grant.purpose != purpose) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        val existing = grants[grant.grantId]
        if (existing != null && existing.canonicalJSON() != grant.canonicalJSON())
            throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        val bound = highestEpoch(grant.revocationDomainId)
        val authoritative = provider.authoritativeEpoch(grant, bound, nowMillis)
        if (authoritative < 0 || authoritative != grant.authorizationEpoch || authoritative < bound)
            throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
        grant.validate(scope, purpose, authoritative, FixedClock(nowMillis))
        val nextEpochs = epochs.toMutableMap()
        nextEpochs[grant.revocationDomainId] = authoritative
        val nextGrants = grants.toMutableMap().also { it.putIfAbsent(grant.grantId, grant) }
        return IdentityAuthorizationState(purpose, nextEpochs, nextGrants)
    }

    fun revoking(grantId: String): IdentityAuthorizationState {
        token(grantId)
        val grant = grants[grantId] ?: throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        val current = highestEpoch(grant.revocationDomainId)
        if (grant.authorizationEpoch != current) return this
        if (current == Long.MAX_VALUE) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return IdentityAuthorizationState(purpose, epochs.toMutableMap().also {
            it[grant.revocationDomainId] = current + 1
        }, grants)
    }

    fun canonicalJSON(): String {
        val json = IdentityContractJSON.encode(mapOf(
            "epochs" to epochs,
            "grants" to grants.mapValues { it.value.canonicalJSON() },
            "purpose" to purpose,
            "version" to 1
        ))
        if (json.toByteArray(Charsets.UTF_8).size > 65536) {
            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        }
        return json
    }

    companion object {
        fun requireOwnership(
            ownership: OwnershipAuthorization,
            storeId: String,
            provider: IdentityAuthorizationProvider,
            nowMillis: Long
        ) {
            token(storeId)
            if (ownership.storeId != storeId) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
            provider.validateOwnership(ownership, nowMillis)
        }

        fun restore(json: String, purpose: String): IdentityAuthorizationState {
            token(purpose)
            val root = fields(json, setOf("epochs", "grants", "purpose", "version"))
            if (root.long("version") != 1L || root.string("purpose") != purpose)
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            @Suppress("UNCHECKED_CAST")
            val rawEpochs = root["epochs"] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            @Suppress("UNCHECKED_CAST")
            val rawGrants = root["grants"] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val epochs = rawEpochs.mapValues { (domain, value) ->
                token(domain)
                (value as? Long)?.takeIf { it >= 0 } ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }
            val grants = rawGrants.mapValues { (id, value) ->
                token(id)
                val grant = AuthorizationGrant.decodeJSON(value as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT))
                if (grant.grantId != id || grant.purpose != purpose || grant.authorizationEpoch > (epochs[grant.revocationDomainId]
                    ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT))) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                grant
            }
            return IdentityAuthorizationState(purpose, epochs, grants)
        }
    }

    private data class FixedClock(override val nowMillis: Long) : IdentityWallClock
}
