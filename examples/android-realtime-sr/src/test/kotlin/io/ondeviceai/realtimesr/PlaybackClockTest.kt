package io.ondeviceai.realtimesr

import org.junit.Assert.*
import org.junit.Test

class PlaybackClockTest {

    private val sampleRate = 48_000

    @Test
    fun initiallyUnanchored() {
        val clock = PlaybackClock(sampleRate)
        assertNull(clock.targetNs(0L))
        assertNull(clock.targetNs(1_000_000L))
        assertNull(clock.positionUs())
    }

    @Test
    fun firstObserveAnchorsAndOneSecondMapsExactly() {
        val clock = PlaybackClock(sampleRate)
        val now = 10_000_000_000L

        clock.observe(head32 = 0, mediaAnchorUs = 1_000_000L, nowNs = now)

        assertEquals(1_000_000L, clock.positionUs()!!)
        assertEquals(now, clock.targetNs(1_000_000L)!!)
        // 1 s of media = 1_000_000 us = 1_000_000_000 ns
        assertEquals(now + 1_000_000_000L, clock.targetNs(2_000_000L)!!)
    }

    @Test
    fun subsequentObserveUsesPlayedHeadDelta() {
        val clock = PlaybackClock(sampleRate)
        val now = 10_000_000_000L

        clock.observe(head32 = 0, mediaAnchorUs = 1_000_000L, nowNs = now)
        // 48_000 samples at 48 kHz == 1 s of audio actually played
        clock.observe(head32 = 48_000, mediaAnchorUs = 2_000_000L, nowNs = now + 1_000_000_000L)

        assertEquals(2_000_000L, clock.positionUs()!!)
        assertEquals(now + 1_000_000_000L, clock.targetNs(2_000_000L)!!)
        assertEquals(now + 2_000_000_000L, clock.targetNs(3_000_000L)!!)
    }

    @Test
    fun pauseFreezesMappingAndIgnoresObservations() {
        val clock = PlaybackClock(sampleRate)
        val now = 10_000_000_000L

        clock.observe(head32 = 0, mediaAnchorUs = 1_000_000L, nowNs = now)
        val frozenTarget = clock.targetNs(2_000_000L)!!
        val frozenPosition = clock.positionUs()!!

        clock.pause()

        // This observation must be ignored entirely while paused.
        clock.observe(head32 = 48_000, mediaAnchorUs = 2_000_000L, nowNs = now + 500_000_000L)

        assertEquals(frozenPosition, clock.positionUs()!!)
        assertEquals(frozenTarget, clock.targetNs(2_000_000L)!!)
        assertEquals(now + 1_000_000_000L, clock.targetNs(2_000_000L)!!)
    }

    @Test
    fun resetInvalidatesAndUnpausesForSeek() {
        val clock = PlaybackClock(sampleRate)
        val now = 10_000_000_000L

        clock.observe(head32 = 0, mediaAnchorUs = 1_000_000L, nowNs = now)
        clock.pause()
        clock.reset()

        assertNull(clock.targetNs(1_000_000L))
        assertNull(clock.positionUs())

        val seekNow = now + 5_000_000_000L
        clock.observe(head32 = 7_000, mediaAnchorUs = 50_000_000L, nowNs = seekNow)

        assertEquals(50_000_000L, clock.positionUs()!!)
        assertEquals(seekNow, clock.targetNs(50_000_000L)!!)
        assertEquals(seekNow + 1_000_000_000L, clock.targetNs(51_000_000L)!!)
    }

    @Test
    fun unsignedHeadWrapAdvancesOneMillisecond() {
        val clock = PlaybackClock(sampleRate)
        val now = 10_000_000_000L
        val wrappedHead = 0xfffffff0.toInt()

        clock.observe(head32 = wrappedHead, mediaAnchorUs = 1_000_000L, nowNs = now)
        // head wraps: 0xfffffff0 -> 0x20 == 48 samples == 1 ms at 48 kHz
        clock.observe(head32 = 0x20, mediaAnchorUs = 1_001_000L, nowNs = now + 1_000_000L)

        assertEquals(1_001_000L, clock.positionUs()!!)
        assertEquals(now + 1_000_000L, clock.targetNs(1_001_000L)!!)
    }
}
