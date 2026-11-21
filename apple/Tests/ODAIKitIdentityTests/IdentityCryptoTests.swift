import XCTest
import CryptoKit
@testable import ODAIKitIdentity

final class IdentityCryptoTests: XCTestCase {
    func testPlatformProviderRejectsInvalidNamespaceAndKeyBeforeNativeAccess() throws {
        XCTAssertThrowsError(try KeychainIdentityKeyProvider(namespace: "bad|namespace"))
        let provider = try KeychainIdentityKeyProvider(namespace: "fixture-test")
        XCTAssertThrowsError(try provider.key(id: "bad|key", createIfMissing: true)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
        }
        XCTAssertThrowsError(try provider.deleteKey(id: "bad|key"))
    }
    func testForgedKeyContextTokensAreRejectedBeforeLookup() throws {
        let provider = EphemeralKeys()
        let a = try IdentityEnvelope.seal(Data([1, 2]), keyId: "k1", rotationEpoch: 0, keyProvider: provider, context: "store.record")
        let malformed = IdentityEnvelope(formatVersion: 1, keyId: "bad|key", rotationEpoch: 0, nonce: a.nonce, ciphertext: a.ciphertext, tag: a.tag)
        XCTAssertThrowsError(try malformed.open(keyProvider: provider, context: "store.record")) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
        }
    }
    func testAES256EnvelopeAuthenticatesPayloadContextAndRandomNonce() throws {
        let provider = EphemeralKeys()
        let plaintext = Data("synthetic-template".utf8)
        let a = try IdentityEnvelope.seal(plaintext, keyId: "template-key", rotationEpoch: 3, keyProvider: provider, context: "store.record.r1")
        let b = try IdentityEnvelope.seal(plaintext, keyId: "template-key", rotationEpoch: 3, keyProvider: provider, context: "store.record.r1")
        XCTAssertEqual(try a.open(keyProvider: provider, context: "store.record.r1"), plaintext)
        XCTAssertEqual(a.nonce.count, 12)
        XCTAssertEqual(a.tag.count, 16)
        XCTAssertNotEqual(a.nonce, b.nonce)
        XCTAssertNotEqual(a.ciphertext, plaintext)
        XCTAssertThrowsError(try a.open(keyProvider: provider, context: "other.record.r1"))
        XCTAssertThrowsError(try a.open(keyProvider: EphemeralKeys(), context: "store.record.r1"))
    }
    func testTamperedCiphertextAndAuthenticatedHeaderAreRejected() throws {
        let provider = EphemeralKeys()
        let a = try IdentityEnvelope.seal(Data([1, 2, 3]), keyId: "k1", rotationEpoch: 3, keyProvider: provider, context: "store.record")
        var corrupt = a.ciphertext; corrupt[corrupt.startIndex] ^= 1
        for envelope in [
            IdentityEnvelope(formatVersion: 1, keyId: a.keyId, rotationEpoch: 3, nonce: a.nonce, ciphertext: corrupt, tag: a.tag),
            IdentityEnvelope(formatVersion: 1, keyId: a.keyId, rotationEpoch: 4, nonce: a.nonce, ciphertext: a.ciphertext, tag: a.tag),
            IdentityEnvelope(formatVersion: 1, keyId: a.keyId, rotationEpoch: 3, nonce: Data(), ciphertext: a.ciphertext, tag: a.tag)
        ] {
            XCTAssertThrowsError(try envelope.open(keyProvider: provider, context: "store.record"))
        }
    }
    func testMissingKeyNeverCreatesAReplacementAndDeletedKeyCannotDecrypt() throws {
        let provider = EphemeralKeys()
        let a = try IdentityEnvelope.seal(Data([1]), keyId: "k1", rotationEpoch: 0, keyProvider: provider, context: "store.record")
        try provider.deleteKey(id: "k1")
        XCTAssertThrowsError(try a.open(keyProvider: provider, context: "store.record")) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .reEnrollmentRequired)
        }
        XCTAssertTrue(provider.isEmpty)
    }
    func testInvalidKeySizeAndEnvelopeVersionFailClosed() throws {
        XCTAssertThrowsError(try IdentityEnvelope.seal(Data([1]), keyId: "k1", rotationEpoch: 0, keyProvider: EphemeralKeys(bits: .bits128), context: "store.record"))
        let invalid = IdentityEnvelope(formatVersion: 2, keyId: "k1", rotationEpoch: 0, nonce: Data(repeating: 0, count: 12), ciphertext: Data([1]), tag: Data(repeating: 0, count: 16))
        XCTAssertThrowsError(try invalid.open(keyProvider: EphemeralKeys(), context: "store.record"))
    }
}

private final class EphemeralKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock()
    private var keys: [String: SymmetricKey] = [:]
    private let bits: SymmetricKeySize
    init(bits: SymmetricKeySize = .bits256) { self.bits = bits }
    var isEmpty: Bool { lock.lock(); defer { lock.unlock() }; return keys.isEmpty }
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if let key = keys[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: bits); keys[id] = key; return key
    }
    func deleteKey(id: String) throws { lock.lock(); defer { lock.unlock() }; keys.removeValue(forKey: id) }
}
