package io.ondeviceai.identity

import java.security.GeneralSecurityException
import android.security.keystore.KeyInfo
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKeyFactory

interface IdentityKeyProvider {
    fun key(id: String, createIfMissing: Boolean): SecretKey
    fun deleteKey(id: String)
}

class IdentityEnvelope(val formatVersion: Int, val keyId: String, val rotationEpoch: Long, nonce: ByteArray, ciphertext: ByteArray, tag: ByteArray) {
    private val nonceBytes = nonce.clone()
    private val ciphertextBytes = ciphertext.clone()
    private val tagBytes = tag.clone()
    val nonce: ByteArray get() = nonceBytes.clone()
    val ciphertext: ByteArray get() = ciphertextBytes.clone()
    val tag: ByteArray get() = tagBytes.clone()
    companion object {
        fun seal(plaintext: ByteArray, keyId: String, rotationEpoch: Long, keyProvider: IdentityKeyProvider, context: String): IdentityEnvelope {
            token(keyId); token(context); if (rotationEpoch < 0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            try {
                val key = keyProvider.key(keyId, true)
                validateAES256(key)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                cipher.updateAAD(header(1, keyId, rotationEpoch, context))
                val result = cipher.doFinal(plaintext)
                return IdentityEnvelope(1, keyId, rotationEpoch, cipher.iv, result.copyOfRange(0, result.size - 16), result.copyOfRange(result.size - 16, result.size))
            } catch (error: IdentityError) { throw error }
            catch (_: GeneralSecurityException) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
        }
        private fun header(version: Int, keyId: String, epoch: Long, context: String) = "odai.identity.v1|$version|$keyId|$epoch|$context".encodeToByteArray()
        private fun validateAES256(key: SecretKey) {
            if (key.algorithm != "AES") throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val bits = key.encoded?.size?.times(8) ?: (SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo).keySize
            if (bits != 256) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        }
    }
    fun open(keyProvider: IdentityKeyProvider, context: String): ByteArray {
        token(keyId); token(context)
        if (formatVersion != 1 || nonceBytes.size != 12 || tagBytes.size != 16 || rotationEpoch < 0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val key = try { keyProvider.key(keyId, false) } catch (error: IdentityError) { throw error } catch (_: Exception) { throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) }
        return try {
            validateAES256(key)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonceBytes))
            cipher.updateAAD(header(1, keyId, rotationEpoch, context))
            cipher.doFinal(ciphertextBytes + tagBytes)
        } catch (_: GeneralSecurityException) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
    }
}
