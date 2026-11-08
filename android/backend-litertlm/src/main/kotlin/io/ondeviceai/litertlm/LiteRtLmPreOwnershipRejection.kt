package io.ondeviceai.litertlm

import io.ondeviceai.multimodal.MultimodalClientError

/**
 * Trusted factory-only signal for a rejection after the coordinator claim was locally released,
 * but before any native or remote owner was acquired. Missing or ambiguous HELLO, accepted START,
 * arbitrary failures or cancellation, and unresolved remote ownership must not use this signal.
 * It stores no service, lease, or private metadata and does not release anything itself.
 */
internal class LiteRtLmPreOwnershipRejection(
    val rejection: MultimodalClientError,
) : RuntimeException(rejection)
