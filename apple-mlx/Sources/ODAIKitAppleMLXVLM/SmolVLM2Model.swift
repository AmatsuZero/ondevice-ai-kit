import Foundation
import ODAIKitModelStore
import ODAIKitMultimodal

/// Fixed asset metadata only. This type never downloads or installs a model.
public enum SmolVLM2Model {
    public static let backendId = "mlx.smolvlm2-500m"
    public static let identity = try! MultimodalModelIdentity(
        modelId: "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
        modelRevision: "fa57db46815177fbdfd65cc85a2b3416a8332268",
        runtimeRevision: "6e338e5df49424fc0db7c6d58184d622f92ed18a")

    public static func manifest() throws -> ModelManifest {
        guard let url = Bundle.module.url(forResource: "smolvlm2-model", withExtension: "json") else {
            throw MLXVLMBackendError.invalidModel
        }
        return try JSONDecoder().decode(ModelManifest.self, from: Data(contentsOf: url))
    }

    static func validate(_ manifest: ModelManifest) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        guard try encoder.encode(manifest) == encoder.encode(Self.manifest()) else {
            throw MLXVLMBackendError.invalidModel
        }
    }
}

public enum MLXVLMBackendError: Error, Equatable, Sendable {
    case invalidModel
    case invalidImage
    case runtimeFailure
    case noResult
}
