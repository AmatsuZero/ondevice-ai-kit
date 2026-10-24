import Foundation
import ODAIKitModelStore
import ODAIKitMultimodal

/// Transfers ownership at each boundary; failures close only resources already acquired here.
@MainActor
enum MultimodalModelHostFactory {
  static func make(
    modelStore: ModelStore, manifest: ModelManifest, source: URL, inputDirectory: URL,
    videoInputDirectory: URL? = nil,
    videoPicker: ((@escaping (URL?) -> Void) throws -> Void)? = nil,
    picker: @escaping (@escaping (URL?) -> Void) throws -> Void,
    backend build: (ModelLease) throws -> any MultimodalGenerationBackend
  ) async throws -> MultimodalHost {
    try Task.checkCancellation()
    try await modelStore.install(manifest: manifest, source: source)
    try Task.checkCancellation()
    let lease = try await modelStore.acquire(manifest.ref)
    let backend: any MultimodalGenerationBackend
    do {
      try Task.checkCancellation()
      backend = try build(lease)
    } catch {
      await lease.close()
      throw error
    }
    let client: MultimodalGenerationClient
    do {
      try Task.checkCancellation()
      client = try MultimodalGenerationClient(
        defaultBackendId: backend.backendId, backends: [backend])
    } catch {
      await backend.close()
      throw error
    }
    do {
      try Task.checkCancellation()
      let inputs = try MultimodalInputStore(directory: inputDirectory)
      do {
        let videos = try videoInputDirectory.map { try MultimodalInputStore(directory: $0) }
        let bridge = IOSMultimodalBridge(client: client, inputs: inputs, videoInputs: videos)
        return MultimodalHost(
          store: inputs, bridge: bridge, videoStore: videos,
          videoPicker: videoPicker, picker: picker)
      } catch {
        inputs.close()
        throw error
      }
    } catch {
      await client.close()
      throw error
    }
  }
}
