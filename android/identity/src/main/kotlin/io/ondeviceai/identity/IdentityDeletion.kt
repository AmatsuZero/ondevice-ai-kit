package io.ondeviceai.identity

enum class DeletionStatus(internal val wireValue: String) {
    REVOKED("revoked"), PURGE_PENDING("purgePending"), PURGED("purged"), PURGE_FAILED("purgeFailed")
}

/** A store resolves this opaque receipt against its durable deletion record. */
data class DeletionReceipt internal constructor(val id: String, val storeId: String, val templateId: String) {
    init { token(id); token(storeId); token(templateId) }
    fun canonicalJSON(): String = IdentityContractJSON.encode(mapOf("id" to id, "storeId" to storeId, "templateId" to templateId))
    companion object {
        fun decodeJSON(json: String): DeletionReceipt {
            val fields = IdentityContractJSON.parse(json)
            if (fields.keys != setOf("id", "storeId", "templateId")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return DeletionReceipt(fields.string("id"), fields.string("storeId"), fields.string("templateId"))
        }
    }
}

internal data class IdentityTemplateDeletion(val receiptId: String, val status: DeletionStatus) {
    fun fields() = mapOf("receiptId" to receiptId, "status" to status.wireValue)
    companion object {
        fun restore(fields: Map<String, Any?>): IdentityTemplateDeletion {
            if (fields.keys != setOf("receiptId", "status")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val id = fields.string("receiptId").also(::token)
            val status = DeletionStatus.entries.firstOrNull { it.wireValue == fields.string("status") }
                ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return IdentityTemplateDeletion(id, status)
        }
    }
}
