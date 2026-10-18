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
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .busy)
        }
        await lease.close(); await lease.close(); try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
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

    func testTemplateDeletionWaitsForLeaseAndKeepsReceiptAfterRestart() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner)
        let pending = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(pending, .purgePending)
        do { _ = try await fixture.store.recordedTemplateCandidate(fixture.ref); XCTFail("tombstoned template accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        await lease.close(); await lease.close()
        let purged = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(purged, .purged)
        try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let restored = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(restored, .purged)
        let repeated = try await reopened.deleteTemplate(fixture.ref, ownership: owner)
        XCTAssertEqual(repeated, receipt)
        try await reopened.close()
    }

    func testTemplateDeletionFailureRetriesAfterRestartWithoutRevivingTemplate() async throws {
        let fixture = try await fixture()
        fixture.keys.rejectTemplateDeletion = true
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        let failed = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(failed, .purgeFailed)
        try await fixture.store.close()
        fixture.keys.rejectTemplateDeletion = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let restored = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(restored, .purged)
        do { _ = try await reopened.recordedTemplateCandidate(fixture.ref); XCTFail("purged template revived") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await reopened.close()
    }

    func testTemplateDeletionRejectsOwnershipAndForgedReceipt() async throws {
        let fixture = try await fixture()
        fixture.authority.denyOwnership = true
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        do { _ = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner); XCTFail("ownership denial ignored") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        fixture.authority.denyOwnership = false
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner)
        let forged = try DeletionReceipt(id: receipt.id, storeId: receipt.storeId, templateId: "forged")
        do { _ = try await fixture.store.awaitDeletion(forged); XCTFail("receipt substitution accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        try await fixture.store.close()
    }

    func testDeletionWaitsForAllLeasesAndRemovesOnlyItsOwnKeyAndFile() async throws {
        let fixture = try await fixture()
        let second = try await fixture.store.enroll(IdentityProbe(fixtureID: "second", vector: [0.5, 0.5]), candidate: fixture.candidate, grant: fixture.grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        let otherRef = try XCTUnwrap(second.templateRef)
        let file = fixture.root.appendingPathComponent("identity-template-\(fixture.ref.id).bin")
        let first = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let last = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        await first.close()
        let pending = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(pending, .purgePending)
        XCTAssertTrue(FileManager.default.fileExists(atPath: file.path))
        XCTAssertEqual(fixture.keys.templateDeletions, 0)
        await last.close()
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
        XCTAssertEqual(fixture.keys.templateDeletions, 1)
        let other = try await fixture.store.acquireTemplateLease(otherRef, grant: fixture.grant, scope: .verification, embeddingSpace: otherRef.embeddingSpaceId)
        XCTAssertEqual(try other.embedding().values, [0.5, 0.5]); await other.close()
        _ = try await fixture.store.createCandidate(grant(id: "after-delete"))
        try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let restored = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(restored, .purged)
        try await reopened.close()
    }

    func testRepeatedDeletionRevalidatesOwnershipAndImmutableRefAfterGrantRevocation() async throws {
        let fixture = try await fixture()
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        _ = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: owner)
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner)
        fixture.authority.denyOwnership = true
        do { _ = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner); XCTFail("repeat bypassed ownership") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        fixture.authority.denyOwnership = false
        let forged = try TemplateRef(id: fixture.ref.id, embeddingSpaceId: fixture.ref.embeddingSpaceId, authorizationEpoch: fixture.ref.authorizationEpoch, revision: "forged")
        do { _ = try await fixture.store.deleteTemplate(forged, ownership: owner); XCTFail("purged ref binding lost") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateNotFound) }
        let wire = try DeletionReceipt.decodeJSON(receipt.canonicalJSON())
        XCTAssertEqual(wire, receipt)
        let crossStore = try DeletionReceipt(id: receipt.id, storeId: "other", templateId: try XCTUnwrap(receipt.templateId))
        do { _ = try await fixture.store.awaitDeletion(crossStore); XCTFail("cross-store receipt accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        try await fixture.store.close()
    }

    func testFailedLogicalDeletionDoesNotRemovePayloadOrPublishTombstone() async throws {
        let fixture = try await fixture()
        fixture.keys.rejectJournal = true
        do { _ = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")); XCTFail("failed commit accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        XCTAssertEqual(fixture.keys.templateDeletions, 0)
        fixture.keys.rejectJournal = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let lease = try await reopened.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        await lease.close(); try await reopened.close(); try await fixture.store.close()
    }

    func testPurgeStatusCommitFailureRetiresWriterAndRecoveryUsesDurableTombstone() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        fixture.keys.onTemplateDelete = { fixture.keys.rejectJournal = true }
        await lease.close()
        do { _ = try await fixture.store.awaitDeletion(receipt); XCTFail("ambiguous writer reported purge") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        fixture.keys.onTemplateDelete = nil; fixture.keys.rejectJournal = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let status = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(status, .purged)
        let count = fixture.keys.templateDeletions
        await lease.close(); try await fixture.store.close()
        XCTAssertEqual(fixture.keys.templateDeletions, count)
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority))
        try await reopened.close()
    }

    func testRetiredPendingLeaseReleaseCannotPerformCleanupOwnedByReopenedWriter() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        fixture.keys.rejectJournal = true
        do { _ = try await fixture.store.createCandidate(grant(id: "retire")); XCTFail("expected writer retirement") } catch {}
        fixture.keys.rejectJournal = false
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        let count = fixture.keys.templateDeletions
        await lease.close(); try await fixture.store.close()
        XCTAssertEqual(fixture.keys.templateDeletions, count)
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let status = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(status, .purged)
        try await reopened.close()
    }

    func testRestoreRejectsInvalidDeletionMetadataBeforeAnyRecoveryCleanup() async throws {
        let fixture = try await fixture()
        fixture.keys.rejectTemplateDeletion = true
        _ = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        try await fixture.store.close()
        let journal = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
        var deletions = try XCTUnwrap(fields["deletions"] as? [String: Any])
        deletions[UUID().uuidString.lowercased()] = ["receiptId": "unowned", "status": "purgePending"]
        fields["deletions"] = deletions
        try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
        fixture.keys.rejectTemplateDeletion = false
        let count = fixture.keys.templateDeletions
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        XCTAssertEqual(fixture.keys.templateDeletions, count)
    }

    func testCandidateDeletionUsesFixedTemplateSetAndPreservesEarlierTemplateReceipt() async throws {
        let fixture = try await fixture()
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        let otherCandidate = try await fixture.store.createCandidate(grant(id: "other-candidate"))
        let otherResult = try await fixture.store.enroll(IdentityProbe(fixtureID: "other", vector: [0.5, 0.5]), candidate: otherCandidate, grant: grant(id: "other-candidate"),
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        let otherRef = try XCTUnwrap(otherResult.templateRef)
        _ = try await fixture.store.enroll(IdentityProbe(fixtureID: "second", vector: [0.5, 0.5]), candidate: fixture.candidate, grant: fixture.grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let templateReceipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner)
        let receipt = try await fixture.store.deleteCandidate(fixture.candidate, ownership: owner)
        XCTAssertEqual(receipt.candidateId, fixture.candidate.id)
        XCTAssertNil(receipt.templateId)
        let pending = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(pending, .purgePending)
        do { _ = try await fixture.store.recordedCandidateGrant(fixture.candidate); XCTFail("candidate revived") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
        let other = try await fixture.store.recordedTemplateCandidate(otherRef)
        XCTAssertEqual(other, otherCandidate)
        await lease.close()
        let purged = try await fixture.store.awaitDeletion(receipt)
        let oldPurged = try await fixture.store.awaitDeletion(templateReceipt)
        XCTAssertEqual(purged, .purged); XCTAssertEqual(oldPurged, .purged)
        try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let repeated = try await reopened.deleteCandidate(fixture.candidate, ownership: owner)
        XCTAssertEqual(repeated, receipt)
        let restored = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(restored, .purged)
        do { _ = try await reopened.enroll(IdentityProbe(fixtureID: "late", vector: [1, 0]), candidate: fixture.candidate, grant: fixture.grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system()); XCTFail("deleted candidate enrolled") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
        try await reopened.close()
    }

    func testEmptyCandidateDeletionPersistsAndRevalidatesOwnershipAndReceiptKind() async throws {
        let fixture = try await fixture()
        let candidate = try await fixture.store.createCandidate(grant(id: "empty"))
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        let receipt = try await fixture.store.deleteCandidate(candidate, ownership: owner)
        let status = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(status, .purged)
        let wire = try DeletionReceipt.decodeJSON(receipt.canonicalJSON())
        XCTAssertEqual(wire, receipt)
        fixture.authority.denyOwnership = true
        do { _ = try await fixture.store.deleteCandidate(candidate, ownership: owner); XCTFail("repeat bypassed owner") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRequired) }
        fixture.authority.denyOwnership = false
        let forged = try DeletionReceipt(id: receipt.id, storeId: receipt.storeId, candidateId: fixture.candidate.id)
        do { _ = try await fixture.store.awaitDeletion(forged); XCTFail("substituted candidate receipt accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        do { _ = try await reopened.recordedCandidateGrant(candidate); XCTFail("empty candidate tombstone lost") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
        try await reopened.close()
    }

    func testCandidateDeletionPurgeFailureRecoversAllTargets() async throws {
        let fixture = try await fixture()
        fixture.keys.rejectTemplateDeletion = true
        let receipt = try await fixture.store.deleteCandidate(fixture.candidate, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        let failed = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(failed, .purgeFailed)
        try await fixture.store.close()
        fixture.keys.rejectTemplateDeletion = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let status = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(status, .purged)
        try await reopened.close()
    }

    func testCandidateDeletionFailureDoesNotPublishTombstoneOrPurge() async throws {
        let fixture = try await fixture()
        fixture.keys.rejectJournal = true
        do { _ = try await fixture.store.deleteCandidate(fixture.candidate, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")); XCTFail("failed commit published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        XCTAssertEqual(fixture.keys.templateDeletions, 0)
        fixture.keys.rejectJournal = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let candidate = try await reopened.recordedTemplateCandidate(fixture.ref)
        XCTAssertEqual(candidate, fixture.candidate)
        try await reopened.close(); try await fixture.store.close()
    }

    func testCandidateDeletionStopsOnlyItsTicketsAndSuppressesBackendFailure() async throws {
        let fixture = try await fixture()
        let other = try await fixture.store.createCandidate(grant(id: "unaffected"))
        let affected = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        let reentered = DispatchSemaphore(value: 0)
        let registration = affected.stopSignal.registerStopHandler {
            Task {
                do { _ = try await fixture.store.recordedCandidateGrant(fixture.candidate); XCTFail("stop preceded durable candidate tombstone") }
                catch { XCTAssertEqual((error as? IdentityError)?.code, .candidateRevoked) }
                reentered.signal()
            }
        }
        let unaffected = try await fixture.store.beginOperation(other, grant: grant(id: "unaffected"), scope: .verification)
        _ = try await fixture.store.deleteCandidate(fixture.candidate, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        XCTAssertEqual(reentered.wait(timeout: .now() + 2), .success, "stop callback did not reenter after commit")
        XCTAssertThrowsError(try affected.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertNoThrow(try unaffected.stopSignal.check())
        do { try await fixture.store.finishOperation(affected, backendError: IdentityError(.backendUnavailable)); XCTFail("deleted candidate published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        try await fixture.store.finishOperation(unaffected)
        registration.close()
        try await fixture.store.close()
    }

    func testRestoreRejectsCandidateTargetWithoutItsTemplatePurgeFact() async throws {
        let fixture = try await fixture()
        _ = try await fixture.store.deleteCandidate(fixture.candidate, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        try await fixture.store.close()
        let journal = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
        fields["deletions"] = [String: Any]()
        try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
    }

    func testRestoreRejectsCandidateReceiptCollisionWithGrantRevocation() async throws {
        let fixture = try await fixture()
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        _ = try await fixture.store.deleteCandidate(fixture.candidate, ownership: owner)
        let revocation = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: owner)
        try await fixture.store.close()
        let journal = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
        var candidates = try XCTUnwrap(fields["candidateDeletions"] as? [String: [String: Any]])
        candidates[fixture.candidate.id]?["receiptId"] = revocation.id
        fields["candidateDeletions"] = candidates
        try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
    }

    func testRevocationReceiptTracksAllDomainTemplatesAndIsStableAfterRestart() async throws {
        let fixture = try await fixture()
        _ = try await fixture.store.enroll(IdentityProbe(fixtureID: "second", vector: [0.5, 0.5]), candidate: fixture.candidate, grant: fixture.grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        let firstLease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        let aliasGrant = try grant(id: "other-candidate")
        let otherCandidate = try await fixture.store.createCandidate(aliasGrant)
        _ = try await enroll(fixture.store, candidate: otherCandidate, grant: aliasGrant)
        let receipt = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: owner)
        let pendingStatus = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(pendingStatus, .purgePending)
        await firstLease.close()
        let purgedStatus = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(purgedStatus, .purged)
        XCTAssertEqual(fixture.keys.templateDeletions, 3)
        try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let alias = try await reopened.revokeGrant(fixture.grant.grantId, ownership: owner)
        XCTAssertEqual(alias, receipt)
        let restoredStatus = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(restoredStatus, .purged)
        try await reopened.close()
    }

    func testNewEpochCandidateCommitsCleanupOfOldDomainTemplates() async throws {
        let fixture = try await fixture()
        fixture.authority.epoch = 1
        let newer = try grant(id: "g2", domain: fixture.grant.revocationDomainId, expiry: 300, epoch: 1)
        let candidate = try await fixture.store.createCandidate(newer)
        do { _ = try await fixture.store.recordedTemplateCandidate(fixture.ref); XCTFail("old template survived newer epoch") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        XCTAssertGreaterThanOrEqual(fixture.keys.templateDeletions, 1)
        _ = candidate
        try await fixture.store.close()
    }

    func testRevocationPurgeFailureStillStopsMatchingTicket() async throws {
        let fixture = try await fixture()
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        let stopped = expectation(description: "revocation stop")
        let registration = ticket.stopSignal.registerStopHandler { stopped.fulfill() }
        fixture.keys.rejectTemplateDeletion = true
        let receipt = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        await fulfillment(of: [stopped], timeout: 2)
        let failedStatus = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(failedStatus, .purgeFailed)
        XCTAssertThrowsError(try ticket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        do { try await fixture.store.finishOperation(ticket); XCTFail("revoked ticket published") } catch {}
        registration.close(); try await fixture.store.close()
    }

    func testV5RevocationMigrationDerivesTemplateTargetsBeforeRecoveryPurge() async throws {
        let fixture = try await fixture()
        fixture.keys.rejectTemplateDeletion = true
        let receipt = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        try await fixture.store.close()
        let journal = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
        var revocations = try XCTUnwrap(fields["revocations"] as? [String: [String: Any]])
        var record = try XCTUnwrap(revocations[receipt.id]); record.removeValue(forKey: "templateIds")
        revocations[receipt.id] = record; fields["revocations"] = revocations; fields["version"] = 5
        fields["deletions"] = [String: Any]()
        try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
        fixture.keys.rejectTemplateDeletion = false
        fixture.keys.onTemplateDelete = { fixture.keys.rejectJournal = true }
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority))
        fixture.keys.onTemplateDelete = nil; fixture.keys.rejectJournal = false
        let migrated = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
        let migratedFields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(migrated.read())) as? [String: Any])
        XCTAssertEqual(migratedFields["version"] as? Int, 6)
        let migratedRevocations = try XCTUnwrap(migratedFields["revocations"] as? [String: [String: Any]])
        XCTAssertEqual(migratedRevocations[receipt.id]?["templateIds"] as? [String], [fixture.ref.id])
        let migratedFacts = try XCTUnwrap(migratedFields["deletions"] as? [String: [String: Any]])
        XCTAssertEqual(migratedFacts[fixture.ref.id]?["status"] as? String, "purgePending")
        try migrated.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let restored = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(restored, .purged)
        try await reopened.close()
    }

    func testV6RevocationTargetForgeryFailsBeforeAnyPurgeIOMutation() async throws {
        let fixture = try await fixture()
        fixture.keys.rejectTemplateDeletion = true
        let receipt = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        try await fixture.store.close()
        let journal = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
        var revocations = try XCTUnwrap(fields["revocations"] as? [String: [String: Any]])
        var record = try XCTUnwrap(revocations[receipt.id]); record["templateIds"] = ["forged-template"]
        revocations[receipt.id] = record; fields["revocations"] = revocations
        try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
        fixture.keys.rejectTemplateDeletion = false
        let count = fixture.keys.templateDeletions
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        XCTAssertEqual(fixture.keys.templateDeletions, count)
    }

    func testV6RevocationRejectsMissingFactAndDuplicateTargetsBeforePurge() async throws {
        for malformed in ["missing-fact", "duplicate-target"] {
            let fixture = try await fixture()
            fixture.keys.rejectTemplateDeletion = true
            let receipt = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
            try await fixture.store.close()
            let journal = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
            var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
            if malformed == "missing-fact" { fields["deletions"] = [String: Any]() }
            else {
                var records = try XCTUnwrap(fields["revocations"] as? [String: [String: Any]])
                records[receipt.id]?["templateIds"] = [fixture.ref.id, fixture.ref.id]
                fields["revocations"] = records
            }
            try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
            fixture.keys.rejectTemplateDeletion = false
            let count = fixture.keys.templateDeletions
            XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority), malformed) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
            XCTAssertEqual(fixture.keys.templateDeletions, count)
        }
    }

    func testNewEpochPurgeStatusFailureStillStopsOldDomainTicket() async throws {
        let fixture = try await fixture()
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        fixture.authority.epoch = 1
        fixture.keys.onTemplateDelete = { fixture.keys.rejectJournal = true }
        do { _ = try await fixture.store.createCandidate(grant(id: "new-epoch", epoch: 1)); XCTFail("status failure ignored") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        XCTAssertThrowsError(try ticket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        do { try await fixture.store.finishOperation(ticket); XCTFail("old ticket completed") } catch {}
        fixture.keys.onTemplateDelete = nil; fixture.keys.rejectJournal = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        do { _ = try await reopened.recordedTemplateCandidate(fixture.ref); XCTFail("stale template recovered") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .templateRevoked) }
        try await fixture.store.close(); try await reopened.close()
    }

    func testPendingStopDeliveryBlocksConcurrentEpochCleanupAndLeaseRelease() async throws {
        for holdLease in [false, true] {
            let fixture = try await fixture()
            let lease = holdLease ? try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId) : nil
            let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
            let entered = expectation(description: "old epoch stop entered")
            let resume = DispatchSemaphore(value: 0)
            let registration = ticket.stopSignal.registerStopHandler { entered.fulfill(); XCTAssertEqual(resume.wait(timeout: .now() + 5), .success) }
            fixture.authority.epoch = 1
            let newer = try grant(id: "epoch-one", epoch: 1)
            let first = Task { try await fixture.store.createCandidate(newer) }
            await fulfillment(of: [entered], timeout: 2)
            _ = try await fixture.store.createCandidate(newer)
            await lease?.close()
            XCTAssertEqual(fixture.keys.templateDeletions, 0, "purge preceded in-progress stop delivery; lease=\(holdLease)")
            resume.signal(); _ = try await first.value
            XCTAssertEqual(fixture.keys.templateDeletions, 1)
            do { try await fixture.store.finishOperation(ticket); XCTFail("old epoch operation completed") } catch {}
            registration.close(); try await fixture.store.close()
        }
    }

    func testOldRevocationReceiptDoesNotIncludeNewEpochTemplatesOrOtherDomains() async throws {
        let fixture = try await fixture()
        let otherGrant = try grant(id: "other-domain", domain: "unaffected-domain")
        let other = try await fixture.store.createCandidate(otherGrant)
        let otherRef = try await enroll(fixture.store, candidate: other, grant: otherGrant)
        let first = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let last = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        let alias = try grant(id: "alias")
        _ = try await fixture.store.createCandidate(alias)
        let templateReceipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner)
        let receipt = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: owner)
        let aliasReceipt = try await fixture.store.revokeGrant(alias.grantId, ownership: owner)
        XCTAssertEqual(aliasReceipt, receipt)
        fixture.authority.epoch = 1
        let newer = try grant(id: "new-epoch", epoch: 1)
        let fresh = try await fixture.store.createCandidate(newer)
        let freshRef = try await enroll(fixture.store, candidate: fresh, grant: newer)
        await first.close()
        let pending = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(pending, .purgePending)
        await last.close()
        let purged = try await fixture.store.awaitDeletion(receipt)
        let oldPurged = try await fixture.store.awaitDeletion(templateReceipt)
        XCTAssertEqual(purged, .purged); XCTAssertEqual(oldPurged, .purged)
        fixture.authority.epoch = 0
        let otherLease = try await fixture.store.acquireTemplateLease(otherRef, grant: otherGrant, scope: .verification, embeddingSpace: otherRef.embeddingSpaceId)
        await otherLease.close()
        fixture.authority.epoch = 1
        let freshLease = try await fixture.store.acquireTemplateLease(freshRef, grant: newer, scope: .verification, embeddingSpace: freshRef.embeddingSpaceId)
        await freshLease.close()
        try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let recovered = try await reopened.recordedTemplateCandidate(freshRef)
        XCTAssertEqual(recovered, fresh)
        let restoredReceipt = try await reopened.revokeGrant(alias.grantId, ownership: owner)
        XCTAssertEqual(restoredReceipt, receipt)
        try await reopened.close()
    }

    func testRevocationStatusCommitFailureCannotSuppressAlreadyRequiredStop() async throws {
        let fixture = try await fixture()
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        fixture.keys.onTemplateDelete = { fixture.keys.rejectJournal = true }
        do { _ = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")); XCTFail("status commit failure ignored") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
        XCTAssertThrowsError(try ticket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked) }
        do { try await fixture.store.finishOperation(ticket); XCTFail("revoked ticket completed") } catch {}
        fixture.keys.onTemplateDelete = nil; fixture.keys.rejectJournal = false
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let receipt = try await reopened.revokeGrant(fixture.grant.grantId, ownership: OwnershipAuthorization(storeId: reopened.storeId, evidenceRef: "owner"))
        let restored = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(restored, .purged)
        try await fixture.store.close(); try await reopened.close()
    }

    func testFailedLogicalRevocationRetiresWithoutPublishingRevocationOrPurge() async throws {
        let fixture = try await fixture()
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        let stopped = expectation(description: "failed writer requests closed")
        let registration = ticket.stopSignal.registerStopHandler { stopped.fulfill() }
        fixture.keys.rejectJournal = true
        do { _ = try await fixture.store.revokeGrant(fixture.grant.grantId, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")); XCTFail("failed logical commit published") } catch {}
        await fulfillment(of: [stopped], timeout: 2)
        XCTAssertThrowsError(try ticket.stopSignal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, .closed) }
        XCTAssertEqual(fixture.keys.templateDeletions, 0)
        fixture.keys.rejectJournal = false
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        do { try await fixture.store.finishOperation(ticket); XCTFail("retired ticket published") } catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        try await fixture.store.close()
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let recovered = try await reopened.recordedTemplateCandidate(fixture.ref)
        XCTAssertEqual(recovered, fixture.candidate)
        registration.close()
        try await reopened.close()
    }

    private func enroll(_ store: IdentityStore, candidate: CandidateRef, grant: AuthorizationGrant) async throws -> TemplateRef {
        let result = try await store.enroll(IdentityProbe(fixtureID: "template", vector: [0.25, -0.75]), candidate: candidate, grant: grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        return try XCTUnwrap(result.templateRef)
    }

    func testRestoreRejectsUnsortedCandidateTemplateTargets() async throws {
        let fixture = try await fixture()
        _ = try await fixture.store.enroll(IdentityProbe(fixtureID: "second", vector: [0.5, 0.5]), candidate: fixture.candidate, grant: fixture.grant,
            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        _ = try await fixture.store.deleteCandidate(fixture.candidate, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        try await fixture.store.close()
        let journal = try IdentityJournal(root: fixture.root, recordID: "store", keyProvider: fixture.keys)
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
        var candidates = try XCTUnwrap(fields["candidateDeletions"] as? [String: [String: Any]])
        var record = try XCTUnwrap(candidates[fixture.candidate.id])
        let targets = try XCTUnwrap(record["templateIds"] as? [String])
        XCTAssertGreaterThanOrEqual(targets.count, 2)
        record["templateIds"] = Array(targets.reversed())
        candidates[fixture.candidate.id] = record
        fields["candidateDeletions"] = candidates
        try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
    }

    func testReceiptAllocatorSkipsEveryOccupiedNamespaceID() throws {
        let occupied: Set<String> = ["occupied-1", "occupied-2"]
        var values = ["occupied-1", "occupied-2", "fresh"]
        let id = try IdentityStore.allocateReceiptID(existing: occupied) { values.removeFirst() }
        XCTAssertEqual(id, "fresh")
    }

    func testExactTemplateStopDeliveryBlocksRepeatedDeletionCandidateCleanupAndLeaseRelease() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification, templateTargets: [fixture.ref])
        let entered = expectation(description: "exact stop entered")
        let resume = DispatchSemaphore(value: 0)
        let registration = ticket.stopSignal.registerStopHandler {
            entered.fulfill()
            _ = resume.wait(timeout: .now() + 5)
        }
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        let deleting = Task { try await fixture.store.deleteTemplate(fixture.ref, ownership: owner) }
        await fulfillment(of: [entered], timeout: 2)
        _ = try await fixture.store.deleteTemplate(fixture.ref, ownership: owner)
        _ = try await fixture.store.deleteCandidate(fixture.candidate, ownership: owner)
        await lease.close()
        XCTAssertEqual(fixture.keys.templateDeletions, 0, "cleanup overtook exact stop delivery")
        resume.signal()
        let receipt = try await deleting.value
        let status = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(status, .purged)
        do { try await fixture.store.finishOperation(ticket, backendError: IdentityError(.backendUnavailable)); XCTFail("revoked operation published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        registration.close(); try await fixture.store.close()
    }

    func testAwaitDeletionDeadlineReturnsPendingFromInjectedMonotonicClock() async throws {
        let clock = MutableLeaseClock()
        let fixture = try await fixture(monotonicClock: clock)
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        let deadline = try IdentityDeadline(clock: clock, durationNanos: 10)
        clock.advance(to: 11)
        let pending = try await fixture.store.awaitDeletion(receipt, deadline: deadline)
        XCTAssertEqual(pending, .purgePending)
        await lease.close(); try await fixture.store.close()
    }

    func testAwaitDeletionWaitsForLeaseReleaseAndHonorsStopSignal() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        let deadline = try IdentityDeadline(clock: LeaseClock(), durationNanos: 1_000_000_000)
        let cancelled = IdentityStopSignal(); cancelled.requestStop(.cancelled)
        do { _ = try await fixture.store.awaitDeletion(receipt, deadline: deadline, stopSignal: cancelled); XCTFail("pre-cancel accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        let active = IdentityStopSignal()
        let stopping = Task { try await fixture.store.awaitDeletion(receipt, deadline: deadline, stopSignal: active) }
        await Task.yield(); active.requestStop(.cancelled)
        do { _ = try await stopping.value; XCTFail("active cancel accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        let waiting = Task { try await fixture.store.awaitDeletion(receipt, deadline: deadline) }
        await Task.yield(); await lease.close()
        let purged = try await waiting.value
        XCTAssertEqual(purged, .purged)
        try await fixture.store.close()
    }

    func testAwaitDeletionTaskCancellationDoesNotUndoPendingTombstone() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let receipt = try await fixture.store.deleteTemplate(fixture.ref, ownership: OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner"))
        let deadline = try IdentityDeadline(clock: LeaseClock(), durationNanos: 1_000_000_000)
        let waiting = Task { try await fixture.store.awaitDeletion(receipt, deadline: deadline) }
        await Task.yield(); waiting.cancel()
        do { _ = try await waiting.value; XCTFail("task cancellation accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        let pending = try await fixture.store.awaitDeletion(receipt)
        XCTAssertEqual(pending, .purgePending)
        await lease.close(); try await fixture.store.close()
    }

    func testRetirementDrainsMixedResourcesInBothCompletionOrders() async throws {
        for ticketFirst in [true, false] {
            let fixture = try await fixture()
            let first = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
            let last = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
            let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
            let stopped = expectation(description: "retirement requests stop")
            let registration = ticket.stopSignal.registerStopHandler { stopped.fulfill() }
            fixture.keys.rejectJournal = true
            do { _ = try await fixture.store.createCandidate(grant(id: "failed")); XCTFail("fault published") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .reEnrollmentRequired) }
            fixture.keys.rejectJournal = false
            await fulfillment(of: [stopped], timeout: 2)
            XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
            let closeOne = Task { try await fixture.store.close() }
            let closeTwo = Task { try await fixture.store.close() }
            await first.close()
            if ticketFirst {
                do { try await fixture.store.finishOperation(ticket); XCTFail("retired ticket published") }
                catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
            } else { await last.close() }
            XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
            if ticketFirst { await last.close() }
            else {
                do { try await fixture.store.finishOperation(ticket); XCTFail("retired ticket published") }
                catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
            }
            try await closeOne.value; try await closeTwo.value
            let reopened = try open(fixture.root, fixture.keys, fixture.authority)
            let before = try Data(contentsOf: fixture.root.appendingPathComponent("identity-journal-store.bin"))
            await first.close(); await last.close(); try await fixture.store.close()
            do { try await fixture.store.finishOperation(ticket); XCTFail("duplicate ticket accepted") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
            XCTAssertEqual(try Data(contentsOf: fixture.root.appendingPathComponent("identity-journal-store.bin")), before)
            XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
            let recovered = try await reopened.recordedTemplateCandidate(fixture.ref)
            XCTAssertEqual(recovered, fixture.candidate)
            registration.close(); try await reopened.close()
        }
    }

    func testCloseWaitsForEmptyTargetStopDeliveryAfterOperationDrains() async throws {
        let fixture = try await fixture()
        let empty = try await fixture.store.createCandidate(grant(id: "empty"))
        let ticket = try await fixture.store.beginOperation(empty, grant: grant(id: "empty"), scope: .verification)
        let entered = expectation(description: "empty target delivery entered")
        let resume = DispatchSemaphore(value: 0)
        let registration = ticket.stopSignal.registerStopHandler {
            entered.fulfill(); _ = resume.wait(timeout: .now() + 5)
        }
        let owner = try OwnershipAuthorization(storeId: fixture.store.storeId, evidenceRef: "owner")
        let deleting = Task { try await fixture.store.deleteCandidate(empty, ownership: owner) }
        await fulfillment(of: [entered], timeout: 2)
        do { try await fixture.store.finishOperation(ticket); XCTFail("revoked ticket published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        let returned = expectation(description: "close returned during delivery"); returned.isInverted = true
        let closing = Task { try await fixture.store.close(); returned.fulfill() }
        await fulfillment(of: [returned], timeout: 0.05)
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        resume.signal()
        let receipt = try await deleting.value; try await closing.value
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        let status = try await reopened.awaitDeletion(receipt)
        XCTAssertEqual(status, .purged)
        registration.close(); try await reopened.close()
    }

    func testStopCallbackCanCloseItsStoreWithoutWaitingForItsOwnDelivery() async throws {
        let fixture = try await fixture()
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        let callbackReturned = expectation(description: "callback returned")
        let closedInside = LeaseFlag()
        let retainedInside = LeaseFlag()
        let registration = ticket.stopSignal.registerStopHandler {
            let done = DispatchSemaphore(value: 0)
            Task {
                try? await fixture.store.finishOperation(ticket)
                try? await fixture.store.close()
                done.signal()
            }
            closedInside.set(done.wait(timeout: .now() + 1) == .success)
            do { let unexpected = try self.open(fixture.root, fixture.keys, fixture.authority); Task { try? await unexpected.close() } }
            catch { retainedInside.set((error as? IdentityError)?.code == .busy) }
            callbackReturned.fulfill()
        }
        let closing = Task { try await fixture.store.close() }
        await fulfillment(of: [callbackReturned], timeout: 2)
        try await closing.value
        XCTAssertTrue(closedInside.value, "callback close waited for its own delivery")
        XCTAssertTrue(retainedInside.value, "callback close released writer before delivery returned")
        let reopened = try open(fixture.root, fixture.keys, fixture.authority)
        registration.close(); try await reopened.close()
    }

    func testDelayedTaskCreatedByStopHandlerStillJoinsRetirementDrain() async throws {
        let fixture = try await fixture()
        let lease = try await fixture.store.acquireTemplateLease(fixture.ref, grant: fixture.grant, scope: .verification, embeddingSpace: fixture.ref.embeddingSpaceId)
        let ticket = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        let callbackReturned = expectation(description: "stop callback returned")
        let delayedFinished = expectation(description: "delayed close finished"); delayedFinished.isInverted = true
        let completed = expectation(description: "delayed close joined drain")
        let registration = ticket.stopSignal.registerStopHandler {
            Task {
                try? await Task.sleep(nanoseconds: 50_000_000)
                try? await fixture.store.close()
                delayedFinished.fulfill()
                completed.fulfill()
            }
            callbackReturned.fulfill()
        }
        let closing = Task { try await fixture.store.close() }
        await fulfillment(of: [callbackReturned], timeout: 2)
        do { try await fixture.store.finishOperation(ticket); XCTFail("closed ticket published") } catch {}
        await fulfillment(of: [delayedFinished], timeout: 0.15)
        await lease.close()
        try await closing.value
        await fulfillment(of: [completed], timeout: 2)
        registration.close()
    }

    func testTaskFromReturnedHandlerJoinsWhenAnotherHandlerOnSameSignalBlocks() async throws {
        try await assertReturnedHandlerTaskJoinsDrain(separateSignals: false)
    }

    func testTaskFromReturnedSignalHandlerJoinsWhenAnotherSignalHandlerBlocks() async throws {
        try await assertReturnedHandlerTaskJoinsDrain(separateSignals: true)
    }

    private func assertReturnedHandlerTaskJoinsDrain(separateSignals: Bool) async throws {
        let fixture = try await fixture()
        let first = try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification)
        let second = separateSignals ? try await fixture.store.beginOperation(fixture.candidate, grant: fixture.grant, scope: .verification) : first
        let secondEntered = expectation(description: "second handler entered")
        let childEntered = expectation(description: "returned handler child calls close")
        let earlyReturn = expectation(description: "child close returned before delivery drained"); earlyReturn.isInverted = true
        let childFinished = expectation(description: "child close eventually joined")
        let startChild = DispatchSemaphore(value: 0)
        let resume = DispatchSemaphore(value: 0)
        let order = LeaseCounter()
        let handler: @Sendable () -> Void = {
            if order.next() == 1 {
                Task {
                    guard startChild.wait(timeout: .now() + 5) == .success else { return }
                    childEntered.fulfill()
                    try? await fixture.store.close()
                    earlyReturn.fulfill()
                    childFinished.fulfill()
                }
            } else {
                secondEntered.fulfill()
                startChild.signal()
                _ = resume.wait(timeout: .now() + 5)
            }
        }
        let registrations = [first.stopSignal.registerStopHandler(handler), second.stopSignal.registerStopHandler(handler)]
        let closing = Task { try await fixture.store.close() }
        await fulfillment(of: [secondEntered, childEntered], timeout: 2)
        // Both tickets must be consumed independently: finish always throws once
        // closed, so a shared do/catch would strand the second operation.
        for ticket in separateSignals ? [first, second] : [first] {
            do { try await fixture.store.finishOperation(ticket); XCTFail("closed ticket published") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        }
        await fulfillment(of: [earlyReturn], timeout: 0.1)
        XCTAssertThrowsError(try open(fixture.root, fixture.keys, fixture.authority)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        resume.signal()
        try await closing.value
        await fulfillment(of: [childFinished], timeout: 2)
        registrations.forEach { $0.close() }
    }

    private struct Fixture {
        let root: URL; let keys: LeaseKeys; let authority: LeaseAuthority
        let store: IdentityStore; let ref: TemplateRef; let candidate: CandidateRef; let grant: AuthorizationGrant
    }
    private func fixture(monotonicClock: any IdentityMonotonicClock = LeaseClock()) async throws -> Fixture {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let keys = LeaseKeys(); let authority = LeaseAuthority(); let store = try open(root, keys, authority, monotonicClock: monotonicClock)
        let grant = try grant(); let candidate = try await store.createCandidate(grant)
        let result = try await store.enroll(IdentityProbe(fixtureID: "lease", vector: [0.25, -0.75]), candidate: candidate, grant: grant,
                                            options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host.quality", revision: "q1"), backend: FixtureIdentityBackend.system())
        return try Fixture(root: root, keys: keys, authority: authority, store: store, ref: XCTUnwrap(result.templateRef), candidate: candidate, grant: grant)
    }
    private func grant(id: String = "g1", domain: String = "tenant.subject", scopes: Set<IdentityScope> = [.enrollment, .verification], expiry: Int64 = 200, epoch: Int64 = 0) throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: scopes, purpose: "login", issuedAtMillis: 0, expiresAtMillis: expiry, authorizationEpoch: epoch, evidenceRef: "audit")
    }
    private func open(_ root: URL, _ keys: LeaseKeys, _ authority: LeaseAuthority, monotonicClock: any IdentityMonotonicClock = LeaseClock()) throws -> IdentityStore {
        try IdentityStore(root: root, purpose: "login", authorizationProvider: authority, keyProvider: keys, wallClock: LeaseClock(), monotonicClock: monotonicClock)
    }
}

private struct LeaseClock: IdentityWallClock, IdentityMonotonicClock { let nowMillis: Int64 = 100; let nowNanos: Int64 = 1 }
private final class MutableLeaseClock: IdentityMonotonicClock, @unchecked Sendable {
    private let lock = NSLock(); private var value: Int64 = 1
    var nowNanos: Int64 { lock.lock(); defer { lock.unlock() }; return value }
    func advance(to next: Int64) { lock.lock(); value = next; lock.unlock() }
}
private final class LeaseAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    var epoch: Int64 = 0
    var denyOwnership = false
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { epoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {
        if denyOwnership { throw IdentityError(.authorizationRequired) }
    }
}
private final class LeaseKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock(); private var keys: [String: SymmetricKey] = [:]
    var onTemplateRead: (@Sendable () -> Void)?
    var rejectJournal = false
    var rejectTemplateDeletion = false
    var templateDeletions = 0
    var onTemplateDelete: (@Sendable () -> Void)?
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if rejectJournal && id.hasPrefix("journal.") { throw IdentityError(.reEnrollmentRequired) }
        if !createIfMissing && id.hasPrefix("template.") { onTemplateRead?() }
        if let key = keys[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256); keys[id] = key; return key
    }
    func deleteKey(id: String) throws {
        lock.lock(); defer { lock.unlock() }
        if rejectTemplateDeletion && id.hasPrefix("template.") { throw IdentityError(.reEnrollmentRequired) }
        keys.removeValue(forKey: id)
        if id.hasPrefix("template.") { templateDeletions += 1; onTemplateDelete?() }
    }
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

private final class LeaseFlag: @unchecked Sendable {
    private let lock = NSLock(); private var stored = false
    var value: Bool { lock.lock(); defer { lock.unlock() }; return stored }
    func set(_ value: Bool) { lock.lock(); stored = value; lock.unlock() }
}

private final class LeaseCounter: @unchecked Sendable {
    private let lock = NSLock(); private var stored = 0
    func next() -> Int { lock.lock(); defer { lock.unlock() }; stored += 1; return stored }
}
