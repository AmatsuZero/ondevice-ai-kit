import Foundation
import XCTest
@testable import ODAIKitIdentity

final class IdentityContractsTests: XCTestCase {
    func testReferencesRejectEmptyAndForgedMetadata() throws {
        XCTAssertThrowsError(try CandidateRef(id: "", storeId: "store"))
        let issued = try CandidateRef(id: "candidate", storeId: "store")
        let forged = try CandidateRef(id: "candidate", storeId: "other")
        XCTAssertThrowsError(try forged.validate(against: issued))
        XCTAssertNoThrow(try issued.validate(against: issued))
        XCTAssertThrowsError(try GalleryRef(id: "gallery", revision: ""))
    }
    func testGrantUsesClockScopePurposeAndAuthoritativeEpoch() throws {
        let grant = try AuthorizationGrant(grantId: "g1", revocationDomainId: "domain", scope: [.verification], purpose: "auth", issuedAtMillis: 900, expiresAtMillis: 1100, authorizationEpoch: 3, evidenceRef: "e1")
        XCTAssertNoThrow(try grant.validate(scope: .verification, purpose: "auth", epoch: 3, clock: Clock(nowMillis: 1000)))
        for (scope, purpose, epoch, now, code) in [
            (IdentityScope.enrollment,"auth",Int64(3),Int64(1000),IdentityErrorCode.authorizationRequired),
            (.verification,"other",3,1000,.authorizationRequired),
            (.verification,"auth",3,899,.authorizationRequired),
            (.verification,"auth",3,1100,.authorizationExpired),
            (.verification,"auth",4,1000,.authorizationRevoked),
            (.verification,"auth",2,1000,.authorizationRevoked)
        ] {
            XCTAssertThrowsError(try grant.validate(scope: scope, purpose: purpose, epoch: epoch, clock: Clock(nowMillis: now))) {
                XCTAssertEqual(($0 as? IdentityError)?.code, code)
            }
        }
        XCTAssertThrowsError(try AuthorizationGrant(grantId: "g", revocationDomainId: "d", scope: [.verification], purpose: "auth", issuedAtMillis: 1000, expiresAtMillis: 900, authorizationEpoch: 0, evidenceRef: "e"))
    }
    func testRegistryRejectsDuplicatesAndNeverFallsBack() throws {
        let registry = try IdentityBackendRegistry(defaultBackendId: "system", registrations: [
            IdentityBackendRegistration(backendId: "system", backend: 1, available: true),
            IdentityBackendRegistration(backendId: "optional", backend: 2, available: false)
        ])
        XCTAssertEqual(try registry.select(.default), 1)
        XCTAssertThrowsError(try registry.select(.explicit("missing"))) { XCTAssertEqual(($0 as? IdentityError)?.code, .backendNotRegistered) }
        XCTAssertThrowsError(try registry.select(.explicit("optional"))) { XCTAssertEqual(($0 as? IdentityError)?.code, .backendUnavailable) }
        XCTAssertThrowsError(try IdentityBackendRegistry(defaultBackendId: "x", registrations: [IdentityBackendRegistration(backendId: "x", backend: 1), IdentityBackendRegistration(backendId: "x", backend: 2)]))
    }
    func testScoreFieldsRejectPartialOrNonFiniteValuesAndKeepNulls() throws {
        XCTAssertEqual(try IdentityScoreFields().canonicalJSON(), #"{"metric":null,"score":null,"threshold":null}"#)
        XCTAssertThrowsError(try IdentityScoreFields(score: 0.8))
        XCTAssertThrowsError(try IdentityScoreFields(score: .nan, metric: "cosine", threshold: 0.7))
        XCTAssertThrowsError(try IdentityScoreFields(score: 0.8, metric: "cosine", threshold: .infinity))
    }
    func testCanonicalFixtureAndStableErrorsMatchSharedFile() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let fixture = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("fixtures/contracts/face-identity.json"))) as! [String: Any]
        XCTAssertEqual(Set(IdentityErrorCode.allCases.map(\.rawValue)), Set(fixture["errorCodes"] as! [String]))
        let candidate = try CandidateRef(id: "candidate-1", storeId: "store-1")
        XCTAssertEqual(candidate.canonicalJSON(), fixture["candidateJSON"] as? String)
        XCTAssertEqual(IdentityBackendSelection.default.canonicalJSON(), fixture["defaultSelectionJSON"] as? String)
        XCTAssertEqual(try IdentityScoreFields().canonicalJSON(), fixture["nullScoreJSON"] as? String)
        XCTAssertEqual(try CandidateRef.decodeJSON(candidate.canonicalJSON()), candidate)
        XCTAssertThrowsError(try CandidateRef.decodeJSON(#"{"id":"candidate-1","storeId":"store-1","score":1}"#))
        XCTAssertThrowsError(try CandidateRef.decodeJSON(#"{"id":"","storeId":"store-1"}"#))
    }
    func testTemplateRequiresExactIssuedSpaceEpochAndRevision() throws {
        let space = try makeSpace(revision: "model-v1")
        let issued = try TemplateRef(id: "t1", embeddingSpaceId: space, authorizationEpoch: 3, revision: "r1")
        XCTAssertNoThrow(try issued.validate(against: issued))
        let otherSpace = try makeSpace(revision: "model-v2")
        XCTAssertThrowsError(try TemplateRef(id: "t1", embeddingSpaceId: otherSpace, authorizationEpoch: 3, revision: "r1").validate(against: issued)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .embeddingSpaceMismatch)
        }
        XCTAssertThrowsError(try TemplateRef(id: "t1", embeddingSpaceId: space, authorizationEpoch: 4, revision: "r1").validate(against: issued)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked)
        }
        XCTAssertThrowsError(try TemplateRef(id: "t1", embeddingSpaceId: space, authorizationEpoch: 3, revision: "r2").validate(against: issued))
        XCTAssertThrowsError(try TemplateRef(id: "t1", embeddingSpaceId: space, authorizationEpoch: -1, revision: "r1"))
        XCTAssertEqual(try TemplateRef.decodeJSON(issued.canonicalJSON()), issued)
        let forged = issued.canonicalJSON().dropLast() + #","score":0.99}"#
        XCTAssertThrowsError(try TemplateRef.decodeJSON(String(forged)))
    }
    func testEmbeddingSpaceAndModelBindingRejectUnsupportedOrEmptyValues() throws {
        XCTAssertNotEqual(try makeSpace(revision: "v1"), try makeSpace(revision: "v2"))
        XCTAssertThrowsError(try makeSpace(revision: "v1", dimension: 0))
        XCTAssertThrowsError(try ModelBinding(kind: .system, id: "system", revision: ""))
        let system = try ModelBinding(kind: .system, id: "system", revision: "runtime-os-build")
        XCTAssertEqual(try ModelBinding.decodeJSON(system.canonicalJSON()), system)
        XCTAssertThrowsError(try EmbeddingSpaceId.decodeJSON(try makeSpace(revision: "v1").canonicalJSON().replacingOccurrences(of: #""featureFormatVersion":"1""#, with: #""featureFormatVersion":"999""#)))
    }
    func testDeadlineUsesOnlyInjectedMonotonicClockAndRejectsOverflow() throws {
        let deadline = try IdentityDeadline(clock: Mono(nowNanos: 100), durationNanos: 50)
        XCTAssertFalse(deadline.isExpired(clock: Mono(nowNanos: 149)))
        XCTAssertTrue(deadline.isExpired(clock: Mono(nowNanos: 150)))
        XCTAssertThrowsError(try IdentityDeadline(clock: Mono(nowNanos: Int64.max), durationNanos: 1))
        XCTAssertThrowsError(try IdentityDeadline(clock: Mono(nowNanos: 100), durationNanos: 0))
    }
    func testIndependentPolicyOwnershipAndProofReferencesValidateBinding() throws {
        let threshold = try ThresholdPolicyRef(id: "verify", revision: "v1")
        let pad = try PADPolicyRef(id: "pad", revision: "v1")
        XCTAssertEqual(try ThresholdPolicyRef.decodeJSON(threshold.canonicalJSON()), threshold)
        XCTAssertEqual(try PADPolicyRef.decodeJSON(pad.canonicalJSON()), pad)
        XCTAssertThrowsError(try PADPolicyRef(id: "pad", revision: ""))
        XCTAssertThrowsError(try OwnershipAuthorization(storeId: "", evidenceRef: "e"))
        let ownership = try OwnershipAuthorization(storeId: "store", evidenceRef: "e")
        XCTAssertEqual(ownership.storeId, "store")
        let binding = try ModelBinding(kind: .model, id: "fixture", revision: "v1")
        XCTAssertThrowsError(try LivenessProof(id: "proof", issuerId: "store", sessionId: "session", nonce: "nonce", purpose: "auth", captureDigest: "not-a-digest", policy: pad, modelBinding: binding, authorizationEpoch: 1, issuedAtMillis: 100, expiresAtMillis: 200))
    }
    func testWireRejectsUnknownCapabilitiesAndCoercedIntegers() throws {
        let space = try makeSpace(revision: "v1")
        XCTAssertThrowsError(try EmbeddingSpaceId.decodeJSON(space.canonicalJSON().replacingOccurrences(of: #""identification""#, with: #""liveness""#)))
        let template = try TemplateRef(id: "t", embeddingSpaceId: space, authorizationEpoch: 1, revision: "r")
        for value in ["true", "1.5", "1.0", "1e0", "\"1\"", "9223372036854775808"] {
            XCTAssertThrowsError(try TemplateRef.decodeJSON(template.canonicalJSON().replacingOccurrences(of: #""authorizationEpoch":1"#, with: #""authorizationEpoch":"# + value)))
        }
        for json in [#"{"id":"a","storeId":"s"} trailing"#, #"{"id":"a","storeId":1}"#, #"{"id":"a","storeId":"s","issuer":"forged"}"#] {
            XCTAssertThrowsError(try CandidateRef.decodeJSON(json))
        }
        XCTAssertThrowsError(try CandidateRef.decodeJSON(#"{"id":"forged","id":"a","storeId":"s"}"#))
        XCTAssertThrowsError(try TemplateRef.decodeJSON(template.canonicalJSON().replacingOccurrences(of: #""dimension":2"#, with: #""dimension":2.0"#)))
    }
    func testDeadlineCapturesClockOnceAndProofRejectsNegativeWallTime() throws {
        let clock = AdvancingMono()
        let deadline = try IdentityDeadline(clock: clock, durationNanos: 50)
        XCTAssertTrue(deadline.isExpired(clock: Mono(nowNanos: 150)))
        XCTAssertThrowsError(try LivenessProof(id: "proof", issuerId: "store", sessionId: "session", nonce: "nonce", purpose: "auth", captureDigest: String(repeating: "a", count: 64), policy: PADPolicyRef(id: "pad", revision: "v1"), modelBinding: ModelBinding(kind: .model, id: "fixture", revision: "v1"), authorizationEpoch: 1, issuedAtMillis: -1, expiresAtMillis: 200))
    }
    func testCompleteWireFixturesAndAllErrorEnvelopes() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let fixture = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("fixtures/contracts/face-identity.json"))) as! [String: Any]
        let space = try makeSpace(revision: "v1")
        let template = try TemplateRef(id: "template-1", embeddingSpaceId: space, authorizationEpoch: Int64.max, revision: "r1")
        XCTAssertEqual(template.canonicalJSON(), fixture["templateJSON"] as? String)
        XCTAssertEqual(try TemplateRef.decodeJSON(template.canonicalJSON()), template)
        let grantJSON = fixture["grantJSON"] as! String
        let grant = try AuthorizationGrant.decodeJSON(grantJSON)
        XCTAssertEqual(grant.canonicalJSON(), grantJSON)
        XCTAssertNoThrow(try grant.validate(scope: .verification, purpose: "auth", epoch: Int64.max, clock: Clock(nowMillis: 1000)))
        XCTAssertEqual(try GalleryRef.decodeJSON(fixture["galleryJSON"] as! String).canonicalJSON(), fixture["galleryJSON"] as? String)
        XCTAssertEqual(try IdentityBackendSelection.decodeJSON(fixture["explicitSelectionJSON"] as! String), .explicit("optional"))
        XCTAssertThrowsError(try IdentityBackendSelection.decodeJSON(#"{"backendId":"optional","kind":"default"}"#))
        let proofJSON = fixture["proofJSON"] as! String
        let proof = try LivenessProof.decodeJSON(proofJSON)
        XCTAssertEqual(proof.canonicalJSON(), proofJSON)
        XCTAssertEqual(proof.authorizationEpoch, Int64.max)
        let errors = fixture["errorJSON"] as! [String: String]
        for code in IdentityErrorCode.allCases {
            let error = IdentityError(code)
            XCTAssertEqual(error.canonicalJSON(), errors[code.rawValue])
            XCTAssertEqual(try IdentityError.decodeJSON(error.canonicalJSON()), error)
        }
        let resolved = IdentityError(.thresholdUnverified, backendId: "fixture", modelBinding: space.modelBinding, policyId: "p1", sessionId: "s1")
        XCTAssertEqual(try IdentityError.decodeJSON(resolved.canonicalJSON()), resolved)
    }
}
private struct Clock: IdentityWallClock { let nowMillis: Int64 }
private struct Mono: IdentityMonotonicClock { let nowNanos: Int64 }
private final class AdvancingMono: IdentityMonotonicClock, @unchecked Sendable {
    private let lock = NSLock()
    private var value: Int64 = 100
    var nowNanos: Int64 { lock.lock(); defer { value += 1; lock.unlock() }; return value }
}
private func makeSpace(revision: String, dimension: Int = 2) throws -> EmbeddingSpaceId {
    try EmbeddingSpaceId(supportedCapabilities: [.verification, .identification], backendId: "fixture", backendVersion: "1", modelBinding: ModelBinding(kind: .model, id: "fixture", revision: revision), featureFormatVersion: "1", dimension: dimension, dtype: .float32, endianness: .little, normalization: "l2", preprocessRevision: "p1", scoreMetric: "cosine", scoreDirection: .higherIsBetter)
}
