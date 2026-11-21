import Foundation
import ODAIKitCore

public struct VerificationOptions: Sendable {
    public let policy: ThresholdPolicy
    public let revision: String
    public init(policy: ThresholdPolicy, revision: String) throws {
        try identityToken(revision)
        self.policy = policy; self.revision = revision
    }
}

public enum VerificationOutcome: String, Sendable { case match, nonMatch, insufficientQuality }

/// Decision metadata only. The decrypted template and probe embedding stay task-scoped.
public struct VerificationResult: Sendable {
    public let outcome: VerificationOutcome
    public let scoreFields: IdentityScoreFields
    public let quality: IdentityQuality
    public let templateRevision: String
    public let thresholdPolicyId: String
    public let thresholdPolicyRevision: String
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
public final class VerificationTask: @unchecked Sendable {
    public let runId: String
    fileprivate let signal = IdentityStopSignal()
    private let lock = NSLock()
    private var outcome: TaskTerminal?
    private var worker: Task<VerificationResult, Error>?
    private var delivery: Task<Void, Never>?

    fileprivate init(runId: String) { self.runId = runId }
    public var terminal: TaskTerminal? { lock.lock(); defer { lock.unlock() }; return outcome }
    public func cancel() { requestStop(.cancelled) }
    public func close() async {
        cancel()
        if IdentityStopHandlerContext.current?.isActive == true { return }
        await drain()
    }
    public func result() async throws -> VerificationResult {
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
    fileprivate func start(_ operation: @escaping @Sendable () async throws -> VerificationResult) {
        lock.lock(); defer { lock.unlock() }
        worker = Task.detached { try await operation() }
    }
    fileprivate func complete(_ value: VerificationResult) throws -> VerificationResult {
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
    private func currentWorker() -> Task<VerificationResult, Error> {
        lock.lock(); defer { lock.unlock() }; return worker!
    }
    private func currentDelivery() -> Task<Void, Never>? {
        lock.lock(); defer { lock.unlock() }; return delivery
    }
}

/// Capability-scoped backend selection over the store's serial authorization gate.
public actor FaceVerification {
    private let identityStore: IdentityStore
    private let registry: IdentityBackendRegistry<any IdentityBackend>
    private let backends: [any IdentityBackend]
    private let monotonicClock: any IdentityMonotonicClock
    private var active: VerificationTask?
    private var admitting = false
    private var admissionWaiters: [CheckedContinuation<Void, Never>] = []
    private var closed = false
    private var closing: Task<Void, Never>?

    public init(identityStore: IdentityStore, defaultBackendId: String, backends: [any IdentityBackend],
                monotonicClock: any IdentityMonotonicClock = SystemIdentityMonotonicClock()) throws {
        self.identityStore = identityStore; self.backends = backends; self.monotonicClock = monotonicClock
        registry = try IdentityBackendRegistry(defaultBackendId: defaultBackendId,
            registrations: backends.map { IdentityBackendRegistration(backendId: $0.backendId, backend: $0) })
    }

    public func verify(probe: IdentityProbe, templateRef: TemplateRef, grant: AuthorizationGrant,
                       options: VerificationOptions, backend: IdentityBackendSelection = .default) async throws -> VerificationTask {
        guard !closed else { throw IdentityError(.closed) }
        guard active == nil, !admitting else { throw IdentityError(.busy) }
        let selected = try registry.select(backend)
        let binding = IdentityEnrollmentBinding(selected)
        try binding.validate(selected)
        guard selected.availability().status == .available else { throw IdentityError(.backendUnavailable, backendId: selected.backendId) }
        do {
            try options.policy.validate(embeddingSpace: binding.space, kind: .verification, epoch: grant.authorizationEpoch)
        } catch let error as IdentityError {
            throw IdentityError(error.code, backendId: selected.backendId, modelBinding: selected.modelBinding,
                                policyId: error.policyId ?? options.policy.id)
        }
        guard options.policy.scoreMetric == "cosine", options.policy.scoreDirection == .higherIsBetter else {
            throw IdentityError(.unsupportedOption, backendId: selected.backendId, modelBinding: selected.modelBinding,
                                policyId: options.policy.id)
        }
        admitting = true
        defer {
            admitting = false
            let waiters = admissionWaiters; admissionWaiters.removeAll()
            for waiter in waiters { waiter.resume() }
        }
        let ticket: IdentityOperationTicket
        do { ticket = try await identityStore.beginVerification(templateRef, grant: grant) }
        catch let error as IdentityError {
            throw IdentityError(error.code, backendId: binding.space.backendId, modelBinding: binding.space.modelBinding,
                                policyId: options.policy.id)
        }
        let task = VerificationTask(runId: ticket.id.uuidString.lowercased())
        if closed { task.requestStop(.closed) }
        if Task.isCancelled { task.cancel() }
        active = task
        let clock = monotonicClock
        let started = clock.nowNanos
        task.start { [self] in
            let result = await Result { () async throws -> VerificationResult in
                try await self.execute(task, ticket: ticket, probe: probe, template: templateRef,
                    grant: grant, options: options, backend: selected, binding: binding, started: started)
            }
            await ticket.stopSignal.drain()
            await task.drainStopDelivery()
            await self.didFinish(task)
            return try result.get()
        }
        return task
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

    private func didFinish(_ task: VerificationTask) {
        if active === task { active = nil }
    }

    private nonisolated func execute(_ task: VerificationTask, ticket: IdentityOperationTicket,
        probe: IdentityProbe, template: TemplateRef, grant: AuthorizationGrant, options: VerificationOptions,
        backend: any IdentityBackend, binding: IdentityEnrollmentBinding, started: Int64) async throws -> VerificationResult {
        let registration = task.signal.registerStopHandler {
            do { try task.signal.check() }
            catch let error as IdentityError { ticket.stopSignal.requestStop(error.code == .closed ? .closed : .cancelled) }
            catch { ticket.stopSignal.requestStop(.cancelled) }
        }
        defer { registration.close() }
        var lease: IdentityTemplateLease?
        var consumed = false
        do {
            try task.signal.check()
            lease = try await identityStore.acquireTemplateLease(template, grant: grant, scope: .verification, embeddingSpace: binding.space)
            let retained = try lease!.embedding()
            let qualityOptions = try EnrollmentOptions(qualityFloor: options.policy.qualityFloor!, qualityPolicyId: options.policy.id, revision: options.revision)
            let prepared = try binding.prepare(probe, options: qualityOptions, backend: backend, signal: ticket.stopSignal)
            let score = try prepared.embedding.map { try verificationCosine($0, retained) }
            let fields = try score.map { try IdentityScoreFields(score: $0, metric: options.policy.scoreMetric, threshold: options.policy.threshold) } ?? IdentityScoreFields()
            let outcome: VerificationOutcome = score.map { $0 >= options.policy.threshold! ? .match : .nonMatch } ?? .insufficientQuality
            let now = monotonicClock.nowNanos
            let elapsed = now.subtractingReportingOverflow(started)
            let value = VerificationResult(outcome: outcome, scoreFields: fields, quality: prepared.quality,
                templateRevision: template.revision, thresholdPolicyId: options.policy.id, thresholdPolicyRevision: options.policy.revision,
                backendId: binding.space.backendId, backendVersion: binding.space.backendVersion, modelBinding: binding.space.modelBinding,
                inputRevision: probe.revision, optionsRevision: options.revision, preprocessingRevision: binding.space.preprocessRevision,
                sessionId: task.runId, runId: task.runId, durationNanos: elapsed.overflow ? Int64.max : max(0, elapsed.partialValue))
            consumed = true
            let result = try await identityStore.finishVerification(ticket) { try task.complete(value) }
            await lease?.close()
            return result
        } catch {
            var failure: Error = error
            if !consumed {
                do { try await identityStore.finishOperation(ticket, backendError: error) }
                catch { failure = error }
            }
            let code = (failure as? IdentityError)?.code ?? .backendUnavailable
            let diagnostic = IdentityError(code, backendId: binding.space.backendId, modelBinding: binding.space.modelBinding,
                                           policyId: options.policy.id, sessionId: task.runId)
            task.fail(diagnostic)
            await lease?.close()
            throw diagnostic
        }
    }
}

private func verificationCosine(_ lhs: IdentityEmbedding, _ rhs: IdentityEmbedding) throws -> Double {
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
