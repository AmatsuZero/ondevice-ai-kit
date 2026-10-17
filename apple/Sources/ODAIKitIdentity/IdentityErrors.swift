import Foundation

public enum IdentityErrorCode: String, Codable, CaseIterable, Sendable {
    case backendNotRegistered, backendUnavailable, unsupportedOption
    case authorizationRequired, authorizationExpired, authorizationRevoked
    case candidateNotFound, candidateRevoked, templateNotFound, templateRevoked
    case galleryNotFound, galleryRevisionGone, galleryRevisionConflict, builderClosed
    case embeddingSpaceMismatch, featureFormatUnsupported, reEnrollmentRequired, rebuildRequired
    case indexUnavailable, indexBuilding, thresholdUnverified, invalidInput, multipleFaces
    case invalidChallenge, challengeExpired, sessionTimeout, busy, closed, cancelled
}

/// Context contains only resolved resource metadata, never raw vectors or business IDs.
public struct IdentityError: Error, Equatable, Sendable {
    public let code: IdentityErrorCode
    public let backendId: String?
    public let modelBinding: ModelBinding?
    public let policyId: String?
    public let sessionId: String?
    public init(_ code: IdentityErrorCode, backendId: String? = nil, modelBinding: ModelBinding? = nil,
                policyId: String? = nil, sessionId: String? = nil) {
        self.code = code; self.backendId = backendId; self.modelBinding = modelBinding
        self.policyId = policyId; self.sessionId = sessionId
    }
}

