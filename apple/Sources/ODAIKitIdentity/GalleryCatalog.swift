import Foundation

/// Encrypted, copy-on-write catalog for immutable gallery metadata.
/// IdentityStore remains the authority for template/candidate ownership; this
/// catalog only persists internally consistent snapshot records and CAS heads.
final class GalleryCatalog: @unchecked Sendable {
    let storeId: String
    let identityStoreId: String
    private let journal: IdentityJournal
    private var snapshots: [String: GallerySnapshotRecord] = [:]
    private var heads: [String: String] = [:]
    // A tombstone survives physical cleanup so a deleted lineage cannot revive
    // from an older journal or be recreated by a stale builder after reopen.
    private var tombstones: [String: String] = [:]
    private var purgeFailures: Set<String> = []
    private var closed = false
    private var retired = false
    private var operating = false
    private let lock = NSRecursiveLock()

    init(root: URL, identityStoreId: String, keyProvider: any IdentityKeyProvider) throws {
        try identityToken(identityStoreId)
        let journal = try IdentityJournal(root: root, recordID: "index", keyProvider: keyProvider)
        do {
            if let data = try journal.read() {
                guard let json = String(data: data, encoding: .utf8) else { throw IdentityError(.invalidInput) }
                let fields: [String: Any]
                let version: Int64
                if let v1 = try? identityObject(json, keys: ["version", "storeId", "identityStoreId", "snapshots", "heads"]) {
                    version = try identityInteger(v1, "version"); guard version == 1 else { throw IdentityError(.invalidInput) }; fields = v1
                } else if let v2 = try? identityObject(json, keys: ["version", "storeId", "identityStoreId", "snapshots", "heads", "tombstones"]) {
                    version = try identityInteger(v2, "version"); guard version == 2 else { throw IdentityError(.invalidInput) }; fields = v2
                } else {
                    let v3 = try identityObject(json, keys: ["version", "storeId", "identityStoreId", "snapshots", "heads", "tombstones", "purgeFailures"])
                    version = try identityInteger(v3, "version"); guard version == 3 else { throw IdentityError(.invalidInput) }; fields = v3
                }
                let storeId = try identityString(fields, "storeId")
                let persistedIdentityStoreId = try identityString(fields, "identityStoreId")
                try identityToken(storeId); try identityToken(persistedIdentityStoreId)
                guard persistedIdentityStoreId == identityStoreId,
                      let snapshotJSON = fields["snapshots"] as? [String: String],
                      let persistedHeads = fields["heads"] as? [String: String] else { throw IdentityError(.invalidInput) }
                var restored: [String: GallerySnapshotRecord] = [:]
                for (revision, encoded) in snapshotJSON {
                    let record = try GallerySnapshotRecord.restore(encoded)
                    guard revision == record.ref.revision, record.indexStoreId == storeId,
                          record.identityStoreId == identityStoreId else { throw IdentityError(.invalidInput) }
                    restored[revision] = record
                }
                for (lineage, revision) in persistedHeads {
                    guard let record = restored[revision], record.ref.id == lineage else { throw IdentityError(.invalidInput) }
                }
                for record in restored.values {
                    if let parent = record.parent {
                        guard let parentRecord = restored[parent.revision], parentRecord.ref == parent,
                              parentRecord.ref.id == record.ref.id else { throw IdentityError(.invalidInput) }
                    }
                }
                var tombstones: [String: String] = [:]
                var purgeFailures: Set<String> = []
                if version == 2 || version == 3 {
                    guard let persisted = fields["tombstones"] as? [String: String] else { throw IdentityError(.invalidInput) }
                    var receiptIDs = Set<String>()
                    for (lineage, receipt) in persisted {
                        try identityToken(lineage); try identityToken(receipt)
                        guard receiptIDs.insert(receipt).inserted else { throw IdentityError(.invalidInput) }
                    }
                    tombstones = persisted
                    if version == 3 {
                        guard let values = fields["purgeFailures"] as? [String], values == values.sorted(), Set(values).count == values.count,
                              Set(values).isSubset(of: Set(persisted.keys)), Set(values).isSubset(of: Set(persistedHeads.keys)) else { throw IdentityError(.invalidInput) }
                        for lineage in values { try identityToken(lineage) }
                        purgeFailures = Set(values)
                    }
                }
                var reachable = Set<String>()
                for (lineage, revision) in persistedHeads {
                    var cursor: String? = revision
                    while let current = cursor {
                        guard reachable.insert(current).inserted, let record = restored[current], record.ref.id == lineage else {
                            throw IdentityError(.invalidInput)
                        }
                        cursor = record.parent?.revision
                    }
                }
                guard reachable == Set(restored.keys) else { throw IdentityError(.invalidInput) }
                self.storeId = storeId; self.identityStoreId = identityStoreId
                self.snapshots = restored; self.heads = persistedHeads; self.tombstones = tombstones; self.purgeFailures = purgeFailures
            } else {
                let storeId = UUID().uuidString.lowercased()
                self.storeId = storeId; self.identityStoreId = identityStoreId
                try journal.commit(Self.encode(storeId: storeId, identityStoreId: identityStoreId, snapshots: [:], heads: [:], tombstones: [:], purgeFailures: []))
            }
            self.journal = journal
        } catch {
            try? journal.close(); throw error
        }
    }

    func publish(_ record: GallerySnapshotRecord) throws {
        try access {
            guard record.indexStoreId == storeId, record.identityStoreId == identityStoreId else { throw IdentityError(.invalidInput) }
            guard tombstones[record.ref.id] == nil else { throw IdentityError(.galleryRevisionGone) }
            guard snapshots[record.ref.revision] == nil else { throw IdentityError(.galleryRevisionConflict) }
            if let parent = record.parent {
                guard heads[record.ref.id] == parent.revision, snapshots[parent.revision]?.ref == parent else {
                    throw IdentityError(.galleryRevisionConflict)
                }
            } else if heads[record.ref.id] != nil {
                throw IdentityError(.galleryRevisionConflict)
            }
            var nextSnapshots = snapshots; nextSnapshots[record.ref.revision] = record
            var nextHeads = heads; nextHeads[record.ref.id] = record.ref.revision
            let reserved = Self.reserveTombstoneCapacity(heads: nextHeads, tombstones: tombstones)
            _ = try Self.encode(storeId: storeId, identityStoreId: identityStoreId, snapshots: nextSnapshots, heads: nextHeads, tombstones: reserved, purgeFailures: purgeFailures.union(nextHeads.keys))
            let bytes = try Self.encode(storeId: storeId, identityStoreId: identityStoreId, snapshots: nextSnapshots, heads: nextHeads, tombstones: tombstones, purgeFailures: purgeFailures)
            do { try journal.commit(bytes) } catch { retired = true; throw error }
            snapshots = nextSnapshots; heads = nextHeads
        }
    }

    func snapshot(_ ref: GalleryRef) throws -> GallerySnapshotRecord {
        try access {
            guard tombstones[ref.id] == nil else { throw IdentityError(.galleryRevisionGone) }
            guard let record = snapshots[ref.revision], record.ref == ref else { throw IdentityError(.galleryRevisionGone) }
            return record
        }
    }

    func head(lineageId: String) throws -> GalleryRef? {
        try access {
            guard tombstones[lineageId] == nil else { throw IdentityError(.galleryRevisionGone) }
            guard let revision = heads[lineageId] else { return nil }
            guard let record = snapshots[revision] else { throw IdentityError(.invalidInput) }
            return record.ref
        }
    }

    func tombstoneReceipt(lineageId: String) throws -> String? {
        try access { tombstones[lineageId] }
    }

    func tombstoneReceipts() throws -> [String: String] { try access { tombstones } }

    func deletionStatus(lineageId: String) throws -> DeletionStatus? {
        try access {
            guard tombstones[lineageId] != nil else { return nil }
            if purgeFailures.contains(lineageId) { return .purgeFailed }
            return snapshots.values.contains { $0.ref.id == lineageId } ? .purgePending : .purged
        }
    }

    /// A failed or interrupted removal leaves this conservative durable marker.
    /// Completion removes it atomically with the snapshots. No attempt is made
    /// to rewrite a poisoned journal after a failed marker commit.
    @discardableResult
    func markPurgeAttempt(_ lineageId: String) throws -> Bool {
        try access {
            guard tombstones[lineageId] != nil else { throw IdentityError(.galleryRevisionGone) }
            guard heads[lineageId] != nil else { return false }
            guard !purgeFailures.contains(lineageId) else { return true }
            var next = purgeFailures; next.insert(lineageId)
            let bytes = try Self.encode(storeId: storeId, identityStoreId: identityStoreId, snapshots: snapshots, heads: heads,
                                        tombstones: tombstones, purgeFailures: next)
            do { try journal.commit(bytes) } catch { retired = true; throw error }
            purgeFailures = next
            return true
        }
    }

    func hasSnapshots(lineageId: String) throws -> Bool {
        try access { snapshots.values.contains { $0.ref.id == lineageId } }
    }

    func tombstoneLineage(_ lineageId: String, receiptId: String) throws {
        try access {
            try identityToken(lineageId); try identityToken(receiptId)
            if let existing = tombstones[lineageId] {
                guard existing == receiptId else { throw IdentityError(.invalidInput) }
                return
            }
            guard heads[lineageId] != nil else { throw IdentityError(.galleryNotFound) }
            guard !tombstones.values.contains(receiptId) else { throw IdentityError(.invalidInput) }
            var next = tombstones; next[lineageId] = receiptId
            let reserved = Self.reserveTombstoneCapacity(heads: heads, tombstones: next)
            _ = try Self.encode(storeId: storeId, identityStoreId: identityStoreId, snapshots: snapshots, heads: heads, tombstones: reserved, purgeFailures: purgeFailures.union(heads.keys))
            let bytes = try Self.encode(storeId: storeId, identityStoreId: identityStoreId, snapshots: snapshots, heads: heads, tombstones: next, purgeFailures: purgeFailures)
            do { try journal.commit(bytes) } catch { retired = true; throw error }
            tombstones = next
        }
    }

    func removeLineage(_ lineageId: String) throws {
        try access {
            guard tombstones[lineageId] != nil else { throw IdentityError(.galleryRevisionGone) }
            let remaining = snapshots.filter { $0.value.ref.id != lineageId }
            var nextHeads = heads
            nextHeads.removeValue(forKey: lineageId)
            guard remaining.count != snapshots.count || nextHeads != heads else { return }
            var nextFailures = purgeFailures; nextFailures.remove(lineageId)
            let bytes = try Self.encode(storeId: storeId, identityStoreId: identityStoreId, snapshots: remaining, heads: nextHeads,
                                        tombstones: tombstones, purgeFailures: nextFailures)
            do { try journal.commit(bytes) } catch { retired = true; throw error }
            snapshots = remaining; heads = nextHeads; purgeFailures = nextFailures
        }
    }

    func close() throws {
        lock.lock(); defer { lock.unlock() }
        guard !operating else { throw IdentityError(.busy) }
        guard !closed else { return }; closed = true; try journal.close()
    }

    private func access<T>(_ body: () throws -> T) throws -> T {
        lock.lock(); defer { lock.unlock() }
        guard !closed, !retired else { throw IdentityError(.closed) }
        guard !operating else { throw IdentityError(.busy) }
        operating = true; defer { operating = false }
        return try body()
    }

    private static func encode(storeId: String, identityStoreId: String, snapshots: [String: GallerySnapshotRecord], heads: [String: String], tombstones: [String: String], purgeFailures: Set<String>) throws -> Data {
        let fields: [String: Any] = ["version": 3, "storeId": storeId, "identityStoreId": identityStoreId,
            "snapshots": snapshots.mapValues { $0.canonicalJSON() }, "heads": heads, "tombstones": tombstones,
            "purgeFailures": purgeFailures.sorted()]
        let bytes = Data(identityJSON(fields).utf8)
        guard bytes.count <= 65_536 else { throw IdentityError(.invalidInput) }
        return bytes
    }

    private static func reserveTombstoneCapacity(heads: [String: String], tombstones: [String: String]) -> [String: String] {
        var reserved = tombstones
        for lineage in heads.keys where reserved[lineage] == nil { reserved[lineage] = String(repeating: "r", count: 256) }
        return reserved
    }
}
