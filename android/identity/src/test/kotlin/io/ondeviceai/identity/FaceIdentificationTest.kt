package io.ondeviceai.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaceIdentificationTest {
    @Test fun aggregateBeforeTopKPreservesTheSecondDistinctCandidate() {
        val snapshot = snapshot(listOf("t1" to "A", "t2" to "A", "t3" to "B", "t4" to "C"))
        val values = rankIdentificationCandidates(snapshot, mapOf("t1" to 0.9, "t2" to 0.8, "t3" to 0.7, "t4" to 0.6), 2)
        assertEquals(listOf("A", "B"), values.map { it.candidateRef.id })
        assertEquals(listOf(0.9, 0.7), values.map { it.score })
        assertEquals(listOf(1, 2), values.map { it.rank })
    }

    @Test fun subjectMaximumIsIndependentOfTemplateOrder() {
        val value = snapshot(listOf("a-first" to "A", "z-last" to "A", "middle" to "B"))
        val values = rankIdentificationCandidates(value, mapOf("a-first" to 0.5, "z-last" to 0.9, "middle" to 0.8))
        assertEquals(listOf("A", "B"), values.map { it.candidateRef.id })
        assertEquals(listOf(0.9, 0.8), values.map { it.score })
    }

    @Test fun equalScoresUseOpaqueByteOrder() {
        val value = snapshot(listOf("t1" to "a", "t2" to "Z", "t3" to "A"))
        val values = rankIdentificationCandidates(value, mapOf("t1" to 0.9, "t2" to 0.9, "t3" to 0.9))
        assertEquals(listOf("A", "Z", "a"), values.map { it.candidateRef.id })
    }

    @Test fun thresholdIsInclusiveAndUnknownLeaksNoBelowThresholdCandidate() {
        val value = snapshot(listOf("t1" to "A", "t2" to "B"))
        val atThreshold = rankIdentificationCandidates(value, mapOf("t1" to 0.5, "t2" to 0.49))
        assertEquals(listOf("A"), atThreshold.map { it.candidateRef.id })
        assertTrue(rankIdentificationCandidates(value, mapOf("t1" to -0.2, "t2" to 0.49)).isEmpty())
    }

    @Test fun defaultTopKReturnsAtMost32DistinctCandidates() {
        val pairs = (0 until 34).map { "t-$it" to "c%02d".format(it) }
        val values = rankIdentificationCandidates(snapshot(pairs), pairs.associate { it.first to 0.9 })
        assertEquals(32, values.size)
        assertEquals("c00", values.first().candidateRef.id)
        assertEquals("c31", values.last().candidateRef.id)
        assertEquals(32, values.last().rank)
    }

    @Test fun invalidTopKAndIncompleteOrNonfiniteScoresFailClosed() {
        val value = snapshot(listOf("t1" to "A"))
        for (topK in listOf(0, 33)) assertEquals(IdentityErrorCode.INVALID_INPUT,
            assertFailsWith<IdentityError> { rankIdentificationCandidates(value, mapOf("t1" to 0.9), topK) }.code)
        for (scores in listOf(emptyMap(), mapOf("t1" to 0.9, "extra" to 0.9), mapOf("t1" to Double.NaN), mapOf("t1" to Double.POSITIVE_INFINITY), mapOf("t1" to 1.1))) {
            assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { rankIdentificationCandidates(value, scores) }.code)
        }
    }

    @Test fun unknownAggregationPolicyNeverUsesAnImplicitFallback() {
        val value = snapshot(listOf("t1" to "A"), "unsupported-v1")
        assertEquals(IdentityErrorCode.UNSUPPORTED_OPTION,
            assertFailsWith<IdentityError> { rankIdentificationCandidates(value, mapOf("t1" to 0.9)) }.code)
    }

    private fun snapshot(pairs: List<Pair<String, String>>, aggregation: String = "subject-max-v1"): GallerySnapshotRecord {
        val space = FixtureIdentityBackend.system().embeddingSpace
        val grant = AuthorizationGrant("grant", "domain", setOf(IdentityScope.IDENTIFICATION), "identity", 0, null, 0, "fixture")
        val policy = ThresholdPolicy("identify", "1", space, "cosine", ScoreDirection.HIGHER_IS_BETTER,
            CalibrationStatus.VERIFIED, IdentityPolicyKind.IDENTIFICATION, 0.5, 0.8, "fixture", "fixture", 1..64, "fixture-multiple", 0)
        return GallerySnapshotRecord.create("index", "store", "gallery", null,
            pairs.map { (template, candidate) -> GallerySnapshotMember(TemplateRef(template, space, 0, "1"), CandidateRef(candidate, "store"), grant) },
            grant, policy, aggregation)
    }
}
