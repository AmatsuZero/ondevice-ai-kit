package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.*

private class IdentityTask(private val onCancel: () -> Unit = {}) : IdentityBridgeTask {
    override fun cancel() = onCancel()
    override fun close() = onCancel()
}
private fun verification() = IdentityVerificationResult(IdentityVerificationOutcome.NON_MATCH,
    IdentityScoreFields(0.1, "cosine", 0.8), IdentityQualitySummary(1, 1.0), "template-v1",
    IdentityPolicyReference("policy", "v1"), metadata())
private fun metadata() = IdentityRunMetadata("fixture.identity", "1", IdentityModelBinding("system"),
    "probe-v1", "options-v1", "preprocess-v1", "session", "run", 1)
private class FakeIdentityBridge : IdentityBridge by UnavailableIdentityBridge() {
    val callbacks = mutableListOf<IdentityCallback<IdentityVerificationResult>>()
    val selections = mutableListOf<IdentityBackendSelection>()
    var cancellations = 0
    var completeSynchronously = false
    var enrollmentCallback: IdentityCallback<IdentityEnrollmentResult>? = null
    var identificationCallback: IdentityCallback<IdentityIdentificationResult>? = null
    var livenessCallback: IdentityCallback<IdentityLivenessSession>? = null
    var lastProbe: IdentityProbeReference? = null
    var lastCandidate: IdentityCandidateReference? = null
    var lastTemplate: IdentityTemplateReference? = null
    var lastGallery: IdentityGalleryReference? = null
    var lastGrant: IdentityAuthorizationGrant? = null
    var advertisedAvailability: IdentityBridgeAvailability = IdentityBridgeAvailability.Available("fixture.identity")
    var candidateCount = 1
    var closes = 0
    val deletedCandidates = mutableListOf<IdentityCandidateReference>()
    val deletedGalleries = mutableListOf<IdentityGalleryReference>()
    val addedTemplates = mutableListOf<IdentityTemplateReference>()
    var galleryOptions: IdentityGalleryOptions? = null
    var deletionStatus = IdentityDeletionStatus.PURGED
    var indexDeletionStatus: IdentityDeletionStatus? = null
    val deletionEvents = mutableListOf<String>()
    var candidateCallback: IdentityCallback<IdentityCandidateReference>? = null
    var delayCandidate = false
    override fun availability() = advertisedAvailability
    override fun createCandidate(grant: IdentityAuthorizationGrant, callback: IdentityCallback<IdentityCandidateReference>): IdentityBridgeTask {
        candidateCallback = callback
        if (!delayCandidate) callback.onSuccess(IdentityCandidateReference("candidate-${++candidateCount}"))
        return IdentityTask()
    }
    override fun deleteCandidate(reference: IdentityCandidateReference, ownership: IdentityOwnershipAuthorization,
        callback: IdentityCallback<IdentityDeletionReceipt>): IdentityBridgeTask {
        deletedCandidates += reference
        deletionEvents += "delete-candidate"
        callback.onSuccess(IdentityDeletionReceipt("candidate-receipt", IdentityDeletionStore.IDENTITY))
        return IdentityTask()
    }
    override fun awaitDeletion(receipt: IdentityDeletionReceipt, options: IdentityDeletionWaitOptions,
        callback: IdentityCallback<IdentityDeletionStatus>): IdentityBridgeTask {
        deletionEvents += "await-${receipt.store}"
        callback.onSuccess(if (receipt.store == IdentityDeletionStore.INDEX) indexDeletionStatus ?: deletionStatus else deletionStatus)
        return IdentityTask()
    }
    override fun createGalleryBuilder(baseGallery: IdentityGalleryReference?, grant: IdentityAuthorizationGrant,
        options: IdentityGalleryOptions, backend: IdentityBackendSelection,
        callback: IdentityCallback<IdentityGalleryBuilderReference>): IdentityBridgeTask {
        assertNull(baseGallery); galleryOptions = options; selections += backend
        callback.onSuccess(IdentityGalleryBuilderReference("builder")); return IdentityTask()
    }
    override fun addTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference,
        grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>): IdentityBridgeTask {
        addedTemplates += template; callback.onSuccess(Unit); return IdentityTask()
    }
    override fun publishGallery(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<IdentityGalleryReference>): IdentityBridgeTask {
        callback.onSuccess(IdentityGalleryReference("published-gallery")); return IdentityTask()
    }
    override fun closeGalleryBuilder(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<Unit>): IdentityBridgeTask {
        callback.onSuccess(Unit); return IdentityTask()
    }
    override fun deleteGallery(reference: IdentityGalleryReference, ownership: IdentityOwnershipAuthorization,
        callback: IdentityCallback<IdentityDeletionReceipt>): IdentityBridgeTask {
        deletedGalleries += reference
        deletionEvents += "delete-gallery"
        callback.onSuccess(IdentityDeletionReceipt("gallery-receipt", IdentityDeletionStore.INDEX)); return IdentityTask()
    }
    override fun close() { closes++ }
    override fun verify(probe: IdentityProbeReference, template: IdentityTemplateReference,
        grant: IdentityAuthorizationGrant, options: IdentityVerificationOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityVerificationResult>): IdentityBridgeTask {
        lastProbe = probe; lastTemplate = template; lastGrant = grant
        selections += backend; callbacks += callback
        if (completeSynchronously) callback.onSuccess(verification())
        return IdentityTask { cancellations++; callback.onFailure(IdentityBridgeFailure.Cancelled) }
    }
    override fun enroll(probe: IdentityProbeReference, candidate: IdentityCandidateReference,
        grant: IdentityAuthorizationGrant, options: IdentityEnrollmentOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityEnrollmentResult>): IdentityBridgeTask {
        lastProbe = probe; lastCandidate = candidate; lastGrant = grant
        selections += backend; enrollmentCallback = callback; return IdentityTask()
    }
    override fun identify(probe: IdentityProbeReference, gallery: IdentityGalleryReference,
        grant: IdentityAuthorizationGrant, options: IdentityIdentificationOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityIdentificationResult>): IdentityBridgeTask {
        lastProbe = probe; lastGallery = gallery; lastGrant = grant
        selections += backend; identificationCallback = callback; return IdentityTask()
    }
    override fun openLivenessSession(grant: IdentityAuthorizationGrant, options: IdentityLivenessOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityLivenessSession>): IdentityBridgeTask {
        selections += backend; livenessCallback = callback; return IdentityTask()
    }
}
private class FakeSession(override val sessionId: String = "session") : IdentityLivenessSession {
    var submittedFrames = 0
    val eventCallbacks = mutableListOf<IdentityCallback<IdentityLivenessEvent?>>()
    var closes = 0
    var immediateEvents = 0
    override fun start(callback: IdentityCallback<Unit>): IdentityBridgeTask { callback.onSuccess(Unit); return IdentityTask() }
    override fun submitFrame(frame: IdentityLivenessFrame): Boolean { submittedFrames++; return true }
    override fun nextEvent(callback: IdentityCallback<IdentityLivenessEvent?>): IdentityBridgeTask {
        eventCallbacks += callback
        if (immediateEvents > 0) { immediateEvents--; callback.onSuccess(IdentityLivenessEvent.FrameAccepted) }
        return IdentityTask()
    }
    override fun finish() {}
    override fun cancel() {}
    override fun result(callback: IdentityCallback<IdentityLivenessResult>) = IdentityTask()
    override fun consumeProof(proof: IdentityLivenessProof, captureDigest: String, purpose: String, callback: IdentityCallback<Unit>) = IdentityTask()
    override fun close() { closes++ }
}

class IdentityViewModelTest {
    private val probe = IdentityProbeReference("probe")
    private val template = IdentityTemplateReference("template")
    private val grant = IdentityAuthorizationGrant("grant")
    private val options = IdentityVerificationOptions(IdentityThresholdPolicy("policy"), "options-v1")
    private val livenessOptions = IdentityLivenessOptions(IdentityPADPolicy("pad-policy"), "purpose", "v1")

    @Test fun defaultAndExplicitBackendReachVerificationSeparately() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.verify(probe, template, grant, options)
        model.verify(probe, template, grant, options, IdentityBackendSelection.Explicit("vendor.identity"))
        assertEquals(1, bridge.cancellations)
        assertEquals(listOf(IdentityBackendSelection.Default, IdentityBackendSelection.Explicit("vendor.identity")), bridge.selections)
        assertTrue(model.state.busy)
        assertNull(model.state.error)
    }
    @Test fun explicitCapabilityCallsAreNotBlockedByDefaultIdentityAvailability() {
        val bridge = FakeIdentityBridge().apply {
            advertisedAvailability = IdentityBridgeAvailability.Unavailable(IdentityAvailabilityReason.BACKEND_UNAVAILABLE)
        }
        val model = IdentityViewModel(bridge)
        val selection = IdentityBackendSelection.Explicit("host.plugin")
        model.verify(probe, template, grant, options, selection)
        assertEquals(1, bridge.callbacks.size)
        model.enroll(probe, IdentityCandidateReference("candidate"), grant,
            IdentityEnrollmentOptions(0.8, "host-quality", "v1"), selection)
        assertNotNull(bridge.enrollmentCallback)
        model.identify(probe, IdentityGalleryReference("gallery"), grant,
            IdentityIdentificationOptions(3, null, "v1"), selection)
        assertNotNull(bridge.identificationCallback)
        model.openLiveness(grant, livenessOptions, selection)
        assertNotNull(bridge.livenessCallback)
        assertEquals<List<IdentityBackendSelection>>(List(4) { selection }, bridge.selections)
        assertTrue(model.state.busy)
        assertNull(model.state.error)
    }
    @Test fun unconfiguredBridgeStillReturnsUnavailableWithoutIdentityResults() {
        val model = IdentityViewModel(UnavailableIdentityBridge())
        model.verify(probe, template, grant, options)
        assertEquals(IdentityBridgeFailure.Unavailable(IdentityAvailabilityReason.UNVERIFIED), model.state.error)
        assertFalse(model.state.busy)
        assertNull(model.state.verification)
    }
    private fun hostConfiguration() = IdentityDemoConfiguration(grant = grant,
        candidate = IdentityCandidateReference("host-candidate"), template = template,
        gallery = IdentityGalleryReference("host-gallery"),
        enrollmentOptions = IdentityEnrollmentOptions(0.8, "host-quality", "v1"),
        verificationOptions = options,
        identificationOptions = IdentityIdentificationOptions(3, null, "v1"),
        livenessOptions = livenessOptions)

    private fun galleryConfiguration() = hostConfiguration().copy(template = null, gallery = null,
        ownership = IdentityOwnershipAuthorization("host-store", "owner-proof"),
        galleryOptions = IdentityGalleryOptions(IdentityThresholdPolicy("host-identification-policy"), "subject-max-v1"))

    private fun completeEnrollment(bridge: FakeIdentityBridge, ref: String) {
        bridge.enrollmentCallback!!.onSuccess(IdentityEnrollmentResult(IdentityEnrollmentOutcome.ENROLLED,
            IdentityTemplateReference(ref), IdentityEmbeddingSpace("space"), IdentityQualitySummary(1, 1.0), "quality", metadata()))
    }

    @Test fun addingAndSelectingCandidatesKeepsBridgeOpenAndSeparatesInputsAndTemplates() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); model.selectProbe(probe); model.enrollConfigured()
        completeEnrollment(bridge, "first-template")
        val first = model.candidates.single().reference
        model.createCandidateConfigured()
        assertEquals(2, model.candidates.size)
        assertEquals(IdentityCandidateReference("candidate-2"), model.selectedCandidate)
        assertFalse(model.inputReady); assertNull(model.enrolledTemplate); assertEquals(0, bridge.closes)
        model.selectCandidateConfigured(first)
        assertFalse(model.inputReady)
        model.selectProbe(IdentityProbeReference("fresh-input")); model.verifyConfigured()
        assertEquals(IdentityTemplateReference("first-template"), bridge.lastTemplate)
        assertEquals(0, bridge.closes)
    }

    @Test fun multipleEnrollmentsStayWithTheirSelectedCandidate() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "a")
        model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "b")
        assertEquals(listOf("a", "b"), model.candidates.single().templates.map { it.canonicalValue })
        model.createCandidateConfigured(); model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "c")
        assertEquals(listOf(2, 1), model.candidates.map { it.templates.size })
        assertEquals(IdentityTemplateReference("c"), model.enrolledTemplate)
    }

    @Test fun candidateDeletionRetainsOtherCandidatesAndReportsActualPurgeStatus() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); val first = model.selectedCandidate
        model.createCandidateConfigured(); val removed = model.selectedCandidate
        bridge.deletionStatus = IdentityDeletionStatus.PURGE_PENDING
        model.deleteCandidateConfigured()
        assertEquals(listOf(removed), bridge.deletedCandidates)
        assertEquals(first, model.selectedCandidate)
        assertEquals(1, model.candidates.size)
        assertEquals(IdentityDeletionStatus.PURGE_PENDING, model.state.deletion)
        assertFalse(model.inputReady)
    }

    @Test fun galleryActionsUseAllTemplatesAndHostPolicyWithoutClosingTheBridge() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        val configuration = galleryConfiguration(); model.configure(configuration)
        model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "a")
        model.createCandidateConfigured(); model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "b")
        val selection = IdentityBackendSelection.Explicit("host.plugin")
        model.publishGalleryConfigured(selection)
        assertEquals(configuration.galleryOptions, bridge.galleryOptions)
        assertEquals(listOf("a", "b"), bridge.addedTemplates.map { it.canonicalValue })
        assertEquals(selection, bridge.selections.last())
        assertEquals(2, model.galleryTemplateCount)
        assertEquals(IdentityGalleryReference("published-gallery"), model.publishedGallery)
        model.selectProbe(probe); model.identifyConfigured()
        assertEquals(model.publishedGallery, bridge.lastGallery)
        model.deleteGalleryConfigured()
        assertEquals(listOf(IdentityGalleryReference("published-gallery")), bridge.deletedGalleries)
        assertNull(model.publishedGallery); assertEquals(0, model.galleryTemplateCount)
        assertEquals(2, model.candidates.size); assertEquals(0, bridge.closes)
        assertEquals(IdentityDeletionStatus.PURGED, model.state.deletion)
    }

    @Test fun cancelledCandidateCallbackCannotReplaceTheCurrentSelection() {
        val bridge = FakeIdentityBridge().apply { delayCandidate = true }; val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); val first = model.selectedCandidate
        model.createCandidateConfigured(); model.cancel()
        bridge.candidateCallback!!.onSuccess(IdentityCandidateReference("late-candidate"))
        assertEquals(first, model.selectedCandidate); assertEquals(1, model.candidates.size)
        assertEquals(IdentityBridgeFailure.Cancelled, model.state.error)
    }

    @Test fun deletingCandidatePurgesItsGalleryBeforeDeletingTheMember() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "a")
        model.publishGalleryConfigured(); model.deleteCandidateConfigured()
        assertEquals(listOf("delete-gallery", "await-INDEX", "delete-candidate", "await-IDENTITY"), bridge.deletionEvents)
        assertNull(model.publishedGallery); assertTrue(model.candidates.isEmpty())
        assertEquals(IdentityDeletionStatus.PURGED, model.state.deletion)
    }

    @Test fun pendingGalleryPurgeDefersCandidateDeletionUntilRefreshed() {
        val bridge = FakeIdentityBridge().apply { indexDeletionStatus = IdentityDeletionStatus.PURGE_PENDING }
        val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "a")
        model.publishGalleryConfigured(); model.deleteCandidateConfigured()
        assertTrue(bridge.deletedCandidates.isEmpty()); assertEquals(1, model.candidates.size)
        assertEquals(IdentityDeletionStatus.PURGE_PENDING, model.state.deletion)
        bridge.indexDeletionStatus = IdentityDeletionStatus.PURGED
        model.refreshDeletion()
        assertEquals(listOf(IdentityCandidateReference("host-candidate")), bridge.deletedCandidates)
        assertTrue(model.candidates.isEmpty()); assertEquals(IdentityDeletionStatus.PURGED, model.state.deletion)
    }

    @Test fun currentGalleryMustBeDeletedBeforeRepublishing() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "a")
        model.publishGalleryConfigured(); model.publishGalleryConfigured()
        assertEquals(1, bridge.addedTemplates.size)
        assertEquals(IdentityBridgeFailure.Rejected(IdentityErrorCode.GALLERY_REVISION_CONFLICT), model.state.error)
        assertEquals(IdentityGalleryReference("published-gallery"), model.publishedGallery)
    }

    @Test fun failedGalleryPurgeIsTerminalAndRefreshDoesNotPretendToRecover() {
        val bridge = FakeIdentityBridge().apply { indexDeletionStatus = IdentityDeletionStatus.PURGE_FAILED }
        val model = IdentityViewModel(bridge)
        model.configure(galleryConfiguration()); model.selectProbe(probe); model.enrollConfigured(); completeEnrollment(bridge, "a")
        model.publishGalleryConfigured(); model.deleteCandidateConfigured()
        assertEquals(IdentityDeletionStatus.PURGE_FAILED, model.state.deletion)
        assertTrue(model.deletionRecoveryRequired); assertTrue(model.candidateDeletionPending)
        val events = bridge.deletionEvents.toList()
        model.selectProbe(probe); model.refreshDeletion()
        assertEquals(events, bridge.deletionEvents)
        assertTrue(bridge.deletedCandidates.isEmpty()); assertEquals(1, model.candidates.size)
    }

    @Test fun configuredActionsRequireHostAuthorizationAndInputBeforeBridgeWork() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.enrollConfigured()
        assertEquals(IdentityBridgeFailure.Unavailable(IdentityAvailabilityReason.AUTHORIZATION_REQUIRED), model.state.error)
        model.configure(hostConfiguration())
        model.verifyConfigured()
        assertEquals(IdentityBridgeFailure.Rejected(IdentityErrorCode.INVALID_INPUT), model.state.error)
        assertTrue(bridge.selections.isEmpty())
        assertFalse(model.state.busy)
    }
    @Test fun configuredActionsUseOnlyHostReferencesPoliciesAndSelectedBackend() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(hostConfiguration()); model.selectProbe(probe)
        val selection = IdentityBackendSelection.Explicit("installed.plugin")
        model.enrollConfigured(selection)
        assertEquals(IdentityCandidateReference("host-candidate"), bridge.lastCandidate)
        assertEquals(probe, bridge.lastProbe); assertEquals(grant, bridge.lastGrant)
        model.verifyConfigured(selection)
        assertEquals(template, bridge.lastTemplate)
        model.identifyConfigured(selection)
        assertEquals(IdentityGalleryReference("host-gallery"), bridge.lastGallery)
        model.openLivenessConfigured(selection)
        assertEquals(List<IdentityBackendSelection>(4) { selection }, bridge.selections)
    }
    @Test fun enrollingThenSelectingAnotherImageKeepsTheOpaqueTemplateForVerification() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(hostConfiguration().copy(template = null)); model.selectProbe(probe)
        model.enrollConfigured()
        val issued = IdentityTemplateReference("native-issued")
        bridge.enrollmentCallback!!.onSuccess(IdentityEnrollmentResult(IdentityEnrollmentOutcome.ENROLLED,
            issued, IdentityEmbeddingSpace("space"), IdentityQualitySummary(1, 1.0), "quality", metadata()))
        model.selectProbe(IdentityProbeReference("next-image"))
        model.verifyConfigured()
        assertEquals(issued, bridge.lastTemplate)
        assertEquals(IdentityProbeReference("next-image"), bridge.lastProbe)
    }
    @Test fun replacingHostConfigurationInvalidatesOldResultAndClearsOldTemplate() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(hostConfiguration()); model.selectProbe(probe); model.enrollConfigured()
        val old = bridge.enrollmentCallback!!
        model.configure(hostConfiguration().copy(grant = IdentityAuthorizationGrant("new-grant"), template = null))
        old.onSuccess(IdentityEnrollmentResult(IdentityEnrollmentOutcome.ENROLLED, template,
            IdentityEmbeddingSpace("space"), IdentityQualitySummary(1, 1.0), "quality", metadata()))
        assertFalse(model.inputReady)
        model.selectProbe(probe)
        model.verifyConfigured()
        assertEquals(IdentityBridgeFailure.Rejected(IdentityErrorCode.TEMPLATE_NOT_FOUND), model.state.error)
        assertNull(model.enrolledTemplate)
        assertTrue(bridge.callbacks.isEmpty())
    }
    @Test fun configurationCanEnableVerificationWithoutEnrollmentOrPADPolicies() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(IdentityDemoConfiguration(grant, template = template, verificationOptions = options))
        model.selectProbe(probe); model.verifyConfigured()
        assertEquals(1, bridge.callbacks.size)
        model.openLivenessConfigured()
        assertEquals(IdentityBridgeFailure.Rejected(IdentityErrorCode.UNSUPPORTED_OPTION), model.state.error)
        assertNull(bridge.livenessCallback)
    }
    @Test fun clearingSelectedProbePreventsReuseOfAnOldHostInput() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.configure(hostConfiguration()); model.selectProbe(probe); model.verifyConfigured()
        model.selectProbe(null)
        bridge.callbacks.single().onSuccess(verification())
        assertNull(model.state.verification)
        model.verifyConfigured()
        assertEquals(IdentityBridgeFailure.Rejected(IdentityErrorCode.INVALID_INPUT), model.state.error)
        assertEquals(1, bridge.callbacks.size)
    }
    @Test fun staleVerificationCallbackIsCancelledAndIgnored() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.verify(probe, template, grant, options)
        model.verify(probe, template, grant, options)
        bridge.callbacks[0].onSuccess(verification())
        assertTrue(model.state.busy); assertNull(model.state.verification)
        bridge.callbacks[1].onSuccess(verification())
        assertEquals(verification(), model.state.verification)
        assertFalse(model.state.busy)
    }
    @Test fun verificationPublishesOnlyTheFirstTerminalCallback() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.verify(probe, template, grant, options)
        bridge.callbacks.single().onSuccess(verification())
        bridge.callbacks.single().onFailure(IdentityBridgeFailure.Cancelled)
        assertNotNull(model.state.verification); assertNull(model.state.error)
    }
    @Test fun synchronousCompletionDoesNotLeaveAnActiveTaskToCancel() {
        val bridge = FakeIdentityBridge().apply { completeSynchronously = true }
        val model = IdentityViewModel(bridge)
        model.verify(probe, template, grant, options)
        model.verify(probe, template, grant, options)
        assertEquals(0, bridge.cancellations); assertFalse(model.state.busy)
    }
    @Test fun enrollmentAndIdentificationRemainSeparateWithOpaqueResults() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.enroll(probe, IdentityCandidateReference("opaque"), grant, IdentityEnrollmentOptions(0.8, "host-quality", "v1"))
        val result = IdentityEnrollmentResult(IdentityEnrollmentOutcome.ENROLLED, template, IdentityEmbeddingSpace("full-space"), IdentityQualitySummary(1, 1.0), "host-quality", metadata())
        bridge.enrollmentCallback!!.onSuccess(result)
        assertEquals(template, model.state.enrollment?.template)
        assertNull(model.state.verification); assertNull(model.state.identification)
        model.identify(probe, IdentityGalleryReference("gallery"), grant, IdentityIdentificationOptions(3, null, "v1"), IdentityBackendSelection.Explicit("other"))
        assertNull(model.state.enrollment); assertTrue(model.state.busy)
        bridge.identificationCallback!!.onFailure(IdentityBridgeFailure.Rejected(IdentityErrorCode.THRESHOLD_UNVERIFIED))
        assertEquals(IdentityBridgeFailure.Rejected(IdentityErrorCode.THRESHOLD_UNVERIFIED), model.state.error)
        assertNull(model.state.identification)
    }
    @Test fun livenessConsumesEventsAndAcceptsOneTerminalPerSession() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.openLiveness(grant, livenessOptions)
        val first = FakeSession(); bridge.livenessCallback!!.onSuccess(first)
        assertEquals(1, first.eventCallbacks.size)
        first.eventCallbacks[0].onSuccess(IdentityLivenessEvent.FrameAccepted)
        assertEquals(2, first.eventCallbacks.size)
        first.eventCallbacks[1].onSuccess(IdentityLivenessEvent.Cancelled)
        first.eventCallbacks[1].onSuccess(IdentityLivenessEvent.Failed(IdentityErrorCode.SESSION_TIMEOUT))
        assertEquals(IdentityLivenessEvent.Cancelled, model.state.liveness)
        assertFalse(model.state.busy)
        model.openLiveness(grant, livenessOptions)
        val second = FakeSession(); bridge.livenessCallback!!.onSuccess(second)
        second.eventCallbacks[0].onSuccess(IdentityLivenessEvent.Failed(IdentityErrorCode.AUTHORIZATION_REVOKED))
        assertEquals(IdentityLivenessEvent.Failed(IdentityErrorCode.AUTHORIZATION_REVOKED), model.state.liveness)
        first.eventCallbacks[1].onSuccess(IdentityLivenessEvent.Cancelled)
        assertEquals(IdentityLivenessEvent.Failed(IdentityErrorCode.AUTHORIZATION_REVOKED), model.state.liveness)
    }
    @Test fun livenessChallengeStaysBoundToSessionAcrossFrameEvents() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.openLiveness(grant, livenessOptions)
        val session = FakeSession(); bridge.livenessCallback!!.onSuccess(session)
        val challenge = IdentityLivenessChallenge("native-canonical-challenge")
        session.eventCallbacks[0].onSuccess(IdentityLivenessEvent.ChallengeIssued(challenge))
        session.eventCallbacks[1].onSuccess(IdentityLivenessEvent.FrameAccepted)
        assertEquals(challenge, model.livenessChallenge)
        model.openLiveness(grant, livenessOptions)
        assertNull(model.livenessChallenge)
        model.cancel()
        assertNull(model.livenessChallenge)
    }
    @Test fun terminalLivenessReleasesChallengeAndRejectsDelayedHostFrames() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.openLiveness(grant, livenessOptions)
        val session = FakeSession(); bridge.livenessCallback!!.onSuccess(session)
        val capturedFor = model.livenessSessionId!!
        session.eventCallbacks[0].onSuccess(IdentityLivenessEvent.ChallengeIssued(IdentityLivenessChallenge("issued")))
        session.eventCallbacks[1].onSuccess(IdentityLivenessEvent.Failed(IdentityErrorCode.SESSION_TIMEOUT))
        assertNull(model.livenessSessionId)
        assertNull(model.livenessChallenge)
        assertEquals(1, session.closes)
        val frame = IdentityLivenessFrame(byteArrayOf(1), 1, "frame-v1", null)
        assertFalse(model.submitLivenessFrame(frame, capturedFor))
        assertFalse(model.submitLivenessFrame(frame))
        assertEquals(0, session.submittedFrames)
    }
    @Test fun delayedHostFrameCannotEnterAReplacementLivenessSession() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.openLiveness(grant, livenessOptions)
        bridge.livenessCallback!!.onSuccess(FakeSession("first"))
        val capturedFor = model.livenessSessionId!!
        model.openLiveness(grant, livenessOptions)
        val replacement = FakeSession("replacement"); bridge.livenessCallback!!.onSuccess(replacement)
        val frame = IdentityLivenessFrame(byteArrayOf(1), 1, "frame-v1", null)
        assertFalse(model.submitLivenessFrame(frame, capturedFor))
        assertEquals(0, replacement.submittedFrames)
        assertTrue(model.submitLivenessFrame(frame, "replacement"))
        assertEquals(1, replacement.submittedFrames)
    }
    @Test fun immediateLivenessEventsAreDrainedWithoutRecursiveStackGrowth() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.openLiveness(grant, livenessOptions)
        val session = FakeSession().apply { immediateEvents = 10000 }
        bridge.livenessCallback!!.onSuccess(session)
        assertEquals(10001, session.eventCallbacks.size)
        assertTrue(model.state.busy)
        session.eventCallbacks.last().onSuccess(IdentityLivenessEvent.Cancelled)
        assertFalse(model.state.busy)
    }
    @Test fun lateSessionAfterCancellationIsClosedWithoutStartingIt() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.openLiveness(grant, livenessOptions)
        model.cancel()
        val session = FakeSession(); bridge.livenessCallback!!.onSuccess(session)
        assertEquals(1, session.closes); assertTrue(session.eventCallbacks.isEmpty())
        assertEquals(IdentityBridgeFailure.Cancelled, model.state.error)
    }
    @Test fun cancellingAndClosingReleaseSessionAndRejectLateCallbacks() {
        val bridge = FakeIdentityBridge(); val model = IdentityViewModel(bridge)
        model.openLiveness(grant, livenessOptions)
        val session = FakeSession(); bridge.livenessCallback!!.onSuccess(session)
        model.close()
        assertEquals(1, session.closes)
        assertFalse(model.state.busy)
        model.verify(probe, template, grant, options)
        assertTrue(bridge.callbacks.isEmpty())
    }
}
