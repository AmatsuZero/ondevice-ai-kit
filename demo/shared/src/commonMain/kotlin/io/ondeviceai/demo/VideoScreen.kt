package io.ondeviceai.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.*

@Composable
fun VideoScreen(model: VideoViewModel, actions: VideoDemoActions?) {
    val state = model.state
    var backendId by remember { mutableStateOf("") }
    LaunchedEffect(model) { model.refreshBackends() }
    Text("视频超分", style = MaterialTheme.typography.titleMedium)
    Text("Apple 当前支持：320×180、24 fps、1–48 帧、BT.709 SDR，4× 输出。音频保留支持单条等长 AAC。")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { model.selectBackend(VideoBridgeSelection.Default) }, enabled = state.controlsEnabled) { Text("使用默认后端") }
        TextButton(onClick = model::refreshBackends, enabled = !state.refreshing) { Text(if (state.refreshing) "刷新中…" else "刷新后端") }
    }
    OutlinedTextField(backendId, { backendId = it; if (it.isNotBlank()) model.selectBackend(VideoBridgeSelection.Explicit(it.trim())) },
        label = { Text("显式后端 ID（严格选择）") }, enabled = state.controlsEnabled, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("video-backend"))
    Text(when (val selection = state.selection) {
        VideoBridgeSelection.Default -> "选择：默认"
        is VideoBridgeSelection.Explicit -> "选择：${selection.backendId}"
    })
    state.backends.forEach {
        Text("${it.backendId} · ${if (it.availability.available) "可用" else it.availability.reason ?: "不可用"} · minOS ${it.availability.minimumOS ?: "—"}")
    }
    state.queryFailure?.let { Text("查询：${it.code} ${it.reason ?: ""}", color = MaterialTheme.colorScheme.error) }
    Text(state.inputDisplayName ?: "尚未选择视频", Modifier.testTag("video-input"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { actions?.selectVideoFile() }, enabled = state.controlsEnabled && actions != null,
            modifier = Modifier.testTag("video-select")) { Text("选择视频") }
        TextButton(onClick = { actions?.clearVideoFile() }, enabled = state.controlsEnabled && state.inputReference != null) { Text("移除输入") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { model.selectAudioPolicy(VideoBridgeAudioPolicy.PRESERVE) }, enabled = state.controlsEnabled) {
            Text(if (state.audioPolicy == VideoBridgeAudioPolicy.PRESERVE) "保留音轨 ✓" else "保留音轨")
        }
        TextButton(onClick = { model.selectAudioPolicy(VideoBridgeAudioPolicy.DISCARD) }, enabled = state.controlsEnabled) {
            Text(if (state.audioPolicy == VideoBridgeAudioPolicy.DISCARD) "丢弃音轨 ✓" else "丢弃音轨")
        }
    }
    Button(onClick = model::process, enabled = state.controlsEnabled && state.inputReference != null,
        modifier = Modifier.testTag("video-process")) { Text("开始 4× 超分") }
    if (!state.controlsEnabled && !state.closed) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        state.progress?.let { Text("已处理 ${it.completedFrames} / ${it.totalFrames ?: "?"} 帧", Modifier.testTag("video-progress")) }
        TextButton(onClick = model::cancel, enabled = state.phase != VideoPhase.CANCELLING,
            modifier = Modifier.testTag("video-cancel")) { Text(if (state.phase == VideoPhase.CANCELLING) "等待处理结束…" else "取消视频任务") }
    }
    Text(state.phase.name, Modifier.testTag("video-phase"))
    state.failure?.let { Text("${it.code} ${it.reason ?: ""}", Modifier.testTag("video-failure"), color = MaterialTheme.colorScheme.error) }
    state.output?.let { output ->
        Text("${output.width}×${output.height} · ${output.frameCount} 帧 · ${output.durationSeconds} 秒 · 音轨 ${output.audioTrackCount}", Modifier.testTag("video-output"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { actions?.openVideo(output.reference) }, enabled = actions != null) { Text("播放结果") }
            TextButton(onClick = { actions?.shareVideo(output.reference) }, enabled = actions != null) { Text("分享结果") }
            TextButton(onClick = model::clearOutput) { Text("清理结果") }
        }
    }
    state.releaseFailure?.let {
        Text("清理失败：${it.code}", color = MaterialTheme.colorScheme.error)
        TextButton(onClick = model::retryReleases) { Text("重试清理") }
    }
}
