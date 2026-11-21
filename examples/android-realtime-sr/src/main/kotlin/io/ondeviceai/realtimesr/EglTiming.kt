package io.ondeviceai.realtimesr

object EglTiming {
    private val nativeAvailable = runCatching { System.loadLibrary("srtiming"); true }.getOrDefault(false)

    // EGL_ANDROID_get_frame_timestamps (API 26+). Call from the render thread.
    private external fun enable(): Boolean
    private external fun nextId(): Long
    private external fun displayNs(id: Long): Long

    // EXT_disjoint_timer_query. Call from the render thread owning the GL context.
    private external fun startGpu(): Int
    private external fun endGpu(id: Int)
    private external fun elapsedGpuNs(id: Int): Long
    private external fun deleteGpu(id: Int)

    fun safeEnable(): Boolean = nativeAvailable && runCatching { enable() }.getOrDefault(false)
    fun safeNextId(): Long = if (nativeAvailable) runCatching { nextId() }.getOrDefault(-1L) else -1L
    fun safeDisplayNs(id: Long): Long = if (nativeAvailable) runCatching { displayNs(id) }.getOrDefault(-1L) else -1L
    fun safeStartGpu(): Int = if (nativeAvailable) runCatching { startGpu() }.getOrDefault(-1) else -1
    fun safeEndGpu(id: Int) { if (nativeAvailable) runCatching { endGpu(id) } }
    fun safeElapsedGpuNs(id: Int): Long = if (nativeAvailable) runCatching { elapsedGpuNs(id) }.getOrDefault(-1L) else -1L
    fun safeDeleteGpu(id: Int) { if (nativeAvailable) runCatching { deleteGpu(id) } }
}
