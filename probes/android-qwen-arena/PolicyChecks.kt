package io.ondeviceai.acceptance.qwenarena

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

private var assertions = 0
private var groups = 0
private val parent = Files.createTempDirectory(Paths.get("/private/tmp"), "odai-arena-policy-check-").toFile()
private fun root(name: String): File = File(parent, name).also { check(it.mkdirs()) }
private fun ok(value: Boolean) { assertions++; check(value) { "assertion $assertions failed" } }
private fun rejects(body: () -> Unit) {
    var error: Exception? = null
    try { body() } catch (e: Exception) { error = e }
    ok(error != null)
}
private fun group(name: String, body: () -> Unit) { body(); groups++; println("PASS $name") }

fun main() {
    val exact = "EnableCpuMemArena=0\n".toByteArray(Charsets.UTF_8)
    group("exact bytes, canonical provider and independently computed receipt") {
        val r = root("normal with space")
        val receipt = QwenArenaPolicy.prepare(r)
        val path = File(r, "cpu-arena-off/cpu.config").canonicalFile
        ok(path.readBytes().contentEquals(exact))
        ok(receipt.configPath == path.path)
        ok(receipt.provider == "cpu:${path.path}")
        ok(receipt.bytes == exact.size.toLong())
        ok(receipt.sha256 == MessageDigest.getInstance("SHA-256").digest(exact).joinToString("") { "%02x".format(it) })
        QwenArenaPolicy.verify(r, receipt)
        ok(File(r, "cpu-arena-off").listFiles()!!.map { it.name } == listOf("cpu.config"))
        rejects { QwenArenaPolicy.prepare(r) }
        ok(path.readBytes().contentEquals(exact))
    }
    group("existing child directory is never adopted or overwritten") {
        val r = root("collision"); val d = File(r, "cpu-arena-off"); ok(d.mkdir())
        val f = File(d, "cpu.config"); f.writeText("preserve")
        rejects { QwenArenaPolicy.prepare(r) }; ok(f.readText() == "preserve")
    }
    group("missing root and regular file root are rejected without changes") {
        val missing = File(parent, "missing")
        rejects { QwenArenaPolicy.prepare(missing) }; ok(!missing.exists())
        val file = File(parent, "regular").also { it.writeText("preserve") }
        rejects { QwenArenaPolicy.prepare(file) }; ok(file.readText() == "preserve")
    }
    group("symlink private root does not redirect writes") {
        val real = root("real-root"); val link = File(parent, "root-link")
        Files.createSymbolicLink(link.toPath(), real.toPath())
        rejects { QwenArenaPolicy.prepare(link) }; ok(real.listFiles()!!.isEmpty())
    }
    group("symlink ancestor does not redirect writes") {
        val real = root("real-ancestor"); val nested = File(real, "nested").also { ok(it.mkdir()) }
        val link = File(parent, "ancestor-link"); Files.createSymbolicLink(link.toPath(), real.toPath())
        rejects { QwenArenaPolicy.prepare(File(link, "nested")) }; ok(nested.listFiles()!!.isEmpty())
    }
    group("normalization cannot erase a symlink before dot-dot") {
        val targetParent = root("dot-target-parent")
        val targetChild = File(targetParent, "child").also { ok(it.mkdir()) }
        val actual = File(targetParent, "target").also { ok(it.mkdir()) }
        val normalized = root("target")
        val link = File(parent, "dot-link"); Files.createSymbolicLink(link.toPath(), targetChild.toPath())
        rejects { QwenArenaPolicy.prepare(File(link, "../target")) }
        ok(actual.listFiles()!!.isEmpty()); ok(normalized.listFiles()!!.isEmpty())
    }
    group("symlink reserved child does not overwrite external data") {
        val r = root("child-link"); val outside = root("outside-child")
        val f = File(outside, "cpu.config").also { it.writeText("preserve") }
        Files.createSymbolicLink(File(r, "cpu-arena-off").toPath(), outside.toPath())
        rejects { QwenArenaPolicy.prepare(r) }; ok(f.readText() == "preserve")
    }
    group("equal-size content tampering is rejected") {
        val r = root("content-tamper"); val receipt = QwenArenaPolicy.prepare(r)
        File(receipt.configPath).writeText("EnableCpuMemArena=1\n")
        rejects { QwenArenaPolicy.verify(r, receipt) }
    }
    group("extra policy key is rejected") {
        val r = root("extra-key"); val receipt = QwenArenaPolicy.prepare(r)
        File(receipt.configPath).appendText("EnableMemPattern=0\n")
        rejects { QwenArenaPolicy.verify(r, receipt) }
    }
    group("oversized sparse config is rejected before an unbounded read") {
        val r = root("oversized"); val receipt = QwenArenaPolicy.prepare(r)
        val file = File(receipt.configPath)
        java.io.RandomAccessFile(file, "rw").use { it.setLength(Int.MAX_VALUE.toLong() + 1L) }
        try { rejects { QwenArenaPolicy.verify(r, receipt) } } finally { ok(file.delete()) }
    }
    group("receipt fields are independently bound") {
        val r = root("receipt"); val receipt = QwenArenaPolicy.prepare(r)
        rejects { QwenArenaPolicy.verify(r, receipt.copy(provider = "cpu")) }
        rejects { QwenArenaPolicy.verify(r, receipt.copy(configPath = receipt.configPath + ".other")) }
        rejects { QwenArenaPolicy.verify(r, receipt.copy(bytes = receipt.bytes + 1)) }
        rejects { QwenArenaPolicy.verify(r, receipt.copy(sha256 = "0".repeat(64))) }
        ok(File(receipt.configPath).readBytes().contentEquals(exact))
    }
    group("missing config and symlink replacement reject verification") {
        val r = root("replace-leaf"); val receipt = QwenArenaPolicy.prepare(r)
        val p = File(receipt.configPath); ok(p.delete())
        rejects { QwenArenaPolicy.verify(r, receipt) }
        val outside = File(parent, "outside-leaf").also { it.writeBytes(exact) }
        Files.createSymbolicLink(p.toPath(), outside.toPath())
        rejects { QwenArenaPolicy.verify(r, receipt) }; ok(outside.readBytes().contentEquals(exact))
    }
    group("alternate root cannot borrow a valid receipt") {
        val r = root("first-root"); val receipt = QwenArenaPolicy.prepare(r)
        val other = root("other-root"); QwenArenaPolicy.prepare(other)
        rejects { QwenArenaPolicy.verify(other, receipt) }
    }
    println("RESULT groups=$groups assertions=$assertions nativeCalls=0 deviceOperations=0")
    println("retainedTestRoot=${parent.path}")
}
