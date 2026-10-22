package io.ondeviceai.demo

import io.ondeviceai.binding.AndroidMultimodalInputLease
import io.ondeviceai.binding.AndroidMultimodalInputProvider
import java.io.File
import java.io.IOException
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

/** Host-owned private copies. Image decoding and bounded admission belong to the backend. */
class MultimodalInputStore(private val directory: File) : AndroidMultimodalInputProvider {
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()
    private var closed = false

    init {
        // Never adopt an existing directory, including an empty directory or a symlink.
        Files.createDirectory(directory.toPath())
    }

    fun importImage(source: File): String = synchronized(lock) {
        check(!closed) { "Input store is closed" }
        require(Files.isRegularFile(source.toPath(), NOFOLLOW_LINKS)) { "Regular file required" }
        val reference = UUID.randomUUID().toString()
        val destination = directory.resolve(reference)
        var created = false
        try {
            // NOFOLLOW on the opened handle also rejects a final-component symlink swap.
            Files.newByteChannel(source.toPath(), setOf(READ, NOFOLLOW_LINKS)).use { channel ->
                Files.newOutputStream(destination.toPath(), CREATE_NEW, WRITE).use { output ->
                    created = true
                    Channels.newInputStream(channel).use { input -> input.copyTo(output) }
                }
            }
        } catch (error: Exception) {
            if (created) {
                // Keep failed copies in the registry if deletion fails, for close() retry.
                entries[reference] = Entry(destination, selected = false)
                reclaim(reference)
            }
            throw error
        }
        entries[reference] = Entry(destination)
        reference
    }

    override fun acquire(reference: String): AndroidMultimodalInputLease = synchronized(lock) {
        check(!closed) { "Input store is closed" }
        val entry = entries[reference]
        require(entry != null && entry.selected) { "Unknown input reference" }
        entry.leases++
        object : AndroidMultimodalInputLease {
            override val file = entry.file
            private var released = false

            override suspend fun release() = synchronized(lock) {
                if (!released) {
                    released = true
                    entry.leases--
                    reclaim(reference)
                }
            }
        }
    }

    fun remove(reference: String) = synchronized(lock) {
        entries[reference]?.selected = false
        reclaim(reference)
    }

    fun close() = synchronized(lock) {
        closed = true
        entries.values.forEach { it.selected = false }
        entries.keys.toList().forEach(::reclaim)
        removeEmptyDirectory()
    }

    private fun reclaim(reference: String) {
        val entry = entries[reference]
        if (entry != null && !entry.selected && entry.leases == 0) {
            try {
                Files.deleteIfExists(entry.file.toPath())
                entries.remove(reference)
            } catch (_: IOException) {
                // Retain ownership so repeated close/remove can retry cleanup.
            }
        }
        removeEmptyDirectory()
    }

    private fun removeEmptyDirectory() {
        if (closed && entries.isEmpty()) {
            try {
                // Nonrecursive and atomic: unknown files keep their containing directory alive.
                Files.deleteIfExists(directory.toPath())
            } catch (_: IOException) {
                // A later close may retry; never remove unowned contents.
            }
        }
    }

    private class Entry(val file: File, var selected: Boolean = true, var leases: Int = 0)
}
