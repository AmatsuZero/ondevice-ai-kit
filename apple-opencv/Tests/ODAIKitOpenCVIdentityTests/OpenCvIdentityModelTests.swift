import XCTest
import Foundation
import ODAIKitIdentity
import ODAIKitModelStore
@testable import ODAIKitOpenCVIdentity

final class OpenCvIdentityModelTests: XCTestCase {
    func testManifestIsAcceptedByMatchingStoreAndPoliciesRemainUnverified() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let manifest = try OpenCvIdentityModel.manifest()
        let store = try ModelStore(root: root, compatibility: ModelCompatibility(
            backendId: OpenCvIdentityModel.embeddingSpace.backendId, backendVersion: "4.12.0", osVersion: "15.0", architecture: "arm64"))
        try await store.validate(manifest)
        XCTAssertEqual(manifest.ref, OpenCvIdentityModel.ref)
        for kind in [IdentityPolicyKind.verification, .identification] {
            let policy = try OpenCvIdentityModel.unverifiedPolicy(kind: kind)
            XCTAssertNil(policy.threshold)
            XCTAssertThrowsError(try policy.validate(embeddingSpace: OpenCvIdentityModel.embeddingSpace,
                kind: kind, epoch: 0, gallerySize: kind == .identification ? 2 : nil)) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified)
            }
        }
    }

    func testFeatureNormalizationRejectsBadShapeAndPreservesFiniteExtremes() throws {
        for value in [Float(1), .greatestFiniteMagnitude, .leastNonzeroMagnitude] {
            let normalized = try OpenCvIdentityFeatures.normalized(Array(repeating: value, count: 128))
            XCTAssertEqual(normalized.count, 128)
            XCTAssertTrue(normalized.allSatisfy(\.isFinite))
            XCTAssertEqual(normalized.reduce(0) { $0 + Double($1) * Double($1) }, 1, accuracy: 0.000001)
        }
        for invalid in [[], Array(repeating: Float(0), count: 128), [1, 0], Array(repeating: Float.nan, count: 128)] {
            XCTAssertThrowsError(try OpenCvIdentityFeatures.normalized(invalid)) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
            }
        }
    }
}
