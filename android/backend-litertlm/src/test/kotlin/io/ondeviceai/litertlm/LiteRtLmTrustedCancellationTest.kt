package io.ondeviceai.litertlm

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.modelstore.ModelInUseException
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiteRtLmTrustedCancellationTest {
    @Test
    fun trustedFactoryCancellationPreservesCallerCancellationAndReleasesPrivateState() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val original = CancellationException("local connection cancelled")
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                throw LiteRtLmPreOwnershipCancellation(original)
            })

            val caught = assertFailsWith<CancellationException> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }

            // Opening must recognize the factory's released-local-claim proof, not poison as an unknown owner.
            assertCancellationIdentityInCauseChain(caught, original)
            assertTrue(privateCacheFiles(fixture).isEmpty(), "Private generation cache must be removed")
            assertEquals(CapabilityStatus.AVAILABLE, backend.availability().status)
            backend.close()
            fixture.store.remove(fixture.ref)
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun trustedCancellationAllowsSameBackendToOpenAndDrainNextGeneration() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val original = CancellationException("coordinator connect cancelled")
            val events = mutableListOf<String>()
            var opens = 0
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                events += "open"
                if (opens++ == 0) throw LiteRtLmPreOwnershipCancellation(original)
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        onSnapshot("fixed output")
                        return "fixed output"
                    }
                    override fun cancelProcess() { events += "cancel" }
                    override suspend fun awaitTerminated() { events += "drain" }
                    override suspend fun close() { events += "close" }
                }
            })

            val caught = assertFailsWith<CancellationException> {
                backend.generate(MultimodalRequest(fixture.image, "first"))
            }
            assertCancellationIdentityInCauseChain(caught, original)
            assertTrue(privateCacheFiles(fixture).isEmpty())

            assertEquals("fixed output", backend.generate(MultimodalRequest(fixture.image, "second")).text)
            assertEquals(listOf("open", "open", "cancel", "drain", "close"), events)
            backend.close()
            fixture.store.remove(fixture.ref)
            assertTrue(privateCacheFiles(fixture).isEmpty())
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun unmarkedFactoryCancellationRemainsUnsafeAndRetainsLeaseAndCache() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                throw CancellationException("unproven factory cancellation")
            })

            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }

            assertTrue(privateCacheFiles(fixture).isNotEmpty(), "Unsafe opening must retain its private cache")
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertEquals(CapabilityStatus.UNAVAILABLE, backend.availability().status)
            assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, backend.availability().reason)
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun trustedCancellationWithFailedCacheCleanupRetainsLeaseAndClosesBackend() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val backend = fixture.backend(
                LiteRtLmEngineFactory { _, _ ->
                    throw LiteRtLmPreOwnershipCancellation(
                        CancellationException("locally released coordinator claim"))
                },
                cacheCleaner = LiteRtLmCacheCleaner { false },
            )

            assertFailsWith<LiteRtLmError.RuntimeFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }

            assertTrue(privateCacheFiles(fixture).isNotEmpty(), "Failed cleanup must retain its evidence")
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertEquals(CapabilityStatus.UNAVAILABLE, backend.availability().status)
            assertEquals(UnavailableReason.CLOSED, backend.availability().reason)
            backend.close()
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun childCancellationDuringFactoryConnectUsesReleasedClaimWithoutPoisoning() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            supervisorScope {
                val enteredFactory = CompletableDeferred<Unit>()
                val observedCancellation = CompletableDeferred<CancellationException>()
                val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                    enteredFactory.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (cancelled: CancellationException) {
                        observedCancellation.complete(cancelled)
                        throw LiteRtLmPreOwnershipCancellation(cancelled)
                    }
                })
                val callerCancellation = CancellationException("generation cancelled during connect")
                val generation = async {
                    backend.generate(MultimodalRequest(fixture.image, "describe"))
                }

                withTimeout(5_000L) { enteredFactory.await() }
                generation.cancel(callerCancellation)
                val factoryCancellation = withTimeout(5_000L) { observedCancellation.await() }
                withTimeout(5_000L) { generation.join() }

                assertCancellationIdentityInCauseChain(factoryCancellation, callerCancellation)
                assertTrue(generation.isCancelled, "The caller's generation must remain cancelled")
                assertEquals(CapabilityStatus.AVAILABLE, backend.availability().status)
                assertTrue(privateCacheFiles(fixture).isEmpty(), "Safe cancellation must remove private cache")
                backend.close()
                fixture.store.remove(fixture.ref)
            }
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    private fun privateCacheFiles(fixture: BackendFixture): List<File> =
        File(fixture.cache, "litertlm").walkTopDown().filter { it.isFile }.toList()

    private fun assertCancellationIdentityInCauseChain(
        actual: Throwable,
        original: CancellationException,
    ) {
        var cause: Throwable? = actual
        val seen = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>())
        while (cause != null && seen.add(cause)) {
            if (cause === original) return
            cause = cause.cause
        }
        assertTrue(false, "The original cancellation must appear by identity in the cause chain")
    }
}
