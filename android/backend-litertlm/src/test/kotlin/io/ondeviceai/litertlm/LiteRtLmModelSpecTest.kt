package io.ondeviceai.litertlm

import io.ondeviceai.modelstore.ModelFile
import io.ondeviceai.modelstore.ModelManifest
import io.ondeviceai.modelstore.ModelProcessing
import io.ondeviceai.modelstore.ModelResources
import io.ondeviceai.modelstore.VersionRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiteRtLmModelSpecTest {
    @Test
    fun fixedIdentityAndBudgetsAreStable() {
        val spec = LiteRtLmModelSpec("model.bin", setOf("tokenizer.json", "config.json"))
        assertEquals("litertlm.android", spec.backendId)
        assertEquals("multimodal.image", spec.capability)
        assertEquals("litertlm-model-v1", spec.formatVersion)
        assertEquals("image-file+text-prompt-v1", spec.inputSignature)
        assertEquals("litertlm-android:0.18.1", spec.runtimeRevision)
        assertEquals(512, spec.maxOutputTokens)
        assertEquals(16L * 1024 * 1024, spec.maxImageBytes)
        assertEquals(LiteRtLmBackendPolicy.CPU_ONLY, spec.backendPolicy)
        assertEquals(1, spec.maxNumImages)
    }

    @Test
    fun unsafeRelativePathsAndOutputBudgetsAreRejected() {
        val metadata = setOf("tokenizer.json")
        assertFailsWith<IllegalArgumentException> { LiteRtLmModelSpec("../model.bin", metadata) }
        assertFailsWith<IllegalArgumentException> { LiteRtLmModelSpec("/model.bin", metadata) }
        assertFailsWith<IllegalArgumentException> { LiteRtLmModelSpec("model.bin", setOf("../tokenizer.json")) }
        assertFailsWith<IllegalArgumentException> { LiteRtLmModelSpec("model.bin", metadata, maxOutputTokens = 0) }
        assertFailsWith<IllegalArgumentException> { LiteRtLmModelSpec("model.bin", metadata, maxOutputTokens = 128) }
        assertFailsWith<IllegalArgumentException> { LiteRtLmModelSpec("model.bin", metadata, maxOutputTokens = 513) }
    }

    @Test
    fun manifestMustMatchIdentityVersionLicenseAndRequiredFiles() {
        val spec = LiteRtLmModelSpec("model.bin", setOf("tokenizer.json", "config.json"))
        val manifest = manifestFor(spec)
        assertTrue(spec.matchesManifest(manifest))
        assertFalse(spec.matchesManifest(manifest.copy(capability = "multimodal.video")))
        assertFalse(spec.matchesManifest(manifest.copy(formatVersion = "other")))
        assertFalse(spec.matchesManifest(manifest.copy(backendVersion = VersionRange("0.18.2", "1.0.0"))))
        assertFalse(spec.matchesManifest(manifest.copy(files = manifest.files.dropLast(1))))
        assertFalse(spec.matchesManifest(manifest.copy(licenseIdentifier = "")))
    }

    @Test
    fun modelPathResolvesOnlyInsideLeaseDirectory() {
        val spec = LiteRtLmModelSpec("models/model.bin", setOf("tokenizer.json"))
        val lease = java.io.File("/tmp/litertlm-lease")
        assertEquals(java.io.File(lease, "models/model.bin"), spec.resolveModelPath(lease))
    }
}

private fun manifestFor(spec: LiteRtLmModelSpec): ModelManifest {
    val files = (listOf(spec.modelPathRelative) + spec.metadataPathsRelative).map { path ->
        ModelFile(path, "https://models.example/$path", 1, "0".repeat(64))
    }
    return ModelManifest(
        modelId = "fixture-vlm",
        revision = "r1",
        upstreamURL = "https://models.example/fixture-vlm",
        upstreamRevision = "upstream-r1",
        capability = spec.capability,
        backendId = spec.backendId,
        backendVersion = VersionRange("0.18.1", "0.19.0"),
        minimumOS = "26",
        architectures = listOf("arm64-v8a", "x86_64"),
        formatVersion = spec.formatVersion,
        files = files,
        processing = ModelProcessing(spec.inputSignature, "text-v1", "preprocess-v1", "tokenizer-v1", "prompt-v1", 1),
        resources = ModelResources(files.size.toLong(), files.size.toLong(), files.size.toLong()),
        licenseIdentifier = "Apache-2.0",
        licenseURL = "https://www.apache.org/licenses/LICENSE-2.0",
        attribution = "fixture",
        redistribution = "permitted",
    )
}
