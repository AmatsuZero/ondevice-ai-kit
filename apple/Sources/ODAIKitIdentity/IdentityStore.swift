import Foundation

/// Persistent candidate/template admission and logical revocation.
/// Template deletion retains immutable metadata after exact payload/key cleanup.
public actor IdentityStore {
    public nonisolated let storeId: String
    private let journal: IdentityJournal
    private let payload: IdentityTemplatePayload
    private let provider: any IdentityAuthorizationProvider
    private let wallClock: any IdentityWallClock
    private let monotonicClock: any IdentityMonotonicClock
    private var authorization: IdentityAuthorizationState
    private var candidates: [String: String]
    private var revocations: [String: IdentityRevocation]
    private var templates: [String: IdentityTemplateRecord] = [:]
    private var deletions: [String: IdentityTemplateDeletion] = [:]
    private var candidateDeletions: [String: IdentityCandidateDeletion] = [:]
    private var stages: Set<String> = []
    private var templateLeases: [String: Int] = [:]
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
                    if let v2 = try? identityObject(json, keys: legacyKeys.union(["revocations"])), try identityInteger(v2, "version") == 2 {
                        fields = v2
                    } else {
                        if let v3 = try? identityObject(json, keys: legacyKeys.union(["revocations", "templates", "stages"])), try identityInteger(v3, "version") == 3 {
                            fields = v3
                        } else if let v4 = try? identityObject(json, keys: legacyKeys.union(["revocations", "templates", "stages", "deletions"])), try identityInteger(v4, "version") == 4 {
                            fields = v4
                        } else {
                            fields = try identityObject(json, keys: legacyKeys.union(["revocations", "templates", "stages", "deletions", "candidateDeletions"]))
                            guard try identityInteger(fields, "version") == 5 else { throw IdentityError(.invalidInput) }
                        }
                    }
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
                var templates: [String: IdentityTemplateRecord] = [:]
                var stages: Set<String> = []
                if fields["templates"] != nil {
                    guard let records = fields["templates"] as? [String: [String: Any]], let pending = fields["stages"] as? [String],
                          pending.count == Set(pending).count else { throw IdentityError(.invalidInput) }
                    for (recordId, value) in records {
                        let record = try IdentityTemplateRecord.restore(value)
                        guard recordId == record.ref.id, UUID(uuidString: recordId) != nil, recordId == recordId.lowercased(),
                              let candidateGrantId = candidates[record.candidateId], let candidateGrant = auth.recordedGrant(candidateGrantId),
                              let templateGrant = auth.recordedGrant(record.grantId),
                              templateGrant.scope.contains(.enrollment), templateGrant.purpose == candidateGrant.purpose,
                              templateGrant.revocationDomainId == candidateGrant.revocationDomainId,
                              templateGrant.authorizationEpoch == candidateGrant.authorizationEpoch,
                              record.ref.authorizationEpoch == templateGrant.authorizationEpoch else { throw IdentityError(.invalidInput) }
                        templates[recordId] = record
                    }
                    for id in pending {
                        guard UUID(uuidString: id) != nil, id == id.lowercased(), templates[id] == nil else { throw IdentityError(.invalidInput) }
                        stages.insert(id)
                    }
                }
                let version = try identityInteger(fields, "version")
                var deletions: [String: IdentityTemplateDeletion] = [:]
                if version >= 4 {
                    guard let records = fields["deletions"] as? [String: [String: Any]] else { throw IdentityError(.invalidInput) }
                    var receipts = Set<String>()
                    for (templateId, value) in records {
                        let deletion = try IdentityTemplateDeletion.restore(value)
                        guard templates[templateId] != nil, receipts.insert(deletion.receiptId).inserted,
                              revocations[deletion.receiptId] == nil else { throw IdentityError(.invalidInput) }
                        deletions[templateId] = deletion
                    }
                }
                var candidateDeletions: [String: IdentityCandidateDeletion] = [:]
                if version >= 5 {
                    guard let records = fields["candidateDeletions"] as? [String: [String: Any]] else { throw IdentityError(.invalidInput) }
                    var receipts = Set(deletions.values.map(\.receiptId))
                    receipts.formUnion(revocations.keys)
                    for (candidateId, value) in records {
                        let deletion = try IdentityCandidateDeletion.restore(value)
                        guard candidates[candidateId] != nil, receipts.insert(deletion.receiptId).inserted,
                              deletion.templateIds == Set(templates.values.filter { $0.candidateId == candidateId }.map { $0.ref.id }),
                              deletion.templateIds.allSatisfy({ deletions[$0] != nil }) else { throw IdentityError(.invalidInput) }
                        candidateDeletions[candidateId] = deletion
                    }
                }
                // All metadata is validated before the first recovery side effect.
                let payload = try IdentityTemplatePayload(root: root, storeId: id, provider: keyProvider)
                for stage in stages { try payload.remove(recordId: stage) }
                if !stages.isEmpty {
                    try journal.commit(Self.encode(storeId: id, authorization: auth, candidates: candidates, revocations: revocations, templates: templates, deletions: deletions, candidateDeletions: candidateDeletions))
                }
                for (templateId, deletion) in deletions where deletion.status != .purged {
                    let status = Self.removePayload(templateId, payload: payload)
                    var next = deletions; next[templateId] = deletion.withStatus(status)
                    try journal.commit(Self.encode(storeId: id, authorization: auth, candidates: candidates, revocations: revocations, templates: templates, deletions: next, candidateDeletions: candidateDeletions))
                    deletions = next
                }
                self.payload = payload; self.templates = templates; self.deletions = deletions; self.candidateDeletions = candidateDeletions
                self.storeId = id; self.authorization = auth; self.candidates = candidates; self.revocations = revocations
            } else {
                let id = UUID().uuidString.lowercased()
                try journal.commit(Self.encode(storeId: id, authorization: initial, candidates: [:], revocations: [:], candidateDeletions: [:]))
                self.storeId = id; self.authorization = initial; self.candidates = [:]; self.revocations = [:]; self.candidateDeletions = [:]
                self.payload = try IdentityTemplatePayload(root: root, storeId: id, provider: keyProvider)
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
        let bytes = try Self.encode(storeId: storeId, authorization: auth, candidates: next, revocations: revocations, templates: templates, stages: stages, deletions: deletions, candidateDeletions: candidateDeletions)
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
        guard candidateDeletions[ref.id] == nil,
              grant.authorizationEpoch == authorization.highestEpoch(domain: grant.revocationDomainId),
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
        let receipt = try RevocationReceipt(id: newReceiptID(), storeId: storeId)
        let record = IdentityRevocation(domainId: grant.revocationDomainId, epoch: epoch, grantIds: targets,
                                        candidateIds: Set(candidates.filter { targets.contains($0.value) }.keys))
        var next = revocations; next[receipt.id] = record
        let bytes = try Self.encode(storeId: storeId, authorization: nextAuthorization, candidates: candidates, revocations: next, templates: templates, stages: stages, deletions: deletions, candidateDeletions: candidateDeletions)
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
            let bytes = try Self.encode(storeId: storeId, authorization: next, candidates: candidates, revocations: revocations, templates: templates, stages: stages, deletions: deletions, candidateDeletions: candidateDeletions)
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
        try consumeOperation(ticket) { if let backendError { throw backendError } }
    }

    private func consumeOperation<T>(_ ticket: IdentityOperationTicket, publication: () throws -> T) throws -> T {
        guard let operation = operations[ticket.id], operation.ticket === ticket else { throw IdentityError(.invalidInput) }
        defer {
            operations.removeValue(forKey: ticket.id)
            if operations.isEmpty && templateLeases.isEmpty {
                let waiters = drainWaiters; drainWaiters.removeAll()
                for waiter in waiters { waiter.resume() }
            }
        }
        if operation.terminal == .authorizationRevoked { throw IdentityError(.authorizationRevoked) }
        guard !closed else { throw IdentityError(.closed) }
        try ticket.stopSignal.check()
        _ = try recordedCandidateGrant(ticket.candidate)
        _ = try authorization.admitting(ticket.grant, scope: ticket.scope, provider: provider, nowMillis: wallClock.nowMillis)
        return try publication()
    }

    /// The backend runs outside this actor. Completion and template membership
    /// publication share exactly the same serial gate as durable revocation.
    public nonisolated func enroll(_ probe: IdentityProbe, candidate: CandidateRef, grant: AuthorizationGrant,
                                   options: EnrollmentOptions, backend: any IdentityBackend,
                                   stopSignal: IdentityStopSignal? = nil) async throws -> EnrollmentResult {
        let ticket = try await beginOperation(candidate, grant: grant, scope: .enrollment)
        let started = await monotonicNow()
        let registration = stopSignal?.registerStopHandler { ticket.stopSignal.requestStop(.cancelled) }
        defer { registration?.close() }
        return try await withTaskCancellationHandler {
            if Task.isCancelled { ticket.stopSignal.requestStop(.cancelled) }
            let prepared = await Task.detached {
                let binding = IdentityEnrollmentBinding(backend)
                return (binding, Result { try binding.prepare(probe, options: options, backend: backend, signal: ticket.stopSignal) })
            }.value
            do {
                return try await completeEnrollment(ticket, prepared: prepared, probe: probe, options: options, started: started)
            } catch let error as IdentityError {
                throw IdentityError(error.code, backendId: prepared.0.space.backendId, modelBinding: prepared.0.space.modelBinding,
                                    policyId: error.policyId, sessionId: ticket.id.uuidString.lowercased())
            }
        } onCancel: { ticket.stopSignal.requestStop(.cancelled) }
    }

    private func completeEnrollment(_ ticket: IdentityOperationTicket,
                                    prepared: (IdentityEnrollmentBinding, Result<IdentityPreparedEnrollment, Error>),
                                    probe: IdentityProbe, options: EnrollmentOptions,
                                    started: Int64) throws -> EnrollmentResult {
        try consumeOperation(ticket) {
            let binding = prepared.0
            let work: IdentityPreparedEnrollment
            do { work = try prepared.1.get() }
            catch let error as IdentityError { throw error }
            catch { throw IdentityError(.backendUnavailable) }
            var ref: TemplateRef?
            if let embedding = work.embedding {
                let issued = try TemplateRef(id: UUID().uuidString.lowercased(), embeddingSpaceId: binding.space,
                                             authorizationEpoch: ticket.grant.authorizationEpoch, revision: UUID().uuidString.lowercased())
                let pending = stages.union([issued.id])
                do {
                    try journal.commit(Self.encode(storeId: storeId, authorization: authorization, candidates: candidates,
                                                   revocations: revocations, templates: templates, stages: pending, deletions: deletions, candidateDeletions: candidateDeletions))
                    stages = pending
                    try payload.write(recordId: issued.id, embedding: embedding)
                    // Cancellation and expiry may occur during synchronous I/O.
                    try ticket.stopSignal.check()
                    _ = try authorization.admitting(ticket.grant, scope: .enrollment, provider: provider, nowMillis: wallClock.nowMillis)
                    var next = templates
                    next[issued.id] = IdentityTemplateRecord(ref: issued, candidateId: ticket.candidate.id, grantId: ticket.grant.grantId)
                    let remaining = stages.subtracting([issued.id])
                    try journal.commit(Self.encode(storeId: storeId, authorization: authorization, candidates: candidates,
                                                   revocations: revocations, templates: next, stages: remaining, deletions: deletions, candidateDeletions: candidateDeletions))
                    templates = next; stages = remaining; ref = issued
                } catch {
                    // The durable manifest owns any partial file/key. Recovery
                    // retries exact cleanup; an ambiguous writer never publishes.
                    closed = true; throw (error as? IdentityError) ?? IdentityError(.invalidInput)
                }
            }
            let id = ticket.id.uuidString.lowercased()
            return EnrollmentResult(outcome: ref == nil ? .insufficientQuality : .enrolled, templateRef: ref,
                                    embeddingSpace: binding.space, quality: work.quality, backendId: binding.space.backendId,
                                    backendVersion: binding.space.backendVersion, modelBinding: binding.space.modelBinding,
                                    inputRevision: probe.revision, optionsRevision: options.revision, qualityPolicyId: options.qualityPolicyId,
                                    sessionId: id, runId: id, preprocessingRevision: binding.space.preprocessRevision,
                                    durationNanos: Self.elapsed(from: started, to: monotonicClock.nowNanos))
        }
    }

    private func monotonicNow() -> Int64 { monotonicClock.nowNanos }
    private static func elapsed(from start: Int64, to end: Int64) -> Int64 {
        guard end >= start else { return 0 }
        let (value, overflow) = end.subtractingReportingOverflow(start)
        return overflow ? .max : value
    }

    func recordedTemplateCandidate(_ ref: TemplateRef) throws -> CandidateRef {
        guard !closed else { throw IdentityError(.closed) }
        guard let record = templates[ref.id] else { throw IdentityError(.templateNotFound) }
        try ref.validate(against: record.ref)
        guard deletions[ref.id] == nil else { throw IdentityError(.templateRevoked) }
        guard candidateDeletions[record.candidateId] == nil,
              let grant = authorization.recordedGrant(record.grantId),
              grant.authorizationEpoch == authorization.highestEpoch(domain: grant.revocationDomainId),
              !revocations.values.contains(where: { $0.candidateIds.contains(record.candidateId) }) else { throw IdentityError(.templateRevoked) }
        return try CandidateRef(id: record.candidateId, storeId: storeId)
    }

    /// Reads and pins a template vector for a verification or identification
    /// operation. The passed declaration may be a different grant in the same
    /// domain and epoch; the provider remains authoritative at admission.
    func acquireTemplateLease(_ ref: TemplateRef, grant: AuthorizationGrant,
                              scope: IdentityScope, embeddingSpace: EmbeddingSpaceId) throws -> IdentityTemplateLease {
        guard !closed else { throw IdentityError(.closed) }
        guard scope == .verification || scope == .identification else { throw IdentityError(.unsupportedOption) }
        _ = try recordedTemplateCandidate(ref)
        guard let record = templates[ref.id] else { throw IdentityError(.templateNotFound) }
        try ref.validate(against: record.ref)
        guard record.ref.embeddingSpaceId == embeddingSpace else { throw IdentityError(.embeddingSpaceMismatch) }
        let capability: IdentityCapability = scope == .verification ? .verification : .identification
        guard embeddingSpace.supportedCapabilities.contains(capability) else { throw IdentityError(.embeddingSpaceMismatch) }
        guard let storedGrant = authorization.recordedGrant(record.grantId),
              storedGrant.scope.contains(.enrollment),
              storedGrant.revocationDomainId == grant.revocationDomainId,
              storedGrant.purpose == grant.purpose,
              storedGrant.authorizationEpoch == grant.authorizationEpoch else {
            throw IdentityError(.authorizationRequired)
        }
        let next = try authorization.admitting(grant, scope: scope, provider: provider, nowMillis: wallClock.nowMillis)
        let value = try payload.read(recordId: ref.id, space: embeddingSpace)
        // Recheck after decryption so a provider callback cannot make the
        // lease stale before it becomes visible to a consumer.
        _ = try next.admitting(grant, scope: scope, provider: provider, nowMillis: wallClock.nowMillis)
        if next != authorization {
            let bytes = try Self.encode(storeId: storeId, authorization: next, candidates: candidates,
                                        revocations: revocations, templates: templates, stages: stages, deletions: deletions, candidateDeletions: candidateDeletions)
            do { try journal.commit(bytes) } catch { closed = true; throw error }
            authorization = next
            _ = try authorization.admitting(grant, scope: scope, provider: provider, nowMillis: wallClock.nowMillis)
        }
        templateLeases[ref.id, default: 0] += 1
        return IdentityTemplateLease(value: value) {
            await self.releaseTemplateLease(ref.id)
        }
    }

    /// Logically revokes this exact template before physical cleanup. Ownership is
    /// independent of biometric grant validity, including on repeated requests.
    public func deleteTemplate(_ ref: TemplateRef, ownership: OwnershipAuthorization) throws -> DeletionReceipt {
        guard !closed else { throw IdentityError(.closed) }
        try IdentityAuthorizationState.requireOwnership(ownership, storeId: storeId, provider: provider, nowMillis: wallClock.nowMillis)
        guard let record = templates[ref.id] else { throw IdentityError(.templateNotFound) }
        try ref.validate(against: record.ref)
        if let existing = deletions[ref.id] {
            return try DeletionReceipt(id: existing.receiptId, storeId: storeId, templateId: ref.id)
        }
        let receipt = try DeletionReceipt(id: newReceiptID(), storeId: storeId, templateId: ref.id)
        var next = deletions
        next[ref.id] = IdentityTemplateDeletion(receiptId: receipt.id, status: .purgePending)
        try commitDeletions(next)
        try purgeTemplate(ref.id)
        return receipt
    }

    public func deleteCandidate(_ candidate: CandidateRef, ownership: OwnershipAuthorization) async throws -> DeletionReceipt {
        guard !closed else { throw IdentityError(.closed) }
        try IdentityAuthorizationState.requireOwnership(ownership, storeId: storeId, provider: provider, nowMillis: wallClock.nowMillis)
        guard candidate.storeId == storeId, candidates[candidate.id] != nil else { throw IdentityError(.candidateNotFound) }
        if let existing = candidateDeletions[candidate.id] { return try DeletionReceipt(id: existing.receiptId, storeId: storeId, candidateId: candidate.id) }
        let targetIds = Set(templates.values.filter { $0.candidateId == candidate.id }.map { $0.ref.id })
        let receipt = try DeletionReceipt(id: newReceiptID(), storeId: storeId, candidateId: candidate.id)
        var nextCandidates = candidateDeletions
        nextCandidates[candidate.id] = IdentityCandidateDeletion(receiptId: receipt.id, templateIds: targetIds)
        var nextTemplates = deletions
        var reserved: Set<String> = [receipt.id]
        for templateId in targetIds where nextTemplates[templateId] == nil {
            let id = try newReceiptID(reserved: reserved)
            reserved.insert(id)
            nextTemplates[templateId] = IdentityTemplateDeletion(receiptId: id, status: .purgePending)
        }
        let bytes = try Self.encode(storeId: storeId, authorization: authorization, candidates: candidates, revocations: revocations,
                                    templates: templates, stages: stages, deletions: nextTemplates, candidateDeletions: nextCandidates)
        do { try journal.commit(bytes) } catch { closed = true; throw error }
        deletions = nextTemplates; candidateDeletions = nextCandidates
        var signals: [IdentityStopSignal] = []
        for (operationId, operation) in operations where operation.ticket.candidate.id == candidate.id {
            operations[operationId]?.terminal = .authorizationRevoked
            signals.append(operation.ticket.stopSignal)
        }
        await Self.stop(signals, reason: .authorizationRevoked)
        for templateId in targetIds { try purgeTemplate(templateId) }
        return receipt
    }

    /// Returns the current durable status. Deadline-bound waiting is a separate overload.
    public func awaitDeletion(_ receipt: DeletionReceipt) throws -> DeletionStatus {
        guard !closed else { throw IdentityError(.closed) }
        guard receipt.storeId == storeId else { throw IdentityError(.invalidInput) }
        if let templateId = receipt.templateId, let record = deletions[templateId], record.receiptId == receipt.id { return record.status }
        if let candidateId = receipt.candidateId, let candidate = candidateDeletions[candidateId], candidate.receiptId == receipt.id {
            let statuses = try candidate.templateIds.map { id -> DeletionStatus in
                guard let record = deletions[id] else { throw IdentityError(.invalidInput) }
                return record.status
            }
            if statuses.contains(.purgeFailed) { return .purgeFailed }
            if statuses.allSatisfy({ $0 == .purged }) { return .purged }
            return .purgePending
        }
        throw IdentityError(.invalidInput)
    }

    nonisolated static func allocateReceiptID(existing: Set<String>, generate: () -> String) throws -> String {
        var id = generate()
        while existing.contains(id) { id = generate() }
        try identityToken(id)
        return id
    }

    private func newReceiptID(reserved: Set<String> = []) throws -> String {
        let existing = reserved.union(revocations.keys).union(deletions.values.map(\.receiptId))
            .union(candidateDeletions.values.map(\.receiptId))
        return try Self.allocateReceiptID(existing: existing) { UUID().uuidString.lowercased() }
    }

    private func commitDeletions(_ next: [String: IdentityTemplateDeletion]) throws {
        let bytes = try Self.encode(storeId: storeId, authorization: authorization, candidates: candidates,
                                    revocations: revocations, templates: templates, stages: stages, deletions: next, candidateDeletions: candidateDeletions)
        do { try journal.commit(bytes) } catch { closed = true; throw error }
        deletions = next
    }

    private func purgeTemplate(_ id: String) throws {
        guard !closed, !writerReleased, templateLeases[id] == nil,
              let deletion = deletions[id], deletion.status != .purged else { return }
        let status = Self.removePayload(id, payload: payload)
        var next = deletions; next[id] = deletion.withStatus(status)
        // Payload may already be gone. An ambiguous metadata failure retires the
        // writer, and recovery retries from the last durable tombstone.
        do { try commitDeletions(next) } catch { closed = true; throw error }
    }

    private nonisolated static func removePayload(_ id: String, payload: IdentityTemplatePayload) -> DeletionStatus {
        do { try payload.remove(recordId: id); return .purged }
        catch { return .purgeFailed }
    }

    private func releaseTemplateLease(_ id: String) {
        guard let count = templateLeases[id] else { return }
        if count <= 1 { templateLeases.removeValue(forKey: id) }
        else { templateLeases[id] = count - 1 }
        // Retired and closing stores never do file/key I/O from an old callback.
        try? purgeTemplate(id)
        if operations.isEmpty && templateLeases.isEmpty {
            let waiters = drainWaiters; drainWaiters.removeAll()
            for waiter in waiters { waiter.resume() }
        }
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
        if !operations.isEmpty || !templateLeases.isEmpty {
            await withCheckedContinuation { drainWaiters.append($0) }
        }
        guard !writerReleased else { return }
        try journal.close(); writerReleased = true
    }

    private nonisolated static func stop(_ signals: [IdentityStopSignal], reason: IdentityStopReason) async {
        guard !signals.isEmpty else { return }
        await Task.detached { for signal in signals { signal.requestStop(reason) } }.value
    }

    private static func encode(storeId: String, authorization: IdentityAuthorizationState,
                               candidates: [String: String], revocations: [String: IdentityRevocation],
                               templates: [String: IdentityTemplateRecord] = [:], stages: Set<String> = [],
                               deletions: [String: IdentityTemplateDeletion] = [:],
                               candidateDeletions: [String: IdentityCandidateDeletion]) throws -> Data {
        let json = identityJSON(["version": 5, "storeId": storeId,
                                 "authorization": try authorization.canonicalJSON(), "candidates": candidates,
                                 "revocations": revocations.mapValues(\.fields), "templates": templates.mapValues(\.fields), "stages": stages.sorted(), "deletions": deletions.mapValues(\.fields), "candidateDeletions": candidateDeletions.mapValues(\.fields)])
        guard json.utf8.count <= 65_536 else { throw IdentityError(.invalidInput) }
        return Data(json.utf8)
    }
}
