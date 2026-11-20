package io.ondeviceai.realtimesr

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PlaybackUiProbe : Instrumentation() {
    private val timeoutMs = 20_000L
    private val positionPattern = Pattern.compile("\\b(\\d{2}):(\\d{2})\\s*/\\s*\\d{2}:\\d{2}\\b")
    private lateinit var runId: String
    private lateinit var outputDir: File
    private val records = JSONArray()
    private val swaps = JSONArray()
    private val captures = ArrayList<Bitmap>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        runId = arguments?.getString("runId") ?: ""
        start()
    }

    override fun onStart() {
        Thread({ execute() }, "PlaybackUiProbe").start()
    }

    private fun execute() {
        var failure: String? = null
        var lifecycle = JSONObject().put("attempted", false)
        try {
            check(runId.matches(Regex("[0-9a-f]{32}"))) { "Invalid instrumentation runId" }
            outputDir = File(File(targetContext.filesDir, "sr-ui"), runId)
            check(outputDir.mkdirs() || outputDir.isDirectory) { "Cannot create private output directory" }
            val intent = Intent().setClassName(targetContext.packageName, "io.ondeviceai.realtimesr.SrRunActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("runId", runId).putExtra("srCase", "original-egl")
            val activity = startActivitySync(intent)
            val initial = awaitPosition(activity, 2)
            val pause = awaitButton(activity, "Pause")
            click(pause)
            awaitButton(activity, "Play")
            val runDir = File(File(targetContext.filesDir, "sr-runs"), runId)
            val eventFile = File(runDir, "events.jsonl")
            var lastLine = eventFile.takeIf { it.isFile }?.readLines()?.size ?: 0
            var generation = -1L
            var identity: Pair<Long, Long>? = null
            val stages = listOf("Original" to "ORIGINAL", "CuNNy" to "CUNNY", "Original" to "ORIGINAL")
            var renderSize: Pair<Long, Long>? = null
            for ((index, stage) in stages.withIndex()) {
                val button = awaitButton(activity, stage.first)
                click(button)
                val status = awaitStatus(activity, stage.second)
                val event = awaitSwap(eventFile, lastLine, stage.second, generation, identity)
                lastLine = event.first
                val swap = event.second
                generation = swap.getLong("generation")
                val nextIdentity = swap.getLong("sourceOrdinal") to swap.getLong("ptsUs")
                if (identity == null) identity = nextIdentity else check(identity == nextIdentity) { "Paused preview source identity changed" }
                val submittedSize = swap.getLong("renderWidth") to swap.getLong("renderHeight")
                if (renderSize == null) renderSize = submittedSize
                else check(renderSize == submittedSize) { "Paused preview render size changed" }
                swaps.put(swap)
                val position = awaitPosition(activity, 2)
                val surface = awaitSurface(activity)
                repeat(3) {
                    val tick = CountDownLatch(1)
                    surface.postOnAnimation { tick.countDown() }
                    check(tick.await(2, TimeUnit.SECONDS)) { "No animation tick after paused swap" }
                }
                val bitmap = pixelCopy(surface)
                check(submittedSize == (bitmap.width.toLong() to bitmap.height.toLong())) {
                    "Paused preview render size does not match capture dimensions"
                }
                captures.add(bitmap)
                val name = "stage-${index + 1}.png"
                FileOutputStream(File(outputDir, name)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed" } }
                records.put(JSONObject().put("button", stage.first).put("position", position.first)
                    .put("status", status).put("capture", name))
            }
            check(equal(captures[0], captures[2])) { "First and third full-frame captures differ" }
            check(!equal(captures[0], captures[1])) { "Original and CuNNy full-frame captures are identical" }
            check(captures.all { it.width.toLong() == renderSize!!.first && it.height.toLong() == renderSize!!.second }) {
                "Paused preview capture dimensions differ from submitted render size"
            }
            click(awaitButton(activity, "Play"))
            val after = awaitPosition(activity, initial.second + 2)
            check(after.second >= initial.second + 2) { "Playback clock did not advance by two seconds" }
            check(after.second < 10) { "Playback advanced beyond the pre-seek range" }
            val seekBar = snapshot(activity).views.firstOrNull { it is android.widget.SeekBar } as? android.widget.SeekBar
                ?: error("No SeekBar available")
            awaitButton(activity, "Pause")
            runOnMainSync {
                check(seekBar.isEnabled && seekBar.max > 0 && seekBar.width > seekBar.paddingLeft + seekBar.paddingRight) {
                    "SeekBar is not ready for touch"
                }
                val left = seekBar.paddingLeft.toFloat()
                val trackWidth = (seekBar.width - seekBar.paddingLeft - seekBar.paddingRight).toFloat()
                val y = seekBar.height / 2f
                val downTime = android.os.SystemClock.uptimeMillis()
                fun dispatch(action: Int, x: Float, time: Long) {
                    val event = android.view.MotionEvent.obtain(downTime, time, action, x, y, 0)
                    try { check(seekBar.dispatchTouchEvent(event)) { "SeekBar did not handle touch" } }
                    finally { event.recycle() }
                }
                val currentX = left + trackWidth * seekBar.progress.coerceIn(0, seekBar.max) / seekBar.max
                val targetX = left + trackWidth / 2f
                dispatch(android.view.MotionEvent.ACTION_DOWN, currentX, downTime)
                dispatch(android.view.MotionEvent.ACTION_MOVE, targetX, downTime + 50)
                dispatch(android.view.MotionEvent.ACTION_UP, targetX, downTime + 100)
            }
            val seek = awaitPosition(activity, 15)
            check(seek.second in 15..18) { "Seek did not reach approximately halfway: ${seek.first}" }
            val advanced = awaitPosition(activity, seek.second + 2)
            check(advanced.second >= seek.second + 2) { "Playback did not continue after seek" }
            awaitButton(activity, "Pause")
            val decodedEnd = android.os.SystemClock.elapsedRealtime() + timeoutMs
            var decoded: JSONObject? = null
            while (decoded == null && android.os.SystemClock.elapsedRealtime() < decodedEnd) {
                decoded = readEvents(eventFile).firstOrNull {
                    it.optString("stage") == "decodeRelease" && it.optLong("generation", -1) > generation &&
                        it.optLong("ptsUs", -1) >= 14_000_000L
                }
                if (decoded == null) Thread.sleep(100)
            }
            check(decoded != null) { "No decoded frame at or beyond 14s in a new generation after seek" }
            val afterSeekGeneration = decoded!!.getLong("generation")
            records.put(JSONObject().put("type", "seek").put("displayedAtSeek", seek.first)
                .put("displayedAfterAdvance", advanced.first).put("decodedGeneration", afterSeekGeneration)
                .put("decodedPtsUs", decoded!!.getLong("ptsUs")))
            lifecycle = try {
                runLifecycleCheck(activity, eventFile, advanced.second, afterSeekGeneration)
            } catch (t: Throwable) {
                JSONObject().put("attempted", true).put("success", false)
                    .put("failure", t.message ?: t.javaClass.simpleName)
            }
            check(readEvents(eventFile).none { it.optString("reason") == "swap_failed" }) { "swap_failed event recorded" }
        } catch (t: Throwable) {
            failure = t.message ?: t.javaClass.simpleName
        } finally {
            captures.forEach { if (!it.isRecycled) it.recycle() }
            try {
                val result = JSONObject().put("runId", runId).put("records", records).put("swapEvents", swaps)
                    .put("success", failure == null).put("failure", failure)
                    .put("lifecycle", lifecycle)
                if (::outputDir.isInitialized) File(outputDir, "result.json").writeText(result.toString())
            } catch (writeError: Throwable) {
                failure = listOfNotNull(failure, "Could not write result.json: ${writeError.message}").joinToString("; ")
            }
            finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
                if (::outputDir.isInitialized) putString("result", File(outputDir, "result.json").absolutePath)
                failure?.let { putString("failure", it) }
            })
        }
    }

    private fun runLifecycleCheck(
        activity: Activity,
        eventFile: File,
        lastBeforeBackground: Int,
        afterSeekGeneration: Long
    ): JSONObject {
        val stopped = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(candidate: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(candidate: Activity) = Unit
            override fun onActivityResumed(candidate: Activity) {
                if (candidate === activity) resumed.countDown()
            }
            override fun onActivityPaused(candidate: Activity) = Unit
            override fun onActivityStopped(candidate: Activity) {
                if (candidate === activity) stopped.countDown()
            }
            override fun onActivitySaveInstanceState(candidate: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(candidate: Activity) = Unit
        }
        var registered = false
        try {
            runOnMainSync { activity.application.registerActivityLifecycleCallbacks(callbacks) }
            registered = true
            check(runOnMainValue { activity.moveTaskToBack(true) }) { "Could not move the playback task to background" }
            check(stopped.await(4, TimeUnit.SECONDS)) { "Timed out waiting for playback activity to stop" }
            runOnMainSync {
                activity.getSystemService(ActivityManager::class.java).appTasks
                    .single { it.taskInfo.id == activity.taskId }.moveToFront()
            }
            check(resumed.await(4, TimeUnit.SECONDS)) { "Timed out waiting for playback activity to resume" }
            val resumedEventStart = if (eventFile.isFile) eventFile.readLines().size else 0
            val positionAfterResume = awaitPosition(activity, (lastBeforeBackground - 1).coerceAtLeast(0))
            check(positionAfterResume.second >= lastBeforeBackground - 1) {
                "Playback position regressed by more than one second after resume"
            }
            val positionAfterAdvance = awaitPosition(activity, positionAfterResume.second + 2)
            check(positionAfterAdvance.second >= positionAfterResume.second + 2) {
                "Playback did not advance by two seconds after resume"
            }
            val minimumPtsUs = (positionAfterResume.second - 1).coerceAtLeast(0).toLong() * 1_000_000L
            val maximumPtsUs = (positionAfterAdvance.second + 1).toLong() * 1_000_000L
            val decodeEnd = android.os.SystemClock.elapsedRealtime() + timeoutMs
            var resumedDecode: JSONObject? = null
            while (resumedDecode == null && android.os.SystemClock.elapsedRealtime() < decodeEnd) {
                val lines = if (eventFile.isFile) eventFile.readLines() else emptyList()
                resumedDecode = lines.drop(resumedEventStart).asSequence().mapNotNull {
                    try { JSONObject(it) } catch (_: Exception) { null }
                }.firstOrNull {
                    val ptsUs = it.optLong("ptsUs", -1)
                    it.optString("stage") == "decodeRelease" &&
                        it.optLong("generation", -1) > afterSeekGeneration &&
                        ptsUs in minimumPtsUs..maximumPtsUs
                }
                if (resumedDecode == null) Thread.sleep(100)
            }
            check(resumedDecode != null) {
                "No resumed decodeRelease in a new generation near resumed playback position"
            }
            return JSONObject().put("attempted", true).put("success", true)
                .put("positionBeforeBackground", lastBeforeBackground)
                .put("positionAfterResume", positionAfterResume.second)
                .put("positionAfterResumeAdvance", positionAfterAdvance.second)
                .put("decodedGeneration", resumedDecode!!.getLong("generation"))
                .put("decodedPtsUs", resumedDecode!!.getLong("ptsUs"))
        } finally {
            if (registered) runOnMainSync { activity.application.unregisterActivityLifecycleCallbacks(callbacks) }
        }
    }

    private data class Snapshot(val views: List<View>, val texts: List<String>, val surfaces: List<SurfaceView>)

    private fun snapshot(activity: Activity): Snapshot {
        val result = arrayListOf<View>()
        val texts = arrayListOf<String>()
        val surfaces = arrayListOf<SurfaceView>()
        runOnMainSync {
            fun visit(view: View) {
                result.add(view)
                if (view is TextView) texts.add(view.text?.toString().orEmpty())
                if (view is SurfaceView) surfaces.add(view)
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
        }
        return Snapshot(result, texts, surfaces)
    }

    private fun awaitButton(activity: Activity, label: String): View {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val s = snapshot(activity)
            val found = s.views.firstOrNull { v ->
                (v is Button || v is TextView) && s.texts.any { it.equals(label, true) } &&
                    runOnMainValue { (v as? TextView)?.text?.toString().equals(label, true) && v.isEnabled }
            }
            if (found != null) return found
            Thread.sleep(100)
        }
        error("Timed out waiting for $label button")
    }

    private fun click(view: View) = runOnMainSync { check(view.performClick()) { "View click was not handled" } }

    private fun awaitPosition(activity: Activity, minimumSeconds: Int): Pair<String, Int> {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val values = snapshot(activity).texts
            for (text in values) {
                val m = positionPattern.matcher(text)
                if (m.find()) {
                    val seconds = m.group(1).toInt() * 60 + m.group(2).toInt()
                    if (seconds >= minimumSeconds) return text to seconds
                }
            }
            Thread.sleep(100)
        }
        error("Timed out waiting for displayed media position >= ${minimumSeconds}s")
    }

    private fun awaitStatus(activity: Activity, backend: String): String {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val text = snapshot(activity).texts.firstOrNull {
                it.contains("Preferred:", true) && it.contains("actual:", true) && it.contains("paused preview", true) && it.startsWith("Preferred: $backend;")
            }
            if (text != null) return text
            Thread.sleep(100)
        }
        error("Timed out waiting for $backend paused-preview status")
    }

    private fun awaitSurface(activity: Activity): SurfaceView {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            snapshot(activity).surfaces.firstOrNull()?.let { return it }
            Thread.sleep(100)
        }
        error("Timed out waiting for SurfaceView")
    }

    private fun pixelCopy(surface: SurfaceView): Bitmap {
        val size = runOnMainValue { surface.width to surface.height }
        check(size.first > 0 && size.second > 0) { "SurfaceView has no measured size" }
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        runOnMainSync { PixelCopy.request(surface, bitmap, { code -> result = code; latch.countDown() }, handler) }
        check(latch.await(timeoutMs, TimeUnit.MILLISECONDS)) { "PixelCopy timed out" }
        check(result == PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
        return bitmap
    }

    private fun awaitSwap(file: File, fromLine: Int, backend: String, previousGeneration: Long,
                          identity: Pair<Long, Long>?): Pair<Int, JSONObject> {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val lines = if (file.isFile) file.readLines() else emptyList()
            check(lines.none { it.contains("swap_failed") }) { "swap_failed event recorded" }
            for (i in fromLine.coerceAtMost(lines.size) until lines.size) {
                val event = try { JSONObject(lines[i]) } catch (_: Exception) { continue }
                if (event.optString("stage") != "swap" || !event.optBoolean("pausedPreview")) continue
                val actual = event.optString("submittedBackend").uppercase()
                if (actual != backend) continue
                val gen = event.optLong("generation", -1)
                val id = event.optLong("sourceOrdinal", -1) to event.optLong("ptsUs", -1)
                if (gen <= previousGeneration || id.first < 0 || id.second < 0) continue
                if (identity != null && id != identity) error("Paused preview source identity changed")
                return (i + 1) to event
            }
            Thread.sleep(100)
        }
        error("Timed out waiting for flushed $backend pausedPreview event")
    }

    private fun readEvents(file: File): List<JSONObject> = if (!file.isFile) emptyList() else file.readLines().mapNotNull {
        try { JSONObject(it) } catch (_: Exception) { null }
    }

    private fun equal(a: Bitmap, b: Bitmap) = a.sameAs(b)

    private fun <T> runOnMainValue(block: () -> T): T {
        var value: T? = null
        runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
