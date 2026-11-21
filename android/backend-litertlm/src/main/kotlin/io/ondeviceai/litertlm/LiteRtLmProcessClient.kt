package io.ondeviceai.litertlm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.ondeviceai.multimodal.MultimodalClientError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real Context/Binder client for the dedicated-process LiteRT-LM service.
 *
 * Ownership model: [LiteRtLmProcessClient.connect] only claims the shared coordinator, binds
 * the explicit [LiteRtLmProcessService] component, and links the ORIGINAL binder's death
 * recipient -- it never sends HELLO. HELLO and remote reservation are deferred to
 * [LiteRtLmProcessConnection.reserve]. Consequently every bind/link/cancellation failure that
 * happens before HELLO is a proven "no remote owner acquired" situation and is reported with the
 * trusted [LiteRtLmPreOwnershipRejection] after a safe local-unreserved release.
 *
 * All blocking transacts and bind/link/unbind work run on [Dispatchers.IO]; ServiceConnection and
 * DeathRecipient callbacks only publish signals. Ownership-wait completion is driven solely by a
 * confirmed local-unreserved release (null) or the exact registered binder's death (coordinator
 * exactDeath outcome), never by status/terminal/cancel/retire/timeout/unbind/disconnect,
 * isBinderAlive, or waiter cancellation.
 */
internal class LiteRtLmProcessClient(
    context: Context,
    private val coordinator: LiteRtLmProcessCoordinator = LiteRtLmProcessCoordinator.shared,
) {
    // Binding via applicationContext honors a ContextWrapper (the test wrapper overrides
    // getApplicationContext() to return itself), so bind/unbind go through its overrides.
    private val appContext: Context = context.applicationContext ?: context

    suspend fun connect(nonce: String): LiteRtLmProcessConnection {
        var undelivered: LiteRtLmProcessConnection? = null
        var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                val claim = try {
                    coordinator.claim(nonce)
                } catch (busy: MultimodalClientError.Busy) {
                    // A competing client is rejected at claim time, before any second bind.
                    throw LiteRtLmPreOwnershipRejection(busy)
                }
                val connection = LiteRtLmProcessConnection(claim, nonce, coordinator, appContext)
                undelivered = connection
                connection.performConnect()
                connection
            }
            delivered = true
            return result
        } finally {
            // withContext result-dispatch cancellation can discard a fully created (but
            // undelivered) connection; clean it locally, non-cancellably and off-main. The same
            // path covers every other pre-HELLO connect failure (like ModelStore.acquire).
            if (!delivered) {
                withContext(NonCancellable + Dispatchers.IO) {
                    undelivered?.cleanupLocal()
                }
            }
        }
    }
}

private sealed class BindSignal {
    object Connected : BindSignal()
    object Failed : BindSignal()
}

internal class LiteRtLmProcessConnection internal constructor(
    private val claim: LiteRtLmProcessClaim,
    private val nonce: String,
    private val coordinator: LiteRtLmProcessCoordinator,
    private val appContext: Context,
) {
    private val expectedProcessName = appContext.packageName + PROCESS_SUFFIX

    private val stateLock = Any()

    @Volatile
    private var originalBinder: IBinder? = null

    @Volatile
    private var transportRef: LiteRtLmProcessTransport? = null
    private var connected = false
    private var terminated = false

    private val bindResult = CompletableDeferred<BindSignal>()
    private val ownershipReleased = CompletableDeferred<LiteRtLmProcessDeathOutcome?>()

    private val deathUnlinked = AtomicBoolean(false)
    private val unbound = AtomicBoolean(false)
    private val deathRecipientFired = AtomicBoolean(false)
    private val ownershipDecided = AtomicBoolean(false)
    private val cleanupStarted = AtomicBoolean(false)
    private val reserveAttempted = AtomicBoolean(false)
    private val startAccepted = AtomicBoolean(false)
    private val terminalRecorded = AtomicBoolean(false)

    // Death handling and late-callback unbinds must not run on a Binder callback thread.
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) {
                if (!bindResult.complete(BindSignal.Failed)) offloadUnbind()
                return
            }
            val first = synchronized(stateLock) {
                if (!connected && !terminated) {
                    originalBinder = service
                    transportRef = LiteRtLmProcessTransport(service)
                    connected = true
                    true
                } else {
                    false
                }
            }
            if (first) bindResult.complete(BindSignal.Connected)
            // A late/duplicate onServiceConnected never replaces the original remote, rebinds,
            // or unbinds.
        }

        override fun onNullBinding(name: ComponentName?) {
            // Official contract: onNullBinding still requires unbindService.
            if (!bindResult.complete(BindSignal.Failed)) offloadUnbind()
        }

        override fun onBindingDied(name: ComponentName?) {
            // Official contract: onBindingDied requires unbind (never rebind); it is NOT exact
            // binder-death evidence.
            if (!bindResult.complete(BindSignal.Failed)) offloadUnbind()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Never proves exact binder death; a no-op failure signal once bind is resolved.
            bindResult.complete(BindSignal.Failed)
        }
    }

    private val deathRecipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            // Binder callback thread: only record + signal; all work is offloaded to IO.
            if (deathRecipientFired.compareAndSet(false, true)) {
                lifecycleScope.launch { handleExactBinderDeath() }
            }
        }
    }

    // ---- connect (runs on Dispatchers.IO, inside the client's withContext) ----

    internal suspend fun performConnect() {
        val intent = Intent(appContext, LiteRtLmProcessService::class.java)
        val bound = try {
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // SecurityException / any bind failure is a proven no-HELLO failure. Official cleanup
            // (unbindService) is performed by cleanupLocal even though bindService threw.
            throw preOwnershipRejection("LiteRT-LM process bindService failed")
        }
        if (!bound) {
            // Official contract: a false return still requires unbindService (cleanupLocal).
            throw preOwnershipRejection("LiteRT-LM process bindService returned false")
        }
        val signal = bindResult.await() // cancellable, callback-driven; no auto-retry
        if (signal !== BindSignal.Connected) {
            throw preOwnershipRejection("LiteRT-LM process has no usable binding")
        }
        val theBinder = originalBinder
            ?: throw preOwnershipRejection("LiteRT-LM process binder missing")
        try {
            // Define the exact remote identity first, then link death on the ORIGINAL binder so a
            // racing death is still resolvable by exactDeath.
            coordinator.linkDeath(claim, theBinder)
            theBinder.linkToDeath(deathRecipient, 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            throw preOwnershipRejection("LiteRT-LM process death link failed")
        }
    }

    // ---- reserve / start / status / cancel / retire ----

    suspend fun reserve(): LiteRtLmProcessHello {
        // reserve only once; a repeat rejects locally.
        if (!reserveAttempted.compareAndSet(false, true)) {
            throw IllegalStateException("LiteRT-LM HELLO already attempted")
        }
        return withContext(Dispatchers.IO) {
            val theBinder = originalBinder
                ?: throw IllegalStateException("no registered LiteRT-LM binder")
            val tx = transportRef
                ?: throw IllegalStateException("no LiteRT-LM transport")
            // beginHello IMMEDIATELY before the actual HELLO transact.
            coordinator.beginHello(claim, theBinder)
            // A thrown/missing/malformed reply after beginHello stays ambiguous and owned until
            // the registered exact death; it is never wrapped as a safe/no-owner rejection and no
            // stale coordinator call is made in the failure path.
            val hello = tx.hello(nonce)
            if (!hello.accepted) {
                // Explicit, well-formed HELLO rejection: helloRejected + release binding +
                // trusted safe rejection.
                if (coordinator.helloRejected(claim)) {
                    markTerminated()
                    cleanupBindingOnce()
                    completeLocalRelease()
                }
                throw preOwnershipRejection("LiteRT-LM HELLO explicitly rejected")
            }
            // Validate exact identity BEFORE reserveRemote.
            if (hello.nonce != nonce || hello.pid <= 0 || hello.processName != expectedProcessName) {
                // Invalid identity after beginHello is conservatively ambiguous (keep owned until
                // registered exact death), never a safe/no-owner signal.
                throw IllegalStateException("invalid LiteRT-LM HELLO identity")
            }
            coordinator.reserveRemote(claim)
            hello
        }
    }

    suspend fun start(request: LiteRtLmProcessStart): Boolean = withContext(Dispatchers.IO) {
        val accepted = transport().start(nonce, request)
        if (accepted) {
            try {
                coordinator.acceptStart(claim)
                startAccepted.set(true)
            } catch (_: IllegalStateException) {
                // A concurrent exact death may have cleared the claim; do not mask the transact
                // result with a stale coordinator call.
            }
        }
        // An explicit false START never releases remote ownership.
        accepted
    }

    suspend fun status(): LiteRtLmProcessStatus = withContext(Dispatchers.IO) {
        val decoded = transport().status(nonce)
        // Record terminal on the coordinator only when START was accepted; otherwise merely
        // observe the status. The coordinator keeps SUCCESS vs CANCELED precedence.
        if (startAccepted.get() && decoded.phase == "TERMINAL" &&
            terminalRecorded.compareAndSet(false, true)
        ) {
            val terminal = terminalOf(decoded.generation)
            if (terminal != null) {
                try {
                    coordinator.recordTerminal(claim, terminal)
                } catch (_: IllegalStateException) {
                    // Stale/conflicting coordinator state must not mask the decoded status.
                }
            }
        }
        // Exact decoded raw chunks are returned unchanged; no aggregation.
        decoded
    }

    suspend fun cancel(): Boolean = withContext(Dispatchers.IO) {
        // Record coordinator cancel first, then the remote CANCEL; never release ownership.
        try {
            coordinator.requestCancel(claim)
        } catch (_: IllegalStateException) {
            // Stale claim (death won the race): do not mask the remote result.
        }
        transport().cancel(nonce)
    }

    suspend fun retire(): Boolean = withContext(Dispatchers.IO) {
        // RETIRE reply is separate from actual binder death; the reply never releases the claim.
        transport().retire(nonce)
    }

    fun releaseBeforeReservation(): Boolean {
        // Delegates to the coordinator guard: false after beginHello/reserved even if START was
        // rejected, CANCELED, or RETIRE accepted.
        val released = try {
            coordinator.releaseBeforeRemoteReservation(claim)
        } catch (_: Throwable) {
            false
        }
        if (released) {
            markTerminated()
            lifecycleScope.launch {
                cleanupBindingOnce()
                completeLocalRelease()
            }
        }
        return released
    }

    suspend fun awaitOwnershipReleased(): LiteRtLmProcessDeathOutcome? = ownershipReleased.await()

    // ---- cleanup / exact death ----

    /** Confirmed local-unreserved release: idempotent, performed before any HELLO. */
    internal fun cleanupLocal() {
        if (!cleanupStarted.compareAndSet(false, true)) return
        markTerminated()
        cleanupBindingOnce()
        val released = try {
            coordinator.releaseBeforeRemoteReservation(claim)
        } catch (_: Throwable) {
            false
        }
        if (released) completeLocalRelease()
    }

    private fun handleExactBinderDeath() {
        val theBinder = originalBinder ?: return
        val outcome = try {
            coordinator.exactDeath(claim, theBinder)
        } catch (_: Throwable) {
            null
        }
        // null means a stale callback / newer shared claim; never fabricate death or clear it.
        if (outcome == null) return
        if (ownershipDecided.compareAndSet(false, true)) {
            cleanupBindingOnce()
            ownershipReleased.complete(outcome) // nonnull only from exact registered binder death
        }
    }

    private fun offloadUnbind() {
        lifecycleScope.launch { unbindOnce() }
    }

    private fun cleanupBindingOnce() {
        unlinkDeathOnce()
        unbindOnce()
    }

    private fun unlinkDeathOnce() {
        if (!deathUnlinked.compareAndSet(false, true)) return
        val b = originalBinder ?: return
        try {
            b.unlinkToDeath(deathRecipient, 0)
        } catch (_: Throwable) {
            // best-effort cleanup
        }
    }

    private fun unbindOnce() {
        if (!unbound.compareAndSet(false, true)) return
        try {
            appContext.unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
            // Expected already-unbound / never-registered connection (bindService false or a
            // SecurityException). Do not mask the original error and never invent remote death.
        } catch (_: RuntimeException) {
            // Any other cleanup-time failure must not mask the original error either.
        }
    }

    private fun completeLocalRelease() {
        // The ONLY source of a null outcome: a confirmed local-unreserved release.
        if (ownershipDecided.compareAndSet(false, true)) {
            ownershipReleased.complete(null)
        }
    }

    private fun markTerminated() {
        synchronized(stateLock) { terminated = true }
    }

    private fun transport(): LiteRtLmProcessTransport =
        transportRef ?: throw IllegalStateException("no LiteRT-LM transport")

    private fun terminalOf(generation: String): LiteRtLmGenerationTerminal? = when (generation) {
        "SUCCEEDED" -> LiteRtLmGenerationTerminal.SUCCEEDED
        "CANCELED" -> LiteRtLmGenerationTerminal.CANCELED
        "ERROR" -> LiteRtLmGenerationTerminal.ERROR
        "LOST" -> LiteRtLmGenerationTerminal.LOST
        else -> null
    }

    private companion object {
        const val PROCESS_SUFFIX = ":odai_litertlm"

        fun preOwnershipRejection(reason: String): LiteRtLmPreOwnershipRejection =
            LiteRtLmPreOwnershipRejection(MultimodalClientError.InvalidRegistration(reason))
    }
}
