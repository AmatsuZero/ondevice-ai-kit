package io.ondeviceai.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.VoiceEffectsBridgePreset

@Composable
fun VoiceEffectsScreen(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions) {
    val state = model.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("离线变声", style = MaterialTheme.typography.headlineSmall)
        Text("最长 30 秒。导入单声道 PCM16 WAV（16 / 44.1 / 48 kHz），或点击录音。升调 / 降调为 ±4 半音。")
        Text(state.inputName ?: "尚未选择音频", Modifier.testTag("voice-input"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions::selectAudioFile, enabled = state.controlsEnabled,
                modifier = Modifier.testTag("voice-import")) { Text("导入 WAV") }
            if (state.recording) Button(onClick = actions::stopRecording, modifier = Modifier.testTag("voice-stop-record")) { Text("停止录音") }
            else OutlinedButton(onClick = actions::startRecording, enabled = state.controlsEnabled,
                modifier = Modifier.testTag("voice-record")) { Text("开始录音") }
        }
        if (state.importing) Text("正在准备音频…")
        if (state.recording) Text("录音中，到 30 秒自动停止")
        Row {
            VoiceEffectsBridgePreset.entries.forEach { preset ->
                TextButton(onClick = { model.selectPreset(preset) }, enabled = state.controlsEnabled) {
                    val label = when (preset) { VoiceEffectsBridgePreset.ORIGINAL -> "原声"; VoiceEffectsBridgePreset.HIGH -> "升调"; VoiceEffectsBridgePreset.LOW -> "降调" }
                    Text(label + if (state.preset == preset) " ✓" else "")
                }
            }
        }
        Button(onClick = model::process, enabled = state.controlsEnabled && state.inputReference != null,
            modifier = Modifier.testTag("voice-process")) { Text("转换") }
        if (state.phase == VoiceEffectsPhase.RUNNING || state.phase == VoiceEffectsPhase.CANCELLING) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = model::cancel, modifier = Modifier.testTag("voice-cancel")) {
                Text(if (state.phase == VoiceEffectsPhase.CANCELLING) "等待清理（点击可重试）" else "取消转换")
            }
        }
        Text(when (state.phase) {
            VoiceEffectsPhase.IDLE -> "就绪"; VoiceEffectsPhase.RUNNING -> "转换中"; VoiceEffectsPhase.CANCELLING -> "正在取消并释放资源"
            VoiceEffectsPhase.SUCCEEDED -> "转换完成"; VoiceEffectsPhase.FAILED -> "操作失败"; VoiceEffectsPhase.CANCELLED -> "已取消"
        }, Modifier.testTag("voice-phase"))
        TextButton(onClick = actions::playOriginal, enabled = state.controlsEnabled && state.inputReference != null) { Text("试听归一化原声") }
        state.output?.let { output ->
            Text("${output.sampleRate} Hz · ${output.frameCount} 帧 · ${output.durationSeconds} 秒", Modifier.testTag("voice-output"))
            Row {
                TextButton(onClick = { actions.playResult(output.reference) }, enabled = state.controlsEnabled) { Text("试听结果") }
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
        TextButton(onClick = actions::leavePage) { Text("返回") }
    }
}
