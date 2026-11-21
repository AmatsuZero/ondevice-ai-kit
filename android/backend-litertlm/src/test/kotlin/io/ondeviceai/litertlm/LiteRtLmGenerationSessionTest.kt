package io.ondeviceai.litertlm

import io.ondeviceai.modelstore.*
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.yield
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.ondeviceai.multimodal.MultimodalClientError

class LiteRtLmGenerationSessionTest {
    @Test
    fun explicitMixedPolicyReachesEngineWithoutChangingDefault() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            assertEquals(LiteRtLmBackendPolicy.CPU_ONLY, fixture.spec.backendPolicy)
            var selected: LiteRtLmBackendPolicy? = null
            val backend = LiteRtLmBackend(fixture.store, fixture.ref,
                fixture.spec.copy(backendPolicy = LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION),
                fixture.cache, LiteRtLmEngineFactory { _, config ->
                    selected = config.backendPolicy
                    object : LiteRtLmSession {
                        override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                        override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                            onSnapshot: (String) -> Unit) = "fixture output"
                        override fun cancelProcess() = Unit
                        override suspend fun awaitTerminated() = Unit
                        override suspend fun close() = Unit
                    }
                }, apiLevel = 35, supportedAbi = true, probeState = LiteRtLmProbeState.SYNCHRONOUS)
            assertEquals("fixture output", backend.generate(MultimodalRequest(fixture.image, "describe")).text)
            assertEquals(LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION, selected)
            backend.close()
            fixture.store.remove(fixture.ref)
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun secondGenerationIsBusyUntilFirstDrainCompletes() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var opened = 0
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                opened++
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        started.complete(Unit)
                        release.await()
                        return "text"
                    }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            })
            val first = async { backend.generate(MultimodalRequest(fixture.image, "first")) }
            started.await()
            assertFailsWith<MultimodalClientError.Busy> {
                backend.generate(MultimodalRequest(fixture.image, "second"))
            }
            assertEquals(1, opened)
            release.complete(Unit)
            first.await()
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun verifiedDeltaCallbacksBecomeCumulativeUpdates() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.DELTA
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        listOf("a", "b", "c").forEach(onSnapshot)
                        return "abc"
                    }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            }, LiteRtLmProbeState.PROVEN_DELTA)
            val updates = mutableListOf<String>()
            val output = backend.generate(MultimodalRequest(fixture.image, "describe")) { updates += it.text }
            assertEquals(listOf("a", "ab", "abc"), updates)
            assertEquals("abc", output.text)
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun nativeOpenGenerateCancelDrainAndCloseLeaveCallerDispatcher() = runBlocking {
        val fixture = BackendFixture()
        val caller = Executors.newSingleThreadExecutor { task -> Thread(task, "litertlm-caller") }.asCoroutineDispatcher()
        try {
            fixture.install()
            val threads = mutableListOf<String>()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                threads += Thread.currentThread().name
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        threads += Thread.currentThread().name
                        return "text"
                    }
                    override fun cancelProcess() { threads += Thread.currentThread().name }
                    override suspend fun awaitTerminated() { threads += Thread.currentThread().name }
                    override suspend fun close() { threads += Thread.currentThread().name }
                }
            })
            assertTrue(withContext(caller) { Thread.currentThread().name.startsWith("litertlm-caller") })
            withContext(caller) { backend.generate(MultimodalRequest(fixture.image, "describe")) }
            assertEquals(5, threads.size)
            assertFalse(threads.any { it.contains("litertlm-caller") }, "$threads")
            backend.close()
        } finally {
            caller.close()
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun callbackAfterCallerCancellationButBeforeNativeDrainIsNotDelivered() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var callback: ((String) -> Unit)? = null
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        callback = onSnapshot
                        onSnapshot("a")
                        started.complete(Unit)
                        try { awaitCancellation() }
                        catch (failure: CancellationException) {
                            cancelled.complete(Unit)
                            withContext(NonCancellable) { release.await() }
                            throw failure
                        }
                    }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            })
            val updates = mutableListOf<String>()
            val generation = async {
                backend.generate(MultimodalRequest(fixture.image, "describe")) { updates += it.text }
            }
            started.await()
            generation.cancel()
            cancelled.await()
            callback?.invoke("ab")
            release.complete(Unit)
            generation.join()
            assertEquals(listOf("a"), updates)
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun closeCancelsActiveGenerationAndRejectsLateCallbacks() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val events = mutableListOf<String>()
            val started = CompletableDeferred<Unit>()
            var lateCallback: ((String) -> Unit)? = null
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        events += "generate"
                        lateCallback = onSnapshot
                        onSnapshot("a")
                        started.complete(Unit)
                        awaitCancellation()
                    }
                    override fun cancelProcess() { events += "cancel" }
                    override suspend fun awaitTerminated() { events += "drain" }
                    override suspend fun close() { events += "native-close" }
                }
            })
            val updates = mutableListOf<String>()
            val generation = async {
                backend.generate(MultimodalRequest(fixture.image, "describe")) { updates += it.text }
            }
            started.await()
            backend.close()
            generation.join()
            lateCallback?.invoke("ab")
            assertEquals(listOf("a"), updates)
            assertEquals(listOf("generate", "cancel", "drain", "native-close"), events)
            assertFailsWith<MultimodalClientError.Closed> {
                backend.generate(MultimodalRequest(fixture.image, "again"))
            }
            fixture.store.remove(fixture.ref)
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun generationDrainsBeforeClosingNativeAndReleasesLeaseForReentry() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val events = mutableListOf<String>()
            val factory = LiteRtLmEngineFactory { _, _ ->
                events += "open"
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        events += "generate"
                        assertEquals("describe", prompt)
                        assertEquals(32, maxOutputTokens)
                        onSnapshot("a")
                        onSnapshot("ab")
                        onSnapshot("ab")
                        return "ab"
                    }
                    override fun cancelProcess() { events += "cancel" }
                    override suspend fun awaitTerminated() { events += "drain" }
                    override suspend fun close() { events += "native-close" }
                }
            }
            val backend = fixture.backend(factory)
            val updates = mutableListOf<String>()
            repeat(2) {
                val output = backend.generate(MultimodalRequest(fixture.image, "describe", 32)) { updates += it.text }
                assertEquals("ab", output.text)
                assertEquals("fixed-revision", output.model.modelRevision)
                assertEquals("litertlm-android:0.18.1", output.model.runtimeRevision)
            }
            assertEquals(listOf("a", "ab", "a", "ab"), updates)
            assertEquals(listOf("open", "generate", "cancel", "drain", "native-close",
                "open", "generate", "cancel", "drain", "native-close"), events)
            backend.drain()
            backend.close()
            fixture.store.remove(fixture.ref)
            assertFalse(File(fixture.cache, "litertlm").walkTopDown().any { it.isFile })
        } finally { fixture.root.deleteRecursively() }
    }

    // --- W5 late-death cleanup regressions -------------------------------------
    //
    // These use a pure fake barrier (CompletableDeferred) and a test-controlled
    // termination timeout (terminationTimeoutMs, defaulting to 30_000) so the JVM
    // suite runs without real native inference and without wall-clock waits. The fake
    // never claims native inference; it only gates the exact-death barrier.
    //
    // All cross-thread observations use thread-safe collections / deferreds: the
    // surviving cleanup owner runs on its own IO dispatcher, while the test reads on
    // the runBlocking thread. Completion is synchronized on deferreds that fire AFTER
    // the real side effects (cache deletion, native close, lease release) rather than
    // on a gate completed before the work, so there is no sleep-based proof.

    @Test
    fun lateBinderDeathPreservesCacheAndLeaseUntilBarrierThenReleasesExactlyOnce() = runBlocking {
        val fixture = BackendFixture()
        val barrier = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val deletedDirs = CopyOnWriteArrayList<File>()
        val signals = CleanupSignals()
        var session: ControllableSession? = null
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, config ->
                ControllableSession(barrier, events, signals, cacheDir = config.cacheDir).also { session = it }
            }, terminationTimeoutMs = 50L, cacheCleaner = LiteRtLmCacheCleaner { dir ->
                deletedDirs += dir
                dir.deleteRecursively()
                signals.deleted.complete(Unit)
                true
            })
            // Caller observes the 30s-equivalent DrainFailure; the backend is poisoned exactly
            // as before.
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            // The owned cache and its input exist before the exact death confirms.
            val cacheDir = session!!.cacheDir
            assertTrue(cacheDir.exists(), "owned cache must exist before barrier")
            assertTrue(File(cacheDir, "input.image").exists(), "owned input must exist before barrier")
            // Predeath: nothing is released before the exact death confirms.
            assertTrue(deletedDirs.isEmpty(), "owned cache must not be deleted before barrier")
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(events.none { it == "native-close" }, "native must not be closed before barrier")
            assertTrue(events.count { it == "cancel" } == 1, "native cancelled exactly once, never repeated")

            // The exact death now confirms (late). The surviving cleanup owner releases.
            barrier.complete(Unit)
            withTimeout(5000L) { signals.deleted.await() }

            // Postdeath: only the owned cache was deleted, exactly once, and native closed once.
            assertEquals(1, deletedDirs.size, "exactly one owned cache deleted")
            assertEquals(cacheDir, deletedDirs[0], "only the owned cache is deleted")
            assertEquals(1, events.count { it == "native-close" }, "native closed exactly once")
            assertTrue(events.count { it == "cancel" } == 1, "native never re-cancelled")

            // Late arrival: the same old backend stays poisoned, while a fresh backend may run.
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "again"))
            }
            val backend2 = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit) = "reentry-output"
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            })
            assertEquals("reentry-output",
                backend2.generate(MultimodalRequest(fixture.image, "again")).text)
            backend2.close()

            // Lease released exactly once: ModelStore removal now succeeds only once the
            // surviving owner has actually executed lease.close() — which runs AFTER the
            // owned-cache delete (signals.deleted) and is not synchronized by it. Poll
            // cooperatively under a finite bound; no fixed sleep, no weaker assertion.
            withTimeout(5000L) {
                while (true) {
                    try {
                        fixture.store.remove(fixture.ref)
                        break
                    } catch (inUse: ModelInUseException) {
                        yield()
                    }
                }
            }
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun lateBinderDeathDoesNotReleaseOnGenuineBarrierError() = runBlocking {
        val fixture = BackendFixture()
        val barrier = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val deletedDirs = CopyOnWriteArrayList<File>()
        val signals = CleanupSignals()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                ControllableSession(barrier, events, signals, failFirstBarrier = true)
            }, terminationTimeoutMs = 50L, cacheCleaner = LiteRtLmCacheCleaner { dir ->
                deletedDirs += dir
                dir.deleteRecursively()
                true
            })
            // Genuine barrier error surfaces as the caller's DrainFailure + poison (unchanged path).
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            // No safe release: the lease is held and the owned cache is untouched; native never closed.
            assertTrue(deletedDirs.isEmpty(), "no safe release on genuine barrier error")
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(events.none { it == "native-close" }, "native never closed on genuine error")
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun lateTimeoutThenSecondBarrierAwaitFailsNoRelease() = runBlocking {
        val fixture = BackendFixture()
        // Never completed: the first awaitTerminated hangs and times out, entering the late branch.
        val barrier = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val deletedDirs = CopyOnWriteArrayList<File>()
        val signals = CleanupSignals()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                ControllableSession(barrier, events, signals, failSecondBarrier = true)
            }, terminationTimeoutMs = 50L, cacheCleaner = LiteRtLmCacheCleaner { dir ->
                deletedDirs += dir
                dir.deleteRecursively()
                true
            })
            // Caller times out then observes DrainFailure; the surviving owner re-awaits the barrier.
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            // The second barrier await fails: the cleanup owner must not release anything.
            withTimeout(5000L) { signals.barrier.await() }
            assertTrue(deletedDirs.isEmpty(), "no release when second barrier await fails")
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(events.none { it == "native-close" }, "native never closed when barrier fails")
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun lateNativeCloseThrowsRetainsCacheAndLease() = runBlocking {
        val fixture = BackendFixture()
        val barrier = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val deletedDirs = CopyOnWriteArrayList<File>()
        val signals = CleanupSignals()
        var session: ControllableSession? = null
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, config ->
                ControllableSession(barrier, events, signals, closeThrows = true,
                    cacheDir = config.cacheDir).also { session = it }
            }, terminationTimeoutMs = 50L, cacheCleaner = LiteRtLmCacheCleaner { dir ->
                deletedDirs += dir
                dir.deleteRecursively()
                signals.deleted.complete(Unit)
                true
            })
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            // Let the surviving owner proceed past the barrier to its native.close attempt.
            barrier.complete(Unit)
            withTimeout(5000L) { signals.closed.await() }
            // native.close threw: the lease is never released and the owned cache is retained.
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(session!!.cacheDir.exists(), "owned cache retained when native.close throws")
            assertTrue(deletedDirs.isEmpty(), "cache never deleted when native.close throws")
            assertEquals(1, events.count { it == "native-close" }, "native-close attempted exactly once")
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun lateCacheDeleteFalseRetainsCacheAndLease() = runBlocking {
        val fixture = BackendFixture()
        val barrier = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val deletedDirs = CopyOnWriteArrayList<File>()
        val signals = CleanupSignals()
        var session: ControllableSession? = null
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, config ->
                ControllableSession(barrier, events, signals, cacheDir = config.cacheDir).also { session = it }
            }, terminationTimeoutMs = 50L, cacheCleaner = LiteRtLmCacheCleaner { dir ->
                signals.deleted.complete(Unit)
                false // report failure without deleting
            })
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            barrier.complete(Unit)
            withTimeout(5000L) { signals.deleted.await() }
            // cacheCleaner reported false: lease never released, owned cache retained.
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(session!!.cacheDir.exists(), "owned cache retained when delete reports false")
            assertTrue(deletedDirs.isEmpty(), "cache never deleted when delete reports false")
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun lateCacheDeleteThrowsRetainsCacheAndLease() = runBlocking {
        val fixture = BackendFixture()
        val barrier = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val deletedDirs = CopyOnWriteArrayList<File>()
        val signals = CleanupSignals()
        var session: ControllableSession? = null
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, config ->
                ControllableSession(barrier, events, signals, cacheDir = config.cacheDir).also { session = it }
            }, terminationTimeoutMs = 50L, cacheCleaner = LiteRtLmCacheCleaner { dir ->
                signals.deleted.complete(Unit)
                throw IOException("cache delete failed")
            })
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            barrier.complete(Unit)
            withTimeout(5000L) { signals.deleted.await() }
            // cacheCleaner threw: lease never released, owned cache retained.
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(session!!.cacheDir.exists(), "owned cache retained when delete throws")
            assertTrue(deletedDirs.isEmpty(), "cache never deleted when delete throws")
        } finally { fixture.root.deleteRecursively() }
    }
    // Regression for review F2: a TimeoutCancellationException raised by a *timely* native.close
    // must surface as a cleanup failure and must NOT start a second native.close or delete. The
    // lease stays retained (fail-closed).
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun timelyNativeCloseTimeoutDoesNotRelaunchAndKeepsLease() = runBlocking {
        val fixture = BackendFixture()
        val barrier = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val deletedDirs = CopyOnWriteArrayList<File>()
        val signals = CleanupSignals()
        var session: ControllableSession? = null
        // Scheduler-controlled cleanup owner dispatcher: behaves like the production
        // default (Dispatchers.IO) but lets the test drain any surviving owner
        // deterministically after generate returns instead of racing the assertion
        // against an independent IO thread (reviewer F2 false-green fix).
        val scheduler = TestCoroutineScheduler()
        val cleanupDispatcher = StandardTestDispatcher(scheduler)
        try {
            fixture.install()
            // Exact death already confirmed: the termination wait returns timely, so cleanup
            // proceeds to native.close (which throws a TimeoutCancellationException), exercising
            // the timely cleanup path rather than the late-death handoff.
            barrier.complete(Unit)
            val backend = fixture.backend(LiteRtLmEngineFactory { _, config ->
                ControllableSession(barrier, events, signals, closeThrowsTimeout = true,
                    cacheDir = config.cacheDir).also { session = it }
            }, terminationTimeoutMs = 50L, cacheCleaner = LiteRtLmCacheCleaner { dir ->
                deletedDirs += dir
                dir.deleteRecursively()
                signals.deleted.complete(Unit)
                true
            }, cleanupDispatcher = cleanupDispatcher)
            // A TimeoutCancellationException from the timely native.close is a genuine cleanup
            // error, not a late-death handoff: caller sees DrainFailure, no surviving owner.
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            // Finite guard: await the single native.close attempt (completes on Dispatchers.IO,
            // independent of the test dispatcher).
            withTimeout(5000L) { signals.closed.await() }
            // Drain any surviving owner that the narrow catch must NOT have launched. On the
            // scheduler-controlled dispatcher the owner (if wrongly launched by a broad catch)
            // only executes here, so the counts below are deterministic, not racy.
            scheduler.runCurrent()
            // Exactly one native.close attempt — the late-death owner was never launched, so no
            // second close and no cache delete occur.
            assertEquals(1, events.count { it == "native-close" },
                "timely native.close timeout must not trigger a second close")
            assertTrue(deletedDirs.isEmpty(), "no cache delete without a successful native.close")
            // The lease is still retained: removal keeps failing.
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(session!!.cacheDir.exists(), "owned cache retained while lease held")
        } finally { fixture.root.deleteRecursively() }
    }
}

/** Precise cross-thread signals for the W5 late-death tests. */
internal class CleanupSignals {
    val closed = CompletableDeferred<Unit>()   // native.close invoked
    val deleted = CompletableDeferred<Unit>()  // cacheCleaner invoked (after deciding outcome)
    val barrier = CompletableDeferred<Unit>()  // failing second barrier await invoked
}

/** Pure fake session whose exact-death barrier is an externally controlled deferred gate. */
internal class ControllableSession(
    private val barrier: CompletableDeferred<Unit>,
    private val events: MutableList<String>,
    private val signals: CleanupSignals? = null,
    private val failFirstBarrier: Boolean = false,
    private val failSecondBarrier: Boolean = false,
    private val closeThrows: Boolean = false,
    private val closeThrowsTimeout: Boolean = false,
    val cacheDir: File = File(""),
    private val result: String = "late-death-output",
) : LiteRtLmSession {
    private val awaitCount = AtomicInteger(0)
    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
        onSnapshot: (String) -> Unit): String {
        events += "generate"
        onSnapshot(result)
        return result
    }
    override fun cancelProcess() { events += "cancel" }
    override suspend fun awaitTerminated() {
        events += "await"
        val n = awaitCount.incrementAndGet()
        if (n == 1) {
            if (failFirstBarrier) throw IOException("genuine barrier failure")
            barrier.await()
        } else {
            if (failSecondBarrier) { signals?.barrier?.complete(Unit); throw IOException("second barrier failure") }
            barrier.await()
        }
        events += "terminated"
    }
    override suspend fun close() {
        events += "native-close"
        signals?.closed?.complete(Unit)
        if (closeThrowsTimeout) {
            // kotlinx.coroutines 1.11 made the TimeoutCancellationException(message)
            // constructor internal. Raise a real one via the supported suspend timeout API.
            withTimeout(1L) { awaitCancellation() }
        }
        if (closeThrows) throw IOException("late native.close failure")
    }
}

internal class BackendFixture {
    val root: File = Files.createTempDirectory("litertlm-backend").toFile()
    val source = File(root, "source").apply { mkdirs() }
    val cache = File(root, "cache").apply { mkdirs() }
    val image = File(root, "fixture.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    val ref = ModelRef("fixture", "r1")
    val store = ModelStore(File(root, "store"), ModelCompatibility("litertlm.android", "0.18.1", "35", "arm64-v8a"))
    val spec = LiteRtLmModelSpec("model.bin", setOf("tokenizer.json"))

    suspend fun install() {
        val files = listOf("model.bin", "tokenizer.json").map { path ->
            val bytes = "fixture-$path".toByteArray()
            File(source, path).writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            ModelFile(path, "https://models.example/$path", bytes.size.toLong(), hash)
        }
        val total = files.sumOf { it.bytes }
        store.install(ModelManifest(
            modelId = ref.modelId, revision = ref.revision,
            upstreamURL = "https://models.example/fixture", upstreamRevision = "fixed-revision",
            capability = "multimodal.image", backendId = "litertlm.android",
            backendVersion = VersionRange("0.18.1", "0.19.0"), minimumOS = "26",
            architectures = listOf("arm64-v8a"), formatVersion = "litertlm-model-v1", files = files,
            processing = ModelProcessing("image-file+text-prompt-v1", "text-v1", "preprocess-v1", "tokenizer-v1", "prompt-v1", 1),
            resources = ModelResources(total, total, total), licenseIdentifier = "Apache-2.0",
            licenseURL = "https://www.apache.org/licenses/LICENSE-2.0", attribution = "test fixture",
            redistribution = "permitted",
        ), source)
    }

    fun backend(factory: LiteRtLmEngineFactory,
        probeState: LiteRtLmProbeState = LiteRtLmProbeState.PROVEN_CUMULATIVE,
        cacheCleaner: LiteRtLmCacheCleaner = LiteRtLmCacheCleaner { it.deleteRecursively() },
        terminationTimeoutMs: Long = 30_000L,
        cleanupDispatcher: CoroutineDispatcher = Dispatchers.IO) = LiteRtLmBackend(
        store, ref, spec, cache, factory, apiLevel = 35, supportedAbi = true,
        probeState = probeState, cacheCleaner = cacheCleaner, terminationTimeoutMs = terminationTimeoutMs,
        cleanupDispatcher = cleanupDispatcher)
}
