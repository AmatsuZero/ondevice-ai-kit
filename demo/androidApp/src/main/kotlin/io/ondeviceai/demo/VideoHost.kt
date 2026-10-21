package io.ondeviceai.demo

import io.ondeviceai.binding.*

/** Android has no registered video backend yet; selection never implies processing support. */
class VideoHost(
    bridge: VideoBridge = UnavailableVideoBridge(),
    private val selectFile: ((String?, String?) -> Unit) -> Unit,
    private val openOutput: (String) -> Unit = {},
    private val shareOutput: (String) -> Unit = {},
) : VideoDemoActions {
    val model = VideoViewModel(bridge)
    private var pickerGeneration = 0L
    private var foreground = true
    private var closed = false
    override fun selectVideoFile() {
        if (closed || !foreground || !model.state.controlsEnabled) return
        val generation = ++pickerGeneration
        selectFile { reference, name ->
            if (!closed && foreground && pickerGeneration == generation && reference != null) model.selectVideo(reference, name)
        }
    }
    override fun clearVideoFile() { pickerGeneration++; model.selectVideo(null, null) }
    override fun openVideo(reference: String) { if (!closed) openOutput(reference) }
    override fun shareVideo(reference: String) { if (!closed) shareOutput(reference) }
    override fun leavePage() { pickerGeneration++; model.leavePage() }
    fun background(preservePicker: Boolean = false) { foreground = false; if (!preservePicker) pickerGeneration++; model.background() }
    fun foreground() { if (!closed) foreground = true }
    fun close() { if (!closed) { closed = true; pickerGeneration++; model.close() } }
}
