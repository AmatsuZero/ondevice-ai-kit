package io.ondeviceai.demo

import io.ondeviceai.identity.*
import kotlin.test.*
import org.junit.Test

class PrototypeIdentityConsentTest {
    @Test fun authorityAcceptsOnlyItsExplicitlyIssuedGrant() {
        val consent = PrototypeIdentityConsent(1_000)
        val grant = consent.grant
        assertEquals(0L, consent.authoritativeEpoch(grant, 0, 1_001))
        val forged = AuthorizationGrant("other", grant.revocationDomainId, grant.scope, grant.purpose,
            grant.issuedAtMillis, grant.expiresAtMillis, 0, grant.evidenceRef)
        assertEquals(IdentityErrorCode.AUTHORIZATION_REQUIRED,
            assertFailsWith<IdentityError> { consent.authoritativeEpoch(forged, 0, 1_001) }.code)
    }

    @Test fun closingConsentStopsFurtherAdmission() {
        val consent = PrototypeIdentityConsent(1_000)
        consent.close()
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED,
            assertFailsWith<IdentityError> { consent.authoritativeEpoch(consent.grant, 0, 1_001) }.code)
    }

    @Test fun ownershipMustBeBoundToTheOpenedStore() {
        val consent = PrototypeIdentityConsent(1_000)
        consent.bindStore("store-a")
        consent.validateOwnership(consent.ownership(), 1_001)
        assertEquals(IdentityErrorCode.AUTHORIZATION_REQUIRED, assertFailsWith<IdentityError> {
            consent.validateOwnership(OwnershipAuthorization("store-b", consent.ownership().evidenceRef), 1_001)
        }.code)
    }
}
