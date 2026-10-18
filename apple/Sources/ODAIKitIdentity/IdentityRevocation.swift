import Foundation

/// A stable reference to a committed logical revocation, not proof of physical purge.
/// Decoding does not establish issuer authenticity; the store resolves its own records.
public struct RevocationReceipt: Equatable, Sendable {
    public let id: String
    public let storeId: String
    init(id: String, storeId: String) throws {
        try identityToken(id); try identityToken(storeId)
        self.id = id; self.storeId = storeId
    }
    public func canonicalJSON() -> String { identityJSON(["id": id, "storeId": storeId]) }
    public static func decodeJSON(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["id", "storeId"])
        return try Self(id: identityString(fields, "id"), storeId: identityString(fields, "storeId"))
    }
}

/// Retains grant aliases and the exact candidate target set after logical deletion.
struct IdentityRevocation: Sendable {
    let domainId: String
    let epoch: Int64
    let grantIds: Set<String>
    let candidateIds: Set<String>
    let templateIds: Set<String>

    var fields: [String: Any] {
        ["domainId": domainId, "epoch": epoch, "grantIds": grantIds.sorted(), "candidateIds": candidateIds.sorted(), "templateIds": templateIds.sorted()]
    }

    static func restore(_ fields: [String: Any], includeTemplateIds: Bool) throws -> Self {
        let required: Set<String> = includeTemplateIds ? ["domainId", "epoch", "grantIds", "candidateIds", "templateIds"] : ["domainId", "epoch", "grantIds", "candidateIds"]
        guard Set(fields.keys) == required,
              let grants = fields["grantIds"] as? [String], let candidates = fields["candidateIds"] as? [String],
              let templates = includeTemplateIds ? (fields["templateIds"] as? [String]) : [],
              !grants.isEmpty, Set(grants).count == grants.count, Set(candidates).count == candidates.count,
              Set(templates).count == templates.count, grants == Set(grants).sorted(), candidates == Set(candidates).sorted(), templates == Set(templates).sorted() else {
            throw IdentityError(.invalidInput)
        }
        let domain = try identityString(fields, "domainId"); try identityToken(domain)
        for id in grants + candidates + templates { try identityToken(id) }
        let epoch = try identityInteger(fields, "epoch")
        guard epoch > 0 else { throw IdentityError(.invalidInput) }
        return Self(domainId: domain, epoch: epoch, grantIds: Set(grants), candidateIds: Set(candidates), templateIds: Set(templates))
    }

    static func validate(_ records: [String: Self], authorization: IdentityAuthorizationState,
                         candidates: [String: String]) throws {
        var coveredGrants = Set<String>(); var coveredCandidates = Set<String>()
        var domainEpochs: [String: Set<Int64>] = [:]
        for (id, record) in records {
            try identityToken(id)
            guard record.epoch <= authorization.highestEpoch(domain: record.domainId),
                  !record.grantIds.isEmpty,
                  coveredGrants.isDisjoint(with: record.grantIds),
                  coveredCandidates.isDisjoint(with: record.candidateIds),
                  !(domainEpochs[record.domainId] ?? []).contains(record.epoch) else { throw IdentityError(.invalidInput) }
            for grantId in record.grantIds {
                guard let grant = authorization.recordedGrant(grantId), grant.revocationDomainId == record.domainId,
                      grant.authorizationEpoch < record.epoch else { throw IdentityError(.invalidInput) }
            }
            let expected = Set(candidates.filter { record.grantIds.contains($0.value) }.keys)
            guard record.candidateIds == expected else { throw IdentityError(.invalidInput) }
            coveredGrants.formUnion(record.grantIds); coveredCandidates.formUnion(record.candidateIds)
            domainEpochs[record.domainId, default: []].insert(record.epoch)
        }
        for record in records.values {
            let required = authorization.recordedGrantIDs(domain: record.domainId, before: record.epoch)
            let covered = records.values.filter { $0.domainId == record.domainId && $0.epoch <= record.epoch }
                .reduce(into: Set<String>()) { $0.formUnion($1.grantIds) }
            guard required == covered else { throw IdentityError(.invalidInput) }
        }
    }
}
