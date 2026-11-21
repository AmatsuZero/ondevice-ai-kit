import Foundation
import ODAIKitCore

/// Internal fixture backend, never registered by the SDK or exported as a production backend.
final class FixtureIdentityBackend: IdentityBackend, @unchecked Sendable {
    let backendId: String
    let backendVersion = "fixture-1"
    let modelBinding: ModelBinding
    let embeddingSpace: EmbeddingSpaceId
    private let available: Bool
    private let lock = NSLock()
    private var isClosed = false

    private init(backendId: String, revision: String, available: Bool) throws {
        self.backendId = backendId; self.available = available
        modelBinding = try ModelBinding(kind: backendId == "system.fixture" ? .system : .model, id: backendId, revision: revision)
        embeddingSpace = try EmbeddingSpaceId(supportedCapabilities: [.verification, .identification], backendId: backendId, backendVersion: "fixture-1",
                                             modelBinding: modelBinding, featureFormatVersion: "1", dimension: 2, dtype: .float32, endianness: .little,
                                             normalization: "none", preprocessRevision: "fixture-p1", scoreMetric: "cosine", scoreDirection: .higherIsBetter)
    }
    static func system(revision: String = "os-build-fixture") throws -> FixtureIdentityBackend { try FixtureIdentityBackend(backendId: "system.fixture", revision: revision, available: true) }
    static func optionalUnavailable() throws -> FixtureIdentityBackend { try FixtureIdentityBackend(backendId: "optional.fixture", revision: "v1", available: false) }
    func availability() -> CapabilityAvailability {
        lock.lock(); defer { lock.unlock() }
        return CapabilityAvailability(status: available && !isClosed ? .available : .unavailable, reason: isClosed ? .closed : (available ? nil : .missingAssets), backendId: backendId)
    }
    private func check(_ probe: IdentityProbe) throws {
        if isClosed { throw IdentityError(.closed, backendId: backendId) }
        guard available else { throw IdentityError(.backendUnavailable, backendId: backendId) }
        guard probe.fixtureID != nil, let vector = probe.vector, vector.count == embeddingSpace.dimension else { throw IdentityError(.invalidInput) }
    }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding {
        lock.lock(); defer { lock.unlock() }
        try check(probe)
        return try IdentityEmbedding(embeddingSpace: embeddingSpace, values: probe.vector!)
    }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality {
        lock.lock(); defer { lock.unlock() }
        try check(probe)
        return try IdentityQuality(faceCount: 1, score: 1)
    }
    func close() { lock.lock(); defer { lock.unlock() }; isClosed = true }
}
