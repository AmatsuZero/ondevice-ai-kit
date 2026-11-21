package io.ondeviceai.litertlm

internal enum class LiteRtLmProcessPhase {
    LOCAL_CLAIMED,
    DEATH_LINKED,
    REMOTE_RESERVED,
    START_ACCEPTED,
    TERMINAL,
    EXACT_DEATH,
}

internal enum class LiteRtLmGenerationTerminal { SUCCEEDED, CANCELED, ERROR, LOST }

internal class LiteRtLmProcessClaim internal constructor(
    internal val generation: Long,
    internal val nonce: String?,
)

internal data class LiteRtLmProcessDeathOutcome(val terminal: LiteRtLmGenerationTerminal)

/**
 * Process-wide ownership guard for the future dedicated-process Binder adapter.
 *
 * This class does not own native resources. The adapter must keep its ModelStore lease until
 * [exactDeath] returns a non-null outcome for its exact claim and remote identity.
 */
internal class LiteRtLmProcessCoordinator {
    internal companion object {
        val shared = LiteRtLmProcessCoordinator()
    }
    private data class Active(
        val claim: LiteRtLmProcessClaim,
        var phase: LiteRtLmProcessPhase,
        var remoteIdentity: Any? = null,
        var helloInFlight: Boolean = false,
        var terminal: LiteRtLmGenerationTerminal? = null,
        var cancellationRequested: Boolean = false,
    )

    private val lock = Any()
    private var nextGeneration = 0L
    private var active: Active? = null

    fun claim(nonce: String? = null): LiteRtLmProcessClaim = synchronized(lock) {
        if (active != null) throw io.ondeviceai.multimodal.MultimodalClientError.Busy()
        LiteRtLmProcessClaim(++nextGeneration, nonce).also {
            active = Active(it, LiteRtLmProcessPhase.LOCAL_CLAIMED)
        }
    }

    fun linkDeath(claim: LiteRtLmProcessClaim, remote: Any) = synchronized(lock) {
        val current = requireActive(claim)
        check(current.phase == LiteRtLmProcessPhase.LOCAL_CLAIMED)
        current.remoteIdentity = remote
        current.phase = LiteRtLmProcessPhase.DEATH_LINKED
    }

    /**
     * Must run immediately before the HELLO transact. A missing reply is ambiguous and therefore
     * cannot be released locally; [exactDeath] can still resolve the linked remote identity.
     */
    fun beginHello(claim: LiteRtLmProcessClaim, remote: Any) = synchronized(lock) {
        val current = requireActive(claim)
        check(current.phase == LiteRtLmProcessPhase.DEATH_LINKED &&
            current.remoteIdentity === remote && !current.helloInFlight)
        current.helloInFlight = true
    }

    /** Only an explicit remote HELLO rejection makes the in-flight handshake locally releasable. */
    fun helloRejected(claim: LiteRtLmProcessClaim): Boolean = synchronized(lock) {
        val current = active ?: return@synchronized false
        if (current.claim !== claim || !current.helloInFlight) return@synchronized false
        active = null
        true
    }

    fun reserveRemote(claim: LiteRtLmProcessClaim) = synchronized(lock) {
        val current = requireActive(claim)
        check(current.phase == LiteRtLmProcessPhase.DEATH_LINKED &&
            current.remoteIdentity != null && current.helloInFlight)
        current.helloInFlight = false
        current.phase = LiteRtLmProcessPhase.REMOTE_RESERVED
    }

    /** Missing HELLO is not a local failure: it can no longer be released without exact death. */
    fun markRemoteOwnershipAmbiguous(claim: LiteRtLmProcessClaim) = synchronized(lock) {
        val current = requireActive(claim)
        check(current.phase == LiteRtLmProcessPhase.DEATH_LINKED && current.helloInFlight &&
            current.remoteIdentity != null)
    }

    fun acceptStart(claim: LiteRtLmProcessClaim) = synchronized(lock) {
        val current = requireActive(claim)
        check(current.phase == LiteRtLmProcessPhase.REMOTE_RESERVED)
        current.phase = LiteRtLmProcessPhase.START_ACCEPTED
    }

    /** Returns true only for a confirmed local pre-reservation failure. */
    fun rejectStart(claim: LiteRtLmProcessClaim): Boolean = releaseBeforeRemoteReservation(claim)

    fun requestCancel(claim: LiteRtLmProcessClaim) = synchronized(lock) {
        val current = requireActive(claim)
        if (current.terminal == null) current.cancellationRequested = true
    }

    fun recordTerminal(claim: LiteRtLmProcessClaim, terminal: LiteRtLmGenerationTerminal) = synchronized(lock) {
        val current = requireActive(claim)
        check(current.phase == LiteRtLmProcessPhase.START_ACCEPTED ||
            current.phase == LiteRtLmProcessPhase.TERMINAL)
        val resolved = if (current.cancellationRequested && terminal == LiteRtLmGenerationTerminal.SUCCEEDED) {
            LiteRtLmGenerationTerminal.CANCELED
        } else {
            terminal
        }
        val existing = current.terminal
        if (existing == null) {
            current.terminal = resolved
            current.phase = LiteRtLmProcessPhase.TERMINAL
        } else {
            check(existing == resolved) { "conflicting LiteRT-LM process terminal" }
        }
    }

    fun releaseBeforeRemoteReservation(claim: LiteRtLmProcessClaim): Boolean = synchronized(lock) {
        val current = active ?: return@synchronized false
        if (current.claim !== claim || current.helloInFlight || current.phase !in setOf(
                LiteRtLmProcessPhase.LOCAL_CLAIMED, LiteRtLmProcessPhase.DEATH_LINKED,
            )) {
            return@synchronized false
        }
        active = null
        true
    }

    /** Only an exact linked remote death releases ownership. Terminal status alone never does. */
    fun exactDeath(claim: LiteRtLmProcessClaim, remote: Any): LiteRtLmProcessDeathOutcome? = synchronized(lock) {
        val current = active ?: return@synchronized null
        if (current.claim !== claim || current.remoteIdentity !== remote) return@synchronized null
        current.phase = LiteRtLmProcessPhase.EXACT_DEATH
        active = null
        LiteRtLmProcessDeathOutcome(current.terminal ?: LiteRtLmGenerationTerminal.LOST)
    }

    private fun requireActive(claim: LiteRtLmProcessClaim): Active {
        val current = checkNotNull(active) { "no active LiteRT-LM process claim" }
        check(current.claim === claim) { "stale LiteRT-LM process claim" }
        return current
    }
}
