import Foundation

/// Process-local admission pinned to an immutable grant and candidate membership.
/// Only IdentityStore may validate completion; this is never a public result.
final class IdentityOperationTicket: Sendable {
    let id = UUID()
    let candidate: CandidateRef
    let grant: AuthorizationGrant
    let scope: IdentityScope
    let stopSignal = IdentityStopSignal()

    init(candidate: CandidateRef, grant: AuthorizationGrant, scope: IdentityScope) {
        self.candidate = candidate; self.grant = grant; self.scope = scope
    }
}
