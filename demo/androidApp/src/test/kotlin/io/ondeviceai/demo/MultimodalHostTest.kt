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

    @Test fun videoAndImageUseIndependentStoresAndLatestCrossMediaTicket() = runBlocking {
        val images = store()
        val videos = MultimodalInputStore(temporary.root.resolve("videos"))
        var imageReply: ((File?) -> Unit)? = null
        var videoReply: ((File?) -> Unit)? = null
        val host = MultimodalHost(images, videoStore = videos,
            selectVideo = { callback, _ -> videoReply = callback },
            selectFile = { callback, _ -> imageReply = callback })
        host.selectImageFile(); host.selectVideoFile()
        imageReply!!(source()); assertNull(host.model.state.inputReference)
        videoReply!!(source())
        val token = assertNotNull(host.model.state.inputReference)
        assertEquals(MultimodalInputKind.VIDEO, host.model.state.inputKind)
        assertFails { images.acquire(token) }
        val lease = videos.acquire(token)
        host.selectImageFile(); imageReply!!(source())
        assertFails { videos.acquire(token) }
        assertTrue(lease.file.exists())
        val image = assertNotNull(host.model.state.inputReference)
        assertFails { videos.acquire(image) }
        host.close(); assertTrue(lease.file.exists())
        lease.release(); assertFalse(lease.file.exists())
    }

    @Test fun asyncImportDoesNotPublishInlineAndClearCancelsBeforeCopy() = runBlocking {
        val queue = java.util.ArrayDeque<Runnable>()
        val dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queue.add(block) }
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)
        val directory = temporary.root.resolve("async-copies")
        var reply: ((File?) -> Unit)? = null
        val host = MultimodalHost(MultimodalInputStore(directory), importScope = scope,
            importDispatcher = dispatcher, selectFile = { callback, _ -> reply = callback })
        host.selectImageFile(); reply!!(source())
        assertNull(host.model.state.inputReference, "copy must not execute in picker callback")
        host.clearImageFile()
        while (queue.isNotEmpty()) queue.removeFirst().run()
        host.awaitImports()
        assertNull(host.model.state.inputReference)
        assertNull(host.model.state.failure)
        assertTrue(directory.listFiles()!!.isEmpty())
        host.close()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
    }

    @Test fun asyncQueuedImportsAreInvalidatedByEveryLifecycleBoundary() = runBlocking {
        for (action in listOf<(MultimodalHost) -> Unit>(
            { it.clearImageFile() }, { it.leavePage() }, { it.background() }, { it.close() },
        )) {
            val queue = java.util.ArrayDeque<Runnable>()
            val dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
                override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queue.add(block) }
            }
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)
            val directory = temporary.root.resolve("lifecycle-${System.nanoTime()}")
            var reply: ((File?) -> Unit)? = null
            val host = MultimodalHost(MultimodalInputStore(directory), importScope = scope,
                importDispatcher = dispatcher, selectFile = { callback, _ -> reply = callback })
            host.selectImageFile(); reply!!(source()); action(host)
            while (queue.isNotEmpty()) queue.removeFirst().run()
            host.awaitImports()
            assertNull(host.model.state.inputReference)
            assertNull(host.model.state.failure)
            host.close()
            assertFalse(directory.exists())
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        }
    }

    @Test fun asyncImportPublishesThenRetiresOnClose() = runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val directory = temporary.root.resolve("async-success")
        var reply: ((File?) -> Unit)? = null
        val host = MultimodalHost(MultimodalInputStore(directory), importScope = scope,
            importDispatcher = kotlinx.coroutines.Dispatchers.IO,
            selectFile = { callback, _ -> reply = callback })
        host.selectImageFile(); reply!!(source())
        host.awaitImports()
        assertNotNull(host.model.state.inputReference)
        host.close()
        assertFalse(directory.exists())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
    }

    @Test fun unavailableVideoPickerDoesNotLaunchImagePicker() {
        var launches = 0
        val host = MultimodalHost(store(), selectFile = { _, _ -> launches++ })
        host.selectVideoFile()
        assertEquals(0, launches)
        assertNull(host.model.state.inputReference)
        assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, host.model.state.failure?.code)
        host.close()
    }

    @Test fun replacementAndClearRetireOnlySelectionOwnership() = runBlocking {
        val store = store()
        var reply: ((File?) -> Unit)? = null
        val host = MultimodalHost(store, selectFile = { callback, _ -> reply = callback })
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
        val host = MultimodalHost(store(), selectFile = { callback, _ -> replies += callback })
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
        val host = MultimodalHost(store(), selectFile = { callback, _ -> if (throws) error("private details") else reply = callback })
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
            val host = MultimodalHost(MultimodalInputStore(directory), selectFile = { callback, _ -> launches++; reply = callback })
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
        val host = MultimodalHost(store, bridge, selectFile = { callback, _ -> reply = callback })
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
        val host = MultimodalHost(store, bridge, selectFile = { callback, _ -> reply = callback })
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
        val host = MultimodalHost(store(), selectFile = { callback, _ -> reply = callback })
        host.selectImageFile(); reply!!(source())
        host.model.refreshBackends(); host.model.generate()
        assertEquals(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, host.model.state.queryFailure?.code)
        assertEquals(MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE, host.model.state.failure?.code)
        assertNull(host.model.state.output)
        host.close()
    }

    @Test fun asynchronousPickerFailureUsesSameOnceOnlyTicketAsSuccess() {
        val replies = mutableListOf<(File?) -> Unit>()
        val failures = mutableListOf<() -> Unit>()
        val host = MultimodalHost(store(), selectFile = { reply, failure -> replies += reply; failures += failure })
        host.selectImageFile(); host.leavePage(); failures[0]()
        assertNull(host.model.state.failure)
        host.selectImageFile(); failures[1]()
        assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, host.model.state.failure?.code)
        replies[1](source())
        assertNull(host.model.state.inputReference)
        host.selectImageFile(); replies[2](source()); failures[2]()
        assertNotNull(host.model.state.inputReference)
        assertNull(host.model.state.failure)
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
