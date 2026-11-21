package io.ondeviceai.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExactCosineIdentityIndexTest {
    private val space = FixtureIdentityBackend.system().embeddingSpace
    private fun embedding(vararg values: Float) = IdentityEmbedding(space, values.toList())
    private val signal get() = IdentityStopSignal()

    @Test fun exactScoresUseStoredFloatVectorsInOrder() {
        val retained = listOf(embedding(1f, 0f), embedding(0.8f, 0.6f), embedding(-1f, 0f))
        val bytes = ExactCosineIdentityIndex.build(retained, signal)
        val scores = ExactCosineIdentityIndex.scores(bytes, embedding(1f, 0f), retained, signal)
        assertEquals(1.0, scores[0], 0.000001)
        assertEquals(0.8, scores[1], 0.000001)
        assertEquals(-1.0, scores[2], 0.000001)
    }

    @Test fun corruptTruncatedAndWrongSnapshotPayloadsAreRejected() {
        val retained = listOf(embedding(1f, 0f), embedding(0f, 1f))
        val bytes = ExactCosineIdentityIndex.build(retained, signal)
        for (bad in listOf(bytes.copyOf(bytes.size - 1), bytes + 0, bytes.clone().apply { this[lastIndex] = 0 })) {
            assertEquals(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED, assertFailsWith<IdentityError> {
                ExactCosineIdentityIndex.scores(bad, retained[0], retained, signal)
            }.code)
        }
        assertEquals(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED, assertFailsWith<IdentityError> {
            ExactCosineIdentityIndex.scores(bytes, retained[0], retained.reversed(), signal)
        }.code)
    }

    @Test fun sameDimensionsDoNotPermitAnotherEmbeddingSpace() {
        val retained = listOf(embedding(1f, 0f))
        val bytes = ExactCosineIdentityIndex.build(retained, signal)
        val other = IdentityEmbedding(FixtureIdentityBackend.system("other-revision").embeddingSpace, listOf(1f, 0f))
        assertEquals(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, assertFailsWith<IdentityError> {
            ExactCosineIdentityIndex.scores(bytes, other, retained, signal)
        }.code)
        assertEquals(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED, assertFailsWith<IdentityError> {
            ExactCosineIdentityIndex.scores(bytes, other, listOf(other), signal)
        }.code)
    }

    @Test fun emptyAndZeroNormMatricesAreRejected() {
        for (vectors in listOf(emptyList(), listOf(embedding(0f, 0f)))) {
            assertFailsWith<IdentityError> { ExactCosineIdentityIndex.build(vectors, signal) }
        }
    }

    @Test fun finiteFloatExtremesKeepValidDoublePrecisionCosine() {
        for (value in listOf(Float.MAX_VALUE, Float.MIN_VALUE)) {
            val retained = listOf(embedding(value, value))
            val bytes = ExactCosineIdentityIndex.build(retained, signal)
            assertEquals(1.0, ExactCosineIdentityIndex.scores(bytes, retained[0], retained, signal).single(), 0.000001)
        }
    }

    @Test fun cancellationStopsBuildAndQuery() {
        val vectors = listOf(embedding(1f, 0f))
        val bytes = ExactCosineIdentityIndex.build(vectors, signal)
        val stopped = signal.apply { requestStop(IdentityStopReason.CANCELLED) }
        assertEquals(IdentityErrorCode.CANCELLED, assertFailsWith<IdentityError> {
            ExactCosineIdentityIndex.build(vectors, stopped)
        }.code)
        assertEquals(IdentityErrorCode.CANCELLED, assertFailsWith<IdentityError> {
            ExactCosineIdentityIndex.scores(bytes, vectors[0], vectors, stopped)
        }.code)
    }
}
