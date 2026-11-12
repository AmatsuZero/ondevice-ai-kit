package io.ondeviceai.acceptance.terminal

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Comparator

private fun ackBoundaryIOException(block: () -> Unit) {
    try { block() } catch (_: IOException) { return }
    error("expected IOException")
}

fun main() {
    val root = Files.createTempDirectory(Paths.get("/private/tmp"), "ack-boundary-").toFile().canonicalFile
    var passed = 0
    fun case(name: String, block: () -> Unit) {
        block()
        passed++
        println("PASS $name")
    }
    try {
        case("constructor accepts an existing final and stage without modifying them") {
            val final = File(root, "ack-existing.txt")
            val stage = File(final.path + ".stage")
            val bytes = byteArrayOf(13, 10, -1, 0)
            final.writeBytes(bytes)
            stage.writeBytes(byteArrayOf(1))
            val reader = TerminalAckFile(root, "existing")
            check(reader.readOrNull()!!.contentEquals(bytes))
            ackBoundaryIOException { reader.requireAbsent() }
            check(final.readBytes().contentEquals(bytes))
            check(stage.readBytes().contentEquals(byteArrayOf(1)))
        }
        case("canonical root rejects dot and parent aliases") {
            val child = File(root, "child")
            check(child.mkdir())
            for (alias in listOf(File(root, "."), File(child, ".."))) {
                ackBoundaryIOException { TerminalAckFile(alias, "alias") }
            }
        }
        case("root rejects a symlink ancestor and a cancelled symlink") {
            val target = File(root, "target")
            check(target.mkdir())
            check(File(target, "child").mkdir())
            val link = File(root, "link")
            Files.createSymbolicLink(link.toPath(), target.toPath())
            ackBoundaryIOException { TerminalAckFile(File(link, "child"), "alias") }
            ackBoundaryIOException { TerminalAckFile(File(link, ".."), "alias") }
            check(Files.isSymbolicLink(link.toPath()))
        }
        case("ASCII maximum run ID is allowed and trailing newline is rejected") {
            val id = "a".repeat(80)
            check(TerminalAckFile(root, id).file == File(root, "ack-$id.txt"))
            check(TerminalAckFile(root, "-").readOrNull() == null)
            for (bad in listOf("ok\n", "ok\r", "é", "a".repeat(81))) {
                try {
                    TerminalAckFile(root, bad)
                    error("invalid run ID was accepted")
                } catch (_: IllegalArgumentException) { }
            }
        }
        println("TOTAL TESTS: 4  PASSED: $passed  FAILED: 0")
        check(passed == 4)
    } finally {
        Files.walk(root.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }
}
