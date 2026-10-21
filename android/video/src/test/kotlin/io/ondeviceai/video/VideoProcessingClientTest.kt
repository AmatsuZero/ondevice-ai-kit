package io.ondeviceai.video

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Test

class VideoProcessingClientTest {
    private val request = VideoProcessingRequest(File("/tmp/video-fixture.mp4"), 4)

    @Test fun validatesRequestOutputAndProgress() {
        assertEquals(VideoAudioPolicy.PRESERVE, request.audioPolicy)
        assertFailsWith<IllegalArgumentException> { request.copy(scaleFactor = 1) }
        for (duration in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { VideoOutputInfo(1280, 720, 48, duration, 0) }
        }
        assertFailsWith<IllegalArgumentException> { VideoOutputInfo(0, 720, 48, 2.0, 0) }
        assertFailsWith<IllegalArgumentException> { VideoOutputInfo(1280, 720, 0, 2.0, 0) }
        assertFailsWith<IllegalArgumentException> { VideoOutputInfo(1280, 720, 48, 2.0, -1) }
        assertFailsWith<IllegalArgumentException> { VideoProcessingProgress(-1) }
        assertFailsWith<IllegalArgumentException> { VideoProcessingProgress(0, 0) }
        assertFailsWith<IllegalArgumentException> { VideoProcessingProgress(2, 1) }
        assertEquals(1, VideoProcessingProgress(1, 2).completedFrames)
    }

    @Test fun registrationAndStrictSelectionNeverFallback() = runBlocking {
        val ready = FixtureBackend("ready")
        val absent = FixtureBackend("absent", CapabilityStatus.REQUIRES_ASSETS)
        assertFailsWith<IllegalArgumentException> { VideoProcessingClient("missing", listOf(ready)) }
        assertFailsWith<IllegalArgumentException> { VideoProcessingClient("ready", listOf(ready, ready)) }
        assertFailsWith<IllegalArgumentException> { VideoProcessingClient("", listOf(FixtureBackend(""))) }
        val client = VideoProcessingClient("ready", listOf(ready, absent))
        val list = client.listBackends()
        assertEquals(listOf("absent", "ready"), list.map { it.backendId })
        assertEquals(UnavailableReason.MISSING_ASSETS, list[0].availability.reason)
        assertEquals(listOf(4), list[0].supportedScaleFactors)
        assertEquals(listOf(VideoAudioPolicy.PRESERVE), list[0].supportedAudioPolicies)
        assertFailsWith<VideoClientError.BackendNotRegistered> { client.process(request, VideoBackendSelection.Explicit("missing")) }
        assertFailsWith<VideoClientError.BackendUnavailable> { client.process(request, VideoBackendSelection.Explicit("absent")) }
        assertFailsWith<VideoClientError.UnsupportedScaleFactor> { client.process(request.copy(scaleFactor = 2)) }
        assertFailsWith<VideoClientError.UnsupportedAudioPolicy> { client.process(request.copy(audioPolicy = VideoAudioPolicy.DISCARD)) }
        assertEquals(0, ready.starts.get()); assertEquals(0, absent.starts.get())
        client.close()
    }

    @Test fun cancellationKeepsSlotUntilDrainAndReleasesLateOutput() = runBlocking {
        withTimeout(10_000) {
            val backend = FixtureBackend(blockProcess = true, blockDrain = true)
            val client = VideoProcessingClient(backend.backendId, listOf(backend))
            val operation = async { client.process(request) }
            backend.processing.entered.await()
            operation.cancel(); backend.processing.open()
            backend.draining.entered.await()
            assertFalse(operation.isCompleted)
            assertFailsWith<VideoClientError.Busy> { client.process(request) }
            backend.draining.open()
            assertFailsWith<CancellationException> { operation.await() }
            operation.join()
            assertEquals(1, backend.outputs[0].releases.get())
            val second = client.process(request)
            client.close()
            assertEquals(0, backend.outputs[1].releases.get())
            second.output.release()
        }
    }

    @Test fun cleanupFailureIsNotSwallowed() = runBlocking {
        supervisorScope {
            val backend = FixtureBackend(blockProcess = true, cleanupFails = true)
            val client = VideoProcessingClient(backend.backendId, listOf(backend))
            val operation = async { client.process(request) }
            backend.processing.entered.await(); operation.cancel(); backend.processing.open()
            assertFailsWith<CleanupFailure> { operation.await() }
            client.close()
        }
    }

    @Test fun closeDuringAvailabilityPreventsLateStartAndClosesOnce() = runBlocking {
        withTimeout(10_000) {
            val backend = FixtureBackend(blockAvailability = true)
            val client = VideoProcessingClient(backend.backendId, listOf(backend))
            val operation = async { client.process(request) }
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

    @Test fun closeJoinsQueryBeforeBackendClose() = runBlocking {
        supervisorScope {
            val backend = FixtureBackend(blockAvailability = true)
            val client = VideoProcessingClient(backend.backendId, listOf(backend))
            val query = async { client.listBackends() }
            backend.admission.entered.await()
            val closing = async { client.close() }
            awaitClosed(client)
            assertEquals(0, backend.closes.get())
            backend.admission.open()
            assertFailsWith<VideoClientError.Closed> { query.await() }
            closing.await()
            assertEquals(1, backend.closes.get())
        }
    }

    @Test fun oldAndRegressiveProgressIsSuppressed() = runBlocking {
        val backend = FixtureBackend(blockProcess = true)
        val client = VideoProcessingClient(backend.backendId, listOf(backend))
        val frames = Collections.synchronizedList(mutableListOf<Int>())
        val operation = async { client.process(request, progress = { frames.add(it.completedFrames) }) }
        backend.processing.entered.await()
        backend.emit(0, 3); backend.emit(0, 2)
        assertEquals(listOf(3), frames.toList())
        operation.cancel(); backend.processing.open()
        assertFailsWith<CancellationException> { operation.await() }; operation.join()
        val second = client.process(request, progress = { frames.add(it.completedFrames) })
        backend.emit(0, 99)
        assertEquals(listOf(3), frames.toList())
        client.close(); second.output.release()
    }

    private suspend fun awaitClosed(client: VideoProcessingClient) {
        withTimeout(10_000) {
            while (true) {
                try { client.listBackends() } catch (_: VideoClientError.Closed) { break }
                yield()
            }
        }
    }
}

private class Barrier(open: Boolean) {
    val entered = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>().apply { if (open) complete(Unit) }
    suspend fun waitIgnoringCancellation() = withContext(NonCancellable) { entered.complete(Unit); gate.await() }
    fun open() { gate.complete(Unit) }
}

private class CleanupFailure : IllegalStateException("cleanup failed")
private class FixtureOutput(private val cleanupFails: Boolean = false) : VideoOutputLease {
    override val file = File("/tmp/video-output.mov")
    override val info = VideoOutputInfo(1280, 720, 48, 2.0, 0)
    val releases = AtomicInteger()
    override suspend fun release() {
        currentCoroutineContext().ensureActive()
        releases.incrementAndGet()
        if (cleanupFails) throw CleanupFailure()
    }
}

private class FixtureBackend(
    override val backendId: String = "fixture.video",
    private val status: CapabilityStatus = CapabilityStatus.AVAILABLE,
    blockAvailability: Boolean = false, blockProcess: Boolean = false,
    blockDrain: Boolean = false, cleanupFails: Boolean = false
) : VideoSuperResolutionBackend {
    override val supportedScaleFactors = listOf(4)
    override val supportedAudioPolicies = listOf(VideoAudioPolicy.PRESERVE)
    val admission = Barrier(!blockAvailability)
    val processing = Barrier(!blockProcess)
    val draining = Barrier(!blockDrain)
    val outputs = listOf(FixtureOutput(cleanupFails), FixtureOutput())
    val starts = AtomicInteger(); val closes = AtomicInteger(); private val queries = AtomicInteger()
    private val callbacks = Collections.synchronizedList(mutableListOf<((VideoProcessingProgress) -> Unit)?>())
    override suspend fun availability(): CapabilityAvailability {
        if (queries.getAndIncrement() == 0) admission.waitIgnoringCancellation()
        return CapabilityAvailability(status, if (status == CapabilityStatus.AVAILABLE) null else UnavailableReason.MISSING_ASSETS, backendId)
    }
    override suspend fun process(request: VideoProcessingRequest, progress: ((VideoProcessingProgress) -> Unit)?): VideoOutputLease {
        val index = starts.getAndIncrement()
        callbacks.add(progress)
        // Returning inside NonCancellable intentionally simulates an external API ignoring cancel.
        return withContext(NonCancellable) { processing.waitIgnoringCancellation(); outputs[index] }
    }
    fun emit(index: Int, frames: Int) { callbacks[index]?.invoke(VideoProcessingProgress(frames)) }
    override suspend fun drain() { currentCoroutineContext().ensureActive(); draining.waitIgnoringCancellation() }
    override suspend fun close() { closes.incrementAndGet() }
}
