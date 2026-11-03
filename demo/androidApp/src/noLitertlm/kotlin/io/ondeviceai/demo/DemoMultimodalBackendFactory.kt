package io.ondeviceai.demo

import android.content.Context
import io.ondeviceai.binding.MultimodalBridge
import io.ondeviceai.binding.UnavailableMultimodalBridge
import kotlinx.coroutines.CoroutineScope

internal object DemoMultimodalBackendFactory {
    const val enabled = false
    const val imageOnly = false
    fun create(context: Context, inputs: MultimodalInputStore, scope: CoroutineScope): MultimodalBridge =
        UnavailableMultimodalBridge()
}
