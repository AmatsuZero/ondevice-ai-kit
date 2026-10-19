package io.ondeviceai.identity

import java.io.File
import kotlin.test.*

class IdentityPolicyCodecTest {
    private fun fixture(key: String): String {
        var root = File(requireNotNull(System.getProperty("user.dir")))
        while (!File(root, "fixtures/contracts/identity-policies.json").exists()) root = root.parentFile ?: error("fixture missing")
        return IdentityContractJSON.parse(File(root, "fixtures/contracts/identity-policies.json").readText())[key] as String
    }
    @Test fun sharedVerifiedIdentificationPolicyPreservesAllFieldsAndExactBits() {
        val json = fixture("thresholdJSON")
        val p = ThresholdPolicy.decodeJSON(json)
        assertEquals(json, p.canonicalJSON())
        assertEquals("identify-policy", p.id); assertEquals("r2", p.revision)
        assertEquals(IdentityPolicyKind.IDENTIFICATION, p.kind)
        assertEquals(CalibrationStatus.VERIFIED, p.calibrationStatus)
        assertEquals(0.8.toRawBits(), p.threshold!!.toRawBits())
        assertEquals(0.625, p.qualityFloor); assertEquals(1..32, p.gallerySizeRange)
        assertEquals("synthetic-data", p.datasetRevision); assertEquals("multi-template", p.enrollmentRule)
        assertEquals(7, p.effectiveAuthorizationEpoch)
        p.validate(p.embeddingSpace, IdentityPolicyKind.IDENTIFICATION, 7, 2)
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> { p.validate(p.embeddingSpace, IdentityPolicyKind.IDENTIFICATION, 8, 2) }.code)
    }
    @Test fun sharedActivePADPolicyKeepsIndependentBindingAndCaptureConstraints() {
        val json = fixture("padJSON"); val p = PADPolicy.decodeJSON(json)
        assertEquals(json, p.canonicalJSON())
        assertEquals(PADMode.ACTIVE, p.mode); assertEquals(3, p.minimumFrames)
        assertEquals(9876543210L, p.maximumDurationNanos); assertEquals("challenge-v2", p.challengePolicyRevision)
        assertEquals(setOf("print", "replay"), p.attackTypes); assertEquals("class-v2", p.classificationPolicyRevision)
        assertNull(p.scoreMetric); assertNull(p.threshold)
        p.validate(p.backendId, p.backendVersion, p.modelBinding, p.mode, 7)
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> { p.validate(p.backendId, "other", p.modelBinding, p.mode, 7) }.code)
    }
    @Test fun unverifiedPoliciesStayUnverifiedWithNullCalibrationFields() {
        val space = ThresholdPolicy.decodeJSON(fixture("thresholdJSON")).embeddingSpace
        val identity = ThresholdPolicy("draft", "1", space, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.UNVERIFIED)
        val restored = ThresholdPolicy.decodeJSON(identity.canonicalJSON())
        assertEquals(identity, restored); assertNull(restored.threshold)
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> { restored.requireVerified() }.code)
        val pad = PADPolicy("draft-pad", "1", "fixture.pad", "1", ModelBinding(ModelBindingKind.SYSTEM, "fixture.pad", "os"), CalibrationStatus.UNVERIFIED)
        val restoredPAD = PADPolicy.decodeJSON(pad.canonicalJSON())
        assertEquals(pad.canonicalJSON(), restoredPAD.canonicalJSON())
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> { restoredPAD.requireVerified() }.code)
    }
    @Test fun policyCodecRejectsCoercionUnknownFieldsMalformedBitsAndBindingChanges() {
        val identity = fixture("thresholdJSON"); val pad = fixture("padJSON")
        for (value in listOf("7.0", "7e0", "true", "\"7\"", "9223372036854775808")) {
            assertFailsWith<IdentityError> { ThresholdPolicy.decodeJSON(identity.replace("\"effectiveAuthorizationEpoch\":7", "\"effectiveAuthorizationEpoch\":" + value)) }
            assertFailsWith<IdentityError> { PADPolicy.decodeJSON(pad.replace("\"effectiveAuthorizationEpoch\":7", "\"effectiveAuthorizationEpoch\":" + value)) }
        }
        assertFailsWith<IdentityError> { ThresholdPolicy.decodeJSON(identity.dropLast(1) + ",\"unknown\":1}") }
        assertFailsWith<IdentityError> { PADPolicy.decodeJSON(pad.replace("\"minimumFrames\":3", "\"minimumFrames\":3,\"minimumFrames\":4")) }
        for (bits in listOf("3fe999999999999", "3FE999999999999A", "7ff0000000000000", "7ff8000000000000", "not-hex")) {
            assertFailsWith<IdentityError> { ThresholdPolicy.decodeJSON(identity.replace("3fe999999999999a", bits)) }
        }
        assertFailsWith<IdentityError> { PADPolicy.decodeJSON(pad.replace("[\"print\",\"replay\"]", "[\"print\",\"print\"]")) }
        assertFailsWith<IdentityError> { ThresholdPolicy.decodeJSON(identity.replace("\"upper\":32", "\"upper\":2147483648")) }
        assertFailsWith<IdentityError> { PADPolicy.decodeJSON(pad.replace("\"minimumFrames\":3", "\"minimumFrames\":2147483648")) }
    }
}
