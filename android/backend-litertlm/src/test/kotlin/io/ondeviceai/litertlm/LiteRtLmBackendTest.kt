package io.ondeviceai.litertlm

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.modelstore.ModelInUseException
import io.ondeviceai.multimodal.MultimodalClientError
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiteRtLmBackendTest {
    @Test
    fun closeJoinsActiveAvailabilityQueryBeforeReturning() = runBlocking {
        val fixture = BackendFixture()
        try {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val backend = LiteRtLmBackend(fixture.store, fixture.ref, fixture.spec, fixture.cache,
                LiteRtLmEngineFactory { _, _ -> error("generation must not start") },
                apiLevel = 35, supportedAbi = true, probeState = LiteRtLmProbeState.PROVEN_CUMULATIVE,
                availabilitySource = {
                    entered.complete(Unit)
                    release.await()
                    CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = "litertlm.android")
                })
            val query = async(start = CoroutineStart.UNDISPATCHED) { backend.availability() }
            entered.await()
            val closing = async(start = CoroutineStart.UNDISPATCHED) { backend.close() }
            assertTrue(!closing.isCompleted)
            release.complete(Unit)
            assertEquals(UnavailableReason.CLOSED, query.await().reason)
            closing.await()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun outputAndImageBudgetsRejectOverflowWithoutNativeOpen() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            var opened = 0
            val budgets = mutableListOf<Int>()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                opened++
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        budgets += maxOutputTokens
                        return "text"
                    }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            })
            backend.generate(MultimodalRequest(fixture.image, "describe", 512))
            assertFailsWith<MultimodalClientError.InvalidInput> {
                backend.generate(MultimodalRequest(fixture.image, "describe", 513))
            }
            RandomAccessFile(fixture.image, "rw").use { it.setLength(16L * 1024 * 1024 + 1) }
            assertFailsWith<MultimodalClientError.InvalidInput> {
                backend.generate(MultimodalRequest(fixture.image, "describe", 32))
            }
            assertEquals(listOf(512), budgets)
            assertEquals(1, opened)
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun blankNativeOutputIsRejectedAfterProvenDrainAndLeaseRelease() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            var closes = 0
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String = " "
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() { closes++ }
                }
            })
            assertFailsWith<LiteRtLmError.NoResult> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            assertEquals(1, closes)
            fixture.store.remove(fixture.ref)
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun failedPrivateCacheDeletionRetainsLeaseAndProhibitsAnotherGeneration() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            var opened = 0
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                opened++
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String = "text"
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            }, cacheCleaner = LiteRtLmCacheCleaner {
                runBlocking { assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) } }
                false
            })
            assertFailsWith<LiteRtLmError.RuntimeFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            assertTrue(File(fixture.cache, "litertlm").walkTopDown().any { it.isFile })
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertFailsWith<MultimodalClientError.Closed> {
                backend.generate(MultimodalRequest(fixture.image, "again"))
            }
            assertEquals(1, opened)
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }
}
