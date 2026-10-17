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
    private let root: URL, recordID: String, provider: any IdentityKeyProvider, context: String
    private let dataURL: URL, lockFD: Int32, stateLock = NSLock()
    private var closed = false
    private var hadRecord = false
    let keyID: String

    init(root: URL, recordID: String, keyProvider: any IdentityKeyProvider) throws {
        try identityToken(recordID)
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
        if FileManager.default.fileExists(atPath: self.dataURL.path) {
            let existing = try Data(contentsOf: self.dataURL)
            guard existing.count >= 7, existing.prefix(4) == Self.magic, existing[4] == 1 else { throw IdentityError(.invalidInput) }
            let length = Int(existing.uint16(at: 5)); let begin = 7
            guard begin + length <= existing.count, let restored = String(data: existing[begin..<(begin + length)], encoding: .utf8) else { throw IdentityError(.invalidInput) }
            try identityToken(restored)
            _ = try Self.decode(existing).open(keyProvider: keyProvider, context: self.context)
            self.keyID = restored; self.hadRecord = true
        } else {
            self.keyID = "journal.\(UUID().uuidString.lowercased())"
        }
        ownsLock = false
    }

    deinit { try? close() }

    func read() throws -> Data? {
        stateLock.lock(); defer { stateLock.unlock() }; try admittedUnlocked()
        guard FileManager.default.fileExists(atPath: dataURL.path) else {
            if hadRecord { poisonUnlocked(); throw IdentityError(.reEnrollmentRequired) }
            return nil
        }
        do { return try Self.decode(Data(contentsOf: dataURL)).open(keyProvider: provider, context: context) }
        catch { poisonUnlocked(); throw error }
    }

    func commit(_ plaintext: Data) throws {
        stateLock.lock(); defer { stateLock.unlock() }; try admittedUnlocked()
        do {
            guard !hadRecord || FileManager.default.fileExists(atPath: dataURL.path) else { throw IdentityError(.reEnrollmentRequired) }
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

    private func admittedUnlocked() throws { if closed { throw IdentityError(.closed) } }
    private func poisonUnlocked() { closed = true; _ = flock(lockFD, LOCK_UN); _ = DarwinOrGlibcClose(lockFD) }

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
