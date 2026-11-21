package io.ondeviceai.systemtts

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TtsTaskGateTest {
    @Test
    fun cancelledTaskReleasesAdmissionAndIgnoresLateCompletion() {
        val gate = TtsTaskGate()
        assertTrue(gate.begin("first"))

        var stopCalls = 0
        assertTrue(gate.cancel("first") { stopCalls++ })
        assertTrue(gate.begin("second"))

        assertFalse(gate.finish("first"))
        assertTrue(gate.finish("second"))
        assertFalse(gate.finish("second"))
        assertEquals(1, stopCalls)
    }

    @Test
    fun stopRunsOnceAndNormalCompletionDoesNotStop() {
        val gate = TtsTaskGate()
        var stopCalls = 0
        assertTrue(gate.begin("first"))
        assertFalse(gate.begin("second"))
        assertTrue(gate.cancel("first") { stopCalls++ })
        assertFalse(gate.cancel("first") { stopCalls++ })
        assertEquals(1, stopCalls)

        assertTrue(gate.begin("second"))
        assertTrue(gate.finish("second"))
        assertFalse(gate.cancel("second") { stopCalls++ })
        assertEquals(1, stopCalls)
    }

    @Test
    fun cleanupRunsWhenPlatformStopFails() {
        var cleared = false
        assertFailsWith<IllegalStateException> {
            stopAndClear({ error("stop failed") }) { cleared = true }
        }
        assertTrue(cleared)
    }
}
