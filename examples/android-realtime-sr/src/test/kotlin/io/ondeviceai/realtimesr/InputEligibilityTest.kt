package io.ondeviceai.realtimesr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputEligibilityTest {
    @Test
    fun acceptsBt709SdrAtKnownThirtyFpsWithin1080p() {
        val result = InputEligibility.evaluate(1920, 1080, 30, 1, 3)

        assertTrue(result.eligible)
        assertEquals(null, result.reason)
    }

    @Test
    fun infersSdrForBt709WhenTransferIsUnspecified() {
        val result = InputEligibility.evaluate(1280, 720, 30, 1, 0)

        assertTrue(result.eligible)
        assertTrue(result.reason.orEmpty().contains("SDR inferred"))
    }

    @Test
    fun infersBt709OnlyFor1080pInput() {
        val accepted = InputEligibility.evaluate(1920, 1080, 30, 0, 3)
        val rejected = InputEligibility.evaluate(1280, 720, 30, 0, 3)

        assertTrue(accepted.eligible)
        assertTrue(accepted.reason.orEmpty().contains("BT.709 inferred"))
        assertFalse(rejected.eligible)
        assertTrue(rejected.reason.orEmpty().contains("1920x1080 input"))
    }

    @Test
    fun rejectsHdrTransfers() {
        val st2084 = InputEligibility.evaluate(1920, 1080, 30, 1, 6)
        val hlg = InputEligibility.evaluate(1920, 1080, 30, 1, 7)

        assertFalse(st2084.eligible)
        assertTrue(st2084.reason.orEmpty().contains("ST2084"))
        assertFalse(hlg.eligible)
        assertTrue(hlg.reason.orEmpty().contains("HLG"))
    }

    @Test
    fun rejectsNonBt709Standards() {
        val result = InputEligibility.evaluate(1920, 1080, 30, 2, 3)

        assertFalse(result.eligible)
        assertTrue(result.reason.orEmpty().contains("color standard (2)"))
    }

    @Test
    fun rejectsUnsupportedFrameRatesAndDimensions() {
        assertFalse(InputEligibility.evaluate(1280, 720, 29, 1, 3).eligible)
        assertFalse(InputEligibility.evaluate(1280, 720, 31, 1, 3).eligible)
        assertFalse(InputEligibility.evaluate(1920, 1081, 30, 1, 3).eligible)
        assertFalse(InputEligibility.evaluate(0, 720, 30, 1, 3).eligible)
    }

    @Test
    fun rejectsOtherExplicitTransferValues() {
        val result = InputEligibility.evaluate(1920, 1080, 30, 1, 4)

        assertFalse(result.eligible)
        assertTrue(result.reason.orEmpty().contains("color transfer (4)"))
    }
}
