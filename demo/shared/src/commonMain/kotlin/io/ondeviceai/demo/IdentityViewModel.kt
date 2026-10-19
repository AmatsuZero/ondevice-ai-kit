package io.ondeviceai.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ondeviceai.binding.*

/** Supplied by the host; optional capability policies never gain synthetic defaults. */
data class IdentityDemoConfiguration(
    val grant: IdentityAuthorizationGrant,
    val candidate: IdentityCandidateReference? = null,
    val template: IdentityTemplateReference? = null,
    val gallery: IdentityGalleryReference? = null,
    val enrollmentOptions: IdentityEnrollmentOptions? = null,
    val verificationOptions: IdentityVerificationOptions? = null,
    val identificationOptions: IdentityIdentificationOptions? = null,
    val livenessOptions: IdentityLivenessOptions? = null
)

data class IdentityViewState(
    val busy: Boolean = false,
    val enrollment: IdentityEnrollmentResult? = null,
    val verification: IdentityVerificationResult? = null,
    val identification: IdentityIdentificationResult? = null,
    val liveness: IdentityLivenessEvent? = null,
    val error: IdentityBridgeFailure? = null
)

/** Demo state keeps identity operations separate from detection and never exposes subject IDs. */
class IdentityViewModel(private val bridge: IdentityBridge) {
    var state by mutableStateOf(IdentityViewState()); private set
    private var generation = 0L
    private var active: IdentityBridgeTask? = null
    private var session: IdentityLivenessSession? = null
    private var closed = false
    private var configuration: IdentityDemoConfiguration? = null
    private var selectedProbe: IdentityProbeReference? = null
    var enrolledTemplate by mutableStateOf<IdentityTemplateReference?>(null)
        private set
    var configured by mutableStateOf(false)
        private set
    var inputReady by mutableStateOf(false)
        private set
    private var livenessTerminalDelivered = false
    private class EventPump(val generation: Long, val session: IdentityLivenessSession) {
        var running = false
        var again = false
    }

    fun availability(): IdentityBridgeAvailability = bridge.availability()

    fun verify(probe: IdentityProbeReference, template: IdentityTemplateReference,
        grant: IdentityAuthorizationGrant, options: IdentityVerificationOptions,
        backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        if (closed) return
        val current = beginOperation()
        state = IdentityViewState(busy = true)
        var terminalDelivered = false
        val task = bridge.verify(probe, template, grant, options, backend, object : IdentityCallback<IdentityVerificationResult> {
            override fun onSuccess(value: IdentityVerificationResult) {
                if (current != generation || terminalDelivered) return
                terminalDelivered = true; active = null; state = IdentityViewState(verification = value)
            }
            override fun onFailure(error: IdentityBridgeFailure) {
                if (current != generation || terminalDelivered) return
                terminalDelivered = true; active = null; state = IdentityViewState(error = error)
            }
        })
        if (current == generation && state.busy && !terminalDelivered) active = task
    }

    fun enroll(probe: IdentityProbeReference, candidate: IdentityCandidateReference,
        grant: IdentityAuthorizationGrant, options: IdentityEnrollmentOptions,
        backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        if (closed) return
        val current = beginOperation()
        state = IdentityViewState(busy = true)
        var terminalDelivered = false
        val task = bridge.enroll(probe, candidate, grant, options, backend, object : IdentityCallback<IdentityEnrollmentResult> {
            override fun onSuccess(value: IdentityEnrollmentResult) {
                if (current != generation || terminalDelivered) return
                terminalDelivered = true; active = null; enrolledTemplate = value.template; state = IdentityViewState(enrollment = value)
            }
            override fun onFailure(error: IdentityBridgeFailure) {
                if (current != generation || terminalDelivered) return
                terminalDelivered = true; active = null; state = IdentityViewState(error = error)
            }
        })
        if (current == generation && state.busy && !terminalDelivered) active = task
    }

    fun identify(probe: IdentityProbeReference, gallery: IdentityGalleryReference,
        grant: IdentityAuthorizationGrant, options: IdentityIdentificationOptions,
        backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        if (closed) return
        val current = beginOperation()
        state = IdentityViewState(busy = true)
        var terminalDelivered = false
        val task = bridge.identify(probe, gallery, grant, options, backend, object : IdentityCallback<IdentityIdentificationResult> {
            override fun onSuccess(value: IdentityIdentificationResult) {
                if (current != generation || terminalDelivered) return
                terminalDelivered = true; active = null; state = IdentityViewState(identification = value)
            }
            override fun onFailure(error: IdentityBridgeFailure) {
                if (current != generation || terminalDelivered) return
                terminalDelivered = true; active = null; state = IdentityViewState(error = error)
            }
        })
        if (current == generation && state.busy && !terminalDelivered) active = task
    }

    fun openLiveness(grant: IdentityAuthorizationGrant, options: IdentityLivenessOptions,
        backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        if (closed) return
        val current = beginOperation(); livenessTerminalDelivered = false
        state = IdentityViewState(busy = true)
        var terminalDelivered = false
        val task = bridge.openLivenessSession(grant, options, backend, object : IdentityCallback<IdentityLivenessSession> {
            override fun onSuccess(value: IdentityLivenessSession) {
                if (current != generation || terminalDelivered) { if (value !== session) value.close(); return }
                terminalDelivered = true; session = value
                val openedSession = value
                var startDelivered = false
                val startTask = value.start(object : IdentityCallback<Unit> {
                    override fun onSuccess(value: Unit) {
                        if (current != generation || startDelivered) return
                        startDelivered = true
                        state = state.copy(busy = true, liveness = IdentityLivenessEvent.Started)
                        requestEvent(EventPump(current, openedSession))
                    }
                    override fun onFailure(error: IdentityBridgeFailure) {
                        if (current != generation || startDelivered) return
                        startDelivered = true
                        acceptLivenessTerminal(current, IdentityLivenessEvent.Failed((error as? IdentityBridgeFailure.Rejected)?.code ?: IdentityErrorCode.BACKEND_UNAVAILABLE))
                    }
                })
                if (current == generation && !startDelivered) active = startTask
            }
            override fun onFailure(error: IdentityBridgeFailure) {
                if (current != generation || terminalDelivered) return
                terminalDelivered = true; active = null; state = IdentityViewState(error = error)
            }
        })
        if (current == generation && state.busy && !terminalDelivered) active = task
    }

    fun configure(value: IdentityDemoConfiguration?) {
        if (closed) return
        beginOperation()
        configuration = value
        configured = value != null
        selectedProbe = null
        inputReady = false
        enrolledTemplate = null
        state = IdentityViewState()
    }

    fun selectProbe(value: IdentityProbeReference?) {
        if (closed) return
        beginOperation()
        selectedProbe = value
        inputReady = value != null
        state = IdentityViewState()
    }

    fun enrollConfigured(backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        val value = requireConfiguration() ?: return
        val probe = requireProbe() ?: return
        val candidate = value.candidate ?: run { reject(IdentityErrorCode.CANDIDATE_NOT_FOUND); return }
        val options = value.enrollmentOptions ?: run { reject(IdentityErrorCode.UNSUPPORTED_OPTION); return }
        enroll(probe, candidate, value.grant, options, backend)
    }

    fun verifyConfigured(backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        val value = requireConfiguration() ?: return
        val probe = requireProbe() ?: return
        val template = value.template ?: enrolledTemplate
        if (template == null) { reject(IdentityErrorCode.TEMPLATE_NOT_FOUND); return }
        val options = value.verificationOptions ?: run { reject(IdentityErrorCode.UNSUPPORTED_OPTION); return }
        verify(probe, template, value.grant, options, backend)
    }

    fun identifyConfigured(backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        val value = requireConfiguration() ?: return
        val probe = requireProbe() ?: return
        val gallery = value.gallery
        if (gallery == null) { reject(IdentityErrorCode.GALLERY_NOT_FOUND); return }
        val options = value.identificationOptions ?: run { reject(IdentityErrorCode.UNSUPPORTED_OPTION); return }
        identify(probe, gallery, value.grant, options, backend)
    }

    fun openLivenessConfigured(backend: IdentityBackendSelection = IdentityBackendSelection.Default) {
        val value = requireConfiguration() ?: return
        val options = value.livenessOptions ?: run { reject(IdentityErrorCode.UNSUPPORTED_OPTION); return }
        openLiveness(value.grant, options, backend)
    }

    fun submitLivenessFrame(frame: IdentityLivenessFrame): Boolean = session?.submitFrame(frame) ?: false
    fun finishLiveness() { session?.finish() }
    fun cancel() {
        generation++; active?.cancel(); active = null
        val previous = session; session = null; previous?.cancel(); previous?.close()
        state = state.copy(busy = false, error = if (closed) state.error else IdentityBridgeFailure.Cancelled)
    }
    fun close() { if (!closed) { closed = true; cancel(); bridge.close() } }

    private fun beginOperation(): Long { generation++; active?.cancel(); active = null; session?.close(); session = null; return generation }
    private fun requireConfiguration(): IdentityDemoConfiguration? {
        if (closed) return null
        val value = configuration
        if (value == null) { beginOperation(); state = IdentityViewState(error = IdentityBridgeFailure.Unavailable(IdentityAvailabilityReason.AUTHORIZATION_REQUIRED)); return null }
        return value
    }
    private fun requireProbe(): IdentityProbeReference? {
        val value = selectedProbe
        if (value == null) { reject(IdentityErrorCode.INVALID_INPUT); return null }
        return value
    }
    private fun reject(code: IdentityErrorCode) { beginOperation(); state = IdentityViewState(error = IdentityBridgeFailure.Rejected(code)) }
    private fun acceptLivenessTerminal(current: Long, event: IdentityLivenessEvent) {
        if (current != generation || livenessTerminalDelivered) return
        if (event is IdentityLivenessEvent.Completed || event is IdentityLivenessEvent.Failed || event === IdentityLivenessEvent.Cancelled || event === IdentityLivenessEvent.Closed) {
            livenessTerminalDelivered = true; active = null; state = state.copy(busy = false, liveness = event)
        } else state = state.copy(liveness = event)
    }

    // A native bridge may deliver immediately. Trampoline queued events instead of recursing.
    private fun requestEvent(pump: EventPump) {
        if (pump.running) { pump.again = true; return }
        pump.running = true
        try {
            do {
                pump.again = false
                if (pump.generation != generation || livenessTerminalDelivered || session !== pump.session) return
                var delivered = false
                val task = pump.session.nextEvent(object : IdentityCallback<IdentityLivenessEvent?> {
                    override fun onSuccess(value: IdentityLivenessEvent?) {
                        if (delivered || pump.generation != generation || livenessTerminalDelivered) return
                        delivered = true; active = null
                        acceptLivenessTerminal(pump.generation, value ?: IdentityLivenessEvent.Closed)
                        if (!livenessTerminalDelivered) requestEvent(pump)
                    }
                    override fun onFailure(error: IdentityBridgeFailure) {
                        if (delivered || pump.generation != generation || livenessTerminalDelivered) return
                        delivered = true; active = null
                        acceptLivenessTerminal(pump.generation, IdentityLivenessEvent.Failed((error as? IdentityBridgeFailure.Rejected)?.code ?: IdentityErrorCode.BACKEND_UNAVAILABLE))
                        state = state.copy(error = error)
                    }
                })
                if (!delivered && pump.generation == generation && !livenessTerminalDelivered) active = task
            } while (pump.again)
        } finally { pump.running = false }
    }
}
