package io.ondeviceai.demo

import io.ondeviceai.binding.MultimodalBridge

internal data class DemoMultimodalRuntime(
    val bridge: MultimodalBridge,
    val modelSetup: MultimodalModelSetupDemoActions? = null,
)
