package io.ondeviceai.demo

import androidx.activity.ComponentActivity
import io.ondeviceai.binding.UnavailableMultimodalBridge
import kotlinx.coroutines.CoroutineScope

internal object DemoMultimodalBackendFactory {
    const val enabled = false
    const val imageOnly = false
    fun create(context: ComponentActivity, inputs: MultimodalInputStore, scope: CoroutineScope,
        canImport: () -> Boolean, onImported: () -> Unit): DemoMultimodalRuntime =
        DemoMultimodalRuntime(UnavailableMultimodalBridge())
}
