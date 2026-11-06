package io.ondeviceai.speech

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

private class ClosingSpeechBackend(
    override val backendId: String,
    private val cleanup: suspend () -> Unit
) : SpeechBackend {
    override val capability = SpeechCapability.TTS
    override suspend fun availability() =
        CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    override suspend fun close() = cleanup()
}

class SpeechCloseFailureTest {
    @Test fun closeAttemptsEveryBackendAndSharesTheOriginalFailure() = runBlocking<Unit> {
        val closed = mutableListOf<String>()
        val firstFailure = IllegalStateException("first cleanup")
        val secondFailure = IllegalArgumentException("second cleanup")
        val backends = listOf(
            ClosingSpeechBackend("first") { closed += "first"; throw firstFailure },
            ClosingSpeechBackend("second") { closed += "second"; throw secondFailure },
            ClosingSpeechBackend("third") { closed += "third" }
        )
        val client = SpeechClient(SpeechCapability.TTS, "first",
            backends.map { SpeechBackendRegistration(SpeechCapability.TTS, it) })

        val failure = assertFailsWith<IllegalStateException> { client.close() }

        assertEquals(listOf("first", "second", "third"), closed)
        assertEquals("first cleanup", failure.message)
        assertEquals(listOf(secondFailure), firstFailure.suppressed.toList())
        assertEquals("first cleanup", assertFailsWith<IllegalStateException> { client.close() }.message)
        assertEquals(listOf("first", "second", "third"), closed)
        assertFailsWith<IllegalStateException> { client.select() }
    }

    @Test fun repeatedFailureIdentityDoesNotInterruptRemainingCleanup() = runBlocking<Unit> {
        val closed = mutableListOf<String>()
        val failure = IllegalStateException("shared cleanup failure")
        val backends = listOf(
            ClosingSpeechBackend("first") { closed += "first"; throw failure },
            ClosingSpeechBackend("second") { closed += "second"; throw failure },
            ClosingSpeechBackend("third") { closed += "third" }
        )
        val client = SpeechClient(SpeechCapability.TTS, "first",
            backends.map { SpeechBackendRegistration(SpeechCapability.TTS, it) })

        assertEquals("shared cleanup failure", assertFailsWith<IllegalStateException> { client.close() }.message)
        assertEquals(listOf("first", "second", "third"), closed)
        assertTrue(failure.suppressed.isEmpty())
    }

    @Test fun cancelledCloseOwnerStillReleasesEveryBackendBeforeJoinerFails() = runBlocking<Unit> {
        withTimeout(5_000) {
            val failure = IllegalStateException("cleanup failure")
            val finalStarted = CompletableDeferred<Unit>()
            val finalRelease = CompletableDeferred<Unit>()
            val closed = mutableListOf<String>()
            val backends = listOf(
                ClosingSpeechBackend("first") { closed += "first"; throw failure },
                ClosingSpeechBackend("final") {
                    finalStarted.complete(Unit)
                    finalRelease.await()
                    closed += "final"
                }
            )
            val client = SpeechClient(SpeechCapability.TTS, "first",
                backends.map { SpeechBackendRegistration(SpeechCapability.TTS, it) })
            val owner = async(start = CoroutineStart.UNDISPATCHED) { runCatching { client.close() } }
            val joiner = async(start = CoroutineStart.UNDISPATCHED) { runCatching { client.close() } }
            try {
                finalStarted.await()
                assertFalse(joiner.isCompleted)
                owner.cancel()
                assertFalse(joiner.isCompleted)
            } finally {
                finalRelease.complete(Unit)
            }

            assertEquals("cleanup failure", assertIs<IllegalStateException>(joiner.await().exceptionOrNull()).message)
            owner.join()
            assertTrue(owner.isCancelled)
            assertEquals(listOf("first", "final"), closed)
            assertEquals("cleanup failure", assertFailsWith<IllegalStateException> { client.close() }.message)
            assertEquals(listOf("first", "final"), closed)
        }
    }
}
