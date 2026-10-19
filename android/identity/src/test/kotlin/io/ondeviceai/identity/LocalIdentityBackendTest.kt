package io.ondeviceai.identity

import io.ondeviceai.core.*
import io.ondeviceai.modelstore.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LocalIdentityBackendTest {
    @Test fun adapterChecksStopEvenWhenRuntimeIgnoresSignal() = runBlocking<Unit> {
        LocalModelFixture().use { f ->
            f.install()
            val backend = LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime)
            val signal = IdentityStopSignal()
            f.runtime.onEmbed = { signal.requestStop(IdentityStopReason.CANCELLED) }
            assertCode(IdentityErrorCode.CANCELLED) { backend.embed(IdentityProbe("unit", listOf(1f, 0f)), signal) }
            backend.closeAndWait()
        }
    }
    @Test fun runtimeVersionMustMatchManifestIndependentlyOfStoreCompatibility() = runBlocking<Unit> {
        LocalModelFixture().use { f ->
            f.install()
            for (version in listOf("2.0.0", "0.9.0", "not-a-version")) {
                f.runtime.version = version
                assertCode(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH) { LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime) }
            }
            f.store.remove(f.manifest.ref)
        }
    }
    @Test fun actualModelIsPinnedUntilCloseAndWait() = runBlocking<Unit> {
        LocalModelFixture().use { f ->
            f.install()
            val backend = LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime)
            assertFailsWith<ModelInUseException> { f.store.remove(f.manifest.ref) }
            val probe = IdentityProbe("unit", listOf(1f, 0f))
            assertEquals(listOf(1f, 0f), backend.embed(probe).values)
            assertEquals(1, backend.evaluateQuality(probe).faceCount)
            assertEquals("v1", backend.modelBinding.revision)
            backend.closeAndWait(); backend.closeAndWait()
            assertEquals(1, f.runtime.closeCount)
            f.store.remove(f.manifest.ref)
            assertCode(IdentityErrorCode.CLOSED) { backend.embed(probe) }
        }
    }

    @Test fun missingModelAndWrongBindingDoNotLeakLeaseOrConsumeRuntime() = runBlocking<Unit> {
        LocalModelFixture().use { f ->
            assertCode(IdentityErrorCode.BACKEND_UNAVAILABLE) { LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime) }
            f.install()
            f.runtime.bindingRevision = "v2"
            assertCode(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH) { LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime) }
            f.store.remove(f.manifest.ref)
            assertEquals(0, f.runtime.closeCount)
        }
    }

    @Test fun wrongManifestPreprocessReleasesLease() = runBlocking<Unit> {
        LocalModelFixture("other-p1").use { f ->
            f.install()
            assertCode(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH) { LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime) }
            f.store.remove(f.manifest.ref)
        }
    }

    @Test fun unverifiedPolicyAndSyntheticImagesNeverProduceIdentityOutcome() = runBlocking<Unit> {
        LocalModelFixture().use { f ->
            f.install()
            val backend = LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime)
            val policy = ThresholdPolicy("draft", "v1", backend.embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.UNVERIFIED)
            assertCode(IdentityErrorCode.THRESHOLD_UNVERIFIED) { policy.validate(backend.embeddingSpace) }
            val image = IdentityProbe(byteArrayOf(0, 1, 2, 3), "synthetic")
            assertCode(IdentityErrorCode.INVALID_INPUT) { backend.embed(image) }
            assertCode(IdentityErrorCode.INVALID_INPUT) { backend.evaluateQuality(image) }
            backend.closeAndWait()
        }
    }

    @Test fun runtimeBindingDriftCannotReturnEmbedding() = runBlocking<Unit> {
        LocalModelFixture().use { f ->
            f.install()
            val backend = LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime)
            f.runtime.onEmbed = { f.runtime.bindingRevision = "v2" }
            assertCode(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH) { backend.embed(IdentityProbe("unit", listOf(1f, 0f))) }
            assertEquals("v1", backend.modelBinding.revision)
            assertEquals(CapabilityStatus.UNAVAILABLE, backend.availability().status)
            backend.closeAndWait()
        }
    }

    @Test fun closePinsLeaseUntilRuntimeFinishesAndCallbackCanReenter() = runBlocking<Unit> {
        LocalModelFixture().use { f ->
            f.install()
            val backend = LocalIdentityBackend.open(f.store, f.manifest.ref, f.runtime)
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            f.runtime.onEmbed = { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)) }
            f.runtime.onClose = { backend.close() }
            val pool = Executors.newSingleThreadExecutor()
            try {
                val work = pool.submit<IdentityErrorCode?> {
                    try { backend.embed(IdentityProbe("unit", listOf(1f, 0f))); null } catch (e: IdentityError) { e.code }
                }
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                backend.close()
                assertEquals(UnavailableReason.CLOSED, backend.availability().reason)
                assertFailsWith<ModelInUseException> { f.store.remove(f.manifest.ref) }
                release.countDown()
                assertEquals(IdentityErrorCode.CLOSED, work.get(3, TimeUnit.SECONDS))
                backend.closeAndWait()
                f.store.remove(f.manifest.ref)
                assertEquals(1, f.runtime.closeCount)
            } finally { release.countDown(); pool.shutdownNow() }
        }
    }
}

private inline fun assertCode(code: IdentityErrorCode, action: () -> Unit) {
    assertEquals(code, assertFailsWith<IdentityError> { action() }.code)
}

private class LocalModelFixture(preprocess: String = "fixture-p1") : AutoCloseable {
    val root = Files.createTempDirectory("odai-local-backend").toFile()
    val source = File(root, "source").apply { mkdirs() }
    val runtime = LeasedFixtureRuntime()
    val store = ModelStore(File(root, "models"), ModelCompatibility("local.fixture", "1.0.0", "26", "arm64"))
    val manifest: ModelManifest
    init {
        val bytes = "not a biometric model".toByteArray()
        File(source, "fixture.bin").writeBytes(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        manifest = ModelManifest(modelId = "synthetic", revision = "v1", upstreamURL = "https://models.example/fixture", upstreamRevision = "synthetic-v1",
            capability = "face-identity", backendId = "local.fixture", backendVersion = VersionRange("1.0.0", "2.0.0"), minimumOS = "26", architectures = listOf("arm64"),
            formatVersion = "fixture-1", files = listOf(ModelFile("fixture.bin", "https://models.example/fixture.bin", bytes.size.toLong(), hash)),
            processing = ModelProcessing("fixture-vector-2", "float32-2", preprocess, null, null, 2),
            resources = ModelResources(bytes.size.toLong(), bytes.size.toLong(), bytes.size.toLong()), licenseIdentifier = "CC0-1.0",
            licenseURL = "https://creativecommons.org/publicdomain/zero/1.0/", attribution = "Synthetic SDK fixture", redistribution = "permitted")
    }
    suspend fun install() { store.install(manifest, source) }
    override fun close() { root.deleteRecursively() }
}

private class LeasedFixtureRuntime : IdentityBackend {
    override val backendId = "local.fixture"
    var version = "1.0.0"
    override val backendVersion get() = version
    @Volatile var bindingRevision = "v1"
    var onEmbed: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var closeCount = 0
    override val modelBinding get() = ModelBinding(ModelBindingKind.MODEL, "synthetic", bindingRevision)
    override val embeddingSpace get() = EmbeddingSpaceId(setOf(IdentityCapability.VERIFICATION, IdentityCapability.IDENTIFICATION), backendId, backendVersion,
        modelBinding, "1", 2, FeatureDType.FLOAT32, Endianness.LITTLE, "none", "fixture-p1", "cosine", ScoreDirection.HIGHER_IS_BETTER)
    override fun availability() = CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    override fun embed(probe: IdentityProbe): IdentityEmbedding {
        if (probe.fixtureID == null || probe.vector == null) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val result = IdentityEmbedding(embeddingSpace, probe.vector)
        onEmbed?.invoke()
        return result
    }
    override fun embed(probe: IdentityProbe, stopSignal: IdentityStopSignal) = embed(probe)
    override fun evaluateQuality(probe: IdentityProbe): IdentityQuality {
        if (probe.fixtureID == null) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return IdentityQuality(1, 1.0)
    }
    override fun close() { closeCount++; onClose?.invoke() }
}
