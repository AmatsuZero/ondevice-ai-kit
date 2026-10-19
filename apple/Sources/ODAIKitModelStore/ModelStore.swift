import Foundation
import CryptoKit

/// One store owns one app-private root; do not point multiple stores at the same root.
public actor ModelStore {
    private let root: URL
    private let compatibility: ModelCompatibility
    private var leases: [ModelRef: Set<UUID>] = [:]
    private let fm = FileManager.default
    public init(root: URL, compatibility: ModelCompatibility) throws {
        self.root = root.standardizedFileURL; self.compatibility = compatibility
        try FileManager.default.createDirectory(at: root.appendingPathComponent("installed"), withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: root.appendingPathComponent("staging"), withIntermediateDirectories: true)
        // This root is exclusively owned by this store. Installed revisions are never cleaned here.
        for item in try FileManager.default.contentsOfDirectory(at: root.appendingPathComponent("staging"), includingPropertiesForKeys: nil) {
            try FileManager.default.removeItem(at: item)
        }
    }
    private func directory(_ ref: ModelRef) -> URL { root.appendingPathComponent("installed").appendingPathComponent(ref.modelId).appendingPathComponent(ref.revision) }
    private func validIdentifier(_ value: String) -> Bool {
        !value.isEmpty && value != "." && value != ".." && value.utf8.allSatisfy { (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || [45,46,95].contains($0) }
    }
    private func validateRef(_ ref: ModelRef) throws {
        guard validIdentifier(ref.modelId), validIdentifier(ref.revision) else { throw ModelStoreError.invalidManifest }
    }
    public func validate(_ manifest: ModelManifest) throws {
        try validateRef(manifest.ref)
        guard manifest.schemaVersion == 1, !manifest.files.isEmpty,
              !manifest.upstreamRevision.isEmpty, !manifest.capability.isEmpty, !manifest.formatVersion.isEmpty,
              !manifest.licenseIdentifier.isEmpty, !manifest.attribution.isEmpty, !manifest.redistribution.isEmpty,
              !manifest.processing.preprocessRevision.isEmpty, manifest.processing.maxInputElements > 0,
              !manifest.processing.inputSignature.isEmpty, !manifest.processing.outputSignature.isEmpty else { throw ModelStoreError.invalidManifest }
        for url in [manifest.upstreamURL, manifest.licenseURL] {
            guard url.scheme == "https", let host = url.host, !host.isEmpty,
                  url.user == nil, url.password == nil else { throw ModelStoreError.invalidManifest }
        }
        if let memory = manifest.resources.measuredMemoryBytes {
            guard memory >= 0, let context = manifest.resources.memoryMeasurementContext,
                  !context.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw ModelStoreError.invalidManifest }
        }
        let versions = [manifest.minimumOS, compatibility.osVersion, manifest.backendVersion.minimum, manifest.backendVersion.maximumExclusive, compatibility.backendVersion]
        guard versions.allSatisfy({ !$0.isEmpty && $0.split(separator: ".", omittingEmptySubsequences: false).allSatisfy { !$0.isEmpty && $0.utf8.allSatisfy { (48...57).contains($0) } && Int($0) != nil } }) else { throw ModelStoreError.invalidManifest }
        guard manifest.backendId == compatibility.backendId,
              manifest.architectures.contains(compatibility.architecture),
              compatibility.osVersion.compare(manifest.minimumOS, options: .numeric) != .orderedAscending,
              compatibility.backendVersion.compare(manifest.backendVersion.minimum, options: .numeric) != .orderedAscending,
              compatibility.backendVersion.compare(manifest.backendVersion.maximumExclusive, options: .numeric) == .orderedAscending else { throw ModelStoreError.incompatible }
        var paths = Set<String>(); var total: Int64 = 0
        for file in manifest.files {
            let parts = file.path.split(separator: "/", omittingEmptySubsequences: false)
            guard !parts.isEmpty, parts.allSatisfy({ !$0.isEmpty && $0 != "." && $0 != ".." && !$0.contains("\\") && !$0.contains("\0") }),
                  !file.path.hasPrefix("/"), !file.path.hasPrefix(".odai-"), paths.insert(file.path).inserted,
                  file.bytes >= 0, file.sha256.count == 64, file.sha256.allSatisfy({ $0.isHexDigit && !$0.isUppercase }),
                  file.url.scheme == "https", file.url.host != nil, file.url.user == nil, file.url.password == nil,
                  !total.addingReportingOverflow(file.bytes).overflow else { throw ModelStoreError.invalidManifest }
            total += file.bytes
        }
        guard manifest.resources.downloadBytes == total, manifest.resources.installedBytesLimit >= total,
              manifest.resources.temporaryBytes >= total,
              (manifest.resources.measuredMemoryBytes == nil) == (manifest.resources.memoryMeasurementContext == nil) else { throw ModelStoreError.invalidManifest }
    }
    private func verify(_ manifest: ModelManifest, source: URL, includesManifest: Bool = false) throws {
        let keys: [URLResourceKey] = [.isSymbolicLinkKey, .isRegularFileKey, .isDirectoryKey]
        guard try source.resourceValues(forKeys: [.isSymbolicLinkKey, .isDirectoryKey]).isSymbolicLink != true else { throw ModelStoreError.invalidSource }
        guard let enumerator = fm.enumerator(atPath: source.path) else { throw ModelStoreError.invalidSource }
        var seen = Set<String>()
        for case let relative as String in enumerator {
            let url = source.appendingPathComponent(relative)
            let values = try url.resourceValues(forKeys: Set(keys))
            guard values.isSymbolicLink != true else { throw ModelStoreError.invalidSource }
            if values.isDirectory == true { continue }
            guard values.isRegularFile == true else { throw ModelStoreError.invalidSource }
            if includesManifest && relative == ".odai-manifest.json" { continue }
            seen.insert(relative)
        }
        guard seen == Set(manifest.files.map(\.path)) else { throw ModelStoreError.invalidSource }
        for file in manifest.files {
            let url = source.appendingPathComponent(file.path)
            guard (try url.resourceValues(forKeys: [.fileSizeKey])).fileSize == Int(file.bytes) else { throw ModelStoreError.integrity }
            let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
            var hash = SHA256()
            while let data = try handle.read(upToCount: 1024 * 1024), !data.isEmpty { hash.update(data: data) }
            guard hash.finalize().map({ String(format: "%02x", $0) }).joined() == file.sha256 else { throw ModelStoreError.integrity }
        }
    }
    public func status(_ ref: ModelRef) -> ModelStatus {
        guard (try? validateRef(ref)) != nil else { return .invalid }
        let dir = directory(ref)
        guard fm.fileExists(atPath: dir.path) else { return .absent }
        do {
            let manifest = try JSONDecoder().decode(ModelManifest.self, from: Data(contentsOf: dir.appendingPathComponent(".odai-manifest.json")))
            guard manifest.ref == ref else { return .invalid }
            try validate(manifest); try verify(manifest, source: dir, includesManifest: true)
            return .installed
        } catch { return .invalid }
    }
    public func install(manifest: ModelManifest, source: URL) throws {
        try validate(manifest); try verify(manifest, source: source)
        let target = directory(manifest.ref)
        if fm.fileExists(atPath: target.path) {
            let existing = try Data(contentsOf: target.appendingPathComponent(".odai-manifest.json"))
            let encoder = JSONEncoder(); encoder.outputFormatting = .sortedKeys
            let decoded = try JSONDecoder().decode(ModelManifest.self, from: existing)
            guard try encoder.encode(decoded) == encoder.encode(manifest), status(manifest.ref) == .installed else { throw ModelStoreError.integrity }
            return
        }
        let available = try fm.attributesOfFileSystem(forPath: root.path)[.systemFreeSize] as? NSNumber
        guard let available, available.int64Value >= manifest.resources.temporaryBytes else { throw ModelStoreError.diskSpace }
        let stage = root.appendingPathComponent("staging").appendingPathComponent(UUID().uuidString)
        try fm.createDirectory(at: stage, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: stage) }
        for file in manifest.files {
            let output = stage.appendingPathComponent(file.path)
            try fm.createDirectory(at: output.deletingLastPathComponent(), withIntermediateDirectories: true)
            try fm.copyItem(at: source.appendingPathComponent(file.path), to: output)
        }
        try verify(manifest, source: stage)
        try JSONEncoder().encode(manifest).write(to: stage.appendingPathComponent(".odai-manifest.json"), options: .atomic)
        try fm.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        try fm.moveItem(at: stage, to: target)
    }
    public func acquire(_ ref: ModelRef) throws -> ModelLease {
        guard status(ref) == .installed else { throw ModelStoreError.absent }
        let manifest = try JSONDecoder().decode(ModelManifest.self, from: Data(contentsOf: directory(ref).appendingPathComponent(".odai-manifest.json")))
        let token = UUID(); leases[ref, default: []].insert(token)
        return ModelLease(directory: directory(ref), manifest: manifest, store: self, ref: ref, token: token)
    }
    func release(_ ref: ModelRef, token: UUID) { leases[ref]?.remove(token) }
    public func remove(_ ref: ModelRef) throws {
        try validateRef(ref)
        guard leases[ref, default: []].isEmpty else { throw ModelStoreError.inUse }
        let dir = directory(ref); if fm.fileExists(atPath: dir.path) { try fm.removeItem(at: dir) }
    }
}
