package io.ondeviceai.realtimesr

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSrAvailabilityTest {
    @Test
    fun missingGooglePlayServicesTakesPrecedence() {
        assertEquals(
            "MISSING_SERVICES",
            GoogleSrAvailabilityDecision.decide(false, false, false).state
        )
    }

    @Test
    fun unknownDeviceSupportRemainsUnverified() {
        assertEquals(
            "UNVERIFIED",
            GoogleSrAvailabilityDecision.decide(true, null, null).state
        )
    }

    @Test
    fun unsupportedDeviceIsReported() {
        assertEquals(
            "UNSUPPORTED",
            GoogleSrAvailabilityDecision.decide(true, false, null).state
        )
    }

    @Test
    fun uninstalledModuleIsNotReady() {
        assertEquals(
            "MODULE_NOT_READY",
            GoogleSrAvailabilityDecision.decide(true, true, false).state
        )
    }

    @Test
    fun installedModuleDoesNotProvePerEffectVideoOutput() {
        val result = GoogleSrAvailabilityDecision.decide(true, true, true)
        assertEquals("UNVERIFIED", result.state)
        assertEquals(
            "Device support and module installation are confirmed; per-effect video output is unverified.",
            result.detail
        )
    }
}
