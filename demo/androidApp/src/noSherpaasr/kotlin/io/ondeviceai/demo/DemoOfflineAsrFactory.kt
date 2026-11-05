package io.ondeviceai.demo

import android.content.Context
import io.ondeviceai.speech.SpeechRecognitionBackend

internal object DemoOfflineAsrFactory {
    val defaultBackendId: String? = null
    suspend fun create(context: Context): List<SpeechRecognitionBackend> = emptyList()
}
