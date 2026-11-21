package io.ondeviceai.identity

enum class IdentityErrorCode(val wire:String) {
    BACKEND_NOT_REGISTERED("backendNotRegistered"), BACKEND_UNAVAILABLE("backendUnavailable"), UNSUPPORTED_OPTION("unsupportedOption"),
    AUTHORIZATION_REQUIRED("authorizationRequired"), AUTHORIZATION_EXPIRED("authorizationExpired"), AUTHORIZATION_REVOKED("authorizationRevoked"),
    CANDIDATE_NOT_FOUND("candidateNotFound"), CANDIDATE_REVOKED("candidateRevoked"), TEMPLATE_NOT_FOUND("templateNotFound"), TEMPLATE_REVOKED("templateRevoked"),
    GALLERY_NOT_FOUND("galleryNotFound"), GALLERY_REVISION_GONE("galleryRevisionGone"), GALLERY_REVISION_CONFLICT("galleryRevisionConflict"), BUILDER_CLOSED("builderClosed"),
    EMBEDDING_SPACE_MISMATCH("embeddingSpaceMismatch"), FEATURE_FORMAT_UNSUPPORTED("featureFormatUnsupported"), RE_ENROLLMENT_REQUIRED("reEnrollmentRequired"), REBUILD_REQUIRED("rebuildRequired"),
    INDEX_UNAVAILABLE("indexUnavailable"), INDEX_BUILDING("indexBuilding"), THRESHOLD_UNVERIFIED("thresholdUnverified"), INVALID_INPUT("invalidInput"), MULTIPLE_FACES("multipleFaces"),
    INVALID_CHALLENGE("invalidChallenge"), CHALLENGE_EXPIRED("challengeExpired"), SESSION_TIMEOUT("sessionTimeout"), BUSY("busy"), CLOSED("closed"), CANCELLED("cancelled");
    companion object { fun fromWire(value:String) = entries.firstOrNull { it.wire == value } ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
}
class IdentityError(val code:IdentityErrorCode, val backendId:String?=null, val modelBinding:ModelBinding?=null, val policyId:String?=null, val sessionId:String?=null):IllegalArgumentException(code.wire) {
    fun canonicalJSON() = IdentityContractJSON.encode(mapOf(
        "backendId" to backendId,
        "code" to code.wire,
        "modelBinding" to modelBinding?.fields(),
        "policyId" to policyId,
        "sessionId" to sessionId
    ))
    companion object {
        fun decodeJSON(json: String): IdentityError {
            val f = fields(json, setOf("backendId", "code", "modelBinding", "policyId", "sessionId"))
            val code = IdentityErrorCode.fromWire(f.string("code"))
            val binding = if (f["modelBinding"] == null) null
                else ModelBinding.decodeJSON(IdentityContractJSON.encode(f.objectValue("modelBinding")))
            return IdentityError(code, f.optionalString("backendId"), binding, f.optionalString("policyId"), f.optionalString("sessionId"))
        }
    }
}
