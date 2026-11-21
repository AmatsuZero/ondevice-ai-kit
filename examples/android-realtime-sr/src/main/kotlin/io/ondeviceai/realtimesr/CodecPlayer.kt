package io.ondeviceai.realtimesr

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CodecPlayer(context: Context, private val listener: Listener) {
    interface Listener {
        fun format(width: Int, height: Int, frameRate: Int, colorStandard: Int, colorRange: Int, rotation: Int, colorTransfer: Int)
        fun beforeFrame(ptsUs: Long, targetNs: Long): Boolean
        fun tick(mediaUs: Long)
        fun ended()
        fun error(message: String)
    }

    @Volatile
    var textureOutput: Boolean = false

    private val appContext = context.applicationContext
    private val thread = HandlerThread("CodecPlayer").apply { start() }
    private val handler = Handler(thread.looper)
    private val gate = Any()
    @Volatile private var closed = false

    private var videoExtractor: MediaExtractor? = null
    private var audioExtractor: MediaExtractor? = null
    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var outputSurface: Surface? = null

    private var clock = PlaybackClock(48_000)
    private var playing = false
    private var paused = true
    private var opened = false
    private var endedNotified = false
    private var videoInputEos = false
    private var videoOutputEos = false
    private var audioInputEos = false
    private var audioOutputEos = false
    private var pausedFrameRendered = false
    private var seekUs = 0L
    private var pauseUs = 0L
    private var audioAnchorUs: Long? = null
    private var freshAnchor = true

    private var heldVideoIndex = -1
    private val heldVideoInfo = MediaCodec.BufferInfo()
    private var pendingPcm: ByteArray? = null
    private var pendingOffset = 0
    private var pendingPtsUs = 0L
    private var audioRate = 0
    private var audioChannels = 0
    private var bytesPerFrame = 0
    private var writtenBytes = 0L
    private var playedFrames = 0L
    private var previousHead = 0
    private var pumpScheduled = false

    fun open(uri: Uri, output: Surface) = post {
        releaseCurrent()
        outputSurface = output
        try {
            val ve = MediaExtractor().also { videoExtractor = it }
            val ae = MediaExtractor().also { audioExtractor = it }
            ve.setDataSource(appContext, uri, null)
            ae.setDataSource(appContext, uri, null)

            val videoTrack = findTrack(ve, "video/avc")
                ?: error("No H.264 video track")
            val audioTrackIndex = findTrack(ae, "audio/mp4a-latm")
                ?: error("No AAC audio track")
            val vf = ve.getTrackFormat(videoTrack)
            val af = ae.getTrackFormat(audioTrackIndex)
            ve.selectTrack(videoTrack)
            ae.selectTrack(audioTrackIndex)

            ve.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            ae.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            videoCodec = MediaCodec.createDecoderByType("video/avc").also {
                it.configure(vf, output, null, 0)
                it.start()
            }
            audioCodec = MediaCodec.createDecoderByType("audio/mp4a-latm").also {
                it.configure(af, null, null, 0)
                it.start()
            }

            opened = true
            playing = false
            paused = true
            endedNotified = false
            seekUs = 0L
            pauseUs = 0L
            freshAnchor = true
            clock.reset()

            listener.format(
                intOr(vf, MediaFormat.KEY_WIDTH, 0),
                intOr(vf, MediaFormat.KEY_HEIGHT, 0),
                intOr(vf, MediaFormat.KEY_FRAME_RATE, 0),
                intOr(vf, MediaFormat.KEY_COLOR_STANDARD, 0),
                intOr(vf, MediaFormat.KEY_COLOR_RANGE, 0),
                intOr(vf, MediaFormat.KEY_ROTATION, 0),
                intOr(vf, MediaFormat.KEY_COLOR_TRANSFER, 0)
            )
            schedulePump()
        } catch (e: Exception) {
            releaseCurrent()
            listener.error(e.message ?: "Unable to open media")
        }
    }

    fun play() = post {
        if (!opened || playing) return@post
        if (!freshAnchor) {
            val track = audioTrack
            if (track != null) {
                clock.reset()
                clock.observe(track.playbackHeadPosition, pauseUs, System.nanoTime())
                audioAnchorUs = pauseUs
            }
        }
        playing = true
        paused = false
        pausedFrameRendered = false
        try {
            audioTrack?.play()
        } catch (e: IllegalStateException) {
            fail(e)
            return@post
        }
        schedulePump()
    }

    fun pause() = post {
        if (!opened || !playing) return@post
        pauseUs = clock.positionUs() ?: seekUs
        clock.pause()
        playing = false
        paused = true
        try {
            audioTrack?.pause()
        } catch (e: IllegalStateException) {
            fail(e)
        }
    }

    fun seekTo(ptsUs: Long) = post {
        if (!opened) return@post
        try {
            seekUs = ptsUs.coerceAtLeast(0L)
            pauseUs = seekUs
            clock.reset()
            audioAnchorUs = null
            freshAnchor = true
            endedNotified = false
            videoInputEos = false
            videoOutputEos = false
            audioInputEos = false
            audioOutputEos = false
            pausedFrameRendered = false
            pendingPcm = null
            pendingOffset = 0
            heldVideoIndex = -1

            videoExtractor?.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            audioExtractor?.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            videoCodec?.flush()
            audioCodec?.flush()

            audioTrack?.let {
                it.pause()
                it.flush()
                previousHead = it.playbackHeadPosition
                writtenBytes = 0L
                playedFrames = 0L
                if (playing) it.play()
            }
            schedulePump()
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun close() {
        val done = CountDownLatch(1)
        val onWorker = Thread.currentThread() === thread
        synchronized(gate) {
            if (closed) return
            closed = true
            if (onWorker) {
                releaseCurrent()
                thread.quitSafely()
                return
            }
            handler.post {
                releaseCurrent()
                thread.quitSafely()
                done.countDown()
            }
        }
        done.await()
    }

    private fun post(action: () -> Unit) {
        synchronized(gate) {
            if (!closed) handler.post(action)
        }
    }

    private fun findTrack(extractor: MediaExtractor, mime: String): Int? {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) == mime) return i
        }
        return null
    }

    private fun schedulePump() {
        if (!opened || pumpScheduled) return
        pumpScheduled = true
        handler.postDelayed({
            pumpScheduled = false
            pump()
        }, 5L)
    }

    private fun pump() {
        if (!opened) return
        try {
            if (playing) pumpAudio()
            pumpVideo()
            if (playing) {
                clock.positionUs()?.let { listener.tick(it) }
                updatePlayedHead()
                checkEnded()
            }
            schedulePump()
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun pumpAudio() {
        val codec = audioCodec ?: return
        val extractor = audioExtractor ?: return
        var track = audioTrack
        if (track != null) {
            writePending(track)
            if (pendingPcm != null) return
        }

        repeat(4) {
            if (!audioInputEos) queueInput(codec, extractor, false)
            val info = MediaCodec.BufferInfo()
            val index = codec.dequeueOutputBuffer(info, 0L)
            when {
                index >= 0 -> {
                    if (track == null) {
                        configureAudioTrack(codec.getOutputFormat(index))
                        track = audioTrack
                    }
                    val buffer = codec.getOutputBuffer(index)
                        ?: throw IllegalStateException("Missing AAC output buffer")
                    if (info.size > 0) {
                        val data = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(data)
                        val trim = trimPcm(data, info.presentationTimeUs)
                        if (trim != null) {
                            pendingPcm = trim.first
                            pendingOffset = 0
                            pendingPtsUs = trim.second
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        audioOutputEos = true
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (pendingPcm != null) {
                        val outputTrack = track
                            ?: throw IllegalStateException("Missing audio output track")
                        writePending(outputTrack)
                        if (pendingPcm != null) return
                    }
                    if (audioOutputEos) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    configureAudioTrack(codec.outputFormat)
                    track = audioTrack
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
            }
        }
    }

    private fun trimPcm(data: ByteArray, ptsUs: Long): Pair<ByteArray, Long>? {
        if (bytesPerFrame == 0) return null
        val frames = data.size / bytesPerFrame
        if (frames == 0) return null
        val trimFrames = if (ptsUs < seekUs) {
            (((seekUs - ptsUs) * audioRate + 999_999L) / 1_000_000L).coerceAtMost(frames.toLong()).toInt()
        } else 0
        if (trimFrames >= frames) return null
        val offset = trimFrames * bytesPerFrame
        val result = data.copyOfRange(offset, frames * bytesPerFrame)
        val adjustedPts = ptsUs + trimFrames * 1_000_000L / audioRate
        return result to adjustedPts
    }

    private fun writePending(track: AudioTrack) {
        val data = pendingPcm ?: return
        if (pendingOffset == 0 && audioAnchorUs == null) {
            audioAnchorUs = pendingPtsUs
            clock.observe(track.playbackHeadPosition, pendingPtsUs, System.nanoTime())
            freshAnchor = false
        }
        val count = track.write(data, pendingOffset, data.size - pendingOffset, AudioTrack.WRITE_NON_BLOCKING)
        if (count < 0) throw IllegalStateException("AudioTrack write failed: $count")
        if (count > 0) {
            pendingOffset += count
            writtenBytes += count
            if (pendingOffset >= data.size) {
                pendingPcm = null
                pendingOffset = 0
            }
        }
    }

    private fun configureAudioTrack(format: MediaFormat) {
        val rate = intOr(format, MediaFormat.KEY_SAMPLE_RATE, 0)
        val channels = intOr(format, MediaFormat.KEY_CHANNEL_COUNT, 0)
        val encoding = intOr(format, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        if (rate <= 0 || channels <= 0 || encoding != AudioFormat.ENCODING_PCM_16BIT) {
            throw IllegalStateException("Unsupported AAC decoder PCM format")
        }
        val mask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> error("Unsupported audio channel count: $channels")
        }
        val minBytes = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) throw IllegalStateException("Unsupported audio output format")
        audioTrack?.release()
        audioRate = rate
        audioChannels = channels
        clock = PlaybackClock(rate)
        bytesPerFrame = channels * 2
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(mask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes * 2)
            .build()
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            throw IllegalStateException("Unable to initialize AudioTrack")
        }
        previousHead = audioTrack!!.playbackHeadPosition
        writtenBytes = 0L
        playedFrames = 0L
        if (playing) audioTrack?.play()
    }

    private fun pumpVideo() {
        if (paused && pausedFrameRendered) return
        val codec = videoCodec ?: return
        val extractor = videoExtractor ?: return

        if (heldVideoIndex < 0) {
            if (videoOutputEos) return
            for (attempt in 0 until 4) {
                if (!videoInputEos) queueInput(codec, extractor, true)
                val info = MediaCodec.BufferInfo()
                val index = codec.dequeueOutputBuffer(info, 0L)
                when {
                    index >= 0 -> {
                        val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size <= 0 || info.presentationTimeUs < seekUs) {
                            codec.releaseOutputBuffer(index, false)
                            if (isEos) {
                                videoOutputEos = true
                                return
                            }
                        } else {
                            heldVideoIndex = index
                            heldVideoInfo.set(info.offset, info.size, info.presentationTimeUs, info.flags)
                            break
                        }
                    }
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> return
                }
            }
        }

        if (heldVideoIndex < 0) return
        val pts = heldVideoInfo.presentationTimeUs
        val target = if (playing) clock.targetNs(pts) ?: return else System.nanoTime()
        val rate = 33_333_333L
        if (target - System.nanoTime() > rate) return
        if (!listener.beforeFrame(pts, target)) return

        val texture = textureOutput
        val isEos = heldVideoInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        if (texture) {
            codec.releaseOutputBuffer(heldVideoIndex, pts * 1_000L)
        } else {
            codec.releaseOutputBuffer(heldVideoIndex, target)
        }
        heldVideoIndex = -1
        if (isEos) videoOutputEos = true
        if (paused) pausedFrameRendered = true
    }

    private fun queueInput(codec: MediaCodec, extractor: MediaExtractor, video: Boolean) {
        val input = codec.dequeueInputBuffer(0L)
        if (input < 0) return
        val buffer = codec.getInputBuffer(input) ?: throw IllegalStateException("Missing decoder input buffer")
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(input, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            if (video) videoInputEos = true else audioInputEos = true
        } else {
            val pts = extractor.sampleTime
            val flags = if (video && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else 0
            codec.queueInputBuffer(input, 0, size, pts, flags)
            extractor.advance()
        }
    }

    private fun updatePlayedHead() {
        val track = audioTrack ?: return
        val head = track.playbackHeadPosition
        playedFrames += Integer.toUnsignedLong(head - previousHead)
        previousHead = head
        if (audioAnchorUs != null) {
            clock.observe(head, audioAnchorUs!!, System.nanoTime())
        }
    }

    private fun checkEnded() {
        if (endedNotified || !audioOutputEos || !videoOutputEos || pendingPcm != null) return
        if (playedFrames < writtenBytes / bytesPerFrame.coerceAtLeast(1)) return
        endedNotified = true
        playing = false
        paused = true
        clock.pause()
        audioTrack?.pause()
        listener.ended()
    }

    private fun fail(error: Exception) {
        val message = error.message ?: "Playback failed"
        releaseCurrent()
        listener.error(message)
    }

    private fun releaseCurrent() {
        opened = false
        playing = false
        paused = true
        pumpScheduled = false
        heldVideoIndex = -1
        pendingPcm = null
        pendingOffset = 0
        clock.reset()

        try { audioTrack?.pause() } catch (_: RuntimeException) {}
        try { audioTrack?.flush() } catch (_: RuntimeException) {}
        try { audioTrack?.release() } catch (_: RuntimeException) {}
        audioTrack = null

        releaseCodec(videoCodec)
        releaseCodec(audioCodec)
        videoCodec = null
        audioCodec = null
        try { videoExtractor?.release() } catch (_: RuntimeException) {}
        try { audioExtractor?.release() } catch (_: RuntimeException) {}
        videoExtractor = null
        audioExtractor = null
        outputSurface = null
    }

    private fun releaseCodec(codec: MediaCodec?) {
        if (codec == null) return
        try { codec.stop() } catch (_: RuntimeException) {}
        try { codec.release() } catch (_: RuntimeException) {}
    }

    private fun intOr(format: MediaFormat, key: String, fallback: Int): Int =
        if (format.containsKey(key)) format.getInteger(key) else fallback
}
