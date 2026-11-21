package io.ondeviceai.voiceeffects

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WaveCodecTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun malformedUnsupportedAndOutOfBoundsFilesAreRejectedWithoutOutput(): Unit = runBlocking {
        val valid = wave()
        fun changed(offset: Int, width: Int, value: Int): ByteArray = valid.copyOf().also {
            val b = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
            if (width == 2) b.putShort(offset, value.toShort()) else b.putInt(offset, value)
        }
        val cases = listOf(
            byteArrayOf(), valid.copyOf(42), valid + byteArrayOf(0, 0),
            valid.copyOf().apply { this[0] = 0 },
            valid.copyOf().apply { this[8] = 0 },
            valid.copyOf().apply { this[12] = 0 },
            valid.copyOf().apply { this[36] = 0 },
            changed(4, 4, 1), changed(16, 4, 18), changed(20, 2, 3),
            changed(22, 2, 2), changed(24, 4, 8000), changed(28, 4, 1),
            changed(32, 2, 4), changed(34, 2, 8), changed(40, 4, 0),
            changed(40, 4, 63), changed(40, 4, -1), changed(4, 4, -1),
            wave(16000, 16000 * 30 + 1), ByteArray(WaveCodec.MAX_FILE_BYTES + 1)
        )
        val root = temporary.newFolder()
        val outputs = File(root, "out")
        val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), outputs)
        try {
            for (bytes in cases) {
                val source = File(root, "bad.wav").apply { writeBytes(bytes) }
                val error = assertFailsWith<VoiceEffectsError> {
                    client.process(VoiceEffectsRequest(source, VoiceEffectsPreset.HIGH))
                }
                assertTrue(error.code in listOf(VoiceEffectsFailureCode.INVALID_INPUT, VoiceEffectsFailureCode.UNSUPPORTED_INPUT))
                assertNull(error.cause); assertFalse(error.message.orEmpty().contains(root.path))
                assertContentEquals(bytes, source.readBytes())
                assertTrue(outputs.listFiles().orEmpty().isEmpty())
            }
            for (file in listOf(File(root, "missing.wav"), root)) {
                assertFailsWith<VoiceEffectsError.InvalidInput> { client.process(VoiceEffectsRequest(file, VoiceEffectsPreset.HIGH)) }
            }
        } finally { client.close() }
    }

    @Test fun unsupportedHasDistinctCodeFromMalformedHeader() {
        val stereo = wave().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(22, 2) }
        assertFailsWith<VoiceEffectsError.UnsupportedInput> { WaveCodec.read(ByteArrayInputStream(stereo)) }
        assertFailsWith<VoiceEffectsError.InvalidInput> { WaveCodec.read(ByteArrayInputStream(byteArrayOf())) }
    }

    @Test fun growthIsBoundedByDeclaredPayloadPlusOneAndNeverProducesOutput(): Unit = runBlocking {
        val root = temporary.newFolder()
        val input = root.inputWave(frames = 1)
        var consumed = 0
        val tiny = wave(frames = 1)
        val files = object : VoiceEffectsFiles() {
            override fun openInput(file: File): InputStream = object : InputStream() {
                override fun read(): Int {
                    val i = consumed++
                    return if (i < tiny.size) tiny[i].toInt() and 255 else 0
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    // An infinite, concurrently growing source: no EOF ever arrives.
                    repeat(len) { b[off + it] = read().toByte() }
                    return len
                }
            }
        }
        val out = File(root, "out")
        val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), out, files)
        assertFailsWith<VoiceEffectsError.InvalidInput> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.ORIGINAL)) }
        assertEquals(tiny.size + 1, consumed)
        assertTrue(out.listFiles().orEmpty().isEmpty())
        client.close()
    }

    @Test fun growthDuringRealFileReadIsRejectedAtEofProbe(): Unit = runBlocking {
        val root = temporary.newFolder()
        val input = root.inputWave()
        val files = object : VoiceEffectsFiles() {
            override fun openInput(file: File): InputStream = object : java.io.FilterInputStream(super.openInput(file)) {
                var appended = false
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val count = super.read(b, off, len)
                    if (!appended) { appended = true; file.appendBytes(ByteArray(16384)) }
                    return count
                }
            }
        }
        val out = File(root, "out")
        val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), out, files)
        assertFailsWith<VoiceEffectsError.InvalidInput> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH)) }
        assertTrue(out.listFiles().orEmpty().isEmpty()); client.close()
    }

    @Test fun headerBoundsAreCheckedBeforeAnyPayloadReadOrLargeAllocation() {
        val header = wave(frames = 1).copyOf(44)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Int.MAX_VALUE)
        var bytesRead = 0
        val source = object : ByteArrayInputStream(header) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) bytesRead += n
                return n
            }
        }
        assertFailsWith<VoiceEffectsError.InvalidInput> { WaveCodec.read(source) }
        assertEquals(44, bytesRead)
    }

    @Test fun decoderAndEncoderUseFullPcmRangeRoundingAndClamping() {
        val bytes = wave(frames = 4)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        listOf(-32768, -1, 0, 32767).forEachIndexed { i, s -> b.putShort(44 + 2 * i, s.toShort()) }
        val data = WaveCodec.read(ByteArrayInputStream(bytes))
        assertContentEquals(floatArrayOf(-1f, -1f / 32768, 0f, 32767f / 32768), WaveCodec.decode(data))
        val encoded = WaveCodec.encode(data, floatArrayOf(-1f, -0.5f / 32768, 0.5f / 32768, 1f))
        assertContentEquals(shortArrayOf(-32768, 0, 1, 32767), pcm(encoded))
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, 1.01f)) {
            assertFailsWith<VoiceEffectsError.Platform> { WaveCodec.encode(data, FloatArray(4) { value }) }
        }
        assertFailsWith<VoiceEffectsError.Platform> { WaveCodec.encode(data, floatArrayOf(0f)) }
    }
}
