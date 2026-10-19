package io.ondeviceai.identity

import java.util.UUID

/** Standalone PAD admission with no candidate, template, gallery or embedding-space dependency. */
internal class IdentityLivenessOperation(val grant: AuthorizationGrant) {
    val id: UUID = UUID.randomUUID()
    val stopSignal = IdentityStopSignal()
}
