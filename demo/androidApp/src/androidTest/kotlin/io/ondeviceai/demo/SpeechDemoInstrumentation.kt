package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.view.accessibility.AccessibilityNodeInfo

/** Exercises the real Activity and Compose accessibility surface; never prints user text. */
class SpeechDemoInstrumentation : Instrumentation() {
    private var lastControl = "launch"
    private var mode: String? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode"); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            if (mode == "asr-download") { downloadEnglishModel(result); finish(-1, result); return }
            node("speech-tab").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (mode == "asr-live") {
                node("speech-recognize-mic", scroll = true).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val permissionDeadline = SystemClock.uptimeMillis() + 5_000
                while (SystemClock.uptimeMillis() < permissionDeadline) {
                    if (targetContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) break
                    val permission = uiAutomation.rootInActiveWindow?.let { root -> find(root) {
                        it.viewIdResourceName?.endsWith(":id/permission_allow_foreground_only_button") == true
                    } }
                    if (permission != null) { permission.performAction(AccessibilityNodeInfo.ACTION_CLICK); break }
                    SystemClock.sleep(100)
                }
                // RUNNING is admission, not proof of the platform onReadyForSpeech callback.
                sendStatus(1, Bundle().apply { putString("stage", "asr-request-active") })
                val terminal = node("speech-asr-result", scroll = true, timeout = 60_000, alternate = "speech-failure")
                result.putString("asr", if (terminal.viewIdResourceName == "speech-asr-result") "passed-real-microphone" else "failed")
                if (terminal.viewIdResourceName == "speech-failure") result.putString("asrFailure", terminal.text?.toString())
                result.putString("status", if (terminal.viewIdResourceName == "speech-asr-result") "passed" else "failed")
                finish(-1, result)
                return
            }
            val input = node("speech-tts-text", scroll = true)
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Speech demo fixture.")
            })
            node("speech-speak", scroll = true).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node("speech-tts-result", scroll = true, timeout = 30_000)
            result.putString("ttsCompletion", "passed")
            val longInput = node("speech-tts-text", scroll = true)
            longInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "This is an on device cancellation fixture. ".repeat(24))
            })
            node("speech-speak", scroll = true).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node("speech-cancel", scroll = true).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node("speech-cancelled", scroll = true)
            result.putString("ttsCancellation", "passed")
            node("speech-speak", scroll = true).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node("speech-cancel", scroll = true).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node("speech-cancelled", scroll = true)
            result.putString("ttsReentry", "passed")
            node("speech-speak", scroll = true).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            runOnMainSync { activity.moveTaskToBack(true) }
            val stoppedDeadline = SystemClock.uptimeMillis() + 5_000
            var stopped = false
            while (!stopped && SystemClock.uptimeMillis() < stoppedDeadline) {
                runOnMainSync { stopped = !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
                SystemClock.sleep(100)
            }
            check(stopped)
            runOnMainSync { activity.startActivity(Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            node("speech-cancelled", scroll = true)
            result.putString("backgroundCancellationAndNoRestart", "passed")
            result.putString("asr", "unverified-requires-live-speech")
            result.putString("status", "passed")
        } catch (_: Throwable) {
            result.putString("status", "failed")
            result.putString("failure", lastControl)
            result.putBoolean("speechTabTextPresent", uiAutomation.rootInActiveWindow?.let { root ->
                find(root) { it.text?.toString() == "语音" } != null
            } ?: false)
            val ids = mutableListOf<String>()
            uiAutomation.rootInActiveWindow?.let { root -> find(root) {
                it.viewIdResourceName?.takeIf { name -> name.startsWith("speech-") }?.let(ids::add)
                false
            } }
            result.putString("controlIds", ids.distinct().joinToString(","))
        }
        finish(-1, result)
    }

    /** Explicit operator-only provisioning mode; never called by the App or normal test run. */
    @android.annotation.TargetApi(34)
    private fun downloadEnglishModel(result: Bundle) {
        check(android.os.Build.VERSION.SDK_INT >= 34)
        val terminal = CountDownLatch(1)
        var recognizer: SpeechRecognizer? = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        runOnMainSync {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(targetContext)
            recognizer!!.checkRecognitionSupport(intent, targetContext.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    sendStatus(1, Bundle().apply {
                        putString("installed", support.installedOnDeviceLanguages.joinToString(","))
                        putString("supported", support.supportedOnDeviceLanguages.joinToString(","))
                        putString("pending", support.pendingOnDeviceLanguages.joinToString(","))
                    })
                    recognizer!!.triggerModelDownload(intent, targetContext.mainExecutor, object : ModelDownloadListener {
                        override fun onProgress(completedPercent: Int) {
                            sendStatus(1, Bundle().apply { putInt("downloadPercent", completedPercent) })
                        }
                        override fun onSuccess() { result.putString("download", "success"); terminal.countDown() }
                        override fun onScheduled() { result.putString("download", "scheduled-not-ready"); terminal.countDown() }
                        override fun onError(error: Int) {
                            if (error == SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS) {
                                recognizer!!.triggerModelDownload(intent)
                                result.putString("download", "requested-without-events-not-ready")
                            } else result.putString("download", "error-$error")
                            terminal.countDown()
                        }
                    })
                }
                override fun onError(error: Int) { result.putString("support", "error-$error"); terminal.countDown() }
            })
        }
        if (!terminal.await(120, TimeUnit.SECONDS)) result.putString("download", "timeout")
        runOnMainSync { recognizer?.destroy() }
        result.putString("status", if (result.getString("download") == "success") "passed" else "unverified")
    }

    private fun node(id: String, scroll: Boolean = false, timeout: Long = 15_000, alternate: String? = null): AccessibilityNodeInfo {
        lastControl = id
        val end = SystemClock.uptimeMillis() + timeout
        var attempts = 0
        while (SystemClock.uptimeMillis() < end) {
            val root = uiAutomation.rootInActiveWindow
            if (root != null) {
                find(root) { it.contentDescription?.toString() == id || it.viewIdResourceName == id || (alternate != null && it.viewIdResourceName == alternate) }?.let { return it }
                if (scroll) find(root) { it.isScrollable }?.let { swipe(it, (attempts / 6) % 2 == 0) }
            }
            attempts++; SystemClock.sleep(200)
        }
        error("missing speech control")
    }
    private fun swipe(container: AccessibilityNodeInfo, forward: Boolean) {
        val bounds = Rect().also(container::getBoundsInScreen)
        val x = bounds.left + 8f
        val bottom = bounds.bottom - 80f
        val top = bounds.top + 80f
        val from = if (forward) bottom else top
        val to = if (forward) top else bottom
        val start = SystemClock.uptimeMillis()
        fun inject(action: Int, y: Float) {
            MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0).also {
                it.source = InputDevice.SOURCE_TOUCHSCREEN
                uiAutomation.injectInputEvent(it, true); it.recycle()
            }
        }
        inject(MotionEvent.ACTION_DOWN, from)
        for (step in 1..12) { SystemClock.sleep(40); inject(MotionEvent.ACTION_MOVE, from + (to - from) * step / 12) }
        inject(MotionEvent.ACTION_UP, to)
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) node.getChild(index)?.let { child -> find(child, predicate)?.let { return it } }
        return null
    }
}
