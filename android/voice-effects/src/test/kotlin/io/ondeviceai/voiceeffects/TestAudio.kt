package io.ondeviceai.voiceeffects

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun wave(rate: Int = 16000, frames: Int = 32, frequency: Int? = null): ByteArray {
    val bytes = ByteArray(44 + frames * 2)
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(bytes.size - 8)
    b.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
    b.putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
    b.put("data".toByteArray(Charsets.US_ASCII)).putInt(frames * 2)
    repeat(frames) { i ->
        b.putShort(if (frequency == null) 0 else
            (16384 * sin(2 * PI * frequency * i / rate)).roundToInt().toShort())
    }
    return bytes
}

internal fun File.inputWave(rate: Int = 16000, frames: Int = 32, frequency: Int? = null): File =
    File(this, "input.wav").apply { writeBytes(wave(rate, frames, frequency)) }

internal fun pcm(bytes: ByteArray): ShortArray {
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return ShortArray((bytes.size - 44) / 2) { b.getShort(44 + it * 2) }
}
