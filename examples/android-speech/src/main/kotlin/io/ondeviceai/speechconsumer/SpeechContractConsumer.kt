package io.ondeviceai.speechconsumer

import io.ondeviceai.speech.SpeechBackendSelection
import io.ondeviceai.speech.SpeechRecognitionRequest
import io.ondeviceai.speech.SpeechSynthesisRequest

/** Compile/link consumer for the provider-independent speech contract. */
object SpeechContractConsumer {
    fun synthesisRequest(text: String, locale: String) = SpeechSynthesisRequest(text, locale)

    fun recognitionRequest(locale: String) = SpeechRecognitionRequest(locale)

    fun explicitSelection(backendId: String) = SpeechBackendSelection.explicit(backendId)
}
