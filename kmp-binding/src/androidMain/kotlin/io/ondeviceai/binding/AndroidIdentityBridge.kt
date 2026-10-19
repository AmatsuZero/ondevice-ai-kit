package io.ondeviceai.binding

import android.os.Handler
import android.os.Looper
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.identity.*
import io.ondeviceai.identity.IdentityBackendSelection as NativeSelection
import io.ondeviceai.identity.IdentityErrorCode as NativeError
import io.ondeviceai.identity.IdentityScoreFields as NativeScore
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Borrows host stores, engines and backends. Only bridge tasks, builders and sessions are owned.
 * The host supplies task-scoped image resolution and a serial UI callback executor.
 * close() rejects admission immediately and initiates native stop/drain off the UI thread.
 */
class AndroidIdentityBridge(
    private val store: IdentityStore,
    private val indexes: IndexStore,
    private val registry: IdentityBackendRegistry<IdentityBackend>,
    private val verification: FaceVerification,
    private val identification: FaceIdentification,
    private val liveness: FaceLiveness? = null,
    private val probeResolver: (IdentityProbeReference) -> IdentityProbe,
    private val callbackExecutor: Executor = androidIdentityCallbacks(),
) : IdentityBridge {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val gate = java.lang.Object()
    private var closed = false
    private val tasks = mutableSetOf<NativeBridgeTask>()
    private val builders = mutableMapOf<String, GalleryBuilder>()
    private val sessions = mutableSetOf<AndroidLivenessSession>()

    override fun availability(): IdentityBridgeAvailability {
        if (synchronized(gate) { closed }) return IdentityBridgeAvailability.Unavailable(IdentityAvailabilityReason.CLOSED)
        return try {
            val backend = registry.select(NativeSelection.Default)
            if (backend.availability().status == CapabilityStatus.AVAILABLE) IdentityBridgeAvailability.Available(backend.backendId)
            else IdentityBridgeAvailability.Unavailable(IdentityAvailabilityReason.BACKEND_UNAVAILABLE)
        } catch (_: IdentityError) { IdentityBridgeAvailability.Unavailable(IdentityAvailabilityReason.BACKEND_UNAVAILABLE) }
    }

    override fun createCandidate(grant: IdentityAuthorizationGrant, callback: IdentityCallback<IdentityCandidateReference>) = submit(callback) {
        IdentityCandidateReference(store.createCandidate(grant.native()).canonicalJSON())
    }
    override fun enroll(probe: IdentityProbeReference, candidate: IdentityCandidateReference, grant: IdentityAuthorizationGrant,
        options: IdentityEnrollmentOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityEnrollmentResult>) = submit(callback) { signal ->
        val selected = select(backend)
        store.enroll(probeResolver(probe), CandidateRef.decodeJSON(candidate.canonicalValue), grant.native(),
            EnrollmentOptions(options.qualityFloor, options.qualityPolicyId, options.revision), selected, signal).bridge()
    }
    override fun verify(probe: IdentityProbeReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant,
        options: IdentityVerificationOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityVerificationResult>) = submit(callback) { signal ->
        val task = verification.verify(probeResolver(probe), TemplateRef.decodeJSON(template.canonicalValue), grant.native(),
            VerificationOptions(ThresholdPolicy.decodeJSON(options.policy.canonicalValue), options.revision), backend.native())
        val stop = signal.registerStopHandler { task.cancel() }
        try { task.result().bridge() } finally { stop.close() }
    }
    override fun createGalleryBuilder(baseGallery: IdentityGalleryReference?, grant: IdentityAuthorizationGrant,
        options: IdentityGalleryOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityGalleryBuilderReference>) = submit(callback) {
        val selected = select(backend)
        val builder = indexes.createGalleryBuilder(baseGallery?.let { GalleryRef.decodeJSON(it.canonicalValue) }, grant.native(),
            ThresholdPolicy.decodeJSON(options.policy.canonicalValue), selected, options.aggregationPolicyId)
        val id = UUID.randomUUID().toString()
        val accepted = synchronized(gate) { if (closed) false else { builders[id] = builder; true } }
        if (!accepted) { builder.close(); throw IdentityError(NativeError.CLOSED) }
        IdentityGalleryBuilderReference(id)
    }
    override fun addTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>) = submit(callback) {
        builder(builder).addTemplate(TemplateRef.decodeJSON(template.canonicalValue), grant.native())
    }
    override fun removeTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference, grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>) = submit(callback) {
        builder(builder).removeTemplate(TemplateRef.decodeJSON(template.canonicalValue), grant.native())
    }
    override fun publishGallery(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<IdentityGalleryReference>) = submit(callback) { signal ->
        val native = builder(builder)
        val stop = signal.registerStopHandler { native.close() }
        try { IdentityGalleryReference(native.publish().canonicalJSON()) } finally { stop.close() }
    }
    override fun closeGalleryBuilder(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<Unit>) = submit(callback) {
        synchronized(gate) { builders.remove(builder.canonicalValue) }?.close()
    }
    override fun identify(probe: IdentityProbeReference, gallery: IdentityGalleryReference, grant: IdentityAuthorizationGrant,
        options: IdentityIdentificationOptions, backend: IdentityBackendSelection, callback: IdentityCallback<IdentityIdentificationResult>) = submit(callback) { signal ->
        val task = identification.identify(probeResolver(probe), GalleryRef.decodeJSON(gallery.canonicalValue), grant.native(),
            IdentificationOptions(options.topK, options.policy?.let { ThresholdPolicyRef(it.id, it.revision) }, options.revision), backend.native())
        val stop = signal.registerStopHandler { task.cancel() }
        try { task.result().bridge() } finally { stop.close() }
    }
    override fun openLivenessSession(grant: IdentityAuthorizationGrant, options: IdentityLivenessOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityLivenessSession>) = submit(callback) {
        val engine = liveness ?: throw IdentityError(NativeError.BACKEND_UNAVAILABLE)
        val native = engine.openLivenessSession(grant.native(), LivenessOptions(PADPolicy.decodeJSON(options.policy.canonicalValue), options.purpose, options.revision), backend.native())
        val session = AndroidLivenessSession(native)
        val accepted = synchronized(gate) { if (closed) false else { sessions.add(session); true } }
        if (!accepted) { native.close(); throw IdentityError(NativeError.CLOSED) }
        session
    }
    override fun revokeGrant(grantId: String, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityRevocationReceipt>) = submit(callback) {
        IdentityRevocationReceipt(store.revokeGrant(grantId, ownership.native()).canonicalJSON())
    }
    override fun deleteTemplate(reference: IdentityTemplateReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>) = submit(callback) {
        IdentityDeletionReceipt(store.deleteTemplate(TemplateRef.decodeJSON(reference.canonicalValue), ownership.native()).canonicalJSON(), IdentityDeletionStore.IDENTITY)
    }
    override fun deleteCandidate(reference: IdentityCandidateReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>) = submit(callback) {
        IdentityDeletionReceipt(store.deleteCandidate(CandidateRef.decodeJSON(reference.canonicalValue), ownership.native()).canonicalJSON(), IdentityDeletionStore.IDENTITY)
    }
    override fun deleteGallery(reference: IdentityGalleryReference, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback<IdentityDeletionReceipt>) = submit(callback) {
        IdentityDeletionReceipt(indexes.deleteGallery(GalleryRef.decodeJSON(reference.canonicalValue), ownership.native()).canonicalJSON(), IdentityDeletionStore.INDEX)
    }
    override fun awaitDeletion(receipt: IdentityDeletionReceipt, options: IdentityDeletionWaitOptions, callback: IdentityCallback<IdentityDeletionStatus>) = submit(callback) { signal ->
        val native = DeletionReceipt.decodeJSON(receipt.canonicalValue)
        val deadline = options.durationNanos?.let { IdentityDeadline(SystemIdentityMonotonicClock, it) }
        val status = when (receipt.store) {
            IdentityDeletionStore.IDENTITY -> if (deadline == null) store.awaitDeletion(native) else store.awaitDeletion(native, deadline, signal)
            IdentityDeletionStore.INDEX -> if (deadline == null) indexes.awaitDeletion(native) else indexes.awaitDeletion(native, deadline, signal)
        }
        IdentityDeletionStatus.valueOf(status.name)
    }
    override fun awaitRevocation(receipt: IdentityRevocationReceipt, options: IdentityDeletionWaitOptions, callback: IdentityCallback<IdentityDeletionStatus>) = submit(callback) { signal ->
        val native = RevocationReceipt.decodeJSON(receipt.canonicalValue)
        val status = options.durationNanos?.let { store.awaitDeletion(native, IdentityDeadline(SystemIdentityMonotonicClock, it), signal) } ?: store.awaitDeletion(native)
        IdentityDeletionStatus.valueOf(status.name)
    }

    private fun select(backend: IdentityBackendSelection): IdentityBackend = registry.select(backend.native()).also {
        if (it.availability().status != CapabilityStatus.AVAILABLE) throw IdentityError(NativeError.BACKEND_UNAVAILABLE, it.backendId, it.modelBinding)
    }
    private fun builder(ref: IdentityGalleryBuilderReference): GalleryBuilder = synchronized(gate) { builders[ref.canonicalValue] }
        ?: throw IdentityError(NativeError.BUILDER_CLOSED)

    private inner class NativeBridgeTask : IdentityBridgeTask {
        val signal = IdentityStopSignal()
        override fun cancel() { identityWorkers.execute { signal.requestStop(IdentityStopReason.CANCELLED) } }
        override fun close() = cancel()
        fun stop() { identityWorkers.execute { signal.requestStop(IdentityStopReason.CLOSED) } }
    }
    private fun <T> submit(callback: IdentityCallback<T>, work: (IdentityStopSignal) -> T): IdentityBridgeTask {
        val task = NativeBridgeTask()
        val admitted = synchronized(gate) { if (closed) false else { tasks.add(task); true } }
        if (!admitted) {
            callbackExecutor.execute { callback.onFailure(IdentityBridgeFailure.Rejected(IdentityErrorCode.CLOSED)) }
            return task
        }
        identityWorkers.execute {
            val result = try { task.signal.check(); Result.success(work(task.signal)) } catch (error: Exception) { Result.failure(error) }
            task.signal.awaitDelivery()
            callbackExecutor.execute {
                try { result.fold(callback::onSuccess) { callback.onFailure(it.bridgeFailure()) } }
                finally { synchronized(gate) { tasks.remove(task); gate.notifyAll() } }
            }
        }
        return task
    }
    override fun close() {
        val resources = synchronized(gate) {
            if (closed) return
            closed = true
            Triple(tasks.toList(), builders.values.toList().also { builders.clear() }, sessions.toList().also { sessions.clear() })
        }
        resources.first.forEach { it.stop() }
        resources.second.forEach { builder -> identityWorkers.execute { builder.close() } }
        resources.third.forEach { it.close() }
    }

    private inner class AndroidLivenessSession(private val native: LivenessSession) : IdentityLivenessSession {
        override val sessionId get() = native.sessionId
        private val sessionGate = Any()
        private var closing = false
        override fun start(callback: IdentityCallback<Unit>) = submit(callback) { native.start() }
        override fun submitFrame(frame: IdentityLivenessFrame): Boolean {
            return try {
                val challenge = synchronized(sessionGate) {
                    if (closing) return false
                    frame.challenge?.let { LivenessChallenge.decodeJSON(it.canonicalValue) }
                }
                native.submitFrame(LivenessFrame(frame.encodedFrame, frame.timestampNanos, frame.revision, challenge))
            } catch (_: IdentityError) { false }
        }
        override fun nextEvent(callback: IdentityCallback<IdentityLivenessEvent?>) = submit(callback) { signal ->
            val stop = signal.registerStopHandler { native.close() }
            try { native.nextEvent()?.let(::event) } finally { stop.close() }
        }
        private fun event(value: LivenessEvent): IdentityLivenessEvent = when (value) {
            LivenessEvent.Started -> IdentityLivenessEvent.Started
            is LivenessEvent.ChallengeIssued -> IdentityLivenessEvent.ChallengeIssued(IdentityLivenessChallenge(value.challenge.canonicalJSON()))
            LivenessEvent.FrameAccepted -> IdentityLivenessEvent.FrameAccepted
            LivenessEvent.FrameDropped -> IdentityLivenessEvent.FrameDropped
            LivenessEvent.FrameStale -> IdentityLivenessEvent.FrameStale
            is LivenessEvent.Completed -> IdentityLivenessEvent.Completed(value.result.bridge())
            is LivenessEvent.Failed -> IdentityLivenessEvent.Failed(IdentityErrorCode.valueOf(value.code.name))
            LivenessEvent.Cancelled -> IdentityLivenessEvent.Cancelled
            LivenessEvent.Closed -> IdentityLivenessEvent.Closed
        }
        override fun finish() = native.finish()
        override fun cancel() = native.cancel()
        override fun result(callback: IdentityCallback<IdentityLivenessResult>) = submit(callback) { signal ->
            val stop = signal.registerStopHandler { native.cancel() }
            try { native.result().bridge() } finally { stop.close() }
        }
        override fun consumeProof(proof: IdentityLivenessProof, captureDigest: String, purpose: String, callback: IdentityCallback<Unit>) = submit(callback) {
            native.consumeProof(LivenessProof.decodeJSON(proof.canonicalValue), captureDigest, purpose)
        }
        override fun close() {
            synchronized(sessionGate) { if (closing) return; closing = true }
            identityWorkers.execute { native.close(); synchronized(gate) { sessions.remove(this) } }
        }
    }
}

private val identityWorkers = Executors.newCachedThreadPool { Thread(it, "odai-identity-bridge").apply { isDaemon = true } }
private fun androidIdentityCallbacks(): Executor {
    val handler = Handler(Looper.getMainLooper())
    return Executor { handler.post(it) }
}
private fun IdentityAuthorizationGrant.native() = AuthorizationGrant.decodeJSON(canonicalValue)
private fun IdentityOwnershipAuthorization.native() = OwnershipAuthorization(storeId, evidenceRef)
private fun IdentityBackendSelection.native(): NativeSelection = when (this) {
    IdentityBackendSelection.Default -> NativeSelection.Default
    is IdentityBackendSelection.Explicit -> NativeSelection.Explicit(backendId)
}
private fun Throwable.bridgeFailure(): IdentityBridgeFailure {
    val native = this as? IdentityError ?: return IdentityBridgeFailure.Rejected(IdentityErrorCode.BACKEND_UNAVAILABLE)
    if (native.code == NativeError.CANCELLED) return IdentityBridgeFailure.Cancelled
    return IdentityBridgeFailure.Rejected(IdentityErrorCode.valueOf(native.code.name), native.backendId,
        native.modelBinding?.let { IdentityModelBinding(it.canonicalJSON()) }, native.policyId, native.sessionId)
}
private fun NativeScore.bridge() = IdentityScoreFields(score, metric, threshold)
private fun IdentityQuality.bridge() = IdentityQualitySummary(faceCount, score)
private fun EnrollmentResult.bridge() = IdentityEnrollmentResult(IdentityEnrollmentOutcome.valueOf(outcome.name),
    templateRef?.let { IdentityTemplateReference(it.canonicalJSON()) }, IdentityEmbeddingSpace(embeddingSpace.canonicalJSON()), quality.bridge(), qualityPolicyId,
    IdentityRunMetadata(backendId, backendVersion, IdentityModelBinding(modelBinding.canonicalJSON()), inputRevision, optionsRevision, preprocessingRevision, sessionId, runId, durationNanos))
private fun VerificationResult.bridge() = IdentityVerificationResult(IdentityVerificationOutcome.valueOf(outcome.name), scoreFields.bridge(), quality.bridge(), templateRevision,
    IdentityPolicyReference(thresholdPolicyId, thresholdPolicyRevision), IdentityRunMetadata(backendId, backendVersion, IdentityModelBinding(modelBinding.canonicalJSON()), inputRevision, optionsRevision, preprocessingRevision, sessionId, runId, durationNanos))
private fun IdentificationResult.bridge() = IdentityIdentificationResult(IdentityIdentificationOutcome.valueOf(outcome.name),
    candidates.map { IdentityIdentificationCandidate(IdentityCandidateReference(it.candidateRef.canonicalJSON()), it.score, it.rank) }, scoreFields.bridge(),
    thresholdFields?.let { IdentityIdentificationThreshold(it.metric, it.threshold) }, quality.bridge(), galleryRevision, galleryContentDigest, aggregationPolicyId,
    IdentityPolicyReference(thresholdPolicyId, thresholdPolicyRevision), galleryCandidateCount, galleryTemplateCount,
    enrollmentCounts.map { IdentityEnrollmentCount(IdentityCandidateReference(it.candidateRef.canonicalJSON()), it.templateCount) },
    IdentityIdentificationGrouping(fpirFnirGrouping.datasetRevision, fpirFnirGrouping.operatingPoint, fpirFnirGrouping.enrollmentRule, fpirFnirGrouping.gallerySizeRange.first, fpirFnirGrouping.gallerySizeRange.last, fpirFnirGrouping.topK),
    IdentityRunMetadata(backendId, backendVersion, IdentityModelBinding(modelBinding.canonicalJSON()), inputRevision, optionsRevision, preprocessingRevision, sessionId, runId, durationNanos))
private fun LivenessResult.bridge() = IdentityLivenessResult(IdentityLivenessOutcome.valueOf(outcome.name), scoreFields.bridge(), quality.bridge(),
    IdentityPolicyReference(policy.id, policy.revision), acceptedFrameCount, droppedFrameCount, staleFrameCount, captureDigest,
    proof?.let { IdentityLivenessProof(it.canonicalJSON()) }, IdentityRunMetadata(backendId, backendVersion, IdentityModelBinding(modelBinding.canonicalJSON()), inputRevision, optionsRevision, null, sessionId, runId, durationNanos))
