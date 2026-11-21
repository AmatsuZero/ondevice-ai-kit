package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityAvailability
import java.util.Collections

/** Images are task-scoped; fixture inputs are internal, never a production image path. */
class IdentityProbe private constructor(val revision: String, image: ByteArray?, internal val fixtureID: String?, vector: List<Float>?) {
    private val imageBytes = image?.clone()
    val encodedImage: ByteArray? get() = imageBytes?.clone()
    internal val vector: List<Float>? = vector?.let { Collections.unmodifiableList(it.toList()) }
    constructor(encodedImage: ByteArray, revision: String): this(revision, encodedImage, null, null) {
        token(revision)
        if (encodedImage.isEmpty()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }
    internal constructor(fixtureID: String, vector: List<Float>): this("fixture-p1", null, fixtureID, vector) {
        token(fixtureID)
        if (vector.isEmpty() || vector.any { !it.isFinite() }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }
}
/** Backend extension boundary only; task results never contain these values. */
class IdentityEmbedding(val embeddingSpace: EmbeddingSpaceId, values: List<Float>) {
    val values: List<Float> = Collections.unmodifiableList(values.toList())
    init { if (values.size != embeddingSpace.dimension || values.any { !it.isFinite() }) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
}
data class IdentityQuality(val faceCount: Int, val score: Double? = null) {
    init { if (faceCount < 0 || score?.let { !it.isFinite() || it !in 0.0..1.0 } == true) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
}
/** Backend primitives do not issue identity decisions; engines enforce grants and policies. */
interface IdentityBackend {
    val backendId: String
    val backendVersion: String
    val modelBinding: ModelBinding
    val embeddingSpace: EmbeddingSpaceId
    fun availability(): CapabilityAvailability
    fun embed(probe: IdentityProbe): IdentityEmbedding
    fun evaluateQuality(probe: IdentityProbe): IdentityQuality
    fun embed(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityEmbedding {
        stopSignal.check()
        return try {
            embed(probe).also { stopSignal.check() }
        } catch (error: Exception) {
            stopSignal.check()
            throw error
        }
    }
    fun evaluateQuality(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityQuality {
        stopSignal.check()
        return try {
            evaluateQuality(probe).also { stopSignal.check() }
        } catch (error: Exception) {
            stopSignal.check()
            throw error
        }
    }
    fun close()
}
enum class CalibrationStatus { VERIFIED, UNVERIFIED }
enum class IdentityPolicyKind { VERIFICATION, IDENTIFICATION }
data class ThresholdPolicy(
    val id: String, val revision: String, val embeddingSpace: EmbeddingSpaceId,
    val scoreMetric: String, val scoreDirection: ScoreDirection, val calibrationStatus: CalibrationStatus,
    val kind: IdentityPolicyKind = IdentityPolicyKind.VERIFICATION, val threshold: Double? = null,
    val qualityFloor: Double? = null, val operatingPoint: String? = null, val datasetRevision: String? = null,
    val gallerySizeRange: IntRange? = null, val enrollmentRule: String? = null, val effectiveAuthorizationEpoch: Long = 0
) {
    init {
        listOf(id, revision, scoreMetric).forEach(::token)
        listOfNotNull(operatingPoint, datasetRevision, enrollmentRule).forEach(::token)
        val capability = if (kind == IdentityPolicyKind.VERIFICATION) IdentityCapability.VERIFICATION else IdentityCapability.IDENTIFICATION
        if (scoreMetric != embeddingSpace.scoreMetric || scoreDirection != embeddingSpace.scoreDirection || capability !in embeddingSpace.supportedCapabilities)
            throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        if (effectiveAuthorizationEpoch < 0 || threshold?.let { !it.isFinite() } == true ||
            qualityFloor?.let { !it.isFinite() || it !in 0.0..1.0 } == true ||
            gallerySizeRange?.let { it.isEmpty() || it.first <= 0 } == true) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        if (calibrationStatus == CalibrationStatus.VERIFIED && (threshold == null || qualityFloor == null || operatingPoint == null || datasetRevision == null || enrollmentRule == null ||
            (kind == IdentityPolicyKind.IDENTIFICATION && gallerySizeRange == null))) throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED, policyId = id)
    }
    fun canonicalJSON(): String = IdentityPolicyCodecs.encode(this)
    companion object { fun decodeJSON(json: String): ThresholdPolicy = IdentityPolicyCodecs.threshold(json) }
    fun validate(space: EmbeddingSpaceId, kind: IdentityPolicyKind = IdentityPolicyKind.VERIFICATION, epoch: Long = 0, gallerySize: Int? = null) {
        if (embeddingSpace != space) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        requireVerified()
        if (this.kind != kind || effectiveAuthorizationEpoch != epoch ||
            (kind == IdentityPolicyKind.IDENTIFICATION && (gallerySize == null || gallerySizeRange?.contains(gallerySize) != true)))
            throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED, policyId = id)
    }
    fun requireVerified() { if (calibrationStatus != CalibrationStatus.VERIFIED) throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED, policyId = id) }
}

enum class PADMode { PASSIVE, ACTIVE }
/** PAD calibration and binding are independent of identity embedding policies. */
class PADPolicy(
    val id: String, val revision: String, val backendId: String, val backendVersion: String,
    val modelBinding: ModelBinding, val calibrationStatus: CalibrationStatus, val mode: PADMode = PADMode.PASSIVE,
    val challengePolicyRevision: String? = null, val minimumFrames: Int = 1, val maximumDurationNanos: Long = 1_000_000_000,
    val qualityFloor: Double? = null, val scoreMetric: String? = null, val threshold: Double? = null,
    val classificationPolicyRevision: String? = null, val datasetRevision: String? = null, val operatingPoint: String? = null,
    attackTypes: Set<String> = emptySet(), val effectiveAuthorizationEpoch: Long = 0
) {
    val attackTypes: Set<String> = Collections.unmodifiableSet(attackTypes.toSet())
    init {
        listOf(id, revision, backendId, backendVersion).forEach(::token)
        listOfNotNull(challengePolicyRevision, scoreMetric, classificationPolicyRevision, datasetRevision, operatingPoint).forEach(::token)
        attackTypes.forEach(::token)
        if (minimumFrames <= 0 || maximumDurationNanos <= 0 || effectiveAuthorizationEpoch < 0 ||
            qualityFloor?.let { !it.isFinite() || it !in 0.0..1.0 } == true || threshold?.let { !it.isFinite() } == true ||
            ((scoreMetric == null) != (threshold == null)) || (classificationPolicyRevision != null && scoreMetric != null))
            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        if (calibrationStatus == CalibrationStatus.VERIFIED && (qualityFloor == null || datasetRevision == null || operatingPoint == null || attackTypes.isEmpty() ||
            (scoreMetric == null && classificationPolicyRevision == null) || (mode == PADMode.ACTIVE && challengePolicyRevision == null)))
            throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED, policyId = id)
    }
    fun requireVerified() { if (calibrationStatus != CalibrationStatus.VERIFIED) throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED, policyId = id) }
    fun canonicalJSON(): String = IdentityPolicyCodecs.encode(this)
    companion object { fun decodeJSON(json: String): PADPolicy = IdentityPolicyCodecs.pad(json) }
    fun validate(backendId: String, backendVersion: String, modelBinding: ModelBinding, mode: PADMode, epoch: Long) {
        requireVerified()
        if (this.backendId != backendId || this.backendVersion != backendVersion || this.modelBinding != modelBinding || this.mode != mode || effectiveAuthorizationEpoch != epoch)
            throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED, policyId = id)
    }
}
