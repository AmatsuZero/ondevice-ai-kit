package io.ondeviceai.litertlm

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import androidx.test.platform.app.InstrumentationRegistry
import io.ondeviceai.multimodal.MultimodalClientError
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class LiteRtLmProcessSessionInstrumentation {
    @Test
    fun openOnlyConnectsAndDefersHelloAndStartUntilGeneration() = runBlocking {
        val fixture = Fixture()
        try {
            val session = open(fixture)
            assertEquals(LiteRtLmCallbackMode.DELTA, session.callbackMode)
            assertEquals(listOf(BIND), fixture.context.events)
            assertEquals(0, fixture.binder.codes.size)
            assertEquals(1, fixture.context.bindCalls)
            assertFalse(fixture.context.bindOnMain)

            session.close()
            assertEquals(listOf(BIND, UNBIND), fixture.context.events)
            assertEquals(0, fixture.binder.codes.size)
            assertFalse(fixture.context.unbindOnMain)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun factoryExcludesSecondOpenUntilLocalPreHelloClose() = runBlocking {
        val fixture = Fixture()
        try {
            val session = open(fixture)
            assertBusy(fixture)
            assertEquals(1, fixture.context.bindCalls)

            session.close()
            val reopened = open(fixture)
            assertEquals(2, fixture.context.bindCalls)
            assertEquals(0, fixture.binder.codes.size)
            reopened.close()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun deltaCallbacksStayRawAndAppendExactlyOnceAcrossDuplicateStatusHistory() = runBlocking {
        val fixture = Fixture(callbackMode = LiteRtLmCallbackMode.DELTA)
        val seen = CopyOnWriteArrayList<String>()
        try {
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("a")))
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("a")))
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("a", "ab")))
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("a", "ab", "!")))
            fixture.binder.addStatus(status("TERMINAL", "SUCCEEDED", listOf("a", "ab", "!")))

            val session = open(fixture)
            val text = session.generateImage(fixture.files.image, "prompt", 17, seen::add)

            assertEquals("aab!", text)
            assertEquals(listOf("a", "ab", "!"), seen)
            assertEquals(
                LiteRtLmProcessStart(
                    modelAbsolutePath = fixture.files.model.absolutePath,
                    imageAbsolutePath = fixture.files.image.absolutePath,
                    cacheAbsolutePath = fixture.files.cache.absolutePath,
                    prompt = "prompt",
                    maxOutputTokens = 17,
                    backendPolicy = LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION,
                ),
                fixture.binder.startRequest.get(),
            )
            assertEquals(listOf(HELLO, START, STATUS, STATUS, STATUS, STATUS, STATUS), fixture.binder.codes)

            finishOwned(session, fixture.binder)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cumulativeCallbacksUseWholeSnapshotsAndPreserveRawValues() = runBlocking {
        val fixture = Fixture(callbackMode = LiteRtLmCallbackMode.CUMULATIVE)
        val seen = CopyOnWriteArrayList<String>()
        try {
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("a")))
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("a", "ab")))
            fixture.binder.addStatus(status("TERMINAL", "SUCCEEDED", listOf("a", "ab", "ab!")))

            val session = open(fixture)
            assertEquals("ab!", session.generateImage(fixture.files.image, "describe", 8, seen::add))
            assertEquals(listOf("a", "ab", "ab!"), seen)
            finishOwned(session, fixture.binder)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun rewrittenStatusHistoryFailsAndDoesNotReleaseOwnership() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("original")))
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("rewritten")))
            val session = open(fixture)
            val failure = runCatching {
                session.generateImage(fixture.files.image, "prompt", 4) {}
            }.exceptionOrNull()
            assertNotNull("rewriting an existing sequence must fail", failure)
            assertBusy(fixture)
            finishOwned(session, fixture.binder)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun truncatedStatusHistoryFailsInsteadOfFabricatingCompletion() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("first", "second")))
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("first")))
            val session = open(fixture)
            val failure = runCatching {
                session.generateImage(fixture.files.image, "prompt", 4) {}
            }.exceptionOrNull()
            assertNotNull("truncating previously observed status must fail", failure)
            assertBusy(fixture)
            finishOwned(session, fixture.binder)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun mainThreadCancelSchedulesRemoteWorkAndSuppressesLateCallbacks() = runBlocking {
        val fixture = Fixture()
        val seen = CopyOnWriteArrayList<String>()
        try {
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("before-cancel")))
            fixture.binder.addStatus(status("TERMINAL", "CANCELED", listOf("before-cancel", "late")))
            fixture.binder.gateStatusCall = 2
            fixture.binder.holdCancel = true
            val session = open(fixture)
            val generation = async {
                runCatching {
                    session.generateImage(fixture.files.image, "prompt", 4, seen::add)
                }
            }
            withContext(Dispatchers.IO) {
                assertTrue(fixture.binder.statusGateEntered.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            }

            val returnedAt = AtomicReference<Long>()
            onMain {
                val started = System.nanoTime()
                session.cancelProcess()
                returnedAt.set(System.nanoTime() - started)
            }
            assertTrue(
                "cancelProcess must return without waiting for Binder work",
                checkNotNull(returnedAt.get()) < TimeUnit.SECONDS.toNanos(1),
            )
            assertTrue(fixture.binder.cancelGateEntered.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            assertFalse(fixture.binder.cancelTransactedOnMain)
            fixture.binder.cancelGateRelease.countDown()
            withTimeout(TEST_TIMEOUT_MILLIS) { fixture.binder.awaitCode(RETIRE) }

            fixture.binder.fireDeath()
            withTimeout(TEST_TIMEOUT_MILLIS) { session.awaitTerminated() }
            session.close()
            fixture.binder.statusGateRelease.countDown()
            assertNotNull(withTimeout(TEST_TIMEOUT_MILLIS) { generation.await().exceptionOrNull() })
            assertEquals(listOf("before-cancel"), seen)
            assertFalse(fixture.binder.transactedOnMain)
            assertFalse(fixture.context.bindOnMain)
            assertFalse(fixture.context.unbindOnMain)
        } finally {
            fixture.binder.cancelGateRelease.countDown()
            fixture.binder.statusGateRelease.countDown()
            fixture.close()
        }
    }

    @Test
    fun rejectedStartKeepsRemoteOwnershipUntilExactDeath() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.binder.startAccepted = false
            val session = open(fixture)
            val failure = runCatching {
                session.generateImage(fixture.files.image, "prompt", 4) {}
            }.exceptionOrNull()
            assertNotNull("an explicit START refusal must fail generation", failure)
            assertEquals(listOf(HELLO, START), fixture.binder.codes)
            assertBusy(fixture)

            val waiting = async { session.awaitTerminated() }
            delay(100)
            assertFalse("START refusal is not exact binder death", waiting.isCompleted)
            fixture.binder.fireDeath()
            withTimeout(TEST_TIMEOUT_MILLIS) { waiting.await() }
            session.close()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun successfulTerminalStillDrainsOnlyAtOriginalBinderDeath() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.binder.addStatus(status("TERMINAL", "SUCCEEDED", listOf("done")))
            val session = open(fixture)
            assertEquals("done", session.generateImage(fixture.files.image, "prompt", 4) {})
            val waiting = async { session.awaitTerminated() }
            delay(100)
            assertFalse("semantic success must not release process ownership", waiting.isCompleted)
            assertBusy(fixture)

            val closing = async { session.close() }
            withTimeout(TEST_TIMEOUT_MILLIS) { fixture.binder.awaitCode(RETIRE) }
            assertFalse("RETIRE reply is not exact binder death", closing.isCompleted)
            fixture.binder.fireDeath()
            withTimeout(TEST_TIMEOUT_MILLIS) {
                waiting.await()
                closing.await()
            }

            val reopened = open(fixture)
            assertEquals(2, fixture.context.bindCalls)
            reopened.close()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun bindFalseIsTrustedPreownershipRejectionAndUnbindsBeforeRetry() = runBlocking {
        val fixture = Fixture(bindResult = false)
        try {
            val rejection = try {
                open(fixture)
                fail("bind=false must reject before ownership")
                null
            } catch (rejection: LiteRtLmPreOwnershipRejection) {
                rejection
            }
            assertNotNull(rejection)
            assertFalse(rejection!!.rejection is MultimodalClientError.Busy)
            assertEquals(1, fixture.context.bindCalls)
            assertEquals(1, fixture.context.unbindCalls)

            fixture.context.bindResult = true
            open(fixture).close()
            assertEquals(2, fixture.context.bindCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun serviceDisconnectDoesNotReleaseUnreservedClaim() = runBlocking {
        val fixture = Fixture()
        try {
            val session = open(fixture)
            fixture.context.lastConnection!!.onServiceDisconnected(
                ComponentName(fixture.context.packageName, "SyntheticProcessService"),
            )
            assertBusy(fixture)
            session.close()
            open(fixture).close()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancelBeforeGenerationDoesNotSendHelloOrStart() = runBlocking {
        val fixture = Fixture()
        var session: LiteRtLmSession? = null
        try {
            val activeSession = open(fixture)
            session = activeSession
            activeSession.cancelProcess()

            val failure = runCatching {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    activeSession.generateImage(fixture.files.image, "prompt", 4) {}
                }
            }.exceptionOrNull()

            assertNotNull("pre-cancelled generation must not succeed", failure)
            assertFalse("pre-cancel must prevent HELLO", HELLO in fixture.binder.codes)
            assertFalse("pre-cancel must prevent START", START in fixture.binder.codes)
        } finally {
            cleanup(fixture, session)
        }
    }

    @Test
    fun cancellationDuringGatedHelloPreventsStartAndReturnsPromptlyOnMain() = runBlocking {
        val fixture = Fixture()
        var session: LiteRtLmSession? = null
        var generation: Job? = null
        try {
            fixture.binder.holdHello = true
            val activeSession = open(fixture)
            session = activeSession
            val generating = async {
                runCatching {
                    activeSession.generateImage(fixture.files.image, "prompt", 4) {}
                }
            }
            generation = generating
            awaitLatch(fixture.binder.helloGateEntered)

            val returnedAt = AtomicReference<Long>()
            onMain {
                val started = System.nanoTime()
                activeSession.cancelProcess()
                returnedAt.set(System.nanoTime() - started)
            }
            assertTrue(
                "cancelProcess must return without waiting for gated HELLO",
                checkNotNull(returnedAt.get()) < TimeUnit.SECONDS.toNanos(1),
            )
            assertTrue(
                "HELLO must remain gated until cancellation is requested",
                fixture.binder.helloGateRelease.count > 0L,
            )

            fixture.binder.helloGateRelease.countDown()
            val outcome = withTimeout(TEST_TIMEOUT_MILLIS) { generating.await() }
            assertNotNull("cancellation must prevent successful generation", outcome.exceptionOrNull())
            assertFalse("cancellation won before HELLO reply; START must not be sent",
                START in fixture.binder.codes)
        } finally {
            cleanup(fixture, session, listOfNotNull(generation))
        }
    }

    @Test
    fun closeDuringGatedHelloPreventsStartAfterHelloReply() = runBlocking {
        val fixture = Fixture()
        var session: LiteRtLmSession? = null
        var generation: Job? = null
        var closing: Job? = null
        try {
            fixture.binder.holdHello = true
            val activeSession = open(fixture)
            session = activeSession
            val generating = async {
                runCatching {
                    activeSession.generateImage(fixture.files.image, "prompt", 4) {}
                }
            }
            generation = generating
            awaitLatch(fixture.binder.helloGateEntered)

            val closeResult = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { activeSession.close() }
            }
            closing = closeResult
            assertFalse("close must remain pending while exact death is held", closeResult.isCompleted)

            fixture.binder.helloGateRelease.countDown()
            val outcome = withTimeout(TEST_TIMEOUT_MILLIS) { generating.await() }
            assertNotNull("close winning during HELLO must stop generation", outcome.exceptionOrNull())
            assertFalse("close won before HELLO reply; START must not be sent",
                START in fixture.binder.codes)

            fixture.binder.fireDeath()
            val closeFailure = withTimeout(TEST_TIMEOUT_MILLIS) { closeResult.await() }.exceptionOrNull()
            assertTrue("close should complete without failure", closeFailure == null)
        } finally {
            cleanup(fixture, session, listOfNotNull(generation, closing))
        }
    }

    @Test
    fun closeDuringGatedStartReplyPreventsSuccessfulGeneration() = runBlocking {
        val fixture = Fixture()
        val seen = CopyOnWriteArrayList<String>()
        var session: LiteRtLmSession? = null
        var generation: Job? = null
        var closing: Job? = null
        try {
            fixture.binder.addStatus(status("TERMINAL", "SUCCEEDED", listOf("done")))
            fixture.binder.holdStartReply = true
            val activeSession = open(fixture)
            session = activeSession
            val generating = async {
                runCatching {
                    activeSession.generateImage(fixture.files.image, "prompt", 4, seen::add)
                }
            }
            generation = generating
            awaitLatch(fixture.binder.startGateEntered)

            val closeResult = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { activeSession.close() }
            }
            closing = closeResult
            withTimeout(TEST_TIMEOUT_MILLIS) { fixture.binder.awaitCode(RETIRE) }
            assertFalse("close must remain pending for exact binder death", closeResult.isCompleted)

            fixture.binder.startGateRelease.countDown()
            val outcome = withTimeout(TEST_TIMEOUT_MILLIS) { generating.await() }
            assertTrue(
                "close during START reply must fail generation with RuntimeFailure",
                outcome.exceptionOrNull() is LiteRtLmError.RuntimeFailure,
            )
            assertTrue("close must suppress snapshots after termination", seen.isEmpty())
            assertFalse("close must remain pending for exact binder death", closeResult.isCompleted)

            fixture.binder.fireDeath()
            val closeFailure = withTimeout(TEST_TIMEOUT_MILLIS) { closeResult.await() }.exceptionOrNull()
            assertTrue("close should complete without failure", closeFailure == null)
            assertEquals(1, fixture.context.unbindCalls)
        } finally {
            cleanup(fixture, session, listOfNotNull(generation, closing))
        }
    }

    @Test
    fun concurrentCloseCallersBothWaitForExactDeathAndCleanup() = runBlocking {
        val fixture = Fixture()
        var session: LiteRtLmSession? = null
        var firstClose: Job? = null
        var secondClose: Job? = null
        try {
            fixture.binder.addStatus(status("TERMINAL", "SUCCEEDED", listOf("done")))
            val activeSession = open(fixture)
            session = activeSession
            assertEquals(
                "done",
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    activeSession.generateImage(fixture.files.image, "prompt", 4) {}
                },
            )

            val first = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { activeSession.close() }
            }
            firstClose = first
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { activeSession.close() }
            }
            secondClose = second
            assertFalse("first close must await exact binder death", first.isCompleted)
            assertFalse("second close must share the termination barrier", second.isCompleted)
            assertEquals("registered binding remains until exact death", 0, fixture.context.unbindCalls)

            fixture.binder.fireDeath()
            val failures = withTimeout(TEST_TIMEOUT_MILLIS) {
                listOf(first.await().exceptionOrNull(), second.await().exceptionOrNull())
            }
            assertEquals(listOf(null, null), failures)
            withTimeout(TEST_TIMEOUT_MILLIS) { activeSession.awaitTerminated() }
            assertEquals("both close callers return after cleanup", 1, fixture.context.unbindCalls)
        } finally {
            cleanup(fixture, session, listOfNotNull(firstClose, secondClose))
        }
    }

    @Test
    fun cumulativeSnapshotMustExtendThePreviousFullText() = runBlocking {
        val fixture = Fixture(callbackMode = LiteRtLmCallbackMode.CUMULATIVE)
        var session: LiteRtLmSession? = null
        try {
            fixture.binder.addStatus(status("GENERATING", "RUNNING", listOf("complete text")))
            fixture.binder.addStatus(
                status("TERMINAL", "SUCCEEDED", listOf("complete text", "rewritten text")),
            )
            val activeSession = open(fixture)
            session = activeSession

            val failure = runCatching {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    activeSession.generateImage(fixture.files.image, "prompt", 4) {}
                }
            }.exceptionOrNull()

            assertTrue(
                "a non-extending cumulative snapshot must fail with RuntimeFailure",
                failure is LiteRtLmError.RuntimeFailure,
            )
        } finally {
            cleanup(fixture, session)
        }
    }

    @Test
    fun emptyTerminalSuccessIsRejectedForBothModesAndSnapshotShapes() = runBlocking {
        val invalidCases = mutableListOf<String>()
        for (mode in listOf(LiteRtLmCallbackMode.DELTA, LiteRtLmCallbackMode.CUMULATIVE)) {
            val snapshotShapes = listOf(
                "[]" to emptyList<String>(),
                "[empty string]" to listOf(""),
            )
            for ((shape, chunks) in snapshotShapes) {
                val case = "$mode/$shape"
                val fixture = Fixture(callbackMode = mode)
                var session: LiteRtLmSession? = null
                try {
                    fixture.binder.addStatus(status("TERMINAL", "SUCCEEDED", chunks))
                    val activeSession = open(fixture)
                    session = activeSession

                    val failure = runCatching {
                        withTimeout(TEST_TIMEOUT_MILLIS) {
                            activeSession.generateImage(fixture.files.image, "prompt", 4) {}
                        }
                    }.exceptionOrNull()

                    if (failure == null) invalidCases += "$case returned success"
                    if (failure is NoSuchElementException) {
                        invalidCases += "$case leaked NoSuchElementException"
                    }
                    val acceptedFailure = failure is LiteRtLmError.NoResult ||
                        failure is LiteRtLmError.RuntimeFailure ||
                        (failure is IllegalArgumentException && failure.message == "invalid process status")
                    if (!acceptedFailure) {
                        invalidCases += "$case returned an unexpected failure"
                    }
                } finally {
                    cleanup(fixture, session)
                }
            }
        }
        assertTrue("invalid empty terminal cases: ${invalidCases.joinToString()}", invalidCases.isEmpty())
    }

    private suspend fun open(fixture: Fixture): LiteRtLmSession =
        try {
            fixture.factory.open(
                fixture.files.model,
                LiteRtLmEngineConfig(
                    backendPolicy = LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION,
                    cacheDir = fixture.files.cache,
                ),
            )
        } catch (missing: UnsupportedOperationException) {
            if (missing.message == "process session adapter not implemented") {
                throw AssertionError(
                    "missing LiteRT-LM process session adapter implementation",
                    missing,
                )
            }
            throw missing
        }

    private suspend fun assertBusy(fixture: Fixture) {
        try {
            open(fixture)
            fail("a second process session must be excluded while ownership is held")
        } catch (rejection: LiteRtLmPreOwnershipRejection) {
            assertTrue(rejection.rejection is MultimodalClientError.Busy)
        }
    }

    private suspend fun finishOwned(session: LiteRtLmSession, binder: ScriptedBinder) {
        session.cancelProcess()
        withTimeout(TEST_TIMEOUT_MILLIS) {
            binder.awaitCode(CANCEL)
            binder.awaitCode(RETIRE)
        }
        binder.fireDeath()
        withTimeout(TEST_TIMEOUT_MILLIS) { session.awaitTerminated() }
        session.close()
    }

    private suspend fun awaitLatch(latch: CountDownLatch) {
        withContext(Dispatchers.IO) {
            assertTrue(
                "expected test gate was not reached",
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    latch.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                },
            )
        }
    }

    private suspend fun cleanup(
        fixture: Fixture,
        session: LiteRtLmSession?,
        jobs: List<Job> = emptyList(),
    ) {
        try {
            fixture.binder.releaseGates()
            if (session != null) {
                jobs.forEach { job ->
                    job.cancel()
                    withTimeout(TEST_TIMEOUT_MILLIS) { job.join() }
                }
                fixture.binder.fireDeathIfRegistered()
                withTimeout(TEST_TIMEOUT_MILLIS) { session.awaitTerminated() }
            }
        } finally {
            fixture.close()
        }
    }

    private fun onMain(block: () -> Unit) {
        val error = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (failure: Throwable) {
                error.set(failure)
            }
        }
        error.get()?.let { throw it }
    }

    private class Fixture(
        callbackMode: LiteRtLmCallbackMode = LiteRtLmCallbackMode.DELTA,
        bindResult: Boolean = true,
    ) {
        val files = TestFiles(targetContext())
        val context = ScriptedContext(targetContext(), bindResult)
        val binder = ScriptedBinder(context.packageName + ":odai_litertlm")
        val factory = LiteRtLmProcessEngineFactory(
            context,
            callbackMode,
            LiteRtLmProcessCoordinator(),
        )

        init {
            context.binderProvider = { binder }
        }

        fun close() {
            files.close()
        }
    }

    private class TestFiles(context: Context) {
        private val directory = File(
            context.cacheDir,
            "litertlm-process-session-contract-${UUID.randomUUID()}",
        )
        val model = File(directory, "tiny-model.bin")
        val image = File(directory, "tiny-image.bin")
        val cache = File(directory, "engine-cache")

        init {
            check(directory.mkdir())
            model.writeBytes(byteArrayOf(1, 2, 3))
            image.writeBytes(byteArrayOf(4, 5))
            check(cache.mkdir())
        }

        fun close() {
            image.delete()
            model.delete()
            cache.delete()
            directory.delete()
        }
    }

    private class ScriptedContext(
        base: Context,
        @Volatile var bindResult: Boolean,
    ) : ContextWrapper(base) {
        @Volatile
        var binderProvider: (() -> ScriptedBinder)? = null
        @Volatile
        var lastConnection: ServiceConnection? = null
        @Volatile
        var bindCalls = 0
        @Volatile
        var unbindCalls = 0
        @Volatile
        var bindOnMain = false
        @Volatile
        var unbindOnMain = false
        val events = CopyOnWriteArrayList<String>()

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCalls++
            bindOnMain = Looper.myLooper() == Looper.getMainLooper()
            events += BIND
            lastConnection = conn
            if (!bindResult) return false
            val binder = checkNotNull(binderProvider).invoke()
            conn.onServiceConnected(
                ComponentName(packageName, "SyntheticProcessService"),
                binder,
            )
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCalls++
            unbindOnMain = Looper.myLooper() == Looper.getMainLooper()
            events += UNBIND
        }
    }

    private class ScriptedBinder(private val processName: String) : IBinder by Binder() {
        val codes = CopyOnWriteArrayList<Int>()
        val startRequest = AtomicReference<LiteRtLmProcessStart?>()
        val statuses = ConcurrentLinkedQueue<String>()
        private val death = AtomicReference<IBinder.DeathRecipient?>()
        private val activeNonce = AtomicReference<String?>()
        private val statusCalls = java.util.concurrent.atomic.AtomicInteger()
        @Volatile var helloAccepted = true
        @Volatile var startAccepted = true
        @Volatile var gateStatusCall = 0
        @Volatile var holdCancel = false
        @Volatile var holdStartReply = false
        @Volatile var transactedOnMain = false
        @Volatile var cancelTransactedOnMain = false
        val statusGateEntered = CountDownLatch(1)
        val statusGateRelease = CountDownLatch(1)
        val cancelGateEntered = CountDownLatch(1)
        val cancelGateRelease = CountDownLatch(1)
        val startGateEntered = CountDownLatch(1)
        val startGateRelease = CountDownLatch(1)
        @Volatile var holdHello = false
        val helloGateEntered = CountDownLatch(1)
        val helloGateRelease = CountDownLatch(1)
        val cancelReplyCompleted = CountDownLatch(1)

        fun addStatus(value: String) {
            statuses.add(value)
        }

        fun releaseGates() {
            helloGateRelease.countDown()
            cancelGateRelease.countDown()
            startGateRelease.countDown()
            statusGateRelease.countDown()
        }

        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
            death.set(recipient)
        }

        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean =
            death.compareAndSet(recipient, null)

        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val onMain = Looper.myLooper() == Looper.getMainLooper()
            transactedOnMain = transactedOnMain || onMain
            if (code == CANCEL && onMain) cancelTransactedOnMain = true
            codes += code
            data.setDataPosition(0)
            data.enforceInterface(DESCRIPTOR)
            val nonce = checkNotNull(data.readString())
            val output = checkNotNull(reply)

            when (code) {
                HELLO -> {
                    checkEqualsNoTrailing(data)
                    activeNonce.set(nonce)
                    if (holdHello) {
                        helloGateEntered.countDown()
                        check(helloGateRelease.await(GATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            "test did not release gated HELLO"
                        }
                    }
                    output.writeInt(if (helloAccepted) OK else REJECT)
                    output.writeString(nonce)
                    output.writeInt(12345)
                    output.writeString(processName)
                }

                START -> {
                    val request = LiteRtLmProcessStart(
                        modelAbsolutePath = checkNotNull(data.readString()),
                        imageAbsolutePath = checkNotNull(data.readString()),
                        cacheAbsolutePath = checkNotNull(data.readString()),
                        prompt = checkNotNull(data.readString()),
                        maxOutputTokens = data.readInt(),
                        backendPolicy = LiteRtLmBackendPolicy.valueOf(checkNotNull(data.readString())),
                    )
                    checkEqualsNoTrailing(data)
                    startRequest.set(request)
                    if (holdStartReply) {
                        startGateEntered.countDown()
                        check(startGateRelease.await(GATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            "test did not release gated START"
                        }
                    }
                    output.writeInt(if (startAccepted) OK else REJECT)
                }

                STATUS -> {
                    checkEqualsNoTrailing(data)
                    val call = statusCalls.incrementAndGet()
                    if (call == gateStatusCall) {
                        statusGateEntered.countDown()
                        check(statusGateRelease.await(GATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            "test did not release gated STATUS"
                        }
                    }
                    output.writeInt(OK)
                    val raw = statuses.poll() ?: error("no scripted STATUS response")
                    output.writeString(
                        JSONObject(raw).put("nonce", checkNotNull(activeNonce.get())).toString(),
                    )
                }

                CANCEL -> {
                    checkEqualsNoTrailing(data)
                    if (holdCancel) {
                        cancelGateEntered.countDown()
                        check(cancelGateRelease.await(GATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            "test did not release gated CANCEL"
                        }
                    }
                    output.writeInt(OK)
                }

                RETIRE -> {
                    checkEqualsNoTrailing(data)
                    output.writeInt(OK)
                }

                else -> error("unexpected Binder transaction $code")
            }
            output.setDataPosition(0)
            if (code == CANCEL) cancelReplyCompleted.countDown()
            return true
        }

        suspend fun awaitCode(code: Int) {
            withTimeout(TEST_TIMEOUT_MILLIS) {
                while (codes.count { it == code } == 0) delay(5)
            }
        }

        fun fireDeath() {
            checkNotNull(death.get()) { "no original death recipient was registered" }.binderDied()
        }

        fun fireDeathIfRegistered(): Boolean =
            death.getAndSet(null)?.let { recipient ->
                recipient.binderDied()
                true
            } ?: false

        private fun checkEqualsNoTrailing(data: Parcel) {
            check(data.dataAvail() == 0) { "unexpected trailing request data" }
        }

        companion object {
            private const val DESCRIPTOR = "io.ondeviceai.litertlm.process.v1"
            private const val OK = 0
            private const val REJECT = 1
        }
    }

    private fun status(phase: String, generation: String, chunks: List<String>): String =
        JSONObject()
            .put("nonce", "process-session-test")
            .put("phase", phase)
            .put("generation", generation)
            .put("snapshots", JSONArray().apply {
                chunks.forEachIndexed { index, text ->
                    put(JSONObject().put("sequence", index).put("text", text))
                }
            })
            .put("nativeAttempted", phase == "GENERATING" || phase == "TERMINAL")
            .put("nativeInitialized", phase == "GENERATING" || phase == "TERMINAL")
            .put("errorCode", JSONObject.NULL)
            .put("callbackMode", "UNPROVEN")
            .toString()

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 5_000L
        const val GATE_TIMEOUT_MILLIS = 2_500L
        const val BIND = "bind"
        const val UNBIND = "unbind"
        const val HELLO = 1
        const val START = 2
        const val STATUS = 3
        const val CANCEL = 4
        const val RETIRE = 5

        fun targetContext(): Context =
            InstrumentationRegistry.getInstrumentation().targetContext
    }
}
