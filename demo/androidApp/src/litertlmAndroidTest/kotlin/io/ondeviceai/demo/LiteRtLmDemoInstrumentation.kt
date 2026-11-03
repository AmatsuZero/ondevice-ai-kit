package io.ondeviceai.demo

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

/** Actual opt-in Activity and shared Compose UI, without a model, media read or inference. */
class LiteRtLmDemoInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val report = Bundle()
        var activity: Activity? = null
        var resultCode = Activity.RESULT_CANCELED
        var stage = "preflight"
        try {
            if (targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                !targetContext.getSystemService(PowerManager::class.java).isInteractive) {
                report.putString("status", "blocked-device-locked-or-screen-off")
                finish(Activity.RESULT_CANCELED, report)
                return
            }
            check(DemoMultimodalBackendFactory.enabled)
            check(!File(targetContext.filesDir, "litertlm/import/manifest.json").exists()) {
                "missing-model UI smoke requires an absent model bundle"
            }
            stage = "launch-opt-in"
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            check(node("multimodal-tab").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            stage = "missing-assets-backend"
            node("multimodal-backends") { it.text?.contains("litertlm.android") == true &&
                it.text?.contains("MISSING_ASSETS") == true }
            node("multimodal-select-input")
            check(find(uiAutomation.rootInActiveWindow, "multimodal-select-video") == null)
            check(!node("multimodal-generate").isEnabled)
            report.putString("status", "passed")
            report.putString("backend", "litertlm.android")
            report.putString("assets", "MISSING_ASSETS")
            report.putString("imageEntry", "visible")
            report.putString("videoEntry", "hidden-unsupported")
            report.putString("inference", "unverified-no-model")
            resultCode = Activity.RESULT_OK
        } catch (failure: Throwable) {
            report.putString("status", "failed")
            report.putString("stage", stage)
            report.putString("error", failure.javaClass.simpleName)
        } finally {
            activity?.let { runOnMainSync { it.finish() } }
        }
        finish(resultCode, report)
    }

    private fun node(tag: String, predicate: (AccessibilityNodeInfo) -> Boolean = { true }): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15_000
        do {
            find(uiAutomation.rootInActiveWindow, tag)?.let { if (predicate(it)) return it }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        error("missing expected Demo control")
    }

    private fun find(root: AccessibilityNodeInfo?, tag: String): AccessibilityNodeInfo? {
        root ?: return null
        if (root.viewIdResourceName == tag) return root
        for (index in 0 until root.childCount) find(root.getChild(index), tag)?.let { return it }
        return null
    }
}
