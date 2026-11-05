package io.ondeviceai.speechconsumer

import android.content.Context
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import io.ondeviceai.sherpaasr.SherpaWhisperModel
import io.ondeviceai.speech.SpeechRecognitionClient
import io.ondeviceai.speech.SpeechRecognitionResult
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The host supplies verified model files and owns microphone permission. */
object SherpaAsrConsumer {
    suspend fun recognize(context: Context, modelDirectory: File, locale: String): SpeechRecognitionResult {
        val backend = SherpaAsrBackend(context, SherpaWhisperModel(modelDirectory))
        val client = try {
            SpeechRecognitionClient(backend.backendId, listOf(backend))
        } catch (error: Throwable) {
            withContext(NonCancellable) { backend.close() }
            throw error
        }
        return try {
            client.recognize(
                SpeechContractConsumer.recognitionRequest(locale),
                SpeechContractConsumer.explicitSelection(backend.backendId),
            )
        } finally {
            withContext(NonCancellable) { client.close() }
        }
    }
}
