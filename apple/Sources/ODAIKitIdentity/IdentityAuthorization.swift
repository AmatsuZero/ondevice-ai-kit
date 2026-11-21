import Foundation

/// Trusted host boundary. The provider authenticates the declaration and independently
/// reports the current domain epoch; request fields are never authoritative by themselves.
public protocol IdentityAuthorizationProvider: Sendable {
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws
}

/// Immutable, journal-ready authorization metadata. It is deliberately separate from
/// IdentityStore so failed admission can never mutate the published value.
struct IdentityAuthorizationState: Sendable, Equatable {
    private static let version = 1
    let purpose: String
    private let epochs: [String: Int64]
    private let grants: [String: AuthorizationGrant]

    init(purpose: String) throws {
        try identityToken(purpose)
        self.purpose = purpose
        self.epochs = [:]
        self.grants = [:]
    }

    private init(purpose: String, epochs: [String: Int64], grants: [String: AuthorizationGrant]) throws {
        try identityToken(purpose)
        guard epochs.values.allSatisfy({ $0 >= 0 }), grants.keys.allSatisfy({ !$0.isEmpty }) else {
            throw IdentityError(.invalidInput)
        }
        for (id, grant) in grants {
            guard id == grant.grantId, grant.purpose == purpose,
                  let epoch = epochs[grant.revocationDomainId], grant.authorizationEpoch <= epoch else {
                throw IdentityError(.invalidInput)
            }
        }
        self.purpose = purpose; self.epochs = epochs; self.grants = grants
    }

    func highestEpoch(domain: String) -> Int64 { epochs[domain] ?? 0 }

    func recordedGrant(_ id: String) -> AuthorizationGrant? { grants[id] }

    func recordedGrantIDs(domain: String, before epoch: Int64) -> Set<String> {
        Set(grants.values.filter { $0.revocationDomainId == domain && $0.authorizationEpoch < epoch }.map(\.grantId))
    }

    func admitting(_ grant: AuthorizationGrant, scope: IdentityScope,
                   provider: any IdentityAuthorizationProvider, nowMillis: Int64) throws -> Self {
        guard grant.purpose == purpose else { throw IdentityError(.authorizationRequired) }
        let persisted = highestEpoch(domain: grant.revocationDomainId)
        if let existing = grants[grant.grantId], existing != grant {
            throw IdentityError(.authorizationRequired)
        }
        let authoritative = try provider.authoritativeEpoch(grant: grant, minimumEpoch: persisted, nowMillis: nowMillis)
        guard authoritative >= 0, authoritative == grant.authorizationEpoch,
              authoritative >= persisted else { throw IdentityError(.authorizationRevoked) }
        try grant.validate(scope: scope, purpose: purpose, epoch: authoritative, clock: FixedIdentityWallClock(nowMillis: nowMillis))
        if grants[grant.grantId] != nil { return self }
        var next = grants; next[grant.grantId] = grant
        var nextEpochs = epochs; nextEpochs[grant.revocationDomainId] = max(persisted, grant.authorizationEpoch)
        return try Self(purpose: purpose, epochs: nextEpochs, grants: next)
    }

    func revoking(grantId: String) throws -> Self {
        guard let grant = grants[grantId] else { throw IdentityError(.authorizationRequired) }
        let current = highestEpoch(domain: grant.revocationDomainId)
        guard grant.authorizationEpoch == current else { return self }
        guard current < Int64.max else { throw IdentityError(.invalidInput) }
        var next = epochs; next[grant.revocationDomainId] = current + 1
        return try Self(purpose: purpose, epochs: next, grants: grants)
    }

    static func requireOwnership(_ authorization: OwnershipAuthorization, storeId: String,
                                 provider: any IdentityAuthorizationProvider, nowMillis: Int64) throws {
        guard authorization.storeId == storeId else { throw IdentityError(.authorizationRequired) }
        try provider.validateOwnership(authorization, nowMillis: nowMillis)
    }

    func canonicalJSON() throws -> String {
        let epochFields: [String: Any] = epochs.reduce(into: [:]) { $0[$1.key] = $1.value }
        let grantFields: [String: Any] = grants.reduce(into: [:]) { $0[$1.key] = $1.value.canonicalJSON() }
        let json = identityJSON(["epochs": epochFields, "grants": grantFields, "purpose": purpose, "version": Self.version])
        guard json.utf8.count <= 65_536 else { throw IdentityError(.invalidInput) }
        return json
    }

    static func restore(_ json: String, purpose expectedPurpose: String) throws -> Self {
        try identityToken(expectedPurpose)
        let fields = try identityObject(json, keys: ["epochs", "grants", "purpose", "version"])
        guard try identityInteger(fields, "version") == Self.version,
              try identityString(fields, "purpose") == expectedPurpose,
              let epochFields = fields["epochs"] as? [String: Any],
              let grantFields = fields["grants"] as? [String: Any] else { throw IdentityError(.invalidInput) }
        var epochs: [String: Int64] = [:]
        for domain in epochFields.keys {
            try identityToken(domain)
            let epoch = try identityInteger(epochFields, domain)
            guard epoch >= 0 else {
                throw IdentityError(.invalidInput)
            }
            epochs[domain] = epoch
        }
        var grants: [String: AuthorizationGrant] = [:]
        for (id, value) in grantFields {
            try identityToken(id)
            guard let grantJSON = value as? String else { throw IdentityError(.invalidInput) }
            let grant = try AuthorizationGrant.decodeJSON(grantJSON)
            guard grant.grantId == id, grant.purpose == expectedPurpose,
                  let epoch = epochs[grant.revocationDomainId], grant.authorizationEpoch <= epoch else {
                throw IdentityError(.invalidInput)
            }
            grants[id] = grant
        }
        return try Self(purpose: expectedPurpose, epochs: epochs, grants: grants)
    }
}

private struct FixedIdentityWallClock: IdentityWallClock {
    let nowMillis: Int64
}
