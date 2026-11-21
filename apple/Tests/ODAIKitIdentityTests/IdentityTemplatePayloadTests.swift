import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IdentityTemplatePayloadTests: XCTestCase {
    func testIndependentKeysRoundTripAndExactRemoval() throws {
        let root = temporaryRoot(); let keys = PayloadKeys(); let store = UUID().uuidString.lowercased()
        let payload = try IdentityTemplatePayload(root: root, storeId: store, provider: keys)
        let first = UUID().uuidString.lowercased(); let second = UUID().uuidString.lowercased()
        let backend = try FixtureIdentityBackend.system()
        let embedding = try IdentityEmbedding(embeddingSpace: backend.embeddingSpace, values: [0.25, -0.75])
        try payload.write(recordId: first, embedding: embedding)
        try payload.write(recordId: second, embedding: embedding)
        XCTAssertEqual(keys.created.count, 2)
        let reopened = try IdentityTemplatePayload(root: root, storeId: store, provider: keys)
        XCTAssertEqual(try reopened.read(recordId: first, space: backend.embeddingSpace), embedding)
        let unrelated = root.appendingPathComponent("unowned.bin")
        try Data([9]).write(to: unrelated)
        XCTAssertThrowsError(try payload.write(recordId: first, embedding: embedding))
        try reopened.remove(recordId: first); try reopened.remove(recordId: first)
        XCTAssertTrue(FileManager.default.fileExists(atPath: unrelated.path))
        XCTAssertEqual(try reopened.read(recordId: second, space: backend.embeddingSpace), embedding)
    }

    func testTamperSwapAndMissingKeyFailWithoutRecreation() throws {
        let root = temporaryRoot(); let keys = PayloadKeys(); let store = UUID().uuidString.lowercased()
        let payload = try IdentityTemplatePayload(root: root, storeId: store, provider: keys)
        let first = UUID().uuidString.lowercased(); let second = UUID().uuidString.lowercased()
        let space = try FixtureIdentityBackend.system().embeddingSpace
        let embedding = try IdentityEmbedding(embeddingSpace: space, values: [1, 0])
        try payload.write(recordId: first, embedding: embedding); try payload.write(recordId: second, embedding: embedding)
        let file = root.appendingPathComponent("identity-template-\(first).bin")
        let original = try Data(contentsOf: file)
        try Data(contentsOf: root.appendingPathComponent("identity-template-\(second).bin")).write(to: file)
        XCTAssertThrowsError(try payload.read(recordId: first, space: space))
        var tampered = original; tampered[tampered.count - 1] ^= 1; try tampered.write(to: file)
        XCTAssertThrowsError(try payload.read(recordId: first, space: space))
        try original.write(to: file)
        try keys.deleteKey(id: "template.\(store).\(first)")
        let creations = keys.created.count
        XCTAssertThrowsError(try payload.read(recordId: first, space: space)) { XCTAssertEqual(($0 as? IdentityError)?.code, .reEnrollmentRequired) }
        XCTAssertEqual(keys.created.count, creations)
    }

    func testManagedNamesAndSymlinksAreRejected() throws {
        let root = temporaryRoot(); let keys = PayloadKeys()
        let payload = try IdentityTemplatePayload(root: root, storeId: UUID().uuidString.lowercased(), provider: keys)
        let record = UUID().uuidString.lowercased(); let target = root.appendingPathComponent("unowned")
        try Data([1]).write(to: target)
        try FileManager.default.createSymbolicLink(at: root.appendingPathComponent("identity-template-\(record).bin"), withDestinationURL: target)
        XCTAssertThrowsError(try payload.remove(recordId: record))
        XCTAssertThrowsError(try payload.remove(recordId: "../unowned"))
        XCTAssertEqual(try Data(contentsOf: target), Data([1]))
    }

    func testPayloadBindsCompleteEmbeddingSpaceAndRejectsDanglingSymlink() throws {
        let root = temporaryRoot(); let keys = PayloadKeys(); let record = UUID().uuidString.lowercased()
        let payload = try IdentityTemplatePayload(root: root, storeId: "store", provider: keys)
        let space = try FixtureIdentityBackend.system().embeddingSpace
        try payload.write(recordId: record, embedding: IdentityEmbedding(embeddingSpace: space, values: [1, 0]))
        let wrong = try FixtureIdentityBackend.system(revision: "different-model").embeddingSpace
        XCTAssertThrowsError(try payload.read(recordId: record, space: wrong)) { XCTAssertEqual(($0 as? IdentityError)?.code, .embeddingSpaceMismatch) }
        let dangling = UUID().uuidString.lowercased()
        try FileManager.default.createSymbolicLink(at: root.appendingPathComponent("identity-template-\(dangling).bin"), withDestinationURL: root.appendingPathComponent("absent"))
        XCTAssertThrowsError(try payload.remove(recordId: dangling))
    }

    private func temporaryRoot() -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try! FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }; return root
    }
}

private final class PayloadKeys: IdentityKeyProvider, @unchecked Sendable {
    private var keys: [String: SymmetricKey] = [:]
    var created: [String] = []
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        if let key = keys[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256); keys[id] = key; created.append(id); return key
    }
    func deleteKey(id: String) throws { keys.removeValue(forKey: id) }
}
