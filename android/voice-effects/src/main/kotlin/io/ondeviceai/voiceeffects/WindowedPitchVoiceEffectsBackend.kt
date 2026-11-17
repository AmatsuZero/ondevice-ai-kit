package io.ondeviceai.voiceeffects

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Original 80 ms dual-read-head windowed shifter. Not a neural voice conversion. */
class WindowedPitchVoiceEffectsBackend : VoiceEffectsBackend {
    override val backendId: String = "android.windowed-pitch"

    override suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset): FloatArray =
        withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            val semitones = when (preset) {
                VoiceEffectsPreset.ORIGINAL -> 0
                VoiceEffectsPreset.HIGH -> 4
                VoiceEffectsPreset.LOW -> -4
            }
            WindowedPitch.shift(input, sampleRate, semitones) { context.ensureActive() }
        }
}

/** Arithmetic matches the verified PitchCore.kt, including floor for negative phase. */
internal object WindowedPitch {
    fun shift(input: FloatArray, sampleRate: Int, semitones: Int, checkCancelled: () -> Unit = {}): FloatArray {
        require(sampleRate in intArrayOf(16000, 44100, 48000)) { "unsupported sample rate" }
        require(semitones in intArrayOf(0, 4, -4)) { "unsupported semitones" }
        require(input.isNotEmpty() && input.size <= sampleRate * 30) { "invalid input length" }
        for (i in input.indices) {
            if (i % 1024 == 0) checkCancelled()
            require(input[i].isFinite() && input[i] >= -1f && input[i] <= 1f) { "invalid input values" }
        }
        checkCancelled()
        if (semitones == 0) return input.copyOf()
        val window = sampleRate * 0.080
        val ratio = Math.pow(2.0, semitones / 12.0)
        val step = (1.0 - ratio) / window
        val output = FloatArray(input.size)
        for (n in input.indices) {
            if (n % 1024 == 0) checkCancelled()
            val pos = n * step
            val p = pos - Math.floor(pos)
            val q = (p + 0.5) - Math.floor(p + 0.5)
            val weightP = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * p)
            val weightQ = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * q)
            val posP = n + window * (0.5 - p)
            val posQ = n + window * (0.5 - q)
            output[n] = (weightP * read(posP, input) + weightQ * read(posQ, input)).toFloat()
        }
        checkCancelled()
        return output
    }

    private fun read(pos: Double, input: FloatArray): Double {
        val base = Math.floor(pos).toInt()
        val fraction = pos - base
        val a = input.getOrNull(base)?.toDouble() ?: 0.0
        val b = input.getOrNull(base + 1)?.toDouble() ?: 0.0
        return (1.0 - fraction) * a + fraction * b
    }
}
