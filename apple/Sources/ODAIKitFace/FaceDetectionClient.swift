import ODAIKitCore

public enum BackendSelection: Sendable, Equatable {
    case `default`
    case explicit(String)
}

public struct FaceDetectionRegistration: Sendable {
    public let capabilityId: String
    public let backendId: String
    public let detector: any FaceDetector
    public let supportedOptionIds: Set<String>

    public init(capabilityId: String = "face.detection", backendId: String,
                detector: any FaceDetector, supportedOptionIds: Set<String> = []) {
        self.capabilityId = capabilityId
        self.backendId = backendId
        self.detector = detector
        self.supportedOptionIds = supportedOptionIds
    }
}

public struct FaceBackendDescriptor: Sendable, Equatable {
    public let capabilityId: String
    public let backendId: String
    public let availability: CapabilityAvailability
    public let supportedOptionIds: Set<String>
}

public enum FaceDetectionClientError: Error, Equatable, Sendable {
    case invalidRegistration(String)
    case backendNotRegistered(capabilityId: String, backendId: String)
    case backendUnavailable(capabilityId: String, backendId: String, reason: UnavailableReason)
    case unsupportedOption(capabilityId: String, backendId: String, optionId: String)
    case closed
}

/// Selects exactly one registered face backend for each request. It never falls back.
public actor FaceDetectionClient {
    public static let capabilityId = "face.detection"
    private let defaultBackendId: String
    private let registrations: [String: FaceDetectionRegistration]
    private var closed = false

    public init(defaultBackendId: String, registrations: [FaceDetectionRegistration]) throws {
        guard !defaultBackendId.isEmpty else { throw FaceDetectionClientError.invalidRegistration("empty default backend") }
        var table: [String: FaceDetectionRegistration] = [:]
        for registration in registrations {
            guard registration.capabilityId == Self.capabilityId, registration.backendId == registration.detector.backendId,
                  !registration.backendId.isEmpty else {
                throw FaceDetectionClientError.invalidRegistration(registration.backendId)
            }
            guard table[registration.backendId] == nil else {
                throw FaceDetectionClientError.invalidRegistration("duplicate backend: \(registration.backendId)")
            }
            table[registration.backendId] = registration
        }
        guard table[defaultBackendId] != nil else { throw FaceDetectionClientError.invalidRegistration("default backend not registered") }
        self.defaultBackendId = defaultBackendId
        self.registrations = table
    }

    public func listBackends() async -> [FaceBackendDescriptor] {
        await withTaskGroup(of: FaceBackendDescriptor.self, returning: [FaceBackendDescriptor].self) { group in
            for registration in registrations.values {
                group.addTask {
                    FaceBackendDescriptor(capabilityId: Self.capabilityId, backendId: registration.backendId,
                                          availability: await registration.detector.availability(),
                                          supportedOptionIds: registration.supportedOptionIds)
                }
            }
            var result: [FaceBackendDescriptor] = []
            for await descriptor in group { result.append(descriptor) }
            return result.sorted { $0.backendId < $1.backendId }
        }
    }

    public func detect(input: ImageInput, options: FaceOptions = FaceOptions(),
                       optionIds: Set<String> = [], backend: BackendSelection = .default) async throws -> FaceResult {
        guard !closed else { throw FaceDetectionClientError.closed }
        let backendId = backend == .default ? defaultBackendId : {
            if case .explicit(let id) = backend { return id }
            return defaultBackendId
        }()
        guard let registration = registrations[backendId] else {
            throw FaceDetectionClientError.backendNotRegistered(capabilityId: Self.capabilityId, backendId: backendId)
        }
        let availability = await registration.detector.availability()
        guard availability.status == .available else {
            throw FaceDetectionClientError.backendUnavailable(capabilityId: Self.capabilityId, backendId: backendId,
                                                              reason: availability.reason ?? .unsupportedDevice)
        }
        if let unsupported = optionIds.first(where: { !registration.supportedOptionIds.contains($0) }) {
            throw FaceDetectionClientError.unsupportedOption(capabilityId: Self.capabilityId, backendId: backendId, optionId: unsupported)
        }
        return try await registration.detector.detect(input: input, options: options)
    }

    public func close() async {
        guard !closed else { return }
        closed = true
        await withTaskGroup(of: Void.self) { group in
            for registration in registrations.values { group.addTask { await registration.detector.close() } }
        }
    }
}
