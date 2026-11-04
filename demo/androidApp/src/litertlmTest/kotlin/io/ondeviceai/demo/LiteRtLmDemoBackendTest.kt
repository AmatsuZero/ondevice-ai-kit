package io.ondeviceai.demo

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.litertlm.LiteRtLmBackendPolicy
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.modelstore.*
import io.ondeviceai.multimodal.*
import io.ondeviceai.core.CapabilityAvailability
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class LiteRtLmDemoBackendTest {
    @Test
    fun descriptorRequiresExplicitKnownExecutionPolicy() {
        assertEquals(LiteRtLmBackendPolicy.CPU_ONLY,
            liteRtLmBackendPolicy(org.json.JSONObject("{}")))
        assertEquals(LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION,
            liteRtLmBackendPolicy(org.json.JSONObject("""{"backendPolicy":"CPU_TEXT_GPU_VISION"}""")))
        for (value in listOf("\"AUTO\"", "null", "true", "12")) {
            assertFailsWith<IllegalArgumentException> {
                liteRtLmBackendPolicy(org.json.JSONObject("{\"backendPolicy\":$value}"))
            }
        }
    }

    @Test
    fun authorizedLocalBundleInstallsAndRoutesImageGeneration() = authorizedBundle(null)

    @Test
    fun explicitMixedBundleReachesBackendConstruction() = authorizedBundle(LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION)

    @Test
    fun unknownPolicyImportRejectsBeforeInstallingOrConstructing() = authorizedBundle(null, invalidPolicy = true)

    private fun authorizedBundle(policy: LiteRtLmBackendPolicy?, invalidPolicy: Boolean = false) = runBlocking {
        val root = Files.createTempDirectory("litertlm-demo-bundle").toFile()
        try {
            val imported = File(root, "import").apply { mkdirs() }
            val source = File(imported, "files").apply { mkdirs() }
            val files = listOf("model.litertlm", "model-card.txt").map { path ->
                val bytes = "synthetic host fixture $path".toByteArray()
                File(source, path).writeBytes(bytes)
                ModelFile(path, "https://example.org/$path", bytes.size.toLong(),
                    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            }
            val total = files.sumOf { it.bytes }
            val manifest = ModelManifest(modelId = "host-fixture", revision = "r1",
                upstreamURL = "https://example.org/model", upstreamRevision = "fixture-revision",
                capability = "multimodal.image", backendId = "litertlm.android",
                backendVersion = VersionRange("0.18.1", "0.19.0"), minimumOS = "26",
                architectures = listOf("arm64-v8a"), formatVersion = "litertlm-model-v1", files = files,
                processing = ModelProcessing("image-file+text-prompt-v1", "text-v1", "preprocess-v1", "tokenizer-v1", "prompt-v1", 1),
                resources = ModelResources(total, total, total), licenseIdentifier = "Apache-2.0",
                licenseURL = "https://example.org/license", attribution = "host fixture", redistribution = "permitted")
            File(imported, "manifest.json").writeText(manifest.toJson())
            File(imported, "demo-model.json").writeText(
                """{"schemaVersion":1,"modelPath":"model.litertlm","metadataPaths":["model-card.txt"]}""".let {
                    if (invalidPolicy) org.json.JSONObject(it).put("backendPolicy", "AUTO").toString()
                    else if (policy == null) it else org.json.JSONObject(it).put("backendPolicy", policy.name).toString()
                })
            val incoming = File(root, "selected-bundle")
            check(imported.renameTo(incoming))
            val store = ModelStore(File(root, "store"), ModelCompatibility("litertlm.android", "0.18.1", "35", "arm64-v8a"))
            var constructions = 0
            var closed = false
            val backend = LiteRtLmDemoBackend(imported, store, File(root, "cache"),
                LiteRtLmRuntimeEnvironment(35, setOf("arm64-v8a")), create = { _, model, _, _ ->
                    constructions++
                    assertEquals(manifest.ref, model.ref)
                    assertEquals("model.litertlm", model.modelPathRelative)
                    assertEquals(policy ?: LiteRtLmBackendPolicy.CPU_ONLY, model.backendPolicy)
                    object : MultimodalGenerationBackend {
                        override val backendId = "litertlm.android"
                        override suspend fun availability() = CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
                        override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
                            assertEquals("describe", request.prompt)
                            assertEquals(37, request.maxOutputTokens)
                            return MultimodalTextOutput("host fake output", MultimodalModelIdentity("host-fixture", "fixture-revision", "litertlm-android:0.18.1"))
                        }
                        override suspend fun drain() = Unit
                        override suspend fun close() { closed = true }
                    }
                })
            assertEquals(UnavailableReason.MISSING_ASSETS, backend.availability().reason)
            if (invalidPolicy) {
                assertFailsWith<IllegalArgumentException> { backend.replaceImportedBundle(incoming) }
                assertEquals(0, constructions)
                assertEquals(ModelStatus.ABSENT, store.status(manifest.ref))
                assertFalse(imported.exists())
                backend.close()
                return@runBlocking
            }
            backend.replaceImportedBundle(incoming)
            assertEquals(CapabilityStatus.AVAILABLE, backend.availability().status)
            assertEquals(ModelStatus.INSTALLED, store.status(manifest.ref))
            assertEquals("host fake output", backend.generate(MultimodalRequest(File(root, "image"), "describe", 37), null).text)
            backend.close()
            assertEquals(1, constructions)
            assertEquals(true, closed)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun missingModelReportsAssetsAndCloseDoesNotOpenRuntime() = runBlocking {
        val root = Files.createTempDirectory("litertlm-demo-missing").toFile()
        var opened = false
        try {
            val backend = LiteRtLmDemoBackend(File(root, "import"),
                ModelStore(File(root, "store"), ModelCompatibility("litertlm.android", "0.18.1", "35", "arm64-v8a")),
                File(root, "cache"), LiteRtLmRuntimeEnvironment(35, setOf("arm64-v8a")),
                create = { _, _, _, _ -> opened = true; error("missing model must not open") })
            assertEquals(CapabilityStatus.REQUIRES_ASSETS, backend.availability().status)
            assertEquals(UnavailableReason.MISSING_ASSETS, backend.availability().reason)
            backend.close()
            assertEquals(UnavailableReason.CLOSED, backend.availability().reason)
            assertFalse(opened)
        } finally { root.deleteRecursively() }
    }
}
