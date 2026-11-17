package io.ondeviceai.binding

import kotlin.test.*

class VoiceEffectsBridgeContractTest {
    @Test fun validatesOpaqueInputAndOutputMetadata() {
        assertFailsWith<IllegalArgumentException> { VoiceEffectsBridgeRequest(" ", VoiceEffectsBridgePreset.HIGH) }
        assertFailsWith<IllegalArgumentException> { VoiceEffectsBridgeOutput("out", "backend", 16000, 0, 1.0) }
        assertFailsWith<IllegalArgumentException> { VoiceEffectsBridgeOutput("out", "backend", 16000, 1, Double.NaN) }
    }
    @Test fun platformDiagnosticsHaveOneStableShape() {
        assertFailsWith<IllegalArgumentException> { VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.PLATFORM, "path") }
        assertFailsWith<IllegalArgumentException> { VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.BUSY, null, "domain", 1) }
        VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.PLATFORM, null, "test", 1)
    }
    @Test fun unavailableIsExplicitAndCloseIsSticky() {
        val bridge = UnavailableVoiceEffectsBridge()
        val failures = mutableListOf<VoiceEffectsBridgeFailureCode>()
        val callback = object : VoiceEffectsBridgeProcessCallback {
            override fun onResult(output: VoiceEffectsBridgeOutput) = error("synthetic success")
            override fun onCancelled() = error("unexpected cancellation")
            override fun onFailure(failure: VoiceEffectsBridgeFailure) { failures += failure.code }
        }
        val request = VoiceEffectsBridgeRequest("input", VoiceEffectsBridgePreset.ORIGINAL)
        bridge.process(request, callback)
        bridge.close(object : VoiceEffectsBridgeCloseCallback {
            override fun onClosed() {}
            override fun onFailure(failure: VoiceEffectsBridgeFailure) = error("close")
        })
        bridge.process(request, callback)
        assertEquals(listOf(VoiceEffectsBridgeFailureCode.BACKEND_UNAVAILABLE, VoiceEffectsBridgeFailureCode.CLOSED), failures)
    }
}
