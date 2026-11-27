package io.ondeviceai.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Roles available for live voice conversion. */
enum class LiveRole { UNCLE, GIRL, ANNOUNCER }

/** Where live converted audio is played (hardware observation). */
enum class LiveDestination { HEADPHONES, BUILTIN_SPEAKER }

/** Platform bridge. The host calls these on the Kotlin main thread. */
interface LiveVoicePort {
    fun start(id: Long, role: LiveRole, destination: LiveDestination)
    fun stop(id: Long)
    fun refreshRoute()
}

/** Host exposing a live voice session (usually also the demo actions). */
interface LiveVoiceOwner {
    val liveSession: RealtimeVoiceSession
}

/**
 * 主线程专用的按住说话状态机。宿主在主线程报告 [ready] / [ended]。
 * 公开状态全部由 Compose mutableStateOf 支撑，确保界面可见刷新。
 */
class RealtimeVoiceSession(private val port: LiveVoicePort) {

    enum class LivePhase { IDLE, STARTING, RUNNING, STOPPING }

    var role: LiveRole by mutableStateOf(LiveRole.UNCLE)
        private set

    /** null 表示当前没有可用的实时输出设备。 */
    var destination: LiveDestination? by mutableStateOf<LiveDestination?>(null)
        private set

    var phase: LivePhase by mutableStateOf(LivePhase.IDLE)
        private set

    var message: String? by mutableStateOf<String?>(null)
        private set

    val busy: Boolean get() = phase != LivePhase.IDLE

    private var nextId = 0L
    private var activeId: Long? = null
    private var stopPending = false

    /** 硬件输出路线变化；null 表示输出不可用。仅观察，不触发刷新、不重新 start。 */
    fun route(destination: LiveDestination?) {
        val changed = this.destination != destination
        this.destination = destination
        if (changed && busy) release()
    }

    /** 选择目标音色；按住期间（忙）忽略。 */
    fun select(role: LiveRole) {
        if (busy || this.role == role) return
        this.role = role
    }

    /** 请求宿主重新探测硬件输出路线。 */
    fun refresh() {
        port.refreshRoute()
    }

    /** 开始采集；输出不可用或忙时直接返回。 */
    fun press() {
        if (busy) return
        val dest = destination ?: return
        val id = ++nextId
        activeId = id
        stopPending = false
        message = null
        phase = LivePhase.STARTING
        port.start(id, role, dest)
    }

    /** 宿主报告 [id] 已就绪；过期或迟到的报告忽略。 */
    fun ready(id: Long) {
        if (id != activeId || phase != LivePhase.STARTING) return
        phase = LivePhase.RUNNING
    }

    /** 宿主报告 [id] 结束/清理完成（启动失败或停止确认），message 为原因。 */
    fun ended(id: Long, message: String) {
        if (id != activeId) return
        this.message = message
        activeId = null
        stopPending = false
        phase = LivePhase.IDLE
    }

    /** 结束当前采集（松开按钮 / 拆页面）。 */
    fun release() {
        val id = activeId ?: return
        if (stopPending) return
        stopPending = true
        phase = LivePhase.STOPPING
        port.stop(id)
    }
}
