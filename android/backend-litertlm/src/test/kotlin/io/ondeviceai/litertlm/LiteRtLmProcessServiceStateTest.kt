package io.ondeviceai.litertlm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class LiteRtLmProcessServiceStateTest {
    @Test
    fun reservationIsSingleUseAndRejectsInvalidNonces(): Unit {
        val state = LiteRtLmProcessServiceState()

        assertFalse(state.reserve(""))
        assertFalse(state.reserve("has space"))
        assertFalse(state.reserve("non-ascii-é"))
        assertFalse(state.reserve("n".repeat(129)))
        assertTrue(state.reserve("valid_nonce-1"))
        assertFalse(state.reserve("valid_nonce-1"))
        assertFalse(state.reserve("another-owner"))
    }

    @Test
    fun staleNonceCannotReadOrMutateTheCurrentOwner(): Unit {
        val state = LiteRtLmProcessServiceState()
        assertTrue(state.reserve("current"))
        val before = state.snapshot("current")

        assertFalse(state.begin("stale"))
        assertFalse(state.markNativeAttempted("stale"))
        assertFalse(state.markNativeInitialized("stale"))
        assertFalse(state.beginGeneration("stale"))
        assertFalse(state.append("stale", "ignored"))
        assertFalse(state.complete("stale"))
        assertFalse(state.fail("stale", "EngineFailure"))
        assertFalse(state.cancel("stale"))
        assertNull(state.snapshot("stale"))
        assertEquals(before, state.snapshot("current"))
    }

    @Test
    fun beginAndNativeLifecycleGatesAreOneWay(): Unit {
        val state = LiteRtLmProcessServiceState()

        assertFalse(state.begin("owner"))
        assertFalse(state.markNativeAttempted("owner"))
        assertFalse(state.markNativeInitialized("owner"))
        assertFalse(state.beginGeneration("owner"))
        assertTrue(state.reserve("owner"))
        assertFalse(state.markNativeAttempted("owner"))
        assertTrue(state.begin("owner"))
        assertFalse(state.begin("owner"))
        assertFalse(state.markNativeInitialized("owner"))
        assertFalse(state.beginGeneration("owner"))
        assertTrue(state.markNativeAttempted("owner"))
        assertFalse(state.markNativeAttempted("owner"))
        assertTrue(state.markNativeInitialized("owner"))
        assertFalse(state.markNativeInitialized("owner"))
        assertTrue(state.beginGeneration("owner"))
        assertFalse(state.beginGeneration("owner"))

        val snapshot = state.snapshot("owner")
        assertEquals(LiteRtLmServicePhase.GENERATING, snapshot?.phase)
        assertTrue(snapshot?.nativeAttempted == true)
        assertTrue(snapshot?.nativeInitialized == true)
    }

    @Test
    fun initializationRecordedAfterCancellationDoesNotStartGeneration(): Unit {
        val state = LiteRtLmProcessServiceState()
        assertTrue(state.reserve("owner"))
        assertTrue(state.begin("owner"))
        assertTrue(state.markNativeAttempted("owner"))
        assertTrue(state.cancel("owner"))

        assertTrue(state.markNativeInitialized("owner"))
        assertFalse(state.beginGeneration("owner"))
        val snapshot = state.snapshot("owner")
        assertEquals(LiteRtLmServicePhase.TERMINAL, snapshot?.phase)
        assertEquals(LiteRtLmGenerationTerminal.CANCELED, snapshot?.terminal)
        assertTrue(snapshot?.nativeInitialized == true)
    }

    @Test
    fun rawChunksPreserveWhitespaceAndPunctuationExactly(): Unit {
        val state = generatingState("owner")
        val chunks = listOf(" \t", "Hello,  world!\n", "!?.,")

        chunks.forEach { assertTrue(state.append("owner", it)) }

        assertEquals(chunks, state.snapshot("owner")?.rawChunks)
    }

    @Test
    fun chunkCountOverflowTerminatesWithoutRetainingTheExtraChunk(): Unit {
        val state = generatingState(
            "owner",
            maxSnapshots = 1,
            maxTotalUtf8Bytes = 8,
            maxChunkUtf8Bytes = 8,
        )
        assertTrue(state.append("owner", "é"))

        assertFalse(state.append("owner", "!"))
        val snapshot = state.snapshot("owner")
        assertEquals(listOf("é"), snapshot?.rawChunks)
        assertEquals(LiteRtLmGenerationTerminal.ERROR, snapshot?.terminal)
        assertEquals("OutputOverflow", snapshot?.errorCode)
    }

    @Test
    fun perChunkUtf8OverflowCountsMultibyteText(): Unit {
        val state = generatingState(
            "owner",
            maxSnapshots = 3,
            maxTotalUtf8Bytes = 8,
            maxChunkUtf8Bytes = 2,
        )
        assertTrue(state.append("owner", "é"))

        assertFalse(state.append("owner", "€"))
        val snapshot = state.snapshot("owner")
        assertEquals(listOf("é"), snapshot?.rawChunks)
        assertEquals(LiteRtLmGenerationTerminal.ERROR, snapshot?.terminal)
        assertEquals("OutputOverflow", snapshot?.errorCode)
    }

    @Test
    fun totalUtf8OverflowCountsMultibyteTextAcrossChunks(): Unit {
        val state = generatingState(
            "owner",
            maxSnapshots = 3,
            maxTotalUtf8Bytes = 3,
            maxChunkUtf8Bytes = 3,
        )
        assertTrue(state.append("owner", "é"))

        assertFalse(state.append("owner", "é"))
        val snapshot = state.snapshot("owner")
        assertEquals(listOf("é"), snapshot?.rawChunks)
        assertEquals(LiteRtLmGenerationTerminal.ERROR, snapshot?.terminal)
        assertEquals("OutputOverflow", snapshot?.errorCode)
    }

    @Test
    fun emptyAndWhitespaceOnlyOutputCompletesAsNoOutput(): Unit {
        val state = generatingState("owner")

        assertFalse(state.append("owner", ""))
        assertTrue(state.append("owner", " \t\n"))
        assertTrue(state.complete("owner"))
        val snapshot = state.snapshot("owner")
        assertEquals(listOf(" \t\n"), snapshot?.rawChunks)
        assertEquals(LiteRtLmGenerationTerminal.ERROR, snapshot?.terminal)
        assertEquals("NoOutput", snapshot?.errorCode)
        assertFalse(state.complete("owner"))
    }

    @Test
    fun terminalCallbacksCannotPromoteOrChangeTheFirstTerminal(): Unit {
        val succeeded = generatingState("success-owner")
        assertTrue(succeeded.append("success-owner", "answer"))
        assertTrue(succeeded.complete("success-owner"))
        val successSnapshot = succeeded.snapshot("success-owner")

        assertFalse(succeeded.fail("success-owner", "LateFailure"))
        assertTrue(succeeded.cancel("success-owner"))
        assertFalse(succeeded.append("success-owner", "late"))
        assertFalse(succeeded.complete("success-owner"))
        assertEquals(successSnapshot, succeeded.snapshot("success-owner"))
        assertEquals(LiteRtLmGenerationTerminal.SUCCEEDED, successSnapshot?.terminal)

        val failed = generatingState("failure-owner")
        assertTrue(failed.fail("failure-owner", "EngineFailure"))
        assertFalse(failed.complete("failure-owner"))
        assertFalse(failed.fail("failure-owner", "DifferentFailure"))
        assertTrue(failed.cancel("failure-owner"))
        assertFalse(failed.append("failure-owner", "late"))
        val failureSnapshot = failed.snapshot("failure-owner")
        assertEquals(LiteRtLmGenerationTerminal.ERROR, failureSnapshot?.terminal)
        assertEquals("EngineFailure", failureSnapshot?.errorCode)
    }

    @Test
    fun unsafeFailureCodeUsesRuntimeFailure(): Unit {
        val state = generatingState("owner")

        assertTrue(state.fail("owner", "not-safe!"))

        val snapshot = state.snapshot("owner")
        assertEquals(LiteRtLmGenerationTerminal.ERROR, snapshot?.terminal)
        assertEquals("RuntimeFailure", snapshot?.errorCode)
    }

    @Test
    fun canceledAndSuccessfulOwnersRemainReserved(): Unit {
        val canceled = LiteRtLmProcessServiceState()
        assertTrue(canceled.reserve("canceled-owner"))
        assertTrue(canceled.cancel("canceled-owner"))
        assertFalse(canceled.reserve("later-owner"))
        assertEquals(LiteRtLmGenerationTerminal.CANCELED, canceled.snapshot("canceled-owner")?.terminal)

        val succeeded = generatingState("successful-owner")
        assertTrue(succeeded.append("successful-owner", "answer"))
        assertTrue(succeeded.complete("successful-owner"))
        assertFalse(succeeded.reserve("later-owner"))
        assertEquals(
            LiteRtLmGenerationTerminal.SUCCEEDED,
            succeeded.snapshot("successful-owner")?.terminal,
        )
    }

    @Test
    fun earlierSnapshotDoesNotChangeAfterLaterCallbacks(): Unit {
        val state = generatingState("owner")
        assertTrue(state.append("owner", "first"))
        val earlier = state.snapshot("owner")

        assertTrue(state.append("owner", " second!"))
        assertTrue(state.complete("owner"))

        assertEquals(listOf("first"), earlier?.rawChunks)
        assertEquals(LiteRtLmServicePhase.GENERATING, earlier?.phase)
        assertNull(earlier?.terminal)
        assertEquals(listOf("first", " second!"), state.snapshot("owner")?.rawChunks)
    }

    @Test
    fun concurrentReservationsHaveExactlyOneWinner(): Unit {
        val state = LiteRtLmProcessServiceState()
        val contenderCount = 16
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        val threadFailure = AtomicReference<Throwable?>()
        val threads = (0 until contenderCount).map { index ->
            Thread {
                try {
                    ready.countDown()
                    start.await()
                    if (state.reserve("owner-$index")) winners.incrementAndGet()
                } catch (failure: Throwable) {
                    threadFailure.compareAndSet(null, failure)
                }
            }.apply { start() }
        }

        ready.await()
        start.countDown()
        threads.forEach { it.join() }

        assertNull(threadFailure.get())
        assertEquals(1, winners.get())
    }

    @Test
    fun constructorRejectsInvalidLimits(): Unit {
        assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessServiceState(maxSnapshots = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessServiceState(maxSnapshots = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessServiceState(maxTotalUtf8Bytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessServiceState(maxTotalUtf8Bytes = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessServiceState(maxChunkUtf8Bytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessServiceState(maxChunkUtf8Bytes = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessServiceState(maxTotalUtf8Bytes = 8, maxChunkUtf8Bytes = 9)
        }
    }

    @Test
    fun lifecycleCallbacksCannotProceedAfterCancellationFromReservedOrInitializing(): Unit {
        val reserved = LiteRtLmProcessServiceState()
        assertTrue(reserved.reserve("reserved"))
        assertTrue(reserved.cancel("reserved"))
        assertFalse(reserved.begin("reserved"))
        assertFalse(reserved.markNativeAttempted("reserved"))
        assertFalse(reserved.append("reserved", "late"))

        val initializing = LiteRtLmProcessServiceState()
        assertTrue(initializing.reserve("initializing"))
        assertTrue(initializing.begin("initializing"))
        assertTrue(initializing.cancel("initializing"))
        assertFalse(initializing.begin("initializing"))
        assertFalse(initializing.markNativeAttempted("initializing"))
        assertFalse(initializing.append("initializing", "late"))
    }

    @Test
    fun cancellationDuringGenerationPreservesChunksAndRejectsLateCallbacks(): Unit {
        val state = generatingState("owner")
        val chunks = listOf("first ", "second!")
        chunks.forEach { assertTrue(state.append("owner", it)) }

        assertTrue(state.cancel("owner"))
        val canceled = state.snapshot("owner")
        assertEquals(chunks, canceled?.rawChunks)
        assertEquals(LiteRtLmGenerationTerminal.CANCELED, canceled?.terminal)
        assertFalse(state.complete("owner"))
        assertFalse(state.fail("owner", "LateFailure"))
        assertFalse(state.append("owner", "late"))
        assertEquals(canceled, state.snapshot("owner"))
    }

    @Test
    fun failureCodesAreSanitizedAndSafeCodeIsPreserved(): Unit {
        listOf("Bad\nCode", "../secret", "A".repeat(65)).forEachIndexed { index, unsafeCode ->
            val state = generatingState("unsafe-$index")

            assertTrue(state.fail("unsafe-$index", unsafeCode))
            assertEquals("RuntimeFailure", state.snapshot("unsafe-$index")?.errorCode)
        }

        val safeState = generatingState("safe")
        assertTrue(safeState.fail("safe", "NativeEngine_42"))
        assertEquals("NativeEngine_42", safeState.snapshot("safe")?.errorCode)
    }

    @Test
    fun concurrentBeginForReservedOwnerHasExactlyOneWinner(): Unit {
        val state = LiteRtLmProcessServiceState()
        assertTrue(state.reserve("owner"))
        val contenderCount = 16
        val ready = CountDownLatch(contenderCount)
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        val threadFailure = AtomicReference<Throwable?>()
        val threads = (0 until contenderCount).map {
            Thread {
                try {
                    ready.countDown()
                    start.await()
                    if (state.begin("owner")) winners.incrementAndGet()
                } catch (failure: Throwable) {
                    threadFailure.compareAndSet(null, failure)
                }
            }.apply { start() }
        }

        ready.await()
        start.countDown()
        threads.forEach { it.join() }

        assertNull(threadFailure.get())
        assertEquals(1, winners.get())
    }

    @Test
    fun concurrentBoundedAppendsRetainAllUniqueChunks(): Unit {
        val state = generatingState("owner")
        val chunks = (0 until 16).map { "chunk-$it" }
        val ready = CountDownLatch(chunks.size)
        val start = CountDownLatch(1)
        val accepted = AtomicInteger()
        val threadFailure = AtomicReference<Throwable?>()
        val threads = chunks.map { chunk ->
            Thread {
                try {
                    ready.countDown()
                    start.await()
                    if (state.append("owner", chunk)) accepted.incrementAndGet()
                } catch (failure: Throwable) {
                    threadFailure.compareAndSet(null, failure)
                }
            }.apply { start() }
        }

        ready.await()
        start.countDown()
        threads.forEach { it.join() }

        assertNull(threadFailure.get())
        assertEquals(chunks.size, accepted.get())
        val retained = state.snapshot("owner")?.rawChunks.orEmpty()
        assertEquals(chunks.size, retained.size)
        assertEquals(chunks.toSet(), retained.toSet())
    }

    private fun generatingState(
        nonce: String,
        maxSnapshots: Int = 256,
        maxTotalUtf8Bytes: Int = 32768,
        maxChunkUtf8Bytes: Int = 8192,
    ): LiteRtLmProcessServiceState {
        val state = LiteRtLmProcessServiceState(
            maxSnapshots = maxSnapshots,
            maxTotalUtf8Bytes = maxTotalUtf8Bytes,
            maxChunkUtf8Bytes = maxChunkUtf8Bytes,
        )
        assertTrue(state.reserve(nonce))
        assertTrue(state.begin(nonce))
        assertTrue(state.markNativeAttempted(nonce))
        assertTrue(state.markNativeInitialized(nonce))
        assertTrue(state.beginGeneration(nonce))
        return state
    }
}
