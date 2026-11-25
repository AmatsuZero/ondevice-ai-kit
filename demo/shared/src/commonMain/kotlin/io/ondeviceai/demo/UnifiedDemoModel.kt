package io.ondeviceai.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DemoTab { EXPLORE, MODELS, RECORDS }

enum class DemoCapabilityId {
    ASR,
    TTS,
    VOICE_EFFECTS,
    FACE_DETECTION,
    IMAGE_UNDERSTANDING,
    FILE_SUPER_RESOLUTION,
    PLAYBACK_ENHANCEMENT,
    VIDEO_UNDERSTANDING,
    IDENTITY_LIVENESS,
}

enum class DemoCapabilityAvailabilityKind {
    NOT_QUERIED,
    PREPARING,
    FAILED,
    READY,
    MISSING_HOST,
    MISSING_BACKEND,
    MISSING_MODEL,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
    FROZEN_PREVIEW,
}

data class DemoCapabilityStatus(
    val kind: DemoCapabilityAvailabilityKind,
    val detail: String? = null,
)

enum class DemoHostState { PREPARING, READY, FAILED }

fun demoHostAvailability(state: DemoHostState): DemoCapabilityStatus = DemoCapabilityStatus(when (state) {
    DemoHostState.PREPARING -> DemoCapabilityAvailabilityKind.PREPARING
    DemoHostState.READY -> DemoCapabilityAvailabilityKind.READY
    DemoHostState.FAILED -> DemoCapabilityAvailabilityKind.FAILED
})

/** Maps Host evidence, never a platform name, to a user-facing state. */
fun demoAvailability(hostPresent: Boolean = true, available: Boolean = false,
    queried: Boolean = false, reason: String? = null): DemoCapabilityStatus {
    val normalized = reason.orEmpty().lowercase().replace("_", "").replace("-", "")
    val kind = when {
        !hostPresent -> DemoCapabilityAvailabilityKind.MISSING_HOST
        normalized.contains("permission") || normalized.contains("notauthorized") -> DemoCapabilityAvailabilityKind.PERMISSION_REQUIRED
        available -> DemoCapabilityAvailabilityKind.READY
        normalized.contains("model") || normalized.contains("resourcemissing") -> DemoCapabilityAvailabilityKind.MISSING_MODEL
        normalized.contains("unsupported") || normalized.contains("requiresos") -> DemoCapabilityAvailabilityKind.UNSUPPORTED
        !queried && reason == null -> DemoCapabilityAvailabilityKind.NOT_QUERIED
        else -> DemoCapabilityAvailabilityKind.MISSING_BACKEND
    }
    return DemoCapabilityStatus(kind, reason)
}

val DemoCapabilityStatus.label: String get() = when (kind) {
    DemoCapabilityAvailabilityKind.NOT_QUERIED -> "待查询"
    DemoCapabilityAvailabilityKind.PREPARING -> "正在准备"
    DemoCapabilityAvailabilityKind.FAILED -> "准备失败"
    DemoCapabilityAvailabilityKind.READY -> "可体验"
    DemoCapabilityAvailabilityKind.MISSING_HOST -> "当前构建未接入"
    DemoCapabilityAvailabilityKind.MISSING_BACKEND -> "后端不可用"
    DemoCapabilityAvailabilityKind.MISSING_MODEL -> "需要本地模型"
    DemoCapabilityAvailabilityKind.PERMISSION_REQUIRED -> "需要权限"
    DemoCapabilityAvailabilityKind.UNSUPPORTED -> "当前环境不支持"
    DemoCapabilityAvailabilityKind.FROZEN_PREVIEW -> "研发暂停 · 仅预览"
}

val DemoCapabilityStatus.explanation: String get() = listOfNotNull(label, detail).joinToString(" · ")

data class DemoCapabilityDescriptor(
    val id: DemoCapabilityId,
    val title: String,
    val category: String,
    val purpose: String,
    val frozen: Boolean = false,
    val previewOnly: Boolean = false,
    val previewCopy: String = "",
    val status: DemoCapabilityStatus = DemoCapabilityStatus(DemoCapabilityAvailabilityKind.NOT_QUERIED),
)

enum class DemoRecordOutcome { COMPLETED, FAILED, CANCELLED }

typealias DemoTerminalObserver = (DemoCapabilityId, DemoRecordOutcome, String) -> Unit

data class DemoRecord(
    val capability: DemoCapabilityId,
    val platform: String,
    val timestampMillis: Long,
    val outcome: DemoRecordOutcome,
    val summary: String,
)

class DemoRecordsStore {
    private var entries by mutableStateOf<List<DemoRecord>>(emptyList())

    fun append(record: DemoRecord) {
        entries = (entries + record).takeLast(30)
    }

    fun clear() { entries = emptyList() }

    fun snapshot(): List<DemoRecord> = entries.toList()
}

sealed interface UnifiedDemoRoute {
    data object Explore : UnifiedDemoRoute
    data object Models : UnifiedDemoRoute
    data object Records : UnifiedDemoRoute
    data class Capability(val id: DemoCapabilityId) : UnifiedDemoRoute
    data class ModelDetail(val id: DemoCapabilityId) : UnifiedDemoRoute
    data class RecordDetail(val index: Int) : UnifiedDemoRoute
}

fun UnifiedDemoRoute.topLevel(): UnifiedDemoRoute = when (this) {
    is UnifiedDemoRoute.Capability -> UnifiedDemoRoute.Explore
    is UnifiedDemoRoute.ModelDetail -> UnifiedDemoRoute.Models
    is UnifiedDemoRoute.RecordDetail -> UnifiedDemoRoute.Records
    UnifiedDemoRoute.Explore, UnifiedDemoRoute.Models, UnifiedDemoRoute.Records -> this
}

class UnifiedDemoModel(
    initialCatalog: List<DemoCapabilityDescriptor> = defaultDemoCatalog,
    val records: DemoRecordsStore = DemoRecordsStore(),
    private val onLeave: (DemoCapabilityId) -> Unit = {},
    private val isIdle: () -> Boolean = { true },
) {
    var search by mutableStateOf("")
    var category by mutableStateOf("全部")
    val filteredCatalog: List<DemoCapabilityDescriptor> get() = catalog.filter {
        (category == "全部" || category == it.category) &&
            (search.isBlank() || (it.title + it.purpose).contains(search.trim(), ignoreCase = true))
    }

    private var pendingRecords by mutableStateOf<Map<DemoCapabilityId, DemoRecord>>(emptyMap())

    fun recordTerminal(id: DemoCapabilityId, platform: String, timestamp: Long,
        outcome: DemoRecordOutcome, summary: String) {
        if (!descriptor(id).frozen) pendingRecords = pendingRecords + (id to DemoRecord(id, platform, timestamp, outcome, summary))
    }

    fun canSaveRecord(id: DemoCapabilityId): Boolean = id in pendingRecords

    fun saveRecord(id: DemoCapabilityId) {
        val record = pendingRecords[id] ?: return
        records.append(record)
        pendingRecords = pendingRecords - id
    }
    var route by mutableStateOf<UnifiedDemoRoute>(UnifiedDemoRoute.Explore)
        private set

    var catalog by mutableStateOf(initialCatalog.toList())
        private set

    var leaving by mutableStateOf(false)
        private set
    private var pendingRoute: UnifiedDemoRoute? = null
    private var origin: UnifiedDemoRoute = UnifiedDemoRoute.Explore

    fun navigate(next: UnifiedDemoRoute) {
        if (leaving || next == route) return
        if (next is UnifiedDemoRoute.Capability || next is UnifiedDemoRoute.ModelDetail) {
            if (route == UnifiedDemoRoute.Explore || route == UnifiedDemoRoute.Models || route == UnifiedDemoRoute.Records) origin = route
        }
        val id = when (val current = route) {
            is UnifiedDemoRoute.Capability -> current.id
            is UnifiedDemoRoute.ModelDetail -> current.id
            else -> null
        }
        if (id != null) onLeave(id)
        if (!isIdle()) {
            pendingRoute = next
            leaving = true
        } else route = next
    }

    fun openCapability(id: DemoCapabilityId) = navigate(UnifiedDemoRoute.Capability(id))

    fun back() = navigate(origin)

    fun finishNavigation() {
        if (!leaving || !isIdle()) return
        pendingRoute?.let { route = it }
        pendingRoute = null
        leaving = false
    }

    fun navigateTopLevel(tab: DemoTab) {
        navigate(when (tab) {
            DemoTab.EXPLORE -> UnifiedDemoRoute.Explore
            DemoTab.MODELS -> UnifiedDemoRoute.Models
            DemoTab.RECORDS -> UnifiedDemoRoute.Records
        })
    }

    fun updateStatus(id: DemoCapabilityId, status: DemoCapabilityStatus) {
        catalog = catalog.map { descriptor ->
            if (descriptor.id == id && !descriptor.frozen) descriptor.copy(status = status) else descriptor
        }
    }

    fun descriptor(id: DemoCapabilityId): DemoCapabilityDescriptor =
        catalog.first { it.id == id }
}

val defaultDemoCatalog: List<DemoCapabilityDescriptor> = listOf(
    DemoCapabilityDescriptor(DemoCapabilityId.ASR, "语音识别", "语音", "将麦克风输入转换为文字，支持文件输入的后端还可转写音频文件。"),
    DemoCapabilityDescriptor(DemoCapabilityId.TTS, "语音合成", "语音", "把文字转换为本机语音。"),
    DemoCapabilityDescriptor(DemoCapabilityId.VOICE_EFFECTS, "离线变声", "语音", "对比不同音色，也可以导入或录制自己的声音。"),
    DemoCapabilityDescriptor(DemoCapabilityId.FACE_DETECTION, "人脸检测", "视觉", "查看人脸框、关键点和检测状态。"),
    DemoCapabilityDescriptor(DemoCapabilityId.IMAGE_UNDERSTANDING, "图像理解", "视觉", "为图片输入提出问题并查看模型输出。"),
    DemoCapabilityDescriptor(DemoCapabilityId.FILE_SUPER_RESOLUTION, "视频文件超分", "视频", "选择视频并查看真实处理进度与输出。"),
    DemoCapabilityDescriptor(DemoCapabilityId.PLAYBACK_ENHANCEMENT, "播放时增强", "视频", "查看播放器支持的实时增强模式和实际呈现状态。"),
    DemoCapabilityDescriptor(
        id = DemoCapabilityId.VIDEO_UNDERSTANDING,
        title = "视频理解",
        category = "实验室",
        purpose = "预览视频理解的输入、授权和结果布局。",
        frozen = true,
        previewOnly = true,
        previewCopy = "研发暂停 · 仅预览，不调用冻结模型。",
        status = DemoCapabilityStatus(DemoCapabilityAvailabilityKind.FROZEN_PREVIEW, "研发暂停"),
    ),
    DemoCapabilityDescriptor(
        id = DemoCapabilityId.IDENTITY_LIVENESS,
        title = "身份与活体",
        category = "实验室",
        purpose = "预览本地授权、管理和拒绝状态。",
        frozen = true,
        previewOnly = true,
        previewCopy = "研发暂停 · 未校准，不能给出认证结论。",
        status = DemoCapabilityStatus(DemoCapabilityAvailabilityKind.FROZEN_PREVIEW, "未校准"),
    ),
)
