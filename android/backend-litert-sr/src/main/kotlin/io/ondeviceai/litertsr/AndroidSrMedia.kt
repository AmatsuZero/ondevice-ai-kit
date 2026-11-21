package io.ondeviceai.litertsr

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import io.ondeviceai.video.*
import java.io.File
import java.nio.ByteBuffer

/** Bounded SDR AVC decode → learned RGB SR → AVC encode. Encoded PTS come from decoded frames. */
internal object AndroidSrMedia {
    fun process(request: VideoProcessingRequest, output: File, checkpoint: () -> Unit,
                progress: ((VideoProcessingProgress) -> Unit)?,
                upscale: (IntArray, Int, Int) -> IntArray): VideoOutputInfo {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var decoderStarted = false
        var encoderStarted = false
        var muxerStarted = false
        try {
            extractor.setDataSource(request.inputFile.absolutePath)
            val videoTracks = (0 until extractor.trackCount).filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            require(videoTracks.size == 1) { "one video track required" }
            val track = videoTracks.single()
            val format = extractor.getTrackFormat(track)
            require(format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC) { "H.264 input required" }
            val width = format.getInteger(MediaFormat.KEY_WIDTH); val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            require(width in 16..320 && height in 16..180 && width % 2 == 0 && height % 2 == 0) { "expected even dimensions up to 320×180" }
            require(!format.containsKey(MediaFormat.KEY_COLOR_TRANSFER) || format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_SDR_VIDEO) { "SDR transfer required" }
            require(!format.containsKey(MediaFormat.KEY_COLOR_RANGE) || format.getInteger(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_LIMITED) { "limited YUV range required" }
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            require(durationUs in 1..2_000_000) { "clips up to 2 seconds supported" }
            val fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE) else 24
            require(fps in 1..30)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            require(rotation in listOf(0, 90, 180, 270))
            val audioTracks = (0 until extractor.trackCount).filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            if (request.audioPolicy == VideoAudioPolicy.PRESERVE) {
                require(audioTracks.size <= 1) { "at most one audio track supported" }
                audioTracks.forEach { require(extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC) { "AAC audio required" } }
            }
            val savedAudio = if (request.audioPolicy == VideoAudioPolicy.PRESERVE) audioTracks else emptyList()
            extractor.selectTrack(track)
            // Never allow a device codec to apply rotation before the RGB inference stage.
            format.setInteger(MediaFormat.KEY_ROTATION, 0)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            decoder = codec(format, false)
            decoder.configure(format, null, null, 0); decoder.start(); decoderStarted = true
            val outWidth = width * 4; val outHeight = height * 4
            val encodeFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outWidth, outHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, maxOf(512_000, outWidth * outHeight * fps / 3))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
            encoder = codec(encodeFormat, true)
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start(); encoderStarted = true
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(rotation)
            val audioOutputTracks = savedAudio.map { muxer.addTrack(extractor.getTrackFormat(it)) }
            var outputTrack = -1
            var encoderEnded = false
            var lastEncodedPts = -1L
            var encodedFrames = 0
            val encodedInfo = MediaCodec.BufferInfo()
            var lastActivity = SystemClock.elapsedRealtime()
            fun checkAlive() { checkpoint(); check(SystemClock.elapsedRealtime() - lastActivity < 30_000) { "media codec stalled" } }
            fun drainEncoder() {
                while (true) {
                    checkAlive()
                    val index = encoder.dequeueOutputBuffer(encodedInfo, 0)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!muxerStarted)
                        outputTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start(); muxerStarted = true; lastActivity = SystemClock.elapsedRealtime()
                    } else if (index >= 0) {
                        try {
                            if (encodedInfo.size > 0 && encodedInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                check(muxerStarted && encodedInfo.presentationTimeUs > lastEncodedPts)
                                val data = checkNotNull(encoder.getOutputBuffer(index))
                                data.position(encodedInfo.offset); data.limit(encodedInfo.offset + encodedInfo.size)
                                muxer.writeSampleData(outputTrack, data, encodedInfo)
                                lastEncodedPts = encodedInfo.presentationTimeUs; encodedFrames++
                            }
                            if (encodedInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderEnded = true
                            lastActivity = SystemClock.elapsedRealtime()
                        } finally { encoder.releaseOutputBuffer(index, false) }
                    }
                }
            }
            fun encoderInput(): Int {
                while (true) {
                    checkAlive()
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) return index
                    drainEncoder()
                }
            }
            var inputEnded = false; var decoderEnded = false
            var processedFrames = 0; var lastPts = -1L; var firstPts = -1L; var frameInterval = 1_000_000L / fps
            val decodedInfo = MediaCodec.BufferInfo()
            while (!decoderEnded) {
                checkAlive()
                if (!inputEnded) {
                    val index = decoder.dequeueInputBuffer(0)
                    if (index >= 0) {
                        val buffer = checkNotNull(decoder.getInputBuffer(index))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance()
                        }
                        lastActivity = SystemClock.elapsedRealtime()
                    }
                }
                val index = decoder.dequeueOutputBuffer(decodedInfo, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val decodedFormat = decoder.outputFormat
                    require(decodedFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD) && decodedFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD) == MediaFormat.COLOR_STANDARD_BT709) { "BT.709 decoded color metadata required: $decodedFormat" }
                    require(!decodedFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER) || decodedFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_SDR_VIDEO) { "SDR decoded transfer required" }
                    require(!decodedFormat.containsKey(MediaFormat.KEY_COLOR_RANGE) || decodedFormat.getInteger(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_LIMITED) { "limited decoded range required" }
                }
                if (index >= 0) {
                    try {
                        if (decodedInfo.size > 0) {
                            require(processedFrames < 48) { "at most 48 frames supported" }
                            val pts = decodedInfo.presentationTimeUs
                            require(pts >= 0 && pts > lastPts) { "nonmonotonic decoded timestamps" }
                            if (firstPts < 0) firstPts = pts
                            if (lastPts >= 0) frameInterval = pts - lastPts
                            val pixels = checkNotNull(decoder.getOutputImage(index)).use { image -> readRgb(image, width, height) }
                            val outputPixels = upscale(pixels, width, height)
                            // Model work can exceed the media I/O timeout without implying a codec stall.
                            lastActivity = SystemClock.elapsedRealtime()
                            checkpoint()
                            val inputIndex = encoderInput()
                            checkNotNull(encoder.getInputImage(inputIndex)).use { image -> writeRgb(image, outputPixels, outWidth, outHeight) }
                            encoder.queueInputBuffer(inputIndex, 0, outWidth * outHeight * 3 / 2, pts, 0)
                            processedFrames++; lastPts = pts
                            progress?.invoke(VideoProcessingProgress(processedFrames))
                        }
                        decoderEnded = decodedInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        lastActivity = SystemClock.elapsedRealtime()
                    } finally { decoder.releaseOutputBuffer(index, false) }
                }
                drainEncoder()
            }
            require(processedFrames > 0)
            encoder.queueInputBuffer(encoderInput(), 0, 0, lastPts + frameInterval, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            while (!encoderEnded) { drainEncoder(); if (!encoderEnded) Thread.sleep(1) }
            check(encodedFrames == processedFrames) { "encoded frame count mismatch" }
            // Compressed audio samples retain their original timestamps and packet boundaries.
            savedAudio.forEachIndexed { audioIndex, sourceTrack ->
                checkpoint()
                val audio = MediaExtractor()
                try {
                    audio.setDataSource(request.inputFile.absolutePath); audio.selectTrack(sourceTrack)
                    val audioFormat = audio.getTrackFormat(sourceTrack)
                    val size = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024) else 256 * 1024
                    require(size <= 1024 * 1024)
                    val buffer = ByteBuffer.allocateDirect(size)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        checkpoint(); buffer.clear()
                        val count = audio.readSampleData(buffer, 0)
                        if (count < 0) break
                        require(audio.sampleTime >= 0 && audio.sampleTime <= durationUs) { "audio outside supported clip duration" }
                        info.set(0, count, audio.sampleTime, if (audio.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        muxer.writeSampleData(audioOutputTracks[audioIndex], buffer, info); audio.advance()
                    }
                } finally { audio.release() }
            }
            muxer.stop(); muxerStarted = false
            return VideoOutputInfo(outWidth, outHeight, processedFrames, (lastPts - firstPts + frameInterval) / 1_000_000.0, savedAudio.size)
        } finally {
            // Codec stop/release is synchronous; no native buffers survive process/drain return.
            try { if (decoderStarted) decoder?.stop() } finally {
                try { decoder?.release() } finally {
                    try { if (encoderStarted) encoder?.stop() } finally {
                        try { encoder?.release() } finally {
                            try { if (muxerStarted) muxer?.stop() } finally {
                                try { muxer?.release() } finally { extractor.release() }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun codec(format: MediaFormat, encoder: Boolean): MediaCodec {
        val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            info.isEncoder == encoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                info.getCapabilitiesForType(mime).isFormatSupported(format)
        }.sortedBy { if (it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")) 0 else 1 }
        return MediaCodec.createByCodecName(checkNotNull(candidates.firstOrNull()) { "No codec for supported SR format" }.name)
    }
    private fun readRgb(image: Image, width: Int, height: Int): IntArray {
        val crop = image.cropRect
        require(crop.width() == width && crop.height() == height && image.planes.size == 3)
        fun sample(plane: Int, x: Int, y: Int): Int {
            val p = image.planes[plane]
            return p.buffer.get(p.buffer.position() + y * p.rowStride + x * p.pixelStride).toInt() and 255
        }
        return IntArray(width * height) { index ->
            val x = crop.left + index % width; val y = crop.top + index / width
            VideoColor.rgb(sample(0, x, y), sample(1, x / 2, y / 2), sample(2, x / 2, y / 2))
        }
    }
    private fun writeRgb(image: Image, pixels: IntArray, width: Int, height: Int) {
        require(pixels.size == width * height && image.planes.size == 3)
        fun put(plane: Int, x: Int, y: Int, value: Int) {
            val p = image.planes[plane]
            p.buffer.put(p.buffer.position() + y * p.rowStride + x * p.pixelStride, value.toByte())
        }
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            var u = 0; var v = 0
            for (dy in 0..1) for (dx in 0..1) {
                val color = VideoColor.yuv(pixels[(y + dy) * width + x + dx])
                put(0, x + dx, y + dy, color[0]); u += color[1]; v += color[2]
            }
            put(1, x / 2, y / 2, (u + 2) / 4); put(2, x / 2, y / 2, (v + 2) / 4)
        }
    }
}
