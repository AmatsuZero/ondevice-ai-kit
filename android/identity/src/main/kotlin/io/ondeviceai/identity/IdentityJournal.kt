package io.ondeviceai.identity

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.UUID

/** Internal one-record encrypted journal. Android production construction supplies a noBackupFilesDir child. */
internal class IdentityJournal(root: Path, private val recordId: String, private val provider: IdentityKeyProvider) : AutoCloseable {
    private val canonicalRoot = root.also { token(recordId) }.toRealPathOrCreate()
    private val dataFile = canonicalRoot.resolve("identity-journal-$recordId.bin")
    private val lockChannel = FileChannel.open(canonicalRoot.resolve(".identity-journal.lock"), CREATE, WRITE)
    private val fileLock: FileLock
    private val context: String
    internal val keyId: String
    @Volatile private var closed = false
    private var hadRecord = false

    init {
        token(recordId)
        try { fileLock = lockChannel.tryLock() ?: throw IdentityError(IdentityErrorCode.BUSY) }
        catch (error: IdentityError) { lockChannel.close(); throw error }
        catch (_: Exception) { lockChannel.close(); throw IdentityError(IdentityErrorCode.BUSY) }
        try {
            context = "journal.${MessageDigest.getInstance("SHA-256").digest(canonicalRoot.toString().encodeToByteArray()).joinToString("") { "%02x".format(it) }}.$recordId"
            keyId = existingKeyId()?.also {
                decode(Files.readAllBytes(dataFile)).open(provider, context)
                hadRecord = true
            } ?: "journal.${UUID.randomUUID()}"
        } catch (error: Exception) {
            try { fileLock.release() } finally { lockChannel.close() }
            throw error
        }
    }

    @Synchronized fun read(): ByteArray? {
        admitted()
        try {
            if (!Files.exists(dataFile)) {
                if (hadRecord) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
                return null
            }
            return decode(Files.readAllBytes(dataFile)).open(provider, context)
        } catch (error: Exception) { poison(); throw error }
    }
    @Synchronized fun commit(plaintext: ByteArray) {
        admitted()
        try {
            if (hadRecord && !Files.exists(dataFile)) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
            persist(encode(IdentityEnvelope.seal(plaintext, keyId, 0, provider, context, !hadRecord)))
            hadRecord = true
        }
        catch (error: Exception) { poison(); throw error }
    }
    @Synchronized override fun close() {
        if (closed) return; closed = true
        try { fileLock.release() } finally { lockChannel.close() }
    }
    private fun admitted() { if (closed) throw IdentityError(IdentityErrorCode.CLOSED) }
    private fun poison() { if (!closed) close() }

    private fun persist(bytes: ByteArray) {
        val temporary = canonicalRoot.resolve(".identity-journal-$recordId-${UUID.randomUUID()}.tmp")
        try {
            FileChannel.open(temporary, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try { Files.move(temporary, dataFile, ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
            FileChannel.open(canonicalRoot, READ).use { it.force(true) }
        } catch (error: Exception) { Files.deleteIfExists(temporary); throw error }
    }
    private fun existingKeyId(): String? {
        if (!Files.exists(dataFile)) return null
        val data = Files.readAllBytes(dataFile)
        if (data.size < 7 || !data.copyOfRange(0, 4).contentEquals(MAGIC) || data[4].toInt() != 1) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val size = ByteBuffer.wrap(data, 5, 2).short.toInt() and 0xffff
        if (data.size < 7 + size) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return data.copyOfRange(7, 7 + size).decodeToString().also { token(it) }
    }
    private fun encode(envelope: IdentityEnvelope): ByteArray {
        val id = envelope.keyId.encodeToByteArray(); return ByteBuffer.allocate(4 + 1 + 2 + id.size + 8 + 12 + 16 + envelope.ciphertext.size)
            .put(MAGIC).put(1).putShort(id.size.toShort()).put(id).putLong(envelope.rotationEpoch).put(envelope.nonce).put(envelope.tag).put(envelope.ciphertext).array()
    }
    private fun decode(data: ByteArray): IdentityEnvelope {
        if (data.size < 43 || !data.copyOfRange(0, 4).contentEquals(MAGIC) || data[4].toInt() != 1) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val idSize = ByteBuffer.wrap(data, 5, 2).short.toInt() and 0xffff; val idStart = 7; val nonceStart = idStart + idSize
        if (data.size < nonceStart + 36) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return IdentityEnvelope(1, data.copyOfRange(idStart, nonceStart).decodeToString(), ByteBuffer.wrap(data, nonceStart, 8).long, data.copyOfRange(nonceStart + 8, nonceStart + 20), data.copyOfRange(nonceStart + 36, data.size), data.copyOfRange(nonceStart + 20, nonceStart + 36))
    }
    private fun Path.toRealPathOrCreate(): Path { Files.createDirectories(this); return toRealPath() }
    private companion object { val MAGIC = byteArrayOf(0x4f, 0x44, 0x4a, 0x31) }
}
