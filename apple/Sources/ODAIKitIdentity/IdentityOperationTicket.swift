import Foundation

/// Process-local admission pinned to an immutable grant and candidate membership.
/// Only IdentityStore may validate completion; this is never a public result.
final class IdentityOperationTicket: Sendable {
    let id = UUID()
    let candidate: CandidateRef
    let grant: AuthorizationGrant
    let scope: IdentityScope
    /// Nil means the operation is candidate-wide; otherwise the ticket is
    /// invalidated only when one of these exact template IDs is deleted.
    let templateTargets: Set<String>?
    let stopSignal = IdentityStopSignal()

    init(candidate: CandidateRef, grant: AuthorizationGrant, scope: IdentityScope, templateTargets: Set<String>? = nil) {
        self.candidate = candidate; self.grant = grant; self.scope = scope; self.templateTargets = templateTargets
    }
}
