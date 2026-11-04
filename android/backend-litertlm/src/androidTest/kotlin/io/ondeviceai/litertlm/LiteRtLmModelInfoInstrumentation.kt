package io.ondeviceai.litertlm

import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.BackendType
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ModelInfo
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test

/** Explicit authorized-model preflight; no Activity, engine, camera or microphone. */
class LiteRtLmModelInfoInstrumentation {
    @Test
    fun inspectAuthorizedModelBeforeEngineCreation() {
        assumeTrue("explicit authorized-model opt-in required",
            InstrumentationRegistry.getArguments().getString("authorizedModelProbe") == "true")
        val policy = InstrumentationRegistry.getArguments().getString("backendPolicy")
            ?.let(LiteRtLmBackendPolicy::valueOf) ?: LiteRtLmBackendPolicy.CPU_ONLY
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("fixed runtime selected policy $policy rejected; see authorized-model-info.json",
            inspect(File(context.filesDir, "authorized-model.litertlm"),
                File(context.filesDir, "authorized-model-info.json"), policy))
    }

    companion object {
        /** Shell entry uses the same Android APK/runtime without cached-App freezing. */
        @JvmStatic
        fun main(arguments: Array<String>) {
            require(arguments.size in 2..3)
            val policy = if (arguments.size == 3) LiteRtLmBackendPolicy.valueOf(arguments[2])
                else LiteRtLmBackendPolicy.CPU_ONLY
            check(inspect(File(arguments[0]), File(arguments[1]), policy))
        }

        private fun inspect(input: File, output: File,
            policy: LiteRtLmBackendPolicy = LiteRtLmBackendPolicy.CPU_ONLY): Boolean {
            val report = JSONObject().put("runtime", "litertlm-android:0.18.1")
                .put("engineCreated", false).put("inputBytes", input.length()).put("backendPolicy", policy.name)
            var compatible = false
            try {
                output.writeText(report.put("stage", "started").toString(2) + "\n")
                report.put("stage", "integrity")
                require(input.isFile && input.length() == 3_655_827_456L)
                val digest = MessageDigest.getInstance("SHA-256")
                input.inputStream().use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                report.put("modelSha256", sha256)
                require(sha256 == "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6")
                report.put("stage", "model-info")
                ModelInfo.from(input.absolutePath).use { info ->
                    val modalities = info.inputModalities()
                    val text = info.supportedBackends(Modality.TEXT)
                    val vision = info.supportedBackends(Modality.VISION)
                    val minimum = info.minRuntimeVersion()
                    val support = LiteRtLmModelSupport(modalities.text, modalities.vision,
                        BackendType.CPU in text, BackendType.CPU in vision, BackendType.GPU in vision,
                        info.maxVisionTokenBudget(), minimum)
                    compatible = support.supports(policy)
                    report.put("text", modalities.text).put("vision", modalities.vision)
                        .put("textBackends", JSONArray(text.map { it.toString() }))
                        .put("visionBackends", JSONArray(vision.map { it.toString() }))
                        .put("maxVisionTokenBudget", info.maxVisionTokenBudget())
                        .put("minimumRuntimeVersion", minimum ?: JSONObject.NULL)
                        .put("cpuVisionCompatible", support.supports(LiteRtLmBackendPolicy.CPU_ONLY))
                        .put("cpuTextGpuVisionCompatible", support.supports(LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION))
                        .put("selectedPolicyCompatible", compatible).put("status", "inspected")
                }
            } catch (failure: Throwable) {
                report.put("status", "failed").put("errorType", failure.javaClass.simpleName)
                throw failure
            } finally {
                output.writeText(report.toString(2) + "\n")
            }
            return compatible
        }
    }
}
