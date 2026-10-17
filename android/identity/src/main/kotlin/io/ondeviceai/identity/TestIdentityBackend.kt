package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason

/** Internal fixture backend, never registered by the SDK as a production backend. */
internal class FixtureIdentityBackend private constructor(override val backendId: String, revision: String, private val available: Boolean): IdentityBackend {
    override val backendVersion = "fixture-1"
    override val modelBinding = ModelBinding(if (backendId == "system.fixture") ModelBindingKind.SYSTEM else ModelBindingKind.MODEL, backendId, revision)
    override val embeddingSpace = EmbeddingSpaceId(setOf(IdentityCapability.VERIFICATION, IdentityCapability.IDENTIFICATION), backendId, backendVersion, modelBinding, "1", 2, FeatureDType.FLOAT32, Endianness.LITTLE, "none", "fixture-p1", "cosine", ScoreDirection.HIGHER_IS_BETTER)
    private var closed = false
    companion object {
        fun system(revision: String = "os-build-fixture") = FixtureIdentityBackend("system.fixture", revision, true)
        fun optionalUnavailable() = FixtureIdentityBackend("optional.fixture", "v1", false)
    }
    @Synchronized override fun availability() = CapabilityAvailability(
        if (available && !closed) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
        if (closed) UnavailableReason.CLOSED else if (available) null else UnavailableReason.MISSING_ASSETS, backendId
    )
    private fun check(probe: IdentityProbe) {
        if (closed) throw IdentityError(IdentityErrorCode.CLOSED, backendId)
        if (!available) throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE, backendId)
        if (probe.fixtureID == null || probe.vector?.size != embeddingSpace.dimension) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }
    @Synchronized override fun embed(probe: IdentityProbe): IdentityEmbedding {
        check(probe)
        return IdentityEmbedding(embeddingSpace, requireNotNull(probe.vector))
    }
    @Synchronized override fun evaluateQuality(probe: IdentityProbe): IdentityQuality { check(probe); return IdentityQuality(1, 1.0) }
    @Synchronized override fun close() { closed = true }
}
