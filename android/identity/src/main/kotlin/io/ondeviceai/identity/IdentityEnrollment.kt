package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityStatus

/** Explicit host quality rule for enrollment; the SDK supplies no numeric default. */
class EnrollmentOptions(val qualityFloor: Double, val qualityPolicyId: String, val revision: String) {
    init { if (!qualityFloor.isFinite() || qualityFloor !in 0.0..1.0) throw IdentityError(IdentityErrorCode.INVALID_INPUT); token(qualityPolicyId); token(revision) }
}
enum class EnrollmentOutcome { ENROLLED, INSUFFICIENT_QUALITY }
class EnrollmentResult internal constructor(
    val outcome: EnrollmentOutcome, val templateRef: TemplateRef?, val embeddingSpace: EmbeddingSpaceId,
    val quality: IdentityQuality, val backendId: String, val backendVersion: String, val modelBinding: ModelBinding,
    val inputRevision: String, val optionsRevision: String, val qualityPolicyId: String, val sessionId: String,
    val runId: String, val preprocessingRevision: String, val durationNanos: Long
)

internal class IdentityEnrollmentBinding(private val backend: IdentityBackend) {
    val space = backend.embeddingSpace
    fun validate() {
        if (backend.embeddingSpace != space || backend.backendId != space.backendId || backend.backendVersion != space.backendVersion || backend.modelBinding != space.modelBinding)
            throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
    }
    fun prepare(probe: IdentityProbe, options: EnrollmentOptions, signal: IdentityStopSignal): PreparedEnrollment {
        signal.check(); validate()
        if (backend.availability().status != CapabilityStatus.AVAILABLE) throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)
        val quality = backend.evaluateQuality(probe, signal); validate(); signal.check()
        if (quality.faceCount > 1) throw IdentityError(IdentityErrorCode.MULTIPLE_FACES)
        if (quality.faceCount != 1 || quality.score == null || quality.score < options.qualityFloor) return PreparedEnrollment(quality, null)
        val embedding = backend.embed(probe, signal); validate(); signal.check()
        if (embedding.embeddingSpace != space) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        return PreparedEnrollment(quality, embedding)
    }
}
internal data class PreparedEnrollment(val quality: IdentityQuality, val embedding: IdentityEmbedding?)
internal data class IdentityTemplateRecord(val ref: TemplateRef, val candidateId: String, val grantId: String) {
    fun fields() = mapOf("candidateId" to candidateId, "grantId" to grantId, "ref" to ref.canonicalJSON())
    companion object {
        fun restore(raw: Map<String, Any?>): IdentityTemplateRecord {
            if (raw.keys != setOf("candidateId", "grantId", "ref")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val result = IdentityTemplateRecord(TemplateRef.decodeJSON(raw.string("ref")), raw.string("candidateId"), raw.string("grantId"))
            token(result.candidateId); token(result.grantId); return result
        }
    }
}
