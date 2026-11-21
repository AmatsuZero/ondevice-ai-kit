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
