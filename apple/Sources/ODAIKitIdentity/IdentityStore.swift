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

    public func createCandidate(_ grant: AuthorizationGrant) throws -> CandidateRef {
        guard !closed else { throw IdentityError(.closed) }
        let auth = try authorization.admitting(grant, scope: .enrollment, provider: provider, nowMillis: wallClock.nowMillis)
        let ref = try CandidateRef(id: UUID().uuidString.lowercased(), storeId: storeId)
        var next = candidates; next[ref.id] = grant.grantId
        let bytes = try Self.encode(storeId: storeId, authorization: auth, candidates: next, revocations: revocations)
        _ = try auth.admitting(grant, scope: .enrollment, provider: provider, nowMillis: wallClock.nowMillis)
        do { try journal.commit(bytes) }
        catch { closed = true; throw error }
        authorization = auth; candidates = next
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

    public func revokeGrant(_ grantId: String, ownership: OwnershipAuthorization) throws -> RevocationReceipt {
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
        return receipt
    }

    public func close() throws {
        guard !closed else { return }
        closed = true; try journal.close()
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
