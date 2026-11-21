package io.ondeviceai.identity

/** Internal immutable-vector lease. Closing drops the vector before notifying its store. */
internal class IdentityTemplateLease(
    initial: IdentityEmbedding,
    private val onRelease: (IdentityTemplateLease) -> Unit
) : AutoCloseable {
    private var held: IdentityEmbedding? = initial

    val embedding: IdentityEmbedding
        get() = synchronized(this) { held ?: throw IdentityError(IdentityErrorCode.CLOSED) }

    override fun close() {
        val release = synchronized(this) {
            if (held == null) null else {
                held = null
                onRelease
            }
        }
        release?.invoke(this)
    }
}
