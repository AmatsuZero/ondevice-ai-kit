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

public enum VLMRunStatus: String, Codable, Sendable { case unavailable, cancelled, success }

public struct VLMRunResult: Codable, Sendable {
    public let status: VLMRunStatus
    public let actualBackend: String
    public let modelRevision: String
    public let runtimeRevision: String
    public let fixtureHash: String
    public let firstTokenMs: Double?
    public let totalMs: Double?
    public let peakMemory: Int?
    public let peakMemoryStatus: String
    public let cancelled: Bool
    public let output: String
    public let errorReason: String?

    public init(status: VLMRunStatus, actualBackend: String, modelRevision: String,
                runtimeRevision: String, fixtureHash: String, firstTokenMs: Double?,
                totalMs: Double?, peakMemory: Int?, peakMemoryStatus: String = "unmeasured",
                cancelled: Bool, output: String, errorReason: String? = nil) {
        self.status = status; self.actualBackend = actualBackend; self.modelRevision = modelRevision
        self.runtimeRevision = runtimeRevision; self.fixtureHash = fixtureHash
        self.firstTokenMs = firstTokenMs; self.totalMs = totalMs; self.peakMemory = peakMemory
        self.peakMemoryStatus = peakMemoryStatus; self.cancelled = cancelled; self.output = output
        self.errorReason = errorReason
    }

    private enum CodingKeys: String, CodingKey {
        case status, actualBackend, modelRevision, runtimeRevision, fixtureHash
        case firstTokenMs, totalMs, peakMemory, peakMemoryStatus, cancelled, output, errorReason
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(status, forKey: .status)
        try container.encode(actualBackend, forKey: .actualBackend)
        try container.encode(modelRevision, forKey: .modelRevision)
        try container.encode(runtimeRevision, forKey: .runtimeRevision)
        try container.encode(fixtureHash, forKey: .fixtureHash)
        try container.encodeIfPresent(firstTokenMs, forKey: .firstTokenMs)
        try container.encodeIfPresent(totalMs, forKey: .totalMs)
        if let peakMemory { try container.encode(peakMemory, forKey: .peakMemory) }
        else { try container.encodeNil(forKey: .peakMemory) }
        try container.encode(peakMemoryStatus, forKey: .peakMemoryStatus)
        try container.encode(cancelled, forKey: .cancelled)
        try container.encode(output, forKey: .output)
        try container.encodeIfPresent(errorReason, forKey: .errorReason)
    }
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
            guard !generation.output.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return VLMRunResult(status: .unavailable, actualBackend: backend.identifier,
                    modelRevision: manifest.modelRevision, runtimeRevision: manifest.runtimeRevision,
                    fixtureHash: fixtureHash, firstTokenMs: generation.firstTokenMs,
                    totalMs: nil, peakMemory: nil, peakMemoryStatus: "unmeasured",
                    cancelled: false, output: "", errorReason: "empty generation")
            }
            let elapsed = start.duration(to: clock.now)
            let ms = Double(elapsed.components.seconds) * 1000 + Double(elapsed.components.attoseconds) / 1e15
            return VLMRunResult(status: .success, actualBackend: backend.identifier, modelRevision: manifest.modelRevision,
                runtimeRevision: manifest.runtimeRevision, fixtureHash: fixtureHash, firstTokenMs: generation.firstTokenMs,
                totalMs: ms, peakMemory: nil, peakMemoryStatus: "unmeasured", cancelled: false, output: generation.output)
        } catch is CancellationError {
            return VLMRunResult(status: .cancelled, actualBackend: backend.identifier, modelRevision: manifest.modelRevision,
                runtimeRevision: manifest.runtimeRevision, fixtureHash: fixtureHash, firstTokenMs: nil,
                totalMs: nil, peakMemory: nil, peakMemoryStatus: "unmeasured", cancelled: true, output: "")
        } catch VLMProbeError.unavailable {
            return VLMRunResult(status: .unavailable, actualBackend: backend.identifier, modelRevision: manifest.modelRevision,
                runtimeRevision: manifest.runtimeRevision, fixtureHash: fixtureHash, firstTokenMs: nil,
                totalMs: nil, peakMemory: nil, peakMemoryStatus: "unmeasured", cancelled: false, output: "")
        }
    }
}
