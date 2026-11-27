package io.ondeviceai.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealtimeVoiceSessionTest {

    private class FakePort : LiveVoicePort {
        val starts = mutableListOf<Triple<Long, LiveRole, LiveDestination>>()
        val stops = mutableListOf<Long>()
        var refreshes = 0

        override fun start(id: Long, role: LiveRole, destination: LiveDestination) {
            starts += Triple(id, role, destination)
        }

        override fun stop(id: Long) {
            stops += id
        }

        override fun refreshRoute() {
            refreshes++
        }
    }

    @Test
    fun unknownDestinationCannotStart() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        assertNull(session.destination)
        session.press()
        assertTrue(port.starts.isEmpty())
        assertEquals(RealtimeVoiceSession.LivePhase.IDLE, session.phase)
        session.route(null)
        assertNull(session.destination)
        session.press()
        assertTrue(port.starts.isEmpty())
    }

    @Test
    fun routeChangeWhileBusyReleasesOnceAndNeverRefreshes() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.route(LiveDestination.HEADPHONES)
        session.press()
        val id = port.starts.single().first
        session.ready(id)
        assertEquals(RealtimeVoiceSession.LivePhase.RUNNING, session.phase)
        session.route(LiveDestination.BUILTIN_SPEAKER)
        assertEquals(RealtimeVoiceSession.LivePhase.STOPPING, session.phase)
        assertEquals(1, port.stops.size)
        assertEquals(id, port.stops.single())
        assertEquals(0, port.refreshes)
        session.route(LiveDestination.BUILTIN_SPEAKER)
        assertEquals(1, port.stops.size)
        session.route(null)
        assertEquals(1, port.stops.size)
        session.ended(id, "路线变化，已结束")
        assertFalse(session.busy)
        assertNull(session.destination)
    }

    @Test
    fun routeNeverStartsSession() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.route(LiveDestination.HEADPHONES)
        assertTrue(port.starts.isEmpty())
        session.route(LiveDestination.BUILTIN_SPEAKER)
        assertTrue(port.starts.isEmpty())
        assertEquals(0, port.refreshes)
    }

    @Test
    fun staleReadyIsIgnored() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.route(LiveDestination.HEADPHONES)
        session.press()
        val first = port.starts.single().first
        session.release()
        session.ended(first, "stopped")
        session.press()
        val second = port.starts.last().first
        assertTrue(second > first)
        session.ready(first)
        assertEquals(RealtimeVoiceSession.LivePhase.STARTING, session.phase)
        session.ready(second)
        assertEquals(RealtimeVoiceSession.LivePhase.RUNNING, session.phase)
    }

    @Test
    fun reentryIsIgnored() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.route(LiveDestination.HEADPHONES)
        session.press()
        session.press()
        assertEquals(1, port.starts.size)
        session.release()
        session.release()
        assertEquals(1, port.stops.size)
    }

    @Test
    fun roleFrozenWhileHeld() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.route(LiveDestination.HEADPHONES)
        session.select(LiveRole.UNCLE)
        session.press()
        val id = port.starts.single().first
        session.ready(id)
        session.select(LiveRole.GIRL)
        assertEquals(LiveRole.UNCLE, session.role)
        assertEquals(LiveRole.UNCLE, port.starts.single().second)
        assertEquals(0, port.refreshes)
        session.release()
        session.ended(id, "done")
        session.select(LiveRole.GIRL)
        assertEquals(LiveRole.GIRL, session.role)
    }

    @Test
    fun stopAckUsesCurrentIdOnly() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.route(LiveDestination.HEADPHONES)
        session.press()
        val first = port.starts.last().first
        session.release()
        session.ended(first, "stopped")
        session.press()
        val second = port.starts.last().first
        session.ended(first, "stale")
        assertTrue(session.busy)
        session.ready(second)
        session.ended(second, "未获得麦克风权限")
        assertFalse(session.busy)
        assertEquals("未获得麦克风权限", session.message)
    }

    @Test
    fun retainsSettingsAcrossSessions() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.select(LiveRole.GIRL)
        session.route(LiveDestination.BUILTIN_SPEAKER)
        session.press()
        val id = port.starts.single().first
        session.ready(id)
        session.release()
        session.ended(id, "done")
        assertFalse(session.busy)
        assertEquals(LiveRole.GIRL, session.role)
        assertEquals(LiveDestination.BUILTIN_SPEAKER, session.destination)
        assertEquals("done", session.message)
        session.press()
        assertEquals(LiveRole.GIRL, port.starts.last().second)
        assertEquals(LiveDestination.BUILTIN_SPEAKER, port.starts.last().third)
    }

    @Test
    fun refreshDelegatesToPort() {
        val port = FakePort()
        val session = RealtimeVoiceSession(port)
        session.refresh()
        assertEquals(1, port.refreshes)
    }
}
