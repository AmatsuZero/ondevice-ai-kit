import XCTest
@testable import ODAIKitIdentity

final class FaceIdentificationTests: XCTestCase {
    func testAggregateBeforeTopKPreservesTheSecondDistinctCandidate() throws {
        let snapshot = try snapshot([("t1", "A"), ("t2", "A"), ("t3", "B"), ("t4", "C")])
        let values = try rankIdentificationCandidates(snapshot: snapshot, scores: ["t1": 0.9, "t2": 0.8, "t3": 0.7, "t4": 0.6], topK: 2)
        XCTAssertEqual(values.map { $0.candidateRef.id }, ["A", "B"])
        XCTAssertEqual(values.map { $0.score }, [0.9, 0.7])
        XCTAssertEqual(values.map { $0.rank }, [1, 2])
    }

    func testSubjectMaximumIsIndependentOfTemplateOrder() throws {
        let value = try snapshot([("a-first", "A"), ("z-last", "A"), ("middle", "B")])
        let values = try rankIdentificationCandidates(snapshot: value, scores: ["a-first": 0.5, "z-last": 0.9, "middle": 0.8])
        XCTAssertEqual(values.map { $0.candidateRef.id }, ["A", "B"])
        XCTAssertEqual(values.map { $0.score }, [0.9, 0.8])
    }

    func testEqualScoresUseOpaqueByteOrder() throws {
        let value = try snapshot([("t1", "a"), ("t2", "Z"), ("t3", "A")])
        let values = try rankIdentificationCandidates(snapshot: value, scores: ["t1": 0.9, "t2": 0.9, "t3": 0.9])
        XCTAssertEqual(values.map { $0.candidateRef.id }, ["A", "Z", "a"])
    }

    func testThresholdIsInclusiveAndUnknownLeaksNoBelowThresholdCandidate() throws {
        let value = try snapshot([("t1", "A"), ("t2", "B")])
        let atThreshold = try rankIdentificationCandidates(snapshot: value, scores: ["t1": 0.5, "t2": 0.49])
        XCTAssertEqual(atThreshold.map { $0.candidateRef.id }, ["A"])
        XCTAssertTrue(try rankIdentificationCandidates(snapshot: value, scores: ["t1": -0.2, "t2": 0.49]).isEmpty)
    }

    func testDefaultTopKReturnsAtMost32DistinctCandidates() throws {
        let members = (0..<34).map { ("t-\($0)", String(format: "c%02d", $0)) }
        let value = try snapshot(members)
        let values = try rankIdentificationCandidates(snapshot: value, scores: Dictionary(uniqueKeysWithValues: members.map { ($0.0, 0.9) }))
        XCTAssertEqual(values.count, 32)
        XCTAssertEqual(values.first?.candidateRef.id, "c00")
        XCTAssertEqual(values.last?.candidateRef.id, "c31")
        XCTAssertEqual(values.last?.rank, 32)
    }

    func testInvalidTopKAndIncompleteOrNonfiniteScoresFailClosed() throws {
        let value = try snapshot([("t1", "A")])
        for topK in [0, 33] {
            XCTAssertThrowsError(try rankIdentificationCandidates(snapshot: value, scores: ["t1": 0.9], topK: topK)) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
            }
        }
        for scores: [String: Double] in [[:], ["t1": 0.9, "extra": 0.9], ["t1": .nan], ["t1": .infinity], ["t1": 1.1]] {
            XCTAssertThrowsError(try rankIdentificationCandidates(snapshot: value, scores: scores)) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
            }
        }
    }

    func testUnknownAggregationPolicyNeverUsesAnImplicitFallback() throws {
        let value = try snapshot([("t1", "A")], aggregation: "unsupported-v1")
        XCTAssertThrowsError(try rankIdentificationCandidates(snapshot: value, scores: ["t1": 0.9])) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .unsupportedOption)
        }
    }

    private func snapshot(_ pairs: [(String, String)], aggregation: String = "subject-max-v1") throws -> GallerySnapshotRecord {
        let space = try FixtureIdentityBackend.system().embeddingSpace
        let grant = try AuthorizationGrant(grantId: "grant", revocationDomainId: "domain", scope: [.identification], purpose: "identity",
            issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "fixture")
        let policy = try ThresholdPolicy(id: "identify", revision: "1", embeddingSpace: space,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .verified, kind: .identification,
            threshold: 0.5, qualityFloor: 0.8, operatingPoint: "fixture", datasetRevision: "fixture", gallerySizeRange: 1...64,
            enrollmentRule: "fixture-multiple", effectiveAuthorizationEpoch: 0)
        return try GallerySnapshotRecord(indexStoreId: "index", identityStoreId: "store", lineageId: "gallery", parent: nil,
            members: pairs.map { try GallerySnapshotMember(ref: TemplateRef(id: $0.0, embeddingSpaceId: space, authorizationEpoch: 0, revision: "1"),
                candidate: CandidateRef(id: $0.1, storeId: "store"), grant: grant) }, operationGrant: grant, policy: policy, aggregationPolicyId: aggregation)
    }
}
