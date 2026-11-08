package io.ondeviceai.litertlm

import java.io.File
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter bridging [LiteRtLmSession] onto the real Binder [LiteRtLmProcessConnection].
 *
 * [LiteRtLmProcessEngineFactory.open] only connects (bind + original-binder death link, no HELLO);
 * this adapter defers HELLO/reserve and START to [generateImage]. Ownership follows the shared
 * coordinator contract: terminal status or an explicit START false never releases remote ownership;
 * only the original registered exact binder death (or a confirmed local pre-reservation release)
 * does. Semantic success collected before a post-success death is preserved.
 *
 * Lifecycle arbitration (the fix for the two confirmed admission defects):
 *
 * The progression NEW -> HELLO admission -> START admission is arbitrated against cancel/close by a
 * SINGLE coherent, locked, atomic state machine ([Stage]). Both cancel/close and the generateImage
 * admission gates mutate [Stage] only under [lock], so there is one linearizable history — there is
 * no scattered, independent "check-then-act" on separate flags. [lock] is NEVER held across a
 * blocking Binder transaction or a suspension point, so [cancelProcess] returns immediately on the
 * main thread while HELLO/CANCEL Binder work is suspended on an independent IO scope.
 *
 * START admission linearization point (HIGH #2 exact point): the single locked transition that
 * sets [Stage.ADMITTING_START] immediately before [LiteRtLmProcessConnection.start] is invoked;
 * cancellation/close linearizes (under [lock]) either before or after this exact point. This point
 * governs only whether generation is allowed to invoke START -- it is NOT the ownership boundary.
 * By the time START can be admitted HELLO/reserve has already run, so a remote HELLO owner may
 * ALREADY exist even if a close wins "before START admission". Whether termination performs a safe
 * local release or must retain ambiguous/remote ownership is decided solely by the
 * coordinator-guarded [LiteRtLmProcessConnection.releaseBeforeReservation] (see Ownership release
 * below), never by [stage], by the START-admission timing, or by the generation-attempt flag. A
 * close that wins after START was admitted still keeps [Stage.TERMINATED] terminal, rejects
 * generation, and leaves release to the single exact-death barrier.
 *
 * Ownership release (HIGH #1): cancel/close NEVER read the generation-attempt flag [reserved] to
 * infer beginHello. The single finalization barrier probes the coordinator-guarded
 * [LiteRtLmProcessConnection.releaseBeforeReservation]:
 *   - true  => safe local release (HELLO never began); no CANCEL/RETIRE is sent and the exact-death
 *              barrier completes with a null (locally unreserved) outcome.
 *   - false => HELLO may have begun; ownership is ambiguous and retained until the exact registered
 *              binder death, so we send best-effort CANCEL + RETIRE and then await that barrier.
 * Because this guard reflects beginHello (not [stage]), a close that wins before START admission but
 * after HELLO still sees false (ambiguous remote ownership), never a stage-derived safe release. A
 * cancel/close that wins before HELLO makes a later [generateImage] fail at its admission gate
 * WITHOUT flipping any flag that would make close() falsely believe a remote owner exists.
 *
 * Raw status chunks are tracked as an immutable-prefix, append-only history: each new raw chunk is
 * delivered to [onSnapshot] exactly once, without trimming/normalizing or inferring the mode from
 * prefix similarity. In CUMULATIVE mode every newly observed snapshot must extend the previous full
 * cumulative text; a rewritten semantic cumulative snapshot is rejected with [LiteRtLmError]. There
 * is no fabricated final oracle; the returned text is assembled from the explicit mode only on a
 * terminal SUCCEEDED status, and an empty final text yields [LiteRtLmError.NoResult].
 */
internal class LiteRtLmProcessSession(
    private val connection: LiteRtLmProcessConnection,
    override val callbackMode: LiteRtLmCallbackMode,
    private val modelPath: File,
    private val cacheDir: File,
    private val backendPolicy: LiteRtLmBackendPolicy,
) : LiteRtLmSession {

    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private val deliveredChunks = ArrayList<String>()
    private var observedLength = 0

    // Generation-attempt guard ONLY: set when generateImage is first entered. It is deliberately NOT
    // used to decide ownership release; equating it with beginHello was the root of HIGH #1.
    private val reserved = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val deliverySuppressed = AtomicBoolean(false)

    /**
     * Coherent admission state. Every transition is made under [lock], so cancel/close and the
     * generateImage admission gates share one linearizable history. The lock is never held across a
     * blocking Binder transaction or a suspension point. Marked @Volatile so the status loop can
     * read the latest value off-lock after a cancel/close write. [Stage.TERMINATED] is terminal:
     * once set it is never overwritten by any admission gate.
     */
    @Volatile
    private var stage: Stage = Stage.NEW

    private enum class Stage {
        NEW,              // connected, nothing started
        ADMITTING_HELLO,  // generateImage passed the cancel/close gate; about to begin HELLO
        HELLO_DONE,       // connection.reserve() returned; a remote owner may exist
        ADMITTING_START,  // START admission linearized; about to invoke connection.start()
        START_DONE,       // connection.start() returned true; generation running remotely
        TERMINATED,       // cancel or close won; the finalization barrier owns release
    }

    // Shared completion representation for the single finalization operation. [close] awaits this
    // CompletableDeferred -- the same ordinary pattern the Client uses for bindResult /
    // ownershipReleased. A genuine finalize failure is completed exceptionally on it, so every
    // await() propagates it rather than a detached Job.join() hiding it. [finalizeStarted]
    // (guarded by [lock]) ensures the operation is launched at most once, so all concurrent /
    // repeated closes await this exact one.
    private val finalizeBarrier = CompletableDeferred<Unit>()
    private var finalizeStarted = false

    override suspend fun generateImage(
        image: File,
        prompt: String,
        maxOutputTokens: Int,
        onSnapshot: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        // Atomic admission gate for HELLO. Linearizes against cancel/close under [lock].
        val admitHello = synchronized(lock) {
            if (!reserved.compareAndSet(false, true)) {
                throw IllegalStateException("LiteRT-LM generation already started")
            }
            if (stage == Stage.TERMINATED) {
                // cancel/close won before any admission; generation must fail with no HELLO/START.
                false
            } else {
                stage = Stage.ADMITTING_HELLO
                true
            }
        }
        if (!admitHello) throw LiteRtLmError.RuntimeFailure()

        // Deferred remote reserve (HELLO). This may block on a Binder transact; we do NOT hold any
        // lock across it, so a concurrent cancelProcess/close on another thread only races on the
        // atomic flags and returns promptly. beginHello runs inside connection.reserve().
        connection.reserve()

        // Recheck after the (possibly blocking) HELLO transact. If cancellation/close won while HELLO
        // was in flight the claim is now ambiguous (owned until exact death); generation must still
        // fail and START must not be sent.
        val admitAfterHello = synchronized(lock) {
            if (stage == Stage.TERMINATED) false else { stage = Stage.HELLO_DONE; true }
        }
        if (!admitAfterHello) throw LiteRtLmError.RuntimeFailure()

        val startRequest = LiteRtLmProcessStart(
            modelAbsolutePath = modelPath.absolutePath,
            imageAbsolutePath = image.absolutePath,
            cacheAbsolutePath = cacheDir.absolutePath,
            prompt = prompt,
            maxOutputTokens = maxOutputTokens,
            backendPolicy = backendPolicy,
        )

        // START admission linearization point (HIGH #2 exact point): this locked transition commits
        // to invoking START. HELLO/reserve already completed, so a remote HELLO owner may ALREADY
        // exist before this point; the coordinator guard (not [stage] or the generation-attempt
        // flag) decides local release vs ambiguous ownership. A close that wins here or later keeps
        // TERMINATED terminal and rejects generation instead of allowing a late SUCCEEDED to escape.
        val admitStart = synchronized(lock) {
            if (stage == Stage.TERMINATED) false else { stage = Stage.ADMITTING_START; true }
        }
        if (!admitStart) throw LiteRtLmError.RuntimeFailure()

        val started = connection.start(startRequest)
        if (!started) {
            // Explicit START false: the coordinator keeps the remote owner; fail generation without
            // fabricating a result or releasing ownership.
            throw LiteRtLmError.RuntimeFailure()
        }
        // close()/cancelProcess() may have won (TERMINATED) while the START transact was in flight.
        // TERMINATED is terminal: never overwrite it with START_DONE. A close that won even though
        // START had been admitted still takes precedence, so reject generation here rather than
        // entering the status loop (where a late SUCCEEDED could otherwise escape after close).
        val startStillAdmitted = synchronized(lock) {
            if (stage == Stage.TERMINATED) false else { stage = Stage.START_DONE; true }
        }
        if (!startStillAdmitted) throw LiteRtLmError.RuntimeFailure()

        var finalText: String? = null
        while (true) {
            // STATUS is polled off-main with a cooperative delay; status calls do not block
            // concurrent cancellation/retirement (those run on an independent IO scope).
            val status = connection.status()
            ingestStatus(status, onSnapshot)
            when (status.generation) {
                "SUCCEEDED" -> {
                    // A cancel/close requested before success takes precedence over a late success.
                    if (cancelled.get() || stage == Stage.TERMINATED) throw LiteRtLmError.RuntimeFailure()
                    finalText = assembleFinal(status)
                    break
                }
                "CANCELED", "ERROR", "LOST" -> throw LiteRtLmError.RuntimeFailure()
                else -> delay(STATUS_POLL_DELAY_MILLIS)
            }
        }
        checkNotNull(finalText)
    }

    override fun cancelProcess() {
        val won = synchronized(lock) {
            if (cancelled.compareAndSet(false, true)) {
                deliverySuppressed.set(true)
                if (stage == Stage.TERMINATED) {
                    false
                } else {
                    stage = Stage.TERMINATED
                    true
                }
            } else {
                false
            }
        }
        if (!won) return
        // Returns immediately. The actual release work runs on an independent IO scope and never
        // holds a monitor across the blocking Binder work. We do NOT join here (that is close()'s
        // job); awaitTerminated() resolves via the same exact-death/local-release barrier.
        ensureFinalize()
    }

    override suspend fun awaitTerminated() {
        // Pure barrier on the exact registered binder death (or confirmed local release). It must
        // not fabricate a native drain or use RETIRE reply / isBinderAlive / timeouts as death.
        connection.awaitOwnershipReleased()
    }

    override suspend fun close() {
        // Record termination under [lock]: keep TERMINATED terminal, record cancellation so a late
        // SUCCEEDED cannot escape, and suppress callbacks after close. No lock is held across the
        // suspension below.
        synchronized(lock) {
            if (stage != Stage.TERMINATED) stage = Stage.TERMINATED
            cancelled.set(true)
            deliverySuppressed.set(true)
        }
        ensureFinalize()
        // Every concurrent/repeated close awaits the SAME finalization operation. A genuine finalize
        // failure was completed exceptionally on the barrier, so await() propagates it (a detached
        // Job.join() would hide it). This is the Client's ordinary CompletableDeferred.await pattern.
        finalizeBarrier.await()
    }

    private fun ensureFinalize() {
        // Start the single finalization operation at most once (guard under [lock]); every caller
        // (close, and a winning cancelProcess) shares it. The non-blocking launch records the
        // genuine outcome on [finalizeBarrier] so all await()ing closes see the same completion or
        // exception. No monitor is held across Binder work or suspension.
        val start = synchronized(lock) {
            if (finalizeStarted) false else { finalizeStarted = true; true }
        }
        if (!start) return
        lifecycleScope.launch {
            try {
                runFinalize()
                finalizeBarrier.complete(Unit)
            } catch (failure: Throwable) {
                finalizeBarrier.completeExceptionally(failure)
            }
        }
    }

    /**
     * The single ownership-release barrier, run exactly once on the lifecycle scope. It probes the
     * coordinator-guarded [LiteRtLmProcessConnection.releaseBeforeReservation] (the authoritative
     * source for beginHello, NOT the generation-attempt flag):
     *   - true  => safe local release; await its completion (cleanup + null) and return.
     *   - false => HELLO may have begun; ownership is ambiguous and retained. Send best-effort
     *              CANCEL + RETIRE, then await the exact registered binder death.
     * The ownership-release await is not swallowed locally: any genuine failure is surfaced to
     * close callers through [finalizeBarrier].
     */
    private suspend fun runFinalize() {
        if (connection.releaseBeforeReservation()) {
            // Safe local release already performed; await its full completion (cleanup + null).
            connection.awaitOwnershipReleased()
            return
        }
        // Ambiguous / remote ownership: best-effort remote CANCEL then RETIRE, then wait on the
        // exact death / local-release barrier. We do not catch/erase the barrier outcome.
        runCatching { connection.cancel() }
        runCatching { connection.retire() }
        connection.awaitOwnershipReleased()
    }

    private fun ingestStatus(status: LiteRtLmProcessStatus, onSnapshot: (String) -> Unit) {
        val chunks = status.rawChunks
        synchronized(lock) {
            if (chunks.size < observedLength) {
                // History shrank: a previously observed raw chunk was dropped. Never fabricate.
                throw LiteRtLmError.RuntimeFailure()
            }
            // In CUMULATIVE mode the previous full text is the last previously delivered snapshot;
            // every newly observed snapshot must extend it (no rewritten semantic cumulative).
            var previousFull: String? =
                if (callbackMode == LiteRtLmCallbackMode.CUMULATIVE && deliveredChunks.isNotEmpty()) {
                    deliveredChunks.last()
                } else {
                    null
                }
            for (index in chunks.indices) {
                if (index < observedLength) {
                    if (deliveredChunks[index] != chunks[index]) {
                        // Immutable prefix violated: an observed raw chunk was rewritten.
                        throw LiteRtLmError.RuntimeFailure()
                    }
                } else {
                    val chunk = chunks[index]
                    if (callbackMode == LiteRtLmCallbackMode.CUMULATIVE) {
                        if (previousFull != null && !chunk.startsWith(previousFull)) {
                            // A new cumulative snapshot that does not extend the previous full text
                            // is a rewritten semantic cumulative snapshot; reject it.
                            throw LiteRtLmError.RuntimeFailure()
                        }
                        previousFull = chunk
                    }
                    deliveredChunks.add(chunk)
                    if (!deliverySuppressed.get()) onSnapshot(chunk)
                }
            }
            observedLength = chunks.size
        }
    }

    private fun assembleFinal(status: LiteRtLmProcessStatus): String {
        // Assemble the returned text from the explicit mode only. Raw whitespace is preserved when
        // the result is nonempty; an empty final text (either [] or [empty string]) is no result.
        val text = when (callbackMode) {
            LiteRtLmCallbackMode.DELTA -> status.rawChunks.joinToString("")
            LiteRtLmCallbackMode.CUMULATIVE -> status.rawChunks.lastOrNull() ?: ""
        }
        if (text.isEmpty()) throw LiteRtLmError.NoResult()
        return text
    }

    private companion object {
        const val STATUS_POLL_DELAY_MILLIS = 5L
    }
}
