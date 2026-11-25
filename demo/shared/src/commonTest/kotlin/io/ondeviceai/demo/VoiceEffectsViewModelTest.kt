package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.*

class VoiceEffectsViewModelTest {
    @Test fun choosingCoreMLRetiresCPUResultAndRoutesCurrentInputAndVoice() {
        val neural = CoreMLTimbreFake()
        val model = VoiceEffectsViewModel(VoiceFake(), neural)
        model.selectInput("recording", "录音.wav")
        model.selectEffect(DemoVoiceEffect.VOICE_B)
        model.process()
        neural.finish("cpu")
        model.selectCoreML(true)
        assertTrue(neural.outputs.isEmpty())
        model.process()
        assertEquals("recording" to "voiceB", neural.coreMLRequest)
        model.leavePage()
        neural.finish("late-coreml")
        assertTrue(neural.outputs.isEmpty())
        assertNull(model.state.output)
        assertEquals(VoiceEffectsPhase.CANCELLED, model.state.phase)
    }

    @Test fun cpuOnlyHostAndPitchEffectsNeverRouteToCoreML() {
        val neural = TimbreFake()
        val model = VoiceEffectsViewModel(VoiceFake(), neural)
        model.setExample("bundled")
        model.selectCoreML(true)
        assertFalse(model.state.useCoreML)
        model.process()
        assertEquals("bundled" to "voiceA", neural.request)

        val pitch = VoiceFake()
        val ios = VoiceEffectsViewModel(pitch, CoreMLTimbreFake())
        ios.setExample("bundled")
        ios.selectCoreML(true)
        ios.selectEffect(DemoVoiceEffect.HIGH)
        ios.process()
        assertEquals(1, pitch.calls.size)
    }

    @Test fun selectedTimbreUsesCurrentInputAndRetiresEachOwnedResult() {
        val pitch = VoiceFake()
        val neural = TimbreFake()
        val model = VoiceEffectsViewModel(pitch, neural)
        model.setExample("bundled")
        model.process()
        assertEquals("bundled" to "voiceA", neural.request)
        neural.finish("first")
        assertTrue(model.holdOutput("first"))
        model.selectInput("recorded", "录音.wav")
        assertNull(model.state.output)
        assertTrue(neural.outputs.contains("first"))
        model.endOutputUse("first")
        assertFalse(neural.outputs.contains("first"))
        model.selectEffect(DemoVoiceEffect.VOICE_B)
        model.process()
        assertEquals("recorded" to "voiceB", neural.request)
        neural.finish("second")
        assertEquals(22050, model.state.output?.sampleRate)
        model.selectEffect(DemoVoiceEffect.VOICE_C)
        assertNull(model.state.output)
        assertEquals(VoiceEffectsPhase.IDLE, model.state.phase)
        model.selectInput("imported", "speech.wav")
        model.process()
        assertEquals("imported" to "voiceC", neural.request)
        model.leavePage()
        neural.finish("late")
        assertNull(model.state.output)
        assertTrue(neural.outputs.isEmpty())
        assertTrue(pitch.calls.isEmpty())
        assertTrue(pitch.releases.isEmpty())
        model.selectEffect(DemoVoiceEffect.HIGH)
        model.process()
        assertEquals(1, pitch.calls.size)
    }

    @Test fun closingWhileOnnxIsReadingReleasesItsLateFile() {
        val neural = TimbreFake()
        val model = VoiceEffectsViewModel(VoiceFake(), neural)
        model.selectInput("recorded", "录音.wav")
        model.process()
        model.close()
        neural.finish("late")
        assertTrue(neural.outputs.isEmpty())
        assertNull(model.state.output)
        assertEquals(VoiceEffectsPhase.CANCELLED, model.state.phase)
    }

    @Test fun cancellationCanRetryPendingCleanup() {
        val bridge = VoiceFake(); val model = VoiceEffectsViewModel(bridge)
        model.selectInput("input",null); model.process(); model.cancel(); model.cancel()
        assertEquals(2,bridge.cancels)
        assertEquals(VoiceEffectsPhase.CANCELLING,model.state.phase)
    }
    @Test fun synchronousCompletionNeverLeavesBusy() {
        val bridge = VoiceFake().apply { synchronous = true }; val model = VoiceEffectsViewModel(bridge)
        model.selectInput("input", "tone.wav"); model.process(); model.process()
        assertEquals(VoiceEffectsPhase.SUCCEEDED, model.state.phase)
        assertEquals(listOf("out-1"), bridge.releases)
    }
    @Test fun cancellationWaitsForTerminalAndLateSuccessIsReleased() {
        val bridge = VoiceFake(); val model = VoiceEffectsViewModel(bridge)
        model.selectInput("input", "tone.wav"); model.process(); model.cancel(); model.process()
        assertEquals(1, bridge.calls.size); assertEquals(VoiceEffectsPhase.CANCELLING, model.state.phase)
        bridge.calls[0].onResult(voiceOutput("late"))
        assertNull(model.state.output); assertEquals(listOf("late"), bridge.releases)
        model.process(); assertEquals(2, bridge.calls.size)
    }
    @Test fun mediaHoldsDeferReleaseAcrossCloseAndFailureCanRetry() {
        val bridge = VoiceFake().apply { synchronous = true; releaseFails = true }; val model = VoiceEffectsViewModel(bridge)
        model.selectInput("input", null); model.process(); assertTrue(model.holdOutput("out-1"))
        model.close(); assertTrue(bridge.releases.isEmpty())
        model.endOutputUse("out-1"); assertEquals(1, model.state.pendingReleases)
        bridge.releaseFails = false; model.retryReleases()
        assertEquals(0, model.state.pendingReleases); assertNull(model.state.releaseFailure)
    }
    @Test fun failedImportAndPickerCancellationPreserveCurrentInput() {
        val model = VoiceEffectsViewModel(VoiceFake())
        model.selectInput("old", "old.wav"); model.selectInput(null, null); model.inputFailed()
        assertEquals("old", model.state.inputReference)
        model.setRecording(true); model.process(); assertEquals(VoiceEffectsPhase.FAILED, model.state.phase)
    }
    @Test fun closeAndOldCallbacksDoNotReviveOutput() {
        val bridge = VoiceFake(); val model = VoiceEffectsViewModel(bridge)
        model.selectInput("input", null); model.process(); model.close()
        bridge.calls[0].onResult(voiceOutput("after-close"))
        assertTrue(model.state.closed); assertNull(model.state.output); assertEquals(listOf("after-close"), bridge.releases)
    }
}
private fun voiceOutput(id: String) = VoiceEffectsBridgeOutput(id, "test", 16000, 16000, 1.0)
private class VoiceFake : VoiceEffectsBridge {
    val calls = mutableListOf<VoiceEffectsBridgeProcessCallback>(); val releases = mutableListOf<String>()
    var synchronous = false; var releaseFails = false; var cancels = 0
    override fun process(request: VoiceEffectsBridgeRequest, callback: VoiceEffectsBridgeProcessCallback): BridgeTask {
        calls += callback
        if (synchronous) callback.onResult(voiceOutput("out-${calls.size}"))
        return object : BridgeTask { override fun cancel() { cancels++ } }
    }
    override fun releaseOutput(reference: String, callback: VoiceEffectsBridgeReleaseCallback) {
        releases += reference
        if (releaseFails) callback.onFailure(VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.PLATFORM, null, "test", 1))
        else callback.onReleased()
    }
    override fun close(callback: VoiceEffectsBridgeCloseCallback) { callback.onClosed() }
}

private open class TimbreFake : DemoVoiceTimbres {
    val outputs = mutableSetOf<String>()
    lateinit var request: Pair<String, String>
    private lateinit var callback: VoiceEffectsBridgeProcessCallback

    override fun render(inputReference: String, key: String, callback: VoiceEffectsBridgeProcessCallback) {
        request = inputReference to key
        this.callback = callback
    }
    fun finish(reference: String) {
        outputs += reference
        callback.onResult(VoiceEffectsBridgeOutput(reference, "openvoice.onnx", 22050, 22050, 1.0))
    }
    override fun ownsOutput(reference: String) = reference in outputs
    override fun releaseOutput(reference: String, callback: VoiceEffectsBridgeReleaseCallback) {
        assertTrue(outputs.remove(reference))
        callback.onReleased()
    }
}

private class CoreMLTimbreFake : TimbreFake(), DemoCoreMLVoiceTimbres {
    var coreMLRequest: Pair<String, String>? = null
    override fun renderCoreML(inputReference: String, key: String, callback: VoiceEffectsBridgeProcessCallback) {
        coreMLRequest = inputReference to key
        render(inputReference, key, callback)
    }
}
