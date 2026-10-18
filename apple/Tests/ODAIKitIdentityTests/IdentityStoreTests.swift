import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IdentityStoreTests: XCTestCase {
    func testCandidateCreationPersistsStoreAndUsesTrustedAuthorization() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let candidate = try await store.createCandidate(grant())
        let id = store.storeId
        try await store.close()

        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        XCTAssertEqual(reopened.storeId, id)
        XCTAssertEqual(candidate.storeId, id)
        let membership = try await reopened.recordedCandidateGrant(candidate)
        XCTAssertEqual(membership, try grant())
        do { _ = try await reopened.createCandidate(grant(purpose: "other")); XCTFail("purpose must reject") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        try await reopened.close()
    }

    func testCandidateCreationRejectsProviderEpochMismatchAndLeavesStateUnchanged() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let before = store.storeId
        provider.epoch = 1
        do { _ = try await store.createCandidate(grant()); XCTFail("epoch must reject") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertEqual(store.storeId, before)
        provider.epoch = 0
        _ = try await store.createCandidate(grant())
        try await store.close()
    }

    func testCloseIsIdempotentAndRejectsLaterAdmission() async throws {
        let store = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        try await store.close(); try await store.close()
        do { _ = try await store.createCandidate(grant()); XCTFail("closed must reject") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
    }

    func testCandidateReferencesAreStoreBoundAndCannotBeForged() async throws {
        let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let first = try makeStore(root: temporaryRoot(), keys: keys, provider: provider)
        let second = try makeStore(root: temporaryRoot(), keys: keys, provider: provider)
        let candidate = try await first.createCandidate(grant())
        let other = try await first.createCandidate(grant())
        XCTAssertNotEqual(candidate, other)
        for ref in [candidate, try CandidateRef(id: "forged", storeId: second.storeId)] {
            do { _ = try await second.recordedCandidateGrant(ref); XCTFail("foreign reference accepted") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateNotFound) }
        }
        try await first.close(); try await second.close()
    }

    func testFailedCommitRetiresStoreWithoutPublishingCandidateOrGrant() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let committed = try await store.createCandidate(grant())
        keys.rejectAccess = true
        do { _ = try await store.createCandidate(grant(id: "uncommitted")); XCTFail("key denial accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        keys.rejectAccess = false
        do { _ = try await store.createCandidate(grant()); XCTFail("retired store accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        try await store.close() // Must not unlock the new writer.
        XCTAssertThrowsError(try makeStore(root: root, keys: keys, provider: provider))
        let restored = try await reopened.recordedCandidateGrant(committed)
        XCTAssertEqual(restored.grantId, "g1")
        try await reopened.close()
    }

    func testEncryptedMetadataAndWrongPurposeReopenFailClosed() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        _ = try await store.createCandidate(grant())
        try await store.close()
        let file = root.appendingPathComponent("identity-journal-store.bin")
        let committed = try Data(contentsOf: file)
        XCTAssertFalse(committed.contains(Data("tenant.subject".utf8)))
        XCTAssertThrowsError(try IdentityStore(root: root, purpose: "different", authorizationProvider: provider,
                                              keyProvider: keys, wallClock: FixedClock(nowMillis: 100), monotonicClock: FixedMono()))
        XCTAssertEqual(try Data(contentsOf: file), committed)
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        try await reopened.close()
    }

    func testPublicationRechecksAuthorizationAfterAdmission() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let clock = MovingClock()
        let provider = StoreAuthority(epoch: 0)
        let store = try IdentityStore(root: root, purpose: "login", authorizationProvider: provider,
                                      keyProvider: keys, wallClock: clock, monotonicClock: FixedMono())
        let file = root.appendingPathComponent("identity-journal-store.bin")
        let initial = try Data(contentsOf: file)
        provider.onAuthorize = { clock.nowMillis = 200 }
        do { _ = try await store.createCandidate(grant()); XCTFail("expired publication accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationExpired) }
        XCTAssertEqual(try Data(contentsOf: file), initial)
        try await store.close()
    }

    func testRecoveredDomainEpochRemainsAuthoritativeAcrossNewGrantIDs() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 4)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        _ = try await store.createCandidate(grant(epoch: 4))
        try await store.close()
        provider.epoch = 3
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        do { _ = try await reopened.createCandidate(grant(id: "new", epoch: 3)); XCTFail("epoch rollback accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await reopened.close()
    }

    func testConcurrentCandidateAdmissionsRemainRecoverable() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let authorization = try grant()
        let refs = try await withThrowingTaskGroup(of: CandidateRef.self) { group in
            for _ in 0..<16 { group.addTask { try await store.createCandidate(authorization) } }
            var refs: [CandidateRef] = []
            for try await ref in group { refs.append(ref) }
            return refs
        }
        XCTAssertEqual(Set(refs.map(\.id)).count, 16)
        try await store.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        for ref in refs {
            let recovered = try await reopened.recordedCandidateGrant(ref)
            XCTAssertEqual(recovered, authorization)
        }
        try await reopened.close()
    }

    private func makeStore(root: URL, keys: StoreKeys, provider: StoreAuthority) throws -> IdentityStore {
        try IdentityStore(root: root, purpose: "login", authorizationProvider: provider, keyProvider: keys,
                          wallClock: FixedClock(nowMillis: 100), monotonicClock: FixedMono())
    }
    private func grant(id: String = "g1", purpose: String = "login", epoch: Int64 = 0) throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: "tenant.subject", scope: [.enrollment], purpose: purpose,
                               issuedAtMillis: 0, expiresAtMillis: 200, authorizationEpoch: epoch, evidenceRef: "audit")
    }
    private func temporaryRoot() -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        return root
    }
}

private final class StoreKeys: IdentityKeyProvider, @unchecked Sendable {
    private var values: [String: SymmetricKey] = [:]; private let lock = NSLock()
    var rejectAccess = false
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if rejectAccess { throw IdentityError(.reEnrollmentRequired) }
        if let value = values[id] { return value }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let value = SymmetricKey(size: .bits256); values[id] = value; return value
    }
    func deleteKey(id: String) throws { lock.lock(); values.removeValue(forKey: id); lock.unlock() }
}
private final class StoreAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    var epoch: Int64; init(epoch: Int64) { self.epoch = epoch }
    var onAuthorize: (@Sendable () -> Void)?
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { onAuthorize?(); return epoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private struct FixedClock: IdentityWallClock { let nowMillis: Int64 }
private struct FixedMono: IdentityMonotonicClock { let nowNanos: Int64 = 1 }
private final class MovingClock: IdentityWallClock, @unchecked Sendable { var nowMillis: Int64 = 100 }
