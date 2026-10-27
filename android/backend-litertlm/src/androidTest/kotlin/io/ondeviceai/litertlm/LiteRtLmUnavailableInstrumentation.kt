package io.ondeviceai.litertlm

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelFile
import io.ondeviceai.modelstore.ModelManifest
import io.ondeviceai.modelstore.ModelProcessing
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelResources
import io.ondeviceai.modelstore.ModelStatus
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.modelstore.VersionRange
import io.ondeviceai.multimodal.MultimodalClientError
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real Android ModelStore/admission coverage with synthetic files, never model inference. */
class LiteRtLmUnavailableInstrumentation {
    private lateinit var root: File
    private lateinit var store: ModelStore
    private lateinit var backend: LiteRtLmBackend
    private lateinit var request: MultimodalRequest
    private lateinit var architecture: String
    private val ref = ModelRef("synthetic-unavailable", "r1")
    private val nativeOpens = AtomicInteger()
    private val updates = AtomicInteger()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "litertlm-unavailable-${UUID.randomUUID()}")
        check(root.mkdir())
        architecture = Build.SUPPORTED_ABIS.first { it == "arm64-v8a" || it == "x86_64" }
        store = ModelStore(File(root, "store"), ModelCompatibility(
            "litertlm.android", "0.18.1", Build.VERSION.SDK_INT.toString(), architecture))
        backend = LiteRtLmBackend(
            store, ref, LiteRtLmModelSpec("model.bin", setOf("tokenizer.json")),
            File(root, "generation-cache"),
            LiteRtLmEngineFactory { _, _ ->
                nativeOpens.incrementAndGet()
                error("unavailable admission must not open a native engine")
            },
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbi = true,
            probeState = LiteRtLmProbeState.UNPROVEN,
        )
        // Nonempty local input makes an accidental admission reach the rejecting factory.
        // The backend must reject availability before reading or copying this synthetic input.
        request = MultimodalRequest(File(root, "synthetic-input.bin").apply {
            writeText("synthetic image admission input; not user media")
        }, "synthetic unavailable admission check", 32)
    }

    @After
    fun tearDown() = runBlocking {
        try {
            if (::backend.isInitialized) backend.close()
        } finally {
            if (::root.isInitialized) {
                assertTrue("only the fresh test root must be deleted", root.deleteRecursively())
                assertFalse("test root survived cleanup", root.exists())
            }
        }
    }

    // Catches missing-assets being hidden by the unproven-runtime gate or generation bypass.
    @Test
    fun missingAssetsRejectGenerationWithoutOpeningRuntime() = runBlocking {
        assertEquals(ModelStatus.ABSENT, store.status(ref))
        assertAvailability(CapabilityStatus.REQUIRES_ASSETS, UnavailableReason.MISSING_ASSETS)
        assertGenerationUnavailable(UnavailableReason.MISSING_ASSETS)
        backend.drain()
        assertEquals(ModelStatus.ABSENT, store.status(ref))
        assertNoGenerationSideEffects()
    }

    // Catches installed assets incorrectly making an unproved native runtime available.
    @Test
    fun installedSyntheticAssetsRemainUnavailableWithoutDrainProof() = runBlocking {
        installSyntheticAssets()
        assertEquals(ModelStatus.INSTALLED, store.status(ref))
        assertAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.UNSUPPORTED_DEVICE)
        assertGenerationUnavailable(UnavailableReason.UNSUPPORTED_DEVICE)
        backend.drain()
        assertNoGenerationSideEffects()
        // Real removal also proves rejected admission left no active ModelStore lease.
        store.remove(ref)
        assertEquals(ModelStatus.ABSENT, store.status(ref))
        assertAvailability(CapabilityStatus.REQUIRES_ASSETS, UnavailableReason.MISSING_ASSETS)
    }

    @Test
    fun publicQueryUsesCurrentDeviceAndKeepsInstalledAssetsUnavailable() = runBlocking {
        installSyntheticAssets()
        assertEquals(ModelStatus.INSTALLED, store.status(ref))
        val value = LiteRtLm.availability(store,
            LiteRtLmModel(ref, "model.bin", setOf("tokenizer.json")))
        assertEquals(CapabilityStatus.UNAVAILABLE, value.status)
        assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, value.reason)
        assertEquals("litertlm.android", value.backendId)
        assertEquals("26", value.minOS)
        assertNoGenerationSideEffects()
        store.remove(ref)
        assertEquals(ModelStatus.ABSENT, store.status(ref))
        assertEquals(UnavailableReason.MISSING_ASSETS,
            LiteRtLm.availability(store,
                LiteRtLmModel(ref, "model.bin", setOf("tokenizer.json"))).reason)
    }

    // Catches close losing precedence to either asset state, or admitting generation afterward.
    @Test
    fun repeatedCloseRejectsGenerationBeforeAndAfterAssetRemoval() = runBlocking {
        installSyntheticAssets()
        backend.close()
        backend.close()
        assertAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.CLOSED)
        assertGenerationClosed()
        store.remove(ref)
        assertEquals(ModelStatus.ABSENT, store.status(ref))
        assertAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.CLOSED)
        assertGenerationClosed()
        backend.drain()
        assertNoGenerationSideEffects()
    }

    private suspend fun assertAvailability(status: CapabilityStatus, reason: UnavailableReason) {
        val value = backend.availability()
        assertEquals(status, value.status)
        assertEquals(reason, value.reason)
        assertEquals("litertlm.android", value.backendId)
        assertEquals("26", value.minOS)
    }

    private suspend fun assertGenerationUnavailable(reason: UnavailableReason) {
        val failure = runCatching { backend.generate(request) { updates.incrementAndGet() } }.exceptionOrNull()
        assertTrue("generation must reject unavailable admission", failure is MultimodalClientError.BackendUnavailable)
        val unavailable = failure as MultimodalClientError.BackendUnavailable
        assertEquals("litertlm.android", unavailable.backendId)
        assertEquals(reason, unavailable.reason)
        assertNoGenerationSideEffects()
    }

    private suspend fun assertGenerationClosed() {
        val failure = runCatching { backend.generate(request) { updates.incrementAndGet() } }.exceptionOrNull()
        assertTrue("generation after close must reject with Closed", failure is MultimodalClientError.Closed)
        assertNoGenerationSideEffects()
    }

    private fun assertNoGenerationSideEffects() {
        assertEquals("unavailable admission opened the runtime", 0, nativeOpens.get())
        assertEquals("unavailable admission emitted output", 0, updates.get())
        assertFalse("unavailable admission created a generation cache", File(root, "generation-cache").exists())
    }

    private suspend fun installSyntheticAssets() {
        val source = File(root, "synthetic-source").apply { check(mkdir()) }
        val contents = mapOf(
            "model.bin" to "synthetic availability fixture; not LiteRT-LM weights",
            "tokenizer.json" to "{\"synthetic\":true}",
        )
        val files = contents.map { (path, text) ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            File(source, path).writeBytes(bytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            ModelFile(path, "https://example.invalid/synthetic/$path", bytes.size.toLong(), digest)
        }
        val total = files.sumOf { it.bytes }
        val manifest = ModelManifest(
            modelId = ref.modelId, revision = ref.revision,
            upstreamURL = "https://example.invalid/synthetic", upstreamRevision = "synthetic-r1",
            capability = "multimodal.image", backendId = "litertlm.android",
            backendVersion = VersionRange("0.18.1", "0.19.0"), minimumOS = "26",
            architectures = listOf(architecture), formatVersion = "litertlm-model-v1", files = files,
            processing = ModelProcessing("image-file+text-prompt-v1", "text-v1", "synthetic-v1", "synthetic-v1", "synthetic-v1", 1),
            resources = ModelResources(total, total, total),
            licenseIdentifier = "CC0-1.0", licenseURL = "https://creativecommons.org/publicdomain/zero/1.0/",
            attribution = "generated synthetic availability test files",
            redistribution = "synthetic test files only; not model weights",
        )
        store.install(manifest, source)
    }
}
