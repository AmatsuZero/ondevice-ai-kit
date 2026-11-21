package io.ondeviceai.demo

import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope

internal object DemoIdentityBackendFactory {
    fun create(activity: ComponentActivity, scope: CoroutineScope, configure: ConfigureIdentityPrototype,
        reset: () -> Unit): DemoIdentityRuntime? = null
}
