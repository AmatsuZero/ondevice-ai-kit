package io.ondeviceai.binding
interface FaceBridge { fun backendId(): String }
fun probeBackend(bridge: FaceBridge): String = bridge.backendId()
