import Foundation

/// Explicit host quality rule for enrollment; this is not a calibrated identity
/// decision threshold and has no SDK supplied numeric default.
public struct EnrollmentOptions: Sendable, Equatable {
    public let qualityFloor: Double
    public let qualityPolicyId: String
    public let revision: String
    public init(qualityFloor: Double, qualityPolicyId: String, revision: String) throws {
        guard qualityFloor.isFinite, (0...1).contains(qualityFloor) else { throw IdentityError(.invalidInput) }
        try identityToken(qualityPolicyId); try identityToken(revision)
        self.qualityFloor = qualityFloor; self.qualityPolicyId = qualityPolicyId; self.revision = revision
    }
}

public enum EnrollmentOutcome: String, Sendable { case enrolled, insufficientQuality }
public struct EnrollmentResult: Sendable, Equatable {
    public let outcome: EnrollmentOutcome
    public let templateRef: TemplateRef?
    public let embeddingSpace: EmbeddingSpaceId
    public let quality: IdentityQuality
    public let backendId: String
    public let backendVersion: String
    public let modelBinding: ModelBinding
    public let inputRevision: String
    public let optionsRevision: String
    public let qualityPolicyId: String
    public let sessionId: String
    public let runId: String
    public let preprocessingRevision: String
    public let durationNanos: Int64
}

struct IdentityEnrollmentBinding: Sendable {
    let space: EmbeddingSpaceId
    init(_ backend: any IdentityBackend) {
        space = backend.embeddingSpace
    }
    func validate(_ backend: any IdentityBackend) throws {
        guard backend.embeddingSpace == space, backend.backendId == space.backendId,
              backend.backendVersion == space.backendVersion, backend.modelBinding == space.modelBinding else { throw IdentityError(.embeddingSpaceMismatch) }
    }
    func prepare(_ probe: IdentityProbe, options: EnrollmentOptions, backend: any IdentityBackend,
                 signal: IdentityStopSignal) throws -> IdentityPreparedEnrollment {
        try signal.check()
        try validate(backend)
        guard backend.availability().status == .available else { throw IdentityError(.backendUnavailable) }
        let quality = try backend.evaluateQuality(probe, stopSignal: signal)
        try validate(backend); try signal.check()
        guard quality.faceCount <= 1 else { throw IdentityError(.multipleFaces) }
        guard quality.faceCount == 1, let score = quality.score, score >= options.qualityFloor else {
            return IdentityPreparedEnrollment(quality: quality, embedding: nil)
        }
        let embedding = try backend.embed(probe, stopSignal: signal)
        try validate(backend); try signal.check()
        guard embedding.embeddingSpace == space else { throw IdentityError(.embeddingSpaceMismatch) }
        return IdentityPreparedEnrollment(quality: quality, embedding: embedding)
    }
}

struct IdentityPreparedEnrollment: Sendable {
    let quality: IdentityQuality
    let embedding: IdentityEmbedding?
}

struct IdentityTemplateRecord: Sendable {
    let ref: TemplateRef
    let candidateId: String
    let grantId: String
    var fields: [String: Any] { ["ref": ref.canonicalJSON(), "candidateId": candidateId, "grantId": grantId] }
    static func restore(_ fields: [String: Any]) throws -> Self {
        guard Set(fields.keys) == ["ref", "candidateId", "grantId"] else { throw IdentityError(.invalidInput) }
        let record = try Self(ref: TemplateRef.decodeJSON(identityString(fields, "ref")), candidateId: identityString(fields, "candidateId"), grantId: identityString(fields, "grantId"))
        try identityToken(record.candidateId); try identityToken(record.grantId); return record
    }
}
