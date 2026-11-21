package io.ondeviceai.demo

import io.ondeviceai.identity.*
import java.util.UUID

/** Demo host authority for one explicit local prototype session, never a production issuer. */
internal class PrototypeIdentityConsent(nowMillis: Long) : IdentityAuthorizationProvider, AutoCloseable {
    private val evidence = UUID.randomUUID().toString()
    val grant = AuthorizationGrant(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        setOf(IdentityScope.ENROLLMENT, IdentityScope.VERIFICATION, IdentityScope.IDENTIFICATION),
        "local-identity-prototype", nowMillis, Math.addExact(nowMillis, 30 * 60 * 1000L), 0, evidence)
    private var active = true
    private var storeId: String? = null

    @Synchronized fun bindStore(value: String) { check(storeId == null); storeId = value }
    @Synchronized fun ownership() = OwnershipAuthorization(checkNotNull(storeId), evidence)

    @Synchronized override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        if (!active || minimumEpoch != 0L) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
        if (grant.canonicalJSON() != this.grant.canonicalJSON() || nowMillis < grant.issuedAtMillis)
            throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        if (nowMillis >= checkNotNull(grant.expiresAtMillis)) throw IdentityError(IdentityErrorCode.AUTHORIZATION_EXPIRED)
        return 0
    }
    @Synchronized override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {
        if (authorization.storeId != storeId || authorization.evidenceRef != evidence)
            throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
    }
    @Synchronized override fun close() { active = false }
}
