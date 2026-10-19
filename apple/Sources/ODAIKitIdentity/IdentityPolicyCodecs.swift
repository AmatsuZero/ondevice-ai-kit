import Foundation

// Version 1 encodes finite Double values as 16 lowercase IEEE-754 hex digits.
// The existing integer-only reference/authorization wire rules remain intact.
// A decoded calibration declaration is not evidence of calibration provenance.
public extension ThresholdPolicy {
    func canonicalJSON() -> String {
        identityJSON([
            "version": 1, "id": id, "revision": revision, "embeddingSpace": embeddingSpace.fields,
            "scoreMetric": scoreMetric, "scoreDirection": scoreDirection.rawValue,
            "calibrationStatus": calibrationStatus.rawValue, "kind": kind.rawValue,
            "threshold": policyBits(threshold) as Any? ?? NSNull(),
            "qualityFloor": policyBits(qualityFloor) as Any? ?? NSNull(),
            "operatingPoint": operatingPoint as Any? ?? NSNull(), "datasetRevision": datasetRevision as Any? ?? NSNull(),
            "gallerySizeRange": gallerySizeRange.map { ["lower": $0.lowerBound, "upper": $0.upperBound] } as Any? ?? NSNull(),
            "enrollmentRule": enrollmentRule as Any? ?? NSNull(), "effectiveAuthorizationEpoch": effectiveAuthorizationEpoch
        ])
    }
    static func decodeJSON(_ json: String) throws -> Self {
        let f = try identityObject(json, keys: ["version", "id", "revision", "embeddingSpace", "scoreMetric", "scoreDirection",
            "calibrationStatus", "kind", "threshold", "qualityFloor", "operatingPoint", "datasetRevision", "gallerySizeRange",
            "enrollmentRule", "effectiveAuthorizationEpoch"])
        try policyVersion(f)
        guard let space = f["embeddingSpace"] as? [String: Any],
              let direction = ScoreDirection(rawValue: try identityString(f, "scoreDirection")),
              let status = CalibrationStatus(rawValue: try identityString(f, "calibrationStatus")),
              let kind = IdentityPolicyKind(rawValue: try identityString(f, "kind")) else { throw IdentityError(.invalidInput) }
        let range: ClosedRange<Int>?
        if f["gallerySizeRange"] is NSNull { range = nil }
        else {
            guard let r = f["gallerySizeRange"] as? [String: Any], Set(r.keys) == ["lower", "upper"] else { throw IdentityError(.invalidInput) }
            let lower = try policyPositiveInt(r, "lower"); let upper = try policyPositiveInt(r, "upper")
            guard lower <= upper else { throw IdentityError(.invalidInput) }
            range = lower...upper
        }
        return try Self(id: identityString(f, "id"), revision: identityString(f, "revision"),
            embeddingSpace: EmbeddingSpaceId.decodeJSON(identityJSON(space)), scoreMetric: identityString(f, "scoreMetric"),
            scoreDirection: direction, calibrationStatus: status, kind: kind,
            threshold: policyNumber(f, "threshold"), qualityFloor: policyNumber(f, "qualityFloor"),
            operatingPoint: policyOptionalString(f, "operatingPoint"), datasetRevision: policyOptionalString(f, "datasetRevision"),
            gallerySizeRange: range, enrollmentRule: policyOptionalString(f, "enrollmentRule"),
            effectiveAuthorizationEpoch: identityInteger(f, "effectiveAuthorizationEpoch"))
    }
}

public extension PADPolicy {
    func canonicalJSON() -> String {
        identityJSON([
            "version": 1, "id": id, "revision": revision, "backendId": backendId, "backendVersion": backendVersion,
            "modelBinding": modelBinding.fields, "calibrationStatus": calibrationStatus.rawValue, "mode": mode.rawValue,
            "challengePolicyRevision": challengePolicyRevision as Any? ?? NSNull(), "minimumFrames": minimumFrames,
            "maximumDurationNanos": maximumDurationNanos, "qualityFloor": policyBits(qualityFloor) as Any? ?? NSNull(),
            "scoreMetric": scoreMetric as Any? ?? NSNull(), "threshold": policyBits(threshold) as Any? ?? NSNull(),
            "classificationPolicyRevision": classificationPolicyRevision as Any? ?? NSNull(),
            "datasetRevision": datasetRevision as Any? ?? NSNull(), "operatingPoint": operatingPoint as Any? ?? NSNull(),
            "attackTypes": attackTypes.sorted(), "effectiveAuthorizationEpoch": effectiveAuthorizationEpoch
        ])
    }
    static func decodeJSON(_ json: String) throws -> Self {
        let f = try identityObject(json, keys: ["version", "id", "revision", "backendId", "backendVersion", "modelBinding",
            "calibrationStatus", "mode", "challengePolicyRevision", "minimumFrames", "maximumDurationNanos", "qualityFloor",
            "scoreMetric", "threshold", "classificationPolicyRevision", "datasetRevision", "operatingPoint", "attackTypes", "effectiveAuthorizationEpoch"])
        try policyVersion(f)
        guard let binding = f["modelBinding"] as? [String: Any],
              let status = CalibrationStatus(rawValue: try identityString(f, "calibrationStatus")),
              let mode = PADMode(rawValue: try identityString(f, "mode")), let attacks = f["attackTypes"] as? [String],
              Set(attacks).count == attacks.count else { throw IdentityError(.invalidInput) }
        return try Self(id: identityString(f, "id"), revision: identityString(f, "revision"),
            backendId: identityString(f, "backendId"), backendVersion: identityString(f, "backendVersion"),
            modelBinding: ModelBinding.decodeJSON(identityJSON(binding)), calibrationStatus: status, mode: mode,
            challengePolicyRevision: policyOptionalString(f, "challengePolicyRevision"),
            minimumFrames: policyPositiveInt(f, "minimumFrames"), maximumDurationNanos: identityInteger(f, "maximumDurationNanos"),
            qualityFloor: policyNumber(f, "qualityFloor"), scoreMetric: policyOptionalString(f, "scoreMetric"),
            threshold: policyNumber(f, "threshold"), classificationPolicyRevision: policyOptionalString(f, "classificationPolicyRevision"),
            datasetRevision: policyOptionalString(f, "datasetRevision"), operatingPoint: policyOptionalString(f, "operatingPoint"),
            attackTypes: Set(attacks), effectiveAuthorizationEpoch: identityInteger(f, "effectiveAuthorizationEpoch"))
    }
}

private func policyVersion(_ f: [String: Any]) throws {
    guard try identityInteger(f, "version") == 1 else { throw IdentityError(.invalidInput) }
}
private func policyPositiveInt(_ f: [String: Any], _ key: String) throws -> Int {
    let value = try identityInteger(f, key)
    guard value > 0, value <= Int32.max else { throw IdentityError(.invalidInput) }
    return Int(value)
}
private func policyOptionalString(_ f: [String: Any], _ key: String) throws -> String? {
    if f[key] is NSNull { return nil }
    return try identityString(f, key)
}
private func policyBits(_ value: Double?) -> String? {
    value.map { String(format: "%016llx", $0.bitPattern) }
}
private func policyNumber(_ f: [String: Any], _ key: String) throws -> Double? {
    if f[key] is NSNull { return nil }
    let text = try identityString(f, key)
    guard text.range(of: "^[0-9a-f]{16}$", options: .regularExpression) != nil, let bits = UInt64(text, radix: 16) else { throw IdentityError(.invalidInput) }
    let value = Double(bitPattern: bits)
    guard value.isFinite else { throw IdentityError(.invalidInput) }
    return value
}
