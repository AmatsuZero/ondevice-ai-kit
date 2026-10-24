package io.ondeviceai.binding

import kotlin.test.*

class MultimodalVideoBridgeContractTest {
    @Test fun videoRequestPreservesOpaqueTokenPromptBudgetAndRejectsBlankValues() {
        val request = MultimodalBridgeVideoRequest("opaque-video", " raw prompt \n", 37)
        assertEquals("opaque-video", request.inputReference); assertEquals(" raw prompt \n", request.prompt)
        assertEquals(37, request.maxOutputTokens); assertEquals(128, MultimodalBridgeVideoRequest("v", "p").maxOutputTokens)
        for (bad in listOf("", " \n")) {
            assertFailsWith<IllegalArgumentException> { request.copy(inputReference = bad) }
            assertFailsWith<IllegalArgumentException> { request.copy(prompt = bad) }
        }
        for (bad in listOf(0,-1)) assertFailsWith<IllegalArgumentException> { request.copy(maxOutputTokens = bad) }
    }
    @Test fun optionalVideoBridgeKeepsLegacyImageConformerSourceCompatible() {
        val legacy = object : MultimodalBridge {
            override fun queryMultimodalBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask = error("unused")
            override fun generateMultimodal(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection, callback: MultimodalBridgeGenerateCallback): BridgeTask = error("unused")
            override fun closeMultimodal(callback: MultimodalBridgeCloseCallback) { callback.onMultimodalClosed() }
        }
        var closed = false
        legacy.closeMultimodal(object : MultimodalBridgeCloseCallback {
            override fun onMultimodalClosed() { closed = true }
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = error("unused")
        })
        assertTrue(closed)
    }
    @Test fun unavailableVideoNeverSynthesizesAndClosedHasPriority() {
        val bridge: MultimodalVideoBridge = UnavailableMultimodalBridge()
        val errors = mutableListOf<MultimodalBridgeFailure>()
        val callback = object : MultimodalBridgeGenerateCallback {
            override fun onMultimodalUpdate(update: MultimodalBridgeUpdate) = error("no update")
            override fun onMultimodalResult(output: MultimodalBridgeOutput) = error("no result")
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) { errors += failure }
            override fun onMultimodalCancelled() = error("no admission")
        }
        val request = MultimodalBridgeVideoRequest("opaque", "describe")
        bridge.generateMultimodalVideo(request, MultimodalBridgeSelection.Explicit("missing"), callback).cancel()
        bridge.closeMultimodal(object : MultimodalBridgeCloseCallback {
            override fun onMultimodalClosed() {}
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = error("close")
        })
        bridge.generateMultimodalVideo(request, MultimodalBridgeSelection.Default, callback).cancel()
        assertEquals(listOf(MultimodalBridgeFailure(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE,"backendNotLinked"),
            MultimodalBridgeFailure(MultimodalBridgeFailureCode.CLOSED,null)), errors)
    }
}
