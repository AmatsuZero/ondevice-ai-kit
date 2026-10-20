package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.*

class SpeechViewModelTest {
    private val tts = SpeechBridgeTtsRequest("fixture", "en-US")
    private val mic = SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone)

    @Test fun synchronousTerminalDoesNotResurrectHandle() {
        val bridge = ControlledSpeechBridge().apply { synchronous = true }
        val model = SpeechViewModel(bridge)
        model.speak(tts)
        assertEquals(SpeechPhase.SUCCEEDED, model.state.phase)
        model.cancel()
        assertEquals(0, bridge.audioCancels)
        model.speak(tts)
        assertEquals(2, bridge.audioStarts)
    }

    @Test fun switchSelectionWhileRunningWaitsForDrain() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.speak(tts)
        model.selectBackend(SpeechBridgeCapability.TTS, SpeechBridgeSelection.Explicit("other"))
        assertEquals(SpeechPhase.CANCELLING, model.state.phase)
        assertFalse(model.state.controlsEnabled)
        model.recognize(mic)
        assertEquals(1, bridge.audioStarts)
        bridge.ttsCallback!!.onCancelled()
        assertEquals(SpeechPhase.CANCELLED, model.state.phase)
        assertTrue(model.state.controlsEnabled)
        model.speak(tts)
        assertEquals(2, bridge.audioStarts)
        assertEquals(SpeechBridgeSelection.Explicit("other"), bridge.lastSelection)
    }

    @Test fun backgroundAndLeaveDoNotAutorestart() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.recognize(mic)
        model.background(); model.leavePage()
        assertEquals(SpeechPhase.CANCELLING, model.state.phase)
        assertEquals(1, bridge.audioCancels)
        bridge.asrCallback!!.onResult(SpeechBridgeAsrResult("asr", "fixture", null))
        assertEquals(SpeechPhase.CANCELLED, model.state.phase)
        assertNull(model.state.asrResult)
        assertEquals(1, bridge.audioStarts)
        assertEquals(0, bridge.closes)
    }

    @Test fun lateQueryCannotOverwriteNewQuery() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.refreshBackends(SpeechBridgeCapability.TTS)
        val old = bridge.queries.last()
        model.refreshBackends(SpeechBridgeCapability.TTS)
        bridge.queries.last().onResult(listOf(descriptor("new")))
        old.onResult(listOf(descriptor("stale")))
        assertEquals(listOf("new"), model.state.tts.backends.map { it.backendId })
        assertFalse(model.state.tts.refreshing)
        assertEquals(1, bridge.queryCancels)
    }

    @Test fun explicitMissingFromListStillReachesNativeAdmission() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.refreshBackends(SpeechBridgeCapability.TTS)
        bridge.queries.last().onResult(listOf(descriptor("known")))
        model.speak(tts, SpeechBridgeSelection.Explicit("unlisted"))
        assertEquals(1, bridge.audioStarts)
        assertEquals(SpeechBridgeSelection.Explicit("unlisted"), bridge.lastSelection)
        bridge.ttsCallback!!.onFailure(SpeechBridgeFailure(SpeechBridgeFailureCode.BACKEND_NOT_REGISTERED, null))
        assertEquals(SpeechPhase.FAILED, model.state.phase)
        assertEquals(SpeechBridgeFailureCode.BACKEND_NOT_REGISTERED, model.state.failure?.code)
    }

    @Test fun ttsAndAsrResultsNeverOverlap() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.speak(tts)
        val old = bridge.ttsCallback!!
        old.onResult(SpeechBridgeTtsResult("tts", null))
        model.recognize(mic)
        bridge.asrCallback!!.onResult(SpeechBridgeAsrResult("asr", "fixture", null))
        old.onResult(SpeechBridgeTtsResult("late", null))
        assertNull(model.state.ttsResult)
        assertEquals("asr", model.state.asrResult?.backendId)
        model.leavePage()
        assertNull(model.state.asrResult)
    }

    @Test fun closedRejectsUiAdmission() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.close(); model.close()
        model.speak(tts); model.recognize(mic); model.refreshBackends(SpeechBridgeCapability.ASR)
        assertEquals(1, bridge.closes)
        assertEquals(0, bridge.audioStarts)
        assertTrue(bridge.queries.isEmpty())
        assertFalse(model.state.controlsEnabled)
        assertEquals(SpeechBridgeFailureCode.CLOSED, model.state.failure?.code)
    }

    @Test fun unsupportedInputIsExplainedWithoutCallingNative() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.refreshBackends(SpeechBridgeCapability.ASR)
        bridge.queries.last().onResult(listOf(descriptor("file", SpeechBridgeCapability.ASR)))
        model.recognize(mic, SpeechBridgeSelection.Explicit("file"))
        assertEquals(0, bridge.audioStarts)
        assertEquals(SpeechBridgeFailureCode.UNSUPPORTED_INPUT, model.state.failure?.code)
        // An unknown explicit backend remains the adapter's admission decision.
        model.recognize(mic, SpeechBridgeSelection.Explicit("unknown"))
        assertEquals(1, bridge.audioStarts)
    }

    @Test fun inputReplacementCancelsAndPreservesSelectedTokenDuringDrain() {
        val bridge = ControlledSpeechBridge()
        val model = SpeechViewModel(bridge)
        model.selectAudio("one", "one.wav")
        model.recognize(SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.AudioFile("one")))
        model.selectAudio("two", "two.wav")
        assertEquals(SpeechPhase.CANCELLING, model.state.phase)
        assertEquals("two", model.state.audioReference)
        bridge.asrCallback!!.onCancelled()
        assertEquals("two.wav", model.state.audioDisplayName)
    }

    @Test fun synchronousQueryAndRepeatedTerminalCannotResurrectOrOverwrite() {
        val bridge = ControlledSpeechBridge().apply { synchronous = true }
        val model = SpeechViewModel(bridge)
        model.refreshBackends(SpeechBridgeCapability.TTS)
        val callback = bridge.queries.single()
        callback.onFailure(SpeechBridgeFailure(SpeechBridgeFailureCode.PLATFORM, null, "fixture", 1))
        assertNull(model.state.tts.failure)
        model.leavePage()
        assertEquals(0, bridge.queryCancels)
    }

    private fun descriptor(id: String, capability: SpeechBridgeCapability = SpeechBridgeCapability.TTS) =
        SpeechBridgeBackend(capability, id, BridgeAvailability(true, null, id, null),
            if (capability == SpeechBridgeCapability.ASR) listOf(SpeechBridgeInputKind.AUDIO_FILE) else emptyList(), true)
}

private class ControlledSpeechBridge : SpeechBridge {
    var synchronous = false
    var audioStarts = 0
    var audioCancels = 0
    var queryCancels = 0
    var closes = 0
    var lastSelection: SpeechBridgeSelection? = null
    var ttsCallback: SpeechBridgeTtsCallback? = null
    var asrCallback: SpeechBridgeAsrCallback? = null
    val queries = mutableListOf<SpeechBridgeBackendsCallback>()
    override fun queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback): BridgeTask {
        queries += callback
        if (synchronous) callback.onResult(emptyList())
        return object : BridgeTask { override fun cancel() { queryCancels++ } }
    }
    override fun speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback): BridgeTask {
        audioStarts++; lastSelection = selection; ttsCallback = callback
        if (synchronous) callback.onResult(SpeechBridgeTtsResult("tts", null))
        return object : BridgeTask { override fun cancel() { audioCancels++ } }
    }
    override fun recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback): BridgeTask {
        audioStarts++; lastSelection = selection; asrCallback = callback
        return object : BridgeTask { override fun cancel() { audioCancels++ } }
    }
    override fun close(callback: SpeechBridgeCloseCallback) { closes++; callback.onClosed() }
}
