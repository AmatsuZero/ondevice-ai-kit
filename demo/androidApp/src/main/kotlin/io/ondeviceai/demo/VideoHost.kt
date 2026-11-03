package io.ondeviceai.demo

import io.ondeviceai.binding.*

/** UI-thread selection owner. Processing leases outlive retired selections. */
class VideoHost(
    bridge: VideoBridge = UnavailableVideoBridge(),
    private val selectFile: ((String?, String?) -> Unit) -> Unit,
    private val openOutput: (String) -> Unit = {},
    private val shareOutput: (String) -> Unit = {},
    private val releaseInput: (String) -> Unit = {},
    private val closeInputs: () -> Unit = {},
    private val importModel: (() -> Unit)? = null,
) : VideoDemoActions, VideoModelSetupDemoActions {
    override val canImportVideoModel get() = importModel != null
    override val videoProfileDescription get() = if (canImportVideoModel)
        "Android CPU 逐帧 4×：H.264、BT.709 SDR、偶数尺寸 16×16 至 320×180、最多 2 秒/48 帧。可保留单条 AAC。逐帧 ESRGAN 不提供时域增强，CPU 处理可能较慢。"
        else "Android 视频超分后端未接入。"
    override fun importVideoModel() { if (!closed && foreground && model.state.controlsEnabled) importModel?.invoke() }

    val model = VideoViewModel(bridge)
    private var pickerGeneration = 0L
    private var foreground = true
    private var closed = false
    override fun selectVideoFile() {
        if (closed || !foreground || !model.state.controlsEnabled) return
        val generation = ++pickerGeneration
        selectFile { reference, name ->
            if (reference != null) {
                if (!closed && foreground && pickerGeneration == generation) {
                    val old = model.state.inputReference
                    model.selectVideo(reference, name)
                    if (old != null && old != reference) releaseInput(old)
                } else releaseInput(reference)
            }
        }
    }
    override fun clearVideoFile() { pickerGeneration++; val old = model.state.inputReference; model.selectVideo(null, null); old?.let(releaseInput) }
    override fun openVideo(reference: String) { if (!closed) openOutput(reference) }
    override fun shareVideo(reference: String) { if (!closed) shareOutput(reference) }
    override fun leavePage() { pickerGeneration++; model.leavePage() }
    fun background(preservePicker: Boolean = false) { foreground = false; if (!preservePicker) pickerGeneration++; model.background() }
    fun foreground() { if (!closed) foreground = true }
    fun close() { if (!closed) { closed = true; pickerGeneration++; model.close(); model.state.inputReference?.let(releaseInput); closeInputs() } }
}
