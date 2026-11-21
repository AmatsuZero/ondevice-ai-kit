package io.ondeviceai.demo
import java.io.File
import io.ondeviceai.video.VideoProcessingClient
import io.ondeviceai.litertsr.LiteRtVideoSuperResolutionBackend
import io.ondeviceai.litertsr.LiteRtSrModel
object DemoVideoBackendFactory {
    const val enabled = true
    fun create(model: File, outputs: File): VideoProcessingClient {
        val backend = LiteRtVideoSuperResolutionBackend(model, outputs)
        return VideoProcessingClient(backend.backendId, listOf(backend))
    }
    fun install(source: File, destination: File) = LiteRtSrModel.install(source, destination)
}
