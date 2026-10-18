import Foundation

/// Persistent candidate admission and logical revocation. Physical purge is a later lifecycle slice.
public actor IdentityStore {
    public nonisolated let storeId: String
    private let journal: IdentityJournal
    private let provider: any IdentityAuthorizationProvider
    private let wallClock: any IdentityWallClock
    private let monotonicClock: any IdentityMonotonicClock
    private var authorization: IdentityAuthorizationState
    private var candidates: [String: String]
    private var revocations: [String: IdentityRevocation]
    private var closed = false
    private var writerReleased = false
    private struct Operation {
        let ticket: IdentityOperationTicket
        var terminal: IdentityStopReason?
    }
    private var operations: [UUID: Operation] = [:]
    private var drainWaiters: [CheckedContinuation<Void, Never>] = []

    public init(root: URL, purpose: String, authorizationProvider: any IdentityAuthorizationProvider,
                keyProvider: any IdentityKeyProvider, wallClock: any IdentityWallClock,
                monotonicClock: any IdentityMonotonicClock) throws {
        let initial = try IdentityAuthorizationState(purpose: purpose)
        let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keyProvider)
        do {
            if let bytes = try journal.read() {
                guard let json = String(data: bytes, encoding: .utf8) else { throw IdentityError(.invalidInput) }
                let legacyKeys: Set<String> = ["version", "storeId", "authorization", "candidates"]
                let fields: [String: Any]
                if let legacy = try? identityObject(json, keys: legacyKeys), try identityInteger(legacy, "version") == 1 {
                    fields = legacy
                } else {
                    fields = try identityObject(json, keys: legacyKeys.union(["revocations"]))
                    guard try identityInteger(fields, "version") == 2 else { throw IdentityError(.invalidInput) }
                }
                guard let candidates = fields["candidates"] as? [String: String] else { throw IdentityError(.invalidInput) }
                let id = try identityString(fields, "storeId"); try identityToken(id)
                let auth = try IdentityAuthorizationState.restore(identityString(fields, "authorization"), purpose: purpose)
                for (candidate, grantId) in candidates {
                    try identityToken(candidate); try identityToken(grantId)
                    guard auth.recordedGrant(grantId) != nil else { throw IdentityError(.invalidInput) }
                }
                var revocations: [String: IdentityRevocation] = [:]
                if fields["revocations"] != nil {
                    guard let records = fields["revocations"] as? [String: [String: Any]] else { throw IdentityError(.invalidInput) }
                    for (id, value) in records { revocations[id] = try IdentityRevocation.restore(value) }
                }
                try IdentityRevocation.validate(revocations, authorization: auth, candidates: candidates)
                self.storeId = id; self.authorization = auth; self.candidates = candidates; self.revocations = revocations
            } else {
                let id = UUID().uuidString.lowercased()
                try journal.commit(Self.encode(storeId: id, authorization: initial, candidates: [:], revocations: [:]))
                self.storeId = id; self.authorization = initial; self.candidates = [:]; self.revocations = [:]
            }
        } catch { try? journal.close(); throw error }
        self.journal = journal; self.provider = authorizationProvider
        self.wallClock = wallClock; self.monotonicClock = monotonicClock
    }

    public func createCandidate(_ grant: AuthorizationGrant) async throws -> CandidateRef {
        guard !closed else { throw IdentityError(.closed) }
        let auth = try authorization.admitting(grant, scope: .enrollment, provider: provider, nowMillis: wallClock.nowMillis)
        let ref = try CandidateRef(id: UUID().uuidString.lowercased(), storeId: storeId)
        var next = candidates; next[ref.id] = grant.grantId
        let bytes = try Self.encode(storeId: storeId, authorization: auth, candidates: next, revocations: revocations)
        _ = try auth.admitting(grant, scope: .enrollment, provider: provider, nowMillis: wallClock.nowMillis)
        do { try journal.commit(bytes) }
        catch { closed = true; throw error }
        authorization = auth; candidates = next
        let signals = invalidateOperations(domain: grant.revocationDomainId, before: auth.highestEpoch(domain: grant.revocationDomainId))
        await Self.stop(signals, reason: .authorizationRevoked)
        return ref
    }

    func recordedCandidateGrant(_ ref: CandidateRef) throws -> AuthorizationGrant {
        guard !closed else { throw IdentityError(.closed) }
        guard ref.storeId == storeId, let grantId = candidates[ref.id],
              let grant = authorization.recordedGrant(grantId) else { throw IdentityError(.candidateNotFound) }
        guard grant.authorizationEpoch == authorization.highestEpoch(domain: grant.revocationDomainId),
              !revocations.values.contains(where: { $0.candidateIds.contains(ref.id) }) else { throw IdentityError(.candidateRevoked) }
        return grant
    }

    public func revokeGrant(_ grantId: String, ownership: OwnershipAuthorization) async throws -> RevocationReceipt {
        guard !closed else { throw IdentityError(.closed) }
        try identityToken(grantId)
        try IdentityAuthorizationState.requireOwnership(ownership, storeId: storeId, provider: provider, nowMillis: wallClock.nowMillis)
        if let existing = revocations.first(where: { $0.value.grantIds.contains(grantId) }) {
            return try RevocationReceipt(id: existing.key, storeId: storeId)
        }
        guard let grant = authorization.recordedGrant(grantId) else { throw IdentityError(.authorizationRequired) }
        let nextAuthorization = try authorization.revoking(grantId: grantId)
        let epoch = nextAuthorization.highestEpoch(domain: grant.revocationDomainId)
        let alreadyCovered = revocations.values.reduce(into: Set<String>()) { $0.formUnion($1.grantIds) }
        let targets = nextAuthorization.recordedGrantIDs(domain: grant.revocationDomainId, before: epoch).subtracting(alreadyCovered)
        let receipt = try RevocationReceipt(id: UUID().uuidString.lowercased(), storeId: storeId)
        let record = IdentityRevocation(domainId: grant.revocationDomainId, epoch: epoch, grantIds: targets,
                                        candidateIds: Set(candidates.filter { targets.contains($0.value) }.keys))
        var next = revocations; next[receipt.id] = record
        let bytes = try Self.encode(storeId: storeId, authorization: nextAuthorization, candidates: candidates, revocations: next)
        do { try journal.commit(bytes) }
        catch { closed = true; throw error }
        authorization = nextAuthorization; revocations = next
        let signals = invalidateOperations(domain: grant.revocationDomainId, before: epoch)
        await Self.stop(signals, reason: .authorizationRevoked)
        return receipt
    }

    private func invalidateOperations(domain: String, before epoch: Int64) -> [IdentityStopSignal] {
        var signals: [IdentityStopSignal] = []
        for (id, operation) in operations where operation.ticket.grant.revocationDomainId == domain &&
            operation.ticket.grant.authorizationEpoch < epoch {
            operations[id]?.terminal = .authorizationRevoked
            signals.append(operation.ticket.stopSignal)
        }
        return signals
    }

    func beginOperation(_ candidate: CandidateRef, grant: AuthorizationGrant, scope: IdentityScope) throws -> IdentityOperationTicket {
        let membership = try recordedCandidateGrant(candidate)
        guard membership.revocationDomainId == grant.revocationDomainId, membership.purpose == grant.purpose,
              membership.authorizationEpoch == grant.authorizationEpoch else { throw IdentityError(.authorizationRequired) }
        let next = try authorization.admitting(grant, scope: scope, provider: provider, nowMillis: wallClock.nowMillis)
        if next != authorization {
            let bytes = try Self.encode(storeId: storeId, authorization: next, candidates: candidates, revocations: revocations)
            _ = try next.admitting(grant, scope: scope, provider: provider, nowMillis: wallClock.nowMillis)
            do { try journal.commit(bytes) } catch { closed = true; throw error }
            authorization = next
        }
        let ticket = IdentityOperationTicket(candidate: candidate, grant: grant, scope: scope)
        operations[ticket.id] = Operation(ticket: ticket)
        return ticket
    }

    /// Consumes the ticket even on failure. Template publication will use this same gate.
    func finishOperation(_ ticket: IdentityOperationTicket, backendError: (any Error)? = nil) throws {
        guard let operation = operations[ticket.id], operation.ticket === ticket else { throw IdentityError(.invalidInput) }
        defer {
            operations.removeValue(forKey: ticket.id)
            if operations.isEmpty {
                let waiters = drainWaiters; drainWaiters.removeAll()
                for waiter in waiters { waiter.resume() }
            }
        }
        if operation.terminal == .authorizationRevoked { throw IdentityError(.authorizationRevoked) }
        guard !closed else { throw IdentityError(.closed) }
        try ticket.stopSignal.check()
        _ = try recordedCandidateGrant(ticket.candidate)
        _ = try authorization.admitting(ticket.grant, scope: ticket.scope, provider: provider, nowMillis: wallClock.nowMillis)
        if let backendError { throw backendError }
    }

    public func close() async throws {
        guard !writerReleased else { return }
        closed = true
        var revoked: [IdentityStopSignal] = []; var closing: [IdentityStopSignal] = []
        for (id, operation) in operations {
            if operation.terminal == .authorizationRevoked { revoked.append(operation.ticket.stopSignal) }
            else { operations[id]?.terminal = .closed; closing.append(operation.ticket.stopSignal) }
        }
        await Self.stop(revoked, reason: .authorizationRevoked)
        await Self.stop(closing, reason: .closed)
        if !operations.isEmpty { await withCheckedContinuation { drainWaiters.append($0) } }
        guard !writerReleased else { return }
        try journal.close(); writerReleased = true
    }

    private nonisolated static func stop(_ signals: [IdentityStopSignal], reason: IdentityStopReason) async {
        guard !signals.isEmpty else { return }
        await Task.detached { for signal in signals { signal.requestStop(reason) } }.value
    }

    private static func encode(storeId: String, authorization: IdentityAuthorizationState,
                               candidates: [String: String], revocations: [String: IdentityRevocation]) throws -> Data {
        let json = identityJSON(["version": 2, "storeId": storeId,
                                 "authorization": try authorization.canonicalJSON(), "candidates": candidates,
                                 "revocations": revocations.mapValues(\.fields)])
        guard json.utf8.count <= 65_536 else { throw IdentityError(.invalidInput) }
        return Data(json.utf8)
    }
}
