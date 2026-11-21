package io.ondeviceai.sherpaasr

import io.ondeviceai.speech.SpeechBackendUnavailableException
import io.ondeviceai.speech.SpeechNotDetectedException
import java.nio.file.Files
import kotlin.test.*

class SherpaModelTest {
    @Test fun missingAndTruncatedModelsAreRejectedBeforeNativeLoad() {
        val directory = Files.createTempDirectory("sherpa-model").toFile()
        try {
            val model = SherpaWhisperModel(directory)
            assertFalse(model.isPresent())
            assertFailsWith<SpeechBackendUnavailableException> { model.verify() }
            SherpaWhisperModel.files.keys.forEach { directory.resolve(it).writeText("truncated") }
            assertFalse(model.isPresent())
            assertFailsWith<SpeechBackendUnavailableException> { model.verify() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun shortUtteranceEndsAfterSpeechAndOneSecondOfSilence() {
        val input = UtteranceBuffer { frame -> frame.any { it > .1f } }
        repeat(5) { assertFalse(input.append(ShortArray(1600), 1600)) }
        repeat(4) { assertFalse(input.append(ShortArray(1600) { 5000 }, 1600)) }
        repeat(9) { assertFalse(input.append(ShortArray(1600), 1600)) }
        assertFalse(input.append(ShortArray(1600), 1600)) // sub-window silence remains buffered
        assertTrue(input.append(ShortArray(1600), 1600))
        assertTrue(input.samples().size < 25600) // leading/trailing silence is trimmed
    }

    @Test fun continuousSpeechIsCappedAtTenSeconds() {
        val input = UtteranceBuffer { true }
        repeat(99) { assertFalse(input.append(ShortArray(1600) { 5000 }, 1600)) }
        assertTrue(input.append(ShortArray(1600) { 5000 }, 1600))
        assertTrue(input.samples().size in 150000..160000)
    }

    @Test fun silenceAndNonSpeechNoiseNeverBecomeDecodableAudio() {
        for (amplitude in listOf(0, 5000)) {
            val input = UtteranceBuffer { false }
            repeat(100) { input.append(ShortArray(1600) { amplitude.toShort() }, 1600) }
            assertFailsWith<SpeechNotDetectedException> { input.samples() }
        }
    }

    @Test fun aSingleSoundBurstDoesNotCountAsAnUtterance() {
        val input = UtteranceBuffer { frame -> frame.any { it > .1f } }
        input.append(ShortArray(1600) { 5000 }, 1600)
        repeat(10) { input.append(ShortArray(1600), 1600) }
        assertFailsWith<SpeechNotDetectedException> { input.samples() }
    }

    @Test fun resetDoesNotReuseThePreviousUtterance() {
        val input = UtteranceBuffer { true }
        repeat(5) { input.append(ShortArray(1600) { 5000 }, 1600) }
        assertTrue(input.samples().isNotEmpty())
        input.clear()
        assertFailsWith<SpeechNotDetectedException> { input.samples() }
    }
}
