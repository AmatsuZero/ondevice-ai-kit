package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.*

class MultimodalViewModelTest {
    @Test fun invalidRequestsNeverReachBridgeAndValidRequestPreservesRawFields() {
        val bridge = ControlledMultimodalBridge()
        val model = MultimodalViewModel(bridge)
        model.generate()
        assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, model.state.failure?.code)
        model.selectImage("opaque-token", "fixture.png")
        model.setPrompt(" \n")
        model.generate()
        assertFalse(model.state.canGenerate)
        model.setPrompt(" Describe. \n")
        for (budget in listOf("", "not-a-number", "0", "-1", "2147483648")) {
            model.setTokenBudget(budget); model.generate()
            assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, model.state.failure?.code)
            assertFalse(model.state.canGenerate)
        }
        assertEquals(0, bridge.requests.size)
        model.setTokenBudget("37")
        model.selectBackend(MultimodalBridgeSelection.Explicit(" strict.id "))
        assertTrue(model.state.canGenerate)
        model.generate()
        assertEquals(MultimodalBridgeRequest("opaque-token", " Describe. \n", 37), bridge.requests.single())
        assertEquals(MultimodalBridgeSelection.Explicit(" strict.id "), bridge.selections.single())
        assertFalse(model.state.controlsEnabled)
        assertEquals(MultimodalPhase.RUNNING, model.state.phase)
    }

    @Test fun synchronousTerminalDoesNotRetainHandleAndOutputIdentityIsAuthoritative() {
        val bridge = ControlledMultimodalBridge().apply { syncGenerate = true }
        val model = MultimodalViewModel(bridge)
        model.selectImage("input", null); model.generate()
        assertEquals(MultimodalPhase.SUCCEEDED, model.state.phase)
        assertEquals("actual.backend", model.state.output?.backendId)
        assertEquals("runtime1", model.state.output?.model?.runtimeRevision)
        model.cancel(); model.generate()
        assertEquals(2, bridge.requests.size)
        assertEquals(0, bridge.tasks.sumOf { it.cancels })
    }

    @Test fun cancellingHoldsSlotUntilTerminalAndWinsLateResultOrFailure() {
        for (lateFailure in listOf(false, true)) {
            val bridge = ControlledMultimodalBridge()
            val model = MultimodalViewModel(bridge)
            model.selectImage("input", null); model.generate(); model.cancel(); model.cancel()
            assertEquals(MultimodalPhase.CANCELLING, model.state.phase)
            assertEquals(1, bridge.tasks.single().cancels)
            model.generate()
            assertEquals(1, bridge.requests.size)
            if (lateFailure) bridge.callbacks[0].onMultimodalFailure(platformFailure())
            else bridge.callbacks[0].onMultimodalResult(output())
            assertEquals(MultimodalPhase.CANCELLED, model.state.phase)
            assertNull(model.state.failure); assertNull(model.state.output)
            assertTrue(model.state.controlsEnabled)
            model.generate()
            assertEquals(2, bridge.requests.size)
        }
    }

    @Test fun duplicateRegressiveCancelledAndOldUpdatesNeverReviveState() {
        val bridge = ControlledMultimodalBridge(); val model = MultimodalViewModel(bridge)
        model.selectImage("input", null); model.generate()
        val old = bridge.callbacks[0]
        old.onMultimodalUpdate(MultimodalBridgeUpdate(2, "A red"))
        old.onMultimodalUpdate(MultimodalBridgeUpdate(2, "duplicate"))
        old.onMultimodalUpdate(MultimodalBridgeUpdate(1, "regressive"))
        assertEquals("A red", model.state.update?.text)
        model.cancel(); old.onMultimodalUpdate(MultimodalBridgeUpdate(3, "cancelled"))
        assertEquals(2, model.state.update?.sequence)
        old.onMultimodalCancelled(); model.generate()
        old.onMultimodalResult(output()); old.onMultimodalFailure(platformFailure())
        old.onMultimodalUpdate(MultimodalBridgeUpdate(99, "old"))
        assertEquals(MultimodalPhase.RUNNING, model.state.phase)
        assertNull(model.state.update); assertNull(model.state.output); assertNull(model.state.failure)
        bridge.callbacks[1].onMultimodalResult(output())
        bridge.callbacks[1].onMultimodalFailure(platformFailure())
        assertEquals(MultimodalPhase.SUCCEEDED, model.state.phase)
    }

    @Test fun editsCancelOnceKeepNewValuesAndWaitForOldDrainTerminal() {
        val bridge = ControlledMultimodalBridge(); val model = MultimodalViewModel(bridge)
        model.selectImage("old", "old.png"); model.generate()
        bridge.callbacks[0].onMultimodalUpdate(MultimodalBridgeUpdate(1, "old text"))
        model.selectImage("new", "new.png")
        model.setPrompt("new prompt"); model.setTokenBudget("12")
        model.selectBackend(MultimodalBridgeSelection.Explicit("new.backend"))
        assertEquals(MultimodalPhase.CANCELLING, model.state.phase)
        assertEquals(1, bridge.tasks[0].cancels)
        assertNull(model.state.update)
        model.generate(); assertEquals(1, bridge.requests.size)
        bridge.callbacks[0].onMultimodalResult(output())
        assertNull(model.state.output)
        model.generate()
        assertEquals(MultimodalBridgeRequest("new", "new prompt", 12), bridge.requests[1])
        assertEquals(MultimodalBridgeSelection.Explicit("new.backend"), bridge.selections[1])
    }

    @Test fun eachRequestEditIndependentlyCancelsButIdenticalEditsDoNot() {
        val edits: List<(MultimodalViewModel) -> Unit> = listOf(
            { it.selectImage("new", null) }, { it.setPrompt("new") },
            { it.setTokenBudget("12") }, { it.selectBackend(MultimodalBridgeSelection.Explicit("new")) })
        for (edit in edits) {
            val bridge = ControlledMultimodalBridge(); val model = MultimodalViewModel(bridge)
            model.selectImage("input", "fixture.png"); model.generate()
            model.selectImage("input", "fixture.png"); model.setPrompt("Describe this image.")
            model.setTokenBudget("32"); model.selectBackend(MultimodalBridgeSelection.Default)
            assertEquals(0, bridge.tasks[0].cancels)
            edit(model)
            assertEquals(1, bridge.tasks[0].cancels)
            assertEquals(MultimodalPhase.CANCELLING, model.state.phase)
        }
    }

    @Test fun staleAndDuplicateQueriesCannotOverrideNewResults() {
        val bridge = ControlledMultimodalBridge(); val model = MultimodalViewModel(bridge)
        model.refreshBackends(); model.refreshBackends()
        bridge.queries[0].onMultimodalFailure(platformFailure())
        assertTrue(model.state.refreshing); assertNull(model.state.queryFailure)
        val backend = MultimodalBridgeBackend("actual", BridgeAvailability(false, "missingAssets", "actual", "18"))
        bridge.queries[1].onMultimodalBackends(listOf(backend))
        bridge.queries[1].onMultimodalFailure(platformFailure())
        assertFalse(model.state.refreshing); assertNull(model.state.queryFailure)
        assertEquals(listOf(backend), model.state.backends)
        model.leavePage(); bridge.queries[1].onMultimodalBackends(emptyList())
        assertEquals(listOf(backend), model.state.backends)
    }

    @Test fun synchronousQueryCompletionDoesNotLeaveHandleToCancel() {
        val bridge = ControlledMultimodalBridge().apply { syncQuery = true }
        val model = MultimodalViewModel(bridge)
        model.refreshBackends(); model.refreshBackends(); model.leavePage()
        assertFalse(model.state.refreshing)
        assertEquals(0, bridge.queryTasks.sumOf { it.cancels })
    }

    @Test fun backgroundInvalidatesQueryCancelsGenerationAndNeverRestarts() {
        val bridge = ControlledMultimodalBridge(); val model = MultimodalViewModel(bridge)
        model.selectImage("input", null); model.refreshBackends(); model.generate(); model.background()
        assertEquals(MultimodalPhase.CANCELLING, model.state.phase)
        bridge.queries[0].onMultimodalFailure(platformFailure())
        bridge.callbacks[0].onMultimodalUpdate(MultimodalBridgeUpdate(5, "late"))
        bridge.callbacks[0].onMultimodalResult(output())
        assertNull(model.state.output); assertNull(model.state.update); assertNull(model.state.queryFailure)
        assertEquals(MultimodalPhase.CANCELLED, model.state.phase)
        assertEquals(1, bridge.requests.size); assertEquals(1, bridge.queries.size)
    }

    @Test fun closeIsOnceSealsAdmissionAndIgnoresLateCallbacksAfterCompletion() {
        val bridge = ControlledMultimodalBridge(); val model = MultimodalViewModel(bridge)
        model.selectImage("input", null); model.generate(); model.close(); model.close()
        assertTrue(model.state.closed); assertTrue(model.state.closing)
        assertFalse(model.state.controlsEnabled); assertEquals(1, bridge.closes.size)
        bridge.closes[0].onMultimodalClosed()
        assertFalse(model.state.closing)
        bridge.callbacks[0].onMultimodalResult(output())
        bridge.callbacks[0].onMultimodalFailure(platformFailure())
        bridge.closes[0].onMultimodalFailure(platformFailure())
        assertNull(model.state.output); assertNull(model.state.failure); assertNull(model.state.closeFailure)
        model.generate(); model.refreshBackends(); model.setPrompt("after close")
        assertEquals(MultimodalBridgeFailureCode.CLOSED, model.state.failure?.code)
        assertEquals(1, bridge.requests.size); assertEquals(0, bridge.queries.size)
        assertEquals("Describe this image.", model.state.prompt)
    }

    @Test fun failedCloseRemainsClosedAndPreservesPrivateTypedFailure() {
        val bridge = ControlledMultimodalBridge(); val model = MultimodalViewModel(bridge)
        model.close(); bridge.closes.single().onMultimodalFailure(platformFailure())
        assertTrue(model.state.closed); assertFalse(model.state.closing)
        assertEquals(platformFailure(), model.state.closeFailure)
        model.close(); assertEquals(1, bridge.closes.size)
    }

    @Test fun defaultBridgeIsExplicitlyUnavailableAndInputFailureDoesNotAcquireOrStart() {
        val model = MultimodalViewModel()
        model.refreshBackends()
        assertEquals("backendNotLinked", model.state.queryFailure?.reason)
        model.selectImage("input", null); model.generate()
        assertEquals(MultimodalPhase.FAILED, model.state.phase)
        assertEquals(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, model.state.failure?.code)
        model.inputFailed()
        assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, model.state.failure?.code)
        model.selectImage(null, "stale name"); assertNull(model.state.inputDisplayName)
        model.close(); assertFalse(model.state.closing)
    }
}

private fun output() = MultimodalBridgeOutput("actual.backend", "A red square.",
    MultimodalBridgeIdentity("fixture", "r1", "runtime1"))
private fun platformFailure() = MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, null, "test.multimodal", 1)
private class ControlledTask : BridgeTask { var cancels = 0; override fun cancel() { cancels++ } }
/** Only the platform boundary is controlled; tests assert real ViewModel state. */
private class ControlledMultimodalBridge : MultimodalBridge {
    var syncGenerate = false; var syncQuery = false
    val requests = mutableListOf<MultimodalBridgeRequest>()
    val selections = mutableListOf<MultimodalBridgeSelection>()
    val callbacks = mutableListOf<MultimodalBridgeGenerateCallback>()
    val queries = mutableListOf<MultimodalBridgeBackendsCallback>()
    val tasks = mutableListOf<ControlledTask>(); val queryTasks = mutableListOf<ControlledTask>()
    val closes = mutableListOf<MultimodalBridgeCloseCallback>()
    override fun queryMultimodalBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask {
        queries += callback
        val task = ControlledTask(); queryTasks += task
        if (syncQuery) callback.onMultimodalBackends(emptyList())
        return task
    }
    override fun generateMultimodal(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection,
                                   callback: MultimodalBridgeGenerateCallback): BridgeTask {
        requests += request; selections += selection; callbacks += callback
        val task = ControlledTask(); tasks += task
        if (syncGenerate) callback.onMultimodalResult(output())
        return task
    }
    override fun closeMultimodal(callback: MultimodalBridgeCloseCallback) { closes += callback }
}
