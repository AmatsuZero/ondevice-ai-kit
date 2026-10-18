import Foundation
#if canImport(Darwin)
import Darwin
#else
import Glibc
#endif

/// Internal per-template encrypted payload. Ownership is established by the Store
/// journal before this writer is called; this type never sweeps a directory.
final class IdentityTemplatePayload: @unchecked Sendable {
    private static let magic = Data([0x4f, 0x44, 0x54, 0x31]) // ODT1
    private static let maximumBytes = 16 * 1024 * 1024
    private let root: URL
    private let storeId: String
    private let provider: any IdentityKeyProvider

    init(root: URL, storeId: String, provider: any IdentityKeyProvider) throws {
        try identityToken(storeId)
        let canonical = root.standardizedFileURL.resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: canonical, withIntermediateDirectories: true)
        self.root = canonical; self.storeId = storeId; self.provider = provider
    }

    func write(recordId: String, embedding: IdentityEmbedding) throws {
        try validateRecord(recordId)
        guard embedding.embeddingSpace.dtype == .float32 else { throw IdentityError(.featureFormatUnsupported) }
        guard embedding.values.count <= (Self.maximumBytes - 1024) / 4 else { throw IdentityError(.invalidInput) }
        let url = fileURL(recordId); guard !pathExists(url) else { throw IdentityError(.invalidInput) }
        let keyID = keyId(recordId); let context = context(recordId)
        let plaintext = try encode(embedding)
        let envelope = try IdentityEnvelope.seal(plaintext, keyId: keyID, rotationEpoch: 0, keyProvider: provider, context: context)
        let bytes = encode(envelope)
        guard bytes.count <= Self.maximumBytes else { throw IdentityError(.invalidInput) }
        do {
            let fd = open(url.path, O_CREAT | O_EXCL | O_WRONLY, S_IRUSR | S_IWUSR)
            guard fd >= 0 else { throw IdentityError(.invalidInput) }
            let handle = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
            defer { try? handle.close() }
            try handle.write(contentsOf: bytes); try handle.synchronize()
            try syncDirectory()
        } catch {
            throw error is IdentityError ? error : IdentityError(.invalidInput)
        }
    }

    func read(recordId: String, space: EmbeddingSpaceId) throws -> IdentityEmbedding {
        try validateRecord(recordId)
        let url = fileURL(recordId)
        guard pathExists(url) else { throw IdentityError(.templateNotFound) }
        let values = try url.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
        guard values.isRegularFile == true, values.isSymbolicLink != true,
              let size = values.fileSize, size <= Self.maximumBytes else { throw IdentityError(.invalidInput) }
        let data = try Data(contentsOf: url)
        let envelope = try decodeEnvelope(data, recordId: recordId)
        let plaintext = try envelope.open(keyProvider: provider, context: context(recordId))
        return try decode(plaintext, space: space)
    }

    func remove(recordId: String) throws {
        try validateRecord(recordId)
        let url = fileURL(recordId)
        if pathExists(url) {
            let values = try url.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
            guard values.isRegularFile == true, values.isSymbolicLink != true else { throw IdentityError(.invalidInput) }
            try FileManager.default.removeItem(at: url); try syncDirectory()
        }
        try provider.deleteKey(id: keyId(recordId))
    }

    private func validateRecord(_ id: String) throws {
        try identityToken(id)
        guard UUID(uuidString: id) != nil, id == id.lowercased() else { throw IdentityError(.invalidInput) }
    }
    private func keyId(_ id: String) -> String { "template.\(storeId).\(id)" }
    private func context(_ id: String) -> String { "template.\(storeId).\(id)" }
    private func fileURL(_ id: String) -> URL { root.appendingPathComponent("identity-template-\(id).bin", isDirectory: false) }
    private func syncDirectory() throws {
        let fd = open(root.path, O_RDONLY); guard fd >= 0 else { throw IdentityError(.invalidInput) }
        defer { _ = close(fd) }; guard fsync(fd) == 0 else { throw IdentityError(.invalidInput) }
    }
    private func pathExists(_ url: URL) -> Bool {
        var info = stat(); return lstat(url.path, &info) == 0
    }
    private func encode(_ embedding: IdentityEmbedding) throws -> Data {
        let space = Data(embedding.embeddingSpace.canonicalJSON().utf8)
        guard space.count <= 65_536, embedding.values.count <= (Self.maximumBytes - space.count - 1024) / 4 else { throw IdentityError(.invalidInput) }
        var data = Self.magic; data.appendUInt32(UInt32(space.count)); data.append(space); data.appendUInt32(UInt32(embedding.values.count))
        for value in embedding.values { data.appendUInt32(value.bitPattern) }
        return data
    }
    private func decode(_ data: Data, space: EmbeddingSpaceId) throws -> IdentityEmbedding {
        guard data.count >= 12, data.prefix(4) == Self.magic else { throw IdentityError(.invalidInput) }
        let spaceLength = Int(data.uint32(at: 4)); let dimensionOffset = 8 + spaceLength
        guard spaceLength > 0, dimensionOffset + 4 <= data.count,
              let encoded = String(data: data[8..<dimensionOffset], encoding: .utf8) else { throw IdentityError(.invalidInput) }
        guard try EmbeddingSpaceId.decodeJSON(encoded) == space else { throw IdentityError(.embeddingSpaceMismatch) }
        guard
              data.uint32(at: dimensionOffset) == UInt32(space.dimension),
              data.count == dimensionOffset + 4 + space.dimension * 4 else { throw IdentityError(.invalidInput) }
        var values: [Float] = []; values.reserveCapacity(space.dimension)
        for index in 0..<space.dimension { values.append(Float(bitPattern: data.uint32(at: dimensionOffset + 4 + index * 4))) }
        return try IdentityEmbedding(embeddingSpace: space, values: values)
    }
    private func encode(_ envelope: IdentityEnvelope) -> Data {
        let id = Data(envelope.keyId.utf8); var data = Data([0x4f, 0x44, 0x45, 0x31])
        data.appendUInt16(UInt16(id.count)); data.append(id); data.appendUInt64(UInt64(envelope.rotationEpoch))
        data.append(envelope.nonce); data.append(envelope.tag); data.append(envelope.ciphertext); return data
    }
    private func decodeEnvelope(_ data: Data, recordId: String) throws -> IdentityEnvelope {
        guard data.count >= 4 + 2 + 8 + 12 + 16, data.prefix(4) == Data([0x4f, 0x44, 0x45, 0x31]) else { throw IdentityError(.invalidInput) }
        let idLength = Int(data.uint16(at: 4)); let start = 6; let epoch = start + idLength
        guard idLength > 0, data.count >= epoch + 8 + 12 + 16,
              let id = String(data: data[start..<epoch], encoding: .utf8), id == keyId(recordId), data.uint64(at: epoch) == 0 else { throw IdentityError(.invalidInput) }
        let nonce = epoch + 8; let tag = nonce + 12
        return IdentityEnvelope(formatVersion: 1, keyId: id, rotationEpoch: Int64(bitPattern: data.uint64(at: epoch)), nonce: Data(data[nonce..<tag]), ciphertext: Data(data[(tag + 16)...]), tag: Data(data[tag..<(tag + 16)]))
    }
}

private extension Data {
    mutating func appendUInt16(_ value: UInt16) { append(UInt8(value >> 8)); append(UInt8(value & 0xff)) }
    mutating func appendUInt32(_ value: UInt32) { for shift in stride(from: 24, through: 0, by: -8) { append(UInt8((value >> UInt32(shift)) & 0xff)) } }
    mutating func appendUInt64(_ value: UInt64) { for shift in stride(from: 56, through: 0, by: -8) { append(UInt8((value >> UInt64(shift)) & 0xff)) } }
    func uint16(at offset: Int) -> UInt16 { (UInt16(self[offset]) << 8) | UInt16(self[offset + 1]) }
    func uint32(at offset: Int) -> UInt32 { (0..<4).reduce(0) { ($0 << 8) | UInt32(self[offset + $1]) } }
    func uint64(at offset: Int) -> UInt64 { (0..<8).reduce(0) { ($0 << 8) | UInt64(self[offset + $1]) } }
}
