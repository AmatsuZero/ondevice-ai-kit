package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.TaskTerminal
import java.util.concurrent.CountDownLatch

class VerificationOptions(val policy: ThresholdPolicy, val revision: String) {
    init { token(revision) }
}

enum class VerificationOutcome { MATCH, NON_MATCH, INSUFFICIENT_QUALITY }

class VerificationResult internal constructor(
    val outcome: VerificationOutcome,
    val scoreFields: IdentityScoreFields,
    val quality: IdentityQuality,
    val templateRevision: String,
    val thresholdPolicyId: String,
    val thresholdPolicyRevision: String,
    val backendId: String,
    val backendVersion: String,
    val modelBinding: ModelBinding,
    val inputRevision: String,
    val optionsRevision: String,
    val preprocessingRevision: String,
    val sessionId: String,
    val runId: String,
    val durationNanos: Long
) {
    val score get() = scoreFields.score
    val scoreMetric get() = scoreFields.metric
    val threshold get() = scoreFields.threshold
}

class VerificationTask internal constructor(
    private val ticket: IdentityOperationTicket,
    onFinished: () -> Unit,
    run: (VerificationTask) -> VerificationResult
) : AutoCloseable {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private val completion = CountDownLatch(1)
    private var value: VerificationResult? = null
    private var failure: Throwable? = null
    private var stopDelivery: CountDownLatch? = null
    private var terminalValue: TaskTerminal? = null
    val terminal: TaskTerminal? get() = synchronized(lock) { terminalValue }

    private val worker = Thread {
        try {
            run(this)
        } catch (error: Throwable) {
            synchronized(lock) {
                failure = error
                terminalValue = if ((error as? IdentityError)?.code == IdentityErrorCode.CANCELLED) TaskTerminal.CANCELLED else TaskTerminal.FAILED
            }
        } finally {
            ticket.stopSignal.awaitDelivery()
            awaitStopDelivery()
            onFinished()
            completion.countDown()
        }
    }.apply { isDaemon = true; name = "odai-face-verification-${ticket.id}" }

    internal fun start() = worker.start()

    internal fun complete(result: VerificationResult): VerificationResult = synchronized(lock) {
        ticket.stopSignal.check()
        value = result
        terminalValue = TaskTerminal.COMPLETED
        result
    }

    fun cancel() { requestStop(IdentityStopReason.CANCELLED) }

    internal fun requestStop(reason: IdentityStopReason) {
        val (action, delivery) = synchronized(lock) {
            if (terminalValue != null) return
            val prepared = ticket.stopSignal.prepareStopDelivery(reason) ?: return
            val latch = CountDownLatch(1)
            stopDelivery = latch
            prepared to latch
        }
        Thread({
            try { action() }
            finally { delivery.countDown() }
        }, "odai-face-verification-stop-${ticket.id}").apply { isDaemon = true }.start()
    }

    private fun awaitStopDelivery() {
        val delivery = synchronized(lock) { stopDelivery } ?: return
        if (IdentityStopSignal.isDelivering) return
        var interrupted = false
        try { while (true) try { delivery.await(); break } catch (_: InterruptedException) { interrupted = true } }
        finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    fun result(): VerificationResult {
        if (IdentityStopSignal.isDelivering && completion.count != 0L) {
            ticket.stopSignal.check()
            throw IdentityError(IdentityErrorCode.BUSY, sessionId = ticket.id.toString())
        }
        var interrupted = false
        try {
            while (true) {
                try { completion.await(); break }
                catch (_: InterruptedException) { interrupted = true; requestStop(IdentityStopReason.CANCELLED) }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
        return synchronized(lock) { failure?.let { throw it }; requireNotNull(value) }
    }

    override fun close() {
        cancel()
        if (IdentityStopSignal.isDelivering) return
        try { result() } catch (_: IdentityError) { }
    }
}

class FaceVerification(
    private val identityStore: IdentityStore,
    defaultBackendId: String,
    backends: List<IdentityBackend>,
    private val monotonicClock: IdentityMonotonicClock = SystemIdentityMonotonicClock
) : AutoCloseable {
    private val registeredBackends = backends.toList()
    private val registry = IdentityBackendRegistry(defaultBackendId, registeredBackends.map { IdentityBackendRegistration(it.backendId, it) })
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private var active: VerificationTask? = null
    private var closed = false
    private var admissionWaiters = 0
    private var closeLatch: CountDownLatch? = null

    fun verify(
        probe: IdentityProbe,
        templateRef: TemplateRef,
        grant: AuthorizationGrant,
        options: VerificationOptions,
        backend: IdentityBackendSelection = IdentityBackendSelection.Default
    ): VerificationTask {
        token(options.revision)
        synchronized(lock) {
            if (closed) throw IdentityError(IdentityErrorCode.CLOSED)
            if (active != null || admissionWaiters != 0) throw IdentityError(IdentityErrorCode.BUSY)
            admissionWaiters++
        }
        try {
            val selected = registry.select(backend)
            if (selected.availability().status != CapabilityStatus.AVAILABLE)
                throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, selected.backendId, selected.modelBinding)
            val binding = VerificationBinding(selected)
            binding.validate()
            try {
                options.policy.validate(binding.space, IdentityPolicyKind.VERIFICATION, grant.authorizationEpoch)
            } catch (error: IdentityError) {
                throw IdentityError(error.code, selected.backendId, selected.modelBinding, error.policyId ?: options.policy.id)
            }
            if (binding.space.scoreMetric != "cosine" || binding.space.scoreDirection != ScoreDirection.HIGHER_IS_BETTER)
                throw IdentityError(IdentityErrorCode.UNSUPPORTED_OPTION, selected.backendId, selected.modelBinding, options.policy.id)
            val ticket = try {
                val candidate = identityStore.recordedTemplateCandidate(templateRef)
                identityStore.beginOperation(candidate, grant, IdentityScope.VERIFICATION, listOf(templateRef))
            } catch (error: IdentityError) {
                throw IdentityError(error.code, binding.space.backendId, binding.space.modelBinding, options.policy.id)
            }
            val task = synchronized(lock) {
                lateinit var created: VerificationTask
                created = VerificationTask(ticket, { synchronized(lock) { if (active === created) active = null; lock.notifyAll() } }) { task ->
                    verifyPrepared(probe, templateRef, grant, options, selected, ticket, ticket.stopSignal, task)
                }
                if (closed) created.requestStop(IdentityStopReason.CLOSED)
                active = created
                created
            }
            task.start()
            return task
        } finally {
            synchronized(lock) { admissionWaiters-- ; lock.notifyAll() }
        }
    }

    private fun verifyPrepared(
        probe: IdentityProbe,
        templateRef: TemplateRef,
        grant: AuthorizationGrant,
        options: VerificationOptions,
        selected: IdentityBackend,
        ticket: IdentityOperationTicket,
        signal: IdentityStopSignal,
        task: VerificationTask
    ): VerificationResult {
        val started = monotonicClock.nowNanos
        var lease: IdentityTemplateLease? = null
        var consumed = false
        try {
            val binding = VerificationBinding(selected)
            binding.validate()
            options.policy.validate(binding.space, IdentityPolicyKind.VERIFICATION, grant.authorizationEpoch)
            if (binding.space.scoreMetric != "cosine" || binding.space.scoreDirection != ScoreDirection.HIGHER_IS_BETTER)
                throw IdentityError(IdentityErrorCode.UNSUPPORTED_OPTION, binding.space.backendId, binding.space.modelBinding)
            if (selected.availability().status != CapabilityStatus.AVAILABLE)
                throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, selected.backendId, selected.modelBinding)
            signal.check()
            lease = identityStore.acquireTemplateLease(templateRef, grant, IdentityScope.VERIFICATION, binding.space)
            val quality = selected.evaluateQuality(probe, signal)
            binding.validate(); signal.check()
            if (quality.faceCount > 1) throw IdentityError(IdentityErrorCode.MULTIPLE_FACES, binding.space.backendId, binding.space.modelBinding)
            val result = if (quality.faceCount != 1 || quality.score == null || quality.score < requireNotNull(options.policy.qualityFloor)) {
                result(VerificationOutcome.INSUFFICIENT_QUALITY, IdentityScoreFields(), quality, templateRef, options, binding.space, probe, ticket.id.toString(), started)
            } else {
                val embedding = selected.embed(probe, signal)
                binding.validate(); signal.check()
                if (embedding.embeddingSpace != binding.space) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, binding.space.backendId, binding.space.modelBinding)
                val score = cosine(embedding.values, lease.embedding.values)
                val threshold = requireNotNull(options.policy.threshold)
                result(if (score >= threshold) VerificationOutcome.MATCH else VerificationOutcome.NON_MATCH,
                    IdentityScoreFields(score, binding.space.scoreMetric, threshold), quality, templateRef, options, binding.space, probe, ticket.id.toString(), started)
            }
            consumed = true
            val published = identityStore.finishOperation(ticket) { task.complete(result) }
            return published
        } catch (error: Throwable) {
            var failure = error
            if (!consumed) {
                try { identityStore.finishOperation(ticket) { throw error } }
                catch (terminal: Throwable) { failure = terminal }
            }
            if (failure is Error) throw failure
            val code = (failure as? IdentityError)?.code ?: IdentityErrorCode.BACKEND_UNAVAILABLE
            throw IdentityError(code, selected.backendId, selected.modelBinding, options.policy.id, ticket.id.toString())
        } finally {
            lease?.close()
        }
    }

    private fun result(outcome: VerificationOutcome, scoreFields: IdentityScoreFields, quality: IdentityQuality,
        templateRef: TemplateRef, options: VerificationOptions, space: EmbeddingSpaceId, probe: IdentityProbe, id: String, started: Long) =
        VerificationResult(outcome, scoreFields, quality, templateRef.revision, options.policy.id, options.policy.revision,
            space.backendId, space.backendVersion, space.modelBinding, probe.revision, options.revision,
            space.preprocessRevision, id, id, elapsed(started, monotonicClock.nowNanos))

    override fun close() {
        val (latch, reentrant) = synchronized(lock) {
            val existing = closeLatch
            if (existing != null) existing to IdentityStopSignal.isDelivering
            else {
                closed = true
                val created = CountDownLatch(1)
                closeLatch = created
                Thread({ finishClose(created) }, "odai-face-verification-close").apply { isDaemon = true }.start()
                created to IdentityStopSignal.isDelivering
            }
        }
        if (reentrant) return
        var interrupted = false
        try {
            while (true) try { latch.await(); break } catch (_: InterruptedException) { interrupted = true }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    private fun finishClose(latch: CountDownLatch) {
        var interrupted = false
        try {
            synchronized(lock) {
                while (admissionWaiters != 0) try { lock.wait() } catch (_: InterruptedException) { interrupted = true }
            }
            val task = synchronized(lock) { active }
            task?.requestStop(IdentityStopReason.CLOSED)
            task?.closeForEngine()
            synchronized(lock) {
                while (active != null) try { lock.wait() } catch (_: InterruptedException) { interrupted = true }
            }
            registeredBackends.forEach { runCatching { it.close() } }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
            latch.countDown()
        }
    }

    private fun VerificationTask.closeForEngine() { try { result() } catch (_: Throwable) { /* result() retains the task failure. */ } }

    private fun cosine(left: List<Float>, right: List<Float>): Double {
        if (left.size != right.size) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
        var dot = 0.0; var leftNorm = 0.0; var rightNorm = 0.0
        left.indices.forEach { index ->
            val a = left[index].toDouble(); val b = right[index].toDouble()
            if (!a.isFinite() || !b.isFinite()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            dot += a * b; leftNorm += a * a; rightNorm += b * b
        }
        if (!dot.isFinite() || !leftNorm.isFinite() || !rightNorm.isFinite() || leftNorm == 0.0 || rightNorm == 0.0)
            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return (dot / (kotlin.math.sqrt(leftNorm) * kotlin.math.sqrt(rightNorm))).coerceIn(-1.0, 1.0)
    }
}

private class VerificationBinding(private val backend: IdentityBackend) {
    val space = backend.embeddingSpace
    fun validate() {
        if (backend.embeddingSpace != space || backend.backendId != space.backendId || backend.backendVersion != space.backendVersion || backend.modelBinding != space.modelBinding)
            throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
    }
}

private fun elapsed(start: Long, end: Long): Long = if (end < start) 0L else try {
    Math.subtractExact(end, start)
} catch (_: ArithmeticException) {
    Long.MAX_VALUE
}
