package io.ondeviceai.identity

import kotlin.test.*
import java.io.File

class IdentityContractsTest {
    @Test fun referencesRejectEmptyAndForgedMetadata() {
        assertFailsWith<IdentityError> { CandidateRef("", "store") }
        val issued = CandidateRef("candidate", "store")
        assertFailsWith<IdentityError> { CandidateRef("candidate", "other").validate(issued) }
        issued.validate(issued)
        assertFailsWith<IdentityError> { GalleryRef("gallery", "") }
    }
    @Test fun grantUsesClockScopePurposeAndAuthoritativeEpoch() {
        val grant = AuthorizationGrant("g1", "domain", setOf(IdentityScope.VERIFICATION), "auth", 900, 1100, 3, "e1")
        grant.validate(IdentityScope.VERIFICATION,"auth",3, Clock(1000))
        fun check(scope:IdentityScope,purpose:String,epoch:Long,now:Long,code:IdentityErrorCode) {
            assertEquals(code,assertFailsWith<IdentityError> { grant.validate(scope,purpose,epoch,Clock(now)) }.code)
        }
        check(IdentityScope.ENROLLMENT,"auth",3,1000,IdentityErrorCode.AUTHORIZATION_REQUIRED)
        check(IdentityScope.VERIFICATION,"other",3,1000,IdentityErrorCode.AUTHORIZATION_REQUIRED)
        check(IdentityScope.VERIFICATION,"auth",3,899,IdentityErrorCode.AUTHORIZATION_REQUIRED)
        check(IdentityScope.VERIFICATION,"auth",3,1100,IdentityErrorCode.AUTHORIZATION_EXPIRED)
        check(IdentityScope.VERIFICATION,"auth",4,1000,IdentityErrorCode.AUTHORIZATION_REVOKED)
        check(IdentityScope.VERIFICATION,"auth",2,1000,IdentityErrorCode.AUTHORIZATION_REVOKED)
        assertFailsWith<IdentityError> { AuthorizationGrant("g","d",setOf(IdentityScope.VERIFICATION),"auth",1000,900,0,"e") }
    }
    @Test fun registryRejectsDuplicatesAndNeverFallsBack() {
        val registry = IdentityBackendRegistry("system", listOf(IdentityBackendRegistration("system",1),IdentityBackendRegistration("optional",2,false)))
        assertEquals(1,registry.select(IdentityBackendSelection.Default))
        assertEquals(IdentityErrorCode.BACKEND_NOT_REGISTERED, assertFailsWith<IdentityError> { registry.select(IdentityBackendSelection.Explicit("missing")) }.code)
        assertEquals(IdentityErrorCode.BACKEND_UNAVAILABLE, assertFailsWith<IdentityError> { registry.select(IdentityBackendSelection.Explicit("optional")) }.code)
        assertFailsWith<IdentityError> { IdentityBackendRegistry("x",listOf(IdentityBackendRegistration("x",1),IdentityBackendRegistration("x",2))) }
    }
    @Test fun scoreFieldsRejectPartialOrNonFiniteValuesAndKeepNulls() {
        assertEquals("{\"metric\":null,\"score\":null,\"threshold\":null}",IdentityScoreFields().canonicalJSON())
        assertFailsWith<IdentityError> { IdentityScoreFields(score=0.8) }
        assertFailsWith<IdentityError> { IdentityScoreFields(Double.NaN,"cosine",0.7) }
        assertFailsWith<IdentityError> { IdentityScoreFields(0.8,"cosine",Double.POSITIVE_INFINITY) }
    }
    @Test fun canonicalFixtureAndStableErrorsMatchSharedFile() {
        var root=File(requireNotNull(System.getProperty("user.dir")))
        while(!File(root,"fixtures/contracts/face-identity.json").exists()) { root=root.parentFile ?: error("fixture missing") }
        val fixture=IdentityContractJSON.parse(File(root,"fixtures/contracts/face-identity.json").readText())
        assertEquals(IdentityErrorCode.entries.map { it.wire }.toSet(),(fixture["errorCodes"] as List<*>).toSet())
        val candidate=CandidateRef("candidate-1","store-1")
        assertEquals(fixture["candidateJSON"],candidate.canonicalJSON())
        assertEquals(fixture["defaultSelectionJSON"],IdentityBackendSelection.Default.canonicalJSON())
        assertEquals(fixture["nullScoreJSON"],IdentityScoreFields().canonicalJSON())
        assertEquals(candidate,CandidateRef.decodeJSON(candidate.canonicalJSON()))
        assertFailsWith<IdentityError> { CandidateRef.decodeJSON("{\"id\":\"candidate-1\",\"storeId\":\"store-1\",\"score\":1}") }
        assertFailsWith<IdentityError> { CandidateRef.decodeJSON("{\"id\":\"\",\"storeId\":\"store-1\"}") }
    }
    @Test fun templateRequiresExactIssuedSpaceEpochAndRevision() {
        val space=makeSpace("model-v1")
        val issued=TemplateRef("t1",space,3,"r1")
        issued.validate(issued)
        assertEquals(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH,assertFailsWith<IdentityError> { TemplateRef("t1",makeSpace("model-v2"),3,"r1").validate(issued) }.code)
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED,assertFailsWith<IdentityError> { TemplateRef("t1",space,4,"r1").validate(issued) }.code)
        assertFailsWith<IdentityError> { TemplateRef("t1",space,3,"r2").validate(issued) }
        assertFailsWith<IdentityError> { TemplateRef("t1",space,-1,"r1") }
        assertEquals(issued,TemplateRef.decodeJSON(issued.canonicalJSON()))
        assertFailsWith<IdentityError> { TemplateRef.decodeJSON(issued.canonicalJSON().dropLast(1)+",\"score\":0.99}") }
    }
    @Test fun embeddingSpaceAndModelBindingRejectUnsupportedOrEmptyValues() {
        assertNotEquals(makeSpace("v1"),makeSpace("v2"))
        assertFailsWith<IdentityError> { makeSpace("v1",0) }
        assertFailsWith<IdentityError> { ModelBinding(ModelBindingKind.SYSTEM,"system","") }
        val system=ModelBinding(ModelBindingKind.SYSTEM,"system","runtime-os-build")
        assertEquals(system,ModelBinding.decodeJSON(system.canonicalJSON()))
        assertFailsWith<IdentityError> { EmbeddingSpaceId.decodeJSON(makeSpace("v1").canonicalJSON().replace("\"featureFormatVersion\":\"1\"","\"featureFormatVersion\":\"999\"")) }
    }
    @Test fun deadlineUsesOnlyInjectedMonotonicClockAndRejectsOverflow() {
        val deadline=IdentityDeadline(Mono(100),50)
        assertFalse(deadline.isExpired(Mono(149)))
        assertTrue(deadline.isExpired(Mono(150)))
        assertFailsWith<IdentityError> { IdentityDeadline(Mono(Long.MAX_VALUE),1) }
        assertFailsWith<IdentityError> { IdentityDeadline(Mono(100),0) }
    }
    @Test fun independentPolicyOwnershipAndProofReferencesValidateBinding() {
        val threshold=ThresholdPolicyRef("verify","v1")
        val pad=PADPolicyRef("pad","v1")
        assertEquals(threshold,ThresholdPolicyRef.decodeJSON(threshold.canonicalJSON()))
        assertEquals(pad,PADPolicyRef.decodeJSON(pad.canonicalJSON()))
        assertFailsWith<IdentityError> { PADPolicyRef("pad","") }
        assertFailsWith<IdentityError> { OwnershipAuthorization("","e") }
        assertEquals("store",OwnershipAuthorization("store","e").storeId)
        assertFailsWith<IdentityError> { LivenessProof("proof","store","session","nonce","auth","not-a-digest",pad,ModelBinding(ModelBindingKind.MODEL,"fixture","v1"),1,100,200) }
    }
    @Test fun wireRejectsUnknownCapabilitiesAndCoercedIntegers() {
        val space=makeSpace("v1")
        assertFailsWith<IdentityError> { EmbeddingSpaceId.decodeJSON(space.canonicalJSON().replace("\"identification\"","\"liveness\"")) }
        val template=TemplateRef("t",space,1,"r")
        for(value in listOf("true","1.5","1.0","1e0","\"1\"","9223372036854775808")) {
            assertFailsWith<IdentityError> { TemplateRef.decodeJSON(template.canonicalJSON().replace("\"authorizationEpoch\":1","\"authorizationEpoch\":"+value)) }
        }
        for(json in listOf("{\"id\":\"a\",\"storeId\":\"s\"} trailing","{\"id\":\"a\",\"storeId\":1}","{\"id\":\"a\",\"storeId\":\"s\",\"issuer\":\"forged\"}")) {
            assertFailsWith<IdentityError> { CandidateRef.decodeJSON(json) }
        }
        assertFailsWith<IdentityError> { CandidateRef.decodeJSON("{\"id\":\"forged\",\"id\":\"a\",\"storeId\":\"s\"}") }
        assertFailsWith<IdentityError> { TemplateRef.decodeJSON(template.canonicalJSON().replace("\"dimension\":2","\"dimension\":2.0")) }
        val mutable=mutableSetOf(IdentityScope.VERIFICATION)
        val grant=AuthorizationGrant("g","d",mutable,"auth",0,null,1,"e")
        mutable.add(IdentityScope.ENROLLMENT)
        assertFailsWith<IdentityError> { grant.validate(IdentityScope.ENROLLMENT,"auth",1,Clock(1)) }
    }
    @Test fun deadlineCapturesClockOnceAndProofRejectsNegativeWallTime() {
        val clock=object:IdentityMonotonicClock { var value=100L; override val nowNanos get()=value++ }
        assertTrue(IdentityDeadline(clock,50).isExpired(Mono(150)))
        assertFailsWith<IdentityError> { LivenessProof("proof","store","session","nonce","auth","a".repeat(64),PADPolicyRef("pad","v1"),ModelBinding(ModelBindingKind.MODEL,"fixture","v1"),1,-1,200) }
    }
    @Test fun completeWireFixturesAndAllErrorEnvelopes() {
        var root=File(requireNotNull(System.getProperty("user.dir")))
        while(!File(root,"fixtures/contracts/face-identity.json").exists()) { root=root.parentFile ?: error("fixture missing") }
        val fixture=IdentityContractJSON.parse(File(root,"fixtures/contracts/face-identity.json").readText())
        val space=makeSpace("v1")
        val template=TemplateRef("template-1",space,Long.MAX_VALUE,"r1")
        assertEquals(fixture["templateJSON"],template.canonicalJSON())
        assertEquals(template,TemplateRef.decodeJSON(template.canonicalJSON()))
        val grantJSON=fixture["grantJSON"] as String
        val grant=AuthorizationGrant.decodeJSON(grantJSON)
        assertEquals(grantJSON,grant.canonicalJSON())
        grant.validate(IdentityScope.VERIFICATION,"auth",Long.MAX_VALUE,Clock(1000))
        assertEquals(fixture["galleryJSON"],GalleryRef.decodeJSON(fixture["galleryJSON"] as String).canonicalJSON())
        assertEquals(IdentityBackendSelection.Explicit("optional"),IdentityBackendSelection.decodeJSON(fixture["explicitSelectionJSON"] as String))
        assertFailsWith<IdentityError> { IdentityBackendSelection.decodeJSON("{\"backendId\":\"optional\",\"kind\":\"default\"}") }
        val proofJSON=fixture["proofJSON"] as String
        val proof=LivenessProof.decodeJSON(proofJSON)
        assertEquals(proofJSON,proof.canonicalJSON())
        assertEquals(Long.MAX_VALUE,proof.authorizationEpoch)
        val errors=fixture["errorJSON"] as Map<*,*>
        for(code in IdentityErrorCode.entries) {
            val error=IdentityError(code)
            assertEquals(errors[code.wire],error.canonicalJSON())
            assertEquals(code,IdentityError.decodeJSON(error.canonicalJSON()).code)
        }
        val resolved=IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED,"fixture",space.modelBinding,"p1","s1")
        assertEquals(resolved.canonicalJSON(),IdentityError.decodeJSON(resolved.canonicalJSON()).canonicalJSON())
    }
}
private data class Clock(override val nowMillis:Long):IdentityWallClock
private data class Mono(override val nowNanos:Long):IdentityMonotonicClock
private fun makeSpace(revision:String, dimension:Int=2)=EmbeddingSpaceId(setOf(IdentityCapability.VERIFICATION,IdentityCapability.IDENTIFICATION),"fixture","1",ModelBinding(ModelBindingKind.MODEL,"fixture",revision),"1",dimension,FeatureDType.FLOAT32,Endianness.LITTLE,"l2","p1","cosine",ScoreDirection.HIGHER_IS_BETTER)
