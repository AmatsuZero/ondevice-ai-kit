package io.ondeviceai.demo

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

data class SpeechAsrResourceState(
    val locale: String? = null,
    val status: String = "NOT_CHECKED",
    val busy: Boolean = false,
    val canDownload: Boolean = false,
    val progress: Int? = null,
    val platformCode: Int? = null,
)

interface SpeechAsrResourceProvider {
    val asrResources: SpeechAsrResources?
}

/** Optional host controls; metadata is separate from microphone recognition results. */
interface SpeechAsrResources {
    val state: SpeechAsrResourceState
    fun check(locale: String)
    fun download(locale: String)
    fun cancel()
}

@Composable
internal fun AsrResourceControls(resources: SpeechAsrResources, locale: String, enabled: Boolean) {
    val state = resources.state
    var confirmLocale by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(locale, state.canDownload) { confirmLocale = null }
    Text("Android 系统本机 ASR 语言资源")
    val label = when (state.status) {
        "NOT_CHECKED" -> "尚未检查"
        "CHECKING" -> "正在查询"
        "INSTALLED" -> "已安装（尚未验证转写）"
        "PENDING" -> "系统已排队，请稍后重新检查"
        "DOWNLOADABLE" -> "可请求下载；需要 Android 14 或更高版本"
        "NOT_REPORTED" -> "服务未报告该语言的本机资源"
        "DOWNLOADING" -> "等待系统下载${state.progress?.let { " · $it%" } ?: ""}"
        "MONITOR_CANCELLED" -> "已停止等待；系统下载可能继续"
        "MONITOR_TIMEOUT" -> "等待超时，请重新检查资源"
        "QUERY_ERROR" -> "系统不支持此次查询或查询失败"
        "DOWNLOAD_ERROR" -> "系统下载失败，请重新检查资源"
        "REQUIRES_OS_VERSION" -> "资源查询需要 Android 13 或更高版本"
        "UNSUPPORTED_DEVICE" -> "此设备没有可用的本机识别服务"
        "INVALID_LOCALE" -> "请输入有效语言标签"
        else -> "资源操作失败"
    }
    Text("${state.locale ?: locale} · $label${state.platformCode?.let { " · error $it" } ?: ""}",
        Modifier.testTag("speech-asr-resource-state"))
    Row {
        TextButton(onClick = { resources.check(locale) }, enabled = enabled && !state.busy && locale.isNotBlank(),
            modifier = Modifier.testTag("speech-check-asr-resources")) { Text("检查语言资源") }
        if (state.canDownload && state.locale == locale) {
            TextButton(onClick = { confirmLocale = locale }, enabled = enabled && !state.busy,
                modifier = Modifier.testTag("speech-download-asr-resources")) { Text("准备语言资源") }
        }
        if (state.busy) TextButton(onClick = resources::cancel) { Text("停止等待") }
    }
    if (state.busy) LinearProgressIndicator()
    val selected = confirmLocale
    if (selected != null && selected == locale && state.canDownload && enabled && !state.busy) {
        AlertDialog(onDismissRequest = { confirmLocale = null },
            title = { Text("准备 $selected 本机语音资源") },
            text = { Text("将请求系统下载，可能使用网络并显示系统确认。停止等待或离开页面不保证取消系统下载；本操作不会录音。") },
            confirmButton = { TextButton(onClick = { confirmLocale = null; resources.download(selected) },
                modifier = Modifier.testTag("speech-confirm-asr-download")) { Text("请求系统下载") } },
            dismissButton = { TextButton(onClick = { confirmLocale = null }) { Text("返回") } })
    }
}
