package io.ondeviceai.binding

// Regular classes are intentional: these values must be callable from Swift/Objective-C.
// Native decoders/store admission validate them; a common wrapper grants no authority.
data class IdentityAuthorizationGrant(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityCandidateReference(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityTemplateReference(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityProbeReference(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityGalleryReference(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityGalleryBuilderReference(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityThresholdPolicy(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityPADPolicy(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityEmbeddingSpace(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityModelBinding(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityLivenessProof(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityLivenessChallenge(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }
data class IdentityRevocationReceipt(val canonicalValue: String) { init { require(canonicalValue.isNotBlank()) } }

data class IdentityOwnershipAuthorization(val storeId: String, val evidenceRef: String)
data class IdentityPolicyReference(val id: String, val revision: String)
enum class IdentityDeletionStore { IDENTITY, INDEX }
data class IdentityDeletionReceipt(val canonicalValue: String, val store: IdentityDeletionStore)

sealed interface IdentityBackendSelection {
    data object Default : IdentityBackendSelection
    data class Explicit(val backendId: String) : IdentityBackendSelection { init { require(backendId.isNotBlank()) } }
}
enum class IdentityAvailabilityReason { UNVERIFIED, AUTHORIZATION_REQUIRED, BACKEND_UNAVAILABLE, PERMISSION_REQUIRED, CLOSED }
sealed interface IdentityBridgeAvailability {
    data class Available(val backendId: String) : IdentityBridgeAvailability
    data class Unavailable(val reason: IdentityAvailabilityReason) : IdentityBridgeAvailability
}
enum class IdentityErrorCode {
    BACKEND_NOT_REGISTERED, BACKEND_UNAVAILABLE, UNSUPPORTED_OPTION,
    AUTHORIZATION_REQUIRED, AUTHORIZATION_EXPIRED, AUTHORIZATION_REVOKED,
    CANDIDATE_NOT_FOUND, CANDIDATE_REVOKED, TEMPLATE_NOT_FOUND, TEMPLATE_REVOKED,
    GALLERY_NOT_FOUND, GALLERY_REVISION_GONE, GALLERY_REVISION_CONFLICT, BUILDER_CLOSED,
    EMBEDDING_SPACE_MISMATCH, FEATURE_FORMAT_UNSUPPORTED, RE_ENROLLMENT_REQUIRED, REBUILD_REQUIRED,
    INDEX_UNAVAILABLE, INDEX_BUILDING, THRESHOLD_UNVERIFIED, INVALID_INPUT, MULTIPLE_FACES,
    INVALID_CHALLENGE, CHALLENGE_EXPIRED, SESSION_TIMEOUT, BUSY, CLOSED, CANCELLED
}
sealed interface IdentityBridgeFailure {
    data class Unavailable(val reason: IdentityAvailabilityReason) : IdentityBridgeFailure
    data object Cancelled : IdentityBridgeFailure
    data class Rejected(val code: IdentityErrorCode, val backendId: String? = null,
        val modelBinding: IdentityModelBinding? = null, val policyId: String? = null,
        val sessionId: String? = null) : IdentityBridgeFailure
}

data class IdentityEnrollmentOptions(val qualityFloor: Double, val qualityPolicyId: String, val revision: String)
data class IdentityVerificationOptions(val policy: IdentityThresholdPolicy, val revision: String)
data class IdentityGalleryOptions(val policy: IdentityThresholdPolicy, val aggregationPolicyId: String)
data class IdentityIdentificationOptions(val topK: Int, val policy: IdentityPolicyReference?, val revision: String)
data class IdentityLivenessOptions(val policy: IdentityPADPolicy, val purpose: String, val revision: String)
/** Null duration asks for the current deletion observation; a duration waits monotonically. */
data class IdentityDeletionWaitOptions(val durationNanos: Long?)

data class IdentityScoreFields(val score: Double? = null, val metric: String? = null, val threshold: Double? = null) {
    init {
        require((score == null && metric == null && threshold == null) ||
            (score != null && score.isFinite() && !metric.isNullOrBlank() && threshold != null && threshold.isFinite()))
    }
}
data class IdentityQualitySummary(val faceCount: Int, val score: Double?)
data class IdentityRunMetadata(val backendId: String, val backendVersion: String,
    val modelBinding: IdentityModelBinding, val inputRevision: String?, val optionsRevision: String,
    val preprocessingRevision: String?, val sessionId: String, val runId: String, val durationNanos: Long)
enum class IdentityEnrollmentOutcome { ENROLLED, INSUFFICIENT_QUALITY }
data class IdentityEnrollmentResult(val outcome: IdentityEnrollmentOutcome, val template: IdentityTemplateReference?,
    val embeddingSpace: IdentityEmbeddingSpace, val quality: IdentityQualitySummary,
    val qualityPolicyId: String, val metadata: IdentityRunMetadata)
enum class IdentityVerificationOutcome { MATCH, NON_MATCH, INSUFFICIENT_QUALITY }
data class IdentityVerificationResult(val outcome: IdentityVerificationOutcome, val scoreFields: IdentityScoreFields,
    val quality: IdentityQualitySummary, val templateRevision: String, val policy: IdentityPolicyReference,
    val metadata: IdentityRunMetadata)
enum class IdentityIdentificationOutcome { CANDIDATES, UNKNOWN, INSUFFICIENT_QUALITY }
data class IdentityIdentificationCandidate(val candidate: IdentityCandidateReference, val score: Double, val rank: Int)
data class IdentityEnrollmentCount(val candidate: IdentityCandidateReference, val templateCount: Int)
data class IdentityIdentificationThreshold(val metric: String, val threshold: Double)
data class IdentityIdentificationGrouping(val datasetRevision: String, val operatingPoint: String,
    val enrollmentRule: String, val minimumGallerySize: Int, val maximumGallerySize: Int, val topK: Int)
data class IdentityIdentificationResult(val outcome: IdentityIdentificationOutcome,
    val candidates: List<IdentityIdentificationCandidate>, val scoreFields: IdentityScoreFields,
    val thresholdFields: IdentityIdentificationThreshold?, val quality: IdentityQualitySummary,
    val galleryRevision: String, val galleryContentDigest: String, val aggregationPolicyId: String,
    val policy: IdentityPolicyReference, val galleryCandidateCount: Int, val galleryTemplateCount: Int,
    val enrollmentCounts: List<IdentityEnrollmentCount>, val grouping: IdentityIdentificationGrouping,
    val metadata: IdentityRunMetadata)
enum class IdentityDeletionStatus { REVOKED, PURGE_PENDING, PURGED, PURGE_FAILED }

/** Owned frame bytes; no embedding/vector input crosses the common API. */
class IdentityLivenessFrame(encodedFrame: ByteArray, val timestampNanos: Long, val revision: String,
    val challenge: IdentityLivenessChallenge?) {
    private val bytes = encodedFrame.copyOf()
    val encodedFrame: ByteArray get() = bytes.copyOf()
    init { require(bytes.isNotEmpty()); require(timestampNanos >= 0); require(revision.isNotBlank()) }
}
enum class IdentityLivenessOutcome { LIVE, SPOOF, INSUFFICIENT_QUALITY, CHALLENGE_FAILED }
data class IdentityLivenessResult(val outcome: IdentityLivenessOutcome, val scoreFields: IdentityScoreFields,
    val quality: IdentityQualitySummary, val policy: IdentityPolicyReference, val acceptedFrameCount: Int,
    val droppedFrameCount: Int, val staleFrameCount: Int, val captureDigest: String,
    val proof: IdentityLivenessProof?, val metadata: IdentityRunMetadata)
sealed interface IdentityLivenessEvent {
    data object Started : IdentityLivenessEvent
    data class ChallengeIssued(val challenge: IdentityLivenessChallenge) : IdentityLivenessEvent
    data object FrameAccepted : IdentityLivenessEvent
    data object FrameDropped : IdentityLivenessEvent
    data object FrameStale : IdentityLivenessEvent
    data class Completed(val result: IdentityLivenessResult) : IdentityLivenessEvent
    data class Failed(val code: IdentityErrorCode) : IdentityLivenessEvent
    data object Cancelled : IdentityLivenessEvent
    data object Closed : IdentityLivenessEvent
}

/** Calls and callbacks are serialized on the platform UI thread; work runs off it. */
interface IdentityCallback<T> { fun onSuccess(value: T); fun onFailure(error: IdentityBridgeFailure) }
interface IdentityBridgeTask { fun cancel(); fun close() }
interface IdentityLivenessSession {
    val sessionId: String
    fun start(callback: IdentityCallback<Unit>): IdentityBridgeTask
    fun submitFrame(frame: IdentityLivenessFrame): Boolean
    fun nextEvent(callback: IdentityCallback<IdentityLivenessEvent?>): IdentityBridgeTask
    fun finish()
    fun cancel()
    fun result(callback: IdentityCallback<IdentityLivenessResult>): IdentityBridgeTask
    fun consumeProof(proof: IdentityLivenessProof, captureDigest: String, purpose: String, callback: IdentityCallback<Unit>): IdentityBridgeTask
    fun close()
}

interface IdentityBridge {
    fun availability(): IdentityBridgeAvailability
    fun createCandidate(grant: IdentityAuthorizationGrant, callback: IdentityCallback<IdentityCandidateReference>): IdentityBridgeTask
    fun enroll(probe: IdentityProbeReference, candidate: IdentityCandidateReference, grant: IdentityAuthorizationGrant, options: IdentityEnrollmentOptions, backend: IdentityBackendSelection = IdentityBackendSelection.Default, callback: IdentityCallback<IdentityEnrollmentResult>): IdentityBridgeTask
    fun verify(probe: IdentityProbeReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, options: IdentityVerificationOptions, backend: IdentityBackendSelection = IdentityBackendSelection.Default, callback: IdentityCallback<IdentityVerificationResult>): IdentityBridgeTask
    fun createGalleryBuilder(baseGallery: IdentityGalleryReference?, grant: IdentityAuthorizationGrant, options: IdentityGalleryOptions, backend: IdentityBackendSelection = IdentityBackendSelection.Default, callback: IdentityCallback<IdentityGalleryBuilderReference>): IdentityBridgeTask
    fun addTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>): IdentityBridgeTask
    fun removeTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>): IdentityBridgeTask
    fun publishGallery(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<IdentityGalleryReference>): IdentityBridgeTask
    fun closeGalleryBuilder(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<Unit>): IdentityBridgeTask
    fun identify(probe: IdentityProbeReference, gallery: IdentityGalleryReference, grant: IdentityAuthorizationGrant, options: IdentityIdentificationOptions, backend: IdentityBackendSelection = IdentityBackendSelection.Default, callback: IdentityCallback<IdentityIdentificationResult>): IdentityBridgeTask
    fun openLivenessSession(grant: IdentityAuthorizationGrant, options: IdentityLivenessOptions, backend: IdentityBackendSelection = IdentityBackendSelection.Default, callback: IdentityCallback<IdentityLivenessSession>): IdentityBridgeTask
    fun revokeGrant(grantId: String, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityRevocationReceipt>): IdentityBridgeTask
    fun deleteTemplate(reference: IdentityTemplateReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>): IdentityBridgeTask
    fun deleteCandidate(reference: IdentityCandidateReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>): IdentityBridgeTask
    fun deleteGallery(reference: IdentityGalleryReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>): IdentityBridgeTask
    fun awaitDeletion(receipt: IdentityDeletionReceipt, options: IdentityDeletionWaitOptions, callback: IdentityCallback<IdentityDeletionStatus>): IdentityBridgeTask
    fun awaitRevocation(receipt: IdentityRevocationReceipt, options: IdentityDeletionWaitOptions, callback: IdentityCallback<IdentityDeletionStatus>): IdentityBridgeTask
    fun close()
}

private object CompletedIdentityBridgeTask : IdentityBridgeTask { override fun cancel() {}; override fun close() {} }
/** Explicit unavailable host configuration; never constructs a biometric result. */
class UnavailableIdentityBridge(private val reason: IdentityAvailabilityReason = IdentityAvailabilityReason.UNVERIFIED) : IdentityBridge {
    private var closed = false
    override fun availability() = IdentityBridgeAvailability.Unavailable(if (closed) IdentityAvailabilityReason.CLOSED else reason)
    private fun <T> reject(callback: IdentityCallback<T>): IdentityBridgeTask {
        callback.onFailure(if (closed) IdentityBridgeFailure.Rejected(IdentityErrorCode.CLOSED) else IdentityBridgeFailure.Unavailable(reason))
        return CompletedIdentityBridgeTask
    }
    override fun createCandidate(grant: IdentityAuthorizationGrant, callback: IdentityCallback<IdentityCandidateReference>) = reject(callback)
    override fun enroll(probe: IdentityProbeReference, candidate: IdentityCandidateReference, grant: IdentityAuthorizationGrant, options: IdentityEnrollmentOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityEnrollmentResult>) = reject(callback)
    override fun verify(probe: IdentityProbeReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, options: IdentityVerificationOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityVerificationResult>) = reject(callback)
    override fun createGalleryBuilder(baseGallery: IdentityGalleryReference?, grant: IdentityAuthorizationGrant, options: IdentityGalleryOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityGalleryBuilderReference>) = reject(callback)
    override fun addTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>) = reject(callback)
    override fun removeTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>) = reject(callback)
    override fun publishGallery(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<IdentityGalleryReference>) = reject(callback)
    override fun closeGalleryBuilder(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<Unit>) = reject(callback)
    override fun identify(probe: IdentityProbeReference, gallery: IdentityGalleryReference, grant: IdentityAuthorizationGrant, options: IdentityIdentificationOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityIdentificationResult>) = reject(callback)
    override fun openLivenessSession(grant: IdentityAuthorizationGrant, options: IdentityLivenessOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityLivenessSession>) = reject(callback)
    override fun revokeGrant(grantId: String, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityRevocationReceipt>) = reject(callback)
    override fun deleteTemplate(reference: IdentityTemplateReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>) = reject(callback)
    override fun deleteCandidate(reference: IdentityCandidateReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>) = reject(callback)
    override fun deleteGallery(reference: IdentityGalleryReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>) = reject(callback)
    override fun awaitDeletion(receipt: IdentityDeletionReceipt, options: IdentityDeletionWaitOptions, callback: IdentityCallback<IdentityDeletionStatus>) = reject(callback)
    override fun awaitRevocation(receipt: IdentityRevocationReceipt, options: IdentityDeletionWaitOptions, callback: IdentityCallback<IdentityDeletionStatus>) = reject(callback)
    override fun close() { closed = true }
}

/** Swift cannot construct Kotlin Unit from an erased IdentityCallback<*> protocol. */
object IdentityBridgeValues { val unitValue: Any get() = Unit }
