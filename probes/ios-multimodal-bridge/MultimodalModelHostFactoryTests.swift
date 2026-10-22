import CryptoKit
import ODAIKitCore
import ODAIKitModelStore
import ODAIKitMultimodal
import XCTest

@MainActor final class MultimodalModelHostFactoryTests: XCTestCase {
  func testHostCloseReleasesTransferredModelLease() async throws {
    let e = try environment()
    defer { try? FileManager.default.removeItem(at: e.root) }
    let host = try await make(e) { ModelFactoryBackend(lease: $0) }
    do {
      try await e.store.remove(e.manifest.ref)
      XCTFail("lease must remain held")
    } catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
    host.model.refreshBackends()
    for _ in 0..<1000 {
      if !host.model.state.refreshing { break }
      await Task.yield()
    }
    XCTAssertEqual(host.model.state.backends.first?.backendId, "fixture")
    host.close()
    for _ in 0..<1000 {
      if !host.model.state.closing { break }
      await Task.yield()
    }
    XCTAssertFalse(host.model.state.closing)
    try await e.store.remove(e.manifest.ref)
  }

  func testBuilderThrowReleasesUntransferredLease() async throws {
    let e = try environment()
    defer { try? FileManager.default.removeItem(at: e.root) }
    do {
      _ = try await make(e) { _ in throw FactoryError.injected }
      XCTFail("must throw")
    } catch { XCTAssertEqual(error as? FactoryError, .injected) }
    try await e.store.remove(e.manifest.ref)
    XCTAssertFalse(FileManager.default.fileExists(atPath: e.input.path))
  }

  func testInvalidRegistrationClosesBackendAndLease() async throws {
    let e = try environment()
    defer { try? FileManager.default.removeItem(at: e.root) }
    do {
      _ = try await make(e) { ModelFactoryBackend(lease: $0, backendId: " ") }
      XCTFail("must reject")
    } catch { guard case MultimodalClientError.invalidRegistration = error else { throw error } }
    try await e.store.remove(e.manifest.ref)
  }

  func testCancelledBeforeInstallDoesNotBuildBackend() async throws {
    let e = try environment()
    defer { try? FileManager.default.removeItem(at: e.root) }
    var calls = 0
    let task = Task {
      try await make(e) {
        calls += 1
        return ModelFactoryBackend(lease: $0)
      }
    }
    task.cancel()
    do {
      _ = try await task.value
      XCTFail("must cancel")
    } catch is CancellationError {}
    XCTAssertEqual(calls, 0)
    let status = await e.store.status(e.manifest.ref)
    XCTAssertEqual(status, .absent)
  }

  func testCancellationAfterAcquireClosesBackendAndLease() async throws {
    let e = try environment()
    defer { try? FileManager.default.removeItem(at: e.root) }
    let task = Task {
      try await make(e) { lease in
        withUnsafeCurrentTask { $0?.cancel() }
        return ModelFactoryBackend(lease: lease)
      }
    }
    do {
      _ = try await task.value
      XCTFail("must cancel")
    } catch is CancellationError {}
    try await e.store.remove(e.manifest.ref)
  }

  func testIntegrityFailureDoesNotBuildOrLeakInputStore() async throws {
    let e = try environment()
    defer { try? FileManager.default.removeItem(at: e.root) }
    try Data([9]).write(to: e.source.appendingPathComponent("weights.bin"))
    var calls = 0
    do {
      _ = try await make(e) {
        calls += 1
        return ModelFactoryBackend(lease: $0)
      }
      XCTFail("must reject")
    } catch { XCTAssertEqual(error as? ModelStoreError, .integrity) }
    XCTAssertEqual(calls, 0)
    XCTAssertFalse(FileManager.default.fileExists(atPath: e.input.path))
  }

  private struct Environment {
    let root: URL
    let source: URL
    let input: URL
    let manifest: ModelManifest
    let store: ModelStore
  }
  private func make(
    _ e: Environment, backend: (ModelLease) throws -> any MultimodalGenerationBackend
  ) async throws -> MultimodalHost {
    try await MultimodalModelHostFactory.make(
      modelStore: e.store, manifest: e.manifest,
      source: e.source, inputDirectory: e.input, picker: { $0(nil) }, backend: backend)
  }
  private func environment() throws -> Environment {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    let source = root.appendingPathComponent("source")
    try FileManager.default.createDirectory(at: source, withIntermediateDirectories: true)
    let data = Data([1, 2, 3])
    try data.write(to: source.appendingPathComponent("weights.bin"))
    let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    let manifest = ModelManifest(
      modelId: "fixture", revision: "v1",
      upstreamURL: URL(string: "https://models.example/fixture")!, upstreamRevision: "fixed-test",
      capability: "multimodal", backendId: "fixture",
      backendVersion: .init(minimum: "1.0.0", maximumExclusive: "2.0.0"),
      minimumOS: "15.0", architectures: ["arm64"], formatVersion: "raw-v1",
      files: [
        .init(
          path: "weights.bin", url: URL(string: "https://models.example/weights.bin")!, bytes: 3,
          sha256: hash)
      ],
      processing: .init(
        inputSignature: "bytes", outputSignature: "text", preprocessRevision: "v1",
        tokenizerRevision: nil, promptRevision: nil, maxInputElements: 1),
      resources: .init(downloadBytes: 3, temporaryBytes: 3, installedBytesLimit: 3),
      licenseIdentifier: "CC0-1.0",
      licenseURL: URL(string: "https://creativecommons.org/publicdomain/zero/1.0/")!,
      attribution: "synthetic fixture", redistribution: "permitted")
    let store = try ModelStore(
      root: root.appendingPathComponent("models"),
      compatibility:
        .init(
          backendId: "fixture", backendVersion: "1.0.0", osVersion: "27.0", architecture: "arm64"))
    return Environment(
      root: root, source: source, input: root.appendingPathComponent("inputs"), manifest: manifest,
      store: store)
  }
}

private enum FactoryError: Error { case injected }
private final class ModelFactoryBackend: MultimodalGenerationBackend, @unchecked Sendable {
  let lease: ModelLease
  let backendId: String
  init(lease: ModelLease, backendId: String = "fixture") {
    self.lease = lease
    self.backendId = backendId
  }
  func availability() async -> CapabilityAvailability {
    .init(status: .available, backendId: backendId)
  }
  func generate(_ request: MultimodalRequest, update: (@Sendable (MultimodalTextUpdate) -> Void)?)
    async throws -> MultimodalTextOutput
  { throw FactoryError.injected }
  func drain() async {}
  func close() async { await lease.close() }
}
