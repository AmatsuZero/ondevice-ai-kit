import Foundation
import ODAIKitCore

public enum SpeechCapability: String, Sendable, Equatable { case asr, tts }
public enum SpeechBackendSelection: Sendable, Equatable { case `default`, explicit(String) }

public protocol SpeechBackend: Sendable {
    var backendId: String { get }
    var capability: SpeechCapability { get }
    func availability() async -> CapabilityAvailability
    func close() async
}

/// A host-owned local audio file. Keep it unchanged until recognition returns, or,
/// after caller cancellation/timeout, until `drain()` or `close()` finishes.
public struct SpeechRecognitionRequest: Sendable, Equatable {
    public let locale: String
    public let audioFile: URL

    public init(locale: String, audioFile: URL) throws {
        let trimmed = locale.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw KitError.invalidInput("empty speech locale") }
        guard audioFile.isFileURL else { throw KitError.invalidInput("audio must be a file URL") }
        self.locale = Locale.canonicalIdentifier(from: trimmed.replacingOccurrences(of: "-", with: "_"))
        self.audioFile = audioFile
    }
}

/// One final transcript; partial transcript streaming is outside this file-only contract.
public struct SpeechRecognitionResult: Sendable, Equatable {
    public let backendId: String
    public let transcript: String
    public let confidence: Float?

    public init(backendId: String, transcript: String, confidence: Float? = nil) {
        self.backendId = backendId
        self.transcript = transcript
        self.confidence = confidence
    }
}

public enum SpeechRecognitionError: Error, Equatable, Sendable {
    case timedOut
    case noResult
    case platform(domain: String, code: Int)
}

public protocol SpeechRecognitionBackend: SpeechBackend {
    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult
    /// Wait for the currently admitted platform operation to release its input.
    /// Does not cancel work or close admission; callers coordinate new requests.
    func drain() async
}

/// Typed file recognition facade over the capability-scoped backend registry.
public actor SpeechRecognitionClient {
    private let registry: SpeechClient
    private let backends: [String: any SpeechRecognitionBackend]
    private var closed = false
    private var closeTask: Task<Void, Never>?

    public init(defaultBackendId: String, backends: [any SpeechRecognitionBackend]) throws {
        registry = try SpeechClient(capability: .asr, defaultBackendId: defaultBackendId,
                                   registrations: backends.map { .init(capability: .asr, backend: $0) })
        self.backends = Dictionary(uniqueKeysWithValues: backends.map { ($0.backendId, $0) })
    }

    public func recognize(_ request: SpeechRecognitionRequest,
                          backend: SpeechBackendSelection = .default) async throws -> SpeechRecognitionResult {
        guard !closed else { throw SpeechClientError.closed }
        let selected = try await registry.select(backend)
        guard !closed else { throw SpeechClientError.closed }
        guard let implementation = backends[selected.backendId] else {
            throw SpeechClientError.backendNotRegistered(capability: .asr, backendId: selected.backendId)
        }
        return try await implementation.recognize(request)
    }

    public func listBackends() async throws -> [SpeechBackendDescriptor] {
        guard !closed else { throw SpeechClientError.closed }
        let descriptors = await registry.listBackends()
        try Task.checkCancellation()
        guard !closed else { throw SpeechClientError.closed }
        return descriptors
    }

    public func close() async {
        closed = true
        if closeTask == nil {
            closeTask = Task { await registry.close() }
        }
        await closeTask?.value
    }

    /// Wait for current backend leases without closing the client. Do not start a new
    /// request using the same input while draining if you intend to delete that input.
    public func drain() async {
        for implementation in backends.values { await implementation.drain() }
    }
}

public struct SpeechBackendRegistration: Sendable {
    public let capability: SpeechCapability
    public let backend: any SpeechBackend
    public init(capability: SpeechCapability, backend: any SpeechBackend) {
        self.capability = capability
        self.backend = backend
    }
}

public struct SpeechBackendDescriptor: Sendable, Equatable {
    public let capability: SpeechCapability
    public let backendId: String
    public let availability: CapabilityAvailability
}

public enum SpeechClientError: Error, Equatable, Sendable {
    case invalidRegistration(String)
    case backendNotRegistered(capability: SpeechCapability, backendId: String)
    case backendUnavailable(capability: SpeechCapability, backendId: String, reason: UnavailableReason)
    case closed
}

/// A capability-scoped registry. Task/audio APIs are defined with concrete backends later.
public actor SpeechClient {
    private let capability: SpeechCapability
    private let defaultBackendId: String
    private let registrations: [String: any SpeechBackend]
    private var closed = false
    private var closeTask: Task<Void, Never>?

    public init(capability: SpeechCapability, defaultBackendId: String,
                registrations: [SpeechBackendRegistration]) throws {
        guard !defaultBackendId.isEmpty else { throw SpeechClientError.invalidRegistration("empty default backend") }
        var table: [String: any SpeechBackend] = [:]
        for item in registrations {
            guard item.capability == capability, item.backend.capability == capability, !item.backend.backendId.isEmpty else {
                throw SpeechClientError.invalidRegistration("invalid capability or backend")
            }
            guard table[item.backend.backendId] == nil else {
                throw SpeechClientError.invalidRegistration("duplicate backend: \(item.backend.backendId)")
            }
            table[item.backend.backendId] = item.backend
        }
        guard table[defaultBackendId] != nil else {
            throw SpeechClientError.invalidRegistration("default backend not registered")
        }
        self.capability = capability
        self.defaultBackendId = defaultBackendId
        self.registrations = table
    }

    public func listBackends() async -> [SpeechBackendDescriptor] {
        await withTaskGroup(of: SpeechBackendDescriptor.self, returning: [SpeechBackendDescriptor].self) { group in
            for backend in registrations.values {
                group.addTask {
                    SpeechBackendDescriptor(capability: self.capability, backendId: backend.backendId,
                                            availability: await backend.availability())
                }
            }
            var result: [SpeechBackendDescriptor] = []
            for await descriptor in group { result.append(descriptor) }
            return result.sorted { $0.backendId < $1.backendId }
        }
    }

    public func select(_ selection: SpeechBackendSelection = .default) async throws -> SpeechBackendDescriptor {
        guard !closed else { throw SpeechClientError.closed }
        let id: String
        switch selection {
        case .default: id = defaultBackendId
        case .explicit(let value): id = value
        }
        guard let backend = registrations[id] else {
            throw SpeechClientError.backendNotRegistered(capability: capability, backendId: id)
        }
        let availability = await backend.availability()
        guard !closed else { throw SpeechClientError.closed }
        guard availability.status == .available else {
            throw SpeechClientError.backendUnavailable(capability: capability, backendId: id,
                                                        reason: availability.reason ?? .unsupportedDevice)
        }
        return SpeechBackendDescriptor(capability: capability, backendId: id, availability: availability)
    }

    public func close() async {
        closed = true
        if closeTask == nil {
            closeTask = Task { [registrations] in
                for backend in registrations.values { await backend.close() }
            }
        }
        await closeTask?.value
    }
}

public struct SpeechSynthesisRequest: Sendable, Equatable {
    public let text: String
    public let locale: String
    public let voiceId: String?
    public init(text: String, locale: String, voiceId: String? = nil) {
        self.text = text; self.locale = locale; self.voiceId = voiceId
    }
}

public struct SpeechSynthesisResult: Sendable, Equatable {
    public let backendId: String
    public let voiceId: String?
    public init(backendId: String, voiceId: String? = nil) {
        self.backendId = backendId; self.voiceId = voiceId
    }
}

public protocol SpeechSynthesisBackend: SpeechBackend {
    func speak(_ request: SpeechSynthesisRequest) async throws -> SpeechSynthesisResult
}

/// Typed TTS facade over the same strict backend selection rules.
public actor SpeechSynthesisClient {
    private let registry: SpeechClient
    private let backends: [String: any SpeechSynthesisBackend]
    private var closed = false
    private var closeTask: Task<Void, Never>?
    public init(defaultBackendId: String, backends: [any SpeechSynthesisBackend]) throws {
        self.backends = try Self.makeTable(backends)
        self.registry = try SpeechClient(capability: .tts, defaultBackendId: defaultBackendId,
                                         registrations: backends.map { SpeechBackendRegistration(capability: .tts, backend: $0) })
    }
    public func speak(_ request: SpeechSynthesisRequest,
                      backend: SpeechBackendSelection = .default) async throws -> SpeechSynthesisResult {
        guard !closed else { throw SpeechClientError.closed }
        let selected = try await registry.select(backend)
        guard !closed else { throw SpeechClientError.closed }
        guard let implementation = backends[selected.backendId] else {
            throw SpeechClientError.backendNotRegistered(capability: .tts, backendId: selected.backendId)
        }
        return try await implementation.speak(request)
    }
    public func listBackends() async throws -> [SpeechBackendDescriptor] {
        guard !closed else { throw SpeechClientError.closed }
        let descriptors = await registry.listBackends()
        try Task.checkCancellation()
        guard !closed else { throw SpeechClientError.closed }
        return descriptors
    }
    public func close() async {
        closed = true
        if closeTask == nil {
            closeTask = Task { await registry.close() }
        }
        await closeTask?.value
    }
    private static func makeTable(_ backends: [any SpeechSynthesisBackend]) throws -> [String: any SpeechSynthesisBackend] {
        var table: [String: any SpeechSynthesisBackend] = [:]
        for backend in backends {
            guard backend.capability == .tts, !backend.backendId.isEmpty else {
                throw SpeechClientError.invalidRegistration("invalid tts backend")
            }
            guard table[backend.backendId] == nil else {
                throw SpeechClientError.invalidRegistration("duplicate backend: \(backend.backendId)")
            }
            table[backend.backendId] = backend
        }
        return table
    }
}
