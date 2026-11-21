package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.LatestFrameQueue
import io.ondeviceai.core.TaskTerminal
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch

/** Owned bytes; camera callers cannot mutate an admitted frame. */
class LivenessFrame(encodedFrame: ByteArray, val timestampNanos: Long, val revision: String, val challenge: LivenessChallenge? = null) {
    private val bytes = encodedFrame.clone()
    val encodedFrame: ByteArray get() = bytes.clone()
    init { token(revision); if (bytes.isEmpty() || timestampNanos < 0) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
}
enum class LivenessOutcome { LIVE, SPOOF, INSUFFICIENT_QUALITY, CHALLENGE_FAILED }
enum class LivenessSessionState { CREATED, RUNNING, CHALLENGE_ISSUED, EVALUATING, COMPLETED, FAILED, CANCELLED, CLOSED }
@ConsistentCopyVisibility
data class LivenessChallenge internal constructor(
    val id: String, val nonce: String, val sessionId: String, val purpose: String,
    val authorizationEpoch: Long, val issuedAtNanos: Long, val expiresAtNanos: Long
) {
    init {
        listOf(id, nonce, sessionId, purpose).forEach(::token)
        if (authorizationEpoch < 0 || issuedAtNanos < 0 || expiresAtNanos <= issuedAtNanos) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }
    fun canonicalJSON(): String = IdentityContractJSON.encode(mapOf(
        "authorizationEpoch" to authorizationEpoch, "expiresAtNanos" to expiresAtNanos, "id" to id,
        "issuedAtNanos" to issuedAtNanos, "nonce" to nonce, "purpose" to purpose, "sessionId" to sessionId
    ))
    companion object {
        fun decodeJSON(json: String): LivenessChallenge {
            val f = fields(json, setOf("authorizationEpoch", "expiresAtNanos", "id", "issuedAtNanos", "nonce", "purpose", "sessionId"))
            return LivenessChallenge(f.string("id"), f.string("nonce"), f.string("sessionId"), f.string("purpose"),
                f.long("authorizationEpoch"), f.long("issuedAtNanos"), f.long("expiresAtNanos"))
        }
    }
}
data class LivenessDecision(val outcome: LivenessOutcome, val quality: IdentityQuality, val scoreFields: IdentityScoreFields = IdentityScoreFields())
/** Nil requests more frames. Temporal tracking is scoped to the supplied session signal. */
interface LivenessBackend {
    val backendId: String
    val backendVersion: String
    val modelBinding: ModelBinding
    fun availability(): CapabilityAvailability
    fun evaluate(frame: LivenessFrame, challenge: LivenessChallenge?, stopSignal: IdentityStopSignal): LivenessDecision?
    fun close()
}
class LivenessOptions(val policy: PADPolicy, val purpose: String, val revision: String) {
    init { token(purpose); token(revision) }
}
class LivenessResult internal constructor(
    val outcome: LivenessOutcome, val scoreFields: IdentityScoreFields, val quality: IdentityQuality,
    val policy: PADPolicyRef, val backendId: String, val backendVersion: String, val modelBinding: ModelBinding,
    val acceptedFrameCount: Int, val droppedFrameCount: Int, val staleFrameCount: Int,
    val sessionId: String, val runId: String, val captureDigest: String, val inputRevision: String?,
    val optionsRevision: String, val durationNanos: Long, val proof: LivenessProof?
)
sealed interface LivenessEvent {
    data object Started : LivenessEvent
    data class ChallengeIssued(val challenge: LivenessChallenge) : LivenessEvent
    data object FrameAccepted : LivenessEvent
    data object FrameDropped : LivenessEvent
    data object FrameStale : LivenessEvent
    data class Completed(val result: LivenessResult) : LivenessEvent
    data class Failed(val code: IdentityErrorCode) : LivenessEvent
    data object Cancelled : LivenessEvent
    data object Closed : LivenessEvent
}

/** One native worker and one latest pending frame; admission never waits for a backend. */
class LivenessSession internal constructor(
    private val store: IdentityStore, private val ticket: IdentityLivenessOperation,
    private val backend: LivenessBackend, private val options: LivenessOptions,
    private val clock: IdentityMonotonicClock, private val wallClock: IdentityWallClock
) : AutoCloseable {
    val sessionId: String = UUID.randomUUID().toString()
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private val completion = CountDownLatch(1)
    private val queue = LatestFrameQueue<LivenessFrame>()
    private val nativeSignal = IdentityStopSignal()
    private val events = ArrayDeque<LivenessEvent>()
    private var state = LivenessSessionState.CREATED
    private var terminalValue: TaskTerminal? = null
    private var resultValue: LivenessResult? = null
    private var failure: Throwable? = null
    private var closing = false
    private var finishRequested = false
    private var pendingError: IdentityErrorCode? = null
    private var challenge: LivenessChallenge? = null
    private var startedAt: Long? = null
    private var deadline: Long? = null
    private var lastTimestamp: Long? = null
    private var accepted = 0
    private var stale = 0
    private var proofUsed = false
    private var awakened = false
    private var timer: Thread? = null
    private var stopDelivery: CountDownLatch? = null
    private val worker = Thread({ runWorker() }, "odai-liveness-$sessionId").apply { isDaemon = true }

    val currentState: LivenessSessionState get() = synchronized(lock) { state }
    val terminal: TaskTerminal? get() = synchronized(lock) { terminalValue }
    internal fun launch() = worker.start()

    fun start() {
        synchronized(lock) {
            if (closing || terminalValue != null) throw diagnostic(IdentityErrorCode.CLOSED)
            if (state != LivenessSessionState.CREATED) throw diagnostic(IdentityErrorCode.BUSY)
            ticket.stopSignal.check()
            val now = clock.nowNanos
            val end = try { Math.addExact(now, options.policy.maximumDurationNanos) }
                catch (_: ArithmeticException) { throw diagnostic(IdentityErrorCode.INVALID_INPUT) }
            if (now < 0) throw diagnostic(IdentityErrorCode.INVALID_INPUT)
            val issued = if (options.policy.mode == PADMode.ACTIVE) LivenessChallenge(UUID.randomUUID().toString(), livenessNonce(),
                sessionId, options.purpose, ticket.grant.authorizationEpoch, now, end) else null
            startedAt = now; deadline = end; challenge = issued
            state = LivenessSessionState.RUNNING; emitLocked(LivenessEvent.Started)
            if (issued != null) { state = LivenessSessionState.CHALLENGE_ISSUED; emitLocked(LivenessEvent.ChallengeIssued(issued)) }
            timer = Thread({
                try {
                    val duration = options.policy.maximumDurationNanos
                    Thread.sleep(duration / 1_000_000, (duration % 1_000_000).toInt())
                    requestFailure(if (options.policy.mode == PADMode.ACTIVE) IdentityErrorCode.CHALLENGE_EXPIRED else IdentityErrorCode.SESSION_TIMEOUT)
                } catch (_: InterruptedException) { /* Terminal publication cancels the timer. */ }
            }, "odai-liveness-timeout-$sessionId").apply { isDaemon = true; start() }
            wakeLocked()
        }
    }

    fun submitFrame(frame: LivenessFrame): Boolean = synchronized(lock) {
        val start = startedAt; val end = deadline
        if (closing || terminalValue != null || finishRequested || pendingError != null || start == null || end == null) return false
        if (frame.timestampNanos < start || lastTimestamp?.let { frame.timestampNanos <= it } == true) {
            stale++; emitLocked(LivenessEvent.FrameStale); return false
        }
        val now = clock.nowNanos
        if (now < start || now >= end || frame.timestampNanos >= end || frame.timestampNanos > now) {
            pendingError = if (challenge == null) IdentityErrorCode.SESSION_TIMEOUT else IdentityErrorCode.CHALLENGE_EXPIRED
            stopNativeLocked()
            wakeLocked(); return false
        }
        if (frame.challenge != challenge) {
            pendingError = IdentityErrorCode.INVALID_CHALLENGE; stopNativeLocked(); wakeLocked(); return false
        }
        lastTimestamp = frame.timestampNanos; accepted++
        val previousDrops = queue.droppedCount
        queue.offer(frame); emitLocked(LivenessEvent.FrameAccepted)
        if (queue.droppedCount != previousDrops) emitLocked(LivenessEvent.FrameDropped)
        wakeLocked(); true
    }

    fun finish(): Unit = synchronized(lock) {
        if (terminalValue != null || closing) return
        if (startedAt == null) pendingError = IdentityErrorCode.INVALID_INPUT else finishRequested = true
        wakeLocked()
    }
    fun cancel() = requestStop(IdentityStopReason.CANCELLED)

    fun result(): LivenessResult {
        if ((IdentityStopSignal.isDelivering || Thread.currentThread() === worker) && completion.count != 0L)
            throw diagnostic(IdentityErrorCode.BUSY)
        awaitLatch(completion) { cancel() }
        return synchronized(lock) { failure?.let { throw it }; requireNotNull(resultValue) }
    }
    fun nextEvent(): LivenessEvent? = synchronized(lock) {
        var interrupted = false
        try {
            while (events.isEmpty() && state != LivenessSessionState.CLOSED) {
                try { lock.wait() } catch (_: InterruptedException) { interrupted = true }
            }
            if (events.isEmpty()) null else events.removeFirst()
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }
    fun consumeProof(proof: LivenessProof, captureDigest: String, purpose: String) {
        synchronized(lock) { checkProof(proof, captureDigest, purpose) }
        val authorization = store.beginLivenessOperation(ticket.grant)
        store.finishLivenessOperation(authorization) {
            synchronized(lock) { checkProof(proof, captureDigest, purpose); proofUsed = true }
        }
    }
    private fun checkProof(proof: LivenessProof, digest: String, purpose: String) {
        if (closing || state == LivenessSessionState.CLOSED) throw diagnostic(IdentityErrorCode.CLOSED)
        if (resultValue?.proof != proof || proofUsed || proof.purpose != purpose || proof.captureDigest != digest ||
            wallClock.nowMillis < proof.issuedAtMillis || wallClock.nowMillis >= proof.expiresAtMillis)
            throw diagnostic(IdentityErrorCode.INVALID_CHALLENGE)
    }
    override fun close() {
        synchronized(lock) { closing = true; queue.close() }
        requestStop(IdentityStopReason.CLOSED)
        if (IdentityStopSignal.isDelivering || Thread.currentThread() === worker) return
        awaitLatch(completion)
        awaitStopDelivery()
        markClosed()
    }
    private fun requestStop(reason: IdentityStopReason) {
        val prepared = synchronized(lock) {
            if (terminalValue != null) return
            val action = ticket.stopSignal.prepareStopDelivery(reason)
            val latch = action?.let { CountDownLatch(1).also { stopDelivery = it } }
            wakeLocked()
            action?.let { it to requireNotNull(latch) }
        } ?: return
        Thread({
            try { prepared.first() } finally { prepared.second.countDown() }
        }, "odai-liveness-stop-$sessionId").apply { isDaemon = true; start() }
    }
    private fun requestFailure(code: IdentityErrorCode) = synchronized(lock) {
        if (terminalValue == null && pendingError == null) { pendingError = code; stopNativeLocked(); wakeLocked() }
    }
    private fun stopNativeLocked() {
        val action = nativeSignal.prepareStopDelivery(IdentityStopReason.CANCELLED) ?: return
        Thread({ action() }, "odai-liveness-native-stop-$sessionId").apply { isDaemon = true; start() }
    }
    private fun awaitStopDelivery() {
        if (!IdentityStopSignal.isDelivering) synchronized(lock) { stopDelivery }?.let { awaitLatch(it) }
    }

    private fun runWorker() {
        val registration = ticket.stopSignal.registerStopHandler {
            synchronized(lock) { wakeLocked() }
            val reason = try { ticket.stopSignal.check(); IdentityStopReason.CANCELLED } catch (error: IdentityError) {
                when (error.code) {
                    IdentityErrorCode.AUTHORIZATION_REVOKED -> IdentityStopReason.AUTHORIZATION_REVOKED
                    IdentityErrorCode.CLOSED -> IdentityStopReason.CLOSED
                    else -> IdentityStopReason.CANCELLED
                }
            }
            nativeSignal.requestStop(reason)
        }
        try {
            val evaluation = runCatching { evaluateFrames() }
            // Retain the writer ticket while a locally requested native callback drains.
            nativeSignal.awaitDelivery()
            // Success and failure consume the original ticket once, including fatal JVM errors.
            try {
                store.finishLivenessOperation(ticket) {
                    synchronized(lock) {
                        ticket.stopSignal.check()
                        pendingError?.let { throw diagnostic(it) }
                        if (deadline?.let { clock.nowNanos >= it } == true) throw diagnostic(timeoutCode())
                        validateBinding()
                        val result = evaluation.getOrThrow()
                        resultValue = result; terminalValue = TaskTerminal.COMPLETED; state = LivenessSessionState.COMPLETED
                        queue.close(); emitLocked(LivenessEvent.Completed(result))
                    }
                }
            } catch (error: Throwable) {
                val final = if (error is Error) error else diagnostic((error as? IdentityError)?.code ?: IdentityErrorCode.BACKEND_UNAVAILABLE)
                synchronized(lock) {
                    failure = final
                    val cancelled = (final as? IdentityError)?.code == IdentityErrorCode.CANCELLED
                    terminalValue = if (cancelled) TaskTerminal.CANCELLED else TaskTerminal.FAILED
                    state = if (cancelled) LivenessSessionState.CANCELLED else LivenessSessionState.FAILED
                    emitLocked(if (cancelled) LivenessEvent.Cancelled else LivenessEvent.Failed((final as? IdentityError)?.code ?: IdentityErrorCode.BACKEND_UNAVAILABLE))
                }
            }
        } finally {
            synchronized(lock) { timer?.interrupt(); queue.close() }
            registration.close(); ticket.stopSignal.awaitDelivery(); nativeSignal.awaitDelivery(); awaitStopDelivery()
            if (synchronized(lock) { closing }) markClosed()
            completion.countDown()
        }
    }

    private fun evaluateFrames(): LivenessResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var processed = 0
        var revision: String? = null
        while (true) {
            synchronized(lock) {
                while (!awakened) lock.wait()
                awakened = false
            }
            while (true) {
                ticket.stopSignal.check()
                synchronized(lock) { pendingError?.let { throw diagnostic(it) } }
                if (synchronized(lock) { deadline?.let { clock.nowNanos >= it } == true }) throw diagnostic(timeoutCode())
                val frame = queue.next() ?: break
                try {
                    synchronized(lock) { state = LivenessSessionState.EVALUATING }
                    digest.update(frame.encodedFrame); processed++; revision = frame.revision
                    validateBinding()
                    val decision = backend.evaluate(frame, synchronized(lock) { challenge }, nativeSignal)
                    ticket.stopSignal.check(); validateBinding()
                    if (synchronized(lock) { deadline?.let { clock.nowNanos >= it } == true }) throw diagnostic(timeoutCode())
                    if (decision != null) {
                        if (decision.quality.faceCount > 1) throw diagnostic(IdentityErrorCode.MULTIPLE_FACES)
                        val qualityOK = decision.quality.faceCount == 1 && (decision.quality.score ?: -1.0) >= requireNotNull(options.policy.qualityFloor)
                        if (!qualityOK || decision.outcome == LivenessOutcome.INSUFFICIENT_QUALITY)
                            return makeResult(LivenessOutcome.INSUFFICIENT_QUALITY, decision.quality, IdentityScoreFields(), digest, revision)
                        if (decision.outcome == LivenessOutcome.LIVE && processed < options.policy.minimumFrames) continue
                        validateFields(decision.scoreFields)
                        return makeResult(decision.outcome, decision.quality, decision.scoreFields, digest, revision)
                    }
                } finally { queue.complete() }
            }
            if (synchronized(lock) { finishRequested })
                return makeResult(LivenessOutcome.INSUFFICIENT_QUALITY, IdentityQuality(0), IdentityScoreFields(), digest, revision)
        }
    }
    private fun validateBinding() = options.policy.validate(backend.backendId, backend.backendVersion, backend.modelBinding, options.policy.mode, ticket.grant.authorizationEpoch)
    private fun timeoutCode() = if (options.policy.mode == PADMode.ACTIVE) IdentityErrorCode.CHALLENGE_EXPIRED else IdentityErrorCode.SESSION_TIMEOUT
    private fun validateFields(fields: IdentityScoreFields) {
        if (options.policy.scoreMetric == null) {
            if (fields != IdentityScoreFields()) throw diagnostic(IdentityErrorCode.THRESHOLD_UNVERIFIED)
        } else if (fields.metric != options.policy.scoreMetric || fields.threshold != options.policy.threshold || fields.score == null) {
            throw diagnostic(IdentityErrorCode.THRESHOLD_UNVERIFIED)
        }
    }
    private fun makeResult(outcome: LivenessOutcome, quality: IdentityQuality, fields: IdentityScoreFields,
        digest: MessageDigest, revision: String?): LivenessResult {
        val capture = livenessHex(digest.digest())
        val ref = PADPolicyRef(options.policy.id, options.policy.revision)
        val now = wallClock.nowMillis
        val proof = if (outcome == LivenessOutcome.LIVE) {
            val expiry = try { Math.addExact(now, 30_000) } catch (_: ArithmeticException) { throw diagnostic(IdentityErrorCode.INVALID_INPUT) }
            LivenessProof(UUID.randomUUID().toString(), options.policy.backendId, sessionId, synchronized(lock) { challenge?.nonce } ?: livenessNonce(),
                options.purpose, capture, ref, options.policy.modelBinding, ticket.grant.authorizationEpoch, now, expiry)
        } else null
        return synchronized(lock) {
            // Freeze admission and counters before waiting for the final store gate.
            finishRequested = true
            LivenessResult(outcome, fields, quality, ref, options.policy.backendId, options.policy.backendVersion, options.policy.modelBinding,
                accepted, queue.droppedCount, stale, sessionId, sessionId, capture, revision, options.revision,
                livenessElapsed(startedAt ?: clock.nowNanos, clock.nowNanos), proof)
        }
    }
    private fun wakeLocked() { awakened = true; lock.notifyAll() }
    private fun emitLocked(event: LivenessEvent) {
        when (event) {
            LivenessEvent.FrameAccepted, LivenessEvent.FrameDropped, LivenessEvent.FrameStale -> if (events.size < 16) events.addLast(event)
            else -> events.addLast(event)
        }
        lock.notifyAll()
    }
    private fun markClosed() = synchronized(lock) {
        if (state != LivenessSessionState.CLOSED) { state = LivenessSessionState.CLOSED; emitLocked(LivenessEvent.Closed) }
    }
    private fun diagnostic(code: IdentityErrorCode) = IdentityError(code, options.policy.backendId, options.policy.modelBinding, options.policy.id, sessionId)
}

class FaceLiveness(
    private val identityStore: IdentityStore, backends: List<LivenessBackend>,
    defaultBackendId: String = backends.firstOrNull()?.backendId ?: throw IdentityError(IdentityErrorCode.BACKEND_NOT_REGISTERED),
    private val monotonicClock: IdentityMonotonicClock = SystemIdentityMonotonicClock,
    private val wallClock: IdentityWallClock = SystemIdentityWallClock
) : AutoCloseable {
    private val allBackends = backends.toList()
    private val registry = IdentityBackendRegistry(defaultBackendId, allBackends.map { IdentityBackendRegistration(it.backendId, it) })
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private var active: LivenessSession? = null
    private var admitting = false
    private var closed = false
    private var closeLatch: CountDownLatch? = null

    fun openLivenessSession(grant: AuthorizationGrant, options: LivenessOptions,
        backend: IdentityBackendSelection = IdentityBackendSelection.Default): LivenessSession {
        synchronized(lock) {
            if (closed) throw IdentityError(IdentityErrorCode.CLOSED)
            if (admitting || active?.let { it.currentState != LivenessSessionState.CLOSED } == true) throw IdentityError(IdentityErrorCode.BUSY)
            admitting = true
        }
        try {
            val selected = registry.select(backend)
            if (selected.availability().status != CapabilityStatus.AVAILABLE) throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, selected.backendId, selected.modelBinding)
            if (grant.purpose != options.purpose) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
            try {
                options.policy.validate(selected.backendId, selected.backendVersion, selected.modelBinding, options.policy.mode, grant.authorizationEpoch)
            } catch (error: IdentityError) {
                throw IdentityError(error.code, selected.backendId, selected.modelBinding, error.policyId ?: options.policy.id)
            }
            val ticket = try {
                identityStore.beginLivenessOperation(grant)
            } catch (error: IdentityError) {
                throw IdentityError(error.code, selected.backendId, selected.modelBinding, error.policyId ?: options.policy.id)
            }
            val session = LivenessSession(identityStore, ticket, selected, options, monotonicClock, wallClock)
            synchronized(lock) { active = session; session.launch() }
            if (synchronized(lock) { closed }) session.close()
            return session
        } finally { synchronized(lock) { admitting = false; lock.notifyAll() } }
    }
    override fun close() {
        val latch = synchronized(lock) {
            closed = true
            closeLatch ?: CountDownLatch(1).also { created ->
                closeLatch = created
                Thread({
                    try {
                        synchronized(lock) { while (admitting) lock.wait() }
                        synchronized(lock) { active }?.close()
                        allBackends.forEach { runCatching { it.close() } }
                    } finally { created.countDown() }
                }, "odai-liveness-close").apply { isDaemon = true; start() }
            }
        }
        if (!IdentityStopSignal.isDelivering) awaitLatch(latch)
    }
}

private fun awaitLatch(latch: CountDownLatch, onInterrupt: () -> Unit = {}) {
    var interrupted = false
    try {
        while (true) try { latch.await(); break } catch (_: InterruptedException) { interrupted = true; onInterrupt() }
    } finally { if (interrupted) Thread.currentThread().interrupt() }
}
private fun livenessNonce(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let(::livenessHex)
private fun livenessHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
private fun livenessElapsed(start: Long, end: Long): Long = if (end < start) 0 else try { Math.subtractExact(end, start) } catch (_: ArithmeticException) { Long.MAX_VALUE }

