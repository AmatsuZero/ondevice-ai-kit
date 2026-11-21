import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IdentityGalleryOperationTests: XCTestCase {
    func testPublicationChecksEveryMemberDomainAndConsumesTicketAfterRevocation() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll()
        let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        let receipt = try await f.store.revokeGrant(member.grant.grantId, ownership: f.owner)
        XCTAssertEqual(receipt.storeId, f.store.storeId)
        XCTAssertThrowsError(try ticket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        var published = false
        do {
            try await f.store.finishGalleryOperation(ticket) { published = true }
            XCTFail("member revocation accepted")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertFalse(published)
        try await f.store.close()
    }

    func testOperationGrantIsRecordedWithoutFakeCandidateAndCanBeRevokedAfterReopen() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll()
        let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        let value = try await f.store.finishGalleryOperation(ticket) { 42 }
        XCTAssertEqual(value, 42)
        try await f.store.close()
        let reopened = try f.reopen()
        _ = try await reopened.revokeGrant(f.operationGrant.grantId, ownership: f.owner)
        do {
            _ = try await reopened.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
            XCTFail("operation grant resurrected")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await reopened.close()
    }

    func testOperationRevocationStopsTicketEvenWhenMembersAreOtherDomains() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll()
        let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        _ = try await f.store.revokeGrant(f.operationGrant.grantId, ownership: f.owner)
        XCTAssertThrowsError(try ticket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        do { try await f.store.finishGalleryOperation(ticket) {}; XCTFail("revoked operation published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await f.store.close()
    }

    func testEnrollmentScopeDoesNotImplicitlyAuthorizeGalleryMembership() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll(scope: [.enrollment])
        do {
            _ = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
            XCTFail("enrollment scope became identification")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        try await f.store.close()
    }

    func testTemplateDeletionAndCandidateDeletionInvalidateWholeOperation() async throws {
        for candidateDeletion in [false, true] {
            let f = try GalleryGateFixture(); let member = try await f.enroll()
            let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
            if candidateDeletion {
                let candidate = try await f.store.recordedTemplateCandidate(member.ref)
                _ = try await f.store.deleteCandidate(candidate, ownership: f.owner)
            } else { _ = try await f.store.deleteTemplate(member.ref, ownership: f.owner) }
            XCTAssertThrowsError(try ticket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
            do { try await f.store.finishGalleryOperation(ticket) {}; XCTFail("deleted member published") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
            try await f.store.close()
        }
    }

    func testAnyMemberDomainInvalidatesEntireTicketWhileUnrelatedTicketRemainsUsable() async throws {
        let f = try GalleryGateFixture(); let first = try await f.enroll()
        let second = try await f.enroll(id: "member-2", domain: "member-2")
        let both = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [first, second], embeddingSpace: f.backend.embeddingSpace)
        let unrelated = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [first], embeddingSpace: f.backend.embeddingSpace)
        _ = try await f.store.revokeGrant(second.grant.grantId, ownership: f.owner)
        do { try await f.store.finishGalleryOperation(both) {}; XCTFail("second member silently filtered") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        let value = try await f.store.finishGalleryOperation(unrelated) { 7 }
        XCTAssertEqual(value, 7)
        try await f.store.close()
    }

    func testSeparateMemberIdentificationGrantIsPersistedAndRevocable() async throws {
        let f = try GalleryGateFixture(); let enrollment = try await f.enroll(scope: [.enrollment])
        let memberGrant = try GalleryGateFixture.grant("member-identify", domain: "member", scope: [.identification])
        let member = IdentityGalleryMember(ref: enrollment.ref, grant: memberGrant)
        let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        _ = try await f.store.revokeGrant(memberGrant.grantId, ownership: f.owner)
        do { try await f.store.finishGalleryOperation(ticket) {}; XCTFail("alternate member grant was not revoked") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await f.store.close()
    }

    func testFinalAdmissionRechecksExpiryAndCancellationAndConsumesFailedTicket() async throws {
        let clock = GalleryGateMutableClock()
        let f = try GalleryGateFixture(clock: clock); let member = try await f.enroll()
        let expiring = try AuthorizationGrant(grantId: "expiring", revocationDomainId: "expiring", scope: [.identification], purpose: "identity",
            issuedAtMillis: 0, expiresAtMillis: 200, authorizationEpoch: 0, evidenceRef: "audit")
        let expired = try await f.store.beginGalleryOperation(grant: expiring, members: [member], embeddingSpace: f.backend.embeddingSpace)
        clock.set(200)
        do { try await f.store.finishGalleryOperation(expired) {}; XCTFail("expired operation published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationExpired) }
        clock.set(100)
        let cancelled = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        cancelled.stopSignal.requestStop(.cancelled)
        do { try await f.store.finishGalleryOperation(cancelled) {}; XCTFail("cancelled operation published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        try await f.store.close()
    }

    func testSynchronousPublicationAndRevocationShareOneSerialGate() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll()
        let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        let entered = expectation(description: "publication gate entered")
        let release = DispatchSemaphore(value: 0)
        let publishing = Task.detached {
            try await f.store.finishGalleryOperation(ticket) {
                entered.fulfill()
                guard release.wait(timeout: .now() + 5) == .success else { throw IdentityError(.busy) }
                return 17
            }
        }
        await fulfillment(of: [entered], timeout: 3)
        let revoking = Task { try await f.store.revokeGrant(member.grant.grantId, ownership: f.owner) }
        release.signal()
        let value = try await publishing.value; XCTAssertEqual(value, 17)
        _ = try await revoking.value
        do {
            _ = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
            XCTFail("revoked member re-admitted")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await f.store.close()
    }

    func testTrustedNewerMemberEpochPersistsTombstoneBeforeRejectingOldTemplate() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll()
        let old = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        let newer = try AuthorizationGrant(grantId: "member-new", revocationDomainId: member.grant.revocationDomainId, scope: [.identification],
            purpose: "identity", issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 1, evidenceRef: "audit")
        do {
            _ = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [IdentityGalleryMember(ref: member.ref, grant: newer)],
                                                       embeddingSpace: f.backend.embeddingSpace)
            XCTFail("old template used at newer epoch")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        do { try await f.store.finishGalleryOperation(old) {}; XCTFail("old epoch operation survived") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await f.store.close()
        let reopened = try f.reopen()
        do { _ = try await reopened.recordedTemplateCandidate(member.ref); XCTFail("tombstone not persisted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await reopened.close()
    }

    func testCancellationDuringFinalProviderValidationPreventsPublication() async throws {
        let authority = GalleryGateAuthority(); let f = try GalleryGateFixture(authority: authority)
        let member = try await f.enroll()
        let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        authority.setHook { ticket.stopSignal.requestStop(.cancelled) }
        do { try await f.store.finishGalleryOperation(ticket) {}; XCTFail("stop during final provider call ignored") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        try await f.store.close()
    }

    func testMemberGrantCannotBorrowAnotherDomainOrPurpose() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll()
        for (domain, purpose) in [("another", "identity"), ("member", "other-purpose")] {
            let wrong = try AuthorizationGrant(grantId: "wrong-" + domain + purpose, revocationDomainId: domain, scope: [.identification],
                purpose: purpose, issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "audit")
            do {
                _ = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [IdentityGalleryMember(ref: member.ref, grant: wrong)],
                                                           embeddingSpace: f.backend.embeddingSpace)
                XCTFail("member domain/purpose bypass")
            } catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        }
        try await f.store.close()
    }

    func testFailedPublicationConsumesTicketAndCloseRetainsWriterUntilTicketDrains() async throws {
        let f = try GalleryGateFixture(); let member = try await f.enroll()
        let first = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        do { try await f.store.finishGalleryOperation(first) { throw IdentityError(.indexUnavailable) }; XCTFail("build failure lost") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .indexUnavailable) }
        do { try await f.store.finishGalleryOperation(first) {}; XCTFail("ticket reused") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        let ticket = try await f.store.beginGalleryOperation(grant: f.operationGrant, members: [member], embeddingSpace: f.backend.embeddingSpace)
        let stopped = expectation(description: "native stopped")
        let registration = ticket.stopSignal.registerStopHandler { stopped.fulfill() }
        let closing = Task { try await f.store.close() }
        await fulfillment(of: [stopped], timeout: 3)
        XCTAssertThrowsError(try f.reopen()) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        do { try await f.store.finishGalleryOperation(ticket) {}; XCTFail("closed store published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        try await closing.value; registration.close()
        let reopened = try f.reopen(); try await reopened.close()
    }
}

private final class GalleryGateFixture {
    let root: URL; let keys = GalleryGateKeys(); let backend: FixtureIdentityBackend
    let store: IdentityStore; let operationGrant: AuthorizationGrant
    var owner: OwnershipAuthorization { try! OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner") }
    init(clock: any IdentityWallClock = GalleryGateClock(), authority: GalleryGateAuthority = GalleryGateAuthority()) throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        backend = try FixtureIdentityBackend.system()
        operationGrant = try Self.grant("operation", domain: "operation", scope: [.identification])
        store = try IdentityStore(root: root, purpose: "identity", authorizationProvider: authority, keyProvider: keys,
                                  wallClock: clock, monotonicClock: GalleryGateMono())
    }
    deinit { try? FileManager.default.removeItem(at: root) }
    func reopen() throws -> IdentityStore {
        try IdentityStore(root: root, purpose: "identity", authorizationProvider: GalleryGateAuthority(), keyProvider: keys,
                          wallClock: GalleryGateClock(), monotonicClock: GalleryGateMono())
    }
    func enroll(id: String = "member", domain: String = "member", scope: Set<IdentityScope> = [.enrollment, .identification]) async throws -> IdentityGalleryMember {
        let grant = try Self.grant(id, domain: domain, scope: scope)
        let candidate = try await store.createCandidate(grant)
        let result = try await store.enroll(IdentityProbe(fixtureID: "fixture", vector: [1, 2]), candidate: candidate, grant: grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "q", revision: "1"), backend: backend)
        return IdentityGalleryMember(ref: try XCTUnwrap(result.templateRef), grant: grant)
    }
    static func grant(_ id: String, domain: String, scope: Set<IdentityScope>) throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: scope, purpose: "identity", issuedAtMillis: 0,
                               expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "audit")
    }
}
private struct GalleryGateClock: IdentityWallClock { var nowMillis: Int64 { 100 } }
private struct GalleryGateMono: IdentityMonotonicClock { var nowNanos: Int64 { 1 } }
private final class GalleryGateAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    private let lock = NSLock(); private var hook: (@Sendable () -> Void)?
    func setHook(_ hook: @escaping @Sendable () -> Void) { lock.lock(); self.hook = hook; lock.unlock() }
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        lock.lock(); let callback = hook; hook = nil; lock.unlock(); callback?()
        return grant.authorizationEpoch
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private final class GalleryGateKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock(); private var values: [String: SymmetricKey] = [:]
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if let key = values[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256); values[id] = key; return key
    }
    func deleteKey(id: String) throws { lock.lock(); values[id] = nil; lock.unlock() }
}

private final class GalleryGateMutableClock: IdentityWallClock, @unchecked Sendable {
    private let lock = NSLock(); private var value: Int64 = 100
    var nowMillis: Int64 { lock.lock(); defer { lock.unlock() }; return value }
    func set(_ value: Int64) { lock.lock(); self.value = value; lock.unlock() }
}
