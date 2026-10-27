package io.ondeviceai.probe.litertlm

/** Closes pre-generation resources; any attempted send needs a native drain proof. */
internal class ProbeNativeOwner(private val closeEngine: () -> Unit) {
    private var closeConversation: (() -> Unit)? = null
    private var sendAttempted = false
    private var closed = false

    fun acquiredConversation(close: () -> Unit) {
        check(closeConversation == null && !closed)
        closeConversation = close
    }

    fun beginSend() { sendAttempted = true }

    fun closeIfSafe() {
        if (closed || sendAttempted) return
        closed = true
        try { closeConversation?.invoke() }
        finally { closeEngine() }
    }
}
