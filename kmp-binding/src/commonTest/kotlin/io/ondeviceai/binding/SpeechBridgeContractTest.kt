package io.ondeviceai.binding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class SpeechBridgeContractTest {
    @Test
    fun explicitBlankIdIsInvalid() {
        assertFailsWith<IllegalArgumentException> { SpeechBridgeSelection.Explicit("") }
        assertFailsWith<IllegalArgumentException> { SpeechBridgeSelection.Explicit("  ") }
    }

    @Test
    fun fileReferenceIsOpaqueAndNonblank() {
        assertFailsWith<IllegalArgumentException> { SpeechBridgeInput.AudioFile("") }
        assertFailsWith<IllegalArgumentException> { SpeechBridgeInput.AudioFile(" \t") }
        assertEquals("opaque-token", SpeechBridgeInput.AudioFile("opaque-token").reference)
    }

    @Test
    fun unavailableNeverReturnsResult() {
        val bridge = UnavailableSpeechBridge()
        var backendResults = 0
        var ttsResults = 0
        var asrResults = 0
        val failures = mutableListOf<SpeechBridgeFailure>()
        bridge.queryBackends(SpeechBridgeCapability.TTS, object : SpeechBridgeBackendsCallback {
            override fun onResult(backends: List<SpeechBridgeBackend>) { backendResults++ }
            override fun onFailure(failure: SpeechBridgeFailure) { failures += failure }
            override fun onCancelled() = error("unavailable query must not be cancelled")
        })
        bridge.speak(SpeechBridgeTtsRequest("hello", "en-US"), SpeechBridgeSelection.Default,
            object : SpeechBridgeTtsCallback {
                override fun onResult(synthesis: SpeechBridgeTtsResult) { ttsResults++ }
                override fun onFailure(failure: SpeechBridgeFailure) { failures += failure }
                override fun onCancelled() = error("unavailable TTS must not be cancelled")
            })
        bridge.recognize(SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone), SpeechBridgeSelection.Default,
            object : SpeechBridgeAsrCallback {
                override fun onResult(output: SpeechBridgeAsrResult) { asrResults++ }
                override fun onFailure(failure: SpeechBridgeFailure) { failures += failure }
                override fun onCancelled() = error("unavailable ASR must not be cancelled")
            })

        assertEquals(0, backendResults)
        assertEquals(0, ttsResults)
        assertEquals(0, asrResults)
        assertEquals(3, failures.size)
        assertEquals(setOf(SpeechBridgeFailureCode.BACKEND_UNAVAILABLE), failures.map { it.code }.toSet())
    }

    @Test
    fun invalidRequestsFailBeforeAvailability() {
        val bridge = UnavailableSpeechBridge()
        val failures = mutableListOf<SpeechBridgeFailure>()
        bridge.speak(SpeechBridgeTtsRequest("  ", "en-US"), SpeechBridgeSelection.Default, failureTts(failures))
        bridge.speak(SpeechBridgeTtsRequest("hello", "  "), SpeechBridgeSelection.Default, failureTts(failures))
        bridge.recognize(SpeechBridgeAsrRequest("", SpeechBridgeInput.Microphone), SpeechBridgeSelection.Default,
            failureAsr(failures))

        assertEquals(3, failures.size)
        assertEquals(setOf(SpeechBridgeFailureCode.INVALID_INPUT), failures.map { it.code }.toSet())
    }

    @Test
    fun closedRejectsEveryMethodAndRepeatedCloseCompletes() {
        val bridge = UnavailableSpeechBridge()
        var closeCount = 0
        bridge.close(object : SpeechBridgeCloseCallback {
            override fun onClosed() { closeCount++ }
            override fun onFailure(failure: SpeechBridgeFailure) = error("close must succeed")
        })
        bridge.close(object : SpeechBridgeCloseCallback {
            override fun onClosed() { closeCount++ }
            override fun onFailure(failure: SpeechBridgeFailure) = error("repeated close must succeed")
        })
        val failures = mutableListOf<SpeechBridgeFailure>()
        bridge.queryBackends(SpeechBridgeCapability.ASR, failureBackends(failures))
        bridge.speak(SpeechBridgeTtsRequest("hello", "en-US"), SpeechBridgeSelection.Default, failureTts(failures))
        bridge.recognize(SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone), SpeechBridgeSelection.Default,
            failureAsr(failures))

        assertEquals(2, closeCount)
        assertEquals(3, failures.size)
        assertEquals(listOf(SpeechBridgeFailureCode.CLOSED, SpeechBridgeFailureCode.CLOSED, SpeechBridgeFailureCode.CLOSED), failures.map { it.code })
    }

    @Test
    fun dtoValuesPreserveOpaqueInputsAndOptionalMetadata() {
        val backend = SpeechBridgeBackend(
            SpeechBridgeCapability.ASR,
            "system.asr",
            BridgeAvailability(true, null, "system.asr", "31"),
            listOf(SpeechBridgeInputKind.MICROPHONE),
            offline = true,
        )
        val tts = SpeechBridgeTtsRequest(" hello ", "en-US", "voice-1")
        val asr = SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.AudioFile("opaque-file"))
        assertEquals(" hello ", tts.text)
        assertEquals("voice-1", tts.voiceId)
        assertEquals("opaque-file", (asr.input as SpeechBridgeInput.AudioFile).reference)
        assertEquals("system.asr", backend.availability.backendId)
        assertNull(SpeechBridgeAsrResult("system.asr", "hello", null).confidence)
        assertSame(SpeechBridgeCapability.TTS, SpeechBridgeCapability.valueOf("TTS"))
    }

    @Test
    fun platformFailureAcceptsOnlyStableDomainAndCode() {
        assertFailsWith<IllegalArgumentException> {
            SpeechBridgeFailure(SpeechBridgeFailureCode.PLATFORM, "raw exception message", "speech", 7)
        }
        assertFailsWith<IllegalArgumentException> {
            SpeechBridgeFailure(SpeechBridgeFailureCode.PLATFORM, null, "", 7)
        }
        assertFailsWith<IllegalArgumentException> {
            SpeechBridgeFailure(SpeechBridgeFailureCode.PLATFORM, null, "speech", null)
        }
        assertEquals(
            SpeechBridgeFailureCode.PLATFORM,
            SpeechBridgeFailure(SpeechBridgeFailureCode.PLATFORM, null, "speech", 7).code,
        )
    }

    private fun failureBackends(target: MutableList<SpeechBridgeFailure>) = object : SpeechBridgeBackendsCallback {
        override fun onResult(backends: List<SpeechBridgeBackend>) = error("closed query must fail")
        override fun onFailure(failure: SpeechBridgeFailure) { target += failure }
        override fun onCancelled() = error("closed query must not cancel")
    }

    private fun failureTts(target: MutableList<SpeechBridgeFailure>) = object : SpeechBridgeTtsCallback {
        override fun onResult(synthesis: SpeechBridgeTtsResult) = error("closed TTS must fail")
        override fun onFailure(failure: SpeechBridgeFailure) { target += failure }
        override fun onCancelled() = error("closed TTS must not cancel")
    }

    private fun failureAsr(target: MutableList<SpeechBridgeFailure>) = object : SpeechBridgeAsrCallback {
        override fun onResult(output: SpeechBridgeAsrResult) = error("closed ASR must fail")
        override fun onFailure(failure: SpeechBridgeFailure) { target += failure }
        override fun onCancelled() = error("closed ASR must not cancel")
    }
}
