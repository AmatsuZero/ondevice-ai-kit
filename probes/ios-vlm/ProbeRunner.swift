import CryptoKit
import Foundation

public struct VLMManifest: Codable, Sendable {
    public let modelId: String
    public let modelRevision: String
    public let runtime: String
    public let runtimeRevision: String
    public let platformMin: String
    public let status: String

    public static let candidate = VLMManifest(
        modelId: "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
        modelRevision: "fa57db46815177fbdfd65cc85a2b3416a8332268",
        runtime: "MLXVLM",
        runtimeRevision: "6e338e5df49424fc0db7c6d58184d622f92ed18a",
        platformMin: "iOS 18.0",
        status: "candidate")

    public init(modelId: String, modelRevision: String, runtime: String,
                runtimeRevision: String, platformMin: String, status: String) {
        self.modelId = modelId; self.modelRevision = modelRevision; self.runtime = runtime
        self.runtimeRevision = runtimeRevision; self.platformMin = platformMin; self.status = status
    }
}

public enum VLMRunStatus: String, Sendable { case unavailable, cancelled, success }

public struct VLMRunResult: Sendable {
    public let status: VLMRunStatus
    public let actualBackend: String
    public let modelRevision: String
    public let runtimeRevision: String
    public let fixtureHash: String
    public let firstTokenMs: Double?
    public let totalMs: Double?
    public let peakMemory: Int?
    public let cancelled: Bool
    public let output: String
}

public struct VLMGeneration: Sendable {
    public let output: String
    public let firstTokenMs: Double?
    public init(output: String, firstTokenMs: Double? = nil) {
        self.output = output
        self.firstTokenMs = firstTokenMs
    }
}

public enum VLMProbeError: Error { case missingManifest, missingInput, candidateManifest, unavailable }

public protocol VLMBackend: Sendable {
    var identifier: String { get }
    func generate(input: URL, manifest: VLMManifest) async throws -> VLMGeneration
}

public struct UnavailableBackend: VLMBackend {
    public init() {}
    public let identifier = "MLXVLM"
    public func generate(input: URL, manifest: VLMManifest) async throws -> VLMGeneration { throw VLMProbeError.unavailable }
}

public struct CancellingBackend: VLMBackend {
    public init() {}
    public let identifier = "MLXVLM"
    public func generate(input: URL, manifest: VLMManifest) async throws -> VLMGeneration { throw CancellationError() }
}

public struct VLMProbe<B: VLMBackend>: Sendable {
    private let backend: B
    public init(backend: B) { self.backend = backend }

    public func run(input: URL, manifest: VLMManifest?) async throws -> VLMRunResult {
        guard let manifest else { throw VLMProbeError.missingManifest }
        guard FileManager.default.fileExists(atPath: input.path) else { throw VLMProbeError.missingInput }
        guard manifest.status == "verified" else { throw VLMProbeError.candidateManifest }
        let fixtureHash = SHA256.hash(data: try Data(contentsOf: input)).map { String(format: "%02x", $0) }.joined()
        let clock = ContinuousClock()
        let start = clock.now
        do {
            let generation = try await backend.generate(input: input, manifest: manifest)
            let elapsed = start.duration(to: clock.now)
            let ms = Double(elapsed.components.seconds) * 1000 + Double(elapsed.components.attoseconds) / 1e15
            return VLMRunResult(status: .success, actualBackend: backend.identifier, modelRevision: manifest.modelRevision,
                runtimeRevision: manifest.runtimeRevision, fixtureHash: fixtureHash, firstTokenMs: generation.firstTokenMs,
                totalMs: ms, peakMemory: nil, cancelled: false, output: generation.output)
        } catch is CancellationError {
            return VLMRunResult(status: .cancelled, actualBackend: backend.identifier, modelRevision: manifest.modelRevision,
                runtimeRevision: manifest.runtimeRevision, fixtureHash: fixtureHash, firstTokenMs: nil,
                totalMs: nil, peakMemory: nil, cancelled: true, output: "")
        } catch VLMProbeError.unavailable {
            return VLMRunResult(status: .unavailable, actualBackend: backend.identifier, modelRevision: manifest.modelRevision,
                runtimeRevision: manifest.runtimeRevision, fixtureHash: fixtureHash, firstTokenMs: nil,
                totalMs: nil, peakMemory: nil, cancelled: false, output: "")
        }
    }
}
