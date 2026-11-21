package io.ondeviceai.litertsr

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Exact publicly licensed TensorFlow Android ESRGAN example, not arbitrary model execution. */
object LiteRtSrModel {
    const val SHA256 = "1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b"
    const val BYTES = 4_993_712L
    fun valid(file: File): Boolean {
        if (!file.isFile || file.length() != BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == SHA256
    }
    fun install(source: File, destination: File) {
        require(valid(source)) { "Expected the official ESRGAN.tflite model" }
        check(destination.parentFile?.isDirectory == true || destination.parentFile?.mkdirs() == true)
        val staging = File(destination.parentFile, ".model-${UUID.randomUUID()}")
        try {
            source.copyTo(staging)
            check(valid(staging))
            Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { staging.delete() }
    }
}
