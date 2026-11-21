package io.ondeviceai.sherpaasr

import io.ondeviceai.speech.SpeechNotDetectedException

/** Bounded short utterance, gated by a speech classifier rather than loudness. */
internal class UtteranceBuffer(private val isSpeech: (FloatArray) -> Boolean) {
    private val waveform = FloatArray(160_000)
    private val window = FloatArray(512)
    private var size = 0
    private var speechSamples = 0
    private var firstSpeech = -1
    private var lastSpeech = 0

    fun append(input: ShortArray, count: Int): Boolean {
        require(count in 0..input.size)
        for (i in 0 until count) if (appendSample(input[i] / 32768f)) return true
        return false
    }

    fun append(input: FloatArray): Boolean {
        for (sample in input) if (appendSample(sample)) return true
        return false
    }

    private fun appendSample(sample: Float): Boolean {
        if (size == waveform.size) return true
        waveform[size] = sample
        window[size % window.size] = sample
        size++
        if (size % window.size == 0 && isSpeech(window)) {
            if (firstSpeech < 0) firstSpeech = size - window.size
            speechSamples += window.size
            lastSpeech = size
        }
        return size == waveform.size || (speechSamples >= 4096 && size - lastSpeech >= 16_000)
    }

    fun samples(): FloatArray {
        if (speechSamples < 4096) throw SpeechNotDetectedException()
        // Preserve 200 ms of context around speech; omit long waits and trailing noise.
        return waveform.copyOfRange(maxOf(0, firstSpeech - 3200), minOf(size, lastSpeech + 3200))
    }

    fun clear() {
        waveform.fill(0f)
        window.fill(0f)
        size = 0
        speechSamples = 0
        firstSpeech = -1
        lastSpeech = 0
    }
}
