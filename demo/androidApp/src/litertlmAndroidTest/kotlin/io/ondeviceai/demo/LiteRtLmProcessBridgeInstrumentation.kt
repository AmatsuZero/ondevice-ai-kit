package io.ondeviceai.demo

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.Process
import io.ondeviceai.binding.*
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.litertlm.LiteRtLm
import io.ondeviceai.litertlm.LiteRtLmBackendPolicy
import io.ondeviceai.litertlm.LiteRtLmModel
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelManifest
import io.ondeviceai.modelstore.ModelStatus
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.multimodal.MultimodalGenerationBackend
import io.ondeviceai.multimodal.MultimodalGenerationClient
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Custom instrumentation test runner (no Activity, Binder, JNI, reflection or protocol access).
 * Exercises the real LiteRT-LM process backend through the production DemoBackend/bridge/input
 * store on a single Main.immediate thread, mirroring LiteRtLmRealModelProbe but using the explicit
 * process-backend builder. Three cycles: normal success, long-cancel, and same-store reuse.
 */
class LiteRtLmProcessBridgeInstrumentation : Instrumentation() {
    private lateinit var args: Bundle
    private val mainPid = Process.myPid()
    private val genTimeoutMs = 600_000L
    private val closeTimeoutMs = 60_000L
    private val revision = "c03b6f60b8da6c5400b6838a2cf26420f80c0a01"
    private val modelSha = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
    private val inputSha = "991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa"
    private val runtime = "litertlm-android:0.18.1"
    private val backendId = "litertlm.android"
    private val expectedProcessName get() = targetContext.packageName + ":odai_litertlm"
    private val normalPrompt = "In one short sentence, name the dominant color you see in this image."
    private val longPrompt = "Describe this image in as much detail as you can. Discuss the dominant colors, any shapes or boundaries, lighting, and every object or pattern you can identify. Write several long, detailed sentences."

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        args = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        val (code, result) = try {
            runBlocking(Dispatchers.Main.immediate) { orchestrate() }
        } catch (e: Throwable) {
            Pair(Activity.RESULT_CANCELED, Bundle().apply {
                putString("status", "failed")
                putString("errorType", e.javaClass.simpleName)
                putString("stage", lastStage)
            })
        }
        finish(code, result)
    }

    private var lastStage = "init"
    private lateinit var receipt: JSONObject
    private lateinit var runDir: File
    private lateinit var receiptFile: File
    private var updateTotal = 0
    private var resultTotal = 0
    private var failureTotal = 0
    private var cancelledTotal = 0
    // All generation observers retained until the bridge close barrier so late/duplicate
    // callbacks during close are rechecked, not silently lost.
    private val observers = mutableListOf<GenerateObserver>()

    private fun save() {
        receiptFile.outputStream().use { os ->
            os.write(receipt.toString(2).toByteArray(Charsets.UTF_8)); os.flush(); os.fd.sync()
        }
    }

    private fun countsJson() = JSONObject()
        .put("result", resultTotal).put("failure", failureTotal).put("cancelled", cancelledTotal)

    private fun mark(stage: String, state: String = "running") {
        lastStage = stage
        receipt.put("stage", stage).put("state", state)
            .put("terminalCounts", countsJson()).put("updateCount", updateTotal)
        save()
    }

    private suspend fun orchestrate(): Pair<Int, Bundle> {
        updateTotal = 0; resultTotal = 0; failureTotal = 0; cancelledTotal = 0
        val root = File(args.getString("rootPath") ?: error("rootPath required")).canonicalFile
        val filesDir = targetContext.filesDir.canonicalFile
        require(root.startsWith(filesDir)) { "rootPath must be inside targetContext.filesDir" }
        val runId = args.getString("runId") ?: error("runId required")
        require(runId.matches(Regex("^[A-Za-z0-9_-]{1,80}$"))) { "invalid runId" }
        runDir = File(root, "run-$runId")
        require(!runDir.exists()) { "run dir already exists" }
        require(runDir.mkdirs() && runDir.isDirectory) { "cannot create run dir" }
        receiptFile = File(runDir, "receipt.json")
        // Initial exclusive receipt init: own the receipt file before any other step so a
        // failed preflight can never leave a dangling "running" receipt unwritten.
        receipt = JSONObject().put("state", "running").put("stage", "init").put("cycle", 0)
            .put("mainPid", mainPid).put("expectedProcessName", expectedProcessName)
            .put("runId", runId)
        save()

        var resultCode = Activity.RESULT_CANCELED
        var primaryFailure: Throwable? = null
        // Allocated resources are declared here so the cleanup block can always reach them.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var bridge1: AndroidMultimodalBridge? = null
        var inputs1: MultimodalInputStore? = null
        var inputsDir1: File? = null
        var bridge2: AndroidMultimodalBridge? = null
        var inputs2: MultimodalInputStore? = null
        var inputsDir2: File? = null

        // Everything after the exclusive receipt init is inside the top-level error recording.
        try {
            mark("preflight")
            val bundle = File(root, "bundle")
            val manifestFile = File(bundle, "manifest.json")
            val descriptorFile = File(bundle, "demo-model.json")
            val cardFile = File(bundle, "files/model-card.md")
            val modelFile = File(bundle, "files/model.litertlm")
            val inputFile = File(root, "input.png")
            for ((f, n) in listOf(manifestFile to "manifest", descriptorFile to "descriptor",
                cardFile to "card", modelFile to "model", inputFile to "input")) {
                require(f.isFile) { "missing $n file" }
                require(f.canonicalFile.startsWith(root)) { "$n escapes root" }
            }

            val manifest = ModelManifest.parse(manifestFile.readText())
            require(manifest.upstreamRevision == revision) { "revision mismatch" }
            val modelEntry = manifest.files.single { it.path == "model.litertlm" }
            require(modelEntry.sha256 == modelSha) { "model sha mismatch" }
            require(modelEntry.bytes == 3655827456L) { "model bytes mismatch" }
            require(manifest.files.any { it.path == "model-card.md" }) { "manifest missing model-card.md" }

            val descriptor = JSONObject(descriptorFile.readText())
            require(descriptor.getInt("schemaVersion") == 1) { "descriptor schemaVersion != 1" }
            val modelPath = descriptor.getString("modelPath")
            require(modelPath == "model.litertlm" && !modelPath.contains("/") && !modelPath.contains("..")) { "modelPath invalid" }
            val metaPaths = descriptor.getJSONArray("metadataPaths")
            val metaSet = (0 until metaPaths.length()).map { metaPaths.getString(it) }
            require(metaSet.contains("model-card.md")) { "metadataPaths missing model-card.md" }
            require(metaSet.all { !it.contains("/") && !it.contains("..") }) { "metadataPaths escape" }
            require(liteRtLmBackendPolicy(descriptor) == LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION) { "policy mismatch" }

            // 564-byte input only; the heavy model hash is owned by ModelStore on install.
            require(inputFile.length() == 564L) { "input size mismatch" }
            require(sha256(inputFile) == inputSha) { "input sha mismatch" }
            mark("manifest-checked")

            val architecture = Build.SUPPORTED_ABIS.first { it == "arm64-v8a" || it == "x86_64" }
            val store = ModelStore(File(runDir, "store"),
                ModelCompatibility(backendId, "0.18.1", Build.VERSION.SDK_INT.toString(), architecture))
            val cache = File(runDir, "cache")
            val env = LiteRtLmRuntimeEnvironment.current()
            val ctx = targetContext
            val create: (ModelStore, LiteRtLmModel, File, LiteRtLmRuntimeEnvironment) -> MultimodalGenerationBackend =
                { s, m, c, e -> LiteRtLm.createProcessBackend(ctx, s, m, c, e) }

            // Cycle 1: normal generation, exact identity checks.
            inputsDir1 = File(runDir, "inputs-1")
            inputs1 = MultimodalInputStore(inputsDir1!!)
            val ref1 = inputs1!!.importImage(inputFile)
            val backend1 = LiteRtLmDemoBackend(bundle, store, cache, env, create)
            val client1 = MultimodalGenerationClient(backend1.backendId, listOf(backend1))
            bridge1 = AndroidMultimodalBridge(client1, inputs1!!, scope)
            mark("before-install")
            require(backend1.availability().status == CapabilityStatus.AVAILABLE) { "backend unavailable" }
            mark("after-install")

            receipt.put("cycle", 1); mark("cycle-1-request")
            val out1 = generate(bridge1!!, ref1, normalPrompt, 256, "cycle-1")
            require(out1.text.isNotBlank()) { "blank output" }
            require(out1.backendId == backendId) { "backend id mismatch" }
            require(out1.model.modelRevision == revision) { "revision mismatch" }
            require(out1.model.runtimeRevision == runtime) { "runtime mismatch" }
            mark("cycle-1-done")

            // Cycle 2: long generation; queue a cancel on Main AFTER a real nonblank update, and
            // invoke it exactly once. A valid update during the gap before the queued cancel runs
            // is NOT a breach; only updates after the cancel actually executed are sticky failures.
            receipt.put("cycle", 2); mark("cycle-2-request")
            val obs2 = GenerateObserver()
            observers += obs2
            var cancelQueued = false
            var cancelInvoked = false
            var firstNonblankUpdate = false
            var task2: BridgeTask? = null
            obs2.onUpdate = { u ->
                if (cancelInvoked) {
                    // Update after the cancel actually executed: sticky test failure (breach).
                    obs2.noteLateUpdate()
                } else if (!cancelQueued && u.text.isNotBlank()) {
                    // Queue the cancel exactly once, only after a real nonblank update.
                    cancelQueued = true
                    firstNonblankUpdate = true
                    // Schedule on the (non-immediate) Main dispatcher so it runs only after
                    // generateMultimodal has returned and assigned task2, avoiding any
                    // synchronous-callback lateinit race.
                    scope.launch(Dispatchers.Main) {
                        try {
                            if (task2 != null) {
                                cancelInvoked = true
                                task2!!.cancel()
                            }
                        } catch (e: Throwable) {
                            obs2.noteError(RuntimeException("cancel invocation threw", e))
                        }
                    }
                }
            }
            task2 = bridge1!!.generateMultimodal(MultimodalBridgeRequest(ref1, longPrompt, 512),
                MultimodalBridgeSelection.Explicit(backendId), obs2)
            try {
                withTimeout(genTimeoutMs) { obs2.terminal.await() }
                throw AssertionError("cycle-2 produced a success terminal before cancel")
            } catch (_: BridgeCancelled) { /* required cancelled terminal */ }
            resultTotal += obs2.results
            failureTotal += obs2.failures
            cancelledTotal += obs2.cancels
            updateTotal += obs2.updates
            require(obs2.testFailure == null) { "cycle-2 observer validation failure: ${obs2.testFailure?.message}" }
            require(firstNonblankUpdate) { "no real nonblank first update before cancel" }
            require(cancelQueued) { "cancel was not queued" }
            require(cancelInvoked) { "cancel was not invoked" }
            require(obs2.updates >= 1) { "no update before cancel" }
            require(obs2.cancels == 1) { "expected exactly one cancel terminal" }
            require(obs2.results == 0 && obs2.failures == 0) { "unexpected result/failure in cancel cycle" }
            receipt.put("cycle2CancelQueued", cancelQueued).put("cycle2CancelInvoked", cancelInvoked)
                .put("cycle2Updates", obs2.updates).put("terminalCounts", countsJson()).put("updateCount", updateTotal)
            save()
            mark("cycle-2-done")

            // Close bridge1; a follow-up query must report CLOSED (tracked separately, not a
            // generation failure).
            awaitClose(bridge1!!)
            queryClosed(bridge1!!)
            mark("cycle-2-closed")
            // Barrier: recheck every retained observer after bridge1 close + CLOSED so late or
            // duplicate callbacks that arrived during close are caught, not silently lost.
            recheckObservers("cycle-2-closed")

            // Cycle 3: NEW backend/client/bridge/inputstore, SAME installed ModelStore.
            inputsDir2 = File(runDir, "inputs-2")
            inputs2 = MultimodalInputStore(inputsDir2!!)
            val ref2 = inputs2!!.importImage(inputFile)
            val backend2 = LiteRtLmDemoBackend(bundle, store, cache, env, create)
            val client2 = MultimodalGenerationClient(backend2.backendId, listOf(backend2))
            bridge2 = AndroidMultimodalBridge(client2, inputs2!!, scope)
            require(backend2.availability().status == CapabilityStatus.AVAILABLE) { "reuse unavailable" }
            receipt.put("cycle", 3); mark("cycle-3-request")
            val out3 = generate(bridge2!!, ref2, normalPrompt, 256, "cycle-3")
            require(out3.text.isNotBlank()) { "blank output 3" }
            require(out3.model.modelRevision == revision && out3.model.runtimeRevision == runtime) { "identity mismatch 3" }
            awaitClose(bridge2!!)
            // Barrier: recheck every retained observer after bridge2 close.
            recheckObservers("cycle-3-closed")
            mark("cycle-3-done")

            // Tear down the owned store; everything private must be gone. Inputs/bridges are
            // finalized by the cleanup block, and the final "completed" receipt is decided there.
            store.remove(manifest.ref)
            require(store.status(manifest.ref) == ModelStatus.ABSENT) { "store not removed" }
            require(!cache.exists() || cache.walkTopDown().none { it.isFile }) { "cache files remain" }
        } catch (e: Throwable) {
            primaryFailure = e
            receipt.put("state", "failed").put("errorType", e.javaClass.simpleName).put("stage", lastStage)
            save()
        } finally {
            // Single NonCancellable cleanup: capture real errors, never swallow, never delete
            // inputs before their bridge has actually closed.
            withContext(NonCancellable) {
                val cleanupErrors = mutableListOf<Throwable>()
                val bridge1Ok = if (bridge1 != null) {
                    try { awaitClose(bridge1!!); true } catch (ce: Throwable) { cleanupErrors += ce; false }
                } else true
                val bridge2Ok = if (bridge2 != null) {
                    try { awaitClose(bridge2!!); true } catch (ce: Throwable) { cleanupErrors += ce; false }
                } else true
                // Close inputs ONLY after their bridge has been closed successfully.
                if (inputs1 != null && (bridge1 == null || bridge1Ok)) {
                    try { inputs1!!.close() } catch (ie: Throwable) { cleanupErrors += ie }
                }
                if (inputs2 != null && (bridge2 == null || bridge2Ok)) {
                    try { inputs2!!.close() } catch (ie: Throwable) { cleanupErrors += ie }
                }
                if (inputsDir1 != null && inputsDir1!!.exists()) cleanupErrors += RuntimeException("inputs-1 dir remains")
                if (inputsDir2 != null && inputsDir2!!.exists()) cleanupErrors += RuntimeException("inputs-2 dir remains")
                // Scope cleanup only after verified shutdown; no fake release.
                scope.cancel()
                if (primaryFailure == null) {
                    if (cleanupErrors.isEmpty()) {
                        try {
                            // Final barrier: recheck every retained observer just before "completed"
                            // so any late/duplicate callback during shutdown is caught.
                            recheckObservers("final-barrier")
                            // Final totals are computed from the observers (queryClosed failures are
                            // deliberately not counted as generation failures).
                            val fr = observers.sumOf { it.results }
                            val ff = observers.sumOf { it.failures }
                            val fc = observers.sumOf { it.cancels }
                            val fu = observers.sumOf { it.updates }
                            require(fr == 2) { "aggregate results=$fr expected 2" }
                            require(ff == 0) { "aggregate failures=$ff expected 0" }
                            require(fc == 1) { "aggregate cancels=$fc expected 1" }
                            receipt.put("state", "completed").put("stage", "complete")
                                .put("terminalCounts", JSONObject().put("result", fr).put("failure", ff).put("cancelled", fc))
                                .put("updateCount", fu)
                            save()
                            resultCode = Activity.RESULT_OK
                        } catch (e: Throwable) {
                            receipt.put("state", "failed").put("errorType", e.javaClass.simpleName).put("stage", "final-barrier")
                            save()
                            resultCode = Activity.RESULT_CANCELED
                        }
                    } else {
                        val e = cleanupErrors.first()
                        receipt.put("state", "failed").put("errorType", e.javaClass.simpleName).put("stage", "cleanup")
                        save()
                        resultCode = Activity.RESULT_CANCELED
                    }
                } else {
                    // Preserve the failed receipt; attach cleanup errors as suppressed.
                    cleanupErrors.forEach { primaryFailure.addSuppressed(it) }
                    receipt.put("state", "failed").put("errorType", primaryFailure.javaClass.simpleName).put("stage", lastStage)
                    save()
                    resultCode = Activity.RESULT_CANCELED
                }
            }
        }
        return Pair(resultCode, Bundle().apply {
            if (resultCode == Activity.RESULT_OK) {
                putString("status", "passed")
                putString("receipt", "run-$runId/receipt.json")
            } else {
                putString("status", "failed")
                putString("errorType", receipt.optString("errorType"))
                putString("stage", lastStage)
            }
        })
    }

    private suspend fun generate(bridge: AndroidMultimodalBridge, ref: String, prompt: String,
        max: Int, cycleTag: String): MultimodalBridgeOutput {
        val obs = GenerateObserver()
        observers += obs
        obs.onUpdate = { if (obs.updates == 1) mark("$cycleTag-first-update") }
        bridge.generateMultimodal(MultimodalBridgeRequest(ref, prompt, max),
            MultimodalBridgeSelection.Explicit(backendId), obs)
        val out = withTimeout(genTimeoutMs) { obs.terminal.await() }
        resultTotal += obs.results
        failureTotal += obs.failures
        cancelledTotal += obs.cancels
        updateTotal += obs.updates
        require(obs.testFailure == null) { "observer validation failure $cycleTag: ${obs.testFailure?.message}" }
        // Keep the actual output and per-cycle model identity in the receipt.
        receipt.put("lastOutput", JSONObject()
            .put("text", out.text).put("backendId", out.backendId)
            .put("modelId", out.model.modelId).put("modelRevision", out.model.modelRevision)
            .put("runtimeRevision", out.model.runtimeRevision))
            .put("output-$cycleTag", JSONObject()
                .put("text", out.text).put("backendId", out.backendId)
                .put("modelRevision", out.model.modelRevision).put("runtimeRevision", out.model.runtimeRevision)
                .put("updates", obs.updates))
            .put("terminalCounts", countsJson()).put("updateCount", updateTotal)
        save()
        mark("$cycleTag-result")
        return out
    }

    private suspend fun awaitClose(bridge: AndroidMultimodalBridge) {
        val closed = CompletableDeferred<Unit>()
        bridge.closeMultimodal(object : MultimodalBridgeCloseCallback {
            override fun onMultimodalClosed() { closed.complete(Unit) }
            override fun onMultimodalFailure(f: MultimodalBridgeFailure) {
                closed.completeExceptionally(RuntimeException("close ${f.code}"))
            }
        })
        withTimeout(closeTimeoutMs) { closed.await() }
    }

    private suspend fun queryClosed(bridge: AndroidMultimodalBridge) {
        val fail = CompletableDeferred<MultimodalBridgeFailure>()
        bridge.queryMultimodalBackends(object : MultimodalBridgeBackendsCallback {
            override fun onMultimodalBackends(b: List<MultimodalBridgeBackend>) {
                fail.completeExceptionally(RuntimeException("expected CLOSED, got backends"))
            }
            override fun onMultimodalFailure(f: MultimodalBridgeFailure) { fail.complete(f) }
            override fun onMultimodalCancelled() { fail.completeExceptionally(RuntimeException("cancelled")) }
        })
        val f = withTimeout(closeTimeoutMs) { fail.await() }
        require(f.code == MultimodalBridgeFailureCode.CLOSED) { "expected CLOSED, got ${f.code}" }
    }

    /**
     * Rechecks every retained generation observer. Late/duplicate callbacks during close may set
     * [GenerateObserver.testFailure] after the per-generation terminal check, so the barrier must
     * re-assert: no sticky failure, and exactly one terminal (result/failure/cancel) per generation.
     */
    private fun recheckObservers(tag: String) {
        for (o in observers) {
            if (o.testFailure != null) {
                throw RuntimeException("observer validation breach at $tag: ${o.testFailure?.message}")
            }
            val terminals = o.results + o.failures + o.cancels
            require(terminals == 1) { "observer at $tag has $terminals terminals (expected exactly one)" }
        }
    }

    private fun sha256(file: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; d.update(buf, 0, n) }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    private class BridgeCancelled : CancellationException("cancelled")

    /**
     * Robust observer: never throws out of a callback. Sticky [testFailure] records any
     * validation breach (non-strict sequence, non-cumulative text, duplicate/late terminal,
     * or late update) so the caller can reject the run instead of silently passing.
     */
    private inner class GenerateObserver : MultimodalBridgeGenerateCallback {
        val terminal = CompletableDeferred<MultimodalBridgeOutput>()
        var updates = 0
        var lastSequence = -1
        var lastText: String = ""
        var results = 0
        var failures = 0
        var cancels = 0
        var testFailure: Throwable? = null
            private set
        var onUpdate: ((MultimodalBridgeUpdate) -> Unit)? = null

        private fun fail(cause: Throwable) { if (testFailure == null) testFailure = cause }

        /** Records an update that arrived after cancellation was initiated (breach, not silent). */
        fun noteLateUpdate() { fail(RuntimeException("update after cancel initiated")) }

        /** Records any other breach raised by host code (e.g. a cancel-invocation throw). */
        fun noteError(cause: Throwable) { fail(cause) }

        override fun onMultimodalUpdate(u: MultimodalBridgeUpdate) {
            if (terminal.isCompleted) { fail(RuntimeException("late update after terminal (seq=${u.sequence})")); return }
            if (u.sequence <= lastSequence) { fail(RuntimeException("sequence not strictly increasing: ${u.sequence} <= $lastSequence")); return }
            if (!u.text.startsWith(lastText)) { fail(RuntimeException("update text not cumulative at seq=${u.sequence}")); return }
            lastSequence = u.sequence; lastText = u.text; updates++
            // A callback must never throw onto Main (including onUpdate hooks / mark / save);
            // convert any throw into a sticky test failure instead.
            try { onUpdate?.invoke(u) } catch (e: Throwable) { fail(RuntimeException("onUpdate hook threw", e)) }
        }
        override fun onMultimodalResult(o: MultimodalBridgeOutput) {
            if (terminal.isCompleted || results > 0 || failures > 0 || cancels > 0) {
                fail(RuntimeException("duplicate/late result terminal")); return
            }
            results++
            terminal.complete(o)
        }
        override fun onMultimodalFailure(f: MultimodalBridgeFailure) {
            if (terminal.isCompleted || results > 0 || failures > 0 || cancels > 0) {
                fail(RuntimeException("duplicate/late failure terminal (${f.code})")); return
            }
            failures++
            terminal.completeExceptionally(RuntimeException("failure ${f.code}"))
        }
        override fun onMultimodalCancelled() {
            if (terminal.isCompleted || results > 0 || failures > 0 || cancels > 0) {
                fail(RuntimeException("duplicate/late cancel terminal")); return
            }
            cancels++
            terminal.completeExceptionally(BridgeCancelled())
        }
    }
}
