import Foundation

/// Internal request facts. Candidate ownership is resolved from IdentityStore,
/// never supplied by a builder or decoded reference.
struct IdentityGalleryMember: Sendable, Equatable {
    let ref: TemplateRef
    let grant: AuthorizationGrant
}

/// Process-local admission spanning every domain in a fixed gallery snapshot.
/// This does not issue a public reference or expose template vectors.
final class IdentityGalleryOperation: Sendable {
    let id = UUID()
    let grant: AuthorizationGrant
    let members: [IdentityGalleryMember]
    let embeddingSpace: EmbeddingSpaceId
    let stopSignal = IdentityStopSignal()

    init(grant: AuthorizationGrant, members: [IdentityGalleryMember], embeddingSpace: EmbeddingSpaceId) {
        self.grant = grant; self.members = members; self.embeddingSpace = embeddingSpace
    }
    func uses(domain: String, before epoch: Int64) -> Bool {
        ([grant] + members.map(\.grant)).contains { $0.revocationDomainId == domain && $0.authorizationEpoch < epoch }
    }
    func uses(templates: Set<String>) -> Bool {
        members.contains { templates.contains($0.ref.id) }
    }
}
