import XCTest
import CryptoKit
import ODAIKitCore
import ODAIKitModelStore
@testable import ODAIKitIdentity

final class LocalIdentityBackendTests: XCTestCase {
    func testAdapterChecksStopEvenWhenRuntimeIgnoresCooperativeSignal() async throws {
        let f = try LocalModelFixture(); defer { f.cleanup() }
        try await f.install()
        let backend = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime)
        let signal = IdentityStopSignal()
        f.runtime.onEmbed = { signal.requestStop(.cancelled) }
        XCTAssertThrowsError(try backend.embed(IdentityProbe(fixtureID: "unit", vector: [1, 0]), stopSignal: signal)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .cancelled)
        }
        await backend.closeAndWait()
    }
    func testRuntimeVersionMustSatisfyInstalledManifestEvenIfStoreUsesAnotherVersion() async throws {
        let f = try LocalModelFixture(); defer { f.cleanup() }
        try await f.install()
        for version in ["2.0.0", "0.9.0", "not-a-version"] {
            f.runtime.version = version
            do { _ = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime); XCTFail("incompatible runtime \(version)") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .embeddingSpaceMismatch) }
        }
        try await f.store.remove(f.manifest.ref)
    }
    func testLeasePinsActualModelUntilBackendCloseAndWait() async throws {
        let f = try LocalModelFixture(); defer { f.cleanup() }
        try await f.install()
        let backend = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime)
        do { try await f.store.remove(f.manifest.ref); XCTFail("unleased model") }
        catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
        let probe = try IdentityProbe(fixtureID: "unit", vector: [1, 0])
        XCTAssertEqual(try backend.embed(probe).values, [1, 0])
        XCTAssertEqual(try backend.evaluateQuality(probe).faceCount, 1)
        XCTAssertEqual(backend.modelBinding.revision, "v1")
        XCTAssertEqual(backend.embeddingSpace.preprocessRevision, "fixture-p1")
        await backend.closeAndWait(); await backend.closeAndWait()
        XCTAssertEqual(f.runtime.closeCount, 1)
        try await f.store.remove(f.manifest.ref)
        XCTAssertThrowsError(try backend.embed(probe)) { XCTAssertEqual(($0 as? IdentityError)?.code, .closed) }
    }

    func testMissingModelAndBindingMismatchFailWithStableMetadataWithoutLeakingLease() async throws {
        let f = try LocalModelFixture(); defer { f.cleanup() }
        do { _ = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime); XCTFail("missing model") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .backendUnavailable) }
        try await f.install()
        f.runtime.bindingRevision = "v2"
        do { _ = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime); XCTFail("mismatch") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .embeddingSpaceMismatch) }
        try await f.store.remove(f.manifest.ref)
        XCTAssertEqual(f.runtime.closeCount, 0, "factory failure retains caller runtime ownership")
    }

    func testManifestPreprocessMismatchReleasesAcquiredLease() async throws {
        let f = try LocalModelFixture(preprocess: "other-p1"); defer { f.cleanup() }
        try await f.install()
        do { _ = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime); XCTFail("preprocess mismatch") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .embeddingSpaceMismatch) }
        try await f.store.remove(f.manifest.ref)
    }

    func testUnverifiedPolicyAndSyntheticImageNeverProduceIdentityOutcome() async throws {
        let f = try LocalModelFixture(); defer { f.cleanup() }
        try await f.install()
        let backend = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime)
        let policy = try ThresholdPolicy(id: "draft", revision: "v1", embeddingSpace: backend.embeddingSpace,
                                         scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .unverified)
        XCTAssertThrowsError(try policy.validate(embeddingSpace: backend.embeddingSpace)) { XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified) }
        let image = try IdentityProbe(encodedImage: Data([0, 1, 2, 3]), revision: "synthetic")
        XCTAssertThrowsError(try backend.embed(image)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        XCTAssertThrowsError(try backend.evaluateQuality(image)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        await backend.closeAndWait()
    }

    func testRuntimeBindingDriftIsRejectedBeforeReturningEmbedding() async throws {
        let f = try LocalModelFixture(); defer { f.cleanup() }
        try await f.install()
        let backend = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime)
        f.runtime.onEmbed = { f.runtime.bindingRevision = "v2" }
        XCTAssertThrowsError(try backend.embed(IdentityProbe(fixtureID: "unit", vector: [1, 0]))) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .embeddingSpaceMismatch)
        }
        XCTAssertEqual(backend.modelBinding.revision, "v1", "admitted snapshot is immutable")
        XCTAssertEqual(backend.availability().status, .unavailable)
        await backend.closeAndWait()
    }

    func testClosePinsLeaseUntilBlockedRuntimeFinishesAndCloseCallbackReenters() async throws {
        let f = try LocalModelFixture(); defer { f.cleanup() }
        try await f.install()
        let backend = try await LocalIdentityBackend.open(modelStore: f.store, modelRef: f.manifest.ref, runtime: f.runtime)
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        f.runtime.onEmbed = { entered.signal(); release.wait() }
        f.runtime.onClose = { backend.close() }
        let work = Task.detached { try backend.embed(IdentityProbe(fixtureID: "unit", vector: [1, 0])) }
        XCTAssertEqual(entered.wait(timeout: .now() + 3), .success)
        backend.close()
        XCTAssertEqual(backend.availability().reason, .closed)
        do { try await f.store.remove(f.manifest.ref); XCTFail("released while runtime still reading") }
        catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
        release.signal()
        do { _ = try await work.value; XCTFail("late result") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        await backend.closeAndWait()
        try await f.store.remove(f.manifest.ref)
        XCTAssertEqual(f.runtime.closeCount, 1)
    }
}

private final class LocalModelFixture {
    let root: URL
    let source: URL
    let store: ModelStore
    let manifest: ModelManifest
    let runtime = LeasedFixtureRuntime()
    init(preprocess: String = "fixture-p1") throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        source = root.appendingPathComponent("source")
        try FileManager.default.createDirectory(at: source, withIntermediateDirectories: true)
        let bytes = Data("not a biometric model".utf8)
        try bytes.write(to: source.appendingPathComponent("fixture.bin"))
        let hash = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
        manifest = ModelManifest(modelId: "synthetic", revision: "v1", upstreamURL: URL(string: "https://models.example/fixture")!,
            upstreamRevision: "synthetic-v1", capability: "face-identity", backendId: "local.fixture", backendVersion: .init(minimum: "1.0.0", maximumExclusive: "2.0.0"),
            minimumOS: "12", architectures: ["arm64"], formatVersion: "fixture-1",
            files: [.init(path: "fixture.bin", url: URL(string: "https://models.example/fixture.bin")!, bytes: Int64(bytes.count), sha256: hash)],
            processing: .init(inputSignature: "fixture-vector-2", outputSignature: "float32-2", preprocessRevision: preprocess, tokenizerRevision: nil, promptRevision: nil, maxInputElements: 2),
            resources: .init(downloadBytes: Int64(bytes.count), temporaryBytes: Int64(bytes.count), installedBytesLimit: Int64(bytes.count)),
            licenseIdentifier: "CC0-1.0", licenseURL: URL(string: "https://creativecommons.org/publicdomain/zero/1.0/")!, attribution: "Synthetic SDK fixture", redistribution: "permitted")
        store = try ModelStore(root: root.appendingPathComponent("models"), compatibility: .init(backendId: "local.fixture", backendVersion: "1.0.0", osVersion: "12", architecture: "arm64"))
    }
    func install() async throws { try await store.install(manifest: manifest, source: source) }
    func cleanup() { try? FileManager.default.removeItem(at: root) }
}

private final class LeasedFixtureRuntime: IdentityBackend, @unchecked Sendable {
    let backendId = "local.fixture"
    var version = "1.0.0"
    var backendVersion: String { version }
    var bindingRevision = "v1"
    var onEmbed: (() -> Void)?
    var onClose: (() -> Void)?
    var closeCount = 0
    var modelBinding: ModelBinding { try! .init(kind: .model, id: "synthetic", revision: bindingRevision) }
    var embeddingSpace: EmbeddingSpaceId {
        try! .init(supportedCapabilities: [.verification, .identification], backendId: backendId, backendVersion: backendVersion,
                   modelBinding: modelBinding, featureFormatVersion: "1", dimension: 2, dtype: .float32, endianness: .little,
                   normalization: "none", preprocessRevision: "fixture-p1", scoreMetric: "cosine", scoreDirection: .higherIsBetter)
    }
    func availability() -> CapabilityAvailability { .init(status: .available, backendId: backendId) }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding {
        guard probe.fixtureID != nil, let vector = probe.vector else { throw IdentityError(.invalidInput) }
        let result = try IdentityEmbedding(embeddingSpace: embeddingSpace, values: vector)
        onEmbed?()
        return result
    }
    func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding { try embed(probe) }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality {
        guard probe.fixtureID != nil else { throw IdentityError(.invalidInput) }
        return try .init(faceCount: 1, score: 1)
    }
    func close() { closeCount += 1; onClose?() }
}
