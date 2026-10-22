package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.ondeviceai.binding.*

/** Real shared Compose UI with synthetic references only; no picker, media or inference. */
class MultimodalScreenInstrumentation : Instrumentation() {
    private var stage = "launch"
    private lateinit var activity: MainActivity
    private val face = FaceViewModel(object : FaceBridge {
        override fun backendId() = "ui.fixture.face"
        override fun availability() = BridgeAvailability(false, "fixture", backendId(), null)
        override fun detect(inputId: String, selection: BridgeBackendSelection, callback: FaceCallback): BridgeTask = error("no face input")
        override fun close() = Unit
    })
    private val actions = object : DemoActions {
        override fun selectPhoto() = error("no media")
        override fun startCamera() = error("no camera")
        override fun stopCamera() = Unit
        override fun runBenchmark() = error("no benchmark")
        override fun exportReport(json: String) = error("no report")
    }

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            stage = "default-opt-out"
            show(null)
            node { it.text?.toString() == "人脸检测" }
            check(find(uiAutomation.rootInActiveWindow, "multimodal-tab") == null)
            result.putString("optOut", "passed-no-entry")

            stage = "unavailable"
            val unavailable = MultimodalViewModel()
            show(unavailable)
            click("multimodal-tab")
            stage = "unavailable-query"
            node { it.viewIdResourceName == "multimodal-query-failure" && it.text?.contains("BACKEND_UNAVAILABLE") == true }
            stage = "unavailable-no-input-generate"
            node { it.viewIdResourceName == "multimodal-generate" && !it.isEnabled }
            stage = "unavailable-input-name"
            check(node("multimodal-input").text?.toString() == "尚未选择图片") { "initial input name" }
            runOnMainSync { unavailable.selectImage("synthetic-opaque", "synthetic.png") }
            stage = "unavailable-generate"
            node { it.viewIdResourceName == "multimodal-generate" && it.isEnabled }
            click("multimodal-generate")
            node { it.viewIdResourceName == "multimodal-failure" && it.text?.contains("BACKEND_UNAVAILABLE") == true }
            result.putString("unavailable", "passed-real-default-bridge")

            stage = "raw-edits"
            val bridge = ScreenBridge()
            val model = MultimodalViewModel(bridge)
            show(model)
            runOnMainSync { model.selectImage("opaque-ui-fixture", "public-synthetic.png") }
            click("multimodal-tab")
            node { it.viewIdResourceName == "multimodal-backends" && it.text?.contains("ui.fixture.vlm") == true }
            edit("multimodal-backend", "  ui.fixture.vlm  ")
            edit("multimodal-prompt", "  原始 prompt\n保留空白  ")
            edit("multimodal-budget", "0")
            node { it.viewIdResourceName == "multimodal-generate" && !it.isEnabled }
            edit("multimodal-budget", "2147483648")
            node { it.viewIdResourceName == "multimodal-generate" && !it.isEnabled }
            edit("multimodal-budget", "7")
            node { it.viewIdResourceName == "multimodal-generate" && it.isEnabled }
            click("multimodal-generate")
            await { model.state.phase == MultimodalPhase.RUNNING }
            runOnMainSync {
                check(bridge.request == MultimodalBridgeRequest("opaque-ui-fixture", "  原始 prompt\n保留空白  ", 7)) { "raw prompt/budget mismatch: ${bridge.request}" }
                check(bridge.selection == MultimodalBridgeSelection.Explicit("  ui.fixture.vlm  ")) { "strict selection mismatch" }
                bridge.callback.onMultimodalUpdate(MultimodalBridgeUpdate(1, "累计甲"))
            }
            node { it.viewIdResourceName == "multimodal-update" && it.text?.contains("累计甲") == true }
            node { it.viewIdResourceName == "multimodal-prompt" && !it.isEnabled }
            click("multimodal-cancel")
            node { it.viewIdResourceName == "multimodal-phase" && it.text?.contains("CANCELLING") == true }
            node { it.viewIdResourceName == "multimodal-generate" && !it.isEnabled }
            runOnMainSync { check(bridge.cancels == 1); bridge.callback.onMultimodalCancelled() }
            await { model.state.phase == MultimodalPhase.CANCELLED }
            result.putString("editsCancel", "passed-raw-budget-update-drain")

            stage = "result-identity"
            click("multimodal-generate")
            await { model.state.phase == MultimodalPhase.RUNNING }
            runOnMainSync {
                bridge.callback.onMultimodalUpdate(MultimodalBridgeUpdate(2, "累计甲乙"))
            }
            node { it.viewIdResourceName == "multimodal-update" && it.text?.contains("累计甲乙") == true }
            runOnMainSync {
                bridge.callback.onMultimodalResult(MultimodalBridgeOutput("ui.fixture.actual", "合成结果",
                    MultimodalBridgeIdentity("fixture-model", "model-rev-1", "runtime-rev-2")))
            }
            node { it.viewIdResourceName == "multimodal-identity" && it.text?.contains("runtime-rev-2") == true && it.text?.contains("model-rev-1") == true && it.text?.contains("fixture-model") == true }
            node { it.viewIdResourceName == "multimodal-output" && it.text?.contains("合成结果") == true }
            node { it.viewIdResourceName == "multimodal-output-backend" && it.text?.contains("ui.fixture.actual") == true }
            result.putString("output", "passed-cumulative-and-actual-identity-fixture-only")

            stage = "typed-platform-failure"
            click("multimodal-generate")
            await { model.state.phase == MultimodalPhase.RUNNING }
            runOnMainSync {
                bridge.callback.onMultimodalFailure(MultimodalBridgeFailure(MultimodalBridgeFailureCode.PLATFORM, null, "ui.fixture", 23))
            }
            node { it.viewIdResourceName == "multimodal-failure" && it.text?.contains("PLATFORM") == true && it.text?.contains("ui.fixture") == true && it.text?.contains("23") == true }
            result.putString("failure", "passed-typed-domain-code")

            stage = "leave-reentry"
            click("multimodal-generate")
            node { it.viewIdResourceName == "multimodal-phase" && it.text?.contains("RUNNING") == true }
            val faceTab = node { it.text?.toString() == "人脸检测" }
            val faceButton = if (faceTab.isClickable) faceTab else faceTab.parent
            check(faceButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { bridge.cancels == 2 }
            runOnMainSync { check(bridge.cancels == 2); bridge.callback.onMultimodalCancelled() }
            click("multimodal-tab")
            node { it.viewIdResourceName == "multimodal-phase" && it.text?.contains("CANCELLED") == true }
            runOnMainSync { check(bridge.generations == 4); model.close(); unavailable.close() }
            result.putString("navigation", "passed-leave-cancel-no-auto-restart")

            stage = "legacy-page-leave"
            val speech = SpeechViewModel(object : SpeechBridge by UnavailableSpeechBridge() {
                override fun speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback): BridgeTask {
                    return object : BridgeTask { override fun cancel() = callback.onCancelled() }
                }
            })
            val video = VideoViewModel(object : VideoBridge by UnavailableVideoBridge() {
                override fun process(request: VideoBridgeRequest, selection: VideoBridgeSelection, callback: VideoBridgeProcessCallback): BridgeTask {
                    return object : BridgeTask { override fun cancel() = callback.onCancelled() }
                }
            })
            val navModel = MultimodalViewModel()
            show(navModel, speech, video)
            click("speech-tab")
            runOnMainSync { speech.speak(SpeechBridgeTtsRequest("silent UI fixture", "en-US")) }
            click("multimodal-tab")
            await { speech.state.phase == SpeechPhase.CANCELLED }
            click("video-tab")
            runOnMainSync { video.selectVideo("opaque-synthetic", "synthetic.mp4"); video.process() }
            click("multimodal-tab")
            await { video.state.phase == VideoPhase.CANCELLED }
            runOnMainSync { speech.close(); video.close(); navModel.close() }
            result.putString("legacyNavigation", "passed-active-speech-video-leave-no-audio-media")
            result.putString("inference", "unverified-ui-fixture-only")
            result.putString("status", "passed")
        } catch (error: Throwable) {
            result.putString("status", "failed")
            result.putString("failureStage", stage)
            result.putString("errorType", error.javaClass.simpleName)
            result.putString("assertion", error.message?.take(200))
            result.putString("ui", snapshot(uiAutomation.rootInActiveWindow).take(6000))
        } finally {
            if (::activity.isInitialized) runOnMainSync { activity.finish() }
        }
        finish(-1, result)
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun show(model: MultimodalViewModel?, speech: SpeechViewModel? = null, video: VideoViewModel? = null) {
        runOnMainSync {
            activity.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        FaceScreen(face, actions, speech = speech, video = video, multimodal = model)
                    }
                }
            }
        }
        waitForIdleSync()
    }

    private fun click(tag: String) {
        check(node { it.viewIdResourceName == tag && it.isEnabled && it.isVisibleToUser }.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "click $tag" }
        waitForIdleSync()
    }
    private fun edit(tag: String, text: String) {
        check(node { it.viewIdResourceName == tag && it.isVisibleToUser }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })) { "edit $tag" }
        waitForIdleSync()
    }
    private fun node(tag: String) = node { it.viewIdResourceName == tag }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            runOnMainSync { done = predicate() }
            if (done) return
            SystemClock.sleep(50)
        }
        error("state transition timeout at $stage")
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 12_000
        var attempts = 0
        while (SystemClock.uptimeMillis() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 33) uiAutomation.clearCache()
            val root = uiAutomation.rootInActiveWindow
            find(root, predicate)?.let { return it }
            find(root) { it.isScrollable }?.performAction(if ((attempts / 5) % 2 == 0)
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            // Scroll updates and Compose accessibility events are asynchronous. Do not
            // reverse a still-running scroll or read cached descendants from its old frame.
            try { uiAutomation.waitForIdle(250, 2_000) } catch (_: java.util.concurrent.TimeoutException) { }
            attempts++; SystemClock.sleep(100)
        }
        error("missing UI assertion at $stage")
    }
    private fun find(root: AccessibilityNodeInfo?, tag: String) = find(root) { it.viewIdResourceName == tag }
    private fun snapshot(root: AccessibilityNodeInfo?): String {
        if (root == null) return "no root"
        val own = root.packageName?.toString() == targetContext.packageName
        val line = if (own) "[${root.viewIdResourceName} text=${root.text} enabled=${root.isEnabled} visible=${root.isVisibleToUser} clickable=${root.isClickable} scroll=${root.isScrollable}]\n" else ""
        return line + (0 until root.childCount).joinToString("") { snapshot(root.getChild(it)) }
    }
    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(root.getChild(index), predicate)?.let { return it }
        return null
    }

    private class ScreenBridge : MultimodalBridge {
        lateinit var request: MultimodalBridgeRequest
        lateinit var selection: MultimodalBridgeSelection
        lateinit var callback: MultimodalBridgeGenerateCallback
        var generations = 0
        var cancels = 0
        override fun queryMultimodalBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask {
            callback.onMultimodalBackends(listOf(MultimodalBridgeBackend("ui.fixture.vlm", BridgeAvailability(true, null, "ui.fixture.vlm", "fixture"))))
            return object : BridgeTask { override fun cancel() = Unit }
        }
        override fun generateMultimodal(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection, callback: MultimodalBridgeGenerateCallback): BridgeTask {
            this.request = request; this.selection = selection; this.callback = callback; generations++
            return object : BridgeTask { override fun cancel() { cancels++ } }
        }
        override fun closeMultimodal(callback: MultimodalBridgeCloseCallback) = callback.onMultimodalClosed()
    }
}
