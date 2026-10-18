import XCTest
import CryptoKit
import ODAIKitCore
@testable import ODAIKitIdentity

final class IdentityTemplateLeaseTests: XCTestCase {
    func testLeaseReadsPersistedVectorAndReleaseDisablesAccess() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification,
                                                                 embeddingSpace: fixture.ref.embeddingSpaceId)
        let values = try lease.embedding().values
        XCTAssertEqual(values, [0.25, -0.75])
        await lease.close(); await lease.close()
        do { _ = try lease.embedding(); XCTFail("released vector remains accessible") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        try await fixture.store.close()
    }

    func testLeaseValidatesScopeExpiryDomainProviderAndCompleteSpace() async throws {
        let fixture = try await fixture()
        let otherSpace = try FixtureIdentityBackend.system(revision: "other").embeddingSpace
        for (grant, scope, space, code) in [
            (try grant(id: "no-scope", scopes: [.enrollment]), IdentityScope.verification, fixture.ref.embeddingSpaceId, IdentityErrorCode.authorizationRequired),
            (try grant(id: "expired", expiry: 100), .verification, fixture.ref.embeddingSpaceId, .authorizationExpired),
            (try grant(id: "other-domain", domain: "other"), .verification, fixture.ref.embeddingSpaceId, .authorizationRequired),
            (fixture.grant, .enrollment, fixture.ref.embeddingSpaceId, .unsupportedOption),
            (fixture.grant, .verification, otherSpace, .embeddingSpaceMismatch)
        ] {
            do { _ = try await fixture.store.acquireTemplateLease(fixture.ref, grant: grant, scope: scope, embeddingSpace: space); XCTFail("invalid lease accepted") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, code) }
        }
        fixture.authority.epoch = 1
        do { _ = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId); XCTFail("provider epoch ignored") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        fixture.authority.epoch = 0
        let alternative = try grant(id: "same-domain")
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: alternative, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        await lease.close()
        _ = try await fixture.store.revokeGrant("same-domain", ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        do { _ = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId); XCTFail("revoked template leased") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await fixture.store.close()
    }

    func testCloseDrainsAllLeasesBeforeWriterReleaseAndOldReleaseCannotAffectNewWriter() async throws {
        let fixture = try await fixture()
        let first = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let second = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        let stopped = expectation(description: "close entered")
        let registration = ticket.stopSignal.registerStopHandler { stopped.fulfill() }
        let closing = Task { try await fixture.store.close() }
        await fulfillment(of: [stopped], timeout: 2)
        do { try await fixture.store.finishOperation(ticket); XCTFail("closed operation published") } catch {}
        await first.close()
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        await second.close(); try await closing.value
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        await first.close(); await second.close()
        let recovered = try await reopened.recordedTemplateCandidate(fixture.ref)
        XCTAssertEqual(recovered, fixture.candidate)
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority))
        registration.close(); try await reopened.close()
    }

    func testLeaseRechecksProviderAfterDecryptionAndDoesNotPinFailedRead() async throws {
        let fixture = try await fixture()
        fixture.keys.onTemplateRead = { fixture.authority.epoch = 1 }
        do { _ = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId); XCTFail("stale decrypt admitted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        fixture.keys.onTemplateRead = nil; fixture.authority.epoch = 0
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        await lease.close(); try await fixture.store.close()
    }

    func testRetiredLeaseReleaseDoesNotMutateReopenedStore() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        fixture.keys.rejectJournal = true
        do { _ = try await fixture.store.createCandidate(grant(id: "failed-commit")); XCTFail("journal failure ignored") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        fixture.keys.rejectJournal = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        await lease.close(); await lease.close(); try await fixture.store.close()
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        let current = try await reopened.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        XCTAssertEqual(try current.embedding().values, [0.25, -0.75])
        await current.close(); try await reopened.close()
    }

    func testLeasePersistsFullAlternativeGrantBeforeReturningIt() async throws {
        let fixture = try await fixture()
        let alternative = try grant(id: "persisted")
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: alternative, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        await lease.close(); try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        do { _ = try await reopened.acquireTemplateLease(fixture.ref, grant: grant(id: "persisted", scopes: [.verification]), scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId); XCTFail("persisted grant was rebound") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        _ = try await reopened.revokeGrant("persisted", ownership: OwnershipAuthorization(storeId: reopened.storeId, evidenceRef: "owner"))
        try await reopened.close()
    }

    func testIdentificationLeaseRejectsVerificationOnlyEmbeddingSpace() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let keys = LeaseKeys(); let authority = LeaseAuthority(); let store = try open(root, keys, authority)
        let enrollment = try grant(); let candidate = try await store.createCandidate(enrollment)
        let backend = try VerificationOnlyBackend()
        let result = try await store.enroll(IdentityProbe(fixtureID: "verification-only", vector: [0.25, -0.75]), candidate: candidate,
                                            grant: enrollment, options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: backend)
        let ref = try XCTUnwrap(result.templateRef)
        let identification = try grant(id: "identify", scopes: [.identification])
        do {
            let lease = try await store.acquireTemplateLease(ref, grant: identification, scope: .identification, embeddingSpace: ref.embeddingSpaceId)
            await lease.close(); XCTFail("unsupported embedding capability accepted")
        }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .embeddingSpaceMismatch) }
        try await store.close()
    }

    private struct Fixture {
        let root: URL; let keys: LeaseKeys; let authority: LeaseAuthority
        let store: IdentityStore; let ref: TemplateRef; let candidate: CandidateRef; let grant: AuthorizationGrant
    }
    private func fixture() async throws -> Fixture {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let keys = LeaseKeys(); let authority = LeaseAuthority(); let store = try open(root, keys, authority)
        let grant = try grant(); let candidate = try await store.createCandidate(grant)
        let result = try await store.enroll(IdentityProbe(fixtureID: "lease", vector: [0.25, -0.75]), candidate: candidate, grant: grant,
                                            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        return try Fixture(root: root, keys: keys, authority: authority, store: store, ref: XCTUnwrap(result.templateRef), candidate: candidate, grant: grant)
    }
    private func grant(id: String = "g1", domain: String = "tenant.subject", scopes: Set<IdentityScope> = [.enrollment, .verification], expiry: Int64 = 200) throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: scopes, purpose: "login", issuedAtMillis: 0, expiresAtMillis: expiry, authorizationEpoch: 0, evidenceRef: "audit")
    }
    private func open(_ root: URL, _ keys: LeaseKeys, _ authority: LeaseAuthority) throws -> IdentityStore {
        try IdentityStore(root: root, purpose: "login", authorizationProvider: authority, keyProvider: keys, wallClock: LeaseClock(), monotonicClock: LeaseClock())
    }
}

private struct LeaseClock: IdentityWallClock, IdentityMonotonicClock { let nowMillis: Int64 = 100; let nowNanos: Int64 = 1 }
private final class LeaseAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    var epoch: Int64 = 0
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { epoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private final class LeaseKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock(); private var keys: [String: SymmetricKey] = [:]
    var onTemplateRead: (@Sendable () -> Void)?
    var rejectJournal = false
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if rejectJournal && id.hasPrefix("journal.") { throw IdentityError(.reEnrollmentRequired) }
        if !createIfMissing && id.hasPrefix("template.") { onTemplateRead?() }
        if let key = keys[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256); keys[id] = key; return key
    }
    func deleteKey(id: String) throws { lock.lock(); defer { lock.unlock() }; keys.removeValue(forKey: id) }
}

private final class VerificationOnlyBackend: IdentityBackend, @unchecked Sendable {
    private let base: FixtureIdentityBackend
    let backendId: String; let backendVersion: String; let modelBinding: ModelBinding; let embeddingSpace: EmbeddingSpaceId
    init() throws {
        base = try .system(); backendId = base.backendId; backendVersion = base.backendVersion; modelBinding = base.modelBinding
        embeddingSpace = try EmbeddingSpaceId(supportedCapabilities: [.verification], backendId: base.embeddingSpace.backendId,
                                              backendVersion: base.embeddingSpace.backendVersion, modelBinding: base.embeddingSpace.modelBinding,
                                              featureFormatVersion: base.embeddingSpace.featureFormatVersion, dimension: base.embeddingSpace.dimension,
                                              dtype: base.embeddingSpace.dtype, endianness: base.embeddingSpace.endianness,
                                              normalization: base.embeddingSpace.normalization, preprocessRevision: base.embeddingSpace.preprocessRevision,
                                              scoreMetric: base.embeddingSpace.scoreMetric, scoreDirection: base.embeddingSpace.scoreDirection)
    }
    func availability() -> CapabilityAvailability { base.availability() }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { try base.evaluateQuality(probe) }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { try IdentityEmbedding(embeddingSpace: embeddingSpace, values: base.embed(probe).values) }
    func close() { base.close() }
}
