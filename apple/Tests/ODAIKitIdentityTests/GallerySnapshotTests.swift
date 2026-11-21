import XCTest
@testable import ODAIKitIdentity

final class GallerySnapshotTests: XCTestCase {
    func testSharedCanonicalSnapshotsMatchIndependentDigests() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("fixtures/contracts/gallery-snapshots.json"))) as? [String: Any])
        let records = try XCTUnwrap(fixture["records"] as? [[String: String]])
        XCTAssertEqual(records.count, 2)
        let f = try SnapshotFixture()
        var parent: GalleryRef?
        for expected in records {
            let record = try f.record(parent: parent)
            XCTAssertEqual(record.canonicalJSON(), expected["snapshotJSON"])
            XCTAssertEqual(record.contentDigest, expected["contentDigest"])
            XCTAssertEqual(try GallerySnapshotRecord.restore(XCTUnwrap(expected["snapshotJSON"])), record)
            parent = record.ref
        }
    }

    func testCanonicalSnapshotPinsAllPoliciesMembersParentAndDomainEpochs() throws {
        let f = try SnapshotFixture()
        let record = try f.record(members: f.members.reversed())
        let reordered = try f.record()
        XCTAssertEqual(record.canonicalJSON(), reordered.canonicalJSON())
        XCTAssertEqual(record.ref.revision, record.contentDigest)
        XCTAssertEqual(record.domainEpochs, ["operation-domain": 0, "member-a": 0, "member-b": 0])
        let restored = try GallerySnapshotRecord.restore(record.canonicalJSON())
        XCTAssertEqual(restored, record)
        XCTAssertEqual(restored.policy, f.policy)
        XCTAssertEqual(restored.members.map(\.candidate.id), ["candidate-a", "candidate-b"])
        let child = try f.record(parent: record.ref)
        XCTAssertEqual(child.parent, record.ref)
        XCTAssertNotEqual(child.ref.revision, record.ref.revision)
        XCTAssertEqual(child.ref.id, record.ref.id)
    }

    func testStrictRecoveryRejectsDigestEpochMapUnknownFieldsAndDuplicateMembers() throws {
        let f = try SnapshotFixture(); let record = try f.record()
        let mutations: [(inout [String: Any]) -> Void] = [
            { $0["contentDigest"] = String(repeating: "0", count: 64) },
            { $0["domainEpochs"] = ["operation-domain": 0] },
            { $0["unknown"] = "value" },
            { $0["version"] = true },
            { let members = $0["members"] as! [String]; $0["members"] = members + [members[0]] },
            { $0["operationGrant"] = f.members[0].grant.canonicalJSON() }
        ]
        for mutate in mutations {
            var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(record.canonicalJSON().utf8)) as? [String: Any])
            mutate(&fields)
            XCTAssertThrowsError(try GallerySnapshotRecord.restore(identityJSON(fields)))
        }
    }

    func testConstructorRejectsForeignCandidateAndWrongPurposeOrEpoch() throws {
        let f = try SnapshotFixture(); let member = f.members[0]
        let foreign = GallerySnapshotMember(ref: member.ref, candidate: try CandidateRef(id: "candidate", storeId: "other-store"), grant: member.grant)
        XCTAssertThrowsError(try f.record(members: [foreign]))
        let wrongPurpose = try SnapshotFixture.grant("wrong", domain: "member-a", purpose: "other")
        let wrongEpoch = try SnapshotFixture.grant("new", domain: "member-a", epoch: 1)
        for grant in [wrongPurpose, wrongEpoch] {
            XCTAssertThrowsError(try f.record(members: [GallerySnapshotMember(ref: member.ref, candidate: member.candidate, grant: grant)]))
        }
        XCTAssertThrowsError(try f.record(members: [member, member]))
        XCTAssertThrowsError(try f.record(members: []))
        XCTAssertThrowsError(try f.record(parent: GalleryRef(id: "other-lineage", revision: "prior")))
    }

    func testCandidateAndGrantDeclarationsRemainConsistentAcrossAllMembers() throws {
        let f = try SnapshotFixture(); let first = f.members[0]
        let sameCandidateDifferentDomain = GallerySnapshotMember(ref: f.members[1].ref, candidate: first.candidate, grant: f.members[1].grant)
        XCTAssertThrowsError(try f.record(members: [first, sameCandidateDifferentDomain]))
        let duplicateGrantId = try SnapshotFixture.grant(first.grant.grantId, domain: f.members[1].grant.revocationDomainId)
        let conflictingGrant = GallerySnapshotMember(ref: f.members[1].ref, candidate: f.members[1].candidate, grant: duplicateGrantId)
        XCTAssertThrowsError(try f.record(members: [first, conflictingGrant]))
        let secondRef = try TemplateRef(id: "other-template", embeddingSpaceId: f.space, authorizationEpoch: 0, revision: "r2")
        let sameCandidate = GallerySnapshotMember(ref: secondRef, candidate: first.candidate, grant: first.grant)
        let oneSubject = try f.record(members: [first, sameCandidate], policy: SnapshotFixture.policy(space: f.space, range: 1...1))
        XCTAssertEqual(oneSubject.members.count, 2)
        XCTAssertEqual(try GallerySnapshotRecord.restore(oneSubject.canonicalJSON()), oneSubject)
    }

    func testPolicyIsFullyBoundAndUnverifiedOrIncompatiblePublicationFailsClosed() throws {
        let f = try SnapshotFixture(); let original = try f.record()
        let changedPolicy = try SnapshotFixture.policy(space: f.space, threshold: 0.9)
        let changed = try f.record(policy: changedPolicy)
        XCTAssertNotEqual(changed.ref.revision, original.ref.revision)
        let unverified = try ThresholdPolicy(id: "unverified", revision: "1", embeddingSpace: f.space,
            scoreMetric: f.space.scoreMetric, scoreDirection: f.space.scoreDirection, calibrationStatus: .unverified, kind: .identification)
        XCTAssertThrowsError(try f.record(policy: unverified)) { XCTAssertEqual(($0 as? IdentityError)?.code, .thresholdUnverified) }
        let tooSmall = try SnapshotFixture.policy(space: f.space, range: 1...1)
        XCTAssertThrowsError(try f.record(policy: tooSmall))
    }

    func testFiniteThresholdBitPatternsRoundTripAndOversizedRecordFailsBeforePublication() throws {
        let f = try SnapshotFixture()
        for value in [-0.0, -0.5, Double.leastNonzeroMagnitude, Double.greatestFiniteMagnitude] {
            let record = try f.record(policy: SnapshotFixture.policy(space: f.space, threshold: value))
            let restored = try GallerySnapshotRecord.restore(record.canonicalJSON())
            XCTAssertEqual(restored.policy.threshold?.bitPattern, value.bitPattern)
            XCTAssertEqual(restored.canonicalJSON(), record.canonicalJSON())
        }
        let first = f.members[0]
        let members = try (0..<80).map { index in
            GallerySnapshotMember(ref: try TemplateRef(id: "template-\(index)", embeddingSpaceId: f.space, authorizationEpoch: 0, revision: "r1"),
                candidate: first.candidate, grant: first.grant)
        }
        XCTAssertThrowsError(try f.record(members: members)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
    }
}

private struct SnapshotFixture {
    let space: EmbeddingSpaceId
    let policy: ThresholdPolicy
    let operation: AuthorizationGrant
    let members: [GallerySnapshotMember]
    init() throws {
        let space = try FixtureIdentityBackend.system().embeddingSpace
        self.space = space
        policy = try Self.policy(space: space)
        operation = try Self.grant("operation", domain: "operation-domain")
        members = try ["a", "b"].map { key in
            GallerySnapshotMember(ref: try TemplateRef(id: "template-" + key, embeddingSpaceId: space, authorizationEpoch: 0, revision: "r1"),
                candidate: try CandidateRef(id: "candidate-" + key, storeId: "identity-store"),
                grant: try Self.grant("grant-" + key, domain: "member-" + key))
        }
    }
    func record(members: [GallerySnapshotMember]? = nil, parent: GalleryRef? = nil, policy: ThresholdPolicy? = nil) throws -> GallerySnapshotRecord {
        try GallerySnapshotRecord(indexStoreId: "index-store", identityStoreId: "identity-store", lineageId: "lineage", parent: parent,
            members: members ?? self.members, operationGrant: operation, policy: policy ?? self.policy, aggregationPolicyId: "subject-max-v1")
    }
    static func policy(space: EmbeddingSpaceId, threshold: Double = 0.5, range: ClosedRange<Int> = 1...32) throws -> ThresholdPolicy {
        try ThresholdPolicy(id: "identify", revision: "1", embeddingSpace: space, scoreMetric: space.scoreMetric, scoreDirection: space.scoreDirection,
            calibrationStatus: .verified, kind: .identification, threshold: threshold, qualityFloor: 0.8, operatingPoint: "fixture", datasetRevision: "fixture",
            gallerySizeRange: range, enrollmentRule: "one", effectiveAuthorizationEpoch: 0)
    }
    static func grant(_ id: String, domain: String, purpose: String = "identity", epoch: Int64 = 0) throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: [.identification], purpose: purpose,
            issuedAtMillis: 0, expiresAtMillis: 200, authorizationEpoch: epoch, evidenceRef: "audit")
    }
}
