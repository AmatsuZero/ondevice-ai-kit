package io.ondeviceai.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
    val waves = remember { mutableStateMapOf<String, VoiceWaveform>() }
    if (state.referenceMode) {
        listOfNotNull(state.referenceReference, state.inputReference, state.output?.reference)
            .distinct()
            .forEach { reference -> key(reference) { WaveLoader(reference, actions, waves) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !state.referenceMode, enabled = state.controlsEnabled,
            onClick = { model.setReferenceMode(false) }, modifier = Modifier.testTag("voice-submode-fixed"),
            label = { Text("固定音色") })
        FilterChip(selected = state.referenceMode, enabled = state.controlsEnabled && model.hasTimbres,
            onClick = { model.setReferenceMode(true) }, modifier = Modifier.testTag("voice-submode-reference"),
            label = { Text("参考音色") })
    }
    if (state.referenceMode) {
        ReferenceVoicePanel(model = model, actions = actions, waves = waves)
    } else {
        FixedVoiceInputPanel(model = model, actions = actions)
    }
    VoiceStatus(model = model, actions = actions,
        showBackends = state.referenceMode || (model.hasCoreMLTimbres && state.effect.pitchPreset == null),
        resultTag = if (state.referenceMode) "voice-reference-result" else "voice-output")
}

@Composable
private fun WaveLoader(reference: String, actions: VoiceEffectsDemoActions,
                       waves: MutableMap<String, VoiceWaveform>) {
    DisposableEffect(reference) {
        var retired = false
        actions.readVoiceWave(reference, object : VoiceWaveReply {
            override fun ready(wave: VoiceWaveform?) {
                if (!retired && wave != null) waves[reference] = wave
            }
        })
        onDispose {
            retired = true
            waves.remove(reference)
        }
    }
}

@Composable
private fun FixedVoiceInputPanel(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions) {
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
    TextButton(onClick = actions::playOriginal, enabled = state.controlsEnabled && state.inputReference != null,
        modifier = Modifier.testTag("voice-content-play")) { Text("试听原声") }
}

@Composable
private fun ReferenceVoicePanel(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions,
                                waves: Map<String, VoiceWaveform>) {
    val state = model.state
    Box(Modifier.fillMaxWidth().background(Color(0xFF1B4332), RoundedCornerShape(12.dp)).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("参考音色", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFA7F3D0))
            Text("先录参考音色，再录要说的话。", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA7F3D0))
            Button(onClick = actions::loadReferenceExample, enabled = state.controlsEnabled,
                modifier = Modifier.testTag("voice-reference-example"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA7F3D0), contentColor = Color(0xFF1B4332))) { Text("载入样例") }
        }
    }
    NumberedCard(number = "01", title = "想变成谁的声音") {
        VoiceInputCard(
            title = "参考音频", name = state.referenceName, reference = state.referenceReference,
            wave = waves[state.referenceReference], referenceSlot = true, actions = actions,
            importTag = "voice-reference-import", recordTag = "voice-reference-record",
            stopTag = "voice-reference-stop", playTag = "voice-reference-play", clearTag = "voice-reference-clear",
            onImport = actions::selectReferenceAudioFile, onRecord = actions::startReferenceRecording,
            onStop = actions::stopReferenceRecording,
            recording = state.referenceRecording, importing = state.referenceImporting, enabled = state.controlsEnabled, poll = state.mediaBusy)
    }
    NumberedCard(number = "02", title = "想说些什么") {
        VoiceInputCard(
            title = "内容音频", name = state.inputName, reference = state.inputReference,
            wave = waves[state.inputReference], referenceSlot = false, actions = actions,
            importTag = "voice-content-import", recordTag = "voice-content-record",
            stopTag = "voice-content-stop", playTag = "voice-content-play", clearTag = "voice-content-clear",
            onImport = actions::selectAudioFile, onRecord = actions::startRecording, onStop = actions::stopRecording,
            recording = state.recording, importing = state.importing, enabled = state.controlsEnabled, poll = state.mediaBusy)
    }
    NumberedCard(number = "03", title = "听听变化") {
        Button(onClick = model::process,
            enabled = state.controlsEnabled && state.referenceReference != null && state.inputReference != null,
            modifier = Modifier.testTag("voice-reference-process")) { Text("按参考音色转换") }
        VoiceComparison(
            inputReference = state.inputReference, content = waves[state.inputReference],
            outputReference = state.output?.reference, output = state.output?.let { waves[it.reference] },
            actions = actions, enabled = state.controlsEnabled || state.mediaBusy, poll = state.mediaBusy)
    }
}

@Composable
private fun NumberedCard(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$number $title", style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun VoiceInputCard(
    title: String, name: String?, reference: String?, wave: VoiceWaveform?, referenceSlot: Boolean,
    actions: VoiceEffectsDemoActions, importTag: String, recordTag: String, stopTag: String,
    playTag: String, clearTag: String, onImport: () -> Unit, onRecord: () -> Unit, onStop: () -> Unit,
    recording: Boolean, importing: Boolean, enabled: Boolean, poll: Boolean,
) {
    val shape = RoundedCornerShape(8.dp)
    val buttons = Modifier.height(48.dp)
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(name ?: "请选择音频", Modifier.testTag("${importTag}-name"))
    Text("支持 0.5–30 秒的单声道 PCM16 WAV（16 / 44.1 / 48 kHz）。音频仅在本机处理，不会上传。", style = MaterialTheme.typography.bodySmall)
    if (importing) Text("正在准备音频…")
    if (recording) Text("录音中，到 30 秒自动停止")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onImport, enabled = enabled, shape = shape,
            modifier = buttons.weight(1f).testTag(importTag),
            contentPadding = PaddingValues(4.dp)) { Text("导入 WAV", maxLines = 1, softWrap = false) }
        if (recording) OutlinedButton(onClick = onStop, shape = shape,
            modifier = buttons.weight(1f).testTag(stopTag),
            contentPadding = PaddingValues(4.dp)) { Text("停止录音", maxLines = 1, softWrap = false) }
        else OutlinedButton(onClick = onRecord, enabled = enabled, shape = shape,
            modifier = buttons.weight(1f).testTag(recordTag),
            contentPadding = PaddingValues(4.dp)) { Text("录制声音", maxLines = 1, softWrap = false) }
        OutlinedButton(onClick = { actions.clearVoiceInput(referenceSlot) },
            enabled = enabled && reference != null, shape = shape,
            modifier = buttons.weight(1f).testTag(clearTag),
            contentPadding = PaddingValues(4.dp)) { Text("清空", maxLines = 1, softWrap = false) }
    }
    VoiceWaveView(reference = reference, wave = wave, actions = actions,
        tag = "${importTag}-wave", playTag = playTag, label = title,
        enabled = enabled || poll, poll = poll)
}

@Composable
private fun VoiceWaveView(
    reference: String?, wave: VoiceWaveform?, actions: VoiceEffectsDemoActions,
    tag: String, label: String, maxDuration: Double = wave?.durationSeconds ?: 0.0,
    color: Color = Color(0xFF2E7D32), enabled: Boolean = true, poll: Boolean = false,
    showControls: Boolean = true, playTag: String = "",
) {
    var fraction by remember(reference) { mutableStateOf(-1f) }
    LaunchedEffect(reference, poll) {
        if (reference == null || !poll) {
            fraction = -1f
            return@LaunchedEffect
        }
        while (true) {
            fraction = actions.playbackFraction(reference)
            delay(80)
        }
    }
    if (reference == null || wave == null || wave.peaks.isEmpty()) return
    fun seek(x: Float, width: Float) {
        if (width > 0f) {
            val fraction = ((x / width) * maxDuration / wave.durationSeconds).toFloat().coerceIn(0f, 1f)
            actions.playVoiceAt(reference, fraction)
        }
    }
    Text("$label ${formatSeconds(wave.durationSeconds)} 秒", Modifier.testTag("${tag}-duration"),
        style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(56.dp).testTag(tag)
        .semantics(mergeDescendants = true) { contentDescription = "$label 波形，点击或拖动可定位播放" }
        .pointerInput(reference, enabled, maxDuration, wave.durationSeconds) {
            if (!enabled) return@pointerInput
            detectTapGestures { offset -> seek(offset.x, size.width.toFloat()) }
        }
        .pointerInput(reference, enabled, maxDuration, wave.durationSeconds) {
            if (!enabled) return@pointerInput
            var dragX = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, _ -> dragX = change.position.x },
                onDragEnd = { seek(dragX, size.width.toFloat()) }
            )
        }) {
        val w = size.width
        val h = size.height
        val peaks = wave.peaks
        val span = w * (wave.durationSeconds / maxDuration.coerceAtLeast(0.000001)).toFloat().coerceIn(0f, 1f)
        val barW = (span / peaks.size).coerceAtLeast(0.01f)
        val mid = h / 2f
        drawLine(color, Offset(0f, mid), Offset(span, mid), strokeWidth = 1f)
        peaks.forEachIndexed { i, peak ->
            val db = (20.0 * kotlin.math.log10(peak.coerceAtLeast(1e-6f).toDouble())).coerceIn(-60.0, 0.0)
            val norm = ((db + 60.0) / 60.0).toFloat()
            val amp = norm * mid * 0.92f
            val cx = (i * barW + barW / 2f).coerceAtMost(span)
            drawLine(color, Offset(cx, mid - amp), Offset(cx, mid + amp), strokeWidth = barW.coerceAtMost(3f))
        }
        if (fraction in 0f..1f) {
            val cx = fraction * span
            drawLine(Color.Red, Offset(cx, 0f), Offset(cx, h), strokeWidth = 2f)
        }
    }
    if (showControls) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { actions.playVoiceAt(reference, 0f) }, enabled = enabled,
                modifier = Modifier.testTag(playTag).semantics { contentDescription = "播放$label" }) { Text("播放") }
            if (fraction in 0f..1f) {
                TextButton(onClick = actions::stopPlayback,
                    modifier = Modifier.testTag("${tag}-stop").semantics { contentDescription = "停止播放$label" }) { Text("停止") }
            }
        }
    }
}

@Composable
private fun VoiceComparison(
    inputReference: String?, content: VoiceWaveform?,
    outputReference: String?, output: VoiceWaveform?,
    actions: VoiceEffectsDemoActions, enabled: Boolean, poll: Boolean,
) {
    if (outputReference == null || output == null) return
    val duration = maxOf(content?.durationSeconds ?: 0.0, output.durationSeconds)
    if (duration <= 0.0) return
    Text("原声与结果对比（−60..0 dBFS）", style = MaterialTheme.typography.titleSmall)
    VoiceWaveView(reference = inputReference, wave = content, actions = actions,
        tag = "voice-compare-content", label = "原声", maxDuration = duration,
        color = Color.Gray, enabled = enabled, poll = poll, showControls = false)
    VoiceWaveView(reference = outputReference, wave = output, actions = actions,
        tag = "voice-compare-output", label = "转换后", maxDuration = duration,
        color = Color(0xFF2E7D32), enabled = enabled, poll = poll, showControls = false)
}

private fun formatSeconds(value: Double): String {
    val tenths = (value * 10).toLong()
    return "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun VoiceStatus(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions, showBackends: Boolean,
                        resultTag: String = "voice-output") {
    val state = model.state
    if (state.phase == VoiceEffectsPhase.RUNNING || state.phase == VoiceEffectsPhase.CANCELLING) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = model::cancel, modifier = Modifier.testTag("voice-cancel")) {
            Text(if (state.phase == VoiceEffectsPhase.CANCELLING) "等待清理（点击可重试）" else "取消转换")
        }
    }
    Text(when (state.phase) {
        VoiceEffectsPhase.IDLE -> "就绪"
        VoiceEffectsPhase.RUNNING -> if (state.referenceMode) "正在按参考音色转换…" else "正在本机转换为${state.effect.label}…"
        VoiceEffectsPhase.CANCELLING -> "正在取消并释放资源"
        VoiceEffectsPhase.SUCCEEDED -> if (state.referenceMode) "参考音色转换完成" else "${state.effect.label}转换完成"
        VoiceEffectsPhase.FAILED -> "操作失败"
        VoiceEffectsPhase.CANCELLED -> "已取消"
    }, Modifier.testTag("voice-phase"))
    state.output?.let { output ->
        if (showBackends) {
            Text(voiceBackendLabel(output.backendId), Modifier.testTag("voice-backend-result"))
        }
        Text("${output.sampleRate} Hz · ${output.frameCount} 帧 · ${formatSeconds(output.durationSeconds)} 秒", Modifier.testTag(resultTag))
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
