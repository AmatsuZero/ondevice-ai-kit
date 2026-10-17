package io.ondeviceai.speechconsumer

import android.content.Context
import io.ondeviceai.speech.SpeechSynthesisClient
import io.ondeviceai.speech.SpeechSynthesisResult
import io.ondeviceai.systemtts.AndroidTextToSpeechBackend
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** System TTS consumer; engine creation is explicit and never happens at module init. */
object SystemTtsConsumer {
    suspend fun speak(context: Context, text: String, locale: String): SpeechSynthesisResult {
        val backend = AndroidTextToSpeechBackend.create(context)
        val client = try {
            SpeechSynthesisClient(backend.backendId, listOf(backend))
        } catch (error: Throwable) {
            withContext(NonCancellable) { backend.close() }
            throw error
        }

        return try {
            client.speak(
                SpeechContractConsumer.synthesisRequest(text, locale),
                SpeechContractConsumer.explicitSelection(backend.backendId)
            )
        } finally {
            withContext(NonCancellable) { client.close() }
        }
    }
}
