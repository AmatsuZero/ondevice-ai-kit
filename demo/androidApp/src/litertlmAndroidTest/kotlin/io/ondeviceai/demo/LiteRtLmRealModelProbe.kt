package io.ondeviceai.demo

import android.os.Build
import io.ondeviceai.binding.*
import io.ondeviceai.litertlm.LiteRtLmBackendPolicy
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.*
import io.ondeviceai.multimodal.MultimodalGenerationClient
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.json.JSONObject

/** Explicit shell smoke through the real Demo backend/bridge; never launches an Activity. */
object LiteRtLmRealModelProbe {
    private const val REVISION = "c03b6f60b8da6c5400b6838a2cf26420f80c0a01"
    private const val MODEL_SHA256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
    private const val INPUT_SHA256 = "991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa"
    private const val PROMPT = "What is the color of this image? Respond with a single color word."

    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 1)
        val root = File(arguments[0])
        require(root.isDirectory)
        val report = JSONObject().put("runId", UUID.randomUUID().toString())
            .put("status", "running").put("activityLaunched", false)
            .put("uiAcceptance", "unverified").put("device", Build.MODEL)
            .put("apiLevel", Build.VERSION.SDK_INT)
        fun save(stage: String) {
            report.put("stage", stage)
            File(root, "generation-report.json").writeText(report.toString(2) + "\n")
        }
        try {
            save("model-preflight")
            val info = JSONObject(File(root, "model-info.json").readText())
            require(info.getBoolean("cpuTextGpuVisionCompatible") && info.getString("modelSha256") == MODEL_SHA256)
            val policy = liteRtLmBackendPolicy(JSONObject(File(root, "bundle/demo-model.json").readText()))
            require(policy == LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION)
            report.put("backendPolicy", policy.name)
            val manifest = ModelManifest.parse(File(root, "bundle/manifest.json").readText())
            require(manifest.upstreamRevision == REVISION)
            require(manifest.files.single { it.path == "model.litertlm" }.sha256 == MODEL_SHA256)
            val input = File(root, "input.png")
            require(input.isFile && input.length() in 1..(16L * 1024 * 1024))
            val digest = MessageDigest.getInstance("SHA-256")
            input.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 16L * 1024 * 1024)
                    digest.update(buffer, 0, count)
                }
            }
            val inputDigest = digest.digest().joinToString("") { "%02x".format(it) }
            require(inputDigest == INPUT_SHA256)
            report.put("inputSha256", inputDigest).put("prompt", PROMPT).put("maxOutputTokens", 32)
                .put("modelSha256", MODEL_SHA256)
            val architecture = Build.SUPPORTED_ABIS.first { it == "arm64-v8a" || it == "x86_64" }
            val store = ModelStore(File(root, "model-store"), ModelCompatibility(
                "litertlm.android", "0.18.1", Build.VERSION.SDK_INT.toString(), architecture))
            val cache = File(root, "generation-cache")
            val inputsDirectory = File(root, "inputs-${UUID.randomUUID()}")
            val inputs = MultimodalInputStore(inputsDirectory)
            val reference = inputs.importImage(input)
            val backend = LiteRtLmDemoBackend(File(root, "bundle"), store, cache,
                LiteRtLmRuntimeEnvironment.current())
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                runBlocking(dispatcher) {
                    val hostScope = CoroutineScope(SupervisorJob() + dispatcher)
                    val bridge = AndroidMultimodalBridge(
                        MultimodalGenerationClient(backend.backendId, listOf(backend)), inputs, hostScope)
                    try {
                        save("install-and-availability")
                        check(backend.availability().status.name == "AVAILABLE")
                        save("image-generation")
                        val started = android.os.SystemClock.elapsedRealtime()
                        val terminal = CompletableDeferred<MultimodalBridgeOutput>()
                        var updates = 0
                        bridge.generateMultimodal(MultimodalBridgeRequest(reference, PROMPT, 32),
                            MultimodalBridgeSelection.Explicit("litertlm.android"),
                            object : MultimodalBridgeGenerateCallback {
                                override fun onMultimodalUpdate(update: MultimodalBridgeUpdate) { updates++ }
                                override fun onMultimodalResult(output: MultimodalBridgeOutput) { terminal.complete(output) }
                                override fun onMultimodalFailure(failure: MultimodalBridgeFailure) {
                                    report.put("bridgeFailure", failure.code.name)
                                    terminal.completeExceptionally(IllegalStateException("bridge failure"))
                                }
                                override fun onMultimodalCancelled() {
                                    terminal.completeExceptionally(CancellationException("probe cancelled"))
                                }
                            })
                        val result = terminal.await()
                        check(result.backendId == "litertlm.android" && result.text.isNotBlank())
                        check(result.model.modelRevision == REVISION)
                        check(result.model.runtimeRevision == "litertlm-android:0.18.1")
                        report.put("outputText", result.text).put("backend", result.backendId)
                            .put("modelId", result.model.modelId).put("modelRevision", result.model.modelRevision)
                            .put("runtimeRevision", result.model.runtimeRevision).put("updates", updates)
                            .put("generationMs", android.os.SystemClock.elapsedRealtime() - started)
                    } finally {
                        withContext(NonCancellable) {
                            save("close-and-drain")
                            val closed = CompletableDeferred<Unit>()
                            bridge.closeMultimodal(object : MultimodalBridgeCloseCallback {
                                override fun onMultimodalClosed() { closed.complete(Unit) }
                                override fun onMultimodalFailure(failure: MultimodalBridgeFailure) {
                                    report.put("closeFailure", failure.code.name)
                                    closed.completeExceptionally(IllegalStateException("close failure"))
                                }
                            })
                            try { closed.await(); report.put("closed", true) }
                            finally { hostScope.cancel(); inputs.remove(reference); inputs.close() }
                        }
                    }
                    // This isolated probe owns its installed copy; removal proves the lease was released.
                    store.remove(manifest.ref)
                    check(store.status(manifest.ref) == ModelStatus.ABSENT)
                    check(!inputsDirectory.exists())
                    check(!cache.exists() || cache.walkTopDown().none { it.isFile })
                    report.put("modelLeaseReleased", true).put("privateInputsRemoved", true)
                        .put("generationCacheFilesRemoved", true)
                }
            }
            report.put("status", "passed")
            save("complete")
        } catch (failure: Throwable) {
            report.put("status", "failed").put("errorType", failure.javaClass.simpleName)
            save(report.optString("stage", "preflight"))
            throw failure
        }
    }
}
