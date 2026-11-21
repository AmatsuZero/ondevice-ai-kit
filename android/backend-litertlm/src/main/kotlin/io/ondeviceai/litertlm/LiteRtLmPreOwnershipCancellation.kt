package io.ondeviceai.litertlm

import kotlinx.coroutines.CancellationException

/**
 * Factory-only signal requiring proof that local coordinator/Binder release completed before any HELLO or native acquisition.
 * This marker performs no cleanup; ambiguous HELLO, accepted START, or native ownership must never use it.
 */
internal class LiteRtLmPreOwnershipCancellation(
    val cancellation: CancellationException,
) : CancellationException(cancellation.message) {
    init {
        initCause(cancellation)
    }
}
