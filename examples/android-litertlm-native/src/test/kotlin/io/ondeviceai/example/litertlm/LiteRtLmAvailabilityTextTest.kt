package io.ondeviceai.example.litertlm

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiteRtLmAvailabilityTextTest {
    @Test
    fun missingAssetsAreDisplayedAsAnExplicitStableState() {
        val text = LiteRtLmAvailabilityText.format(CapabilityAvailability(
            CapabilityStatus.REQUIRES_ASSETS,
            UnavailableReason.MISSING_ASSETS,
            backendId = "litertlm.android",
            minOS = "26",
        ))

        assertEquals(
            "Backend: litertlm.android\nStatus: REQUIRES_ASSETS\nReason: MISSING_ASSETS\nMinimum Android API: 26",
            text,
        )
        assertFalse(text.contains("download", ignoreCase = true))
        assertFalse(text.contains("generate", ignoreCase = true))
    }

    @Test
    fun unprovedRuntimeIsDisplayedAsUnsupported() {
        assertEquals(
            "Backend: litertlm.android\nStatus: UNAVAILABLE\nReason: UNSUPPORTED_DEVICE\nMinimum Android API: 26",
            LiteRtLmAvailabilityText.format(CapabilityAvailability(
                CapabilityStatus.UNAVAILABLE,
                UnavailableReason.UNSUPPORTED_DEVICE,
                backendId = "litertlm.android",
                minOS = "26",
            )),
        )
    }
}
