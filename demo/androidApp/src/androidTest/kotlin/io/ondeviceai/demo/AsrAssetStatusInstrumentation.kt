package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Resource metadata only: no Activity, startListening, model download or permission request. */
class AsrAssetStatusInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle().apply {
            putString("locale", "en-US")
            putString("recognition", "not-run")
            putBoolean("microphoneRequested", false)
            putBoolean("downloadRequested", false)
            putBoolean("activityStarted", false)
            putInt("apiLevel", Build.VERSION.SDK_INT)
        }
        if (Build.VERSION.SDK_INT < 33) {
            result.putString("status", "requires-api-33")
            finish(-1, result)
            return
        }
        query(result)
        finish(-1, result)
    }

    @android.annotation.TargetApi(33)
    private fun query(result: Bundle) {
        val completion = CountDownLatch(1)
        val response = AtomicReference<Bundle?>()
        var recognizer: SpeechRecognizer? = null
        try {
            runOnMainSync {
                try {
                    if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(targetContext)) {
                        response.set(Bundle().apply { putString("status", "on-device-service-unavailable") })
                        completion.countDown()
                    } else {
                        val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(targetContext)
                        recognizer = engine
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        engine.checkRecognitionSupport(intent, targetContext.mainExecutor,
                            object : RecognitionSupportCallback {
                                override fun onSupportResult(support: RecognitionSupport) {
                                    fun List<String>.hasEnglish() = any { it.replace('_', '-').equals("en-US", ignoreCase = true) }
                                    val installed = support.installedOnDeviceLanguages.hasEnglish()
                                    val pending = support.pendingOnDeviceLanguages.hasEnglish()
                                    val downloadable = support.supportedOnDeviceLanguages.hasEnglish()
                                    response.set(Bundle().apply {
                                        putString("status", "observed")
                                        putBoolean("englishInstalled", installed)
                                        putBoolean("englishPending", pending)
                                        putBoolean("englishDownloadable", downloadable)
                                        putString("languageState", when {
                                            installed -> "INSTALLED"
                                            pending -> "PENDING"
                                            downloadable -> "DOWNLOADABLE"
                                            else -> "NOT_REPORTED"
                                        })
                                        putInt("installedLanguageCount", support.installedOnDeviceLanguages.size)
                                        putInt("pendingLanguageCount", support.pendingOnDeviceLanguages.size)
                                        putInt("downloadableLanguageCount", support.supportedOnDeviceLanguages.size)
                                    })
                                    completion.countDown()
                                }
                                override fun onError(error: Int) {
                                    response.set(Bundle().apply {
                                        putString("status", "support-query-error")
                                        putInt("supportError", error)
                                    })
                                    completion.countDown()
                                }
                            })
                    }
                } catch (_: Exception) {
                    response.set(Bundle().apply { putString("status", "probe-error") })
                    completion.countDown()
                }
            }
            if (completion.await(30, TimeUnit.SECONDS)) {
                result.putAll(checkNotNull(response.get()))
            } else result.putString("status", "support-query-timeout")
        } catch (_: Exception) {
            result.putString("status", "probe-error")
        } finally {
            runOnMainSync {
                try {
                    recognizer?.destroy()
                    result.putBoolean("recognizerDestroyed", true)
                } catch (_: Exception) {
                    result.putBoolean("recognizerDestroyed", false)
                    result.putString("status", "cleanup-error")
                }
            }
        }
    }
}
