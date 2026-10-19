package io.ondeviceai.binding

import kotlin.test.*

class IdentityBridgeContractTest {
    @Test fun closedUnavailableBridgeRejectsAdmissionWithClosed() {
        val bridge = UnavailableIdentityBridge()
        bridge.close()
        var failure: IdentityBridgeFailure? = null
        bridge.createCandidate(IdentityAuthorizationGrant("{}"), object : IdentityCallback<IdentityCandidateReference> {
            override fun onSuccess(value: IdentityCandidateReference) = fail("must not create identity")
            override fun onFailure(error: IdentityBridgeFailure) { failure = error }
        })
        assertEquals(IdentityBridgeFailure.Rejected(IdentityErrorCode.CLOSED), failure)
    }

    @Test fun scoreTripleRejectsPartialAndNonFiniteValues() {
        assertFailsWith<IllegalArgumentException> { IdentityScoreFields(0.2, null, null) }
        assertFailsWith<IllegalArgumentException> { IdentityScoreFields(Double.NaN, "cosine", 0.5) }
        assertFailsWith<IllegalArgumentException> { IdentityScoreFields(0.2, "cosine", Double.POSITIVE_INFINITY) }
        assertNull(IdentityScoreFields().score)
    }

    @Test fun nativeCanonicalValuesArePreservedWithoutGuessingTokenMeaning() {
        val grant = IdentityAuthorizationGrant("{\"grantId\":\"host-opaque\",\"authorizationEpoch\":8}")
        val candidate = IdentityCandidateReference("{\"id\":\"host-opaque\",\"storeId\":\"opaque\"}")
        assertEquals("{\"grantId\":\"host-opaque\",\"authorizationEpoch\":8}", grant.canonicalValue)
        assertEquals("{\"id\":\"host-opaque\",\"storeId\":\"opaque\"}", candidate.canonicalValue)
        assertEquals("vendor.identity", IdentityBackendSelection.Explicit("vendor.identity").backendId)
    }

    @Test fun livenessFramesOwnAdmittedBytes() {
        val bytes = byteArrayOf(1, 2)
        val frame = IdentityLivenessFrame(bytes, 42, "capture-v1", null)
        bytes[0] = 9
        val returned = frame.encodedFrame
        returned[1] = 9
        assertContentEquals(byteArrayOf(1, 2), frame.encodedFrame)
    }
}
