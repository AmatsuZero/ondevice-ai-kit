import Foundation
import CryptoKit

/// Persisted membership facts are checked against IdentityStore again before use.
/// A decoded candidate reference is never proof of template ownership.
struct GallerySnapshotMember: Sendable, Equatable {
    let ref: TemplateRef
    let candidate: CandidateRef
    let grant: AuthorizationGrant

    func canonicalJSON() -> String {
        identityJSON(["template": ref.canonicalJSON(), "candidate": candidate.canonicalJSON(), "grant": grant.canonicalJSON()])
    }
    static func restore(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["template", "candidate", "grant"])
        return try Self(ref: TemplateRef.decodeJSON(identityString(fields, "template")),
                        candidate: CandidateRef.decodeJSON(identityString(fields, "candidate")),
                        grant: AuthorizationGrant.decodeJSON(identityString(fields, "grant")))
    }
}

/// Immutable metadata for one encrypted IndexStore revision. This codec checks
/// internal consistency, not current authorization or issuer authenticity.
struct GallerySnapshotRecord: Sendable, Equatable {
    let indexStoreId: String
    let identityStoreId: String
    let ref: GalleryRef
    let parent: GalleryRef?
    let members: [GallerySnapshotMember]
    let operationGrant: AuthorizationGrant
    let policy: ThresholdPolicy
    let aggregationPolicyId: String
    let domainEpochs: [String: Int64]
    let contentDigest: String

    init(indexStoreId: String, identityStoreId: String, lineageId: String, parent: GalleryRef?,
         members: [GallerySnapshotMember], operationGrant: AuthorizationGrant,
         policy: ThresholdPolicy, aggregationPolicyId: String) throws {
        for value in [indexStoreId, identityStoreId, lineageId, aggregationPolicyId] { try identityToken(value) }
        guard !members.isEmpty, Set(members.map { $0.ref.id }).count == members.count,
              parent.map({ $0.id == lineageId }) ?? true else { throw IdentityError(.invalidInput) }
        guard operationGrant.scope.contains(.identification) else { throw IdentityError(.authorizationRequired) }
        try policy.validate(embeddingSpace: policy.embeddingSpace, kind: .identification,
                            epoch: operationGrant.authorizationEpoch, gallerySize: Set(members.map { $0.candidate.id }).count)
        var epochs = [operationGrant.revocationDomainId: operationGrant.authorizationEpoch]
        var candidateDomains: [String: String] = [:]
        var grants = [operationGrant.grantId: operationGrant]
        for member in members {
            guard member.candidate.storeId == identityStoreId else { throw IdentityError(.candidateNotFound) }
            guard member.ref.embeddingSpaceId == policy.embeddingSpace else { throw IdentityError(.embeddingSpaceMismatch) }
            guard member.grant.purpose == operationGrant.purpose, member.grant.scope.contains(.identification) else {
                throw IdentityError(.authorizationRequired)
            }
            guard member.ref.authorizationEpoch == member.grant.authorizationEpoch else { throw IdentityError(.authorizationRevoked) }
            if let domain = candidateDomains[member.candidate.id], domain != member.grant.revocationDomainId {
                throw IdentityError(.authorizationRequired)
            }
            if let recorded = grants[member.grant.grantId], recorded != member.grant {
                throw IdentityError(.authorizationRequired)
            }
            if let prior = epochs[member.grant.revocationDomainId], prior != member.grant.authorizationEpoch {
                throw IdentityError(.authorizationRevoked)
            }
            epochs[member.grant.revocationDomainId] = member.grant.authorizationEpoch
            candidateDomains[member.candidate.id] = member.grant.revocationDomainId
            grants[member.grant.grantId] = member.grant
        }
        self.indexStoreId = indexStoreId; self.identityStoreId = identityStoreId; self.parent = parent
        self.members = members.sorted { $0.ref.id < $1.ref.id }; self.operationGrant = operationGrant
        self.policy = policy; self.aggregationPolicyId = aggregationPolicyId; self.domainEpochs = epochs
        let content = Self.contentFields(indexStoreId: indexStoreId, identityStoreId: identityStoreId, lineageId: lineageId,
            parent: parent, members: self.members, operationGrant: operationGrant, policy: policy,
            aggregationPolicyId: aggregationPolicyId, domainEpochs: epochs)
        let body = identityJSON(content)
        guard body.utf8.count <= 65_000 else { throw IdentityError(.invalidInput) }
        let digest = SHA256.hash(data: Data(body.utf8)).map { String(format: "%02x", $0) }.joined()
        self.contentDigest = digest; self.ref = try GalleryRef(id: lineageId, revision: digest)
    }

    func canonicalJSON() -> String {
        var fields = Self.contentFields(indexStoreId: indexStoreId, identityStoreId: identityStoreId, lineageId: ref.id,
            parent: parent, members: members, operationGrant: operationGrant, policy: policy,
            aggregationPolicyId: aggregationPolicyId, domainEpochs: domainEpochs)
        fields["contentDigest"] = contentDigest
        return identityJSON(fields)
    }

    static func restore(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["version", "indexStoreId", "identityStoreId", "lineageId", "parent",
            "members", "operationGrant", "policy", "aggregationPolicyId", "domainEpochs", "contentDigest"])
        guard try identityInteger(fields, "version") == 1,
              let memberJSON = fields["members"] as? [String], let epochFields = fields["domainEpochs"] as? [String: Any] else {
            throw IdentityError(.invalidInput)
        }
        let parent: GalleryRef? = try fields["parent"] is NSNull ? nil : GalleryRef.decodeJSON(identityString(fields, "parent"))
        let record = try Self(indexStoreId: identityString(fields, "indexStoreId"), identityStoreId: identityString(fields, "identityStoreId"),
            lineageId: identityString(fields, "lineageId"), parent: parent, members: memberJSON.map(GallerySnapshotMember.restore),
            operationGrant: AuthorizationGrant.decodeJSON(identityString(fields, "operationGrant")),
            policy: ThresholdPolicy.restoreGalleryJSON(identityString(fields, "policy")),
            aggregationPolicyId: identityString(fields, "aggregationPolicyId"))
        var epochs: [String: Int64] = [:]
        for key in epochFields.keys { epochs[key] = try identityInteger(epochFields, key) }
        guard record.contentDigest == (try identityString(fields, "contentDigest")), record.domainEpochs == epochs,
              record.members.map({ $0.canonicalJSON() }) == memberJSON else { throw IdentityError(.invalidInput) }
        return record
    }

    private static func contentFields(indexStoreId: String, identityStoreId: String, lineageId: String, parent: GalleryRef?,
                                      members: [GallerySnapshotMember], operationGrant: AuthorizationGrant, policy: ThresholdPolicy,
                                      aggregationPolicyId: String, domainEpochs: [String: Int64]) -> [String: Any] {
        ["version": 1, "indexStoreId": indexStoreId, "identityStoreId": identityStoreId, "lineageId": lineageId,
         "parent": parent?.canonicalJSON() as Any? ?? NSNull(), "members": members.map { $0.canonicalJSON() },
         "operationGrant": operationGrant.canonicalJSON(), "policy": policy.galleryJSON(),
         "aggregationPolicyId": aggregationPolicyId, "domainEpochs": domainEpochs]
    }
}

private extension ThresholdPolicy {
    func galleryJSON() -> String {
        identityJSON(["id": id, "revision": revision, "embeddingSpace": embeddingSpace.canonicalJSON(), "scoreMetric": scoreMetric,
            "scoreDirection": scoreDirection.rawValue, "calibrationStatus": calibrationStatus.rawValue, "kind": kind.rawValue,
            "threshold": threshold.map(galleryDoubleBits) as Any? ?? NSNull(), "qualityFloor": qualityFloor.map(galleryDoubleBits) as Any? ?? NSNull(),
            "operatingPoint": operatingPoint as Any? ?? NSNull(), "datasetRevision": datasetRevision as Any? ?? NSNull(),
            "gallerySizeRange": gallerySizeRange.map { ["lower": $0.lowerBound, "upper": $0.upperBound] } as Any? ?? NSNull(),
            "enrollmentRule": enrollmentRule as Any? ?? NSNull(), "effectiveAuthorizationEpoch": effectiveAuthorizationEpoch])
    }

    static func restoreGalleryJSON(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["id", "revision", "embeddingSpace", "scoreMetric", "scoreDirection", "calibrationStatus",
            "kind", "threshold", "qualityFloor", "operatingPoint", "datasetRevision", "gallerySizeRange", "enrollmentRule", "effectiveAuthorizationEpoch"])
        guard try identityString(fields, "calibrationStatus") == CalibrationStatus.verified.rawValue,
              try identityString(fields, "kind") == IdentityPolicyKind.identification.rawValue,
              let direction = ScoreDirection(rawValue: try identityString(fields, "scoreDirection")),
              let range = fields["gallerySizeRange"] as? [String: Any], Set(range.keys) == ["lower", "upper"] else {
            throw IdentityError(.invalidInput)
        }
        let lower = try identityInteger(range, "lower"), upper = try identityInteger(range, "upper")
        guard lower > 0, lower <= upper, upper <= Int32.max else { throw IdentityError(.invalidInput) }
        return try Self(id: identityString(fields, "id"), revision: identityString(fields, "revision"),
            embeddingSpace: EmbeddingSpaceId.decodeJSON(identityString(fields, "embeddingSpace")), scoreMetric: identityString(fields, "scoreMetric"),
            scoreDirection: direction, calibrationStatus: .verified, kind: .identification,
            threshold: galleryDouble(fields, "threshold"), qualityFloor: galleryDouble(fields, "qualityFloor"),
            operatingPoint: identityString(fields, "operatingPoint"), datasetRevision: identityString(fields, "datasetRevision"),
            gallerySizeRange: Int(lower)...Int(upper), enrollmentRule: identityString(fields, "enrollmentRule"),
            effectiveAuthorizationEpoch: identityInteger(fields, "effectiveAuthorizationEpoch"))
    }
}

private func galleryDouble(_ fields: [String: Any], _ key: String) throws -> Double {
    guard let value = fields[key] as? String, value.range(of: "^[0-9a-f]{16}$", options: .regularExpression) != nil,
          let bits = UInt64(value, radix: 16), Double(bitPattern: bits).isFinite else {
        throw IdentityError(.invalidInput)
    }
    return Double(bitPattern: bits)
}

/// The journal's strict DTO wire format is integer-only. IEEE-754 hex strings
/// preserve every bit without platform-dependent decimal rendering.
private func galleryDoubleBits(_ value: Double) -> String {
    String(format: "%016llx", value.bitPattern)
}
