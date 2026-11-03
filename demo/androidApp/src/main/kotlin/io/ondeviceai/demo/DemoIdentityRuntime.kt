package io.ondeviceai.demo

import android.graphics.Bitmap
import io.ondeviceai.binding.*

internal interface DemoIdentityRuntime : IdentityModelSetupDemoActions {
    fun stop()
    fun close()
}

internal typealias ConfigureIdentityPrototype = (IdentityBridge, IdentityDemoConfiguration, (Bitmap) -> IdentityProbeReference) -> Unit
