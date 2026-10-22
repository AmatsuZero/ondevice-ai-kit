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
        session = MLXVLMGenerationSession(lease: lease, runtime: LocalMLXVLMRuntime(), identity: SmolVLM2Model.identity)
    }

    public func availability() async -> CapabilityAvailability {
        await session.availability(supported: Self.isSupported)
    }

    public func generate(_ request: MultimodalRequest,
                         update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        let state = await session.availability(supported: Self.isSupported)
        if state.reason == .closed { throw MultimodalClientError.closed }
        guard state.status == .available else {
            throw MultimodalClientError.backendUnavailable(backendId, .unsupportedDevice)
        }
        return try await session.generate(request, update: update)
    }
    private static var isSupported: Bool {
        #if os(iOS) && arch(arm64) && !targetEnvironment(simulator)
        true
        #else
        false
        #endif
    }

    public func drain() async { await session.drain() }
    public func close() async { await session.close() }
}
