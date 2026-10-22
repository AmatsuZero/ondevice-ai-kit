package io.ondeviceai.binding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultimodalBridgeContractTest {
    @Test fun validatesRequestsWithoutChangingRawPrompt() {
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeSelection.Explicit(" ") }
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeRequest("", "describe", 32) }
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeRequest("input", "\n", 32) }
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeRequest("input", "describe", 0) }
        assertEquals(" raw prompt \n", MultimodalBridgeRequest("input", " raw prompt \n", 32).prompt)
    }

    @Test fun validatesUpdatesIdentityAndOutputs() {
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeUpdate(-1, "") }
        assertEquals("", MultimodalBridgeUpdate(0, "").text)
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeIdentity("", "revision", "runtime") }
        val model = MultimodalBridgeIdentity("model", "revision", "runtime")
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeOutput("", "text", model) }
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeOutput("backend", " ", model) }
        assertFailsWith<IllegalArgumentException> {
            MultimodalBridgeBackend("a", BridgeAvailability(true, null, "b", null))
        }
    }

    @Test fun failureCannotExposePrivateDiagnostics() {
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, "/private/image", "ios.multimodal", 1) }
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, null, "", 1) }
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, null, "ios.multimodal", null) }
        assertFailsWith<IllegalArgumentException> { MultimodalBridgeFailure(MultimodalBridgeFailureCode.BUSY, null, "ios.multimodal", 1) }
    }

    @Test fun unavailableNeverGeneratesAndClosedHasPriority() {
        val bridge = UnavailableMultimodalBridge()
        val failures = mutableListOf<MultimodalBridgeFailureCode>()
        val query = object : MultimodalBridgeBackendsCallback {
            override fun onMultimodalBackends(backends: List<MultimodalBridgeBackend>) = error("no synthetic backend")
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) { failures += failure.code }
            override fun onMultimodalCancelled() = error("not admitted")
        }
        val generation = object : MultimodalBridgeGenerateCallback {
            override fun onMultimodalUpdate(update: MultimodalBridgeUpdate) = error("no synthetic update")
            override fun onMultimodalResult(output: MultimodalBridgeOutput) = error("no synthetic output")
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) { failures += failure.code }
            override fun onMultimodalCancelled() = error("not admitted")
        }
        val request = MultimodalBridgeRequest("opaque", "describe", 32)
        bridge.queryMultimodalBackends(query).cancel()
        bridge.generateMultimodal(request, MultimodalBridgeSelection.Default, generation).cancel()
        var closes = 0
        val close = object : MultimodalBridgeCloseCallback {
            override fun onMultimodalClosed() { closes++ }
            override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = error("close cannot fail")
        }
        bridge.closeMultimodal(close); bridge.closeMultimodal(close)
        bridge.queryMultimodalBackends(query)
        bridge.generateMultimodal(request, MultimodalBridgeSelection.Explicit("missing"), generation)
        assertEquals(2, closes)
        assertEquals(listOf(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE,
            MultimodalBridgeFailureCode.CLOSED, MultimodalBridgeFailureCode.CLOSED), failures)
    }
}
