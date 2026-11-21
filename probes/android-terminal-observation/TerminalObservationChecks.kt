package io.ondeviceai.acceptance.terminal

import java.io.IOException
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.TimeoutException

fun ack(b: TerminalObservationBinding): ByteArray {
    val lines = listOf(
        "ODAI-TERMINAL-ACK-v1",
        b.runId,
        b.sourceFingerprint,
        b.pid.toString(),
        b.startTicks,
        b.terminalReportSha256
    )
    return (lines.joinToString("\n") + "\n").toByteArray(US_ASCII)
}

fun makeValid(): TerminalObservationBinding = TerminalObservationBinding(
    runId = "run-1",
    sourceFingerprint = "a".repeat(64),
    pid = 1234,
    startTicks = "1700000000000",
    terminalReportSha256 = "b".repeat(64)
)

var passed = 0
var failed = 0

fun ok(msg: String) { passed++; println("PASS $msg") }
fun bad(msg: String, e: Throwable?) {
    failed++
    val d = e?.let { " -> ${it::class.simpleName}: ${it.message}" } ?: ""
    println("FAIL $msg$d")
}

fun assertNoThrow(msg: String, block: () -> Unit) {
    try { block(); ok(msg) } catch (e: Throwable) { bad(msg, e) }
}
inline fun <reified T : Throwable> assertThrows(msg: String, block: () -> Unit) {
    try { block(); bad(msg, null) } catch (e: Throwable) {
        if (e is T) ok(msg) else bad(msg, e)
    }
}
inline fun <reified T : Throwable> assertThrowsSame(msg: String, ex: Throwable, block: () -> Unit) {
    try { block(); bad(msg, null) } catch (e: Throwable) {
        if (e is T && e === ex) ok(msg) else bad(msg, e)
    }
}

fun main() {
    val v = makeValid()

    assertNoThrow("valid binding constructs") { makeValid() }
    assertThrows<IllegalArgumentException>("non-ASCII runId rejected") { TerminalObservationBinding("run\u00e91", "a".repeat(64), 1, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("control char runId rejected") { TerminalObservationBinding("run\u00011", "a".repeat(64), 1, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("newline runId rejected") { TerminalObservationBinding("run\n1", "a".repeat(64), 1, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("runId >80 rejected") { TerminalObservationBinding("a".repeat(81), "a".repeat(64), 1, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("uppercase sha rejected") { TerminalObservationBinding("r", "A".repeat(64), 1, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("non-hex sha rejected") { TerminalObservationBinding("r", "g".repeat(64), 1, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("pid zero rejected") { TerminalObservationBinding("r", "a".repeat(64), 0, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("pid negative rejected") { TerminalObservationBinding("r", "a".repeat(64), -1, "1", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("startTicks leading zero rejected") { TerminalObservationBinding("r", "a".repeat(64), 1, "01", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("startTicks sign rejected") { TerminalObservationBinding("r", "a".repeat(64), 1, "-5", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("startTicks whitespace rejected") { TerminalObservationBinding("r", "a".repeat(64), 1, "5 ", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("startTicks overflow rejected") { TerminalObservationBinding("r", "a".repeat(64), 1, "9223372036854775808", "b".repeat(64)) }
    assertThrows<IllegalArgumentException>("startTicks zero rejected") { TerminalObservationBinding("r", "a".repeat(64), 1, "0", "b".repeat(64)) }

    assertNoThrow("ackBytes matches exact payload") {
        require(v.ackBytes().contentEquals(ack(v))) { "ackBytes mismatch" }
    }

    assertThrows<IllegalArgumentException>("timeout 0 rejected") { TerminalObservationBarrier(v).awaitAck({ null }, { 0 }, {}, 0) }
    assertThrows<IllegalArgumentException>("timeout -1 rejected") { TerminalObservationBarrier(v).awaitAck({ null }, { 0 }, {}, -1) }
    assertThrows<IllegalArgumentException>("timeout 30001 rejected") { TerminalObservationBarrier(v).awaitAck({ null }, { 0 }, {}, 30001) }

    assertNoThrow("exact ACK accepted immediately") { TerminalObservationBarrier(v).awaitAck({ ack(v) }, { 0 }, {}) }

    run {
        var t = 0L; var reads = 0
        val now = { t }
        val read = { reads++; if (reads <= 2) { t += 40; null } else ack(v) }
        assertNoThrow("delayed ACK accepted") { TerminalObservationBarrier(v).awaitAck(read, now, {}) }
    }

    run {
        var t = 0L; var afterExpiry = 0
        val now = { t }
        val read = { if (t >= 100) afterExpiry++; t += 60; null }
        assertThrows<TimeoutException>("null until timeout throws") { TerminalObservationBarrier(v).awaitAck(read, now, {}, 100) }
        if (afterExpiry == 0) ok("no read after expired deadline") else bad("no read after expired deadline", null)
    }

    assertThrows<IOException>("empty bytes rejected") { TerminalObservationBarrier(v).awaitAck({ byteArrayOf() }, { 0 }, {}) }
    assertThrows<IOException>("mismatch bytes rejected") {
        val bad = ack(v).also { it[it.size - 2] = 'X'.code.toByte() }
        TerminalObservationBarrier(v).awaitAck({ bad }, { 0 }, {})
    }
    assertThrows<IOException>("oversize bytes rejected") { TerminalObservationBarrier(v).awaitAck({ ByteArray(1025) { 'a'.code.toByte() } }, { 0 }, {}) }
    assertThrows<IOException>("malformed UTF8 rejected") { TerminalObservationBarrier(v).awaitAck({ byteArrayOf(0xFF.toByte(), 0xFE.toByte()) }, { 0 }, {}) }
    assertThrows<IOException>("CRLF bytes rejected") {
        val s = listOf("ODAI-TERMINAL-ACK-v1", v.runId, v.sourceFingerprint, v.pid.toString(), v.startTicks, v.terminalReportSha256).joinToString("\r\n") + "\r\n"
        TerminalObservationBarrier(v).awaitAck({ s.toByteArray(US_ASCII) }, { 0 }, {})
    }

    run {
        var t = 0L; var reads = 0
        val now = { t }
        val read = { reads++; if (reads <= 2) { t += 40; null } else { t += 10; ack(v) } }
        assertNoThrow("ACK at 90ms accepted") { TerminalObservationBarrier(v).awaitAck(read, now, {}, 100) }
    }

    run {
        var reads = 0
        var t = 0L
        val now = { t }
        val read = { reads++; t = 100L; ack(v) }
        assertThrows<TimeoutException>("matching ACK at exactly 100ms times out") { TerminalObservationBarrier(v).awaitAck(read, now, {}, 100) }
        if (reads == 1) ok("exactly-100ms read happens once") else bad("exactly-100ms read happens once", null)
    }
    run {
        var reads = 0
        var t = 0L
        val now = { t }
        val read = { reads++; t = 101L; ack(v) }
        assertThrows<TimeoutException>("matching ACK at exactly 101ms times out") { TerminalObservationBarrier(v).awaitAck(read, now, {}, 100) }
        if (reads == 1) ok("exactly-101ms read happens once") else bad("exactly-101ms read happens once", null)
    }
    assertThrows<IllegalStateException>("negative entry clock rejected") { TerminalObservationBarrier(v).awaitAck({ null }, { -1 }, {}) }
    run {
        var n = 0
        val now = { if (n++ == 0) 100L else 50L }
    run {
        var reads = 0
        var n = 0
        val now = { if (n++ == 0) 0L else 100L }
        val read = { reads++; null }
        assertThrows<TimeoutException>("pre-read expiration times out") { TerminalObservationBarrier(v).awaitAck(read, now, {}, 100) }
        if (reads == 0) ok("no read on pre-read expiration") else bad("no read on pre-read expiration", null)
    }
        assertThrows<IllegalStateException>("regressing clock rejected") { TerminalObservationBarrier(v).awaitAck({ null }, now, {}, 1000) }
    }

    run {
        val ex = RuntimeException("clock boom")
    run {
        var reads = 0
        var t = 10L
        val now = { t }
        val read = { reads++; t = 9L; ack(v) }
        assertThrows<IllegalStateException>("clock regression after matching ACK rejected") { TerminalObservationBarrier(v).awaitAck(read, now, {}, 100) }
    }
        assertThrowsSame<RuntimeException>("clock error propagates", ex) { TerminalObservationBarrier(v).awaitAck({ null }, { throw ex }, {}) }
    }
    run {
        val ex = RuntimeException("read boom")
        assertThrowsSame<RuntimeException>("read error propagates", ex) { TerminalObservationBarrier(v).awaitAck({ throw ex }, { 0 }, {}) }
    }
    run {
        val ex = RuntimeException("sleep boom")
        assertThrowsSame<RuntimeException>("sleep error propagates", ex) { TerminalObservationBarrier(v).awaitAck({ null }, { 0 }, { throw ex }) }
    }
    run {
        val ex = InterruptedException("interrupted")
        assertThrowsSame<InterruptedException>("interrupted sleep propagates", ex) { TerminalObservationBarrier(v).awaitAck({ null }, { 0 }, { throw ex }) }
    }

    run {
        assertNoThrow("sleep budget at timeout 1") {
            val slept = mutableListOf<Long>()
            var t = 0L
            val now = { t }
            val sleep = { d: Long -> slept += d; t += d }
            try { TerminalObservationBarrier(v).awaitAck({ null }, now, sleep, 1); throw AssertionError("expected timeout") }
            catch (e: TimeoutException) {}
            require(slept.size == 1 && slept[0] == 1L) { "expected [1] got $slept" }
            require(slept.all { it in 1..50 }) { "sleep zero/out-of-budget: $slept" }
        }
    }
    run {
        assertNoThrow("sleep budget at timeout 51") {
            val slept = mutableListOf<Long>()
            var t = 0L
            val now = { t }
            val sleep = { d: Long -> slept += d; t += d }
            try { TerminalObservationBarrier(v).awaitAck({ null }, now, sleep, 51); throw AssertionError("expected timeout") }
            catch (e: TimeoutException) {}
            require(slept == listOf(50L, 1L)) { "expected [50,1] got $slept" }
        }
    }

    run {
        assertNoThrow("near-MAX timeout exhausts budget") {
            val start = Long.MAX_VALUE - 51
            var t = start
            val slept = mutableListOf<Long>()
            val now = { t }
            val sleep = { d: Long -> slept += d; t += d }
            try { TerminalObservationBarrier(v).awaitAck({ null }, now, sleep, 51); throw AssertionError("expected timeout") }
            catch (e: TimeoutException) {}
            require(slept == listOf(50L, 1L)) { "expected [50,1] got $slept" }
        }
    }
    run {
        val start = Long.MAX_VALUE - 5
        var t = start
        val now = { t }
        assertNoThrow("entry near Long.MAX_VALUE accepted") { TerminalObservationBarrier(v).awaitAck({ ack(v) }, now, {}) }
    }

    println()
    println("TOTAL TESTS: ${passed + failed}  PASSED: $passed  FAILED: $failed")
    if (failed > 0) throw RuntimeException("$failed test(s) failed")
}
