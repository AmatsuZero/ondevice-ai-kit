package io.ondeviceai.binding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VideoBridgeContractTest {
    @Test fun rejectsInvalidReferencesAndScale() {
        assertFailsWith<IllegalArgumentException> { VideoBridgeSelection.Explicit(" ") }
        assertFailsWith<IllegalArgumentException> { VideoBridgeRequest(" ") }
        assertFailsWith<IllegalArgumentException> { VideoBridgeRequest("token", 1) }
        assertFailsWith<IllegalArgumentException> { output(reference = "") }
    }

    @Test fun rejectsInvalidProgressAndOutputMetadata() {
        assertFailsWith<IllegalArgumentException> { VideoBridgeProgress(-1, null) }
        assertFailsWith<IllegalArgumentException> { VideoBridgeProgress(2, 1) }
        assertFailsWith<IllegalArgumentException> { VideoBridgeProgress(0, 0) }
        assertFailsWith<IllegalArgumentException> { output(width = 0) }
        assertFailsWith<IllegalArgumentException> { output(frames = 0) }
        assertFailsWith<IllegalArgumentException> { output(duration = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { output(duration = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { output(duration = 0.0) }
        assertFailsWith<IllegalArgumentException> { output(audio = -1) }
        assertFailsWith<IllegalArgumentException> {
            VideoBridgeBackend("a", BridgeAvailability(true, null, "b", null), listOf(4), listOf(VideoBridgeAudioPolicy.PRESERVE))
        }
    }

    @Test fun unavailableCannotReturnSuccessAndCloseHasPriority() {
        val bridge = UnavailableVideoBridge()
        val codes = mutableListOf<VideoBridgeFailureCode>()
        val query = object : VideoBridgeBackendsCallback {
            override fun onResult(backends: List<VideoBridgeBackend>) = error("no synthetic backends")
            override fun onFailure(failure: VideoBridgeFailure) { codes += failure.code }
            override fun onCancelled() = error("no work was admitted")
        }
        val process = object : VideoBridgeProcessCallback {
            override fun onProgress(progress: VideoBridgeProgress) = error("no synthetic progress")
            override fun onResult(output: VideoBridgeOutput) = error("no synthetic output")
            override fun onFailure(failure: VideoBridgeFailure) { codes += failure.code }
            override fun onCancelled() = error("no work was admitted")
        }
        bridge.queryBackends(query).cancel()
        bridge.process(VideoBridgeRequest("opaque-token"), VideoBridgeSelection.Default, process).cancel()
        var closes = 0
        val close = object : VideoBridgeCloseCallback {
            override fun onClosed() { closes++ }
            override fun onFailure(failure: VideoBridgeFailure) = error("nothing to close")
        }
        bridge.close(close)
        bridge.close(close)
        bridge.queryBackends(query)
        bridge.process(VideoBridgeRequest("opaque-token"), VideoBridgeSelection.Explicit("unknown"), process)
        assertEquals(2, closes)
        assertEquals(listOf(VideoBridgeFailureCode.BACKEND_UNAVAILABLE, VideoBridgeFailureCode.BACKEND_UNAVAILABLE,
            VideoBridgeFailureCode.CLOSED, VideoBridgeFailureCode.CLOSED), codes)
        var release: VideoBridgeFailure? = null
        bridge.releaseOutput("unknown", object : VideoBridgeReleaseCallback {
            override fun onReleased() = error("unknown output must not succeed")
            override fun onFailure(failure: VideoBridgeFailure) { release = failure }
        })
        assertEquals(VideoBridgeFailureCode.INVALID_INPUT, release?.code)
    }

    @Test fun platformFailureCannotCarryRawDiagnostics() {
        assertFailsWith<IllegalArgumentException> { VideoBridgeFailure(VideoBridgeFailureCode.PLATFORM, "private path", "video", 1) }
        assertFailsWith<IllegalArgumentException> { VideoBridgeFailure(VideoBridgeFailureCode.PLATFORM, null, "", 1) }
        assertFailsWith<IllegalArgumentException> { VideoBridgeFailure(VideoBridgeFailureCode.PLATFORM, null, "video", null) }
        assertFailsWith<IllegalArgumentException> { VideoBridgeFailure(VideoBridgeFailureCode.BUSY, null, "video", 1) }
        assertEquals(1, VideoBridgeFailure(VideoBridgeFailureCode.PLATFORM, null, "video", 1).platformCode)
    }

    private fun output(reference: String = "output-token", width: Int = 1280, frames: Int = 48,
                       duration: Double = 2.0, audio: Int = 1) =
        VideoBridgeOutput(reference, "apple.video", width, 720, frames, duration, audio)
}
