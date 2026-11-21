import Foundation

private func optionalToken(_ fields: [String: Any], _ key: String) throws -> String? {
    if fields[key] is NSNull { return nil }
    let value = try identityString(fields, key)
    try identityToken(value)
    return value
}

public extension GalleryRef {
    func canonicalJSON() -> String { identityJSON(["id": id, "revision": revision]) }
    static func decodeJSON(_ json: String) throws -> Self {
        let f = try identityObject(json, keys: ["id", "revision"])
        return try Self(id: identityString(f, "id"), revision: identityString(f, "revision"))
    }
}
public extension AuthorizationGrant {
    func canonicalJSON() -> String {
        identityJSON(["authorizationEpoch": authorizationEpoch, "evidenceRef": evidenceRef,
                      "expiresAtMillis": expiresAtMillis as Any? ?? NSNull(), "grantId": grantId,
                      "issuedAtMillis": issuedAtMillis, "purpose": purpose, "revocationDomainId": revocationDomainId,
                      "scope": scope.map(\.rawValue).sorted()])
    }
    static func decodeJSON(_ json: String) throws -> Self {
        let f = try identityObject(json, keys: ["authorizationEpoch", "evidenceRef", "expiresAtMillis", "grantId",
                                              "issuedAtMillis", "purpose", "revocationDomainId", "scope"])
        guard let scopes = f["scope"] as? [String] else { throw IdentityError(.invalidInput) }
        let parsed = scopes.compactMap(IdentityScope.init(rawValue:))
        guard parsed.count == scopes.count else { throw IdentityError(.invalidInput) }
        let expiry = try f["expiresAtMillis"] is NSNull ? nil : identityInteger(f, "expiresAtMillis")
        return try Self(grantId: identityString(f, "grantId"), revocationDomainId: identityString(f, "revocationDomainId"),
                        scope: Set(parsed), purpose: identityString(f, "purpose"), issuedAtMillis: identityInteger(f, "issuedAtMillis"),
                        expiresAtMillis: expiry, authorizationEpoch: identityInteger(f, "authorizationEpoch"),
                        evidenceRef: identityString(f, "evidenceRef"))
    }
}
public extension IdentityBackendSelection {
    func canonicalJSON() -> String {
        switch self {
        case .default: return identityJSON(["backendId": NSNull(), "kind": "default"])
        case .explicit(let id): return identityJSON(["backendId": id, "kind": "explicit"])
        }
    }
    static func decodeJSON(_ json: String) throws -> Self {
        let f = try identityObject(json, keys: ["backendId", "kind"])
        switch try identityString(f, "kind") {
        case "default":
            guard f["backendId"] is NSNull else { throw IdentityError(.invalidInput) }
            return .default
        case "explicit":
            let id = try identityString(f, "backendId")
            try identityToken(id)
            return .explicit(id)
        default: throw IdentityError(.invalidInput)
        }
    }
}
public extension IdentityError {
    func canonicalJSON() -> String {
        identityJSON(["backendId": backendId as Any? ?? NSNull(), "code": code.rawValue,
                      "modelBinding": modelBinding?.fields as Any? ?? NSNull(),
                      "policyId": policyId as Any? ?? NSNull(), "sessionId": sessionId as Any? ?? NSNull()])
    }
    static func decodeJSON(_ json: String) throws -> Self {
        let f = try identityObject(json, keys: ["backendId", "code", "modelBinding", "policyId", "sessionId"])
        guard let code = IdentityErrorCode(rawValue: try identityString(f, "code")) else { throw IdentityError(.invalidInput) }
        let binding: ModelBinding?
        if f["modelBinding"] is NSNull { binding = nil }
        else {
            guard let fields = f["modelBinding"] as? [String: Any] else { throw IdentityError(.invalidInput) }
            binding = try ModelBinding.decodeJSON(identityJSON(fields))
        }
        return try Self(code, backendId: optionalToken(f, "backendId"), modelBinding: binding,
                        policyId: optionalToken(f, "policyId"), sessionId: optionalToken(f, "sessionId"))
    }
}
public extension LivenessProof {
    func canonicalJSON() -> String {
        identityJSON(["authorizationEpoch": authorizationEpoch, "captureDigest": captureDigest,
                      "expiresAtMillis": expiresAtMillis, "id": id, "issuedAtMillis": issuedAtMillis,
                      "issuerId": issuerId, "modelBinding": modelBinding.fields, "nonce": nonce,
                      "policy": ["id": policy.id, "revision": policy.revision], "purpose": purpose, "sessionId": sessionId])
    }
    static func decodeJSON(_ json: String) throws -> Self {
        let f = try identityObject(json, keys: ["authorizationEpoch", "captureDigest", "expiresAtMillis", "id", "issuedAtMillis",
                                              "issuerId", "modelBinding", "nonce", "policy", "purpose", "sessionId"])
        guard let binding = f["modelBinding"] as? [String: Any], let policy = f["policy"] as? [String: Any] else { throw IdentityError(.invalidInput) }
        return try Self(id: identityString(f, "id"), issuerId: identityString(f, "issuerId"), sessionId: identityString(f, "sessionId"),
                        nonce: identityString(f, "nonce"), purpose: identityString(f, "purpose"), captureDigest: identityString(f, "captureDigest"),
                        policy: PADPolicyRef.decodeJSON(identityJSON(policy)), modelBinding: ModelBinding.decodeJSON(identityJSON(binding)),
                        authorizationEpoch: identityInteger(f, "authorizationEpoch"), issuedAtMillis: identityInteger(f, "issuedAtMillis"),
                        expiresAtMillis: identityInteger(f, "expiresAtMillis"))
    }
}
