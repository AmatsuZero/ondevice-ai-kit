package io.ondeviceai.acceptance.terminal

import java.io.IOException
import java.util.concurrent.TimeoutException
import java.nio.charset.StandardCharsets.US_ASCII

data class TerminalObservationBinding(
    val runId: String,
    val sourceFingerprint: String,
    val pid: Int,
    val startTicks: String,
    val terminalReportSha256: String
) {
    init {
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "invalid runId" }
        require(sourceFingerprint.matches(Regex("[0-9a-f]{64}"))) { "invalid sourceFingerprint" }
        require(terminalReportSha256.matches(Regex("[0-9a-f]{64}"))) { "invalid terminalReportSha256" }
        require(pid > 0) { "pid must be positive" }
        require(startTicks.matches(Regex("[1-9][0-9]*"))) { "invalid startTicks" }
        val ticks = startTicks.toLongOrNull()
        require(ticks != null && ticks <= Long.MAX_VALUE) { "startTicks overflow" }
    }

    fun ackBytes(): ByteArray {
        val lines = listOf(
            "ODAI-TERMINAL-ACK-v1",
            runId,
            sourceFingerprint,
            pid.toString(),
            startTicks,
            terminalReportSha256
        )
        return (lines.joinToString("\n") + "\n").toByteArray(US_ASCII)
    }
}

class TerminalObservationBarrier(private val binding: TerminalObservationBinding) {
    fun awaitAck(
        readAck: () -> ByteArray?,
        nowMillis: () -> Long,
        sleepMillis: (Long) -> Unit,
        timeoutMillis: Long = 30000
    ) {
        require(timeoutMillis in 1..30000) { "timeout out of range" }
        val expected = binding.ackBytes()
        val entry = nowMillis()
        if (entry < 0) throw IllegalStateException("negative entry clock")
        var lastObserved = entry
        while (true) {
            val beforeRead = nowMillis()
            if (beforeRead < lastObserved) throw IllegalStateException("clock regression")
            if (beforeRead - entry >= timeoutMillis) throw TimeoutException()
            val data = readAck()
            val afterRead = nowMillis()
            if (afterRead < beforeRead) throw IllegalStateException("clock regression")
            if (afterRead - entry >= timeoutMillis) throw TimeoutException()
            if (data != null) {
                if (data.contentEquals(expected)) return
                throw IOException("invalid ACK bytes")
            }
            val remaining = timeoutMillis - (afterRead - entry)
            val s = minOf(50L, remaining)
            if (s <= 0L) throw TimeoutException()
            sleepMillis(s)
            val afterSleep = nowMillis()
            if (afterSleep < afterRead) throw IllegalStateException("clock regression")
            if (afterSleep - entry >= timeoutMillis) throw TimeoutException()
            lastObserved = afterSleep
        }
    }
}