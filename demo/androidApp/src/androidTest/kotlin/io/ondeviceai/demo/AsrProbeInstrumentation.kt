package io.ondeviceai.demo

import android.app.Instrumentation
import android.os.Bundle
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.ClosedException
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.speech.SpeechRecognitionClient
import io.ondeviceai.speech.SpeechRecognitionRequest
import io.ondeviceai.speech.SpeechClientError
import io.ondeviceai.systemasr.AndroidSpeechRecognizerBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Device lifecycle probe; never labels availability or microphone cancellation as transcription. */
class AsrProbeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val test = Bundle().apply {
            putInt("numtests", 1)
            putInt("current", 1)
            putString("id", "AsrProbeInstrumentation")
            putString("class", this@AsrProbeInstrumentation.javaClass.name)
            putString("test", "onDeviceRecognitionLifecycle")
        }
        sendStatus(1, test)
        try {
            runBlocking(Dispatchers.Default) { supervisorScope {
                val backend = AndroidSpeechRecognizerBackend(targetContext)
                val client = SpeechRecognitionClient(backend.backendId, listOf(backend))
                try {
                    val availability = backend.availability()
                    result.putString("backendId", backend.backendId)
                    result.putString("availability", availability.status.name)
                    result.putString("reason", availability.reason?.name)
                    result.putString("transcription", "not-run")
                    result.putString("networkBehavior", "on-device-factory; network not independently measured")
                    if (availability.status != CapabilityStatus.AVAILABLE) {
                        check(availability.reason in setOf(
                            UnavailableReason.PERMISSION_DENIED,
                            UnavailableReason.REQUIRES_OS_VERSION,
                            UnavailableReason.UNSUPPORTED_DEVICE,
                            UnavailableReason.MISSING_ASSETS
                        )) { "unexpected unavailable reason" }
                        result.putString("probeStatus", "unavailable")
                        sendStatus(-3, Bundle(test).apply { putString("stream", "ASR unavailable: ${availability.reason}") })
                        return@supervisorScope
                    }
                    repeat(2) { index ->
                        val task = async { client.recognize(SpeechRecognitionRequest("en-US")) }
                        delay(250)
                        if (task.isCompleted) {
                            val earlyOutcome = runCatching { task.await() }
                            val unavailable = earlyOutcome.exceptionOrNull() as? SpeechClientError.BackendUnavailable
                                ?: error("ASR ended before cancellation: ${earlyOutcome.exceptionOrNull() ?: "result-before-cancel"}")
                            result.putString("transcription", "not-run")
                            result.putString("earlyCompletion", unavailable.cause?.cause?.toString() ?: unavailable.toString())
                            result.putString("probeStatus", "unavailable")
                            result.putString("reason", unavailable.reason.name)
                            sendStatus(-3, Bundle(test).apply {
                                putString("stream", "ASR service ended before cancellation; transcription not measured")
                            })
                            return@supervisorScope
                        }
                        withTimeout(10_000) { task.cancelAndJoin() }
                        check(task.isCancelled) { "ASR task did not cancel" }
                        result.putString("cancellation${index + 1}", "cancelled")
                    }
                    val active = async { client.recognize(SpeechRecognitionRequest("en-US")) }
                    delay(250)
                    check(!active.isCompleted) { "ASR ended before close check" }
                    withTimeout(10_000) { client.close() }
                    val closeResult = runCatching { withTimeout(10_000) { active.await() } }
                    check(closeResult.exceptionOrNull() is ClosedException) { "active ASR close result: $closeResult" }
                    result.putString("activeClose", "closed")
                    client.close()
                    val after = backend.availability()
                    check(after.reason == UnavailableReason.CLOSED) { "backend not marked closed" }
                    result.putString("afterClose", "closed")
                    result.putString("probeStatus", "pass")
                    sendStatus(0, Bundle(test).apply { putString("stream", "ASR lifecycle passed; transcription not run") })
                } finally {
                    withContext(NonCancellable) { client.close() }
                }
            } }
        } catch (error: Throwable) {
            result.putString("probeStatus", "fail")
            result.putString("probeError", error.toString())
            sendStatus(-2, Bundle(test).apply { putString("stack", error.stackTraceToString()) })
        } finally {
            finish(-1, result)
        }
    }
}
