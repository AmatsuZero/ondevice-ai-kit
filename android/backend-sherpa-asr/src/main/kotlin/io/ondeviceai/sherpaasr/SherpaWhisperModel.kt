package io.ondeviceai.sherpaasr

import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.speech.SpeechBackendUnavailableException
import java.io.File
import java.security.MessageDigest

/** Pinned multilingual Whisper tiny int8 files; the caller owns this directory. */
class SherpaWhisperModel(val directory: File) {
    fun isPresent(): Boolean = files.all { (name, metadata) ->
        directory.resolve(name).let { it.isFile && it.length() == metadata.first }
    }

    /** Call off Main before native loading. Invalid files never reach JNI. */
    fun verify() {
        if (!isPresent()) throw SpeechBackendUnavailableException(UnavailableReason.MISSING_ASSETS)
        for ((name, metadata) in files) {
            val hash = MessageDigest.getInstance("SHA-256")
            directory.resolve(name).inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    hash.update(buffer, 0, size)
                }
            }
            val actual = hash.digest().joinToString("") { "%02x".format(it) }
            if (actual != metadata.second) throw SpeechBackendUnavailableException(UnavailableReason.MISSING_ASSETS)
        }
    }

    companion object {
        val files: Map<String, Pair<Long, String>> = mapOf(
            "silero_vad.onnx" to (643854L to "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"),
            "tiny-encoder.int8.onnx" to (12937772L to "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
            "tiny-decoder.int8.onnx" to (89855401L to "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
            "tiny-tokens.txt" to (816730L to "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
        )
    }
}
