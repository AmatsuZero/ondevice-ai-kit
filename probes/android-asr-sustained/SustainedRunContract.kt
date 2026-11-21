package io.ondeviceai.demo

/** Evidence required for one paced create/transcribe/close cycle. No resource budget. */
data class SustainedCycleEvidence(
    val index: Int,
    val startedNs: Long,
    val endedNs: Long,
    val measuredSuccesses: Int,
    val closeSucceeded: Boolean,
    val inputUnchanged: Boolean
)

fun sustainedRunComplete(
    cycles: List<SustainedCycleEvidence>, warmups: Int, attempts: Int, successes: Int,
    elapsedNs: Long, samplerHealthy: Boolean, lifecycleHealthy: Boolean,
    teardownSucceeded: Boolean, persistenceHealthy: Boolean
): Boolean =
    cycles.size == 20 && warmups == 1 && attempts == 40 && successes == 40 &&
        elapsedNs >= 600_000_000_000L && samplerHealthy && lifecycleHealthy &&
        teardownSucceeded && persistenceHealthy && cycles.withIndex().all { (i, c) ->
            c.index == i + 1 && c.startedNs >= 0 &&
                c.endedNs - c.startedNs >= 30_000_000_000L &&
                (i == 0 || c.startedNs >= cycles[i - 1].endedNs) &&
                c.measuredSuccesses == 2 && c.closeSucceeded && c.inputUnchanged
        }
