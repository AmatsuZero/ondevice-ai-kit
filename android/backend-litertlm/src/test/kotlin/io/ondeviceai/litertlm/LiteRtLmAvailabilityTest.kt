package io.ondeviceai.litertlm

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.modelstore.ModelStatus
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelManifest
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelFile
import io.ondeviceai.modelstore.ModelProcessing
import io.ondeviceai.modelstore.ModelResources
import io.ondeviceai.modelstore.VersionRange
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LiteRtLmAvailabilityTest {
    @Test
    fun missingAssetsAreStableAndDoNotInitializeRuntime() {
        val availability = LiteRtLmAvailability.forState(
            closed = false,
            apiLevel = 35,
            supportedAbi = true,
            modelStatus = ModelStatus.ABSENT,
            probeState = LiteRtLmProbeState.PROVEN_CUMULATIVE,
        )
        assertEquals(CapabilityStatus.REQUIRES_ASSETS, availability.status)
        assertEquals(UnavailableReason.MISSING_ASSETS, availability.reason)
    }

    @Test
    fun missingAssetsStayVisibleWhenDrainProbeIsUnproven() = runBlocking {
        val root = Files.createTempDirectory("litertlm-absent").toFile()
        try {
            val store = ModelStore(File(root, "store"), ModelCompatibility("litertlm.android", "0.18.1", "35", "arm64-v8a"))
            val result = LiteRtLmAvailability.forModel(store, ModelRef("missing", "r1"),
                LiteRtLmModelSpec("model.bin", setOf("tokenizer.json")), false, 35, true,
                LiteRtLmProbeState.UNPROVEN)
            assertEquals(CapabilityStatus.REQUIRES_ASSETS, result.status)
            assertEquals(UnavailableReason.MISSING_ASSETS, result.reason)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unprovenRuntimeAndUnsupportedDeviceFailClosed() {
        val unproven = LiteRtLmAvailability.forState(false, 35, true, ModelStatus.INSTALLED, LiteRtLmProbeState.UNPROVEN)
        assertEquals(CapabilityStatus.UNAVAILABLE, unproven.status)
        assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, unproven.reason)

        val oldOs = LiteRtLmAvailability.forState(false, 25, true, ModelStatus.INSTALLED, LiteRtLmProbeState.PROVEN_CUMULATIVE)
        assertEquals(UnavailableReason.REQUIRES_OS_VERSION, oldOs.reason)

        val abi = LiteRtLmAvailability.forState(false, 35, false, ModelStatus.INSTALLED, LiteRtLmProbeState.PROVEN_CUMULATIVE)
        assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, abi.reason)
    }

    @Test
    fun closedStateWinsAndErrorMessagesDoNotContainSensitiveText() {
        val availability = LiteRtLmAvailability.forState(true, 35, true, ModelStatus.INSTALLED, LiteRtLmProbeState.PROVEN_CUMULATIVE)
        assertEquals(CapabilityStatus.UNAVAILABLE, availability.status)
        assertEquals(UnavailableReason.CLOSED, availability.reason)
        val path = "/private/model.bin"
        val prompt = "secret prompt"
        val errors = listOf(
            LiteRtLmError.InvalidModel(),
            LiteRtLmError.RuntimeFailure(),
            LiteRtLmError.NoResult(),
            LiteRtLmError.DrainFailure(),
            LiteRtLmError.RuntimeUnavailable(),
        )
        errors.forEach {
            assertFalse(it.message.orEmpty().contains(path))
            assertFalse(it.message.orEmpty().contains(prompt))
        }
        assertTrue(errors.all { it.message!!.isNotBlank() })
    }

    @Test
    fun verifiedAssetQueryAcquiresAndReleasesLeaseWithoutEngine() = runBlocking {
        val root = Files.createTempDirectory("litertlm-assets").toFile()
        try {
            val source = File(root, "source").apply { mkdirs() }
            val paths = listOf("model.bin", "tokenizer.json")
            paths.forEach { File(source, it).writeText("fixture-$it") }
            val files = paths.map { path ->
                val bytes = File(source, path).readBytes()
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                ModelFile(path, "https://models.example/$path", bytes.size.toLong(), digest)
            }
            val total = files.sumOf { it.bytes }
            val manifest = ModelManifest(
                modelId = "fixture", revision = "r1", upstreamURL = "https://models.example/fixture",
                upstreamRevision = "fixed-revision", capability = "multimodal.image",
                backendId = "litertlm.android", backendVersion = VersionRange("0.18.1", "0.19.0"),
                minimumOS = "26", architectures = listOf("arm64-v8a"), formatVersion = "litertlm-model-v1",
                files = files,
                processing = ModelProcessing("image-file+text-prompt-v1", "text-v1", "preprocess-v1", "tokenizer-v1", "prompt-v1", 1),
                resources = ModelResources(total, total, total),
                licenseIdentifier = "Apache-2.0", licenseURL = "https://www.apache.org/licenses/LICENSE-2.0",
                attribution = "test fixture", redistribution = "permitted",
            )
            val store = ModelStore(File(root, "store"), ModelCompatibility("litertlm.android", "0.18.1", "35", "arm64-v8a"))
            val spec = LiteRtLmModelSpec("model.bin", setOf("tokenizer.json"))
            val ref = ModelRef("fixture", "r1")
            assertEquals(CapabilityStatus.REQUIRES_ASSETS,
                LiteRtLmAvailability.forModel(store, ref, spec, false, 35, true, LiteRtLmProbeState.PROVEN_CUMULATIVE).status)
            store.install(manifest, source)
            assertEquals(CapabilityStatus.AVAILABLE,
                LiteRtLmAvailability.forModel(store, ref, spec, false, 35, true, LiteRtLmProbeState.PROVEN_CUMULATIVE).status)
            store.remove(ref)
            assertEquals(ModelStatus.ABSENT, store.status(ref))
        } finally {
            root.deleteRecursively()
        }
    }
}
