package io.ondeviceai.speechconsumer

import android.content.Context
import io.ondeviceai.speech.SpeechRecognitionClient
import io.ondeviceai.speech.SpeechRecognitionResult
import io.ondeviceai.systemasr.AndroidSpeechRecognizerBackend
import io.ondeviceai.systemasr.AndroidAsrResources
import io.ondeviceai.systemasr.AsrAssetStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** System ASR consumer; the host owns RECORD_AUDIO permission and runtime policy. */
object SystemAsrConsumer {
    /** API33+ resource metadata query. Does not record or request a model download. */
    suspend fun languageResources(context: Context, locale: String): AsrAssetStatus =
        AndroidAsrResources(context).check(locale)

    /** API34+; the host must obtain explicit download confirmation before calling.
     * Cancellation detaches monitoring and does not promise to stop the system download.
     */
    suspend fun downloadLanguageResources(
        context: Context, locale: String, onProgress: (Int) -> Unit,
    ): AsrAssetStatus = AndroidAsrResources(context).download(locale, onProgress)

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
