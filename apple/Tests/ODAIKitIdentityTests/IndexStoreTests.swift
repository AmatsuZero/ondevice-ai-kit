import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IndexStoreTests: XCTestCase {
    func testImmutablePublishCASReadLeaseAndWholeLineageDelete() async throws {
        let storeRoot = temporaryRoot(); let indexRoot = temporaryRoot()
        let keys = IndexTestKeys(); let identity = try IdentityStore(root: storeRoot, purpose: "identity",
            authorizationProvider: IndexTestAuthority(), keyProvider: keys, wallClock: IndexClock(), monotonicClock: IndexMono())
        let grant = try indexGrant(); let candidate = try await identity.createCandidate(grant)
        let backend = try FixtureIdentityBackend.system()
        let enrolled = try await identity.enroll(IdentityProbe(fixtureID: "fixture", vector: [1, 2]), candidate: candidate,
            grant: grant, options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "q", revision: "1"), backend: backend)
        let template = try XCTUnwrap(enrolled.templateRef)
        let index = try await IndexStore.open(root: indexRoot, identityStore: identity, keyProvider: keys,
                                              buildIndex: { _ in Data([1]) })
        let policy = try ThresholdPolicy(id: "identify", revision: "1", embeddingSpace: template.embeddingSpaceId,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .verified,
            kind: .identification, threshold: 0.5, qualityFloor: 0.8, operatingPoint: "fixture", datasetRevision: "fixture", gallerySizeRange: 1...32, enrollmentRule: "one", effectiveAuthorizationEpoch: 0)
        let builder = try await index.createGalleryBuilder(grant: grant, policy: policy, backend: backend)
        try await builder.addTemplate(template, grant: grant)
        let published = try await builder.publish()
        let summary = try await index.snapshotSummary(published)
        XCTAssertEqual(summary.members.count, 1)
        XCTAssertNil(summary.parent)
        let lease = try await index.acquireIndexReadLease(published, grant: grant)
        let second = try await index.createGalleryBuilder(baseGalleryRef: published, grant: grant, policy: policy, backend: backend)
        let deletion = try await index.deleteGallery(published, ownership: try OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "owner"))
        let pendingStatus = try await index.awaitDeletion(deletion)
        XCTAssertEqual(pendingStatus, .purgePending)
        do {
            _ = try await index.snapshotSummary(published)
            XCTFail("deleted gallery revision remained readable")
        } catch {
            XCTAssertEqual((error as? IdentityError)?.code, .galleryRevisionGone)
        }
        await lease.close()
        let purgedStatus = try await index.awaitDeletion(deletion)
        XCTAssertEqual(purgedStatus, .purged)
        await second.close()
        await index.close()
        let reopened = try await IndexStore.open(root: indexRoot, identityStore: identity, keyProvider: keys, buildIndex: { _ in Data([1]) })
        let repeated = try await reopened.deleteGallery(published, ownership: try OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "owner"))
        XCTAssertEqual(repeated, deletion)
        await reopened.close()
        try await identity.close()
    }

    func testBaseRevisionCASConflictDoesNotConsumeSecondBuilderUntilPublish() async throws {
        let storeRoot = temporaryRoot(); let indexRoot = temporaryRoot(); let keys = IndexTestKeys()
        let identity = try IdentityStore(root: storeRoot, purpose: "identity", authorizationProvider: IndexTestAuthority(), keyProvider: keys,
                                         wallClock: IndexClock(), monotonicClock: IndexMono())
        let grant = try indexGrant(); let candidate = try await identity.createCandidate(grant)
        let backend = try FixtureIdentityBackend.system(); let result = try await identity.enroll(
            IdentityProbe(fixtureID: "fixture", vector: [1, 2]), candidate: candidate, grant: grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "q", revision: "1"), backend: backend)
        let template = try XCTUnwrap(result.templateRef)
        let index = try await IndexStore.open(root: indexRoot, identityStore: identity, keyProvider: keys,
                                              buildIndex: { _ in Data([1]) })
        let policy = try ThresholdPolicy(id: "identify", revision: "1", embeddingSpace: template.embeddingSpaceId,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .verified,
            kind: .identification, threshold: 0.5, qualityFloor: 0.8, operatingPoint: "fixture", datasetRevision: "fixture", gallerySizeRange: 1...32, enrollmentRule: "one", effectiveAuthorizationEpoch: 0)
        let first = try await index.createGalleryBuilder(grant: grant, policy: policy, backend: backend)
        try await first.addTemplate(template, grant: grant)
        let base = try await first.publish()
        let left = try await index.createGalleryBuilder(baseGalleryRef: base, grant: grant, policy: policy, backend: backend)
        let right = try await index.createGalleryBuilder(baseGalleryRef: base, grant: grant, policy: policy, backend: backend)
        let leftRef = try await left.publish(); XCTAssertEqual(leftRef.id, base.id)
        do { _ = try await right.publish(); XCTFail("CAS conflict accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .galleryRevisionConflict) }
        await left.close(); await right.close(); await index.close()
        try await identity.close()
    }

    func testBuildReceivesPinnedEmbeddingAndFailureDoesNotPublishRevision() async throws {
        let storeRoot = temporaryRoot(); let indexRoot = temporaryRoot(); let keys = IndexTestKeys()
        let identity = try IdentityStore(root: storeRoot, purpose: "identity", authorizationProvider: IndexTestAuthority(), keyProvider: keys,
                                         wallClock: IndexClock(), monotonicClock: IndexMono())
        let grant = try indexGrant(); let candidate = try await identity.createCandidate(grant)
        let backend = try FixtureIdentityBackend.system()
        let enrolled = try await identity.enroll(IdentityProbe(fixtureID: "fixture", vector: [1, 2]), candidate: candidate, grant: grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "q", revision: "1"), backend: backend)
        let template = try XCTUnwrap(enrolled.templateRef)
        let index = try await IndexStore.open(root: indexRoot, identityStore: identity, keyProvider: keys, buildIndex: { embeddings in
            XCTAssertEqual(embeddings.count, 1)
            XCTAssertEqual(embeddings[0].embeddingSpace, template.embeddingSpaceId)
            throw IdentityError(.indexUnavailable)
        })
        let policy = try identificationPolicy(template)
        let builder = try await index.createGalleryBuilder(grant: grant, policy: policy, backend: backend)
        try await builder.addTemplate(template, grant: grant)
        do { _ = try await builder.publish(); XCTFail("failed build published a revision") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .indexUnavailable) }
        let current = try await index.currentGallery()
        XCTAssertNil(current)
        do { _ = try await builder.publish(); XCTFail("failed builder was reusable") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .builderClosed) }
        await index.close(); try await identity.close()
    }

    func testDeleteRequiresIdentityStoreOwnershipBeforeTombstoning() async throws {
        let storeRoot = temporaryRoot(); let indexRoot = temporaryRoot(); let keys = IndexTestKeys(); let authority = IndexTestAuthority()
        let identity = try IdentityStore(root: storeRoot, purpose: "identity", authorizationProvider: authority, keyProvider: keys,
            wallClock: IndexClock(), monotonicClock: IndexMono())
        let grant = try indexGrant(); let candidate = try await identity.createCandidate(grant); let backend = try FixtureIdentityBackend.system()
        let enrolled = try await identity.enroll(IdentityProbe(fixtureID: "fixture", vector: [1, 2]), candidate: candidate, grant: grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "q", revision: "1"), backend: backend)
        let template = try XCTUnwrap(enrolled.templateRef)
        let index = try await IndexStore.open(root: indexRoot, identityStore: identity, keyProvider: keys, buildIndex: { _ in Data([1]) })
        let builder = try await index.createGalleryBuilder(grant: grant, policy: try identificationPolicy(template), backend: backend)
        try await builder.addTemplate(template, grant: grant); let published = try await builder.publish()
        authority.rejectOwnership = true
        do { _ = try await index.deleteGallery(published, ownership: try OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "owner")); XCTFail("ownership denial tombstoned gallery") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        let surviving = try await index.snapshotSummary(published)
        XCTAssertEqual(surviving.ref, published)
        await index.close(); try await identity.close()
    }

    func testDeleteStopsExistingReadLeaseButPreservesSharedTemplate() async throws {
        let context = try await context()
        let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        try await builder.addTemplate(context.template, grant: context.grant)
        let ref = try await builder.publish()
        let lease = try await context.index.acquireIndexReadLease(ref, grant: context.grant)
        _ = try await context.index.deleteGallery(ref, ownership: context.owner)
        XCTAssertThrowsError(try lease.snapshot()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        let retained = try await context.identity.acquireTemplateLease(context.template, grant: context.grant,
            scope: .verification, embeddingSpace: context.template.embeddingSpaceId)
        XCTAssertEqual(try retained.embedding().embeddingSpace, context.template.embeddingSpaceId)
        await retained.close(); await lease.close(); await context.index.close(); try await context.identity.close()
    }

    func testDeleteCommitFailureDoesNotCacheAnUncommittedReceipt() async throws {
        let context = try await context()
        let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        try await builder.addTemplate(context.template, grant: context.grant)
        let ref = try await builder.publish()
        context.keys.denied = true
        do { _ = try await context.index.deleteGallery(ref, ownership: context.owner); XCTFail("failed commit returned receipt") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        context.keys.denied = false
        do { _ = try await context.index.deleteGallery(ref, ownership: context.owner); XCTFail("cached uncommitted receipt") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        await context.index.close(); try await context.identity.close()
    }

    func testDeletionCommitsBeforeAnInProgressChildIndexBuildReturns() async throws {
        let entered = expectation(description: "child build entered")
        let deleted = expectation(description: "logical deletion committed while build blocked")
        let gate = IndexBuildGate(entered: entered)
        let context = try await context(buildIndex: { _ in try gate.build() })
        let baseBuilder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        try await baseBuilder.addTemplate(context.template, grant: context.grant)
        let base = try await baseBuilder.publish()
        let child = try await context.index.createGalleryBuilder(baseGalleryRef: base, grant: context.grant, policy: context.policy)
        let publication = Task { try await child.publish() }
        await fulfillment(of: [entered], timeout: 2)
        let deletion = Task { let receipt = try await context.index.deleteGallery(base, ownership: context.owner); deleted.fulfill(); return receipt }
        await fulfillment(of: [deleted], timeout: 1)
        gate.release()
        _ = try await deletion.value
        do { _ = try await publication.value; XCTFail("deleted child build was published") }
        catch { XCTAssertTrue([IdentityErrorCode.authorizationRevoked, .galleryRevisionGone, .cancelled].contains((error as? IdentityError)?.code ?? .invalidInput)) }
        await context.index.close(); try await context.identity.close()
    }

    func testCallerCancellationSuppressesPublicationAfterTheBuildReturns() async throws {
        let entered = expectation(description: "child build entered")
        let gate = IndexBuildGate(entered: entered)
        let context = try await context(buildIndex: { _ in try gate.build() })
        let baseBuilder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        try await baseBuilder.addTemplate(context.template, grant: context.grant); let base = try await baseBuilder.publish()
        let child = try await context.index.createGalleryBuilder(baseGalleryRef: base, grant: context.grant, policy: context.policy)
        let publication = Task { try await child.publish() }
        await fulfillment(of: [entered], timeout: 2)
        publication.cancel(); gate.release()
        do { _ = try await publication.value; XCTFail("caller cancellation published a revision") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        let head = try await context.index.currentGallery(); XCTAssertEqual(head, base)
        await context.index.close(); try await context.identity.close()
    }

    func testCloseSignalsExistingLeaseAndWaitsForItsRelease() async throws {
        let context = try await context()
        let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        try await builder.addTemplate(context.template, grant: context.grant); let ref = try await builder.publish()
        let lease = try await context.index.acquireIndexReadLease(ref, grant: context.grant)
        let stopped = expectation(description: "native index read stopped")
        let registration = lease.registerStopHandler { stopped.fulfill() }
        let closing = Task { await context.index.close() }
        await fulfillment(of: [stopped], timeout: 1)
        XCTAssertThrowsError(try lease.snapshot()) { XCTAssertEqual(($0 as? IdentityError)?.code, .closed) }
        await lease.close(); await closing.value; registration.close(); try await context.identity.close()
    }

    func testReleasingDeletedLineagePurgesItWhileOtherLineageIsStillLeased() async throws {
        let context = try await context()
        var refs: [GalleryRef] = []
        for _ in 0..<2 {
            let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
            try await builder.addTemplate(context.template, grant: context.grant); refs.append(try await builder.publish())
        }
        let first = try await context.index.acquireIndexReadLease(refs[0], grant: context.grant)
        let other = try await context.index.acquireIndexReadLease(refs[1], grant: context.grant)
        let receipt = try await context.index.deleteGallery(refs[0], ownership: context.owner)
        await first.close()
        let status = try await context.index.awaitDeletion(receipt); XCTAssertEqual(status, .purged)
        XCTAssertEqual(try other.snapshot().ref, refs[1])
        await other.close(); await context.index.close(); try await context.identity.close()
    }

    func testBuilderRejectsSecondWriterWhileTemplateAdmissionIsSuspended() async throws {
        let context = try await context()
        let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        let entered = expectation(description: "member authorization entered")
        let release = DispatchSemaphore(value: 0)
        context.authority.onNextEpoch { entered.fulfill(); _ = release.wait(timeout: .now() + 3) }
        let addition = Task { try await builder.addTemplate(context.template, grant: context.grant) }
        await fulfillment(of: [entered], timeout: 1)
        do { _ = try await builder.publish(); XCTFail("second writer accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .busy) }
        release.signal(); try await addition.value
        _ = try await builder.publish()
        await context.index.close(); try await context.identity.close()
    }

    func testEveryConcurrentCloseWaitsUntilTheReadLeaseIsReleased() async throws {
        let context = try await context()
        let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        try await builder.addTemplate(context.template, grant: context.grant); let ref = try await builder.publish()
        let lease = try await context.index.acquireIndexReadLease(ref, grant: context.grant)
        let stopped = expectation(description: "close admission")
        let registration = lease.registerStopHandler { stopped.fulfill() }
        let first = Task { await context.index.close() }
        await fulfillment(of: [stopped], timeout: 1)
        let secondReturned = expectation(description: "second close still draining")
        secondReturned.isInverted = true
        let second = Task { await context.index.close(); secondReturned.fulfill() }
        await fulfillment(of: [secondReturned], timeout: 0.05)
        await lease.close(); await first.value; await second.value
        registration.close(); try await context.identity.close()
    }

    func testNativeStopCallbackCanReenterCloseWithoutHoldingTheIndexActor() async throws {
        let context = try await context()
        let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
        try await builder.addTemplate(context.template, grant: context.grant); let ref = try await builder.publish()
        let lease = try await context.index.acquireIndexReadLease(ref, grant: context.grant)
        let stopped = expectation(description: "callback returned")
        let registration = lease.registerStopHandler {
            let returned = DispatchSemaphore(value: 0)
            Task { await context.index.close(); returned.signal() }
            XCTAssertEqual(returned.wait(timeout: .now() + 0.5), .success)
            stopped.fulfill()
        }
        let closing = Task { await context.index.close() }
        await fulfillment(of: [stopped], timeout: 2)
        await lease.close(); await closing.value; registration.close(); try await context.identity.close()
    }

    func testReadLeaseRetainsStoreUntilItsIdentityTicketIsConsumed() async throws {
        let retained = WeakIndexStore()
        func acquire() async throws -> (IndexReadLease, IdentityStore) {
            let context = try await context()
            retained.value = context.index
            let builder = try await context.index.createGalleryBuilder(grant: context.grant, policy: context.policy)
            try await builder.addTemplate(context.template, grant: context.grant); let ref = try await builder.publish()
            return (try await context.index.acquireIndexReadLease(ref, grant: context.grant), context.identity)
        }
        let (lease, identity) = try await acquire()
        XCTAssertNotNil(retained.value, "lease lost its store before consuming IdentityStore ticket")
        await lease.close()
        // The failed implementation leaks a ticket when the weak store is nil;
        // avoid hanging the entire test process after that assertion.
        if let index = retained.value { await index.close(); try await identity.close() }
    }

    private func context(buildIndex: @escaping GalleryIndexBuilder = { _ in Data([1]) }) async throws -> IndexTestContext {
        let keys = IndexTestKeys(); let authority = IndexTestAuthority()
        let identity = try IdentityStore(root: temporaryRoot(), purpose: "identity", authorizationProvider: authority,
            keyProvider: keys, wallClock: IndexClock(), monotonicClock: IndexMono())
        let grant = try indexGrant(); let candidate = try await identity.createCandidate(grant)
        let result = try await identity.enroll(IdentityProbe(fixtureID: "fixture", vector: [1, 2]), candidate: candidate, grant: grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "q", revision: "1"), backend: FixtureIdentityBackend.system())
        let template = try XCTUnwrap(result.templateRef)
        let index = try await IndexStore.open(root: temporaryRoot(), identityStore: identity, keyProvider: keys, buildIndex: buildIndex)
        return try IndexTestContext(index: index, identity: identity, keys: keys, authority: authority, grant: grant, template: template,
            policy: identificationPolicy(template), owner: OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "owner"))
    }

    private func temporaryRoot() -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        return root
    }
}

private func identificationPolicy(_ template: TemplateRef) throws -> ThresholdPolicy {
    try ThresholdPolicy(id: "identify", revision: "1", embeddingSpace: template.embeddingSpaceId,
        scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .verified,
        kind: .identification, threshold: 0.5, qualityFloor: 0.8, operatingPoint: "fixture", datasetRevision: "fixture", gallerySizeRange: 1...32, enrollmentRule: "one", effectiveAuthorizationEpoch: 0)
}

private struct IndexClock: IdentityWallClock { var nowMillis: Int64 { 100 } }
private struct IndexMono: IdentityMonotonicClock { var nowNanos: Int64 { 1 } }
private final class IndexTestAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    var rejectOwnership = false
    private let lock = NSLock(); private var nextEpoch: (@Sendable () -> Void)?
    func onNextEpoch(_ callback: @escaping @Sendable () -> Void) { lock.lock(); nextEpoch = callback; lock.unlock() }
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        lock.lock(); let callback = nextEpoch; nextEpoch = nil; lock.unlock()
        callback?(); return grant.authorizationEpoch
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws { if rejectOwnership { throw IdentityError(.authorizationRequired) } }
}
private final class IndexTestKeys: IdentityKeyProvider, @unchecked Sendable {
    private var values: [String: SymmetricKey] = [:]; private let lock = NSLock()
    var denied = false
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if denied { throw IdentityError(.reEnrollmentRequired) }
        if let value = values[id] { return value }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let value = SymmetricKey(size: .bits256); values[id] = value; return value
    }
    func deleteKey(id: String) throws { lock.lock(); values.removeValue(forKey: id); lock.unlock() }
}
private func indexGrant() throws -> AuthorizationGrant {
    try AuthorizationGrant(grantId: "gallery", revocationDomainId: "domain", scope: [.enrollment, .identification, .verification],
                           purpose: "identity", issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "audit")
}

private struct IndexTestContext {
    let index: IndexStore; let identity: IdentityStore; let keys: IndexTestKeys; let authority: IndexTestAuthority
    let grant: AuthorizationGrant; let template: TemplateRef; let policy: ThresholdPolicy; let owner: OwnershipAuthorization
}
private final class IndexBuildGate: @unchecked Sendable {
    private let lock = NSLock(); private var count = 0
    private let entered: XCTestExpectation; private let semaphore = DispatchSemaphore(value: 0)
    init(entered: XCTestExpectation) { self.entered = entered }
    func build() throws -> Data {
        lock.lock(); count += 1; let current = count; lock.unlock()
        if current > 1 { entered.fulfill(); guard semaphore.wait(timeout: .now() + 5) == .success else { throw IdentityError(.indexUnavailable) } }
        return Data([1])
    }
    func release() { semaphore.signal() }
}

private final class WeakIndexStore { weak var value: IndexStore? }
