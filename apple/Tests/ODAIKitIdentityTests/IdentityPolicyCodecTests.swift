import Foundation
import XCTest
@testable import ODAIKitIdentity

final class IdentityPolicyCodecTests: XCTestCase {
    private func fixture(_ key: String) throws -> String {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let f = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("fixtures/contracts/identity-policies.json"))) as! [String: String]
        return try XCTUnwrap(f[key])
    }
    func testSharedVerifiedIdentificationPolicyPreservesAllFieldsAndExactBits() throws {
        let json = try fixture("thresholdJSON")
        let p = try ThresholdPolicy.decodeJSON(json)
        XCTAssertEqual(json, p.canonicalJSON())
        XCTAssertEqual(p.id, "identify-policy"); XCTAssertEqual(p.revision, "r2")
        XCTAssertEqual(p.kind, .identification); XCTAssertEqual(p.calibrationStatus, .verified)
        XCTAssertEqual(p.threshold?.bitPattern, Double(0.8).bitPattern)
        XCTAssertEqual(p.qualityFloor, 0.625); XCTAssertEqual(p.gallerySizeRange, 1...32)
        XCTAssertEqual(p.datasetRevision, "synthetic-data"); XCTAssertEqual(p.enrollmentRule, "multi-template")
        XCTAssertEqual(p.effectiveAuthorizationEpoch, 7)
        try p.validate(embeddingSpace: p.embeddingSpace, kind: .identification, epoch: 7, gallerySize: 2)
        XCTAssertThrowsError(try p.validate(embeddingSpace: p.embeddingSpace, kind: .identification, epoch: 8, gallerySize: 2)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified)
        }
    }
    func testSharedActivePADPolicyKeepsIndependentBindingAndCaptureConstraints() throws {
        let json = try fixture("padJSON"); let p = try PADPolicy.decodeJSON(json)
        XCTAssertEqual(json, p.canonicalJSON())
        XCTAssertEqual(p.mode, .active); XCTAssertEqual(p.minimumFrames, 3)
        XCTAssertEqual(p.maximumDurationNanos, 9_876_543_210); XCTAssertEqual(p.challengePolicyRevision, "challenge-v2")
        XCTAssertEqual(p.attackTypes, ["print", "replay"]); XCTAssertEqual(p.classificationPolicyRevision, "class-v2")
        XCTAssertNil(p.scoreMetric); XCTAssertNil(p.threshold)
        try p.validate(backendId: p.backendId, backendVersion: p.backendVersion, modelBinding: p.modelBinding, mode: p.mode, epoch: 7)
        XCTAssertThrowsError(try p.validate(backendId: p.backendId, backendVersion: "other", modelBinding: p.modelBinding, mode: p.mode, epoch: 7)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified)
        }
    }
    func testUnverifiedPoliciesStayUnverifiedWithNullCalibrationFields() throws {
        let space = try ThresholdPolicy.decodeJSON(fixture("thresholdJSON")).embeddingSpace
        let p = try ThresholdPolicy(id: "draft", revision: "1", embeddingSpace: space, scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .unverified)
        let restored = try ThresholdPolicy.decodeJSON(p.canonicalJSON())
        XCTAssertEqual(restored, p); XCTAssertNil(restored.threshold)
        XCTAssertThrowsError(try restored.requireVerified()) { XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified) }
        let pad = try PADPolicy(id: "draft-pad", revision: "1", backendId: "fixture.pad", backendVersion: "1", modelBinding: ModelBinding(kind: .system, id: "fixture.pad", revision: "os"), calibrationStatus: .unverified)
        let restoredPAD = try PADPolicy.decodeJSON(pad.canonicalJSON())
        XCTAssertEqual(restoredPAD, pad)
        XCTAssertThrowsError(try restoredPAD.requireVerified()) { XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified) }
    }
    func testCodecRejectsCoercionUnknownFieldsMalformedBitsAndCaptureRanges() throws {
        let identity = try fixture("thresholdJSON"); let pad = try fixture("padJSON")
        for value in ["7.0", "7e0", "true", "\"7\"", "9223372036854775808"] {
            XCTAssertThrowsError(try ThresholdPolicy.decodeJSON(identity.replacingOccurrences(of: "\"effectiveAuthorizationEpoch\":7", with: "\"effectiveAuthorizationEpoch\":" + value)))
            XCTAssertThrowsError(try PADPolicy.decodeJSON(pad.replacingOccurrences(of: "\"effectiveAuthorizationEpoch\":7", with: "\"effectiveAuthorizationEpoch\":" + value)))
        }
        XCTAssertThrowsError(try ThresholdPolicy.decodeJSON(String(identity.dropLast()) + ",\"unknown\":1}"))
        XCTAssertThrowsError(try PADPolicy.decodeJSON(pad.replacingOccurrences(of: "\"minimumFrames\":3", with: "\"minimumFrames\":3,\"minimumFrames\":4")))
        for bits in ["3fe999999999999", "3FE999999999999A", "7ff0000000000000", "7ff8000000000000", "not-hex"] {
            XCTAssertThrowsError(try ThresholdPolicy.decodeJSON(identity.replacingOccurrences(of: "3fe999999999999a", with: bits)))
        }
        XCTAssertThrowsError(try PADPolicy.decodeJSON(pad.replacingOccurrences(of: "[\"print\",\"replay\"]", with: "[\"print\",\"print\"]")))
        XCTAssertThrowsError(try ThresholdPolicy.decodeJSON(identity.replacingOccurrences(of: "\"upper\":32", with: "\"upper\":2147483648")))
        XCTAssertThrowsError(try PADPolicy.decodeJSON(pad.replacingOccurrences(of: "\"minimumFrames\":3", with: "\"minimumFrames\":2147483648")))
    }
}
