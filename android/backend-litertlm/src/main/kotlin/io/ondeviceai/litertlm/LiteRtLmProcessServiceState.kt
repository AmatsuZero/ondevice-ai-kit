package io.ondeviceai.litertlm

/**
 * Phase of the single owner admitted for the whole dedicated service-process lifetime.
 *
 * Pure Kotlin state: no Android/vendor runtime types, no new dependency, and no filesystem,
 * native, clock, timer or process effects. [LiteRtLmGenerationTerminal] is reused from the
 * supplied process coordinator. A terminal outcome is semantic only: it never releases the
 * owner and never proves native drain.
 */
internal enum class LiteRtLmServicePhase { RESERVED, INITIALIZING, GENERATING, TERMINAL }

/** Immutable-by-contract snapshot copy; [rawChunks] is a detached list. */
internal data class LiteRtLmServiceStateSnapshot(
    val ownerNonce: String,
    val phase: LiteRtLmServicePhase,
    val terminal: LiteRtLmGenerationTerminal?,
    val nativeAttempted: Boolean,
    val nativeInitialized: Boolean,
    val rawChunks: List<String>,
    val errorCode: String?,
)

/**
 * Synchronized, atomic single-owner state for the dedicated-process Binder service.
 * Every mutator and the snapshot reader are serialized on [lock], so reservation and phase
 * transitions are atomic. Wrong/stale nonces never mutate state and produce a null snapshot.
 */
internal class LiteRtLmProcessServiceState(
    private val maxSnapshots: Int = 256,
    private val maxTotalUtf8Bytes: Int = 32768,
    private val maxChunkUtf8Bytes: Int = 8192,
) {
    private val lock = Any()

    private var ownerNonce: String? = null
    private var phase = LiteRtLmServicePhase.RESERVED
    private var terminal: LiteRtLmGenerationTerminal? = null
    private var nativeAttempted = false
    private var nativeInitialized = false
    private val rawChunks = ArrayList<String>()
    private var totalUtf8Bytes = 0
    private var errorCode: String? = null

    init {
        require(maxSnapshots > 0) { "maxSnapshots must be positive" }
        require(maxTotalUtf8Bytes > 0) { "maxTotalUtf8Bytes must be positive" }
        require(maxChunkUtf8Bytes > 0) { "maxChunkUtf8Bytes must be positive" }
        require(maxChunkUtf8Bytes <= maxTotalUtf8Bytes) {
            "maxChunkUtf8Bytes must not exceed maxTotalUtf8Bytes"
        }
    }

    fun reserve(nonce: String): Boolean = synchronized(lock) {
        when {
            ownerNonce != null -> false          // one owner for the whole process lifetime
            !isValidNonce(nonce) -> false
            else -> {
                ownerNonce = nonce
                phase = LiteRtLmServicePhase.RESERVED
                true
            }
        }
    }

    fun begin(nonce: String): Boolean = synchronized(lock) {
        if (ownerValid(nonce) && phase == LiteRtLmServicePhase.RESERVED) {
            phase = LiteRtLmServicePhase.INITIALIZING
            true
        } else false
    }

    fun markNativeAttempted(nonce: String): Boolean = synchronized(lock) {
        if (ownerValid(nonce) && phase == LiteRtLmServicePhase.INITIALIZING && !nativeAttempted) {
            nativeAttempted = true
            true
        } else false
    }

    fun markNativeInitialized(nonce: String): Boolean = synchronized(lock) {
        // Truthfully recordable after CANCEL; never changes terminal/phase, never starts generation.
        if (ownerValid(nonce) && nativeAttempted && !nativeInitialized) {
            nativeInitialized = true
            true
        } else false
    }

    fun beginGeneration(nonce: String): Boolean = synchronized(lock) {
        // INITIALIZING guard makes a canceled (TERMINAL) state unable to start a generation.
        if (ownerValid(nonce) && nativeInitialized && phase == LiteRtLmServicePhase.INITIALIZING) {
            phase = LiteRtLmServicePhase.GENERATING
            true
        } else false
    }

    fun append(nonce: String, text: String): Boolean = synchronized(lock) {
        when {
            !ownerValid(nonce) -> false
            phase != LiteRtLmServicePhase.GENERATING || terminal != null -> false
            text.isEmpty() -> false              // ignored/rejected without failing state
            else -> {
                val bytes = utf8Length(text)
                if (bytes > maxChunkUtf8Bytes ||
                    rawChunks.size + 1 > maxSnapshots ||
                    totalUtf8Bytes + bytes > maxTotalUtf8Bytes
                ) {
                    terminal = LiteRtLmGenerationTerminal.ERROR
                    phase = LiteRtLmServicePhase.TERMINAL
                    errorCode = "OutputOverflow"
                    false                         // overflowing chunk not retained
                } else {
                    rawChunks.add(text)           // exact opaque chunk: no trim/normalize/delta
                    totalUtf8Bytes += bytes
                    true
                }
            }
        }
    }

    fun complete(nonce: String): Boolean = synchronized(lock) {
        if (!ownerValid(nonce) || phase != LiteRtLmServicePhase.GENERATING || terminal != null) {
            false
        } else {
            if (rawChunks.any { it.isNotBlank() }) {
                terminal = LiteRtLmGenerationTerminal.SUCCEEDED
            } else {
                terminal = LiteRtLmGenerationTerminal.ERROR
                errorCode = "NoOutput"
            }
            phase = LiteRtLmServicePhase.TERMINAL
            true
        }
    }

    fun fail(nonce: String, errorCode: String): Boolean = synchronized(lock) {
        if (!ownerValid(nonce) || terminal != null) {
            false
        } else {
            terminal = LiteRtLmGenerationTerminal.ERROR
            this.errorCode = safeErrorCode(errorCode)
            phase = LiteRtLmServicePhase.TERMINAL
            true
        }
    }

    fun cancel(nonce: String): Boolean = synchronized(lock) {
        if (!ownerValid(nonce)) {
            false
        } else {
            if (terminal == null) {
                terminal = LiteRtLmGenerationTerminal.CANCELED
                phase = LiteRtLmServicePhase.TERMINAL
            }
            true                                 // existing SUCCEEDED/ERROR/CANCELED preserved
        }
    }

    fun snapshot(nonce: String): LiteRtLmServiceStateSnapshot? = synchronized(lock) {
        if (!ownerValid(nonce)) {
            null
        } else {
            LiteRtLmServiceStateSnapshot(
                ownerNonce = ownerNonce!!,
                phase = phase,
                terminal = terminal,
                nativeAttempted = nativeAttempted,
                nativeInitialized = nativeInitialized,
                rawChunks = rawChunks.toList(),  // detached copy: earlier snapshots stay unchanged
                errorCode = errorCode,
            )
        }
    }

    private fun ownerValid(nonce: String): Boolean = ownerNonce != null && ownerNonce == nonce

    // Explicit ASCII ranges reject non-ASCII characters (e.g. "non-ascii-é").
    private fun isValidNonce(nonce: String): Boolean {
        if (nonce.isEmpty() || nonce.length > 128) return false
        for (c in nonce) {
            if (c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c != '_' && c != '-') {
                return false
            }
        }
        return true
    }

    // Matches [A-Za-z][A-Za-z0-9_]{0,63} without a regex dependency.
    private fun isSafeErrorCode(code: String): Boolean {
        if (code.isEmpty() || code.length > 64) return false
        val first = code[0]
        if (first !in 'A'..'Z' && first !in 'a'..'z') return false
        for (i in 1 until code.length) {
            val c = code[i]
            if (c !in 'A'..'Z' && c !in 'a'..'z' && c !in '0'..'9' && c != '_') return false
        }
        return true
    }

    private fun safeErrorCode(code: String): String =
        if (isSafeErrorCode(code)) code else "RuntimeFailure"

    private fun utf8Length(text: String): Int = text.toByteArray(Charsets.UTF_8).size
}
