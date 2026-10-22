package io.ondeviceai.demo

import io.ondeviceai.binding.*
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class MultimodalHostTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun source() = temporary.newFile("synthetic-${System.nanoTime()}.png").apply { writeText("fixture") }
    private fun store() = MultimodalInputStore(temporary.root.resolve("copies"))

    @Test fun replacementAndClearRetireOnlySelectionOwnership() = runBlocking {
        val store = store()
        var reply: ((File?) -> Unit)? = null
        val host = MultimodalHost(store, selectFile = { reply = it })
        val source = source()
        host.selectImageFile(); reply!!(source)
        val first = assertNotNull(host.model.state.inputReference)
        assertEquals(source.name, host.model.state.inputDisplayName)
        val lease = store.acquire(first)
        host.selectImageFile(); reply!!(source)
        val second = assertNotNull(host.model.state.inputReference)
        assertNotEquals(first, second)
        assertFails { store.acquire(first) }
        assertEquals("fixture", lease.file.readText())
        val secondLease = store.acquire(second)
        host.clearImageFile()
        assertNull(host.model.state.inputReference)
        assertFails { store.acquire(second) }
        secondLease.release()
        assertFalse(secondLease.file.exists())
        assertTrue(lease.file.exists())
        host.close()
        assertTrue(lease.file.exists())
        lease.release()
        assertFalse(lease.file.exists())
        assertEquals("fixture", source.readText())
    }

    @Test fun onlyLatestPickerReplyIsConsumedOnceIncludingCancel() {
        val replies = mutableListOf<(File?) -> Unit>()
        val host = MultimodalHost(store(), selectFile = { replies += it })
        val source = source()
        host.selectImageFile(); host.selectImageFile()
        replies[0](source)
        assertNull(host.model.state.inputReference)
        replies[1](source)
        val reference = host.model.state.inputReference
        replies[1](source)
        assertEquals(reference, host.model.state.inputReference)
        host.selectImageFile(); replies[2](null); replies[2](source)
        assertEquals(reference, host.model.state.inputReference)
        host.close()
    }

    @Test fun importAndLaunchFailuresPreserveSelectionAndUseTypedFailure() {
        var reply: ((File?) -> Unit)? = null
        var throws = false
        val host = MultimodalHost(store(), selectFile = { if (throws) error("private details") else reply = it })
        host.selectImageFile(); reply!!(source())
        val selected = host.model.state.inputReference
        host.selectImageFile(); reply!!(temporary.root)
        assertEquals(selected, host.model.state.inputReference)
        assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, host.model.state.failure?.code)
        throws = true
        host.selectImageFile()
        assertEquals(selected, host.model.state.inputReference)
        assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, host.model.state.failure?.code)
        host.close()
    }

    @Test fun clearLeaveBackgroundAndCloseInvalidateLatePickerReplies() {
        for (action in listOf<(MultimodalHost) -> Unit>(
            { it.clearImageFile() }, { it.leavePage() }, { it.background() }, { it.close() },
        )) {
            val directory = temporary.root.resolve("copies-${System.nanoTime()}")
            var reply: ((File?) -> Unit)? = null
            var launches = 0
            val host = MultimodalHost(MultimodalInputStore(directory), selectFile = { launches++; reply = it })
            host.selectImageFile(); action(host); reply!!(source())
            assertNull(host.model.state.inputReference)
            if (!host.model.state.closed) assertTrue(directory.listFiles()!!.isEmpty())
            host.background(); host.selectImageFile()
            assertEquals(1, launches)
            host.close()
        }
    }

    @Test fun preservedExternalPickerMayFinishBeforeForegroundButNeverGenerates() {
        val store = store()
        val bridge = DrainingBridge(store)
        var reply: ((File?) -> Unit)? = null
        val host = MultimodalHost(store, bridge, selectFile = { reply = it })
        host.selectImageFile(); host.background(preservePicker = true); reply!!(source())
        assertNotNull(host.model.state.inputReference)
        host.onForeground()
        assertEquals(0, bridge.starts)
        host.selectImageFile(); host.background(preservePicker = true); host.leavePage()
        val selected = host.model.state.inputReference
        reply!!(source())
        assertEquals(selected, host.model.state.inputReference)
        host.close()
    }

    @Test fun activeCancelAndCloseWaitForDrainAndCloseOnlyOnce() = runBlocking {
        val store = store()
        val bridge = DrainingBridge(store)
        var reply: ((File?) -> Unit)? = null
        val host = MultimodalHost(store, bridge, selectFile = { reply = it })
        host.selectImageFile(); reply!!(source())
        host.model.generate()
        val lease = assertNotNull(bridge.lease)
        assertEquals(MultimodalPhase.RUNNING, host.model.state.phase)
        host.background(); host.onForeground()
        assertEquals(1, bridge.starts)
        assertEquals(MultimodalPhase.CANCELLING, host.model.state.phase)
        host.clearImageFile(); host.close(); host.close()
        assertEquals(1, bridge.closes)
        assertTrue(host.model.state.closing)
        assertTrue(lease.file.exists())
        assertFails { store.acquire(bridge.reference!!) }
        bridge.finishDrain()
        assertFalse(lease.file.exists())
        assertFalse(host.model.state.closing)
        host.model.generate()
        assertEquals(MultimodalBridgeFailureCode.CLOSED, host.model.state.failure?.code)
        assertEquals(1, bridge.starts)
    }

    @Test fun defaultHostRemainsExplicitlyUnavailable() {
        var reply: ((File?) -> Unit)? = null
        val host = MultimodalHost(store(), selectFile = { reply = it })
        host.selectImageFile(); reply!!(source())
        host.model.refreshBackends(); host.model.generate()
        assertEquals(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, host.model.state.queryFailure?.code)
        assertEquals(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, host.model.state.failure?.code)
        assertNull(host.model.state.output)
        host.close()
    }
}

private class DrainingBridge(private val store: MultimodalInputStore) : MultimodalBridge {
    var starts = 0
    var closes = 0
    var reference: String? = null
    var lease: AndroidMultimodalInputLease? = null
    private var generation: MultimodalBridgeGenerateCallback? = null
    private var closing: MultimodalBridgeCloseCallback? = null
    override fun queryMultimodalBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask {
        callback.onMultimodalBackends(emptyList())
        return object : BridgeTask { override fun cancel() = Unit }
    }
    override fun generateMultimodal(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection,
                                   callback: MultimodalBridgeGenerateCallback): BridgeTask {
        starts++; reference = request.inputReference; lease = store.acquire(request.inputReference); generation = callback
        return object : BridgeTask { override fun cancel() = Unit }
    }
    override fun closeMultimodal(callback: MultimodalBridgeCloseCallback) {
        closes++
        if (generation == null) callback.onMultimodalClosed() else closing = callback
    }
    suspend fun finishDrain() {
        lease?.release(); generation?.onMultimodalCancelled(); generation = null
        closing?.onMultimodalClosed(); closing = null
    }
}
