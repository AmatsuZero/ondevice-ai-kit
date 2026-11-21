import Foundation
public struct ModelRef: Hashable, Codable, Sendable {
    public let modelId: String
    public let revision: String
    public init(modelId: String, revision: String) { self.modelId = modelId; self.revision = revision }
}
public struct ModelFile: Codable, Sendable {
    public var path: String
    public var url: URL
    public var bytes: Int64
    public var sha256: String
    public init(path: String, url: URL, bytes: Int64, sha256: String) { self.path = path; self.url = url; self.bytes = bytes; self.sha256 = sha256 }
}
public struct VersionRange: Codable, Sendable {
    public var minimum: String
    public var maximumExclusive: String
    public init(minimum: String, maximumExclusive: String) { self.minimum = minimum; self.maximumExclusive = maximumExclusive }
}
public struct ModelProcessing: Codable, Sendable {
    public let inputSignature: String
    public let outputSignature: String
    public let preprocessRevision: String
    public let tokenizerRevision: String?
    public let promptRevision: String?
    public let maxInputElements: Int
    public init(inputSignature: String, outputSignature: String, preprocessRevision: String, tokenizerRevision: String?, promptRevision: String?, maxInputElements: Int) {
        self.inputSignature = inputSignature; self.outputSignature = outputSignature; self.preprocessRevision = preprocessRevision
        self.tokenizerRevision = tokenizerRevision; self.promptRevision = promptRevision; self.maxInputElements = maxInputElements
    }
}
public struct ModelResources: Codable, Sendable {
    public let downloadBytes: Int64
    public let temporaryBytes: Int64
    public let installedBytesLimit: Int64
    public let measuredMemoryBytes: Int64?
    public let memoryMeasurementContext: String?
    public init(downloadBytes: Int64, temporaryBytes: Int64, installedBytesLimit: Int64, measuredMemoryBytes: Int64? = nil, memoryMeasurementContext: String? = nil) {
        self.downloadBytes = downloadBytes; self.temporaryBytes = temporaryBytes; self.installedBytesLimit = installedBytesLimit
        self.measuredMemoryBytes = measuredMemoryBytes; self.memoryMeasurementContext = memoryMeasurementContext
    }
}
public struct ModelManifest: Codable, Sendable {
    public var schemaVersion: Int
    public var modelId: String
    public var revision: String
    public let upstreamURL: URL
    public let upstreamRevision: String
    public let capability: String
    public let backendId: String
    public var backendVersion: VersionRange
    public var minimumOS: String
    public let architectures: [String]
    public let formatVersion: String
    public var files: [ModelFile]
    public let processing: ModelProcessing
    public let resources: ModelResources
    public let licenseIdentifier: String
    public let licenseURL: URL
    public let attribution: String
    public let redistribution: String
    public var ref: ModelRef { .init(modelId: modelId, revision: revision) }
    public init(schemaVersion: Int = 1, modelId: String, revision: String, upstreamURL: URL, upstreamRevision: String, capability: String, backendId: String, backendVersion: VersionRange, minimumOS: String, architectures: [String], formatVersion: String, files: [ModelFile], processing: ModelProcessing, resources: ModelResources, licenseIdentifier: String, licenseURL: URL, attribution: String, redistribution: String) {
        self.schemaVersion = schemaVersion; self.modelId = modelId; self.revision = revision; self.upstreamURL = upstreamURL
        self.upstreamRevision = upstreamRevision; self.capability = capability; self.backendId = backendId; self.backendVersion = backendVersion
        self.minimumOS = minimumOS; self.architectures = architectures; self.formatVersion = formatVersion; self.files = files
        self.processing = processing; self.resources = resources; self.licenseIdentifier = licenseIdentifier; self.licenseURL = licenseURL
        self.attribution = attribution; self.redistribution = redistribution
    }
}
public struct ModelCompatibility: Sendable {
    public let backendId: String
    public let backendVersion: String
    public let osVersion: String
    public let architecture: String
    public init(backendId: String, backendVersion: String, osVersion: String, architecture: String) {
        self.backendId = backendId; self.backendVersion = backendVersion; self.osVersion = osVersion; self.architecture = architecture
    }
}
public enum ModelStatus: String, Sendable { case absent, installing, installed, invalid }
public enum ModelStoreError: Error, Equatable { case invalidManifest, invalidSource, integrity, incompatible, inUse, absent, diskSpace }
