package io.ondeviceai.litertlm

import android.content.Context
import android.os.Build
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore
import java.io.File

/**
 * Opt-in LiteRT-LM backends for Android.
 *
 * Two explicit entry points:
 * - [createBackend]: the original synchronous image backend.
 * - [createProcessBackend]: an explicit opt-in, process-backed backend for the pinned LiteRT-LM
 *   0.18.1 text callback path (official MessageCallback latest-chunk-only contract forwarded as
 *   DELTA).
 *
 * Common lazy-construction guarantee (both entry points): the constructor, [availability] and
 * [LiteRtLmBackend.close] before any generation do NOT bind the dedicated process or open a native
 * runtime. Construction only records the model spec and device environment; native model/device
 * compatibility is checked when generation opens the model. There is no public callbackMode knob.
 *
 * Process-ONLY generation/death barrier: for [createProcessBackend], generation owns the remote
 * incarnation (bind plus the original Binder death registration) and cleanup waits for the
 * registered Binder death before releasing resources. The synchronous [createBackend] entry point
 * does NOT use Binder and has no remote incarnation.
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

    /**
     * Explicit opt-in, process-backed backend for the pinned LiteRT-LM 0.18.1 text callback path.
     *
     * Construction never binds the dedicated process, never opens a native runtime, and performs no
     * GPU/drain cleanup. The same ABI/API/spec/policy checks and model paths as [createBackend]
     * apply; only the callback forwarding (official MessageCallback latest-chunk-only contract as
     * DELTA) and proven-input probe state differ. Generation owns the remote incarnation and waits
     * for the registered Binder death on cleanup. There is no public callbackMode knob.
     */
    fun createProcessBackend(
        context: Context,
        store: ModelStore,
        model: LiteRtLmModel,
        cacheRoot: File,
        environment: LiteRtLmRuntimeEnvironment = LiteRtLmRuntimeEnvironment.current(),
    ): LiteRtLmBackend = LiteRtLmBackend(
        store, model.ref, LiteRtLmModelSpec(model.modelPathRelative, model.metadataPathsRelative, backendPolicy = model.backendPolicy),
        cacheRoot, LiteRtLmProcessEngineFactory(context, LiteRtLmCallbackMode.DELTA), environment.apiLevel,
        environment.abis.any { it == "arm64-v8a" || it == "x86_64" }, LiteRtLmProbeState.PROVEN_DELTA,
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
