package io.ondeviceai.speech

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import kotlinx.coroutines.runBlocking
import kotlin.test.*

private class FakeTts(override val backendId: String, private val available: Boolean = true) : SpeechSynthesisBackend {
    override val capability = SpeechCapability.TTS
    val spoken = mutableListOf<String>()
    override suspend fun availability() = CapabilityAvailability(
        if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
        if (available) null else UnavailableReason.UNSUPPORTED_DEVICE, backendId)
    override suspend fun speak(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        spoken += request.text
        return SpeechSynthesisResult(backendId, "test.voice")
    }
    override suspend fun close() = Unit
}

class SpeechSynthesisClientTest {
    @Test fun defaultAndExplicitDispatchWithoutFallback() = runBlocking {
        val local = FakeTts("android.text-to-speech")
        val cloud = FakeTts("test.cloud.tts")
        val client = SpeechSynthesisClient(local.backendId, listOf(local, cloud))
        val request = SpeechSynthesisRequest("hello", "en-US")
        assertEquals(local.backendId, client.speak(request).backendId)
        assertEquals(cloud.backendId, client.speak(request, SpeechBackendSelection.explicit(cloud.backendId)).backendId)
        assertEquals(listOf("hello"), local.spoken)
        assertEquals(listOf("hello"), cloud.spoken)
        assertFailsWith<SpeechClientError.BackendNotRegistered> {
            client.speak(request, SpeechBackendSelection.explicit("missing"))
        }
        client.close()
    }

    @Test fun unavailableExplicitBackendDoesNotInvokeDefault() = runBlocking {
        val local = FakeTts("android.text-to-speech")
        val cloud = FakeTts("test.cloud.tts", available = false)
        val client = SpeechSynthesisClient(local.backendId, listOf(local, cloud))
        assertFailsWith<SpeechClientError.BackendUnavailable> {
            client.speak(SpeechSynthesisRequest("hello", "en-US"), SpeechBackendSelection.explicit(cloud.backendId))
        }
        assertTrue(local.spoken.isEmpty())
    }
}
