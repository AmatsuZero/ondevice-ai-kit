package io.ondeviceai.realtimesr

import io.ondeviceai.binding.RealtimeSRBackend
import org.junit.*
import org.junit.Assert.*

class PresentationPolicyTest {
    @Test
    fun presentationPolicyStatusTracking() {
        val p = PresentationPolicy()
        val g = p.select(PlaybackMode.CUNNY)
        assertNull(p.readStatus().presentedBackend)
        p.presented(g, 2L, RealtimeSRBackend.ORIGINAL, true)
        assertTrue(p.readStatus().fellBackToOriginal)
        p.presented(g, 3L, RealtimeSRBackend.CUNNY, true)
        assertEquals(RealtimeSRBackend.CUNNY, p.readStatus().presentedBackend)
        assertFalse(p.readStatus().fellBackToOriginal)
        p.presented(g, 1L, RealtimeSRBackend.ORIGINAL, true)
        assertEquals(RealtimeSRBackend.CUNNY, p.readStatus().presentedBackend)
        val off = p.select(PlaybackMode.ORIGINAL)
        p.presented(g, 8L, RealtimeSRBackend.CUNNY, true)
        assertNull(p.readStatus().presentedBackend)
        p.presented(off, 9L, RealtimeSRBackend.ORIGINAL, false)
        assertFalse(p.readStatus().fellBackToOriginal)
    }
    @Test
    fun applyEnabledTrueFromOriginalSwitchesToAutoAndGuardsCunny() {
        val policy = PresentationPolicy()
        var callbackMode: PlaybackMode? = null
        var callbackGeneration = -1L
        val host = AndroidRealtimeSRHost(policy, RendererSwitch { mode, generation ->
            callbackMode = mode
            callbackGeneration = generation
        })
        assertEquals(PlaybackMode.ORIGINAL, policy.mode)

        host.applyEnabled(true)
        assertEquals(PlaybackMode.AUTO, callbackMode)
        assertEquals(policy.generation, callbackGeneration)
        val status = host.readStatus()
        assertTrue(status.enabled)
        assertEquals(RealtimeSRBackend.CUNNY, status.preferredBackend)

        val generation = policy.generation
        assertFalse(host.onPresented(generation, 0, RealtimeSRBackend.CUNNY, false))
        assertTrue(host.onPresented(generation, 0, RealtimeSRBackend.CUNNY, true))
    }

    @Test
    fun applyEnabledFalseResetsToOriginalAndIgnoresStalePresentations() {
        val policy = PresentationPolicy()
        val modes = mutableListOf<PlaybackMode>()
        val host = AndroidRealtimeSRHost(policy, RendererSwitch { mode, _ -> modes.add(mode) })

        host.applyEnabled(true)
        val enabledGeneration = policy.generation
        host.applyEnabled(false)
        assertEquals(PlaybackMode.ORIGINAL, policy.mode)
        assertEquals(PlaybackMode.ORIGINAL, modes.last())
        val disabledGeneration = policy.generation
        assertNotEquals(enabledGeneration, disabledGeneration)

        assertFalse(host.onPresented(enabledGeneration, 0, RealtimeSRBackend.CUNNY, true))
        assertTrue(host.onPresented(disabledGeneration, 0, RealtimeSRBackend.ORIGINAL, true))
        assertFalse(host.readStatus().fellBackToOriginal)
    }

    @Test
    fun spatialModeTracksOnlyRequestedBackendAndIgnoresStalePresentations() {
        val policy = PresentationPolicy()
        val spatial = PlaybackMode.valueOf("SPATIAL")
        val originalGeneration = policy.select(PlaybackMode.ORIGINAL)
        policy.presented(originalGeneration, 0L, RealtimeSRBackend.SPATIAL, true)
        assertNull(policy.readStatus().presentedBackend)

        val generation = policy.select(spatial)
        assertEquals(RealtimeSRBackend.SPATIAL, policy.readStatus().preferredBackend)
        policy.presented(generation, 1L, RealtimeSRBackend.CUNNY, true)
        assertNull(policy.readStatus().presentedBackend)

        policy.presented(generation, 2L, RealtimeSRBackend.ORIGINAL, true)
        assertEquals(RealtimeSRBackend.ORIGINAL, policy.readStatus().presentedBackend)
        assertTrue(policy.readStatus().fellBackToOriginal)

        policy.presented(generation, 3L, RealtimeSRBackend.SPATIAL, true)
        assertEquals(RealtimeSRBackend.SPATIAL, policy.readStatus().presentedBackend)
        assertFalse(policy.readStatus().fellBackToOriginal)

        policy.select(PlaybackMode.ORIGINAL)
        policy.presented(generation, 4L, RealtimeSRBackend.SPATIAL, true)
        assertNull(policy.readStatus().presentedBackend)
    }
}
