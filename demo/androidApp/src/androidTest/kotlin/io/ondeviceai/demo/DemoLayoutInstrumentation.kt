package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Page layout regression only: never activates camera, microphone or model inference. */
class DemoLayoutInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            for (page in listOf("speech-tab", "video-tab", "multimodal-tab")) {
                check(node { it.viewIdResourceName == page }.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "$page click rejected" }
                val screenHeight = targetContext.resources.displayMetrics.heightPixels
                // Accessibility events can precede Compose's next layout; observe the new
                // bounds instead of reading a cached title immediately after the click.
                val deadline = SystemClock.uptimeMillis() + 3_000
                val bounds = Rect()
                do {
                    node { it.text?.toString() == "OnDevice AI Kit" }.getBoundsInScreen(bounds)
                    if (bounds.top < screenHeight / 4) break
                    SystemClock.sleep(50)
                } while (SystemClock.uptimeMillis() < deadline)
                result.putInt("$page-titleTop", bounds.top)
                check(bounds.top < screenHeight / 4) { "$page retains the face preview above its controls: titleTop=${bounds.top}" }
                check(find(uiAutomation.rootInActiveWindow) { it.viewIdResourceName == "face-preview" } == null) {
                    "$page still displays face preview"
                }
            }
            // Returning to a face page must restore its preview without starting capture.
            result.putString("stage", "return-to-face")
            check(node { it.viewIdResourceName == "face-tab" }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            result.putString("stage", "observe-face-preview-space")
            val faceBounds = Rect()
            val faceDeadline = SystemClock.uptimeMillis() + 3_000
            val screenHeight = targetContext.resources.displayMetrics.heightPixels
            do {
                node { it.text?.toString() == "OnDevice AI Kit" }.getBoundsInScreen(faceBounds)
                if (faceBounds.top > screenHeight / 3) break
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < faceDeadline)
            result.putInt("face-titleTop", faceBounds.top)
            result.putBoolean("faceControlsVisible", find(uiAutomation.rootInActiveWindow) { it.text?.toString() == "选择图片" } != null)
            check(faceBounds.top > screenHeight / 3) { "face preview space was not restored" }
            result.putString("status", "passed")
        } catch (error: Exception) {
            result.putString("status", "failed")
            result.putString("failure", error.toString())
        }
        sendStatus(0, result)
        finish(-1, result)
    }

    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            uiAutomation?.rootInActiveWindow?.let { find(it, predicate) }?.let { return it }
            SystemClock.sleep(50)
        }
        error("page control not found")
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }
}
