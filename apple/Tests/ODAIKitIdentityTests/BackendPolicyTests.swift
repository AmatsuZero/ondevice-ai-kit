import XCTest
@testable import ODAIKitIdentity

final class BackendPolicyTests: XCTestCase {
    func testExplicitBackendNeverFallsBackAndBindingIsExposed() throws {
        let system = try FixtureIdentityBackend.system()
        let optional = try FixtureIdentityBackend.optionalUnavailable()
        let registry = try IdentityBackendRegistry(defaultBackendId: system.backendId, registrations: [
            IdentityBackendRegistration(backendId: system.backendId, backend: system),
            IdentityBackendRegistration(backendId: optional.backendId, backend: optional, available: false)
        ])
        XCTAssertEqual(try registry.select(.default).backendId, "system.fixture")
        XCTAssertThrowsError(try registry.select(.explicit("optional.fixture"))) { XCTAssertEqual(($0 as? IdentityError)?.code, .backendUnavailable) }
        XCTAssertThrowsError(try registry.select(.explicit("missing.fixture"))) { XCTAssertEqual(($0 as? IdentityError)?.code, .backendNotRegistered) }
        XCTAssertEqual(system.modelBinding.kind, .system)
        XCTAssertFalse(system.modelBinding.revision.isEmpty)
    }
    func testSameBackendWithDifferentModelBindingDoesNotShareEmbeddingSpace() throws {
        let a = try FixtureIdentityBackend.system(revision: "os-a")
        let b = try FixtureIdentityBackend.system(revision: "os-b")
        XCTAssertEqual(a.backendId, b.backendId)
        XCTAssertNotEqual(a.embeddingSpace, b.embeddingSpace)
        let policy = try ThresholdPolicy(id: "verify", revision: "v1", embeddingSpace: a.embeddingSpace, scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .unverified)
        XCTAssertThrowsError(try policy.validate(embeddingSpace: b.embeddingSpace)) { XCTAssertEqual(($0 as? IdentityError)?.code, .embeddingSpaceMismatch) }
    }
    func testUnverifiedPoliciesFailClosedBeforeScoreOrOutcome() throws {
        let backend = try FixtureIdentityBackend.system()
        let threshold = try ThresholdPolicy(id: "verify", revision: "draft", embeddingSpace: backend.embeddingSpace, scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .unverified)
        XCTAssertThrowsError(try threshold.requireVerified()) { XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified) }
        let pad = try PADPolicy(id: "pad", revision: "draft", backendId: backend.backendId, backendVersion: backend.backendVersion, modelBinding: backend.modelBinding, calibrationStatus: .unverified)
        XCTAssertThrowsError(try pad.requireVerified()) { XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified) }
        let probe = try IdentityProbe(fixtureID: "fixture", vector: [0.1, 0.2])
        XCTAssertEqual(try backend.evaluateQuality(probe).faceCount, 1)
        XCTAssertThrowsError(try threshold.validate(embeddingSpace: backend.embeddingSpace))
    }
}

extension BackendPolicyTests {
    func testBackendSpacesMatchSharedWireFixture() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let fixture = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("fixtures/contracts/face-identity.json"))) as! [String: Any]
        let spaces = try XCTUnwrap(fixture["fixtureBackendSpaces"] as? [String: String])
        for backend in [try FixtureIdentityBackend.system(), try FixtureIdentityBackend.optionalUnavailable()] {
            XCTAssertEqual(backend.embeddingSpace.canonicalJSON(), spaces[backend.backendId])
        }
    }
    func testVerifiedPolicyRequiresEvidenceAndExactScopeEpochAndDirection() throws {
        let space = try FixtureIdentityBackend.system().embeddingSpace
        XCTAssertThrowsError(try ThresholdPolicy(id: "p", revision: "v1", embeddingSpace: space, scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .verified))
        XCTAssertThrowsError(try ThresholdPolicy(id: "p", revision: "v1", embeddingSpace: space, scoreMetric: "cosine", scoreDirection: .lowerIsBetter, calibrationStatus: .unverified))
        let policy = try fixtureThreshold(space: space)
        XCTAssertNoThrow(try policy.validate(embeddingSpace: space, kind: .verification, epoch: 3))
        XCTAssertThrowsError(try policy.validate(embeddingSpace: space, kind: .identification, epoch: 3))
        XCTAssertThrowsError(try policy.validate(embeddingSpace: space, kind: .verification, epoch: 4))
        XCTAssertEqual(policy.threshold, 0.8)
        XCTAssertEqual(policy.datasetRevision, "synthetic-v1")
        XCTAssertEqual(policy.qualityFloor, 0.5)
    }
    func testIdentificationPolicyEnforcesGalleryStrata() throws {
        let space = try FixtureIdentityBackend.system().embeddingSpace
        let policy = try fixtureThreshold(space: space, kind: .identification, gallerySizeRange: 2...4)
        XCTAssertNoThrow(try policy.validate(embeddingSpace: space, kind: .identification, epoch: 3, gallerySize: 2))
        for size: Int? in [nil, 0, 1, 5] {
            XCTAssertThrowsError(try policy.validate(embeddingSpace: space, kind: .identification, epoch: 3, gallerySize: size))
        }
    }
    func testPADPolicyEnforcesIndependentBindingAndCategoricalDecisionShape() throws {
        let backend = try FixtureIdentityBackend.system()
        XCTAssertThrowsError(try PADPolicy(id: "pad", revision: "v1", backendId: backend.backendId, backendVersion: backend.backendVersion, modelBinding: backend.modelBinding, calibrationStatus: .verified))
        let policy = try PADPolicy(id: "pad", revision: "v1", backendId: backend.backendId, backendVersion: backend.backendVersion, modelBinding: backend.modelBinding,
                                   calibrationStatus: .verified, mode: .active, challengePolicyRevision: "challenge-v1", minimumFrames: 3, maximumDurationNanos: 1_000,
                                   qualityFloor: 0.5, classificationPolicyRevision: "categorical-fixture-v1", datasetRevision: "synthetic-pad-v1", operatingPoint: "test-only", attackTypes: ["fixture-replay"], effectiveAuthorizationEpoch: 3)
        XCTAssertNoThrow(try policy.validate(backendId: backend.backendId, backendVersion: backend.backendVersion, modelBinding: backend.modelBinding, mode: .active, epoch: 3))
        XCTAssertNil(policy.threshold)
        XCTAssertNil(policy.scoreMetric)
        XCTAssertThrowsError(try policy.validate(backendId: "other", backendVersion: backend.backendVersion, modelBinding: backend.modelBinding, mode: .active, epoch: 3))
        XCTAssertThrowsError(try policy.validate(backendId: backend.backendId, backendVersion: backend.backendVersion, modelBinding: backend.modelBinding, mode: .passive, epoch: 3))
    }
    func testFixtureBackendRejectsImagesWrongDimensionAndClosedUse() throws {
        let backend = try FixtureIdentityBackend.system()
        let probe = try IdentityProbe(fixtureID: "unit-a", vector: [1, 0])
        XCTAssertEqual(try backend.embed(probe).values, [1, 0])
        XCTAssertThrowsError(try backend.embed(IdentityProbe(fixtureID: "wrong", vector: [1])))
        XCTAssertThrowsError(try backend.embed(IdentityProbe(encodedImage: Data([1]), revision: "r1")))
        XCTAssertThrowsError(try IdentityQuality(faceCount: 1, score: .nan))
        XCTAssertThrowsError(try IdentityQuality(faceCount: 1, score: 2))
        backend.close(); backend.close()
        XCTAssertEqual(backend.availability().reason, .closed)
        XCTAssertThrowsError(try backend.embed(probe)) { XCTAssertEqual(($0 as? IdentityError)?.code, .closed) }
    }
}

private func fixtureThreshold(space: EmbeddingSpaceId, kind: IdentityPolicyKind = .verification, gallerySizeRange: ClosedRange<Int>? = nil) throws -> ThresholdPolicy {
    try ThresholdPolicy(id: "fixture-policy", revision: "v1", embeddingSpace: space, scoreMetric: "cosine", scoreDirection: .higherIsBetter,
                        calibrationStatus: .verified, kind: kind, threshold: 0.8, qualityFloor: 0.5, operatingPoint: "test-only", datasetRevision: "synthetic-v1",
                        gallerySizeRange: gallerySizeRange, enrollmentRule: "one-fixture", effectiveAuthorizationEpoch: 3)
}
