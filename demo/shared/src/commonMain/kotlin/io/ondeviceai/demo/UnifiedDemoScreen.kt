package io.ondeviceai.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.mutableStateOf
import io.ondeviceai.binding.RealtimeSRMode
import kotlin.math.roundToInt

interface UnifiedDemoBindings {
    val platformLabel: String
    val faceModel: FaceViewModel
    val faceActions: io.ondeviceai.binding.DemoActions
    val identityModel: IdentityViewModel?
    val identityActions: IdentityDemoActions?
    val identitySetup: IdentityModelSetupDemoActions?
    val speechModel: SpeechViewModel?
    val speechActions: SpeechDemoActions?
    val videoModel: VideoViewModel?
    val videoActions: VideoDemoActions?
    val multimodalModel: MultimodalViewModel?
    val multimodalActions: MultimodalDemoActions?
    val voiceEffectsModel: VoiceEffectsViewModel?
    val voiceEffectsActions: VoiceEffectsDemoActions?
    val realtimeSr: RealtimeSRDemoActions?
    val playbackEnhancement: PlaybackEnhancementDemoActions? get() = null
    fun modelSetup(id: DemoCapabilityId): UnifiedDemoModelActions? = null
    val nowMillis: Long get() = 0L
    fun setRecordSink(sink: DemoTerminalObserver?) {}
    fun enterCapability(id: DemoCapabilityId) {}
    fun retryCleanup() {}
    fun isIdle(): Boolean = true
    fun status(id: DemoCapabilityId): DemoCapabilityStatus
    fun openVoiceEffects()
    fun leaveCapability(id: DemoCapabilityId)
}

interface UnifiedDemoModelActions {
    val status: String
    val busy: Boolean
    val canPrepare: Boolean
    fun prepare()
    fun cancel()
}

interface RealtimeSRDemoActions {
    val supportedModes: List<RealtimeSRMode>
    val selectedMode: RealtimeSRMode
    val presentedMode: RealtimeSRMode?
    val fallbackToOriginal: Boolean
    fun selectMode(mode: RealtimeSRMode): Boolean
}

/** Opens the platform player that owns decoded frames and realtime SR presentation. */
interface PlaybackEnhancementDemoActions {
    val playerAvailable: Boolean
    val playerDescription: String
    /** True when the platform Host exposes the playback controls to CMP. */
    val controlsAvailable: Boolean get() = false
    /** Host-owned playback state; CMP renders it instead of maintaining fake local state. */
    val hasVideo: Boolean get() = false
    val isPlaying: Boolean get() = false
    val inputName: String? get() = null
    val playbackStatus: String get() = "尚未选择视频"
    fun openPlayer()
    /** Platform Host controls the playback engine; CMP owns the controls. */
    fun togglePlayback() {}
    fun showOriginal() {}
    fun showEnhancedPreview() {}
}

@Composable
fun UnifiedDemoScreen(
    model: UnifiedDemoModel,
    bindings: UnifiedDemoBindings,
    modifier: Modifier = Modifier,
    facePreview: (@Composable () -> Unit)? = null,
    voiceContent: (@Composable () -> Unit)? = null,
    playbackContent: (@Composable () -> Unit)? = null,
) {
    LaunchedEffect(bindings) {
        bindings.speechModel?.refreshBackends(io.ondeviceai.binding.SpeechBridgeCapability.TTS)
        bindings.speechModel?.refreshBackends(io.ondeviceai.binding.SpeechBridgeCapability.ASR)
        bindings.videoModel?.refreshBackends()
        bindings.multimodalModel?.refreshBackends()
        while (true) {
            defaultDemoCatalog.forEach { descriptor -> model.updateStatus(descriptor.id, bindings.status(descriptor.id)) }
            kotlinx.coroutines.delay(300)
        }
    }
    DisposableEffect(bindings, model, bindings.faceModel, bindings.speechModel, bindings.videoModel, bindings.multimodalModel, bindings.voiceEffectsModel) {
        bindings.setRecordSink { id, outcome, summary ->
            model.recordTerminal(id, bindings.platformLabel, bindings.nowMillis, outcome, summary)
        }
        onDispose { bindings.setRecordSink(null) }
    }
    LaunchedEffect(model.route) {
        (model.route as? UnifiedDemoRoute.Capability)?.let { bindings.enterCapability(it.id) }
    }
    LaunchedEffect(model.leaving) {
        while (model.leaving) {
            model.finishNavigation()
            if (model.leaving) kotlinx.coroutines.delay(16)
        }
    }
    UnifiedDemoTheme {
        Scaffold(
            modifier = modifier.fillMaxSize().testTag("unified-demo"),
            bottomBar = {
                if (model.route.topLevel() == model.route) UnifiedNavigationBar(model)
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                if (model.leaving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在停止任务并释放资源…", Modifier.testTag("unified-leaving"))
                    TextButton(onClick = bindings::retryCleanup) { Text("重试资源清理") }
                }
                when (val route = model.route) {
                    UnifiedDemoRoute.Explore -> ExplorePage(model)
                    UnifiedDemoRoute.Models -> ModelsPage(model)
                    UnifiedDemoRoute.Records -> RecordsPage(model)
                    is UnifiedDemoRoute.Capability -> key(route.id) { CapabilityPage(model, bindings, route.id, facePreview, voiceContent, playbackContent) }
                    is UnifiedDemoRoute.ModelDetail -> ModelDetailPage(model, bindings, route.id)
                    is UnifiedDemoRoute.RecordDetail -> RecordDetailPage(model, route.index)
                }
            }
        }
    }
}

@Composable
private fun UnifiedNavigationBar(model: UnifiedDemoModel) {
    NavigationBar {
        listOf(
            DemoTab.EXPLORE to "探索",
            DemoTab.MODELS to "模型",
            DemoTab.RECORDS to "记录",
        ).forEach { (tab, label) ->
            NavigationBarItem(
                selected = model.route.topLevel() == when (tab) {
                    DemoTab.EXPLORE -> UnifiedDemoRoute.Explore
                    DemoTab.MODELS -> UnifiedDemoRoute.Models
                    DemoTab.RECORDS -> UnifiedDemoRoute.Records
                },
                onClick = { model.navigateTopLevel(tab) },
                icon = { Text(label.take(1)) },
                label = { Text(label) },
                modifier = Modifier.testTag("nav-${label}"),
            )
        }
    }
}

@Composable
private fun ExplorePage(model: UnifiedDemoModel) {
    Text("端侧智能体验", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
    Text("选择一项能力，体验本机处理。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    val categories = remember { listOf("全部", "语音", "视觉", "视频", "实验室") }
    OutlinedTextField(
        value = model.search,
        onValueChange = { model.search = it },
        label = { Text("搜索能力") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("explore-search"),
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            FilterChip(selected = model.category == category, onClick = { model.category = category }, label = { Text(category) })
        }
    }
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("explore-list")) {
        items(model.filteredCatalog) { descriptor ->
            CapabilityCard(descriptor) { model.openCapability(descriptor.id) }
        }
    }
}

@Composable
private fun CapabilityCard(descriptor: DemoCapabilityDescriptor, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("capability-${descriptor.id.name.lowercase()}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(descriptor.title, style = MaterialTheme.typography.titleMedium)
                Text(descriptor.status.label, color = MaterialTheme.colorScheme.primary)
            }
            Text(descriptor.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (descriptor.previewOnly) Text(descriptor.previewCopy, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun ModelsPage(model: UnifiedDemoModel) {
    Text("模型", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
    Text("查看资源状态，准备当前设备需要的模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("models-list")) {
        items(model.catalog) { descriptor ->
            Card(onClick = { model.navigate(UnifiedDemoRoute.ModelDetail(descriptor.id)) }, modifier = Modifier.fillMaxWidth().testTag("model-${descriptor.id.name.lowercase()}")) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(descriptor.title)
                    Text(descriptor.status.label, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ModelDetailPage(model: UnifiedDemoModel, bindings: UnifiedDemoBindings, id: DemoCapabilityId) {
    PageHeader(title = "模型 · ${model.descriptor(id).title}", onBack = { model.navigateTopLevel(DemoTab.MODELS) })
    val descriptor = model.descriptor(id)
    Text(descriptor.status.explanation)
    if (descriptor.frozen) Text(descriptor.previewCopy, color = MaterialTheme.colorScheme.tertiary)
    else Text("导入与校验由本机完成，准备好后即可返回能力页体验。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    bindings.modelSetup(id)?.takeUnless { descriptor.frozen }?.let { setup ->
        Spacer(Modifier.height(12.dp))
        Text(setup.status, Modifier.testTag("model-status-${id.name.lowercase()}"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = setup::prepare, enabled = setup.canPrepare && !setup.busy,
                modifier = Modifier.testTag("model-prepare-${id.name.lowercase()}")) { Text("准备模型") }
            if (setup.busy) TextButton(onClick = setup::cancel) { Text("取消") }
        }
    }
    if (bindings.status(id).kind == DemoCapabilityAvailabilityKind.MISSING_HOST) {
        Text("当前构建未接入模型设置 Host。", color = MaterialTheme.colorScheme.error)
    }
    if (id == DemoCapabilityId.ASR) {
        val resources = (bindings.speechActions as? SpeechAsrResourceProvider)?.asrResources
        var locale by remember { mutableStateOf("zh-CN") }
        if (resources != null) {
            OutlinedTextField(locale, { locale = it }, label = { Text("语言 / locale") }, enabled = !resources.state.busy)
            AsrResourceControls(resources, locale, bindings.speechModel?.state?.controlsEnabled == true)
        }
    }
    if (!descriptor.frozen) TextButton(onClick = { model.openCapability(id) }) { Text("进入能力页") }
}

@Composable
private fun RecordsPage(model: UnifiedDemoModel) {
    Text("记录", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
    Text("任务结束后点击“保存记录”才会加入历史，取消不会自动保存。仅保留当前进程的摘要，最多 30 条，不保留媒体或身份模板。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("相机连续帧、播放状态和冻结预览不生成任务记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = model.records::clear, enabled = model.records.snapshot().isNotEmpty()) { Text("清空记录") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("records-list")) {
        items(model.records.snapshot().withIndex().toList().asReversed()) { (index, record) ->
            Card(onClick = { model.navigate(UnifiedDemoRoute.RecordDetail(index)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(defaultDemoCatalog.first { it.id == record.capability }.title)
                    Text("${record.platform} · ${record.outcome}")
                    Text(record.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecordDetailPage(model: UnifiedDemoModel, index: Int) {
    PageHeader(title = "记录", onBack = { model.navigateTopLevel(DemoTab.RECORDS) })
    val record = model.records.snapshot().getOrNull(index)
    if (record == null) Text("记录已清空，请重新选择输入。")
    else {
        Text("${record.platform} · ${record.outcome}")
        Text(record.summary)
        Text("重新进入后需要重新选择输入；不会复用历史媒体或身份模板。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { model.openCapability(record.capability) }) { Text("重新体验") }
    }
}

@Composable
private fun CapabilityPage(
    model: UnifiedDemoModel,
    bindings: UnifiedDemoBindings,
    id: DemoCapabilityId,
    facePreview: (@Composable () -> Unit)?,
    voiceContent: (@Composable () -> Unit)?,
    playbackContent: (@Composable () -> Unit)?,
) {
    val descriptor = model.descriptor(id)
    PageHeader(title = descriptor.title, onBack = model::back)
    // File and playback details own their own intro; no repeated purpose / save row above them.
    val isFileDetail = id == DemoCapabilityId.FILE_SUPER_RESOLUTION
    val isPlaybackDetail = id == DemoCapabilityId.PLAYBACK_ENHANCEMENT
    if (!isFileDetail && !isPlaybackDetail) {
        Text(descriptor.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (descriptor.previewOnly) {
        ScrollableDetail { FrozenPreviewPage(descriptor, bindings.identitySetup.takeIf { id == DemoCapabilityId.IDENTITY_LIVENESS },
            bindings.identityModel.takeIf { id == DemoCapabilityId.IDENTITY_LIVENESS }) }
        return
    }
    if (model.leaving) return
    // File details render save inside their real result card; other pages only when a record is pending.
    if (!isFileDetail && model.canSaveRecord(id)) {
        TextButton(onClick = { model.saveRecord(id) }, modifier = Modifier.testTag("save-record")) { Text("保存记录") }
    }
    Box(Modifier.fillMaxSize()) {
    when (id) {
        DemoCapabilityId.ASR, DemoCapabilityId.TTS -> bindings.speechModel?.let { ScrollableDetail { SpeechScreen(it, bindings.speechActions,
            if (id == DemoCapabilityId.ASR) io.ondeviceai.binding.SpeechBridgeCapability.ASR else io.ondeviceai.binding.SpeechBridgeCapability.TTS) } }
            ?: UnavailablePage(bindings.status(id))
        DemoCapabilityId.VOICE_EFFECTS -> {
            val voiceModel = bindings.voiceEffectsModel
            val voiceActions = bindings.voiceEffectsActions
            if (voiceContent != null) voiceContent()
            else if (voiceModel != null && voiceActions != null) VoiceEffectsScreen(voiceModel, voiceActions, showBack = false)
            else UnavailablePage(bindings.status(id))
        }
        DemoCapabilityId.FACE_DETECTION -> Column(Modifier.fillMaxSize()) {
            facePreview?.invoke()
            FaceScreen(bindings.faceModel, bindings.faceActions,
                modifier = Modifier.fillMaxWidth().weight(1f), showSectionTabs = false)
        }
        DemoCapabilityId.IMAGE_UNDERSTANDING -> bindings.multimodalModel?.let { ScrollableDetail { MultimodalScreen(it, bindings.multimodalActions, allowVideoInput = false) } }
            ?: UnavailablePage(bindings.status(id))
        DemoCapabilityId.FILE_SUPER_RESOLUTION -> bindings.videoModel?.let {
            ScrollableDetail {
                VideoScreen(it, bindings.videoActions,
                    canSaveRecord = { model.canSaveRecord(id) },
                    onSaveRecord = { model.saveRecord(id) })
            }
        } ?: UnavailablePage(bindings.status(id))
        // The playback page owns its own layout: a viewport-bounded preview outside
        // the vertical scroll, with the remaining controls scrollable beneath it.
        DemoCapabilityId.PLAYBACK_ENHANCEMENT -> PlaybackEnhancementPage(
            bindings.realtimeSr,
            bindings.status(id),
            bindings.playbackEnhancement,
            playbackContent,
        )
        DemoCapabilityId.VIDEO_UNDERSTANDING, DemoCapabilityId.IDENTITY_LIVENESS -> Unit
    }
    }
}

@Composable
private fun ScrollableDetail(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackEnhancementPage(
    actions: RealtimeSRDemoActions?,
    status: DemoCapabilityStatus,
    player: PlaybackEnhancementDemoActions?,
    playbackContent: (@Composable () -> Unit)?,
) {
    var state by remember(player, actions) { mutableStateOf(PlaybackDemoState.read(player, actions)) }
    LaunchedEffect(player, actions) {
        while (true) {
            state = PlaybackDemoState.read(player, actions)
            kotlinx.coroutines.delay(250)
        }
    }

    // Outer BoxWithConstraints gets finite viewport bounds because it sits outside
    // any vertical scroll. Half of maxHeight is only a maximum cap for the 16:9
    // preview; the preview itself occupies just its actual fitted height.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fitWidth = minOf(maxWidth, maxHeight * 0.5f * 16f / 9f)
        val fitHeight = fitWidth * 9f / 16f
        Column(Modifier.fillMaxSize()) {
            // The native renderer keeps the single `playback-preview` identity; only
            // this CMP wrapper has a distinct identifier. Placed at the top and
            // horizontally centered, with no reserved half-screen slot or vertical
            // centering gap.
            Box(
                Modifier
                    .size(fitWidth, fitHeight)
                    .align(Alignment.CenterHorizontally)
                    .background(Color.Black)
                    .testTag("playback-preview-container")
            ) {
                // Invoke native content even when empty so the surface lifecycle
                // is preserved; the hint is a non-interactive overlay.
                playbackContent?.invoke()
                if (!state.hasVideo) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有选择视频", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Remaining controls scroll independently of the fixed, bounded preview.
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 2. Input / status / select + play controls.
                player?.let {
                    Card(Modifier.fillMaxWidth().testTag("playback-player-entry")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("输入：${state.inputName ?: "尚未选择"}")
                            Text(state.status, Modifier.testTag("playback-host-status"))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = it::openPlayer, enabled = it.playerAvailable,
                                    modifier = Modifier.testTag("open-realtime-player")) { Text("选择视频") }
                                if (it.controlsAvailable) OutlinedButton(onClick = it::togglePlayback,
                                    enabled = it.playerAvailable && state.hasVideo,
                                    modifier = Modifier.testTag("toggle-realtime-player")) {
                                    Text(if (state.isPlaying) "暂停" else "播放")
                                }
                            }
                        }
                    }
                }

                // 3. Supported modes are generated from the Host; chips wrap safely on narrow screens.
                // 4. Presented / fallback is reported by the Host and never synthesized from selection.
                actions?.let {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("增强模式", style = MaterialTheme.typography.titleMedium)
                            Text("仅显示设备支持的选项", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.supportedModes.forEach { mode ->
                                    FilterChip(selected = mode == state.selectedMode,
                                        onClick = { it.selectMode(mode); state = PlaybackDemoState.read(player, actions) },
                                        label = { Text(if (mode == RealtimeSRMode.ORIGINAL) "原画" else if (mode == RealtimeSRMode.AUTO) "自动" else mode.name) },
                                        modifier = Modifier.testTag("playback-mode-${mode.name.lowercase()}"))
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("用户选择：${state.selectedMode}", Modifier.testTag("playback-selected-mode"))
                            val presented = if (!state.hasVideo) "尚未播放"
                                else (state.presentedMode?.name ?: "等待播放器确认上屏")
                            Text("实际呈现：$presented", Modifier.testTag("playback-presented-mode"))
                            Text("开始播放后显示实际使用的增强方式；增强不可用时自动回退原画。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.fallbackToOriginal) Text("当前已回退到原画播放路径。", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                } ?: if (player == null) UnavailablePage(status) else {
                    Text("当前 Host 尚未提供超分模式与实际上屏状态。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FrozenPreviewPage(descriptor: DemoCapabilityDescriptor, identitySetup: IdentityModelSetupDemoActions?, identity: IdentityViewModel?) {
    Text(descriptor.previewCopy, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("frozen-preview"))
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("这里只展示输入、授权、限制和结果布局，不调用冻结模型，也不输出验证或认证结论。")
    Button(onClick = {}, enabled = false) { Text("仅预览") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (descriptor.id == DemoCapabilityId.IDENTITY_LIVENESS) "授权 → 本机数据 → 待校准结果" else "视频输入 → 采样 → 待验证输出")
            Text("合成流程示意 · 未采集输入 · 未运行模型")
            Text("结果区将在能力恢复后展示真实输出。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    identitySetup?.let {
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(it.status, Modifier.testTag("identity-setup-status"))
        if (it.sessionReady) TextButton(onClick = it::deleteLocalEnrollment, enabled = !it.busy) { Text("删除注册并停用") }
    }
    identity?.let {
        if (it.candidates.isNotEmpty() || it.publishedGallery != null || it.candidateDeletionPending || it.deletionRecoveryRequired) {
            Text("已有本机数据管理（仅删除）")
            it.candidates.forEach { candidate ->
                TextButton(onClick = { it.selectCandidateConfigured(candidate.reference) }, enabled = !it.state.busy) { Text("用户 ${candidate.number}") }
            }
            TextButton(onClick = it::deleteCandidateConfigured, enabled = !it.state.busy && it.selectedCandidate != null && !it.candidateDeletionPending) { Text("删除当前用户") }
            TextButton(onClick = it::deleteGalleryConfigured, enabled = !it.state.busy && it.publishedGallery != null) { Text("删除当前图库") }
            TextButton(onClick = it::refreshDeletion, enabled = !it.state.busy) { Text("刷新删除进度") }
            it.state.deletion?.let { deletion -> Text("删除状态：$deletion") }
            if (it.deletionRecoveryRequired) Text("物理清理未完成，请通过已有停用入口处理。", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun UnavailablePage(status: DemoCapabilityStatus) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(status.explanation, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("unavailable"))
    Text("可前往模型页检查资源，或在系统设置中查看权限。", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    // Custom measured layout so the title sits on the full header midpoint, not
    // merely the space left of the back control. The back button is measured at
    // its natural width (>=48dp target); the title's max width is bounded by the
    // header width minus twice that measured back width, reserving symmetric
    // space on both sides so the two can never overlap. Height grows naturally
    // to fit a wrapped title while never dropping below 52dp.
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail-header"),
        content = {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("unified-back"),
            ) { Text("‹ 返回") }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        },
    ) { measurables, constraints ->
        val headerWidth = constraints.maxWidth
        val minBackHeight = 48.dp.toPx().roundToInt()
        val minHeaderHeight = 52.dp.toPx().roundToInt()

        // Loose minimums: natural width, >=48dp height, unbounded upward.
        val backPlaceable = measurables[0].measure(
            Constraints(
                minWidth = 0,
                maxWidth = headerWidth,
                minHeight = minBackHeight,
                maxHeight = Constraints.Infinity,
            )
        )
        // Reserve the measured back width on BOTH sides of the centered title.
        val titleMaxWidth = (headerWidth - 2 * backPlaceable.width).coerceAtLeast(0)
        val titlePlaceable = measurables[1].measure(
            Constraints(
                minWidth = 0,
                maxWidth = titleMaxWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
        )

        val height = maxOf(minHeaderHeight, backPlaceable.height, titlePlaceable.height)
        layout(headerWidth, height) {
            // Back at start, vertically centered.
            backPlaceable.placeRelative(0, (height - backPlaceable.height) / 2)
            // Title exactly centered in the total header width, vertically centered.
            titlePlaceable.placeRelative(
                (headerWidth - titlePlaceable.width) / 2,
                (height - titlePlaceable.height) / 2,
            )
        }
    }
}
