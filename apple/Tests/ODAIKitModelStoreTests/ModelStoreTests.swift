import XCTest
import CryptoKit
@testable import ODAIKitModelStore
final class ModelStoreTests: XCTestCase {
    func environment() throws -> (URL, URL, ModelManifest) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let source = root.appendingPathComponent("source")
        try FileManager.default.createDirectory(at: source, withIntermediateDirectories: true)
        let data = Data("small model fixture".utf8)
        try data.write(to: source.appendingPathComponent("weights.bin"))
        let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        return (root, source, ModelManifest.fixture(file: ModelFile(path: "weights.bin", url: URL(string: "https://models.example/fixture/v1/weights.bin")!, bytes: Int64(data.count), sha256: hash)))
    }
    func testVerifiedInstallAndLeasePreventsRemoval() async throws {
        let (root, source, manifest) = try environment(); defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        try await store.install(manifest: manifest, source: source)
        let status = await store.status(manifest.ref); XCTAssertEqual(status, .installed)
        let lease = try await store.acquire(manifest.ref)
        XCTAssertEqual(try String(contentsOf: lease.directory.appendingPathComponent("weights.bin")), "small model fixture")
        do { try await store.remove(manifest.ref); XCTFail("in use") } catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
        await lease.close(); await lease.close(); try await store.remove(manifest.ref)
        let removed = await store.status(manifest.ref); XCTAssertEqual(removed, .absent)
    }
    func testBadNewRevisionPreservesOld() async throws {
        let (root, source, manifest) = try environment(); defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        try await store.install(manifest: manifest, source: source)
        var broken = manifest; broken.revision = "v2"; broken.files[0].sha256 = String(repeating: "0", count: 64)
        do { try await store.install(manifest: broken, source: source); XCTFail("bad hash") } catch { }
        let old = await store.status(manifest.ref); let new = await store.status(broken.ref)
        XCTAssertEqual(old, .installed); XCTAssertEqual(new, .absent)
    }
    func testRejectsUnsafePathsAndInvalidCompatibility() async throws {
        let (root, source, manifest) = try environment(); defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        for path in ["../escape", "/absolute", "a/../../escape", ".odai-manifest.json", "a//b"] {
            var bad = manifest; bad.files[0].path = path
            do { try await store.install(manifest: bad, source: source); XCTFail(path) } catch { }
        }
        for kind in 0..<5 {
            var bad = manifest
            switch kind { case 0: bad.schemaVersion = 2
            case 1: bad.minimumOS = "99.0"
            case 2: bad.backendVersion.minimum = "99.0.0"
            case 3: bad.files.append(bad.files[0])
            default: bad.modelId = ".." }
            do { try await store.install(manifest: bad, source: source); XCTFail("invalid manifest accepted") } catch { }
        }
    }
    func testRejectsUnexpectedMissingAndSymlinkFiles() async throws {
        let (root, source, manifest) = try environment(); defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let extra = source.appendingPathComponent("extra")
        try Data().write(to: extra)
        do { try await store.install(manifest: manifest, source: source); XCTFail("extra") } catch { }
        try FileManager.default.removeItem(at: extra)
        let weights = source.appendingPathComponent("weights.bin")
        try FileManager.default.moveItem(at: weights, to: root.appendingPathComponent("outside"))
        try FileManager.default.createSymbolicLink(at: weights, withDestinationURL: root.appendingPathComponent("outside"))
        do { try await store.install(manifest: manifest, source: source); XCTFail("symlink") } catch { }
        try FileManager.default.removeItem(at: weights)
        do { try await store.install(manifest: manifest, source: source); XCTFail("missing") } catch { }
    }
    func testRejectsInvalidMetadataAndVersions() async throws {
        let (root, _, manifest) = try environment(); defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let base = try JSONSerialization.jsonObject(with: JSONEncoder().encode(manifest)) as! [String: Any]
        var invalid: [[String: Any]] = []
        for field in ["upstreamURL", "licenseURL"] {
            for url in ["http://models.example/info", "https://user:password@models.example/info", "file:///tmp/info"] {
                var item = base; item[field] = url; invalid.append(item)
            }
        }
        for memory in [-1, 0] {
            var item = base; var resources = item["resources"] as! [String: Any]
            resources["measuredMemoryBytes"] = memory
            resources["memoryMeasurementContext"] = memory < 0 ? "measured" : "   "
            item["resources"] = resources; invalid.append(item)
        }
        for version in ["", "+15.0"] { var item = base; item["minimumOS"] = version; invalid.append(item) }
        for object in invalid {
            let candidate = try JSONDecoder().decode(ModelManifest.self, from: JSONSerialization.data(withJSONObject: object))
            do { try await store.validate(candidate); XCTFail("invalid metadata accepted: \(object)") } catch { }
        }
    }
}
