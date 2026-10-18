import Foundation

public enum DeletionStatus: String, Sendable {
    case revoked, purgePending, purged, purgeFailed
}

/// A store resolves this opaque receipt against its durable deletion record.
public struct DeletionReceipt: Equatable, Sendable {
    public let id: String
    public let storeId: String
    public let templateId: String
    init(id: String, storeId: String, templateId: String) throws {
        try identityToken(id); try identityToken(storeId); try identityToken(templateId)
        self.id = id; self.storeId = storeId; self.templateId = templateId
    }
    public func canonicalJSON() -> String {
        identityJSON(["id": id, "storeId": storeId, "templateId": templateId])
    }
    public static func decodeJSON(_ json: String) throws -> Self {
        let fields = try identityObject(json, keys: ["id", "storeId", "templateId"])
        return try Self(id: identityString(fields, "id"), storeId: identityString(fields, "storeId"),
                        templateId: identityString(fields, "templateId"))
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
