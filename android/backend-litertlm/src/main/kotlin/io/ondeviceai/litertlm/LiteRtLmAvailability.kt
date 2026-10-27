package io.ondeviceai.litertlm

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.modelstore.ModelStatus
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.modelstore.ModelRef
import kotlinx.coroutines.CancellationException

internal enum class LiteRtLmProbeState { UNPROVEN, PROVEN_CUMULATIVE, PROVEN_DELTA }

internal object LiteRtLmAvailability {
    suspend fun forModel(
        store: ModelStore,
        ref: ModelRef,
        spec: LiteRtLmModelSpec,
        closed: Boolean,
        apiLevel: Int,
        supportedAbi: Boolean,
        probeState: LiteRtLmProbeState,
    ): CapabilityAvailability {
        val policy = forState(closed, apiLevel, supportedAbi, ModelStatus.INSTALLED, LiteRtLmProbeState.PROVEN_CUMULATIVE)
        if (policy.status != CapabilityStatus.AVAILABLE) return policy
        val modelStatus = try { store.status(ref) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ModelStatus.INVALID }
        if (modelStatus != ModelStatus.INSTALLED) {
            return forState(closed, apiLevel, supportedAbi, modelStatus, probeState)
        }
        if (probeState == LiteRtLmProbeState.UNPROVEN) {
            return forState(closed, apiLevel, supportedAbi, modelStatus, probeState)
        }
        val valid = try {
            val lease = store.acquire(ref)
            try { spec.matchesManifest(lease.manifest) }
            finally { lease.close() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        return forState(closed, apiLevel, supportedAbi,
            if (valid) ModelStatus.INSTALLED else ModelStatus.INVALID, probeState)
    }

    fun forState(
        closed: Boolean,
        apiLevel: Int,
        supportedAbi: Boolean,
        modelStatus: ModelStatus,
        probeState: LiteRtLmProbeState,
    ): CapabilityAvailability = when {
        closed -> unavailable(UnavailableReason.CLOSED)
        apiLevel < 26 -> unavailable(UnavailableReason.REQUIRES_OS_VERSION)
        !supportedAbi -> unavailable(UnavailableReason.UNSUPPORTED_DEVICE)
        modelStatus != ModelStatus.INSTALLED -> CapabilityAvailability(
            CapabilityStatus.REQUIRES_ASSETS, UnavailableReason.MISSING_ASSETS, "litertlm.android", "26")
        probeState == LiteRtLmProbeState.UNPROVEN -> unavailable(UnavailableReason.UNSUPPORTED_DEVICE)
        else -> CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = "litertlm.android", minOS = "26")
    }

    private fun unavailable(reason: UnavailableReason) = CapabilityAvailability(
        CapabilityStatus.UNAVAILABLE, reason, "litertlm.android", "26")
}
