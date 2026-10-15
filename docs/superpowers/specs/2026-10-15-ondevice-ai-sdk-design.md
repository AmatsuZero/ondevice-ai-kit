# OnDevice AI Kit SDK 设计

日期：2026-10-15

状态：已完成 brainstorming 设计，待用户审阅

## 1. 目标与边界

OnDevice AI Kit 是供多个公司 App 复用的端侧智能 SDK。首版以主流 iOS/Android 手机为验收基线，先交付人脸检测与分析，再交付 ASR/TTS、视频超分和图像/视频理解。SDK 需要支持能力按模块接入、模型按需安装、运行时加载与释放、统一进度/取消/错误事件，以及 KMP Demo 验证。

首版人脸范围是检测与分析：人脸框、关键点、姿态和可用的质量指标。它不做身份识别、1:1 比对、1:N 搜索或活体认证。所有识别结果保留坐标系、置信度和指标可用性，不用零值表示“不可用”。

首版不训练模型，不把任意 Hugging Face 模型自动转换成 Core ML/LiteRT，不将所有能力强制统一到一个推理框架，也不把模型权重提交到 Git。

## 2. 兼容策略

SDK Core、KMP 公共 API 和人脸模块的最低 iOS 版本为 iOS 15。高系统版本能力以独立后端发布，并通过能力查询报告不可用原因，不能抬高整个 SDK 的最低部署版本。

| 能力或后端 | 首版目标 | 最低系统/设备策略 |
| --- | --- | --- |
| Core、模型清单、任务事件、错误协议 | 必选 | iOS 15；Android API 26 作为 Demo 基线 |
| iOS Vision 人脸检测/关键点 | 必选 | iOS 15 |
| iOS Speech / `SFSpeechRecognizer` | 必选后端 | iOS 15；运行时检查 `supportsOnDeviceRecognition`，语言包和设备能力不足时返回状态 |
| iOS `AVSpeechSynthesizer` | 必选后端 | iOS 15；语音由系统提供 |
| Android ML Kit Face Detection | 必选后端 | API 26 起；模型安装状态显式暴露 |
| Android `SpeechRecognizer` on-device | 可选系统后端 | API 31+ 且 `isOnDeviceRecognitionAvailable` 为真 |
| Android `TextToSpeech` | 必选后端 | API 26 起；具体语音由系统引擎提供 |
| Core ML 通用模型 | 可选后端 | iOS 15 起，按模型输入/算子单独验证 |
| VideoToolbox `VTSuperResolutionScalerConfiguration` | 可选高版本后端 | iOS/iPadOS 26+；不能作为 iOS 15 默认实现 |
| Real-ESRGAN Core ML | 可选后端 | 独立验证 iOS 15；移动端不得依赖 macOS 的 `Process`、`ditto` 或 `/usr/bin/unzip` |
| MLX/MLX Swift LM | 可选高版本后端 | 当前 `mlx-swift` 和 `mlx-swift-lm` 声明 iOS 17；按模块隔离 |
| SmolVLM2 Swift/MLX | 可选模型 | 500M 为首选，256M 为低端档，2.2B 为高端档；HuggingSnap Demo 当前要求 iOS 18 |
| MiniCPM-V / llama.cpp | 后续候选 | 从官方 1B GGUF/llama.cpp-omni 路径验证；35B-A3B 需要独立的存储、专家分片和内存验收 |

Compose Multiplatform 的当前文档支持 iOS 14，因此 KMP Demo 的 UI 层不会阻止 iOS 15；实际可用版本仍由选定的 Kotlin、Compose、Xcode 组合锁定。

Android 高版本后端不作为基础 API 的编译前提。iOS 15 App 可以只链接 Core、人脸、系统语音和 Core ML 模块；MLX、VLM、系统超分通过独立 Swift Package product/XCFramework 或按版本条件编译接入。

## 3. 方案选择

采用“原生 SDK + KMP 适配层”。

1. 原生层利用平台生命周期、音视频缓冲区、权限和系统模型能力。
2. 公共层只定义能力协议、模型清单、任务事件和错误码，不暴露 Core ML、MLX、LiteRT 或 llama.cpp 类型。
3. KMP 只共享业务流程、模型管理状态和 Demo UI；平台实现由 `expect/actual` 或注入的原生适配器提供。
4. Android 和 iOS 可以为同一能力选择不同模型和后端，但输出契约、错误语义和验收样本保持一致。

相比单一 C/C++ 推理核心，这个方案更适合系统人脸、系统语音和系统 TTS；相比 KMP 作为核心，它不会让 Kotlin 直接承担 MLX、Core ML 和音频会话的桥接细节。

## 4. 模块结构

```text
ondevice-ai-kit/
├── sdk-core/                 # 能力描述、事件、错误、任务取消、资源预算
├── model-store/              # manifest、下载、SHA-256、安装、回滚、缓存
├── capability-face/          # FaceDetector 与统一 FaceObservation
├── capability-speech/        # ASR/TTS 公共模型与会话接口
├── capability-super-resolution/
├── capability-multimodal/
├── backend-apple-system/     # Vision、Speech、AVSpeechSynthesizer
├── backend-android-system/   # ML Kit、SpeechRecognizer、TextToSpeech
├── backend-coreml/           # iOS 15+ Core ML 模型适配
├── backend-mlx/              # iOS 17+ MLX/MLXLMCommon/MLXVLM
├── backend-litert/           # Android LiteRT/LiteRT-LM，按支持模型接入
├── backend-onnx/             # ONNX Runtime，传统模型或明确支持的模型
├── backend-llama/            # GGUF、多模态 llama.cpp 后端
├── kmp-binding/              # commonMain API 与平台注入
└── demo/                     # KMP Demo 与设备/模型性能记录
```

基础模块不依赖高版本后端。每个后端在编译时声明自己的系统版本和能力列表；运行时的 `CapabilityAvailability` 同时包含 `supported`、`requiresOSVersion`、`modelMissing`、`resourceInsufficient`、`permissionRequired` 和 `backendUnavailable` 等状态。

## 5. 公共 API 与数据流

正式 API 按能力定义，而不是让业务方调用一个暴露框架类型的泛型函数。核心概念如下：

```kotlin
interface CapabilityDescriptor {
    val id: String
    val platform: Platform
    val backend: BackendId
    val minOsVersion: OsVersion?
}

interface ModelStore {
    suspend fun status(model: ModelRef): ModelStatus
    suspend fun install(model: ModelRef, observer: ProgressObserver): InstalledModel
    suspend fun remove(model: ModelRef)
}

interface FaceDetector {
    suspend fun availability(): CapabilityAvailability
    suspend fun detect(input: ImageInput, options: FaceOptions = FaceOptions()): FaceResult
}

interface InferenceSession<I, O> {
    suspend fun start(): SessionResult
    fun run(input: I, options: RunOptions = RunOptions()): Flow<InferenceEvent<O>>
    suspend fun cancel()
    suspend fun close()
}
```

ASR 使用 `AudioChunk`/`AudioFile` 输入和 `TranscriptEvent` 输出；TTS 使用 `TextInput` 和 `AudioOutput`；超分使用文件或平台视频句柄，不能逐帧复制到 KMP；多模态使用图片、视频文件/帧引用和文本消息。`InferenceEvent` 的终态只能是 `completed`、`failed` 或 `cancelled`。

数据流为：能力查询 → 模型状态检查 → 按需安装 → 创建会话 → 预处理 → 后端推理 → 增量/进度事件 → 输出校验 → 关闭会话。取消在下载、模型加载、帧循环和生成回调处检查；失败时保留既有有效模型和输出，不发布半成品。

## 6. 模型清单和动态加载

每个模型由版本化 manifest 描述：

```json
{
  "id": "smolvlm2-500m-video-instruct-mlx",
  "revision": 1,
  "capability": "multimodal.video-summary",
  "backend": "mlx",
  "minOS": { "ios": "17.0" },
  "files": [{ "name": "model.safetensors", "url": "...", "sha256": "..." }],
  "input": { "maxFrames": 32, "maxPixels": 921600 },
  "license": "recorded-per-model",
  "memoryClass": "high"
}
```

模型下载写入沙盒临时目录，逐文件校验并校验整套文件关系，成功后原子移动为 `installed/<id>/<revision>`。旧版本只有在没有活跃会话引用时才能删除。安装、加载、卸载和删除是四个独立动作；“已安装”不表示“已加载”。

模型清单不允许由远端响应覆盖能力协议或执行代码。下载源、许可证、版本、SHA-256 和模型输入输出契约随 SDK 版本发布；远端只提供清单允许的资产。模型和第三方依赖的许可证、归属和再分发限制在仓库清单中维护。

## 7. 首版后端策略

### 7.1 人脸

iOS 使用 Vision 的人脸矩形和关键点请求；Android 使用 ML Kit Face Detection。两端统一输出归一化坐标、图像尺寸、镜像/旋转变换、关键点可用性和质量字段。Android 的 bundled/unbundled 模型状态必须映射到 `modelMissing`/`modelDownloading`，不能把模型未下载误报为“无人脸”。

### 7.2 ASR/TTS

iOS 15 首先使用 `SFSpeechRecognizer` 和 `AVSpeechSynthesizer`；设备支持时请求 on-device 识别。iOS 26 的 `SpeechTranscriber` 作为可选增强后端。Android 首先使用系统 `TextToSpeech`；API 31+ 且可用时使用 on-device `SpeechRecognizer`。离线神经 ASR/TTS（Whisper、sherpa-onnx 或 ONNX 模型）另做后端，不改变公共会话协议。

### 7.3 视频超分

先实现离线视频任务和完整性校验：输出尺寸、帧数、时间戳、音轨、取消和可解码性。iOS 26 系统超分作为高版本后端；iOS 15 路径评估 Core ML Real-ESRGAN。Android 评估 LiteRT GPU/NPU 与 ONNX Runtime，并以同一套短视频样本比较质量、耗时和峰值内存。实时摄像头超分不列入首个实现切片。

### 7.4 多模态

SmolVLM2 500M 作为首个 iOS MLX 验证模型，256M 作为低内存档，2.2B 作为高端档。Android 先验证对应 GGUF/llama.cpp 路径；LiteRT-LM 只接入其模型导入和运行时明确支持的模型。Florence-2 作为结构化图像任务模型（caption、OCR、grounding、检测）单独评估，不把它包装成聊天模型。MiniCPM-V 4.7 先验证 1B，35B-A3B 需要额外的专家分片、存储和内存验收。

## 8. KMP Demo 与分发

Demo 使用 Compose Multiplatform，展示能力状态、模型安装、权限、推理进度、取消、结果和性能摘要。平台侧只注入原生能力实现；相机、麦克风、音频会话和系统权限留在平台层。

iOS SDK 以 Swift Package/XCFramework products 分发，按模块选择依赖；Android SDK 以 Maven AAR 分发。模型资产不进入 AAR 或 XCFramework，使用 manifest 和受控下载器按需安装。Demo 可以加载远端公开模型或开发者指定的本地模型路径，但发布 SDK 不携带大模型权重。

## 9. 验收和测试

每个能力同时有纯逻辑测试、后端集成测试和实机验收。测试报告必须记录设备型号、系统版本、SDK/模型 revision、量化方式、冷启动、预热、纯推理、峰值内存和实际执行后端。

首阶段验收：

1. iOS 15 真机与 Android API 26+ 设备完成图片/相机人脸检测。
2. 旋转、镜像、裁剪和坐标变换有固定夹具回归测试。
3. 模型下载中断、SHA-256 失败、磁盘不足、版本不兼容和取消路径可复现。
4. KMP Demo 能显示能力状态，不把高版本后端缺失显示成崩溃或空结果。
5. ASR/TTS、超分和多模态先建立基线，不在没有设备证据时承诺统一实时性指标。

后续阶段分别验证：ASR 中文/英文文件与流式样本；TTS 队列、取消和音频中断；超分输出完整性和质量对比；多模态图片描述、短视频摘要、首字延迟、峰值内存和持续运行稳定性。

## 10. 仓库与同步

项目目录为 `/Users/daubert/Github/ondevice-ai-kit`。远端目标固定为：

- GitHub：`AmatsuZero/ondevice-ai-kit`
- 极狐个人空间：`https://clients-gitlab.ugreeniot.com/Daubert/ondevice-ai-kit`

创建远端前核对 GitHub 当前登录用户和极狐 `Daubert` namespace；不在组织根空间创建。GitHub 作为主仓，极狐作为镜像副本。同步流程为：本地提交 → GitHub 推送 → 重新读取 GitHub HEAD → 极狐推送 → 重新读取极狐 HEAD 并比较 SHA。禁止 force-push、覆盖现有远端分支或把可达性当作同步成功。

首个实现计划只包含 `sdk-core`、`model-store`、人脸模块、iOS 15 Vision 后端、Android ML Kit 后端和 KMP Demo。ASR/TTS、超分、多模态按后续任务分别设计和验收。

## 11. 主要风险与处理

- iOS 15 与 MLX/系统超分版本不一致：以模块化 product 和运行时 capability gate 隔离。
- 系统语音的离线能力因设备和语言变化：公开 `supportsOnDevice` 与实际失败原因。
- 模型权重过大或更新失败：分片清单、临时目录、SHA-256 和原子发布。
- 不同后端输出质量不一致：统一输入预处理、样本集和报告格式，不宣称跨后端等价。
- Android 硬件加速差异：记录实际 delegate/backend，缺失时安全回退 CPU 或报告不可用。
- Apple App Store 代码加载限制：运行时只下载模型/资源，不下载或执行改变 App 功能的代码。

## 12. 参考资料

- Apple Vision：<https://developer.apple.com/documentation/vision>
- Apple Speech `supportsOnDeviceRecognition`：<https://developer.apple.com/documentation/speech/sfspeechrecognizer/supportsondevicerecognition>
- Apple `SpeechTranscriber`：<https://developer.apple.com/documentation/speech/speechtranscriber>
- Apple VideoToolbox Super Resolution：<https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration>
- Apple App Review 2.5.2：<https://developer.apple.com/app-store/review/guidelines/>
- Google ML Kit Face Detection：<https://developers.google.com/ml-kit/vision/face-detection>
- Android `SpeechRecognizer`：<https://developer.android.com/reference/android/speech/SpeechRecognizer>
- LiteRT-LM：<https://github.com/google-ai-edge/LiteRT-LM>
- ONNX Runtime mobile：<https://onnxruntime.ai/docs/tutorials/mobile/>
- SmolVLM2：<https://huggingface.co/blog/smolvlm2>
- HuggingSnap：<https://github.com/huggingface/huggingsnap>
- Florence-2：<https://huggingface.co/microsoft/Florence-2-base>
- MiniCPM-V 4.7：<https://github.com/OpenBMB/MiniCPM-V/blob/main/docs/minicpm_v4dot7_en.md>
- MiniCPM-V mobile demos：<https://github.com/OpenBMB/MiniCPM-V-Apps>

