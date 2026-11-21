package io.ondeviceai.multimodal

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Test

class MultimodalGenerationClientTest {
    private val request = MultimodalRequest(File("/nonexistent/image.png"), " Describe. ", 37)

    @Test fun contractsValidateWithoutOpeningImageOrRewritingValues() {
        assertEquals(128, MultimodalRequest(request.imageFile, request.prompt).maxOutputTokens)
        assertEquals(" Describe. ", request.prompt)
        for (prompt in listOf("", " \n\t")) {
            assertFailsWith<MultimodalClientError.InvalidInput> { request.copy(prompt = prompt) }
        }
        for (limit in listOf(0, -1)) {
            assertFailsWith<MultimodalClientError.InvalidInput> { request.copy(maxOutputTokens = limit) }
        }
        for (fields in listOf(listOf("", "r", "v"), listOf("m", "\n", "v"), listOf("m", "r", "\t"))) {
            assertFailsWith<MultimodalClientError.InvalidInput> { MultimodalModelIdentity(fields[0], fields[1], fields[2]) }
        }
        assertEquals(" 1.2 ", MultimodalModelIdentity("fixture", " 1.2 ", "v1").modelRevision)
        assertFailsWith<MultimodalClientError.InvalidInput> { MultimodalTextUpdate(-1, "text") }
        assertEquals("", MultimodalTextUpdate(0, "").text)
        assertFailsWith<MultimodalClientError.InvalidInput> { MultimodalTextOutput(" \n", OUTPUT.model) }
        assertFailsWith<MultimodalClientError.InvalidInput> { MultimodalGenerationResult("\n", OUTPUT) }
    }

    @Test fun registrationAndStrictSelectionNeverFallback() = runBlocking<Unit> {
        val ready = FixtureBackend("ready")
        val absent = FixtureBackend("absent", available = false)
        assertFailsWith<MultimodalClientError.InvalidRegistration> { MultimodalGenerationClient("missing", listOf(ready)) }
        assertFailsWith<MultimodalClientError.InvalidRegistration> { MultimodalGenerationClient("ready", listOf(ready, ready)) }
        assertFailsWith<MultimodalClientError.InvalidRegistration> { MultimodalGenerationClient(" ", listOf(FixtureBackend(" "))) }
        assertFailsWith<MultimodalClientError.InvalidRegistration> { MultimodalGenerationClient("", emptyList()) }
        val client = MultimodalGenerationClient("ready", listOf(ready, absent))
        assertEquals(listOf("absent", "ready"), client.listBackends().map { it.backendId })
        assertEquals(UnavailableReason.MISSING_ASSETS, client.listBackends()[0].availability.reason)
        val unknown = assertFailsWith<MultimodalClientError.BackendNotRegistered> {
            client.generate(request, MultimodalBackendSelection.Explicit("missing"))
        }
        assertEquals("missing", unknown.backendId)
        val unavailable = assertFailsWith<MultimodalClientError.BackendUnavailable> {
            client.generate(request, MultimodalBackendSelection.Explicit("absent"))
        }
        assertEquals("absent", unavailable.backendId)
        assertEquals(UnavailableReason.MISSING_ASSETS, unavailable.reason)
        assertEquals(0, ready.starts.get()); assertEquals(0, absent.starts.get())
        client.close()
    }

    @Test fun successPreservesSelectedBackendAndInputAndOutputOutlivesClose() = runBlocking<Unit> {
        val first = FixtureBackend("a"); val second = FixtureBackend("b")
        val client = MultimodalGenerationClient("a", listOf(first, second))
        val result = client.generate(request, MultimodalBackendSelection.Explicit("b"))
        assertEquals("b", result.backendId); assertEquals(OUTPUT, result.output)
        assertEquals(listOf(request), second.requests.toList())
        assertEquals(1, second.drains.get()); assertEquals(0, first.starts.get())
        client.close()
        assertEquals("A red square.", result.output.text)
        assertFailsWith<MultimodalClientError.Closed> { client.generate(request) }
        assertFailsWith<MultimodalClientError.Closed> { client.listBackends() }
    }

    @Test fun ordinaryGenerationFailureStillDrainsAndPermitsReentry() = runBlocking<Unit> {
        val backend = FixtureBackend(failGeneration = true)
        val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
        repeat(2) { assertFailsWith<GenerationFailure> { client.generate(request) } }
        assertEquals(2, backend.drains.get()); assertEquals(2, backend.starts.get())
        client.close()
    }

    @Test fun availabilityOccupiesSlotAndCancellationRejectsLateStart() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = FixtureBackend(blockAvailability = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val operation = async { client.generate(request) }
            backend.admission.entered.await()
            assertFailsWith<MultimodalClientError.Busy> { client.generate(request) }
            operation.cancel(); backend.admission.open()
            assertFailsWith<CancellationException> { operation.await() }
            operation.join()
            assertEquals(0, backend.starts.get())
            client.close()
        }
    }

    @Test fun cancellationWinsLateSuccessOrOrdinaryErrorAndKeepsSlotUntilDrain() = runBlocking<Unit> {
        withTimeout(10_000) {
            for (fail in listOf(false, true)) {
                val backend = FixtureBackend(blockGeneration = true, blockDrain = true, failGeneration = fail)
                val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
                val operation = async { client.generate(request) }
                backend.generating[0].entered.await()
                operation.cancel(); backend.generating[0].open()
                backend.draining.entered.await()
                assertFalse(operation.isCompleted)
                assertFailsWith<MultimodalClientError.Busy> { client.generate(request) }
                backend.draining.open()
                assertFailsWith<CancellationException> { operation.await() }
                operation.join()
                assertEquals(1, backend.drains.get())
                client.close()
            }
        }
    }

    @Test fun drainWaitsWithoutCancellingGeneration() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = FixtureBackend(blockGeneration = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val operation = async { client.generate(request) }
            backend.generating[0].entered.await()
            val draining = async(start = CoroutineStart.UNDISPATCHED) { client.drain() }
            assertFalse(draining.isCompleted)
            backend.generating[0].open()
            assertEquals(OUTPUT, operation.await().output)
            draining.await(); client.close()
        }
    }

    @Test fun duplicateRegressiveCancelledAndOldUpdatesAreSuppressed() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = FixtureBackend(blockGeneration = true, blockSecondGeneration = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val updates = Collections.synchronizedList(mutableListOf<Int>())
            val first = async { client.generate(request, update = { updates.add(it.sequence) }) }
            backend.generating[0].entered.await()
            backend.emit(0, 2); backend.emit(0, 2); backend.emit(0, 1)
            assertEquals(listOf(2), updates.toList())
            first.cancel()
            backend.emit(0, 50)
            assertEquals(listOf(2), updates.toList())
            backend.generating[0].open()
            assertFailsWith<CancellationException> { first.await() }; first.join()
            val second = async { client.generate(request, update = { updates.add(it.sequence) }) }
            backend.generating[1].entered.await()
            backend.emit(0, 99); backend.emit(1, 3)
            assertEquals(listOf(2, 3), updates.toList())
            backend.generating[1].open(); second.await()
            backend.emit(1, 50)
            assertEquals(listOf(2, 3), updates.toList())
            client.close()
        }
    }

    @Test fun closeSealsAdmissionDuringAvailabilityAndClosesOnce() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = FixtureBackend(blockAvailability = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val operation = async { client.generate(request) }
            backend.admission.entered.await()
            val first = async { client.close() }
            awaitClosed(client)
            val second = async { client.close() }
            assertFalse(first.isCompleted)
            backend.admission.open()
            assertFailsWith<CancellationException> { operation.await() }
            first.await(); second.await()
            assertEquals(0, backend.starts.get()); assertEquals(1, backend.closes.get())
        }
    }

    @Test fun closeJoinsQueryBeforeBackendClose() = runBlocking<Unit> {
        withTimeout(10_000) {
            supervisorScope {
                val backend = FixtureBackend(blockAvailability = true)
                val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
                val query = async { client.listBackends() }
                backend.admission.entered.await()
                val closing = async { client.close() }
                awaitClosed(client)
                assertEquals(0, backend.closes.get())
                backend.admission.open()
                assertFailsWith<MultimodalClientError.Closed> { query.await() }
                closing.await()
                assertEquals(1, backend.closes.get())
            }
        }
    }

    @Test fun cancelledCloseWaiterCannotInterruptCleanupAndLateUpdatesAreDropped() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = FixtureBackend(blockGeneration = true, blockDrain = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val updates = Collections.synchronizedList(mutableListOf<Int>())
            val operation = async { client.generate(request, update = { updates.add(it.sequence) }) }
            backend.generating[0].entered.await()
            val first = async { client.close() }
            awaitClosed(client)
            first.cancel()
            val second = async { client.close() }
            backend.emit(0, 99); backend.generating[0].open()
            backend.draining.entered.await()
            assertEquals(0, backend.closes.get())
            backend.draining.open()
            assertFailsWith<CancellationException> { operation.await() }
            first.join(); second.await(); client.close()
            assertEquals(1, backend.closes.get()); assertEquals(emptyList(), updates.toList())
        }
    }

    @Test fun closeFailureStillClosesOtherBackendsAndRepeatedCloseRetainsSameFailure() = runBlocking<Unit> {
        supervisorScope {
            val first = FixtureBackend("a", failClose = true)
            val second = FixtureBackend("b", failClose = true)
            val third = FixtureBackend("c")
            val client = MultimodalGenerationClient("a", listOf(third, second, first))
            val failure = assertFailsWith<CloseFailure> { client.close() }
            assertEquals(1, failure.suppressed.size)
            val repeated = assertFailsWith<CloseFailure> { client.close() }
            assertSame(failure, repeated)
            assertEquals(1, first.closes.get()); assertEquals(1, second.closes.get()); assertEquals(1, third.closes.get())
        }
    }

    private suspend fun awaitClosed(client: MultimodalGenerationClient) {
        withTimeout(10_000) {
            while (true) {
                try { client.listBackends() } catch (_: MultimodalClientError.Closed) { break }
                yield()
            }
        }
    }
}

private val OUTPUT = MultimodalTextOutput("A red square.", MultimodalModelIdentity("fixture", "r1", "runtime1"))
private class GenerationFailure : IllegalStateException("generation")
private class CloseFailure : IllegalStateException("close")
private class Barrier(open: Boolean) {
    val entered = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>().apply { if (open) complete(Unit) }
    suspend fun waitIgnoringCancellation() = withContext(NonCancellable) { entered.complete(Unit); gate.await() }
    fun open() { gate.complete(Unit) }
}
private class FixtureBackend(
    override val backendId: String = "fixture.multimodal", private val available: Boolean = true,
    blockAvailability: Boolean = false, blockGeneration: Boolean = false,
    blockSecondGeneration: Boolean = false, blockDrain: Boolean = false,
    private val failGeneration: Boolean = false, private val failClose: Boolean = false
) : MultimodalGenerationBackend {
    val admission = Barrier(!blockAvailability)
    val generating = listOf(Barrier(!blockGeneration), Barrier(!blockSecondGeneration))
    val draining = Barrier(!blockDrain)
    val starts = AtomicInteger(); val closes = AtomicInteger(); val drains = AtomicInteger()
    private val queries = AtomicInteger()
    val requests = Collections.synchronizedList(mutableListOf<MultimodalRequest>())
    private val callbacks = Collections.synchronizedList(mutableListOf<((MultimodalTextUpdate) -> Unit)?>())
    override suspend fun availability(): CapabilityAvailability {
        if (queries.getAndIncrement() == 0) admission.waitIgnoringCancellation()
        return CapabilityAvailability(if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.REQUIRES_ASSETS,
            if (available) null else UnavailableReason.MISSING_ASSETS, backendId)
    }
    override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
        val index = starts.getAndIncrement(); requests.add(request); callbacks.add(update)
        return withContext(NonCancellable) {
            generating[index].waitIgnoringCancellation()
            if (failGeneration) throw GenerationFailure()
            OUTPUT
        }
    }
    fun emit(index: Int, sequence: Int) { callbacks[index]?.invoke(MultimodalTextUpdate(sequence, "snapshot")) }
    override suspend fun drain() {
        currentCoroutineContext().ensureActive()
        drains.incrementAndGet(); draining.waitIgnoringCancellation()
    }
    override suspend fun close() {
        currentCoroutineContext().ensureActive()
        closes.incrementAndGet()
        if (failClose) throw CloseFailure()
    }
}
