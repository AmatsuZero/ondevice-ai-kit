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
    override fun availability() = IdentityBridgeAvailability.Available("fixture.identity")
    override fun verify(probe: IdentityProbeReference, template: IdentityTemplateReference,
        grant: IdentityAuthorizationGrant, options: IdentityVerificationOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityVerificationResult>): IdentityBridgeTask {
        selections += backend; callbacks += callback
        if (completeSynchronously) callback.onSuccess(verification())
        return IdentityTask { cancellations++; callback.onFailure(IdentityBridgeFailure.Cancelled) }
    }
    override fun enroll(probe: IdentityProbeReference, candidate: IdentityCandidateReference,
        grant: IdentityAuthorizationGrant, options: IdentityEnrollmentOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityEnrollmentResult>): IdentityBridgeTask {
        selections += backend; enrollmentCallback = callback; return IdentityTask()
    }
    override fun identify(probe: IdentityProbeReference, gallery: IdentityGalleryReference,
        grant: IdentityAuthorizationGrant, options: IdentityIdentificationOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityIdentificationResult>): IdentityBridgeTask {
        selections += backend; identificationCallback = callback; return IdentityTask()
    }
    override fun openLivenessSession(grant: IdentityAuthorizationGrant, options: IdentityLivenessOptions,
        backend: IdentityBackendSelection, callback: IdentityCallback<IdentityLivenessSession>): IdentityBridgeTask {
        selections += backend; livenessCallback = callback; return IdentityTask()
    }
}
private class FakeSession : IdentityLivenessSession {
    override val sessionId = "session"
    val eventCallbacks = mutableListOf<IdentityCallback<IdentityLivenessEvent?>>()
    var closes = 0
    var immediateEvents = 0
    override fun start(callback: IdentityCallback<Unit>): IdentityBridgeTask { callback.onSuccess(Unit); return IdentityTask() }
    override fun submitFrame(frame: IdentityLivenessFrame) = true
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
