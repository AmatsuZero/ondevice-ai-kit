package io.ondeviceai.sherpaasr

import io.ondeviceai.speech.SpeechNotDetectedException
import kotlin.math.abs

interface Detector {
    fun compute(window: FloatArray): Float
    fun release()
}

data class VadObservation(
    val inputCount: Int,
    val processedCount: Int,
    val classifierCalls: Int,
    val speechWindows: Int,
    val speechSamples: Int,
    val detected: Boolean,
    val preparedSamples: Int,
    val gain: Float,
    val probabilityMin: Float?,
    val probabilityMax: Float?,
    val probabilityMean: Float?
)

object VadGainMeasurement {
    fun measure(source: FloatArray, gain: Float, factory: () -> Detector): VadObservation {
        require(source.isNotEmpty() && source.size <= 160000) { "source size out of bounds" }
        for (s in source) require(s.isFinite()) { "non-finite source sample" }
        require(gain == 1f || gain == 10f) { "gain must be 1 or 10" }
        for (s in source) require(abs(s * gain) < 1f) { "gain-scaled sample saturates" }
        val snapshot = source.copyOf()
        val acc = FloatArray(1)
        var buffer: UtteranceBuffer? = null
        var detector: Detector? = null
        var prepared: FloatArray? = null
        var observation: VadObservation? = null
        try {
            val det = factory()
            detector = det
            var classifierCalls = 0
            var speechWindows = 0
            var probMin = Float.MAX_VALUE
            var probMax = -Float.MAX_VALUE
            var probSum = 0.0
            buffer = UtteranceBuffer { window ->
                val gainWindow = FloatArray(512) { i -> window[i] * gain }
                try {
                    val p = det.compute(gainWindow)
                    require(p.isFinite()) { "non-finite probability" }
                    require(p in 0f..1f) { "probability out of [0,1]: $p" }
                    classifierCalls++
                    if (p < probMin) probMin = p
                    if (p > probMax) probMax = p
                    probSum += p.toDouble()
                    val isSpeech = p >= 0.5f
                    if (isSpeech) speechWindows++
                    isSpeech
                } finally {
                    gainWindow.fill(0f)
                }
            }
            var processed = 0
            for (i in source.indices) {
                acc[0] = source[i]
                if (buffer.append(acc)) { processed++; break }
                processed++
            }
            val detected = try {
                prepared = buffer!!.samples()
                true
            } catch (e: SpeechNotDetectedException) {
                false
            }
            observation = VadObservation(
                inputCount = source.size,
                processedCount = processed,
                classifierCalls = classifierCalls,
                speechWindows = speechWindows,
                speechSamples = speechWindows * 512,
                detected = detected,
                preparedSamples = prepared?.size ?: 0,
                gain = gain,
                probabilityMin = if (classifierCalls > 0) probMin else null,
                probabilityMax = if (classifierCalls > 0) probMax else null,
                probabilityMean = if (classifierCalls > 0) (probSum / classifierCalls).toFloat() else null
            )
        } finally {
            val unchanged = source.contentEquals(snapshot)
            var releaseErr: Throwable? = null
            try {
                detector?.release()
            } catch (e: Throwable) {
                releaseErr = e
            }
            buffer?.clear()
            prepared?.fill(0f)
            acc.fill(0f)
            snapshot.fill(0f)
            if (releaseErr != null) throw releaseErr
            require(unchanged) { "source mutated by detector" }
        }
        return observation!!
    }
}