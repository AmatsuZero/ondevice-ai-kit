package io.ondeviceai.demo

import io.ondeviceai.binding.*

/**
 * Publishes a fresh immutable gallery from host-owned opaque template references.
 * Policy verification and every authorization decision remain in [IdentityBridge].
 */
internal fun publishIdentityGallery(
    bridge: IdentityBridge,
    grant: IdentityAuthorizationGrant,
    options: IdentityGalleryOptions,
    backend: IdentityBackendSelection,
    members: List<IdentityTemplateReference>,
    callback: IdentityCallback<IdentityGalleryReference>,
): IdentityBridgeTask = IdentityGalleryPublication(bridge, grant, options, backend,
    members.distinctBy { it.canonicalValue }, callback).also { it.start() }

private class IdentityGalleryPublication(
    private val bridge: IdentityBridge,
    private val grant: IdentityAuthorizationGrant,
    private val options: IdentityGalleryOptions,
    private val backend: IdentityBackendSelection,
    private val members: List<IdentityTemplateReference>,
    private val callback: IdentityCallback<IdentityGalleryReference>,
) : IdentityBridgeTask {
    private val pending = ArrayDeque<() -> Unit>()
    private var draining = false
    private var terminal = false
    private var nextMember = 0
    private var builder: IdentityGalleryBuilderReference? = null
    private var current: IdentityBridgeTask? = null

    fun start() {
        if (members.isEmpty()) {
            finish(IdentityBridgeFailure.Rejected(IdentityErrorCode.INVALID_INPUT))
        } else {
            schedule(::openBuilder)
        }
    }

    override fun cancel() {
        if (terminal) return
        terminal = true
        val currentTask = current
        current = null
        val currentBuilder = builder
        builder = null
        currentTask?.cancel()
        currentBuilder?.let(::closeBuilder)
        schedule { callback.onFailure(IdentityBridgeFailure.Cancelled) }
    }

    override fun close() = cancel()

    private fun openBuilder() {
        if (isTerminal()) return
        var delivered = false
        val task = bridge.createGalleryBuilder(null, grant, options, backend,
            object : IdentityCallback<IdentityGalleryBuilderReference> {
                override fun onSuccess(value: IdentityGalleryBuilderReference) {
                    if (delivered) return
                    delivered = true
                    val accepted = if (terminal) false else {
                        builder = value
                        true
                    }
                    if (accepted) schedule(::addNext) else closeBuilder(value)
                }
                override fun onFailure(error: IdentityBridgeFailure) {
                    if (delivered) return
                    delivered = true
                    finish(error)
                }
            })
        remember(task)
    }

    private fun addNext() {
        if (terminal) return
        if (nextMember == members.size) { publish(); return }
        val target = members[nextMember]
        val activeBuilder = requireNotNull(builder)
        var delivered = false
        val task = bridge.addTemplate(activeBuilder, target, grant, object : IdentityCallback<Unit> {
            override fun onSuccess(value: Unit) {
                if (delivered) return
                delivered = true
                if (!terminal) nextMember++
                schedule(::addNext)
            }
            override fun onFailure(error: IdentityBridgeFailure) {
                if (delivered) return
                delivered = true
                finish(error)
            }
        })
        remember(task)
    }

    private fun publish() {
        if (terminal) return
        val activeBuilder = builder ?: return
        var delivered = false
        val task = bridge.publishGallery(activeBuilder, object : IdentityCallback<IdentityGalleryReference> {
            override fun onSuccess(value: IdentityGalleryReference) {
                if (delivered) return
                delivered = true
                finish(value)
            }
            override fun onFailure(error: IdentityBridgeFailure) {
                if (delivered) return
                delivered = true
                finish(error)
            }
        })
        remember(task)
    }

    private fun finish(value: IdentityGalleryReference) {
        if (terminal) return
        terminal = true
        current = null
        val toClose = builder
        builder = null
        toClose?.let(::closeBuilder)
        schedule { callback.onSuccess(value) }
    }

    private fun finish(error: IdentityBridgeFailure) {
        if (terminal) return
        terminal = true
        current = null
        val toClose = builder
        builder = null
        toClose?.let(::closeBuilder)
        schedule { callback.onFailure(error) }
    }

    private fun closeBuilder(value: IdentityGalleryBuilderReference) {
        schedule {
            bridge.closeGalleryBuilder(value, object : IdentityCallback<Unit> {
                override fun onSuccess(value: Unit) = Unit
                override fun onFailure(error: IdentityBridgeFailure) = Unit
            })
        }
    }

    private fun remember(task: IdentityBridgeTask) {
        if (!terminal) current = task
    }

    private fun isTerminal() = terminal

    /** Bridge callbacks are serialized on the platform UI thread; trampoline immediate callbacks. */
    private fun schedule(action: () -> Unit) {
        pending.addLast(action)
        if (draining) return
        draining = true
        while (true) {
            val next = pending.removeFirstOrNull()
            if (next == null) { draining = false; return }
            next()
        }
    }
}
