import XCTest
import CryptoKit
import ODAIKitCore
@testable import ODAIKitIdentity

final class FaceIdentificationTests: XCTestCase {
    func testAggregateBeforeTopKPreservesTheSecondDistinctCandidate() throws {
        let snapshot = try snapshot([("t1", "A"), ("t2", "A"), ("t3", "B"), ("t4", "C")])
        let values = try rankIdentificationCandidates(snapshot: snapshot, scores: ["t1": 0.9, "t2": 0.8, "t3": 0.7, "t4": 0.6], topK: 2)
        XCTAssertEqual(values.map { $0.candidateRef.id }, ["A", "B"])
        XCTAssertEqual(values.map { $0.score }, [0.9, 0.7])
        XCTAssertEqual(values.map { $0.rank }, [1, 2])
    }

    func testThresholdMetadataRejectsNonTokenMetric() {
        XCTAssertThrowsError(try IdentificationThresholdFields(metric: "cosine score", threshold: 0.7)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
        }
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

extension FaceIdentificationTests {
    func testIndependentQueryDomainEpochDoesNotReplaceGalleryPolicyEpoch() async throws {
        let f = try await engineFixture(); let engine = try f.engine()
        let query = try AuthorizationGrant(grantId: "query-new", revocationDomainId: "independent-query-domain",
            scope: [.identification], purpose: "identity", issuedAtMillis: 0, expiresAtMillis: nil,
            authorizationEpoch: 7, evidenceRef: "fixture")
        let task = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: query,
            options: IdentificationOptions(revision: "1"))
        let result = try await task.result()
        XCTAssertEqual(result.outcome, .candidates)
        XCTAssertEqual(result.galleryRevision, f.gallery.revision)
        XCTAssertEqual(result.thresholdPolicyId, "identify-policy")
        await engine.close(); await f.close()
    }

    func testEngineAggregatesTemplatesAndReportsFixedSnapshotConditions() async throws {
        let f = try await engineFixture()
        let engine = try f.engine()
        let task = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(topK: 2, revision: "search-1"))
        let result = try await task.result()
        XCTAssertEqual(result.outcome, .candidates)
        XCTAssertEqual(result.candidates.map { $0.candidateRef }, [f.candidates[0], f.candidates[1]])
        XCTAssertEqual(result.candidates.map { $0.rank }, [1, 2])
        XCTAssertEqual(result.galleryRevision, f.gallery.revision)
        XCTAssertEqual(result.galleryContentDigest, f.gallery.revision)
        XCTAssertEqual(result.galleryCandidateCount, 2)
        XCTAssertEqual(result.galleryTemplateCount, 3)
        XCTAssertEqual(result.enrollmentCounts.map { $0.templateCount }.sorted(), [1, 2])
        XCTAssertEqual(result.fpirFnirGrouping.datasetRevision, "synthetic-only")
        XCTAssertEqual(result.fpirFnirGrouping.operatingPoint, "fixture")
        XCTAssertEqual(result.fpirFnirGrouping.topK, 2)
        XCTAssertEqual(result.thresholdPolicyId, "identify-policy")
        XCTAssertEqual(result.thresholdPolicyRevision, "1")
        XCTAssertEqual(result.aggregationPolicyId, "subject-max-v1")
        XCTAssertEqual(result.optionsRevision, "search-1")
        XCTAssertEqual(result.preprocessingRevision, f.base.embeddingSpace.preprocessRevision)
        XCTAssertEqual(result.modelBinding, f.base.modelBinding)
        XCTAssertEqual(result.backendId, f.base.backendId)
        XCTAssertEqual(result.sessionId, task.runId)
        XCTAssertEqual(result.durationNanos, 0)
        XCTAssertEqual(task.terminal, .completed)
        _ = try await f.identity.revokeGrant(f.origin.grantId, ownership: f.owner)
        task.cancel(); await task.close()
        let again = try await task.result()
        XCTAssertEqual(again.candidates.count, 2)
        XCTAssertEqual(task.terminal, .completed)
        await engine.close(); await f.close()
    }

    func testUnknownHasNoBelowThresholdCandidateOrScoreLeak() async throws {
        let f = try await engineFixture(); let engine = try f.engine()
        let task = try await engine.identify(probe: IdentityProbe(fixtureID: "unknown", vector: [-1, 0]),
            galleryRef: f.gallery, grant: f.query, options: IdentificationOptions(revision: "1"))
        let result = try await task.result()
        XCTAssertEqual(result.outcome, .unknown); XCTAssertTrue(result.candidates.isEmpty)
        XCTAssertNil(result.scoreFields.score)
        XCTAssertEqual(result.thresholdFields?.metric, "cosine")
        XCTAssertEqual(result.thresholdFields?.threshold, 0.7)
        XCTAssertEqual(result.enrollmentCounts.count, 2)
        await engine.close(); await f.close()
    }

    func testQualityRejectionSkipsEmbeddingAndKeepsAllDecisionFieldsNull() async throws {
        let f = try await engineFixture()
        let backend = IdentificationTestBackend(base: f.base, quality: try IdentityQuality(faceCount: 1, score: 0.1))
        let engine = try f.engine(backend)
        let task = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1"))
        let result = try await task.result()
        XCTAssertEqual(result.outcome, .insufficientQuality)
        XCTAssertTrue(result.candidates.isEmpty); XCTAssertNil(result.scoreFields.score)
        XCTAssertNil(result.scoreFields.metric); XCTAssertNil(result.scoreFields.threshold)
        XCTAssertNil(result.thresholdFields)
        XCTAssertEqual(result.enrollmentCounts.count, 2); XCTAssertFalse(backend.didEmbed)
        await engine.close(); await f.close()
    }

    func testMultiFaceAndUnsupportedIndexFailWithResolvedMetadataAndReleaseRun() async throws {
        let f = try await engineFixture(payload: Data([1]))
        let backend = IdentificationTestBackend(base: f.base, quality: try IdentityQuality(faceCount: 2, score: 1))
        let engine = try f.engine(backend)
        let multi = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1"))
        do { _ = try await multi.result(); XCTFail("multi-face accepted") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .multipleFaces); XCTAssertEqual(error.backendId, f.base.backendId) }
        await engine.close()
        let next = try f.engine()
        let unsupported = try await next.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1"))
        do { _ = try await unsupported.result(); XCTFail("arbitrary index interpreted") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .featureFormatUnsupported); XCTAssertEqual(error.policyId, "identify-policy") }
        await next.close(); await f.close()
    }

    func testInvalidOptionsExplicitBackendPolicyAndSpaceNeverFallback() async throws {
        for count in [0, 33] { XCTAssertThrowsError(try IdentificationOptions(topK: count, revision: "1")) }
        let f = try await engineFixture()
        let other = try FixtureIdentityBackend.system(revision: "different-space")
        let unavailable = try FixtureIdentityBackend.optionalUnavailable()
        let engine = try FaceIdentification(identityStore: f.identity, indexStore: f.index, defaultBackendId: f.base.backendId,
            backends: [f.base, unavailable], monotonicClock: IdentificationTestMono())
        for selection in [IdentityBackendSelection.explicit("missing"), .explicit(unavailable.backendId)] {
            do { _ = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
                options: IdentificationOptions(revision: "1"), backend: selection); XCTFail("explicit fallback") }
            catch let error as IdentityError { XCTAssertTrue([IdentityErrorCode.backendNotRegistered, .backendUnavailable].contains(error.code)) }
        }
        let wrongPolicy = try IdentificationOptions(policy: ThresholdPolicyRef(id: "identify-policy", revision: "2"), revision: "1")
        do { _ = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query, options: wrongPolicy); XCTFail("policy drift") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .thresholdUnverified) }
        await engine.close()
        let mismatch = try f.engine(other)
        do { _ = try await mismatch.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1")); XCTFail("space drift") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .embeddingSpaceMismatch) }
        await mismatch.close(); await f.close()
    }

    func testResolvedBindingFailureIncludesSafeBackendMetadata() async throws {
        let f = try await engineFixture()
        let backend = IdentificationTestBackend(base: f.base, declaredId: "incompatible.fixture")
        let engine = try f.engine(backend)
        do {
            _ = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
                options: IdentificationOptions(revision: "1"))
            XCTFail("inconsistent binding accepted")
        } catch let error as IdentityError {
            XCTAssertEqual(error.code, .embeddingSpaceMismatch)
            XCTAssertEqual(error.backendId, backend.backendId)
            XCTAssertEqual(error.modelBinding, backend.modelBinding)
            XCTAssertNil(error.sessionId)
        }
        await engine.close(); await f.close()
    }

    func testDeletedGalleryAndEmptyBuilderAreStableErrors() async throws {
        let f = try await engineFixture()
        let empty = try await f.index.createGalleryBuilder(grant: f.origin, policy: f.policy)
        do { _ = try await empty.publish(); XCTFail("empty gallery") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .invalidInput) }
        await empty.close()
        _ = try await f.index.deleteGallery(f.gallery, ownership: f.owner)
        let engine = try f.engine()
        do { _ = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1")); XCTFail("deleted gallery") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .galleryRevisionGone) }
        await engine.close(); await f.close()
    }

    func testCancelAndOriginRevokeStopBackendAndChooseFinalGateWinner() async throws {
        for revoke in [false, true] {
            let f = try await engineFixture()
            let backend = IdentificationTestBackend(base: f.base, blocks: true)
            let engine = try f.engine(backend)
            let task = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
                options: IdentificationOptions(revision: "1"))
            XCTAssertEqual(backend.entered.wait(timeout: .now() + 2), .success)
            if revoke { _ = try await f.identity.revokeGrant(f.origin.grantId, ownership: f.owner) }
            else { task.cancel() }
            do { _ = try await task.result(); XCTFail("stopped result published") }
            catch let error as IdentityError { XCTAssertEqual(error.code, revoke ? .authorizationRevoked : .cancelled) }
            XCTAssertEqual(task.terminal, revoke ? .failed : .cancelled)
            await task.close(); await engine.close(); await f.close()
        }
    }

    func testFinalProviderCancellationSuppressesPublicationAndCloseIsIdempotent() async throws {
        let f = try await engineFixture(); let backend = IdentificationTestBackend(base: f.base, blocks: true)
        let engine = try f.engine(backend)
        let task = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1"))
        XCTAssertEqual(backend.entered.wait(timeout: .now() + 2), .success)
        f.authority.onNextQuery { task.cancel() }
        backend.release.signal()
        do { _ = try await task.result(); XCTFail("cancelled at final gate") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .cancelled) }
        await engine.close(); await engine.close()
        XCTAssertEqual(backend.closeCount, 1)
        await f.close()
    }

    func testMemberRevokeOrGalleryDeleteInvalidatesWholeRunningSnapshot() async throws {
        for delete in [false, true] {
            let f = try await engineFixture(); let backend = IdentificationTestBackend(base: f.base, blocks: true)
            let engine = try f.engine(backend)
            let task = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
                options: IdentificationOptions(revision: "1"))
            XCTAssertEqual(backend.entered.wait(timeout: .now() + 2), .success)
            if delete { _ = try await f.index.deleteGallery(f.gallery, ownership: f.owner) }
            else { _ = try await f.identity.revokeGrant("member-0", ownership: f.owner) }
            do { _ = try await task.result(); XCTFail("invalid snapshot published") }
            catch let error as IdentityError { XCTAssertEqual(error.code, .authorizationRevoked) }
            XCTAssertEqual(task.terminal, .failed)
            await engine.close(); await f.close()
        }
    }

    func testBusyEngineCloseStopsRunAndDrainsReservedNativeCallback() async throws {
        let f = try await engineFixture()
        let stopped = expectation(description: "native stop entered")
        let permit = DispatchSemaphore(value: 0)
        let backend = IdentificationTestBackend(base: f.base, blocks: true, onStop: {
            stopped.fulfill(); _ = permit.wait(timeout: .now() + 3)
        })
        let engine = try f.engine(backend)
        let task = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1"))
        XCTAssertEqual(backend.entered.wait(timeout: .now() + 2), .success)
        do { _ = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1")); XCTFail("overlapping run") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .busy) }
        let closed = expectation(description: "engine closed")
        let closing = Task { await engine.close(); closed.fulfill() }
        await fulfillment(of: [stopped], timeout: 2)
        XCTAssertEqual(backend.closeCount, 0)
        permit.signal(); await closing.value
        await fulfillment(of: [closed], timeout: 2)
        do { _ = try await task.result(); XCTFail("closed run published") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .closed) }
        await engine.close(); XCTAssertEqual(backend.closeCount, 1)
        await f.close()
    }

    func testCloseWaitsForAdmissionAndStopsTheAdmittedTaskBeforeBackendWork() async throws {
        let f = try await engineFixture(); let backend = IdentificationTestBackend(base: f.base)
        let engine = try f.engine(backend)
        let entered = expectation(description: "query admission")
        let release = DispatchSemaphore(value: 0)
        f.authority.onNextQuery { entered.fulfill(); _ = release.wait(timeout: .now() + 3) }
        let admitting = Task { try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
            options: IdentificationOptions(revision: "1")) }
        await fulfillment(of: [entered], timeout: 2)
        let closing = Task { await engine.close() }
        // Observe the engine's closed admission boundary before releasing the provider.
        for _ in 0..<100 {
            do { _ = try await engine.identify(probe: f.probe, galleryRef: f.gallery, grant: f.query,
                options: IdentificationOptions(revision: "1")); XCTFail("second admission") }
            catch let error as IdentityError { if error.code == .closed { break }; XCTAssertEqual(error.code, .busy) }
            await Task.yield()
        }
        XCTAssertEqual(backend.closeCount, 0)
        release.signal()
        let task = try await admitting.value
        await closing.value
        do { _ = try await task.result(); XCTFail("closed admission result") }
        catch let error as IdentityError { XCTAssertEqual(error.code, .closed) }
        XCTAssertFalse(backend.didEmbed); XCTAssertEqual(backend.closeCount, 1)
        await f.close()
    }

    private func engineFixture(payload: Data? = nil) async throws -> IdentificationEngineFixture {
        let f = try await IdentificationEngineFixture(payload: payload)
        addTeardownBlock { try? FileManager.default.removeItem(at: f.root) }
        return f
    }
}

private struct IdentificationEngineFixture {
    let root: URL; let identity: IdentityStore; let index: IndexStore
    let base: FixtureIdentityBackend; let authority: IdentificationTestAuthority
    let origin: AuthorizationGrant; let query: AuthorizationGrant; let owner: OwnershipAuthorization
    let candidates: [CandidateRef]; let gallery: GalleryRef; let policy: ThresholdPolicy
    var probe: IdentityProbe { get throws { try IdentityProbe(fixtureID: "probe", vector: [1, 0]) } }
    init(payload: Data?) async throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent("identification-\(UUID())")
        let keys = IdentificationTestKeys(); authority = IdentificationTestAuthority()
        identity = try IdentityStore(root: root.appendingPathComponent("identity"), purpose: "identity",
            authorizationProvider: authority, keyProvider: keys, wallClock: IdentificationTestWall(), monotonicClock: IdentificationTestMono())
        base = try FixtureIdentityBackend.system()
        origin = try identificationGrant("origin", "origin-domain")
        query = try identificationGrant("query", "query-domain")
        owner = try OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "owner")
        var candidateValues: [CandidateRef] = []; var templates: [(TemplateRef, AuthorizationGrant)] = []
        for (i, vectors): (Int, [[Float]]) in [[ [1, 0], [0.9, 0.1] ], [ [0.8, 0.6] ]].enumerated() {
            let grant = try identificationGrant("member-\(i)", "member-domain-\(i)")
            let candidate = try await identity.createCandidate(grant); candidateValues.append(candidate)
            for vector in vectors {
                let value = try await identity.enroll(IdentityProbe(fixtureID: "enroll", vector: vector), candidate: candidate,
                    grant: grant, options: EnrollmentOptions(qualityFloor: 0.5, qualityPolicyId: "q", revision: "1"), backend: base)
                templates.append((try XCTUnwrap(value.templateRef), grant))
            }
        }
        candidates = candidateValues
        policy = try ThresholdPolicy(id: "identify-policy", revision: "1", embeddingSpace: base.embeddingSpace,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .verified, kind: .identification,
            threshold: 0.7, qualityFloor: 0.5, operatingPoint: "fixture", datasetRevision: "synthetic-only",
            gallerySizeRange: 1...32, enrollmentRule: "fixture-multiple")
        index = try IndexStore(root: root.appendingPathComponent("index"), identityStore: identity, keyProvider: keys,
            buildIndex: { embeddings, _ in try payload ?? fixtureIdentificationIndex(embeddings) })
        let builder = try await index.createGalleryBuilder(grant: origin, policy: policy)
        for (ref, grant) in templates { try await builder.addTemplate(ref, grant: grant) }
        gallery = try await builder.publish()
    }
    func engine(_ backend: (any IdentityBackend)? = nil) throws -> FaceIdentification {
        let selected = backend ?? base
        return try FaceIdentification(identityStore: identity, indexStore: index, defaultBackendId: selected.backendId,
            backends: [selected], monotonicClock: IdentificationTestMono())
    }
    func close() async { await index.close(); try? await identity.close() }
}
private func identificationGrant(_ id: String, _ domain: String) throws -> AuthorizationGrant {
    try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: [.enrollment, .identification], purpose: "identity",
        issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "fixture")
}
private final class IdentificationTestKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock(); private var keys: [String: SymmetricKey] = [:]
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }; if let key = keys[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256); keys[id] = key; return key
    }
    func deleteKey(id: String) throws { lock.lock(); keys.removeValue(forKey: id); lock.unlock() }
}
private final class IdentificationTestAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    private let lock = NSLock(); private var callback: (@Sendable () -> Void)?
    func onNextQuery(_ value: @escaping @Sendable () -> Void) { lock.lock(); callback = value; lock.unlock() }
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        lock.lock(); let action = grant.grantId == "query" ? callback : nil
        if action != nil { callback = nil }; lock.unlock(); action?(); return grant.authorizationEpoch
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private struct IdentificationTestWall: IdentityWallClock { var nowMillis: Int64 { 100 } }
private struct IdentificationTestMono: IdentityMonotonicClock { var nowNanos: Int64 { 1 } }
private final class IdentificationTestBackend: IdentityBackend, @unchecked Sendable {
    let base: FixtureIdentityBackend; let quality: IdentityQuality; let blocks: Bool
    let entered = DispatchSemaphore(value: 0); let release = DispatchSemaphore(value: 0)
    private let lock = NSLock(); private var embedded = false; private var closes = 0
    private let onStop: (@Sendable () -> Void)?
    private let declaredId: String?
    var didEmbed: Bool { lock.lock(); defer { lock.unlock() }; return embedded }
    var closeCount: Int { lock.lock(); defer { lock.unlock() }; return closes }
    init(base: FixtureIdentityBackend, quality: IdentityQuality = try! IdentityQuality(faceCount: 1, score: 1), blocks: Bool = false,
         onStop: (@Sendable () -> Void)? = nil, declaredId: String? = nil) {
        self.base = base; self.quality = quality; self.blocks = blocks; self.onStop = onStop; self.declaredId = declaredId
    }
    var backendId: String { declaredId ?? base.backendId }; var backendVersion: String { base.backendVersion }
    var modelBinding: ModelBinding { base.modelBinding }; var embeddingSpace: EmbeddingSpaceId { base.embeddingSpace }
    func availability() -> CapabilityAvailability { base.availability() }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { quality }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { try base.embed(probe) }
    func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding {
        let registration = stopSignal.registerStopHandler { self.release.signal(); self.onStop?() }; defer { registration.close() }
        lock.lock(); embedded = true; lock.unlock(); entered.signal()
        if blocks { _ = release.wait(timeout: .now() + 3) }; try stopSignal.check()
        return try base.embed(probe)
    }
    func close() { lock.lock(); closes += 1; lock.unlock() }
}
