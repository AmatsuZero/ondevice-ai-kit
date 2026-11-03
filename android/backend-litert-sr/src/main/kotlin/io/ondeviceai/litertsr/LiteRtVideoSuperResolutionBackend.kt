package io.ondeviceai.litertsr

import io.ondeviceai.core.*
import io.ondeviceai.video.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Optional frame-wise learned ESRGAN x4 backend; no network or implicit asset download. */
class LiteRtVideoSuperResolutionBackend(private val modelFile: File, private val outputRoot: File) : VideoSuperResolutionBackend {
    override val backendId = "litert.esrgan.x4"
    override val supportedScaleFactors = listOf(4)
    override val supportedAudioPolicies = listOf(VideoAudioPolicy.PRESERVE, VideoAudioPolicy.DISCARD)
    private val mutex = Mutex()
    @Volatile private var closed = false
    override suspend fun availability(): CapabilityAvailability = withContext(Dispatchers.IO) {
        when {
            closed -> CapabilityAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.CLOSED, backendId, "26")
            !LiteRtSrModel.valid(modelFile) -> CapabilityAvailability(CapabilityStatus.REQUIRES_ASSETS, UnavailableReason.MISSING_ASSETS, backendId, "26")
            else -> CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId, minOS = "26")
        }
    }
    override suspend fun process(request: VideoProcessingRequest, progress: ((VideoProcessingProgress) -> Unit)?): VideoOutputLease = mutex.withLock {
        check(!closed) { "closed" }
        require(request.scaleFactor == 4)
        var undelivered: VideoOutputLease? = null
        try {
            val result = withContext(Dispatchers.Default) {
                check(LiteRtSrModel.valid(modelFile)) { "missing or invalid ESRGAN model" }
                check(outputRoot.isDirectory || outputRoot.mkdirs())
                val directory = outputRoot.resolve(UUID.randomUUID().toString())
                check(directory.mkdir())
                val output = directory.resolve("output.mp4")
                var delivered = false
                try {
                    val context = currentCoroutineContext()
                    val info = EsrganInterpreter(modelFile).use { model ->
                        AndroidSrMedia.process(request, output, checkpoint = { context.ensureActive() }, progress = progress) { pixels, width, height ->
                            EsrganTiles.upscale(pixels, width, height, { context.ensureActive() }, model::infer)
                        }
                    }
                    context.ensureActive()
                    val lease = object : VideoOutputLease {
                        override val file = output
                        override val info = info
                        override suspend fun release() { withContext(Dispatchers.IO) { file.delete(); directory.delete() }; Unit }
                    }
                    undelivered = lease
                    delivered = true
                    lease
                } finally { if (!delivered) { output.delete(); directory.delete() } }
            }
            undelivered = null
            result
        } finally {
            withContext(NonCancellable) { undelivered?.release() }
        }
    }
    override suspend fun drain() { mutex.withLock { } }
    override suspend fun close() { closed = true; mutex.withLock { } }
}
