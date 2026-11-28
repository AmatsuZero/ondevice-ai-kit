package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

/** Real reference-voice flow through the Compose accessibility surface; never prints user text. */
class ReferenceVoiceInstrumentation : Instrumentation() {
    private val facts = Bundle()
    private var checkName = "launch"
    private var index = 0

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        try {
            uiAutomation.serviceInfo?.let { info ->
                info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                uiAutomation.serviceInfo = info
            }
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            step("capability") { click(node(CAPABILITY, scroll = true)) }
            step("referenceMode") { click(node(listOf("voice-submode-reference"), scroll = true)) }
            step("loadSample") {
                click(node(listOf("voice-reference-example"), scroll = true))
                // 先按引用波形等样例真正加载；波形非交互，不要求 enabled。
                // 内容波形与 enabled 的 process 分别由 waveTags / convert 步骤校验，避免先滚到 process 再回头。
                node(listOf("voice-reference-import-wave"), scroll = true, enabledOnly = false, timeout = 30_000)
            }
            step("buttonSizes") {
                val r = listOf("voice-reference-import", "voice-reference-record", "voice-reference-clear")
                    .map { node(listOf(it), scroll = true).bounds }
                check(kotlin.math.abs(r[0].width() - r[1].width()) <= 1 &&
                    kotlin.math.abs(r[1].width() - r[2].width()) <= 1 &&
                    kotlin.math.abs(r[0].height() - r[1].height()) <= 1 &&
                    kotlin.math.abs(r[1].height() - r[2].height()) <= 1 &&
                    r[0].width() > 0 && r[0].height() > 0)
                facts.putString("buttonRects", "${r[0].width()}x${r[0].height()} equal")
            }
            step("waveTags") {
                val ref = node(listOf("voice-reference-import-wave"), scroll = true, enabledOnly = false)
                val content = node(listOf("voice-content-import-wave"), scroll = true, enabledOnly = false)
                check(!ref.contentDescription.isNullOrBlank() && !content.contentDescription.isNullOrBlank())
                facts.putString("waveTags", "${ref.contentDescription} | ${content.contentDescription}")
            }
            step("convert") {
                click(node(listOf("voice-reference-process"), scroll = true, enabledOnly = true))
                val result = node(listOf("voice-reference-result"), scroll = true, timeout = 120_000)
                check(result.text?.toString()?.contains("22050 Hz") == true)
                val phase = node(listOf("voice-phase"), scroll = true)
                check(phase.text?.toString()?.contains("完成") == true)
                facts.putString("result", result.text.toString())
                facts.putString("phase", phase.text.toString())
                screenshot("reference-voice-converted")
            }
            step("playSeekStop") {
                click(node(listOf("voice-play-result"), scroll = true))
                node(listOf("停止试听"), scroll = true, byText = true, timeout = 10_000)
                tapCenter(node(listOf("voice-compare-output"), scroll = true, timeout = 5_000))
                node(listOf("停止试听"), scroll = true, byText = true, timeout = 5_000)
                facts.putString("seek", "center-tap-50pct")
                click(node(listOf("停止试听"), scroll = true, byText = true))
            }
            step("export") {
                val export = node(listOf("voice-export"), scroll = true)
                check(export.isEnabled)
                facts.putString("export", "enabled")
            }
            step("clearReference") {
                click(node(listOf("voice-reference-clear"), scroll = true))
                check(!node(listOf("voice-reference-process"), scroll = true, enabledOnly = false).isEnabled)
                check(root()?.let { find(it) { n ->
                    n.viewIdResourceName == "voice-reference-result" ||
                        n.viewIdResourceName?.endsWith(":id/voice-reference-result") == true
                } } == null)
                facts.putString("clear", "process-disabled-output-gone")
            }
            step("fixedControls") {
                click(node(listOf("voice-submode-fixed"), scroll = true))
                val effects = listOf("voice-effect-voiceA", "voice-effect-voiceB", "voice-effect-voiceC",
                    "voice-effect-high", "voice-effect-low", "voice-effect-original").map {
                    node(listOf(it), scroll = true).viewIdResourceName?.substringAfterLast(":id/") ?: it
                }
                check(effects.size == 6)
                facts.putString("fixedControls", effects.joinToString(","))
            }
            step("liveChips") {
                click(node(listOf("voice-mode-live"), scroll = true))
                val roles = listOf("voice-live-role-UNCLE", "voice-live-role-GIRL", "voice-live-role-ANNOUNCER").map {
                    node(listOf(it), scroll = true).viewIdResourceName?.substringAfterLast(":id/") ?: it
                }
                check(roles.size == 3)
                facts.putString("liveRoles", roles.joinToString(","))
            }
            step("leaveReturn") {
                // liveChips 结束时处于实时模式，引用子模式芯片不可见；先切回文件模式。
                click(node(listOf("voice-mode-file"), scroll = true))
                // 回到引用子模式重新加载样例并等 process enabled，让离开返回真正覆盖清理逻辑。
                click(node(listOf("voice-submode-reference"), scroll = true))
                click(node(listOf("voice-reference-example"), scroll = true))
                node(listOf("voice-reference-process"), scroll = true, enabledOnly = true, timeout = 30_000)
                click(node(listOf("unified-back"), scroll = true))
                click(node(listOf("capability-voice_effects"), scroll = true))
                click(node(listOf("voice-submode-reference"), scroll = true))
                check(!node(listOf("voice-reference-clear"), scroll = true, enabledOnly = false).isEnabled)
                facts.putString("temporaryReference", "gone")
            }
            facts.putString("status", "passed")
        } catch (error: Throwable) {
            facts.putString("status", "failed")
            facts.putString("failure", checkName)
            facts.putString("errorClass", error.javaClass.name)
            facts.putString("errorMessage", error.message)
            report("fail", checkName)
        } finally {
            screenshot()
            finish(-1, facts)
        }
    }

    private companion object {
        val CAPABILITY = listOf("capability-voice_effects", "变声", "语音变声", "语音特效", "语音效果")
    }

    private fun step(name: String, block: () -> Unit) {
        checkName = name; index++
        report("start", name)
        block()
        facts.putString(name, "passed")
        report("pass", name)
    }

    private fun report(kind: String, name: String) {
        sendStatus(when (kind) { "start" -> 1; "pass" -> 0; "fail" -> -2; else -> 1 }, Bundle().apply {
            putString("class", this@ReferenceVoiceInstrumentation.javaClass.name)
            putString("test", name)
            putInt("numtests", 12)
            putInt("current", index)
            putString("status", kind)
            putString("check", name)
        })
    }

    private val AccessibilityNodeInfo.bounds: android.graphics.Rect
        get() = android.graphics.Rect().also { getBoundsInScreen(it) }

    private fun root(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        return uiAutomation.rootInActiveWindow
    }

    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }

    private fun clickable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var guard = 0
        while (cur != null && guard++ < 12) {
            if (cur.isClickable && cur.packageName?.toString() == targetContext.packageName) return cur
            cur = cur.parent
        }
        return null
    }

    private fun click(n: AccessibilityNodeInfo) {
        val target = if (n.isClickable) n else clickable(n)
        check(target != null && target.isVisibleToUser && target.isEnabled &&
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun node(matches: List<String>, scroll: Boolean = false, timeout: Long = 15_000,
                     byText: Boolean = false, enabledOnly: Boolean = true): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + timeout
        var forward = true
        var forwardScrolls = 0
        var scrolls = 0
        while (SystemClock.uptimeMillis() < end) {
            val currentRoot = root()
            if (currentRoot != null && currentRoot.packageName?.toString() == targetContext.packageName) {
                find(currentRoot) { n ->
                    n.isVisibleToUser && (!enabledOnly || n.isEnabled) &&
                        matches.any { m -> if (byText) n.text?.toString() == m
                            else n.viewIdResourceName == m || n.viewIdResourceName?.endsWith(":id/$m") == true ||
                                n.contentDescription?.toString() == m || n.text?.toString() == m }
                }?.let { return it }
                if (scroll && scrolls < 20) {
                    if (forward && forwardScrolls >= 6) forward = false
                    // 选自身应用内可见且面积最小的滚动容器，避免滚错层。
                    val scrollers = mutableListOf<AccessibilityNodeInfo>()
                    find(currentRoot) { n ->
                        if (n.isScrollable && n.isVisibleToUser && n.isEnabled &&
                            n.packageName?.toString() == targetContext.packageName) scrollers += n
                        false
                    }
                    val scroller = scrollers.minByOrNull { it.bounds.let { b -> b.width() * b.height() } }
                    if (scroller != null) {
                        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        if (scroller.performAction(action)) {
                            scrolls++
                            if (forward) forwardScrolls++
                            // 等真实滚动稳定，下一轮重新取 root 再匹配。
                            SystemClock.sleep(600)
                        } else forward = !forward
                    } else forward = !forward
                }
            }
            SystemClock.sleep(200)
        }
        dumpFailureTree(matches)
        error("missing node: ${matches.joinToString("|")}")
    }

    private fun dumpFailureTree(matches: List<String>) {
        try {
            val currentRoot = root() ?: return
            val sb = StringBuilder()
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (n.packageName?.toString() == targetContext.packageName) {
                    val b = n.bounds
                    sb.append("  ".repeat(depth))
                        .append("id=").append(n.viewIdResourceName)
                        .append(" desc=").append(n.contentDescription)
                        .append(" text=").append(n.text)
                        .append(" enabled=").append(n.isEnabled)
                        .append(" visible=").append(n.isVisibleToUser)
                        .append(" bounds=").append(b)
                        .append('\n')
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
            }
            walk(currentRoot, 0)
            File(targetContext.cacheDir, "reference-voice-failure.txt").writeText(sb.toString())
        } catch (_: Throwable) { }
    }

    private fun collect(predicate: (AccessibilityNodeInfo) -> Boolean): List<String> {
        val out = mutableListOf<String>()
        root()?.let { r ->
            if (r.packageName?.toString() == targetContext.packageName) find(r) { n ->
                if (predicate(n)) out += (n.viewIdResourceName?.substringAfterLast(":id/") ?:
                    n.contentDescription?.toString() ?: "node")
                false
            }
        }
        return out.distinct()
    }

    private fun tapCenter(n: AccessibilityNodeInfo) {
        val b = n.bounds
        check(b.width() > 0 && b.height() > 0)
        val x = ((b.left + b.right) / 2).toFloat()
        val y = ((b.top + b.bottom) / 2).toFloat()
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_UP, x, y, 0)
        down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        check(uiAutomation.injectInputEvent(down, true))
        check(uiAutomation.injectInputEvent(up, true))
        down.recycle(); up.recycle()
    }

    private fun screenshot(name: String = "reference-voice-acceptance") {
        try {
            val bitmap: Bitmap = uiAutomation.takeScreenshot() ?: return
            File(targetContext.cacheDir, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (_: Throwable) { }
    }
}
