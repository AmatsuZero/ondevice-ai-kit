package io.ondeviceai.speechconsumer

import android.content.Context
import io.ondeviceai.speech.SpeechRecognitionClient
import io.ondeviceai.speech.SpeechRecognitionResult
import io.ondeviceai.systemasr.AndroidSpeechRecognizerBackend
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** System ASR consumer; the host owns RECORD_AUDIO permission and runtime policy. */
object SystemAsrConsumer {
    suspend fun recognize(context: Context, locale: String): SpeechRecognitionResult {
        val backend = AndroidSpeechRecognizerBackend(context)
        val client = try {
            SpeechRecognitionClient(backend.backendId, listOf(backend))
        } catch (error: Throwable) {
            withContext(NonCancellable) { backend.close() }
            throw error
        }

        return try {
            client.recognize(
                SpeechContractConsumer.recognitionRequest(locale),
                SpeechContractConsumer.explicitSelection(backend.backendId)
            )
        } finally {
            withContext(NonCancellable) { client.close() }
        }
    }
}
