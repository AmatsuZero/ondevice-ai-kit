package io.ondeviceai.sherpaasr

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One native VAD per request; the owner releases it before another request starts. */
internal class WhisperSpeechGate(model: SherpaWhisperModel) : AutoCloseable {
    private val vad = Vad(config = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = model.directory.resolve("silero_vad.onnx").absolutePath,
            threshold = .5f, windowSize = 512),
        sampleRate = 16_000, numThreads = 1, provider = "cpu", debug = false))
    val buffer = UtteranceBuffer { vad.compute(it) >= .5f }

    suspend fun speechRange(samples: FloatArray): IntRange? {
        var voicedSamples = 0
        var firstVoicedFrame = -1
        var lastVoicedFrame = -1
        var offset = 0
        while (offset + 512 <= samples.size) {
            currentCoroutineContext().ensureActive()
            val frame = samples.copyOfRange(offset, offset + 512)
            val voiced = try { vad.compute(frame) >= .5f } finally { frame.fill(0f) }
            currentCoroutineContext().ensureActive()
            if (voiced) {
                if (firstVoicedFrame < 0) firstVoicedFrame = offset
                lastVoicedFrame = offset
                voicedSamples += 512
            }
            offset += 512
        }
        if (voicedSamples < 4096) return null

        val start = (firstVoicedFrame - 3200).coerceAtLeast(0)
        val end = (lastVoicedFrame + 512 + 3200).coerceAtMost(samples.size)
        return start until end
    }

    override fun close() {
        try { vad.release() } finally { buffer.clear() }
    }

    companion object {
        suspend fun prepare(model: SherpaWhisperModel, samples: FloatArray): FloatArray {
            require(samples.isNotEmpty() && samples.size <= 160_000 && samples.all { it.isFinite() })
            return WhisperSpeechGate(model).use { gate ->
                var offset = 0
                while (offset < samples.size) {
                    currentCoroutineContext().ensureActive()
                    val end = minOf(offset + 1600, samples.size)
                    val chunk = samples.copyOfRange(offset, end)
                    val finished = try { gate.buffer.append(chunk) } finally { chunk.fill(0f) }
                    if (finished) break
                    offset = end
                }
                gate.buffer.samples()
            }
        }
    }
}
