import Foundation
import ODAIKitCore

func identityToken(_ value: String) throws {
    guard !value.isEmpty, value.utf8.count <= 256,
          value.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) ||
              (97...122).contains($0) || [45, 46, 58, 95].contains($0) }) else {
        throw IdentityError(.invalidInput)
    }
}
func identityJSON(_ fields: [String: Any]) -> String {
    String(data: try! JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys, .withoutEscapingSlashes]), encoding: .utf8)!
}
func identityObject(_ json: String, keys: Set<String>) throws -> [String: Any] {
    guard json.utf8.count <= 65_536, let data = json.data(using: .utf8),
          let fields = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          Set(fields.keys) == keys else { throw IdentityError(.invalidInput) }
    try validateIdentityWire(json)
    return fields
}
func identityString(_ fields: [String: Any], _ key: String) throws -> String {
    guard let value = fields[key] as? String else { throw IdentityError(.invalidInput) }
    return value
}
func identityInteger(_ fields: [String: Any], _ key: String) throws -> Int64 {
    guard let number = fields[key] as? NSNumber,
          ["i", "q", "s", "l", "I", "Q"].contains(String(cString: number.objCType)),
          let value = Int64(number.stringValue) else { throw IdentityError(.invalidInput) }
    return value
}

/// Decoding a reference does not establish issuer authenticity; stores check their records.
public struct CandidateRef: Equatable, Sendable {
    public let id: String
    public let storeId: String
    init(id: String, storeId: String) throws {
        try identityToken(id); try identityToken(storeId)
        self.id = id; self.storeId = storeId
    }
    func validate(against issued: Self) throws {
        guard self == issued else { throw IdentityError(.candidateNotFound) }
    }
    public func canonicalJSON() -> String { identityJSON(["id": id, "storeId": storeId]) }
    public static func decodeJSON(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["id", "storeId"])
        return try Self(id: identityString(fields, "id"), storeId: identityString(fields, "storeId"))
    }
}
public struct GalleryRef: Equatable, Sendable {
    public let id: String
    public let revision: String
    init(id: String, revision: String) throws {
        try identityToken(id); try identityToken(revision)
        self.id = id; self.revision = revision
    }
}
public enum IdentityScope: String, Sendable, CaseIterable {
    case enrollment, verification, identification, liveness
}
public protocol IdentityWallClock: Sendable { var nowMillis: Int64 { get } }
public protocol IdentityMonotonicClock: Sendable { var nowNanos: Int64 { get } }
public struct SystemIdentityWallClock: IdentityWallClock {
    public init() {}
    public var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
public struct SystemIdentityMonotonicClock: IdentityMonotonicClock {
    public init() {}
    public var nowNanos: Int64 { Int64(clamping: DispatchTime.now().uptimeNanoseconds) }
}

/// Host declaration only. Admission also needs a trusted provider and persisted epoch.
public struct AuthorizationGrant: Equatable, Sendable {
    public let grantId: String
    public let revocationDomainId: String
    public let scope: Set<IdentityScope>
    public let purpose: String
    public let issuedAtMillis: Int64
    public let expiresAtMillis: Int64?
    public let authorizationEpoch: Int64
    public let evidenceRef: String
    public init(grantId: String, revocationDomainId: String, scope: Set<IdentityScope>, purpose: String,
                issuedAtMillis: Int64, expiresAtMillis: Int64?, authorizationEpoch: Int64, evidenceRef: String) throws {
        for token in [grantId, revocationDomainId, purpose, evidenceRef] { try identityToken(token) }
        guard !scope.isEmpty, authorizationEpoch >= 0, issuedAtMillis >= 0,
              expiresAtMillis.map({ $0 > issuedAtMillis }) ?? true else { throw IdentityError(.invalidInput) }
        self.grantId = grantId; self.revocationDomainId = revocationDomainId; self.scope = scope
        self.purpose = purpose; self.issuedAtMillis = issuedAtMillis; self.expiresAtMillis = expiresAtMillis
        self.authorizationEpoch = authorizationEpoch; self.evidenceRef = evidenceRef
    }
    func validate(scope required: IdentityScope, purpose: String, epoch: Int64, clock: any IdentityWallClock) throws {
        guard authorizationEpoch == epoch else { throw IdentityError(.authorizationRevoked) }
        let now = clock.nowMillis
        guard now >= issuedAtMillis, self.purpose == purpose, scope.contains(required) else {
            throw IdentityError(.authorizationRequired)
        }
        if let expiry = expiresAtMillis, now >= expiry { throw IdentityError(.authorizationExpired) }
    }
}

public enum IdentityBackendSelection: Equatable, Sendable {
    case `default`
    case explicit(String)
}
public struct IdentityBackendRegistration<Backend: Sendable>: Sendable {
    public let backendId: String
    public let backend: Backend
    public let available: Bool
    public init(backendId: String, backend: Backend, available: Bool = true) {
        self.backendId = backendId; self.backend = backend; self.available = available
    }
}
public struct IdentityBackendRegistry<Backend: Sendable>: Sendable {
    private let defaultBackendId: String
    private let entries: [String: IdentityBackendRegistration<Backend>]
    public init(defaultBackendId: String, registrations: [IdentityBackendRegistration<Backend>]) throws {
        try identityToken(defaultBackendId)
        var entries: [String: IdentityBackendRegistration<Backend>] = [:]
        for registration in registrations {
            try identityToken(registration.backendId)
            guard entries[registration.backendId] == nil else { throw IdentityError(.invalidInput) }
            entries[registration.backendId] = registration
        }
        guard entries[defaultBackendId] != nil else { throw IdentityError(.backendNotRegistered) }
        self.defaultBackendId = defaultBackendId; self.entries = entries
    }
    public func select(_ selection: IdentityBackendSelection) throws -> Backend {
        let id: String
        switch selection { case .default: id = defaultBackendId; case .explicit(let value): id = value }
        guard let entry = entries[id] else { throw IdentityError(.backendNotRegistered) }
        guard entry.available else { throw IdentityError(.backendUnavailable, backendId: id) }
        return entry.backend
    }
}

/// Engines additionally enforce domain outcome and calibrated-policy requirements.
public struct IdentityScoreFields: Equatable, Sendable {
    public let score: Double?
    public let metric: String?
    public let threshold: Double?
    public init(score: Double? = nil, metric: String? = nil, threshold: Double? = nil) throws {
        if score != nil || metric != nil || threshold != nil {
            guard let score, score.isFinite, let threshold, threshold.isFinite, let metric else {
                throw IdentityError(.invalidInput)
            }
            try identityToken(metric)
        }
        self.score = score; self.metric = metric; self.threshold = threshold
    }
    public func canonicalJSON() -> String {
        identityJSON(["metric": metric as Any? ?? NSNull(), "score": score as Any? ?? NSNull(),
                      "threshold": threshold as Any? ?? NSNull()])
    }
}

public enum ModelBindingKind: String, Codable, Sendable { case model, system }
public struct ModelBinding: Equatable, Sendable {
    public let kind: ModelBindingKind
    public let id: String
    public let revision: String
    public init(kind: ModelBindingKind, id: String, revision: String) throws {
        try identityToken(id); try identityToken(revision)
        self.kind = kind; self.id = id; self.revision = revision
    }
    var fields: [String: Any] { ["id": id, "kind": kind.rawValue, "revision": revision] }
    public func canonicalJSON() -> String { identityJSON(fields) }
    public static func decodeJSON(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["id", "kind", "revision"])
        guard let kind = ModelBindingKind(rawValue: try identityString(fields, "kind")) else { throw IdentityError(.invalidInput) }
        return try Self(kind: kind, id: identityString(fields, "id"), revision: identityString(fields, "revision"))
    }
}
public enum IdentityCapability: String, Codable, CaseIterable, Sendable { case verification, identification }
public enum FeatureDType: String, Codable, Sendable { case float32, float16, int8 }
public enum Endianness: String, Codable, Sendable { case little, big }
public enum ScoreDirection: String, Codable, Sendable { case higherIsBetter, lowerIsBetter }
public struct EmbeddingSpaceId: Equatable, Sendable {
    public let supportedCapabilities: Set<IdentityCapability>
    public let backendId: String
    public let backendVersion: String
    public let modelBinding: ModelBinding
    public let featureFormatVersion: String
    public let dimension: Int
    public let dtype: FeatureDType
    public let endianness: Endianness
    public let normalization: String
    public let preprocessRevision: String
    public let scoreMetric: String
    public let scoreDirection: ScoreDirection
    public init(supportedCapabilities: Set<IdentityCapability>, backendId: String, backendVersion: String,
                modelBinding: ModelBinding, featureFormatVersion: String, dimension: Int, dtype: FeatureDType,
                endianness: Endianness, normalization: String, preprocessRevision: String,
                scoreMetric: String, scoreDirection: ScoreDirection) throws {
        guard !supportedCapabilities.isEmpty, dimension > 0, dimension <= Int32.max else { throw IdentityError(.invalidInput) }
        guard featureFormatVersion == "1" else { throw IdentityError(.featureFormatUnsupported) }
        for token in [backendId, backendVersion, featureFormatVersion, normalization, preprocessRevision, scoreMetric] { try identityToken(token) }
        self.supportedCapabilities = supportedCapabilities; self.backendId = backendId; self.backendVersion = backendVersion
        self.modelBinding = modelBinding; self.featureFormatVersion = featureFormatVersion; self.dimension = dimension
        self.dtype = dtype; self.endianness = endianness; self.normalization = normalization
        self.preprocessRevision = preprocessRevision; self.scoreMetric = scoreMetric; self.scoreDirection = scoreDirection
    }
    var fields: [String: Any] {
        ["backendId": backendId, "backendVersion": backendVersion, "dimension": dimension,
                      "dtype": dtype.rawValue, "endianness": endianness.rawValue,
                      "featureFormatVersion": featureFormatVersion, "modelBinding": modelBinding.fields,
                      "normalization": normalization, "preprocessRevision": preprocessRevision,
                      "scoreDirection": scoreDirection.rawValue, "scoreMetric": scoreMetric,
                      "supportedCapabilities": supportedCapabilities.map(\.rawValue).sorted()]
    }
    public func canonicalJSON() -> String { identityJSON(fields) }
    public static func decodeJSON(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["backendId", "backendVersion", "dimension", "dtype", "endianness", "featureFormatVersion", "modelBinding", "normalization", "preprocessRevision", "scoreDirection", "scoreMetric", "supportedCapabilities"])
        guard let capabilities = fields["supportedCapabilities"] as? [String],
              let dtype = FeatureDType(rawValue: try identityString(fields, "dtype")),
              let endianness = Endianness(rawValue: try identityString(fields, "endianness")),
              let direction = ScoreDirection(rawValue: try identityString(fields, "scoreDirection")),
              let binding = fields["modelBinding"] as? [String: Any] else { throw IdentityError(.invalidInput) }
        let parsedCapabilities = capabilities.compactMap(IdentityCapability.init(rawValue:))
        guard parsedCapabilities.count == capabilities.count else { throw IdentityError(.invalidInput) }
        return try Self(supportedCapabilities: Set(parsedCapabilities),
                        backendId: identityString(fields, "backendId"), backendVersion: identityString(fields, "backendVersion"),
                        modelBinding: try ModelBinding.decodeJSON(identityJSON(binding)), featureFormatVersion: identityString(fields, "featureFormatVersion"),
                        dimension: Int(identityInteger(fields, "dimension")), dtype: dtype, endianness: endianness, normalization: identityString(fields, "normalization"),
                        preprocessRevision: identityString(fields, "preprocessRevision"), scoreMetric: identityString(fields, "scoreMetric"), scoreDirection: direction)
    }
}
public struct TemplateRef: Equatable, Sendable {
    public let id: String
    public let embeddingSpaceId: EmbeddingSpaceId
    public let authorizationEpoch: Int64
    public let revision: String
    init(id: String, embeddingSpaceId: EmbeddingSpaceId, authorizationEpoch: Int64, revision: String) throws {
        try identityToken(id); try identityToken(revision); guard authorizationEpoch >= 0 else { throw IdentityError(.invalidInput) }
        self.id = id; self.embeddingSpaceId = embeddingSpaceId; self.authorizationEpoch = authorizationEpoch; self.revision = revision
    }
    func validate(against issued: Self) throws {
        guard id == issued.id, revision == issued.revision else { throw IdentityError(.templateNotFound) }
        guard embeddingSpaceId == issued.embeddingSpaceId else { throw IdentityError(.embeddingSpaceMismatch) }
        guard authorizationEpoch == issued.authorizationEpoch else { throw IdentityError(.authorizationRevoked) }
    }
    public func canonicalJSON() -> String { identityJSON(["authorizationEpoch": authorizationEpoch, "embeddingSpaceId": embeddingSpaceId.fields, "id": id, "revision": revision]) }
    public static func decodeJSON(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["authorizationEpoch", "embeddingSpaceId", "id", "revision"])
        let epoch = try identityInteger(fields, "authorizationEpoch")
        guard let space = fields["embeddingSpaceId"] as? [String: Any] else { throw IdentityError(.invalidInput) }
        return try Self(id: identityString(fields, "id"), embeddingSpaceId: try EmbeddingSpaceId.decodeJSON(identityJSON(space)), authorizationEpoch: epoch, revision: identityString(fields, "revision"))
    }
}
public struct IdentityDeadline: Sendable {
    private let end: Int64
    public init(clock: any IdentityMonotonicClock, durationNanos: Int64) throws {
        let now = clock.nowNanos
        guard durationNanos > 0, now <= Int64.max - durationNanos else { throw IdentityError(.invalidInput) }
        end = now + durationNanos
    }
    public func isExpired(clock: any IdentityMonotonicClock) -> Bool { clock.nowNanos >= end }
}
public struct ThresholdPolicyRef: Equatable, Sendable {
    public let id: String; public let revision: String
    public init(id: String, revision: String) throws { try identityToken(id); try identityToken(revision); self.id=id; self.revision=revision }
    public func canonicalJSON() -> String { identityJSON(["id": id, "revision": revision]) }
    public static func decodeJSON(_ json: String) throws -> Self { let f=try identityObject(json, keys:["id","revision"]); return try Self(id:identityString(f,"id"),revision:identityString(f,"revision")) }
}
public struct PADPolicyRef: Equatable, Sendable {
    public let id: String; public let revision: String
    public init(id: String, revision: String) throws { try identityToken(id); try identityToken(revision); self.id=id; self.revision=revision }
    public func canonicalJSON() -> String { identityJSON(["id": id, "revision": revision]) }
    public static func decodeJSON(_ json: String) throws -> Self { let f=try identityObject(json, keys:["id","revision"]); return try Self(id:identityString(f,"id"),revision:identityString(f,"revision")) }
}
public struct OwnershipAuthorization: Equatable, Sendable {
    public let storeId: String; public let evidenceRef: String
    public init(storeId: String, evidenceRef: String) throws { try identityToken(storeId); try identityToken(evidenceRef); self.storeId=storeId; self.evidenceRef=evidenceRef }
}
public struct LivenessProof: Equatable, Sendable {
    public let id: String; public let issuerId: String; public let sessionId: String; public let nonce: String
    public let purpose: String; public let captureDigest: String; public let policy: PADPolicyRef; public let modelBinding: ModelBinding
    public let authorizationEpoch: Int64; public let issuedAtMillis: Int64; public let expiresAtMillis: Int64
    init(id: String, issuerId: String, sessionId: String, nonce: String, purpose: String, captureDigest: String,
                policy: PADPolicyRef, modelBinding: ModelBinding, authorizationEpoch: Int64, issuedAtMillis: Int64, expiresAtMillis: Int64) throws {
        for value in [id,issuerId,sessionId,nonce,purpose] { try identityToken(value) }
        guard captureDigest.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil,
              authorizationEpoch >= 0, issuedAtMillis >= 0, expiresAtMillis > issuedAtMillis else { throw IdentityError(.invalidInput) }
        self.id=id; self.issuerId=issuerId; self.sessionId=sessionId; self.nonce=nonce; self.purpose=purpose; self.captureDigest=captureDigest
        self.policy=policy; self.modelBinding=modelBinding; self.authorizationEpoch=authorizationEpoch; self.issuedAtMillis=issuedAtMillis; self.expiresAtMillis=expiresAtMillis
    }
}

