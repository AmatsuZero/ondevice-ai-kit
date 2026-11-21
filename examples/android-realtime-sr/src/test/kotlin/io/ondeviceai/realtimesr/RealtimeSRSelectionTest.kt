package io.ondeviceai.realtimesr

import io.ondeviceai.binding.*
import org.junit.Assert.*
import org.junit.Test

class RealtimeSRSelectionTest {
    @Test
    fun productionHostSelectionTracksPresentedFramesAndFallbacks() {
        val switches = mutableListOf<Pair<PlaybackMode, Long>>()
        val host = AndroidRealtimeSRHost(rendererSwitch = RendererSwitch { mode, generation ->
            switches += mode to generation
        }).apply {
            isModeAvailable = { it == PlaybackMode.AUTO || it == PlaybackMode.CUNNY || it == PlaybackMode.SPATIAL }
        }
        val controller = RealtimeVideoSR(host)

        assertEquals(RealtimeSRMode.ORIGINAL, controller.selectedMode())
        assertTrue(controller.selectMode(RealtimeSRMode.SPATIAL))
        val spatialGeneration = switches.last().second
        assertNull(controller.status().presentedBackend)
        assertTrue(host.onPresented(spatialGeneration, 0, RealtimeSRBackend.SPATIAL, true))
        assertEquals(RealtimeSRBackend.SPATIAL, controller.status().presentedBackend)

        assertTrue(host.onPresented(spatialGeneration, 1, RealtimeSRBackend.ORIGINAL, true))
        assertEquals(RealtimeSRBackend.ORIGINAL, controller.status().presentedBackend)
        assertTrue(controller.status().fellBackToOriginal)

        assertTrue(controller.selectMode(RealtimeSRMode.CUNNY))
        val cunnyGeneration = switches.last().second
        assertFalse(host.onPresented(spatialGeneration, 2, RealtimeSRBackend.SPATIAL, true))
        assertNull(controller.status().presentedBackend)
        assertTrue(host.onPresented(cunnyGeneration, 0, RealtimeSRBackend.CUNNY, true))

        val statusBeforeUnsupported = controller.status()
        val switchesBeforeUnsupported = switches.size
        assertFalse(controller.selectMode(RealtimeSRMode.SYSTEM))
        assertFalse(controller.selectMode(RealtimeSRMode.LITERT))
        assertEquals(RealtimeSRMode.CUNNY, controller.selectedMode())
        assertEquals(statusBeforeUnsupported, controller.status())
        assertEquals(switchesBeforeUnsupported, switches.size)

        assertTrue(controller.selectMode(RealtimeSRMode.CUNNY))
        assertEquals(cunnyGeneration, switches.last().second)
        assertEquals(RealtimeSRBackend.CUNNY, controller.status().presentedBackend)

        assertTrue(controller.selectMode(RealtimeSRMode.AUTO))
        assertEquals(RealtimeSRMode.AUTO, controller.selectedMode())
        assertEquals(RealtimeSRBackend.CUNNY, controller.status().preferredBackend)
        assertNull(controller.status().presentedBackend)

        assertTrue(controller.selectMode(RealtimeSRMode.ORIGINAL))
        assertFalse(controller.status().enabled)
        assertEquals(RealtimeSRBackend.ORIGINAL, controller.status().preferredBackend)
    }

    @Test
    fun availabilityIsFilteredPerHostAndUnwiredHostOnlySupportsOriginal() {
        var spatialAvailable = true
        val switches = mutableListOf<Pair<PlaybackMode, Long>>()
        val host = AndroidRealtimeSRHost(rendererSwitch = RendererSwitch { mode, generation ->
            switches += mode to generation
        }).apply {
            isModeAvailable = {
                it == PlaybackMode.AUTO || it == PlaybackMode.CUNNY ||
                    (it == PlaybackMode.SPATIAL && spatialAvailable)
            }
        }
        val controller = RealtimeVideoSR(host)

        assertTrue(controller.supportedModes().contains(RealtimeSRMode.SPATIAL))
        assertTrue(controller.selectMode(RealtimeSRMode.SPATIAL))
        spatialAvailable = false
        val statusBeforeRefusal = controller.status()
        val switchesBeforeRefusal = switches.size

        assertFalse(controller.supportedModes().contains(RealtimeSRMode.SPATIAL))
        assertFalse(controller.selectMode(RealtimeSRMode.SPATIAL))
        assertEquals(statusBeforeRefusal, controller.status())
        assertEquals(switchesBeforeRefusal, switches.size)

        val unwired = RealtimeVideoSR(AndroidRealtimeSRHost())
        assertEquals(listOf(RealtimeSRMode.ORIGINAL), unwired.supportedModes())
    }

    @Test
    fun compatibilityEnableRespectsRendererAndModeAvailability() {
        val unwired = AndroidRealtimeSRHost()
        unwired.applyEnabled(true)
        assertFalse(unwired.readStatus().enabled)
        assertEquals(RealtimeSRMode.ORIGINAL, unwired.selectedMode())
        assertEquals(listOf(RealtimeSRMode.ORIGINAL), unwired.supportedModes())

        val switches = mutableListOf<Pair<PlaybackMode, Long>>()
        val host = AndroidRealtimeSRHost(rendererSwitch = RendererSwitch { mode, generation ->
            switches += mode to generation
        }).apply {
            isModeAvailable = { it == PlaybackMode.SPATIAL }
        }

        host.applyEnabled(true)
        assertFalse(host.readStatus().enabled)
        assertEquals(RealtimeSRMode.ORIGINAL, host.selectedMode())
        assertTrue(switches.isEmpty())
        assertEquals(
            listOf(RealtimeSRMode.ORIGINAL, RealtimeSRMode.SPATIAL),
            host.supportedModes()
        )

        assertTrue(host.selectMode(RealtimeSRMode.SPATIAL))
        host.applyEnabled(true)
        assertEquals(RealtimeSRMode.SPATIAL, host.selectedMode())
        assertEquals(PlaybackMode.SPATIAL, switches.last().first)
    }

    @Test
    fun legacyHostRetainsBooleanToggleAndOnlyAdvertisesOriginal() {
        val host = LegacyHost()
        val controller = RealtimeVideoSR(host)

        assertEquals(listOf(RealtimeSRMode.ORIGINAL), controller.supportedModes())
        assertEquals(RealtimeSRMode.ORIGINAL, controller.selectedMode())
        assertFalse(controller.selectMode(RealtimeSRMode.SPATIAL))

        controller.setEnabled(true)
        assertTrue(controller.status().enabled)
        assertNull(controller.selectedMode())
        assertTrue(controller.selectMode(RealtimeSRMode.ORIGINAL))
        assertFalse(controller.status().enabled)
        assertEquals(RealtimeSRMode.ORIGINAL, controller.selectedMode())
        assertFalse(controller.selectMode(RealtimeSRMode.SPATIAL))
        assertFalse(controller.status().enabled)
        controller.setEnabled(false)
        assertFalse(controller.status().enabled)
        assertEquals(RealtimeSRMode.ORIGINAL, controller.selectedMode())
    }

    @Test
    fun customHostCanSelectLitertAndRefuseWithoutChangingState() {
        val host = LitertHost()
        val controller = RealtimeVideoSR(host)

        assertTrue(controller.supportedModes().contains(RealtimeSRMode.LITERT))
        assertTrue(controller.selectMode(RealtimeSRMode.LITERT))
        assertEquals(RealtimeSRMode.LITERT, controller.selectedMode())
        assertTrue(controller.status().enabled)
        assertEquals(RealtimeSRBackend.LITERT, controller.status().preferredBackend)
        assertNull(controller.status().presentedBackend)
        host.notifyPresented()
        assertEquals(RealtimeSRBackend.LITERT, controller.status().presentedBackend)

        host.acceptSelections = false
        val selectedBeforeRefusal = controller.selectedMode()
        val statusBeforeRefusal = controller.status()
        assertFalse(controller.selectMode(RealtimeSRMode.CUNNY))
        assertEquals(selectedBeforeRefusal, controller.selectedMode())
        assertEquals(statusBeforeRefusal, controller.status())
    }

    private class LegacyHost : RealtimeSRHost {
        private var status = RealtimeSRStatus(false, RealtimeSRBackend.ORIGINAL, null, false)

        override fun applyEnabled(enabled: Boolean) {
            status = status.copy(enabled = enabled)
        }

        override fun readStatus(): RealtimeSRStatus = status
    }

    private class LitertHost : SelectableRealtimeSRHost {
        private var selected = RealtimeSRMode.ORIGINAL
        private var status = RealtimeSRStatus(false, RealtimeSRBackend.ORIGINAL, null, false)
        var acceptSelections = true

        override fun supportedModes(): List<RealtimeSRMode> =
            listOf(RealtimeSRMode.ORIGINAL, RealtimeSRMode.CUNNY, RealtimeSRMode.LITERT)

        override fun selectedMode(): RealtimeSRMode = selected

        override fun selectMode(mode: RealtimeSRMode): Boolean {
            if (!acceptSelections || mode !in supportedModes()) return false
            selected = mode
            val preferredBackend = when (mode) {
                RealtimeSRMode.ORIGINAL -> RealtimeSRBackend.ORIGINAL
                RealtimeSRMode.CUNNY -> RealtimeSRBackend.CUNNY
                RealtimeSRMode.LITERT -> RealtimeSRBackend.LITERT
                else -> return false
            }
            status = status.copy(
                enabled = mode != RealtimeSRMode.ORIGINAL,
                preferredBackend = preferredBackend,
                presentedBackend = null
            )
            return true
        }

        fun notifyPresented() {
            val presentedBackend = when (selected) {
                RealtimeSRMode.ORIGINAL -> RealtimeSRBackend.ORIGINAL
                RealtimeSRMode.CUNNY -> RealtimeSRBackend.CUNNY
                RealtimeSRMode.LITERT -> RealtimeSRBackend.LITERT
                else -> return
            }
            status = status.copy(presentedBackend = presentedBackend)
        }

        override fun applyEnabled(enabled: Boolean) {
            status = status.copy(enabled = enabled)
        }

        override fun readStatus(): RealtimeSRStatus = status
    }
}
