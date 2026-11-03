package io.ondeviceai.demo

import android.content.Context
import android.os.Build
import io.ondeviceai.binding.AndroidMultimodalBridge
import io.ondeviceai.binding.MultimodalBridge
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.multimodal.MultimodalGenerationClient
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal object DemoMultimodalBackendFactory {
    const val enabled = true
    const val imageOnly = true

    fun create(context: Context, inputs: MultimodalInputStore, scope: CoroutineScope): MultimodalBridge {
        val environment = LiteRtLmRuntimeEnvironment.current()
        val architecture = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" } ?: "unsupported"
        val store = ModelStore(File(context.noBackupFilesDir, "litertlm/store"),
            ModelCompatibility("litertlm.android", "0.18.1", Build.VERSION.SDK_INT.toString(), architecture))
        val backend = LiteRtLmDemoBackend(File(context.filesDir, "litertlm/import"), store,
            context.cacheDir, environment)
        return AndroidMultimodalBridge(MultimodalGenerationClient(backend.backendId, listOf(backend)), inputs, scope)
    }
}
