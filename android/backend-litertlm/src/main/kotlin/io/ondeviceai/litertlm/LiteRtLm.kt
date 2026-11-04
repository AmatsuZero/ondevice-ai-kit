package io.ondeviceai.litertlm

import android.os.Build
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore
import java.io.File

/**
 * Opt-in synchronous image backend. Queries and construction never open a runtime.
 * Native model/device compatibility is checked when generation opens the model.
 */
object LiteRtLm {
    /** Cancellation suppresses output and waits for the blocking send before releasing resources. */
    fun createBackend(
        store: ModelStore,
        model: LiteRtLmModel,
        cacheRoot: File,
        environment: LiteRtLmRuntimeEnvironment = LiteRtLmRuntimeEnvironment.current(),
    ): LiteRtLmBackend = LiteRtLmBackend(
        store, model.ref, LiteRtLmModelSpec(model.modelPathRelative, model.metadataPathsRelative, backendPolicy = model.backendPolicy),
        cacheRoot, LiteRtLmProductionEngineFactory, environment.apiLevel,
        environment.abis.any { it == "arm64-v8a" || it == "x86_64" }, LiteRtLmProbeState.SYNCHRONOUS,
    )

    suspend fun availability(
        store: ModelStore,
        model: LiteRtLmModel,
        environment: LiteRtLmRuntimeEnvironment = LiteRtLmRuntimeEnvironment.current(),
    ): CapabilityAvailability {
        val spec = LiteRtLmModelSpec(model.modelPathRelative, model.metadataPathsRelative, backendPolicy = model.backendPolicy)
        val supportedAbi = environment.abis.any { it == "arm64-v8a" || it == "x86_64" }
        return LiteRtLmAvailability.forModel(
            store,
            model.ref,
            spec,
            closed = false,
            apiLevel = environment.apiLevel,
            supportedAbi = supportedAbi,
            probeState = LiteRtLmProbeState.SYNCHRONOUS,
        )
    }
}

/** ModelStore identity and safe paths relative to its installed model directory. */
data class LiteRtLmModel(
    val ref: ModelRef,
    val modelPathRelative: String,
    val metadataPathsRelative: Set<String>,
    val backendPolicy: LiteRtLmBackendPolicy = LiteRtLmBackendPolicy.CPU_ONLY,
)

/** Device policy inputs. Generation separately checks the actual native model metadata. */
data class LiteRtLmRuntimeEnvironment(
    val apiLevel: Int,
    val abis: Set<String>,
) {
    companion object {
        fun current() = LiteRtLmRuntimeEnvironment(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toSet())
    }
}
