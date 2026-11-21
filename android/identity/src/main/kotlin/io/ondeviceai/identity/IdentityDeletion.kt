package io.ondeviceai.identity

enum class DeletionStatus(internal val wireValue: String) {
    REVOKED("revoked"), PURGE_PENDING("purgePending"), PURGED("purged"), PURGE_FAILED("purgeFailed")
}

/** A store resolves this opaque receipt against its durable deletion record. */
@ConsistentCopyVisibility
data class DeletionReceipt internal constructor(
    val id: String,
    val storeId: String,
    val templateId: String? = null,
    val candidateId: String? = null,
    val galleryId: String? = null
) {
    init {
        token(id); token(storeId)
        if (listOf(templateId, candidateId, galleryId).count { it != null } != 1) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        templateId?.let(::token); candidateId?.let(::token); galleryId?.let(::token)
    }
    fun canonicalJSON(): String = IdentityContractJSON.encode(buildMap {
        put("id", id); put("storeId", storeId)
        when {
            templateId != null -> put("templateId", templateId)
            candidateId != null -> put("candidateId", candidateId)
            else -> put("galleryId", galleryId)
        }
    })
    companion object {
        fun decodeJSON(json: String): DeletionReceipt {
            val fields = IdentityContractJSON.parse(json)
            return when (fields.keys) {
                setOf("id", "storeId", "templateId") -> DeletionReceipt(fields.string("id"), fields.string("storeId"), templateId = fields.string("templateId"))
                setOf("id", "storeId", "candidateId") -> DeletionReceipt(fields.string("id"), fields.string("storeId"), candidateId = fields.string("candidateId"))
                setOf("id", "storeId", "galleryId") -> DeletionReceipt(fields.string("id"), fields.string("storeId"), galleryId = fields.string("galleryId"))
                else -> throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }
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

internal data class IdentityCandidateDeletion(val receiptId: String, val templateIds: List<String>) {
    fun fields() = mapOf("receiptId" to receiptId, "templateIds" to templateIds)
    companion object {
        fun restore(fields: Map<String, Any?>): IdentityCandidateDeletion {
            if (fields.keys != setOf("receiptId", "templateIds")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val receiptId = fields.string("receiptId").also(::token)
            val templateIds = (fields["templateIds"] as? List<*>)?.map { it as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
                ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (templateIds != templateIds.distinct().sorted()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            templateIds.forEach(::token)
            return IdentityCandidateDeletion(receiptId, templateIds)
        }
    }
}
