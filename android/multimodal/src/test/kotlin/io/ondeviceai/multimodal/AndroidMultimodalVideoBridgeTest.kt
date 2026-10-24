package io.ondeviceai.multimodal

import io.ondeviceai.binding.*
import io.ondeviceai.core.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*

class AndroidMultimodalVideoBridgeTest {
    @Test fun unlinkedAndClosedNeverAcquireInput() = runBlocking {
        VideoBridgeFixture(null).use { f ->
            val first = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, first) }
            assertEquals("backendNotLinked", first.failure.await().reason)
            assertEquals(0, f.inputs.acquires.get())
            f.closeBridge()
            val rejected = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, rejected) }
            assertEquals(MultimodalBridgeFailureCode.CLOSED, rejected.failure.await().code)
            assertEquals(0, f.inputs.acquires.get())
        }
    }

    @Test fun exactInputSelectionIdentityAndReentrantResultUseUi() = runBlocking {
        val a = VideoBridgeBackend("a"); val b = VideoBridgeBackend("b")
        VideoBridgeFixture(MultimodalGenerationClient("a", listOf(a, b))).use { f ->
            val second = VideoBridgeGenerateCapture()
            val first = VideoBridgeGenerateCapture { f.bridge.generateMultimodal(MultimodalBridgeRequest("input", "describe"), MultimodalBridgeSelection.Default, second) }
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Explicit("b"), first) }
            val output = withTimeout(TIMEOUT) { first.output.await() }
            assertEquals("b", output.backendId); assertEquals("A red square.", output.text)
            assertEquals(MultimodalBridgeIdentity("fixture", "r1", "runtime1"), output.model)
            assertEquals("result", withTimeout(TIMEOUT) { second.terminal.await() })
            assertEquals(MultimodalVideoRequest(File("/opaque/input.mp4"), " Describe. \n", 37), b.request)
            assertEquals(1, a.starts.get()); assertEquals(2, f.inputs.releases.get())
            first.assertUi(); second.assertUi()
            f.closeBridge()
            assertEquals("A red square.", output.text)
        }
    }

    @Test fun explicitMissingUnavailableNeverFallbackAndQueryPreservesMetadata() = runBlocking {
        val a = VideoBridgeBackend("a"); val b = VideoBridgeBackend("b", available = false)
        VideoBridgeFixture(MultimodalGenerationClient("a", listOf(b, a))).use { f ->
            val query = VideoBridgeBackendsCapture()
            f.ui { f.bridge.queryMultimodalBackends(query) }
            val backends = withTimeout(TIMEOUT) { query.backends.await() }
            assertEquals(listOf("a", "b"), backends.map { it.backendId })
            assertFalse(backends[1].availability.available)
            assertEquals("MISSING_ASSETS", backends[1].availability.reason)
            assertEquals("26", backends[1].availability.minimumOS)
            for ((id, code) in listOf("missing" to MultimodalBridgeFailureCode.BACKEND_NOT_REGISTERED,
                "b" to MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE)) {
                val callback = VideoBridgeGenerateCapture()
                f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Explicit(id), callback) }
                assertEquals(code, withTimeout(TIMEOUT) { callback.failure.await() }.code)
            }
            assertEquals(0, a.starts.get()); assertEquals(0, b.starts.get())
            query.assertUi()
        }
    }

    @Test fun updatesAreCumulativeOrderedUiAndRejectedAfterCancelOrTerminal() = runBlocking {
        val backend = VideoBridgeBackend(blockGenerate = true)
        VideoBridgeFixture(videoBridgeClient(backend)).use { f ->
            val callback = VideoBridgeGenerateCapture()
            val task = f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback) }
            backend.generating.entered.await()
            backend.emit(2, "red"); backend.emit(2, "duplicate"); backend.emit(1, "stale")
            withTimeout(TIMEOUT) { callback.updated.await() }
            f.ui { assertEquals(listOf(MultimodalBridgeUpdate(2, "red")), callback.updates) }
            // Cancel from a non-UI thread, then emit a late native update/error.
            task.cancel(); backend.emit(3, "late"); backend.generating.open()
            assertEquals("cancelled", withTimeout(TIMEOUT) { callback.terminal.await() })
            backend.emit(4, "terminal")
            f.ui { assertEquals(1, callback.updates.size); callback.assertOnly("cancelled"); callback.assertUi() }
        }
    }

    @Test fun cancellationWinsLateErrorAndKeepsSlotAndLeaseThroughDrainAndRelease() = runBlocking {
        for (fail in listOf(false, true)) {
            val backend = VideoBridgeBackend(blockGenerate = true, blockDrain = true, fail = fail)
            val inputs = VideoBridgeInputs(blockRelease = true)
            VideoBridgeFixture(videoBridgeClient(backend), inputs).use { f ->
                val callback = VideoBridgeGenerateCapture()
                val task = f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback) }
                backend.generating.entered.await(); task.cancel(); backend.generating.open()
                backend.draining.entered.await()
                assertEquals(0, inputs.releases.get()); assertFalse(callback.terminal.isCompleted)
                val imageBusy = VideoBridgeGenerateCapture()
                f.ui { f.bridge.generateMultimodal(MultimodalBridgeRequest("image", "describe"), MultimodalBridgeSelection.Default, imageBusy) }
                assertEquals(MultimodalBridgeFailureCode.BUSY, imageBusy.failure.await().code)
                val busy = VideoBridgeGenerateCapture()
                f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, busy) }
                assertEquals(MultimodalBridgeFailureCode.BUSY, busy.failure.await().code)
                backend.draining.open(); inputs.releasing.entered.await()
                assertFalse(callback.terminal.isCompleted)
                val stillBusy = VideoBridgeGenerateCapture()
                f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, stillBusy) }
                assertEquals(MultimodalBridgeFailureCode.BUSY, stillBusy.failure.await().code)
                inputs.releasing.open()
                assertEquals("cancelled", withTimeout(TIMEOUT) { callback.terminal.await() })
                assertEquals(1, inputs.releases.get()); callback.assertOnly("cancelled")
            }
        }
    }

    @Test fun immediateCancelStillReleasesExactlyOnceWithoutStartingNative() = runBlocking {
        val backend = VideoBridgeBackend()
        VideoBridgeFixture(videoBridgeClient(backend)).use { f ->
            val callback = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback).cancel() }
            assertEquals("cancelled", withTimeout(TIMEOUT) { callback.terminal.await() })
            assertEquals(1, f.inputs.releases.get()); assertEquals(0, backend.starts.get())
        }
    }

    @Test fun cancelWinsOrdinaryLeaseErrorAfterDrain() = runBlocking {
        val backend = VideoBridgeBackend(blockGenerate = true)
        VideoBridgeFixture(videoBridgeClient(backend), VideoBridgeInputs(failRelease = true)).use { f ->
            val callback = VideoBridgeGenerateCapture()
            val task = f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback) }
            backend.generating.entered.await(); task.cancel(); backend.generating.open()
            assertEquals("cancelled", withTimeout(TIMEOUT) { callback.terminal.await() })
            assertEquals(1, f.inputs.releases.get()); callback.assertOnly("cancelled")
        }
    }

    @Test fun hostCancellationAndRepeatedCloseJoinNoncancellableQuery() = runBlocking {
        val backend = VideoBridgeBackend(blockQuery = true)
        VideoBridgeFixture(videoBridgeClient(backend)).use { f ->
            val query = VideoBridgeBackendsCapture()
            f.ui { f.bridge.queryMultimodalBackends(query) }; backend.querying.entered.await()
            f.cancelHost()
            val one = VideoBridgeCloseCapture(); val two = VideoBridgeCloseCapture()
            f.ui { f.bridge.closeMultimodal(one); f.bridge.closeMultimodal(two) }
            assertFalse(one.terminal.isCompleted); assertEquals(0, backend.closes.get())
            val rejected = VideoBridgeBackendsCapture()
            f.ui { f.bridge.queryMultimodalBackends(rejected) }
            assertEquals("failure", rejected.terminal.await())
            backend.querying.open()
            assertEquals("cancelled", withTimeout(TIMEOUT) { query.terminal.await() })
            assertEquals("closed", one.terminal.await()); assertEquals("closed", two.terminal.await())
            assertEquals(1, backend.closes.get()); one.assertUi(); query.assertOnly("cancelled")
        }
    }

    @Test fun hostCancellationDoesNotLoseGenerationDrainAndLease() = runBlocking {
        val backend = VideoBridgeBackend(blockGenerate = true, blockDrain = true)
        VideoBridgeFixture(videoBridgeClient(backend)).use { f ->
            val callback = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback) }
            backend.generating.entered.await(); f.cancelHost()
            val close = VideoBridgeCloseCapture(); f.ui { f.bridge.closeMultimodal(close) }
            backend.generating.open(); backend.draining.entered.await()
            assertFalse(close.terminal.isCompleted); assertEquals(0, f.inputs.releases.get())
            backend.draining.open()
            assertEquals("closed", withTimeout(TIMEOUT) { close.terminal.await() })
            assertEquals("cancelled", callback.terminal.await()); assertEquals(1, f.inputs.releases.get())
        }
    }

    @Test fun unknownErrorsAndInputFailuresDoNotLeakPrivateMessages() = runBlocking {
        val backend = VideoBridgeBackend(fail = true)
        VideoBridgeFixture(videoBridgeClient(backend)).use { f ->
            val callback = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback) }
            val failure = withTimeout(TIMEOUT) { callback.failure.await() }
            assertEquals(MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, null, "android.multimodal", 1), failure)
            assertEquals(1, f.inputs.releases.get())
            val bad = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest().copy(inputReference = "bad"), MultimodalBridgeSelection.Default, bad) }
            assertEquals(MultimodalBridgeFailure(MultimodalBridgeFailureCode.INVALID_INPUT, null), bad.failure.await())
        }
    }
    @Test fun legacyNativeBackendRejectsVideoAndReleasesInputWithoutQuerying() = runBlocking<Unit> {
        val queries = AtomicInteger()
        val legacy = object : MultimodalGenerationBackend {
            override val backendId = "legacy"
            override suspend fun availability(): CapabilityAvailability {
                queries.incrementAndGet(); return CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
            }
            override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput = error("must not fallback")
            override suspend fun drain() {}
            override suspend fun close() {}
        }
        VideoBridgeFixture(MultimodalGenerationClient("legacy", listOf(legacy))).use { f ->
            val callback = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback) }
            assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, withTimeout(TIMEOUT) { callback.failure.await() }.code)
            assertEquals(1, f.inputs.releases.get()); assertEquals(0, queries.get())
        }
    }

    @Test fun absentVideoProviderNeverUsesImageProvider() = runBlocking<Unit> {
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, MAIN).apply { isDaemon = true } }.asCoroutineDispatcher()
        try {
            val inputs = VideoBridgeInputs(); val backend = VideoBridgeBackend()
            val bridge = AndroidMultimodalBridge(videoBridgeClient(backend), inputs, CoroutineScope(SupervisorJob() + dispatcher))
            val callback = VideoBridgeGenerateCapture()
            withContext(dispatcher) { bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, callback) }
            assertEquals(MultimodalBridgeFailureCode.INVALID_INPUT, callback.failure.await().code)
            assertEquals(0, inputs.acquires.get()); assertEquals(0, backend.starts.get())
            val close = VideoBridgeCloseCapture(); withContext(dispatcher) { bridge.closeMultimodal(close) }; close.terminal.await()
        } finally { dispatcher.close() }
    }

    @Test fun imageAdmissionAlsoBlocksVideoBeforeAcquisition() = runBlocking<Unit> {
        val backend = VideoBridgeBackend(blockGenerate = true)
        VideoBridgeFixture(videoBridgeClient(backend)).use { f ->
            val image = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodal(MultimodalBridgeRequest("input", "describe"), MultimodalBridgeSelection.Default, image) }
            backend.generating.entered.await()
            val acquired = f.inputs.acquires.get(); val video = VideoBridgeGenerateCapture()
            f.ui { f.bridge.generateMultimodalVideo(videoBridgeRequest(), MultimodalBridgeSelection.Default, video) }
            assertEquals(MultimodalBridgeFailureCode.BUSY, video.failure.await().code)
            assertEquals(acquired, f.inputs.acquires.get())
            backend.generating.open(); withTimeout(TIMEOUT) { image.terminal.await() }
        }
    }
}

private const val TIMEOUT = 10_000L
private const val MAIN = "MultimodalBridgeUI"
private fun videoBridgeRequest() = MultimodalBridgeVideoRequest("input", " Describe. \n", 37)
private fun videoBridgeClient(backend: VideoBridgeBackend) = MultimodalGenerationClient(backend.backendId, listOf(backend))
private class VideoBridgeGate(open: Boolean) {
    val entered = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>().apply { if (open) complete(Unit) }
    suspend fun await() = withContext(NonCancellable) { entered.complete(Unit); gate.await() }
    fun open() { gate.complete(Unit) }
}
private class VideoBridgeBackend(override val backendId: String = "fixture", val available: Boolean = true,
    blockQuery: Boolean = false, blockGenerate: Boolean = false, blockDrain: Boolean = false,
    val fail: Boolean = false) : MultimodalVideoGenerationBackend {
    val querying = VideoBridgeGate(!blockQuery); val generating = VideoBridgeGate(!blockGenerate); val draining = VideoBridgeGate(!blockDrain)
    val starts = AtomicInteger(); val closes = AtomicInteger()
    var request: MultimodalVideoRequest? = null
    @Volatile var update: ((MultimodalTextUpdate) -> Unit)? = null
    override suspend fun availability(): CapabilityAvailability {
        querying.await()
        return CapabilityAvailability(if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.REQUIRES_ASSETS,
            if (available) null else UnavailableReason.MISSING_ASSETS, backendId, "26")
    }
    override suspend fun generateVideo(request: MultimodalVideoRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
        starts.incrementAndGet(); this.request = request; this.update = update
        generating.await()
        if (fail) error("/private/video.mp4 prompt-secret")
        return MultimodalTextOutput("A red square.", MultimodalModelIdentity("fixture", "r1", "runtime1"))
    }
    override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
        starts.incrementAndGet(); this.update = update
        generating.await()
        return MultimodalTextOutput("A red square.", MultimodalModelIdentity("fixture", "r1", "runtime1"))
    }
    fun emit(sequence: Int, text: String) { update?.invoke(MultimodalTextUpdate(sequence, text)) }
    override suspend fun drain() { draining.await() }
    override suspend fun close() { closes.incrementAndGet() }
}
private class VideoBridgeInputs(blockRelease: Boolean = false, val failRelease: Boolean = false) : AndroidMultimodalInputProvider {
    val acquires = AtomicInteger(); val releases = AtomicInteger(); val releasing = VideoBridgeGate(!blockRelease)
    override fun acquire(reference: String): AndroidMultimodalInputLease {
        require(reference == "input") { "/private/video.mp4" }
        assertTrue(Thread.currentThread().name.startsWith(MAIN)); acquires.incrementAndGet()
        return object : AndroidMultimodalInputLease {
            override val file = File("/opaque/input.mp4")
            override suspend fun release() { releasing.await(); releases.incrementAndGet(); if (failRelease) error("/private/video.mp4") }
        }
    }
}
private class VideoBridgeFixture(client: MultimodalGenerationClient?, val inputs: VideoBridgeInputs = VideoBridgeInputs()) : AutoCloseable {
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, MAIN).apply { isDaemon = true } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val bridge = AndroidMultimodalBridge(client, inputs, scope, videoInputs = inputs)
    suspend fun <T> ui(block: () -> T): T = withContext(dispatcher) { block() }
    fun cancelHost() = scope.cancel()
    suspend fun closeBridge() { val close = VideoBridgeCloseCapture(); ui { bridge.closeMultimodal(close) }; withTimeout(TIMEOUT) { close.terminal.await() } }
    override fun close() { runBlocking { closeBridge() }; scope.cancel(); dispatcher.close() }
}
private abstract class VideoBridgeCapture {
    val terminal = CompletableDeferred<String>()
    private val terminals = mutableListOf<String>(); private val threads = mutableListOf<String>()
    fun record(value: String) { terminals += value; thread(); terminal.complete(value) }
    fun thread() { threads += Thread.currentThread().name }
    fun assertOnly(value: String) = assertEquals(listOf(value), terminals)
    fun assertUi() = assertTrue(threads.isNotEmpty() && threads.all { it.substringBefore(" @") == MAIN }, threads.toString())
}
private class VideoBridgeGenerateCapture(val reenter: () -> Unit = {}) : VideoBridgeCapture(), MultimodalBridgeGenerateCallback {
    val output = CompletableDeferred<MultimodalBridgeOutput>(); val failure = CompletableDeferred<MultimodalBridgeFailure>()
    val updated = CompletableDeferred<Unit>(); val updates = mutableListOf<MultimodalBridgeUpdate>()
    override fun onMultimodalUpdate(update: MultimodalBridgeUpdate) { updates += update; thread(); updated.complete(Unit) }
    override fun onMultimodalResult(output: MultimodalBridgeOutput) { reenter(); this.output.complete(output); record("result") }
    override fun onMultimodalFailure(failure: MultimodalBridgeFailure) { this.failure.complete(failure); record("failure") }
    override fun onMultimodalCancelled() = record("cancelled")
}
private class VideoBridgeBackendsCapture : VideoBridgeCapture(), MultimodalBridgeBackendsCallback {
    val backends = CompletableDeferred<List<MultimodalBridgeBackend>>()
    override fun onMultimodalBackends(backends: List<MultimodalBridgeBackend>) { this.backends.complete(backends); record("result") }
    override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = record("failure")
    override fun onMultimodalCancelled() = record("cancelled")
}
private class VideoBridgeCloseCapture : VideoBridgeCapture(), MultimodalBridgeCloseCallback {
    override fun onMultimodalClosed() = record("closed")
    override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = record("failure")
}
