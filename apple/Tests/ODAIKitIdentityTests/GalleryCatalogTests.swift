import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class GalleryCatalogTests: XCTestCase {
    private var roots: [URL] = []
    override func tearDownWithError() throws {
        for root in roots { try FileManager.default.removeItem(at: root) }
    }

    func testEncryptedCatalogPersistsImmutableRevisionsAndCompareAndSwapHead() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let record = try snapshot(indexStoreId: catalog.storeId)
        try catalog.publish(record)
        let child = try snapshot(indexStoreId: catalog.storeId, parent: record.ref)
        try catalog.publish(child)
        XCTAssertEqual(try catalog.head(lineageId: record.ref.id), child.ref)
        XCTAssertEqual(try catalog.snapshot(record.ref), record)
        XCTAssertThrowsError(try catalog.publish(child)) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionConflict) }
        let id = catalog.storeId
        try catalog.close()
        let reopened = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? reopened.close() }
        XCTAssertEqual(reopened.storeId, id)
        XCTAssertEqual(try reopened.head(lineageId: record.ref.id), child.ref)
        XCTAssertEqual(try reopened.snapshot(record.ref), record)
        let file = root.appendingPathComponent("identity-journal-index.bin")
        let bytes = try Data(contentsOf: file)
        XCTAssertNil(String(data: bytes, encoding: .utf8))
    }

    func testCatalogRejectsForeignStoreUnpublishedParentAndClosedWriter() throws {
        let root = temporaryRoot(); let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: CatalogKeys())
        XCTAssertThrowsError(try catalog.publish(snapshot(indexStoreId: "foreign")))
        let parent = try GalleryRef(id: "lineage", revision: "missing")
        XCTAssertThrowsError(try catalog.publish(snapshot(indexStoreId: catalog.storeId, parent: parent))) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionConflict)
        }
        XCTAssertNil(try catalog.head(lineageId: "lineage"))
        try catalog.close()
        XCTAssertThrowsError(try catalog.snapshot(parent)) { XCTAssertEqual(($0 as? IdentityError)?.code, .closed) }
    }

    func testCatalogRestorationRejectsForeignIdentityWithoutRewritingJournal() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        try catalog.publish(snapshot(indexStoreId: catalog.storeId)); try catalog.close()
        let file = root.appendingPathComponent("identity-journal-index.bin")
        let before = try Data(contentsOf: file)
        XCTAssertThrowsError(try GalleryCatalog(root: root, identityStoreId: "foreign", keyProvider: keys))
        XCTAssertEqual(try Data(contentsOf: file), before)
        let reopened = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        try reopened.close()
    }

    func testStrictRestoreRejectsBrokenParentHeadAndSnapshotOwnership() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let first = try snapshot(indexStoreId: "index-store")
        let child = try snapshot(indexStoreId: "index-store", parent: first.ref)
        let fork = try snapshot(indexStoreId: "index-store", parent: first.ref, aggregation: "other-policy")
        let valid: [String: Any] = ["version": 1, "storeId": "index-store", "identityStoreId": "identity-store",
            "snapshots": [first.ref.revision: first.canonicalJSON(), child.ref.revision: child.canonicalJSON()],
            "heads": [first.ref.id: child.ref.revision]]
        let mutations: [(inout [String: Any]) -> Void] = [
            { $0["heads"] = ["lineage": first.ref.revision] },
            { $0["snapshots"] = [child.ref.revision: child.canonicalJSON()] },
            { $0["heads"] = ["wrong": child.ref.revision] },
            { $0["heads"] = [String: String]() },
            { $0["snapshots"] = [first.ref.revision: first.canonicalJSON(), child.ref.revision: child.canonicalJSON(), fork.ref.revision: fork.canonicalJSON()] },
            { $0["storeId"] = "other-store" },
            { $0["version"] = true },
            { $0["unknown"] = "value" }
        ]
        for mutate in mutations {
            var fields = valid; mutate(&fields)
            let journal = try IdentityJournal(root: root, recordID: "index", keyProvider: keys)
            try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
            let before = try Data(contentsOf: root.appendingPathComponent("identity-journal-index.bin"))
            XCTAssertThrowsError(try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys))
            XCTAssertEqual(try Data(contentsOf: root.appendingPathComponent("identity-journal-index.bin")), before)
        }
    }

    func testJournalFaultRetiresAdmissionAndKeepsWriterUntilExplicitClose() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let first = try snapshot(indexStoreId: catalog.storeId)
        try catalog.publish(first)
        keys.denied = true
        XCTAssertThrowsError(try catalog.publish(snapshot(indexStoreId: catalog.storeId, parent: first.ref)))
        XCTAssertThrowsError(try catalog.snapshot(first.ref)) { XCTAssertEqual(($0 as? IdentityError)?.code, .closed) }
        keys.denied = false
        XCTAssertThrowsError(try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .busy)
        }
        try catalog.close()
        let reopened = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        XCTAssertEqual(try reopened.head(lineageId: first.ref.id), first.ref)
        try reopened.close()
    }

    func testConcurrentPublicationHasExactlyOneCasWinner() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? catalog.close() }
        let first = try snapshot(indexStoreId: catalog.storeId)
        try catalog.publish(first)
        let a = try snapshot(indexStoreId: catalog.storeId, parent: first.ref)
        let b = try snapshot(indexStoreId: catalog.storeId, parent: first.ref, aggregation: "different")
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let secondFinished = DispatchSemaphore(value: 0)
        keys.onNextKey = { entered.signal(); _ = release.wait(timeout: .now() + 3) }
        let done = DispatchGroup(); let results = CatalogResults()
        done.enter()
        DispatchQueue.global().async { defer { done.leave() }; results.append(Result { try catalog.publish(a) }) }
        XCTAssertEqual(entered.wait(timeout: .now() + 2), .success)
        done.enter()
        DispatchQueue.global().async { defer { secondFinished.signal(); done.leave() }; results.append(Result { try catalog.publish(b) }) }
        XCTAssertEqual(secondFinished.wait(timeout: .now() + 0.1), .timedOut)
        release.signal()
        XCTAssertEqual(done.wait(timeout: .now() + 3), .success)
        XCTAssertEqual(results.successCount, 1)
        XCTAssertEqual(results.errors, [.galleryRevisionConflict])
        XCTAssertEqual(try catalog.head(lineageId: first.ref.id), a.ref)
    }

    func testReentrantProviderCannotCloseOrMutateCatalogDuringCommit() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? catalog.close() }
        let first = try snapshot(indexStoreId: catalog.storeId)
        keys.onNextKey = {
            do { try catalog.close(); XCTFail("reentrant close must be rejected") }
            catch let error as IdentityError { XCTAssertEqual(error.code, .busy) }
            catch { XCTFail("unexpected reentrant close error") }
            do { try catalog.publish(first); XCTFail("reentrant publish must be rejected") }
            catch let error as IdentityError { XCTAssertEqual(error.code, .busy) }
            catch { XCTFail("unexpected reentrant publish error") }
        }
        try catalog.publish(first)
        XCTAssertEqual(try catalog.head(lineageId: first.ref.id), first.ref)
    }

    func testCatalogCapacityRejectsBeforeCommitAndLeavesPriorHeadUsable() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? catalog.close() }
        var prior: GalleryRef?
        var capacityReached = false
        for _ in 0..<32 {
            let next = try snapshot(indexStoreId: catalog.storeId, parent: prior)
            do { try catalog.publish(next); prior = next.ref }
            catch let error as IdentityError {
                XCTAssertEqual(error.code, .invalidInput)
                capacityReached = true; break
            }
        }
        XCTAssertTrue(capacityReached)
        let last = try XCTUnwrap(prior)
        XCTAssertEqual(try catalog.head(lineageId: last.id), last)
        XCTAssertEqual(try catalog.snapshot(last).ref, last)
    }

    func testCapacityBoundaryReservesLogicalDeletionSpaceForEveryPublishedLineage() throws {
        let catalog = try GalleryCatalog(root: temporaryRoot(), identityStoreId: "identity-store", keyProvider: CatalogKeys())
        defer { try? catalog.close() }
        func records(_ lengths: [Int]) throws -> [GallerySnapshotRecord] {
            try lengths.enumerated().map { index, length in
                try snapshot(indexStoreId: catalog.storeId, aggregation: String(repeating: "a", count: length), lineage: "lineage-\(index)")
            }
        }
        var lengths = Array(repeating: 1, count: 12)
        let baseline = try records(lengths)
        let baseFields: [String: Any] = ["version": 2, "storeId": catalog.storeId, "identityStoreId": "identity-store",
            "snapshots": Dictionary(uniqueKeysWithValues: baseline.map { ($0.ref.revision, $0.canonicalJSON()) }),
            "heads": Dictionary(uniqueKeysWithValues: baseline.map { ($0.ref.id, $0.ref.revision) }), "tombstones": [String: String]()]
        var padding = 65_530 - Data(identityJSON(baseFields).utf8).count
        XCTAssertTrue((1...(12 * 255)).contains(padding))
        for index in lengths.indices { let extra = min(255, padding); lengths[index] += extra; padding -= extra }
        XCTAssertEqual(padding, 0)
        var published: [GalleryRef] = []
        for record in try records(lengths) {
            do { try catalog.publish(record); published.append(record.ref) }
            catch let error as IdentityError { XCTAssertEqual(error.code, .invalidInput); break }
        }
        XCTAssertFalse(published.isEmpty)
        for (index, ref) in published.enumerated() {
            let receipt = String(format: "00000000-0000-0000-0000-%012d", index)
            try catalog.tombstoneLineage(ref.id, receiptId: receipt)
            XCTAssertEqual(try catalog.tombstoneReceipt(lineageId: ref.id), receipt)
        }
        for ref in published {
            try catalog.markPurgeAttempt(ref.id)
            XCTAssertEqual(try catalog.deletionStatus(lineageId: ref.id), .purgeFailed)
        }
    }

    func testDurableLineageTombstoneSurvivesPhysicalCleanupAndRejectsRepublish() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let record = try snapshot(indexStoreId: catalog.storeId)
        try catalog.publish(record)
        XCTAssertThrowsError(try catalog.removeLineage(record.ref.id)) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionGone) }
        try catalog.tombstoneLineage(record.ref.id, receiptId: "delete-receipt")
        try catalog.removeLineage(record.ref.id)
        XCTAssertEqual(try catalog.tombstoneReceipt(lineageId: record.ref.id), "delete-receipt")
        XCTAssertThrowsError(try catalog.head(lineageId: record.ref.id)) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionGone) }
        XCTAssertThrowsError(try catalog.snapshot(record.ref)) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionGone) }
        XCTAssertThrowsError(try catalog.publish(record)) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionGone) }
        try catalog.close()
        let reopened = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? reopened.close() }
        XCTAssertEqual(try reopened.tombstoneReceipt(lineageId: record.ref.id), "delete-receipt")
        XCTAssertThrowsError(try reopened.publish(record)) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionGone) }
    }

    func testTombstoneCommitFaultRetiresAdmissionAndPreservesPriorHeadAfterReopen() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let first = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(first)
        keys.denied = true
        XCTAssertThrowsError(try catalog.tombstoneLineage(first.ref.id, receiptId: "receipt"))
        XCTAssertThrowsError(try catalog.head(lineageId: first.ref.id)) { XCTAssertEqual(($0 as? IdentityError)?.code, .closed) }
        keys.denied = false
        XCTAssertThrowsError(try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)) { XCTAssertEqual(($0 as? IdentityError)?.code, .busy) }
        try catalog.close()
        let reopened = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? reopened.close() }
        XCTAssertNil(try reopened.tombstoneReceipt(lineageId: first.ref.id))
        XCTAssertEqual(try reopened.head(lineageId: first.ref.id), first.ref)
    }

    func testPurgeAttemptIsDurableAcrossOtherLineageChangesAndRetry() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let first = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(first)
        try catalog.tombstoneLineage(first.ref.id, receiptId: "receipt")
        XCTAssertEqual(try catalog.deletionStatus(lineageId: first.ref.id), .purgePending)
        try catalog.markPurgeAttempt(first.ref.id)
        XCTAssertEqual(try catalog.deletionStatus(lineageId: first.ref.id), .purgeFailed)
        let other = try snapshot(indexStoreId: catalog.storeId, lineage: "other"); try catalog.publish(other)
        try catalog.tombstoneLineage(other.ref.id, receiptId: "other-receipt")
        keys.denied = true
        XCTAssertThrowsError(try catalog.removeLineage(first.ref.id))
        keys.denied = false; try catalog.close()
        let restored = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? restored.close() }
        XCTAssertEqual(try restored.deletionStatus(lineageId: first.ref.id), .purgeFailed)
        XCTAssertEqual(try restored.deletionStatus(lineageId: other.ref.id), .purgePending)
        XCTAssertThrowsError(try restored.snapshot(first.ref))
        try restored.removeLineage(first.ref.id)
        XCTAssertEqual(try restored.deletionStatus(lineageId: first.ref.id), .purged)
        keys.denied = true
        XCTAssertNoThrow(try restored.removeLineage(first.ref.id))
        XCTAssertNoThrow(try restored.markPurgeAttempt(first.ref.id))
        XCTAssertEqual(try restored.deletionStatus(lineageId: first.ref.id), .purged)
    }

    func testV2PendingTombstoneUpgradesToRecoverablePurgeAttempt() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let record = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(record)
        try catalog.tombstoneLineage(record.ref.id, receiptId: "receipt"); try catalog.close()
        let journal = try IdentityJournal(root: root, recordID: "index", keyProvider: keys)
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(journal.read())) as? [String: Any])
        fields["version"] = 2; fields.removeValue(forKey: "purgeFailures")
        try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
        let legacy = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        XCTAssertEqual(try legacy.deletionStatus(lineageId: record.ref.id), .purgePending)
        try legacy.markPurgeAttempt(record.ref.id); try legacy.close()
        let upgraded = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? upgraded.close() }
        XCTAssertEqual(try upgraded.deletionStatus(lineageId: record.ref.id), .purgeFailed)
        try upgraded.removeLineage(record.ref.id)
        XCTAssertEqual(try upgraded.deletionStatus(lineageId: record.ref.id), .purged)
    }

    func testNearCapacityV2MigrationRejectsPurgeMarkerWithoutMutatingLegacyJournal() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let seed = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let storeId = seed.storeId; try seed.close()
        var lengths = Array(repeating: 1, count: 12)
        func records() throws -> [GallerySnapshotRecord] {
            try lengths.enumerated().map { index, length in
                try snapshot(indexStoreId: storeId, aggregation: String(repeating: "a", count: length), lineage: "legacy-\(index)")
            }
        }
        func v2(_ records: [GallerySnapshotRecord]) -> [String: Any] {
            ["version": 2, "storeId": storeId, "identityStoreId": "identity-store",
             "snapshots": Dictionary(uniqueKeysWithValues: records.map { ($0.ref.revision, $0.canonicalJSON()) }),
             "heads": Dictionary(uniqueKeysWithValues: records.map { ($0.ref.id, $0.ref.revision) }),
             "tombstones": [records[0].ref.id: "legacy-receipt"]]
        }
        var baseline = try records()
        var padding = 65_530 - Data(identityJSON(v2(baseline)).utf8).count
        XCTAssertTrue((1...(12 * 255)).contains(padding))
        for index in lengths.indices { let extra = min(255, padding); lengths[index] += extra; padding -= extra }
        XCTAssertEqual(padding, 0)
        baseline = try records()
        let legacy = v2(baseline); let legacyBytes = Data(identityJSON(legacy).utf8)
        XCTAssertEqual(legacyBytes.count, 65_530)
        var attempt = legacy; attempt["version"] = 3; attempt["purgeFailures"] = [baseline[0].ref.id]
        XCTAssertGreaterThan(Data(identityJSON(attempt).utf8).count, 65_536)
        let journal = try IdentityJournal(root: root, recordID: "index", keyProvider: keys)
        try journal.commit(legacyBytes); try journal.close()
        let file = root.appendingPathComponent("identity-journal-index.bin")
        let before = try Data(contentsOf: file)
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? catalog.close() }
        XCTAssertThrowsError(try catalog.markPurgeAttempt(baseline[0].ref.id)) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        XCTAssertEqual(try catalog.deletionStatus(lineageId: baseline[0].ref.id), .purgePending)
        XCTAssertEqual(try catalog.snapshot(baseline[1].ref).ref, baseline[1].ref)
        XCTAssertEqual(try Data(contentsOf: file), before)
    }

    func testPurgeFailureCodecRejectsUnboundOrDuplicateMarkersWithoutRewriting() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let first = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(first)
        try catalog.tombstoneLineage(first.ref.id, receiptId: "receipt")
        try catalog.markPurgeAttempt(first.ref.id); try catalog.close()
        let journal = try IdentityJournal(root: root, recordID: "index", keyProvider: keys)
        let original = try XCTUnwrap(journal.read()); try journal.close()
        let base = try XCTUnwrap(JSONSerialization.jsonObject(with: original) as? [String: Any])
        XCTAssertEqual(base["version"] as? Int, 3)
        let mutations: [(inout [String: Any]) -> Void] = [
            { $0["purgeFailures"] = ["unknown"] },
            { $0["purgeFailures"] = [first.ref.id, first.ref.id] },
            { $0["purgeFailures"] = "invalid" },
            { $0["snapshots"] = [String: String](); $0["heads"] = [String: String]() }
        ]
        for mutate in mutations {
            var fields = base; mutate(&fields)
            let bytes = Data(identityJSON(fields).utf8)
            let writer = try IdentityJournal(root: root, recordID: "index", keyProvider: keys)
            try writer.commit(bytes); try writer.close()
            XCTAssertThrowsError(try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys))
            let reader = try IdentityJournal(root: root, recordID: "index", keyProvider: keys)
            XCTAssertEqual(try reader.read(), bytes); try reader.close()
        }
    }

    func testPhysicalCleanupFaultPreservesDurableTombstoneAndRecoveryCanRetry() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let catalog = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        let first = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(first)
        try catalog.tombstoneLineage(first.ref.id, receiptId: "receipt")
        keys.denied = true
        XCTAssertThrowsError(try catalog.removeLineage(first.ref.id))
        keys.denied = false; try catalog.close()
        let reopened = try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys)
        defer { try? reopened.close() }
        XCTAssertEqual(try reopened.tombstoneReceipt(lineageId: first.ref.id), "receipt")
        XCTAssertThrowsError(try reopened.snapshot(first.ref)) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionGone) }
        try reopened.removeLineage(first.ref.id)
        XCTAssertFalse(try reopened.hasSnapshots(lineageId: first.ref.id))
        keys.denied = true
        // Completed cleanup is idempotent and does not attempt another journal write.
        XCTAssertNoThrow(try reopened.removeLineage(first.ref.id))
        keys.denied = false
    }

    func testDeletionReceiptIdentityAndLineagesRemainIsolated() throws {
        let catalog = try GalleryCatalog(root: temporaryRoot(), identityStoreId: "identity-store", keyProvider: CatalogKeys())
        defer { try? catalog.close() }
        let first = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(first)
        let other = try snapshot(indexStoreId: catalog.storeId, lineage: "other"); try catalog.publish(other)
        XCTAssertThrowsError(try catalog.tombstoneLineage("missing", receiptId: "receipt")) { XCTAssertEqual(($0 as? IdentityError)?.code, .galleryNotFound) }
        try catalog.tombstoneLineage(first.ref.id, receiptId: "receipt")
        try catalog.tombstoneLineage(first.ref.id, receiptId: "receipt")
        XCTAssertThrowsError(try catalog.tombstoneLineage(first.ref.id, receiptId: "different")) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        XCTAssertThrowsError(try catalog.tombstoneLineage(other.ref.id, receiptId: "receipt")) { XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput) }
        try catalog.removeLineage(first.ref.id)
        XCTAssertEqual(try catalog.snapshot(other.ref), other)
        XCTAssertEqual(try catalog.head(lineageId: other.ref.id), other.ref)
        let receipt = try DeletionReceipt(id: "receipt", storeId: catalog.storeId, galleryId: first.ref.id)
        XCTAssertEqual(try DeletionReceipt.decodeJSON(receipt.canonicalJSON()), receipt)
        XCTAssertThrowsError(try DeletionReceipt.decodeJSON("{\"id\":\"r\",\"storeId\":\"s\",\"galleryId\":\"g\",\"templateId\":\"t\"}"))
    }

    func testStrictRestoreRejectsAliasedOrMalformedTombstoneReceiptsWithoutRewriting() throws {
        let root = temporaryRoot(); let keys = CatalogKeys()
        let valid: [String: Any] = ["version": 2, "storeId": "index-store", "identityStoreId": "identity-store",
            "snapshots": [String: String](), "heads": [String: String](), "tombstones": ["deleted": "receipt"]]
        let mutations: [(inout [String: Any]) -> Void] = [
            { $0["tombstones"] = ["deleted": 1] },
            { $0["tombstones"] = ["deleted": "receipt", "other": "receipt"] },
            { $0["tombstones"] = ["bad token": "receipt"] },
            { $0["tombstones"] = ["deleted": "bad token"] },
            { $0.removeValue(forKey: "tombstones") }, { $0["unknown"] = true }
        ]
        for mutate in mutations {
            var fields = valid; mutate(&fields)
            let journal = try IdentityJournal(root: root, recordID: "index", keyProvider: keys)
            try journal.commit(Data(identityJSON(fields).utf8)); try journal.close()
            let file = root.appendingPathComponent("identity-journal-index.bin"); let before = try Data(contentsOf: file)
            XCTAssertThrowsError(try GalleryCatalog(root: root, identityStoreId: "identity-store", keyProvider: keys))
            XCTAssertEqual(try Data(contentsOf: file), before)
        }
    }

    func testQueryPublicationHoldsSnapshotGateUntilResultCommit() throws {
        let catalog = try GalleryCatalog(root: temporaryRoot(), identityStoreId: "identity-store", keyProvider: CatalogKeys())
        defer { try? catalog.close() }
        let record = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(record)
        let entered = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let deleteStarted = DispatchSemaphore(value: 0), deleteFinished = DispatchSemaphore(value: 0)
        let done = DispatchGroup(), results = CatalogResults()
        done.enter()
        DispatchQueue.global().async {
            defer { done.leave() }
            results.append(Result { try catalog.withSnapshot(record.ref) { _ in entered.signal(); _ = release.wait(timeout: .now() + 3) } })
        }
        XCTAssertEqual(entered.wait(timeout: .now() + 2), .success)
        done.enter()
        DispatchQueue.global().async {
            defer { deleteFinished.signal(); done.leave() }
            deleteStarted.signal()
            results.append(Result { try catalog.tombstoneLineage(record.ref.id, receiptId: "receipt") })
        }
        XCTAssertEqual(deleteStarted.wait(timeout: .now() + 2), .success)
        XCTAssertEqual(deleteFinished.wait(timeout: .now() + 0.1), .timedOut)
        release.signal(); XCTAssertEqual(done.wait(timeout: .now() + 3), .success)
        XCTAssertEqual(results.successCount, 2)
        XCTAssertThrowsError(try catalog.withSnapshot(record.ref) { _ in XCTFail("deleted snapshot published") }) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .galleryRevisionGone)
        }
    }

    func testQueryResultSealingRejectsPublicationWithoutDeletingSnapshot() throws {
        let catalog = try GalleryCatalog(root: temporaryRoot(), identityStoreId: "identity-store", keyProvider: CatalogKeys())
        defer { try? catalog.close() }
        let record = try snapshot(indexStoreId: catalog.storeId); try catalog.publish(record)
        catalog.sealQueryResults()
        XCTAssertThrowsError(try catalog.withSnapshot(record.ref) { _ in XCTFail("sealed query published") }) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .closed)
        }
        XCTAssertEqual(try catalog.snapshot(record.ref), record)
    }

    private func snapshot(indexStoreId: String, parent: GalleryRef? = nil, aggregation: String = "subject-max-v1", lineage: String? = nil) throws -> GallerySnapshotRecord {
        let fixture = try loadFixture()
        let original = try GallerySnapshotRecord.restore(XCTUnwrap(fixture["snapshotJSON"]))
        return try GallerySnapshotRecord(indexStoreId: indexStoreId, identityStoreId: original.identityStoreId,
            lineageId: lineage ?? original.ref.id, parent: parent, members: original.members, operationGrant: original.operationGrant,
            policy: original.policy, aggregationPolicyId: aggregation)
    }
    private func loadFixture() throws -> [String: String] {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let fields = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("fixtures/contracts/gallery-snapshots.json"))) as? [String: Any])
        return try XCTUnwrap((fields["records"] as? [[String: String]])?.first)
    }
    private func temporaryRoot() -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("gallery-catalog-\(UUID().uuidString)")
        roots.append(root); return root
    }
}

private final class CatalogKeys: IdentityKeyProvider, @unchecked Sendable {
    private var keys: [String: SymmetricKey] = [:]; private let lock = NSLock()
    var denied = false
    var onNextKey: (() -> Void)?
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); let callback = onNextKey; onNextKey = nil; lock.unlock()
        callback?()
        lock.lock(); defer { lock.unlock() }
        if denied { throw IdentityError(.reEnrollmentRequired) }
        if let key = keys[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256); keys[id] = key; return key
    }
    func deleteKey(id: String) throws { lock.lock(); defer { lock.unlock() }; keys.removeValue(forKey: id) }
}

private final class CatalogResults: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [Result<Void, Error>] = []
    func append(_ value: Result<Void, Error>) { lock.lock(); defer { lock.unlock() }; values.append(value) }
    var successCount: Int { lock.lock(); defer { lock.unlock() }; return values.filter { if case .success = $0 { return true }; return false }.count }
    var errors: [IdentityErrorCode] { lock.lock(); defer { lock.unlock() }; return values.compactMap { if case .failure(let error) = $0 { return (error as? IdentityError)?.code }; return nil } }
}
