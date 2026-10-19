package io.ondeviceai.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.*

interface IdentityDemoActions { fun submitLivenessFrame() }

@Composable
fun FaceScreen(model: FaceViewModel, actions: DemoActions, identity: IdentityViewModel? = null,
    identityActions: IdentityDemoActions? = null) {
    val state = model.state
    val availability = model.bridge.availability()
    var identityVisible by remember { mutableStateOf(false) }
    MaterialTheme {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OnDevice AI Kit", style = MaterialTheme.typography.headlineSmall)
            if (identity != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { identityVisible = false }) { Text("人脸检测") }
                TextButton(onClick = { identityVisible = true }) { Text("身份与活体") }
            }
            Text(availability.backendId + if (availability.available) " · offline" else " · " + availability.reason)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.selectPhoto() }, enabled = state.canSelectInput) { Text("选择图片") }
                Button(onClick = { actions.startCamera() }) { Text("相机") }
                TextButton(onClick = { actions.stopCamera(); model.cancel() }) { Text("停止") }
            }
            if (!identityVisible) {
                if (state.showProgress) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { actions.stopCamera(); model.cancel() }) { Text("取消") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.streaming || state.result != null) {
                    Text(state.result?.let { "人脸 ${it.faces.size} · ${it.elapsedMs.toInt()} ms · ${it.width}×${it.height}" } ?: "等待首帧结果")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.runBenchmark() }, enabled = state.canSelectInput) { Text("Benchmark") }
                    state.result?.benchmarkJson?.let { json -> TextButton(onClick = { actions.exportReport(json) }) { Text("导出报告") } }
                }
            }
            if (identityVisible) identity?.let { IdentityControls(it, identityActions) }
        }
    }
}

@Composable
private fun IdentityControls(model: IdentityViewModel, actions: IdentityDemoActions?) {
    val state = model.state
    var backendId by remember { mutableStateOf("") }
    fun selection(): IdentityBackendSelection = backendId.trim().let {
        if (it.isEmpty()) IdentityBackendSelection.Default else IdentityBackendSelection.Explicit(it)
    }
    HorizontalDivider()
    Text(identityAvailabilityText(model.availability()))
    Text(if (model.configured) "授权配置已加载" else "需要授权配置")
    Text(if (model.inputReady) "身份输入已选择" else "注册、验证和识别需要选择图片")
    OutlinedTextField(value = backendId, onValueChange = { backendId = it },
        label = { Text("后端 ID（留空使用默认）") }, singleLine = true,
        enabled = !state.busy, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { model.enrollConfigured(selection()) }, enabled = !state.busy) { Text("注册") }
        OutlinedButton(onClick = { model.verifyConfigured(selection()) }, enabled = !state.busy) { Text("验证") }
        OutlinedButton(onClick = { model.identifyConfigured(selection()) }, enabled = !state.busy) { Text("识别") }
        OutlinedButton(onClick = { model.openLivenessConfigured(selection()) }, enabled = !state.busy) { Text("活体") }
    }
    if (state.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.liveness != null) {
                TextButton(onClick = { actions?.submitLivenessFrame() }, enabled = actions != null) { Text("提交当前帧") }
                TextButton(onClick = { model.finishLiveness() }) { Text("结束采集") }
            }
            TextButton(onClick = { model.cancel() }) { Text("取消身份任务") }
        }
    }
    state.error?.let { Text("身份：${identityFailureText(it)}", color = MaterialTheme.colorScheme.error) }
    state.enrollment?.let { Text("注册结果：${it.outcome} · ${it.metadata.backendId}") }
    state.verification?.let { Text("验证结果：${it.outcome} · ${it.metadata.backendId}") }
    state.identification?.let { Text("识别结果：${it.outcome}，候选 ${it.candidates.size} · ${it.metadata.backendId}") }
    state.liveness?.let { Text("活体：${livenessEventText(it)}") }
}

private fun identityAvailabilityText(value: IdentityBridgeAvailability): String = when (value) {
    is IdentityBridgeAvailability.Available -> "默认后端 ${value.backendId}"
    is IdentityBridgeAvailability.Unavailable -> "默认后端：${value.reason}"
}

private fun identityFailureText(value: IdentityBridgeFailure): String = when (value) {
    is IdentityBridgeFailure.Unavailable -> "不可用（${value.reason}）"
    IdentityBridgeFailure.Cancelled -> "已取消"
    is IdentityBridgeFailure.Rejected -> "拒绝（${value.code}）"
}

private fun livenessEventText(value: IdentityLivenessEvent): String = when (value) {
    IdentityLivenessEvent.Started -> "已开始"
    is IdentityLivenessEvent.ChallengeIssued -> "挑战已发出"
    IdentityLivenessEvent.FrameAccepted -> "已接收帧"
    IdentityLivenessEvent.FrameDropped -> "已丢弃积压帧"
    IdentityLivenessEvent.FrameStale -> "帧已过期"
    is IdentityLivenessEvent.Completed -> "${value.result.outcome} · ${value.result.metadata.backendId}"
    is IdentityLivenessEvent.Failed -> "失败（${value.code}）"
    IdentityLivenessEvent.Cancelled -> "已取消"
    IdentityLivenessEvent.Closed -> "已关闭"
}
