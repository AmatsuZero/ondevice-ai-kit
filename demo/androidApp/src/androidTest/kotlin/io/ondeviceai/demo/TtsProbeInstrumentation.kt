package io.ondeviceai.demo

import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import io.ondeviceai.speech.SpeechSynthesisClient
import io.ondeviceai.speech.SpeechSynthesisRequest
import io.ondeviceai.systemtts.AndroidTextToSpeechBackend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Standalone device probe. This is not a production SDK backend. */
class TtsProbeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val test = Bundle().apply {
            putInt("numtests", 1)
            putInt("current", 1)
            putString("id", "TtsProbeInstrumentation")
            putString("class", this@TtsProbeInstrumentation.javaClass.name)
            putString("test", "synthesizesEnglishToFile")
        }
        sendStatus(1, test)
        var engine: TextToSpeech? = null
        val init = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        val output = File(targetContext.cacheDir, "odai-tts-probe.wav")
        try {
            runOnMainSync {
                engine = TextToSpeech(targetContext) { status ->
                    initStatus = status
                    init.countDown()
                }
            }
            check(init.await(15, TimeUnit.SECONDS)) { "TTS initialization timed out" }
            check(initStatus == TextToSpeech.SUCCESS) { "TTS initialization failed: $initStatus" }
            val tts = checkNotNull(engine)
            result.putString("engine", tts.defaultEngine)
            result.putInt("voiceCount", tts.voices?.size ?: 0)
            val languageStatus = tts.setLanguage(Locale.US)
            result.putInt("languageStatus", languageStatus)
            check(languageStatus >= TextToSpeech.LANG_AVAILABLE) { "en-US unavailable: $languageStatus" }
            val done = CountDownLatch(1)
            var synthesisError: String? = null
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) { done.countDown() }
                override fun onError(utteranceId: String) {
                    synthesisError = "synthesis failed"
                    done.countDown()
                }
            })
            val queueStatus = tts.synthesizeToFile("OnDevice AI Kit Android speech probe", Bundle(), output, "odai-probe")
            check(queueStatus == TextToSpeech.SUCCESS) { "TTS queue failed: $queueStatus" }
            check(done.await(20, TimeUnit.SECONDS)) { "TTS synthesis timed out" }
            check(synthesisError == null) { synthesisError ?: "TTS synthesis failed" }
            check(output.length() > 44) { "TTS output is empty" }
            result.putLong("outputBytes", output.length())
            val sdkBackend = runBlocking(Dispatchers.Default) { AndroidTextToSpeechBackend.create(targetContext) }
            val sdkProbe = runBlocking(Dispatchers.Default) {
                val client = SpeechSynthesisClient(sdkBackend.backendId, listOf(sdkBackend))
                Log.i("ODAI_TTS_PROBE", "normal-start")
                val normal = client.speak(SpeechSynthesisRequest("OnDevice AI Kit Android SDK speech probe", "en-US"))
                Log.i("ODAI_TTS_PROBE", "normal-done")
                val pending = async {
                    client.speak(SpeechSynthesisRequest(
                        "This Android SDK speech task is intentionally interrupted before playback completes. " +
                            "The next request must start after the engine stops the cancelled utterance.", "en-US"))
                }
                delay(100)
                Log.i("ODAI_TTS_PROBE", "cancel-request completed=${pending.isCompleted}")
                val cancellationStatus = if (pending.isCompleted) {
                    pending.await()
                    "completed-before-cancel"
                } else {
                    withTimeout(10_000) { pending.cancelAndJoin() }
                    Log.i("ODAI_TTS_PROBE", "cancel-joined")
                    withTimeout(15_000) { client.speak(SpeechSynthesisRequest("Speech after cancellation", "en-US")) }
                    Log.i("ODAI_TTS_PROBE", "followup-done")
                    "cancelled-followup-success"
                }
                val afterCloseProbe = client
                client.close()
                val afterCloseStatus = runCatching {
                    afterCloseProbe.speak(SpeechSynthesisRequest("after close", "en-US"))
                }.fold({ "unexpected-success" }, { if (it.message == "closed") "closed" else it::class.simpleName ?: "unknown" })
                Triple(normal, cancellationStatus, afterCloseStatus)
            }
            result.putString("sdkBackend", sdkProbe.first.backendId)
            result.putString("sdkVoice", sdkProbe.first.voiceId)
            result.putString("sdkStatus", "success")
            result.putString("sdkCancellationStatus", sdkProbe.second)
            result.putString("sdkAfterCloseStatus", sdkProbe.third)
            check(sdkProbe.third == "closed") { "SDK close not enforced: ${sdkProbe.third}" }
            check(sdkProbe.second == "cancelled-followup-success") { "SDK cancellation not verified: ${sdkProbe.second}" }
            result.putString("asrOnDeviceAvailability", if (Build.VERSION.SDK_INT >= 31) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(targetContext).toString()
            } else "requiresApi31")
            result.putString("probeStatus", "pass")
            sendStatus(0, Bundle(test).apply { putString("stream", "TTS probe passed") })
        } catch (error: Throwable) {
            result.putString("probeStatus", "fail")
            result.putString("probeError", error.toString())
            sendStatus(-2, Bundle(test).apply { putString("stack", error.stackTraceToString()) })
        } finally {
            engine?.shutdown()
            output.delete()
            finish(-1, result)
        }
    }
}
