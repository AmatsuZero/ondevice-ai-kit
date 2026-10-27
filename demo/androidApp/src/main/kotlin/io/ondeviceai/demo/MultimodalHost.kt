package io.ondeviceai.demo

import io.ondeviceai.binding.MultimodalBridge
import io.ondeviceai.binding.UnavailableMultimodalBridge
import java.io.File
import kotlinx.coroutines.*

/** UI-serialized native ownership; the bridge owns generation, drain and operation leases. */
class MultimodalHost(
    private val store: MultimodalInputStore,
    bridge: MultimodalBridge = UnavailableMultimodalBridge(),
    private val selectFile: ((File?) -> Unit, () -> Unit) -> Unit,
    private val videoStore: MultimodalInputStore? = null,
    private val selectVideo: (((File?) -> Unit, () -> Unit) -> Unit)? = null,
    private val importScope: CoroutineScope? = null,
    private val importDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val canSelectInput: () -> Boolean = { true },
) : MultimodalVideoDemoActions {
    override val supportsVideoInput: Boolean get() = videoStore != null && selectVideo != null
    val model = MultimodalViewModel(bridge)
    private var selected: String? = null
    private var selectedStore: MultimodalInputStore = store
    private var pickerGeneration = 0L
    private var pendingPicker: Long? = null
    private var foreground = true
    private var closed = false
    private var importing: Job? = null
    private val imports = mutableSetOf<Job>()

    /** Picker-owned temporary sources must remain alive until this returns. UI dispatcher only. */
    suspend fun awaitImports() {
        while (imports.isNotEmpty()) imports.toList().joinAll()
    }
    private fun invalidateImport() { importing?.cancel(); importing = null }
    private suspend fun stage(target: MultimodalInputStore, source: File): String {
        var token: String? = null
        try {
            return withContext(importDispatcher) {
                target.importImage(source) { ensureActive() }.also { token = it }
            }
        } catch (error: Throwable) {
            token?.let(target::remove) // withContext can cancel after successful copy, before delivery.
            throw error
        }
    }

    override fun selectImageFile() = selectInput(false)
    override fun selectVideoFile() = selectInput(true)
    private fun selectInput(video: Boolean) {
        if (closed || !foreground || !model.state.controlsEnabled || !canSelectInput()) return
        val target = if (video) videoStore else store
        val picker = if (video) selectVideo else selectFile
        if (target == null || picker == null) { model.inputFailed(); return }
        invalidateImport()
        val ticket = ++pickerGeneration
        pendingPicker = ticket
        try {
            picker({ source ->
                if (closed || model.state.closed || pendingPicker != ticket) return@picker
                pendingPicker = null
                if (source != null) {
                    fun publish(token: String) {
                        if (closed || model.state.closed || pickerGeneration != ticket) {
                            target.remove(token)
                            return
                        }
                        val previous = selected
                        val previousStore = selectedStore
                        selected = token
                        selectedStore = target
                        if (video) model.selectVideo(token, source.name) else model.selectImage(token, source.name)
                        previous?.let(previousStore::remove)
                    }
                    val scope = importScope
                    if (scope == null) {
                        // Compatibility path for synchronous embedded hosts; the Activity always supplies a UI scope.
                        try { publish(target.importImage(source)) } catch (_: Exception) { model.inputFailed() }
                    } else {
                        val job = scope.launch(start = CoroutineStart.LAZY) {
                            try { publish(stage(target, source)) }
                            catch (_: CancellationException) { /* Invalidated imports never publish a failure. */ }
                            catch (_: Exception) {
                                if (!closed && !model.state.closed && pickerGeneration == ticket) model.inputFailed()
                            }
                        }
                        importing = job
                        imports += job
                        job.invokeOnCompletion { imports.remove(job); if (importing === job) importing = null }
                        job.start()
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
        pickerGeneration++
        invalidateImport()
        model.selectImage(null, null)
        selected?.let(selectedStore::remove)
        selected = null
    }

    fun leavePage() { pendingPicker = null; pickerGeneration++; invalidateImport(); model.leavePage() }
    override fun leaveMultimodalPage() = leavePage()
    fun background(preservePicker: Boolean = false) {
        foreground = false
        if (!preservePicker) { pendingPicker = null; pickerGeneration++; invalidateImport() }
        model.background()
    }
    fun onForeground() { if (!closed) foreground = true }
    fun close() {
        if (!closed) {
            closed = true
            pickerGeneration++
            invalidateImport()
            pendingPicker = null
            try { model.close() } finally { store.close(); videoStore?.close() }
            selected = null
        } else {
            store.close(); videoStore?.close() // Retry file deletion without closing the bridge again.
        }
    }
}
