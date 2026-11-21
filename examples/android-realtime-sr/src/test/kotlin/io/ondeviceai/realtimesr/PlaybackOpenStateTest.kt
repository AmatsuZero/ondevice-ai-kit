package io.ondeviceai.realtimesr

import io.ondeviceai.binding.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackOpenStateTest {
    @Test fun directFallbackPreservesRequestWithoutInventingPresentation() {
        val state = PlaybackOpenState()
        assertTrue(state.opened(state.begin(), direct = true))
        val pending = RealtimeSRStatus(true, RealtimeSRBackend.CUNNY, null, false)
        for (mode in listOf(RealtimeSRMode.AUTO, RealtimeSRMode.CUNNY, RealtimeSRMode.SPATIAL)) {
            val actual = state.status(mode, pending)
            assertTrue("$mode must report direct-original fallback", actual.fellBackToOriginal)
            assertNull(actual.presentedBackend)
            assertEquals(RealtimeSRBackend.CUNNY, actual.preferredBackend)
        }
        assertFalse(state.status(RealtimeSRMode.ORIGINAL, pending).fellBackToOriginal)
    }

    @Test fun onlyCurrentSuccessfulOpenClearsPreviousFailure() {
        val state = PlaybackOpenState()
        val failed = state.begin()
        assertTrue(state.failed(failed, "decoder failed"))
        val retry = state.begin()
        assertEquals("decoder failed", state.error)
        assertFalse(state.opened(failed, direct = true))
        assertEquals("decoder failed", state.error)
        assertTrue(state.opened(retry, direct = false))
        assertNull(state.error)
        assertFalse(state.failed(failed, "late failure"))
        assertNull(state.error)
    }

    @Test fun reopeningOrLeavingInvalidatesDirectRouteAndLateResults() {
        val state = PlaybackOpenState()
        val first = state.begin()
        state.opened(first, direct = true)
        val pending = RealtimeSRStatus(true, RealtimeSRBackend.SPATIAL, null, false)
        val second = state.begin()
        assertFalse(state.status(RealtimeSRMode.SPATIAL, pending).fellBackToOriginal)
        state.failed(second, "failure")
        state.reset()
        assertFalse(state.opened(second, direct = true))
        assertFalse(state.failed(second, "late error"))
        assertNull(state.error)
        assertFalse(state.status(RealtimeSRMode.SPATIAL, pending).fellBackToOriginal)
    }

    @Test fun rendererRouteRetainsRealFrameFallbackAndPresentation() {
        val state = PlaybackOpenState()
        state.opened(state.begin(), direct = false)
        val confirmed = RealtimeSRStatus(true, RealtimeSRBackend.SPATIAL, RealtimeSRBackend.ORIGINAL, true)
        assertEquals(confirmed, state.status(RealtimeSRMode.SPATIAL, confirmed))
    }
}
