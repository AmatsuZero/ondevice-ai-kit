package io.ondeviceai.acceptance.terminal

import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

class TerminalAckFile(privateRoot: File, runId: String) {

    val file: File

    init {
        if (!runId.matches(Regex("""[A-Za-z0-9-]{1,80}"""))) {
            throw IllegalArgumentException("runId must match [A-Za-z0-9-]{1,80}: '$runId'")
        }
        val rootPath = privateRoot.toPath()
        if (!rootPath.isAbsolute) {
            throw IOException("privateRoot must be absolute: $rootPath")
        }
        var scan: Path? = rootPath
        while (scan != null) {
            if (Files.isSymbolicLink(scan)) {
                throw IOException("privateRoot path contains a symlink component: $scan")
            }
            scan = scan.parent
        }
        val canonicalRoot = try {
            rootPath.toRealPath(LinkOption.NOFOLLOW_LINKS)
        } catch (e: IOException) {
            throw IOException("privateRoot cannot be resolved as a real path: ${e.message}", e)
        }
        if (!Files.isDirectory(canonicalRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("privateRoot must be an existing directory: $canonicalRoot")
        }
        if (rootPath != canonicalRoot) {
            throw IOException("privateRoot must be canonical (expected=$canonicalRoot actual=$rootPath)")
        }
        file = File(privateRoot, "ack-$runId.txt")
    }

    fun requireAbsent() {
        assertAbsent(file.toPath())
        assertAbsent(File(file.path + ".stage").toPath())
    }

    private fun assertAbsent(p: Path) {
        try {
            Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: NoSuchFileException) {
            return
        }
        throw IOException("ack entry already exists: $p")
    }

    fun readOrNull(): ByteArray? {
        val p = file.toPath()
        val initialAttrs: BasicFileAttributes = try {
            Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: NoSuchFileException) {
            return null
        }
        if (initialAttrs.isSymbolicLink) {
            throw IOException("ack final is a symlink: $p")
        }
        if (initialAttrs.isDirectory) {
            throw IOException("ack final is a directory: $p")
        }
        if (!initialAttrs.isRegularFile) {
            throw IOException("ack final is not a regular file: $p")
        }
        val initialSize = initialAttrs.size()
        if (initialSize > 1024) {
            throw IOException("ack final exceeds 1024 bytes (size=$initialSize): $p")
        }
        val channel = try {
            FileChannel.open(p, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        } catch (e: NoSuchFileException) {
            throw IOException("ack final vanished during open: $p", e)
        }
        return channel.use { ch ->
            val openSize = ch.size()
            if (openSize != initialSize) {
                throw IOException("ack final size changed before read (attrs=$initialSize channel=$openSize): $p")
            }
            val buf = ByteBuffer.allocate(1025)
            var total = 0
            while (buf.hasRemaining()) {
                val n = ch.read(buf)
                if (n < 0) break
                if (n == 0) {
                    throw IOException("ack final read made zero progress at $total bytes: $p")
                }
                total += n
            }
            val afterChannelSize = ch.size()
            if (afterChannelSize != initialSize) {
                throw IOException("ack final size changed during/after read (was=$initialSize now=$afterChannelSize): $p")
            }
            val finalAttrs = try {
                Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            } catch (e: IOException) {
                throw IOException("ack final vanished after read: $p", e)
            }
            if (finalAttrs.isSymbolicLink || !finalAttrs.isRegularFile) {
                throw IOException("ack final is not a safe regular file after read: $p")
            }
            if (total.toLong() != initialSize) {
                throw IOException("ack final bytes read ($total) differ from initial size ($initialSize): $p")
            }
            if (finalAttrs.size() != initialSize) {
                throw IOException("ack final size changed after read (was=$initialSize now=${finalAttrs.size()}): $p")
            }
            if (total == 0) {
                throw IOException("ack final is empty: $p")
            }
            if (total > 1024) {
                throw IOException("ack final exceeds 1024 bytes (read=$total): $p")
            }
            buf.flip()
            val out = ByteArray(total)
            buf.get(out)
            out
        }
    }
}