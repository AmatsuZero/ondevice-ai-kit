package io.ondeviceai.identity

import java.util.UUID

/** Process-local admission only. Store identity and membership are checked again at completion. */
internal class IdentityOperationTicket(
    val candidate: CandidateRef,
    val grant: AuthorizationGrant,
    val scope: IdentityScope,
    /** Null means candidate-wide; otherwise only intersecting template IDs invalidate this ticket. */
    val templateTargets: Set<String>? = null
) {
    val id: UUID = UUID.randomUUID()
    val stopSignal = IdentityStopSignal()
}
