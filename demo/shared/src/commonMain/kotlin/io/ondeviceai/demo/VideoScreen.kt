package io.ondeviceai.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.*

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun VideoScreen(
    model: VideoViewModel,
    actions: VideoDemoActions?,
    canSaveRecord: () -> Boolean = { false },
    onSaveRecord: () -> Unit = {},
) {
    val state = model.state
    var backendId by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.refreshBackends() }
    val setup = actions as? VideoModelSetupDemoActions

    // 1. Input: selection / removal plus the real input requirements, on the main path.
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("视频文件", style = MaterialTheme.typography.titleMedium)
            Text(state.inputDisplayName ?: "尚未选择视频", Modifier.testTag("video-input"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions?.selectVideoFile() },
                    enabled = state.controlsEnabled && actions != null,
                    modifier = Modifier.testTag("video-select")) { Text("选择视频") }
                TextButton(onClick = { actions?.clearVideoFile() },
                    enabled = state.controlsEnabled && state.inputReference != null) { Text("移除输入") }
            }
            // The active input profile comes from the Host when supplied: Android's
            // CPU profile differs materially from Apple's, so never present it as
            // Apple-only. Use the accurate Apple profile only as a neutral fallback.
            val profileDescription = setup?.videoProfileDescription
            Text(
                if (profileDescription != null) "当前要求：$profileDescription"
                else "当前要求：Apple 视频超分后端限 iOS 26+ 支持的物理设备；320×180 · 24 fps · 1–48 帧 · BT.709 SDR，4× 输出；音轨保留需单条等长 AAC。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (setup?.canImportVideoModel == true) {
                OutlinedButton(onClick = setup::importVideoModel, enabled = state.controlsEnabled) { Text("导入超分模型") }
            }
            if (state.importing) Text("正在导入模型…", color = MaterialTheme.colorScheme.primary)
        }
    }

    Spacer(Modifier.height(12.dp))

    // 2. Processing settings: 4x output dimensions read-only, audio policy selectable.
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("处理设置", style = MaterialTheme.typography.titleMedium)
            Text("输出尺寸（4×，只读）：${state.output?.let { "${it.width}×${it.height}" } ?: "由后端按实际输入回报"}",
                style = MaterialTheme.typography.bodyMedium)
            Text("音轨", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.selectAudioPolicy(VideoBridgeAudioPolicy.PRESERVE) }, enabled = state.controlsEnabled) {
                    Text(if (state.audioPolicy == VideoBridgeAudioPolicy.PRESERVE) "保留 PRESERVE ✓" else "保留 PRESERVE")
                }
                TextButton(onClick = { model.selectAudioPolicy(VideoBridgeAudioPolicy.DISCARD) }, enabled = state.controlsEnabled) {
                    Text(if (state.audioPolicy == VideoBridgeAudioPolicy.DISCARD) "丢弃 DISCARD ✓" else "丢弃 DISCARD")
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // 3. Run / progress / cancel, raw phase and the actual error. Never gated by an
    //    invented availability flag; enabling follows the ViewModel's controlsEnabled.
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = model::process, enabled = state.controlsEnabled && state.inputReference != null,
                modifier = Modifier.testTag("video-process")) {
                Text(if (state.output != null) "重新 4× 超分" else "开始 4× 超分")
            }
            if ((state.phase == VideoPhase.RUNNING || state.phase == VideoPhase.CANCELLING) && !state.closed) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                state.progress?.let {
                    Text("已处理 ${it.completedFrames} / ${it.totalFrames ?: "?"} 帧", Modifier.testTag("video-progress"))
                }
                TextButton(onClick = model::cancel, enabled = state.phase != VideoPhase.CANCELLING,
                    modifier = Modifier.testTag("video-cancel")) {
                    Text(if (state.phase == VideoPhase.CANCELLING) "等待处理结束…" else "取消视频任务")
                }
            }
            Text(state.phase.name, Modifier.testTag("video-phase"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.failure?.let {
                Text("${it.code} ${it.reason ?: ""}", Modifier.testTag("video-failure"), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Critical query / backend unavailability and recovery stay on the main path.
    if (state.queryFailure != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("后端状态查询失败，当前可能无法处理。", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::refreshBackends, enabled = !state.refreshing) {
                    Text(if (state.refreshing) "重试查询中…" else "重试查询后端")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Main-path backend / model state after a completed refresh, derived only from
    // the reported descriptors. This also covers a query that succeeded but whose
    // descriptors are all unavailable. Never guess a selected backend or gate
    // processing on this UI: raw failure codes stay visible and strict backend IDs
    // remain folded in Advanced.
    if (!state.refreshing && state.queryFailure == null && state.backends.isNotEmpty() &&
        state.backends.all { !it.availability.available }) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前没有可用的视频超分后端。", color = MaterialTheme.colorScheme.error)
                state.backends.mapNotNull { it.availability.reason }.distinct().forEach { reason ->
                    Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = model::refreshBackends, enabled = !state.refreshing) {
                        Text("重试查询后端")
                    }
                    if (setup?.canImportVideoModel == true) {
                        OutlinedButton(onClick = setup::importVideoModel, enabled = state.controlsEnabled) {
                            Text("导入超分模型")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (state.refreshing) {
        Text("正在查询可用后端…", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }

    // 4. Result output: playback / share / save / explicit cleanup, only with a real output.
    state.output?.let { output ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("处理结果", style = MaterialTheme.typography.titleMedium)
                Text("${output.width}×${output.height} · ${output.frameCount} 帧 · ${output.durationSeconds} 秒 · 音轨 ${output.audioTrackCount}",
                    Modifier.testTag("video-output"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { actions?.openVideo(output.reference) }, enabled = actions != null) { Text("播放结果") }
                    TextButton(onClick = { actions?.shareVideo(output.reference) }, enabled = actions != null) { Text("分享结果") }
                    if (canSaveRecord()) {
                        TextButton(onClick = onSaveRecord, modifier = Modifier.testTag("save-record")) { Text("保存记录") }
                    }
                    TextButton(onClick = model::clearOutput) { Text("清理结果") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    state.releaseFailure?.let {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("清理失败：${it.code}", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::retryReleases) { Text("重试清理") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // 5. Advanced (collapsed by default): strict backend ID/default/refresh/descriptors
    //    and raw diagnostics. Backend IDs are never exposed before this is expanded.
    TextButton(onClick = { advanced = !advanced }, modifier = Modifier.testTag("video-advanced-toggle")) {
        Text(if (advanced) "收起高级设置与支持范围" else "高级设置与支持范围")
    }
    if (advanced) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                setup?.videoProfileDescription?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.selectBackend(VideoBridgeSelection.Default) }, enabled = state.controlsEnabled) {
                        Text("使用默认后端")
                    }
                    TextButton(onClick = model::refreshBackends, enabled = !state.refreshing) {
                        Text(if (state.refreshing) "刷新中…" else "刷新后端")
                    }
                }
                OutlinedTextField(backendId,
                    { value -> backendId = value; if (value.isNotBlank()) model.selectBackend(VideoBridgeSelection.Explicit(value.trim())) },
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
            }
        }
    }
}
