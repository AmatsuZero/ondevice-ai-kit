import Foundation
import ODAIKitCore

/// Images stay task-scoped. Fixture input is internal and never a production image path.
public struct IdentityProbe: Sendable {
    public let revision: String
    public let encodedImage: Data?
    let fixtureID: String?
    let vector: [Float]?

    public init(encodedImage: Data, revision: String) throws {
        try identityToken(revision)
        guard !encodedImage.isEmpty else { throw IdentityError(.invalidInput) }
        self.revision = revision; self.encodedImage = encodedImage; fixtureID = nil; vector = nil
    }
    init(fixtureID: String, vector: [Float]) throws {
        try identityToken(fixtureID)
        guard !vector.isEmpty, vector.allSatisfy({ $0.isFinite }) else { throw IdentityError(.invalidInput) }
        self.fixtureID = fixtureID; self.vector = vector; revision = "fixture-p1"; encodedImage = nil
    }
}

/// Backend extension boundary only; task results never contain these values.
public struct IdentityEmbedding: Sendable, Equatable {
    public let embeddingSpace: EmbeddingSpaceId
    public let values: [Float]
    public init(embeddingSpace: EmbeddingSpaceId, values: [Float]) throws {
        guard values.count == embeddingSpace.dimension, values.allSatisfy({ $0.isFinite }) else { throw IdentityError(.invalidInput) }
        self.embeddingSpace = embeddingSpace; self.values = values
    }
}
public struct IdentityQuality: Sendable, Equatable {
    public let faceCount: Int
    public let score: Double?
    public init(faceCount: Int, score: Double? = nil) throws {
        guard faceCount >= 0, score.map({ $0.isFinite && (0...1).contains($0) }) ?? true else { throw IdentityError(.invalidInput) }
        self.faceCount = faceCount; self.score = score
    }
}

/// Backend primitives do not issue identity decisions; engines enforce grants and policies.
public protocol IdentityBackend: AnyObject, Sendable {
    var backendId: String { get }
    var backendVersion: String { get }
    var modelBinding: ModelBinding { get }
    var embeddingSpace: EmbeddingSpaceId { get }
    func availability() -> CapabilityAvailability
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding
    func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality
    func evaluateQuality(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityQuality
    func close()
}
public enum CalibrationStatus: String, Sendable { case verified, unverified }
public enum IdentityPolicyKind: String, Sendable { case verification, identification }
public struct ThresholdPolicy: Sendable, Equatable {
    public let id: String
    public let revision: String
    public let embeddingSpace: EmbeddingSpaceId
    public let scoreMetric: String
    public let scoreDirection: ScoreDirection
    public let calibrationStatus: CalibrationStatus
    public let kind: IdentityPolicyKind
    public let threshold: Double?
    public let qualityFloor: Double?
    public let operatingPoint: String?
    public let datasetRevision: String?
    public let gallerySizeRange: ClosedRange<Int>?
    public let enrollmentRule: String?
    public let effectiveAuthorizationEpoch: Int64

    public init(id: String, revision: String, embeddingSpace: EmbeddingSpaceId, scoreMetric: String,
                scoreDirection: ScoreDirection, calibrationStatus: CalibrationStatus, kind: IdentityPolicyKind = .verification,
                threshold: Double? = nil, qualityFloor: Double? = nil, operatingPoint: String? = nil, datasetRevision: String? = nil,
                gallerySizeRange: ClosedRange<Int>? = nil, enrollmentRule: String? = nil, effectiveAuthorizationEpoch: Int64 = 0) throws {
        for value in [id, revision, scoreMetric] { try identityToken(value) }
        for value in [operatingPoint, datasetRevision, enrollmentRule].compactMap({ $0 }) { try identityToken(value) }
        guard scoreMetric == embeddingSpace.scoreMetric, scoreDirection == embeddingSpace.scoreDirection,
              embeddingSpace.supportedCapabilities.contains(kind == .verification ? .verification : .identification) else { throw IdentityError(.embeddingSpaceMismatch) }
        guard effectiveAuthorizationEpoch >= 0, threshold.map({ $0.isFinite }) ?? true,
              qualityFloor.map({ $0.isFinite && (0...1).contains($0) }) ?? true,
              gallerySizeRange.map({ $0.lowerBound > 0 && $0.upperBound <= Int32.max }) ?? true else { throw IdentityError(.invalidInput) }
        if calibrationStatus == .verified {
            guard threshold != nil, qualityFloor != nil, operatingPoint != nil, datasetRevision != nil, enrollmentRule != nil,
                  kind == .verification || gallerySizeRange != nil else { throw IdentityError(.thresholdUnverified, policyId: id) }
        }
        self.id = id; self.revision = revision; self.embeddingSpace = embeddingSpace
        self.scoreMetric = scoreMetric; self.scoreDirection = scoreDirection; self.calibrationStatus = calibrationStatus
        self.kind = kind; self.threshold = threshold; self.qualityFloor = qualityFloor; self.operatingPoint = operatingPoint
        self.datasetRevision = datasetRevision; self.gallerySizeRange = gallerySizeRange; self.enrollmentRule = enrollmentRule
        self.effectiveAuthorizationEpoch = effectiveAuthorizationEpoch
    }
    public func validate(embeddingSpace: EmbeddingSpaceId, kind: IdentityPolicyKind = .verification, epoch: Int64 = 0, gallerySize: Int? = nil) throws {
        guard self.embeddingSpace == embeddingSpace else { throw IdentityError(.embeddingSpaceMismatch) }
        try requireVerified()
        guard self.kind == kind, effectiveAuthorizationEpoch == epoch else { throw IdentityError(.thresholdUnverified, policyId: id) }
        if kind == .identification {
            guard let gallerySize, let range = gallerySizeRange, range.contains(gallerySize) else { throw IdentityError(.thresholdUnverified, policyId: id) }
        }
    }
    public func requireVerified() throws {
        guard calibrationStatus == .verified else { throw IdentityError(.thresholdUnverified, policyId: id) }
    }
}

public enum PADMode: String, Sendable { case passive, active }
/// PAD calibration and model binding are independent of identity embedding policies.
public struct PADPolicy: Sendable, Equatable {
    public let id: String
    public let revision: String
    public let backendId: String
    public let backendVersion: String
    public let modelBinding: ModelBinding
    public let calibrationStatus: CalibrationStatus
    public let mode: PADMode
    public let challengePolicyRevision: String?
    public let minimumFrames: Int
    public let maximumDurationNanos: Int64
    public let qualityFloor: Double?
    public let scoreMetric: String?
    public let threshold: Double?
    public let classificationPolicyRevision: String?
    public let datasetRevision: String?
    public let operatingPoint: String?
    public let attackTypes: Set<String>
    public let effectiveAuthorizationEpoch: Int64

    public init(id: String, revision: String, backendId: String, backendVersion: String, modelBinding: ModelBinding,
                calibrationStatus: CalibrationStatus, mode: PADMode = .passive, challengePolicyRevision: String? = nil,
                minimumFrames: Int = 1, maximumDurationNanos: Int64 = 1_000_000_000, qualityFloor: Double? = nil,
                scoreMetric: String? = nil, threshold: Double? = nil, classificationPolicyRevision: String? = nil,
                datasetRevision: String? = nil, operatingPoint: String? = nil, attackTypes: Set<String> = [], effectiveAuthorizationEpoch: Int64 = 0) throws {
        for value in [id, revision, backendId, backendVersion] { try identityToken(value) }
        for value in [challengePolicyRevision, scoreMetric, classificationPolicyRevision, datasetRevision, operatingPoint].compactMap({ $0 }) { try identityToken(value) }
        for attack in attackTypes { try identityToken(attack) }
        guard minimumFrames > 0, minimumFrames <= Int32.max, maximumDurationNanos > 0, effectiveAuthorizationEpoch >= 0,
              qualityFloor.map({ $0.isFinite && (0...1).contains($0) }) ?? true,
              threshold.map({ $0.isFinite }) ?? true,
              (scoreMetric == nil) == (threshold == nil),
              classificationPolicyRevision == nil || scoreMetric == nil else { throw IdentityError(.invalidInput) }
        if calibrationStatus == .verified {
            guard qualityFloor != nil, datasetRevision != nil, operatingPoint != nil, !attackTypes.isEmpty,
                  scoreMetric != nil || classificationPolicyRevision != nil,
                  mode == .passive || challengePolicyRevision != nil else { throw IdentityError(.thresholdUnverified, policyId: id) }
        }
        self.id = id; self.revision = revision; self.backendId = backendId; self.backendVersion = backendVersion
        self.modelBinding = modelBinding; self.calibrationStatus = calibrationStatus; self.mode = mode
        self.challengePolicyRevision = challengePolicyRevision; self.minimumFrames = minimumFrames; self.maximumDurationNanos = maximumDurationNanos
        self.qualityFloor = qualityFloor; self.scoreMetric = scoreMetric; self.threshold = threshold; self.classificationPolicyRevision = classificationPolicyRevision
        self.datasetRevision = datasetRevision; self.operatingPoint = operatingPoint; self.attackTypes = attackTypes; self.effectiveAuthorizationEpoch = effectiveAuthorizationEpoch
    }
    public func requireVerified() throws {
        guard calibrationStatus == .verified else { throw IdentityError(.thresholdUnverified, policyId: id) }
    }
    public func validate(backendId: String, backendVersion: String, modelBinding: ModelBinding, mode: PADMode, epoch: Int64) throws {
        try requireVerified()
        guard self.backendId == backendId, self.backendVersion == backendVersion, self.modelBinding == modelBinding,
              self.mode == mode, effectiveAuthorizationEpoch == epoch else { throw IdentityError(.thresholdUnverified, policyId: id) }
    }
}
