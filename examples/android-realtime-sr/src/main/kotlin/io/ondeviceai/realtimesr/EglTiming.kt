package io.ondeviceai.realtimesr

object EglTiming {
    init {
        System.loadLibrary("srtiming")
    }

    // EGL_ANDROID_get_frame_timestamps (API 26+). Call from the render thread.
    external fun enable(): Boolean
    external fun nextId(): Long
    external fun displayNs(id: Long): Long

    // EXT_disjoint_timer_query. Call from the render thread owning the GL context.
    external fun startGpu(): Int
    external fun endGpu(id: Int)
    external fun elapsedGpuNs(id: Int): Long
    external fun deleteGpu(id: Int)
}
