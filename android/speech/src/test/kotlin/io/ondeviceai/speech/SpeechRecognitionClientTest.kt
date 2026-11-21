package io.ondeviceai.speech

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeAsr(override val backendId: String, private val available: Boolean = true) : SpeechRecognitionBackend {
    var failure: Throwable? = null
    var closed = 0
    var calls = 0
    override val capability = SpeechCapability.ASR
    override suspend fun availability() = CapabilityAvailability(
        if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
        backendId = backendId,
        offline = !backendId.contains("cloud")
    )
    override suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult {
        calls++
        failure?.let { throw it }
        return SpeechRecognitionResult(backendId, "recognized ${request.locale}", 0.9f)
    }
    override suspend fun close() { closed++ }
}

class SpeechRecognitionClientTest {
    @Test
    fun runtimeUnavailabilityKeepsStableClientErrorAndCause() = runBlocking<Unit> {
        val backend = FakeAsr("android.speech.recognizer")
        val cause = IllegalStateException("model missing")
        backend.failure = SpeechBackendUnavailableException(UnavailableReason.MISSING_ASSETS, cause)
        val client = SpeechRecognitionClient(backend.backendId, listOf(backend))
        val error = assertFailsWith<SpeechClientError.BackendUnavailable> {
            client.recognize(SpeechRecognitionRequest("en-US"))
        }
        assertEquals(UnavailableReason.MISSING_ASSETS, error.reason)
        assertEquals(backend.backendId, error.backendId)
        assertEquals(backend.failure, error.cause)
        assertEquals(1, backend.calls)
    }
    @Test
    fun explicitSelectionIsStrictAndResultKeepsBackendId() = runBlocking<Unit> {
        val local = FakeAsr("android.speech.recognizer")
        val cloud = FakeAsr("microsoft.azure.speech.asr", available = false)
        val client = SpeechRecognitionClient(local.backendId, listOf(local, cloud))
        val result = client.recognize(SpeechRecognitionRequest("en-US"))
        assertEquals(local.backendId, result.backendId)
        assertEquals("recognized en-US", result.transcript)
        assertFailsWith<SpeechClientError.BackendUnavailable> {
            client.recognize(SpeechRecognitionRequest("en-US"), SpeechBackendSelection.explicit(cloud.backendId))
        }
        assertFailsWith<SpeechClientError.BackendNotRegistered> {
            client.recognize(SpeechRecognitionRequest("en-US"), SpeechBackendSelection.explicit("missing"))
        }
        assertEquals(1, local.calls)
        assertEquals(0, cloud.calls)
        client.close()
        client.close()
        assertEquals(1, local.closed)
        assertFailsWith<IllegalStateException> {
            client.recognize(SpeechRecognitionRequest("en-US"))
        }
    }

    @Test
    fun explicitAvailableBackendDoesNotInvokeDefault() = runBlocking<Unit> {
        val local = FakeAsr("android.speech.recognizer")
        val alternative = FakeAsr("test.other.asr")
        val client = SpeechRecognitionClient(local.backendId, listOf(local, alternative))
        val result = client.recognize(SpeechRecognitionRequest("en-US"),
            SpeechBackendSelection.explicit(alternative.backendId))
        assertEquals(alternative.backendId, result.backendId)
        assertEquals(0, local.calls)
        assertEquals(1, alternative.calls)
        client.close()
    }

    @Test
    fun invalidRecognitionRequestIsRejectedBeforeBackendCall() = runBlocking<Unit> {
        val backend = FakeAsr("android.speech.recognizer")
        val client = SpeechRecognitionClient(backend.backendId, listOf(backend))
        assertFailsWith<IllegalArgumentException> {
            client.recognize(SpeechRecognitionRequest(""))
        }
    }
}
