package io.ondeviceai.systemasr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.speech.SpeechCapability
import io.ondeviceai.speech.SpeechRecognitionBackend
import io.ondeviceai.speech.SpeechRecognitionRequest
import io.ondeviceai.speech.SpeechRecognitionResult
import io.ondeviceai.speech.SpeechBackendUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Android on-device SpeechRecognizer backend. It never creates a network recognizer. */
class AndroidSpeechRecognizerBackend(
    context: Context,
    private val timeoutMillis: Long = RecognitionSession.DEFAULT_TIMEOUT_MILLIS
) : SpeechRecognitionBackend {
    override val backendId: String = "android.speech.recognizer"
    override val capability: SpeechCapability = SpeechCapability.ASR
    private val appContext = context.applicationContext
    private var closed = false
    private val session = RecognitionSession(
        factory = RecognitionEngineFactory { listener -> AndroidRecognitionEngine(appContext, listener) },
        timeoutMillis = timeoutMillis,
        dispatcher = Dispatchers.Main.immediate,
        backendId = backendId
    )

    override suspend fun availability(): CapabilityAvailability = withContext(Dispatchers.Main.immediate) {
        if (closed) {
            unavailable(UnavailableReason.CLOSED)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            unavailable(UnavailableReason.REQUIRES_OS_VERSION)
        } else if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            unavailable(UnavailableReason.PERMISSION_DENIED)
        } else if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            unavailable(UnavailableReason.UNSUPPORTED_DEVICE)
        } else {
            CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId, minOS = "31", offline = true)
        }
    }

    override suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult {
        val availability = availability()
        if (availability.status != CapabilityStatus.AVAILABLE) {
            throw SpeechBackendUnavailableException(availability.reason ?: UnavailableReason.UNSUPPORTED_DEVICE)
        }
        return try {
            session.recognize(request)
        } catch (error: AsrRecognitionException) {
            val reason = when (error.code) {
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> UnavailableReason.MISSING_ASSETS
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> UnavailableReason.UNSUPPORTED_DEVICE
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> UnavailableReason.PERMISSION_DENIED
                else -> throw error
            }
            throw SpeechBackendUnavailableException(reason, error)
        }
    }

    override suspend fun close() = withContext(Dispatchers.Main.immediate + NonCancellable) {
        closed = true
        session.close()
    }

    private fun unavailable(reason: UnavailableReason) = CapabilityAvailability(
        CapabilityStatus.UNAVAILABLE, reason = reason, backendId = backendId, minOS = "31", offline = true
    )

    @android.annotation.TargetApi(Build.VERSION_CODES.S)
    private class AndroidRecognitionEngine(
        context: Context,
        private val listener: RecognitionEngineListener
    ) : RecognitionEngine {
        private val recognizer: SpeechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        private val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        init {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = listener.onError(error)
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        ?.firstOrNull()
                        ?.takeIf { it.isFinite() && it in 0f..1f }
                    listener.onResult(text, confidence)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        override fun start(locale: String) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            recognizer.startListening(intent)
        }

        override fun cancel() { recognizer.cancel() }
        override fun destroy() { recognizer.destroy() }
    }
}
