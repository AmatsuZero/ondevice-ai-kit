package io.ondeviceai.identity

import kotlin.test.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

class IdentityCryptoTest {
    @Test fun platformProviderRejectsInvalidNamespaceAndKeyBeforeNativeAccess() {
        assertFailsWith<IdentityError> { AndroidKeystoreIdentityKeyProvider("bad|namespace") }
        val provider = AndroidKeystoreIdentityKeyProvider("fixture-test")
        assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { provider.key("bad|key", true) }.code)
        assertFailsWith<IdentityError> { provider.deleteKey("bad|key") }
    }
    @Test fun envelopeSnapshotsBytesAndRejectsForgedKeyContextTokens() {
        val provider = EphemeralKeys(); val a = IdentityEnvelope.seal(byteArrayOf(1, 2), "k1", 0, provider, "store.record")
        val nonce = a.nonce
        val envelope = IdentityEnvelope(1, a.keyId, 0, nonce, a.ciphertext, a.tag)
        nonce[0] = (nonce[0].toInt() xor 1).toByte()
        val publicCiphertext = envelope.ciphertext
        publicCiphertext[0] = (publicCiphertext[0].toInt() xor 1).toByte()
        assertContentEquals(byteArrayOf(1, 2), envelope.open(provider, "store.record"))
        val malformed = IdentityEnvelope(1, "bad|key", 0, a.nonce, a.ciphertext, a.tag)
        assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { malformed.open(provider, "store.record") }.code)
    }
    @Test fun aes256EnvelopeAuthenticatesPayloadContextAndRandomNonce() {
        val provider = EphemeralKeys(); val plaintext = "synthetic-template".encodeToByteArray()
        val a = IdentityEnvelope.seal(plaintext, "template-key", 3, provider, "store.record.r1")
        val b = IdentityEnvelope.seal(plaintext, "template-key", 3, provider, "store.record.r1")
        assertContentEquals(plaintext, a.open(provider, "store.record.r1"))
        assertEquals(12, a.nonce.size); assertEquals(16, a.tag.size)
        assertFalse(a.nonce.contentEquals(b.nonce)); assertFalse(a.ciphertext.contentEquals(plaintext))
        assertFailsWith<IdentityError> { a.open(provider, "other.record.r1") }
        assertFailsWith<IdentityError> { a.open(EphemeralKeys(), "store.record.r1") }
    }
    @Test fun tamperedCiphertextAndAuthenticatedHeaderAreRejected() {
        val provider = EphemeralKeys(); val a = IdentityEnvelope.seal(byteArrayOf(1, 2, 3), "k1", 3, provider, "store.record")
        val corrupt = a.ciphertext; corrupt[0] = (corrupt[0].toInt() xor 1).toByte()
        for (envelope in listOf(
            IdentityEnvelope(1, a.keyId, 3, a.nonce, corrupt, a.tag),
            IdentityEnvelope(1, a.keyId, 4, a.nonce, a.ciphertext, a.tag),
            IdentityEnvelope(1, a.keyId, 3, byteArrayOf(), a.ciphertext, a.tag)
        )) assertFailsWith<IdentityError> { envelope.open(provider, "store.record") }
    }
    @Test fun missingKeyNeverCreatesAReplacementAndDeletedKeyCannotDecrypt() {
        val provider = EphemeralKeys(); val a = IdentityEnvelope.seal(byteArrayOf(1), "k1", 0, provider, "store.record")
        provider.deleteKey("k1")
        assertEquals(IdentityErrorCode.RE_ENROLLMENT_REQUIRED, assertFailsWith<IdentityError> { a.open(provider, "store.record") }.code)
        assertTrue(provider.isEmpty)
    }
    @Test fun invalidKeySizeAndEnvelopeVersionFailClosed() {
        assertFailsWith<IdentityError> { IdentityEnvelope.seal(byteArrayOf(1), "k1", 0, EphemeralKeys(16), "store.record") }
        val invalid = IdentityEnvelope(2, "k1", 0, ByteArray(12), byteArrayOf(1), ByteArray(16))
        assertFailsWith<IdentityError> { invalid.open(EphemeralKeys(), "store.record") }
    }
}
private class EphemeralKeys(private val size: Int = 32): IdentityKeyProvider {
    private val keys = mutableMapOf<String, SecretKey>()
    val isEmpty: Boolean get() = keys.isEmpty()
    @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey {
        keys[id]?.let { return it }
        if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        return SecretKeySpec(ByteArray(size).also { SecureRandom().nextBytes(it) }, "AES").also { keys[id] = it }
    }
    @Synchronized override fun deleteKey(id: String) { keys.remove(id) }
}
