package io.ondeviceai.identity

import java.nio.file.Files
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import kotlin.test.*

class IdentityJournalTest {
    @Test fun copiedJournalIsRejectedAndFailedOpenReleasesWriter() {
        val keys = JournalKeys(); val firstRoot = Files.createTempDirectory("identity-journal"); val secondRoot = Files.createTempDirectory("identity-journal")
        val first = IdentityJournal(firstRoot, "store", keys); first.commit(byteArrayOf(8)); first.close()
        val secondFile = secondRoot.resolve("identity-journal-store.bin")
        Files.copy(firstRoot.resolve("identity-journal-store.bin"), secondFile)
        assertFailsWith<IdentityError> { IdentityJournal(secondRoot, "store", keys) }
        Files.delete(secondFile)
        IdentityJournal(secondRoot, "store", keys).use { assertNotEquals(first.keyId, it.keyId) }
    }

    @Test fun failedCommitRetiresWriterAndPreservesCommittedRecord() {
        val root = Files.createTempDirectory("identity-journal"); val keys = JournalKeys()
        val provider = FailingJournalKeys(keys)
        val writer = IdentityJournal(root, "store", provider); writer.commit(byteArrayOf(1)); provider.fail = true
        assertFailsWith<IdentityError> { writer.commit(byteArrayOf(2)) }
        assertFailsWith<IdentityError> { writer.read() }
        IdentityJournal(root, "store", keys).use { reopened ->
            writer.close()
            assertFailsWith<IdentityError> { IdentityJournal(root, "other", keys) }
            assertContentEquals(byteArrayOf(1), reopened.read())
        }
    }
    @Test fun unauthenticatedExistingRecordCannotBeOverwrittenWithoutReading() {
        val root = Files.createTempDirectory("identity-journal"); val keys = JournalKeys()
        IdentityJournal(root, "store", keys).use { it.commit(byteArrayOf(1)) }
        val file = root.resolve("identity-journal-store.bin")
        val corrupt = byteArrayOf(0x4f, 0x44, 0x4a, 0x31, 2)
        Files.write(file, corrupt)
        assertFailsWith<IdentityError> { IdentityJournal(root, "store", keys) }
        assertContentEquals(corrupt, Files.readAllBytes(file))
        Files.delete(file)
        IdentityJournal(root, "store", keys).close()
    }

    @Test fun missingLiveKeyAndDeletedRecordCannotBeRecreatedByCommit() {
        val root = Files.createTempDirectory("identity-journal"); val keys = JournalKeys()
        val writer = IdentityJournal(root, "store", keys)
        writer.commit(byteArrayOf(1)); keys.deleteKey(writer.keyId)
        try {
            assertEquals(IdentityErrorCode.RE_ENROLLMENT_REQUIRED, assertFailsWith<IdentityError> { writer.commit(byteArrayOf(2)) }.code)
            assertFailsWith<IdentityError> { writer.commit(byteArrayOf(3)) }
        } finally { writer.close() }
        val secondRoot = Files.createTempDirectory("identity-journal")
        IdentityJournal(secondRoot, "store", keys).use { second ->
            second.commit(byteArrayOf(1)); Files.delete(secondRoot.resolve("identity-journal-store.bin"))
            assertFailsWith<IdentityError> { second.commit(byteArrayOf(2)) }
        }
    }

    @Test fun malformedLengthReleasesWriterAndRecordIdentifiersCannotEscapeRoot() {
        val root = Files.createTempDirectory("identity-journal"); val keys = JournalKeys()
        val file = root.resolve("identity-journal-store.bin")
        Files.write(file, byteArrayOf(0x4f, 0x44, 0x4a, 0x31, 1, -1, -1))
        assertFailsWith<IdentityError> { IdentityJournal(root, "store", keys) }
        Files.delete(file)
        assertFailsWith<IdentityError> { IdentityJournal(root, "../outside", keys) }
        IdentityJournal(root, "store", keys).close()
    }
    @Test fun writerExclusionCloseReopenAndRoundTrip() {
        val root = Files.createTempDirectory("identity-journal"); val keys = JournalKeys()
        val journal = IdentityJournal(root, "store", keys)
        assertNull(journal.read()); assertFails { IdentityJournal(root, "store", keys) }
        journal.commit("one".encodeToByteArray()); journal.close(); journal.close(); assertFails { journal.read() }
        val reopened = IdentityJournal(root, "store", keys); assertContentEquals("one".encodeToByteArray(), reopened.read()); reopened.commit("two".encodeToByteArray()); reopened.close()
        val final = IdentityJournal(root, "store", keys); assertContentEquals("two".encodeToByteArray(), final.read()); final.close()
    }
    @Test fun ciphertextTamperAndMissingKeyFailClosed() {
        val root = Files.createTempDirectory("identity-journal"); val keys = JournalKeys(); val journal = IdentityJournal(root, "store", keys)
        journal.commit("secret-record".encodeToByteArray()); journal.close(); val file = root.resolve("identity-journal-store.bin")
        assertFalse(Files.readAllBytes(file).decodeToString().contains("secret-record")); val corrupt = Files.readAllBytes(file); corrupt[corrupt.lastIndex] = (corrupt.last().toInt() xor 1).toByte(); Files.write(file, corrupt)
        assertFailsWith<IdentityError> { IdentityJournal(root, "store", keys) }
        Files.delete(file); val fresh = IdentityJournal(root, "store", keys); fresh.commit(byteArrayOf(1)); val id = fresh.keyId; fresh.close(); keys.deleteKey(id)
        assertEquals(IdentityErrorCode.RE_ENROLLMENT_REQUIRED, assertFailsWith<IdentityError> { IdentityJournal(root, "store", keys) }.code)
    }
}
private class FailingJournalKeys(private val base: IdentityKeyProvider) : IdentityKeyProvider {
    var fail = false
    override fun key(id: String, createIfMissing: Boolean): SecretKey {
        if (fail) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        return base.key(id, createIfMissing)
    }
    override fun deleteKey(id: String) = base.deleteKey(id)
}
private class JournalKeys: IdentityKeyProvider { private val values = mutableMapOf<String, SecretKey>(); @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey { values[id]?.let { return it }; if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED); return SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES").also { values[id] = it } }; @Synchronized override fun deleteKey(id: String) { values.remove(id) } }
