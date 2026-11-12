package io.ondeviceai.acceptance.terminal

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

// Owns only TerminalAckFileChecks.kt. References TerminalAckFile (not yet implemented here).
// Missing implementation must surface as unresolved-reference compile failure, never a skipped green.
fun main() {
    val toClean = mutableListOf<File>()
    var seq = 0
    fun nid(): String = "t${seq++}"

    fun mkTmpDir(label: String): File {
        val dir = File("/private/tmp", "tack_${label}_${nid()}_${System.nanoTime()}")
        if (!dir.mkdirs()) throw IllegalStateException("cannot create fixture dir $dir")
        val c = dir.canonicalFile
        toClean.add(c)
        return c
    }

    fun safeDelete(f: File) {
        val p = f.toPath()
        try {
            if (Files.isSymbolicLink(p)) { Files.delete(p); return }
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                f.listFiles()?.forEach { safeDelete(it) }
            }
            Files.delete(p)
        } catch (_: Exception) { /* best-effort cleanup only */ }
    }

    var tests = 0
    var passed = 0
    fun expect(cond: Boolean, msg: String) { if (!cond) throw AssertionError(msg) }
    fun expectIo(block: () -> Unit) {
        try { block() } catch (e: java.io.IOException) { return }
        throw AssertionError("expected IOException was not thrown")
    }
    fun expectIllegal(block: () -> Unit) {
        try { block() } catch (e: IllegalArgumentException) { return }
        throw AssertionError("expected IllegalArgumentException was not thrown")
    }
    fun check(name: String, block: () -> Unit) {
        tests++
        block()
        passed++
    }

    try {
        check("ctor_valid_no_writes") {
            val root = mkTmpDir("valid")
            val t = TerminalAckFile(root, "run-1")
            expect(t.file == File(root, "ack-run-1.txt"), "file path mismatch")
            expect((root.listFiles()?.size ?: 0) == 0, "ctor must not create files in root")
        }

        check("invalid_runids") {
            val root = mkTmpDir("invalidid")
            val bad = listOf("", " ", "a/b", "../x", "a b", "a".repeat(81), "run_1", "UP#", "①②③")
            for (id in bad) expectIllegal { TerminalAckFile(root, id) }
        }

        check("invalid_roots") {
            val missing = File("/private/tmp/tack_missing_${nid()}")
            expectIo { TerminalAckFile(missing, "r1") }
            val root = mkTmpDir("rootfile")
            val asFile = File(root, "afile"); asFile.writeBytes(byteArrayOf(1))
            expectIo { TerminalAckFile(asFile, "r1") }
            expectIo { TerminalAckFile(File("relative/path"), "r1") }
            val real = mkTmpDir("realroot")
            val link = File("/private/tmp", "tack_link_${nid()}")
            Files.createSymbolicLink(link.toPath(), real.toPath())
            toClean.add(link)
            expectIo { TerminalAckFile(link, "r1") }
        }

        check("requireAbsent_stale_files") {
            val root = mkTmpDir("raf")
            val t = TerminalAckFile(root, "r1")
            t.file.writeBytes("FINAL".toByteArray())
            expectIo { t.requireAbsent() }
            t.file.delete()
            val stage = File(t.file.path + ".stage")
            stage.writeBytes("STAGE".toByteArray())
            expectIo { t.requireAbsent() }
        }

        check("requireAbsent_stale_dirs") {
            val root = mkTmpDir("rad")
            val t = TerminalAckFile(root, "r1")
            expect(t.file.mkdirs(), "cannot create final dir fixture")
            expectIo { t.requireAbsent() }
            t.file.delete()
            val stage = File(t.file.path + ".stage")
            expect(stage.mkdirs(), "cannot create stage dir fixture")
            expectIo { t.requireAbsent() }
        }

        check("requireAbsent_stale_symlinks") {
            val root = mkTmpDir("ras")
            val t = TerminalAckFile(root, "r1")
            Files.createSymbolicLink(t.file.toPath(), Paths.get("/private/tmp/tack_nonexistent_target_${nid()}"))
            expectIo { t.requireAbsent() }
            Files.delete(t.file.toPath())
            val stage = File(t.file.path + ".stage")
            Files.createSymbolicLink(stage.toPath(), Paths.get("/private/tmp/tack_nonexistent_target2_${nid()}"))
            expectIo { t.requireAbsent() }
        }

        check("readOrNull_absent_final_with_stage") {
            val root = mkTmpDir("ran")
            val t = TerminalAckFile(root, "r1")
            val stage = File(t.file.path + ".stage")
            stage.writeBytes("host-writing".toByteArray())
            val r = t.readOrNull()
            expect(r == null, "absent final must return null even when stage exists")
            expect(stage.exists(), "stage entry must remain untouched")
        }

        check("readOrNull_byte_exactness_crlf_nonascii") {
            val root = mkTmpDir("rex")
            val t = TerminalAckFile(root, "r1")
            val data = "A\r\nB\u00e9\u4e2d\u0000\uffff".toByteArray(Charsets.UTF_8)
            t.file.writeBytes(data)
            val r = t.readOrNull()
            expect(r != null, "expected non-null read for regular final")
            expect(r!!.contentEquals(data), "byte content not exact (CR/LF/non-ASCII broken)")
        }

        check("readOrNull_boundary_1_and_1024") {
            val root = mkTmpDir("rbd")
            val t = TerminalAckFile(root, "r1")
            val one = byteArrayOf(0x7F)
            t.file.writeBytes(one)
            expect(t.readOrNull()!!.contentEquals(one), "1-byte boundary read mismatch")
            t.file.delete()
            val kb = ByteArray(1024) { it.toByte() }
            t.file.writeBytes(kb)
            expect(t.readOrNull()!!.contentEquals(kb), "1024-byte boundary read mismatch")
        }

        check("readOrNull_empty_and_1025_rejected") {
            val root = mkTmpDir("rem")
            val t = TerminalAckFile(root, "r1")
            t.file.writeBytes(byteArrayOf())
            expectIo { t.readOrNull() }
            t.file.delete()
            val big = ByteArray(1025) { 0 }
            t.file.writeBytes(big)
            expectIo { t.readOrNull() }
        }

        check("readOrNull_final_dir_refused") {
            val root = mkTmpDir("rfd")
            val t = TerminalAckFile(root, "r1")
            expect(t.file.mkdirs(), "cannot create final dir fixture")
            expectIo { t.readOrNull() }
        }

        check("readOrNull_final_symlink_refused") {
            val root = mkTmpDir("rfs")
            val t = TerminalAckFile(root, "r1")
            Files.createSymbolicLink(t.file.toPath(), Paths.get("/private/tmp/tack_dangling_${nid()}"))
            expectIo { t.readOrNull() }
        }

        check("stale_entries_untouched") {
            val root = mkTmpDir("sut")
            val t = TerminalAckFile(root, "r1")
            t.file.writeBytes("KEEP".toByteArray())
            expectIo { t.requireAbsent() }
            expect(t.file.exists() && t.file.readBytes().contentEquals("KEEP".toByteArray()),
                "stale final entry was mutated or removed")
            t.file.delete()
            val stage = File(t.file.path + ".stage")
            stage.writeBytes("STAGE".toByteArray())
            expectIo { t.requireAbsent() }
            expect(stage.exists() && stage.readBytes().contentEquals("STAGE".toByteArray()),
                "stale stage entry was mutated or removed")
        }

        println("TOTAL TESTS: $tests  PASSED: $passed  FAILED: 0")
    } finally {
        toClean.forEach { safeDelete(it) }
    }
}