import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IdentityJournalTests: XCTestCase {
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
        let reopened = try IdentityJournal(root: root, recordID: "store", keyProvider: keys)
        try writer.close() // A retired writer cannot unlock the reopened writer.
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
