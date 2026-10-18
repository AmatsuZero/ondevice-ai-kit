import Foundation

public typealias GalleryIndexBuilder = @Sendable ([IdentityEmbedding], IdentityStopSignal) throws -> Data

/// Immutable gallery snapshots backed by IdentityStore authorization and leases.
public actor IndexStore {
    public nonisolated let storeId: String
    private let catalog: GalleryCatalog
    private let identityStore: IdentityStore
    private let buildIndex: GalleryIndexBuilder
    private var builders: [UUID: BuilderState] = [:]
    private var leases: [String: Int] = [:]
    private var deletedLineages: [String: DeletionReceipt] = [:]
    private var preparedIndexes: [String: Data] = [:]
    private var preparedIndexLineages: [String: String] = [:]
    private var closed = false
    private var lastLineageId: String?
    private var closeWaiters: [CheckedContinuation<Void, Never>] = []
    private var inFlight = 0
    private var busyBuilders: Set<UUID> = []
    private var closeFinished = false
    private var leaseLineages: [String: Int] = [:]
    private var revisionLineages: [String: String] = [:]
    private var activeBuilds: [UUID: Task<Data, Error>] = [:]
    private var activeTickets: [UUID: IdentityGalleryOperation] = [:]
    private var activePublishes: Set<UUID> = []
    private var activeReadTickets: [String: [IdentityGalleryOperation]] = [:]
    private var rebuilding: Set<String> = []
    private var pendingStopDeliveries = 0
    private var lineageStopDeliveries: [String: Int] = [:]

    private struct BuilderState {
        enum Status { case open, publishing, published, discarded }
        var status: Status = .open
        let base: GalleryRef?
        let operationGrant: AuthorizationGrant
        let policy: ThresholdPolicy
        let aggregationPolicyId: String
        let lineageId: String
        var members: [String: GallerySnapshotMember]
        var rebuild: GallerySnapshotRecord? = nil
    }

    public init(root: URL, identityStore: IdentityStore, keyProvider: any IdentityKeyProvider,
                buildIndex: @escaping GalleryIndexBuilder) throws {
        let catalog = try GalleryCatalog(root: root, identityStoreId: identityStore.storeId, keyProvider: keyProvider)
        self.storeId = catalog.storeId; self.catalog = catalog; self.identityStore = identityStore; self.buildIndex = buildIndex
        for (lineageId, receiptId) in try catalog.tombstoneReceipts() {
            self.deletedLineages[lineageId] = try DeletionReceipt(id: receiptId, storeId: catalog.storeId, galleryId: lineageId)
            try catalog.removeLineage(lineageId)
        }
    }

    public static func open(root: URL, identityStore: IdentityStore, keyProvider: any IdentityKeyProvider,
                            buildIndex: @escaping GalleryIndexBuilder) async throws -> IndexStore {
        try IndexStore(root: root, identityStore: identityStore, keyProvider: keyProvider, buildIndex: buildIndex)
    }

    public func createGalleryBuilder(baseGalleryRef: GalleryRef? = nil, grant: AuthorizationGrant,
                                     policy: ThresholdPolicy, backend: (any IdentityBackend)? = nil,
                                     aggregationPolicyId: String = "subject-max-v1") async throws -> GalleryBuilder {
        try beginOperation(); defer { endOperation() }
        guard policy.kind == .identification, policy.calibrationStatus == .verified,
              policy.effectiveAuthorizationEpoch == grant.authorizationEpoch else { throw IdentityError(.thresholdUnverified, policyId: policy.id) }
        if let backend, backend.embeddingSpace != policy.embeddingSpace { throw IdentityError(.embeddingSpaceMismatch) }
        var members: [String: GallerySnapshotMember] = [:]
        if let baseGalleryRef {
            guard deletedLineages[baseGalleryRef.id] == nil else { throw IdentityError(.galleryRevisionGone) }
            let base = try catalog.snapshot(baseGalleryRef)
            guard base.policy.embeddingSpace == policy.embeddingSpace else { throw IdentityError(.embeddingSpaceMismatch) }
            members = Dictionary(uniqueKeysWithValues: base.members.map { ($0.ref.id, $0) })
        }
        // A live operation grant may copy opaque metadata for explicit cleanup;
        // publication and reads still validate every retained member together.
        try await admitMutation(grant: grant, members: [], space: policy.embeddingSpace)
        try requireOpen()
        if let baseGalleryRef { _ = try catalog.snapshot(baseGalleryRef) }
        let id = UUID()
        let lineageId = baseGalleryRef?.id ?? UUID().uuidString.lowercased()
        builders[id] = BuilderState(base: baseGalleryRef, operationGrant: grant, policy: policy, aggregationPolicyId: aggregationPolicyId,
                                    lineageId: lineageId, members: members)
        if baseGalleryRef == nil { lastLineageId = lineageId }
        return GalleryBuilder(store: self, id: id)
    }

    func snapshotSummary(_ ref: GalleryRef) throws -> GallerySnapshotRecord {
        try requireOpen(); guard deletedLineages[ref.id] == nil else { throw IdentityError(.galleryRevisionGone) }
        return try catalog.snapshot(ref)
    }

    public func currentGallery() throws -> GalleryRef? {
        try requireOpen(); guard let lineage = lastLineageId, deletedLineages[lineage] == nil else { return nil }; return try catalog.head(lineageId: lineage)
    }

    /// Recreates only the derived cache for this exact durable snapshot.
    public func rebuildIndex(_ ref: GalleryRef, grant: AuthorizationGrant) async throws -> GalleryRef {
        try beginOperation(); defer { endOperation() }
        let record = try catalog.snapshot(ref)
        guard rebuilding.insert(ref.revision).inserted else { throw IdentityError(.busy) }
        let id = UUID()
        builders[id] = BuilderState(base: ref, operationGrant: grant, policy: record.policy,
            aggregationPolicyId: record.aggregationPolicyId, lineageId: ref.id,
            members: Dictionary(uniqueKeysWithValues: record.members.map { ($0.ref.id, $0) }), rebuild: record)
        defer { builders.removeValue(forKey: id); rebuilding.remove(ref.revision) }
        return try await builderPublish(id)
    }

    func acquireIndexReadLease(_ ref: GalleryRef, grant: AuthorizationGrant? = nil) async throws -> IndexReadLease {
        guard let grant else { throw IdentityError(.authorizationRequired) }
        try beginOperation(); defer { endOperation() }
        let value = try snapshotSummary(ref)
        guard let indexData = preparedIndexes[ref.revision] else { throw IdentityError(.rebuildRequired) }
        let ticket = try await identityStore.beginGalleryOperation(grant: grant,
            members: value.members.map { IdentityGalleryMember(ref: $0.ref, grant: $0.grant) }, embeddingSpace: value.policy.embeddingSpace,
            requiredGrants: [value.operationGrant])
        guard !closed, deletedLineages[ref.id] == nil else {
            try? await identityStore.finishGalleryOperation(ticket) { throw IdentityError(.closed) }
            throw IdentityError(.closed)
        }
        leases[ref.revision, default: 0] += 1
        leaseLineages[ref.id, default: 0] += 1; revisionLineages[ref.revision] = ref.id
        activeReadTickets[ref.id, default: []].append(ticket)
        return IndexReadLease(snapshot: value, indexData: indexData, stopSignal: ticket.stopSignal) { [self] in
            await self.releaseReadLease(ref.revision, lineage: ref.id, ticket: ticket)
        }
    }

    public func deleteGallery(_ ref: GalleryRef, ownership: OwnershipAuthorization) async throws -> DeletionReceipt {
        try beginOperation(); defer { endOperation() }
        try await identityStore.validateGalleryOwnership(ownership)
        try requireOpen()
        if let receipt = deletedLineages[ref.id] { return receipt }
        guard let head = try catalog.head(lineageId: ref.id), head.id == ref.id else { throw IdentityError(.galleryNotFound) }
        let receipt = try DeletionReceipt(id: UUID().uuidString.lowercased(), storeId: storeId, galleryId: ref.id)
        try catalog.tombstoneLineage(ref.id, receiptId: receipt.id)
        deletedLineages[ref.id] = receipt
        activeReadTickets[ref.id, default: []].forEach { queueStop($0.stopSignal, reason: .authorizationRevoked, lineage: ref.id) }
        for id in builders.keys where builders[id]?.lineageId == ref.id { cancelBuilder(id, reason: .authorizationRevoked) }
        try purgeLineageIfPossible(ref.id)
        return receipt
    }

    public func close() async {
        if closeFinished { return }
        if !closed {
            closed = true
            for id in builders.keys { cancelBuilder(id, reason: .closed) }
            for (lineage, tickets) in activeReadTickets {
                for ticket in tickets { queueStop(ticket.stopSignal, reason: .closed, lineage: lineage) }
            }
        }
        if IdentityStopHandlerContext.current?.isActive == true { return }
        if inFlight == 0 && leases.isEmpty && pendingStopDeliveries == 0 { finishClose() }
        else { await withCheckedContinuation { closeWaiters.append($0) } }
    }

    fileprivate func builderAdd(_ id: UUID, template: TemplateRef, grant: AuthorizationGrant) async throws {
        var state = try beginBuilderMutation(id); defer { endBuilderMutation(id) }
        guard state.members[template.id] == nil else { throw IdentityError(.invalidInput) }
        let candidate = try await identityStore.recordedTemplateCandidate(template)
        try requireOpen(); guard builders[id]?.status == .open else { throw IdentityError(.builderClosed) }
        guard template.embeddingSpaceId == state.policy.embeddingSpace else { throw IdentityError(.embeddingSpaceMismatch) }
        let member = GallerySnapshotMember(ref: template, candidate: candidate, grant: grant)
        try await admitMutation(grant: state.operationGrant, members: [member], space: state.policy.embeddingSpace)
        _ = try openBuilder(id)
        state.members[template.id] = member; builders[id] = state
    }

    fileprivate func builderRemove(_ id: UUID, template: TemplateRef, grant: AuthorizationGrant) async throws {
        var state = try beginBuilderMutation(id); defer { endBuilderMutation(id) }
        guard state.members[template.id]?.ref == template else { throw IdentityError(.templateNotFound) }
        try await admitMutation(grant: state.operationGrant, members: [], space: state.policy.embeddingSpace)
        try await admitMutation(grant: grant, members: [], space: state.policy.embeddingSpace)
        try requireOpen(); guard builders[id]?.status == .open else { throw IdentityError(.builderClosed) }
        state.members.removeValue(forKey: template.id); builders[id] = state
    }

    fileprivate func builderPublish(_ id: UUID) async throws -> GalleryRef {
        var state = try beginBuilderMutation(id)
        defer { endBuilderMutation(id) }
        guard !state.members.isEmpty else { throw IdentityError(.invalidInput) }
        activePublishes.insert(id)
        defer { activePublishes.remove(id); try? purgeLineageIfPossible(state.lineageId) }
        state.status = .publishing; builders[id] = state
        let members = state.members.values.sorted { $0.ref.id < $1.ref.id }
        var leases: [IdentityTemplateLease] = []
        var ticket: IdentityGalleryOperation?
        do {
            let admitted = try await identityStore.beginGalleryOperation(grant: state.operationGrant,
                members: members.map { IdentityGalleryMember(ref: $0.ref, grant: $0.grant) }, embeddingSpace: state.policy.embeddingSpace,
                requiredGrants: state.rebuild.map { [$0.operationGrant] } ?? [])
            ticket = admitted
            activeTickets[id] = admitted
            for member in members { leases.append(try await identityStore.acquireTemplateLease(member.ref, grant: member.grant, scope: .identification, embeddingSpace: state.policy.embeddingSpace)) }
            guard builders[id]?.status == .publishing, !closed else { throw IdentityError(.cancelled) }
            if Task.isCancelled { admitted.stopSignal.requestStop(.cancelled); throw IdentityError(.cancelled) }
            let embeddings = try leases.map { try $0.embedding() }
            let indexBuilder = buildIndex
            let buildTask = Task.detached { try indexBuilder(embeddings, admitted.stopSignal) }
            activeBuilds[id] = buildTask
            let bytes = try await withTaskCancellationHandler {
                if Task.isCancelled { admitted.stopSignal.requestStop(.cancelled) }
                return try await buildTask.value
            } onCancel: {
                admitted.stopSignal.requestStop(.cancelled)
                buildTask.cancel()
            }
            activeBuilds.removeValue(forKey: id)
            try admitted.stopSignal.check()
            guard builders[id]?.status == .publishing, !closed else { throw IdentityError(.cancelled) }
            guard !bytes.isEmpty else { throw IdentityError(.indexUnavailable) }
            let record = try state.rebuild ?? GallerySnapshotRecord(indexStoreId: storeId, identityStoreId: identityStore.storeId, lineageId: state.lineageId,
                parent: state.base, members: members, operationGrant: state.operationGrant, policy: state.policy, aggregationPolicyId: state.aggregationPolicyId)
            let isRebuild = state.rebuild != nil
            let result = try await identityStore.finishGalleryOperation(admitted) {
                if isRebuild { _ = try self.catalog.snapshot(record.ref) } else { try self.catalog.publish(record) }
                return record.ref
            }
            activeTickets.removeValue(forKey: id)
            preparedIndexes[record.ref.revision] = bytes
            preparedIndexLineages[record.ref.revision] = record.ref.id
            revisionLineages[record.ref.revision] = record.ref.id
            ticket = nil
            builders[id]?.status = .published; for lease in leases { await lease.close() }; return result
        } catch {
            // beginGalleryOperation owns an IdentityStore drain token. Consume it
            // on every post-admission failure, including index-build errors.
            if let ticket { try? await identityStore.finishGalleryOperation(ticket) { throw error } }
            builders[id]?.status = .discarded; for lease in leases { await lease.close() }
            activeBuilds.removeValue(forKey: id); activeTickets.removeValue(forKey: id)
            throw (error as? IdentityError) ?? IdentityError(.indexUnavailable)
        }
    }

    fileprivate func builderClose(_ id: UUID) { cancelBuilder(id) }

    public func awaitDeletion(_ receipt: DeletionReceipt) throws -> DeletionStatus {
        guard let galleryId = receipt.galleryId, receipt.storeId == storeId, deletedLineages[galleryId] == receipt else { throw IdentityError(.invalidInput) }
        return try catalog.hasSnapshots(lineageId: galleryId) ? .purgePending : .purged
    }

    private func admitMutation(grant: AuthorizationGrant, members: [GallerySnapshotMember], space: EmbeddingSpaceId) async throws {
        let ticket = try await identityStore.beginGalleryOperation(grant: grant, members: members.map { IdentityGalleryMember(ref: $0.ref, grant: $0.grant) }, embeddingSpace: space)
        _ = try await identityStore.finishGalleryOperation(ticket) { () }
    }
    private func openBuilder(_ id: UUID) throws -> BuilderState { try requireOpen(); guard let state = builders[id], state.status == .open else { throw IdentityError(.builderClosed) }; return state }
    private func requireOpen() throws { guard !closed else { throw IdentityError(.closed) } }
    private func beginOperation() throws { try requireOpen(); inFlight += 1 }
    private func endOperation() {
        inFlight -= 1
        if closed && inFlight == 0 && leases.isEmpty && pendingStopDeliveries == 0 { finishClose() }
    }
    private func beginBuilderMutation(_ id: UUID) throws -> BuilderState {
        let state = try openBuilder(id)
        guard busyBuilders.insert(id).inserted else { throw IdentityError(.busy) }
        inFlight += 1
        return state
    }
    private func endBuilderMutation(_ id: UUID) { busyBuilders.remove(id); endOperation() }
    private func releaseReadLease(_ revision: String, lineage: String, ticket: IdentityGalleryOperation) async {
        activeReadTickets[lineage]?.removeAll { $0 === ticket }
        if activeReadTickets[lineage]?.isEmpty == true { activeReadTickets.removeValue(forKey: lineage) }
        try? await identityStore.finishGalleryOperation(ticket) { () }
        releaseLease(revision)
    }
    private func cancelBuilder(_ id: UUID, reason: IdentityStopReason = .cancelled) {
        builders[id]?.status = .discarded
        activeBuilds[id]?.cancel()
        if let ticket = activeTickets[id], let lineage = builders[id]?.lineageId { queueStop(ticket.stopSignal, reason: reason, lineage: lineage) }
    }
    private func queueStop(_ signal: IdentityStopSignal, reason: IdentityStopReason, lineage: String) {
        guard let delivery = signal.prepareStopDelivery(reason) else { return }
        pendingStopDeliveries += 1; lineageStopDeliveries[lineage, default: 0] += 1
        Task.detached { [self] in
            delivery()
            await didDeliverStop(lineage: lineage)
        }
    }
    private func didDeliverStop(lineage: String) {
        pendingStopDeliveries -= 1
        if lineageStopDeliveries[lineage] == 1 { lineageStopDeliveries.removeValue(forKey: lineage) }
        else { lineageStopDeliveries[lineage, default: 1] -= 1 }
        try? purgeLineageIfPossible(lineage)
        if closed && inFlight == 0 && leases.isEmpty && pendingStopDeliveries == 0 { finishClose() }
    }
    private func releaseLease(_ revision: String) {
        guard let count = leases[revision] else { return }
        if count <= 1 { leases.removeValue(forKey: revision) } else { leases[revision] = count - 1 }
        if let lineage = revisionLineages[revision] {
            if let lineCount = leaseLineages[lineage], lineCount <= 1 { leaseLineages.removeValue(forKey: lineage) } else { leaseLineages[lineage] = (leaseLineages[lineage] ?? 1) - 1 }
            try? purgeLineageIfPossible(lineage)
        }
        if closed && inFlight == 0 && leases.isEmpty && pendingStopDeliveries == 0 { finishClose() }
    }
    private func purgeLineageIfPossible(_ lineage: String) throws {
        guard deletedLineages[lineage] != nil, leaseLineages[lineage] == nil, lineageStopDeliveries[lineage] == nil,
              !activePublishes.contains(where: { builders[$0]?.lineageId == lineage }) else { return }
        try catalog.removeLineage(lineage)
        let revisions = preparedIndexLineages.compactMap { $0.value == lineage ? $0.key : nil }
        for revision in revisions { preparedIndexes.removeValue(forKey: revision); preparedIndexLineages.removeValue(forKey: revision); revisionLineages.removeValue(forKey: revision) }
    }
    private func finishClose() {
        guard !closeFinished else { return }
        closeFinished = true
        preparedIndexes.removeAll()
        preparedIndexLineages.removeAll()
        revisionLineages.removeAll()
        try? catalog.close()
        let waiters = closeWaiters; closeWaiters.removeAll(); for waiter in waiters { waiter.resume() }
    }
}

public final class GalleryBuilder: @unchecked Sendable {
    private let store: IndexStore; private let id: UUID
    fileprivate init(store: IndexStore, id: UUID) { self.store = store; self.id = id }
    public func addTemplate(_ template: TemplateRef, grant: AuthorizationGrant) async throws { try await store.builderAdd(id, template: template, grant: grant) }
    public func removeTemplate(_ template: TemplateRef, grant: AuthorizationGrant) async throws { try await store.builderRemove(id, template: template, grant: grant) }
    public func publish() async throws -> GalleryRef { try await store.builderPublish(id) }
    public func close() async { await store.builderClose(id) }
}

public final class IndexReadLease: @unchecked Sendable {
    private let value: GallerySnapshotRecord; private let stopSignal: IdentityStopSignal
    private var release: (@Sendable () async -> Void)?; private var bytes: Data?
    private let lock = NSLock(); private var closed = false
    fileprivate init(snapshot: GallerySnapshotRecord, indexData: Data, stopSignal: IdentityStopSignal, release: @escaping @Sendable () async -> Void) {
        value = snapshot; bytes = indexData; self.stopSignal = stopSignal; self.release = release
    }
    func snapshot() throws -> GallerySnapshotRecord { lock.lock(); defer { lock.unlock() }; guard !closed else { throw IdentityError(.closed) }; try stopSignal.check(); return value }
    func indexData() throws -> Data {
        lock.lock(); defer { lock.unlock() }
        guard !closed, let bytes else { throw IdentityError(.closed) }
        try stopSignal.check(); return bytes
    }
    func registerStopHandler(_ handler: @escaping @Sendable () -> Void) -> IdentityStopRegistration {
        stopSignal.registerStopHandler(handler)
    }
    public func close() async { if let callback = takeRelease() { await callback() } }
    private func takeRelease() -> (@Sendable () async -> Void)? {
        lock.lock(); defer { lock.unlock() }; guard !closed else { return nil }
        closed = true; bytes = nil; let callback = release; release = nil; return callback
    }
    deinit { if let callback = takeRelease() { Task { await callback() } } }
}
