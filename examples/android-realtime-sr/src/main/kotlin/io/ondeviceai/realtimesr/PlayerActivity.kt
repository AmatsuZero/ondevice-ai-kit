package io.ondeviceai.realtimesr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

open class PlayerActivity : Activity(), SurfaceHolder.Callback {
    protected open fun useDirectOutput(): Boolean = false
    protected open fun useEarlySubmit(): Boolean = false
    protected open fun onFrameOffered(ptsUs: Long, targetNs: Long, id: FrameId) {}
    protected open fun onMediaPosition(mediaUs: Long) {}
    protected open fun onRunEnded() {}
    protected open fun onRunError(message: String) {}
    protected open fun onSrEvent(id: FrameId, stage: String, fields: Map<String, Any?>) {}
    protected val srRendererUsed: Boolean get() = session.srRendererUsed

    private lateinit var session: RealtimePlaybackSession
    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private lateinit var positionLabel: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private var seekingUi = false

    protected open fun selectSource(uri: Uri) = session.selectSource(uri)
    protected open fun selectMode(mode: PlaybackMode) = session.selectMode(mode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = RealtimePlaybackSession(this, ::useDirectOutput, ::useEarlySubmit,
            onChanged = ::refreshStatus, onFrameOffered = ::onFrameOffered,
            onMediaPosition = { mediaUs -> onMediaPosition(mediaUs); refreshStatus() },
            onRunEnded = ::onRunEnded, onRunError = ::onRunError, onSrEvent = ::onSrEvent)
        buildUi()
        refreshStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = "实时视频超分"
            textSize = 20f
            setPadding(24, 18, 24, 8)
        })
        surfaceView = SurfaceView(this).also { it.holder.addCallback(this) }
        root.addView(surfaceView, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            setLines(3)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(status)
        positionLabel = TextView(this)
        root.addView(positionLabel)
        seekBar = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(bar: SeekBar) { seekingUi = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    seekingUi = false
                    val length = session.durationUs
                    if (length > 0L) {
                        val target = length * bar.progress / bar.max
                        session.seekTo(target)
                    }
                }
            })
        }
        root.addView(seekBar)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        Button(this).also { it.text = "选择视频"; it.setOnClickListener { pickDocument() }; controls.addView(it) }
        playButton = Button(this).also { button ->
            button.text = "播放"
            button.setOnClickListener {
                session.togglePlayback()
            }
            controls.addView(button)
        }
        root.addView(controls)
        val modes = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        listOf(
            "原画" to PlaybackMode.ORIGINAL,
            "自动" to PlaybackMode.AUTO,
            "CuNNy" to PlaybackMode.CUNNY,
            "Spatial" to PlaybackMode.SPATIAL
        ).forEach { (label, mode) ->
            Button(this).also {
                it.text = label
                it.setOnClickListener { selectMode(mode) }
                modes.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        root.addView(modes)
        setContentView(root)
    }

    private fun pickDocument() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, PICK_VIDEO)
    }

    @Deprecated("Deprecated by Android; retained for a dependency-free Activity implementation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) { }
                selectSource(uri)
            }
        }
    }

    override fun onStart() { super.onStart(); session.start() }
    override fun onResume() { super.onResume(); session.resume() }
    override fun onPause() { session.pause(); super.onPause() }
    override fun onStop() { session.stop(); super.onStop() }
    override fun onDestroy() { session.close(); super.onDestroy() }
    override fun surfaceCreated(holder: SurfaceHolder) = session.surfaceCreated(holder.surface)
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = session.surfaceChanged(holder.surface)
    override fun surfaceDestroyed(holder: SurfaceHolder) = session.surfaceDestroyed()

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val state = session.srStatus
        status.text = "尝试后端：${state.preferredBackend}；实际呈现：${state.presentedBackend ?: "等待确认上屏"} · ${session.statusText}"
        playButton.text = if (session.isPlaying) "暂停" else "播放"
        positionLabel.text = formatTime(session.mediaPositionUs) + if (session.durationUs > 0) " / ${formatTime(session.durationUs)}" else ""
        seekBar.isEnabled = session.durationUs > 0
        if (!seekingUi && session.durationUs > 0) seekBar.progress = (session.mediaPositionUs * seekBar.max / session.durationUs).toInt().coerceIn(0, seekBar.max)
    }
    private fun formatTime(us: Long): String {
        val seconds = us.coerceAtLeast(0) / 1_000_000
        return "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }
    companion object { private const val PICK_VIDEO = 41 }
}
