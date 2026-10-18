package io.ondeviceai.identity

/** Store-bound revocation handle. Decoding the wire value does not establish receipt ownership. */
@ConsistentCopyVisibility
data class RevocationReceipt internal constructor(val id: String, val storeId: String) {
    init { token(id); token(storeId) }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf("id" to id, "storeId" to storeId))
    companion object {
        fun decodeJSON(json: String): RevocationReceipt {
            val fields = fields(json, setOf("id", "storeId"))
            return RevocationReceipt(fields.string("id"), fields.string("storeId"))
        }
    }
}
