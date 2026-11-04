package io.ondeviceai.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.*

interface IdentityDemoActions { fun submitLivenessFrame() }

interface IdentityModelSetupDemoActions {
    val busy: Boolean
    val status: String
    val modelReady: Boolean
    val sessionReady: Boolean
    fun importModels()
    fun enableLocalEnrollment()
    fun deleteLocalEnrollment()
}

@Composable
fun FaceScreen(model: FaceViewModel, actions: DemoActions, identity: IdentityViewModel? = null,
    identityActions: IdentityDemoActions? = null, speech: SpeechViewModel? = null,
    speechActions: SpeechDemoActions? = null, video: VideoViewModel? = null,
    videoActions: VideoDemoActions? = null, multimodal: MultimodalViewModel? = null,
    multimodalActions: MultimodalDemoActions? = null,
    identitySetup: IdentityModelSetupDemoActions? = null,
    modifier: Modifier = Modifier.heightIn(max = 380.dp),
    onFacePageVisibilityChanged: (Boolean) -> Unit = {}) {
    val state = model.state
    val availability = model.bridge.availability()
    var identityVisible by remember { mutableStateOf(false) }
    var speechVisible by remember { mutableStateOf(false) }
    var videoVisible by remember { mutableStateOf(false) }
    var multimodalVisible by remember(multimodal) { mutableStateOf(false) }
    // Read during composition so page changes also re-run the host notification.
    val facePageVisible = !speechVisible && !videoVisible && !multimodalVisible
    SideEffect { onFacePageVisibilityChanged(facePageVisible) }
    fun leaveSpeech() { if (speechVisible) { speech?.leavePage(); speechActions?.leavePage() }; speechVisible = false }
    fun leaveVideo() { if (videoVisible) { video?.leavePage(); videoActions?.leavePage() }; videoVisible = false }
    fun leaveMultimodal() {
        if (multimodalVisible) { multimodal?.leavePage(); multimodalActions?.leaveMultimodalPage() }
        multimodalVisible = false
    }
    DisposableEffect(speech) { onDispose { speech?.leavePage(); speechActions?.leavePage() } }
    DisposableEffect(video) { onDispose { video?.leavePage(); videoActions?.leavePage() } }
    DisposableEffect(multimodal, multimodalActions) { onDispose { multimodal?.leavePage(); multimodalActions?.leaveMultimodalPage() } }
    MaterialTheme {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OnDevice AI Kit", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { leaveSpeech(); leaveVideo(); leaveMultimodal(); identityVisible = false },
                    modifier = Modifier.testTag("face-tab")) { Text("人脸检测") }
                if (identity != null) TextButton(onClick = { leaveSpeech(); leaveVideo(); leaveMultimodal(); identityVisible = true }) { Text("身份与活体") }
                if (speech != null) TextButton(onClick = {
                    leaveVideo(); leaveMultimodal(); actions.stopCamera(); model.cancel(); identity?.cancel(); identityVisible = false; speechVisible = true
                }, modifier = Modifier.testTag("speech-tab")) { Text("语音") }
                if (video != null) TextButton(onClick = {
                    leaveSpeech(); leaveMultimodal(); actions.stopCamera(); model.cancel(); identity?.cancel(); identityVisible = false; videoVisible = true
                }, modifier = Modifier.testTag("video-tab")) { Text("视频") }
            }
            if (multimodal != null) TextButton(onClick = {
                leaveSpeech(); leaveVideo(); actions.stopCamera(); model.cancel(); identity?.cancel(); identityVisible = false; multimodalVisible = true
            }, modifier = Modifier.testTag("multimodal-tab")) { Text("图像理解") }
            if (multimodalVisible && multimodal != null) {
                MultimodalScreen(multimodal, multimodalActions)
            } else if (speechVisible && speech != null) {
                SpeechScreen(speech, speechActions)
            } else if (videoVisible && video != null) {
                VideoScreen(video, videoActions)
            } else {
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
            if (identityVisible) identity?.let { IdentityControls(it, identityActions, identitySetup) }
            }
        }
    }
}

@Composable
private fun IdentityControls(model: IdentityViewModel, actions: IdentityDemoActions?, setup: IdentityModelSetupDemoActions?) {
    val state = model.state
    var backendId by remember { mutableStateOf("") }
    fun selection(): IdentityBackendSelection = backendId.trim().let {
        if (it.isEmpty()) IdentityBackendSelection.Default else IdentityBackendSelection.Explicit(it)
    }
    HorizontalDivider()
    setup?.let {
        Text(it.status)
        Text("本机原型：仅注册本人或获授权图片；特征加密保存。验证、识别和活体尚未校准。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = it::importModels, enabled = !it.busy && !state.busy && !it.sessionReady) { Text("导入身份模型") }
            OutlinedButton(onClick = it::enableLocalEnrollment, enabled = it.modelReady && !it.busy && !it.sessionReady && !state.busy) { Text("同意并启用本机注册") }
        }
        if (it.sessionReady) TextButton(onClick = it::deleteLocalEnrollment, enabled = !it.busy) { Text("删除本次所有注册并停用") }
    }
    Text(identityAvailabilityText(model.availability()))
    Text(if (model.configured) "授权配置已加载" else "需要授权配置")
    Text(if (model.inputReady) "身份输入已选择" else "注册、验证和识别需要选择图片")
    OutlinedTextField(value = backendId, onValueChange = { backendId = it },
        label = { Text("后端 ID（留空使用默认）") }, singleLine = true,
        enabled = !state.busy, modifier = Modifier.fillMaxWidth())
    if (model.managementEnabled) {
        Text("本次会话 ${model.candidates.size} 位用户 · ${model.candidates.sumOf { it.templates.size }} 个模板")
        Text("切换用户后请重新选择获授权的图片。编号仅在本次会话内有效。")
        model.candidates.forEach { candidate ->
            OutlinedButton(onClick = { model.selectCandidateConfigured(candidate.reference) },
                enabled = !state.busy && candidate.reference != model.selectedCandidate) {
                Text("用户 ${candidate.number} · ${candidate.templates.size} 个模板" +
                    if (candidate.reference == model.selectedCandidate) "（当前）" else "")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model::createCandidateConfigured, enabled = !state.busy) { Text("同意新增用户") }
            OutlinedButton(onClick = model::deleteCandidateConfigured,
                enabled = !state.busy && !model.candidateDeletionPending && !model.deletionRecoveryRequired && model.selectedCandidate != null) { Text("删除当前用户") }
        }
        Text(if (model.publishedGallery == null) "尚未发布图库；发布需要已校准的识别策略"
            else "当前图库：${model.galleryTemplateCount} 个模板（固定快照）；更新前请先删除图库")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.publishGalleryConfigured(selection()) },
                enabled = !state.busy && !model.candidateDeletionPending && !model.deletionRecoveryRequired && model.publishedGallery == null &&
                    model.candidates.any { it.templates.isNotEmpty() }) { Text("发布模板图库") }
            OutlinedButton(onClick = model::deleteGalleryConfigured,
                enabled = !state.busy && model.publishedGallery != null) { Text("删除当前图库") }
        }
    }
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
    state.deletion?.let { status ->
        Text(when (status) {
            IdentityDeletionStatus.PURGED -> "删除：物理清理已完成"
            IdentityDeletionStatus.PURGE_FAILED -> "物理清理失败；当前会话无法恢复，用户删除尚未完成"
            else -> "删除：已撤销，物理清理仍在等待"
        })
        if (status != IdentityDeletionStatus.PURGED && status != IdentityDeletionStatus.PURGE_FAILED)
            TextButton(onClick = model::refreshDeletion, enabled = !state.busy) { Text("刷新删除进度") }
    }
    if (model.deletionRecoveryRequired && state.deletion != IdentityDeletionStatus.PURGE_FAILED) {
        Text("物理清理失败；当前会话无法恢复，用户删除尚未完成")
    }
    if (model.candidateDeletionPending && !model.deletionRecoveryRequired && state.deletion == null && !state.busy) {
        Text("用户删除正在等待图库清理，刷新后继续完成删除")
        TextButton(onClick = model::refreshDeletion) { Text("刷新删除进度") }
    }
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
