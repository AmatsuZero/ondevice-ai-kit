package io.ondeviceai.demo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
fun decodeVoiceWave(data: NSData): VoiceWaveform? {
    val length = data.length.toInt()
    if (length <= 0) return null
    val source = data.bytes ?: return null
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), source, length.toULong())
    }
    return VoiceWaves.decode(bytes)
}
