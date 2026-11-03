package io.ondeviceai.litertlm

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LiteRtLmSynchronousSessionTest {
    @Test
    fun successfulSendPropagatesInputBudgetAndClosesOnce() = runBlocking {
        val calls = mutableListOf<String>()
        val session = LiteRtLmSynchronousSession(object : LiteRtLmSynchronousEngine {
            override fun generate(image: File, prompt: String, maxOutputTokens: Int): String {
                calls += "${image.name}:$prompt:$maxOutputTokens"
                return "actual response"
            }
            override fun close() { calls += "close" }
        })
        val updates = mutableListOf<String>()
        assertEquals("actual response", session.generateImage(File("image.png"), "describe", 37, updates::add))
        session.cancelProcess()
        session.awaitTerminated()
        session.close()
        session.close()
        assertEquals(listOf("actual response"), updates)
        assertEquals(listOf("image.png:describe:37", "close"), calls)
    }

    @Test
    fun failedNativeSendCannotCertifyDrainOrClose() = runBlocking {
        var closed = false
        val session = LiteRtLmSynchronousSession(object : LiteRtLmSynchronousEngine {
            override fun generate(image: File, prompt: String, maxOutputTokens: Int): String = error("native failure")
            override fun close() { closed = true }
        })
        assertFailsWith<IllegalStateException> { session.generateImage(File("image.png"), "describe", 32) {} }
        assertFailsWith<LiteRtLmError.DrainFailure> { session.awaitTerminated() }
        assertFailsWith<LiteRtLmError.DrainFailure> { session.close() }
        assertFalse(closed)
    }

    @Test
    fun cancellationWaitsForBlockingSenderBeforeLeaseAndCacheRelease() = runBlocking {
        val fixture = BackendFixture()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val calls = mutableListOf<String>()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                LiteRtLmSynchronousSession(object : LiteRtLmSynchronousEngine {
                    override fun generate(image: File, prompt: String, maxOutputTokens: Int): String {
                        entered.complete(Unit)
                        check(release.await(10, TimeUnit.SECONDS))
                        calls += "send-return"
                        return "discarded after cancellation"
                    }
                    override fun close() { calls += "close" }
                })
            }, LiteRtLmProbeState.SYNCHRONOUS)
            val updates = mutableListOf<String>()
            val task = async { backend.generate(io.ondeviceai.multimodal.MultimodalRequest(fixture.image, "describe")) { updates += it.text } }
            entered.await()
            task.cancel()
            assertFalse(task.isCompleted)
            assertFailsWith<io.ondeviceai.modelstore.ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertEquals(emptyList(), calls)
            release.countDown()
            task.join()
            backend.drain()
            backend.close()
            assertEquals(listOf("send-return", "close"), calls)
            assertEquals(emptyList(), updates)
            fixture.store.remove(fixture.ref)
        } finally {
            release.countDown()
            fixture.root.deleteRecursively()
        }
    }
}
