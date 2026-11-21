import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IdentityLivenessOperationTests: XCTestCase {
    func testStandaloneGrantPersistsWithoutCandidateAndCannotBeReplayedOrBorrowedByAnotherStore() async throws {
        let f = try LivenessGateFixture(); let other = try LivenessGateFixture()
        let ticket = try await f.store.beginLivenessOperation(grant: f.grant())
        do { try await other.store.finishLivenessOperation(ticket) {}; XCTFail("foreign ticket accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        let value = try await f.store.finishLivenessOperation(ticket) { 42 }
        XCTAssertEqual(value, 42)
        do { try await f.store.finishLivenessOperation(ticket) {}; XCTFail("ticket reused") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        try await f.store.close(); try await other.store.close()
        let reopened = try f.reopen()
        _ = try await reopened.revokeGrant("live", ownership: f.owner)
        do { _ = try await reopened.beginLivenessOperation(grant: f.grant()); XCTFail("grant resurrected") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await reopened.close()
    }

    func testAdmissionRequiresLivenessScopePurposeExpiryAndAuthoritativeEpoch() async throws {
        let f = try LivenessGateFixture()
        for (grant, code) in [
            (try f.grant(scope: [.identification]), IdentityErrorCode.authorizationRequired),
            (try f.grant(purpose: "other"), .authorizationRequired),
            (try f.grant(expires: 100), .authorizationExpired)
        ] {
            do { _ = try await f.store.beginLivenessOperation(grant: grant); XCTFail("invalid grant admitted") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, code) }
        }
        f.authority.setEpoch(1)
        do { _ = try await f.store.beginLivenessOperation(grant: f.grant()); XCTFail("untrusted epoch accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await f.store.close()
    }

    func testFinalGateRechecksExpiryAndProviderAndConsumesFailedTicket() async throws {
        let f = try LivenessGateFixture()
        let expiring = try await f.store.beginLivenessOperation(grant: f.grant(expires: 200))
        f.clock.set(200)
        do { try await f.store.finishLivenessOperation(expiring) { XCTFail("expired published") } }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationExpired) }
        f.clock.set(100)
        let stale = try await f.store.beginLivenessOperation(grant: f.grant(id: "stale"))
        f.authority.setEpoch(1)
        do { try await f.store.finishLivenessOperation(stale) { XCTFail("provider revocation ignored") } }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        do { try await f.store.finishLivenessOperation(stale) {}; XCTFail("failed ticket reused") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        try await f.store.close()
    }

    func testRevocationWinsOverCooperativeCancelAndStopsOnlyItsDomain() async throws {
        let f = try LivenessGateFixture()
        let ticket = try await f.store.beginLivenessOperation(grant: f.grant())
        let unrelated = try await f.store.beginLivenessOperation(grant: f.grant(id: "other", domain: "other"))
        ticket.stopSignal.requestStop(.cancelled)
        _ = try await f.store.revokeGrant("live", ownership: f.owner)
        do { try await f.store.finishLivenessOperation(ticket) { XCTFail("revoked published") } }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        let value = try await f.store.finishLivenessOperation(unrelated) { 7 }
        XCTAssertEqual(value, 7)
        try await f.store.close()
    }

    func testNewerTrustedEpochStopsPriorSessionAndPersistsInvalidation() async throws {
        let f = try LivenessGateFixture()
        let old = try await f.store.beginLivenessOperation(grant: f.grant())
        let stopped = expectation(description: "old stopped")
        let registration = old.stopSignal.registerStopHandler { stopped.fulfill() }
        let newer = try await f.store.beginLivenessOperation(grant: f.grant(id: "new", epoch: 1))
        await fulfillment(of: [stopped], timeout: 2)
        do { try await f.store.finishLivenessOperation(old) { XCTFail("old epoch survived") } }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await f.store.finishLivenessOperation(newer) {}; registration.close()
        try await f.store.close()
        let reopened = try f.reopen()
        do { _ = try await reopened.beginLivenessOperation(grant: f.grant()); XCTFail("epoch rolled back") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await reopened.close()
    }

    func testCancellationDuringFinalProviderCallbackSuppressesPublication() async throws {
        let f = try LivenessGateFixture()
        let ticket = try await f.store.beginLivenessOperation(grant: f.grant())
        f.authority.setHook { ticket.stopSignal.requestStop(.cancelled) }
        do { try await f.store.finishLivenessOperation(ticket) { XCTFail("cancel callback ignored") } }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        try await f.store.close()
    }

    func testCloseStopsAndRetainsWriterUntilTicketAndReservedCallbackDrain() async throws {
        let f = try LivenessGateFixture()
        let ticket = try await f.store.beginLivenessOperation(grant: f.grant())
        let stopped = expectation(description: "stop callback entered")
        let release = DispatchSemaphore(value: 0)
        let registration = ticket.stopSignal.registerStopHandler {
            stopped.fulfill(); _ = release.wait(timeout: .now() + 3)
        }
        let closing = Task { try await f.store.close() }
        await fulfillment(of: [stopped], timeout: 2)
        do { try await f.store.finishLivenessOperation(ticket) { XCTFail("closed published") } }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        XCTAssertThrowsError(try f.reopen()) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        release.signal(); try await closing.value; registration.close()
        let reopened = try f.reopen(); try await reopened.close()
    }

    func testRevocationCallbackCanCloseAndConsumeWithoutSelfDeadlock() async throws {
        let f = try LivenessGateFixture()
        let ticket = try await f.store.beginLivenessOperation(grant: f.grant())
        let drained = expectation(description: "callback reentry drained")
        let registration = ticket.stopSignal.registerStopHandler {
            let done = DispatchSemaphore(value: 0)
            Task {
                try? await f.store.close()
                do { try await f.store.finishLivenessOperation(ticket) { XCTFail("revoked published") } }
                catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
                drained.fulfill(); done.signal()
            }
            XCTAssertEqual(done.wait(timeout: .now() + 3), .success)
        }
        _ = try await f.store.revokeGrant("live", ownership: f.owner)
        await fulfillment(of: [drained], timeout: 2)
        registration.close(); try await f.store.close()
        let reopened = try f.reopen(); try await reopened.close()
    }

    func testNewEnrollmentEpochInvalidatesLivenessWithoutMakingItCandidateScoped() async throws {
        let f = try LivenessGateFixture()
        let ticket = try await f.store.beginLivenessOperation(grant: f.grant())
        let newer = try f.grant(id: "enroll", scope: [.enrollment], epoch: 1)
        _ = try await f.store.createCandidate(newer)
        do { try await f.store.finishLivenessOperation(ticket) { XCTFail("old session published") } }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await f.store.close()
    }

    func testPublicationClosureFailureConsumesTicketAndCompletedValueIsNotRecalled() async throws {
        let f = try LivenessGateFixture()
        let failed = try await f.store.beginLivenessOperation(grant: f.grant())
        do { try await f.store.finishLivenessOperation(failed) { throw IdentityError(.invalidChallenge) }; XCTFail("lost failure") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidChallenge) }
        do { try await f.store.finishLivenessOperation(failed) {}; XCTFail("failed ticket reused") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        let completed = try await f.store.beginLivenessOperation(grant: f.grant())
        let value = try await f.store.finishLivenessOperation(completed) { "immutable" }
        _ = try await f.store.revokeGrant("live", ownership: f.owner)
        XCTAssertEqual(value, "immutable")
        try await f.store.close()
    }
}

private final class LivenessGateFixture {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    let keys = LivenessGateKeys(); let authority = LivenessGateAuthority(); let clock = LivenessGateClock()
    let store: IdentityStore
    var owner: OwnershipAuthorization { try! OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner") }
    init() throws {
        store = try IdentityStore(root: root, purpose: "identity", authorizationProvider: authority, keyProvider: keys,
            wallClock: clock, monotonicClock: LivenessGateMono())
    }
    deinit { try? FileManager.default.removeItem(at: root) }
    func reopen() throws -> IdentityStore {
        try IdentityStore(root: root, purpose: "identity", authorizationProvider: authority, keyProvider: keys,
            wallClock: clock, monotonicClock: LivenessGateMono())
    }
    func grant(id: String = "live", domain: String = "live-domain", scope: Set<IdentityScope> = [.liveness],
               purpose: String = "identity", expires: Int64? = nil, epoch: Int64 = 0) throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: scope, purpose: purpose,
            issuedAtMillis: 0, expiresAtMillis: expires, authorizationEpoch: epoch, evidenceRef: "fixture")
    }
}
private final class LivenessGateClock: IdentityWallClock, @unchecked Sendable {
    private let lock = NSLock(); private var value: Int64 = 100
    var nowMillis: Int64 { lock.lock(); defer { lock.unlock() }; return value }
    func set(_ value: Int64) { lock.lock(); self.value = value; lock.unlock() }
}
private struct LivenessGateMono: IdentityMonotonicClock { var nowNanos: Int64 { 1 } }
private final class LivenessGateAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    private let lock = NSLock(); private var epoch: Int64?; private var hook: (@Sendable () -> Void)?
    func setEpoch(_ epoch: Int64) { lock.lock(); self.epoch = epoch; lock.unlock() }
    func setHook(_ hook: @escaping @Sendable () -> Void) { lock.lock(); self.hook = hook; lock.unlock() }
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        lock.lock(); let callback = hook; hook = nil; let value = epoch; lock.unlock(); callback?()
        return value ?? grant.authorizationEpoch
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private final class LivenessGateKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock(); private var values: [String: SymmetricKey] = [:]
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if let value = values[id] { return value }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let value = SymmetricKey(size: .bits256); values[id] = value; return value
    }
    func deleteKey(id: String) throws { lock.lock(); values[id] = nil; lock.unlock() }
}
