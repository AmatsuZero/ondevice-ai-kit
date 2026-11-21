import Foundation
import ODAIKitCore

/// Input kinds that an ASR backend can admit. This metadata describes capability;
/// permission and current device availability remain runtime admission checks.
public enum SpeechRecognitionInputKind: String, Sendable, Equatable {
    case audioFile
    case microphone
}

public enum SpeechRecognitionInputError: Error, Sendable, Equatable {
    case unsupportedInput
}

/// A host-authorized microphone recognition request without exposing AVFoundation
/// objects or an audio buffer to the shared contract layer.
public struct SpeechMicrophoneRecognitionRequest: Sendable, Equatable {
    public let locale: String

    public init(locale: String) throws {
        let trimmed = locale.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw KitError.invalidInput("empty speech locale") }
        self.locale = Locale.canonicalIdentifier(from: trimmed.replacingOccurrences(of: "-", with: "_"))
    }
}

/// One admitted microphone operation. Finishing input asks the recognizer for a
/// final result; cancel discards the current result. Both are idempotent.
public protocol SpeechMicrophoneRecognitionTask: Sendable {
    func result() async throws -> SpeechRecognitionResult
    func finishInput() async
    func cancel() async
}

/// Optional capability implemented by ASR backends that can consume live audio.
/// Existing file-only backends remain source compatible through the default input
/// metadata on SpeechRecognitionBackend.
public protocol SpeechMicrophoneRecognitionBackend: SpeechRecognitionBackend {
    func startMicrophone(_ request: SpeechMicrophoneRecognitionRequest) async throws
        -> any SpeechMicrophoneRecognitionTask
}
