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

data class DemoRecord(
    val capability: DemoCapabilityId,
    val platform: String,
    val timestampMillis: Long,
    val outcome: DemoRecordOutcome,
    val summary: String,
)

class DemoRecordsStore(private val maxEntries: Int = 30) {
    private val entries = ArrayDeque<DemoRecord>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun append(record: DemoRecord) {
        while (entries.size >= maxEntries) entries.removeFirst()
        entries.addLast(record)
    }

    fun clear() = entries.clear()

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
) {
    var route by mutableStateOf<UnifiedDemoRoute>(UnifiedDemoRoute.Explore)
        private set

    var catalog by mutableStateOf(initialCatalog.toList())
        private set

    fun navigate(next: UnifiedDemoRoute) {
        route = next
    }

    fun navigateTopLevel(tab: DemoTab) {
        route = when (tab) {
            DemoTab.EXPLORE -> UnifiedDemoRoute.Explore
            DemoTab.MODELS -> UnifiedDemoRoute.Models
            DemoTab.RECORDS -> UnifiedDemoRoute.Records
        }
    }

    fun updateStatus(id: DemoCapabilityId, status: DemoCapabilityStatus) {
        catalog = catalog.map { descriptor ->
            if (descriptor.id == id) descriptor.copy(status = status) else descriptor
        }
    }

    fun descriptor(id: DemoCapabilityId): DemoCapabilityDescriptor =
        catalog.first { it.id == id }
}

val defaultDemoCatalog: List<DemoCapabilityDescriptor> = listOf(
    DemoCapabilityDescriptor(DemoCapabilityId.ASR, "语音识别", "语音", "把麦克风输入转换为文字。"),
    DemoCapabilityDescriptor(DemoCapabilityId.TTS, "语音合成", "语音", "把文字转换为本机语音。"),
    DemoCapabilityDescriptor(DemoCapabilityId.VOICE_EFFECTS, "离线变声", "语音", "试听原声、升调和降调效果。"),
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
