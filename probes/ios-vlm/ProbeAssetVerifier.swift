import CryptoKit
import Foundation

enum ProbeAssetError: Error {
    case invalidManifest
    case unsafePath(String)
    case missingFile(String)
    case sizeMismatch(String)
    case hashMismatch(String)
}

enum ProbeAssetVerifier {
    private struct FileRecord: Decodable {
        let path: String
        let bytes: Int
        let sha256: String
    }

    private struct AssetManifest: Decodable {
        let modelId: String
        let modelRevision: String
        let runtime: String
        let runtimeRevision: String
        let platformMin: String
        let status: String
        let files: [FileRecord]
    }

    static func verify(manifest: URL, modelDirectory: URL) throws -> VLMManifest {
        let data = try Data(contentsOf: manifest)
        let asset = try JSONDecoder().decode(AssetManifest.self, from: data)
        let pinned = VLMManifest.candidate
        guard asset.status == "verified", !asset.files.isEmpty,
              asset.modelId == pinned.modelId,
              asset.modelRevision == pinned.modelRevision,
              asset.runtime == pinned.runtime,
              asset.runtimeRevision == pinned.runtimeRevision,
              asset.platformMin == pinned.platformMin else {
            throw ProbeAssetError.invalidManifest
        }

        let root = modelDirectory.standardizedFileURL.path + "/"
        for item in asset.files {
            guard !item.path.isEmpty, !item.path.hasPrefix("/"),
                  !item.path.split(separator: "/").contains(".."),
                  item.bytes > 0, item.sha256.count == 64 else {
                throw ProbeAssetError.unsafePath(item.path)
            }
            let file = modelDirectory.appendingPathComponent(item.path).standardizedFileURL
            guard file.path.hasPrefix(root) else { throw ProbeAssetError.unsafePath(item.path) }
            let values = try file.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
            guard values.isRegularFile == true, values.isSymbolicLink != true else {
                throw ProbeAssetError.missingFile(item.path)
            }
            guard values.fileSize == item.bytes else { throw ProbeAssetError.sizeMismatch(item.path) }

            let handle = try FileHandle(forReadingFrom: file)
            defer { try? handle.close() }
            var digest = SHA256()
            while let chunk = try handle.read(upToCount: 1024 * 1024), !chunk.isEmpty {
                    digest.update(data: chunk)
            }
            let actual = digest.finalize().map { String(format: "%02x", $0) }.joined()
            guard actual == item.sha256 else { throw ProbeAssetError.hashMismatch(item.path) }
        }
        return VLMManifest(modelId: asset.modelId, modelRevision: asset.modelRevision,
                           runtime: asset.runtime, runtimeRevision: asset.runtimeRevision,
                           platformMin: asset.platformMin, status: asset.status)
    }
}
