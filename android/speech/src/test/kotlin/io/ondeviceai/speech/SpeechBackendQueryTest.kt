package io.ondeviceai.speech

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class QueryTtsBackend(
    override val backendId: String,
    private val available: Boolean = true,
    private val pauseAvailability: Boolean = false,
) : SpeechSynthesisBackend {
    override val capability = SpeechCapability.TTS
    val availabilityStarted = CompletableDeferred<Unit>()
    val availabilityRelease = CompletableDeferred<Unit>()
    var speakCount = 0
    var closeCount = 0

    override suspend fun availability(): CapabilityAvailability {
        availabilityStarted.complete(Unit)
        if (pauseAvailability) availabilityRelease.await()
        return CapabilityAvailability(
            if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
            if (available) null else UnavailableReason.UNSUPPORTED_DEVICE,
            backendId,
            minOS = "26",
            offline = !backendId.contains("cloud"),
        )
    }

    override suspend fun speak(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        speakCount++
        return SpeechSynthesisResult(backendId)
    }

    override suspend fun close() { closeCount++ }
}

private class QueryAsrBackend(override val backendId: String) : SpeechRecognitionBackend {
    override val capability = SpeechCapability.ASR
    var recognizeCount = 0
    override suspend fun availability() = CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    override suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult {
        recognizeCount++
        return SpeechRecognitionResult(backendId, "fixture")
    }
    override suspend fun close() = Unit
}

class SpeechBackendQueryTest {
    @Test
    fun listsOnlyOwnCapabilitySortedById() = runBlocking<Unit> {
        val z = QueryTtsBackend("z.tts")
        val a = QueryTtsBackend("a.tts")
        val tts = SpeechSynthesisClient(a.backendId, listOf(z, a))
        val asrBackend = QueryAsrBackend("a.asr")
        val asr = SpeechRecognitionClient(asrBackend.backendId, listOf(asrBackend))

        assertEquals(listOf("a.tts", "z.tts"), tts.listBackends().map { it.backendId })
        assertEquals(listOf(SpeechCapability.TTS, SpeechCapability.TTS), tts.listBackends().map { it.capability })
        assertEquals(listOf("a.asr"), asr.listBackends().map { it.backendId })
        assertEquals(listOf(SpeechCapability.ASR), asr.listBackends().map { it.capability })
        assertEquals(0, z.speakCount)
        assertEquals(0, a.speakCount)
        assertEquals(0, asrBackend.recognizeCount)
        tts.close()
        asr.close()
    }

    @Test
    fun queryDoesNotChangeDefaultOrFallback() = runBlocking<Unit> {
        val local = QueryTtsBackend("a.tts")
        val unavailable = QueryTtsBackend("z.cloud.tts", available = false)
        val client = SpeechSynthesisClient(local.backendId, listOf(unavailable, local))
        val descriptors = client.listBackends()
        assertEquals(listOf("a.tts", "z.cloud.tts"), descriptors.map { it.backendId })
        assertEquals(CapabilityStatus.UNAVAILABLE, descriptors[1].availability.status)
        assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, descriptors[1].availability.reason)
        assertEquals("26", descriptors[1].availability.minOS)
        assertEquals(false, descriptors[1].availability.offline)
        client.speak(SpeechSynthesisRequest("hello", "en-US"))
        assertEquals(1, local.speakCount)
        assertFailsWith<SpeechClientError.BackendUnavailable> {
            client.speak(SpeechSynthesisRequest("hello", "en-US"), SpeechBackendSelection.explicit(unavailable.backendId))
        }
        assertEquals(0, unavailable.speakCount)
        client.close()
    }

    @Test
    fun closeWhileAvailabilitySuspendedRejectsQuery() = runBlocking<Unit> {
        val backend = QueryTtsBackend("suspended.tts", pauseAvailability = true)
        val client = SpeechSynthesisClient(backend.backendId, listOf(backend))
        val query = async { runCatching { client.listBackends() } }
        backend.availabilityStarted.await()
        client.close()
        backend.availabilityRelease.complete(Unit)
        assertFailsWith<IllegalStateException> { query.await().getOrThrow() }
    }

    @Test
    fun queryCancellationDoesNotCloseClient() = runBlocking<Unit> {
        val backend = QueryTtsBackend("cancel.tts")
        val client = SpeechSynthesisClient(backend.backendId, listOf(backend))
        val query = async { runCatching { client.listBackends() } }
        query.cancel()
        runCatching { query.await() }
        client.speak(SpeechSynthesisRequest("still open", "en-US"))
        assertEquals(0, backend.closeCount)
        client.close()
    }
}
