package io.ondeviceai.litertlm

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * W5 gate: the real production chain has never done native inference in its current code.
 *
 * This single androidTest drives the REAL [LiteRtLmProcessClient] -> real production
 * [LiteRtLmProcessService] (dedicated ":odai_litertlm" process) -> pinned native
 * Engine.sendMessageAsync. It does NOT use [LiteRtLmProcessSession]/[LiteRtLmProcessEngineFactory]
 * (so it never assembles a DELTA/CUMULATIVE semantic result and never changes a public/default
 * factory), and it does not duplicate the 18+3 scripted protocol/session regressions or the 90
 * host tests.
 *
 * Three sequential real service incarnations: (1) normal -> nonempty SUCCEEDED, (2) a longer
 * generation actively CANCEL-led -> CANCELED, then RETIRE, (3) reopen -> nonempty SUCCEEDED.
 * Ownership is retained after every semantic terminal and released ONLY by the exact registered
 * binder death; the RETIRE reply, onServiceDisconnected, isBinderAlive, timeout, PID disappearance
 * and terminal status are never treated as death.
 *
 * callbackMode stays "UNPROVEN": raw callback chunks are preserved verbatim (no trimming, no
 * joining, no semantic-correctness claim, no production schema change).
 *
 * REQUIRED instrumentation argument: backendPolicy. Only "CPU_ONLY" or "CPU_TEXT_GPU_VISION" are
 * accepted (strictly validated before any bind, no default, no auto-fallback). The chosen policy is
 * forwarded verbatim to the production START; the production support gate (already CPU-correct)
 * decides usability and is NOT edited here.
 */
class LiteRtLmProcessNativeInstrumentation {

    @Test
    fun realServiceNativeThreeIncarnations() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = instrumentation.targetContext
            val arguments = InstrumentationRegistry.getArguments()
            val mainPid = Process.myPid()
            val expectedProcessName = targetContext.packageName + PROCESS_SUFFIX

            var failure: Throwable? = null
            val cleanupErrors = ArrayList<Throwable>(2)
            var pendingConnection: LiteRtLmProcessConnection? = null
            var receiptRef: Receipt? = null
            var scrubModel: String? = null
            var scrubImage: String? = null
            var scrubCache: String? = null
            var scrubReceipt: String? = null

            try {
                // runId names the exclusive receipt and cache root; validate it first (pre-bind).
                // A missing/malformed runId or a pre-existing keyed file cannot produce a receipt
                // we are allowed to write (exclusive, never overwrite/delete), so those fail fast.
                val runId = requireRunId(arguments)
                // backendPolicy is a REQUIRED instrumentation argument; validate strictly before
                // any bind. Only CPU_ONLY or CPU_TEXT_GPU_VISION are supported; there is no default
                // and no auto-fallback. The production support gate (already CPU-correct) decides
                // whether a given policy is usable; this instrumentation only forwards the chosen
                // policy and never assumes CPU support on its own.
                val backendPolicy = requireBackendPolicy(arguments)
                val receiptFile = createReceiptExclusive(targetContext, runId)
                scrubReceipt = receiptFile.absolutePath
                val receipt = Receipt(receiptFile, runId, mainPid, expectedProcessName)
                receiptRef = receipt
                receipt.setBackendPolicy(backendPolicy.name)
                // First fsync'd running receipt (exclusive file already created), before any bind.
                receipt.checkpoint("preflight", null, force = true)
                // ---- validate every remaining input BEFORE any bind; read-only, hashed once ----
                receipt.checkpoint("validating-inputs", null, force = true)
                val modelPath = requireFileArgument(arguments, "modelPath")
                val imagePath = requireFileArgument(arguments, "imagePath")
                val expectedModelSha = requireSha256Argument(arguments, "expectedModelSha256")
                val expectedImageSha = requireSha256Argument(arguments, "expectedImageSha256")
                scrubModel = modelPath
                scrubImage = imagePath

                receipt.checkpoint("hashing-model", null, force = true)
                // Stream each input exactly once on IO (never per cycle); hashing is ~3.6GB and
                // must not block the single runBlocking event loop. Inputs stay read-only.
                val (modelHash, imageHash) = withContext(Dispatchers.IO) {
                    Pair(sha256HexAndLength(File(modelPath)), sha256HexAndLength(File(imagePath)))
                }
                if (!modelHash.sha.equals(expectedModelSha, ignoreCase = true)) {
                    throw AssertionError("model sha256 mismatch: fail before bind")
                }
                if (!imageHash.sha.equals(expectedImageSha, ignoreCase = true)) {
                    throw AssertionError("image sha256 mismatch: fail before bind")
                }

                val appContext: Context = targetContext.applicationContext ?: targetContext
                val cacheRoot = createCacheRootExclusive(targetContext, runId) // new exclusive root
                scrubCache = cacheRoot.absolutePath
                receipt.setInputs(modelHash, imageHash)
                receipt.checkpoint("inputs-ready", null, force = true)

                // One real client for all three cycles. Its real Context constructor defaults to
                // LiteRtLmProcessCoordinator.shared (the single shared coordinator), so the three
                // sequential claims/retirements arbitrate on one coordinator. Each cycle uses a
                // distinct UUID nonce. (Matches the existing production instrumentation test.)
                val client = LiteRtLmProcessClient(appContext)
                val pids = ArrayList<Int>(3)

                // ---- incarnation 1: normal image generation -> nonempty SUCCEEDED ----
                receipt.setCycle(1, "cycle1-normal", 0) // no service pid yet; reset stale pid
                receipt.checkpoint("cycle1-connect", null, force = true)
                val nonce1 = UUID.randomUUID().toString()
                val c1 = connectClient(client, nonce1)
                pendingConnection = c1
                val h1 = reserveAndValidate(c1, nonce1, expectedProcessName, mainPid)
                receipt.setCycle(1, "cycle1-normal", h1.pid)
                receipt.checkpoint("cycle1-reserve", null, force = true) // pid exposed for W8 observer
                val s1 = runNormalGeneration(
                    c1, "cycle1-normal", receipt, modelPath, imagePath, cacheRoot,
                    PROMPT_NORMAL, MAX_OUTPUT_TOKENS_NORMAL, backendPolicy,
                )
                val (retire1, death1) = retireAndAwaitExactDeath(c1, receipt, "cycle1")
                pendingConnection = null
                receipt.addCycle(cycleRecord(1, "cycle1-normal", h1, s1, death1, retire1))
                pids.add(h1.pid)

                // ---- incarnation 2: longer generation -> active CANCEL -> CANCELED -> RETIRE ----
                receipt.setCycle(2, "cycle2-cancel", 0) // no service pid yet; reset stale pid
                receipt.checkpoint("cycle2-connect", null, force = true)
                val nonce2 = UUID.randomUUID().toString()
                val c2 = connectClient(client, nonce2)
                pendingConnection = c2
                val h2 = reserveAndValidate(c2, nonce2, expectedProcessName, mainPid)
                receipt.setCycle(2, "cycle2-cancel", h2.pid)
                receipt.checkpoint("cycle2-reserve", null, force = true)
                val s2 = runCancelGeneration(
                    c2, "cycle2-cancel", receipt, modelPath, imagePath, cacheRoot,
                    PROMPT_LONG, MAX_OUTPUT_TOKENS_LONG, backendPolicy,
                )
                val (retire2, death2) = retireAndAwaitExactDeath(c2, receipt, "cycle2")
                pendingConnection = null
                receipt.addCycle(cycleRecord(2, "cycle2-cancel", h2, s2, death2, retire2))
                pids.add(h2.pid)

                // ---- incarnation 3: reopen -> normal generation -> nonempty SUCCEEDED ----
                receipt.setCycle(3, "cycle3-reopen", 0) // no service pid yet; reset stale pid
                receipt.checkpoint("cycle3-connect", null, force = true)
                val nonce3 = UUID.randomUUID().toString()
                val c3 = connectClient(client, nonce3)
                pendingConnection = c3
                val h3 = reserveAndValidate(c3, nonce3, expectedProcessName, mainPid)
                receipt.setCycle(3, "cycle3-reopen", h3.pid)
                receipt.checkpoint("cycle3-reserve", null, force = true)
                val s3 = runNormalGeneration(
                    c3, "cycle3-reopen", receipt, modelPath, imagePath, cacheRoot,
                    PROMPT_NORMAL, MAX_OUTPUT_TOKENS_NORMAL, backendPolicy,
                )
                val (retire3, death3) = retireAndAwaitExactDeath(c3, receipt, "cycle3")
                pendingConnection = null
                receipt.addCycle(cycleRecord(3, "cycle3-reopen", h3, s3, death3, retire3))
                pids.add(h3.pid)

                // Three distinct dedicated-process incarnations, none equal to the main PID.
                assertTrue("exactly three distinct service incarnation PIDs", pids.distinct().size == 3)
                pids.forEach { assertTrue("each service PID differs from main PID", it != mainPid) }

                receipt.terminalCompleted()
            } catch (t: Throwable) {
                failure = t
            } finally {
                // Best-effort cleanup of a connection still owned on failure. Never kill a PID,
                // never delete models or preexisting paths. Cleanup errors are collected, not
                // swallowed into a pass.
                val pending = pendingConnection
                if (pending != null) {
                    try {
                        withContext(NonCancellable) {
                            withTimeout(CLEANUP_TIMEOUT_MS) {
                                if (!pending.releaseBeforeReservation()) {
                                    pending.retire()
                                }
                                pending.awaitOwnershipReleased()
                            }
                        }
                    } catch (cleanup: Throwable) {
                        cleanupErrors.add(cleanup)
                    }
                }
            }

            // Terminal receipt (completed or failed) and rethrow to JUnit. Cleanup and
            // persistence errors surface as failures; they are never swallowed into a pass.
            val primary: Throwable? = failure
            val combined: Throwable? = if (primary != null) {
                val p: Throwable = primary
                cleanupErrors.forEach { p.addSuppressed(it) }
                p
            } else {
                cleanupErrors.firstOrNull()
            }
            if (combined != null) {
                val failing: Throwable = combined
                val terminalReceipt = receiptRef
                if (terminalReceipt != null) {
                    try {
                        terminalReceipt.terminalFailed(
                            failing, scrubModel, scrubImage, scrubCache, scrubReceipt
                        )
                    } catch (persist: Throwable) {
                        failing.addSuppressed(persist)
                    }
                }
                // boundedWait already maps timeouts to AssertionError. Wrap any remaining
                // CancellationException too, so runBlocking can never swallow it into a pass.
                val toThrow: Throwable = if (failing is CancellationException) {
                    AssertionError("test cancelled: ${failing.javaClass.simpleName}").apply {
                        initCause(failing)
                    }
                } else {
                    failing
                }
                throw toThrow
            }
            // combined == null: terminalCompleted() already fsync'ed the completed receipt inside
            // the try; a persistence error there would have been caught as `failure` above.
        }
    }

    // ---- real-chain helpers (private, no new APIs/dependencies) ----

    private suspend fun <T> boundedWait(timeoutMs: Long, what: String, block: suspend () -> T): T =
        try {
            withTimeout(timeoutMs) { block() }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
        }

    private suspend fun connectClient(
        client: LiteRtLmProcessClient,
        nonce: String,
    ): LiteRtLmProcessConnection =
        boundedWait(CONNECT_TIMEOUT_MS, "connect/bind") { client.connect(nonce) }

    private suspend fun reserveAndValidate(
        connection: LiteRtLmProcessConnection,
        nonce: String,
        expectedProcessName: String,
        mainPid: Int,
    ): LiteRtLmProcessHello {
        val hello = connection.reserve()
        assertTrue("HELLO must be accepted", hello.accepted)
        assertTrue("HELLO must echo the exact nonce", hello.nonce == nonce)
        assertTrue("HELLO service PID must be positive", hello.pid > 0)
        assertTrue("service PID must differ from main PID", hello.pid != mainPid)
        assertTrue("exact dedicated process name", hello.processName == expectedProcessName)
        return hello
    }

    private suspend fun startRequest(
        connection: LiteRtLmProcessConnection,
        label: String,
        modelPath: String,
        imagePath: String,
        cacheRoot: File,
        prompt: String,
        maxOutputTokens: Int,
        backendPolicy: LiteRtLmBackendPolicy,
    ) {
        val started = connection.start(
            LiteRtLmProcessStart(
                modelAbsolutePath = modelPath,
                imageAbsolutePath = imagePath,
                cacheAbsolutePath = cacheRoot.absolutePath,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                backendPolicy = backendPolicy,
            )
        )
        assertTrue("$label: START must be accepted", started)
    }

    private suspend fun runNormalGeneration(
        connection: LiteRtLmProcessConnection,
        label: String,
        receipt: Receipt,
        modelPath: String,
        imagePath: String,
        cacheRoot: File,
        prompt: String,
        maxOutputTokens: Int,
        backendPolicy: LiteRtLmBackendPolicy,
    ): LiteRtLmProcessStatus {
        startRequest(connection, label, modelPath, imagePath, cacheRoot, prompt, maxOutputTokens, backendPolicy)
        receipt.checkpoint("$label-started", null, force = true)
        var terminal: LiteRtLmProcessStatus? = null
        boundedWait(GENERATION_TIMEOUT_MS, "$label generation/init") {
            while (true) {
                val status = connection.status()
                receipt.checkpoint("$label-status", status, force = false)
                if (status.phase == TERMINAL_PHASE) {
                    terminal = status
                    break
                }
                delay(POLL_INTERVAL_MS) // cooperative poll, never a busy loop on the test thread
            }
        }
        val s = terminal ?: throw AssertionError("$label: no terminal status observed")
        receipt.checkpoint("$label-terminal", s, force = true)
        assertTrue(
            "$label: SUCCEEDED generation (errorCode=${s.errorCode})",
            s.generation == "SUCCEEDED",
        )
        assertTrue("$label: nativeAttempted", s.nativeAttempted)
        assertTrue("$label: nativeInitialized", s.nativeInitialized)
        assertTrue("$label: nonempty SUCCEEDED output", s.rawChunks.any { it.isNotBlank() })
        return s
    }

    private suspend fun runCancelGeneration(
        connection: LiteRtLmProcessConnection,
        label: String,
        receipt: Receipt,
        modelPath: String,
        imagePath: String,
        cacheRoot: File,
        prompt: String,
        maxOutputTokens: Int,
        backendPolicy: LiteRtLmBackendPolicy,
    ): LiteRtLmProcessStatus {
        startRequest(connection, label, modelPath, imagePath, cacheRoot, prompt, maxOutputTokens, backendPolicy)
        receipt.checkpoint("$label-started", null, force = true)
        var sawRawChunk = false
        var sawRunning = false
        var activeCancelIssued = false
        var terminal: LiteRtLmProcessStatus? = null
        boundedWait(GENERATION_TIMEOUT_MS, "$label longer generation + active cancel") {
            while (true) {
                val status = connection.status()
                receipt.checkpoint("$label-status", status, force = false)
                val isTerminal = status.phase == TERMINAL_PHASE
                if (!isTerminal) {
                    // rawChunks is an append-only prefix: the first emitted chunk stays observable.
                    if (status.rawChunks.isNotEmpty()) sawRawChunk = true
                    if (status.nativeInitialized && status.generation == "RUNNING") sawRunning = true
                }
                if (isTerminal) {
                    if (!activeCancelIssued) {
                        receipt.checkpoint("$label-not-active-cancel", status, force = true)
                        throw AssertionError(
                            "$label: not active cancel: terminal reached before a raw chunk + " +
                                "RUNNING/nativeInitialized allowed an active cancel"
                        )
                    }
                    terminal = status
                    break
                }
                if (!activeCancelIssued && sawRawChunk && sawRunning) {
                    // Still active (not terminal): issue the one deliberate active CANCEL.
                    connection.cancel()
                    activeCancelIssued = true
                    receipt.checkpoint("$label-cancel-issued", status, force = true)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        val s = terminal ?: throw AssertionError("$label: no terminal status after active cancel")
        receipt.checkpoint("$label-terminal", s, force = true)
        assertTrue("$label: CANCELED terminal after active cancel", s.generation == "CANCELED")
        return s
    }

    private suspend fun retireAndAwaitExactDeath(
        connection: LiteRtLmProcessConnection,
        receipt: Receipt,
        label: String,
    ): Pair<Boolean, LiteRtLmProcessDeathOutcome> {
        receipt.checkpoint("$label-retire", null, force = true)
        // RETIRE reply is NOT exact death; the reply/death race may even drop the reply. Only an
        // ordinary transport exception is treated as evidence (false); cancellation is rethrown and
        // any Error surfaces to the failed receipt. Ownership still resolves solely via the exact
        // registered binder death below, so a dropped reply can never become a false pass.
        val retireReplyOk = try {
            connection.retire()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            false
        }
        val released = boundedWait(EXACT_DEATH_TIMEOUT_MS, "$label exact registered binder death") {
            connection.awaitOwnershipReleased()
        }
        assertNotNull(
            "$label: exact death outcome must be nonnull; RETIRE reply / onServiceDisconnected / " +
                "isBinderAlive / timeout / PID disappearance / terminal status are NOT death",
            released,
        )
        val outcome = released!!
        receipt.checkpoint("$label-exact-death", null, force = true)
        return Pair(retireReplyOk, outcome)
    }

    private fun cycleRecord(
        index: Int,
        label: String,
        hello: LiteRtLmProcessHello,
        status: LiteRtLmProcessStatus,
        death: LiteRtLmProcessDeathOutcome,
        retireReplyOk: Boolean,
    ): JSONObject = JSONObject().apply {
        put("cycleIndex", index)
        put("cycleLabel", label)
        put("hello", JSONObject().apply {
            put("accepted", hello.accepted)
            put("pid", hello.pid)
            put("processName", hello.processName)
        })
        put("status", JSONObject().apply {
            put("phase", status.phase)
            put("generation", status.generation)
            put("nativeAttempted", status.nativeAttempted)
            put("nativeInitialized", status.nativeInitialized)
            put("errorCode", status.errorCode ?: JSONObject.NULL)
            put("callbackMode", "UNPROVEN")
            put("rawChunks", chunksArray(status.rawChunks)) // unchanged, whitespace preserved
        })
        put("exactDeathOutcome", JSONObject().apply {
            // Only the actual LiteRtLmProcessDeathOutcome.terminal; no invented binder-death fields.
            put("terminal", death.terminal.name)
        })
        put("retireReplyOk", retireReplyOk) // RETIRE-reply evidence, deliberately NOT death
    }

    // ---- input validation / hashing / exclusive roots (all fail BEFORE bind) ----

    /**
     * REQUIRED instrumentation argument backendPolicy, validated strictly BEFORE any bind.
     * Only [LiteRtLmBackendPolicy.CPU_ONLY] or [LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION] are
     * accepted. There is NO default and NO auto-fallback: any other value (including a missing
     * argument, blank, or an unknown enum name) fails fast. The production support gate decides
     * usability; this instrumentation only forwards the chosen policy.
     */
    private fun requireBackendPolicy(arguments: Bundle): LiteRtLmBackendPolicy {
        val value = arguments.getString("backendPolicy")
        if (value == null || value.isBlank()) {
            throw AssertionError(
                "backendPolicy missing (required: CPU_ONLY or CPU_TEXT_GPU_VISION)"
            )
        }
        val policy = try {
            LiteRtLmBackendPolicy.valueOf(value)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (policy != LiteRtLmBackendPolicy.CPU_ONLY &&
            policy != LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION
        ) {
            throw AssertionError(
                "backendPolicy unsupported: '$value' (only CPU_ONLY or CPU_TEXT_GPU_VISION allowed)"
            )
        }
        return policy
    }

    private fun requireRunId(arguments: Bundle): String {
        val value = arguments.getString("runId")
        if (value == null || !RUN_ID_PATTERN.matches(value)) {
            throw AssertionError("runId missing or malformed (expected safe 1-80 chars)")
        }
        return value
    }

    private fun requireFileArgument(arguments: Bundle, key: String): String {
        val value = arguments.getString(key)
        if (value == null || value.isBlank() || !value.startsWith("/") || '\u0000' in value) {
            throw AssertionError("$key missing or malformed (expected an absolute path)")
        }
        val file = File(value)
        if (!file.isFile || file.length() <= 0L) {
            throw AssertionError("$key is not a regular nonempty file (fail before bind)")
        }
        return value
    }

    private fun requireSha256Argument(arguments: Bundle, key: String): String {
        val value = arguments.getString(key)
        if (value == null || !SHA256_PATTERN.matches(value)) {
            throw AssertionError("$key missing or malformed (expected 64 hex chars)")
        }
        return value
    }

    private fun sha256HexAndLength(file: File): InputHash {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    digest.update(buffer, 0, read)
                    total += read
                }
            }
        }
        return InputHash(digest.digest().toHex(), total)
    }

    private fun createReceiptExclusive(targetContext: Context, runId: String): File {
        val dir = File(targetContext.noBackupFilesDir, RECEIPT_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw AssertionError("cannot create receipt directory")
        }
        val file = File(dir, "$runId.json")
        if (!file.createNewFile()) {
            throw AssertionError("receipt file already exists; exclusive runId required (never overwrite/delete preexisting)")
        }
        return file
    }

    private fun createCacheRootExclusive(context: Context, runId: String): File {
        val root = File(context.cacheDir, "$CACHE_ROOT_PREFIX$runId")
        if (!root.mkdir()) {
            throw AssertionError("cache root not exclusively created (exists or parent missing); never delete preexisting")
        }
        return root
    }

    /** Persistent, fsync-backed receipt. Single writer (one test coroutine); no lock held across Binder/suspend. */
    private class Receipt(
        private val file: File,
        runId: String,
        mainPid: Int,
        private val expectedProcessName: String,
    ) {
        private val root = JSONObject()
        private val cycles = JSONArray()
        private val current = JSONObject()
        private var lastWriteMs = 0L
        private var lastSignature: String? = null
        private var lastStage = "preflight"
        var currentCycleIndex = 0
        var currentCycleLabel = ""
        var currentServicePid = 0

        init {
            root.put("schemaVersion", 1)
            root.put("runId", runId)
            root.put("state", "running")
            root.put("mainPid", mainPid)
            root.put("expectedProcessName", expectedProcessName)
            root.put("callbackMode", "UNPROVEN")
            root.put("inputs", JSONObject())
            root.put("cycles", cycles)
            root.put("current", current)
            // No I/O here: the first checkpoint/persist runs inside the test's try, so a
            // persistence error is caught, written as a failed terminal receipt, and rethrown.
        }

        fun setInputs(model: InputHash, image: InputHash) {
            root.put("inputs", JSONObject().apply {
                put("model", inputObj(model))
                put("image", inputObj(image))
            })
        }

        fun setCycle(index: Int, label: String, servicePid: Int) {
            currentCycleIndex = index
            currentCycleLabel = label
            currentServicePid = servicePid
        }

        /** Record the strictly-validated required backendPolicy into the root receipt. */
        fun setBackendPolicy(policy: String) {
            root.put("backendPolicy", policy)
        }

        fun addCycle(record: JSONObject) {
            cycles.put(record)
            persist(SystemClock.elapsedRealtime())
        }

        /** Intermediate checkpoint, rebuilt from the ACTUAL connection.status() on each call. */
        fun checkpoint(phase: String, status: LiteRtLmProcessStatus?, force: Boolean) {
            root.put("stage", phase)
            lastStage = phase
            current.put("cycleIndex", currentCycleIndex)
            current.put("cycleLabel", currentCycleLabel)
            current.put("servicePid", currentServicePid)
            current.put("expectedProcessName", expectedProcessName)
            current.put("phase", status?.phase ?: "")
            current.put("generation", status?.generation ?: "")
            current.put("nativeAttempted", status?.nativeAttempted ?: false)
            current.put("nativeInitialized", status?.nativeInitialized ?: false)
            // Persist the actual bounded status.errorCode (or JSONObject.NULL) on EVERY checkpoint,
            // including the terminal failed current, so a hidden InvalidModel is not lost by the
            // failure report. Production DTO/codec are not altered; this only records what the
            // decoded status already exposes.
            current.put("errorCode", status?.errorCode ?: JSONObject.NULL)
            // Production pins callbackMode to the literal "UNPROVEN" and the codec rejects any
            // other value; the decoded status does not expose the field. Report only that real
            // value and keep raw chunks verbatim -- never assume DELTA/CUMULATIVE or join them.
            current.put("callbackMode", "UNPROVEN")
            current.put("rawChunks", chunksArray(status?.rawChunks))
            val nowMs = SystemClock.elapsedRealtime()
            val signature = statusSignature(status)
            if (!force) {
                // Bounded interval / changed snapshots only: avoid huge rewrites during sampling.
                val elapsed = nowMs - lastWriteMs
                val changed = signature != lastSignature
                val due = (changed && elapsed >= MIN_CHECKPOINT_INTERVAL_MS) ||
                    elapsed >= MAX_CHECKPOINT_INTERVAL_MS
                if (!due) return
            }
            persist(nowMs)
            lastSignature = signature
        }

        fun terminalCompleted() {
            root.put("state", "completed")
            root.put("stage", "completed")
            persist(SystemClock.elapsedRealtime())
        }

        fun terminalFailed(throwable: Throwable, vararg scrubSecrets: String?) {
            root.put("state", "failed")
            root.put("stage", lastStage)
            root.put("failure", JSONObject().apply {
                put("stage", lastStage)
                put("code", throwable.javaClass.simpleName) // bounded failure code
                put("detail", scrub(throwable.message, *scrubSecrets))
            })
            persist(SystemClock.elapsedRealtime())
        }

        private fun persist(nowMs: Long) {
            val bytes = root.toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(file, false).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync() // fsync the running/terminal receipt
            }
            lastWriteMs = nowMs
        }

        private fun statusSignature(status: LiteRtLmProcessStatus?): String {
            if (status == null) return "no-status"
            val chunks = status.rawChunks
            return listOf(
                status.phase,
                status.generation,
                status.nativeAttempted.toString(),
                status.nativeInitialized.toString(),
                chunks.size.toString(),
                chunks.lastOrNull()?.take(64) ?: "",
                status.errorCode ?: "",
            ).joinToString("|")
        }

        private fun inputObj(hash: InputHash): JSONObject = JSONObject().apply {
            put("sha256", hash.sha)
            put("bytes", hash.bytes) // Long, not float (org.json 0.0 -> 0 pitfall)
        }
    }

    private companion object {
        const val PROCESS_SUFFIX = ":odai_litertlm"
        const val TERMINAL_PHASE = "TERMINAL"
        const val RECEIPT_DIR = "w5-real-service-native"
        const val CACHE_ROOT_PREFIX = "w5-native-"
        const val PROMPT_NORMAL = "In one short sentence, name the dominant color you see in this image."
        const val PROMPT_LONG =
            "Describe this image in as much detail as you can. Discuss the dominant colors, any shapes or boundaries, lighting, and every object or pattern you can identify. Write several long, detailed sentences."
        const val MAX_OUTPUT_TOKENS_NORMAL = 256
        const val MAX_OUTPUT_TOKENS_LONG = 512
        const val POLL_INTERVAL_MS = 150L
        const val CONNECT_TIMEOUT_MS = 60_000L
        const val GENERATION_TIMEOUT_MS = 600_000L // 10 min init + generation
        const val EXACT_DEATH_TIMEOUT_MS = 60_000L // 60 s exact death
        const val CLEANUP_TIMEOUT_MS = 15_000L
        const val HASH_BUFFER_BYTES = 64 * 1024
        val RUN_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
        val SHA256_PATTERN = Regex("[A-Fa-f0-9]{64}")
    }
}

// ---- file-scoped helpers shared by the class and its nested Receipt ----

/** Exact raw chunks; whitespace is never trimmed or normalized. */
private fun chunksArray(chunks: List<String>?): JSONArray = JSONArray().apply {
    chunks?.forEach { put(it) }
}

/** Bounded, path-scrubbed failure detail: no absolute model/image/cache paths leak. */
private fun scrub(message: String?, vararg secrets: String?): String {
    var text = message ?: ""
    for (secret in secrets) {
        if (!secret.isNullOrEmpty()) text = text.replace(secret, "[redacted]")
    }
    return if (text.length > 200) text.take(200) else text
}

/** Explicit lowercase hex; never relies on locale-sensitive %02x formatting of a signed Byte. */
private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        out.append(digits[v ushr 4])
        out.append(digits[v and 0x0f])
    }
    return out.toString()
}

private data class InputHash(val sha: String, val bytes: Long)

// Bounded checkpoint cadence during STATUS sampling (file scope: referenced by the nested Receipt).
private const val MIN_CHECKPOINT_INTERVAL_MS = 1_500L
private const val MAX_CHECKPOINT_INTERVAL_MS = 5_000L