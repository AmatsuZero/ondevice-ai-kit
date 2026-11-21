package io.ondeviceai.demo

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

/** Flat ZIP: manifest first, descriptor and exactly its hash-bound payload files. */
internal object LiteRtLmModelBundle {
    private data class Payload(val bytes: Long, val sha256: String)

    fun unpack(input: InputStream, destination: File, checkpoint: () -> Unit) {
        ZipInputStream(input).use { zip ->
            require(zip.nextEntry?.name == "manifest.json") { "manifest must be first" }
            val manifest = readSmall(zip, 1_048_576, checkpoint)
            val entries = JSONObject(manifest.toString(Charsets.UTF_8)).getJSONArray("files")
            val payload = mutableMapOf<String, Payload>()
            var total = 0L
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val path = entry.getString("path")
                require(safePath(path))
                val size = entry.getLong("bytes")
                val hash = entry.getString("sha256")
                require(size >= 0 && hash.matches(Regex("[0-9a-f]{64}")))
                require(payload.put("files/$path", Payload(size, hash)) == null)
                total = Math.addExact(total, size)
            }
            require(payload.isNotEmpty() && destination.usableSpace >= Math.multiplyExact(total, 2))
            File(destination, "manifest.json").writeBytes(manifest)
            val seen = mutableSetOf<String>()
            var descriptor = false
            while (true) {
                checkpoint()
                val entry = zip.nextEntry ?: break
                val path = entry.name
                if (entry.isDirectory) {
                    require(path == "files/" || path.startsWith("files/") && safePath(path.removeSuffix("/")))
                    continue
                }
                require(safePath(path) && seen.add(path))
                if (path == "demo-model.json") {
                    File(destination, path).writeBytes(readSmall(zip, 65_536, checkpoint))
                    descriptor = true
                    continue
                }
                val expected = requireNotNull(payload[path])
                val file = File(destination, path)
                require(file.parentFile.mkdirs() || file.parentFile.isDirectory)
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                file.outputStream().use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) {
                        checkpoint()
                        val count = zip.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= expected.bytes)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
                require(copied == expected.bytes && digest.digest().joinToString("") { "%02x".format(it) } == expected.sha256)
            }
            require(descriptor && seen == payload.keys + "demo-model.json")
        }
    }

    private fun safePath(path: String) = path.isNotBlank() && !path.startsWith("/") &&
        '\\' !in path && '\u0000' !in path && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

    private fun readSmall(input: InputStream, limit: Int, checkpoint: () -> Unit): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            checkpoint()
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
