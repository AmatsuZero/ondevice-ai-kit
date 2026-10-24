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

class MultimodalVideoClientTest {
    private val video = MultimodalVideoRequest(File("/nonexistent/video.mp4"), " What changes? ", 37)
    private val image = MultimodalRequest(File("/nonexistent/image.png"), "Describe.")

    // Value construction must not touch nonexistent files or rewrite caller values.
    @Test fun validatesValuesWithoutReadingVideoOrChangingImageDefaults() {
        assertEquals(File("/nonexistent/video.mp4"), video.videoFile)
        assertEquals(" What changes? ", video.prompt); assertEquals(37, video.maxOutputTokens)
        assertEquals(128, MultimodalVideoRequest(video.videoFile, video.prompt).maxOutputTokens)
        assertEquals(128, image.maxOutputTokens)
        for (prompt in listOf("", " \n\t")) assertFailsWith<MultimodalClientError.InvalidInput> { video.copy(prompt = prompt) }
        for (tokens in listOf(0, -1)) assertFailsWith<MultimodalClientError.InvalidInput> { video.copy(maxOutputTokens = tokens) }
    }

    // Wrong dispatch/fallback or consulting legacy availability must fail.
    @Test fun explicitVideoSelectionRejectsUnknownUnavailableAndLegacyBeforeInvocation() = runBlocking<Unit> {
        val a = VideoBackend("a"); val b = VideoBackend("b"); val absent = VideoBackend("absent", available = false)
        val legacy = LegacyVideoTestBackend()
        val client = MultimodalGenerationClient("a", listOf(a, b, absent, legacy))
        val result = client.generate(video, MultimodalBackendSelection.Explicit("b"))
        assertEquals("b", result.backendId); assertEquals(VIDEO_OUTPUT, result.output)
        assertEquals(listOf(video), b.videos.toList()); assertEquals(1, b.drains.get())
        val unknown = assertFailsWith<MultimodalClientError.BackendNotRegistered> { client.generate(video, MultimodalBackendSelection.Explicit("missing")) }
        assertEquals("missing", unknown.backendId)
        val unavailable = assertFailsWith<MultimodalClientError.BackendUnavailable> { client.generate(video, MultimodalBackendSelection.Explicit("absent")) }
        assertEquals(UnavailableReason.MISSING_ASSETS, unavailable.reason)
        val invalid = assertFailsWith<MultimodalClientError.InvalidInput> { client.generate(video, MultimodalBackendSelection.Explicit("legacy")) }
        assertEquals("selected backend does not support video", invalid.detail)
        assertEquals(0, legacy.queries.get()); assertEquals(0, legacy.starts.get())
        assertEquals(0, a.starts.get()); assertEquals(0, absent.starts.get())
        assertEquals(CapabilityStatus.AVAILABLE, client.listBackends().first { it.backendId == "legacy" }.availability.status)
        client.close()
        assertEquals(VIDEO_OUTPUT, result.output)
    }

    // Independent admission for two media would admit an overlapping request.
    @Test fun imageAndVideoOccupyOneSlotInBothOrders() = runBlocking<Unit> {
        withTimeout(10_000) {
            for (videoFirst in listOf(true, false)) {
                val backend = VideoBackend(blockGeneration = true)
                val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
                val operation = async { if (videoFirst) client.generate(video) else client.generate(image) }
                backend.generation[0].entered.await()
                assertFailsWith<MultimodalClientError.Busy> { client.generate(video) }
                assertFailsWith<MultimodalClientError.Busy> { client.generate(image) }
                backend.generation[0].open(); operation.await(); client.close()
            }
        }
    }

    @Test fun cancelledVideoAvailabilityHoldsSharedSlotAndPreventsLateStart() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = VideoBackend(blockAvailability = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val operation = async { client.generate(video) }
            backend.admission.entered.await()
            assertFailsWith<MultimodalClientError.Busy> { client.generate(image) }
            operation.cancel(); backend.admission.open()
            assertFailsWith<CancellationException> { operation.await() }; operation.join()
            assertEquals(0, backend.starts.get()); assertEquals(0, backend.drains.get())
            client.close()
        }
    }

    // Slot release or outcome publication before drain would violate cancellation/reentry.
    @Test fun cancelledVideoWinsLateSuccessErrorOrDrainErrorAndJoinsBeforeImageReentry() = runBlocking<Unit> {
        withTimeout(10_000) {
            for (failure in listOf("none", "generation", "drain")) {
                val backend = VideoBackend(blockGeneration = true, blockDrain = true, failure = failure)
                val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
                val operation = async { client.generate(video) }
                backend.generation[0].entered.await(); operation.cancel(); backend.generation[0].open()
                backend.draining.entered.await()
                assertFalse(operation.isCompleted)
                assertFailsWith<MultimodalClientError.Busy> { client.generate(image) }
                backend.draining.open()
                assertFailsWith<CancellationException> { operation.await() }; operation.join()
                backend.failure = "none"
                assertEquals(VIDEO_OUTPUT, client.generate(image).output)
                assertEquals(2, backend.drains.get()); client.close()
            }
        }
    }

    @Test fun generationAndDrainFailuresKeepSuppressionAndAllowVideoReentry() = runBlocking<Unit> {
        for (failure in listOf("generation", "drain", "both")) {
            val backend = VideoBackend(failure = failure)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            if (failure == "generation") assertFailsWith<VideoGenerationFailure> { client.generate(video) }
            else {
                val error = assertFailsWith<VideoDrainFailure> { client.generate(video) }
                assertEquals(if (failure == "both") 1 else 0, error.suppressed.size)
                if (failure == "both") assertIs<VideoGenerationFailure>(error.suppressed.single())
            }
            backend.failure = "none"
            assertEquals(VIDEO_OUTPUT, client.generate(video).output)
            assertEquals(2, backend.drains.get()); client.close()
        }
    }

    @Test fun updatesRejectDuplicateRegressiveCancelledAndOldVideoCallbacksDuringImage() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = VideoBackend(blockGeneration = true, blockSecond = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val updates = Collections.synchronizedList(mutableListOf<Int>())
            val first = async { client.generate(video, update = { updates.add(it.sequence) }) }
            backend.generation[0].entered.await()
            backend.emit(0, 2); backend.emit(0, 2); backend.emit(0, 1)
            first.cancel(); backend.emit(0, 50); backend.generation[0].open()
            assertFailsWith<CancellationException> { first.await() }; first.join()
            val second = async { client.generate(image, update = { updates.add(it.sequence) }) }
            backend.generation[1].entered.await()
            backend.emit(0, 99); backend.emit(1, 3); backend.generation[1].open(); second.await()
            backend.emit(1, 100)
            assertEquals(listOf(2,3), updates.toList()); client.close()
        }
    }

    // Cancellation within synchronous callback must propagate through the caller Job.
    @Test fun synchronousVideoUpdateCancellationDropsFollowingUpdatesAndDrains() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = VideoBackend(blockGeneration = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val updates = Collections.synchronizedList(mutableListOf<Int>())
            val operation = async {
                val caller = currentCoroutineContext()[Job]!!
                client.generate(video, update = { updates.add(it.sequence); caller.cancel() })
            }
            backend.generation[0].entered.await()
            backend.emit(0,0); backend.emit(0,1); backend.generation[0].open()
            assertFailsWith<CancellationException> { operation.await() }; operation.join()
            assertEquals(listOf(0), updates.toList()); assertEquals(1, backend.drains.get()); client.close()
        }
    }

    @Test fun closeDuringVideoAvailabilitySealsBothOverloadsWithoutLateStart() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = VideoBackend(blockAvailability = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val operation = async { client.generate(video) }
            backend.admission.entered.await()
            val close = async { client.close() }; awaitClosed(client)
            assertFailsWith<MultimodalClientError.Closed> { client.generate(video) }
            assertFailsWith<MultimodalClientError.Closed> { client.generate(image) }
            assertFalse(close.isCompleted)
            backend.admission.open()
            assertFailsWith<CancellationException> { operation.await() }; operation.join(); close.await()
            assertEquals(0, backend.starts.get()); assertEquals(1, backend.closes.get())
        }
    }

    @Test fun closeJoinsVideoDrainAndMetadataQueryDespiteCancelledWaiter() = runBlocking<Unit> {
        withTimeout(10_000) {
            supervisorScope {
                val backend = VideoBackend(blockGeneration = true, blockDrain = true, blockSecondQuery = true)
                val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
                val operation = async { client.generate(video) }
                backend.generation[0].entered.await()
                val query = async { client.listBackends() }; backend.queryGate.entered.await()
                val first = async { client.close() }; awaitClosed(client); first.cancel()
                val second = async { client.close() }
                backend.generation[0].open(); backend.draining.entered.await()
                assertEquals(0, backend.closes.get()); backend.draining.open()
                assertFailsWith<CancellationException> { operation.await() }; operation.join()
                assertFalse(second.isCompleted); assertEquals(0, backend.closes.get())
                backend.queryGate.open(); assertFailsWith<MultimodalClientError.Closed> { query.await() }
                first.join(); second.await(); client.close()
                assertEquals(1, backend.closes.get())
            }
        }
    }

    @Test fun videoDrainWaitsWithoutCancellingOrLosingOutput() = runBlocking<Unit> {
        withTimeout(10_000) {
            val backend = VideoBackend(blockGeneration = true)
            val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
            val operation = async { client.generate(video) }; backend.generation[0].entered.await()
            val draining = async(start = CoroutineStart.UNDISPATCHED) { client.drain() }
            assertFalse(draining.isCompleted); backend.generation[0].open()
            assertEquals(VIDEO_OUTPUT, operation.await().output); draining.await(); client.close()
        }
    }

    @Test fun alreadyCancelledCallerDoesNotQueryOrStartVideoBackend() = runBlocking<Unit> {
        val backend = VideoBackend()
        val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
        val operation = async {
            currentCoroutineContext()[Job]!!.cancel()
            client.generate(video)
        }
        assertFailsWith<CancellationException> { operation.await() }
        assertEquals(0, backend.queries.get()); assertEquals(0, backend.starts.get()); client.close()
    }

    @Test fun throwingVideoUpdateStillDrainsAndAllowsImageReentry() = runBlocking<Unit> {
        val backend = VideoBackend(updateOnReturn = true)
        val client = MultimodalGenerationClient(backend.backendId, listOf(backend))
        val callbackFailure = IllegalArgumentException("callback")
        val actual = assertFailsWith<IllegalArgumentException> {
            client.generate(video, update = { throw callbackFailure })
        }
        assertSame(callbackFailure, actual)
        assertEquals(1, backend.drains.get())
        assertEquals(VIDEO_OUTPUT, client.generate(image).output)
        assertEquals(2, backend.drains.get()); client.close()
    }

    private suspend fun awaitClosed(client: MultimodalGenerationClient) {
        withTimeout(10_000) { while (true) {
            try { client.listBackends() } catch (_: MultimodalClientError.Closed) { break }
            yield()
        } }
    }
}

private val VIDEO_OUTPUT = MultimodalTextOutput("A moving square.", MultimodalModelIdentity("fixture", "r1", "runtime1"))
private class VideoGenerationFailure : IllegalStateException("generation")
private class VideoDrainFailure : IllegalStateException("drain")
private class VideoBarrier(open: Boolean) {
    val entered = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>().apply { if (open) complete(Unit) }
    suspend fun waitIgnoringCancellation() = withContext(NonCancellable) { entered.complete(Unit); gate.await() }
    fun open() { gate.complete(Unit) }
}
private class VideoBackend(
    override val backendId: String = "video.fixture", private val available: Boolean = true,
    blockAvailability: Boolean = false, blockGeneration: Boolean = false, blockSecond: Boolean = false,
    blockDrain: Boolean = false, private val blockSecondQuery: Boolean = false,
    private val updateOnReturn: Boolean = false,
    @Volatile var failure: String = "none"
) : MultimodalVideoGenerationBackend {
    val admission = VideoBarrier(!blockAvailability); val queryGate = VideoBarrier(false)
    val generation = listOf(VideoBarrier(!blockGeneration), VideoBarrier(!blockSecond))
    val draining = VideoBarrier(!blockDrain)
    val starts = AtomicInteger(); val drains = AtomicInteger(); val closes = AtomicInteger(); val queries = AtomicInteger()
    val videos = Collections.synchronizedList(mutableListOf<MultimodalVideoRequest>())
    private val callbacks = Collections.synchronizedList(mutableListOf<((MultimodalTextUpdate)->Unit)?>())
    override suspend fun availability(): CapabilityAvailability {
        val index = queries.getAndIncrement()
        if (index == 0) admission.waitIgnoringCancellation()
        else if (index == 1 && blockSecondQuery) queryGate.waitIgnoringCancellation()
        return CapabilityAvailability(if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.REQUIRES_ASSETS,
            if (available) null else UnavailableReason.MISSING_ASSETS, backendId)
    }
    override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate)->Unit)?): MultimodalTextOutput = run(update)
    override suspend fun generateVideo(request: MultimodalVideoRequest, update: ((MultimodalTextUpdate)->Unit)?): MultimodalTextOutput {
        videos.add(request); return run(update)
    }
    private suspend fun run(update: ((MultimodalTextUpdate)->Unit)?): MultimodalTextOutput = withContext(NonCancellable) {
        val index = starts.getAndIncrement(); callbacks.add(update)
        generation[index].waitIgnoringCancellation()
        if (failure == "generation" || failure == "both") throw VideoGenerationFailure()
        if (updateOnReturn) update?.invoke(MultimodalTextUpdate(0, "update"))
        VIDEO_OUTPUT
    }
    fun emit(index: Int, sequence: Int) { callbacks[index]?.invoke(MultimodalTextUpdate(sequence, "update")) }
    override suspend fun drain() {
        currentCoroutineContext().ensureActive(); drains.incrementAndGet(); draining.waitIgnoringCancellation()
        if (failure == "drain" || failure == "both") throw VideoDrainFailure()
    }
    override suspend fun close() { currentCoroutineContext().ensureActive(); closes.incrementAndGet() }
}
private class LegacyVideoTestBackend : MultimodalGenerationBackend {
    override val backendId = "legacy"
    val queries = AtomicInteger(); val starts = AtomicInteger()
    override suspend fun availability(): CapabilityAvailability {
        queries.incrementAndGet(); return CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    }
    override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate)->Unit)?): MultimodalTextOutput {
        starts.incrementAndGet(); return VIDEO_OUTPUT
    }
    override suspend fun drain() {}
    override suspend fun close() {}
}
