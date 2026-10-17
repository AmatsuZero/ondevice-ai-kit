package io.ondeviceai.identity

import kotlin.test.*

class BackendPolicyTest {
    @Test fun backendSpacesMatchSharedWireFixture() {
        var root = java.io.File(requireNotNull(System.getProperty("user.dir")))
        while (!java.io.File(root, "fixtures/contracts/face-identity.json").exists()) root = root.parentFile ?: error("fixture missing")
        val fixture = IdentityContractJSON.parse(java.io.File(root, "fixtures/contracts/face-identity.json").readText())
        val spaces = fixture["fixtureBackendSpaces"] as Map<*, *>
        for (backend in listOf(FixtureIdentityBackend.system(), FixtureIdentityBackend.optionalUnavailable())) {
            assertEquals(spaces[backend.backendId], backend.embeddingSpace.canonicalJSON())
        }
    }
    @Test fun verifiedPolicyRequiresEvidenceAndExactScopeEpochAndDirection() {
        val space = FixtureIdentityBackend.system().embeddingSpace
        assertFailsWith<IdentityError> { ThresholdPolicy("p", "v1", space, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.VERIFIED) }
        assertFailsWith<IdentityError> { ThresholdPolicy("p", "v1", space, "cosine", ScoreDirection.LOWER_IS_BETTER, CalibrationStatus.UNVERIFIED) }
        val policy = fixtureThreshold(space)
        policy.validate(space, IdentityPolicyKind.VERIFICATION, 3)
        assertFailsWith<IdentityError> { policy.validate(space, IdentityPolicyKind.IDENTIFICATION, 3) }
        assertFailsWith<IdentityError> { policy.validate(space, IdentityPolicyKind.VERIFICATION, 4) }
        assertEquals(0.8, policy.threshold)
        assertEquals("synthetic-v1", policy.datasetRevision)
        assertEquals(0.5, policy.qualityFloor)
    }
    @Test fun identificationPolicyEnforcesGalleryStrata() {
        val space = FixtureIdentityBackend.system().embeddingSpace
        val policy = fixtureThreshold(space, IdentityPolicyKind.IDENTIFICATION, 2..4)
        policy.validate(space, IdentityPolicyKind.IDENTIFICATION, 3, 2)
        for (size in listOf(null, 0, 1, 5)) {
            assertFailsWith<IdentityError> { policy.validate(space, IdentityPolicyKind.IDENTIFICATION, 3, size) }
        }
    }
    @Test fun padPolicyEnforcesIndependentBindingAndCategoricalDecisionShape() {
        val backend = FixtureIdentityBackend.system()
        assertFailsWith<IdentityError> { PADPolicy("pad", "v1", backend.backendId, backend.backendVersion, backend.modelBinding, CalibrationStatus.VERIFIED) }
        val policy = PADPolicy("pad", "v1", backend.backendId, backend.backendVersion, backend.modelBinding, CalibrationStatus.VERIFIED,
            mode = PADMode.ACTIVE, challengePolicyRevision = "challenge-v1", minimumFrames = 3, maximumDurationNanos = 1000,
            qualityFloor = 0.5, classificationPolicyRevision = "categorical-fixture-v1", datasetRevision = "synthetic-pad-v1", operatingPoint = "test-only", attackTypes = setOf("fixture-replay"), effectiveAuthorizationEpoch = 3)
        policy.validate(backend.backendId, backend.backendVersion, backend.modelBinding, PADMode.ACTIVE, 3)
        assertNull(policy.threshold)
        assertNull(policy.scoreMetric)
        assertFailsWith<IdentityError> { policy.validate("other", backend.backendVersion, backend.modelBinding, PADMode.ACTIVE, 3) }
        assertFailsWith<IdentityError> { policy.validate(backend.backendId, backend.backendVersion, backend.modelBinding, PADMode.PASSIVE, 3) }
    }
    @Test fun fixtureBackendRejectsImagesWrongDimensionAndClosedUse() {
        val backend = FixtureIdentityBackend.system()
        val values = mutableListOf(1f, 0f)
        val probe = IdentityProbe("unit-a", values)
        values[0] = 0f
        assertEquals(listOf(1f, 0f), backend.embed(probe).values)
        assertFailsWith<IdentityError> { backend.embed(IdentityProbe("wrong", listOf(1f))) }
        assertFailsWith<IdentityError> { backend.embed(IdentityProbe(byteArrayOf(1), "r1")) }
        assertFailsWith<IdentityError> { IdentityQuality(1, Double.NaN) }
        assertFailsWith<IdentityError> { IdentityQuality(1, 2.0) }
        backend.close(); backend.close()
        assertEquals(io.ondeviceai.core.UnavailableReason.CLOSED, backend.availability().reason)
        assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { backend.embed(probe) }.code)
    }
    @Test fun explicitBackendNeverFallsBackAndBindingIsExposed() {
        val system = FixtureIdentityBackend.system(); val optional = FixtureIdentityBackend.optionalUnavailable()
        val registry = IdentityBackendRegistry(system.backendId, listOf(IdentityBackendRegistration(system.backendId, system), IdentityBackendRegistration(optional.backendId, optional, false)))
        assertEquals("system.fixture", registry.select(IdentityBackendSelection.Default).backendId)
        assertEquals(IdentityErrorCode.BACKEND_UNAVAILABLE, assertFailsWith<IdentityError> { registry.select(IdentityBackendSelection.Explicit("optional.fixture")) }.code)
        assertEquals(IdentityErrorCode.BACKEND_NOT_REGISTERED, assertFailsWith<IdentityError> { registry.select(IdentityBackendSelection.Explicit("missing.fixture")) }.code)
        assertEquals(ModelBindingKind.SYSTEM, system.modelBinding.kind)
        assertTrue(system.modelBinding.revision.isNotEmpty())
    }
    @Test fun sameBackendWithDifferentModelBindingDoesNotShareEmbeddingSpace() {
        val a = FixtureIdentityBackend.system("os-a"); val b = FixtureIdentityBackend.system("os-b")
        assertEquals(a.backendId, b.backendId); assertNotEquals(a.embeddingSpace, b.embeddingSpace)
        val policy = ThresholdPolicy("verify", "v1", a.embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.UNVERIFIED)
        assertEquals(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, assertFailsWith<IdentityError> { policy.validate(b.embeddingSpace) }.code)
    }
    @Test fun unverifiedPoliciesFailClosedBeforeScoreOrOutcome() {
        val backend = FixtureIdentityBackend.system()
        val threshold = ThresholdPolicy("verify", "draft", backend.embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.UNVERIFIED)
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> { threshold.requireVerified() }.code)
        val pad = PADPolicy("pad", "draft", backend.backendId, backend.backendVersion, backend.modelBinding, CalibrationStatus.UNVERIFIED)
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> { pad.requireVerified() }.code)
        assertEquals(1, backend.evaluateQuality(IdentityProbe("fixture", listOf(0.1f, 0.2f))).faceCount)
        assertFailsWith<IdentityError> { threshold.validate(backend.embeddingSpace) }
    }
}

private fun fixtureThreshold(space: EmbeddingSpaceId, kind: IdentityPolicyKind = IdentityPolicyKind.VERIFICATION, gallerySizeRange: IntRange? = null) =
    ThresholdPolicy("fixture-policy", "v1", space, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.VERIFIED,
        kind, 0.8, 0.5, "test-only", "synthetic-v1", gallerySizeRange, "one-fixture", 3)
