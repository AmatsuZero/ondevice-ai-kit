import Foundation

/// Persistent candidate admission. Enrollment and deletion are subsequent lifecycle slices.
public actor IdentityStore {
    public nonisolated let storeId: String
    private let journal: IdentityJournal
    private let provider: any IdentityAuthorizationProvider
    private let wallClock: any IdentityWallClock
    private let monotonicClock: any IdentityMonotonicClock
    private var authorization: IdentityAuthorizationState
    private var candidates: [String: String]
    private var closed = false

    public init(root: URL, purpose: String, authorizationProvider: any IdentityAuthorizationProvider,
                keyProvider: any IdentityKeyProvider, wallClock: any IdentityWallClock,
                monotonicClock: any IdentityMonotonicClock) throws {
        let initial = try IdentityAuthorizationState(purpose: purpose)
        let journal = try IdentityJournal(root: root, recordID: "store", keyProvider: keyProvider)
        do {
            if let bytes = try journal.read() {
                guard let json = String(data: bytes, encoding: .utf8) else { throw IdentityError(.invalidInput) }
                let fields = try identityObject(json, keys: ["version", "storeId", "authorization", "candidates"])
                guard try identityInteger(fields, "version") == 1,
                      let candidates = fields["candidates"] as? [String: String] else { throw IdentityError(.invalidInput) }
                let id = try identityString(fields, "storeId"); try identityToken(id)
                let auth = try IdentityAuthorizationState.restore(identityString(fields, "authorization"), purpose: purpose)
                for (candidate, grantId) in candidates {
                    try identityToken(candidate); try identityToken(grantId)
                    guard auth.recordedGrant(grantId) != nil else { throw IdentityError(.invalidInput) }
                }
                self.storeId = id; self.authorization = auth; self.candidates = candidates
            } else {
                let id = UUID().uuidString.lowercased()
                try journal.commit(Self.encode(storeId: id, authorization: initial, candidates: [:]))
                self.storeId = id; self.authorization = initial; self.candidates = [:]
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
        let bytes = try Self.encode(storeId: storeId, authorization: auth, candidates: next)
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
        return grant
    }

    public func close() throws {
        guard !closed else { return }
        closed = true; try journal.close()
    }

    private static func encode(storeId: String, authorization: IdentityAuthorizationState,
                               candidates: [String: String]) throws -> Data {
        let json = identityJSON(["version": 1, "storeId": storeId,
                                 "authorization": try authorization.canonicalJSON(), "candidates": candidates])
        guard json.utf8.count <= 65_536 else { throw IdentityError(.invalidInput) }
        return Data(json.utf8)
    }
}
