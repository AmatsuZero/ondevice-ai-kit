package io.ondeviceai.litertlm

import io.ondeviceai.modelstore.ModelInUseException
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiteRtLmPoisonedTerminalTest {
    @Test
    fun drainThatNeverReturnsPoisonsAfterThirtySecondDeadline() = runBlocking {
        val fixture = BackendFixture()
        val releaseDrain = CompletableDeferred<Unit>()
        try {
            fixture.install()
            val drainEntered = CompletableDeferred<Unit>()
            var nativeCloses = 0
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String = "text"
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() {
                        drainEntered.complete(Unit)
                        releaseDrain.await()
                    }
                    override suspend fun close() { nativeCloses++ }
                }
            })
            val generation = async { runCatching { backend.generate(MultimodalRequest(fixture.image, "describe")) } }
            try {
                drainEntered.await()
                assertFailsWith<LiteRtLmError.DrainFailure> {
                    withTimeout(35_000) { generation.await().getOrThrow() }
                }
                assertEquals(0, nativeCloses)
                assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
                assertTrue(File(fixture.cache, "litertlm").walkTopDown().any { it.isFile })
                assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, backend.availability().reason)
                assertFailsWith<LiteRtLmError.DrainFailure> { backend.drain() }
                assertFailsWith<LiteRtLmError.DrainFailure> { backend.close() }
                assertFailsWith<LiteRtLmError.DrainFailure> {
                    backend.generate(MultimodalRequest(fixture.image, "again"))
                }
            } finally {
                releaseDrain.complete(Unit)
                generation.join()
            }
            Unit
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun callbackModeDifferentFromProbeProofPoisonsBeforeGeneration() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            var generated = false
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.DELTA
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String { generated = true; return "text" }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            })
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            assertTrue(!generated)
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            Unit
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun nativeOpenFailureCannotReleaseLeaseWithoutDrainProof() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                throw IllegalStateException("partial native initialization")
            })
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(File(fixture.cache, "litertlm").walkTopDown().any { it.isFile })
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun failedDrainRetainsLeaseAndCacheAndRejectsReentry() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val events = mutableListOf<String>()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                events += "open"
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        events += "generate"
                        onSnapshot("text")
                        return "text"
                    }
                    override fun cancelProcess() { events += "cancel" }
                    override suspend fun awaitTerminated(): Unit = throw IllegalStateException("native still alive")
                    override suspend fun close() { events += "native-close" }
                }
            })
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertTrue(File(fixture.cache, "litertlm").walkTopDown().any { it.isFile })
            assertFailsWith<LiteRtLmError.DrainFailure> { backend.drain() }
            assertFailsWith<LiteRtLmError.DrainFailure> { backend.close() }
            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "again"))
            }
            assertEquals(listOf("open", "generate", "cancel"), events)
        } finally { fixture.root.deleteRecursively() }
    }
}
