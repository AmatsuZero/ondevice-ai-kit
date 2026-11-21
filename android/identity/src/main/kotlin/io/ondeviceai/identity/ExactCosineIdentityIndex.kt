package io.ondeviceai.identity

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Exact small-gallery cosine index for [IndexStore]'s derived in-memory cache.
 * Contains sensitive vectors: never log, export or persist these bytes as plaintext.
 * This builds no recognition policy; gallery publication still requires calibration.
 */
object ExactCosineIdentityIndex {
    private val magic = "ODAI-COSINE-IDX-1".toByteArray(Charsets.US_ASCII)

    fun build(embeddings: List<IdentityEmbedding>, stopSignal: IdentityStopSignal): ByteArray {
        stopSignal.check()
        val space = validateMatrix(embeddings)
        val identity = space.canonicalJSON().toByteArray(Charsets.UTF_8)
        if (identity.size > 65536) unsupported()
        val data = ByteBuffer.allocate(magic.size + 12 + identity.size + embeddings.size * space.dimension * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        data.put(magic).putInt(identity.size).putInt(embeddings.size).putInt(space.dimension).put(identity)
        embeddings.forEach { embedding ->
            stopSignal.check()
            if (normSquared(embedding.values) == 0.0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            embedding.values.forEachIndexed { i, value ->
                if (i % 256 == 0) stopSignal.check()
                data.putFloat(value)
            }
        }
        stopSignal.check()
        return data.array()
    }

    /** Only the engine may search after acquiring the exact snapshot's template leases. */
    internal fun scores(bytes: ByteArray, probe: IdentityEmbedding, retained: List<IdentityEmbedding>,
        stopSignal: IdentityStopSignal): List<Double> {
        stopSignal.check()
        val space = validateMatrix(retained)
        if (probe.embeddingSpace != space) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        val identity = space.canonicalJSON().toByteArray(Charsets.UTF_8)
        val size = magic.size + 12L + identity.size + retained.size.toLong() * space.dimension * 4
        if (bytes.size.toLong() != size) unsupported()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (!ByteArray(magic.size).also(data::get).contentEquals(magic) ||
            data.int != identity.size || data.int != retained.size || data.int != space.dimension ||
            !ByteArray(identity.size).also(data::get).contentEquals(identity)) unsupported()
        val probeNorm = normSquared(probe.values)
        if (probeNorm == 0.0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val scores = retained.map { embedding ->
            stopSignal.check()
            var dot = 0.0
            var norm = 0.0
            embedding.values.forEachIndexed { i, expected ->
                if (i % 256 == 0) stopSignal.check()
                val value = data.float
                // Bind payload order/content to the currently authorized snapshot.
                if (value.toRawBits() != expected.toRawBits()) unsupported()
                dot += probe.values[i].toDouble() * value
                norm += value.toDouble() * value
            }
            if (norm == 0.0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val score = dot / (sqrt(probeNorm) * sqrt(norm))
            if (!score.isFinite()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            score.coerceIn(-1.0, 1.0)
        }
        stopSignal.check()
        return scores
    }

    private fun validateMatrix(embeddings: List<IdentityEmbedding>): EmbeddingSpaceId {
        val space = embeddings.firstOrNull()?.embeddingSpace ?: unsupported()
        if (embeddings.size > 4096 || space.dimension !in 1..4096 || space.dtype != FeatureDType.FLOAT32 ||
            space.endianness != Endianness.LITTLE || space.scoreMetric != "cosine" ||
            space.scoreDirection != ScoreDirection.HIGHER_IS_BETTER || embeddings.any { it.embeddingSpace != space }) unsupported()
        return space
    }

    private fun normSquared(values: List<Float>) = values.sumOf { it.toDouble() * it }
    private fun unsupported(): Nothing = throw IdentityError(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED)
}
