package io.ondeviceai.demo

/** Host-owned input selection; no file paths, URIs or leases enter the shared UI. */
interface MultimodalDemoActions {
    fun selectImageFile()
    fun clearImageFile()
    fun leaveMultimodalPage()
}

/** Optional video selection; existing image-only hosts remain source compatible. */
interface MultimodalVideoDemoActions : MultimodalDemoActions {
    /** True only when the host has a video store and picker wired to this screen. */
    val supportsVideoInput: Boolean
    fun selectVideoFile()
}
