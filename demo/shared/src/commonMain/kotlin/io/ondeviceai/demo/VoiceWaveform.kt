package io.ondeviceai.demo

data class VoiceWaveform(val peaks: FloatArray, val durationSeconds: Double)

object VoiceWaves {
    private const val HEADER_BYTES = 44
    private const val MAX_BINS = 128

    fun decode(bytes: ByteArray): VoiceWaveform? {
        if (bytes.size < HEADER_BYTES) return null
        if (tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE" ||
            tag(bytes, 12) != "fmt " || tag(bytes, 36) != "data") return null
        if (le32(bytes, 16) != 16L) return null
        if (le16(bytes, 20) != 1) return null
        if (le16(bytes, 22) != 1) return null
        if (le16(bytes, 34) != 16) return null
        val sampleRate = le32(bytes, 24)
        if (sampleRate <= 0L) return null
        val dataBytes = le32(bytes, 40)
        if (dataBytes < 0L || HEADER_BYTES.toLong() + dataBytes > bytes.size.toLong()) return null
        val samples = (dataBytes / 2L).toInt()
        if (samples <= 0) return VoiceWaveform(FloatArray(0), 0.0)
        val bins = minOf(MAX_BINS, samples)
        val peaks = FloatArray(bins)
        var i = 0
        while (i < samples) {
            val offset = HEADER_BYTES + i * 2
            val raw = le16(bytes, offset)
            val sample = if (raw >= 0x8000) raw - 0x10000 else raw
            val magnitude = kotlin.math.abs(sample) / 32768f
            val bin = (i.toLong() * bins / samples).toInt()
            if (magnitude > peaks[bin]) peaks[bin] = magnitude
            i++
        }
        return VoiceWaveform(peaks, samples.toDouble() / sampleRate.toDouble())
    }

    private fun tag(bytes: ByteArray, offset: Int): String =
        bytes.decodeToString(offset, offset + 4)

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Long =
        le16(bytes, offset).toLong() or (le16(bytes, offset + 2).toLong() shl 16)
}
