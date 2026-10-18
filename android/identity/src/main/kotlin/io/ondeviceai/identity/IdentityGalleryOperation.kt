package io.ondeviceai.identity

import java.util.UUID
import java.util.Collections

/**
 * The identity facts a gallery publication needs from [IdentityStore].  This
 * deliberately carries an identification grant per member: the enrollment
 * grant recorded on a template never implicitly authorizes identification.
 */
internal data class IdentityGalleryMember(
    val ref: TemplateRef,
    val grant: AuthorizationGrant
)

/** Process-local admission for one gallery publication; it is consumed once. */
internal class IdentityGalleryOperationTicket(
    val operationGrant: AuthorizationGrant,
    members: List<IdentityGalleryMember>,
    val embeddingSpace: EmbeddingSpaceId
) {
    val members: List<IdentityGalleryMember> = Collections.unmodifiableList(members.toList())
    val id: UUID = UUID.randomUUID()
    val stopSignal = IdentityStopSignal()
}
