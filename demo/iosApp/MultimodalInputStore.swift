import Foundation

/// Selection and operation leases own separate lifetimes; decoding stays in the backend.
@MainActor
final class MultimodalInputStore: IOSMultimodalInputProvider {
  private let files: SpeechInputStore

  init(
    directory: URL = FileManager.default.temporaryDirectory
      .appendingPathComponent("odai-multimodal-input-" + UUID().uuidString, isDirectory: true)
  ) throws {
    files = try SpeechInputStore(directory: directory)
  }

  func importImage(from source: URL) throws -> String {
    guard source.isFileURL else { throw InputError.invalidSource }
    let scoped = source.startAccessingSecurityScopedResource()
    defer { if scoped { source.stopAccessingSecurityScopedResource() } }
    let values = try source.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
    guard values.isRegularFile == true, values.isSymbolicLink != true else {
      throw InputError.invalidSource
    }
    return try files.importAudio(from: source)
  }

  func acquire(reference: String) throws -> any IOSMultimodalInputLease {
    Lease(try files.acquire(reference: reference))
  }

  func remove(reference: String) { files.remove(reference: reference) }
  func close() { files.close() }

  private enum InputError: Error { case invalidSource }

  private final class Lease: IOSMultimodalInputLease {
    private let underlying: any IOSSpeechAudioInputLease
    var url: URL { underlying.url }
    init(_ underlying: any IOSSpeechAudioInputLease) { self.underlying = underlying }
    func release() { underlying.release() }
  }
}
