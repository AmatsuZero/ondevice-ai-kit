package io.ondeviceai.realtimesr

import android.app.AlertDialog
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale

class MotionPairActivity : PlayerActivity() {
    private val random = SecureRandom()
    private val main = Handler(Looper.getMainLooper())
    private val counts = mutableMapOf("A" to mutableMapOf<String, Int>(), "B" to mutableMapOf<String, Int>())
    private val playStarts = mutableMapOf("A" to 0, "B" to 0)
    private val eosReachedSides = mutableSetOf<String>()
    private val requestedBackendDisplayedSides = mutableSetOf<String>()
    private lateinit var assignments: Map<String, Map<String, PlaybackMode>>
    private lateinit var nonces: Map<Int, String>
    private lateinit var ballotDir: File
    private lateinit var sceneLabel: TextView
    private lateinit var status: TextView
    private lateinit var aButton: Button
    private lateinit var bButton: Button
    private lateinit var voteButton: Button
    private var scene = 1
    private var available = false
    private var playing = false
    private var alive = true
    private var runToken = 0L
    private var activeRun: TrialRun? = null

    private data class FrameKey(val token: Long, val frameId: FrameId, val eglFrameId: Long)

    private data class TrialRun(
        val token: Long,
        val side: String,
        val requestedBackend: String,
        val pending: MutableMap<FrameKey, String> = mutableMapOf(),
        val displayed: MutableMap<String, Int> = mutableMapOf()
    )

    override fun useEarlySubmit(): Boolean = true

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        title = "运动 A/B"

        val content = findViewById<ViewGroup>(android.R.id.content)
        val root = (0 until content.childCount).map { content.getChildAt(it) }
            .filterIsInstance<LinearLayout>()
            .firstOrNull { it.childCount > 0 && it.getChildAt(0) is SurfaceView }
            ?: (content as? LinearLayout)?.takeIf { it.childCount > 0 && it.getChildAt(0) is SurfaceView }
            ?: throw IllegalStateException("播放器视图结构不可用")
        val surface = root.getChildAt(0)
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child !== surface) {
                child.visibility = View.GONE
                child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }

        val id = randomHex(16)
        ballotDir = File(File(filesDir, "motion-pairs"), id)
        available = (1..3).all { File(filesDir, "motion-pairs/$it.mp4").let { f -> f.isFile && f.canRead() } }
        if (ballotDir.exists() || !ballotDir.mkdirs()) available = false
        assignments = (1..3).associate { n ->
            if (random.nextBoolean()) n.toString() to mapOf("A" to PlaybackMode.ORIGINAL, "B" to PlaybackMode.SPATIAL)
            else n.toString() to mapOf("A" to PlaybackMode.SPATIAL, "B" to PlaybackMode.ORIGINAL)
        }
        nonces = (1..3).associateWith { randomHex(16) }

        sceneLabel = TextView(this).apply { textSize = 16f; text = "片段 1 / 3" }
        root.addView(sceneLabel)
        status = TextView(this).apply { setLines(2); textSize = 14f }
        root.addView(status)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        aButton = Button(this).apply { isAllCaps = false; setOnClickListener { play("A") } }
        bButton = Button(this).apply { isAllCaps = false; setOnClickListener { play("B") } }
        row.addView(aButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(bButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        voteButton = Button(this).apply {
            text = "提交偏好"
            isAllCaps = false
            setOnClickListener { if (canVote()) showBallot() }
        }
        root.addView(voteButton)
        status.text = if (available) "请选择 A 或 B 播放/重播。" else "素材或保存目录不可用。"
        updateButtons()
    }

    private fun randomHex(size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun play(side: String) {
        if (!alive || !available || playing || side !in listOf("A", "B")) return
        val mode = assignments.getValue(scene.toString()).getValue(side)
        val backend = if (mode == PlaybackMode.ORIGINAL) "ORIGINAL" else "SPATIAL"
        val token = ++runToken
        activeRun = TrialRun(token, side, backend)
        playing = true
        playStarts[side] = playStarts.getValue(side) + 1
        status.text = "正在播放，请等待结束。"
        updateButtons()
        try {
            selectMode(mode)
            selectSource(Uri.fromFile(File(filesDir, "motion-pairs/$scene.mp4")))
        } catch (_: Exception) {
            playing = false
            activeRun = null
            status.text = "播放失败，请重试。"
            updateButtons()
        }
    }

    override fun onSrEvent(id: FrameId, stage: String, fields: Map<String, Any?>) {
        if (!alive || !playing || fields["pausedPreview"] == true) return
        val run = activeRun ?: return
        val kind = stage.lowercase(Locale.ROOT)
        if (kind != "swap" && kind != "display") return

        val eglValue = fields["eglFrameId"] as? Number ?: return
        val eglFrameId = eglValue.toLong()
        if (eglFrameId < 0L || eglFrameId.toDouble() != eglValue.toDouble()) return

        val key = FrameKey(run.token, id, eglFrameId)
        if (kind == "swap") {
            val backend = fields["submittedBackend"] as? String ?: return
            if (backend in listOf("ORIGINAL", "SPATIAL", "CUNNY") && fields["reason"] != "swap_failed") {
                run.pending[key] = backend
            }
        } else if (((fields["displayNs"] as? Number)?.toLong() ?: 0L) > 0L) {
            run.pending.remove(key)?.let { backend ->
                run.displayed[backend] = (run.displayed[backend] ?: 0) + 1
            }
        }
    }

    override fun onRunEnded() {
        val run = activeRun ?: return
        main.postDelayed({
            if (!alive || !playing || activeRun?.token != run.token) return@postDelayed
            run.displayed.forEach { (backend, n) ->
                val total = counts.getValue(run.side)
                total[backend] = (total[backend] ?: 0) + n
            }
            eosReachedSides.add(run.side)
            if ((run.displayed[run.requestedBackend] ?: 0) > 0) {
                requestedBackendDisplayedSides.add(run.side)
            }
            activeRun = null
            playing = false
            status.text = when {
                canVote() -> "A、B 均已到达播放结束位置，且检测到所选模式的显示帧，可提交偏好。"
                run.side in requestedBackendDisplayedSides ->
                    "该片段已到达播放结束位置。可播放或重播 A、B。"
                else ->
                    "该片段已到达播放结束位置，但未检测到所选模式的显示帧；请重播。"
            }
            updateButtons()
        }, 300L)
    }

    override fun onRunError(message: String) {
        if (!alive) return
        activeRun = null
        playing = false
        status.text = "播放失败，请重试。"
        updateButtons()
    }

    private fun canVote() =
        available && !playing &&
            eosReachedSides.containsAll(listOf("A", "B")) &&
            requestedBackendDisplayedSides.containsAll(listOf("A", "B"))

    private fun updateButtons() {
        aButton.text = if (playStarts.getValue("A") == 0) "播放 A" else "重播 A"
        bButton.text = if (playStarts.getValue("B") == 0) "播放 B" else "重播 B"
        aButton.isEnabled = available && !playing
        bButton.isEnabled = available && !playing
        voteButton.isEnabled = canVote()
    }

    private fun showBallot() {
        val choices = arrayOf("A", "B", "一样", "分不出")
        var selected = -1
        val note = EditText(this).apply {
            hint = "备注（选填）：哪边更清晰、光晕、闪烁、卡顿或音画同步问题"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(240))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("整体偏好")
            .setSingleChoiceItems(choices, -1) { d, which ->
                selected = which
                (d as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }
            .setView(note)
            .setPositiveButton("提交", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = false
                setOnClickListener {
                    if (selected < 0 || !canVote()) return@setOnClickListener
                    try {
                        saveBallot(choices[selected], note.text.toString().trim())
                        dialog.dismiss()
                        if (scene == 3) {
                            available = false
                            status.text = "已完成，感谢参与。"
                        } else {
                            scene++
                            sceneLabel.text = "片段 $scene / 3"
                            counts.values.forEach { it.clear() }
                            playStarts.keys.forEach { playStarts[it] = 0 }
                            eosReachedSides.clear()
                            requestedBackendDisplayedSides.clear()
                            status.text = "请选择 A 或 B 播放/重播。"
                        }
                        updateButtons()
                    } catch (e: Exception) {
                        status.text = "保存失败，请重试：${e.message ?: "未知错误"}"
                    }
                }
            }
        }
        dialog.show()
    }

    private fun saveBallot(choice: String, note: String) {
        val assignment = assignments.getValue(scene.toString())
        val mapping = JSONObject().put("A", assignment.getValue("A").name).put("B", assignment.getValue("B").name)
        val totals = JSONObject()
        counts.forEach { (side, values) -> totals.put(side, JSONObject(values as Map<*, *>)) }
        val json = JSONObject()
            .put("scene", scene)
            .put("nonce", nonces.getValue(scene))
            .put("mapping", mapping)
            .put("playStarts", JSONObject(playStarts as Map<*, *>))
            .put("displayedBackendCounts", totals)
            .put("choice", choice)
            .put("note", note)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val target = File(ballotDir, "scene-$scene.json")
        val temp = File(ballotDir, "scene-$scene.tmp")
        try {
            FileOutputStream(temp).use { it.write(json); it.fd.sync() }
            if (!temp.renameTo(target)) throw IOException("无法完成原子写入")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    override fun onStop() {
        alive = false
        playing = false
        activeRun = null
        runToken++
        main.removeCallbacksAndMessages(null)
        super.onStop()
        if (!isFinishing) finish()
    }
}
