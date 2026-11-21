package io.ondeviceai.video

import io.ondeviceai.binding.AndroidVideoBridge
import io.ondeviceai.binding.AndroidVideoInputLease
import io.ondeviceai.binding.AndroidVideoInputProvider
import io.ondeviceai.binding.VideoBridgeAudioPolicy
import io.ondeviceai.binding.VideoBridgeBackendsCallback
import io.ondeviceai.binding.VideoBridgeBackend
import io.ondeviceai.binding.VideoBridgeCloseCallback
import io.ondeviceai.binding.VideoBridgeFailure
import io.ondeviceai.binding.VideoBridgeFailureCode
import io.ondeviceai.binding.VideoBridgeProcessCallback
import io.ondeviceai.binding.VideoBridgeReleaseCallback
import io.ondeviceai.binding.VideoBridgeRequest
import io.ondeviceai.binding.VideoBridgeSelection
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidVideoBridgeTest {
    @Test fun queryJoinsCloseEvenIfNativeIgnoresCancellation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val backend = TestBackend("default", availabilityBlock = {
            entered.complete(Unit)
            withContext(NonCancellable) { finish.await() }
            available("default")
        })
        Fixture(VideoProcessingClient("default", listOf(backend))).use { fixture ->
            val query = BackendsCapture()
            fixture.onMain { fixture.bridge.queryBackends(query) }
            withTimeout(TIMEOUT) { entered.await() }
            val firstClose = CloseCapture()
            val secondClose = CloseCapture()
            fixture.onMain { fixture.bridge.close(firstClose); fixture.bridge.close(secondClose) }
            try {
                assertFalse(firstClose.terminal.isCompleted)
                assertEquals(0, backend.closes.get())
                val rejected = ProcessCapture()
                fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, rejected) }
                assertEquals(VideoBridgeFailureCode.CLOSED, rejected.failure.await().code)
            } finally { finish.complete(Unit) }
            assertEquals("cancelled", withTimeout(TIMEOUT) { query.terminal.await() })
            assertEquals("closed", withTimeout(TIMEOUT) { firstClose.terminal.await() })
            assertEquals("closed", secondClose.terminal.await())
            assertEquals(1, backend.closes.get())
        }
    }

    @Test fun immediateCancellationReleasesInputBeforeReentry() = runBlocking {
        val inputs = TestInputProvider()
        val backend = TestBackend("default")
        Fixture(VideoProcessingClient("default", listOf(backend)), inputs).use { fixture ->
            val process = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, process).cancel() }
            assertEquals("cancelled", withTimeout(TIMEOUT) { process.terminal.await() })
            assertEquals(1, inputs.lease.releases.get())
            assertEquals(0, backend.calls.get())
        }
    }

    @Test fun hostScopeCancellationDoesNotSkipDrainOrClose() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val backend = TestBackend("default") {
            entered.complete(Unit)
            try { awaitCancellation() } finally { withContext(NonCancellable) { finish.await() } }
        }
        Fixture(VideoProcessingClient("default", listOf(backend))).use { fixture ->
            val process = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, process) }
            withTimeout(TIMEOUT) { entered.await() }
            fixture.cancelHost()
            val close = CloseCapture()
            fixture.onMain { fixture.bridge.close(close) }
            try { assertFalse(close.terminal.isCompleted); assertEquals(0, backend.closes.get()) }
            finally { finish.complete(Unit) }
            assertEquals("closed", withTimeout(TIMEOUT) { close.terminal.await() })
            assertEquals("cancelled", process.terminal.await())
            assertEquals(1, backend.closes.get())
        }
    }

    @Test fun successProgressMetadataAndReleaseUseHostDispatcher() = runBlocking {
        val backend = TestBackend("default")
        Fixture(VideoProcessingClient("default", listOf(backend))).use { fixture ->
            val callback = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, callback) }
            val output = withTimeout(TIMEOUT) { callback.result.await() }
            assertEquals(1280, output.width); assertEquals(720, output.height)
            assertEquals(48, output.frameCount); assertEquals(2.0, output.durationSeconds)
            assertEquals(1, output.audioTrackCount)
            assertFalse(output.reference.contains("/opaque"))
            assertEquals(File("/opaque/output.mov"), fixture.onMain { fixture.bridge.outputFile(output.reference) })
            callback.assertOnMain()
            val release = ReleaseCapture()
            fixture.onMain { fixture.bridge.releaseOutput(output.reference, release) }
            assertEquals("released", withTimeout(TIMEOUT) { release.terminal.await() })
            release.assertOnMain()
            val unknown = ReleaseCapture()
            fixture.onMain { fixture.bridge.releaseOutput(output.reference + "0", unknown) }
            assertEquals(VideoBridgeFailureCode.INVALID_INPUT, unknown.failure.await().code)
        }
    }

    @Test fun strictMissingSelectionAndUnsupportedScaleHaveDistinctFailures() = runBlocking {
        val backend = TestBackend("default")
        Fixture(VideoProcessingClient("default", listOf(backend))).use { fixture ->
            val missing = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Explicit("missing"), missing) }
            assertEquals(VideoBridgeFailureCode.BACKEND_NOT_REGISTERED, withTimeout(TIMEOUT) { missing.failure.await() }.code)
            val scale = ProcessCapture()
            fixture.onMain { fixture.bridge.process(VideoBridgeRequest("input", 2), VideoBridgeSelection.Default, scale) }
            assertEquals(VideoBridgeFailureCode.UNSUPPORTED_SCALE_FACTOR, withTimeout(TIMEOUT) { scale.failure.await() }.code)
            assertEquals(0, backend.calls.get())
        }
    }

    @Test fun explicitUnavailableDoesNotFallbackAndCallbacksUseHostDispatcher() = runBlocking {
        val default = TestBackend("default")
        val unavailable = TestBackend("unavailable", unavailable("unavailable"))
        Fixture(VideoProcessingClient(default.backendId, listOf(default, unavailable))).use { fixture ->
            val callback = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Explicit(unavailable.backendId), callback) }
            assertEquals(VideoBridgeFailureCode.BACKEND_UNAVAILABLE, callback.failure.await().code)
            assertEquals(0, default.calls.get())
            assertEquals(0, unavailable.calls.get())
            callback.assertOnMain()
        }
    }

    @Test fun cancellationKeepsSlotBusyUntilDrainAndLateOutputIsReleased() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        val output = TestOutputLease()
        val backend = TestBackend("default") {
            entered.complete(Unit)
            withContext(NonCancellable) { allowFinish.await() }
            output
        }
        Fixture(VideoProcessingClient(backend.backendId, listOf(backend))).use { fixture ->
            val first = ProcessCapture()
            val task = fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, first) }
            entered.await()
            fixture.onMain { task.cancel() }
            val second = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, second) }
            assertEquals(VideoBridgeFailureCode.BUSY, second.failure.await().code)
            allowFinish.complete(Unit)
            assertEquals("cancelled", first.terminal.await())
            assertEquals(1, output.releases.get())
            first.assertOnlyTerminal("cancelled")
        }
    }

    @Test fun outputReleaseRetriesThenIsIdempotentAfterClose() = runBlocking {
        val output = TestOutputLease(failReleases = 1)
        val backend = TestBackend("default") { output }
        Fixture(VideoProcessingClient(backend.backendId, listOf(backend))).use { fixture ->
            val process = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, process) }
            val bridgeOutput = process.result.await()
            val close = CloseCapture()
            fixture.onMain { fixture.bridge.close(close) }
            assertEquals("closed", close.terminal.await())

            val first = ReleaseCapture()
            fixture.onMain { fixture.bridge.releaseOutput(bridgeOutput.reference, first) }
            assertEquals(VideoBridgeFailureCode.PLATFORM, first.failure.await().code)
            val second = ReleaseCapture()
            fixture.onMain { fixture.bridge.releaseOutput(bridgeOutput.reference, second) }
            assertEquals("released", second.terminal.await())
            val third = ReleaseCapture()
            fixture.onMain { fixture.bridge.releaseOutput(bridgeOutput.reference, third) }
            assertEquals("released", third.terminal.await())
            assertEquals(2, output.releases.get())
            assertNull(fixture.bridge.outputFile(bridgeOutput.reference))
        }
    }

    @Test fun inputLeaseOutlivesClientDrainThenReleases() = runBlocking {
        val input = TestInputProvider()
        val entered = CompletableDeferred<Unit>()
        val releaseDrain = CompletableDeferred<Unit>()
        val backend = TestBackend("default") {
            entered.complete(Unit)
            try { awaitCancellation() } finally { withContext(NonCancellable) { releaseDrain.await() } }
            error("unreachable")
        }
        Fixture(VideoProcessingClient(backend.backendId, listOf(backend)), input).use { fixture ->
            val callback = ProcessCapture()
            val task = fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, callback) }
            entered.await()
            fixture.onMain { task.cancel() }
            assertEquals(0, input.lease.releases.get())
            releaseDrain.complete(Unit)
            assertEquals("cancelled", callback.terminal.await())
            assertEquals(1, input.lease.releases.get())
        }
    }

    @Test fun unknownExceptionHasStablePlatformFailureWithoutMessage() = runBlocking {
        val backend = TestBackend("default") { throw IllegalStateException("/private/user/video.mov") }
        Fixture(VideoProcessingClient(backend.backendId, listOf(backend))).use { fixture ->
            val callback = ProcessCapture()
            fixture.onMain { fixture.bridge.process(request(), VideoBridgeSelection.Default, callback) }
            val failure = callback.failure.await()
            assertEquals(VideoBridgeFailureCode.PLATFORM, failure.code)
            assertNull(failure.reason)
            assertEquals("android.video", failure.domain)
            assertEquals(1, failure.platformCode)
        }
    }
}

private const val TIMEOUT = 10_000L
private const val TEST_MAIN = "AndroidVideoBridgeTestMain"
private fun request() = VideoBridgeRequest("input", 4, VideoBridgeAudioPolicy.PRESERVE)
private fun available(id: String) = CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = id, offline = true)
private fun unavailable(id: String) = CapabilityAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.MISSING_ASSETS, id, "26", true)

private class TestBackend(
    override val backendId: String,
    private val availability: CapabilityAvailability = available(backendId),
    private val availabilityBlock: (suspend () -> CapabilityAvailability)? = null,
    private val processBlock: suspend () -> VideoOutputLease = { TestOutputLease() },
) : VideoSuperResolutionBackend {
    override val supportedScaleFactors = listOf(4)
    override val supportedAudioPolicies = listOf(VideoAudioPolicy.PRESERVE, VideoAudioPolicy.DISCARD)
    val calls = AtomicInteger()
    val closes = AtomicInteger()
    override suspend fun availability() = availabilityBlock?.invoke() ?: availability
    override suspend fun process(request: VideoProcessingRequest, progress: ((VideoProcessingProgress) -> Unit)?): VideoOutputLease {
        calls.incrementAndGet(); progress?.invoke(VideoProcessingProgress(1, 1)); return processBlock()
    }
    override suspend fun drain() = Unit
    override suspend fun close() { closes.incrementAndGet() }
}

private class TestOutputLease(private val failReleases: Int = 0) : VideoOutputLease {
    override val file = File("/opaque/output.mov")
    override val info = VideoOutputInfo(1280, 720, 48, 2.0, 1)
    val releases = AtomicInteger()
    override suspend fun release() { if (releases.incrementAndGet() <= failReleases) error("release failure") }
}

private class TestInputLease : AndroidVideoInputLease {
    override val file = File("/opaque/input.mov")
    val releases = AtomicInteger()
    override suspend fun release() { releases.incrementAndGet() }
}
private class TestInputProvider : AndroidVideoInputProvider {
    val lease = TestInputLease()
    override fun acquire(reference: String): AndroidVideoInputLease { require(reference == "input"); return lease }
}

private class Fixture(client: VideoProcessingClient, inputs: AndroidVideoInputProvider = TestInputProvider()) : AutoCloseable {
    private val ui = Executors.newSingleThreadExecutor { Thread(it, TEST_MAIN).apply { isDaemon = true } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + ui)
    val bridge = AndroidVideoBridge(client, inputs, scope)
    fun cancelHost() = scope.cancel()
    suspend fun <T> onMain(block: () -> T): T = withContext(ui) { block() }
    override fun close() { runBlocking { val close = CloseCapture(); onMain { bridge.close(close) }; withTimeout(TIMEOUT) { close.terminal.await() } }; scope.cancel(); ui.close() }
}

private abstract class Capture {
    val terminal = CompletableDeferred<String>(); private val terminals = mutableListOf<String>(); private val threads = mutableListOf<String>()
    fun record(value: String) { terminals += value; threads += Thread.currentThread().name; terminal.complete(value) }
    fun progress() { threads += Thread.currentThread().name }
    fun assertOnlyTerminal(value: String) = assertEquals(listOf(value), terminals)
    fun assertOnMain() = assertTrue(threads.all { it.substringBefore(" @") == TEST_MAIN }, threads.toString())
}
private class ProcessCapture : Capture(), VideoBridgeProcessCallback {
    val result = CompletableDeferred<io.ondeviceai.binding.VideoBridgeOutput>(); val failure = CompletableDeferred<VideoBridgeFailure>()
    override fun onProgress(progress: io.ondeviceai.binding.VideoBridgeProgress) { progress() }
    override fun onResult(output: io.ondeviceai.binding.VideoBridgeOutput) { result.complete(output); record("result") }
    override fun onFailure(failure: VideoBridgeFailure) { this.failure.complete(failure); record("failure") }
    override fun onCancelled() = record("cancelled")
}
private class ReleaseCapture : Capture(), VideoBridgeReleaseCallback { val failure = CompletableDeferred<VideoBridgeFailure>(); override fun onReleased() = record("released"); override fun onFailure(failure: VideoBridgeFailure) { this.failure.complete(failure); record("failure") } }
private class CloseCapture : Capture(), VideoBridgeCloseCallback { override fun onClosed() = record("closed"); override fun onFailure(failure: VideoBridgeFailure) = record("failure") }
private class BackendsCapture : Capture(), VideoBridgeBackendsCallback {
    override fun onResult(backends: List<VideoBridgeBackend>) = record("result")
    override fun onFailure(failure: VideoBridgeFailure) = record("failure")
    override fun onCancelled() = record("cancelled")
}
