import CryptoKit
import Foundation
import XCTest
@testable import VLMProbe

final class ProbeAssetVerifierTests: XCTestCase {
    private func fixture(path: String = "weight.bin", contents: Data = Data("fixture".utf8)) throws -> (URL, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let model = root.appendingPathComponent("model")
        try FileManager.default.createDirectory(at: model, withIntermediateDirectories: true)
        if path == "weight.bin" {
            try contents.write(to: model.appendingPathComponent(path))
        }
        let digest = SHA256.hash(data: contents).map { String(format: "%02x", $0) }.joined()
        let record: [String: Any] = ["path": path, "bytes": contents.count, "sha256": digest]
        let pinned = VLMManifest.candidate
        let manifest: [String: Any] = [
            "modelId": pinned.modelId, "modelRevision": pinned.modelRevision,
            "runtime": pinned.runtime, "runtimeRevision": pinned.runtimeRevision,
            "platformMin": pinned.platformMin, "status": "verified", "files": [record]
        ]
        let url = root.appendingPathComponent("manifest.json")
        try JSONSerialization.data(withJSONObject: manifest).write(to: url)
        return (url, model)
    }

    func testVerifiedFilesPass() throws {
        let (manifest, model) = try fixture()
        XCTAssertEqual(try ProbeAssetVerifier.verify(manifest: manifest, modelDirectory: model).status, "verified")
    }

    func testSameSizeTamperFailsHash() throws {
        let (manifest, model) = try fixture()
        try Data("wrong!!".utf8).write(to: model.appendingPathComponent("weight.bin"))
        XCTAssertThrowsError(try ProbeAssetVerifier.verify(manifest: manifest, modelDirectory: model)) { error in
            guard case ProbeAssetError.hashMismatch("weight.bin") = error else {
                return XCTFail("unexpected error: \(error)")
            }
        }
    }

    func testTraversalIsRejected() throws {
        let (manifest, model) = try fixture(path: "../weight.bin")
        XCTAssertThrowsError(try ProbeAssetVerifier.verify(manifest: manifest, modelDirectory: model)) { error in
            guard case ProbeAssetError.unsafePath("../weight.bin") = error else {
                return XCTFail("unexpected error: \(error)")
            }
        }
    }
}
