import Foundation
import ODAIKitCore
import ODAIKitSpeech

// This file compiles with Core + Speech only; it does not construct a platform backend.
public func makeSpeechRequest(text: String, locale: String) -> SpeechSynthesisRequest {
    SpeechSynthesisRequest(text: text, locale: locale)
}

public func selectSpeechBackend(_ id: String) -> SpeechBackendSelection {
    .explicit(id)
}

public func makeRecognitionRequest(locale: String, file: URL) throws -> SpeechRecognitionRequest {
    try SpeechRecognitionRequest(locale: locale, audioFile: file)
}

public func makeRecognitionClient(defaultBackendId: String,
                                  backends: [any SpeechRecognitionBackend]) throws -> SpeechRecognitionClient {
    try SpeechRecognitionClient(defaultBackendId: defaultBackendId, backends: backends)
}

public func recognizedText(_ result: SpeechRecognitionResult) -> String {
    result.transcript
}
