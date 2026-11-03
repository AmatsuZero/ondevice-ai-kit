package io.ondeviceai.demo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LiteRtLmModelBundleTest {
    private val payload = "model".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private val manifest = """{"files":[{"path":"model.litertlm","bytes":5,"sha256":"$hash"}]}""".toByteArray()

    private fun archive(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()

    @Test
    fun extractsOnlyHashBoundPayloadAndModelDescriptor() {
        val root = Files.createTempDirectory("model-bundle-valid").toFile()
        try {
            val bytes = archive(listOf("manifest.json" to manifest,
                "demo-model.json" to "{}".toByteArray(), "files/model.litertlm" to payload))
            LiteRtLmModelBundle.unpack(ByteArrayInputStream(bytes), root) {}
            assertEquals("model", root.resolve("files/model.litertlm").readText())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun rejectsTraversalAndPayloadThatDoesNotMatchManifestHash() {
        val root = Files.createTempDirectory("model-bundle-rejected").toFile()
        try {
            for ((index, entry) in listOf("../outside" to payload,
                "files/model.litertlm" to "wrong".toByteArray()).withIndex()) {
                val (path, bytes) = entry
                val zip = archive(listOf("manifest.json" to manifest,
                    "demo-model.json" to "{}".toByteArray(), path to bytes))
                assertFailsWith<IllegalArgumentException> {
                    LiteRtLmModelBundle.unpack(ByteArrayInputStream(zip), root.resolve("next-$index").apply { mkdirs() }) {}
                }
            }
        } finally { root.deleteRecursively() }
    }
}
