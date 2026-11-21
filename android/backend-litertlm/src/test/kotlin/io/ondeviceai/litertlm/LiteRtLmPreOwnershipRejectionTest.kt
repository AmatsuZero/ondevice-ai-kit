package io.ondeviceai.litertlm

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.modelstore.ModelInUseException
import io.ondeviceai.multimodal.MultimodalClientError
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiteRtLmPreOwnershipRejectionTest {
    @Test
    fun wrappedBusyPropagatesOriginalAndReleasesPrivateStateAndLease() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val rejection = MultimodalClientError.Busy()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                throw LiteRtLmPreOwnershipRejection(rejection)
            })

            val caught = assertFailsWith<MultimodalClientError.Busy> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }

            assertRejectionPreserved(rejection, caught)
            assertNoPrivateCacheFiles(fixture)
            backend.close()
            fixture.store.remove(fixture.ref)
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun wrappedRejectionAllowsSameBackendToOpenAndDrainNextGeneration() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val rejection = MultimodalClientError.Busy()
            val events = mutableListOf<String>()
            var opens = 0
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                events += "open"
                if (opens++ == 0) throw LiteRtLmPreOwnershipRejection(rejection)
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        onSnapshot("fixedCUMULATIVE")
                        return "fixedCUMULATIVE"
                    }
                    override fun cancelProcess() { events += "cancel" }
                    override suspend fun awaitTerminated() { events += "drain" }
                    override suspend fun close() { events += "close" }
                }
            })

            val caught = assertFailsWith<MultimodalClientError.Busy> {
                backend.generate(MultimodalRequest(fixture.image, "first"))
            }
            assertRejectionPreserved(rejection, caught)
            assertNoPrivateCacheFiles(fixture)
            assertEquals("fixedCUMULATIVE",
                backend.generate(MultimodalRequest(fixture.image, "second")).text)
            assertEquals(listOf("open", "open", "cancel", "drain", "close"), events)
            backend.drain()
            backend.close()
            fixture.store.remove(fixture.ref)
            assertNoPrivateCacheFiles(fixture)
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun wrappedBackendUnavailablePreservesReasonAndReleasesOwnership() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val rejection = MultimodalClientError.BackendUnavailable(
                "litertlm.android", UnavailableReason.BACKEND_NOT_LINKED)
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                throw LiteRtLmPreOwnershipRejection(rejection)
            })

            val caught = assertFailsWith<MultimodalClientError.BackendUnavailable> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }

            assertRejectionPreserved(rejection, caught)
            assertEquals(UnavailableReason.BACKEND_NOT_LINKED, caught.reason)
            assertNoPrivateCacheFiles(fixture)
            assertEquals(CapabilityStatus.AVAILABLE, backend.availability().status)
            backend.close()
            fixture.store.remove(fixture.ref)
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun rawBusyDuringOpenRemainsUnsafeAndRetainsLeaseAndCache() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                throw MultimodalClientError.Busy()
            })

            assertFailsWith<LiteRtLmError.DrainFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }

            assertTrue(privateCacheFiles(fixture).isNotEmpty())
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertEquals(CapabilityStatus.UNAVAILABLE, backend.availability().status)
            assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, backend.availability().reason)
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun failedCacheCleanupAfterTypedRejectionIsNotReportedAsSuccess() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val backend = fixture.backend(
                LiteRtLmEngineFactory { _, _ ->
                    throw LiteRtLmPreOwnershipRejection(MultimodalClientError.Busy())
                },
                cacheCleaner = LiteRtLmCacheCleaner { false },
            )

            assertFailsWith<LiteRtLmError.RuntimeFailure> {
                backend.generate(MultimodalRequest(fixture.image, "describe"))
            }

            assertTrue(privateCacheFiles(fixture).isNotEmpty())
            assertFailsWith<ModelInUseException> { fixture.store.remove(fixture.ref) }
            assertEquals(CapabilityStatus.UNAVAILABLE, backend.availability().status)
            assertEquals(UnavailableReason.CLOSED, backend.availability().reason)
            backend.close()
            Unit
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    private fun assertNoPrivateCacheFiles(fixture: BackendFixture) {
        assertTrue(privateCacheFiles(fixture).isEmpty())
    }

    private fun assertRejectionPreserved(
        rejection: MultimodalClientError,
        caught: MultimodalClientError,
    ) {
        assertEquals(rejection.javaClass, caught.javaClass)
        assertEquals(rejection.message, caught.message)

        var cause: Throwable? = caught
        while (cause != null && cause !== rejection) {
            cause = cause.cause
        }
        assertTrue(cause === rejection, "Original rejection is absent from the cause chain")
    }

    private fun privateCacheFiles(fixture: BackendFixture): List<File> =
        File(fixture.cache, "litertlm").walkTopDown().filter { it.isFile }.toList()
}
