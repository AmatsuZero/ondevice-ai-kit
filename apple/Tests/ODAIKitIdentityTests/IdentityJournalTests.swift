import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IdentityJournalTests: XCTestCase {
    func testLiveCorruptionAndUnexpectedInsertionCannotBeOverwritten() throws {
        let keys = JournalKeys(); let root = temporaryRoot()
        let writer = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.commit(Data([1]))
        let file = root.appendingPathComponent("identity-journal-store.bin")
        var corrupt = try Data(contentsOf: file); corrupt[corrupt.index(before: corrupt.endIndex)] ^= 1
        try corrupt.write(to: file)
        XCTAssertThrowsError(try writer.commit(Data([2])))
        XCTAssertEqual(try Data(contentsOf: file), corrupt)
        try writer.close()
        let blankRoot = temporaryRoot()
        let blank = try IdentityJournal(root: blankRoot, recordID: "store", keyProvider: keys)
        let inserted = blankRoot.appendingPathComponent("identity-journal-store.bin")
        try Data([1, 2, 3]).write(to: inserted)
        XCTAssertThrowsError(try blank.commit(Data([2])))
        XCTAssertEqual(try Data(contentsOf: inserted), Data([1, 2, 3]))
        try blank.close()
    }

    func testRecordIDLengthIsRejectedBeforeWriterAcquisition() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: String(repeating: "a", count: 65), keyProvider: keys))
        let boundary = try IdentityJournal(root: root, recordID: String(repeating: "a", count: 64), keyProvider: keys)
        try boundary.commit(Data([1])); try boundary.close()
    }

    func testOversizedJournalIsRejectedWithoutLoadingPayload() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let file = root.appendingPathComponent("identity-journal-store.bin")
        FileManager.default.createFile(atPath: file.path, contents: nil)
        let handle = try FileHandle(forWritingTo: file); try handle.truncate(atOffset: 16 * 1024 * 1024 + 1); try handle.close()
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "store", keyProvider: keys))
    }

    func testOversizedCommitIsRejectedAndPreservesPriorRecord() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let writer = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.commit(Data([1]))
        XCTAssertThrowsError(try writer.commit(Data(repeating: 7, count: 16 * 1024 * 1024)))
        try writer.close()
        let reopened = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        XCTAssertEqual(try reopened.read(), Data([1])); try reopened.close()
    }

    func testRecoveryRemovesAllAuthenticatedOrphansSharingAnUnpublishedKey() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let writer = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.commit(Data([1])); try writer.close()
        let dataFile = root.appendingPathComponent("identity-journal-store.bin")
        let orphans = (1...2).map { root.appendingPathComponent(".identity-journal-store-00000000-0000-0000-0000-00000000000\($0).tmp") }
        for orphan in orphans { try FileManager.default.copyItem(at: dataFile, to: orphan) }
        try FileManager.default.removeItem(at: dataFile)
        let reopened = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        for orphan in orphans { XCTAssertFalse(FileManager.default.fileExists(atPath: orphan.path)) }
        XCTAssertNil(try reopened.read()); try reopened.close()
    }

    func testRecoveryRemovesOnlyAuthenticatedJournalTemporaryFiles() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let writer = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.commit(Data([1])); try writer.close()
        let dataFile = root.appendingPathComponent("identity-journal-store.bin")
        let orphan = root.appendingPathComponent(".identity-journal-store-00000000-0000-0000-0000-000000000001.tmp")
        let unrelated = root.appendingPathComponent(".identity-journal-store-00000000-0000-0000-0000-000000000002.tmp")
        try FileManager.default.copyItem(at: dataFile, to: orphan)
        try Data([1, 2, 3]).write(to: unrelated)
        let reopened = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        XCTAssertFalse(FileManager.default.fileExists(atPath: orphan.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: unrelated.path))
        XCTAssertEqual(try reopened.read(), Data([1])); try reopened.close()
    }
    func testUnauthenticatedExistingRecordCannotBeOverwrittenWithoutReading() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let writer = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.commit(Data([1])); try writer.close()
        let file = root.appendingPathComponent("identity-journal-store.bin")
        let corrupt = Data([0x4f, 0x44, 0x4a, 0x31, 2])
        try corrupt.write(to: file)
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "store", keyProvider: keys))
        XCTAssertEqual(try Data(contentsOf: file), corrupt)
        try FileManager.default.removeItem(at: file)
        let replacement = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try replacement.close()
    }

    func testMissingLiveKeyAndDeletedRecordCannotBeRecreatedByCommit() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let writer = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.commit(Data([1])); try keys.deleteKey(id: writer.keyID)
        XCTAssertThrowsError(try writer.commit(Data([2]))) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .reEnrollmentRequired)
        }
        XCTAssertThrowsError(try writer.commit(Data([3])))
        try writer.close()
        let secondRoot = temporaryRoot()
        let second = try IdentityJournal(root: secondRoot, recordID: "store", keyProvider: keys)
        try second.commit(Data([1]))
        try FileManager.default.removeItem(at: secondRoot.appendingPathComponent("identity-journal-store.bin"))
        XCTAssertThrowsError(try second.commit(Data([2])))
        try second.close()
    }

    func testRecordIdentifiersCannotEscapeRoot() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "../outside", keyProvider: keys))
        let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try journal.close()
    }

    func testCopiedJournalIsRejectedAndFailedOpenReleasesWriter() throws {
        let keys = JournalKeys(); let firstRoot = temporaryRoot(); let secondRoot = temporaryRoot()
        let first = try IdentityJournal(root: firstRoot, recordID: "store", keyProvider: keys)
        try first.commit(Data([8])); try first.close()
        let secondFile = secondRoot.appendingPathComponent("identity-journal-store.bin")
        try FileManager.default.copyItem(at: firstRoot.appendingPathComponent("identity-journal-store.bin"), to: secondFile)
        XCTAssertThrowsError(try IdentityJournal(root: secondRoot, recordID: "store", keyProvider: keys))
        try FileManager.default.removeItem(at: secondFile)
        let second = try IdentityJournal(root: secondRoot, recordID: "store", keyProvider: keys)
        XCTAssertNotEqual(second.keyID, first.keyID)
        try second.close()
    }

    func testFailedCommitRetiresWriterAndPreservesCommittedRecord() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let provider = FailingJournalKeys(base: keys)
        let writer = try IdentityJournal(root: root, recordID: "store", keyProvider: provider)
        try writer.commit(Data([1]))
        provider.fail = true
        XCTAssertThrowsError(try writer.commit(Data([2])))
        XCTAssertThrowsError(try writer.read())
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "store", keyProvider: keys)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .busy)
        }
        try writer.close()
        let reopened = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.close() // Duplicate retirement close cannot unlock the new writer.
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "other", keyProvider: keys))
        XCTAssertEqual(try reopened.read(), Data([1]))
        try reopened.close()
    }
    func testWriterExclusionCloseReopenAndRoundTrip() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        XCTAssertNil(try journal.read())
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "store", keyProvider: keys))
        try journal.commit(Data("one".utf8)); try journal.close(); try journal.close()
        XCTAssertThrowsError(try journal.read())
        let reopened = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        XCTAssertEqual(try reopened.read(), Data("one".utf8))
        try reopened.commit(Data("two".utf8)); try reopened.close()
        let final = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        XCTAssertEqual(try final.read(), Data("two".utf8)); try final.close()
    }

    func testCiphertextTamperAndMissingKeyFailClosed() throws {
        let root = temporaryRoot(); let keys = JournalKeys()
        let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try journal.commit(Data("secret-record".utf8)); try journal.close()
        let file = root.appendingPathComponent("identity-journal-store.bin")
        XCTAssertFalse(try Data(contentsOf: file).contains(Data("secret-record".utf8)))
        var bytes = try Data(contentsOf: file); bytes[bytes.index(before: bytes.endIndex)] ^= 1; try bytes.write(to: file)
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "store", keyProvider: keys))
        // Restore a valid record then remove only its managed key: opening must not recreate it.
        try FileManager.default.removeItem(at: file)
        let fresh = try IdentityJournal(root: root, recordID: "store", keyProvider: keys); try fresh.commit(Data([1])); let id = fresh.keyID; try fresh.close()
        try keys.deleteKey(id: id)
        XCTAssertThrowsError(try IdentityJournal(root: root, recordID: "store", keyProvider: keys)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .reEnrollmentRequired)
        }
    }

    func testTruncatedHeaderAndContextAcrossRootsFailClosed() throws {
        let keys = JournalKeys(); let firstRoot = temporaryRoot(); let secondRoot = temporaryRoot()
        let first = try IdentityJournal(root: firstRoot, recordID: "store", keyProvider: keys); try first.commit(Data([7])); try first.close()
        let file = firstRoot.appendingPathComponent("identity-journal-store.bin"); try Data([0x4f, 0x44, 0x4a, 0x31, 1, 0xff, 0xff]).write(to: file)
        XCTAssertThrowsError(try IdentityJournal(root: firstRoot, recordID: "store", keyProvider: keys))
        let second = try IdentityJournal(root: secondRoot, recordID: "store", keyProvider: keys)
        XCTAssertNotEqual(second.keyID, first.keyID); try second.close()
    }

    private func temporaryRoot() -> URL { let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString); try! FileManager.default.createDirectory(at: url, withIntermediateDirectories: true); addTeardownBlock { try? FileManager.default.removeItem(at: url) }; return url }
}

private final class FailingJournalKeys: IdentityKeyProvider, @unchecked Sendable {
    let base: JournalKeys
    var fail = false
    init(base: JournalKeys) { self.base = base }
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        if fail { throw IdentityError(.reEnrollmentRequired) }
        return try base.key(id: id, createIfMissing: createIfMissing)
    }
    func deleteKey(id: String) throws { try base.deleteKey(id: id) }
}

private final class JournalKeys: IdentityKeyProvider, @unchecked Sendable {
    private var values: [String: SymmetricKey] = [:]; private let lock = NSLock()
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey { lock.lock(); defer { lock.unlock() }; if let key = values[id] { return key }; guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }; let key = SymmetricKey(size: .bits256); values[id] = key; return key }
    func deleteKey(id: String) throws { lock.lock(); defer { lock.unlock() }; values.removeValue(forKey: id) }
}
