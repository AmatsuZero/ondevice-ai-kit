package io.ondeviceai.voiceeffects

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Internal fault seams keep lifecycle tests independent of timing/filesystem permissions. */
internal open class VoiceEffectsFiles {
    open fun nextId(): String = UUID.randomUUID().toString()

    fun read(input: File, checkCancelled: () -> Unit): WaveData {
        val path = input.toPath()
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) throw VoiceEffectsError.InvalidInput()
        val initialSize = Files.size(path)
        if (initialSize !in 46L..WaveCodec.MAX_FILE_BYTES.toLong()) throw VoiceEffectsError.InvalidInput()
        val wave = closedResource(openInput(input)) { WaveCodec.read(it, checkCancelled) }
        // Complements the bounded EOF probe; never substitutes length for a read bound.
        if (Files.size(path) != wave.bytes.size.toLong()) throw VoiceEffectsError.InvalidInput()
        return wave
    }

    open fun openInput(file: File): InputStream = Channels.newInputStream(
        FileChannel.open(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
    )

    open fun createTemporary(directory: File): File {
        Files.createDirectories(directory.toPath())
        return Files.createTempFile(directory.toPath(), ".odai-voice-", ".partial").toFile()
    }

    open fun openOutput(file: File): OutputStream = FileOutputStream(file)

    fun writeClosed(file: File, bytes: ByteArray, checkCancelled: () -> Unit) {
        closedResource(openOutput(file)) { output ->
            var offset = 0
            while (offset < bytes.size) {
                checkCancelled()
                val count = minOf(8192, bytes.size - offset)
                output.write(bytes, offset, count)
                offset += count
            }
            output.flush()
            if (output is FileOutputStream) output.fd.sync()
        } // A close failure prevents publication.
    }

    open fun publish(temporary: File, destination: File) {
        // Same directory/filesystem. A hard link exposes the already closed bytes
        // atomically and fails if destination exists (including a symlink).
        // Unsupported/denied hard links fail closed: never fall back to renameTo.
        Files.createLink(destination.toPath(), temporary.toPath())
    }

    open fun delete(file: File) { Files.deleteIfExists(file.toPath()) }
}

/** Like use(), but never attaches raw close errors to a cancellation exception. */
private inline fun <T : Closeable, R> closedResource(resource: T, block: (T) -> R): R {
    var failure: Throwable? = null
    try {
        return block(resource)
    } catch (error: Throwable) {
        val safe = safeFailure(error)
        failure = safe
        throw safe
    } finally {
        try { resource.close() } catch (error: Throwable) {
            val safe = safeFailure(error)
            val original = failure
            if (original == null) throw safe else original.addSuppressed(safe)
        }
    }
}
