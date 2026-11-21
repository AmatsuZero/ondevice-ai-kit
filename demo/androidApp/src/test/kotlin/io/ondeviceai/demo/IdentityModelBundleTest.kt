package io.ondeviceai.demo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import org.junit.Test

class IdentityModelBundleTest {
    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { archive -> files.forEach { (name, bytes) -> archive.putNextEntry(ZipEntry(name)); archive.write(bytes); archive.closeEntry() } }
    }.toByteArray()
    @Test fun importsExactFilesWithoutAcceptingAdditionalPayloads() {
        val root = Files.createTempDirectory("identity-bundle").toFile()
        try {
            IdentityModelBundle.unpack(ByteArrayInputStream(zip("a.onnx" to byteArrayOf(1, 2))), root, mapOf("a.onnx" to 2L)) {}
            assertContentEquals(byteArrayOf(1, 2), root.resolve("a.onnx").readBytes())
            assertFailsWith<IllegalArgumentException> {
                IdentityModelBundle.unpack(ByteArrayInputStream(zip("../outside" to byteArrayOf(1))), root, mapOf("a.onnx" to 2L)) {}
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsMissingTruncatedOversizedAndUnexpectedFiles() {
        for (contents in listOf(zip(), zip("a.onnx" to byteArrayOf(1)), zip("a.onnx" to byteArrayOf(1, 2, 3)), zip("unexpected.onnx" to byteArrayOf(1, 2)))) {
            val root = Files.createTempDirectory("identity-invalid-bundle").toFile()
            try { assertFailsWith<IllegalArgumentException> { IdentityModelBundle.unpack(ByteArrayInputStream(contents), root, mapOf("a.onnx" to 2L)) {} } }
            finally { root.deleteRecursively() }
        }
    }
}
