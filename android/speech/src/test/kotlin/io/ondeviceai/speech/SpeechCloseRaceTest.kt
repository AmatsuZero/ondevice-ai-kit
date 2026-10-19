package io.ondeviceai.speech

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

private class SuspendedSpeechBackend(
    override val capability: SpeechCapability = SpeechCapability.TTS,
    private val pauseAvailability: Boolean = false,
    private val pauseClose: Boolean = false
) : SpeechSynthesisBackend, SpeechRecognitionBackend {
    override val backendId = "test.suspended.speech"
    val availabilityStarted = CompletableDeferred<Unit>()
    val availabilityRelease = CompletableDeferred<Unit>()
    val closeStarted = CompletableDeferred<Unit>()
    val closeRelease = CompletableDeferred<Unit>()
    var calls = 0
    var closes = 0
    override suspend fun availability(): CapabilityAvailability {
        availabilityStarted.complete(Unit)
        if (pauseAvailability) availabilityRelease.await()
        return CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    }
    override suspend fun speak(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        calls++
        return SpeechSynthesisResult(backendId)
    }
    override suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult {
        calls++
        return SpeechRecognitionResult(backendId, "hello")
    }
    override suspend fun close() {
        closes++
        closeStarted.complete(Unit)
        if (pauseClose) closeRelease.await()
    }
}

class SpeechCloseRaceTest {
    @Test fun registryRejectsSelectionWhenCloseWinsDuringAvailability() = runBlocking<Unit> {
        withTimeout(5_000) {
            val backend = SuspendedSpeechBackend(pauseAvailability = true)
            val client = SpeechClient(SpeechCapability.TTS, backend.backendId,
                listOf(SpeechBackendRegistration(SpeechCapability.TTS, backend)))
            val selection = async { runCatching { client.select() } }
            backend.availabilityStarted.await()
            client.close()
            backend.availabilityRelease.complete(Unit)
            assertIs<IllegalStateException>(selection.await().exceptionOrNull())
        }
    }

    @Test fun ttsDoesNotDispatchWhenClosedDuringAvailability() = runBlocking<Unit> {
        withTimeout(5_000) {
            val backend = SuspendedSpeechBackend(pauseAvailability = true)
            val client = SpeechSynthesisClient(backend.backendId, listOf(backend))
            val speech = async { runCatching { client.speak(SpeechSynthesisRequest("hello", "en-US")) } }
            backend.availabilityStarted.await()
            client.close()
            backend.availabilityRelease.complete(Unit)
            assertIs<IllegalStateException>(speech.await().exceptionOrNull())
            assertEquals(0, backend.calls)
        }
    }

    @Test fun asrDoesNotDispatchWhenClosedDuringAvailability() = runBlocking<Unit> {
        withTimeout(5_000) {
            val backend = SuspendedSpeechBackend(SpeechCapability.ASR, pauseAvailability = true)
            val client = SpeechRecognitionClient(backend.backendId, listOf(backend))
            val recognition = async { runCatching { client.recognize(SpeechRecognitionRequest("en-US")) } }
            backend.availabilityStarted.await()
            client.close()
            backend.availabilityRelease.complete(Unit)
            assertIs<IllegalStateException>(recognition.await().exceptionOrNull())
            assertEquals(0, backend.calls)
        }
    }

    @Test fun concurrentCloseWaitsForSameBackendRelease() = runBlocking<Unit> {
        withTimeout(5_000) {
            val backend = SuspendedSpeechBackend(pauseClose = true)
            val client = SpeechClient(SpeechCapability.TTS, backend.backendId,
                listOf(SpeechBackendRegistration(SpeechCapability.TTS, backend)))
            val first = async { client.close() }
            backend.closeStarted.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { client.close() }
            val returnedEarly = second.isCompleted
            backend.closeRelease.complete(Unit)
            first.await()
            second.await()
            assertFalse(returnedEarly, "concurrent close must join backend release")
            assertEquals(1, backend.closes)
        }
    }
}
