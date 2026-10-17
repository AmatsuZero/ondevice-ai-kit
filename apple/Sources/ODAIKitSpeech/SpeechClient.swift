import ODAIKitCore

public enum SpeechCapability: String, Sendable, Equatable { case asr, tts }
public enum SpeechBackendSelection: Sendable, Equatable { case `default`, explicit(String) }

public protocol SpeechBackend: Sendable {
    var backendId: String { get }
    var capability: SpeechCapability { get }
    func availability() async -> CapabilityAvailability
    func close() async
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
        guard availability.status == .available else {
            throw SpeechClientError.backendUnavailable(capability: capability, backendId: id,
                                                        reason: availability.reason ?? .unsupportedDevice)
        }
        return SpeechBackendDescriptor(capability: capability, backendId: id, availability: availability)
    }

    public func close() async {
        guard !closed else { return }
        closed = true
        for backend in registrations.values { await backend.close() }
    }
}
