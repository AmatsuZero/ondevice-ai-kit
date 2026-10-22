package io.ondeviceai.demo

/** Host-owned input selection; no file paths, URIs or leases enter the shared UI. */
interface MultimodalDemoActions {
    fun selectImageFile()
    fun clearImageFile()
    fun leaveMultimodalPage()
}
