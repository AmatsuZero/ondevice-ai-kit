package io.ondeviceai.demo

import android.Manifest
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Operator-only framework diagnostic. No SDK/UI acceptance claim and no audio/text persistence. */
class AsrSingleAttemptInstrumentation : Instrumentation() {
    private var authorized = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        authorized = arguments?.getString("authorizedSingleAttempt") == "true"
        start()
    }

    override fun onStart() {
        val result = Bundle().apply {
            putString("locale", "en-US")
            putString("scope", "framework-on-device-only")
            putString("status", "not-started")
            putInt("startCount", 0)
            putInt("deadlineMillis", 9_500)
            putBoolean("audioPersisted", false)
            putBoolean("transcriptPersisted", false)
            putBoolean("recognizerDestroyed", false)
        }
        if (!authorized || Build.VERSION.SDK_INT < 31 ||
            targetContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            result.putString("status", "precondition-rejected")
            finish(-1, result)
            return
        }
        try {
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            result.putBoolean("activityStarted", true)
            observe(result)
        } catch (error: Exception) {
            result.putString("status", "probe-error")
            result.putString("errorClass", error.javaClass.name)
        }
        finish(-1, result)
    }

    @android.annotation.TargetApi(31)
    private fun observe(result: Bundle) {
        val completed = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        var engine: SpeechRecognizer? = null
        var terminal = false
        var startedAt = 0L
        lateinit var deadline: Runnable

        fun end(status: String) {
            if (terminal) return
            terminal = true
            handler.removeCallbacks(deadline)
            result.putString("status", status)
            try {
                engine?.cancel()
                result.putBoolean("cancelReturned", true)
            } catch (error: Exception) {
                result.putBoolean("cancelReturned", false)
                result.putString("cancelErrorClass", error.javaClass.name)
            }
            try {
                engine?.destroy()
                result.putBoolean("recognizerDestroyed", engine != null)
            } catch (error: Exception) {
                result.putString("status", "cleanup-error")
                result.putString("destroyErrorClass", error.javaClass.name)
            }
            if (startedAt != 0L) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                result.putLong("startToDestroyMillis", elapsed)
                result.putBoolean("withinAuthorizedLimit", elapsed <= 10_000)
            }
            engine = null
            completed.countDown()
        }
        deadline = Runnable { end("deadline-cancelled") }
        try {
            runOnMainSync {
                try {
                    if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(targetContext)) {
                        end("on-device-service-unavailable")
                        return@runOnMainSync
                    }
                    engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(targetContext)
                    engine!!.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { if (!terminal) result.putBoolean("readyForSpeech", true) }
                        override fun onBeginningOfSpeech() { if (!terminal) result.putBoolean("speechBegan", true) }
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() { if (!terminal) result.putBoolean("speechEnded", true) }
                        override fun onError(error: Int) {
                            if (terminal) return
                            result.putInt("platformError", error)
                            end("platform-error")
                        }
                        override fun onResults(results: Bundle?) {
                            if (terminal) return
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                            result.putBoolean("nonEmptyResult", text.isNotBlank())
                            result.putBoolean("expectedSentenceMatched", text.lowercase().replace(Regex("[^a-z ]"), "")
                                .trim().replace(Regex(" +"), " ") == "this is an on device speech test")
                            end(if (text.isBlank()) "empty-result" else "transcribed")
                        }
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    result.putInt("startCount", 1)
                    startedAt = SystemClock.elapsedRealtime()
                    // Independent of the main queue: a stalled cancel/destroy kills this
                    // instrumented target process, closing its recognizer Binder client.
                    // An attempt ending this way has no success receipt and is inconclusive.
                    Thread({
                        if (!completed.await(9_800, TimeUnit.MILLISECONDS)) Process.killProcess(Process.myPid())
                    }, "asr-authorized-deadline").apply { isDaemon = true; start() }
                    handler.postDelayed(deadline, 9_500)
                    engine!!.startListening(intent)
                } catch (error: Exception) {
                    result.putString("errorClass", error.javaClass.name)
                    end("probe-error")
                }
            }
            if (!completed.await(12, TimeUnit.SECONDS)) runOnMainSync { end("watchdog-cleanup") }
        } finally {
            runOnMainSync { if (!terminal) end("finally-cleanup") }
        }
    }
}
