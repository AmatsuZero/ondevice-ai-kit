package io.ondeviceai.acceptance.qwenarena

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Binding receipt for one prepared CPU arena-off policy.
 *
 * - [provider] is `cpu:<canonical absolute config path>` and must not be trusted
 *   as a fallback to bare CPU if verification fails.
 * - [configPath] is the canonical absolute path of the config file; verification
 *   re-binds it to `<privateRoot>/cpu-arena-off/cpu.config` rather than trusting
 *   an arbitrary path from the receipt.
 * - [bytes] is the exact on-disk byte count.
 * - [sha256] is the lowercase SHA-256 of the exact on-disk bytes.
 */
data class ArenaPolicyReceipt(
    val provider: String,
    val configPath: String,
    val bytes: Long,
    val sha256: String,
)

/**
 * W3 isolated Qwen CPU arena hypothesis — one pure-Kotlin candidate.
 *
 * Purpose (see context/DESIGN.md): prepare exactly one allocator hypothesis,
 * `EnableCpuMemArena=0`, into a freshly reserved alias-owned private child
 * directory, sync the bytes, and hand back a binding receipt. It does NOT prove
 * native policy consumption, lower RSS, root cause, memory savings, quality, or
 * W3 completion. The consumed default-allocator FILE run is immutable; this is a
 * new diagnostic candidate, not a replay or acceptance of that run.
 *
 * TOCTOU limit (stated, not claimed race-immune): ancestry/symlink rejection and
 * the child-directory reservation are NOT atomic. A symlink planted in the window
 * between the ancestry check and [prepare] reserving the child would not be fully
 * defended against. This module deliberately avoids generic concurrent adversarial
 * filesystem machinery because the alias is exclusive; callers must not rely on
 * this code for race-immune guarantees.
 */
object QwenArenaPolicy {

    private const val CHILD_DIR = "cpu-arena-off"
    private const val CONFIG_NAME = "cpu.config"
    private val POLICY_BYTES: ByteArray = "EnableCpuMemArena=0\n".toByteArray(Charsets.UTF_8)

    class ArenaPolicyException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Reject if any component of the path (the path itself or any ancestor up to
     * the filesystem root) is a symbolic link. Must run on the original/normalized
     * absolute path BEFORE canonicalization, which would otherwise resolve ".."
     * and hide a symlink. [File.absoluteFile] only makes the path absolute (it
     * prepends the working directory for a relative path); it does NOT resolve
     * "."/".." or symlinks, so the original path components — including a symlink
     * later cancelled by ".." — remain inspectable here.
     */
    private fun assertNoSymlinkAncestry(file: File) {
        var p: File? = file.absoluteFile
        while (p != null) {
            if (Files.isSymbolicLink(p.toPath())) {
                throw ArenaPolicyException("symlink in path ancestry: ${p.path}")
            }
            p = p.parentFile
        }
    }

    /**
     * Read at most [POLICY_BYTES].size + 1 bytes from [path] through a fixed small
     * buffer, so an oversized or sparse file cannot force an input-sized allocation
     * (the failure this module corrects). A plain length precheck is insufficient
     * because the file could grow between the probe and the read; instead the read
     * itself is bounded. Enforces exact length and content, and rejects a wrong
     * length without ever allocating an array sized to the (possibly huge) input.
     * On-disk evidence is preserved — this only throws.
     */
    private fun readBoundedPolicyBytes(path: Path): ByteArray {
        val limit = POLICY_BYTES.size + 1
        val buffer = ByteArray(limit)
        var total = 0
        FileChannel.open(path, StandardOpenOption.READ).use { ch ->
            val bb = ByteBuffer.wrap(buffer)
            while (bb.hasRemaining()) {
                val n = ch.read(bb)
                if (n < 0) break // EOF reached
                total += n
            }
        }
        if (total != POLICY_BYTES.size) {
            throw ArenaPolicyException(
                "policy length unexpected: expected=${POLICY_BYTES.size} actual=$total at $path"
            )
        }
        for (i in POLICY_BYTES.indices) {
            if (buffer[i] != POLICY_BYTES[i]) {
                throw ArenaPolicyException("policy bytes changed: $path")
            }
        }
        return buffer.copyOf(total)
    }

    fun prepare(privateRoot: File): ArenaPolicyReceipt {
        // 1. No symlink ancestry/root may redirect the writes.
        assertNoSymlinkAncestry(privateRoot)

        // 2. The root must already exist as a regular directory. A missing root is
        //    never created; a regular file root is rejected without modification.
        if (!privateRoot.exists() || !privateRoot.isDirectory) {
            throw ArenaPolicyException("privateRoot is not an existing directory: ${privateRoot.path}")
        }

        // 3. Reserve a NEW child directory exclusively. Any pre-existing child
        //    (real directory, file, or symlink — even with matching bytes) causes
        //    failure without overwrite. mkdir() returns false on EEXIST.
        //    TOCTOU: the existence probe and create are not atomic (see class note).
        val child = File(privateRoot, CHILD_DIR)
        if (!child.mkdir()) {
            throw ArenaPolicyException("child directory already exists or cannot be reserved: ${child.path}")
        }

        // 4. Write the config file exclusively (CREATE_NEW fails if it exists),
        //    flush, force data + metadata to stable storage, then close.
        val config = File(child, CONFIG_NAME)
        try {
            FileChannel.open(
                config.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            ).use { ch ->
                ch.write(ByteBuffer.wrap(POLICY_BYTES))
                ch.force(true) // fsync-equivalent: data + metadata to disk
            }
        } catch (e: Exception) {
            // On partial failure preserve the reserved child/evidence; do not adopt,
            // retry, or delete unrelated data.
            throw ArenaPolicyException("failed to write policy config: ${config.path}", e)
        }

        // 5. Re-read the exact bytes (bounded), then form the canonical receipt.
        val reread = readBoundedPolicyBytes(config.toPath())

        val canonical = config.canonicalFile
        val configPath = canonical.path
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(reread)
            .joinToString("") { "%02x".format(it) }

        return ArenaPolicyReceipt(
            provider = "cpu:$configPath",
            configPath = configPath,
            bytes = reread.size.toLong(),
            sha256 = sha256,
        )
    }

    fun verify(privateRoot: File, receipt: ArenaPolicyReceipt) {
        // 1. Any symlink ancestry/root must fail. Read-only: no mutation.
        assertNoSymlinkAncestry(privateRoot)

        // 2. The reserved child directory must exist as a real directory, not a symlink.
        val child = File(privateRoot, CHILD_DIR)
        if (Files.isSymbolicLink(child.toPath())) {
            throw ArenaPolicyException("child directory is a symlink: ${child.path}")
        }
        if (!child.isDirectory) {
            throw ArenaPolicyException("child directory missing or not a directory: ${child.path}")
        }

        // 3. The config leaf must be a regular file, not a symlink (a symlink
        //    replacement that points elsewhere must be rejected before any read).
        val config = File(privateRoot, "$CHILD_DIR/$CONFIG_NAME")
        if (Files.isSymbolicLink(config.toPath())) {
            throw ArenaPolicyException("config is a symlink (likely replacement): ${config.path}")
        }
        if (!Files.isRegularFile(config.toPath())) {
            throw ArenaPolicyException("config missing or not a regular file: ${config.path}")
        }

        // 4. Re-read exact bytes (bounded) and reject any change: equal-length arena=1,
        //    appended memory-pattern key, truncation, or extra keys. Wrong length is
        //    rejected without allocating an input-sized array.
        val bytes = readBoundedPolicyBytes(config.toPath())

        // 5. Bind the receipt to the computed path, not arbitrary receipt paths.
        val canonical = config.canonicalFile
        val configPath = canonical.path
        if (receipt.configPath != configPath) {
            throw ArenaPolicyException("receipt configPath does not bind to this root")
        }
        if (receipt.provider != "cpu:$configPath") {
            throw ArenaPolicyException("receipt provider mismatch")
        }
        if (receipt.bytes != bytes.size.toLong()) {
            throw ArenaPolicyException("receipt byte count mismatch")
        }
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        if (receipt.sha256 != sha256) {
            throw ArenaPolicyException("receipt sha256 mismatch")
        }
        // No fallback to bare cpu: a failing verify simply throws.
    }
}
