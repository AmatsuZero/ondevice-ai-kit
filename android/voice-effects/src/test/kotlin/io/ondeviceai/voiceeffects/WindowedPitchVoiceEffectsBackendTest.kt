package io.ondeviceai.voiceeffects

import java.io.File
import kotlin.math.*
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WindowedPitchVoiceEffectsBackendTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun originalIsIndependentAfterCallerRewritesInput(): Unit = runBlocking {
        val root = temporary.newFolder()
        val input = root.inputWave(44100, 44100, 440)
        val before = input.readBytes()
        val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), File(root, "out"))
        val output = client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.ORIGINAL))
        try {
            input.writeBytes(wave(16000, 1))
            client.close()
            assertContentEquals(before, output.audioFile.readBytes())
        } finally { client.releaseOutput(output.id); client.close() }
    }

    @Test fun realNonzeroSingleFrameAcrossRatesAndPresets(): Unit = runBlocking {
        val root = temporary.newFolder()
        val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), File(root, "out"))
        try {
            for (rate in listOf(16000, 44100, 48000)) {
                val bytes = wave(rate, 1)
                java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort(44, 16384)
                val input = File(root, "one.wav").apply { writeBytes(bytes) }
                for (preset in VoiceEffectsPreset.entries) {
                    val output = client.process(VoiceEffectsRequest(input, preset))
                    try {
                        assertEquals(1L, output.frameCount)
                        val data = WaveCodec.read(output.audioFile.inputStream().use { it.readBytes() }.inputStream())
                        assertEquals(1L, data.frameCount)
                        assertTrue(WaveCodec.decode(data).all { it.isFinite() && it in -1f..1f })
                    } finally { client.releaseOutput(output.id) }
                }
            }
        } finally { client.close() }
    }

    @Test fun realWavAndDspMeetTwentySevenToneCasesAndPreserveInput(): Unit = runBlocking {
        val root = temporary.newFolder()
        val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), File(root, "out"))
        try {
            for (rate in listOf(16000, 44100, 48000)) for (frequency in listOf(220, 440, 880)) {
                val input = root.inputWave(rate, rate * 2, frequency)
                val before = input.readBytes()
                for (preset in VoiceEffectsPreset.entries) {
                    val result = client.process(VoiceEffectsRequest(input, preset))
                    try {
                        val bytes = result.audioFile.readBytes()
                        val parsed = WaveCodec.read(bytes.inputStream())
                        assertEquals(rate, result.sampleRate)
                        assertEquals(rate * 2L, result.frameCount)
                        assertEquals(2.0, result.durationSeconds)
                        assertEquals("android.windowed-pitch", result.backendId)
                        assertEquals(rate, parsed.sampleRate); assertEquals(rate * 2L, parsed.frameCount)
                        assertNotEquals(input.canonicalFile, result.audioFile.canonicalFile)
                        assertContentEquals(before, input.readBytes())
                        if (preset == VoiceEffectsPreset.ORIGINAL) assertContentEquals(before, bytes)
                        val semitones = when (preset) { VoiceEffectsPreset.ORIGINAL -> 0; VoiceEffectsPreset.HIGH -> 4; VoiceEffectsPreset.LOW -> -4 }
                        val target = frequency * 2.0.pow(semitones / 12.0)
                        val actual = estimateFrequency(pcm(bytes), rate)
                        assertTrue(abs(actual - target) / target <= 0.02, "$rate/$frequency/$preset: $actual vs $target")
                        assertTrue(WaveCodec.decode(parsed).all { it.isFinite() && it in -1f..1f })
                    } finally { client.releaseOutput(result.id) }
                }
            }
        } finally { client.close() }
    }

    @Test fun realOneFrameSilenceAndThirtySecondBoundariesAcrossRatesAndPresets(): Unit = runBlocking {
        val root = temporary.newFolder()
        val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), File(root, "out"))
        try {
            for (rate in listOf(16000, 44100, 48000)) for (frames in listOf(1, rate, rate * 30)) {
                val input = root.inputWave(rate, frames)
                for (preset in VoiceEffectsPreset.entries) {
                    val result = client.process(VoiceEffectsRequest(input, preset))
                    try {
                        assertEquals(frames.toLong(), result.frameCount)
                        assertEquals(frames.toDouble() / rate, result.durationSeconds)
                        assertTrue(pcm(result.audioFile.readBytes()).all { it == 0.toShort() })
                    } finally { client.releaseOutput(result.id) }
                }
            }
        } finally { client.close() }
    }

    @Test fun dspHasBoundedCancellationChecksAndNeverChangesInput() {
        val input = FloatArray(48000 * 30) { 0.25f }
        var checks = 0
        val validationChecks = (input.size + 1023) / 1024 + 1
        val stopAt = validationChecks + 4
        assertFailsWith<CancellationException> {
            WindowedPitch.shift(input, 48000, 4) {
                if (++checks == stopAt) throw CancellationException("test cancellation")
            }
        }
        assertEquals(stopAt, checks)
        assertTrue(input.all { it == 0.25f })
    }

    @Test fun realDspRejectsInvalidValuesAndPreservesFiniteRange() {
        for (bad in listOf(floatArrayOf(), floatArrayOf(Float.NaN), floatArrayOf(Float.POSITIVE_INFINITY), floatArrayOf(1.1f))) {
            assertFailsWith<IllegalArgumentException> { WindowedPitch.shift(bad, 16000, 4) }
        }
        assertFailsWith<IllegalArgumentException> { WindowedPitch.shift(floatArrayOf(0f), 8000, 4) }
        assertFailsWith<IllegalArgumentException> { WindowedPitch.shift(floatArrayOf(0f), 16000, 3) }
        assertFailsWith<IllegalArgumentException> { WindowedPitch.shift(FloatArray(16000 * 30 + 1), 16000, 4) }
        val input = floatArrayOf(-1f, 1f, 0f)
        for (semi in listOf(0, 4, -4)) {
            val output = WindowedPitch.shift(input, 16000, semi)
            assertEquals(input.size, output.size); assertNotSame(input, output)
            assertTrue(output.all { it.isFinite() && it in -1f..1f })
            assertContentEquals(floatArrayOf(-1f, 1f, 0f), input)
        }
    }

    // Matches the prototype's interpolated rising crossings and median period;
    // excludes the first/last 200 ms. This measures synthetic pitch only.
    private fun estimateFrequency(samples: ShortArray, rate: Int): Double {
        val edges = mutableListOf<Double>()
        val trim = (rate * 0.2).roundToInt()
        for (i in trim until samples.size - trim - 1) {
            val a = samples[i].toDouble(); val b = samples[i + 1].toDouble()
            if (a <= 0 && b > 0) edges.add(i - a / (b - a))
        }
        val periods = edges.zipWithNext { a, b -> b - a }.sorted()
        assertTrue(periods.isNotEmpty())
        val mid = periods.size / 2
        val median = if (periods.size % 2 == 0) (periods[mid - 1] + periods[mid]) / 2 else periods[mid]
        return rate / median
    }
}
