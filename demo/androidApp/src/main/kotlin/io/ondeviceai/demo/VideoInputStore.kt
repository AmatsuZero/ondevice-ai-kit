package io.ondeviceai.demo

import io.ondeviceai.binding.AndroidVideoInputLease
import io.ondeviceai.binding.AndroidVideoInputProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID

/** Private media imports survive selection retirement until the processing lease is released. */
class VideoInputStore(private val root: File, private val maxBytes: Long = 256L * 1024 * 1024) : AndroidVideoInputProvider {
    data class Input(val reference: String, val displayName: String)
    private class Entry(val file: File, var readers: Int = 0, var retired: Boolean = false)
    private val entries = mutableMapOf<String, Entry>()
    private var closed = false
    init { require(maxBytes > 0); check(!root.exists() && root.mkdirs()) { "video input directory must be fresh" } }

    fun import(displayName: String, open: () -> InputStream, checkpoint: () -> Unit = {}): Input {
        synchronized(this) { check(!closed) }
        val reference = UUID.randomUUID().toString()
        val file = root.resolve("$reference.media")
        var published = false
        try {
            var total = 0L
            open().use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "video input exceeds import limit" }
                    output.write(buffer, 0, count)
                }
            } }
            require(total > 0) { "empty video input" }
            checkpoint()
            synchronized(this) {
                check(!closed)
                entries[reference] = Entry(file)
                published = true
            }
            return Input(reference, displayName.substringAfterLast('/').substringAfterLast('\\').filterNot(Char::isISOControl).take(160).ifBlank { "video" })
        } finally {
            if (!published) synchronized(this) {
                val entry = Entry(file, retired = true)
                entries[reference] = entry
                collect(reference, entry)
            }
        }
    }
    @Synchronized override fun acquire(reference: String): AndroidVideoInputLease {
        check(!closed)
        val entry = checkNotNull(entries[reference]?.takeUnless { it.retired }) { "unknown video input" }
        entry.readers++
        return object : AndroidVideoInputLease {
            override val file = entry.file
            private var released = false
            override suspend fun release() = synchronized(this@VideoInputStore) {
                if (!released) { released = true; entry.readers--; collect(reference, entry) }
            }
        }
    }
    @Synchronized fun remove(reference: String) { entries[reference]?.let { it.retired = true; collect(reference, it) } }
    @Synchronized fun close() {
        closed = true
        entries.toMap().forEach { (reference, entry) -> entry.retired = true; collect(reference, entry) }
        removeRootIfClosed()
    }
    private fun collect(reference: String, entry: Entry) {
        if (entry.retired && entry.readers == 0) {
            try {
                Files.deleteIfExists(entry.file.toPath())
                entries.remove(reference)
            } catch (_: IOException) {
                // Retain ownership so a later remove or close can retry.
            }
        }
        removeRootIfClosed()
    }
    private fun removeRootIfClosed() { if (closed && root.listFiles()?.isEmpty() == true) root.delete() }
}
