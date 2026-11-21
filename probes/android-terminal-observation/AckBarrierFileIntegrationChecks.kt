package io.ondeviceai.acceptance.terminal

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Comparator
import java.util.concurrent.TimeoutException

private var ackFileIntegrationTotal = 0
private var ackFileIntegrationPassed = 0

private fun ackFileCase(name: String, block: () -> Unit) {
    ackFileIntegrationTotal++
    try {
        block()
        ackFileIntegrationPassed++
        println("PASS $name")
    } catch (failure: Throwable) {
        println("FAIL $name: ${failure.javaClass.name}: ${failure.message}")
        failure.printStackTrace()
    }
}

private inline fun <reified T : Throwable> ackFileFailure(block: () -> Unit) {
    try {
        block()
    } catch (failure: Throwable) {
        check(failure is T) { "expected ${T::class.java.name}, got $failure" }
        return
    }
    error("expected ${T::class.java.name}")
}

private fun withAckFileRoot(block: (File) -> Unit) {
    val root = Files.createTempDirectory(Paths.get("/private/tmp"), "w3-ack-parent-")
        .toFile().canonicalFile
    try {
        block(root)
    } finally {
        // walk does not follow symlinks; delete only the owned fixture tree.
        Files.walk(root.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }
}

private fun ackFileBinding() = TerminalObservationBinding(
    runId = "standin-ACK-v1",
    sourceFingerprint = "a".repeat(64),
    pid = 731,
    startTicks = "123456789",
    terminalReportSha256 = "b".repeat(64)
)

fun main() {
    ackFileCase("complete staged bytes become visible through final hard link") {
        withAckFileRoot { root ->
            val binding = ackFileBinding()
            val ack = TerminalAckFile(root, binding.runId)
            ack.requireAbsent()
            val stage = File(ack.file.path + ".stage")
            var now = 0L
            TerminalObservationBarrier(binding).awaitAck(
                readAck = { ack.readOrNull() },
                nowMillis = { now },
                sleepMillis = { duration ->
                    now += duration
                    stage.writeBytes(binding.ackBytes())
                    Files.createLink(ack.file.toPath(), stage.toPath())
                },
                timeoutMillis = 100
            )
            check(now == 50L)
            check(ack.file.readBytes().contentEquals(binding.ackBytes()))
            check(stage.readBytes().contentEquals(binding.ackBytes()))
        }
    }
    ackFileCase("correct stage alone is never an ACK") {
        withAckFileRoot { root ->
            val binding = ackFileBinding()
            val ack = TerminalAckFile(root, binding.runId)
            ack.requireAbsent()
            val stage = File(ack.file.path + ".stage")
            stage.writeBytes(binding.ackBytes())
            var now = 0L
            ackFileFailure<TimeoutException> {
                TerminalObservationBarrier(binding).awaitAck(
                    { ack.readOrNull() }, { now }, { now += it }, 100
                )
            }
            check(!ack.file.exists())
            check(stage.readBytes().contentEquals(binding.ackBytes()))
        }
    }
    ackFileCase("wrong final bytes stop without deleting evidence") {
        withAckFileRoot { root ->
            val binding = ackFileBinding()
            val ack = TerminalAckFile(root, binding.runId)
            ack.requireAbsent()
            val wrong = binding.ackBytes() + byteArrayOf(13)
            ack.file.writeBytes(wrong)
            ackFileFailure<IOException> {
                TerminalObservationBarrier(binding).awaitAck(
                    { ack.readOrNull() }, { 0L }, { error("unexpected sleep") }, 100
                )
            }
            check(ack.file.readBytes().contentEquals(wrong))
        }
    }
    ackFileCase("oversized final rejects through the real reader") {
        withAckFileRoot { root ->
            val binding = ackFileBinding()
            val ack = TerminalAckFile(root, binding.runId)
            val oversized = ByteArray(1025) { 65 }
            ack.file.writeBytes(oversized)
            ackFileFailure<IOException> {
                TerminalObservationBarrier(binding).awaitAck(
                    { ack.readOrNull() }, { 0L }, { error("unexpected sleep") }, 100
                )
            }
            check(ack.file.readBytes().contentEquals(oversized))
        }
    }
    ackFileCase("valid ACK behind a final symlink is refused") {
        withAckFileRoot { root ->
            val binding = ackFileBinding()
            val ack = TerminalAckFile(root, binding.runId)
            val target = File(root, "valid-target")
            target.writeBytes(binding.ackBytes())
            Files.createSymbolicLink(ack.file.toPath(), target.toPath())
            ackFileFailure<IOException> {
                TerminalObservationBarrier(binding).awaitAck(
                    { ack.readOrNull() }, { 0L }, { error("unexpected sleep") }, 100
                )
            }
            check(Files.isSymbolicLink(ack.file.toPath()))
            check(target.readBytes().contentEquals(binding.ackBytes()))
        }
    }
    ackFileCase("real correct final returned at the exact deadline is late") {
        withAckFileRoot { root ->
            val binding = ackFileBinding()
            val ack = TerminalAckFile(root, binding.runId)
            ack.requireAbsent()
            var now = 0L
            ackFileFailure<TimeoutException> {
                TerminalObservationBarrier(binding).awaitAck(
                    readAck = {
                        ack.file.writeBytes(binding.ackBytes())
                        now = 100L
                        ack.readOrNull()
                    },
                    nowMillis = { now },
                    sleepMillis = { error("unexpected sleep") },
                    timeoutMillis = 100
                )
            }
            check(ack.file.readBytes().contentEquals(binding.ackBytes()))
        }
    }
    println("TOTAL TESTS: $ackFileIntegrationTotal  PASSED: $ackFileIntegrationPassed  FAILED: ${ackFileIntegrationTotal - ackFileIntegrationPassed}")
    check(ackFileIntegrationTotal == 6 && ackFileIntegrationPassed == 6)
}
