package io.ondeviceai.demo

import android.content.Context
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.ondeviceai.binding.RealtimeSRMode
import io.ondeviceai.realtimesr.RealtimePlaybackSession

/** CMP owns navigation/controls; the reused native session owns decoding, pixels and presentation. */
class PlaybackEnhancementHost(context: Context, private val chooseVideo: () -> Unit) :
    PlaybackEnhancementDemoActions, RealtimeSRDemoActions {
    private var active = false
    private var pickerEpoch = 0L
    private val session = RealtimePlaybackSession(context)
    val surfaceView = SurfaceView(context).also { view ->
        view.contentDescription = "实时超分视频画面"
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = session.surfaceCreated(holder.surface)
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = session.surfaceChanged(holder.surface)
            override fun surfaceDestroyed(holder: SurfaceHolder) = session.surfaceDestroyed()
        })
    }
    override val playerAvailable = true
    override val controlsAvailable = true
    override val playerDescription = "本地 H.264/AAC · 540p/1080p BT.709、≤30 fps 可尝试 2× CuNNy/Spatial。其他输入播放原画；增强超时逐帧回退。"
    override val hasVideo get() = session.hasVideo
    override val isPlaying get() = session.isPlaying
    override var inputName: String? = null
        private set
    override val playbackStatus get() = session.statusText
    override val supportedModes get() = session.supportedModes
    override val selectedMode get() = session.selectedMode
    override val presentedMode get() = session.srStatus.presentedBackend?.let { backend ->
        RealtimeSRMode.entries.firstOrNull { it.name == backend.name }
    }
    override val fallbackToOriginal get() = session.srStatus.fellBackToOriginal
    val idle get() = !session.cleanupPending
    val selectionEpoch get() = pickerEpoch

    fun enter() { active = true; session.start(); session.resume() }
    fun picked(uri: Uri?, name: String?, epoch: Long) {
        if (!active || epoch != pickerEpoch || uri == null) return
        inputName = name ?: "本地视频"; session.selectSource(uri)
    }
    override fun openPlayer() { if (active) chooseVideo() }
    override fun togglePlayback() = session.togglePlayback()
    override fun selectMode(mode: RealtimeSRMode) = session.selectRealtimeMode(mode)
    override fun showOriginal() { selectMode(RealtimeSRMode.ORIGINAL) }
    override fun showEnhancedPreview() { selectMode(RealtimeSRMode.AUTO) }
    fun background() { if (active) { session.pause(); session.stop() } }
    fun foreground() { if (active) { session.start(); session.resume() } }
    fun leave() { active = false; pickerEpoch++; inputName = null; session.leave() }
    fun close() { active = false; pickerEpoch++; session.close() }
}
