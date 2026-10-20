package io.ondeviceai.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.*

@Composable
fun SpeechScreen(model: SpeechViewModel, actions: SpeechDemoActions?) {
    val state = model.state
    var locale by remember { mutableStateOf("en-US") }
    var text by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(model) {
        model.refreshBackends(SpeechBridgeCapability.TTS)
        model.refreshBackends(SpeechBridgeCapability.ASR)
    }
    Text("语音 · 本机处理", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(locale, { model.cancel(); locale = it }, label = { Text("语言 / locale") },
        modifier = Modifier.fillMaxWidth().testTag("speech-locale"), singleLine = true,
        enabled = state.controlsEnabled)
    BackendControls("TTS", state.tts, state.ttsSelection, state.controlsEnabled,
        { model.selectBackend(SpeechBridgeCapability.TTS, it) }, { model.refreshBackends(SpeechBridgeCapability.TTS) })
    OutlinedTextField(text, { model.cancel(); text = it }, label = { Text("朗读文本") },
        modifier = Modifier.fillMaxWidth().testTag("speech-tts-text"), enabled = state.controlsEnabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
    OutlinedTextField(voice, { model.cancel(); voice = it }, label = { Text("voice ID（可选）") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = state.controlsEnabled)
    Button(onClick = { model.speak(SpeechBridgeTtsRequest(text, locale, voice.takeIf { it.isNotBlank() })) },
        enabled = state.controlsEnabled && text.isNotBlank() && locale.isNotBlank(),
        modifier = Modifier.testTag("speech-speak")) { Text("播放 TTS") }
    HorizontalDivider()
    BackendControls("ASR", state.asr, state.asrSelection, state.controlsEnabled,
        { model.selectBackend(SpeechBridgeCapability.ASR, it) }, { model.refreshBackends(SpeechBridgeCapability.ASR) })
    val inputs = state.asr.backends.flatMap { it.supportedInputs }.toSet()
    if (SpeechBridgeInputKind.AUDIO_FILE in inputs) {
        Text("文件识别 · Apple 系统后端当前语言 en-US")
        Text(state.audioDisplayName ?: "尚未选择音频", Modifier.testTag("speech-audio-name"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions?.selectAudioFile() }, enabled = state.controlsEnabled && actions != null,
                modifier = Modifier.testTag("speech-select-audio")) { Text("选择音频文件") }
            TextButton(onClick = { actions?.clearAudioFile() }, enabled = state.controlsEnabled && state.audioReference != null) { Text("移除") }
        }
        Button(onClick = { state.audioReference?.let { model.recognize(SpeechBridgeAsrRequest(locale, SpeechBridgeInput.AudioFile(it))) } },
            enabled = state.controlsEnabled && state.audioReference != null && locale.isNotBlank(),
            modifier = Modifier.testTag("speech-recognize-file")) { Text("识别音频文件") }
    }
    if (SpeechBridgeInputKind.MICROPHONE in inputs) {
        Text("麦克风识别 · 开始后会采集声音，请在安静环境使用；需要录音权限和本机语言资源")
        Button(onClick = { model.recognize(SpeechBridgeAsrRequest(locale, SpeechBridgeInput.Microphone)) },
            enabled = state.controlsEnabled && locale.isNotBlank(), modifier = Modifier.testTag("speech-recognize-mic")) { Text("开始麦克风 ASR") }
    }
    if (inputs.isEmpty()) Text("ASR 输入尚不可用；刷新后端查看原因")
    if (state.canFinishInput) {
        Button(onClick = { model.finishInput() }, modifier = Modifier.testTag("speech-finish-input")) { Text("结束录音并识别") }
    }
    if (state.phase == SpeechPhase.RUNNING || state.phase == SpeechPhase.FINISHING || state.phase == SpeechPhase.CANCELLING) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.phase == SpeechPhase.FINISHING) Text("正在结束录音，等待识别")
        TextButton(onClick = { model.cancel() }, enabled = state.phase != SpeechPhase.CANCELLING,
            modifier = Modifier.testTag("speech-cancel")) { Text(if (state.phase == SpeechPhase.CANCELLING) "等待原生任务释放…" else "取消语音任务") }
    }
    Text(state.phase.name, Modifier.testTag(if (state.phase == SpeechPhase.CANCELLED) "speech-cancelled" else "speech-phase"))
    state.failure?.let {
        Text("${it.code} ${it.reason ?: ""}", Modifier.testTag("speech-failure"), color = MaterialTheme.colorScheme.error)
        if (it.reason?.contains("permission", ignoreCase = true) == true) {
            TextButton(onClick = { actions?.openSpeechSettings() }) { Text("打开语音权限设置") }
        }
    }
    state.ttsResult?.let { Text("TTS 完成 · ${it.backendId} · ${it.voiceId ?: "系统音色"}", Modifier.testTag("speech-tts-result")) }
    state.asrResult?.let {
        Text("ASR 完成 · ${it.backendId}", Modifier.testTag("speech-asr-result"))
        Text(it.transcript)
    }
}

@Composable
private fun BackendControls(label: String, state: SpeechBackendState, selection: SpeechBridgeSelection,
    enabled: Boolean, select: (SpeechBridgeSelection) -> Unit, refresh: () -> Unit) {
    val explicit = selection as? SpeechBridgeSelection.Explicit
    var backendId by remember { mutableStateOf(explicit?.backendId ?: "") }
    Text("$label 后端", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { select(SpeechBridgeSelection.Default) }, enabled = enabled) {
            Text(if (explicit == null) "默认 ✓" else "使用默认")
        }
        TextButton(onClick = refresh, enabled = !state.refreshing) { Text(if (state.refreshing) "刷新中…" else "刷新 $label") }
    }
    OutlinedTextField(backendId, { backendId = it; if (it.isNotBlank()) select(SpeechBridgeSelection.Explicit(it.trim())) },
        label = { Text("显式后端 ID（严格选择）") }, singleLine = true,
        enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("speech-${label.lowercase()}-backend"))
    Text(if (explicit == null) "选择：系统默认" else "选择：${explicit.backendId}")
    state.backends.forEach { backend ->
        Text("${backend.backendId} · ${if (backend.availability.available) "available" else backend.availability.reason ?: "unavailable"}" +
            " · minOS ${backend.availability.minimumOS ?: "—"} · offline 声明 ${backend.offline}")
    }
    state.failure?.let { Text("$label 查询：${it.code} ${it.reason ?: ""}", color = MaterialTheme.colorScheme.error) }
}
