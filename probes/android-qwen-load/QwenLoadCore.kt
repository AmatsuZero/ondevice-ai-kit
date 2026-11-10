package io.ondeviceai.acceptance.qwenload

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class ModelSpec(val relativePath: String, val bytes: Long, val sha256: String)

object QwenModelFiles {
    val frozenSpecs: List<ModelSpec> = listOf(
        ModelSpec("conv_frontend.onnx", 44148281L,
            "d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e"),
        ModelSpec("decoder.int8.onnx", 755914231L,
            "4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771"),
        ModelSpec("encoder.int8.onnx", 182491662L,
            "60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9"),
        ModelSpec("tokenizer/merges.txt", 1671853L,
            "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5"),
        ModelSpec("tokenizer/tokenizer_config.json", 12487L,
            "4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c"),
        ModelSpec("tokenizer/vocab.json", 2776833L,
            "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910")
    )

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    private fun validRel(rel: String): Boolean {
        if (rel.isEmpty() || rel.startsWith('/')) return false
        for (c in rel.split('/')) if (c.isEmpty() || c == "." || c == "..") return false
        return true
    }

    private fun rejectAncestorSymlink(p: Path) {
        var cur = p.parent
        while (cur != null) {
            if (Files.exists(cur) && Files.isSymbolicLink(cur))
                throw SecurityException("symlink ancestor: $cur")
            cur = cur.parent
        }
    }

    private fun verifyFile(root: Path, spec: ModelSpec) {
        var cur = root
        for (c in spec.relativePath.split('/')) {
            cur = cur.resolve(c)
            if (Files.exists(cur) && Files.isSymbolicLink(cur))
                throw SecurityException("symlink in path: ${spec.relativePath}")
        }
        if (!Files.exists(cur)) throw java.nio.file.NoSuchFileException("missing: ${spec.relativePath}")
        if (!Files.isRegularFile(cur)) throw IllegalArgumentException("not a regular file: ${spec.relativePath}")
        if (Files.size(cur) != spec.bytes)
            throw IllegalArgumentException("size mismatch: ${spec.relativePath}")
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(cur).use { ins ->
            val buf = ByteArray(64 * 1024)
            var n = ins.read(buf)
            while (n != -1) {
                digest.update(buf, 0, n)
                n = ins.read(buf)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != spec.sha256)
            throw IllegalArgumentException("hash mismatch: ${spec.relativePath}")
    }

    fun verify(root: File, specs: List<ModelSpec> = frozenSpecs): List<ModelSpec> {
        if (specs.isEmpty()) throw IllegalArgumentException("empty specs")
        val rootPath = root.toPath()
        if (Files.isSymbolicLink(rootPath)) throw SecurityException("root is symlink")
        if (!Files.isDirectory(rootPath)) throw IllegalArgumentException("root not a directory")
        rejectAncestorSymlink(rootPath)
        val seen = mutableSetOf<String>()
        for (s in specs) {
            if (!seen.add(s.relativePath)) throw IllegalArgumentException("duplicate path: ${s.relativePath}")
            if (s.bytes <= 0L) throw IllegalArgumentException("nonpositive size: ${s.relativePath}")
            if (!HEX64.matches(s.sha256)) throw IllegalArgumentException("bad hash: ${s.relativePath}")
            if (!validRel(s.relativePath)) throw IllegalArgumentException("bad path: ${s.relativePath}")
        }
        for (s in specs) verifyFile(rootPath, s)
        return specs
    }
}

fun interface LoadHandle { fun release() }

data class LoadState(
    val status: String = "running",
    val phase: String = "initial",
    val constructorAttempts: Int = 0,
    val constructorReturned: Boolean = false,
    val releaseAttempts: Int = 0,
    val releaseSucceeded: Boolean = false,
    val loadNanos: Long? = null,
    val releaseNanos: Long? = null,
    val errorClass: String? = null,
    val errorMessage: String? = null,
    val releaseErrorClass: String? = null,
    val persistErrorClass: String? = null
)

class LoadCoordinator(
    private val factory: () -> LoadHandle,
    private val gate: () -> Boolean,
    private val persist: (LoadState) -> Unit,
    private val now: () -> Long = System::nanoTime
) {
    fun execute(): LoadState {
        var s = LoadState()
        var handle: LoadHandle? = null
        var mainErr: Throwable? = null
        var persistErr: Throwable? = null

        fun className(t: Throwable): String = t::class.qualifiedName ?: t.javaClass.name
        fun clamp(d: Long): Long = if (d < 0) 0L else d
        fun recordErr(t: Throwable) {
            if (mainErr == null) {
                mainErr = t
                s = s.copy(errorClass = className(t), errorMessage = t.message)
            }
        }
        fun pers(st: LoadState) {
            try {
                persist(st)
            } catch (t: Throwable) {
                if (persistErr == null) {
                    persistErr = t
                    recordErr(t)
                    s = s.copy(persistErrorClass = className(t))
                }
            }
        }
        fun gateOk(): Boolean {
            return try {
                if (gate()) true else { recordErr(IllegalStateException("gate closed")); false }
            } catch (t: Throwable) {
                recordErr(t)
                false
            }
        }
        fun elapsed(start: Long): Long? {
            return try { clamp(now() - start) } catch (t: Throwable) { recordErr(t); null }
        }

        try {
            val g1 = gateOk()
            s = s.copy(phase = "before_create")
            pers(s)
            val g2 = gateOk()
            if (g1 && g2 && s.persistErrorClass == null) {
                val t0 = try { now() } catch (t: Throwable) { recordErr(t); null }
                s = s.copy(constructorAttempts = s.constructorAttempts + 1)
                try {
                    handle = factory()
                    s = s.copy(constructorReturned = true)
                    if (t0 != null) {
                        val loadDur = elapsed(t0)
                        s = s.copy(loadNanos = loadDur)
                    }
                } catch (t: Throwable) {
                    recordErr(t)
                    if (t0 != null) {
                        val loadDur = elapsed(t0)
                        s = s.copy(loadNanos = loadDur)
                    }
                }
            }
            gateOk()
            s = s.copy(phase = "after_load")
            pers(s)
        } catch (t: Throwable) {
            recordErr(t)
        } finally {
            if (handle != null) {
                val r0 = try { now() } catch (t: Throwable) { recordErr(t); null }
                s = s.copy(releaseAttempts = s.releaseAttempts + 1)
                try {
                    handle.release()
                    s = s.copy(releaseSucceeded = true)
                    if (r0 != null) {
                        val relDur = elapsed(r0)
                        s = s.copy(releaseNanos = relDur)
                    }
                } catch (t: Throwable) {
                    if (s.releaseErrorClass == null) s = s.copy(releaseErrorClass = className(t))
                    recordErr(t)
                }
            }
            gateOk()
            val hasErr = s.errorClass != null || s.releaseErrorClass != null || s.persistErrorClass != null
            s = s.copy(status = if (hasErr) "error" else "completed", phase = "done")
            pers(s)
            if (s.persistErrorClass != null) s = s.copy(status = "error")
        }
        return s
    }
}
