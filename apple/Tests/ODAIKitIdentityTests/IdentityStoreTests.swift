import XCTest
import CryptoKit
import ODAIKitCore
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
    var rejectTemplateCreation = false
    var failJournalAfterTemplateCreation = false
    var rejectJournal = false
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if rejectAccess { throw IdentityError(.reEnrollmentRequired) }
        if rejectJournal && id.hasPrefix("journal.") { throw IdentityError(.reEnrollmentRequired) }
        if rejectTemplateCreation && createIfMissing && id.hasPrefix("template.") { throw IdentityError(.reEnrollmentRequired) }
        if let value = values[id] { return value }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let value = SymmetricKey(size: .bits256); values[id] = value
        if failJournalAfterTemplateCreation && id.hasPrefix("template.") { rejectJournal = true }
        return value
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
    func testEnrollmentRejectsBackendSpaceDriftAndMultipleFacesWithoutFallback() async throws {
        let root = temporaryRoot(); let store = try makeStore(root: root, keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
        let probe = try IdentityProbe(fixtureID: "p", vector: [1, 0])
        for (backend, expected) in [(try EnrollmentVariantBackend(mode: .multiple), IdentityErrorCode.multipleFaces),
                                    (try EnrollmentVariantBackend(mode: .spaceDrift), .embeddingSpaceMismatch),
                                    (try EnrollmentVariantBackend(mode: .wrongEmbedding), .embeddingSpaceMismatch)] {
            do { _ = try await store.enroll(probe, candidate: candidate, grant: authorization, options: enrollmentOptions(), backend: backend); XCTFail("invalid backend result accepted") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, expected) }
        }
        do { _ = try await store.enroll(probe, candidate: candidate, grant: authorization, options: enrollmentOptions(), backend: FixtureIdentityBackend.optionalUnavailable()); XCTFail("unavailable fell back") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .backendUnavailable) }
        XCTAssertFalse(try FileManager.default.contentsOfDirectory(atPath: root.path).contains { $0.hasPrefix("identity-template-") })
        try await store.close()
    }

    func testHigherEpochInvalidatesOldTemplateWithoutResurrectingAfterRestart() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
        let result = try await store.enroll(IdentityProbe(fixtureID: "p", vector: [1, 0]), candidate: candidate, grant: authorization,
                                            options: enrollmentOptions(), backend: FixtureIdentityBackend.system())
        let old = try XCTUnwrap(result.templateRef)
        provider.epoch = 1; let fresh = try await store.createCandidate(grant(id: "fresh", epoch: 1))
        do { _ = try await store.recordedTemplateCandidate(old); XCTFail("old template revived") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await store.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        let recovered = try await reopened.recordedCandidateGrant(fresh)
        XCTAssertEqual(recovered.grantId, "fresh")
        do { _ = try await reopened.recordedTemplateCandidate(old); XCTFail("old template revived on restart") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await reopened.close()
    }

    func testRestoreRejectsInvalidTemplateAndStageOwnershipBeforeCleanup() async throws {
        let provider = StoreAuthority(epoch: 0)
        let auth = try IdentityAuthorizationState(purpose: "login").admitting(grant(), scope: .enrollment, provider: provider, nowMillis: 100)
        let id = UUID().uuidString.lowercased()
        let ref = try TemplateRef(id: id, embeddingSpaceId: FixtureIdentityBackend.system().embeddingSpace, authorizationEpoch: 0, revision: "r1")
        let valid: [String: Any] = ["ref": ref.canonicalJSON(), "candidateId": "c1", "grantId": "g1"]
        let cases: [([String: Any], [String])] = [
            ([id: valid], [id]), ([id: valid], ["../file"]), ([:], [id, id]),
            (["wrong": valid], []), ([id: valid.merging(["grantId": "unknown"]) { _, v in v }], []),
            ([id: valid.merging(["candidateId": "unknown"]) { _, v in v }], []),
            ([id: valid.merging(["extra": true]) { _, v in v }], [])
        ]
        for (templates, stages) in cases {
            let root = temporaryRoot(); let keys = StoreKeys()
            let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
            try journal.commit(Data(identityJSON(["version": 3, "storeId": "store", "authorization": auth.canonicalJSON(), "candidates": ["c1": "g1"], "revocations": [:], "templates": templates, "stages": stages]).utf8)); try journal.close()
            XCTAssertThrowsError(try makeStore(root: root, keys: keys, provider: provider)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        }
    }

    func testRecoveryRemovesOnlyManifestOwnedStagesAfterKeyOrPublicationFailure() async throws {
        for failedPublication in [false, true] {
            let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
            let store = try makeStore(root: root, keys: keys, provider: provider)
            let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
            let backend = try FixtureIdentityBackend.system()
            keys.rejectTemplateCreation = !failedPublication; keys.failJournalAfterTemplateCreation = failedPublication
            do {
                _ = try await store.enroll(IdentityProbe(fixtureID: "p", vector: [1, 0]), candidate: candidate, grant: authorization,
                                            options: enrollmentOptions(), backend: backend)
                XCTFail("failed storage published")
            } catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
            keys.rejectJournal = false; keys.rejectTemplateCreation = false; keys.failJournalAfterTemplateCreation = false
            try await store.close()
            let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
            let fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
            let stages = try XCTUnwrap(fields["stages"] as? [String]); XCTAssertEqual(stages.count, 1)
            XCTAssertEqual((fields["templates"] as? [String: Any])?.count, 0)
            try journal.close()
            let orphan = root.appendingPathComponent("identity-template-\(UUID().uuidString.lowercased()).bin")
            try Data([9, 8, 7]).write(to: orphan)
            let reopened = try makeStore(root: root, keys: keys, provider: provider)
            XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("identity-template-\(stages[0]).bin").path))
            XCTAssertThrowsError(try keys.key(id: "template.\(store.storeId).\(stages[0])", createIfMissing: false))
            XCTAssertEqual(try Data(contentsOf: orphan), Data([9, 8, 7]))
            let result = try await reopened.enroll(IdentityProbe(fixtureID: "p", vector: [1, 0]), candidate: candidate, grant: authorization,
                                                  options: enrollmentOptions(), backend: backend)
            XCTAssertNotNil(result.templateRef); try await reopened.close()
            let recovered = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
            let after = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(recovered.read())) as? [String: Any])
            XCTAssertEqual(after["stages"] as? [String], []); try recovered.close()
        }
    }

    func testEnrollmentMeasuresMonotonicTimeAndAddsResolvedErrorContext() async throws {
        let clock = EnrollmentMono(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try IdentityStore(root: temporaryRoot(), purpose: "login", authorizationProvider: provider, keyProvider: keys,
                                      wallClock: FixedClock(nowMillis: 100), monotonicClock: clock)
        let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
        let backend = try FixtureIdentityBackend.system()
        let result = try await store.enroll(IdentityProbe(fixtureID: "p", vector: [1, 0]), candidate: candidate, grant: authorization,
                                            options: enrollmentOptions(), backend: backend)
        XCTAssertGreaterThan(result.durationNanos, 0)
        let failing = try EnrollmentBackend(failure: true)
        do {
            _ = try await store.enroll(IdentityProbe(fixtureID: "p", vector: [1, 0]), candidate: candidate, grant: authorization,
                                        options: enrollmentOptions(), backend: failing)
            XCTFail("backend failure accepted")
        } catch {
            let issue = error as? IdentityError
            XCTAssertEqual(issue?.code, .backendUnavailable); XCTAssertEqual(issue?.backendId, backend.backendId)
            XCTAssertEqual(issue?.modelBinding, backend.modelBinding); XCTAssertNotNil(issue?.sessionId)
        }
        try await store.close()
    }

    func testEnrollmentPersistsIndependentTemplateAndImmutableMembership() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
        let backend = try FixtureIdentityBackend.system()
        let result = try await store.enroll(IdentityProbe(fixtureID: "p", vector: [0.25, -0.75]), candidate: candidate,
                                            grant: authorization, options: enrollmentOptions(), backend: backend)
        XCTAssertEqual(result.outcome, .enrolled)
        let ref = try XCTUnwrap(result.templateRef)
        XCTAssertEqual(result.backendId, backend.backendId); XCTAssertEqual(result.embeddingSpace, backend.embeddingSpace)
        XCTAssertEqual(result.inputRevision, "fixture-p1"); XCTAssertEqual(result.optionsRevision, "quality-1")
        XCTAssertEqual(result.qualityPolicyId, "host.quality"); XCTAssertFalse(result.runId.isEmpty)
        try await store.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        let membership = try await reopened.recordedTemplateCandidate(ref)
        XCTAssertEqual(membership, candidate)
        let payload = try IdentityTemplatePayload(root: root, storeId: reopened.storeId, provider: keys)
        XCTAssertEqual(try payload.read(recordId: ref.id, space: ref.embeddingSpaceId).values, [0.25, -0.75])
        let bytes = try Data(contentsOf: root.appendingPathComponent("identity-template-\(ref.id).bin"))
        XCTAssertFalse(bytes.contains(Data("fixture-p1".utf8)))
        _ = try await reopened.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))
        do { _ = try await reopened.recordedTemplateCandidate(ref); XCTFail("revoked template accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await reopened.close()
    }

    func testInsufficientQualityDoesNotWriteTemplateOrInvokeEmbedding() async throws {
        let root = temporaryRoot(); let store = try makeStore(root: root, keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
        let backend = try EnrollmentBackend(quality: IdentityQuality(faceCount: 1, score: 0.1))
        let result = try await store.enroll(IdentityProbe(fixtureID: "p", vector: [1, 0]), candidate: candidate,
                                            grant: authorization, options: enrollmentOptions(), backend: backend)
        XCTAssertEqual(result.outcome, .insufficientQuality); XCTAssertNil(result.templateRef)
        XCTAssertEqual(backend.embeddingCalls, 0)
        XCTAssertFalse(try FileManager.default.contentsOfDirectory(atPath: root.path).contains { $0.hasPrefix("identity-template-") })
        try await store.close()
    }

    func testRevokeWhileBackendBlockedSuppressesSuccessQualityAndBackendError() async throws {
        for mode in 0..<3 {
            let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
            let store = try makeStore(root: root, keys: keys, provider: provider)
            let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
            let started = expectation(description: "backend running")
            let backend = try EnrollmentBackend(quality: IdentityQuality(faceCount: 1, score: mode == 1 ? 0.1 : 1),
                                                failure: mode == 2, onStart: { started.fulfill() })
            let probe = try IdentityProbe(fixtureID: "p", vector: [1, 0]); let options = try enrollmentOptions()
            let task = Task { try await store.enroll(probe, candidate: candidate, grant: authorization, options: options, backend: backend) }
            await fulfillment(of: [started], timeout: 2)
            _ = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))
            backend.release.signal()
            do { _ = try await task.value; XCTFail("revocation lost") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
            try await store.close()
            XCTAssertFalse(try FileManager.default.contentsOfDirectory(atPath: root.path).contains { $0.hasPrefix("identity-template-") })
        }
    }

    func testCancellationAndCloseDrainBlockedEnrollment() async throws {
        for closing in [false, true] {
            let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
            let store = try makeStore(root: root, keys: keys, provider: provider)
            let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
            let started = expectation(description: "backend running")
            let backend = try EnrollmentBackend(onStart: { started.fulfill() })
            let probe = try IdentityProbe(fixtureID: "p", vector: [1, 0]); let options = try enrollmentOptions()
            let task = Task { try await store.enroll(probe, candidate: candidate, grant: authorization, options: options, backend: backend) }
            await fulfillment(of: [started], timeout: 2)
            let closer: Task<Void, Error>?
            if closing { closer = Task { try await store.close() } } else { closer = nil; task.cancel() }
            // The backend cooperates via its registered native stop callback.
            do { _ = try await task.value; XCTFail("stopped operation succeeded") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, closing ? .closed : .cancelled) }
            try await closer?.value; try await store.close()
            let reopened = try makeStore(root: root, keys: keys, provider: provider); try await reopened.close()
        }
    }

    private func enrollmentOptions() throws -> EnrollmentOptions {
        try EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "quality-1")
    }
}

private final class EnrollmentMono: IdentityMonotonicClock, @unchecked Sendable {
    private let lock = NSLock(); private var value: Int64 = 0
    var nowNanos: Int64 { lock.lock(); defer { lock.unlock() }; value += 10; return value }
}

private final class EnrollmentVariantBackend: IdentityBackend, @unchecked Sendable {
    enum Mode { case multiple, spaceDrift, wrongEmbedding }
    let base: FixtureIdentityBackend; let other: FixtureIdentityBackend; let mode: Mode
    private var drifted = false
    init(mode: Mode) throws { self.mode = mode; base = try .system(); other = try .system(revision: "other-model") }
    var backendId: String { base.backendId }; var backendVersion: String { base.backendVersion }
    var modelBinding: ModelBinding { drifted ? other.modelBinding : base.modelBinding }
    var embeddingSpace: EmbeddingSpaceId { drifted ? other.embeddingSpace : base.embeddingSpace }
    func availability() -> CapabilityAvailability { base.availability() }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality {
        if mode == .spaceDrift { drifted = true }
        return try IdentityQuality(faceCount: mode == .multiple ? 2 : 1, score: 1)
    }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { try (mode == .wrongEmbedding ? other : base).embed(probe) }
    func close() {}
}

private final class EnrollmentBackend: IdentityBackend, @unchecked Sendable {
    let base: FixtureIdentityBackend
    let quality: IdentityQuality
    let failure: Bool
    let onStart: (@Sendable () -> Void)?
    let release = DispatchSemaphore(value: 0)
    private(set) var embeddingCalls = 0
    var backendId: String { base.backendId }
    var backendVersion: String { base.backendVersion }
    var modelBinding: ModelBinding { base.modelBinding }
    var embeddingSpace: EmbeddingSpaceId { base.embeddingSpace }
    init(quality: IdentityQuality? = nil, failure: Bool = false, onStart: (@Sendable () -> Void)? = nil) throws {
        base = try .system(); self.quality = try quality ?? IdentityQuality(faceCount: 1, score: 1)
        self.failure = failure; self.onStart = onStart
    }
    func availability() -> CapabilityAvailability { base.availability() }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { quality }
    func evaluateQuality(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityQuality {
        let registration = stopSignal.registerStopHandler { self.release.signal() }; defer { registration.close() }
        if let onStart { onStart(); guard release.wait(timeout: .now() + 5) == .success else { throw IdentityError(.sessionTimeout) } }
        if failure { throw IdentityError(.backendUnavailable) }; return quality
    }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { embeddingCalls += 1; return try base.embed(probe) }
    func close() { base.close() }
}

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

    func testRevocationStopsMatchingOperationAndSuppressesFinish() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let authorization = try grant()
        let candidate = try await store.createCandidate(authorization)
        let ticket = try await store.beginOperation(candidate, grant: authorization, scope: .enrollment)
        let stopped = expectation(description: "backend stop requested")
        let registration = ticket.stopSignal.registerStopHandler { stopped.fulfill() }
        _ = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))
        await fulfillment(of: [stopped], timeout: 2)
        do { try ticket.stopSignal.check(); XCTFail("stop not visible") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        do { try await store.finishOperation(ticket); XCTFail("revoked operation published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        registration.close(); try await store.close()
    }

    func testOperationFromOtherDomainSurvivesRevocation() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let same = try grant(); let other = try grant(id: "other", domain: "tenant.other")
        let sameRef = try await store.createCandidate(same); let otherRef = try await store.createCandidate(other)
        let ticket = try await store.beginOperation(otherRef, grant: other, scope: .enrollment)
        _ = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))
        try ticket.stopSignal.check()
        try await store.finishOperation(ticket)
        do { _ = try await store.recordedCandidateGrant(sameRef); XCTFail("same domain remained") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
        try await store.close()
    }

    func testOperationTicketRejectsMismatchedCandidateOrGrant() async throws {
        let store = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let first = try grant(); let second = try grant(id: "g2", domain: "tenant.other")
        let ref = try await store.createCandidate(first)
        do { _ = try await store.beginOperation(ref, grant: second, scope: .enrollment); XCTFail("grant substitution accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        try await store.close()
    }

    func testCloseStopsAndDrainsOperationBeforeReleasingWriter() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: root, keys: keys, provider: provider)
        let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
        let ticket = try await store.beginOperation(candidate, grant: authorization, scope: .enrollment)
        let stopped = expectation(description: "close requested backend stop")
        let registration = ticket.stopSignal.registerStopHandler { stopped.fulfill() }
        let closing = Task { try await store.close() }
        await fulfillment(of: [stopped], timeout: 2)
        let secondClosing = Task { try await store.close() }
        XCTAssertThrowsError(try makeStore(root: root, keys: keys, provider: provider)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .busy)
        }
        do { try await store.finishOperation(ticket); XCTFail("closed operation published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        try await closing.value
        try await secondClosing.value
        registration.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        try await store.close()
        XCTAssertThrowsError(try makeStore(root: root, keys: keys, provider: provider))
        try await reopened.close()
    }

    func testNewAuthoritativeEpochStopsOldOperationAndKeepsOldCandidateRevoked() async throws {
        let provider = StoreAuthority(epoch: 0)
        let store = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: provider)
        let old = try grant(); let ref = try await store.createCandidate(old)
        let ticket = try await store.beginOperation(ref, grant: old, scope: .enrollment)
        provider.epoch = 1
        _ = try await store.createCandidate(grant(id: "new", epoch: 1))
        do { try ticket.stopSignal.check(); XCTFail("old epoch backend still active") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        do { try await store.finishOperation(ticket, backendError: IdentityError(.backendUnavailable)); XCTFail("old epoch result accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await store.close()
    }

    func testFinishedOperationIsNotRetroactivelyStopped() async throws {
        let store = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let authorization = try grant(); let ref = try await store.createCandidate(authorization)
        let ticket = try await store.beginOperation(ref, grant: authorization, scope: .enrollment)
        try await store.finishOperation(ticket)
        _ = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))
        try ticket.stopSignal.check()
        try await store.close()
    }

    func testOperationRechecksExpiryAndPersistsNewGrantDeclaration() async throws {
        let root = temporaryRoot(); let keys = StoreKeys(); let provider = StoreAuthority(epoch: 0); let clock = MovingClock()
        let store = try IdentityStore(root: root, purpose: "login", authorizationProvider: provider,
                                      keyProvider: keys, wallClock: clock, monotonicClock: FixedMono())
        let ref = try await store.createCandidate(grant())
        let freshDeclaration = try grant(id: "g2")
        let ticket = try await store.beginOperation(ref, grant: freshDeclaration, scope: .enrollment)
        clock.nowMillis = 200
        do { try await store.finishOperation(ticket); XCTFail("expired completion accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationExpired) }
        try await store.close()
        let reopened = try makeStore(root: root, keys: keys, provider: provider)
        let changed = try AuthorizationGrant(grantId: "g2", revocationDomainId: "tenant.subject", scope: [.enrollment], purpose: "login",
                                              issuedAtMillis: 1, expiresAtMillis: 200, authorizationEpoch: 0, evidenceRef: "audit")
        do { _ = try await reopened.beginOperation(ref, grant: changed, scope: .enrollment); XCTFail("declaration rebound") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        try await reopened.close()
    }

    func testStopCallbackCanQueryStoreFromAnotherTask() async throws {
        let store = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let authorization = try grant(); let ref = try await store.createCandidate(authorization)
        let other = try await store.createCandidate(grant(id: "other", domain: "tenant.other"))
        let ticket = try await store.beginOperation(ref, grant: authorization, scope: .enrollment)
        let callbackWasOutsideGate = StoreFlag()
        let registration = ticket.stopSignal.registerStopHandler {
            let queried = DispatchSemaphore(value: 0)
            Task { if (try? await store.recordedCandidateGrant(other)) != nil { queried.signal() } }
            callbackWasOutsideGate.set(queried.wait(timeout: .now() + 2) == .success)
        }
        _ = try await store.revokeGrant("g1", ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))
        XCTAssertTrue(callbackWasOutsideGate.value)
        do { try await store.finishOperation(ticket, backendError: IdentityError(.backendUnavailable)); XCTFail("backend error won") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        registration.close(); try await store.close()
    }

    func testForeignStoreCannotConsumeOperationTicket() async throws {
        let first = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let second = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let authorization = try grant(); let ref = try await first.createCandidate(authorization)
        let ticket = try await first.beginOperation(ref, grant: authorization, scope: .enrollment)
        do { try await second.finishOperation(ticket); XCTFail("foreign ticket accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        try await first.finishOperation(ticket)
        try await first.close(); try await second.close()
    }

    func testTemplateDeletionStopsOnlyTicketBoundToDeletedTemplate() async throws {
        let store = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let authorization = try grant(); let candidate = try await store.createCandidate(authorization)
        let options = try EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1")
        let firstResult = try await store.enroll(IdentityProbe(fixtureID: "first", vector: [1, 2]), candidate: candidate, grant: authorization,
                                                 options: options, backend: try FixtureIdentityBackend.system())
        let secondResult = try await store.enroll(IdentityProbe(fixtureID: "second", vector: [3, 4]), candidate: candidate, grant: authorization,
                                                  options: options, backend: try FixtureIdentityBackend.system())
        let first = try XCTUnwrap(firstResult.templateRef)
        let second = try XCTUnwrap(secondResult.templateRef)
        let firstTicket = try await store.beginOperation(candidate, grant: authorization, scope: .enrollment, templateTargets: [first, second])
        let secondTicket = try await store.beginOperation(candidate, grant: authorization, scope: .enrollment, templateTargets: [second])
        let candidateOnly = try await store.beginOperation(candidate, grant: authorization, scope: .enrollment)

        _ = try await store.deleteTemplate(first, ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))

        XCTAssertThrowsError(try firstTicket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertNoThrow(try secondTicket.stopSignal.check())
        XCTAssertNoThrow(try candidateOnly.stopSignal.check())
        do { try await store.finishOperation(firstTicket, backendError: IdentityError(.backendUnavailable)); XCTFail("deleted template ticket published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await store.finishOperation(secondTicket)
        try await store.finishOperation(candidateOnly)
        do { _ = try await store.beginOperation(candidate, grant: authorization, scope: .enrollment, templateTargets: [first]); XCTFail("deleted template target admitted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await store.close()
    }

    func testCandidateDeletionStopsOnlyItsCandidateAndTemplateTickets() async throws {
        let store = try makeStore(root: temporaryRoot(), keys: StoreKeys(), provider: StoreAuthority(epoch: 0))
        let authorization = try grant(); let firstCandidate = try await store.createCandidate(authorization)
        let secondCandidate = try await store.createCandidate(authorization)
        let options = try EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1")
        let firstResult = try await store.enroll(IdentityProbe(fixtureID: "first", vector: [1, 2]), candidate: firstCandidate, grant: authorization,
                                                 options: options, backend: try FixtureIdentityBackend.system())
        let secondResult = try await store.enroll(IdentityProbe(fixtureID: "second", vector: [3, 4]), candidate: secondCandidate, grant: authorization,
                                                  options: options, backend: try FixtureIdentityBackend.system())
        let firstTemplate = try XCTUnwrap(firstResult.templateRef)
        let secondTemplate = try XCTUnwrap(secondResult.templateRef)
        let forged = try TemplateRef(id: firstTemplate.id, embeddingSpaceId: firstTemplate.embeddingSpaceId,
                                     authorizationEpoch: firstTemplate.authorizationEpoch, revision: "forged")
        do { _ = try await store.beginOperation(firstCandidate, grant: authorization, scope: .enrollment, templateTargets: [forged]); XCTFail("forged target admitted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateNotFound) }
        do { _ = try await store.beginOperation(firstCandidate, grant: authorization, scope: .enrollment, templateTargets: [secondTemplate]); XCTFail("foreign candidate target admitted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        let firstTicket = try await store.beginOperation(firstCandidate, grant: authorization, scope: .enrollment, templateTargets: [firstTemplate])
        let secondTicket = try await store.beginOperation(secondCandidate, grant: authorization, scope: .enrollment, templateTargets: [secondTemplate])

        _ = try await store.deleteCandidate(firstCandidate, ownership: OwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner"))

        XCTAssertThrowsError(try firstTicket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertNoThrow(try secondTicket.stopSignal.check())
        do { try await store.finishOperation(firstTicket); XCTFail("deleted candidate ticket published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await store.finishOperation(secondTicket)
        try await store.close()
    }
}

private final class StoreFlag: @unchecked Sendable {
    private let lock = NSLock(); private var stored = false
    var value: Bool { lock.lock(); defer { lock.unlock() }; return stored }
    func set(_ value: Bool) { lock.lock(); stored = value; lock.unlock() }
}
