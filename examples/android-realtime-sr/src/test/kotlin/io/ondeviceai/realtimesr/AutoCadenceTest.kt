package io.ondeviceai.realtimesr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCadenceTest {
    @Test
    fun missingBackendEvidenceTriggersBackoffAndExpiresMonotonically() {
        val cadence = AutoCadence()
        cadence.reset(7L)
        repeat(3) { cadence.record(7L, true, null, null, 1_000_000_000L) }

        assertFalse(cadence.shouldTry(7L, 4_999_999_999L))
        assertTrue(cadence.shouldTry(7L, 5_000_000_000L))
    }

    @Test
    fun originalSubmissionsTriggerProgressivelyLongerBackoffs() {
        val cadence = AutoCadence()
        cadence.reset(1L)
        var now = 1_000_000_000L

        repeat(3) { cadence.record(1L, true, "ORIGINAL", null, now) }
        assertFalse(cadence.shouldTry(1L, now))
        now += 4_000_000_000L
        assertTrue(cadence.shouldTry(1L, now))

        repeat(3) { cadence.record(1L, true, "ORIGINAL", null, now) }
        assertFalse(cadence.shouldTry(1L, now))
        now += 8_000_000_000L
        assertTrue(cadence.shouldTry(1L, now))

        repeat(3) { cadence.record(1L, true, "ORIGINAL", null, now) }
        assertFalse(cadence.shouldTry(1L, now))
        now += 16_000_000_000L
        assertTrue(cadence.shouldTry(1L, now))
    }

    @Test
    fun staleGenerationAndSuppressedFramesDoNotChangeCadence() {
        val cadence = AutoCadence()
        cadence.reset(2L)
        repeat(5) { cadence.record(2L, false, "ORIGINAL", null, 0L) }
        repeat(5) { cadence.record(1L, true, "ORIGINAL", null, 0L) }

        assertTrue(cadence.shouldTry(2L, 0L))
        assertFalse(cadence.shouldTry(1L, 0L))
    }

    @Test
    fun lateEnhancedSubmissionIsAMissAndSuccessReleasesAStrike() {
        val cadence = AutoCadence()
        cadence.reset(3L)
        cadence.record(3L, true, "CUNNY", null, 0L, late = true)
        cadence.record(3L, true, "ORIGINAL", null, 0L)
        cadence.record(3L, true, "CUNNY", null, 0L)
        cadence.record(3L, true, "ORIGINAL", null, 0L)

        assertTrue(cadence.shouldTry(3L, 0L))
    }

    @Test
    fun resetChangesGenerationWithoutDiscardingAnActiveBackoff() {
        val cadence = AutoCadence()
        cadence.reset(4L)
        repeat(3) { cadence.record(4L, true, "ORIGINAL", null, 10L) }
        cadence.reset(5L)

        assertFalse(cadence.shouldTry(4L, 10L))
        assertFalse(cadence.shouldTry(5L, 10L))
        assertTrue(cadence.shouldTry(5L, 4_000_000_010L))
    }
}
