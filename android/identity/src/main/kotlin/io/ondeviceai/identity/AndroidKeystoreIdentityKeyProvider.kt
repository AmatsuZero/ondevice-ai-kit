package io.ondeviceai.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Device keys stay in AndroidKeyStore; decryption never creates a replacement alias. */
class AndroidKeystoreIdentityKeyProvider(private val namespace: String): IdentityKeyProvider {
    init { token(namespace) }
    private fun alias(id: String): String { token(id); return "io.ondeviceai.identity|$namespace|$id" }

    @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey {
        val alias = alias(id)
        return try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(alias)) {
                (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
                    ?: throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
            } else {
                if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
                generator.generateKey()
            }
        } catch (error: IdentityError) { throw error }
        catch (_: Exception) { throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE) }
    }
    @Synchronized override fun deleteKey(id: String) {
        val alias = alias(id)
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(alias)
        } catch (_: Exception) { throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE) }
    }
}
