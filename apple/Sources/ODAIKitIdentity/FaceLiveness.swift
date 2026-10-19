import Foundation
import CryptoKit
import Security
import ODAIKitCore

public struct LivenessFrame: Sendable {
    public let encodedFrame: Data
    public let timestampNanos: Int64
    public let revision: String
    public let challenge: LivenessChallenge?

    public init(encodedFrame: Data, timestampNanos: Int64, revision: String, challenge: LivenessChallenge? = nil) throws {
        guard !encodedFrame.isEmpty, timestampNanos >= 0 else { throw IdentityError(.invalidInput) }
        try identityToken(revision)
        self.encodedFrame = encodedFrame; self.timestampNanos = timestampNanos
        self.revision = revision; self.challenge = challenge
    }
}
public enum LivenessOutcome: String, Sendable { case live, spoof, insufficientQuality, challengeFailed }
public enum LivenessSessionState: String, Sendable { case created, running, challengeIssued, evaluating, completed, failed, cancelled, closed }

/// Issued by one session; echo the entire value on frames responding to it.
public struct LivenessChallenge: Sendable, Equatable {
    public let id: String
    public let nonce: String
    public let sessionId: String
    public let purpose: String
    public let authorizationEpoch: Int64
    public let issuedAtNanos: Int64
    public let expiresAtNanos: Int64
    public init(id: String, nonce: String, sessionId: String, purpose: String, authorizationEpoch: Int64, issuedAtNanos: Int64, expiresAtNanos: Int64) throws {
        try identityToken(id); try identityToken(nonce); try identityToken(sessionId); try identityToken(purpose)
        guard authorizationEpoch >= 0, issuedAtNanos >= 0, expiresAtNanos > issuedAtNanos else { throw IdentityError(.invalidInput) }
        self.id = id; self.nonce = nonce; self.sessionId = sessionId; self.purpose = purpose
        self.authorizationEpoch = authorizationEpoch; self.issuedAtNanos = issuedAtNanos; self.expiresAtNanos = expiresAtNanos
    }
}
public struct LivenessDecision: Sendable {
    public let outcome: LivenessOutcome
    public let quality: IdentityQuality
    public let scoreFields: IdentityScoreFields
    public init(outcome: LivenessOutcome, quality: IdentityQuality, scoreFields: IdentityScoreFields? = nil) throws {
        self.outcome = outcome; self.quality = quality
        self.scoreFields = try scoreFields ?? IdentityScoreFields()
    }
}

/// One backend invocation at a time. Nil requests another frame, never a live result.
/// Implementations must scope any temporal tracking to the supplied session signal.
public protocol LivenessBackend: AnyObject, Sendable {
    var backendId: String { get }
    var backendVersion: String { get }
    var modelBinding: ModelBinding { get }
    func availability() -> CapabilityAvailability
    func evaluate(frame: LivenessFrame, challenge: LivenessChallenge?, stopSignal: IdentityStopSignal) throws -> LivenessDecision?
    func close()
}
public struct LivenessOptions: Sendable {
    public let policy: PADPolicy
    public let purpose: String
    public let revision: String
    public init(policy: PADPolicy, purpose: String, revision: String) throws {
        try identityToken(purpose); try identityToken(revision)
        self.policy = policy; self.purpose = purpose; self.revision = revision
    }
}
public struct LivenessResult: Sendable {
    public let outcome: LivenessOutcome
    public let scoreFields: IdentityScoreFields
    public let quality: IdentityQuality
    public let policy: PADPolicyRef
    public let backendId: String
    public let backendVersion: String
    public let modelBinding: ModelBinding
    public let acceptedFrameCount: Int
    public let droppedFrameCount: Int
    public let staleFrameCount: Int
    public let sessionId: String
    public let runId: String
    public let captureDigest: String
    public let inputRevision: String?
    public let optionsRevision: String
    public let durationNanos: Int64
    public let proof: LivenessProof?
}
public enum LivenessEvent: Sendable {
    case started, challengeIssued(LivenessChallenge), frameAccepted, frameDropped, frameStale
    case completed(LivenessResult), failed(IdentityErrorCode), cancelled, closed
}

/// Synchronous bounded frame admission; a single worker owns native work and final consume.
public final class LivenessSession: @unchecked Sendable {
    public let sessionId = UUID().uuidString.lowercased()
    private let store: IdentityStore
    private let ticket: IdentityLivenessOperation
    private let backend: any LivenessBackend
    private let options: LivenessOptions
    private let clock: any IdentityMonotonicClock
    private let wallClock: any IdentityWallClock
    private let lock = NSLock()
    private let queue = LatestFrameQueue<LivenessFrame>()
    // Local failures can stop native work without converting the store ticket
    // into caller cancellation. The original ticket remains the authority gate.
    private let nativeSignal = IdentityStopSignal()
    private let wake: AsyncStream<Void>
    private let wakeSender: AsyncStream<Void>.Continuation
    private var worker: Task<LivenessResult, Error>?
    private var timer: Task<Void, Never>?
    private var stopDelivery: Task<Void, Never>?
    private var nativeDelivery: Task<Void, Never>?
    private var state: LivenessSessionState = .created
    private var outcome: TaskTerminal?
    private var resultValue: LivenessResult?
    // Lifecycle events are durable; high frequency frame telemetry is coalesced
    // and bounded so a caller who never polls cannot retain an unbounded queue.
    private var events: [LivenessEvent] = []
    private var eventWaiters: [CheckedContinuation<LivenessEvent?, Never>] = []
    private var closing = false
    private var finishRequested = false
    private var pendingError: IdentityErrorCode?
    private var challenge: LivenessChallenge?
    private var startedAt: Int64?
    private var deadline: Int64?
    private var lastTimestamp: Int64?
    private var accepted = 0
    private var stale = 0
    private var proofUsed = false

    public var currentState: LivenessSessionState { locked { state } }
    public var terminal: TaskTerminal? { locked { outcome } }

    fileprivate init(store: IdentityStore, ticket: IdentityLivenessOperation, backend: any LivenessBackend,
                     options: LivenessOptions, clock: any IdentityMonotonicClock, wallClock: any IdentityWallClock) {
        self.store = store; self.ticket = ticket; self.backend = backend
        self.options = options; self.clock = clock; self.wallClock = wallClock
        var sender: AsyncStream<Void>.Continuation!
        wake = AsyncStream(bufferingPolicy: .bufferingNewest(1)) { sender = $0 }
        wakeSender = sender
    }

    fileprivate func launch() {
        locked { worker = Task.detached { [self] in try await run() } }
    }

    public func start() throws {
        try locked {
            guard !closing, outcome == nil else { throw diagnostic(.closed) }
            guard state == .created else { throw diagnostic(.busy) }
            try ticket.stopSignal.check()
            let now = clock.nowNanos
            let end = now.addingReportingOverflow(options.policy.maximumDurationNanos)
            guard now >= 0, !end.overflow else { throw diagnostic(.invalidInput) }
            var issued: LivenessChallenge?
            if options.policy.mode == .active {
                issued = try LivenessChallenge(id: UUID().uuidString.lowercased(), nonce: secureNonce(),
                    sessionId: sessionId, purpose: options.purpose, authorizationEpoch: ticket.grant.authorizationEpoch,
                    issuedAtNanos: now, expiresAtNanos: end.partialValue)
            }
            startedAt = now; deadline = end.partialValue; challenge = issued
            state = .running; emitLocked(.started)
            if let issued { state = .challengeIssued; emitLocked(.challengeIssued(issued)) }
            let duration = UInt64(options.policy.maximumDurationNanos)
            timer = Task.detached { [weak self] in
                do { try await Task.sleep(nanoseconds: duration) }
                catch { return }
                self?.requestFailure(self?.options.policy.mode == .active ? .challengeExpired : .sessionTimeout)
            }
        }
        wakeSender.yield(())
    }

    @discardableResult public func submitFrame(_ frame: LivenessFrame) -> Bool {
        locked {
            guard !closing, outcome == nil, !finishRequested, pendingError == nil,
                  let start = startedAt, let end = deadline else { return false }
            if frame.timestampNanos < start || frame.timestampNanos <= (lastTimestamp ?? -1) {
                stale += 1; emitLocked(.frameStale); return false
            }
            let now = clock.nowNanos
            guard now >= start, now < end, frame.timestampNanos < end, frame.timestampNanos <= now else {
                pendingError = challenge == nil ? .sessionTimeout : .challengeExpired
                stopNativeLocked()
                wakeSender.yield(()); return false
            }
            guard frame.challenge == challenge else {
                pendingError = .invalidChallenge; stopNativeLocked(); wakeSender.yield(()); return false
            }
            lastTimestamp = frame.timestampNanos; accepted += 1
            let drops = queue.droppedCount
            queue.offer(frame)
            emitLocked(.frameAccepted)
            if queue.droppedCount != drops { emitLocked(.frameDropped) }
            wakeSender.yield(())
            return true
        }
    }

    public func finish() {
        locked {
            guard outcome == nil, !closing else { return }
            guard startedAt != nil else { pendingError = .invalidInput; wakeSender.yield(()); return }
            finishRequested = true; wakeSender.yield(())
        }
    }
    public func cancel() { requestStop(.cancelled) }

    public func close() async {
        locked { closing = true; queue.close() }
        requestStop(.closed)
        if IdentityStopHandlerContext.current?.isActive == true { return }
        _ = try? await currentWorker().value
        await currentDelivery()?.value
        markClosed()
    }

    public func result() async throws -> LivenessResult {
        if IdentityStopHandlerContext.current?.isActive == true {
            try ticket.stopSignal.check()
            throw diagnostic(.busy)
        }
        return try await withTaskCancellationHandler {
            if Task.isCancelled { cancel() }
            return try await currentWorker().value
        } onCancel: { self.cancel() }
    }
    public func nextEvent() async -> LivenessEvent? {
        await withCheckedContinuation { continuation in
            locked {
                if !events.isEmpty { continuation.resume(returning: events.removeFirst()) }
                else if state == .closed { continuation.resume(returning: nil) }
                else { eventWaiters.append(continuation) }
            }
        }
    }

    /// Explicit one-time validation for a host-composed transaction. This does not
    /// automatically run verification/identification or turn a PAD fixture into identity.
    public func consumeProof(_ proof: LivenessProof, captureDigest: String, purpose: String) async throws {
        try locked { try checkProof(proof, captureDigest: captureDigest, purpose: purpose) }
        let authorization = try await store.beginLivenessOperation(grant: ticket.grant)
        defer { wakeSender.yield(()) }
        try await store.finishLivenessOperation(authorization) {
            try self.locked {
                try self.checkProof(proof, captureDigest: captureDigest, purpose: purpose)
                self.proofUsed = true
            }
        }
    }

    private func checkProof(_ proof: LivenessProof, captureDigest: String, purpose: String) throws {
        guard !closing, state != .closed else { throw diagnostic(.closed) }
        guard resultValue?.proof == proof, !proofUsed, proof.purpose == purpose,
              proof.captureDigest == captureDigest, wallClock.nowMillis >= proof.issuedAtMillis,
              wallClock.nowMillis < proof.expiresAtMillis else { throw diagnostic(.invalidChallenge) }
    }
    private func requestStop(_ reason: IdentityStopReason) {
        locked {
            guard outcome == nil else { return }
            if let deliver = ticket.stopSignal.prepareStopDelivery(reason) {
                stopDelivery = Task.detached { deliver() }
            }
            wakeSender.yield(())
        }
    }
    private func requestFailure(_ code: IdentityErrorCode) {
        locked {
            guard outcome == nil, pendingError == nil else { return }
            pendingError = code; stopNativeLocked(); wakeSender.yield(())
        }
    }
    private func stopNativeLocked() {
        if let action = nativeSignal.prepareStopDelivery(.cancelled) {
            nativeDelivery = Task.detached { action() }
        }
    }

    private func run() async throws -> LivenessResult {
        let registration = ticket.stopSignal.registerStopHandler { [ticket, nativeSignal, wakeSender] in
            wakeSender.yield(())
            do { try ticket.stopSignal.check() }
            catch let error as IdentityError {
                nativeSignal.requestStop(error.code == .authorizationRevoked ? .authorizationRevoked : (error.code == .closed ? .closed : .cancelled))
            } catch { nativeSignal.requestStop(.cancelled) }
        }
        var evaluation: Result<LivenessResult, Error>
        do { evaluation = .success(try await evaluateFrames()) }
        catch { evaluation = .failure(error) }
        // Keep the original writer ticket until local native stop delivery drains.
        await nativeSignal.drain()
        // Exactly one attempt consumes the original ticket. No error path reconsumes it.
        let final: Result<LivenessResult, Error>
        do {
            final = .success(try await store.finishLivenessOperation(ticket) {
                try self.locked {
                    try self.ticket.stopSignal.check()
                    if let code = self.pendingError { throw self.diagnostic(code) }
                    if let end = self.deadline, self.clock.nowNanos >= end {
                        throw self.diagnostic(self.options.policy.mode == .active ? .challengeExpired : .sessionTimeout)
                    }
                        try self.options.policy.validate(backendId: self.backend.backendId,
                            backendVersion: self.backend.backendVersion,
                            modelBinding: self.backend.modelBinding,
                            mode: self.options.policy.mode,
                            epoch: self.ticket.grant.authorizationEpoch)
                    let value = try evaluation.get()
                    self.outcome = .completed; self.state = .completed; self.resultValue = value
                    self.emitLocked(.completed(value)); self.queue.close()
                    return value
                }
            })
        } catch {
            let error = diagnostic((error as? IdentityError)?.code ?? .backendUnavailable)
            locked {
                outcome = error.code == .cancelled ? .cancelled : .failed
                state = error.code == .cancelled ? .cancelled : .failed
                emitLocked(error.code == .cancelled ? .cancelled : .failed(error.code))
            }
            final = .failure(error)
        }
        locked { timer?.cancel(); queue.close() }
        registration.close(); wakeSender.finish()
        await ticket.stopSignal.drain()
        await nativeSignal.drain()
        await locked({ nativeDelivery })?.value
        await currentDelivery()?.value
        if locked({ closing }) { markClosed() }
        return try final.get()
    }

    private func evaluateFrames() async throws -> LivenessResult {
        var digest = SHA256()
        var processed = 0
        var lastRevision: String?
        for await _ in wake {
            while true {
                try ticket.stopSignal.check()
                try locked { if let code = pendingError { throw diagnostic(code) } }
                if let end = locked({ deadline }), clock.nowNanos >= end { throw diagnostic(options.policy.mode == .active ? .challengeExpired : .sessionTimeout) }
                guard let frame = queue.next() else { break }
                defer { queue.complete() }
                locked { state = .evaluating }
                digest.update(data: frame.encodedFrame); processed += 1; lastRevision = frame.revision
                try options.policy.validate(backendId: backend.backendId, backendVersion: backend.backendVersion,
                    modelBinding: backend.modelBinding, mode: options.policy.mode, epoch: ticket.grant.authorizationEpoch)
                let decision = try backend.evaluate(frame: frame, challenge: locked { challenge }, stopSignal: nativeSignal)
                try ticket.stopSignal.check()
                try options.policy.validate(backendId: backend.backendId, backendVersion: backend.backendVersion,
                    modelBinding: backend.modelBinding, mode: options.policy.mode, epoch: ticket.grant.authorizationEpoch)
                if let end = locked({ deadline }), clock.nowNanos >= end { throw diagnostic(options.policy.mode == .active ? .challengeExpired : .sessionTimeout) }
                if let decision {
                    guard decision.quality.faceCount <= 1 else { throw diagnostic(.multipleFaces) }
                    let qualityOK = decision.quality.faceCount == 1 && (decision.quality.score ?? -1) >= options.policy.qualityFloor!
                    if !qualityOK || decision.outcome == .insufficientQuality {
                        return try makeResult(.insufficientQuality, quality: decision.quality, fields: IdentityScoreFields(),
                            digest: digest, processed: processed, inputRevision: lastRevision)
                    }
                    // A premature positive never satisfies the minimum capture policy.
                    if decision.outcome == .live && processed < options.policy.minimumFrames { continue }
                    try validateFields(decision.scoreFields)
                    return try makeResult(decision.outcome, quality: decision.quality, fields: decision.scoreFields,
                        digest: digest, processed: processed, inputRevision: lastRevision)
                }
            }
            if locked({ finishRequested }) {
                return try makeResult(.insufficientQuality, quality: IdentityQuality(faceCount: 0),
                    fields: IdentityScoreFields(), digest: digest, processed: processed, inputRevision: lastRevision)
            }
        }
        throw diagnostic(.closed)
    }

    private func validateFields(_ fields: IdentityScoreFields) throws {
        if let metric = options.policy.scoreMetric {
            guard fields.score != nil, fields.metric == metric, fields.threshold == options.policy.threshold else {
                throw diagnostic(.thresholdUnverified)
            }
        } else if fields.score != nil || fields.metric != nil || fields.threshold != nil {
            throw diagnostic(.thresholdUnverified)
        }
    }
    private func makeResult(_ result: LivenessOutcome, quality: IdentityQuality, fields: IdentityScoreFields,
                            digest: SHA256, processed: Int, inputRevision: String?) throws -> LivenessResult {
        let capture = digest.finalize().map { String(format: "%02x", $0) }.joined()
        let ref = try PADPolicyRef(id: options.policy.id, revision: options.policy.revision)
        let now = wallClock.nowMillis
        let expiry = now.addingReportingOverflow(30_000)
        let proof: LivenessProof?
        if result == .live {
            guard !expiry.overflow else { throw diagnostic(.invalidInput) }
            proof = try LivenessProof(id: UUID().uuidString.lowercased(), issuerId: options.policy.backendId, sessionId: sessionId,
                nonce: locked { challenge?.nonce } ?? secureNonce(), purpose: options.purpose, captureDigest: capture,
                policy: ref, modelBinding: options.policy.modelBinding, authorizationEpoch: ticket.grant.authorizationEpoch,
                issuedAtMillis: now, expiresAtMillis: expiry.partialValue)
        } else { proof = nil }
        return locked {
            finishRequested = true
            let elapsed = clock.nowNanos.subtractingReportingOverflow(startedAt ?? clock.nowNanos)
            return LivenessResult(outcome: result, scoreFields: fields, quality: quality, policy: ref,
                backendId: options.policy.backendId, backendVersion: options.policy.backendVersion, modelBinding: options.policy.modelBinding,
                acceptedFrameCount: accepted, droppedFrameCount: queue.droppedCount, staleFrameCount: stale,
                sessionId: sessionId, runId: sessionId, captureDigest: capture, inputRevision: inputRevision,
                optionsRevision: options.revision, durationNanos: elapsed.overflow ? Int64.max : max(0, elapsed.partialValue), proof: proof)
        }
    }
    private func emitLocked(_ event: LivenessEvent) {
        if !eventWaiters.isEmpty { eventWaiters.removeFirst().resume(returning: event) }
        else {
            switch event {
            case .frameAccepted, .frameDropped, .frameStale:
                if events.count < 16 { events.append(event) }
            default: events.append(event)
            }
        }
    }
    private func markClosed() {
        locked {
            guard state != .closed else { return }
            state = .closed; emitLocked(.closed)
            let waiters = eventWaiters; eventWaiters.removeAll()
            for waiter in waiters { waiter.resume(returning: nil) }
        }
    }
    private func currentWorker() -> Task<LivenessResult, Error> { locked { worker! } }
    private func currentDelivery() -> Task<Void, Never>? { locked { stopDelivery } }
    private func diagnostic(_ code: IdentityErrorCode) -> IdentityError {
        IdentityError(code, backendId: options.policy.backendId, modelBinding: options.policy.modelBinding, policyId: options.policy.id, sessionId: sessionId)
    }
    private func locked<T>(_ body: () throws -> T) rethrows -> T { lock.lock(); defer { lock.unlock() }; return try body() }
}

public actor FaceLiveness {
    private let store: IdentityStore
    private let registry: IdentityBackendRegistry<any LivenessBackend>
    private let backends: [any LivenessBackend]
    private let clock: any IdentityMonotonicClock
    private let wallClock: any IdentityWallClock
    private var sessions: [LivenessSession] = []
    private var admitting = false
    private var admissionWaiters: [CheckedContinuation<Void, Never>] = []
    private var closed = false
    private var closing: Task<Void, Never>?

    public init(identityStore: IdentityStore, backends: [any LivenessBackend], defaultBackendId: String? = nil,
                monotonicClock: any IdentityMonotonicClock = SystemIdentityMonotonicClock(),
                wallClock: any IdentityWallClock = SystemIdentityWallClock()) throws {
        guard let first = backends.first else { throw IdentityError(.backendNotRegistered) }
        store = identityStore; self.backends = backends; clock = monotonicClock; self.wallClock = wallClock
        registry = try IdentityBackendRegistry(defaultBackendId: defaultBackendId ?? first.backendId,
            registrations: backends.map { IdentityBackendRegistration(backendId: $0.backendId, backend: $0) })
    }
    public func openLivenessSession(grant: AuthorizationGrant, options: LivenessOptions,
                                    backend: IdentityBackendSelection = .default) async throws -> LivenessSession {
        guard !closed else { throw IdentityError(.closed) }
        guard !admitting, sessions.allSatisfy({ $0.currentState == .closed }) else { throw IdentityError(.busy) }
        let selected = try registry.select(backend)
        guard selected.availability().status == .available else {
            throw IdentityError(.backendUnavailable, backendId: selected.backendId, modelBinding: selected.modelBinding)
        }
        guard options.purpose == grant.purpose else { throw IdentityError(.authorizationRequired) }
        do {
            try options.policy.validate(backendId: selected.backendId, backendVersion: selected.backendVersion,
                modelBinding: selected.modelBinding, mode: options.policy.mode, epoch: grant.authorizationEpoch)
        } catch let error as IdentityError {
            throw IdentityError(error.code, backendId: selected.backendId, modelBinding: selected.modelBinding,
                                policyId: error.policyId ?? options.policy.id)
        }
        admitting = true
        defer {
            admitting = false
            let waiters = admissionWaiters; admissionWaiters.removeAll()
            for waiter in waiters { waiter.resume() }
        }
        let ticket: IdentityLivenessOperation
        do { ticket = try await store.beginLivenessOperation(grant: grant) }
        catch let error as IdentityError {
            throw IdentityError(error.code, backendId: selected.backendId, modelBinding: selected.modelBinding,
                                policyId: error.policyId ?? options.policy.id)
        }
        let session = LivenessSession(store: store, ticket: ticket, backend: selected, options: options, clock: clock, wallClock: wallClock)
        sessions.removeAll { $0.currentState == .closed }
        sessions.append(session); session.launch()
        if closed { await session.close() }
        return session
    }
    public func close() async {
        closed = true
        if admitting { await withCheckedContinuation { admissionWaiters.append($0) } }
        if closing == nil {
            let sessions = sessions; let backends = backends
            closing = Task.detached {
                for session in sessions { await session.close() }
                for backend in backends { backend.close() }
            }
        }
        if IdentityStopHandlerContext.current?.isActive == true { return }
        await closing?.value
    }
}

private func secureNonce() throws -> String {
    var bytes = [UInt8](repeating: 0, count: 32)
    guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { throw IdentityError(.backendUnavailable) }
    return bytes.map { String(format: "%02x", $0) }.joined()
}

