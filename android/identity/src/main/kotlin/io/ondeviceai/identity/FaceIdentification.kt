package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.TaskTerminal
import java.util.Collections
import java.util.concurrent.CountDownLatch

/** Candidate-unit score; no template vectors or host subject IDs. */
class IdentificationCandidate internal constructor(val candidateRef: CandidateRef, val score: Double, val rank: Int)

/** Search controls cannot replace the immutable gallery threshold policy. */
class IdentificationOptions(val topK: Int = 32, val policy: ThresholdPolicyRef? = null, val revision: String) {
    init { if (topK !in 1..32) throw IdentityError(IdentityErrorCode.INVALID_INPUT); token(revision) }
}
enum class IdentificationOutcome { CANDIDATES, UNKNOWN, INSUFFICIENT_QUALITY }
class IdentificationEnrollmentCount internal constructor(val candidateRef: CandidateRef, val templateCount: Int)
/** Threshold metadata is separate from comparison scores, so UNKNOWN does not leak a low score. */
class IdentificationThresholdFields internal constructor(val metric: String, val threshold: Double) {
    init { if (metric.isEmpty() || !threshold.isFinite()) throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
}
/** Conditions for grouping authorized evaluation, not measured FPIR/FNIR rates. */
class IdentificationGrouping internal constructor(val datasetRevision: String, val operatingPoint: String,
    val enrollmentRule: String, val gallerySizeRange: IntRange, val topK: Int)
class IdentificationResult internal constructor(
    val outcome: IdentificationOutcome, candidates: List<IdentificationCandidate>, val scoreFields: IdentityScoreFields,
    val thresholdFields: IdentificationThresholdFields?,
    val quality: IdentityQuality, val galleryRevision: String, val galleryContentDigest: String,
    val aggregationPolicyId: String, val thresholdPolicyId: String, val thresholdPolicyRevision: String,
    val galleryCandidateCount: Int, val galleryTemplateCount: Int, enrollmentCounts: List<IdentificationEnrollmentCount>,
    val fpirFnirGrouping: IdentificationGrouping, val backendId: String, val backendVersion: String, val modelBinding: ModelBinding,
    val inputRevision: String, val optionsRevision: String, val preprocessingRevision: String,
    val sessionId: String, val runId: String, val durationNanos: Long
) {
    val candidates: List<IdentificationCandidate> = Collections.unmodifiableList(candidates.toList())
    val enrollmentCounts: List<IdentificationEnrollmentCount> = Collections.unmodifiableList(enrollmentCounts.toList())
}

class IdentificationTask internal constructor(
    private val signal: IdentityStopSignal,
    onFinished: () -> Unit,
    run: (IdentificationTask) -> IdentificationResult
) : AutoCloseable {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    val runId: String = java.util.UUID.randomUUID().toString()
    private val completion = CountDownLatch(1)
    private var value: IdentificationResult? = null
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
            signal.awaitDelivery()
            awaitStopDelivery()
            onFinished()
            completion.countDown()
        }
    }.apply { isDaemon = true; name = "odai-face-identification-$runId" }

    internal fun start() = worker.start()

    internal fun complete(result: IdentificationResult): IdentificationResult = synchronized(lock) {
        signal.check()
        value = result
        terminalValue = TaskTerminal.COMPLETED
        result
    }

    fun cancel() { requestStop(IdentityStopReason.CANCELLED) }

    internal fun requestStop(reason: IdentityStopReason) {
        val (action, delivery) = synchronized(lock) {
            if (terminalValue != null) return
            val prepared = signal.prepareStopDelivery(reason) ?: return
            val latch = CountDownLatch(1)
            stopDelivery = latch
            prepared to latch
        }
        Thread({
            try { action() }
            finally { delivery.countDown() }
        }, "odai-face-identification-stop-$runId").apply { isDaemon = true }.start()
    }

    private fun awaitStopDelivery() {
        val delivery = synchronized(lock) { stopDelivery } ?: return
        if (IdentityStopSignal.isDelivering) return
        var interrupted = false
        try { while (true) try { delivery.await(); break } catch (_: InterruptedException) { interrupted = true } }
        finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    fun result(): IdentificationResult {
        if (IdentityStopSignal.isDelivering && completion.count != 0L) {
            signal.check()
            throw IdentityError(IdentityErrorCode.BUSY, sessionId = runId)
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
/** Fixed-snapshot query engine; this exploration implementation accepts only internal fixture indexes. */
class FaceIdentification(private val identityStore: IdentityStore, private val indexStore: IndexStore,
    defaultBackendId: String, backends: List<IdentityBackend>,
    private val monotonicClock: IdentityMonotonicClock = SystemIdentityMonotonicClock) : AutoCloseable {
    private val registeredBackends = backends.toList()
    private val registry = IdentityBackendRegistry(defaultBackendId, registeredBackends.map { IdentityBackendRegistration(it.backendId, it) })
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val lock = java.lang.Object()
    private var active: IdentificationTask? = null
    private var admissionWaiters = 0
    private var closed = false
    private var closeLatch: CountDownLatch? = null

    fun identify(probe: IdentityProbe, galleryRef: GalleryRef, grant: AuthorizationGrant, options: IdentificationOptions,
        backend: IdentityBackendSelection = IdentityBackendSelection.Default): IdentificationTask {
        synchronized(lock) {
            if (closed) throw IdentityError(IdentityErrorCode.CLOSED)
            if (active != null || admissionWaiters != 0) throw IdentityError(IdentityErrorCode.BUSY)
            admissionWaiters++
        }
        var lease: IndexReadLease? = null
        var handedOff = false
        try {
            val selected = registry.select(backend)
            val binding = IdentityEnrollmentBinding(selected)
            try { binding.validate() }
            catch (error: IdentityError) { throw IdentityError(error.code, selected.backendId, selected.modelBinding) }
            if (selected.availability().status != CapabilityStatus.AVAILABLE)
                throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, binding.space.backendId, binding.space.modelBinding)
            var policyId: String? = null
            try {
                val snapshot = indexStore.snapshotSummary(galleryRef)
                policyId = snapshot.policy.id
                snapshot.policy.validate(binding.space, IdentityPolicyKind.IDENTIFICATION, snapshot.operationGrant.authorizationEpoch,
                    snapshot.members.map { it.candidate.id }.toSet().size)
                if (options.policy?.let { it.id != snapshot.policy.id || it.revision != snapshot.policy.revision } == true)
                    throw IdentityError(IdentityErrorCode.THRESHOLD_UNVERIFIED)
                if (snapshot.aggregationPolicyId != "subject-max-v1" || snapshot.policy.scoreMetric != "cosine" ||
                    snapshot.policy.scoreDirection != ScoreDirection.HIGHER_IS_BETTER) throw IdentityError(IdentityErrorCode.UNSUPPORTED_OPTION)
                val read = indexStore.acquireIndexReadLease(galleryRef, grant)
                lease = read
                lateinit var created: IdentificationTask
                created = IdentificationTask(read.operationSignal,
                    { synchronized(lock) { if (active === created) active = null; lock.notifyAll() } }) { task ->
                    execute(task, read, snapshot, probe, options, selected, binding)
                }
                synchronized(lock) {
                    if (closed) created.requestStop(IdentityStopReason.CLOSED)
                    active = created
                }
                created.start(); handedOff = true
                return created
            } catch (error: IdentityError) {
                throw IdentityError(error.code, binding.space.backendId, binding.space.modelBinding, error.policyId ?: policyId)
            }
        } finally {
            if (!handedOff) lease?.close()
            synchronized(lock) { admissionWaiters--; lock.notifyAll() }
        }
    }

    private fun execute(task: IdentificationTask, read: IndexReadLease, snapshot: GallerySnapshotRecord,
        probe: IdentityProbe, options: IdentificationOptions, backend: IdentityBackend,
        binding: IdentityEnrollmentBinding): IdentificationResult {
        val started = monotonicClock.nowNanos
        val members = mutableListOf<IdentityTemplateLease>()
        var consumed = false
        try {
            read.operationSignal.check()
            snapshot.members.forEach { member -> members.add(identityStore.acquireTemplateLease(member.ref, member.grant,
                IdentityScope.IDENTIFICATION, binding.space)) }
            val prepared = binding.prepare(probe, EnrollmentOptions(requireNotNull(snapshot.policy.qualityFloor),
                snapshot.policy.id, options.revision), read.operationSignal)
            val probeEmbedding = prepared.embedding
            val candidates: List<IdentificationCandidate>
            val outcome: IdentificationOutcome
            if (probeEmbedding == null) {
                candidates = emptyList(); outcome = IdentificationOutcome.INSUFFICIENT_QUALITY
            } else {
                val retained = members.map { it.embedding }
                if (!read.indexData().contentEquals(fixtureIdentificationIndex(retained)) || probe.fixtureID == null)
                    throw IdentityError(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED)
                val scores = snapshot.members.indices.associate { snapshot.members[it].ref.id to identificationCosine(probeEmbedding, retained[it]) }
                candidates = rankIdentificationCandidates(snapshot, scores, options.topK)
                outcome = if (candidates.isEmpty()) IdentificationOutcome.UNKNOWN else IdentificationOutcome.CANDIDATES
            }
            val fields = candidates.firstOrNull()?.let { IdentityScoreFields(it.score, snapshot.policy.scoreMetric, snapshot.policy.threshold) }
                ?: IdentityScoreFields()
            val thresholdFields = probeEmbedding?.let {
                IdentificationThresholdFields(requireNotNull(snapshot.policy.scoreMetric), requireNotNull(snapshot.policy.threshold))
            }
            val counts = snapshot.members.groupBy { it.candidate.id }.toSortedMap().values.map {
                IdentificationEnrollmentCount(it.first().candidate, it.size)
            }
            val policy = snapshot.policy
            val result = IdentificationResult(outcome, candidates, fields, thresholdFields, prepared.quality, snapshot.ref.revision, snapshot.contentDigest,
                snapshot.aggregationPolicyId, policy.id, policy.revision, counts.size, snapshot.members.size, counts,
                IdentificationGrouping(requireNotNull(policy.datasetRevision), requireNotNull(policy.operatingPoint),
                    requireNotNull(policy.enrollmentRule), requireNotNull(policy.gallerySizeRange), options.topK),
                binding.space.backendId, binding.space.backendVersion, binding.space.modelBinding, probe.revision, options.revision,
                binding.space.preprocessRevision, task.runId, task.runId, identificationElapsed(started, monotonicClock.nowNanos))
            consumed = true
            return indexStore.consumeReadLease(read) { task.complete(result) }
        } catch (error: Throwable) {
            var failure = error
            if (!consumed) {
                try { indexStore.consumeReadLease(read) { throw error } }
                catch (terminal: Throwable) { failure = terminal }
            }
            if (failure is Error) throw failure
            throw IdentityError((failure as? IdentityError)?.code ?: IdentityErrorCode.BACKEND_UNAVAILABLE,
                binding.space.backendId, binding.space.modelBinding, snapshot.policy.id, task.runId)
        } finally {
            members.forEach { it.close() }
            read.close()
        }
    }

    override fun close() {
        val (latch, reentrant) = synchronized(lock) {
            val existing = closeLatch
            if (existing != null) existing to IdentityStopSignal.isDelivering
            else {
                closed = true
                val created = CountDownLatch(1)
                closeLatch = created
                Thread({ finishClose(created) }, "odai-face-identification-close").apply { isDaemon = true }.start()
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

    private fun IdentificationTask.closeForEngine() { try { result() } catch (_: Throwable) { /* result() retains the task failure. */ } }

}

/** Bounded internal synthetic format, never a production index contract. */
internal fun fixtureIdentificationIndex(embeddings: List<IdentityEmbedding>): ByteArray {
    val first = embeddings.firstOrNull() ?: throw IdentityError(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED)
    if (embeddings.size > 4096 || first.embeddingSpace.dimension > 4096 || embeddings.any { it.embeddingSpace != first.embeddingSpace })
        throw IdentityError(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED)
    val header = "ODAI-FIXTURE-IDX-1".toByteArray(Charsets.US_ASCII)
    val buffer = java.nio.ByteBuffer.allocate(header.size + embeddings.size * first.embeddingSpace.dimension * 4)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN).put(header)
    embeddings.forEach { embedding -> embedding.values.forEach { buffer.putFloat(it) } }
    return buffer.array()
}
private fun identificationCosine(left: IdentityEmbedding, right: IdentityEmbedding): Double {
    if (left.embeddingSpace != right.embeddingSpace) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
    var dot = 0.0; var a = 0.0; var b = 0.0
    left.values.indices.forEach { i ->
        val x = left.values[i].toDouble(); val y = right.values[i].toDouble()
        dot += x*y; a += x*x; b += y*y
    }
    if (a == 0.0 || b == 0.0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    val score = dot / (kotlin.math.sqrt(a) * kotlin.math.sqrt(b))
    if (!score.isFinite()) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    return score.coerceIn(-1.0, 1.0)
}
private fun identificationElapsed(start: Long, end: Long): Long = try {
    Math.subtractExact(end, start).coerceAtLeast(0)
} catch (_: ArithmeticException) { Long.MAX_VALUE }

/** Pure ranking only; the query engine owns authorization, quality, leases and final publication. */
internal fun rankIdentificationCandidates(snapshot: GallerySnapshotRecord, scores: Map<String, Double>,
    topK: Int = 32): List<IdentificationCandidate> {
    if (topK !in 1..32 || snapshot.members.isEmpty() || scores.keys != snapshot.members.map { it.ref.id }.toSet() ||
        scores.values.any { !it.isFinite() || it !in -1.0..1.0 }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    val policy = snapshot.policy
    policy.validate(policy.embeddingSpace, IdentityPolicyKind.IDENTIFICATION,
        snapshot.operationGrant.authorizationEpoch, snapshot.members.map { it.candidate.id }.toSet().size)
    if (snapshot.aggregationPolicyId != "subject-max-v1" || policy.scoreMetric != "cosine" ||
        policy.scoreDirection != ScoreDirection.HIGHER_IS_BETTER)
        throw IdentityError(IdentityErrorCode.UNSUPPORTED_OPTION, policyId = policy.id)
    val maxima = mutableMapOf<String, Pair<CandidateRef, Double>>()
    for (member in snapshot.members) {
        val score = scores.getValue(member.ref.id)
        if (maxima[member.candidate.id]?.let { score > it.second } != false) {
            maxima[member.candidate.id] = member.candidate to score
        }
    }
    // Opaque IDs accept ASCII tokens only, so String ordering is their unsigned byte ordering.
    return maxima.values.filter { it.second >= requireNotNull(policy.threshold) }
        .sortedWith(compareByDescending<Pair<CandidateRef, Double>> { it.second }.thenBy { it.first.id })
        .take(topK).mapIndexed { index, (candidate, score) -> IdentificationCandidate(candidate, score, index + 1) }
}
