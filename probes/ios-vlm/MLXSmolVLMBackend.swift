import Foundation

#if canImport(MLXVLM) && canImport(MLXLMCommon) && canImport(MLXHuggingFace)
import MLXHuggingFace
import MLXLMCommon
import MLXVLM

/// Optional MLX implementation scoped to the iOS 18 probe target.
/// No MLX type crosses into the base SDK modules.
@available(iOS 18.0, *)
public struct MLXSmolVLMBackend: VLMBackend {
    public let modelDirectory: URL
    public let identifier = "mlx.smolvlm2-500m"

    public init(modelDirectory: URL) { self.modelDirectory = modelDirectory }

    public func generate(input: URL, manifest: VLMManifest) async throws -> VLMGeneration {
        guard manifest.status == "verified" else { throw VLMProbeError.candidateManifest }
        let model = try await VLMModelFactory.shared.loadContainer(
            from: modelDirectory, using: #huggingFaceTokenizerLoader())
        let session = ChatSession(model)
        do {
            let clock = ContinuousClock()
            let started = clock.now
            var firstTokenMs: Double?
            var response = ""
            for try await chunk in session.streamResponse(
                to: "Describe this image in one brief sentence.", image: .url(input)) {
                if firstTokenMs == nil {
                    let elapsed = started.duration(to: clock.now)
                    firstTokenMs = Double(elapsed.components.seconds) * 1000
                        + Double(elapsed.components.attoseconds) / 1e15
                }
                response += chunk
            }
            await session.clear()
            return VLMGeneration(output: response, firstTokenMs: firstTokenMs)
        } catch {
            await session.clear()
            throw error
        }
    }
}
#endif
