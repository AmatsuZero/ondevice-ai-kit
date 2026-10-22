package io.ondeviceai.demo

import io.ondeviceai.binding.MultimodalBridge
import io.ondeviceai.binding.UnavailableMultimodalBridge
import java.io.File

/** UI-serialized native ownership; the bridge owns generation, drain and operation leases. */
class MultimodalHost(
    private val store: MultimodalInputStore,
    bridge: MultimodalBridge = UnavailableMultimodalBridge(),
    private val selectFile: ((File?) -> Unit, () -> Unit) -> Unit,
) : MultimodalDemoActions {
    val model = MultimodalViewModel(bridge)
    private var selected: String? = null
    private var pickerGeneration = 0L
    private var pendingPicker: Long? = null
    private var foreground = true
    private var closed = false

    override fun selectImageFile() {
        if (closed || !foreground || !model.state.controlsEnabled) return
        val ticket = ++pickerGeneration
        pendingPicker = ticket
        try {
            selectFile({ source ->
                if (closed || model.state.closed || pendingPicker != ticket) return@selectFile
                pendingPicker = null
                if (source != null) {
                    try {
                        val token = store.importImage(source)
                        val previous = selected
                        selected = token
                        model.selectImage(token, source.name)
                        previous?.let(store::remove)
                    } catch (_: Exception) {
                        model.inputFailed()
                    }
                }
            }, {
                if (!closed && !model.state.closed && pendingPicker == ticket) {
                    pendingPicker = null
                    model.inputFailed()
                }
            })
        } catch (_: Exception) {
            if (pendingPicker == ticket) {
                pendingPicker = null
                model.inputFailed()
            }
        }
    }

    override fun clearImageFile() {
        if (closed) return
        pendingPicker = null
        model.selectImage(null, null)
        selected?.let(store::remove)
        selected = null
    }

    fun leavePage() { pendingPicker = null; model.leavePage() }
    override fun leaveMultimodalPage() = leavePage()
    fun background(preservePicker: Boolean = false) {
        foreground = false
        if (!preservePicker) pendingPicker = null
        model.background()
    }
    fun onForeground() { if (!closed) foreground = true }
    fun close() {
        if (!closed) {
            closed = true
            pendingPicker = null
            try { model.close() } finally { store.close() }
            selected = null
        } else {
            store.close() // Retry file deletion without closing the bridge again.
        }
    }
}
