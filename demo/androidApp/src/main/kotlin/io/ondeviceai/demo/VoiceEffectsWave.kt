package io.ondeviceai.demo

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded RIFF parsing; only the frozen v1 PCM matrix is admitted. */
internal object VoiceEffectsWave {
    private const val MAX_BYTES = 4 * 1024 * 1024
    fun normalize(input: InputStream): ByteArray {
        val storage = ByteArrayOutputStream(); val chunk = ByteArray(8192)
        while (storage.size() <= MAX_BYTES) {
            val n = input.read(chunk, 0, minOf(chunk.size, MAX_BYTES + 1 - storage.size()))
            if (n < 0) break
            require(n > 0); storage.write(chunk, 0, n)
        }
        val bytes = storage.toByteArray()
        require(bytes.size in 44..MAX_BYTES)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
        require(tag(0) == "RIFF" && tag(8) == "WAVE" && b.getInt(4).toLong() + 8 == bytes.size.toLong())
        var offset = 12; var rate: Int? = null; var pcm: ByteArray? = null
        while (offset < bytes.size) {
            require(offset <= bytes.size - 8)
            val count = b.getInt(offset + 4).toLong() and 0xffffffffL
            val end = offset.toLong() + 8 + count
            require(end <= bytes.size && count <= MAX_BYTES)
            when (tag(offset)) {
                "fmt " -> {
                    require(rate == null && count >= 16)
                    require(b.getShort(offset+8).toInt() == 1 && b.getShort(offset+10).toInt() == 1)
                    val sampleRate = b.getInt(offset+12)
                    require(sampleRate in listOf(16000,44100,48000))
                    require(b.getInt(offset+16) == sampleRate*2 && b.getShort(offset+20).toInt() == 2 && b.getShort(offset+22).toInt() == 16)
                    rate = sampleRate
                }
                "data" -> { require(pcm == null); pcm = bytes.copyOfRange(offset+8, end.toInt()) }
            }
            offset = (end + count % 2).toInt(); require(offset <= bytes.size)
        }
        require(offset == bytes.size)
        return canonical(requireNotNull(rate), requireNotNull(pcm))
    }
    fun canonical(rate: Int, pcm: ByteArray): ByteArray {
        require(rate in listOf(16000,44100,48000) && pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= rate * 60)
        return ByteBuffer.allocate(44+pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36+pcm.size).put("WAVEfmt ".toByteArray()).putInt(16)
            putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16)
            put("data".toByteArray()).putInt(pcm.size).put(pcm)
        }.array()
    }
}
