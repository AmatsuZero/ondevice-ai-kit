import Foundation

/// Process-local standalone PAD authorization. It carries no candidate, template,
/// gallery or embedding-space dependency, and can only be consumed by its store.
final class IdentityLivenessOperation: Sendable {
    let id = UUID()
    let grant: AuthorizationGrant
    let stopSignal = IdentityStopSignal()
    init(grant: AuthorizationGrant) { self.grant = grant }
}
