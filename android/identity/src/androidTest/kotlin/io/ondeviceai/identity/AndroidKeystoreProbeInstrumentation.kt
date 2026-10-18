package io.ondeviceai.identity

import android.app.Instrumentation
import android.os.Bundle
import android.security.keystore.KeyInfo
import javax.crypto.SecretKeyFactory

/**
 * Device-only probe for the AndroidKeyStore boundary. It creates one random,
 * namespaced alias and removes only that alias before returning.
 */
class AndroidKeystoreProbeInstrumentation : Instrumentation() {
    private var probeArguments: Bundle? = null

    override fun onCreate(arguments: Bundle?) {
        probeArguments = arguments
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        if (probeArguments?.getString("probe") == "store") {
            AndroidIdentityStoreProbeInstrumentation(this, probeArguments ?: Bundle()).run()
            return
        }
        val test = Bundle().apply {
            putInt("numtests", 1)
            putInt("current", 1)
            putString("id", "AndroidKeystoreProbeInstrumentation")
            putString("class", this@AndroidKeystoreProbeInstrumentation.javaClass.name)
            putString("test", "androidKeystoreEnvelopeLifecycle")
        }
        val result = Bundle()
        var finalCode = -2
        sendStatus(1, test)
        val namespace = "probe-${java.util.UUID.randomUUID()}"
        val keyId = "envelope-key"
        val context = "probe.context"
        val plaintext = "synthetic-keystore-probe".encodeToByteArray()
        val provider = AndroidKeystoreIdentityKeyProvider(namespace)
        try {
            check(probeArguments?.getString("forceFailure") != "true") {
                "forced probe failure"
            }
            val sealed = IdentityEnvelope.seal(plaintext, keyId, 0, provider, context)
            val key = provider.key(keyId, false)
            val keyInfo = SecretKeyFactory.getInstance("AES", "AndroidKeyStore")
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            check(keyInfo.keySize == 256) { "unexpected key size: ${keyInfo.keySize}" }
            check(key.encoded == null) { "AndroidKeyStore key unexpectedly exportable" }
            result.putInt("keyBits", keyInfo.keySize)
            result.putBoolean("keyEncodedNull", key.encoded == null)

            check(sealed.open(provider, context).contentEquals(plaintext)) { "initial open mismatch" }
            val rebuilt = AndroidKeystoreIdentityKeyProvider(namespace)
            check(sealed.open(rebuilt, context).contentEquals(plaintext)) { "recreated provider could not open" }

            val tamperedCiphertext = sealed.ciphertext.clone().also { it[0] = (it[0].toInt() xor 1).toByte() }
            check(runCatching {
                IdentityEnvelope(1, sealed.keyId, sealed.rotationEpoch, sealed.nonce, tamperedCiphertext, sealed.tag)
                    .open(rebuilt, context)
            }.exceptionOrNull()?.let { (it as? IdentityError)?.code == IdentityErrorCode.INVALID_INPUT } == true) {
                "ciphertext tamper was accepted"
            }
            check(runCatching { sealed.open(rebuilt, "$context.tampered") }.exceptionOrNull()
                ?.let { (it as? IdentityError)?.code == IdentityErrorCode.INVALID_INPUT } == true) {
                "AAD tamper was accepted"
            }

            rebuilt.deleteKey(keyId)
            rebuilt.deleteKey(keyId)
            check(runCatching { sealed.open(rebuilt, context) }.exceptionOrNull()
                ?.let { (it as? IdentityError)?.code == IdentityErrorCode.RE_ENROLLMENT_REQUIRED } == true) {
                "deleted key did not require re-enrollment"
            }
            check(runCatching { rebuilt.key(keyId, false) }.exceptionOrNull()
                ?.let { (it as? IdentityError)?.code == IdentityErrorCode.RE_ENROLLMENT_REQUIRED } == true) {
                "missing key was recreated during read"
            }
            result.putString("probeStatus", "pass")
            result.putString("namespace", namespace)
            result.putString("cleanup", "exact namespace/keyId; no alias enumeration")
            finalCode = -1
            sendStatus(0, Bundle(test).apply { putString("stream", "AndroidKeyStore envelope probe passed") })
        } catch (error: Throwable) {
            result.putString("probeStatus", "fail")
            result.putString("probeError", error.toString())
            sendStatus(-2, Bundle(test).apply { putString("stack", error.stackTraceToString()) })
        } finally {
            runCatching { provider.deleteKey(keyId) }
            finish(finalCode, result)
        }
    }
}
