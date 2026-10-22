import Foundation
import ODAIKitCore

public struct MultimodalRequest: Sendable, Equatable {
    public let imageFile: URL
    public let prompt: String
    public let maxOutputTokens: Int

    /// Validates values only. The backend validates and reads the image during admission.
    public init(imageFile: URL, prompt: String, maxOutputTokens: Int = 128) throws {
        guard imageFile.isFileURL else { throw MultimodalClientError.invalidInput("image must be a file URL") }
        guard !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw MultimodalClientError.invalidInput("prompt must not be blank")
        }
        guard maxOutputTokens > 0 else { throw MultimodalClientError.invalidInput("output budget must be positive") }
        self.imageFile = imageFile
        self.prompt = prompt
        self.maxOutputTokens = maxOutputTokens
    }
}

public struct MultimodalModelIdentity: Sendable, Equatable {
    public let modelId: String
    public let modelRevision: String
    public let runtimeRevision: String

    public init(modelId: String, modelRevision: String, runtimeRevision: String) throws {
        guard [modelId, modelRevision, runtimeRevision].allSatisfy({
            !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }) else { throw MultimodalClientError.invalidInput("model identity fields must not be blank") }
        self.modelId = modelId
        self.modelRevision = modelRevision
        self.runtimeRevision = runtimeRevision
    }
}

/// A cumulative text snapshot. Sequence is an update ordinal, not a token count.
public struct MultimodalTextUpdate: Sendable, Equatable {
    public let sequence: Int
    public let text: String

    public init(sequence: Int, text: String) throws {
        guard sequence >= 0 else { throw MultimodalClientError.invalidInput("sequence must be non-negative") }
        self.sequence = sequence
        self.text = text
    }
}

/// An immutable value; closing the client does not invalidate a returned output.
public struct MultimodalTextOutput: Sendable, Equatable {
    public let text: String
    public let model: MultimodalModelIdentity

    public init(text: String, model: MultimodalModelIdentity) throws {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw MultimodalClientError.invalidInput("output text must not be blank")
        }
        self.text = text
        self.model = model
    }
}

public struct MultimodalGenerationResult: Sendable, Equatable {
    public let backendId: String
    public let output: MultimodalTextOutput

    public init(backendId: String, output: MultimodalTextOutput) throws {
        guard !backendId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw MultimodalClientError.invalidInput("backend ID must not be blank")
        }
        self.backendId = backendId
        self.output = output
    }
}

public enum MultimodalBackendSelection: Sendable, Equatable { case `default`, explicit(String) }

/// Owned by its client. Implementations must honor the request budget and make close nonthrowing.
public protocol MultimodalGenerationBackend: Sendable {
    var backendId: String { get }
    func availability() async -> CapabilityAvailability
    func generate(_ request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput
    func drain() async
    func close() async
}

public struct MultimodalBackendDescriptor: Sendable, Equatable {
    public let backendId: String
    public let availability: CapabilityAvailability

    public init(backendId: String, availability: CapabilityAvailability) {
        self.backendId = backendId
        self.availability = availability
    }
}

public enum MultimodalClientError: Error, Equatable, Sendable {
    case invalidInput(String)
    case invalidRegistration(String)
    case backendNotRegistered(String)
    case backendUnavailable(String, UnavailableReason)
    case busy
    case closed
    case cancelled
}
