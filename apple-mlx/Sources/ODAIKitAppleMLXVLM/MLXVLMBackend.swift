import Foundation
import ODAIKitCore
import ODAIKitModelStore
import ODAIKitMultimodal

/// Owns the supplied lease after successful initialization. Do not separately close that lease.
public struct MLXVLMBackend: MultimodalGenerationBackend {
    public let backendId = SmolVLM2Model.backendId
    private let session: MLXVLMGenerationSession

    public init(lease: ModelLease) throws {
        try SmolVLM2Model.validate(lease.manifest)
        session = MLXVLMGenerationSession(lease: lease, runtime: UnlinkedRuntime(), identity: SmolVLM2Model.identity)
    }

    public func availability() async -> CapabilityAvailability {
        await session.availability(supported: false)
    }

    public func generate(_ request: MultimodalRequest,
                         update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        try await session.generate(request, update: update)
    }
    public func drain() async { await session.drain() }
    public func close() async { await session.close() }
}

private struct UnlinkedRuntime: MLXVLMRuntime {
    func generate(directory: URL, request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        throw MultimodalClientError.backendUnavailable(SmolVLM2Model.backendId, .backendNotLinked)
    }
}
