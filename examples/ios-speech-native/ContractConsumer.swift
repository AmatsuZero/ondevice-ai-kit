import ODAIKitSpeech

// This file compiles with Core + Speech only; it does not construct a platform backend.
public func makeSpeechRequest(text: String, locale: String) -> SpeechSynthesisRequest {
    SpeechSynthesisRequest(text: text, locale: locale)
}

public func selectSpeechBackend(_ id: String) -> SpeechBackendSelection {
    .explicit(id)
}
