import Foundation

public enum DeletionStatus: String, Sendable {
    case revoked, purgePending, purged, purgeFailed
}

/// A store resolves this opaque receipt against its durable deletion record.
public struct DeletionReceipt: Equatable, Sendable {
    public let id: String
    public let storeId: String
    public let templateId: String?
    public let candidateId: String?
    public let galleryId: String?
    init(id: String, storeId: String, templateId: String) throws {
        try identityToken(id); try identityToken(storeId); try identityToken(templateId)
        self.id = id; self.storeId = storeId; self.templateId = templateId; self.candidateId = nil; self.galleryId = nil
    }
    init(id: String, storeId: String, candidateId: String) throws {
        try identityToken(id); try identityToken(storeId); try identityToken(candidateId)
        self.id = id; self.storeId = storeId; self.templateId = nil; self.candidateId = candidateId; self.galleryId = nil
    }
    init(id: String, storeId: String, galleryId: String) throws {
        try identityToken(id); try identityToken(storeId); try identityToken(galleryId)
        self.id = id; self.storeId = storeId; self.templateId = nil; self.candidateId = nil; self.galleryId = galleryId
    }
    public func canonicalJSON() -> String {
        if let templateId { return identityJSON(["id": id, "storeId": storeId, "templateId": templateId]) }
        if let candidateId { return identityJSON(["id": id, "storeId": storeId, "candidateId": candidateId]) }
        return identityJSON(["id": id, "storeId": storeId, "galleryId": galleryId!])
    }
    public static func decodeJSON(_ json: String) throws -> Self {
        if let fields = try? identityObject(json, keys: ["id", "storeId", "templateId"]) {
            return try Self(id: identityString(fields, "id"), storeId: identityString(fields, "storeId"), templateId: identityString(fields, "templateId"))
        }
        if let fields = try? identityObject(json, keys: ["id", "storeId", "galleryId"]) {
            return try Self(id: identityString(fields, "id"), storeId: identityString(fields, "storeId"), galleryId: identityString(fields, "galleryId"))
        }
        let fields = try identityObject(json, keys: ["id", "storeId", "candidateId"])
        return try Self(id: identityString(fields, "id"), storeId: identityString(fields, "storeId"), candidateId: identityString(fields, "candidateId"))
    }
}

struct IdentityCandidateDeletion: Sendable {
    let receiptId: String
    let templateIds: Set<String>
    var fields: [String: Any] { ["receiptId": receiptId, "templateIds": templateIds.sorted()] }
    static func restore(_ fields: [String: Any]) throws -> Self {
        guard Set(fields.keys) == ["receiptId", "templateIds"], let targets = fields["templateIds"] as? [String],
              targets == Set(targets).sorted() else { throw IdentityError(.invalidInput) }
        let id = try identityString(fields, "receiptId"); try identityToken(id)
        for target in targets { try identityToken(target) }
        return Self(receiptId: id, templateIds: Set(targets))
    }
}

struct IdentityTemplateDeletion: Sendable {
    let receiptId: String
    let status: DeletionStatus
    var fields: [String: Any] { ["receiptId": receiptId, "status": status.rawValue] }
    func withStatus(_ status: DeletionStatus) -> Self { Self(receiptId: receiptId, status: status) }
    static func restore(_ fields: [String: Any]) throws -> Self {
        guard Set(fields.keys) == ["receiptId", "status"],
              let status = DeletionStatus(rawValue: try identityString(fields, "status")) else {
            throw IdentityError(.invalidInput)
        }
        let id = try identityString(fields, "receiptId"); try identityToken(id)
        return Self(receiptId: id, status: status)
    }
}
