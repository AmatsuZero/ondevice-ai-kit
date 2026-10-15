import Foundation
@testable import ODAIKitModelStore
extension ModelCompatibility {
    static let fixture = ModelCompatibility(backendId: "fixture", backendVersion: "1.0.0", osVersion: "15.0", architecture: "arm64")
}
extension ModelManifest {
    static func fixture(file: ModelFile) -> ModelManifest {
        ModelManifest(modelId: "fixture", revision: "v1", upstreamURL: URL(string: "https://models.example/fixture")!, upstreamRevision: "pinned-test-commit", capability: "test-only-data", backendId: "fixture", backendVersion: .init(minimum: "1.0.0", maximumExclusive: "2.0.0"), minimumOS: "15.0", architectures: ["arm64"], formatVersion: "raw-v1", files: [file], processing: .init(inputSignature: "bytes", outputSignature: "bytes", preprocessRevision: "none-v1", tokenizerRevision: nil, promptRevision: nil, maxInputElements: 1024), resources: .init(downloadBytes: file.bytes, temporaryBytes: file.bytes, installedBytesLimit: file.bytes), licenseIdentifier: "CC0-1.0", licenseURL: URL(string: "https://creativecommons.org/publicdomain/zero/1.0/")!, attribution: "SDK synthetic fixture", redistribution: "permitted")
    }
}
