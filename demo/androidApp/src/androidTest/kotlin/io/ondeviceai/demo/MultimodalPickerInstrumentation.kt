package io.ondeviceai.demo

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Rect
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
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择输入" }
            result.putString("systemPicker", "passed-open-cancel-without-media-read")

            stage("system-picker-real-selection")
            click("multimodal-select-input")
            await { uiAutomation.rootInActiveWindow?.packageName?.toString()?.let { it != targetContext.packageName } == true }
            clickPickerControl(contentDescription = setOf("显示根目录", "Show roots"))
            // OpenDocument surfaces the test provider as a visible source card on
            // this device; selecting it exercises DocumentsUI without coordinates.
            clickPickerControl(
                text = setOf("OnDevice AI test fixtures"),
                contentDescription = setOf("OnDevice AI test fixtures"),
            )
            clickPickerControl(contentDescription = setOf("real-system.png"))
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString()?.contains("real-system.png") == true }
            result.putString("actualSelectedSystemFile", "passed-documents-provider-real-picker")
            click("multimodal-clear-input")
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择输入" }

            stage("busy-reselection")
            val slowFixture = Uri.parse("content://io.ondeviceai.demo.test.multimodal-fixture/slow.png")
            var busyIntercepted = 0
            monitor = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult? {
                    if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                    busyIntercepted += 1
                    return ActivityResult(Activity.RESULT_OK, Intent().setData(slowFixture))
                }
            }
            addMonitor(monitor)
            click("multimodal-select-input")
            check(awaitActivityImport(activity!!)) { "slow fixture did not keep the Activity-owned import active" }
            result.putString("activityMidCopy", "passed-activity-owned-import-active")
            click("multimodal-select-input")
            check(busyIntercepted == 1) { "busy reselection launched ${busyIntercepted} pickers" }
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString()?.contains("public-slow.png") == true }
            result.putString("busyReselection", "passed-first-import-retained-second-picker-rejected")
            removeMonitor(monitor); monitor = null
            click("multimodal-clear-input")
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择输入" }

            stage("background-active-import")
            monitor = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult? {
                    if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                    return ActivityResult(Activity.RESULT_OK, Intent().setData(slowFixture))
                }
            }
            addMonitor(monitor)
            click("multimodal-select-input")
            check(awaitActivityImport(activity!!)) { "slow fixture did not keep the Activity-owned import active" }
            check(find(uiAutomation.rootInActiveWindow) {
                it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择输入"
            } != null) { "slow import published before lifecycle transition" }
            runOnMainSync { check(activity!!.moveTaskToBack(true)) }
            await { uiAutomation.rootInActiveWindow?.packageName?.toString()?.let { it != targetContext.packageName } == true }
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString()?.contains("public-slow.png") == true }
            result.putString("backgroundMidCopy", "passed-import-retained-after-background-return")
            removeMonitor(monitor); monitor = null
            click("multimodal-clear-input")
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择输入" }

            stage("synthetic-activity-result")
            val fixture = Uri.parse("content://io.ondeviceai.demo.test.multimodal-fixture/synthetic.png")
            var intercepted = 0
            monitor = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult? {
                    if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
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
            val fixtureInput = checkNotNull(targetContext.contentResolver.openInputStream(fixture))
            val fixtureBytes = try {
                fixtureInput.readBytes()
            } finally {
                fixtureInput.close()
            }
            check(fixtureBytes.contentEquals(byteArrayOf(1, 2, 3)))
            click("multimodal-clear-input")
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择输入" }
            result.putString("syntheticResult", "passed-real-callback-copy-host-clear-fixture-only")

            stage("background-return")
            runOnMainSync { check(activity!!.moveTaskToBack(true)) }
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            node { it.viewIdResourceName == "multimodal-phase" && it.text?.toString() == "FAILED" }
            node { it.viewIdResourceName == "multimodal-input" && it.text?.toString() == "尚未选择输入" }
            result.putString("foreground", "passed-no-input-no-auto-generation")
            runOnMainSync { activity!!.finish() }
            waitForIdleSync()
            await { targetContext.cacheDir.listFiles().orEmpty().none { it.name !in before &&
                (it.name.startsWith("multimodal-input-") || it.name.startsWith("multimodal-picker-")) } }
            result.putString("cleanup", "passed-new-owned-directories-released")
            check(result.getString("actualSelectedSystemFile") == "passed-documents-provider-real-picker")
            check(result.getString("backgroundMidCopy") == "passed-import-retained-after-background-return")
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

    private fun clickPickerControl(text: Set<String> = emptySet(), contentDescription: Set<String> = emptySet()) {
        val target = node { candidate ->
            candidate.packageName?.toString() == "com.google.android.documentsui" &&
                candidate.isVisibleToUser &&
                ((candidate.text?.toString() in text &&
                    (text.isEmpty() || candidate.viewIdResourceName == "android:id/title")) || contentDescription.any {
                    candidate.contentDescription?.toString()?.contains(it) == true
                })
        }
        var clickable: AccessibilityNodeInfo? = target
        while (clickable != null && !clickable!!.isClickable) clickable = clickable!!.parent
        if (clickable?.isClickable == true) {
            check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "picker control click failed: text=${target.text} contentDescription=${target.contentDescription}"
            }
        } else {
            // DocumentsUI exposes some root/document rows as non-clickable
            // accessibility containers. Inject a tap at the node-derived center
            // rather than depending on a device-specific coordinate.
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            check(!bounds.isEmpty) { "picker control has no bounds" }
            uiAutomation.executeShellCommand(
                "input tap ${bounds.centerX()} ${bounds.centerY()}",
            ).close()
        }
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

    private fun awaitActivityImport(current: Activity): Boolean {
        val field = MainActivity::class.java.getDeclaredField("multimodalImport").apply { isAccessible = true }
        await {
            var active = false
            runOnMainSync { active = (field.get(current) as? kotlinx.coroutines.Job)?.isActive == true }
            active
        }
        return true
    }
}
