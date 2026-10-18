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
    private func grant(id: String = "g1", purpose: String = "login", epoch: Int64 = 0, domain: String = "tenant.subject") throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: [.enrollment], purpose: purpose,
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
    var denyOwnership = false
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { onAuthorize?(); return epoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {
        if denyOwnership { throw IdentityError(.authorizationRequired) }
    }
}
private struct FixedClock: IdentityWallClock { let nowMillis: Int64 }
private struct FixedMono: IdentityMonotonicClock { let nowNanos: Int64 = 1 }
private final class MovingClock: IdentityWallClock, @unchecked Sendable { var nowMillis: Int64 = 100 }

extension IdentityStoreTests {
    func testRevokePersistsTombstonesAndReturnsIdempotentReceipt() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let candidate = try await store.createCandidate(grant())
        let ownership = try OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")
        let receipt = try await store.revokeGrant("g1", ownership: ownership)
        XCTAssertEqual(receipt.storeId, store.storeId)
        XCTAssertFalse(receipt.id.isEmpty)
        do { _ = try await store.recordedCandidateGrant(candidate); XCTFail("revoked candidate visible") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
        let repeated = try await store.revokeGrant("g1", ownership: ownership)
        XCTAssertEqual(repeated, receipt)
        try await store.close()

        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        let restored = try await reopened.revokeGrant("g1", ownership: try OwnershipAuthorization(storeId: reopened.storeId, evidenceRef: "owner"))
        XCTAssertEqual(restored, receipt)
        do { _ = try await reopened.recordedCandidateGrant(candidate); XCTFail("tombstone lost") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
        try await reopened.close()
    }

    func testRevokeAdvancesDomainEpochAndNewAuthorizedEpochCreatesNewCandidate() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        _ = try await store.createCandidate(grant())
        let ownership = try OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")
        _ = try await store.revokeGrant("g1", ownership: ownership)
        do { _ = try await store.createCandidate(grant(id: "old-new-id")); XCTFail("old epoch bypass") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        provider.epoch = 1
        let fresh = try await store.createCandidate(grant(id: "g2", epoch: 1))
        let freshGrant = try await store.recordedCandidateGrant(fresh)
        XCTAssertEqual(freshGrant.grantId, "g2")
        try await store.close()
    }

    func testRevokeRequiresIndependentOwnershipAndDoesNotChangeStateOnDenial() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let candidate = try await store.createCandidate(grant())
        do { _ = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: "wrong", evidenceRef: "owner")); XCTFail("wrong owner accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        provider.denyOwnership = true
        do { _ = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")); XCTFail("denied owner accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        provider.denyOwnership = false
        let unchanged = try await store.recordedCandidateGrant(candidate)
        XCTAssertEqual(unchanged.grantId, "g1")
        try await store.close()
    }

    func testRevokeTargetsEveryOldGrantInItsDomainAndPreservesOtherDomains() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let first = try await store.createCandidate(grant())
        let second = try await store.createCandidate(grant(id: "g2"))
        let unrelated = try await store.createCandidate(grant(id: "other", domain: "tenant.other"))
        let ownership = try OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")
        let receipt = try await store.revokeGrant("g1", ownership: ownership)
        let alias = try await store.revokeGrant("g2", ownership: ownership)
        XCTAssertEqual(alias, receipt)
        for ref in [first, second] {
            do { _ = try await store.recordedCandidateGrant(ref); XCTFail("same domain survived") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
        }
        let otherGrant = try await store.recordedCandidateGrant(unrelated)
        XCTAssertEqual(otherGrant.grantId, "other")
        try await store.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        let restoredAlias = try await reopened.revokeGrant("g2", ownership: ownership)
        XCTAssertEqual(restoredAlias, receipt)
        do { _ = try await reopened.createCandidate(grant(id: "bypass")); XCTFail("restart lost epoch") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        provider.epoch = 1
        let fresh = try await reopened.createCandidate(grant(id: "fresh", epoch: 1))
        let oldReceipt = try await reopened.revokeGrant("g1", ownership: ownership)
        XCTAssertEqual(oldReceipt, receipt)
        let live = try await reopened.recordedCandidateGrant(fresh)
        XCTAssertEqual(live.grantId, "fresh")
        let nextReceipt = try await reopened.revokeGrant("fresh", ownership: ownership)
        XCTAssertNotEqual(nextReceipt, receipt)
        try await reopened.close()
        let twiceReopened = try makeStore(root: root, keys: keys, provider: provider)
        let firstRestored = try await twiceReopened.revokeGrant("g1", ownership: ownership)
        let secondRestored = try await twiceReopened.revokeGrant("fresh", ownership: ownership)
        XCTAssertEqual(firstRestored, receipt); XCTAssertEqual(secondRestored, nextReceipt)
        try await twiceReopened.close()
    }

    func testFailedRevokeCommitRetiresStoreAndDoesNotPublishTombstone() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let candidate = try await store.createCandidate(grant())
        let ownership = try OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")
        let file = root.appendingPathComponent("identity-journal-store.bin")
        let before = try Data(contentsOf: file)
        keys.rejectAccess = true
        do { _ = try await store.revokeGrant("g1", ownership: ownership); XCTFail("failed commit returned receipt") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        keys.rejectAccess = false
        XCTAssertEqual(try Data(contentsOf: file), before)
        do { _ = try await store.recordedCandidateGrant(candidate); XCTFail("retired store accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        try await store.close()
        XCTAssertThrowsError(try makeStore(root: root, keys: keys, provider: provider))
        let live = try await reopened.recordedCandidateGrant(candidate)
        XCTAssertEqual(live.grantId, "g1")
        try await reopened.close()
    }

    func testUnknownGrantAndClosedStoreRejectRevocation() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let ownership = try OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")
        do { _ = try await store.revokeGrant("unknown", ownership: ownership); XCTFail("unknown grant accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        try await store.close()
        do { _ = try await store.revokeGrant("unknown", ownership: ownership); XCTFail("closed accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
    }

    func testReceiptWireIsStoreBoundAndStrict() throws {
        let receipt = try RevocationReceipt(id: "receipt", storeId: "store")
        XCTAssertEqual(try RevocationReceipt.decodeJSON(receipt.canonicalJSON()), receipt)
        XCTAssertThrowsError(try RevocationReceipt.decodeJSON(#"{"id":"r","storeId":"s","status":"purged"}"#))
        XCTAssertThrowsError(try RevocationReceipt.decodeJSON(#"{"id":"","storeId":"s"}"#))
    }

    func testVersionOneCandidateStoreMigratesOnNextCommit() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let auth = try IdentityAuthorizationState(purpose: "login").admitting(grant(), scope: .enrollment, provider: provider, nowMillis: 100)
        let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try journal.commit(Data(identityJSON(["version": 1, "storeId": "legacy", "authorization": try auth.canonicalJSON(),
                                              "candidates": ["candidate": "g1"]]).utf8))
        try journal.close()
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let ref = try CandidateRef(id: "candidate", storeId: "legacy")
        let membership = try await store.recordedCandidateGrant(ref)
        XCTAssertEqual(membership.grantId, "g1")
        let receipt = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: "legacy", evidenceRef: "owner"))
        try await store.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        let restored = try await reopened.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: "legacy", evidenceRef: "owner"))
        XCTAssertEqual(restored, receipt)
        try await reopened.close()
    }

    func testRestoreRejectsIncompleteOrInconsistentRevocationTargetSets() throws {
        let provider = StoreAuthority(epoch: 0)
        var auth = try IdentityAuthorizationState(purpose: "login").admitting(grant(), scope: .enrollment, provider: provider, nowMillis: 100)
        auth = try auth.admitting(grant(id: "g2"), scope: .enrollment, provider: provider, nowMillis: 100).revoking(grantId: "g1")
        let valid: [String: Any] = ["domainId": "tenant.subject", "epoch": 1, "grantIds": ["g1", "g2"], "candidateIds": ["c1", "c2"]]
        func changing(_ changes: [String: Any]) -> [String: Any] { valid.merging(changes) { _, new in new } }
        let invalidRecords: [[String: Any]] = [
            ["r": changing(["grantIds": ["g1"], "candidateIds": ["c1"]])],
            ["r": changing(["candidateIds": ["c1"]])],
            ["r": changing(["candidateIds": ["c1", "c1"]])],
            ["r": changing(["grantIds": [String]()])],
            ["r": changing(["domainId": "wrong"])],
            ["r": changing(["epoch": 0])], ["r": changing(["epoch": 2])],
            ["r": changing(["extra": true])], ["r": valid, "duplicate": valid]
        ]
        for records in invalidRecords {
            let root = temporaryRoot(); let keys = StoreKeys()
            let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
            try journal.commit(Data(identityJSON(["version": 2, "storeId": "store", "authorization": try auth.canonicalJSON(),
                                                  "candidates": ["c1": "g1", "c2": "g2"], "revocations": records]).utf8))
            try journal.close()
            XCTAssertThrowsError(try makeStore(root: root, keys: keys, provider: provider)) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
            }
        }
    }

    func testConcurrentRevocationsReturnOnePersistentReceipt() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        _ = try await store.createCandidate(grant())
        let ownership = try OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")
        let receipts = try await withThrowingTaskGroup(of: RevocationReceipt.self) { group in
            for _ in 0..<16 { group.addTask { try await store.revokeGrant("g1", ownership: ownership) } }
            var results: [RevocationReceipt] = []
            for try await receipt in group { results.append(receipt) }
            return results
        }
        XCTAssertEqual(receipts.count, 16)
        XCTAssertTrue(receipts.allSatisfy { $0 == receipts.first })
        provider.denyOwnership = true
        do { _ = try await store.revokeGrant("g1", ownership: ownership); XCTFail("repeated revoke bypassed ownership") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        provider.denyOwnership = false
        try await store.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        let restored = try await reopened.revokeGrant("g1", ownership: ownership)
        XCTAssertEqual(restored, receipts.first)
        try await reopened.close()
    }
}
