package io.ondeviceai.identity

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

/** Owns only the encrypted payload file for a manifest-owned template record. */
internal class IdentityTemplatePayload(root: Path, private val storeId: String, private val provider: IdentityKeyProvider) {
    private val canonicalRoot = root.also { token(storeId); Files.createDirectories(it) }.toRealPath()

    fun write(recordId: String, embedding: IdentityEmbedding) {
        validateRecordId(recordId)
        if (embedding.embeddingSpace.dtype != FeatureDType.FLOAT32) throw IdentityError(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED)
        val file = file(recordId)
        if (Files.exists(file, NOFOLLOW_LINKS)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val keyId = keyId(recordId)
        val context = context(recordId)
        val plaintext = encodePayload(embedding)
        val envelope = IdentityEnvelope.seal(plaintext, keyId, 0, provider, context, true)
        val encoded = encodeEnvelope(envelope)
        if (encoded.size > MAXIMUM_BYTES) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        try {
            FileChannel.open(file, CREATE_NEW, WRITE).use { channel ->
                var offset = 0
                while (offset < encoded.size) offset += channel.write(ByteBuffer.wrap(encoded, offset, encoded.size - offset))
                channel.force(true)
            }
            syncDirectory()
        } catch (error: Exception) {
            // The caller's ownership manifest makes a failed write retryable; never sweep unrelated files.
            throw error.toIdentityError()
        }
    }

    fun read(recordId: String, space: EmbeddingSpaceId): IdentityEmbedding {
        validateRecordId(recordId)
        val file = file(recordId)
        if (!Files.exists(file, NOFOLLOW_LINKS)) throw IdentityError(IdentityErrorCode.TEMPLATE_NOT_FOUND)
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val size = Files.size(file)
        if (size > MAXIMUM_BYTES) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val envelope = decodeEnvelope(Files.readAllBytes(file))
        val expectedKey = keyId(recordId)
        if (envelope.keyId != expectedKey) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val payload = envelope.open(provider, context(recordId))
        return decodePayload(payload, space)
    }

    fun remove(recordId: String) {
        validateRecordId(recordId)
        val file = file(recordId)
        if (Files.exists(file, NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            Files.delete(file)
            syncDirectory()
        }
        provider.deleteKey(keyId(recordId))
    }

    private fun file(recordId: String) = canonicalRoot.resolve("identity-template-$recordId.bin")
    private fun keyId(recordId: String) = "template.$storeId.$recordId"
    private fun context(recordId: String) = "template.$storeId.$recordId"

    private fun encodePayload(embedding: IdentityEmbedding): ByteArray {
        val space = embedding.embeddingSpace.canonicalJSON().encodeToByteArray()
        if (space.size > MAX_SPACE_BYTES || embedding.values.size > (MAXIMUM_BYTES - space.size - 1024) / 4)
            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val buffer = ByteBuffer.allocate(4 + 1 + 4 + space.size + 4 + embedding.values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(PAYLOAD_MAGIC).put(1).putInt(space.size).put(space).putInt(embedding.values.size)
        embedding.values.forEach { value -> if (!value.isFinite()) throw IdentityError(IdentityErrorCode.INVALID_INPUT); buffer.putFloat(value) }
        return buffer.array()
    }

    private fun decodePayload(bytes: ByteArray, expectedSpace: EmbeddingSpaceId): IdentityEmbedding {
        if (bytes.size > MAXIMUM_BYTES || bytes.size < 13) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4); buffer.get(magic)
            if (!magic.contentEquals(PAYLOAD_MAGIC) || buffer.get().toInt() != 1) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val spaceSize = buffer.int
            if (spaceSize <= 0 || spaceSize > MAX_SPACE_BYTES || spaceSize > buffer.remaining()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val space = EmbeddingSpaceId.decodeJSON(ByteArray(spaceSize).also(buffer::get).decodeToString())
            if (space.dtype != FeatureDType.FLOAT32 || space != expectedSpace) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
            val dimension = buffer.int
            if (dimension != space.dimension || dimension <= 0 || dimension > buffer.remaining() / 4 || buffer.remaining().toLong() != dimension.toLong() * 4)
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val values = List(dimension) { buffer.float }
            if (values.any { !it.isFinite() }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return IdentityEmbedding(space, values)
        } catch (error: IdentityError) { throw error }
        catch (_: Exception) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
    }

    private fun encodeEnvelope(envelope: IdentityEnvelope): ByteArray {
        val key = envelope.keyId.encodeToByteArray()
        if (key.isEmpty() || key.size > 65535) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return ByteBuffer.allocate(4 + 1 + 2 + key.size + 8 + 12 + 16 + envelope.ciphertext.size).order(ByteOrder.BIG_ENDIAN)
            .put(ENVELOPE_MAGIC).put(1).putShort(key.size.toShort()).put(key).putLong(envelope.rotationEpoch)
            .put(envelope.nonce).put(envelope.tag).put(envelope.ciphertext).array()
    }

    private fun decodeEnvelope(bytes: ByteArray): IdentityEnvelope {
        if (bytes.size < 43 || bytes.size > MAXIMUM_BYTES) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN); val magic = ByteArray(4); buffer.get(magic)
            if (!magic.contentEquals(ENVELOPE_MAGIC) || buffer.get().toInt() != 1) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val keySize = buffer.short.toInt() and 0xffff
            if (keySize <= 0 || keySize > 256 || buffer.remaining() < keySize + 8 + 12 + 16) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val key = ByteArray(keySize).also(buffer::get).decodeToString(); val epoch = buffer.long
            val nonce = ByteArray(12).also(buffer::get); val tag = ByteArray(16).also(buffer::get); val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            if (ciphertext.isEmpty()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return IdentityEnvelope(1, key, epoch, nonce, ciphertext, tag)
        } catch (error: IdentityError) { throw error }
        catch (_: Exception) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
    }

    private fun syncDirectory() { FileChannel.open(canonicalRoot, READ).use { it.force(true) } }

    private fun validateRecordId(recordId: String) {
        if (!Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(recordId) || UUID.fromString(recordId).toString() != recordId)
            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }

    private fun Exception.toIdentityError() = this as? IdentityError ?: IdentityError(IdentityErrorCode.INVALID_INPUT)

    private companion object {
        const val MAXIMUM_BYTES = 16 * 1024 * 1024
        const val MAX_SPACE_BYTES = 64 * 1024
        val ENVELOPE_MAGIC = byteArrayOf(0x4f, 0x44, 0x54, 0x45)
        val PAYLOAD_MAGIC = byteArrayOf(0x4f, 0x44, 0x54, 0x31)
    }
}
