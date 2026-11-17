package io.ondeviceai.voiceeffects

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal data class WaveData(val bytes: ByteArray, val sampleRate: Int, val frameCount: Long)

/** Canonical mono PCM16 only. Every read is bounded by the validated header. */
internal object WaveCodec {
    const val MAX_FILE_BYTES = 44 + 48000 * 30 * 2
    private const val CHUNK = 8192

    fun read(source: InputStream, checkCancelled: () -> Unit = {}): WaveData {
        val header = ByteArray(44)
        readExactly(source, header, 0, 44, checkCancelled)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(offset: Int, expected: String): Boolean =
            expected.indices.all { header[offset + it] == expected[it].code.toByte() }
        fun u16(offset: Int): Int = b.getShort(offset).toInt() and 0xffff
        fun u32(offset: Int): Long = b.getInt(offset).toLong() and 0xffffffffL
        if (!tag(0, "RIFF") || !tag(8, "WAVE")) throw VoiceEffectsError.InvalidInput()
        if (!tag(12, "fmt ") || !tag(36, "data") || u32(16) != 16L) throw VoiceEffectsError.UnsupportedInput()
        val rate = b.getInt(24)
        if (u16(20) != 1 || u16(22) != 1 || u16(34) != 16 || rate !in intArrayOf(16000, 44100, 48000)) {
            throw VoiceEffectsError.UnsupportedInput()
        }
        if (u32(28) != rate * 2L || u16(32) != 2) throw VoiceEffectsError.InvalidInput()
        val size = u32(40)
        // All arithmetic is Long until every bound is proven; no untrusted allocation.
        if (size < 2 || size % 2 != 0L || size > rate * 30L * 2 ||
            size + 44 > MAX_FILE_BYTES || u32(4) != size + 36) throw VoiceEffectsError.InvalidInput()
        val bytes = ByteArray(44 + size.toInt())
        header.copyInto(bytes)
        readExactly(source, bytes, 44, size.toInt(), checkCancelled)
        checkCancelled()
        // Consume at most ONE excess byte, even if a file grows forever.
        if (source.read() != -1) throw VoiceEffectsError.InvalidInput()
        checkCancelled()
        return WaveData(bytes, rate, size / 2)
    }

    private fun readExactly(source: InputStream, target: ByteArray, offset: Int, length: Int, checkCancelled: () -> Unit) {
        var done = 0
        while (done < length) {
            checkCancelled()
            val count = source.read(target, offset + done, minOf(CHUNK, length - done))
            if (count < 0) throw VoiceEffectsError.InvalidInput()
            if (count == 0) {
                val byte = source.read()
                if (byte < 0) throw VoiceEffectsError.InvalidInput()
                target[offset + done++] = byte.toByte()
            } else done += count
        }
    }

    fun decode(wave: WaveData, checkCancelled: () -> Unit = {}): FloatArray {
        val b = ByteBuffer.wrap(wave.bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(wave.frameCount.toInt()) { index ->
            if (index % 1024 == 0) checkCancelled()
            b.getShort(44 + index * 2).toFloat() / 32768f
        }
    }

    fun encode(wave: WaveData, samples: FloatArray, checkCancelled: () -> Unit = {}): ByteArray {
        if (samples.size.toLong() != wave.frameCount) throw VoiceEffectsError.Platform()
        val bytes = ByteArray(wave.bytes.size)
        wave.bytes.copyInto(bytes, endIndex = 44)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.position(44)
        samples.forEachIndexed { index, sample ->
            if (index % 1024 == 0) checkCancelled()
            if (!sample.isFinite() || sample !in -1f..1f) throw VoiceEffectsError.Platform()
            b.putShort((sample * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        checkCancelled()
        return bytes
    }
}
