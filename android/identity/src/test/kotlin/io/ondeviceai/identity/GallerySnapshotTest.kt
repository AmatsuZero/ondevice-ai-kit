package io.ondeviceai.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import java.nio.file.Files
import java.nio.file.Path

class GallerySnapshotTest {
    @Test fun canonicalRestorePinsSortedMembersParentEpochsAndDigest() {
        val f = SnapshotFixture(); val record = f.record(f.members.reversed())
        assertEquals(record.canonicalJSON, f.record().canonicalJSON)
        assertEquals(record.canonicalJSON, GallerySnapshotRecord.restore(record.canonicalJSON).canonicalJSON)
        assertEquals(listOf("template-a", "template-b"), record.members.map { it.ref.id })
        assertEquals(mapOf("operation-domain" to 0L, "member-a" to 0L, "member-b" to 0L), record.domainEpochs)
        val child = f.record(parent = record.ref)
        assertEquals(record.ref, child.parent); assertNotEquals(record.ref.revision, child.ref.revision)
    }

    @Test fun restoreRejectsDigestEpochUnknownAndDuplicateMemberTampering() {
        val record = SnapshotFixture().record()
        val mutations: List<(MutableMap<String, Any?>) -> Unit> = listOf(
            { it["contentDigest"] = "0".repeat(64) }, { it["domainEpochs"] = mapOf("operation-domain" to 0L) },
            { it["unknown"] = "x" }, { it["members"] = (it["members"] as List<String>) + (it["members"] as List<String>).first() }
        )
        mutations.forEach { mutate ->
            @Suppress("UNCHECKED_CAST") val fields = IdentityContractJSON.parse(record.canonicalJSON).toMutableMap()
            mutate(fields); assertFailsWith<IdentityError> { GallerySnapshotRecord.restore(IdentityContractJSON.encode(fields)) }
        }
    }

    @Test fun constructorFailsClosedForPolicyAndMembershipInconsistency() {
        val f = SnapshotFixture(); val member = f.members.first()
        assertFailsWith<IdentityError> { f.record(emptyList()) }
        assertFailsWith<IdentityError> { f.record(listOf(member, member)) }
        val foreign = member.copy(candidate = CandidateRef("foreign", "other-store"))
        assertFailsWith<IdentityError> { f.record(listOf(foreign)) }
        val wrongPurpose = member.copy(grant = AuthorizationGrant("new", "member-a", setOf(IdentityScope.IDENTIFICATION), "other", 0, 200, 0, "audit"))
        assertFailsWith<IdentityError> { f.record(listOf(wrongPurpose)) }
        val unverified = ThresholdPolicy("u", "1", f.space, f.space.scoreMetric, f.space.scoreDirection, CalibrationStatus.UNVERIFIED, IdentityPolicyKind.IDENTIFICATION)
        assertFailsWith<IdentityError> { f.record(policy = unverified) }
    }

    @Test fun candidateDomainAndGrantIdDeclarationsRemainConsistent() {
        val f = SnapshotFixture(); val first = f.members.first()
        val differentDomain = GallerySnapshotMember(f.members[1].ref, first.candidate, f.members[1].grant)
        assertFailsWith<IdentityError> { f.record(listOf(first, differentDomain)) }
        val conflictingGrant = GallerySnapshotMember(f.members[1].ref, f.members[1].candidate, f.grant(first.grant.grantId, "member-b"))
        assertFailsWith<IdentityError> { f.record(listOf(first, conflictingGrant)) }
        val sameCandidate = GallerySnapshotMember(TemplateRef("other-template", f.space, 0, "r2"), first.candidate, first.grant)
        assertEquals(2, f.record(listOf(first, sameCandidate), policy = f.policy.copyGalleryRange(1..1)).members.size)
    }

    @Test fun sharedRootAndChildFixturesRoundTripAndRebuildExactly() {
        val fixturePath = sequenceOf(Path.of("fixtures/contracts/gallery-snapshots.json"), Path.of("../../fixtures/contracts/gallery-snapshots.json"), Path.of("../../../fixtures/contracts/gallery-snapshots.json")).firstOrNull(Files::exists) ?: error("shared fixture missing")
        val root = IdentityContractJSON.parse(String(Files.readAllBytes(fixturePath), Charsets.UTF_8))
        val records = root["records"] as? List<*> ?: error("missing records")
        assertEquals(2, records.size)
        records.forEach { raw ->
            val entry = raw as? Map<*, *> ?: error("invalid fixture")
            val json = entry["snapshotJSON"] as? String ?: error("missing snapshot")
            val restored = GallerySnapshotRecord.restore(json)
            assertEquals(entry["contentDigest"], restored.contentDigest)
            assertEquals(json, restored.canonicalJSON)
            val rebuilt = GallerySnapshotRecord.create(restored.indexStoreId, restored.identityStoreId, restored.ref.id,
                restored.parent, restored.members, restored.operationGrant, restored.policy, restored.aggregationPolicyId)
            assertEquals(json, rebuilt.canonicalJSON)
        }
    }

    @Test fun negativeAndBoundaryDoubleBitsRoundTripExactly() {
        val f = SnapshotFixture()
        listOf(-0.0, -0.5, Double.MIN_VALUE, Double.MAX_VALUE).forEach { value ->
            val policy = ThresholdPolicy("p${value.toRawBits()}", "1", f.space, f.space.scoreMetric, f.space.scoreDirection,
                CalibrationStatus.VERIFIED, IdentityPolicyKind.IDENTIFICATION, value, 0.8, "fixture", "fixture", 1..32, "one", 0)
            val restored = GallerySnapshotRecord.restore(f.record(policy = policy).canonicalJSON)
            assertEquals(value.toRawBits(), restored.policy.threshold!!.toRawBits())
        }
    }

    @Test fun recordCollectionsAreImmutableAndLargeBodyFailsBeforePublication() {
        val f = SnapshotFixture(); val record = f.record()
        assertFailsWith<UnsupportedOperationException> { (record.members as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (record.domainEpochs as MutableMap)["x"] = 1L }
        val suffix = "x".repeat(220)
        val first = f.members.first()
        val many = (0 until 80).map { i -> GallerySnapshotMember(TemplateRef("template-extra-$i-$suffix", f.space, 0, "r$i"), first.candidate, first.grant) }
        assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { f.record(members = many) }.code)
    }

    @Test fun restoreRejectsUnknownPolicyDirectionWithStableError() {
        val record = SnapshotFixture().record()
        val outer = IdentityContractJSON.parse(record.canonicalJSON).toMutableMap()
        val policy = IdentityContractJSON.parse(outer["policy"] as String).toMutableMap()
        policy["scoreDirection"] = "unknown"
        outer["policy"] = IdentityContractJSON.encode(policy)
        assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> {
            GallerySnapshotRecord.restore(IdentityContractJSON.encode(outer))
        }.code)
    }
}

private fun ThresholdPolicy.copyGalleryRange(range: IntRange) = ThresholdPolicy(id, revision, embeddingSpace, scoreMetric, scoreDirection,
    calibrationStatus, kind, threshold, qualityFloor, operatingPoint, datasetRevision, range, enrollmentRule, effectiveAuthorizationEpoch)

private class SnapshotFixture {
    val space = FixtureIdentityBackend.system().embeddingSpace
    val policy = ThresholdPolicy("identify", "1", space, space.scoreMetric, space.scoreDirection, CalibrationStatus.VERIFIED, IdentityPolicyKind.IDENTIFICATION, 0.5, 0.8, "fixture", "fixture", 1..32, "one", 0)
    val operation = grant("operation", "operation-domain")
    val members = listOf("a", "b").map { key -> GallerySnapshotMember(TemplateRef("template-$key", space, 0, "r1"), CandidateRef("candidate-$key", "identity-store"), grant("grant-$key", "member-$key")) }
    fun grant(id: String, domain: String) = AuthorizationGrant(id, domain, setOf(IdentityScope.IDENTIFICATION), "identity", 0, 200, 0, "audit")
    fun record(members: List<GallerySnapshotMember> = this.members, parent: GalleryRef? = null, policy: ThresholdPolicy = this.policy) =
        GallerySnapshotRecord.create("index-store", "identity-store", "lineage", parent, members, operation, policy, "subject-max-v1")
}
