package io.ondeviceai.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceWaveformTest {
    private fun wav(sampleRate: Int, samples: ShortArray): ByteArray {
        val dataBytes = samples.size * 2
        val bytes = ByteArray(44 + dataBytes)
        fun ascii(offset: Int, value: String) {
            value.forEachIndexed { i, c -> bytes[offset + i] = c.code.toByte() }
        }
        fun put16(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        fun put32(offset: Int, value: Int) {
            put16(offset, value and 0xFFFF)
            put16(offset + 2, (value shr 16) and 0xFFFF)
        }
        ascii(0, "RIFF"); put32(4, 36 + dataBytes); ascii(8, "WAVE"); ascii(12, "fmt ")
        put32(16, 16); put16(20, 1); put16(22, 1); put32(24, sampleRate)
        put32(28, sampleRate * 2); put16(32, 2); put16(34, 16)
        ascii(36, "data"); put32(40, dataBytes)
        samples.forEachIndexed { i, s -> put16(44 + i * 2, s.toInt()) }
        return bytes
    }

    @Test
    fun decodesNative22050DurationExactly() {
        val wave = VoiceWaves.decode(wav(22050, ShortArray(22050)))
        assertEquals(1.0, wave?.durationSeconds ?: -1.0, 1e-9)
        assertTrue((wave?.peaks?.size ?: 0) > 0)
        assertTrue(wave!!.peaks.all { it == 0f })
    }

    @Test
    fun peaksAreLinearAbsNormalized() {
        val samples = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 0, 8192)
        val wave = VoiceWaves.decode(wav(44100, samples))!!
        assertEquals(4, wave.peaks.size)
        assertEquals(Short.MAX_VALUE / 32768f, wave.peaks[0])
        assertEquals(1f, wave.peaks[1])
        assertEquals(0f, wave.peaks[2])
        assertEquals(8192f / 32768f, wave.peaks[3])
    }

    @Test
    fun usesMaximumAbsPerBin() {
        val samples = ShortArray(256) { if (it == 7) Short.MAX_VALUE else 0 }
        val wave = VoiceWaves.decode(wav(16000, samples))!!
        assertEquals(Short.MAX_VALUE / 32768f, wave.peaks.maxOrNull())
    }

    @Test
    fun rejectsNonPcm16MonoHeader() {
        val bytes = wav(44100, shortArrayOf(1, 2))
        bytes[22] = 2
        assertNull(VoiceWaves.decode(bytes))
    }

    @Test
    fun rejectsTruncatedData() {
        val bytes = wav(44100, ShortArray(16))
        assertNull(VoiceWaves.decode(bytes.copyOf(bytes.size - 2)))
    }
}
