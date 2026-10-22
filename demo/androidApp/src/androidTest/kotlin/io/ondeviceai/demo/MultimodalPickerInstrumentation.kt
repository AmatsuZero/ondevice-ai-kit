package io.ondeviceai.demo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Real opt-in Activity and system cancellation; selected result is an explicit synthetic test fixture. */
class MultimodalPickerInstrumentation : Instrumentation() {
    private var stage = "launch"
    private fun stage(value: String) { stage = value; sendStatus(0, Bundle().apply { putString("stage", value) }) }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        stage("launch")
        val result = Bundle()
        var activity: Activity? = null
        var monitor: ActivityMonitor? = null
        val before = targetContext.cacheDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            val intent = Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity = startActivitySync(intent)
            node { it.text?.toString() == "人脸检测" }
            check(find(uiAutomation.rootInActiveWindow) { it.viewIdResourceName == "multimodal-tab" } == null)
            result.putString("optOut", "passed-normal-activity-hidden")
            runOnMainSync { activity!!.finish() }
            waitForIdleSync()
            activity = startActivitySync(Intent(intent).putExtra("odai.multimodal.enabled", true))
            click("multimodal-tab")
            node { it.viewIdResourceName == "multimodal-query-failure" && it.text?.contains("BACKEND_UNAVAILABLE") == true }
            result.putString("optIn", "passed-real-unavailable-host")

            stage("system-picker-cancel")
            click("multimodal-select-input")
            await { uiAutomation.rootInActiveWindow?.packageName?.toString()?.let { it != targetContext.packageName } == true }
            check(uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择图片" }
            result.putString("systemPicker", "passed-open-cancel-without-media-read")

            stage("synthetic-activity-result")
            val fixture = Uri.parse("content://io.ondeviceai.demo.test.multimodal-fixture/synthetic.png")
            var intercepted = 0
            monitor = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult? {
                    if (intent.action != Intent.ACTION_GET_CONTENT) return null
                    intercepted += 1
                    return ActivityResult(Activity.RESULT_OK, Intent().setData(fixture))
                }
            }
            addMonitor(monitor)
            click("multimodal-select-input")
            node { it.viewIdResourceName == "multimodal-input" && it.text?.contains("public-synthetic.png") == true }
            check(intercepted == 1)
            removeMonitor(monitor); monitor = null
            click("multimodal-generate")
            node { it.viewIdResourceName == "multimodal-failure" && it.text?.contains("BACKEND_UNAVAILABLE") == true }
            check(targetContext.contentResolver.openInputStream(fixture)!!.use { it.readBytes() }.contentEquals(byteArrayOf(1, 2, 3)))
            click("multimodal-clear-input")
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择图片" }
            result.putString("syntheticResult", "passed-real-callback-copy-host-clear-fixture-only")

            stage("background-return")
            runOnMainSync { check(activity!!.moveTaskToBack(true)) }
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            node { it.viewIdResourceName == "multimodal-phase" && it.text?.toString() == "FAILED" }
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择图片" }
            result.putString("foreground", "passed-no-input-no-auto-generation")
            runOnMainSync { activity!!.finish() }
            waitForIdleSync()
            await { targetContext.cacheDir.listFiles().orEmpty().none { it.name !in before &&
                (it.name.startsWith("multimodal-input-") || it.name.startsWith("multimodal-picker-")) } }
            result.putString("cleanup", "passed-new-owned-directories-released")
            result.putString("actualSelectedSystemFile", "unverified-synthetic-result-only")
            result.putString("inference", "unverified-no-android-backend")
            result.putString("status", "passed")
        } catch (error: Throwable) {
            result.putString("status", "failed")
            result.putString("stage", stage)
            result.putString("error", error.javaClass.simpleName + ": " + error.message?.take(160))
            result.putInt("monitorHits", monitor?.hits ?: -1)
            result.putString("package", uiAutomation.rootInActiveWindow?.packageName?.toString())
            result.putString("input", find(uiAutomation.rootInActiveWindow) { it.viewIdResourceName == "multimodal-input" }?.text?.toString())
            result.putString("failure", find(uiAutomation.rootInActiveWindow) { it.viewIdResourceName == "multimodal-failure" }?.text?.toString())
        } finally {
            monitor?.let(::removeMonitor)
            activity?.let { current -> runOnMainSync { current.finish() } }
        }
        finish(-1, result)
    }
    private fun click(tag: String) {
        check(node { it.viewIdResourceName == tag && it.isEnabled && it.isVisibleToUser }
            .performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitForIdleSync()
    }
    private fun await(predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            if (android.os.Build.VERSION.SDK_INT >= 33) uiAutomation.clearCache()
            if (predicate()) return
            SystemClock.sleep(100)
        }
        error("timeout at $stage")
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        var attempts = 0
        await {
            val root = uiAutomation.rootInActiveWindow
            found = find(root, predicate)
            if (found == null) {
                find(root) { it.isScrollable }?.performAction(if ((attempts++ / 5) % 2 == 0)
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                try { uiAutomation.waitForIdle(250, 2_000) } catch (_: java.util.concurrent.TimeoutException) { }
            }
            found != null
        }
        return checkNotNull(found)
    }
    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(root.getChild(index), predicate)?.let { return it }
        return null
    }
}
