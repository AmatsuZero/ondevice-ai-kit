package io.ondeviceai.litertlm

import android.os.Build
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore

/**
 * Public opt-in availability query; never opens a runtime.
 * Installed assets remain unavailable until the native drain contract is proven.
 */
object LiteRtLm {
    suspend fun availability(
        store: ModelStore,
        model: LiteRtLmModel,
        environment: LiteRtLmRuntimeEnvironment = LiteRtLmRuntimeEnvironment.current(),
    ): CapabilityAvailability {
        val spec = LiteRtLmModelSpec(model.modelPathRelative, model.metadataPathsRelative)
        val supportedAbi = environment.abis.any { it == "arm64-v8a" || it == "x86_64" }
        return LiteRtLmAvailability.forModel(
            store,
            model.ref,
            spec,
            closed = false,
            apiLevel = environment.apiLevel,
            supportedAbi = supportedAbi,
            probeState = LiteRtLmProbeState.UNPROVEN,
        )
    }
}

/** ModelStore identity and safe paths relative to its installed model directory. */
data class LiteRtLmModel(
    val ref: ModelRef,
    val modelPathRelative: String,
    val metadataPathsRelative: Set<String>,
)

/** Device policy inputs. Overriding them cannot bypass the unproven native-runtime gate. */
data class LiteRtLmRuntimeEnvironment(
    val apiLevel: Int,
    val abis: Set<String>,
) {
    companion object {
        fun current() = LiteRtLmRuntimeEnvironment(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toSet())
    }
}
