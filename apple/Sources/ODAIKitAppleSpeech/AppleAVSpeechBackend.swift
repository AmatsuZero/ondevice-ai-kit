import AVFoundation
import ODAIKitCore
import ODAIKitSpeech

@MainActor
public final class AppleAVSpeechBackend: NSObject, AVSpeechSynthesizerDelegate, SpeechSynthesisBackend {
    public nonisolated let backendId = "apple.avspeech"
    public nonisolated let capability: SpeechCapability = .tts
    private let synthesizer = AVSpeechSynthesizer()
    private var continuation: CheckedContinuation<SpeechSynthesisResult, Error>?
    private var activeVoiceId: String?
    private var activeUtterance: AVSpeechUtterance?
    private var closed = false

    public override init() {
        super.init()
        synthesizer.delegate = self
    }

    public func availability() -> CapabilityAvailability {
        if closed { return CapabilityAvailability(status: .unavailable, reason: .closed, backendId: backendId, minOS: "15.0", offline: false) }
        let available = !AVSpeechSynthesisVoice.speechVoices().isEmpty
        return CapabilityAvailability(status: available ? .available : .unavailable,
                                      reason: available ? nil : .missingAssets,
                                      backendId: backendId, minOS: "15.0", offline: false)
    }

    public func speak(_ request: SpeechSynthesisRequest) async throws -> SpeechSynthesisResult {
        guard !closed else { throw KitError.closed }
        guard continuation == nil else { throw KitError.busy }
        guard !request.text.isEmpty else { throw KitError.invalidInput("empty text") }
        let voices = AVSpeechSynthesisVoice.speechVoices()
        guard let voice = voices.first(where: { $0.identifier == request.voiceId && $0.language == request.locale })
            ?? voices.first(where: { $0.language == request.locale }) else {
            throw SpeechClientError.backendUnavailable(capability: .tts, backendId: backendId, reason: .missingAssets)
        }
        let utterance = AVSpeechUtterance(string: request.text)
        utterance.voice = voice
        activeVoiceId = voice.identifier
        activeUtterance = utterance
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<SpeechSynthesisResult, Error>) in
                self.continuation = continuation
                self.synthesizer.speak(utterance)
            }
        }, onCancel: {
            Task { @MainActor [weak self] in
                guard let self else { return }
                guard self.activeUtterance === utterance else { return }
                self.synthesizer.stopSpeaking(at: .immediate)
                self.finish(.failure(KitError.cancelled))
            }
        })
    }

    public func close() async {
        guard !closed else { return }
        closed = true
        synthesizer.stopSpeaking(at: .immediate)
        finish(.failure(KitError.closed))
    }

    private func finish(_ result: Result<SpeechSynthesisResult, Error>) {
        guard let continuation else { return }
        self.continuation = nil
        activeVoiceId = nil
        activeUtterance = nil
        switch result {
        case .success(let value): continuation.resume(returning: value)
        case .failure(let error): continuation.resume(throwing: error)
        }
    }

    @objc nonisolated public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.activeUtterance === utterance else { return }
            self.finish(.success(SpeechSynthesisResult(backendId: self.backendId, voiceId: self.activeVoiceId)))
        }
    }

    @objc nonisolated public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.activeUtterance === utterance else { return }
            self.finish(.failure(KitError.cancelled))
        }
    }
}
