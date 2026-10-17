import Foundation
import CryptoKit
import Security

/// Foreground, device-only keys. An existing envelope never triggers key creation.
public struct KeychainIdentityKeyProvider: IdentityKeyProvider {
    private let service: String

    public init(namespace: String) throws {
        try identityToken(namespace)
        service = "io.ondeviceai.identity." + namespace
    }
    private func query(_ id: String) throws -> [String: Any] {
        try identityToken(id)
        return [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                kSecAttrAccount as String: id, kSecAttrSynchronizable as String: kCFBooleanFalse!]
    }
    public func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        var read = try query(id)
        read[kSecReturnData as String] = true
        read[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(read as CFDictionary, &result)
        if status == errSecSuccess {
            guard let bytes = result as? Data, bytes.count == 32 else { throw IdentityError(.reEnrollmentRequired) }
            return SymmetricKey(data: bytes)
        }
        guard status == errSecItemNotFound else { throw IdentityError(.backendUnavailable) }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256)
        var add = try query(id)
        add[kSecValueData as String] = key.withUnsafeBytes { Data($0) }
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let added = SecItemAdd(add as CFDictionary, nil)
        if added == errSecDuplicateItem { return try self.key(id: id, createIfMissing: false) }
        guard added == errSecSuccess else { throw IdentityError(.backendUnavailable) }
        return key
    }
    public func deleteKey(id: String) throws {
        let status = SecItemDelete(try query(id) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw IdentityError(.backendUnavailable) }
    }
}
