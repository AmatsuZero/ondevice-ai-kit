package io.ondeviceai.demo

import io.ondeviceai.binding.*
import io.ondeviceai.core.*
import io.ondeviceai.speech.*
import kotlinx.coroutines.*
import kotlin.test.*

class SpeechHostTest {
    @Test fun ttsInitializationAfterOwnerCloseDisposesBackend() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val factory = CompletableDeferred<SpeechSynthesisBackend?>()
        val backend = HostTtsBackend()
        val host = SpeechHost(CoroutineScope(Dispatchers.Unconfined),
            createTts = { started.complete(Unit); factory.await() }, createAsr = { null },
            hasPermission = { true }, requestPermission = { it(true) }, openSettings = {})
        started.await()
        val closed = CompletableDeferred<Unit>()
        host.close(object : SpeechBridgeCloseCallback {
            override fun onClosed() { closed.complete(Unit) }
            override fun onFailure(failure: SpeechBridgeFailure) { error("close failed") }
        })
        assertFalse(closed.isCompleted)
        factory.complete(backend)
        withTimeout(3000) { closed.await() }
        assertEquals(1, backend.closes)
    }

    @Test fun permissionReplyAfterBackgroundDoesNotStartRecognition() = runBlocking {
        var reply: ((Boolean) -> Unit)? = null
        val backend = HostAsrBackend()
        val host = SpeechHost(CoroutineScope(Dispatchers.Unconfined), createTts = { null }, createAsr = { backend },
            hasPermission = { false }, requestPermission = { reply = it }, openSettings = {})
        host.onForeground()
        host.model.recognize(SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone))
        assertNotNull(reply)
        host.onBackground()
        reply!!(true)
        assertEquals(0, backend.starts)
        assertEquals(SpeechPhase.CANCELLED, host.model.state.phase)
        host.close()
    }

    @Test fun permissionDeniedNeverCallsRecognizerAndReturnsRecoveryReason() = runBlocking {
        val backend = HostAsrBackend()
        val host = SpeechHost(CoroutineScope(Dispatchers.Unconfined), createTts = { null }, createAsr = { backend },
            hasPermission = { false }, requestPermission = { it(false) }, openSettings = {})
        host.onForeground()
        host.model.recognize(SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone))
        assertEquals(0, backend.starts)
        assertEquals("PERMISSION_DENIED", host.model.state.failure?.reason)
        host.close()
    }
}

private class HostTtsBackend : SpeechSynthesisBackend {
    override val backendId = "fixture.tts"
    override val capability = SpeechCapability.TTS
    var closes = 0
    override suspend fun availability() = CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    override suspend fun speak(request: SpeechSynthesisRequest) = SpeechSynthesisResult(backendId, null)
    override suspend fun close() { closes++ }
}
private class HostAsrBackend : SpeechRecognitionBackend {
    override val backendId = "fixture.asr"
    override val capability = SpeechCapability.ASR
    var starts = 0
    override suspend fun availability() = CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    override suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult {
        starts++
        return SpeechRecognitionResult(backendId, "fixture", null)
    }
    override suspend fun close() {}
}
