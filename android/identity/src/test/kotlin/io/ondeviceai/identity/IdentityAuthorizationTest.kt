package io.ondeviceai.identity

import kotlin.test.*

class IdentityAuthorizationTest {
    @Test fun providerEpochMustEqualGrantAndCannotRollBackBound() {
        val state = IdentityAuthorizationState("login")
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { state.admitting(grant(), IdentityScope.ENROLLMENT, Authority(2), 100) }
        val current = state.admitting(grant(epoch = 4), IdentityScope.ENROLLMENT, Authority(4), 100)
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { current.admitting(grant("rollback", epoch = 3), IdentityScope.ENROLLMENT, Authority(3), 100) }
    }

    @Test fun firstZeroEpochAdmissionCanSnapshotAndReopen() {
        val admitted = IdentityAuthorizationState("login")
            .admitting(grant(), IdentityScope.ENROLLMENT, Authority(), 100)
        val restored = IdentityAuthorizationState.restore(admitted.canonicalJSON(), "login")
        assertEquals(0, restored.highestEpoch("tenant.subject"))
    }

    @Test fun domainRevocationSurvivesNewGrantIdAndUnrelatedDomain() {
        val admitted = IdentityAuthorizationState("login").admitting(grant(), IdentityScope.ENROLLMENT, Authority(), 100)
        val revoked = admitted.revoking("g1")
        assertEquals(1, revoked.highestEpoch("tenant.subject"))
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { revoked.admitting(grant("new"), IdentityScope.ENROLLMENT, Authority(), 100) }
        val other = revoked.admitting(grant("other", domain = "other.subject"), IdentityScope.ENROLLMENT, Authority(), 100)
        assertEquals(0, other.highestEpoch("other.subject"))
    }

    @Test fun declarationsAreImmutableAndScopePurposeTimeAreChecked() {
        val original = grant()
        val state = IdentityAuthorizationState("login").admitting(original, IdentityScope.ENROLLMENT, Authority(), 100)
        for (changed in listOf(
            grant(domain = "other.subject"), grant(epoch = 1), grant(purpose = "other"),
            grant(scopes = setOf(IdentityScope.VERIFICATION)), grant(issued = 1), grant(expires = 300), grant(evidence = "other")
        )) {
            val provider = RecordingAuthority(changed.authorizationEpoch)
            assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { state.admitting(changed, IdentityScope.ENROLLMENT, provider, 100) }
            assertEquals(0, provider.epochCalls)
        }
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { state.admitting(grant(issued = 101), IdentityScope.ENROLLMENT, Authority(), 100) }
        assertCode(IdentityErrorCode.AUTHORIZATION_EXPIRED) { state.admitting(original, IdentityScope.ENROLLMENT, Authority(), 200) }
    }

    @Test fun revokeIsIdempotentAndDoesNotMutateOriginal() {
        val state = IdentityAuthorizationState("login")
            .admitting(grant(), IdentityScope.ENROLLMENT, Authority(), 100)
            .admitting(grant(id = "sibling"), IdentityScope.ENROLLMENT, Authority(), 100)
        val revoked = state.revoking("g1")
        assertEquals(revoked.canonicalJSON(), revoked.revoking("g1").canonicalJSON())
        assertEquals(revoked.canonicalJSON(), revoked.revoking("sibling").canonicalJSON())
        assertEquals(0, state.highestEpoch("tenant.subject"))
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { revoked.revoking("unknown") }
    }

    @Test fun providerDenialAndOwnershipBindingAreIndependent() {
        val ownership = OwnershipAuthorization("store-a", "owner")
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { IdentityAuthorizationState.requireOwnership(ownership, "store-b", Authority(), 100) }
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { IdentityAuthorizationState.requireOwnership(ownership, "store-a", Authority(denied = true), 100) }
        IdentityAuthorizationState.requireOwnership(ownership, "store-a", Authority(), 100)
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { IdentityAuthorizationState("login").admitting(grant(), IdentityScope.ENROLLMENT, Authority(denied = true), 100) }
    }

    @Test fun snapshotRoundTripAndMalformedBindingsAreRejected() {
        val state = IdentityAuthorizationState("login").admitting(grant(), IdentityScope.ENROLLMENT, Authority(), 100).revoking("g1")
        val restored = IdentityAuthorizationState.restore(state.canonicalJSON(), "login")
        assertEquals(state.canonicalJSON(), restored.canonicalJSON())
        assertCode(IdentityErrorCode.INVALID_INPUT) { IdentityAuthorizationState.restore(state.canonicalJSON().replace("\"version\":1", "\"version\":2"), "login") }
        assertCode(IdentityErrorCode.INVALID_INPUT) { IdentityAuthorizationState.restore(state.canonicalJSON(), "other") }
        assertCode(IdentityErrorCode.INVALID_INPUT) { IdentityAuthorizationState.restore(state.canonicalJSON().replace("\"g1\"", "\"wrong\""), "login") }
        for (invalidEpoch in listOf("true", "1.0", "-1")) {
            assertCode(IdentityErrorCode.INVALID_INPUT) {
                IdentityAuthorizationState.restore(state.canonicalJSON().replace("\"tenant.subject\":1", "\"tenant.subject\":$invalidEpoch"), "login")
            }
        }
        assertCode(IdentityErrorCode.INVALID_INPUT) {
            IdentityAuthorizationState.restore(state.canonicalJSON().replace("\"epochs\":{\"tenant.subject\":1}", "\"epochs\":{}"), "login")
        }
    }

    @Test fun trustedNewEpochCreatesNewValidGrantAndProviderSeesPersistedBound() {
        val current = IdentityAuthorizationState("login")
            .admitting(grant(epoch = 4), IdentityScope.ENROLLMENT, Authority(4), 100)
        val provider = RecordingAuthority(4)
        val sameEpoch = current.admitting(grant(id = "new", epoch = 4), IdentityScope.ENROLLMENT, provider, 100)
        assertEquals(4, provider.minimumEpoch)
        assertEquals(4, sameEpoch.highestEpoch("tenant.subject"))
        val revoked = current.revoking("g1")
        val renewed = revoked.admitting(grant(id = "new", epoch = 5), IdentityScope.ENROLLMENT, Authority(5), 100)
        assertEquals(5, renewed.highestEpoch("tenant.subject"))
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { renewed.admitting(grant(epoch = 4), IdentityScope.ENROLLMENT, Authority(5), 100) }
    }

    @Test fun snapshotEncoderRejectsMetadataThatRestoreWouldNotRead() {
        var state = IdentityAuthorizationState("login")
        repeat(200) { index ->
            state = state.admitting(
                grant(id = "g$index", domain = "domain$index", evidence = "a".repeat(256)),
                IdentityScope.ENROLLMENT,
                Authority(),
                100
            )
        }
        assertCode(IdentityErrorCode.INVALID_INPUT) { state.canonicalJSON() }
    }

    @Test fun overflowCannotAdvanceEpoch() {
        val state = IdentityAuthorizationState("login").admitting(grant(epoch = Long.MAX_VALUE), IdentityScope.ENROLLMENT, Authority(Long.MAX_VALUE), 100)
        assertCode(IdentityErrorCode.INVALID_INPUT) { state.revoking("g1") }
        assertEquals(Long.MAX_VALUE, state.highestEpoch("tenant.subject"))
    }

    private fun assertCode(code: IdentityErrorCode, block: () -> Any?) {
        assertEquals(code, assertFailsWith<IdentityError> { block() }.code)
    }

    private fun grant(id: String = "g1", domain: String = "tenant.subject", epoch: Long = 0,
                      purpose: String = "login", scopes: Set<IdentityScope> = setOf(IdentityScope.ENROLLMENT),
                      issued: Long = 0, expires: Long? = 200, evidence: String = "audit") =
        AuthorizationGrant(id, domain, scopes, purpose, issued, expires, epoch, evidence)
}

private data class Authority(val epoch: Long = 0, val denied: Boolean = false) : IdentityAuthorizationProvider {
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        if (denied) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
        return epoch
    }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {
        if (denied) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
    }
}

private class RecordingAuthority(private val epoch: Long) : IdentityAuthorizationProvider {
    var epochCalls = 0
    var minimumEpoch: Long? = null
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        epochCalls++
        this.minimumEpoch = minimumEpoch
        return epoch
    }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) = Unit
}
