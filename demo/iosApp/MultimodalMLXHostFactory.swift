import Foundation
import ODAIKitAppleMLXVLM
import ODAIKitModelStore

@MainActor
enum MultimodalMLXHostFactory {
  static func make(documents: URL, picker: MultimodalFilePicker) async throws -> MultimodalHost {
    let os = ProcessInfo.processInfo.operatingSystemVersion
    let manifest = try SmolVLM2Model.manifest()
    let store = try ModelStore(
      root: documents.appendingPathComponent("multimodal-model-store"),
      compatibility: .init(
        backendId: SmolVLM2Model.backendId, backendVersion: "1.0.0",
        osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)", architecture: "arm64"
      ))
    return try await MultimodalModelHostFactory.make(
      modelStore: store, manifest: manifest,
      source: documents.appendingPathComponent("model"),
      inputDirectory: FileManager.default.temporaryDirectory.appendingPathComponent(
        "odai-multimodal-input-" + UUID().uuidString),
      picker: { picker.present(reply: $0) }, backend: { try MLXVLMBackend(lease: $0) })
  }
}
