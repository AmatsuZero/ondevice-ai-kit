package io.ondeviceai.binding
import kotlin.test.*
class BridgeProbeTest {
    @Test fun injectedBackendIsUsed() {
        val bridge = object : FaceBridge { override fun backendId() = "apple.vision" }
        assertEquals("apple.vision", probeBackend(bridge))
    }
}
