import XCTest
import CryptoKit
import ODAIKitCore
import ODAIKitModelStore
import ODAIKitMultimodal
@testable import ODAIKitAppleMLXVLM

final class MLXVLMBackendTests: XCTestCase {
    func testCanonicalManifestHasFixedIdentityAndFourteenFiles() throws {
        let m = try SmolVLM2Model.manifest()
        XCTAssertEqual(m.modelId, "smolvlm2-500m-video-instruct-mlx")
        XCTAssertEqual(m.upstreamRevision, "fa57db46815177fbdfd65cc85a2b3416a8332268")
        XCTAssertEqual(m.files.count, 14)
        XCTAssertEqual(m.files.first { $0.path == "model.safetensors" }?.sha256,
                       "a9839c8f79ecc93e54a00dc73cc0e68ba477debcd065d50c1c289fbb1075f981")
        XCTAssertEqual(m.resources.downloadBytes, m.files.reduce(0) { $0 + $1.bytes })
        XCTAssertEqual(SmolVLM2Model.identity.runtimeRevision, "6e338e5df49424fc0db7c6d58184d622f92ed18a")
    }

    func testCanonicalManifestValidatesButChangedIdentityOrFileHashIsRejected() throws {
        let expected = try SmolVLM2Model.manifest()
        try SmolVLM2Model.validate(expected)
        var changed = expected
        changed.revision = "other"
        XCTAssertThrowsError(try SmolVLM2Model.validate(changed))
        changed = expected
        changed.files[0].sha256 = String(repeating: "0", count: 64)
        XCTAssertThrowsError(try SmolVLM2Model.validate(changed))
    }

    func testAvailabilityDoesNotLoadAndCloseReleasesActualStoreLease() async throws {
        let fixture = try await ModelFixture.make()
        let runtime = ImmediateRuntime()
        let session = MLXVLMGenerationSession(lease: fixture.lease, runtime: runtime,
                                              identity: SmolVLM2Model.identity)
        let a = await session.availability(supported: true)
        XCTAssertEqual(a.status, .available)
        let unsupported = await session.availability(supported: false)
        XCTAssertEqual(unsupported.reason, .unsupportedDevice)
        let count = await runtime.count
        XCTAssertEqual(count, 0)
        do { try await fixture.store.remove(fixture.manifest.ref); XCTFail("lease released early") }
        catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
        async let first: Void = session.close()
        async let second: Void = session.close()
        _ = await (first, second)
        try await fixture.store.remove(fixture.manifest.ref)
        let closed = await session.availability(supported: true)
        XCTAssertEqual(closed.reason, .closed)
    }

    func testPublicBackendRejectsNonCanonicalLease() async throws {
        let fixture = try await ModelFixture.make()
        XCTAssertThrowsError(try MLXVLMBackend(lease: fixture.lease))
        await fixture.lease.close()
        try await fixture.store.remove(fixture.manifest.ref)
    }
}

actor ImmediateRuntime: MLXVLMRuntime {
    var count = 0
    func generate(directory: URL, request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        count += 1
        return "fixture"
    }
}

struct ModelFixture {
    let store: ModelStore
    let manifest: ModelManifest
    let lease: ModelLease
    static func make() async throws -> Self {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let source = root.appendingPathComponent("source")
        try FileManager.default.createDirectory(at: source, withIntermediateDirectories: true)
        let data = Data("tiny model fixture".utf8)
        try data.write(to: source.appendingPathComponent("weights.bin"))
        let manifest = ModelManifest(modelId: "fixture", revision: "1", upstreamURL: URL(string: "https://example.org/model")!,
            upstreamRevision: "1", capability: "multimodal", backendId: "mlx.smolvlm2-500m",
            backendVersion: .init(minimum: "1.0", maximumExclusive: "2.0"), minimumOS: "18.0", architectures: ["arm64"],
            formatVersion: "1", files: [.init(path: "weights.bin", url: URL(string: "https://example.org/weights.bin")!,
                bytes: Int64(data.count), sha256: SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined())],
            processing: .init(inputSignature: "image", outputSignature: "text", preprocessRevision: "1", tokenizerRevision: nil,
                promptRevision: nil, maxInputElements: 1),
            resources: .init(downloadBytes: Int64(data.count), temporaryBytes: Int64(data.count), installedBytesLimit: Int64(data.count)),
            licenseIdentifier: "Apache-2.0", licenseURL: URL(string: "https://example.org/license")!, attribution: "fixture", redistribution: "fixture")
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .init(backendId: manifest.backendId,
            backendVersion: "1.0", osVersion: "18.0", architecture: "arm64"))
        try await store.install(manifest: manifest, source: source)
        return Self(store: store, manifest: manifest, lease: try await store.acquire(manifest.ref))
    }
}
