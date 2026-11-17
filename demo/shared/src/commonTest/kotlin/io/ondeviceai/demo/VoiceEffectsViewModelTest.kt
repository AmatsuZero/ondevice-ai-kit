package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.*

class VoiceEffectsViewModelTest {
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
