package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoViewModelTest {
    @Test fun synchronousTerminalDoesNotLeaveBusyHandle() {
        val bridge = FakeVideoBridge().apply { synchronous = true }
        val model = VideoViewModel(bridge)
        model.selectVideo("input", "fixture.mov")
        model.process()
        assertEquals(VideoPhase.SUCCEEDED, model.state.phase)
        model.process()
        assertEquals(2, bridge.requests.size)
        assertEquals(listOf("out-1"), bridge.releases)
        assertEquals(0, bridge.tasks.sumOf { it.cancels })
    }

    @Test fun cancellationWaitsForTerminalAndDiscardsLateSuccess() {
        val bridge = FakeVideoBridge(); val model = VideoViewModel(bridge)
        model.selectVideo("input", null); model.process(); model.cancel()
        assertEquals(VideoPhase.CANCELLING, model.state.phase)
        model.process()
        assertEquals(1, bridge.requests.size)
        bridge.callbacks[0].onResult(output("late"))
        assertEquals(VideoPhase.CANCELLED, model.state.phase)
        assertNull(model.state.output)
        assertEquals(listOf("late"), bridge.releases)
        model.process()
        assertEquals(2, bridge.requests.size)
    }

    @Test fun oldProgressCannotChangeNextOperationOrReviveCancelledPage() {
        val bridge = FakeVideoBridge(); val model = VideoViewModel(bridge)
        model.selectVideo("input", null); model.process()
        bridge.callbacks[0].onCancelled(); model.process()
        bridge.callbacks[0].onProgress(VideoBridgeProgress(48, 48))
        assertNull(model.state.progress)
        bridge.callbacks[1].onProgress(VideoBridgeProgress(1, 48))
        assertEquals(1, model.state.progress?.completedFrames)
        model.background()
        bridge.callbacks[1].onProgress(VideoBridgeProgress(2, 48))
        assertEquals(1, model.state.progress?.completedFrames)
        bridge.callbacks[1].onResult(output("background-output"))
        assertNull(model.state.output)
        assertEquals(listOf("background-output"), bridge.releases)
        assertEquals(2, bridge.requests.size)
    }

    @Test fun queryGenerationIgnoresLateResultsAndCloseRejectsNewWork() {
        val bridge = FakeVideoBridge(); val model = VideoViewModel(bridge)
        model.refreshBackends(); model.refreshBackends()
        bridge.queries[0].onFailure(VideoBridgeFailure(VideoBridgeFailureCode.PLATFORM, null, "test", 1))
        assertTrue(model.state.refreshing)
        bridge.queries[1].onResult(emptyList())
        assertFalse(model.state.refreshing)
        model.close(); model.refreshBackends(); model.process()
        assertEquals(2, bridge.queries.size)
        assertTrue(model.state.closed)
        assertEquals(VideoBridgeFailureCode.CLOSED, model.state.failure?.code)
    }

    @Test fun outputIsReleasedOnReplacementLeaveAndCloseOnlyOnce() {
        val bridge = FakeVideoBridge().apply { synchronous = true }
        val model = VideoViewModel(bridge)
        model.selectVideo("input", null); model.process(); model.process()
        model.leavePage(); model.leavePage(); model.close()
        assertEquals(listOf("out-1", "out-2"), bridge.releases)
    }

    @Test fun failedReleaseRetainsReferenceForExplicitRetryAfterClose() {
        val bridge = FakeVideoBridge().apply { synchronous = true; failRelease = true }
        val model = VideoViewModel(bridge)
        model.selectVideo("input", null); model.process(); model.close()
        assertEquals(VideoBridgeFailureCode.PLATFORM, model.state.releaseFailure?.code)
        bridge.failRelease = false
        model.retryReleases()
        assertEquals(listOf("out-1", "out-1"), bridge.releases)
        assertNull(model.state.releaseFailure)
        assertEquals(0, model.state.pendingReleases)
    }

    @Test fun strictSelectionAndAudioPolicyReachBridgeUnchanged() {
        val bridge = FakeVideoBridge(); val model = VideoViewModel(bridge)
        model.selectVideo("token", "user selected.mov")
        model.selectBackend(VideoBridgeSelection.Explicit("missing"))
        model.selectAudioPolicy(VideoBridgeAudioPolicy.DISCARD); model.process()
        assertEquals(VideoBridgeSelection.Explicit("missing"), bridge.selections.single())
        assertEquals(VideoBridgeAudioPolicy.DISCARD, bridge.requests.single().audioPolicy)
        assertEquals("token", bridge.requests.single().inputReference)
        assertEquals(4, bridge.requests.single().scaleFactor)
    }

    @Test fun closeDuringWorkReleasesLateOutputAndNeverRestarts() {
        val bridge = FakeVideoBridge(); val model = VideoViewModel(bridge)
        model.selectVideo("input", null); model.process(); model.close()
        bridge.callbacks[0].onResult(output("after-close"))
        assertNull(model.state.output)
        assertEquals(listOf("after-close"), bridge.releases)
        assertEquals(1, bridge.requests.size)
    }
}

private fun output(token: String) = VideoBridgeOutput(token, "test", 1280, 720, 48, 2.0, 1)
private class FakeVideoTask : BridgeTask { var cancels = 0; override fun cancel() { cancels++ } }
private class FakeVideoBridge : VideoBridge {
    var synchronous = false; var failRelease = false
    val tasks = mutableListOf<FakeVideoTask>()
    val callbacks = mutableListOf<VideoBridgeProcessCallback>()
    val queries = mutableListOf<VideoBridgeBackendsCallback>()
    val requests = mutableListOf<VideoBridgeRequest>()
    val selections = mutableListOf<VideoBridgeSelection>()
    val releases = mutableListOf<String>()
    override fun queryBackends(callback: VideoBridgeBackendsCallback): BridgeTask {
        queries += callback; return FakeVideoTask()
    }
    override fun process(request: VideoBridgeRequest, selection: VideoBridgeSelection, callback: VideoBridgeProcessCallback): BridgeTask {
        requests += request; selections += selection; callbacks += callback
        val task = FakeVideoTask(); tasks += task
        if (synchronous) callback.onResult(output("out-${requests.size}"))
        return task
    }
    override fun releaseOutput(reference: String, callback: VideoBridgeReleaseCallback) {
        releases += reference
        if (failRelease) callback.onFailure(VideoBridgeFailure(VideoBridgeFailureCode.PLATFORM, null, "test", 1))
        else callback.onReleased()
    }
    override fun close(callback: VideoBridgeCloseCallback) { callback.onClosed() }
}
