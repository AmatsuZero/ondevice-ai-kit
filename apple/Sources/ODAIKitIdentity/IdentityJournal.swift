import Foundation
import CryptoKit
#if canImport(Darwin)
import Darwin
#else
import Glibc
#endif

/// Internal, single-record encrypted persistence primitive for IdentityStore.
/// It owns a non-destructive `.identity-journal.lock` advisory lock for its lifetime.
final class IdentityJournal: @unchecked Sendable {
    private static let magic = Data([0x4f, 0x44, 0x4a, 0x31]) // ODJ1
    private static let maximumBytes = 16 * 1024 * 1024
    private let root: URL, recordID: String, provider: any IdentityKeyProvider, context: String
    private let dataURL: URL, lockFD: Int32, stateLock = NSLock()
    private var closed = false
    private var poisoned = false
    private var hadRecord = false
    let keyID: String

    init(root: URL, recordID: String, keyProvider: any IdentityKeyProvider) throws {
        try identityToken(recordID)
        guard recordID.utf8.count <= 64 else { throw IdentityError(.invalidInput) }
        let canonical = root.standardizedFileURL.resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: canonical, withIntermediateDirectories: true)
        var resource = URLResourceValues(); resource.isExcludedFromBackup = true
        var excluded = canonical; try excluded.setResourceValues(resource)
        let lock = canonical.appendingPathComponent(".identity-journal.lock")
        let fd = open(lock.path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
        guard fd >= 0 else { throw IdentityError(.invalidInput) }
        guard flock(fd, LOCK_EX | LOCK_NB) == 0 else { _ = DarwinOrGlibcClose(fd); throw IdentityError(.busy) }
        var ownsLock = true
        defer { if ownsLock { _ = flock(fd, LOCK_UN); _ = DarwinOrGlibcClose(fd) } }
        self.root = canonical; self.recordID = recordID; self.provider = keyProvider
        self.dataURL = canonical.appendingPathComponent("identity-journal-\(recordID).bin")
        self.lockFD = fd
        let digest = SHA256.hash(data: Data(canonical.path.utf8)).map { String(format: "%02x", $0) }.joined()
        self.context = "journal.\(digest).\(recordID)"
        let exists = FileManager.default.fileExists(atPath: self.dataURL.path)
        let restoredID: String
        if exists {
            let envelope = try Self.decode(Self.boundedRead(self.dataURL))
            _ = try envelope.open(keyProvider: keyProvider, context: self.context)
            restoredID = envelope.keyId
        } else { restoredID = "journal.\(UUID().uuidString.lowercased())" }
        try Self.recoverTemporaries(root: canonical, recordID: recordID, activeKeyID: exists ? restoredID : nil, provider: keyProvider, context: self.context)
        self.keyID = restoredID; self.hadRecord = exists
        ownsLock = false
    }

    deinit { try? close() }

    func read() throws -> Data? {
        stateLock.lock(); defer { stateLock.unlock() }; try admittedUnlocked()
        do { return try authenticatedCurrentRecord() }
        catch { poisonUnlocked(); throw error }
    }

    func commit(_ plaintext: Data) throws {
        stateLock.lock(); defer { stateLock.unlock() }; try admittedUnlocked()
        do {
            guard plaintext.count <= Self.maximumBytes - 43 - keyID.utf8.count else { throw IdentityError(.invalidInput) }
            _ = try authenticatedCurrentRecord()
            let envelope = try IdentityEnvelope.seal(plaintext, keyId: keyID, rotationEpoch: 0, keyProvider: provider, context: context, createIfMissing: !hadRecord)
            try persist(encode(envelope))
            hadRecord = true
        } catch {
            poisonUnlocked(); throw error
        }
    }

    func close() throws {
        stateLock.lock(); defer { stateLock.unlock() }
        guard !closed else { return }
        closed = true; _ = flock(lockFD, LOCK_UN); _ = DarwinOrGlibcClose(lockFD)
    }

    private func admittedUnlocked() throws {
        if closed || poisoned { throw IdentityError(.closed) }
    }
    /// Retire admission after an authenticated read/commit failure while
    /// retaining the writer lock. IdentityStore owns the eventual physical
    /// close so live tickets and leases cannot race a reopened writer.
    private func poisonUnlocked() { poisoned = true }

    private func authenticatedCurrentRecord() throws -> Data? {
        let exists = FileManager.default.fileExists(atPath: dataURL.path)
        guard exists == hadRecord else { throw IdentityError(.reEnrollmentRequired) }
        guard exists else { return nil }
        let envelope = try Self.decode(Self.boundedRead(dataURL))
        guard envelope.keyId == keyID else { throw IdentityError(.invalidInput) }
        return try envelope.open(keyProvider: provider, context: context)
    }

    private static func boundedRead(_ url: URL) throws -> Data {
        let values = try url.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey, .isSymbolicLinkKey])
        guard values.isRegularFile == true, values.isSymbolicLink != true,
              let size = values.fileSize, size <= maximumBytes else { throw IdentityError(.invalidInput) }
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        var result = Data()
        while let chunk = try handle.read(upToCount: min(65536, maximumBytes + 1 - result.count)), !chunk.isEmpty {
            result.append(chunk)
            guard result.count <= maximumBytes else { throw IdentityError(.invalidInput) }
        }
        return result
    }

    private static func recoverTemporaries(root: URL, recordID: String, activeKeyID: String?, provider: any IdentityKeyProvider, context: String) throws {
        let prefix = ".identity-journal-\(recordID)-"
        var removed = false; var orphanKeyIDs = Set<String>()
        for url in try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) {
            let name = url.lastPathComponent
            guard name.hasPrefix(prefix), name.hasSuffix(".tmp"),
                  UUID(uuidString: String(name.dropFirst(prefix.count).dropLast(4))) != nil else { continue }
            // A matching name alone does not prove ownership. Keep unrecognized/corrupt files.
            guard let envelope = try? decode(boundedRead(url)),
                  (try? envelope.open(keyProvider: provider, context: context)) != nil else { continue }
            if envelope.keyId != activeKeyID { orphanKeyIDs.insert(envelope.keyId) }
            try FileManager.default.removeItem(at: url); removed = true
        }
        for keyID in orphanKeyIDs { try provider.deleteKey(id: keyID) }
        if removed {
            let fd = open(root.path, O_RDONLY); guard fd >= 0 else { throw IdentityError(.invalidInput) }
            defer { _ = DarwinOrGlibcClose(fd) }
            guard fsync(fd) == 0 else { throw IdentityError(.invalidInput) }
        }
    }

    private func persist(_ bytes: Data) throws {
        let temporary = root.appendingPathComponent(".identity-journal-\(recordID)-\(UUID().uuidString).tmp")
        do {
            let temporaryFD = open(temporary.path, O_CREAT | O_EXCL | O_WRONLY, S_IRUSR | S_IWUSR)
            guard temporaryFD >= 0 else { throw IdentityError(.invalidInput) }
            let handle = FileHandle(fileDescriptor: temporaryFD, closeOnDealloc: true)
            defer { try? handle.close() }
            try handle.write(contentsOf: bytes); try handle.synchronize()
            guard rename(temporary.path, dataURL.path) == 0 else { throw IdentityError(.invalidInput) }
            let fd = open(root.path, O_RDONLY); guard fd >= 0 else { throw IdentityError(.invalidInput) }; defer { _ = DarwinOrGlibcClose(fd) }
            guard fsync(fd) == 0 else { throw IdentityError(.invalidInput) }
        } catch { try? FileManager.default.removeItem(at: temporary); throw error }
    }

    private func encode(_ envelope: IdentityEnvelope) -> Data {
        let id = Data(envelope.keyId.utf8); var result = Self.magic; result.append(1); result.appendUInt16(UInt16(id.count)); result.append(contentsOf: id); result.appendUInt64(UInt64(envelope.rotationEpoch)); result.append(contentsOf: envelope.nonce); result.append(contentsOf: envelope.tag); result.append(contentsOf: envelope.ciphertext); return result
    }
    private static func decode(_ data: Data) throws -> IdentityEnvelope {
        guard data.count >= 4 + 1 + 2 + 8 + 12 + 16, data.prefix(4) == Self.magic, data[4] == 1 else { throw IdentityError(.invalidInput) }
        let idLength = Int(data.uint16(at: 5)); let start = 7; let nonceStart = start + idLength + 8
        guard start + idLength <= data.count, data.count >= nonceStart + 28, let id = String(data: data[start..<(start + idLength)], encoding: .utf8) else { throw IdentityError(.invalidInput) }
        return IdentityEnvelope(formatVersion: 1, keyId: id, rotationEpoch: Int64(bitPattern: data.uint64(at: start + idLength)), nonce: Data(data[nonceStart..<(nonceStart + 12)]), ciphertext: Data(data[(nonceStart + 28)...]), tag: Data(data[(nonceStart + 12)..<(nonceStart + 28)]))
    }
}

private func DarwinOrGlibcClose(_ fd: Int32) -> Int32 { close(fd) }
private extension Data {
    mutating func appendUInt16(_ value: UInt16) { append(UInt8(value >> 8)); append(UInt8(value & 0xff)) }
    mutating func appendUInt64(_ value: UInt64) { for shift in stride(from: 56, through: 0, by: -8) { append(UInt8((value >> UInt64(shift)) & 0xff)) } }
    func uint16(at offset: Int) -> UInt16 { (UInt16(self[offset]) << 8) | UInt16(self[offset + 1]) }
    func uint64(at offset: Int) -> UInt64 { (0..<8).reduce(0) { ($0 << 8) | UInt64(self[offset + $1]) } }
}
