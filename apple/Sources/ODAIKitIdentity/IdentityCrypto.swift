import Foundation
import CryptoKit

public protocol IdentityKeyProvider: Sendable {
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey
    func deleteKey(id: String) throws
}

public struct IdentityEnvelope: Sendable, Equatable {
    public let formatVersion: Int
    public let keyId: String
    public let rotationEpoch: Int64
    public let nonce: Data
    public let ciphertext: Data
    public let tag: Data

    public init(formatVersion: Int, keyId: String, rotationEpoch: Int64, nonce: Data, ciphertext: Data, tag: Data) {
        self.formatVersion = formatVersion; self.keyId = keyId; self.rotationEpoch = rotationEpoch
        self.nonce = nonce; self.ciphertext = ciphertext; self.tag = tag
    }

    public static func seal(_ plaintext: Data, keyId: String, rotationEpoch: Int64, keyProvider: any IdentityKeyProvider, context: String, createIfMissing: Bool = true) throws -> Self {
        try identityToken(keyId); try identityToken(context)
        guard rotationEpoch >= 0 else { throw IdentityError(.invalidInput) }
        let key = try keyProvider.key(id: keyId, createIfMissing: createIfMissing)
        guard key.bitCount == 256 else { throw IdentityError(.invalidInput) }
        let aad = authenticatedHeader(formatVersion: 1, keyId: keyId, rotationEpoch: rotationEpoch, context: context)
        do {
            let box = try AES.GCM.seal(plaintext, using: key, authenticating: aad)
            return Self(formatVersion: 1, keyId: keyId, rotationEpoch: rotationEpoch, nonce: Data(box.nonce), ciphertext: box.ciphertext, tag: box.tag)
        } catch { throw IdentityError(.invalidInput) }
    }

    public func open(keyProvider: any IdentityKeyProvider, context: String) throws -> Data {
        try identityToken(keyId); try identityToken(context)
        guard formatVersion == 1, nonce.count == 12, tag.count == 16, rotationEpoch >= 0 else { throw IdentityError(.invalidInput) }
        let key: SymmetricKey
        do { key = try keyProvider.key(id: keyId, createIfMissing: false) }
        catch let error as IdentityError { throw error }
        catch { throw IdentityError(.reEnrollmentRequired) }
        guard key.bitCount == 256 else { throw IdentityError(.invalidInput) }
        let aad = Self.authenticatedHeader(formatVersion: formatVersion, keyId: keyId, rotationEpoch: rotationEpoch, context: context)
        do { return try AES.GCM.open(try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: nonce), ciphertext: ciphertext, tag: tag), using: key, authenticating: aad) }
        catch { throw IdentityError(.invalidInput) }
    }

    private static func authenticatedHeader(formatVersion: Int, keyId: String, rotationEpoch: Int64, context: String) -> Data {
        Data("odai.identity.v1|\(formatVersion)|\(keyId)|\(rotationEpoch)|\(context)".utf8)
    }
}
