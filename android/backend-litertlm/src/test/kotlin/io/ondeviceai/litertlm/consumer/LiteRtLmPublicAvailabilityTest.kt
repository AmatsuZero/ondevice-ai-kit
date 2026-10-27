package io.ondeviceai.litertlm.consumer

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.litertlm.LiteRtLm
import io.ondeviceai.litertlm.LiteRtLmModel
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class LiteRtLmPublicAvailabilityTest {
    @Test
    fun externalConsumerCanQueryMissingAssetsWithoutConstructingBackend() = runBlocking {
        val root = Files.createTempDirectory("litertlm-public-availability").toFile()
        try {
            val store = ModelStore(File(root, "store"), ModelCompatibility(
                "litertlm.android", "0.18.1", "35", "arm64-v8a"))
            val value = LiteRtLm.availability(
                store,
                LiteRtLmModel(ModelRef("missing", "r1"), "model.bin", setOf("tokenizer.json")),
                LiteRtLmRuntimeEnvironment(35, setOf("arm64-v8a")),
            )

            assertEquals(CapabilityStatus.REQUIRES_ASSETS, value.status)
            assertEquals(UnavailableReason.MISSING_ASSETS, value.reason)
            assertEquals("litertlm.android", value.backendId)
            assertEquals("26", value.minOS)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun externalConsumerGetsStableDevicePolicyBeforeAssetQuery() = runBlocking {
        val root = Files.createTempDirectory("litertlm-public-policy").toFile()
        try {
            val store = ModelStore(File(root, "store"), ModelCompatibility(
                "litertlm.android", "0.18.1", "35", "arm64-v8a"))
            val model = LiteRtLmModel(ModelRef("missing", "r1"), "model.bin", setOf("tokenizer.json"))

            assertEquals(UnavailableReason.REQUIRES_OS_VERSION,
                LiteRtLm.availability(store, model, LiteRtLmRuntimeEnvironment(25, setOf("arm64-v8a"))).reason)
            assertEquals(UnavailableReason.UNSUPPORTED_DEVICE,
                LiteRtLm.availability(store, model, LiteRtLmRuntimeEnvironment(35, setOf("armeabi-v7a"))).reason)
        } finally {
            root.deleteRecursively()
        }
    }
}
