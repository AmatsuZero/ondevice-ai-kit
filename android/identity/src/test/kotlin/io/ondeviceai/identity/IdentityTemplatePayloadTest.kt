package io.ondeviceai.identity

import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

class IdentityTemplatePayloadTest {
    @Test fun acceptedLargeFloat32PayloadRemainsReadable() {
        val root = Files.createTempDirectory("identity-template"); val keys = TemplateKeys(); val small = testSpace()
        val space = EmbeddingSpaceId(small.supportedCapabilities, small.backendId, small.backendVersion, small.modelBinding,
            small.featureFormatVersion, 1_000_001, small.dtype, small.endianness, small.normalization,
            small.preprocessRevision, small.scoreMetric, small.scoreDirection)
        val payload = IdentityTemplatePayload(root, "store", keys); val id = "11111111-1111-1111-1111-111111111111"
        payload.write(id, IdentityEmbedding(space, List(space.dimension) { 0.25f }))
        assertEquals(space.dimension, payload.read(id, space).values.size)
    }

    @Test fun swappedFilesWrongSpaceAndSymlinksFailClosed() {
        val root = Files.createTempDirectory("identity-template"); val keys = TemplateKeys(); val space = testSpace()
        val payload = IdentityTemplatePayload(root, "store", keys)
        val one = "11111111-1111-1111-1111-111111111111"; val two = "22222222-2222-2222-2222-222222222222"
        payload.write(one, IdentityEmbedding(space, listOf(1f, 0f))); payload.write(two, IdentityEmbedding(space, listOf(0f, 1f)))
        val wrongSpace = EmbeddingSpaceId(space.supportedCapabilities, space.backendId, space.backendVersion,
            ModelBinding(ModelBindingKind.MODEL, "other", "1"), space.featureFormatVersion, space.dimension,
            space.dtype, space.endianness, space.normalization, space.preprocessRevision, space.scoreMetric, space.scoreDirection)
        assertEquals(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, assertFailsWith<IdentityError> { payload.read(one, wrongSpace) }.code)
        Files.write(root.resolve("identity-template-$one.bin"), Files.readAllBytes(root.resolve("identity-template-$two.bin")))
        assertFailsWith<IdentityError> { payload.read(one, space) }
        val linkId = "33333333-3333-3333-3333-333333333333"
        Files.createSymbolicLink(root.resolve("identity-template-$linkId.bin"), root.resolve("absent"))
        assertFailsWith<IdentityError> { payload.remove(linkId) }
        assertFailsWith<IdentityError> { payload.read(linkId, space) }
    }

    @Test fun roundTripPersistsAcrossNewInstanceAndUsesIndependentKeys() {
        val root = Files.createTempDirectory("identity-template")
        val keys = TemplateKeys()
        val space = testSpace()
        val first = IdentityTemplatePayload(root, "store", keys)
        first.write("11111111-1111-1111-1111-111111111111", IdentityEmbedding(space, listOf(1f, -2.5f)))
        first.write("22222222-2222-2222-2222-222222222222", IdentityEmbedding(space, listOf(3f, 4f)))
        assertFailsWith<IdentityError> { first.write("11111111-1111-1111-1111-111111111111", IdentityEmbedding(space, listOf(9f, 9f))) }
        assertContentEquals(listOf(1f, -2.5f), IdentityTemplatePayload(root, "store", keys).read("11111111-1111-1111-1111-111111111111", space).values)
        assertContentEquals(listOf(3f, 4f), IdentityTemplatePayload(root, "store", keys).read("22222222-2222-2222-2222-222222222222", space).values)
        assertEquals(2, keys.ids().size)
        assertNotEquals(keys.ids().first(), keys.ids().last())
    }

    @Test fun rejectsTamperMissingKeySwappedContextAndWrongSpace() {
        val root = Files.createTempDirectory("identity-template")
        val keys = TemplateKeys(); val space = testSpace()
        val id = "11111111-1111-1111-1111-111111111111"
        IdentityTemplatePayload(root, "store", keys).write(id, IdentityEmbedding(space, listOf(1f, 2f)))
        val file = root.resolve("identity-template-$id.bin")
        val bytes = Files.readAllBytes(file); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); Files.write(file, bytes)
        assertFailsWith<IdentityError> { IdentityTemplatePayload(root, "store", keys).read(id, space) }
        val other = TemplateKeys(); assertEquals(IdentityErrorCode.RE_ENROLLMENT_REQUIRED, assertFailsWith<IdentityError> { IdentityTemplatePayload(root, "store", other).read(id, space) }.code)
        val clean = IdentityTemplatePayload(root, "store", keys)
        clean.remove(id)
        assertFailsWith<IdentityError> { clean.read(id, space) }
        assertTrue(keys.ids().isEmpty())
    }

    @Test fun rejectsUnsupportedSpaceAndNeverOverwritesOrTouchesUnrelatedFiles() {
        val root = Files.createTempDirectory("identity-template")
        val unrelated = root.resolve("identity-template-not-a-uuid.bin"); Files.write(unrelated, byteArrayOf(7))
        val keys = TemplateKeys(); val good = testSpace(); val id = "11111111-1111-1111-1111-111111111111"
        val bad = EmbeddingSpaceId(good.supportedCapabilities, good.backendId, good.backendVersion, good.modelBinding, good.featureFormatVersion, good.dimension, FeatureDType.FLOAT16, good.endianness, good.normalization, good.preprocessRevision, good.scoreMetric, good.scoreDirection)
        assertEquals(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED, assertFailsWith<IdentityError> { IdentityTemplatePayload(root, "store", keys).write(id, IdentityEmbedding(bad, listOf(1f, 2f))) }.code)
        assertEquals(7, Files.readAllBytes(unrelated).single())
        assertFailsWith<IdentityError> { IdentityTemplatePayload(root, "store", keys).write("not-a-uuid", IdentityEmbedding(good, listOf(1f, 2f))) }
    }

    @Test fun removeIsExactAndIdempotent() {
        val root = Files.createTempDirectory("identity-template"); val keys = TemplateKeys(); val space = testSpace()
        val one = "11111111-1111-1111-1111-111111111111"; val two = "22222222-2222-2222-2222-222222222222"
        val unrelated = root.resolve("keep.bin"); Files.write(unrelated, byteArrayOf(9))
        val payload = IdentityTemplatePayload(root, "store", keys)
        payload.write(one, IdentityEmbedding(space, listOf(1f, 2f))); payload.write(two, IdentityEmbedding(space, listOf(3f, 4f)))
        payload.remove(one); payload.remove(one)
        assertContentEquals(listOf(3f, 4f), payload.read(two, space).values)
        assertContentEquals(byteArrayOf(9), Files.readAllBytes(unrelated))
    }
}

private class TemplateKeys : IdentityKeyProvider {
    private val values = mutableMapOf<String, SecretKey>()
    override fun key(id: String, createIfMissing: Boolean): SecretKey = synchronized(values) {
        values[id]?.let { return@synchronized it }
        if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES").also { values[id] = it }
    }
    override fun deleteKey(id: String) { synchronized(values) { values.remove(id) } }
    fun ids() = synchronized(values) { values.keys.toSet() }
}

private fun testSpace() = EmbeddingSpaceId(setOf(IdentityCapability.VERIFICATION), "fixture", "1", ModelBinding(ModelBindingKind.MODEL, "fixture", "1"), "1", 2, FeatureDType.FLOAT32, Endianness.LITTLE, "l2", "p1", "cosine", ScoreDirection.HIGHER_IS_BETTER)
