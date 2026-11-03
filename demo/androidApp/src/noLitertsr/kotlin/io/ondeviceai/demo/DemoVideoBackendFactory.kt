package io.ondeviceai.demo
import java.io.File
import io.ondeviceai.video.VideoProcessingClient
object DemoVideoBackendFactory {
    const val enabled = false
    fun create(model: File, outputs: File): VideoProcessingClient? = null
    fun install(source: File, destination: File) { error("Video SR runtime is not linked") }
}
