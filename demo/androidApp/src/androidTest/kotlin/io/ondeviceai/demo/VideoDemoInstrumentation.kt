package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Device smoke for the unavailable host. Never selects or opens device media. */
class VideoDemoInstrumentation : Instrumentation() {
    private var stage = "launch"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            stage = "video-tab"
            check(node { it.viewIdResourceName == "video-tab" }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            stage = "unavailable"
            node { it.text?.contains("BACKEND_UNAVAILABLE") == true }
            check(node { it.viewIdResourceName == "video-input" }.text?.toString() == "尚未选择视频")
            check(!node { it.viewIdResourceName == "video-process" }.isEnabled)
            result.putString("backend", "passed-explicit-unavailable")
            stage = "picker-open"
            check(node { it.viewIdResourceName == "video-select" }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            node { it.packageName != targetContext.packageName }
            stage = "picker-cancel"
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            node { it.viewIdResourceName == "video-select" }
            check(node { it.viewIdResourceName == "video-input" }.text?.toString() == "尚未选择视频")
            check(!node { it.viewIdResourceName == "video-process" }.isEnabled)
            result.putString("picker", "passed-cancel-without-input")
            stage = "background"
            runOnMainSync { activity.moveTaskToBack(true) }
            val end = SystemClock.uptimeMillis() + 5_000
            var stopped = false
            while (!stopped && SystemClock.uptimeMillis() < end) {
                runOnMainSync { stopped = !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
                SystemClock.sleep(100)
            }
            check(stopped)
            runOnMainSync { activity.startActivity(Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            check(node { it.viewIdResourceName == "video-phase" }.text?.toString() == "IDLE")
            check(!node { it.viewIdResourceName == "video-process" }.isEnabled)
            result.putString("foreground", "passed-idle-without-input")
            result.putString("processing", "unverified-no-android-backend")
            result.putString("status", "passed")
        } catch (_: Throwable) {
            result.putString("status", "failed")
            result.putString("failureStage", stage)
        }
        finish(-1, result)
    }

    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + 15_000
        var attempts = 0
        while (SystemClock.uptimeMillis() < end) {
            uiAutomation.rootInActiveWindow?.let { root ->
                find(root, predicate)?.let { return it }
                find(root) { it.isScrollable }?.performAction(
                    if ((attempts / 6) % 2 == 0) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
            attempts++; SystemClock.sleep(200)
        }
        error("missing video control")
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }
}
