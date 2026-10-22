package io.ondeviceai.multimodal

import io.ondeviceai.binding.*
import io.ondeviceai.core.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*

class AndroidMultimodalBridgeTest {
    @Test fun unlinkedAndClosedNeverAcquireInput() = runBlocking {
        Fixture(null).use { f ->
            val first = GenerateCapture()
            f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, first) }
            assertEquals("backendNotLinked", first.failure.await().reason)
            assertEquals(0, f.inputs.acquires.get())
            f.closeBridge()
            val rejected = GenerateCapture()
            f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, rejected) }
            assertEquals(MultimodalBridgeFailureCode.CLOSED, rejected.failure.await().code)
            assertEquals(0, f.inputs.acquires.get())
        }
    }

    @Test fun exactInputSelectionIdentityAndReentrantResultUseUi() = runBlocking {
        val a = Backend("a"); val b = Backend("b")
        Fixture(MultimodalGenerationClient("a", listOf(a, b))).use { f ->
            val second = GenerateCapture()
            val first = GenerateCapture { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, second) }
            f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Explicit("b"), first) }
            val output = withTimeout(TIMEOUT) { first.output.await() }
            assertEquals("b", output.backendId); assertEquals("A red square.", output.text)
            assertEquals(MultimodalBridgeIdentity("fixture", "r1", "runtime1"), output.model)
            assertEquals("result", withTimeout(TIMEOUT) { second.terminal.await() })
            assertEquals(MultimodalRequest(File("/opaque/input.png"), " Describe. \n", 37), b.request)
            assertEquals(1, a.starts.get()); assertEquals(2, f.inputs.releases.get())
            first.assertUi(); second.assertUi()
            f.closeBridge()
            assertEquals("A red square.", output.text)
        }
    }

    @Test fun explicitMissingUnavailableNeverFallbackAndQueryPreservesMetadata() = runBlocking {
        val a = Backend("a"); val b = Backend("b", available = false)
        Fixture(MultimodalGenerationClient("a", listOf(b, a))).use { f ->
            val query = BackendsCapture()
            f.ui { f.bridge.queryMultimodalBackends(query) }
            val backends = withTimeout(TIMEOUT) { query.backends.await() }
            assertEquals(listOf("a", "b"), backends.map { it.backendId })
            assertFalse(backends[1].availability.available)
            assertEquals("MISSING_ASSETS", backends[1].availability.reason)
            assertEquals("26", backends[1].availability.minimumOS)
            for ((id, code) in listOf("missing" to MultimodalBridgeFailureCode.BACKEND_NOT_REGISTERED,
                "b" to MultimodalBridgeFailureCode.BACKEND_UNAVAILABLE)) {
                val callback = GenerateCapture()
                f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Explicit(id), callback) }
                assertEquals(code, withTimeout(TIMEOUT) { callback.failure.await() }.code)
            }
            assertEquals(0, a.starts.get()); assertEquals(0, b.starts.get())
            query.assertUi()
        }
    }

    @Test fun updatesAreCumulativeOrderedUiAndRejectedAfterCancelOrTerminal() = runBlocking {
        val backend = Backend(blockGenerate = true)
        Fixture(client(backend)).use { f ->
            val callback = GenerateCapture()
            val task = f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, callback) }
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
            val backend = Backend(blockGenerate = true, blockDrain = true, fail = fail)
            val inputs = Inputs(blockRelease = true)
            Fixture(client(backend), inputs).use { f ->
                val callback = GenerateCapture()
                val task = f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, callback) }
                backend.generating.entered.await(); task.cancel(); backend.generating.open()
                backend.draining.entered.await()
                assertEquals(0, inputs.releases.get()); assertFalse(callback.terminal.isCompleted)
                val busy = GenerateCapture()
                f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, busy) }
                assertEquals(MultimodalBridgeFailureCode.BUSY, busy.failure.await().code)
                backend.draining.open(); inputs.releasing.entered.await()
                assertFalse(callback.terminal.isCompleted)
                val stillBusy = GenerateCapture()
                f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, stillBusy) }
                assertEquals(MultimodalBridgeFailureCode.BUSY, stillBusy.failure.await().code)
                inputs.releasing.open()
                assertEquals("cancelled", withTimeout(TIMEOUT) { callback.terminal.await() })
                assertEquals(1, inputs.releases.get()); callback.assertOnly("cancelled")
            }
        }
    }

    @Test fun immediateCancelStillReleasesExactlyOnceWithoutStartingNative() = runBlocking {
        val backend = Backend()
        Fixture(client(backend)).use { f ->
            val callback = GenerateCapture()
            f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, callback).cancel() }
            assertEquals("cancelled", withTimeout(TIMEOUT) { callback.terminal.await() })
            assertEquals(1, f.inputs.releases.get()); assertEquals(0, backend.starts.get())
        }
    }

    @Test fun cancelWinsOrdinaryLeaseErrorAfterDrain() = runBlocking {
        val backend = Backend(blockGenerate = true)
        Fixture(client(backend), Inputs(failRelease = true)).use { f ->
            val callback = GenerateCapture()
            val task = f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, callback) }
            backend.generating.entered.await(); task.cancel(); backend.generating.open()
            assertEquals("cancelled", withTimeout(TIMEOUT) { callback.terminal.await() })
            assertEquals(1, f.inputs.releases.get()); callback.assertOnly("cancelled")
        }
    }

    @Test fun hostCancellationAndRepeatedCloseJoinNoncancellableQuery() = runBlocking {
        val backend = Backend(blockQuery = true)
        Fixture(client(backend)).use { f ->
            val query = BackendsCapture()
            f.ui { f.bridge.queryMultimodalBackends(query) }; backend.querying.entered.await()
            f.cancelHost()
            val one = CloseCapture(); val two = CloseCapture()
            f.ui { f.bridge.closeMultimodal(one); f.bridge.closeMultimodal(two) }
            assertFalse(one.terminal.isCompleted); assertEquals(0, backend.closes.get())
            val rejected = BackendsCapture()
            f.ui { f.bridge.queryMultimodalBackends(rejected) }
            assertEquals("failure", rejected.terminal.await())
            backend.querying.open()
            assertEquals("cancelled", withTimeout(TIMEOUT) { query.terminal.await() })
            assertEquals("closed", one.terminal.await()); assertEquals("closed", two.terminal.await())
            assertEquals(1, backend.closes.get()); one.assertUi(); query.assertOnly("cancelled")
        }
    }

    @Test fun hostCancellationDoesNotLoseGenerationDrainAndLease() = runBlocking {
        val backend = Backend(blockGenerate = true, blockDrain = true)
        Fixture(client(backend)).use { f ->
            val callback = GenerateCapture()
            f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, callback) }
            backend.generating.entered.await(); f.cancelHost()
            val close = CloseCapture(); f.ui { f.bridge.closeMultimodal(close) }
            backend.generating.open(); backend.draining.entered.await()
            assertFalse(close.terminal.isCompleted); assertEquals(0, f.inputs.releases.get())
            backend.draining.open()
            assertEquals("closed", withTimeout(TIMEOUT) { close.terminal.await() })
            assertEquals("cancelled", callback.terminal.await()); assertEquals(1, f.inputs.releases.get())
        }
    }

    @Test fun unknownErrorsAndInputFailuresDoNotLeakPrivateMessages() = runBlocking {
        val backend = Backend(fail = true)
        Fixture(client(backend)).use { f ->
            val callback = GenerateCapture()
            f.ui { f.bridge.generateMultimodal(request(), MultimodalBridgeSelection.Default, callback) }
            val failure = withTimeout(TIMEOUT) { callback.failure.await() }
            assertEquals(MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, null, "android.multimodal", 1), failure)
            assertEquals(1, f.inputs.releases.get())
            val bad = GenerateCapture()
            f.ui { f.bridge.generateMultimodal(request().copy(inputReference = "bad"), MultimodalBridgeSelection.Default, bad) }
            assertEquals(MultimodalBridgeFailure(MultimodalBridgeFailureCode.INVALID_INPUT, null), bad.failure.await())
        }
    }
}

private const val TIMEOUT = 10_000L
private const val MAIN = "MultimodalBridgeUI"
private fun request() = MultimodalBridgeRequest("input", " Describe. \n", 37)
private fun client(backend: Backend) = MultimodalGenerationClient(backend.backendId, listOf(backend))
private class Gate(open: Boolean) {
    val entered = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>().apply { if (open) complete(Unit) }
    suspend fun await() = withContext(NonCancellable) { entered.complete(Unit); gate.await() }
    fun open() { gate.complete(Unit) }
}
private class Backend(override val backendId: String = "fixture", val available: Boolean = true,
    blockQuery: Boolean = false, blockGenerate: Boolean = false, blockDrain: Boolean = false,
    val fail: Boolean = false) : MultimodalGenerationBackend {
    val querying = Gate(!blockQuery); val generating = Gate(!blockGenerate); val draining = Gate(!blockDrain)
    val starts = AtomicInteger(); val closes = AtomicInteger()
    var request: MultimodalRequest? = null
    @Volatile var update: ((MultimodalTextUpdate) -> Unit)? = null
    override suspend fun availability(): CapabilityAvailability {
        querying.await()
        return CapabilityAvailability(if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.REQUIRES_ASSETS,
            if (available) null else UnavailableReason.MISSING_ASSETS, backendId, "26")
    }
    override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
        starts.incrementAndGet(); this.request = request; this.update = update
        generating.await()
        if (fail) error("/private/image.png prompt-secret")
        return MultimodalTextOutput("A red square.", MultimodalModelIdentity("fixture", "r1", "runtime1"))
    }
    fun emit(sequence: Int, text: String) { update?.invoke(MultimodalTextUpdate(sequence, text)) }
    override suspend fun drain() { draining.await() }
    override suspend fun close() { closes.incrementAndGet() }
}
private class Inputs(blockRelease: Boolean = false, val failRelease: Boolean = false) : AndroidMultimodalInputProvider {
    val acquires = AtomicInteger(); val releases = AtomicInteger(); val releasing = Gate(!blockRelease)
    override fun acquire(reference: String): AndroidMultimodalInputLease {
        require(reference == "input") { "/private/image.png" }
        assertTrue(Thread.currentThread().name.startsWith(MAIN)); acquires.incrementAndGet()
        return object : AndroidMultimodalInputLease {
            override val file = File("/opaque/input.png")
            override suspend fun release() { releasing.await(); releases.incrementAndGet(); if (failRelease) error("/private/image.png") }
        }
    }
}
private class Fixture(client: MultimodalGenerationClient?, val inputs: Inputs = Inputs()) : AutoCloseable {
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, MAIN).apply { isDaemon = true } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val bridge = AndroidMultimodalBridge(client, inputs, scope)
    suspend fun <T> ui(block: () -> T): T = withContext(dispatcher) { block() }
    fun cancelHost() = scope.cancel()
    suspend fun closeBridge() { val close = CloseCapture(); ui { bridge.closeMultimodal(close) }; withTimeout(TIMEOUT) { close.terminal.await() } }
    override fun close() { runBlocking { closeBridge() }; scope.cancel(); dispatcher.close() }
}
private abstract class Capture {
    val terminal = CompletableDeferred<String>()
    private val terminals = mutableListOf<String>(); private val threads = mutableListOf<String>()
    fun record(value: String) { terminals += value; thread(); terminal.complete(value) }
    fun thread() { threads += Thread.currentThread().name }
    fun assertOnly(value: String) = assertEquals(listOf(value), terminals)
    fun assertUi() = assertTrue(threads.isNotEmpty() && threads.all { it.substringBefore(" @") == MAIN }, threads.toString())
}
private class GenerateCapture(val reenter: () -> Unit = {}) : Capture(), MultimodalBridgeGenerateCallback {
    val output = CompletableDeferred<MultimodalBridgeOutput>(); val failure = CompletableDeferred<MultimodalBridgeFailure>()
    val updated = CompletableDeferred<Unit>(); val updates = mutableListOf<MultimodalBridgeUpdate>()
    override fun onMultimodalUpdate(update: MultimodalBridgeUpdate) { updates += update; thread(); updated.complete(Unit) }
    override fun onMultimodalResult(output: MultimodalBridgeOutput) { reenter(); this.output.complete(output); record("result") }
    override fun onMultimodalFailure(failure: MultimodalBridgeFailure) { this.failure.complete(failure); record("failure") }
    override fun onMultimodalCancelled() = record("cancelled")
}
private class BackendsCapture : Capture(), MultimodalBridgeBackendsCallback {
    val backends = CompletableDeferred<List<MultimodalBridgeBackend>>()
    override fun onMultimodalBackends(backends: List<MultimodalBridgeBackend>) { this.backends.complete(backends); record("result") }
    override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = record("failure")
    override fun onMultimodalCancelled() = record("cancelled")
}
private class CloseCapture : Capture(), MultimodalBridgeCloseCallback {
    override fun onMultimodalClosed() = record("closed")
    override fun onMultimodalFailure(failure: MultimodalBridgeFailure) = record("failure")
}
