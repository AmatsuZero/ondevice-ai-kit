package io.ondeviceai.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun VoiceEffectsScreen(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions, showBack: Boolean = true) {
    val state = model.state
    val session = (actions as? LiveVoiceOwner)?.liveSession
    var liveMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showBack) Text(if (liveMode) "实时变声" else "离线变声", style = MaterialTheme.typography.headlineSmall)
        if (session != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !liveMode, enabled = !session.busy, onClick = { liveMode = false },
                    modifier = Modifier.testTag("voice-mode-file"), label = { Text("文件变声") })
                FilterChip(selected = liveMode, enabled = state.controlsEnabled, onClick = { liveMode = true },
                    modifier = Modifier.testTag("voice-mode-live"), label = { Text("实时变声") })
            }
        }
        if (liveMode && session != null) {
            LiveVoicePanel(session = session)
        } else {
            FileVoicePanel(model = model, actions = actions)
        }
        if (showBack) TextButton(onClick = actions::leavePage) { Text("返回") }
    }
}

@Composable
private fun FileVoicePanel(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions) {
    val state = model.state
    Text("输入音频", style = MaterialTheme.typography.titleMedium)
    Text(state.inputName ?: "请选择音频", Modifier.testTag("voice-input"))
    Text("支持最长 30 秒的单声道 PCM16 WAV（16 / 44.1 / 48 kHz）。", style = MaterialTheme.typography.bodySmall)
    if (state.exampleReference != null && state.inputReference != state.exampleReference) {
        TextButton(onClick = actions::useExample, enabled = state.controlsEnabled) { Text("使用内置示例") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions::selectAudioFile, enabled = state.controlsEnabled,
            modifier = Modifier.testTag("voice-import")) { Text("导入 WAV") }
        if (state.recording) Button(onClick = actions::stopRecording, modifier = Modifier.testTag("voice-stop-record")) { Text("停止录音") }
        else OutlinedButton(onClick = actions::startRecording, enabled = state.controlsEnabled,
            modifier = Modifier.testTag("voice-record")) { Text("开始录音") }
    }
    if (state.importing) Text("正在准备音频…")
    if (state.recording) Text("录音中，到 30 秒自动停止")
    Text("目标音色", style = MaterialTheme.typography.titleMedium)
    val choices = DemoVoiceEffect.entries.filter { model.hasTimbres || it.pitchPreset != null }
    choices.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { effect ->
                FilterChip(selected = state.effect == effect, enabled = state.controlsEnabled,
                    onClick = { model.selectEffect(effect) }, modifier = Modifier.testTag("voice-effect-${effect.key}"),
                    label = { Text(effect.label) })
            }
        }
    }
    val showBackends = model.hasCoreMLTimbres && state.effect.pitchPreset == null
    if (showBackends) {
        Text("试听后端", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.useCoreML, enabled = state.controlsEnabled,
                onClick = { model.selectCoreML(false) }, modifier = Modifier.testTag("voice-backend-cpu"),
                label = { Text("ONNX · CPU") })
            FilterChip(selected = state.useCoreML, enabled = state.controlsEnabled,
                onClick = { model.selectCoreML(true) }, modifier = Modifier.testTag("voice-backend-coreml"),
                label = { Text("Core ML 试听") })
        }
    }
    Button(onClick = model::process, enabled = state.controlsEnabled && state.inputReference != null,
        modifier = Modifier.testTag("voice-process")) { Text("转换为${state.effect.label}") }
    if (state.phase == VoiceEffectsPhase.RUNNING || state.phase == VoiceEffectsPhase.CANCELLING) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = model::cancel, modifier = Modifier.testTag("voice-cancel")) {
            Text(if (state.phase == VoiceEffectsPhase.CANCELLING) "等待清理（点击可重试）" else "取消转换")
        }
    }
    Text(when (state.phase) {
        VoiceEffectsPhase.IDLE -> "就绪"; VoiceEffectsPhase.RUNNING -> "正在本机转换为${state.effect.label}…"; VoiceEffectsPhase.CANCELLING -> "正在取消并释放资源"
        VoiceEffectsPhase.SUCCEEDED -> "${state.effect.label}转换完成"; VoiceEffectsPhase.FAILED -> "操作失败"; VoiceEffectsPhase.CANCELLED -> "已取消"
    }, Modifier.testTag("voice-phase"))
    TextButton(onClick = actions::playOriginal, enabled = state.controlsEnabled && state.inputReference != null) { Text("试听原声") }
    state.output?.let { output ->
        if (showBackends) {
            Text(voiceBackendLabel(output.backendId), Modifier.testTag("voice-backend-result"))
        }
        Text("${output.sampleRate} Hz · ${output.frameCount} 帧 · ${output.durationSeconds} 秒", Modifier.testTag("voice-output"))
        Row {
            TextButton(onClick = { actions.playResult(output.reference) }, enabled = state.controlsEnabled, modifier = Modifier.testTag("voice-play-result")) { Text("试听结果") }
            TextButton(onClick = { actions.exportAudio(output.reference) }, enabled = state.controlsEnabled,
                modifier = Modifier.testTag("voice-export")) { Text("导出 WAV") }
            TextButton(onClick = model::clearOutput, enabled = state.controlsEnabled) { Text("清理") }
        }
    }
    if (state.mediaBusy) TextButton(onClick = actions::stopPlayback) { Text("停止试听") }
    state.failure?.let { Text("${it.code} ${it.reason ?: ""}", color = MaterialTheme.colorScheme.error) }
    state.releaseFailure?.let {
        Text("输出清理失败，可重试", color = MaterialTheme.colorScheme.error)
        TextButton(onClick = model::retryReleases) { Text("重试清理") }
    }
}

@Composable
private fun LiveVoicePanel(session: RealtimeVoiceSession) {
    DisposableEffect(session) {
        session.refresh()
        onDispose { session.release() }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("目标音色", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LiveRole.UNCLE to "大叔", LiveRole.GIRL to "少女", LiveRole.ANNOUNCER to "播音员").forEach { (r, label) ->
                FilterChip(selected = session.role == r, enabled = !session.busy, onClick = { session.select(r) },
                    modifier = Modifier.testTag("voice-live-role-${r.name}"), label = { Text(label) })
            }
        }
        val available = session.destination != null
        val pressLabel = when (session.destination) {
            LiveDestination.HEADPHONES -> "按住耳返"
            LiveDestination.BUILTIN_SPEAKER -> "按住外放"
            null -> "输出不可用"
        }
        Box(modifier = Modifier.fillMaxWidth()
            .background(if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    session.press()
                    try {
                        tryAwaitRelease()
                    } finally {
                        session.release()
                    }
                })
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (available) "按住说话，松开结束实时变声" else "输出不可用，无法开始实时变声"
            }
            .testTag("voice-live-press"), contentAlignment = Alignment.Center) {
            Text(pressLabel, style = MaterialTheme.typography.titleMedium)
        }
        val status = if (session.busy) "正在实时变声，松开按钮结束" else "就绪，按住按钮说话"
        val suffix = session.message?.let { "（$it）" }.orEmpty()
        Text("状态：$status$suffix", Modifier.testTag("voice-live-status"))
        if (!available) Text("未检测到可用输出设备，请连接耳机或检查扬声器", color = MaterialTheme.colorScheme.error)
    }
}

private fun voiceBackendLabel(backendId: String): String {
    if (backendId == "openvoice.onnx") return "ONNX Runtime · CPU"
    val stages = backendId.removePrefix("openvoice.").split(".")
    fun provider(value: String) = when (value) {
        "coreml" -> "Core ML"
        "mixed" -> "Core ML + CPU"
        "cpu" -> "CPU"
        else -> "未确认"
    }
    if (stages.size != 2) return "推理后端未确认"
    return "声纹提取 · ${provider(stages[0])}　音色转换 · ${provider(stages[1])}"
}
