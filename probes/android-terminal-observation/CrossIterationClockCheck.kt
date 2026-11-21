package io.ondeviceai.acceptance.terminal

fun main() {
    val binding = TerminalObservationBinding("clock-1", "a".repeat(64), 1234, "8", "b".repeat(64))
    val times = listOf(0L, 0L, 0L, 50L, 49L, 49L)
    var clockIndex = 0
    var reads = 0
    val sleeps = mutableListOf<Long>()
    var failure: Throwable? = null
    try {
        TerminalObservationBarrier(binding).awaitAck(
            readAck = { reads++; if (reads == 1) null else binding.ackBytes() },
            nowMillis = { times[clockIndex++] },
            sleepMillis = { sleeps += it },
            timeoutMillis = 100
        )
    } catch (error: Throwable) {
        failure = error
    }
    check(failure is IllegalStateException) {
        "clock regressed 50 -> 49 between iterations but ACK was accepted; error=$failure"
    }
    check(reads == 1) { "a second ACK read ran after clock regression: reads=$reads" }
    check(sleeps == listOf(50L))
    println("PASS cross-iteration clock regression rejects before ACK read")
}
