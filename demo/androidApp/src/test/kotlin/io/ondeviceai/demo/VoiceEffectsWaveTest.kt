package io.ondeviceai.demo

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class VoiceEffectsWaveTest {
    @Test fun canonicalRoundTripAndExtraChunksAreNormalized() {
        val canonical = wave(16000, shortArrayOf(-32768, 0, 32767))
        assertContentEquals(canonical, VoiceEffectsWave.normalize(ByteArrayInputStream(canonical)))
        val withChunk = canonical.copyOfRange(0, 36) + byteArrayOf(74,85,78,75,2,0,0,0,7,8) + canonical.copyOfRange(36, canonical.size)
        ByteBuffer.wrap(withChunk).order(ByteOrder.LITTLE_ENDIAN).putInt(4, withChunk.size-8)
        assertContentEquals(canonical, VoiceEffectsWave.normalize(ByteArrayInputStream(withChunk)))
    }
    @Test fun oversizedTruncatedAndStereoAreRejected() {
        val valid = wave(16000, shortArrayOf(1,2))
        assertFails { VoiceEffectsWave.normalize(ByteArrayInputStream(valid.dropLast(1).toByteArray())) }
        val stereo = valid.clone(); stereo[22]=2
        assertFails { VoiceEffectsWave.normalize(ByteArrayInputStream(stereo)) }
        val overflow = valid.clone(); ByteBuffer.wrap(overflow).order(ByteOrder.LITTLE_ENDIAN).putInt(4, Int.MAX_VALUE)
        assertFails { VoiceEffectsWave.normalize(ByteArrayInputStream(overflow)) }
        val tooLong = wave(16000, ShortArray(480001))
        assertFails { VoiceEffectsWave.normalize(ByteArrayInputStream(tooLong)) }
    }
    @Test fun noTrailingOrEmptyAudioIsAccepted() {
        assertFails { VoiceEffectsWave.normalize(ByteArrayInputStream(wave(16000, shortArrayOf()))) }
        assertFails { VoiceEffectsWave.normalize(ByteArrayInputStream(wave(16000, shortArrayOf(1)) + byteArrayOf(0))) }
    }
    companion object {
        fun wave(rate: Int, samples: ShortArray): ByteArray {
            val b=ByteBuffer.allocate(44+samples.size*2).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()).putInt(b.capacity()-8).put("WAVEfmt ".toByteArray()).putInt(16)
            b.putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16)
            b.put("data".toByteArray()).putInt(samples.size*2); samples.forEach { b.putShort(it) }; return b.array()
        }
    }
}
