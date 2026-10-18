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
    private var pendingPurgeStops: [String: Int] = [:]
    private var pendingStopDeliveries = 0
    private var closed = false
    private var writerReleased = false
    private let instanceId = UUID()
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
                            guard [5, 6].contains(try identityInteger(fields, "version")) else { throw IdentityError(.invalidInput) }
                        }
                    }
                }
                let version = try identityInteger(fields, "version")
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
                    for (id, value) in records { revocations[id] = try IdentityRevocation.restore(value, includeTemplateIds: version >= 6) }
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
                let staleTemplates = Set(templates.values.filter { record in
                    guard let grant = auth.recordedGrant(record.grantId) else { return false }
                    return grant.authorizationEpoch < auth.highestEpoch(domain: grant.revocationDomainId)
                }.map { $0.ref.id })
                for (receiptId, record) in revocations {
                    let targets = Set(templates.values.filter { record.candidateIds.contains($0.candidateId) }.map { $0.ref.id })
                    if version >= 6 {
                        guard record.templateIds == targets, targets.allSatisfy({ deletions[$0] != nil }) else { throw IdentityError(.invalidInput) }
                    } else {
                        revocations[receiptId] = IdentityRevocation(domainId: record.domainId, epoch: record.epoch, grantIds: record.grantIds,
                                                                   candidateIds: record.candidateIds, templateIds: targets)
                    }
                }
                if version >= 6 {
                    guard staleTemplates.allSatisfy({ deletions[$0] != nil }) else { throw IdentityError(.invalidInput) }
                } else {
                    let reserved = Set(revocations.keys).union(candidateDeletions.values.map(\.receiptId))
                    deletions = try Self.addDeletionFacts(staleTemplates, existing: deletions, reserved: reserved)
                    // Persist the complete migrated ownership/tombstone set before any cleanup.
                    try journal.commit(Self.encode(storeId: id, authorization: auth, candidates: candidates, revocations: revocations,
                                                   templates: templates, stages: stages, deletions: deletions, candidateDeletions: candidateDeletions))
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
        let targets = Set(templates.values.filter { record in
            guard let stored = auth.recordedGrant(record.grantId) else { return false }
            return stored.revocationDomainId == grant.revocationDomainId && stored.authorizationEpoch < auth.highestEpoch(domain: grant.revocationDomainId)
        }.map { $0.ref.id })
        let nextDeletions = try Self.addDeletionFacts(targets, existing: deletions, reserved: receiptIDs)
        let bytes = try Self.encode(storeId: storeId, authorization: auth, candidates: next, revocations: revocations, templates: templates, stages: stages, deletions: nextDeletions, candidateDeletions: candidateDeletions)
        _ = try auth.admitting(grant, scope: .enrollment, provider: provider, nowMillis: wallClock.nowMillis)
        do { try journal.commit(bytes) }
        catch { retire(); throw error }
        authorization = auth; candidates = next; deletions = nextDeletions
        let signals = invalidateOperations(domain: grant.revocationDomainId, before: auth.highestEpoch(domain: grant.revocationDomainId))
        try await stopAndPurge(targets, signals: signals)
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
        let candidateIds = Set(candidates.filter { targets.contains($0.value) }.keys)
        let templateIds = Set(templates.values.filter { candidateIds.contains($0.candidateId) }.map { $0.ref.id })
        let record = IdentityRevocation(domainId: grant.revocationDomainId, epoch: epoch, grantIds: targets,
                                        candidateIds: candidateIds, templateIds: templateIds)
        var next = revocations; next[receipt.id] = record
        let nextDeletions = try Self.addDeletionFacts(templateIds, existing: deletions, reserved: receiptIDs.union([receipt.id]))
        let bytes = try Self.encode(storeId: storeId, authorization: nextAuthorization, candidates: candidates, revocations: next, templates: templates, stages: stages, deletions: nextDeletions, candidateDeletions: candidateDeletions)
        do { try journal.commit(bytes) }
        catch { retire(); throw error }
        authorization = nextAuthorization; revocations = next; deletions = nextDeletions
        let signals = invalidateOperations(domain: grant.revocationDomainId, before: epoch)
        try await stopAndPurge(templateIds, signals: signals)
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

    private func invalidateTemplateOperations(_ targets: Set<String>) -> [IdentityStopSignal] {
        var signals: [IdentityStopSignal] = []
        for (id, operation) in operations {
            let matches = operation.ticket.templateTargets.map { !$0.isDisjoint(with: targets) } ?? false
            if matches {
                operations[id]?.terminal = .authorizationRevoked
                signals.append(operation.ticket.stopSignal)
            }
        }
        return signals
    }

    func beginOperation(_ candidate: CandidateRef, grant: AuthorizationGrant, scope: IdentityScope,
                        templateTargets: [TemplateRef]? = nil) throws -> IdentityOperationTicket {
        let membership = try recordedCandidateGrant(candidate)
        guard membership.revocationDomainId == grant.revocationDomainId, membership.purpose == grant.purpose,
              membership.authorizationEpoch == grant.authorizationEpoch else { throw IdentityError(.authorizationRequired) }
        let next = try authorization.admitting(grant, scope: scope, provider: provider, nowMillis: wallClock.nowMillis)
        if next != authorization {
            let bytes = try Self.encode(storeId: storeId, authorization: next, candidates: candidates, revocations: revocations, templates: templates, stages: stages, deletions: deletions, candidateDeletions: candidateDeletions)
            _ = try next.admitting(grant, scope: scope, provider: provider, nowMillis: wallClock.nowMillis)
            do { try journal.commit(bytes) } catch { retire(); throw error }
            authorization = next
        }
        let targetIDs: Set<String>?
        if let templateTargets {
            targetIDs = Set(try templateTargets.map { ref in
                guard let record = templates[ref.id], record.candidateId == candidate.id, deletions[ref.id] == nil,
                      candidateDeletions[candidate.id] == nil else { throw IdentityError(.templateRevoked) }
                try ref.validate(against: record.ref)
                return ref.id
            })
        } else { targetIDs = nil }
        let ticket = IdentityOperationTicket(candidate: candidate, grant: grant, scope: scope, templateTargets: targetIDs)
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
            maybeReleaseWriter()
        }
        if operation.terminal == .authorizationRevoked { throw IdentityError(.authorizationRevoked) }
        guard !closed else { throw IdentityError(.closed) }
        try ticket.stopSignal.check()
        _ = try recordedCandidateGrant(ticket.candidate)
        if let targets = ticket.templateTargets {
            for id in targets {
                guard let record = templates[id], record.candidateId == ticket.candidate.id,
                      deletions[id] == nil, candidateDeletions[ticket.candidate.id] == nil else {
                    throw IdentityError(.authorizationRevoked)
                }
                guard let stored = authorization.recordedGrant(record.grantId),
                      stored.authorizationEpoch == ticket.grant.authorizationEpoch,
                      stored.revocationDomainId == ticket.grant.revocationDomainId else { throw IdentityError(.authorizationRevoked) }
            }
        }
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
                    retire(); throw (error as? IdentityError) ?? IdentityError(.invalidInput)
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
            do { try journal.commit(bytes) } catch { retire(); throw error }
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
    public func deleteTemplate(_ ref: TemplateRef, ownership: OwnershipAuthorization) async throws -> DeletionReceipt {
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
        let signals = invalidateTemplateOperations([ref.id])
        try await stopAndPurge([ref.id], signals: signals)
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
        do { try journal.commit(bytes) } catch { retire(); throw error }
        deletions = nextTemplates; candidateDeletions = nextCandidates
        var signals: [IdentityStopSignal] = []
        for (operationId, operation) in operations where operation.ticket.candidate.id == candidate.id {
            operations[operationId]?.terminal = .authorizationRevoked
            signals.append(operation.ticket.stopSignal)
        }
        try await stopAndPurge(targetIds, signals: signals)
        return receipt
    }

    /// Returns the current durable status. Deadline-bound waiting is a separate overload.
    public func awaitDeletion(_ receipt: DeletionReceipt) throws -> DeletionStatus {
        guard !closed else { throw IdentityError(.closed) }
        guard receipt.storeId == storeId else { throw IdentityError(.invalidInput) }
        if let templateId = receipt.templateId, let record = deletions[templateId], record.receiptId == receipt.id { return record.status }
        if let candidateId = receipt.candidateId, let candidate = candidateDeletions[candidateId], candidate.receiptId == receipt.id {
            return try deletionStatus(candidate.templateIds)
        }
        throw IdentityError(.invalidInput)
    }

    public func awaitDeletion(_ receipt: RevocationReceipt) throws -> DeletionStatus {
        guard !closed else { throw IdentityError(.closed) }
        guard receipt.storeId == storeId, let record = revocations[receipt.id] else { throw IdentityError(.invalidInput) }
        return try deletionStatus(record.templateIds)
    }

    /// Waits for the committed purge status without performing cleanup or other store operations.
    /// Observation is bounded so the actor remains available to release leases and close.
    public func awaitDeletion(_ receipt: DeletionReceipt, deadline: IdentityDeadline,
                              stopSignal: IdentityStopSignal? = nil) async throws -> DeletionStatus {
        try await awaitDeletion(deadline: deadline, stopSignal: stopSignal) { try self.awaitDeletion(receipt) }
    }

    /// Revocation receipts observe the same per-template committed purge facts as deletion receipts.
    public func awaitDeletion(_ receipt: RevocationReceipt, deadline: IdentityDeadline,
                              stopSignal: IdentityStopSignal? = nil) async throws -> DeletionStatus {
        try await awaitDeletion(deadline: deadline, stopSignal: stopSignal) { try self.awaitDeletion(receipt) }
    }

    private func awaitDeletion(deadline: IdentityDeadline, stopSignal: IdentityStopSignal?,
                               status: () throws -> DeletionStatus) async throws -> DeletionStatus {
        while true {
            let current = try status()
            if current == .purged || current == .purgeFailed { return current }
            do {
                try Task.checkCancellation()
                try stopSignal?.check()
            } catch is CancellationError {
                throw IdentityError(.cancelled)
            }
            if deadline.isExpired(clock: monotonicClock) { return current }
            do { try await Task.sleep(nanoseconds: 10_000_000) }
            catch is CancellationError { throw IdentityError(.cancelled) }
        }
    }

    private func deletionStatus(_ targets: Set<String>) throws -> DeletionStatus {
        let statuses = try targets.map { id -> DeletionStatus in
            guard let record = deletions[id] else { throw IdentityError(.invalidInput) }
            return record.status
        }
        if statuses.contains(.purgeFailed) { return .purgeFailed }
        if statuses.allSatisfy({ $0 == .purged }) { return .purged }
        return .purgePending
    }

    private nonisolated static func addDeletionFacts(_ targets: Set<String>, existing: [String: IdentityTemplateDeletion],
                                                     reserved: Set<String>) throws -> [String: IdentityTemplateDeletion] {
        var result = existing
        var occupied = reserved.union(existing.values.map(\.receiptId))
        for target in targets.sorted() where result[target] == nil {
            let id = try allocateReceiptID(existing: occupied) { UUID().uuidString.lowercased() }
            occupied.insert(id)
            result[target] = IdentityTemplateDeletion(receiptId: id, status: .purgePending)
        }
        return result
    }

    nonisolated static func allocateReceiptID(existing: Set<String>, generate: () -> String) throws -> String {
        var id = generate()
        while existing.contains(id) { id = generate() }
        try identityToken(id)
        return id
    }

    private var receiptIDs: Set<String> {
        Set(revocations.keys).union(deletions.values.map(\.receiptId))
            .union(candidateDeletions.values.map(\.receiptId))
    }

    private func newReceiptID(reserved: Set<String> = []) throws -> String {
        try Self.allocateReceiptID(existing: reserved.union(receiptIDs)) { UUID().uuidString.lowercased() }
    }

    private func commitDeletions(_ next: [String: IdentityTemplateDeletion]) throws {
        let bytes = try Self.encode(storeId: storeId, authorization: authorization, candidates: candidates,
                                    revocations: revocations, templates: templates, stages: stages, deletions: next, candidateDeletions: candidateDeletions)
        do { try journal.commit(bytes) } catch { retire(); throw error }
        deletions = next
    }

    private func purgeTemplate(_ id: String) throws {
        guard !closed, !writerReleased, templateLeases[id] == nil, pendingPurgeStops[id] == nil,
              let deletion = deletions[id], deletion.status != .purged else { return }
        let status = Self.removePayload(id, payload: payload)
        var next = deletions; next[id] = deletion.withStatus(status)
        // Payload may already be gone. An ambiguous metadata failure retires the
        // writer, and recovery retries from the last durable tombstone.
        do { try commitDeletions(next) } catch { retire(); throw error }
    }

    private func stopAndPurge(_ targets: Set<String>, signals: [IdentityStopSignal]) async throws {
        // Actor reentrancy and lease release cannot overtake an earlier stop delivery.
        for id in targets { pendingPurgeStops[id, default: 0] += 1 }
        pendingStopDeliveries += signals.count
        await Self.stop(signals, reason: .authorizationRevoked, store: instanceId)
        pendingStopDeliveries -= signals.count
        for id in targets {
            let remaining = (pendingPurgeStops[id] ?? 1) - 1
            pendingPurgeStops[id] = remaining == 0 ? nil : remaining
        }
        maybeReleaseWriter()
        for id in targets { try purgeTemplate(id) }
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
        maybeReleaseWriter()
    }

    /// Retire admission immediately. Stop delivery owns a drain token before
    /// leaving the actor; failures never await the operation that is unwinding.
    private func retire() {
        if !closed {
            closed = true
            var revoked: [IdentityStopSignal] = []; var closing: [IdentityStopSignal] = []
            for (id, operation) in operations {
                if operation.terminal == .authorizationRevoked { revoked.append(operation.ticket.stopSignal) }
                else { operations[id]?.terminal = .closed; closing.append(operation.ticket.stopSignal) }
            }
            if !revoked.isEmpty || !closing.isEmpty {
                pendingStopDeliveries += 1
                let store = instanceId
                Task.detached { [self] in
                    let delivery = IdentityStoreStopDelivery(store: store)
                    IdentityStoreStopDelivery.$current.withValue(delivery) {
                        defer { delivery.finish() }
                        for signal in revoked { signal.requestStop(.authorizationRevoked) }
                        for signal in closing { signal.requestStop(.closed) }
                    }
                    await self.finishStopDelivery()
                }
            }
        }
        maybeReleaseWriter()
    }

    private func finishStopDelivery() {
        pendingStopDeliveries -= 1
        maybeReleaseWriter()
    }

    private func maybeReleaseWriter() {
        guard closed, !writerReleased, operations.isEmpty, templateLeases.isEmpty,
              pendingStopDeliveries == 0 else { return }
        try? journal.close()
        writerReleased = true
        let waiters = drainWaiters; drainWaiters.removeAll()
        for waiter in waiters { waiter.resume() }
    }

    public func close() async throws {
        retire()
        // A Task started by a synchronous stop handler inherits this marker.
        // It may retire, but cannot join its own delivery token. The outer
        // delivery releases the writer after returning from that handler.
        if let delivery = IdentityStoreStopDelivery.current,
           delivery.store == instanceId, delivery.isActive,
           IdentityStopHandlerContext.current?.isActive == true { return }
        if !writerReleased {
            await withCheckedContinuation { drainWaiters.append($0) }
        }
    }

    private nonisolated static func stop(_ signals: [IdentityStopSignal], reason: IdentityStopReason, store: UUID) async {
        guard !signals.isEmpty else { return }
        await Task.detached {
            let delivery = IdentityStoreStopDelivery(store: store)
            IdentityStoreStopDelivery.$current.withValue(delivery) {
                defer { delivery.finish() }
                for signal in signals { signal.requestStop(reason) }
            }
        }.value
    }

    private static func encode(storeId: String, authorization: IdentityAuthorizationState,
                               candidates: [String: String], revocations: [String: IdentityRevocation],
                               templates: [String: IdentityTemplateRecord] = [:], stages: Set<String> = [],
                               deletions: [String: IdentityTemplateDeletion] = [:],
                               candidateDeletions: [String: IdentityCandidateDeletion]) throws -> Data {
        let json = identityJSON(["version": 6, "storeId": storeId,
                                 "authorization": try authorization.canonicalJSON(), "candidates": candidates,
                                 "revocations": revocations.mapValues(\.fields), "templates": templates.mapValues(\.fields), "stages": stages.sorted(), "deletions": deletions.mapValues(\.fields), "candidateDeletions": candidateDeletions.mapValues(\.fields)])
        guard json.utf8.count <= 65_536 else { throw IdentityError(.invalidInput) }
        return Data(json.utf8)
    }
}

private final class IdentityStoreStopDelivery: @unchecked Sendable {
    @TaskLocal static var current: IdentityStoreStopDelivery?
    let store: UUID
    private let lock = NSLock()
    private var active = true
    init(store: UUID) { self.store = store }
    var isActive: Bool { lock.lock(); defer { lock.unlock() }; return active }
    func finish() { lock.lock(); active = false; lock.unlock() }
}
