// demo/androidApp/src/sherpaasr/kotlin/io/ondeviceai/demo/SpeechFilePcm.kt
package io.ondeviceai.demo

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val OUTPUT_RATE = 16_000
private const val MAX_CHUNK_SAMPLES = 160_000
private const val CODEC_POLL_TIMEOUT_US = 10_000L
private const val CODEC_NO_PROGRESS_TIMEOUT_MS = 15_000L

private data class PcmFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int
) {
    val bytesPerSample: Int
        get() = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> throw IllegalArgumentException("Unsupported PCM encoding: $encoding")
        }
}

private fun pcmFormatFrom(format: MediaFormat): PcmFormat {
    if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ||
        !format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
    ) {
        throw IllegalArgumentException("Audio format is missing its sample rate or channel count")
    }

    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
        AudioFormat.ENCODING_PCM_16BIT
    }

    if (sampleRate <= 0 || channelCount <= 0) {
        throw IllegalArgumentException("Invalid audio sample rate or channel count")
    }
    if (encoding != AudioFormat.ENCODING_PCM_16BIT &&
        encoding != AudioFormat.ENCODING_PCM_FLOAT
    ) {
        throw IllegalArgumentException("Unsupported PCM encoding: $encoding")
    }

    return PcmFormat(sampleRate, channelCount, encoding)
}

private class Pcm16kStreamer(
    private val collector: FlowCollector<FloatArray>
) {
    private var chunk = FloatArray(MAX_CHUNK_SAMPLES)
    private var chunkSize = 0

    private var inputFrames = 0L
    private var previousSample: Float? = null
    private var previousTime = 0.0
    private var segmentStartTime = 0.0
    private var segmentFrameIndex = 0L
    private var segmentRate = 0
    private var nextOutputIndex = 0L

    suspend fun accept(monoSample: Float, sampleRate: Int) {
        val previous = previousSample
        if (previous == null) {
            segmentRate = sampleRate
            segmentStartTime = 0.0
            segmentFrameIndex = 0L
            previousTime = 0.0
            previousSample = monoSample
            inputFrames++
            append(monoSample)
            nextOutputIndex++
            return
        }

        if (sampleRate != segmentRate) {
            segmentRate = sampleRate
            segmentStartTime = previousTime + 1.0 / sampleRate
            segmentFrameIndex = 0L
        } else {
            segmentFrameIndex++
        }

        val currentTime = segmentStartTime + segmentFrameIndex.toDouble() / segmentRate
        while (nextOutputIndex.toDouble() / OUTPUT_RATE <= currentTime) {
            val targetTime = nextOutputIndex.toDouble() / OUTPUT_RATE
            if (targetTime >= previousTime) {
                val fraction = ((targetTime - previousTime) / (currentTime - previousTime))
                    .coerceIn(0.0, 1.0)
                append((previous + (monoSample - previous) * fraction.toFloat()))
                nextOutputIndex++
            } else {
                nextOutputIndex++
            }
        }

        previousSample = monoSample
        previousTime = currentTime
        inputFrames++
    }

    suspend fun finish() {
        if (inputFrames == 0L) {
            throw IllegalArgumentException("Audio contains no PCM samples")
        }
        if (chunkSize > 0) {
            val remainder = chunk.copyOf(chunkSize)
            chunk.fill(0f)
            chunkSize = 0
            try {
                collector.emit(remainder)
            } catch (error: Throwable) {
                remainder.fill(0f)
                throw error
            }
        }
    }

    fun clear() {
        chunk.fill(0f)
        chunkSize = 0
    }

    private suspend fun append(sample: Float) {
        chunk[chunkSize++] = sample
        if (chunkSize == chunk.size) {
            val completed = chunk
            chunk = FloatArray(MAX_CHUNK_SAMPLES)
            chunkSize = 0
            try {
                collector.emit(completed)
            } catch (error: Throwable) {
                completed.fill(0f)
                throw error
            }
        }
    }
}

private suspend fun consumePcm(
    buffer: ByteBuffer,
    byteCount: Int,
    format: PcmFormat,
    streamer: Pcm16kStreamer
) {
    val bytesPerFrame = format.bytesPerSample * format.channelCount
    if (byteCount < 0 || byteCount > buffer.remaining() || byteCount % bytesPerFrame != 0) {
        throw IllegalArgumentException("Audio contains an incomplete PCM frame")
    }

    buffer.order(ByteOrder.nativeOrder())
    var frame = 0
    val frameCount = byteCount / bytesPerFrame
    while (frame < frameCount) {
        if (frame and 0x3ff == 0) {
            currentCoroutineContext().ensureActive()
        }

        var sum = 0f
        repeat(format.channelCount) {
            sum += when (format.encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> buffer.short / 32768f
                AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
                else -> throw IllegalArgumentException("Unsupported PCM encoding")
            }
        }
        streamer.accept(sum / format.channelCount, format.sampleRate)
        frame++
    }
}

private suspend fun consumeRawTrack(
    extractor: MediaExtractor,
    format: PcmFormat,
    streamer: Pcm16kStreamer
) {
    val buffer = ByteBuffer.allocateDirect(1024 * 1024).order(ByteOrder.nativeOrder())

    while (true) {
        currentCoroutineContext().ensureActive()
        if (extractor.sampleTrackIndex < 0) break

        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        if (size > buffer.capacity()) {
            throw IllegalArgumentException("Raw audio sample exceeds the supported buffer size")
        }

        buffer.position(0)
        buffer.limit(size)
        consumePcm(buffer, size, format, streamer)
        extractor.advance()
    }
}

private suspend fun decodeCompressedTrack(
    extractor: MediaExtractor,
    trackFormat: MediaFormat,
    mime: String,
    streamer: Pcm16kStreamer
) {
    val codec = try {
        MediaCodec.createDecoderByType(mime)
    } catch (error: IOException) {
        throw IllegalArgumentException("No decoder is available for $mime", error)
    }

    var started = false
    try {
        codec.configure(trackFormat, null, null, 0)
        codec.start()
        started = true

        val info = MediaCodec.BufferInfo()
        var inputEosQueued = false
        var lastQueuedSampleTime = 0L
        var sampleTimeOffset: Long? = null
        var outputEosSeen = false
        var outputPcmFormat: PcmFormat? = null
        var lastProgressAt = SystemClock.elapsedRealtime()

        while (!outputEosSeen) {
            currentCoroutineContext().ensureActive()
            var madeProgress = false

            if (!inputEosQueued) {
                val inputIndex = codec.dequeueInputBuffer(CODEC_POLL_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                        ?: throw IllegalArgumentException("Decoder returned no input buffer")
                    inputBuffer.clear()

                    val sampleTrackIndex = extractor.sampleTrackIndex
                    if (sampleTrackIndex < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            lastQueuedSampleTime,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEosQueued = true
                    } else {
                        val sampleTime = extractor.sampleTime
                        val sampleFlags = extractor.sampleFlags
                        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                            throw IllegalArgumentException("Encrypted audio samples are not supported")
                        }

                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                lastQueuedSampleTime,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEosQueued = true
                        } else {
                            val timeOffset = sampleTimeOffset
                                ?: if (sampleTime < 0L) -sampleTime else 0L
                            sampleTimeOffset = timeOffset
                            val codecSampleTime = sampleTime + timeOffset

                            var codecFlags = 0
                            if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                            }
                            if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                            }
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                codecSampleTime,
                                codecFlags
                            )
                            lastQueuedSampleTime = codecSampleTime
                            extractor.advance()
                        }
                    }
                    madeProgress = true
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, CODEC_POLL_TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outputIsEos =
                        info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val isCodecConfig =
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

                    try {
                        if (info.size > 0 && !isCodecConfig) {
                            val pcmFormat = outputPcmFormat
                                ?: throw IllegalArgumentException(
                                    "Decoder produced audio before reporting its output format"
                                )
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                                ?: throw IllegalArgumentException("Decoder returned no output buffer")
                            if (info.offset < 0 ||
                                info.size < 0 ||
                                info.offset > outputBuffer.limit() - info.size
                            ) {
                                throw IllegalArgumentException("Decoder returned an invalid output range")
                            }

                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            consumePcm(outputBuffer, info.size, pcmFormat, streamer)
                            madeProgress = true
                        }
                    } finally {
                        codec.releaseOutputBuffer(outputIndex, false)
                    }

                    if (outputIsEos) {
                        outputEosSeen = true
                        madeProgress = true
                    }
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputPcmFormat = pcmFormatFrom(codec.outputFormat)
                    madeProgress = true
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER ||
                    outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
            }

            if (madeProgress) {
                lastProgressAt = SystemClock.elapsedRealtime()
            } else if (SystemClock.elapsedRealtime() - lastProgressAt >
                CODEC_NO_PROGRESS_TIMEOUT_MS
            ) {
                throw IllegalArgumentException("Audio decoder made no progress")
            }
        }
    } finally {
        try {
            if (started) codec.stop()
        } finally {
            codec.release()
        }
    }
}

internal fun speechFilePcm(context: Context, uri: Uri): Flow<FloatArray> = flow {
    require(uri.scheme == "content") { "Audio URI must use the content scheme" }

    val extractor = MediaExtractor()
    var streamer: Pcm16kStreamer? = null

    try {
        try {
            extractor.setDataSource(context, uri, null)
        } catch (error: IOException) {
            throw IllegalArgumentException("Unable to read audio URI", error)
        } catch (error: SecurityException) {
            throw IllegalArgumentException("Unable to access audio URI", error)
        }

        var audioTrackIndex = -1
        var audioTrackFormat: MediaFormat? = null
        var audioMime: String? = null

        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = index
                audioTrackFormat = candidate
                audioMime = mime
                break
            }
        }

        if (audioTrackIndex < 0 || audioTrackFormat == null || audioMime == null) {
            throw IllegalArgumentException("No supported audio track was found")
        }

        extractor.selectTrack(audioTrackIndex)
        val output = Pcm16kStreamer(this)
        streamer = output

        if (audioMime == "audio/raw") {
            consumeRawTrack(extractor, pcmFormatFrom(audioTrackFormat), output)
        } else {
            decodeCompressedTrack(
                extractor,
                audioTrackFormat,
                audioMime,
                output
            )
        }

        output.finish()
    } finally {
        try {
            streamer?.clear()
        } finally {
            extractor.release()
        }
    }
}
