package io.ondeviceai.realtimesr

import org.junit.Assert.*
import org.junit.Test

class FrameBindingTest {

    private fun id(generation: Long, loop: Int = 0, sourceOrdinal: Int = 0): FrameId =
        FrameId(generation = generation, loop = loop, sourceOrdinal = sourceOrdinal)

    @Test
    fun offerStoresPendingWhenGenerationMatches() {
        val binding = FrameBinding()
        binding.reset(1L)

        val frame = id(generation = 1L, loop = 2, sourceOrdinal = 3)
        assertTrue(binding.offer(frame, 1_000L))
        assertEquals(frame, binding.expire())
        assertNull(binding.expire())
    }

    @Test
    fun offerRejectsWhenGenerationDoesNotMatch() {
        val binding = FrameBinding()
        binding.reset(1L)

        assertFalse(binding.offer(id(generation = 2L), 1_000L))
        assertNull(binding.expire())
    }

    @Test
    fun onlyOneFrameCanBePendingAtATime() {
        val binding = FrameBinding()
        binding.reset(1L)

        val first = id(generation = 1L, loop = 0)
        val second = id(generation = 1L, loop = 1)
        assertTrue(binding.offer(first, 100L))
        assertFalse(binding.offer(second, 200L))
        assertEquals(first, binding.expire())
    }

    @Test
    fun confirmConsumesPendingOnMatchingTimestamp() {
        val binding = FrameBinding()
        binding.reset(7L)

        val frame = id(generation = 7L, loop = 4, sourceOrdinal = 1)
        val ptsUs = 12_345L
        assertTrue(binding.offer(frame, ptsUs))

        assertEquals(frame, binding.confirm(7L, ptsUs * 1_000L))
        assertNull(binding.expire())
        assertNull(binding.confirm(7L, ptsUs * 1_000L))
    }

    @Test
    fun confirmDiscardsPendingOnTimestampMismatch() {
        val binding = FrameBinding()
        binding.reset(7L)

        val frame = id(generation = 7L, loop = 4, sourceOrdinal = 1)
        assertTrue(binding.offer(frame, 12_345L))

        assertNull(binding.confirm(7L, 999_999_000L))
        assertNull(binding.expire())
    }

    @Test
    fun staleGenerationConfirmDoesNotClearNewPending() {
        val binding = FrameBinding()
        binding.reset(1L)
        assertTrue(binding.offer(id(generation = 1L), 100L))

        binding.reset(2L)
        val fresh = id(generation = 2L, loop = 5, sourceOrdinal = 2)
        val ptsUs = 200L
        assertTrue(binding.offer(fresh, ptsUs))

        assertNull(binding.confirm(1L, ptsUs * 1_000L))
        assertEquals(fresh, binding.confirm(2L, ptsUs * 1_000L))
    }

    @Test
    fun expireReturnsPendingAndClearsIt() {
        val binding = FrameBinding()
        binding.reset(3L)

        val frame = id(generation = 3L, loop = 1)
        assertTrue(binding.offer(frame, 500L))

        assertEquals(frame, binding.expire())
        assertNull(binding.expire())
        assertNull(binding.confirm(3L, 500L * 1_000L))
    }

    @Test
    fun resetClearsPendingAndAllowsNewGeneration() {
        val binding = FrameBinding()
        binding.reset(1L)
        assertTrue(binding.offer(id(generation = 1L), 10L))

        binding.reset(2L)
        assertNull(binding.expire())

        val fresh = id(generation = 2L, loop = 0)
        assertTrue(binding.offer(fresh, 20L))
        assertEquals(fresh, binding.expire())
    }

    @Test
    fun duplicatePtsOffersRemainDistinctAcrossOffers() {
        val binding = FrameBinding()
        binding.reset(1L)

        val ptsUs = 100L
        val first = id(generation = 1L, loop = 0)
        val second = id(generation = 1L, loop = 1)

        assertTrue(binding.offer(first, ptsUs))
        assertEquals(first, binding.confirm(1L, ptsUs * 1_000L))

        assertTrue(binding.offer(second, ptsUs))
        assertEquals(second, binding.confirm(1L, ptsUs * 1_000L))
    }
}
