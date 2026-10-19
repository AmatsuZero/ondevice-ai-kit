import Foundation
import ODAIKitCore

/// Candidate-unit score; no template vectors or host subject IDs.
public struct IdentificationCandidate: Sendable {
    public let candidateRef: CandidateRef
    public let score: Double
    public let rank: Int
}

/// Search controls cannot replace the immutable gallery threshold policy.
public struct IdentificationOptions: Sendable {
    public let topK: Int
    public let policy: ThresholdPolicyRef?
    public let revision: String
    public init(topK: Int = 32, policy: ThresholdPolicyRef? = nil, revision: String) throws {
        guard (1...32).contains(topK) else { throw IdentityError(.invalidInput) }
        try identityToken(revision)
        self.topK = topK; self.policy = policy; self.revision = revision
    }
}
public enum IdentificationOutcome: String, Sendable { case candidates, unknown, insufficientQuality }
public struct IdentificationEnrollmentCount: Sendable {
    public let candidateRef: CandidateRef
    public let templateCount: Int
}
/// Threshold metadata is reported independently from comparison scores so an unknown result
/// can identify its operating point without exposing a below-threshold score.
public struct IdentificationThresholdFields: Sendable {
    public let metric: String
    public let threshold: Double
    public init(metric: String, threshold: Double) throws {
        try identityToken(metric)
        guard threshold.isFinite else { throw IdentityError(.invalidInput) }
        self.metric = metric; self.threshold = threshold
    }
}

/// Conditions for grouping an authorized evaluation. These are not measured FPIR/FNIR rates.
public struct IdentificationGrouping: Sendable {
    public let datasetRevision: String
    public let operatingPoint: String
    public let enrollmentRule: String
    public let gallerySizeRange: ClosedRange<Int>
    public let topK: Int
}
public struct IdentificationResult: Sendable {
    public let outcome: IdentificationOutcome
    public let candidates: [IdentificationCandidate]
    public let scoreFields: IdentityScoreFields
    public let thresholdFields: IdentificationThresholdFields?
    public let quality: IdentityQuality
    public let galleryRevision: String
    public let galleryContentDigest: String
    public let aggregationPolicyId: String
    public let thresholdPolicyId: String
    public let thresholdPolicyRevision: String
    public let galleryCandidateCount: Int
    public let galleryTemplateCount: Int
    public let enrollmentCounts: [IdentificationEnrollmentCount]
    public let fpirFnirGrouping: IdentificationGrouping
    public let backendId: String
    public let backendVersion: String
    public let modelBinding: ModelBinding
    public let inputRevision: String
    public let optionsRevision: String
    public let preprocessingRevision: String
    public let sessionId: String
    public let runId: String
    public let durationNanos: Int64
}

/// One immutable result and one terminal outcome; cancellation also drains native work.
public final class IdentificationTask: @unchecked Sendable {
    public let runId: String
    fileprivate let signal: IdentityStopSignal
    private let lock = NSLock()
    private var outcome: TaskTerminal?
    private var worker: Task<IdentificationResult, Error>?
    private var delivery: Task<Void, Never>?

    fileprivate init(runId: String, signal: IdentityStopSignal) { self.runId = runId; self.signal = signal }
    public var terminal: TaskTerminal? { lock.lock(); defer { lock.unlock() }; return outcome }
    public func cancel() { requestStop(.cancelled) }
    public func close() async {
        cancel()
        if IdentityStopHandlerContext.current?.isActive == true { return }
        await drain()
    }
    public func result() async throws -> IdentificationResult {
        try await withTaskCancellationHandler {
            if Task.isCancelled { cancel() }
            return try await currentWorker().value
        } onCancel: { self.cancel() }
    }
    fileprivate func requestStop(_ reason: IdentityStopReason) {
        lock.lock()
        if outcome == nil, let action = signal.prepareStopDelivery(reason) {
            delivery = Task.detached { action() }
        }
        lock.unlock()
    }
    fileprivate func start(_ operation: @escaping @Sendable () async throws -> IdentificationResult) {
        lock.lock(); defer { lock.unlock() }
        worker = Task.detached { try await operation() }
    }
    fileprivate func complete(_ value: IdentificationResult) throws -> IdentificationResult {
        lock.lock(); defer { lock.unlock() }
        try signal.check()
        outcome = .completed
        return value
    }
    fileprivate func fail(_ error: IdentityError) {
        lock.lock(); defer { lock.unlock() }
        if outcome == nil { outcome = error.code == .cancelled ? .cancelled : .failed }
    }
    fileprivate func drain() async {
        _ = try? await currentWorker().value
        await drainStopDelivery()
    }
    fileprivate func drainStopDelivery() async { await currentDelivery()?.value }
    private func currentWorker() -> Task<IdentificationResult, Error> {
        lock.lock(); defer { lock.unlock() }; return worker!
    }
    private func currentDelivery() -> Task<Void, Never>? {
        lock.lock(); defer { lock.unlock() }; return delivery
    }
}

/// Fixed-snapshot queries over the same final authorization gate used by gallery reads.
/// This exploration implementation accepts only the internal fixture index format.
public actor FaceIdentification {
    private let identityStore: IdentityStore
    private let indexStore: IndexStore
    private let registry: IdentityBackendRegistry<any IdentityBackend>
    private let backends: [any IdentityBackend]
    private let clock: any IdentityMonotonicClock
    private var active: IdentificationTask?
    private var admitting = false
    private var admissionWaiters: [CheckedContinuation<Void, Never>] = []
    private var closed = false
    private var closing: Task<Void, Never>?

    public init(identityStore: IdentityStore, indexStore: IndexStore, defaultBackendId: String,
                backends: [any IdentityBackend], monotonicClock: any IdentityMonotonicClock = SystemIdentityMonotonicClock()) throws {
        self.identityStore = identityStore; self.indexStore = indexStore; self.backends = backends; self.clock = monotonicClock
        registry = try IdentityBackendRegistry(defaultBackendId: defaultBackendId,
            registrations: backends.map { IdentityBackendRegistration(backendId: $0.backendId, backend: $0) })
    }

    public func identify(probe: IdentityProbe, galleryRef: GalleryRef, grant: AuthorizationGrant,
                         options: IdentificationOptions, backend: IdentityBackendSelection = .default) async throws -> IdentificationTask {
        guard !closed else { throw IdentityError(.closed) }
        guard active == nil, !admitting else { throw IdentityError(.busy) }
        let selected = try registry.select(backend)
        let binding = IdentityEnrollmentBinding(selected)
        do { try binding.validate(selected) }
        catch let error as IdentityError {
            throw IdentityError(error.code, backendId: selected.backendId, modelBinding: selected.modelBinding)
        }
        guard selected.availability().status == .available else {
            throw IdentityError(.backendUnavailable, backendId: binding.space.backendId, modelBinding: binding.space.modelBinding)
        }
        admitting = true
        defer {
            admitting = false
            let waiters = admissionWaiters; admissionWaiters.removeAll()
            for waiter in waiters { waiter.resume() }
        }
        var policyId: String?
        do {
            let snapshot = try await indexStore.snapshotSummary(galleryRef)
            policyId = snapshot.policy.id
            try snapshot.policy.validate(embeddingSpace: binding.space, kind: .identification,
                epoch: snapshot.operationGrant.authorizationEpoch, gallerySize: Set(snapshot.members.map { $0.candidate.id }).count)
            if let requested = options.policy,
               requested.id != snapshot.policy.id || requested.revision != snapshot.policy.revision {
                throw IdentityError(.thresholdUnverified, policyId: snapshot.policy.id)
            }
            guard snapshot.aggregationPolicyId == "subject-max-v1", snapshot.policy.scoreMetric == "cosine",
                  snapshot.policy.scoreDirection == .higherIsBetter else { throw IdentityError(.unsupportedOption) }
            let lease = try await indexStore.acquireIndexReadLease(galleryRef, grant: grant)
            let task = IdentificationTask(runId: UUID().uuidString.lowercased(), signal: lease.operationSignal)
            if closed { task.requestStop(.closed) }
            if Task.isCancelled { task.cancel() }
            active = task
            let started = clock.nowNanos
            task.start { [self] in
                let result = await Result { () async throws -> IdentificationResult in
                    try await self.execute(task, lease: lease, snapshot: snapshot, probe: probe,
                        options: options, backend: selected, binding: binding, started: started)
                }
                await lease.operationSignal.drain()
                await task.drainStopDelivery()
                await self.didFinish(task)
                return try result.get()
            }
            return task
        } catch let error as IdentityError {
            throw IdentityError(error.code, backendId: binding.space.backendId, modelBinding: binding.space.modelBinding,
                policyId: error.policyId ?? policyId)
        }
    }

    public func close() async {
        closed = true
        if admitting { await withCheckedContinuation { admissionWaiters.append($0) } }
        active?.requestStop(.closed)
        if closing == nil {
            let running = active; let backends = backends
            closing = Task {
                await running?.drain()
                for backend in backends { backend.close() }
            }
        }
        if IdentityStopHandlerContext.current?.isActive == true { return }
        await closing?.value
    }
    private func didFinish(_ task: IdentificationTask) { if active === task { active = nil } }

    private nonisolated func execute(_ task: IdentificationTask, lease: IndexReadLease,
        snapshot: GallerySnapshotRecord, probe: IdentityProbe, options: IdentificationOptions,
        backend: any IdentityBackend, binding: IdentityEnrollmentBinding, started: Int64) async throws -> IdentificationResult {
        var members: [IdentityTemplateLease] = []
        var consumed = false
        do {
            try task.signal.check()
            for member in snapshot.members {
                members.append(try await identityStore.acquireTemplateLease(member.ref, grant: member.grant,
                    scope: .identification, embeddingSpace: binding.space))
            }
            let prepared = try binding.prepare(probe, options: EnrollmentOptions(qualityFloor: snapshot.policy.qualityFloor!,
                qualityPolicyId: snapshot.policy.id, revision: options.revision), backend: backend, signal: task.signal)
            var candidates: [IdentificationCandidate] = []
            let outcome: IdentificationOutcome
            if let probeEmbedding = prepared.embedding {
                let retained = try members.map { try $0.embedding() }
                guard try lease.indexData() == fixtureIdentificationIndex(retained), probe.fixtureID != nil else {
                    throw IdentityError(.featureFormatUnsupported)
                }
                let scores = try Dictionary(uniqueKeysWithValues: snapshot.members.indices.map {
                    (snapshot.members[$0].ref.id, try identificationCosine(probeEmbedding, retained[$0]))
                })
                candidates = try rankIdentificationCandidates(snapshot: snapshot, scores: scores, topK: options.topK)
                outcome = candidates.isEmpty ? .unknown : .candidates
            } else { outcome = .insufficientQuality }
            let scoreFields = try candidates.first.map {
                try IdentityScoreFields(score: $0.score, metric: snapshot.policy.scoreMetric, threshold: snapshot.policy.threshold)
            } ?? IdentityScoreFields()
            let thresholdFields = try prepared.embedding.map { _ in
                try IdentificationThresholdFields(metric: snapshot.policy.scoreMetric, threshold: snapshot.policy.threshold!)
            }
            let grouped = Dictionary(grouping: snapshot.members, by: { $0.candidate.id })
            let counts = grouped.keys.sorted().map {
                IdentificationEnrollmentCount(candidateRef: grouped[$0]![0].candidate, templateCount: grouped[$0]!.count)
            }
            let delta = clock.nowNanos.subtractingReportingOverflow(started)
            let value = IdentificationResult(outcome: outcome, candidates: candidates, scoreFields: scoreFields,
                thresholdFields: thresholdFields,
                quality: prepared.quality, galleryRevision: snapshot.ref.revision, galleryContentDigest: snapshot.contentDigest,
                aggregationPolicyId: snapshot.aggregationPolicyId, thresholdPolicyId: snapshot.policy.id,
                thresholdPolicyRevision: snapshot.policy.revision, galleryCandidateCount: counts.count,
                galleryTemplateCount: snapshot.members.count, enrollmentCounts: counts,
                fpirFnirGrouping: IdentificationGrouping(datasetRevision: snapshot.policy.datasetRevision!,
                    operatingPoint: snapshot.policy.operatingPoint!, enrollmentRule: snapshot.policy.enrollmentRule!,
                    gallerySizeRange: snapshot.policy.gallerySizeRange!, topK: options.topK),
                backendId: binding.space.backendId, backendVersion: binding.space.backendVersion, modelBinding: binding.space.modelBinding,
                inputRevision: probe.revision, optionsRevision: options.revision, preprocessingRevision: binding.space.preprocessRevision,
                sessionId: task.runId, runId: task.runId, durationNanos: delta.overflow ? Int64.max : max(0, delta.partialValue))
            consumed = true
            let result = try await indexStore.consumeReadLease(lease) { try task.complete(value) }
            for member in members { await member.close() }
            await lease.close()
            return result
        } catch {
            var failure: Error = error
            if !consumed {
                do { let _: Void = try await indexStore.consumeReadLease(lease) { throw error } }
                catch { failure = error }
            }
            let diagnostic = IdentityError((failure as? IdentityError)?.code ?? .backendUnavailable,
                backendId: binding.space.backendId, modelBinding: binding.space.modelBinding,
                policyId: snapshot.policy.id, sessionId: task.runId)
            task.fail(diagnostic)
            for member in members { await member.close() }
            await lease.close()
            throw diagnostic
        }
    }
}

/// Bounded internal synthetic format. It is not a production index contract.
/// Query validates it against the task's retained template leases before scoring.
func fixtureIdentificationIndex(_ embeddings: [IdentityEmbedding]) throws -> Data {
    guard let first = embeddings.first, embeddings.count <= 4_096,
          first.embeddingSpace.dimension <= 4_096,
          embeddings.allSatisfy({ $0.embeddingSpace == first.embeddingSpace }) else { throw IdentityError(.featureFormatUnsupported) }
    var bytes = Data("ODAI-FIXTURE-IDX-1".utf8)
    for embedding in embeddings {
        for value in embedding.values {
            var bits = value.bitPattern.littleEndian
            withUnsafeBytes(of: &bits) { bytes.append(contentsOf: $0) }
        }
    }
    return bytes
}

private func identificationCosine(_ lhs: IdentityEmbedding, _ rhs: IdentityEmbedding) throws -> Double {
    guard lhs.embeddingSpace == rhs.embeddingSpace else { throw IdentityError(.embeddingSpaceMismatch) }
    var dot = 0.0, left = 0.0, right = 0.0
    for (x, y) in zip(lhs.values, rhs.values) {
        let a = Double(x), b = Double(y)
        dot += a * b; left += a * a; right += b * b
    }
    guard left > 0, right > 0 else { throw IdentityError(.invalidInput) }
    let score = dot / (sqrt(left) * sqrt(right))
    guard score.isFinite else { throw IdentityError(.invalidInput) }
    return min(1, max(-1, score))
}

/// Pure ranking only. The query engine must own authorization, quality, leases,
/// and final publication; this helper cannot authorize a snapshot or a result.
func rankIdentificationCandidates(snapshot: GallerySnapshotRecord, scores: [String: Double],
                                  topK: Int = 32) throws -> [IdentificationCandidate] {
    guard (1...32).contains(topK), !snapshot.members.isEmpty,
          Set(scores.keys) == Set(snapshot.members.map { $0.ref.id }),
          scores.values.allSatisfy({ $0.isFinite && (-1...1).contains($0) }) else {
        throw IdentityError(.invalidInput)
    }
    let policy = snapshot.policy
    try policy.validate(embeddingSpace: policy.embeddingSpace, kind: .identification,
        epoch: snapshot.operationGrant.authorizationEpoch, gallerySize: Set(snapshot.members.map { $0.candidate.id }).count)
    guard snapshot.aggregationPolicyId == "subject-max-v1", policy.scoreMetric == "cosine",
          policy.scoreDirection == .higherIsBetter else { throw IdentityError(.unsupportedOption, policyId: policy.id) }
    var maxima: [String: (CandidateRef, Double)] = [:]
    for member in snapshot.members {
        let score = scores[member.ref.id]!
        if maxima[member.candidate.id].map({ score > $0.1 }) ?? true {
            maxima[member.candidate.id] = (member.candidate, score)
        }
    }
    let threshold = policy.threshold!
    return maxima.values.filter { $0.1 >= threshold }.sorted {
        if $0.1 != $1.1 { return $0.1 > $1.1 }
        return $0.0.id.utf8.lexicographicallyPrecedes($1.0.id.utf8)
    }.prefix(topK).enumerated().map {
        IdentificationCandidate(candidateRef: $0.element.0, score: $0.element.1, rank: $0.offset + 1)
    }
}
