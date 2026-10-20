package io.ondeviceai.speech

import io.ondeviceai.binding.AndroidSpeechBridge
import io.ondeviceai.binding.BridgeTask
import io.ondeviceai.binding.SpeechBridgeAsrCallback
import io.ondeviceai.binding.SpeechBridgeAsrRequest
import io.ondeviceai.binding.SpeechBridgeAsrResult
import io.ondeviceai.binding.SpeechBridgeBackend
import io.ondeviceai.binding.SpeechBridgeBackendsCallback
import io.ondeviceai.binding.SpeechBridgeCapability
import io.ondeviceai.binding.SpeechBridgeCloseCallback
import io.ondeviceai.binding.SpeechBridgeFailure
import io.ondeviceai.binding.SpeechBridgeFailureCode
import io.ondeviceai.binding.SpeechBridgeInput
import io.ondeviceai.binding.SpeechBridgeInputKind
import io.ondeviceai.binding.SpeechBridgeSelection
import io.ondeviceai.binding.SpeechBridgeTtsCallback
import io.ondeviceai.binding.SpeechBridgeTtsRequest
import io.ondeviceai.binding.SpeechBridgeTtsResult
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidSpeechBridgeIntegrationTest {
    @Test
    fun explicitUnavailableNeverCallsDefault() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val default = TestTtsBackend("android.default.tts")
            val unavailable = TestTtsBackend(
                "microsoft.azure.tts",
                availability = unavailable(UnavailableReason.MISSING_ASSETS, "microsoft.azure.tts"),
            )
            BridgeFixture(tts = SpeechSynthesisClient(default.backendId, listOf(default, unavailable))).use { fixture ->
                val callback = TtsCapture()
                fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("hello", "en-US"),
                        SpeechBridgeSelection.Explicit(unavailable.backendId),
                        callback,
                    )
                }

                val failure = callback.failure.await()
                assertEquals(SpeechBridgeFailureCode.BACKEND_UNAVAILABLE, failure.code)
                assertEquals(UnavailableReason.MISSING_ASSETS.name, failure.reason)
                assertEquals(0, default.calls.get())
                assertEquals(0, unavailable.calls.get())
                callback.assertOnlyTerminal("failure")
                callback.assertOnTestMain()
                fixture.shutdown()
            }
        }
    }

    @Test
    fun fileInputRejectedBeforeNativeRecognition() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val backend = TestAsrBackend("android.speech.recognizer")
            BridgeFixture(asr = SpeechRecognitionClient(backend.backendId, listOf(backend))).use { fixture ->
                val recognition = AsrCapture()
                fixture.onMain {
                    fixture.bridge.recognize(
                        SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.AudioFile("opaque-file")),
                        SpeechBridgeSelection.Default,
                        recognition,
                    )
                }
                assertEquals(SpeechBridgeFailureCode.UNSUPPORTED_INPUT, recognition.failure.await().code)
                assertEquals(0, backend.calls.get())

                val query = BackendsCapture()
                fixture.onMain { fixture.bridge.queryBackends(SpeechBridgeCapability.ASR, query) }
                assertEquals(listOf(SpeechBridgeInputKind.MICROPHONE), query.result.await().single().supportedInputs)
                recognition.assertOnlyTerminal("failure")
                fixture.shutdown()
            }
        }
    }

    @Test
    fun ttsAndAsrShareBusySlot() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val ttsBackend = TestTtsBackend(
                backendId = "android.default.tts",
                speakBlock = { request ->
                    entered.complete(Unit)
                    release.await()
                    SpeechSynthesisResult(backendId, request.voiceId)
                },
            )
            val asrBackend = TestAsrBackend("android.speech.recognizer")
            BridgeFixture(
                tts = SpeechSynthesisClient(ttsBackend.backendId, listOf(ttsBackend)),
                asr = SpeechRecognitionClient(asrBackend.backendId, listOf(asrBackend)),
            ).use { fixture ->
                val tts = TtsCapture()
                fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("hold", "en-US"),
                        SpeechBridgeSelection.Default,
                        tts,
                    )
                }
                entered.await()

                val asr = AsrCapture()
                fixture.onMain {
                    fixture.bridge.recognize(
                        SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone),
                        SpeechBridgeSelection.Default,
                        asr,
                    )
                }
                assertEquals(SpeechBridgeFailureCode.BUSY, asr.failure.await().code)
                assertEquals(0, asrBackend.calls.get())
                release.complete(Unit)
                assertEquals(ttsBackend.backendId, tts.result.await().backendId)
                fixture.shutdown()
            }
        }
    }

    @Test
    fun missingAsrClientDoesNotBlockTts() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val backend = TestTtsBackend("android.default.tts")
            BridgeFixture(tts = SpeechSynthesisClient(backend.backendId, listOf(backend))).use { fixture ->
                val asrQuery = BackendsCapture()
                fixture.onMain { fixture.bridge.queryBackends(SpeechBridgeCapability.ASR, asrQuery) }
                assertEquals(SpeechBridgeFailureCode.BACKEND_UNAVAILABLE, asrQuery.failure.await().code)

                val asr = AsrCapture()
                fixture.onMain {
                    fixture.bridge.recognize(
                        SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone),
                        SpeechBridgeSelection.Default,
                        asr,
                    )
                }
                assertEquals(SpeechBridgeFailureCode.BACKEND_UNAVAILABLE, asr.failure.await().code)

                val tts = TtsCapture()
                fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("hello", "en-US"),
                        SpeechBridgeSelection.Default,
                        tts,
                    )
                }
                assertEquals(backend.backendId, tts.result.await().backendId)
                assertEquals(1, backend.calls.get())
                fixture.shutdown()
            }
        }
    }

    @Test
    fun cancelWaitsForNativeFinallyThenAllowsCallbackReentry() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val entered = CompletableDeferred<Unit>()
            val draining = CompletableDeferred<Unit>()
            val releaseDrain = CompletableDeferred<Unit>()
            val backend = TestTtsBackend(
                backendId = "android.default.tts",
                speakBlock = { request ->
                    if (request.text == "first") {
                        entered.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                draining.complete(Unit)
                                releaseDrain.await()
                            }
                        }
                    }
                    SpeechSynthesisResult(backendId, request.voiceId)
                },
            )
            BridgeFixture(tts = SpeechSynthesisClient(backend.backendId, listOf(backend))).use { fixture ->
                val terminals = mutableListOf<String>()
                val reentered = TtsCapture()
                val first = object : SpeechBridgeTtsCallback {
                    override fun onResult(synthesis: SpeechBridgeTtsResult) { terminals += "result" }
                    override fun onFailure(failure: SpeechBridgeFailure) { terminals += "failure" }
                    override fun onCancelled() {
                        terminals += "cancelled"
                        fixture.bridge.speak(
                            SpeechBridgeTtsRequest("reentered", "en-US"),
                            SpeechBridgeSelection.Default,
                            reentered,
                        )
                    }
                }
                val task = fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("first", "en-US"),
                        SpeechBridgeSelection.Default,
                        first,
                    )
                }
                entered.await()
                fixture.onMain { task.cancel() }
                draining.await()
                assertEquals(emptyList(), terminals)

                val beforeDrain = TtsCapture()
                fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("too-early", "en-US"),
                        SpeechBridgeSelection.Default,
                        beforeDrain,
                    )
                }
                assertEquals(SpeechBridgeFailureCode.BUSY, beforeDrain.failure.await().code)

                releaseDrain.complete(Unit)
                assertEquals(backend.backendId, reentered.result.await().backendId)
                assertEquals(listOf("cancelled"), terminals)
                reentered.assertOnlyTerminal("result")
                fixture.shutdown()
            }
        }
    }

    @Test
    fun lateSuccessCannotReplaceCancelled() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val backend = TestTtsBackend(
                backendId = "android.default.tts",
                speakBlock = { request ->
                    withContext(NonCancellable) {
                        entered.complete(Unit)
                        release.await()
                        SpeechSynthesisResult(backendId, request.voiceId)
                    }
                },
            )
            BridgeFixture(tts = SpeechSynthesisClient(backend.backendId, listOf(backend))).use { fixture ->
                val callback = TtsCapture()
                val task = fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("late", "en-US"),
                        SpeechBridgeSelection.Default,
                        callback,
                    )
                }
                entered.await()
                fixture.onMain { task.cancel() }
                assertFalse(callback.terminal.isCompleted)
                release.complete(Unit)
                assertEquals("cancelled", callback.terminal.await())
                callback.assertOnlyTerminal("cancelled")
                fixture.shutdown()
            }
        }
    }

    @Test
    fun closeJoinsAllWorkersAndConcurrentWaiters() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val entered = CompletableDeferred<Unit>()
            val draining = CompletableDeferred<Unit>()
            val releaseDrain = CompletableDeferred<Unit>()
            val closeEntered = CompletableDeferred<Unit>()
            val releaseClose = CompletableDeferred<Unit>()
            val backend = TestTtsBackend(
                backendId = "android.default.tts",
                speakBlock = {
                    entered.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            draining.complete(Unit)
                            releaseDrain.await()
                        }
                    }
                },
                closeBlock = {
                    closeEntered.complete(Unit)
                    releaseClose.await()
                },
            )
            BridgeFixture(tts = SpeechSynthesisClient(backend.backendId, listOf(backend))).use { fixture ->
                try {
                    val audio = TtsCapture()
                    fixture.onMain {
                        fixture.bridge.speak(
                            SpeechBridgeTtsRequest("hold", "en-US"),
                            SpeechBridgeSelection.Default,
                            audio,
                        )
                    }
                    entered.await()
                    fixture.hostScope.cancel()

                    val first = CloseCapture()
                    val second = CloseCapture()
                    fixture.onMain {
                        fixture.bridge.close(first)
                        fixture.bridge.close(second)
                    }
                    draining.await()
                    assertFalse(first.terminal.isCompleted)
                    assertFalse(second.terminal.isCompleted)
                    assertEquals(0, backend.closes.get())

                    val rejected = TtsCapture()
                    fixture.onMain {
                        fixture.bridge.speak(
                            SpeechBridgeTtsRequest("after-close", "en-US"),
                            SpeechBridgeSelection.Default,
                            rejected,
                        )
                    }
                    assertEquals(SpeechBridgeFailureCode.CLOSED, rejected.failure.await().code)

                    val rejectedRecognition = AsrCapture()
                    fixture.onMain {
                        fixture.bridge.recognize(
                            SpeechBridgeAsrRequest("", SpeechBridgeInput.AudioFile("opaque")),
                            SpeechBridgeSelection.Default,
                            rejectedRecognition,
                        )
                    }
                    assertEquals(SpeechBridgeFailureCode.CLOSED, rejectedRecognition.failure.await().code)

                    val rejectedQuery = BackendsCapture()
                    fixture.onMain { fixture.bridge.queryBackends(SpeechBridgeCapability.TTS, rejectedQuery) }
                    assertEquals(SpeechBridgeFailureCode.CLOSED, rejectedQuery.failure.await().code)

                    releaseDrain.complete(Unit)
                    closeEntered.await()
                    assertEquals("cancelled", audio.terminal.await())
                    assertFalse(first.terminal.isCompleted)
                    releaseClose.complete(Unit)
                    assertEquals("closed", first.terminal.await())
                    assertEquals("closed", second.terminal.await())
                    assertEquals(1, backend.closes.get())
                    first.assertOnTestMain()
                    second.assertOnTestMain()
                } finally {
                    releaseDrain.complete(Unit)
                    releaseClose.complete(Unit)
                }
            }
        }
    }

    @Test
    fun cancelQueryLeavesAudioOwnerOpen() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val queryEntered = CompletableDeferred<Unit>()
            val queryCancelled = CompletableDeferred<Unit>()
            val availabilityCalls = AtomicInteger()
            val backend = TestTtsBackend(
                backendId = "android.default.tts",
                availabilityBlock = {
                    if (availabilityCalls.incrementAndGet() == 1) {
                        queryEntered.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            queryCancelled.complete(Unit)
                        }
                    }
                    available(backendId)
                },
            )
            BridgeFixture(tts = SpeechSynthesisClient(backend.backendId, listOf(backend))).use { fixture ->
                val query = BackendsCapture()
                val queryTask = fixture.onMain { fixture.bridge.queryBackends(SpeechBridgeCapability.TTS, query) }
                queryEntered.await()

                val audio = TtsCapture()
                fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("parallel", "en-US"),
                        SpeechBridgeSelection.Default,
                        audio,
                    )
                }
                assertEquals(backend.backendId, audio.result.await().backendId)
                fixture.onMain { queryTask.cancel() }
                queryCancelled.await()
                assertEquals("cancelled", query.terminal.await())
                assertEquals(0, backend.closes.get())
                fixture.shutdown()
            }
        }
    }

    @Test
    fun backendErrorsRetainReasonWithoutUserText() = runBlocking<Unit> {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val unavailableBackend = TestAsrBackend("android.speech.recognizer") {
                throw SpeechBackendUnavailableException(
                    UnavailableReason.MISSING_ASSETS,
                    IllegalStateException("private transcript and /private/audio/path"),
                )
            }
            BridgeFixture(asr = SpeechRecognitionClient(unavailableBackend.backendId, listOf(unavailableBackend))).use { fixture ->
                val unavailableCapture = AsrCapture()
                fixture.onMain {
                    fixture.bridge.recognize(
                        SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone),
                        SpeechBridgeSelection.Default,
                        unavailableCapture,
                    )
                }
                val unavailableFailure = unavailableCapture.failure.await()
                assertEquals(SpeechBridgeFailureCode.BACKEND_UNAVAILABLE, unavailableFailure.code)
                assertEquals(UnavailableReason.MISSING_ASSETS.name, unavailableFailure.reason)
                assertFalse(unavailableFailure.toString().contains("transcript"))
                assertFalse(unavailableFailure.toString().contains("/private/audio"))
                fixture.shutdown()
            }

            val platformBackend = TestTtsBackend(
                backendId = "android.default.tts",
                speakBlock = {
                    throw RuntimeException("private TTS text and /private/output/path")
                },
            )
            BridgeFixture(tts = SpeechSynthesisClient(platformBackend.backendId, listOf(platformBackend))).use { fixture ->
                val capture = TtsCapture()
                fixture.onMain {
                    fixture.bridge.speak(
                        SpeechBridgeTtsRequest("secret user text", "en-US"),
                        SpeechBridgeSelection.Default,
                        capture,
                    )
                }
                val failure = capture.failure.await()
                assertEquals(SpeechBridgeFailureCode.PLATFORM, failure.code)
                assertNull(failure.reason)
                assertEquals("android.speech", failure.domain)
                assertEquals(1, failure.platformCode)
                assertFalse(failure.toString().contains("secret user text"))
                assertFalse(failure.toString().contains("/private/output"))
                capture.assertOnlyTerminal("failure")
                fixture.shutdown()
            }
        }
    }
}

private const val TEST_TIMEOUT_MILLIS = 5_000L
private const val TEST_MAIN_THREAD = "speech-bridge-test-main"

private fun available(backendId: String) = CapabilityAvailability(
    CapabilityStatus.AVAILABLE,
    backendId = backendId,
    minOS = "26",
    offline = true,
)

private fun unavailable(reason: UnavailableReason, backendId: String) = CapabilityAvailability(
    CapabilityStatus.UNAVAILABLE,
    reason = reason,
    backendId = backendId,
    minOS = "26",
    offline = true,
)

private class TestTtsBackend(
    override val backendId: String,
    private val availability: CapabilityAvailability = available(backendId),
    private val availabilityBlock: (suspend TestTtsBackend.() -> CapabilityAvailability)? = null,
    private val speakBlock: suspend TestTtsBackend.(SpeechSynthesisRequest) -> SpeechSynthesisResult = { request ->
        SpeechSynthesisResult(backendId, request.voiceId)
    },
    private val closeBlock: suspend TestTtsBackend.() -> Unit = {},
) : SpeechSynthesisBackend {
    override val capability = SpeechCapability.TTS
    val calls = AtomicInteger()
    val closes = AtomicInteger()

    override suspend fun availability(): CapabilityAvailability = availabilityBlock?.invoke(this) ?: availability

    override suspend fun speak(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        calls.incrementAndGet()
        return speakBlock(request)
    }

    override suspend fun close() {
        closes.incrementAndGet()
        closeBlock()
    }
}

private class TestAsrBackend(
    override val backendId: String,
    private val availability: CapabilityAvailability = available(backendId),
    private val recognizeBlock: suspend TestAsrBackend.(SpeechRecognitionRequest) -> SpeechRecognitionResult = {
        SpeechRecognitionResult(backendId, "recognized", 0.9f)
    },
) : SpeechRecognitionBackend {
    override val capability = SpeechCapability.ASR
    val calls = AtomicInteger()
    val closes = AtomicInteger()

    override suspend fun availability(): CapabilityAvailability = availability

    override suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult {
        calls.incrementAndGet()
        return recognizeBlock(request)
    }

    override suspend fun close() { closes.incrementAndGet() }
}

private class BridgeFixture(
    tts: SpeechSynthesisClient? = null,
    asr: SpeechRecognitionClient? = null,
) : AutoCloseable {
    private val ui = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, TEST_MAIN_THREAD).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    val hostScope = CoroutineScope(SupervisorJob() + ui)
    val bridge = AndroidSpeechBridge(tts, asr, hostScope)
    private var shutDown = false

    suspend fun <T> onMain(block: () -> T): T = withContext(ui) { block() }

    suspend fun shutdown() {
        if (shutDown) return
        val close = CloseCapture()
        onMain { bridge.close(close) }
        close.terminal.await()
        shutDown = true
    }

    override fun close() {
        if (!shutDown) runBlocking { shutdown() }
        hostScope.cancel()
        ui.close()
    }
}

private abstract class TerminalCapture {
    val terminal = CompletableDeferred<String>()
    private val terminals = mutableListOf<String>()
    private val callbackThreads = mutableListOf<String>()

    protected fun record(value: String) {
        terminals += value
        callbackThreads += Thread.currentThread().name
        terminal.complete(value)
    }

    fun assertOnlyTerminal(value: String) = assertEquals(listOf(value), terminals)
    fun assertOnTestMain() {
        assertTrue(callbackThreads.isNotEmpty())
        assertTrue(callbackThreads.all { it.substringBefore(" @") == TEST_MAIN_THREAD }, callbackThreads.toString())
    }
}

private class TtsCapture : TerminalCapture(), SpeechBridgeTtsCallback {
    val result = CompletableDeferred<SpeechBridgeTtsResult>()
    val failure = CompletableDeferred<SpeechBridgeFailure>()
    override fun onResult(synthesis: SpeechBridgeTtsResult) { result.complete(synthesis); record("result") }
    override fun onFailure(failure: SpeechBridgeFailure) { this.failure.complete(failure); record("failure") }
    override fun onCancelled() = record("cancelled")
}

private class AsrCapture : TerminalCapture(), SpeechBridgeAsrCallback {
    val result = CompletableDeferred<SpeechBridgeAsrResult>()
    val failure = CompletableDeferred<SpeechBridgeFailure>()
    override fun onResult(output: SpeechBridgeAsrResult) { result.complete(output); record("result") }
    override fun onFailure(failure: SpeechBridgeFailure) { this.failure.complete(failure); record("failure") }
    override fun onCancelled() = record("cancelled")
}

private class BackendsCapture : TerminalCapture(), SpeechBridgeBackendsCallback {
    val result = CompletableDeferred<List<SpeechBridgeBackend>>()
    val failure = CompletableDeferred<SpeechBridgeFailure>()
    override fun onResult(backends: List<SpeechBridgeBackend>) { result.complete(backends); record("result") }
    override fun onFailure(failure: SpeechBridgeFailure) { this.failure.complete(failure); record("failure") }
    override fun onCancelled() = record("cancelled")
}

private class CloseCapture : TerminalCapture(), SpeechBridgeCloseCallback {
    val failure = CompletableDeferred<SpeechBridgeFailure>()
    override fun onClosed() = record("closed")
    override fun onFailure(failure: SpeechBridgeFailure) { this.failure.complete(failure); record("failure") }
}
