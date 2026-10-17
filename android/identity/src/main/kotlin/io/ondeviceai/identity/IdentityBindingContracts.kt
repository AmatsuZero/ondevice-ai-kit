package io.ondeviceai.identity

internal fun fields(json: String, keys: Set<String>): Map<String, Any?> =
    IdentityContractJSON.parse(json).also { if (it.keys != keys) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
internal fun Map<String, Any?>.string(key: String) = this[key] as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
internal fun Map<String, Any?>.optionalString(key: String) = this[key]?.let { it as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
internal fun Map<String, Any?>.long(key: String) = this[key] as? Long ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
internal fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    return this[key] as? Map<String, Any?> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
}
internal inline fun <reified T : Enum<T>> enumValue(value: String, wire: (T) -> String): T =
    enumValues<T>().firstOrNull { wire(it) == value } ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)

enum class ModelBindingKind(val wire: String) { MODEL("model"), SYSTEM("system") }
data class ModelBinding(val kind: ModelBindingKind, val id: String, val revision: String) {
    init { token(id); token(revision) }
    internal fun fields() = mapOf("id" to id, "kind" to kind.wire, "revision" to revision)
    fun canonicalJSON() = IdentityContractJSON.encode(fields())
    companion object {
        fun decodeJSON(json: String): ModelBinding {
            val f = fields(json, setOf("id", "kind", "revision"))
            return ModelBinding(enumValue(f.string("kind")) { it.wire }, f.string("id"), f.string("revision"))
        }
    }
}
enum class IdentityCapability(val wire: String) { VERIFICATION("verification"), IDENTIFICATION("identification") }
enum class FeatureDType(val wire: String) { FLOAT32("float32"), FLOAT16("float16"), INT8("int8") }
enum class Endianness(val wire: String) { LITTLE("little"), BIG("big") }
enum class ScoreDirection(val wire: String) { HIGHER_IS_BETTER("higherIsBetter"), LOWER_IS_BETTER("lowerIsBetter") }
class EmbeddingSpaceId(
    supportedCapabilities: Set<IdentityCapability>, val backendId: String, val backendVersion: String,
    val modelBinding: ModelBinding, val featureFormatVersion: String, val dimension: Int, val dtype: FeatureDType,
    val endianness: Endianness, val normalization: String, val preprocessRevision: String,
    val scoreMetric: String, val scoreDirection: ScoreDirection
) {
    val supportedCapabilities: Set<IdentityCapability> = java.util.Collections.unmodifiableSet(supportedCapabilities.toSet())
    init {
        if (supportedCapabilities.isEmpty() || dimension <= 0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        if (featureFormatVersion != "1") throw IdentityError(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED)
        listOf(backendId, backendVersion, normalization, preprocessRevision, scoreMetric).forEach(::token)
    }
    internal fun fields(): Map<String, Any?> = mapOf(
        "backendId" to backendId, "backendVersion" to backendVersion, "dimension" to dimension,
        "dtype" to dtype.wire, "endianness" to endianness.wire, "featureFormatVersion" to featureFormatVersion,
        "modelBinding" to modelBinding.fields(), "normalization" to normalization,
        "preprocessRevision" to preprocessRevision, "scoreDirection" to scoreDirection.wire,
        "scoreMetric" to scoreMetric, "supportedCapabilities" to supportedCapabilities.map { it.wire }.sorted()
    )
    fun canonicalJSON() = IdentityContractJSON.encode(fields())
    override fun equals(other: Any?) = other is EmbeddingSpaceId && fields() == other.fields()
    override fun hashCode() = fields().hashCode()
    companion object {
        fun decodeJSON(json: String): EmbeddingSpaceId {
            val f = fields(json, setOf("backendId", "backendVersion", "dimension", "dtype", "endianness",
                "featureFormatVersion", "modelBinding", "normalization", "preprocessRevision",
                "scoreDirection", "scoreMetric", "supportedCapabilities"))
            val capabilities = f["supportedCapabilities"] as? List<*> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val dimension = f.long("dimension")
            if (dimension !in 1..Int.MAX_VALUE.toLong()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return EmbeddingSpaceId(capabilities.map { v -> enumValue<IdentityCapability>(v as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)) { it.wire } }.toSet(),
                f.string("backendId"), f.string("backendVersion"), ModelBinding.decodeJSON(IdentityContractJSON.encode(f.objectValue("modelBinding"))),
                f.string("featureFormatVersion"), dimension.toInt(), enumValue(f.string("dtype")) { it.wire },
                enumValue(f.string("endianness")) { it.wire }, f.string("normalization"), f.string("preprocessRevision"),
                f.string("scoreMetric"), enumValue(f.string("scoreDirection")) { it.wire })
        }
    }
}
@ConsistentCopyVisibility
data class TemplateRef internal constructor(val id: String, val embeddingSpaceId: EmbeddingSpaceId,
    val authorizationEpoch: Long, val revision: String) {
    init { token(id); token(revision); if (authorizationEpoch < 0) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
    internal fun validate(issued: TemplateRef) {
        if (id != issued.id || revision != issued.revision) throw IdentityError(IdentityErrorCode.TEMPLATE_NOT_FOUND)
        if (embeddingSpaceId != issued.embeddingSpaceId) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        if (authorizationEpoch != issued.authorizationEpoch) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
    }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf("authorizationEpoch" to authorizationEpoch,
        "embeddingSpaceId" to embeddingSpaceId.fields(), "id" to id, "revision" to revision))
    companion object {
        fun decodeJSON(json: String): TemplateRef {
            val f = fields(json, setOf("authorizationEpoch", "embeddingSpaceId", "id", "revision"))
            return TemplateRef(f.string("id"), EmbeddingSpaceId.decodeJSON(IdentityContractJSON.encode(f.objectValue("embeddingSpaceId"))),
                f.long("authorizationEpoch"), f.string("revision"))
        }
    }
}
class IdentityDeadline(clock: IdentityMonotonicClock, durationNanos: Long) {
    private val end: Long
    init {
        val now = clock.nowNanos
        if (durationNanos <= 0 || now > Long.MAX_VALUE - durationNanos) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        end = now + durationNanos
    }
    fun isExpired(clock: IdentityMonotonicClock) = clock.nowNanos >= end
}
data class ThresholdPolicyRef(val id: String, val revision: String) {
    init { token(id); token(revision) }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf("id" to id, "revision" to revision))
    companion object {
        fun decodeJSON(json: String): ThresholdPolicyRef {
            val f = fields(json, setOf("id", "revision")); return ThresholdPolicyRef(f.string("id"), f.string("revision"))
        }
    }
}
data class PADPolicyRef(val id: String, val revision: String) {
    init { token(id); token(revision) }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf("id" to id, "revision" to revision))
    companion object {
        fun decodeJSON(json: String): PADPolicyRef {
            val f = fields(json, setOf("id", "revision")); return PADPolicyRef(f.string("id"), f.string("revision"))
        }
    }
}
data class OwnershipAuthorization(val storeId: String, val evidenceRef: String) {
    init { token(storeId); token(evidenceRef) }
}
@ConsistentCopyVisibility
data class LivenessProof internal constructor(
    val id: String, val issuerId: String, val sessionId: String, val nonce: String, val purpose: String,
    val captureDigest: String, val policy: PADPolicyRef, val modelBinding: ModelBinding,
    val authorizationEpoch: Long, val issuedAtMillis: Long, val expiresAtMillis: Long
) {
    init {
        listOf(id, issuerId, sessionId, nonce, purpose).forEach(::token)
        if (!Regex("[0-9a-f]{64}").matches(captureDigest) || authorizationEpoch < 0 || issuedAtMillis < 0 || expiresAtMillis <= issuedAtMillis)
            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf(
        "authorizationEpoch" to authorizationEpoch, "captureDigest" to captureDigest,
        "expiresAtMillis" to expiresAtMillis, "id" to id, "issuedAtMillis" to issuedAtMillis,
        "issuerId" to issuerId, "modelBinding" to modelBinding.fields(), "nonce" to nonce,
        "policy" to mapOf("id" to policy.id, "revision" to policy.revision), "purpose" to purpose, "sessionId" to sessionId
    ))
    companion object {
        fun decodeJSON(json: String): LivenessProof {
            val f = fields(json, setOf("authorizationEpoch", "captureDigest", "expiresAtMillis", "id", "issuedAtMillis",
                "issuerId", "modelBinding", "nonce", "policy", "purpose", "sessionId"))
            return LivenessProof(f.string("id"), f.string("issuerId"), f.string("sessionId"), f.string("nonce"),
                f.string("purpose"), f.string("captureDigest"), PADPolicyRef.decodeJSON(IdentityContractJSON.encode(f.objectValue("policy"))),
                ModelBinding.decodeJSON(IdentityContractJSON.encode(f.objectValue("modelBinding"))),
                f.long("authorizationEpoch"), f.long("issuedAtMillis"), f.long("expiresAtMillis"))
        }
    }
}
