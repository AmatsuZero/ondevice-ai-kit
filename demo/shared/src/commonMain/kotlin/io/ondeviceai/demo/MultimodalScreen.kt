package io.ondeviceai.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.*

/** Display and user actions only. Input acquisition and model ownership belong to the host. */
@Composable
fun MultimodalScreen(model: MultimodalViewModel, actions: MultimodalDemoActions? = null) {
    val state = model.state
    val explicit = state.selection as? MultimodalBridgeSelection.Explicit
    var backendId by remember(model, state.selection) { mutableStateOf(explicit?.backendId ?: "") }
    val focus = LocalFocusManager.current
    val keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
    LaunchedEffect(model) { model.refreshBackends() }

    Text(if (state.inputKind == MultimodalInputKind.VIDEO) "视频理解 · 实验性单视频" else "图像理解 · 单图",
        style = MaterialTheme.typography.titleMedium)
    if (state.inputKind == MultimodalInputKind.VIDEO) Text("视频支持取决于所选后端及输入规格；后端可用不代表视频可用。")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { model.selectBackend(MultimodalBridgeSelection.Default) }, enabled = state.controlsEnabled,
            modifier = Modifier.testTag("multimodal-default")) { Text(if (explicit == null) "默认 ✓" else "使用默认后端") }
        TextButton(onClick = model::refreshBackends, enabled = !state.refreshing && !state.closed,
            modifier = Modifier.testTag("multimodal-refresh")) { Text(if (state.refreshing) "刷新中…" else "刷新后端") }
    }
    OutlinedTextField(backendId, { backendId = it; if (it.isNotBlank()) model.selectBackend(MultimodalBridgeSelection.Explicit(it)) },
        label = { Text("显式后端 ID（严格选择）") }, enabled = state.controlsEnabled, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth().testTag("multimodal-backend"))
    Text(if (explicit == null) "选择：默认" else "选择：${explicit.backendId}", Modifier.testTag("multimodal-selection"))
    Text(state.backends.joinToString("\n") { backend ->
        "${backend.backendId} · ${if (backend.availability.available) "可用" else backend.availability.reason ?: "不可用"} · minOS ${backend.availability.minimumOS ?: "—"}"
    }.ifEmpty { "尚无后端列表" }, Modifier.testTag("multimodal-backends"))
    state.queryFailure?.let { Text("查询：${failureText(it)}", Modifier.testTag("multimodal-query-failure"), color = MaterialTheme.colorScheme.error) }

    Text(state.inputDisplayName ?: if (state.inputReference == null) "尚未选择输入" else "已选择输入",
        Modifier.testTag("multimodal-input"))
    if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = actions::selectImageFile, enabled = state.controlsEnabled,
            modifier = Modifier.testTag("multimodal-select-input")) { Text("选择图片") }
        if (actions is MultimodalVideoDemoActions) TextButton(onClick = actions::selectVideoFile,
            enabled = state.controlsEnabled, modifier = Modifier.testTag("multimodal-select-video")) { Text("选择视频") }
        TextButton(onClick = actions::clearImageFile, enabled = !state.closed && state.inputReference != null,
            modifier = Modifier.testTag("multimodal-clear-input")) { Text("清除输入") }
    } else Text("图片输入由宿主提供")
    OutlinedTextField(state.prompt, model::setPrompt, label = { Text("原始 prompt") }, enabled = state.controlsEnabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth().testTag("multimodal-prompt"))
    OutlinedTextField(state.tokenBudget, model::setTokenBudget, label = { Text("输出 token budget（正整数）") },
        enabled = state.controlsEnabled, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth().testTag("multimodal-budget"))
    Button(onClick = { focus.clearFocus(); model.generate() }, enabled = state.canGenerate,
        modifier = Modifier.testTag("multimodal-generate")) { Text("生成") }
    if (state.phase == MultimodalPhase.RUNNING || state.phase == MultimodalPhase.CANCELLING) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = model::cancel, enabled = state.phase == MultimodalPhase.RUNNING,
            modifier = Modifier.testTag("multimodal-cancel")) {
            Text(if (state.phase == MultimodalPhase.CANCELLING) "等待原生任务释放…" else "取消生成")
        }
    }
    Text(state.phase.name, Modifier.testTag("multimodal-phase"))
    state.update?.let { Text(it.text, Modifier.testTag("multimodal-update")) }
    state.failure?.let { Text(failureText(it), Modifier.testTag("multimodal-failure"), color = MaterialTheme.colorScheme.error) }
    state.output?.let { output ->
        Text("实际后端：${output.backendId}", Modifier.testTag("multimodal-output-backend"))
        Text("model=${output.model.modelId}\nmodelRevision=${output.model.modelRevision}\nruntime=${output.model.runtimeRevision}", Modifier.testTag("multimodal-identity"))
        Text(output.text, Modifier.testTag("multimodal-output"))
    }
    if (state.closed) Text(if (state.closing) "正在关闭…" else "已关闭", Modifier.testTag("multimodal-closed"))
    state.closeFailure?.let { Text("关闭：${failureText(it)}", Modifier.testTag("multimodal-close-failure"), color = MaterialTheme.colorScheme.error) }
}

private fun failureText(failure: MultimodalBridgeFailure): String =
    listOfNotNull(failure.code.name, failure.reason, failure.domain, failure.platformCode?.toString()).joinToString(" · ")
